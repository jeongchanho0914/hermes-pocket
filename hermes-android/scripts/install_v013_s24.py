"""Serial-bound, data-preserving v0.13 update with actual installed-code verification.
Never uninstalls/clears data or reads the production database, preferences or credentials.
"""
from pathlib import Path
import datetime,hashlib,json,re,subprocess,time
R=Path(__file__).resolve().parents[1];D=R/'docs/android-v013';ADB=R/'.toolchain/android-test/platform-tools/adb';SERIAL='R3CX20QTRPD';PKG='dev.chanho.hermes'
info=json.loads((R/'dist/build-info.json').read_text());apk=R/'dist'/info['file']
expected='ce76dfbf9835b895e010ca46b02994676e58da03bf58526f26f9574e4ea342ad'
if info['versionCode']!=13 or info['version']!='0.13' or info['sha256']!=expected or hashlib.sha256(apk.read_bytes()).hexdigest()!=expected:raise SystemExit('Release identity changed; refusing installation.')
for name,digest in info['sourceHashes'].items():
 if hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:raise SystemExit('Source changed after build: '+name)
native=json.loads((D/'native-verification.json').read_text())
if native['apkSha256']!=expected:raise SystemExit('Emulator verified a different artifact.')
for group in ['jobs','reopenedJobs']:
 result=native.get(group,{})
 if not result.get('ok') or result.get('result',{}).get('failure') or not all(result['result']['checks'].values()):raise SystemExit('Required native job/CLI checks did not pass.')
if native.get('cancelledQueuedWorkerContactedAPI') is not False:raise SystemExit('Queued cancellation did not stop API dispatch.')
signature=(R/'dist/signature-verification.txt').read_text()
certificate='6d997f21e5f0c15e3756f5cc2b792262b40ed6d9c6e688f79ab7081a1c6d8e7c'
if certificate not in signature:raise SystemExit('Signing identity changed.')
def adb(*args,timeout=120,check=True):
 return subprocess.run([str(ADB),'-s',SERIAL,*args],capture_output=True,text=True,timeout=timeout,check=check)
report={'installedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'serial':SERIAL,'package':PKG,'version':'0.13','versionCode':13,'apkSha256':expected,'apkBytes':apk.stat().st_size,'certificateSha256':certificate,'mode':'adb install -r','uninstallOrDataClear':False,'privateDataExtracted':False,'emulatorChecks':'native-verification.json','knownVerificationGap':'Emulator accessibility service was not bound; semantic click dispatch and live S24/default-browser/remote-provider end-to-end behavior are not established by this installation.'}
try:
 if adb('get-state').stdout.strip()!='device':raise RuntimeError('Authorized USB target not ready.')
 report['deviceModel']=adb('shell','getprop','ro.product.model').stdout.strip()
 if report['deviceModel'] not in ['SM-S928N','SM_S928N']:raise RuntimeError('Unexpected physical target model.')
 report['installOutput']=adb('install','-r',str(apk)).stdout.strip()
 if 'Success' not in report['installOutput']:raise RuntimeError('Android did not confirm update success.')
 paths=adb('shell','pm','path',PKG).stdout.splitlines();bases=[x[len('package:'):].strip() for x in paths if x.startswith('package:') and x.endswith('/base.apk')]
 if len(bases)!=1:raise RuntimeError('Could not identify exactly one installed base APK.')
 installed=D/'s24-installed-v013.apk';adb('pull',bases[0],str(installed))
 report['installedApkSha256']=hashlib.sha256(installed.read_bytes()).hexdigest();report['installedApkBytes']=installed.stat().st_size
 if report['installedApkSha256']!=expected or report['installedApkBytes']!=apk.stat().st_size:raise RuntimeError('Installed artifact does not match the tested build.')
 package=adb('shell','dumpsys','package',PKG).stdout
 report['versionEvidence']=[x.strip() for x in package.splitlines() if re.search(r'\b(versionCode=|versionName=|firstInstallTime=|lastUpdateTime=)',x)]
 if not re.search(r'\bversionCode=13\b',package) or not re.search(r'\bversionName=0\.13\b',package):raise RuntimeError('Installed version mismatch.')
 report['launchOutput']=adb('shell','am','start','-W','-n',PKG+'/.MainActivity').stdout.strip()
 time.sleep(2)
 process=adb('shell','pidof',PKG,check=False).stdout.strip();report['processIds']=process
 report['processAlive']=bool(process);report['installVerified']=True
 if not process:raise RuntimeError('App process not observed after launch.')
except Exception as error:
 report['failure']=str(error);report['installVerified']=False
finally:
 report['completedAt']=datetime.datetime.now(datetime.timezone.utc).isoformat();(D/'s24-install.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
 print(json.dumps(report,ensure_ascii=False,indent=2),flush=True)
raise SystemExit(0 if report.get('installVerified') and report.get('processAlive') else 1)
