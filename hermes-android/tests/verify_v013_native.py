"""Real release-APK/WebView/job checks on the dedicated emulator; no physical-device access.
The localhost model is deterministic and consumes no user's model credentials or quota.
"""
from pathlib import Path
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
import datetime,hashlib,json,os,subprocess,sys,threading,time
R=Path(__file__).resolve().parents[1];D=R/'docs/android-v013';SERIAL='emulator-5554'
ADB=R/'.toolchain/android-test/platform-tools/adb'
os.environ['PATH']=str(ADB.parent)+os.pathsep+os.environ.get('PATH','')
sys.path.insert(0,str(R/'tests'))
from android_probe import build,invoke
from build_android_fixture import build as build_fixture
requests=[];lock=threading.Lock();active=0;maximum=0
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def do_GET(self):
  data=json.dumps({'data':[{'id':'pocket-native-fixture','context_length':131072}]}).encode();self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
 def do_POST(self):
  global active,maximum
  body=json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))))
  messages=body.get('messages',[]);text=' '.join(str(m.get('content','')) for m in messages if m.get('role')=='user')
  marker=next((x for x in ['PUBLIC_V013_A','PUBLIC_V013_B','PUBLIC_V013_CANCEL','PUBLIC_V013_MAIN'] if x in text),'unknown')
  with lock:
   requests.append({'marker':marker,'stream':body.get('stream'),'startedAt':time.monotonic()});active+=1;maximum=max(maximum,active)
  try:
   # Main response is independent while two workers stay in flight.
   time.sleep(.15 if marker=='PUBLIC_V013_MAIN' else 4)
   self.send_response(200);self.send_header('Content-Type','text/event-stream');self.send_header('Cache-Control','no-cache');self.end_headers()
   for delta,finish in [({'content':marker+'_DONE'},None),({},'stop')]:
    frame={'choices':[{'index':0,'delta':delta,'finish_reason':finish}]}
    self.wfile.write(('data: '+json.dumps(frame)+'\n\n').encode())
   self.wfile.write(b'data: [DONE]\n\n');self.wfile.flush();self.close_connection=True
  except (BrokenPipeError,ConnectionResetError):pass
  finally:
   with lock:active-=1
server=ThreadingHTTPServer(('127.0.0.1',0),Handler);server.daemon_threads=True
thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
def adb(*args,timeout=120):
 return subprocess.run([str(ADB),'-s',SERIAL,*args],text=True,capture_output=True,timeout=timeout,check=True).stdout.strip()
