"""Terminal UI native-packet tests; bridge responses are fixtures, never shell proof."""
import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
import test_ui as ui_helpers

class TerminalUITest(unittest.TestCase):
    setUpClass=classmethod(ui_helpers.UITest.setUpClass.__func__)
    tearDownClass=classmethod(ui_helpers.UITest.tearDownClass.__func__)
    setUp=ui_helpers.UITest.setUp
    tearDown=ui_helpers.UITest.tearDown
    navigate=ui_helpers.UITest.navigate
    open_provider_details=ui_helpers.UITest.open_provider_details

    def bridge(self,config=None,jobs=None):
        self.page.evaluate('''fixture=>{
            window.testPackets=[];
            window.testConfig={mode:'direct',providerId:'custom',endpoint:'https://fixture.example/v1',model:'fixture-model',enabledPlugins:['terminal'],...fixture.config};
            window.terminalFixture={available:true,workspace:'/fixture/app/terminal',cwd:'/fixture/app/terminal',pty:false,sessions:fixture.jobs};
            window.nextTerminalResponse={ok:true,result:{session_id:'proc_fixture_a',backend:'app',status:'exited',running:false,exit_code:13,output:'actual fixture diagnostic',pty:false}};
            NativeBridge={postMessage(raw){
                const p=JSON.parse(raw);testPackets.push(p);let data={};
                if(p.method==='boot')data={config:testConfig,sessions:[],audit:[],device:{},busy:false,version:'0.09'};
                if(p.method==='terminalStatus')data=terminalFixture;
                if(p.method==='stop'&&window.holdStop){window.pendingStop=p;return;}
                if(p.method==='runTool'){
                    if(window.holdTerminal){window.pendingTerminal=p;return;}
                    data=nextTerminalResponse;
                }
                setTimeout(()=>PocketNative({id:p.id,ok:true,data}),0);
            }};boot();
        }''',{'config':config or {},'jobs':jobs or []})
        self.page.wait_for_function('() => state.config.model === "fixture-model"')

    def open_terminal(self):
        self.navigate('settings')
        self.page.locator('#settingsTerminal').click()

    def test_typed_terminal_arguments_preserve_command_and_omit_unsaved_model_key(self):
        self.bridge();self.navigate('settings');self.page.locator('#settingsConnection').click();self.open_provider_details()
        self.page.locator('#token').fill('unsaved-terminal-model-fixture-key');self.open_terminal()
        command="printf '%s\\n' '한글 $literal <tag>'\nexit 13"
        self.page.locator('#terminalCommand').fill(command)
        self.page.locator('#terminalBackground').check();self.page.locator('#terminalTimeout').fill('7')
        self.page.locator('#terminalRun').click();self.page.wait_for_function('() => !terminalUi.request && testPackets.some(p=>p.method==="runTool")')
        packet=self.page.evaluate('testPackets.find(p=>p.method==="runTool").data')
        self.assertEqual(packet,{'name':'terminal','arguments':{'command':command,'backend':'app','background':True,'timeout':7,'max_output_chars':12000,'pty':False}})
        self.assertNotIn('unsaved-terminal-model-fixture-key',str(packet))
        self.assertEqual(self.page.locator('#token').input_value(),'unsaved-terminal-model-fixture-key')
        self.assertIn('"exit_code": 13',self.page.locator('#terminalOutput').inner_text())
        self.assertNotIn('성공',self.page.locator('#terminalRequestStatus').inner_text())
        self.assertEqual(self.errors,[])

    def test_native_job_list_and_process_buttons_preserve_backend_and_session_identity(self):
        jobs=[{'session_id':'proc_fixture_remote','backend':'shizuku','status':'running','running':True,'exit_code':None,'stdin_closed':False}]
        self.bridge(jobs=jobs);self.open_terminal();self.page.locator('#terminalRefresh').click()
        self.page.wait_for_function('() => document.querySelectorAll("#terminalJobs [data-process-action]").length===2')
        for action in ('poll','kill'):
            self.page.locator('[data-process-action="'+action+'"]').click()
            self.page.wait_for_function('(action)=>!terminalUi.request && testPackets.some(p=>p.method==="runTool"&&p.data.arguments.action===action)',arg=action)
            self.assertEqual(self.page.evaluate('testPackets.filter(p=>p.method==="runTool").at(-1).data'),{'name':'process_manage','arguments':{'action':action,'session_id':'proc_fixture_remote','backend':'shizuku','max_output_chars':12000}})
        self.assertEqual(self.errors,[])

    def test_pending_terminal_and_stop_wait_for_actual_native_response_before_reenabling(self):
        self.bridge();self.open_terminal();self.page.evaluate('window.holdTerminal=true;window.holdStop=true')
        self.page.locator('#terminalCommand').fill('fixture pending command');self.page.locator('#terminalRun').click()
        self.page.wait_for_function('() => !!window.pendingTerminal')
        self.assertTrue(self.page.locator('#terminalRun').is_disabled())
        self.assertTrue(self.page.locator('#terminalCommand').is_disabled())
        self.assertTrue(self.page.locator('#terminalStop').is_visible())
        self.page.locator('#terminalStop').click()
        self.page.wait_for_function('() => testPackets.some(p=>p.method==="stop")')
        self.assertTrue(self.page.locator('#terminalRun').is_disabled())
        self.page.evaluate("PocketNative({id:pendingTerminal.id,ok:false,data:{message:'fixture command cancelled'}})")
        self.page.wait_for_function('() => !terminalUi.request && !state.busy')
        self.assertFalse(self.page.locator('#terminalRun').is_disabled())
        self.assertIn('fixture command cancelled',self.page.locator('#terminalRequestStatus').inner_text())
        self.page.locator('#terminalRun').click()
        self.page.wait_for_function('() => terminalUi.request && testPackets.filter(p=>p.method==="runTool").length===2')
        self.page.evaluate("PocketNative({id:pendingStop.id,ok:true,data:{}})")
        self.page.wait_for_timeout(40)
        self.assertIn('실제 기기 응답 대기',self.page.locator('#terminalRequestStatus').inner_text())
        self.assertTrue(self.page.locator('#terminalRun').is_disabled())
        self.page.evaluate("PocketNative({id:pendingTerminal.id,ok:false,data:{message:'second actual command cancelled'}})")
        self.page.wait_for_function('() => !terminalUi.request && !state.busy')
        self.assertIn('second actual command cancelled',self.page.locator('#terminalRequestStatus').inner_text())
        self.assertEqual(self.errors,[])

    def test_disabled_plugin_actual_inventory_and_busy_block_terminal_without_native_execution(self):
        self.bridge({'enabledPlugins':[]});self.open_terminal()
        self.assertTrue(self.page.locator('#terminalRun').is_disabled())
        self.page.evaluate("requestTerminalTool('terminal',{command:'must not run'})")
        self.assertEqual(self.page.evaluate('testPackets.filter(p=>p.method==="runTool").length'),0)
        self.page.evaluate("state.config.enabledPlugins=['terminal'];state.config.availableTools={names:['terminal']};renderTerminal()")
        self.assertTrue(self.page.locator('#terminalRun').is_disabled())
        self.page.evaluate("state.config.availableTools={names:['terminal','process_manage']};renderTerminal();PocketNative({event:'started',data:{session:'fixture-busy',text:'active task'}})")
        self.assertTrue(self.page.locator('#terminalRun').is_disabled())
        self.page.evaluate("requestTerminalTool('terminal',{command:'must not run while busy'})")
        self.assertEqual(self.page.evaluate('testPackets.filter(p=>p.method==="runTool").length'),0)
        self.assertEqual(self.errors,[])

    def test_shizuku_failure_is_literal_and_never_retried_under_app_backend(self):
        self.bridge();self.open_terminal()
        self.page.evaluate("nextTerminalResponse={ok:false,error:'fixture Shizuku unavailable <img src=x onerror=alert(1)>'}")
        self.page.locator('#terminalBackend').select_option('shizuku')
        self.assertIn('자동 전환하지 않습니다',self.page.locator('#terminalBackendStatus').inner_text())
        self.page.locator('#terminalCommand').fill('id');self.page.locator('#terminalRun').click()
        self.page.wait_for_function('() => !terminalUi.request && testPackets.some(p=>p.method==="runTool")')
        packets=self.page.evaluate('testPackets.filter(p=>p.method==="runTool")')
        self.assertEqual(len(packets),1);self.assertEqual(packets[0]['data']['arguments']['backend'],'shizuku')
        self.assertIn('fixture Shizuku unavailable',self.page.locator('#terminalRequestStatus').inner_text())
        self.assertIn('<img',self.page.locator('#terminalOutput').inner_text())
        self.assertEqual(self.page.locator('#terminalOutput img,#terminalRequestStatus img').count(),0)
        self.assertEqual(self.errors,[])
