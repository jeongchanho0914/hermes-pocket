"""Native typed public MiMo output UI; actual SSE timing is covered by production JVM tests."""
import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
import test_ui as ui
class ModelProgressUITest(unittest.TestCase):
 setUpClass=classmethod(ui.UITest.setUpClass.__func__)
 tearDownClass=classmethod(ui.UITest.tearDownClass.__func__)
 setUp=ui.UITest.setUp;tearDown=ui.UITest.tearDown
 set_configured_model=ui.UITest.set_configured_model
 def start(self):
  self.set_configured_model();self.page.evaluate("PocketNative({event:'started',data:{session:'thought-one',runId:'run-one',text:'Actual owner request'}})")
 def thought(self,text='MiMo public thought',**extra):
  value={'session':'thought-one','runId':'run-one','provider':'mimo','source':'mimo.reasoning_content','round':1,'text':text,'elapsedMs':100,'timestamp':20,**extra}
  self.page.evaluate('data=>PocketNative({event:"providerThought",data})',value)
 def progress(self,phase='thinking',**extra):
  value={'session':'thought-one','runId':'run-one','phase':phase,'round':1,'elapsedMs':100,'timestamp':20,**extra}
  self.page.evaluate('data=>PocketNative({event:"modelProgress",data})',value)
 def test_public_thought_is_visible_before_answer_and_cumulative_updates_preserve_expanded_card(self):
  self.start();self.progress();self.thought('먼저 현재 앱의 화면을 확인합니다.')
  self.assertEqual(self.page.evaluate('state.live'),'')
  self.assertRegex(self.page.locator('.provider-thought .thinking-caption').inner_text(),r'^thinking\.{1,3} \d+s$')
  self.assertEqual(self.page.evaluate('state.providerThoughts[0].source'),'mimo.reasoning_content')
  self.assertTrue(self.page.locator('.provider-thought-heading svg').count())
  self.assertFalse(self.page.locator('.provider-thought-detail').evaluate('e=>e.open'))
  self.page.locator('.provider-thought-detail summary').click()
  self.page.evaluate('window.originalThought=document.querySelector(".provider-thought")')
  latest='먼저 현재 앱의 화면을 확인합니다. 실제 결과를 확인한 뒤 답변합니다.'
  self.thought(latest,timestamp=21)
  self.assertEqual(self.page.locator('.provider-thought').count(),1)
  self.assertTrue(self.page.evaluate('originalThought===document.querySelector(".provider-thought")'))
  self.assertTrue(self.page.locator('.provider-thought-detail').evaluate('e=>e.open'))
  self.assertEqual(self.page.locator('.provider-thought-body').inner_text(),latest)
  self.page.evaluate("PocketNative({event:'complete',data:{text:'Actual public answer'}})")
  self.assertEqual(self.page.locator('.provider-thought').count(),1)
  self.assertNotIn(latest,str(self.page.evaluate('state.messages')))
  self.assertEqual(self.errors,[])
 def test_real_phase_and_elapsed_ui_advance_only_while_running_and_stop_on_final_cancel_or_failure(self):
  self.start();self.progress(reasoning_content='unsupported-private-thought',token='private-token')
  self.assertTrue(self.page.locator('#chatModelProgress').is_visible())
  self.assertRegex(self.page.locator('#chatModelProgress').inner_text(),r'^thinking\.{1,3} \d+s$')
  self.assertEqual(self.page.evaluate('state.modelProgress.phase'),'thinking')
  first=self.page.evaluate('modelProgressElapsed()');self.page.wait_for_timeout(320)
  self.assertGreater(self.page.evaluate('modelProgressElapsed()'),first+200)
  self.assertNotIn('private-',str(self.page.evaluate('state.modelProgress')))
  for phase in ('completed','failed','cancelled'):
   self.progress(phase,elapsedMs=400)
   stopped=self.page.evaluate('modelProgressElapsed()');self.page.wait_for_timeout(80)
   self.assertEqual(self.page.evaluate('modelProgressElapsed()'),stopped)
   self.assertIsNone(self.page.evaluate('modelProgressTimer'))
  self.assertEqual(self.page.locator('.provider-thought').count(),0)
  self.assertEqual(self.errors,[])
 def test_public_provider_source_session_run_and_round_are_checked_and_unknown_fields_are_discarded(self):
  self.start()
  for extra in ({'session':'other-session'},{'runId':'old-run'},{'provider':'openai'},{'source':'unsupported.reasoning_content'},{'round':0},{'round':65}):
   self.thought('must not display',**extra)
  self.assertEqual(self.page.locator('.provider-thought').count(),0)
  self.progress(session='other-session');self.progress(runId='old-run');self.progress(phase='unknown')
  self.assertIsNone(self.page.evaluate('state.modelProgress'))
  self.thought('<img src=x onerror=alert(1)> literal public MiMo output',reasoning_content='private-extra-reasoning',imageDataURL='private-extra-pixels',token='private-extra-token')
  self.assertEqual(self.page.locator('.provider-thought img,.provider-thought script').count(),0)
  self.assertNotIn('private-extra-',str(self.page.evaluate('state.providerThoughts')))
  self.assertEqual(self.errors,[])
 def test_historical_public_thoughts_reload_from_separate_session_api_without_provider_message_roles(self):
  self.page.evaluate('''()=>{window.thoughtPackets=[];NativeBridge={postMessage(raw){const p=JSON.parse(raw);thoughtPackets.push(p);let data=[];
   if(p.method==='boot')data={config:{mode:'direct',providerId:'xiaomi',endpoint:'https://api.xiaomimimo.com/v1',model:'fixture-model'},sessions:[],audit:[],device:{},busy:window.resumeBusy||false,runId:'resume-native-run',modelProgress:{session:'thought-one',runId:'resume-native-run',phase:'thinking',round:3,elapsedMs:2200,timestamp:20},session:'thought-one',version:'0.11'};
   if(p.method==='messages')data=[{role:'user',content:'Owner question',created:10},{role:'assistant',content:'Public answer',created:30}];
   if(p.method==='providerThoughts')data=[{session:'thought-one',runId:'old-native-run',provider:'mimo',source:'mimo.reasoning_content',round:2,text:'Historical documented public thought',timestamp:20,reasoning_content:'private-extra-reasoning',token:'private-extra-token'},{session:'other-session',runId:'wrong-run',provider:'mimo',source:'mimo.reasoning_content',round:1,text:'Other session text',timestamp:20},{session:'thought-one',runId:'wrong-provider',provider:'deepseek',source:'reasoning_content',round:1,text:'Unsupported private text',timestamp:20}];
   setTimeout(()=>PocketNative({id:p.id,ok:true,data}),0);
  }};boot();}''')
  self.page.wait_for_function('()=>document.querySelectorAll(".provider-thought").length===1')
  self.assertEqual(self.page.evaluate('state.providerThoughts[0].round'),2)
  self.assertFalse(self.page.locator('.provider-thought-detail').evaluate('e=>e.open'))
  self.assertEqual(self.page.locator('.provider-thought-body').text_content(),'Historical documented public thought')
  self.assertEqual(self.page.evaluate('state.messages.map(m=>m.role)'),['user','assistant'])
  self.assertEqual(self.page.evaluate('thoughtPackets.find(p=>p.method==="providerThoughts").data'),{'session':'thought-one'})
  self.assertNotIn('private-extra-',str(self.page.evaluate('state.providerThoughts')))
  self.assertNotIn('Other session text',self.page.locator('#conversation').inner_text())
  self.assertNotIn('Unsupported private text',self.page.locator('#conversation').inner_text())
  self.assertIsNone(self.page.evaluate('modelProgressTimer'))
  self.page.evaluate('window.resumeBusy=true;boot()')
  self.page.wait_for_function('()=>state.modelProgress?.phase==="thinking"')
  self.assertEqual(self.page.evaluate('state.modelProgress.round'),3)
  self.assertTrue(self.page.locator('#chatModelProgress').is_visible())
  self.assertIn('2s',self.page.locator('#chatModelProgress').inner_text())
  self.page.evaluate('window.resumeBusy=false;boot()')
  self.page.wait_for_function('()=>!state.busy && state.modelProgress===null')
  self.assertIsNone(self.page.evaluate('modelProgressTimer'))
  self.assertEqual(self.errors,[])
 def test_public_text_cap_and_portrait_details_preserve_literal_unicode_without_overflow(self):
  self.start();self.thought('한'*32100)
  self.assertEqual(len(self.page.locator('.provider-thought-body').text_content()),32000)
  self.assertIn('일부만 저장',self.page.locator('.provider-thought-limit').inner_text())
  self.page.locator('.provider-thought-detail summary').click()
  for theme in ('white','black'):
   self.page.evaluate('theme=>applyDesign(theme,false)',theme)
   for width in (320,393):
    self.page.set_viewport_size({'width':width,'height':852})
    self.assertLessEqual(self.page.evaluate('document.documentElement.scrollWidth'),width)
  self.assertEqual(self.errors,[])
 def test_all_provider_phases_show_compact_brain_and_elapsed_without_invented_public_thought(self):
  self.start();self.page.evaluate("state.config.providerId='openai';state.config.endpoint='https://api.openai.com/v1'")
  for phase in ('sending','receiving','thinking','tool_preparing','answering'):
   self.progress(phase,elapsedMs=2100,reasoning_content='private-provider-thought')
   self.assertEqual(self.page.evaluate('state.modelProgress.phase'),phase)
   self.assertRegex(self.page.locator('#chatModelProgress .thinking-caption').inner_text(),r'^thinking\.{1,3} 2s$')
   self.assertEqual(self.page.locator('#chatModelProgress svg').count(),1)
   self.assertTrue(self.page.locator('#chatModelProgress').is_visible())
  self.assertEqual(self.page.locator('.provider-thought').count(),0)
  self.assertEqual(self.page.locator('#streamingMessage .message-content').inner_text(),'')
  self.assertNotIn('private-provider-thought',self.page.locator('#conversation').inner_text())
  self.page.evaluate("PocketNative({event:'failure',data:{session:'thought-one',runId:'run-one',message:'Fixture provider failed'}})")
  self.assertIsNone(self.page.evaluate('modelProgressTimer'))
  stopped=self.page.evaluate('modelProgressElapsed()');self.page.wait_for_timeout(300)
  self.assertEqual(self.page.evaluate('modelProgressElapsed()'),stopped)
  self.assertEqual(self.errors,[])
 def test_busy_composer_stop_and_background_send_real_native_requests_without_claiming_completion(self):
  self.start();self.progress()
  self.page.evaluate('''()=>{window.controlPackets=[];NativeBridge={postMessage(raw){const p=JSON.parse(raw);controlPackets.push(p);setTimeout(()=>PocketNative({id:p.id,ok:true,data:{accepted:true}}),0)}}}''')
  self.assertEqual(self.page.locator('#sendMessage').get_attribute('data-action'),'stop')
  self.assertFalse(self.page.locator('#sendMessage').is_disabled())
  self.page.locator('#sendMessage').click();self.page.wait_for_function('()=>controlPackets.some(p=>p.method==="stop")')
  self.assertTrue(self.page.evaluate('state.busy'))
  self.page.locator('#backgroundRun').click();self.page.wait_for_function('()=>controlPackets.some(p=>p.method==="background")')
  self.assertEqual(self.page.evaluate('controlPackets.map(p=>p.method)'),['stop','background'])
  self.assertTrue(self.page.evaluate('state.busy'))
  self.assertEqual(self.page.evaluate('state.messages.filter(m=>m.role==="assistant").length'),0)
  self.page.evaluate("PocketNative({event:'failure',data:{session:'thought-one',runId:'run-one',message:'Fixture cancelled'}})")
  self.assertEqual(self.page.locator('#sendMessage').get_attribute('data-action'),'send')
  self.assertTrue(self.page.locator('#sendMessage').is_disabled())
  self.assertIsNone(self.page.evaluate('modelProgressTimer'))
  self.assertEqual(self.errors,[])
