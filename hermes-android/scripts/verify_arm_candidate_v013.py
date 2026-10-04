"""Run the already-built, isolated genuine-engine candidate on the authorized S24.
Reads only the test package's own fixture report, never the production app's private state.
"""
from pathlib import Path
import subprocess,hashlib,json,time,datetime
R=Path(__file__).resolve().parents[1]
adb=R/'.toolchain/android-test/platform-tools/adb'
serial='R3CX20QTRPD'
package='dev.chanho.hermes.enginecandidate.fullwebp'
apk=R/'engine-spike/candidates/full-webp/app/build/outputs/apk/engine/debug/app-engine-debug.apk'
expected='43e6a835a7755cde5ec1c0d0df7feefd32c073550853ab8c048dbda755764693'
sha=hashlib.sha256(apk.read_bytes()).hexdigest()
if sha!=expected:raise SystemExit('Candidate changed; inspect before installing.')
def call(*args,timeout=120,check=True):
 return subprocess.run([str(adb),'-s',serial,*args],text=True,capture_output=True,timeout=timeout,check=check)
report={'testedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'serial':serial,'testPackage':package,'apkSha256':sha,'productionPrivateDataRead':False}
report['pageSize']=call('shell','getconf','PAGE_SIZE').stdout.strip()
report['abis']=call('shell','getprop','ro.product.cpu.abilist').stdout.strip()
report['install']=call('install','-r',str(apk),timeout=180).stdout.strip()
call('shell','am','force-stop',package,check=False)
prior=call('shell','run-as',package,'cat','files/probe-result.json',check=False)
if prior.returncode==0:
 (R/'docs/android-v013/s24-arm-candidate.previous.json').write_text(prior.stdout,encoding='utf-8')
 call('shell','run-as',package,'rm','files/probe-result.json')
report['launch']=call('shell','am','start','-n',package+'/dev.chanho.hermes.engineprobe.MainActivity').stdout.strip()
deadline=time.monotonic()+110
while time.monotonic()<deadline:
 out=call('shell','run-as',package,'cat','files/probe-result.json',check=False,timeout=10)
 if out.returncode==0:
  report['probe']=json.loads(out.stdout);break
 time.sleep(1)
else:
 report['probeStatus']='report_not_produced_within_110_seconds'
 # Only a dedicated test application's diagnostic tag, not production chats/logs.
 report['probeLog']=call('logcat','-d','-s','HermesEngineProbe:I','*:S',check=False,timeout=10).stdout[-14000:]
p=R/'docs/android-v013/s24-arm-candidate.json'
p.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))
# This test has a defined end; leave no test server or worker running.
call('shell','am','force-stop',package,check=False)
