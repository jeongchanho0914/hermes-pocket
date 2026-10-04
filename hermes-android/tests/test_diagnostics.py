"""Real local diagnostic collection/review files and test execution; ADB calls are injected only."""
import contextlib,copy,io,json,shlex,subprocess,sys,tempfile,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import collect_diagnostics as collect
import diagnostics_review as review

def record(kind='caught',id='a'*64):
 return {'schema':1,'id':id,'versionCode':12,'versionName':'0.12','phase':'runtime','type':kind,'firstSeen':10,'lastSeen':20,'count':1,'exceptions':[{'class':'java.lang.IllegalStateException','frames':[{'class':'dev.chanho.hermes.Fixture','method':'run','source':'Fixture.java','line':7}]}]}
class DiagnosticsTest(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
  (self.root/'version.json').write_text(json.dumps({'major':0,'minor':12}))
  self.source=self.root/'app/src/main/fixture.py';self.source.parent.mkdir(parents=True);self.source.write_text('def ratio(n, d):\n    return n / d\n')
 def report(self,records,name='fixture.json'):
  path=self.root/'docs/diagnostics/reports'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps({'schema':1,'package':'dev.chanho.hermes','source':'app-private-diagnostics','records':records}));return path
 def verified(self):
  self.source.write_text('def ratio(n, d):\n    return None if d == 0 else n / d\n')
  command=[sys.executable,'-c',"import runpy; ratio=runpy.run_path('app/src/main/fixture.py')['ratio']; assert ratio(4,0) is None; assert ratio(4,2)==2; print('PASS actual source regression')"]
  output=review.run_verification(self.root,['app/src/main/fixture.py'],command,'docs/diagnostics/verification/fixture.json')
  return shlex.join(command),output
 def accepted(self,ids,command,evidence,resolution='fixed'):
  return review.record_review(self.root,ids,resolution,'Corrected fixture source and verified its actual behavior',['app/src/main/fixture.py'],command,[evidence])
 def test_metadata_whitelist_drops_payloads_and_redacts_credential_shaped_frames(self):
  raw=record();raw.update(message='private-user-message',headers={'Authorization':'private-token'},thought='private-thought',output='private-shell-output',imageDataURL='private-pixels')
  raw['exceptions'][0].update(message='private-error');raw['exceptions'][0]['frames'][0].update(**{'class':'sk-fixture_private_123','method':'ghp_private_token_123','output':'private-output'})
  safe=collect.sanitize_record(raw);self.assertNotIn('private',json.dumps(safe));self.assertEqual(safe['exceptions'][0]['frames'][0]['class'],'unknown');self.assertEqual(safe['severity'],'error');self.assertEqual(collect.sanitize_record(record('uncaught'))['severity'],'critical')
 def test_process_exit_provenance_is_critical_without_claiming_historical_apk_or_retaining_os_trace(self):
  raw=record('process_exit');raw.update(phase='startup',versionMeaning='observed_at_startup',exitReason=6,exitTimestamp=100,pid=77,description='private OS description',trace='private OS trace')
  safe=collect.sanitize_record(raw);self.assertEqual(safe['severity'],'critical');self.assertEqual(safe['versionMeaning'],'observed_at_startup');self.assertNotIn('exceptions',safe);self.assertNotIn('private',json.dumps(safe))
  for change in ({'exitReason':1},{'versionMeaning':'actual_crash_version'},{'pid':True},{'exitTimestamp':-1}):
   with self.assertRaises(ValueError):collect.sanitize_record({**raw,**change})
 def test_decoder_rejects_duplicate_ids_invalid_types_utf8_and_caps(self):
  good=json.dumps(record()).encode();self.assertEqual(len(collect.decode_events(good+b'\n')),1)
  for data in (good+b'\n'+good,b'\xff',b'x'*262145,b'\n'.join([good]*257)):
   with self.assertRaises((ValueError,UnicodeError)):collect.decode_events(data)
  for changes in ({'schema':2},{'count':True},{'versionCode':0},{'type':'successful'},{'phase':'Authorization Bearer secret'}):
   with self.assertRaises(ValueError):collect.sanitize_record({**record(),**changes})
 def test_browser_error_report_keeps_known_locations_and_drops_raw_javascript_payload(self):
  raw=record('browser_error');raw.update(phase='ui',code='webview_error',source='app.js',line=12,column=7,message='private-message',stack='private-stack',url='private-url')
  safe=collect.sanitize_record(raw);self.assertEqual(safe['code'],'webview_error');self.assertEqual(safe['severity'],'error');self.assertNotIn('private',json.dumps(safe));self.assertNotIn('exceptions',safe)
  for change in ({'code':'script_error'},{'source':'https://private.example'},{'line':True},{'column':10000001}):
   with self.assertRaises(ValueError):collect.sanitize_record({**raw,**change})
 def configure(self,serial=None):
  cfg=self.root/'.toolchain/diagnostics-device.json';cfg.parent.mkdir(parents=True,exist_ok=True);cfg.write_text(json.dumps({'serial':serial or collect.AUTHORIZED_SERIAL}));adb=self.root/'.toolchain/android-test/platform-tools/adb';adb.parent.mkdir(parents=True,exist_ok=True);adb.write_text('injected command fixture; never executed')
 def test_collector_reads_only_bounded_own_private_file_via_exact_injected_command_and_never_saves_stderr(self):
  self.configure();calls=[]
  def runner(command,**kwargs):calls.append((command,kwargs));return subprocess.CompletedProcess(command,0,json.dumps(record()).encode(),b'private-stderr-token')
  with contextlib.redirect_stdout(io.StringIO()):path=collect.collect(self.root,runner)
  self.assertEqual(calls[0][0][1:],['-s',collect.AUTHORIZED_SERIAL,'shell','content','read','--uri','content://dev.chanho.hermes.diagnostics/events'])
  self.assertEqual(calls[0][1]['timeout'],15);self.assertFalse(calls[0][1]['check']);self.assertNotIn('private-stderr-token',path.read_text());self.assertEqual(json.loads(path.read_text())['status'],'collected');self.assertEqual(json.loads((self.root/'docs/diagnostics/latest.json').read_text())['records'][0]['id'],'a'*64)
 def test_collection_missing_unconfigured_unauthorized_or_failed_devices_never_fabricates_success(self):
  self.assertIsNone(collect.collect(self.root,lambda *a,**k:self.fail('Unconfigured collector touched ADB')))
  self.configure('unauthorized-serial')
  with self.assertRaises(ValueError):collect.collect(self.root,lambda *a,**k:self.fail('Unauthorized collector touched ADB'))
  self.configure()
  with contextlib.redirect_stdout(io.StringIO()):path=collect.collect(self.root,lambda cmd,**k:subprocess.CompletedProcess(cmd,1,b'',b'Could not find provider: dev.chanho.hermes.diagnostics'))
  self.assertEqual(json.loads(path.read_text())['status'],'not_available')
  with self.assertRaises(RuntimeError):collect.collect(self.root,lambda cmd,**k:subprocess.CompletedProcess(cmd,1,b'',b'private secret permission denied'))
 def test_critical_preflight_requires_review_bound_to_actual_successful_test_execution_and_source_hash(self):
  report=self.report([record('uncaught')]);before=report.read_bytes()
  with self.assertRaises(review.PendingCriticalError):review.preflight(self.root)
  command,evidence=self.verified();self.accepted(['a'*64],command,evidence);self.assertEqual(review.pending_records(self.root),[])
  with contextlib.redirect_stdout(io.StringIO()):self.assertEqual(review.preflight(self.root)['criticalIds'],[])
  self.assertEqual(report.read_bytes(),before,'Review erased collected errors');manifest=json.loads((self.root/evidence).read_text());self.assertEqual(manifest['exitCode'],0);self.assertIn('PASS actual source regression',(self.root/manifest['output']['path']).read_text())
 def test_counterfeit_logs_failure_results_mismatched_commands_and_protected_or_external_source_evidence_are_rejected(self):
  self.report([record('uncaught')]);fake=self.root/'fake.log';fake.write_text('not an executed test')
  with self.assertRaises(ValueError):self.accepted(['a'*64],'python3 fake.py','fake.log')
  command,evidence=self.verified()
  for ids,cmd,result in [(['f'*64],command,evidence),(['a'*64,'a'*64],command,evidence),(['a'*64],command+' --forged',evidence)]:
   with self.assertRaises(ValueError):self.accepted(ids,cmd,result)
  with self.assertRaises(ValueError):self.accepted(['a'*64],command,evidence,'external')
  with self.assertRaises(ValueError):review.run_verification(self.root,['app/src/main/fixture.py'],[sys.executable,'-c',"print('FAIL actual failed test');raise SystemExit(2)"],'docs/diagnostics/verification/fail.json')
  failure_manifest=json.loads((self.root/'docs/diagnostics/verification/fail.json').read_text())
  with self.assertRaises(ValueError):self.accepted(['a'*64],shlex.join(failure_manifest['command']),'docs/diagnostics/verification/fail.json')
  for value in ('../outside.py','.toolchain/private.json','app/src/missing.py'):
   with self.assertRaises(ValueError):review.checked_file(self.root,value,source=True)
 def test_new_occurrence_or_changed_source_or_test_output_reopens_exact_review_without_claiming_fixed(self):
  raw=record('uncaught');self.report([raw]);command,evidence=self.verified();self.accepted(['a'*64],command,evidence);self.assertFalse(review.pending_records(self.root))
  self.source.write_text('def ratio(n, d):\n    return n / d\n');self.assertEqual(len(review.pending_records(self.root)),1)
  self.source.write_text('def ratio(n, d):\n    return None if d == 0 else n / d\n');manifest=json.loads((self.root/evidence).read_text());(self.root/manifest['output']['path']).write_text('tampered output');self.assertEqual(len(review.pending_records(self.root)),1)
  command,evidence=self.verified();self.accepted(['a'*64],command,evidence);raw.update(lastSeen=21,count=2);self.report([raw],'recurrence.json');self.assertEqual(review.pending_records(self.root)[0]['count'],2)
  with contextlib.redirect_stdout(io.StringIO()):deferred=review.preflight(self.root,defer_critical='Explicit test-only deferral retains this pending crash')
  self.assertEqual(deferred['criticalIds'],['a'*64]);self.assertEqual(len(review.pending_records(self.root)),1)
 def test_explicit_offline_collection_never_calls_adb_or_marks_cached_critical_errors_clean(self):
  cached=self.report([record('uncaught')]);original=cached.read_bytes();calls=[]
  for reason in ('', 'too short', None):
   if reason is not None:
    with self.assertRaises(ValueError):collect.collect(self.root,runner=lambda *a,**k:calls.append(a),offline_reason=reason)
  path=collect.collect(self.root,runner=lambda *a,**k:calls.append(a),offline_reason='Phone intentionally disconnected for host-only preparation')
  data=json.loads(path.read_text());self.assertEqual(calls,[]);self.assertEqual(data['status'],'offline');self.assertFalse(data['freshDiagnosticsAvailable']);self.assertFalse(data['cachedReportsReviewed']);self.assertEqual(data['records'],[])
  self.assertIn(str(cached.relative_to(self.root)),[r['path'] for r in data['cachedReports']])
  with self.assertRaises(review.PendingCriticalError):review.preflight(self.root)
  self.assertFalse(json.loads(path.read_text())['cachedReportsReviewed'])
  command,evidence=self.verified();self.accepted(['a'*64],command,evidence)
  collect.complete_offline_review(path,review.preflight(self.root))
  data=json.loads(path.read_text());self.assertTrue(data['cachedReportsReviewed']);self.assertFalse(data['freshDiagnosticsAvailable']);self.assertEqual(data['cachedCriticalIds'],[]);self.assertEqual(cached.read_bytes(),original)
