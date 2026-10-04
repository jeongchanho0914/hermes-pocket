"""Rerun the failed JVM suite; preserve initial failure and already-passed unchanged-source suites."""
from pathlib import Path
import datetime,hashlib,json,re,subprocess,sys
R=Path(__file__).resolve().parents[1];D=R/'docs/android-v013'
report_path=D/'host-regression-results.json';prior=json.loads(report_path.read_text())
current={str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((R/'app/src/main').rglob('*')) if p.is_file()}
if current!=prior['sourceHashes'] or not prior['sourceUnchanged']:raise SystemExit('Application source changed; previous suites cannot be reused.')
archive=D/'host-regression-first-attempt.json'
if not archive.exists():archive.write_text(json.dumps(prior,indent=2),encoding='utf-8')
command=[sys.executable,'tests/run_jvm_tests.py'];stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ');log=D/('jvm-rerun-'+stamp+'.log');started=datetime.datetime.now(datetime.timezone.utc).isoformat()
(D/('host-regression-before-'+stamp+'.json')).write_text(json.dumps(prior,indent=2),encoding='utf-8')
with log.open('w',encoding='utf-8') as f:result=subprocess.run(command,cwd=R,stdout=f,stderr=subprocess.STDOUT,timeout=180)
text=log.read_text();count=len(re.findall(r'^PASS ',text,re.M));m=re.search(r'MemoryDocumentsHarness: (\d+) checks passed',text)
row={'suite':'jvm','command':command,'startedAt':started,'exitCode':result.returncode,'log':str(log.relative_to(R)),'logSha256':hashlib.sha256(log.read_bytes()).hexdigest(),'passedChecks':count+(int(m.group(1)) if m else 0)}
end={str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((R/'app/src/main').rglob('*')) if p.is_file()}
prior['suites']=[row if x['suite']=='jvm' else x for x in prior['suites']]
prior['sourceUnchanged']=current==end;prior['allPassed']=all(x['exitCode']==0 for x in prior['suites']) and current==end
prior['completedAt']=datetime.datetime.now(datetime.timezone.utc).isoformat()
prior['rerunNote']='JVM inventory assertion updated for exactly one new screen tool. Initial failed run remains in host-regression-first-attempt.json and jvm-final.log. Other suites ran successfully against identical application sources; this is not one uninterrupted all-pass invocation.'
report_path.write_text(json.dumps(prior,indent=2),encoding='utf-8')
print(json.dumps({'jvm':row,'allPassed':prior['allPassed'],'sourceUnchanged':prior['sourceUnchanged']},indent=2),flush=True)
if result.returncode:print(text[-10000:],flush=True)
raise SystemExit(0 if prior['allPassed'] else 1)
