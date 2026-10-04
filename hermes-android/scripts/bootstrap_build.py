#!/usr/bin/env python3
"""Prepare an isolated Linux x86_64 Android toolchain from Google's repository.
Requires JDK 17+ already installed. Nothing is installed into the system SDK.
Review the Android SDK terms before running: https://developer.android.com/studio/terms
"""
from pathlib import Path
import hashlib,json,os,platform,re,shutil,subprocess,urllib.request,zipfile
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
TC=ROOT/'.toolchain'
BASE='https://dl.google.com/android/repository/'

def get(url,path,expected=None,algorithm='sha256'):
    def digest(p):
        h=hashlib.new(algorithm)
        with p.open('rb') as f:
            while block:=f.read(1024*1024):h.update(block)
        return h.hexdigest()
    if path.exists() and (expected is None or digest(path)==expected):return path
    print('Downloading '+path.name,flush=True)
    request=urllib.request.Request(url,headers={'User-Agent':'HermesPocket-build/0.1'})
    temporary=path.with_suffix(path.suffix+'.part')
    with urllib.request.urlopen(request,timeout=120) as response,temporary.open('wb') as out:
        while block:=response.read(1024*1024):out.write(block)
    if expected and digest(temporary)!=expected:raise RuntimeError('Checksum mismatch: '+path.name)
    temporary.replace(path);return path

def extract(path,destination):
    destination.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path) as z:
        for info in z.infolist():
            if not (destination/info.filename).resolve().is_relative_to(destination.resolve()):raise ValueError('Unsafe archive path')
        z.extractall(destination)
        for info in z.infolist():
            mode=info.external_attr>>16
            if mode and not info.is_dir():os.chmod(destination/info.filename,mode&0o777)

def prepare():
    if platform.system()!='Linux' or platform.machine() not in ('x86_64','AMD64'):
        raise RuntimeError('This helper supports Linux x86_64 only. Use Android Studio on other platforms.')
    local_jdk=TC/'jdk/usr/lib/jvm/java-17-openjdk-amd64'
    candidate=Path(os.environ.get('JAVA_HOME') or (str(local_jdk) if local_jdk.is_dir() else ''))/'bin/java'
    java=candidate if candidate.is_file() else Path(shutil.which('java') or '/java-not-installed')
    if not java.is_file():raise RuntimeError('Install JDK 17 or 21, or set JAVA_HOME to Android Studio jbr.')
    home=java.resolve().parents[1]
    java_version=subprocess.check_output([str(java),'-version'],stderr=subprocess.STDOUT,text=True)
    major=re.search(r'version "(\d+)',java_version)
    if not major or int(major.group(1))<17:raise RuntimeError('JDK 17 or newer is required.')
    modules=subprocess.check_output([str(java),'--list-modules'],text=True)
    if 'jdk.compiler@' not in modules:raise RuntimeError('A complete JDK including jdk.compiler is required.')
    if not (home/'lib/ct.sym').is_file() or not (home/'bin/keytool').is_file():
        raise RuntimeError('This Java runtime is incomplete. Set JAVA_HOME to a complete JDK 17 or 21 (compiler, ct.sym and keytool).')
    TC.mkdir(exist_ok=True)
    for sdk in [os.environ.get('ANDROID_HOME',''),os.environ.get('ANDROID_SDK_ROOT',''),str(Path.home()/'Android/Sdk')]:
        if sdk and (Path(sdk)/'platforms/android-35/android.jar').is_file() and (Path(sdk)/'build-tools/35.0.0/aapt2').is_file():
            config={'java_home':str(home),'android_jar':str(Path(sdk)/'platforms/android-35/android.jar'),'build_tools':str(Path(sdk)/'build-tools/35.0.0')}
            break
    else:
        repo=get(BASE+'repository2-1.xml',TC/'repository.xml')
        packages={p.attrib['path']:p for p in ET.parse(repo).getroot().findall('remotePackage')}
        for ident,dest,marker in [('platforms;android-35',TC/'platform','android.jar'),('build-tools;35.0.0',TC/'build-tools','aapt2')]:
            if dest.exists() and any(dest.rglob(marker)):continue
            p=packages[ident]
            archive=next(a for a in p.findall('archives/archive') if a.findtext('host-os') in ('linux',None))
            complete=archive.find('complete');filename=complete.findtext('url')
            checksum=complete.findtext('checksum')
            downloaded=get(BASE+filename,TC/Path(filename).name,checksum,'sha1')
            extract(downloaded,dest)
        config={'java_home':str(home),'android_jar':str(next((TC/'platform').rglob('android.jar'))),'build_tools':str(next((TC/'build-tools').rglob('aapt2')).parent)}
    for executable in ['aapt2','d8','zipalign','apksigner']:
        if not (Path(config['build_tools'])/executable).is_file():raise RuntimeError('SDK tool missing: '+executable)
    config['java_version']=java_version.strip()
    config['sdk_platform']=35
    config['build_tools_version']='35.0.0'
    (TC/'paths.json').write_text(json.dumps(config,indent=2)+'\n')
    print(json.dumps(config,indent=2),flush=True)
if __name__=='__main__':
    try:prepare()
    except Exception as exc:raise SystemExit('Toolchain setup failed: '+str(exc))
