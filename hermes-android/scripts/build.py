#!/usr/bin/env python3
"""Local no-Gradle development APK build using Google's SDK tools.

A local signing key is generated once; never include .signing in source releases.
"""
from pathlib import Path
import argparse,datetime,hashlib,json,os,secrets,shutil,struct,subprocess,zipfile,sys
from versioning import check_version
from android_dependencies import resolve_android_dependencies
from collect_diagnostics import collect as collect_diagnostics, complete_offline_review
from diagnostics_review import preflight as review_diagnostics
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument("--defer-critical-diagnostics", metavar="REASON", help="Explicit, recorded reason for a diagnostic development build; unresolved crashes remain pending")
parser.add_argument("--offline-diagnostics", metavar="REASON", help="Explicit disconnected preparation; fresh errors unavailable and cached critical-error gate still applies")
args=parser.parse_args()
ROOT=Path(__file__).resolve().parents[1]
VERSION=check_version(ROOT)
# Read the authorized phone's app-owned, sanitized failures before compilation.
# An initial older APK can have no diagnostics file; never call that a clean run.
diagnostics_report=collect_diagnostics(ROOT,offline_reason=args.offline_diagnostics)
diagnostics_preflight=review_diagnostics(ROOT,args.defer_critical_diagnostics)
diagnostics_collection=complete_offline_review(diagnostics_report,diagnostics_preflight) if diagnostics_report else {}
toolchain=ROOT/'.toolchain/paths.json'
if not toolchain.is_file():
    raise SystemExit('Run scripts/bootstrap_build.py first, or start scripts/build_gui.py.')
config=json.loads(toolchain.read_text())
JAVA=Path(config['java_home']);BT=Path(config['build_tools']);ANDROID=Path(config['android_jar'])
SRC=ROOT/'app/src/main';BUILD=ROOT/'build';DIST=ROOT/'dist'
BUILD.mkdir(exist_ok=True);DIST.mkdir(exist_ok=True)
LOG=DIST/'build-log.txt'
LOG.write_text('Hermes Pocket '+VERSION.display_version+' '+VERSION.channel+' build\n',encoding='utf-8')
def source_hashes():
    return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(SRC.rglob('*')) if p.is_file()}
original_sources=source_hashes()
dependency_jars,dependency_records=resolve_android_dependencies(ROOT)
for sub in ['gen','classes','dex']:
    folder=BUILD/sub
    if folder.exists():shutil.rmtree(folder)
    folder.mkdir()
