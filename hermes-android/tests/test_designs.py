"""Browser regression checks; the native bridge below is ONLY a test double.

These tests verify WebView UI behavior and outgoing request shapes. They do not
claim Android permissions, provider connectivity, or actual phone execution.
"""
import os
import shutil
import sys
import unittest
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from make_preview import bundle

DESIGNS = ('white', 'black')
TABS = ('chat', 'device', 'history', 'memory', 'settings')
BRIDGE = '''() => {
    window.testPackets = [];
    window.NativeBridge = {postMessage(raw) {
        const packet = JSON.parse(raw);
        window.testPackets.push(packet);
        let data = {};
        if (packet.method === 'boot') data = {
            config: {mode:'direct', endpoint:'https://test.example/v1',
                model:'test-model', memory:'', allowedApps:['dev.test.app']},
            sessions:[], audit:[], device:{model:'Test phone', battery:67},
            busy:false
        };
        if (['sessions', 'audit', 'messages'].includes(packet.method)) data = [];
        if (packet.method === 'apps') data = [{package:'dev.test.app', label:'Test app', allowed:true, system:false}];
        if (packet.method === 'runTool') data = {testOnly:true,
            name:packet.data.name, arguments:packet.data.arguments};
        if (packet.method === 'runTool' && packet.data.name === 'read_screen') data = {
            snapshot:'test-screen',package:'dev.test.app',expiresInSeconds:45,
            elements:[{id:'e0',description:'Test input',clickable:true,editable:true,scrollable:true}]};
        if (packet.method === 'runTool') data = {ok:true, result:data};
        setTimeout(() => window.PocketNative({id:packet.id, ok:true, data}), 0);
    }};
}'''


