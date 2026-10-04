"""Actual activity-event UI contract; native session database is not simulated as proof."""
import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
import test_ui as ui

class ActivityUITest(unittest.TestCase):
    setUpClass=classmethod(ui.UITest.setUpClass.__func__)
    tearDownClass=classmethod(ui.UITest.tearDownClass.__func__)
    setUp=ui.UITest.setUp;tearDown=ui.UITest.tearDown
    set_configured_model=ui.UITest.set_configured_model
    def event(self,status,call='call-real-1',**extra):
        data={'runId':'run-real','session':'activity-one','toolCallId':call,'seq':1,'kind':'tool','name':'terminal','status':status,'summary':'Observed tool state','argsSummary':'{"command":"printf done"}','timestamp':20,**extra}
        self.page.evaluate('data=>PocketNative({event:"activity",data})',data)
    def started(self):
        self.set_configured_model();self.page.evaluate("PocketNative({event:'started',data:{session:'activity-one',text:'Owner request'}})")
    def test_actual_call_id_updates_pending_approval_complete_card_without_duplicates_or_losing_open_details(self):
        self.started();self.event('started')
        self.assertEqual(self.page.locator('.chat-activity').count(),1)
        self.page.locator('.chat-activity details summary').click()
        self.page.evaluate('window.originalActivityCard=document.querySelector(".chat-activity")')
        self.event('approval',summary='Native owner approval pending',seq=2,timestamp=21)
        self.assertEqual(self.page.locator('.chat-activity').get_attribute('data-activity-status'),'approval')
        self.assertIn('승인 대기',self.page.locator('.chat-activity-status').inner_text())
        self.event('completed',summary='Actual tool response received',argsSummary='{"command":"'+'x'*160+'"}',seq=3,timestamp=22)
        self.assertEqual(self.page.locator('.chat-activity').count(),1)
        self.assertEqual(self.page.locator('.chat-activity').get_attribute('data-activity-status'),'completed')
        self.assertTrue(self.page.evaluate('originalActivityCard===document.querySelector(".chat-activity")'))
        self.assertTrue(self.page.locator('.chat-activity details').evaluate('e=>e.open'))
        self.assertEqual(self.page.evaluate('state.activities[0].firstTimestamp'),20)
        for width in (320,393):
            self.page.set_viewport_size({'width':width,'height':740})
            self.assertLessEqual(self.page.evaluate('document.documentElement.scrollWidth'),width)
        self.assertEqual(self.errors,[])
    def test_denied_failed_cancelled_and_public_response_cards_never_display_private_event_fields(self):
        self.started()
        self.event('failed',call='denied',summary='사용자가 승인하지 않았습니다. 실행하지 않았습니다.')
        self.event('failed',call='error',summary='Actual command exited with a failure')
        self.event('cancelled',call='cancel',summary='Owner stopped the request')
        self.event('completed',call='response_round_1',kind='response',name='response',summary='<img src=x onerror=alert(1)> literal public response',reasoning_content='private-hidden-reasoning',imageDataURL='private-hidden-pixels',token='private-hidden-token',result={'output':'private-hidden-raw-output'})
        self.assertEqual(self.page.locator('.chat-activity').count(),4)
        self.assertEqual(self.page.locator('[data-activity-status="failed"]').count(),2)
        self.assertEqual(self.page.locator('[data-activity-status="cancelled"]').count(),1)
        self.assertIn('실행하지 않았습니다',self.page.locator('.chat-activity').first.inner_text())
        self.assertEqual(self.page.locator('.chat-activity img,.chat-activity script').count(),0)
        self.assertNotIn('private-hidden-',self.page.locator('#conversation').inner_text())
        self.assertNotIn('private-hidden-',str(self.page.evaluate('state.activities')))
        self.assertEqual(self.errors,[])
    def test_boot_rehydrates_separate_session_timeline_without_provider_history_roles_or_private_fields(self):
        self.page.evaluate('''()=>{
          window.testPackets=[];
          NativeBridge={postMessage(raw){const p=JSON.parse(raw);testPackets.push(p);let data=[];
            if(p.method==='boot')data={config:{mode:'direct',providerId:'custom',endpoint:'https://fixture.example/v1',model:'fixture-model'},sessions:[],audit:[],device:{},busy:false,session:'activity-one',version:'0.10'};
            if(p.method==='messages')data=[{role:'user',content:'Owner question',created:10},{role:'assistant',content:'Public answer',created:30}];
            if(p.method==='activities')data=[{runId:'native-run',session:'activity-one',toolCallId:'native-call',seq:3,firstSeq:1,kind:'tool',name:'list_apps',status:window.oldActivityStatus||'completed',summary:'Actual app list returned',argsSummary:'{}',timestamp:25,firstTimestamp:20,reasoning_content:'private-loaded-reasoning',imageDataURL:'private-loaded-pixels',token:'private-loaded-token'},
              {runId:'other-run',session:'activity-two',toolCallId:'other-call',kind:'tool',name:'terminal',status:'started',summary:'Other session activity',timestamp:15}];
            setTimeout(()=>PocketNative({id:p.id,ok:true,data}),0);
          }};boot();
        }''')
        self.page.wait_for_function('()=>document.querySelectorAll(".chat-activity").length===1')
        self.assertEqual(self.page.evaluate('state.messages.map(m=>m.role)'),['user','assistant'])
        self.assertEqual(self.page.locator('.chat-activity').get_attribute('data-activity-status'),'completed')
        self.assertNotIn('Other session activity',self.page.locator('#conversation').inner_text())
        self.assertNotIn('private-loaded-',self.page.locator('#conversation').inner_text())
        self.assertNotIn('private-loaded-',str(self.page.evaluate('state.activities')))
        self.assertEqual(self.page.evaluate('testPackets.find(p=>p.method==="activities").data'),{'session':'activity-one'})
        self.page.evaluate("window.oldActivityStatus='started';boot()")
        self.page.wait_for_function('()=>document.querySelector(".chat-activity")?.dataset.activityStatus==="started"')
        self.assertIn('실행 상태 확인 필요',self.page.locator('.chat-activity-status').inner_text())
        self.assertNotIn('진행 중',self.page.locator('.chat-activity-status').inner_text())
        self.assertEqual(self.errors,[])
    def test_other_session_events_and_late_activity_reload_cannot_overwrite_current_session(self):
        self.started();self.event('started')
        self.event('completed',call='other',session='activity-two',summary='must not cross sessions')
        self.assertEqual(self.page.locator('.chat-activity').count(),1)
        self.page.evaluate('''()=>{
            NativeBridge={postMessage(raw){window.pendingActivities=JSON.parse(raw)}};
            loadChatActivities('activity-one');state.sid='activity-two';
            state.activities=[{runId:'run-two',session:'activity-two',toolCallId:'call-two',kind:'tool',name:'list_apps',status:'completed',summary:'Current session state',timestamp:20}];
            renderConversation();PocketNative({id:pendingActivities.id,ok:true,data:[{runId:'old-run',session:'activity-one',toolCallId:'old-call',kind:'tool',name:'terminal',status:'completed',summary:'Late previous session',timestamp:21}]});
        }''')
        self.page.wait_for_timeout(40)
        self.assertEqual(self.page.evaluate('state.activities[0].session'),'activity-two')
        self.assertIn('Current session state',self.page.locator('#conversation').inner_text())
        self.assertNotIn('Late previous session',self.page.locator('#conversation').inner_text())
        self.assertEqual(self.errors,[])