ENV={**os.environ,'JAVA_HOME':str(JAVA),'PATH':str(JAVA/'bin')+os.pathsep+os.environ['PATH']}
def run(*argv):
    args=[str(x) for x in argv]
    print('+',args[0],args[1] if len(args)>1 else '',flush=True)
    with LOG.open('a',encoding='utf-8') as log:
        log.write('Command: '+args[0]+' '+(args[1] if len(args)>1 else '')+'\n');log.flush()
        result=subprocess.run(args,cwd=ROOT,env=ENV,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
        log.write(result.stdout);log.flush()
    if result.stdout:print(result.stdout,end='',flush=True)
    result.check_returncode()
run(BT/'aapt2','compile','--dir',SRC/'res','-o',BUILD/'resources.zip')
run(BT/'aapt2','link','-o',BUILD/'unsigned.apk','-I',ANDROID,'--manifest',SRC/'AndroidManifest.xml','--java',BUILD/'gen','-A',SRC/'assets','--min-sdk-version','26','--target-sdk-version','35','--version-code',VERSION.version_code,'--version-name',VERSION.version_name,BUILD/'resources.zip')
sources=sorted((SRC/'java').rglob('*.java'))+sorted((BUILD/'gen').rglob('*.java'))
# Use the complete JDK's compiler module; --release 8 also requires lib/ct.sym.
classpath=os.pathsep.join(str(path) for path in [ANDROID,*dependency_jars])
run(JAVA/'bin/java','-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','--release','8','-classpath',classpath,'-d',BUILD/'classes',*sources)
with zipfile.ZipFile(BUILD/'classes.jar','w',zipfile.ZIP_DEFLATED) as jar:
    for path in sorted((BUILD/'classes').rglob('*.class')):jar.write(path,path.relative_to(BUILD/'classes'))
run(BT/'d8','--lib',ANDROID,'--min-api','26','--output',BUILD/'dex',BUILD/'classes.jar',*dependency_jars)
with zipfile.ZipFile(BUILD/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as apk:
    for path in (BUILD/'dex').glob('*.dex'):apk.write(path,path.name)
run(BT/'zipalign','-f','-p','4',BUILD/'unsigned.apk',BUILD/'aligned.apk')
SIGN=ROOT/'.signing';SIGN.mkdir(mode=0o700,exist_ok=True)
SIGN.chmod(0o700)
password=SIGN/'password';key=SIGN/'development.jks'
if not key.exists():
    password.write_text(secrets.token_urlsafe(36));password.chmod(0o600)
    run(JAVA/'bin/keytool','-genkeypair','-noprompt','-keystore',key,'-storepass:file',password,'-keypass:file',password,'-alias','development','-keyalg','RSA','-keysize','3072','-validity','3650','-dname','CN=Hermes Pocket Development, OU=Unpublished Development Build')
    key.chmod(0o600)
if not password.is_file():raise RuntimeError('Signing key password file is missing; retain the original key and password for upgrades.')
key.chmod(0o600);password.chmod(0o600)
output=DIST/VERSION.apk_name
# A self-contained sideload APK: retain legacy JAR signatures for file inspectors,
# with v2/v3 providing platform integrity. No separate incremental-install idsig.
run(BT/'apksigner','sign','--ks',key,'--ks-key-alias','development','--ks-pass','file:'+str(password),'--min-sdk-version','26','--v1-signing-enabled','true','--v2-signing-enabled','true','--v3-signing-enabled','true','--v4-signing-enabled','false','--out',output,BUILD/'aligned.apk')
output.with_suffix(output.suffix+'.idsig').unlink(missing_ok=True)
run(BT/'zipalign','-c','-v','4',output)
verification=subprocess.run([str(BT/'apksigner'),'verify','--min-sdk-version','26','--verbose','--print-certs',str(output)],env=ENV,check=True,text=True,capture_output=True).stdout
# API 26+ prefers v2, so its report intentionally does not evaluate v1. Check
# the embedded JAR signature separately; this does not lower app minSdkVersion.
jar_verification=subprocess.run([str(BT/'apksigner'),'verify','--min-sdk-version','23','--verbose',str(output)],env=ENV,check=True,text=True,capture_output=True).stdout
if 'Verified using v1 scheme (JAR signing): true' not in jar_verification:raise RuntimeError('Missing or invalid embedded JAR signature')
(DIST/'legacy-signature-verification.txt').write_text('Embedded JAR signature inspection only; app still requires Android API 26+.\n'+jar_verification)
(DIST/'signature-verification.txt').write_text(verification)
with LOG.open('a',encoding='utf-8') as log:log.write(verification)
badging=subprocess.check_output([str(BT/'aapt2'),'dump','badging',str(output)],env=ENV,text=True)
(DIST/'apk-manifest.txt').write_text(badging,encoding='utf-8')
expected="package: name='dev.chanho.hermes' versionCode='"+str(VERSION.version_code)+"' versionName='"+VERSION.version_name+"'"
if not badging.startswith(expected):raise RuntimeError('Packaged APK version/package mismatch')
with zipfile.ZipFile(output) as apk:
    if apk.testzip() is not None:raise RuntimeError('Corrupt APK ZIP member')
    if len(apk.namelist())!=len(set(apk.namelist())):raise RuntimeError('Duplicate APK ZIP entries')
    for required in ['classes.dex','AndroidManifest.xml','resources.arsc','assets/index.html']:
        if required not in apk.namelist():raise RuntimeError('APK missing '+required)
    # Required for targetSdk >= 30; zipalign alone does not reject a compressed
    # resource table because it only checks uncompressed ZIP entries.
    resources=apk.getinfo('resources.arsc')
    if resources.compress_type!=zipfile.ZIP_STORED:raise RuntimeError('resources.arsc must be uncompressed for Android 11+')
    with output.open('rb') as raw:
        raw.seek(resources.header_offset+26)
        name_length,extra_length=struct.unpack('<HH',raw.read(4))
    resource_offset=resources.header_offset+30+name_length+extra_length
    if resource_offset%4:raise RuntimeError('resources.arsc must be 4-byte aligned for Android 11+')
if original_sources!=source_hashes():raise RuntimeError('App sources changed during compilation; rebuild after edits finish.')
metadata={'file':output.name,'bytes':output.stat().st_size,'sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'package':'dev.chanho.hermes','version':VERSION.version_name,'versionCode':VERSION.version_code,'displayVersion':VERSION.display_version,'channel':VERSION.channel,'builtAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'sourceHashes':original_sources,'toolchain':config,'minSdk':26,'targetSdk':35,'signing':'local development key; not Play Store release','signatureSchemes':['v1','v2','v3'],'signatureVerification':'signature-verification.txt','alignmentVerified':True,'manifestVerified':True,'resourceTableUncompressed':True,'resourceTableDataOffset':resource_offset}
metadata['androidDependencies']=dependency_records
metadata['diagnostics']={**diagnostics_preflight,'collectedReport':str(diagnostics_report.relative_to(ROOT)) if diagnostics_report else None,
    'collectionStatus':diagnostics_collection.get('status','not_configured'),
    'freshDiagnosticsAvailable':diagnostics_collection.get('status')=='collected',
    'offlineReason':diagnostics_collection.get('offlineReason'),
    'cachedReportsReviewed':diagnostics_collection.get('cachedReportsReviewed'),
    'cachedReports':diagnostics_collection.get('cachedReports',[])}
download_verification=ROOT/'.toolchain/download-verification.json'
if download_verification.is_file():metadata['toolchainDownloads']=json.loads(download_verification.read_text())
(DIST/'build-info.json').write_text(json.dumps(metadata,indent=2))
(DIST/(output.name+'.sha256')).write_text(metadata['sha256']+'  '+output.name+'\n')
print(verification);print(json.dumps({k:v for k,v in metadata.items() if k not in ('sourceHashes','toolchain')},indent=2))
