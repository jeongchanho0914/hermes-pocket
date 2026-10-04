"""Run actual host regression commands and save immutable evidence for the v0.13 build."""
from pathlib import Path
import datetime,hashlib,json,os,re,subprocess,sys
R=Path(__file__).resolve().parents[1]
OUT=R/'docs/android-v013';OUT.mkdir(exist_ok=True)
def hashes():return {str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((R/'app/src/main').rglob('*')) if p.is_file()}
start=hashes();env=dict(os.environ);env['PYTHONPATH']='/tmp/hermes-v008-test-deps:'+str(R/'tests')
commands=[('jvm',[sys.executable,'tests/run_jvm_tests.py']),('android-contracts',[sys.executable,'tests/run_android_contract_tests.py']),('diagnostics',[sys.executable,'tests/run_diagnostic_jvm_tests.py']),('python-ui',[sys.executable,'-m','unittest','discover','-s','tests','-p','test_*.py','-v'])]
rows=[]
for name,command in commands:
 when=datetime.datetime.now(datetime.timezone.utc).isoformat();log=OUT/(name+'-final.log')
 with log.open('w',encoding='utf-8') as f:
  result=subprocess.run(command,cwd=R,env=env,stdout=f,stderr=subprocess.STDOUT,timeout=240)
 text=log.read_text();row={'suite':name,'command':command,'startedAt':when,'exitCode':result.returncode,'log':str(log.relative_to(R)),'logSha256':hashlib.sha256(log.read_bytes()).hexdigest()}
 if name=='jvm':
  count=len(re.findall(r'^PASS ',text,re.M));m=re.search(r'MemoryDocumentsHarness: (\d+) checks passed',text)
  row['passedChecks']=count+(int(m.group(1)) if m else 0)
 elif name in ('android-contracts','diagnostics'):row['passedChecks']=len(re.findall(r'^PASS ',text,re.M))
 else:
  counts=re.findall(r'Ran (\d+) tests?',text)
  if counts:row['testsRun']=int(counts[-1])
 rows.append(row);print(json.dumps(row),flush=True)
 if result.returncode:print(text[-10000:],flush=True)
end=hashes();report={'testedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'sourceUnchanged':start==end,'allPassed':all(x['exitCode']==0 for x in rows) and start==end,'sourceHashes':end,'suites':rows,'scope':'host/JVM plus actual Android SDK compilation and real browser DOM tests; not physical-phone lifecycle or live provider proof'}
(OUT/'host-regression-results.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
raise SystemExit(0 if report['allPassed'] else 1)
