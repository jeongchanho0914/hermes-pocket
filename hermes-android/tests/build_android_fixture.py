"""Build independent accessibility fixture APK; never installs or targets devices."""
from pathlib import Path
import json,os,subprocess,zipfile,shutil
ROOT=Path(__file__).resolve().parents[1]
def build():
    cfg=json.loads((ROOT/'.toolchain/paths.json').read_text());java=Path(cfg['java_home']);bt=Path(cfg['build_tools']);android=Path(cfg['android_jar']);src=ROOT/'tests/android-fixture';out=ROOT/'.toolchain/android-test/fixture-build';out.mkdir(exist_ok=True)
    for folder in ['classes','dex']:(out/folder).mkdir(exist_ok=True)
    env=dict(os.environ,JAVA_HOME=str(java));env['PATH']=str(java/'bin')+os.pathsep+env.get('PATH','')
    def run(*args):subprocess.run(list(map(str,args)),env=env,check=True,capture_output=True,text=True)
    run(bt/'aapt2','link','-o',out/'base.apk','-I',android,'--manifest',src/'AndroidManifest.xml')
    run(java/'bin/javac','-source','8','-target','8','-classpath',android,'-d',out/'classes',*sorted((src/'src').glob('*.java')))
    with zipfile.ZipFile(out/'classes.jar','w') as z:
        for f in (out/'classes').rglob('*.class'):z.write(f,f.relative_to(out/'classes'))
    run(bt/'d8','--lib',android,'--min-api','26','--output',out/'dex',out/'classes.jar')
    shutil.copyfile(out/'base.apk',out/'unsigned.apk')
    with zipfile.ZipFile(out/'unsigned.apk','a') as z:z.write(out/'dex/classes.dex','classes.dex',compress_type=zipfile.ZIP_DEFLATED)
    run(bt/'zipalign','-f','4',out/'unsigned.apk',out/'aligned.apk')
    run(bt/'apksigner','sign','--ks',ROOT/'.signing/development.jks','--ks-key-alias','development','--ks-pass','file:'+str(ROOT/'.signing/password'),'--out',out/'fixture.apk',out/'aligned.apk')
    return out/'fixture.apk'
if __name__=='__main__':print(build())