class DesignUITest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.html = bundle()
        cls.pw = sync_playwright().start()
        cls.browser = cls.pw.chromium.launch(
            executable_path=os.environ.get('CHROMIUM_PATH') or
            shutil.which('chromium') or '/snap/bin/chromium',
            headless=True, args=['--no-sandbox'])

    @classmethod
    def tearDownClass(cls):
        cls.browser.close()
        cls.pw.stop()

    def setUp(self):
        self.page = self.browser.new_page(viewport={'width':393, 'height':852})
        self.errors = []
        self.page.on('pageerror', lambda e: self.errors.append(str(e)))
        self.page.set_content(self.html)
        self.page.wait_for_function('() => typeof applyDesign === "function"')
        if self.page.locator('#setupDialog').is_visible():
            self.page.locator('#setupLater').click()

    def tearDown(self):
        self.page.close()

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

    def choose(self, design):
        self.page.evaluate('(id)=>applyDesign(id)',design)

    def test_every_design_all_tabs_fit_mobile_and_wide(self):
        self.page.emulate_media(reduced_motion="reduce")
        for design in DESIGNS:
            self.choose(design)
            for width in (320, 360, 393, 720, 1100):
                self.page.set_viewport_size({'width':width, 'height':852})
                for tab in TABS:
                    with self.subTest(design=design, width=width, tab=tab):
                        # Click navigation is covered separately; this matrix tests layout.
                        self.page.evaluate('(tab) => showPage(tab)', tab)
                        self.assertTrue(self.page.locator('#page-' + tab).is_visible())
                        self.assertLessEqual(self.page.evaluate(
                            'document.documentElement.scrollWidth'), width)
        self.assertEqual(self.errors, [])

    def test_design_switch_preserves_unsaved_data(self):
        self.page.locator('#messageInput').fill('작성 중인 대화와 <문자>')
        self.navigate('memory')
        self.page.locator('#memoryEditor').fill('저장하지 않은 개인 메모리')
        self.navigate('settings')
        self.page.locator('#settingsConnection').click()
        self.open_provider_details()
        self.page.locator('#providerSelect').select_option('custom')
        self.page.locator('#endpoint').fill('https://draft.example/v1')
        self.page.locator('#token').fill('test-only-draft-secret')
        for design in DESIGNS:
            self.choose(design)
            with self.subTest(design=design):
                self.assertEqual(self.page.locator('#messageInput').input_value(),
                                 '작성 중인 대화와 <문자>')
                self.assertEqual(self.page.locator('#memoryEditor').input_value(),
                                 '저장하지 않은 개인 메모리')
                self.assertEqual(self.page.locator('#endpoint').input_value(),
                                 'https://draft.example/v1')
                self.assertEqual(self.page.locator('#token').input_value(),
                                 'test-only-draft-secret')
                self.assertEqual(self.page.locator(
                    f'[data-design-choice="{design}"]').get_attribute('aria-pressed'), 'true')
                self.assertEqual(self.page.locator(
                    '[data-design-choice][aria-pressed="true"]').count(), 1)
        self.assertEqual(self.errors, [])

    def test_design_switch_sends_matching_native_system_bar_appearance(self):
        self.page.evaluate(BRIDGE)
        self.page.emulate_media(reduced_motion='reduce')
        for design in DESIGNS:
            self.page.evaluate('window.testPackets=[]')
            self.choose(design)
            self.page.wait_for_function("() => testPackets.some(p => p.method === 'appearance')")
            packet=self.page.evaluate("testPackets.filter(p => p.method === 'appearance').at(-1).data")
            expected=self.page.evaluate("""() => {
                const rgb=getComputedStyle(document.body).backgroundColor.match(/\\d+/g).slice(0,3).map(Number);
                return {background:'#'+rgb.map(v=>v.toString(16).padStart(2,'0')).join(''),light:rgb[0]*.299+rgb[1]*.587+rgb[2]*.114>150};
            }""")
            with self.subTest(design=design):
                self.assertEqual(packet,expected)
        self.assertEqual(self.errors,[])

    def test_stream_completion_and_failure_all_designs(self):
        self.page.evaluate(BRIDGE)
        self.page.evaluate('boot()')
        self.page.wait_for_function('() => state.config.model === "test-model"')
        for design in DESIGNS:
            self.choose(design)
            self.navigate('chat')
            self.page.evaluate('''() => window.PocketNative({event:'started',
                data:{session:'test', text:'질문'}})''')
            self.assertFalse(self.page.locator('#sendMessage').is_disabled())
            self.assertEqual(self.page.locator('#sendMessage').get_attribute('data-action'),'stop')
            self.assertTrue(self.page.locator('#sendMessage').is_visible())
            self.page.evaluate('''() => window.PocketNative({event:'delta',
                data:{text:'<script>window.bad=true</script>응답'}})''')
            self.assertEqual(self.page.locator('#streamingMessage script').count(), 0)
            self.page.evaluate('''() => window.PocketNative({event:'complete',
                data:{text:'완료'}})''')
            self.assertFalse(self.page.locator('#messageInput').is_disabled())
            self.page.locator('#messageInput').fill('다음 질문')
            self.assertFalse(self.page.locator('#sendMessage').is_disabled())
            self.assertEqual(self.page.locator('#streamingMessage').count(), 0)
            self.page.evaluate('''() => {
                window.PocketNative({event:'started',data:{session:'test',text:'재시도'}});
                window.PocketNative({event:'failure',data:{message:'테스트 오류'}});
            }''')
            self.assertFalse(self.page.locator('#messageInput').is_disabled())
            self.assertIn('테스트 오류', self.page.locator('.message.error').last.inner_text())
        self.assertEqual(self.errors, [])

    def test_composer_tool_and_model_shortcuts_reach_controls(self):
        self.page.evaluate(BRIDGE)
        self.page.evaluate('boot()')
        self.page.wait_for_function('() => state.config.model === "test-model"')
        self.page.locator('#composerTools').click()
        self.assertTrue(self.page.locator('#capabilitySheet').is_visible())
        self.page.locator('#sheetDevice').click()
        self.assertTrue(self.page.locator('#page-device').is_visible())
        self.assertFalse(self.page.locator('#manualDeviceDetails').evaluate('(e)=>e.open'))
        self.page.locator('#manualDeviceDetails > summary').click()
        self.assertTrue(self.page.locator('#executeTool').is_visible())
        self.navigate('chat')
        self.page.locator('#composerConnection').click()
        self.assertTrue(self.page.locator('#modelSheet').is_visible())
        self.page.locator('#sheetApiSettings').click()
        self.assertTrue(self.page.locator('#page-settings').is_visible())
        self.open_provider_details()
        self.page.locator('#providerSelect').select_option('custom')
        self.open_provider_details()
        self.assertTrue(self.page.locator('#endpoint').is_visible())
        self.assertEqual(self.errors,[])

    def test_sidebar_session_search_open_and_deliberate_delete(self):
        self.page.evaluate(BRIDGE)
        self.page.evaluate("""() => {
            const base=NativeBridge.postMessage;
            window.testSessions=[{id:'s1', title:'Saved conversation',created:Date.now(),count:2}];
            window.allowDeletion=false;
            NativeBridge.postMessage=raw => {
                const packet=JSON.parse(raw);
                let data;
                if(packet.method==='sessions') data=testSessions;
                else if(packet.method==='messages') data=[{role:'user',content:'Saved question'},{role:'assistant',content:'Saved answer'}];
                else if(packet.method==='deleteSession') {
                    if(allowDeletion) testSessions=[];
                    data={deleted:allowDeletion,sessions:testSessions};
                } else return base(raw);
                testPackets.push(packet);
                setTimeout(()=>PocketNative({id:packet.id,ok:true,data}),0);
            };
            boot();
        }""")
        self.page.wait_for_function('() => state.config.model === "test-model"')
        self.page.locator('#menuToggle').click()
        self.page.locator('#sidebarSearch').fill('missing')
        self.assertEqual(self.page.locator('.sidebar-session').count(),0)
        self.page.locator('#sidebarSearch').fill('Saved')
        self.page.locator('.sidebar-session-open').click()
        self.page.wait_for_function('() => state.sid === "s1"')
        self.assertIn('Saved answer',self.page.locator('#conversation').inner_text())
        self.assertEqual(self.page.locator('.sidebar-session.selected').count(),1)
        self.assertEqual(self.page.locator('#sidebar [data-page="chat"]').count(),0)
        self.assertEqual(self.page.locator('#menuToggle').get_attribute('aria-expanded'),'false')
        self.page.locator('#menuToggle').click()
        self.page.locator('.sidebar-session-open').focus()
        self.page.keyboard.press('Delete')
        self.page.wait_for_function("() => testPackets.some(p => p.method === 'deleteSession')")
        self.page.wait_for_function("() => !document.querySelector('.sidebar-session-open')?.disabled")
        self.assertEqual(self.page.locator('.sidebar-session').count(),1)
        self.assertEqual(self.page.evaluate('state.sid'),'s1')
        self.page.evaluate('window.allowDeletion=true')
        self.page.locator('.sidebar-session-open').focus()
        self.page.keyboard.press('Delete')
        self.page.wait_for_function('() => state.sessions.length === 0')
        self.assertEqual(self.page.evaluate('state.sid'),'')
        self.assertEqual(self.page.locator('#conversation .message').count(),0)
        self.assertEqual(self.errors,[])

    def test_design_deep_links_select_requested_choice(self):
        for design in DESIGNS:
            page = self.browser.new_page()
            page.route('https://preview.test/**', lambda route: route.fulfill(
                status=200, content_type='text/html', body=self.html))
            page.goto('https://preview.test/?design=' + design)
            self.assertEqual(page.locator(
                f'[data-design-choice="{design}"]').get_attribute('aria-pressed'), 'true')
            page.close()

    def test_legacy_design_links_migrate_to_two_current_themes(self):
        for legacy,expected in [('linen','white'),('clay','white'),('midnight','black'),('terminal','black')]:
            page=self.browser.new_page()
            page.route('https://preview.test/**',lambda route:route.fulfill(status=200,content_type='text/html',body=self.html))
            page.goto('https://preview.test/?design='+legacy)
            with self.subTest(legacy=legacy):
                self.assertEqual(page.locator('body').get_attribute('data-design'),expected)
                self.assertEqual(page.locator('[data-design-choice]').count(),2)
            page.close()

    def test_design_shortcut_opens_appearance(self):
        self.navigate('settings')
        self.page.locator('#settingsAppearance').click()
        self.assertTrue(self.page.locator('#page-settings').is_visible())
        self.assertTrue(self.page.locator('#designPicker').is_visible())
        self.assertEqual(self.page.locator('[data-design-choice]').count(), 2)
        self.assertEqual(self.errors, [])

    def test_all_manual_tools_send_native_arguments(self):
        self.page.evaluate(BRIDGE)
        self.page.evaluate('boot()')
        self.page.wait_for_function('() => state.config.model === "test-model"')
        self.navigate('device')
        self.assertEqual(self.page.locator('#toolSelect option').count(),self.page.evaluate('Object.keys(toolNames).length'))
        cases = [
            ('get_device_state', {}, {}), ('list_apps', {}, {}),
            ('launch_app', {'package':'dev.test.app'}, {'package':'dev.test.app'}),
            ('open_settings', {'page':'display'}, {'page':'display'}),
            ('set_volume', {'volume':'0'}, {'percent':0}),
            ('set_brightness', {'brightness':'100'}, {'percent':100}),
            ('root_processes', {}, {}),
            ('set_wifi', {'enabled':'false'}, {'enabled':False}),
            ('force_stop_app', {'package':'dev.test.app'}, {'package':'dev.test.app'}),
            ('read_screen', {}, {}),
            ('capture_screen', {}, {}),
            ('press_back', {}, {'snapshot':'test-snapshot'}),
            ('press_home', {}, {'snapshot':'test-snapshot'}),
            ('tap_screen', {'x':'100','y':'150'}, {'snapshot':'test-snapshot','x':100,'y':150}),
            ('swipe_screen', {'startX':'100','startY':'300','endX':'100','endY':'150','durationMs':'350'}, {'snapshot':'test-snapshot','startX':100,'startY':300,'endX':100,'endY':150,'durationMs':350}),
            ('click_element', {'element':'e0'}, {'snapshot':'test-snapshot','element':'e0'}),
            ('type_text', {'element':'e0','text':'입력 <문자>'},
                {'snapshot':'test-snapshot','element':'e0','text':'입력 <문자>'}),
            ('scroll_element', {'element':'e0','direction':'up'},
                {'snapshot':'test-snapshot','element':'e0','direction':'up'}),
        ]
        for name, fields, expected in cases:
            with self.subTest(tool=name):
                if name in ('click_element','type_text','scroll_element','press_back','press_home','tap_screen','swipe_screen'):
                    self.page.evaluate("""() => captureToolResult('read_screen', {
                        snapshot:'test-snapshot',package:'dev.test.app',bounds:[0,0,393,852],expiresInSeconds:45,
                        elements:[{id:'e0',description:'Test element',clickable:true,
                            editable:true,scrollable:true}]})""")
                self.page.locator('#toolSelect').select_option(name)
                if 'package' in fields:
                    self.page.wait_for_function('() => !manualToolState.loadingApps')
                for key, value in fields.items():
                    control = self.page.locator('#manual_' + key)
                    if control.evaluate('(e) => e.tagName') == 'SELECT':
                        control.select_option(value)
                    else:
                        control.fill(value)
                count = self.page.evaluate("testPackets.filter(p => p.method === 'runTool').length")
                self.page.locator('#executeTool').click()
                self.page.wait_for_function("""(count) => testPackets.filter(
                    p => p.method === 'runTool').length === count + 1""", arg=count)
                packet = self.page.evaluate("testPackets.filter(p => p.method === 'runTool').at(-1)")
                self.assertEqual(packet['data'], {'name':name, 'arguments':expected})
                self.page.wait_for_function('() => !manualToolState.running')
                self.assertTrue(self.page.locator('#resultModal').is_visible())
                self.page.locator('#closeResult').click()
        self.assertEqual(self.errors, [])

    def test_screen_result_buttons_select_target_and_invalidate_after_action(self):
        self.page.evaluate(BRIDGE)
        self.page.evaluate('boot()')
        self.page.wait_for_function('() => state.config.model === "test-model"')
        self.page.evaluate("runTool('read_screen', {})")
        self.page.locator('#screenTargets button').filter(has_text='입력').click()
        self.assertTrue(self.page.locator('#page-device').is_visible())
        self.assertFalse(self.page.locator('#resultModal').is_visible())
        self.assertEqual(self.page.locator('#toolSelect').input_value(), 'type_text')
        self.assertEqual(self.page.locator('#manual_element').input_value(), 'e0')
        self.assertEqual(self.page.locator('#manual_snapshot').input_value(), 'test-screen')
        self.page.locator('#manual_text').fill('Test text')
        self.page.locator('#executeTool').click()
        self.page.wait_for_function('() => manualToolState.snapshot === null')
        self.assertEqual(self.page.locator('#manual_snapshot').input_value(), '')
        self.assertEqual(self.errors, [])

    def test_manual_tool_cancellation_restores_controls(self):
        self.page.evaluate(BRIDGE)
        self.page.evaluate('boot()')
        self.page.wait_for_function('() => state.config.model === "test-model"')
        self.page.evaluate("""() => {
            const base = window.NativeBridge.postMessage;
            window.NativeBridge.postMessage = raw => {
                const packet = JSON.parse(raw);
                if (packet.method === 'runTool') {
                    window.testPackets.push(packet);
                    window.waitingToolId = packet.id;
                    return;
                }
                base(raw);
                if (packet.method === 'stop') setTimeout(() => window.PocketNative({
                    id:window.waitingToolId, ok:false,
                    data:{message:'테스트에서 취소됨'}
                }), 0);
            };
        }""")
        self.navigate('device')
        self.page.locator('#executeTool').click()
        self.page.wait_for_function('() => manualToolState.running')
        self.assertTrue(self.page.locator('#toolSelect').is_disabled())
        self.assertTrue(self.page.locator('#executeTool').is_disabled())
        self.assertTrue(self.page.locator('#stopManualTool').is_visible())
        self.page.locator('#stopManualTool').click()
        self.page.wait_for_function('() => !manualToolState.running')
        self.assertEqual(self.page.evaluate(
            "testPackets.filter(p => p.method === 'stop').length"), 1)
        self.assertFalse(self.page.locator('#toolSelect').is_disabled())
        self.assertFalse(self.page.locator('#executeTool').is_disabled())
        self.assertFalse(self.page.locator('#stopManualTool').is_visible())
        self.assertFalse(self.page.locator('#messageInput').is_disabled())
        self.assertEqual(self.errors, [])


if __name__ == '__main__':
    unittest.main(verbosity=2)
