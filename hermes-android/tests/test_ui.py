"""GUI smoke tests in Chromium. This does not emulate Android native APIs."""
import os,shutil,sys,unittest
from pathlib import Path
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"scripts"))
from make_preview import bundle
class UITest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.html=bundle()
        cls.pw=sync_playwright().start();cls.browser=cls.pw.chromium.launch(executable_path=os.environ.get('CHROMIUM_PATH') or shutil.which('chromium') or '/snap/bin/chromium',headless=True,args=['--no-sandbox'])
    @classmethod
    def tearDownClass(cls):
        cls.browser.close();cls.pw.stop()
    def setUp(self):
        self.page=self.browser.new_page(viewport={'width':393,'height':852},device_scale_factor=2)
        self.errors=[];self.page.on('pageerror',lambda e:self.errors.append(str(e)))
        self.page.set_content(self.html);self.page.wait_for_timeout(80)
        if self.page.locator('#setupDialog').is_visible():
            self.page.locator('#setupLater').click()
    def tearDown(self):self.page.close()
    def set_configured_model(self):
        # UI fixture only: does not connect a provider or store a real API key.
        self.page.evaluate("hydrateConfig({mode:'direct',providerId:'custom',endpoint:'https://fixture.example/v1',model:'fixture-model',reasoningEffort:'auto',allowedApps:[]})")

    def open_provider_details(self):
        details=self.page.locator('#providerSettingsDetails')
        if not details.evaluate('(e)=>e.open'):
            details.locator('summary').first.click()

    def navigate(self, tab):
        if tab == 'chat':
            for _ in range(8):
                if self.page.evaluate('state.page') == 'chat':break
                self.page.evaluate('PocketBack()')
            return
        if self.page.evaluate('state.returnToSettings'):
            self.page.locator('#menuToggle').click()
        if self.page.evaluate('state.page') != 'settings':
            menu=self.page.locator('#menuToggle')
            if menu.get_attribute('aria-expanded') != 'true':menu.click()
            self.page.locator('#sidebar [data-page="settings"]').click()
        for _ in range(5):
            if not self.page.locator('#settingsDetail').is_visible():break
            self.page.locator('#menuToggle').click()
        if tab == 'device':
            self.page.locator('#settingsToolsHome').click()
            self.page.locator('#settingsTools').click()
            self.page.locator('#manualDeviceDetails').evaluate('(e)=>e.open=true')
        elif tab == 'permissions':
            self.page.locator('#settingsPermissionsHome').click()
        elif tab == 'history':
            self.page.locator('#settingsAbout').click()
            self.page.locator('#settingsHistory').click()
        elif tab == 'memory':
            self.page.locator('#settingsMemory').click()
            self.page.locator('#memoryEditor').evaluate('e=>e.closest("details").open=true')

    def test_browser_preview_is_honest(self):
        self.assertIn('브라우저',self.page.locator('#appVersion').text_content())
        self.assertEqual(self.page.locator('.composer-caption,#previewNotice').count(),0)
        self.navigate('device')
        self.page.locator('#refreshDevice').click()
        self.assertIn('브라우저 미리보기',self.page.locator('#toast').inner_text())
        self.assertEqual(self.errors,[])
    def test_five_tabs_render_without_overflow(self):
        for width in (360,393,720):
            self.page.set_viewport_size({'width':width,'height':852})
            for tab in ('chat','device','history','memory','settings'):
                self.navigate(tab)
                self.assertTrue(self.page.locator('#page-'+tab).is_visible())
                self.assertLessEqual(self.page.evaluate('document.documentElement.scrollWidth'),width)
        self.assertEqual(self.errors,[])
    def test_standalone_model_api_setup_has_no_engine_selector(self):
        self.navigate('settings')
        self.page.locator('#settingsConnection').click()
        self.open_provider_details()
        self.assertEqual(self.page.locator('[data-mode]').count(),0)
        self.assertTrue(self.page.locator('#providerSelect').is_visible())
        self.assertFalse(self.page.locator('#providerFields').is_visible())
        self.assertIn('API',self.page.locator('#modeInfo').inner_text())
        self.assertEqual(self.page.evaluate('state.config.mode'),'direct')
        self.assertIn('암호화',self.page.locator('#token').get_attribute('placeholder'))
        self.assertNotIn('Hermes 엔진',self.page.locator('#page-settings').inner_text())
        self.assertEqual(self.errors,[])

    def test_model_api_credentials_save_and_connection_use_direct_mode(self):
        self.page.evaluate("""() => {
            window.testPackets=[];
            window.testConfig={mode:'direct',endpoint:'https://old.example/v1',model:'old-model',allowedApps:[],memory:'',maxRounds:24,contextChars:60000};
            NativeBridge={postMessage(raw){
                const packet=JSON.parse(raw);testPackets.push(packet);let data={};
                if(packet.method==='boot') data={config:testConfig,sessions:[],audit:[],device:{},busy:false,version:'0.01',releaseChannel:'beta'};
                if(packet.method==='saveSettings') {testConfig={...testConfig,...packet.data,hasToken:!!packet.data.token};delete testConfig.token;data=testConfig;}
                if(packet.method==='testConnection') data={mode:'direct',data:{ok:true}};
                setTimeout(()=>PocketNative({id:packet.id,ok:true,data}),0);
            }};
            boot();
        }""")
        self.page.wait_for_function('() => state.config.model === "old-model"')
        self.navigate('settings')
        self.page.locator('#settingsConnection').click()
        self.open_provider_details()
        self.page.locator('#manualModelDetails').evaluate('(e)=>e.open=true')
        self.page.locator('#endpoint').fill('https://model.example/v1')
        self.page.locator('#model').fill('chosen-model')
        self.page.locator('#token').fill('test-only-api-key')
        self.page.locator('#settingsForm [type=submit]').click()
        self.page.wait_for_function('() => state.config.model === "chosen-model"')
        packet=self.page.evaluate("testPackets.find(p=>p.method==='saveSettings')")
        self.assertEqual(packet['data']['mode'],'direct')
        self.assertEqual(packet['data']['endpoint'],'https://model.example/v1')
        self.assertEqual(packet['data']['model'],'chosen-model')
        self.assertEqual(packet['data']['token'],'test-only-api-key')
        self.assertNotIn('maxRounds',packet['data'])
        self.assertNotIn('contextChars',packet['data'])
        self.assertEqual(self.page.locator('#maxRounds,#contextChars').count(),0)
        self.assertIn('0.01',self.page.locator('#appVersion').inner_text())
        self.assertEqual(self.page.locator('#token').input_value(),'')
        self.assertTrue(self.page.locator('#modelSheet').is_visible())
        self.page.locator('#closeModels').click()
        self.open_provider_details()
        self.assertIn('암호화',self.page.locator('#tokenStatus').inner_text())
        self.page.locator('#testConnection').click()
        self.page.wait_for_function('() => state.connected === true')
        self.assertEqual(self.page.evaluate("testPackets.filter(p=>p.method==='testConnection').length"),1)
        self.assertNotIn('엔진 미준비',self.page.locator('#connectionResult').inner_text())
        self.assertEqual(self.errors,[])

    def test_model_text_is_not_html(self):
        self.page.evaluate('''() => {window.PocketNative({event:'started',data:{session:'test',text:'Hi'}});window.PocketNative({event:'complete',data:{text:'<img src=x onerror=alert(1)><script>window.bad=true</script>'}});}''')
        self.assertEqual(self.page.locator('.message-content img').count(),0)
        self.assertIsNone(self.page.evaluate('window.bad'))
        self.assertIn('<img',self.page.locator('.message.assistant .message-content').inner_text())
    def test_stream_and_stop_state(self):
        self.set_configured_model()
        self.page.evaluate("window.PocketNative({event:'started',data:{session:'test',text:'example'}})")
        self.assertTrue(self.page.locator('#sendMessage').is_visible())
        self.assertFalse(self.page.locator('#sendMessage').is_disabled())
        self.assertEqual(self.page.locator('#sendMessage').get_attribute('data-action'),'stop')
        self.page.evaluate("window.PocketNative({event:'delta',data:{text:'test-only stream'}})")
        self.page.wait_for_function("() => document.getElementById('streamingMessage').textContent.includes('test-only stream')")
        self.assertIn('test-only stream',self.page.locator('#streamingMessage').inner_text())
        self.page.evaluate("window.PocketNative({event:'complete',data:{text:'test-only complete'}})")
        self.assertFalse(self.page.locator('#messageInput').is_disabled())
        self.page.locator('#messageInput').fill('다음 질문')
        self.assertFalse(self.page.locator('#sendMessage').is_disabled())
        self.assertEqual(self.page.locator('#streamingMessage').count(),0)
    def test_failed_stream_preserves_partial_answer_as_incomplete(self):
        self.set_configured_model()
        self.page.evaluate("""() => {
            PocketNative({event:'started',data:{session:'failed-stream',text:'Question'}});
            PocketNative({event:'delta',data:{text:'Partial answer before interruption'}});
            PocketNative({event:'failure',data:{message:'Stream disconnected'}});
        }""")
        answer=self.page.locator('.message.assistant .message-content').last.inner_text()
        self.assertIn('Partial answer before interruption',answer)
        self.assertIn('미완료 응답',answer)
        self.assertIn('실행 완료를 의미하지 않습니다',answer)
        self.assertIn('Stream disconnected',self.page.locator('.message.error').last.inner_text())
        self.assertEqual(self.page.locator('#streamingMessage').count(),0)
        self.assertFalse(self.page.locator('#messageInput').is_disabled())
        self.page.locator('#messageInput').fill('Continue after interruption')
        self.assertFalse(self.page.locator('#sendMessage').is_disabled())
        self.assertEqual(self.errors,[])
    def test_tool_catalog_covers_native_phone_loop(self):
        self.navigate('memory')
        self.assertEqual(self.page.locator('.tool-item').count(),self.page.evaluate('Object.keys(toolNames).length'))
        for name in ['press_back','press_home','tap_screen','swipe_screen']:
            self.assertTrue(self.page.evaluate('(name)=>!!toolNames[name]',name))
        self.assertEqual(self.errors,[])
    def test_mobile_drawer_keyboard_scrim_and_wide_mobile_preview(self):
        self.assertTrue(self.page.locator('#sidebar').evaluate('(e) => e.inert'))
        self.page.locator('#menuToggle').click()
        self.assertEqual(self.page.locator('#menuToggle').get_attribute('aria-expanded'), 'true')
        self.assertEqual(self.page.evaluate('document.activeElement.id'), 'closeSidebar')
        self.page.locator('#closeSidebar').press('Shift+Tab')
        self.assertEqual(self.page.evaluate('document.activeElement.dataset.page'), 'settings')
        self.page.keyboard.press('Tab')
        self.assertEqual(self.page.evaluate('document.activeElement.id'), 'closeSidebar')
        self.page.keyboard.press('Escape')
        self.assertEqual(self.page.locator('#menuToggle').get_attribute('aria-expanded'), 'false')
        self.assertEqual(self.page.evaluate('document.activeElement.id'), 'menuToggle')
        self.page.locator('#menuToggle').click()
        self.page.locator('#sidebarScrim').click(position={'x':380, 'y':400})
        self.assertTrue(self.page.locator('#sidebar').evaluate('(e) => e.inert'))
        self.page.set_viewport_size({'width':1100,'height':852})
        self.assertTrue(self.page.locator('#menuToggle').is_visible())
        self.assertTrue(self.page.locator('#sidebar').evaluate('(e) => e.inert'))
        self.navigate('settings')
        self.page.locator('#settingsConnection').click()
        self.open_provider_details()
        self.assertTrue(self.page.locator('#page-settings').is_visible())
        self.assertEqual(self.errors, [])

    def test_new_chat_clears_conversation_and_disables_blank_send(self):
        self.set_configured_model()
        self.page.evaluate("""() => {
            PocketNative({event:'started',data:{session:'previous',text:'Old question'}});
            PocketNative({event:'complete',data:{text:'Old answer'}});
        }""")
        self.page.locator('#messageInput').fill('Unsent draft')
        self.page.locator('#headerNewChat').click()
        self.assertEqual(self.page.evaluate('state.sid'), '')
        self.assertEqual(self.page.locator('#conversation .message').count(), 0)
        self.assertTrue(self.page.locator('#emptyChat').is_visible())
        self.assertEqual(self.page.locator('#messageInput').input_value(), '')
        self.assertTrue(self.page.locator('#sendMessage').is_disabled())
        self.assertNotEqual(self.page.evaluate('document.activeElement.id'), 'messageInput')

    def test_stream_preserves_reading_position_and_jump_returns_to_latest(self):
        self.page.evaluate("""() => {
            PocketNative({event:'started',data:{session:'long',text:'Question'}});
            PocketNative({event:'delta',data:{text:('Long answer\\n').repeat(300)}});
        }""")
        self.page.wait_for_function("() => document.getElementById('streamingMessage').textContent.includes('Long answer')")
        self.page.wait_for_function("() => {const e=document.getElementById('main');return e.scrollHeight-e.scrollTop-e.clientHeight <= 2;}")
        self.page.evaluate("document.getElementById('main').scrollTo({top:150,behavior:'instant'})")
        self.page.wait_for_function("() => Math.abs(document.getElementById('main').scrollTop-150) <= 1")
        self.page.wait_for_timeout(40)
        before=self.page.locator('#main').evaluate('(e) => e.scrollTop')
        self.page.evaluate("PocketNative({event:'delta',data:{text:'More incoming text'}})")
        self.page.wait_for_function("() => document.getElementById('streamingMessage').textContent.includes('More incoming text')")
        self.assertAlmostEqual(self.page.locator('#main').evaluate('(e) => e.scrollTop'), before, delta=2)
        self.assertTrue(self.page.locator('#scrollToLatest').is_visible())
        self.page.locator('#scrollToLatest').click()
        self.page.wait_for_function("() => {const e=document.getElementById('main');return e.scrollHeight-e.scrollTop-e.clientHeight <= 2;}")
        remaining=self.page.locator('#main').evaluate('(e) => e.scrollHeight-e.scrollTop-e.clientHeight')
        self.assertLessEqual(remaining,2)

    def test_rapid_stream_completion_flushes_final_text_without_delayed_overwrite(self):
        self.set_configured_model()
        self.page.evaluate("""() => {PocketNative({event:'started',data:{session:'burst',text:'question'}});
          for(let i=0;i<100;i++)PocketNative({event:'delta',data:{text:'chunk'+i+' '}});
          PocketNative({event:'complete',data:{text:'FINAL authoritative answer'}});
        }""")
        self.assertEqual(self.page.locator('.message.assistant .message-content').last.inner_text(),'FINAL authoritative answer')
        self.assertEqual(self.page.locator('#streamingMessage').count(),0)
        self.page.wait_for_timeout(200)
        self.assertEqual(self.page.locator('.message.assistant .message-content').last.inner_text(),'FINAL authoritative answer')
        self.assertFalse(self.page.locator('#messageInput').is_disabled())
        self.assertEqual(self.errors,[])

    def test_rapid_stream_interruption_preserves_latest_pending_delta(self):
        self.set_configured_model()
        self.page.evaluate("""() => {PocketNative({event:'started',data:{session:'burst-cancel',text:'question'}});
          for(let i=0;i<100;i++)PocketNative({event:'delta',data:{text:'chunk'+i+' '}});
          PocketNative({event:'failure',data:{message:'Fixture stopped'}});
        }""")
        answer=self.page.locator('.message.assistant .message-content').last.inner_text()
        self.assertIn('chunk0',answer);self.assertIn('chunk99',answer);self.assertIn('미완료 응답',answer)
        self.assertEqual(self.page.locator('#streamingMessage').count(),0)
        self.page.wait_for_timeout(200)
        self.assertEqual(self.page.locator('.message.assistant .message-content').last.inner_text(),answer)
        self.assertFalse(self.page.locator('#messageInput').is_disabled())
        self.assertEqual(self.errors,[])

    def test_composer_draft_restores_after_reload_without_focus_or_api_key_storage(self):
        page=self.browser.new_page(viewport={'width':393,'height':852})
        try:
            page.route('https://draft.fixture/**',lambda route:route.fulfill(status=200,content_type='text/html',body=self.html))
            page.goto('https://draft.fixture/')
            if page.locator('#setupDialog').is_visible():page.locator('#setupLater').click()
            page.locator('#messageInput').fill('Unsent renderer recovery draft')
            record=page.evaluate("JSON.parse(localStorage.getItem('hermes-pocket-composer-draft'))")
            self.assertEqual(record,{'text':'Unsent renderer recovery draft','pendingSince':0})
            page.reload()
            self.assertEqual(page.locator('#messageInput').input_value(),'Unsent renderer recovery draft')
            self.assertNotEqual(page.evaluate('document.activeElement.id'),'messageInput')
            if page.locator('#setupDialog').is_visible():page.locator('#setupLater').click()
            page.locator('#headerNewChat').click()
            self.assertEqual(page.locator('#messageInput').input_value(),'')
            self.assertIsNone(page.evaluate("localStorage.getItem('hermes-pocket-composer-draft')"))
        finally:page.close()

    def test_accepted_pending_draft_reconciles_with_native_history_without_resend(self):
        page=self.browser.new_page(viewport={'width':393,'height':852})
        try:
            page.route('https://recovery.fixture/**',lambda route:route.fulfill(status=200,content_type='text/html',body=self.html))
            page.goto('https://recovery.fixture/')
            page.evaluate("localStorage.setItem('hermes-pocket-composer-draft',JSON.stringify({text:'Already accepted message',pendingSince:Date.now()-1000}))")
            page.add_init_script("""window.recoveryPackets=[];window.NativeBridge={postMessage(raw){const p=JSON.parse(raw);recoveryPackets.push(p);let data={};if(p.method==='boot')data={config:{providerId:'custom',endpoint:'https://fixture.example/v1',model:'fixture-model',allowedApps:[]},sessions:[],audit:[],device:{},session:'accepted',version:'0.05'};if(p.method==='messages')data=[{role:'user',content:'Already accepted message',created:Date.now()}];setTimeout(()=>PocketNative({id:p.id,ok:true,data}),0);}};""")
            page.reload()
            page.wait_for_function("() => state.messages.some(m=>m.content==='Already accepted message')")
            self.assertEqual(page.locator('#messageInput').input_value(),'')
            self.assertIsNone(page.evaluate("localStorage.getItem('hermes-pocket-composer-draft')"))
            self.assertEqual(page.evaluate("recoveryPackets.filter(p=>p.method==='startChat').length"),0)
            self.assertNotEqual(page.evaluate('document.activeElement.id'),'messageInput')
        finally:page.close()

    def test_composer_remains_visible_in_short_keyboard_sized_viewport(self):
        for design in ('white','black'):
            self.page.evaluate('(id)=>applyDesign(id)',design)
            for width in (360,393):
                with self.subTest(design=design,width=width):
                    self.page.set_viewport_size({'width':width,'height':420})
                    self.page.locator('#messageInput').fill('Draft after keyboard opens')
                    for selector in ('#messageInput','#sendMessage'):
                        box=self.page.locator(selector).bounding_box()
                        self.assertGreaterEqual(box['y'],0)
                        self.assertLessEqual(box['y']+box['height'],420)
                    self.assertLessEqual(self.page.evaluate('document.documentElement.scrollWidth'),width)
                    self.assertEqual(self.page.locator('.composer-caption,#previewNotice').count(),0)
        self.assertEqual(self.errors,[])

if __name__=='__main__':unittest.main(verbosity=2)
