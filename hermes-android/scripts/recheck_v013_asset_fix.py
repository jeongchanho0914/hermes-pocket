"""Preserve failed real-APK evidence and verify the narrowly scoped native CLI asset fix."""
from pathlib import Path
import datetime,hashlib,json,shutil,subprocess,sys
R=Path(__file__).resolve().parents[1];D=R/'docs/android-v013';old=json.loads((D/'host-regression-results.json').read_text())
source={str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((R/'app/src/main').rglob('*')) if p.is_file()}
changed=[p for p in set(source)|set(old['sourceHashes']) if source.get(p)!=old['sourceHashes'].get(p)]
if changed!=['app/src/main/java/dev/chanho/hermes/MainActivity.java']:raise SystemExit('Expected only the reviewed native asset allowlist change: '+str(changed))
archive=D/'candidate-997528785a5a';archive.mkdir(exist_ok=True)
for p in [D/'native-verification.json',D/'host-regression-results.json',R/'dist/build-info.json',R/'dist/signature-verification.txt']:
 if p.exists() and not (archive/p.name).exists():shutil.copy2(p,archive/p.name)
log=D/'native-asset-fix-contracts.log';command=[sys.executable,'tests/run_android_contract_tests.py'];started=datetime.datetime.now(datetime.timezone.utc).isoformat()
with log.open('w',encoding='utf-8') as f:r=subprocess.run(command,cwd=R,stdout=f,stderr=subprocess.STDOUT,timeout=90)
report={'startedAt':started,'changedApplicationFiles':changed,'previousHostResults':'candidate-997528785a5a/host-regression-results.json','command':command,'exitCode':r.returncode,'log':str(log.relative_to(R)),'logSha256':hashlib.sha256(log.read_bytes()).hexdigest(),'sourceHashes':source,'scope':'compile all actual Android sources against SDK, run eight production contract checks; actual WebView asset execution checked by separate native-verification.json'}
(D/'native-asset-fix-verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in report.items() if k!='sourceHashes'},indent=2))
if r.returncode:print(log.read_text())
raise SystemExit(r.returncode)
