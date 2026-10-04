"""Authoritative native order contracts across streamed public text, tools and persisted reload."""
import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
import test_ui as ui
class TimelineOrderUITest(unittest.TestCase):
 setUpClass=classmethod(ui.UITest.setUpClass.__func__)
 tearDownClass=classmethod(ui.UITest.tearDownClass.__func__)
 setUp=ui.UITest.setUp;tearDown=ui.UITest.tearDown
 set_configured_model=ui.UITest.set_configured_model
 def emit(self,event,**data):self.page.evaluate('value=>PocketNative(value)',{'event':event,'data':data})
 def start(self):
  self.set_configured_model();self.emit('started',session='ordered-session',runId='native-run',messageId=1,text='Owner request',created=900000,timelineOrder=1)
 def thought(self,round,order,text=None):self.emit('providerThought',session='ordered-session',runId='native-run',provider='mimo',source='mimo.reasoning_content',round=round,text=text or ('Public thought '+str(round)),timestamp=1,elapsedMs=100,timelineOrder=order)
 def activity(self,call,order,kind='tool',status='started',**extras):self.emit('activity',**{'session':'ordered-session','runId':'native-run','toolCallId':call,'kind':kind,'name':'get_device_state' if kind=='tool' else 'assistant','status':status,'seq':1,'summary':'Observed '+status,'argsSummary':'{}','timestamp':1,'timelineOrder':order,**extras})
 def delta(self,text,order):self.emit('delta',session='ordered-session',runId='native-run',text=text,timelineOrder=order);self.page.wait_for_timeout(90)
 def orders(self):return self.page.locator('#conversation > [data-timeline-key]').evaluate_all('els=>els.map(e=>Number(conversationTimeline.find(v=>v.key===e.dataset.timelineKey)?.value.timelineOrder))')
 def test_three_actual_rounds_interleave_user_thought_public_text_tool_updates_and_final_without_duplicates(self):
  self.start();self.thought(1,2);self.delta('First public action explanation',3)
  self.assertEqual(self.orders(),[1,2,3])
  self.activity('response_1',3,kind='response',status='completed',text='First public action explanation')
  self.activity('tool_1',4);self.page.locator('.chat-activity details summary').click()
  self.page.evaluate('window.originalTool=document.querySelector(".chat-activity")')
  self.activity('tool_1',99,status='approval');self.activity('tool_1',98,status='completed')
  self.assertTrue(self.page.evaluate('originalTool===document.querySelector(".chat-activity")'))
  self.assertTrue(self.page.locator('.chat-activity details').evaluate('e=>e.open'))
  self.assertEqual(self.page.evaluate('state.activities.find(a=>a.toolCallId==="tool_1").timelineOrder'),4)
  self.thought(2,5);self.delta('Second public action explanation',6)
  self.activity('response_2',6,kind='response',status='completed',text='Second public action explanation')
  self.activity('tool_2',7);self.activity('tool_2',88,status='completed')
  self.thought(3,8);self.delta('Final public answer',9)
  self.emit('complete',session='ordered-session',runId='native-run',messageId=2,timelineOrder=9,created=2,text='Final public answer')
  self.assertEqual(self.orders(),list(range(1,10)))
  for text in ('First public action explanation','Second public action explanation','Final public answer'):
   self.assertEqual(sum(text in s for s in self.page.locator('#conversation .message-content').all_inner_texts()),1)
  self.assertEqual(self.page.evaluate('state.messages.map(m=>m.content)'),['Owner request','Final public answer'])
  self.page.evaluate('''()=>{window.savedOrderMessages=JSON.parse(JSON.stringify(state.messages));window.savedOrderActivities=JSON.parse(JSON.stringify(state.activities)).reverse();window.savedOrderThoughts=JSON.parse(JSON.stringify(state.providerThoughts)).reverse();NativeBridge={postMessage(raw){const p=JSON.parse(raw);let data=[];if(p.method==='boot')data={config:state.config,sessions:[],audit:[],device:{},busy:false,session:'ordered-session',version:'0.12'};if(p.method==='messages')data=savedOrderMessages;if(p.method==='activities')data=savedOrderActivities;if(p.method==='providerThoughts')data=savedOrderThoughts;setTimeout(()=>PocketNative({id:p.id,ok:true,data}),0)}};boot()}''')
  self.page.wait_for_function('()=>!state.busy&&state.activities.length===4&&state.providerThoughts.length===3')
  self.assertEqual(self.orders(),list(range(1,10)))
  self.assertEqual(self.errors,[])
 def test_unrelated_tool_append_preserves_selected_public_text_open_thought_details_and_reading_scroll(self):
  self.start();self.thought(1,2,'Public thought\n'*100);self.page.locator('.provider-thought-detail summary').click()
  self.delta('Stable public explanation with selectable text',3);self.activity('response_1',3,kind='response',status='completed',text='Stable public explanation with selectable text')
  self.page.evaluate('''()=>{const text=document.createTreeWalker(document.querySelector('.chat-public-response .message-content'),NodeFilter.SHOW_TEXT).nextNode();window.selectedPublicNode=text;const range=document.createRange();range.setStart(text,0);range.setEnd(text,6);window.getSelection().removeAllRanges();window.getSelection().addRange(range);followLatest=false;document.getElementById('main').scrollTop=100;window.beforeReadingScroll=document.getElementById('main').scrollTop}''')
  self.activity('tool_1',4)
  self.assertTrue(self.page.locator('.provider-thought-detail').evaluate('e=>e.open'))
  self.assertTrue(self.page.evaluate('document.querySelector(".chat-public-response .message-content").contains(selectedPublicNode)'))
  self.assertEqual(self.page.evaluate('window.getSelection().toString()'),'Stable')
  self.assertAlmostEqual(self.page.evaluate('document.getElementById("main").scrollTop'),self.page.evaluate('beforeReadingScroll'),delta=2)
  self.assertEqual(self.errors,[])
 def test_late_history_snapshot_cannot_revert_live_completed_tool_or_new_public_thought(self):
  self.start();self.activity('tool_1',2);self.thought(1,3)
  self.page.evaluate("window.oldActivityRows=JSON.parse(JSON.stringify(state.activities));window.oldThoughtRows=JSON.parse(JSON.stringify(state.providerThoughts));window.heldHistory={};NativeBridge={postMessage(raw){const p=JSON.parse(raw);heldHistory[p.method]=p}};loadChatActivities('ordered-session');undefined")
  self.activity('tool_1',99,status='completed',seq=3)
  self.emit('providerThought',session='ordered-session',runId='native-run',provider='mimo',source='mimo.reasoning_content',round=1,text='Latest public thought',timestamp=100,elapsedMs=100,timelineOrder=99)
  self.page.evaluate("PocketNative({id:heldHistory.activities.id,ok:true,data:oldActivityRows});PocketNative({id:heldHistory.providerThoughts.id,ok:true,data:oldThoughtRows})")
  self.page.wait_for_timeout(40);self.page.evaluate('renderConversation()')
  self.assertEqual(self.page.locator('.chat-activity').get_attribute('data-activity-status'),'completed')
  self.assertIn('Latest public thought',self.page.locator('.provider-thought-body').text_content())
  self.assertEqual(self.orders(),[1,2,3])
  self.assertEqual(self.errors,[])
 def test_busy_reload_places_current_round_stream_between_persisted_first_appearances_and_failure_uses_native_message_ids(self):
  self.page.evaluate('''()=>{NativeBridge={postMessage(raw){const p=JSON.parse(raw);let data=[];if(p.method==='boot')data={config:{mode:'direct',providerId:'custom',model:'fixture-model'},sessions:[],audit:[],device:{},busy:true,session:'ordered-session',runId:'native-run',live:'Current public text',liveTimelineOrder:3,version:'0.12'};if(p.method==='messages')data=[{id:1,role:'user',content:'Owner request',runId:'native-run',created:99999,timelineOrder:1}];if(p.method==='activities')data=[{runId:'native-run',session:'ordered-session',toolCallId:'parallel-tool',kind:'tool',name:'get_device_state',status:'started',summary:'Native pending',timestamp:1,timelineOrder:4}];if(p.method==='providerThoughts')data=[{session:'ordered-session',runId:'native-run',provider:'mimo',source:'mimo.reasoning_content',round:1,text:'First thought',timestamp:1,timelineOrder:2}];setTimeout(()=>PocketNative({id:p.id,ok:true,data}),0)}};boot()}''')
  self.page.wait_for_function('()=>state.busy&&state.activities.length===1&&state.providerThoughts.length===1')
  self.assertEqual(self.orders(),[1,2,3,4])
  self.emit('failure',session='ordered-session',runId='native-run',message='Actual stream failure',partialMessage={'id':2,'role':'assistant','content':'[미완료 응답]\nCurrent public text','runId':'native-run','created':1,'timelineOrder':3},errorMessage={'id':3,'role':'error','content':'Actual stream failure','runId':'native-run','created':1,'timelineOrder':5})
  self.assertEqual(self.orders(),[1,2,3,4,5])
  self.assertEqual(self.page.evaluate('state.messages.map(m=>m.id)'),[1,2,3])
  self.assertEqual(self.errors,[])
