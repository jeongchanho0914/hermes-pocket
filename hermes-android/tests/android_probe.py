"""Same-signed, emulator-only instrumentation of the actual release WebView bridge.
Never included in the release APK. No release debugger is enabled.
"""
from pathlib import Path
import json, subprocess, os, shutil, zipfile, base64, argparse
ROOT=Path(__file__).resolve().parents[1]
def build():
 p=json.loads((ROOT/'.toolchain/paths.json').read_text());bt=Path(p['build_tools']);java=Path(p['java_home']);android=Path(p['android_jar']);src=ROOT/'tests/android-probe';b=ROOT/'.toolchain/android-test/probe-build'
 b.mkdir(exist_ok=True);(b/'classes').mkdir(exist_ok=True);(b/'dex').mkdir(exist_ok=True)
 env=dict(os.environ,JAVA_HOME=str(java));env['PATH']=str(java/'bin')+os.pathsep+env.get('PATH','')
 def run(*args):subprocess.run(list(map(str,args)),env=env,check=True,capture_output=True,text=True)
 run(bt/'aapt2','link','-o',b/'base.apk','-I',android,'--manifest',src/'AndroidManifest.xml')
 run(java/'bin/javac','-source','8','-target','8','-classpath',android,'-d',b/'classes',src/'PocketProbeInstrumentation.java')
 with zipfile.ZipFile(b/'classes.jar','w') as z:
  for f in (b/'classes').rglob('*.class'):z.write(f,f.relative_to(b/'classes'))
 run(bt/'d8','--lib',android,'--min-api','26','--output',b/'dex',b/'classes.jar')
 shutil.copyfile(b/'base.apk',b/'unsigned.apk')
 with zipfile.ZipFile(b/'unsigned.apk','a') as z:z.write(b/'dex/classes.dex','classes.dex',compress_type=zipfile.ZIP_DEFLATED)
 run(bt/'zipalign','-f','4',b/'unsigned.apk',b/'aligned.apk')
 run(bt/'apksigner','sign','--ks',ROOT/'.signing/development.jks','--ks-key-alias','development','--ks-pass','file:'+str(ROOT/'.signing/password'),'--out',b/'probe.apk',b/'aligned.apk')
 return b/'probe.apk'
def invoke(script,serial):
 if not serial.startswith('emulator-'):raise ValueError('Physical devices are forbidden for this test probe')
 encoded=base64.b64encode(script.encode()).decode();cmd=['adb','-s',serial,'shell','am','instrument','-w','-r','-e','script',encoded,'dev.chanho.hermes.tests/.PocketProbeInstrumentation']
 r=subprocess.run(cmd,capture_output=True,text=True,timeout=180);line=next((x for x in r.stdout.splitlines() if x.startswith('INSTRUMENTATION_RESULT: probe=')),None)
 if line is None:raise RuntimeError(r.stdout+r.stderr)
 result=json.loads(line.split('probe=',1)[1]);return result
if __name__=='__main__':
 a=argparse.ArgumentParser();a.add_argument('--serial',default='emulator-5556');a.add_argument('--build',action='store_true');a.add_argument('--script');a.add_argument('--file',type=Path);opt=a.parse_args()
 if not opt.serial.startswith('emulator-'):raise SystemExit('Emulator serial required')
 if opt.build:
  apk=build();subprocess.run(['adb','-s',opt.serial,'install','-r','-t',str(apk)],check=True)
 if opt.script or opt.file:print(json.dumps(invoke(opt.script or opt.file.read_text(),opt.serial),ensure_ascii=False,indent=2))