report={'testedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'serial':SERIAL,'physicalDeviceTouched':False,'api':'deterministic localhost fixture; not a live provider'}
apk=R/'dist/hermes-pocket-v0.13.apk';report['apkSha256']=hashlib.sha256(apk.read_bytes()).hexdigest()
try:
 report['install']=adb('install','-r',str(apk))
 report['probeInstall']=adb('install','-r','-t',str(build()))
 report['fixtureInstall']=adb('install','-r',str(build_fixture()))
 adb('reverse','tcp:18993','tcp:'+str(server.server_port))
 script=r'''
const checks={},details={},ids={};const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const assert=(ok,name)=>{checks[name]=!!ok;if(!ok)throw Error(name);};
async function idle(){for(let i=0;i<120&&(await native('boot')).busy;i++)await sleep(50);}
try{
 await native('stop');await idle();
 await native('saveSettings',{providerId:'custom',endpoint:'http://127.0.0.1:18993',allowLan:true,model:'pocket-native-fixture',token:'PUBLIC_FIXTURE_NOT_A_USER_SECRET'});
 await native('setCapabilities',{enabledPlugins:['device','screen','agent','web'],deviceScope:'all',approvalMode:'auto'});
 const boot=await native('boot');assert(boot.version==='0.13','release_version_013');
 assert(boot.availableTools.names.includes('delegate_task')&&boot.availableTools.names.includes('act_on_screen')&&boot.availableTools.names.includes('browser_open'),'new_native_tools_registered');
 await handleCliCommand('/help');assert(document.getElementById('resultBody').textContent.includes('/bg'),'actual_webview_cli_help');
 ids.a=(await native('startBackgroundTask',{task:'PUBLIC_V013_A'})).job_id;
 ids.b=(await native('startBackgroundTask',{task:'PUBLIC_V013_B'})).job_id;
 ids.c=(await native('startBackgroundTask',{task:'PUBLIC_V013_CANCEL'})).job_id;
 let jobs=await native('jobs');assert(jobs.jobs.find(j=>j.id===ids.c).status==='queued','third_worker_is_queued');
 await native('cancelJob',{job_id:ids.c});
 await native('startChat',{text:'PUBLIC_V013_MAIN',session:''});
 const concurrent=await native('boot');assert(concurrent.busy&&concurrent.jobs.active>=2,'foreground_chat_and_workers_coexist');
 const background=await native('background');details.background=background;assert(background.backgrounded&&background.retained,'background_retains_actual_work');
 for(let i=0;i<160;i++){jobs=await native('jobs');if([ids.a,ids.b,ids.c].every(id=>!['queued','running','cancelling'].includes(jobs.jobs.find(j=>j.id===id)?.status)))break;await sleep(100);}
 details.results={a:await native('jobResult',{job_id:ids.a}),b:await native('jobResult',{job_id:ids.b}),c:await native('jobResult',{job_id:ids.c})};
 assert(details.results.a.status==='completed'&&details.results.a.result==='PUBLIC_V013_A_DONE','first_worker_real_sse_result');
 assert(details.results.b.status==='completed'&&details.results.b.result==='PUBLIC_V013_B_DONE','second_worker_real_sse_result');
 assert(details.results.c.status==='cancelled','queued_worker_cancelled');
 assert(details.results.a.usageKnown===false,'missing_usage_not_invented');
 await idle();assert(!(await native('boot')).busy,'foreground_chat_settled');
 return {checks,details,ids};
}catch(e){return {checks,details,ids,failure:e.message};}finally{await native('stop');}
'''
 report['jobs']=invoke(script,SERIAL)
 ids=report['jobs'].get('result',{}).get('ids',{})
 if len(ids)==3:
  # Instrumentation ends the prior app process; fresh launch reads durable test jobs.
  restore='const ids='+json.dumps(ids)+''';const checks={};for(const k of ['a','b','c']){const job=await native('jobResult',{job_id:ids[k]});checks[k]=job.status===(k==='c'?'cancelled':'completed');}return {checks};'''
  report['reopenedJobs']=invoke(restore,SERIAL)
 # Independent native UI fixture, without changing actual phone permissions.
 semantic=r'''
const checks={},details={};const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const assert=(ok,name)=>{checks[name]=!!ok;if(!ok)throw Error(name);};
async function run(name,args={}){for(let i=0;i<100&&(await native('boot')).busy;i++)await sleep(50);return await native('runTool',{name,arguments:args});}
try{
 await native('setCapabilities',{enabledPlugins:['device','screen','agent','web'],deviceScope:'all',approvalMode:'auto'});
 let state=await native('device');for(let i=0;i<60&&!state.accessibility;i++){await sleep(250);state=await native('device');}assert(state.accessibility,'existing_accessibility_bound');
 details.launch=await run('launch_app',{package:'dev.hermesfixture.android'});await sleep(700);
 let before=await run('read_screen');assert(before.ok,'real_fixture_observed');
 const first=before.result.elements.find(e=>/^Counter: \d+$/.test(e.text));assert(!!first,'counter_observed_before');const count=Number(first.text.split(': ')[1]);
 details.action=await run('act_on_screen',{action:'click',expected_package:'dev.hermesfixture.android',label:'Increment counter'});
 assert(details.action.ok&&details.action.result.dispatched,'semantic_click_dispatched');
 const after=await run('read_screen');assert(after.ok&&after.result.elements.some(e=>e.text==='Counter: '+(count+1)),'actual_counter_incremented_exactly_once');
 const missing=await run('act_on_screen',{action:'click',expected_package:'dev.hermesfixture.android',label:'THIS_TARGET_DOES_NOT_EXIST'});
 assert(!missing.ok&&missing.errorCode==='TARGET_NOT_FOUND'&&missing.dispatched===false,'missing_target_never_dispatched');
 await native('setCapabilities',{deviceScope:'none'});
 let rejected=false;try{const denied=await run('act_on_screen',{action:'click',expected_package:'dev.hermesfixture.android',label:'Increment counter'});rejected=!denied.ok;}catch(e){rejected=true;}assert(rejected,'none_scope_rejects_semantic_action');
 return {checks,details};
}catch(e){return {checks,details,failure:e.message};}finally{await native('stop');}
'''
 report['semantic']=invoke(semantic,SERIAL)
except Exception as e:report['runnerFailure']=str(e)
finally:
 report['modelRequests']=requests;report['maxSimultaneousModelRequests']=maximum
 report['cancelledQueuedWorkerContactedAPI']=any(x['marker']=='PUBLIC_V013_CANCEL' for x in requests)
 report['completedAt']=datetime.datetime.now(datetime.timezone.utc).isoformat()
 groups=[report.get(name,{}) for name in ['jobs','reopenedJobs','semantic']]
 report['allPassed']=all(g.get('ok') and not g.get('result',{}).get('failure') and g.get('result',{}).get('checks') and all(g['result']['checks'].values()) for g in groups) and not report['cancelledQueuedWorkerContactedAPI']
 (D/'native-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
 try:adb('reverse','--remove','tcp:18993')
 except Exception:pass
 server.shutdown();server.server_close();thread.join(timeout=2)
 print(json.dumps(report,ensure_ascii=False,indent=2),flush=True)
raise SystemExit(0 if report['allPassed'] else 1)
