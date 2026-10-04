"""Real browser pointer gestures with a NativeBridge double; no phone sessions touched."""
import unittest
import test_ui as ui_helpers

class SidebarGestureTest(unittest.TestCase):
    setUpClass=classmethod(ui_helpers.UITest.setUpClass.__func__)
    tearDownClass=classmethod(ui_helpers.UITest.tearDownClass.__func__)
    def tearDown(self):
        self.page.mouse.up();self.page.close()
    def setUp(self):
        ui_helpers.UITest.setUp(self)
        self.page.evaluate("""() => {
          window.gesturePackets=[];window.rejectDeletion=false;window.falseDeletion=false;
          window.gestureSessions=['left','right','keep'].map(id=>({id,title:'Conversation '+id,created:Date.now(),count:2}));
          window.confirm=()=>{throw new Error('unexpected extra confirmation');};
          NativeBridge={postMessage(raw){const p=JSON.parse(raw);gesturePackets.push(p);let data={},ok=true;
            if(p.method==='boot')data={config:{mode:'direct',providerId:'custom',endpoint:'https://fixture.example/v1',model:'fixture',allowedApps:[],deviceScope:'all',approvalMode:'ask'},sessions:gestureSessions,audit:[],device:{},busy:false,version:'0.07'};
            if(p.method==='sessions')data=gestureSessions;
            if(p.method==='messages')data=[{role:'user',content:'Saved question'},{role:'assistant',content:'Saved answer'}];
            if(p.method==='audit')data=[];
            if(p.method==='deleteSession'){
              if(rejectDeletion){ok=false;data={message:'fixture deletion rejected'};}
              else{if(!falseDeletion)gestureSessions=gestureSessions.filter(s=>s.id!==p.data.session);data={deleted:!falseDeletion,sessions:gestureSessions};}
            }
            setTimeout(()=>PocketNative({id:p.id,ok,data}),0);
          }};boot();
        }""")
        self.page.wait_for_function("() => state.config.model === 'fixture'")
        self.page.locator('#menuToggle').click()
    def row(self,sid='left'):return self.page.locator('.sidebar-session[data-session="'+sid+'"]')
    def button(self,sid='left'):return self.row(sid).locator('.sidebar-session-open')
    def start_hold(self,sid='left'):
        self.button(sid).hover()  # Wait for the real drawer transition to settle.
        box=self.button(sid).bounding_box();x=box['x']+box['width']/2;y=box['y']+box['height']/2
        self.page.mouse.move(x,y);self.page.mouse.down();return x,y
    def arm(self,sid='left'):
        x,y=self.start_hold(sid);self.page.wait_for_timeout(500)
        self.assertIn('delete-armed',self.row(sid).get_attribute('class'));return x,y
    def swipe(self,sid,direction):
        x,y=self.arm(sid);self.page.mouse.move(x+direction*120,y,steps=5);self.page.mouse.up()
    def deletions(self):return self.page.evaluate("gesturePackets.filter(p=>p.method==='deleteSession')")
    def test_short_tap_opens_and_never_deletes(self):
        self.assertEqual(self.page.locator('.sidebar-session-delete').count(),0)
        self.button().click();self.page.wait_for_function("() => state.sid === 'left'")
        self.assertIn('Saved answer',self.page.locator('#conversation').inner_text())
        self.assertEqual(self.deletions(),[]);self.assertEqual(self.errors,[])
    def test_vertical_motion_before_hold_does_not_arm_or_delete(self):
        x,y=self.start_hold();self.page.mouse.move(x,y+30,steps=4);self.page.wait_for_timeout(550)
        self.assertNotIn('delete-armed',self.row().get_attribute('class'));self.page.mouse.up()
        self.assertEqual(self.deletions(),[]);self.assertEqual(len(self.page.evaluate('state.sessions')),3)
    def test_long_hold_release_without_swipe_preserves_conversation(self):
        self.arm()
        color=self.button().evaluate('(e)=>getComputedStyle(e).color.match(/\\d+/g).map(Number)')
        self.assertGreater(color[0],color[1]);self.assertGreater(color[0],color[2])
        self.page.mouse.up()
        self.assertEqual(self.deletions(),[]);self.assertEqual(self.page.evaluate('state.sid'),'')
        self.assertNotIn('delete-armed',self.row().get_attribute('class'))
        self.assertEqual(len(self.page.evaluate('state.sessions')),3)
    def test_small_armed_swipe_does_not_delete(self):
        x,y=self.arm();self.page.mouse.move(x-25,y,steps=4);self.page.mouse.up()
        self.assertEqual(self.deletions(),[]);self.assertEqual(len(self.page.evaluate('state.sessions')),3)
        self.assertEqual(self.page.evaluate('state.sid'),'')
        self.assertEqual(self.button().evaluate('(e)=>e.style.transform'),'')

    def test_large_left_and_right_swipes_delete_exactly_once_without_prompt(self):
        for sid,direction in [('left',-1),('right',1)]:
            self.swipe(sid,direction);self.page.wait_for_function('(id)=>!state.sessions.some(s=>s.id===id)',arg=sid)
            packets=[p for p in self.deletions() if p['data']['session']==sid]
            self.assertEqual(len(packets),1);self.assertEqual(packets[0]['data'],{'session':sid,'gesture':True})
        self.assertEqual(self.page.locator('.sidebar-session').count(),1);self.assertEqual(self.errors,[])
    def test_native_error_or_false_result_restores_row_without_losing_session(self):
        for flag in ['rejectDeletion','falseDeletion']:
            self.page.evaluate('(flag)=>window[flag]=true',flag)
            count=len(self.deletions());self.swipe('left',-1)
            self.page.wait_for_function('(count)=>gesturePackets.filter(p=>p.method===\'deleteSession\').length===count+1',arg=count)
            self.page.wait_for_function("() => document.querySelector('[data-session=left]').dataset.deleting === 'false'")
            self.assertFalse(self.button().is_disabled());self.assertEqual(self.button().evaluate('(e)=>e.style.transform'),'')
            self.assertNotIn('delete-armed',self.row().get_attribute('class'));self.assertEqual(len(self.page.evaluate('state.sessions')),3)
            self.page.evaluate('(flag)=>window[flag]=false',flag)
        self.assertEqual(self.errors,[])
    def test_pointer_cancel_lost_capture_and_busy_cancel_armed_swipe(self):
        for event in ['pointercancel','lostpointercapture']:
            x,y=self.arm();self.button().dispatch_event(event,{'pointerId':1,'pointerType':'mouse'})
            self.page.mouse.move(x-120,y);self.page.mouse.up()
            self.assertNotIn('delete-armed',self.row().get_attribute('class'));self.assertEqual(self.deletions(),[])
        x,y=self.arm();self.page.evaluate("PocketNative({event:'started',data:{session:'busy',text:'question'}})")
        self.page.mouse.move(x-120,y);self.page.mouse.up();self.button().focus();self.page.keyboard.press('Delete')
        self.assertEqual(self.deletions(),[]);self.assertEqual(len(self.page.evaluate('state.sessions')),3)
    def test_keyboard_delete_uses_same_explicit_native_request(self):
        self.assertEqual(self.button().get_attribute('aria-keyshortcuts'),'Delete')
        self.button().focus();self.page.keyboard.press('Delete')
        self.page.wait_for_function("() => !state.sessions.some(s=>s.id==='left')")
        self.assertEqual([p['data'] for p in self.deletions()],[{'session':'left','gesture':True}])
        self.assertEqual(self.errors,[])

if __name__=='__main__':unittest.main()
