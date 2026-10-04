"""Real DOM/CLI behavior with explicit native fixtures, never a provider or Android lifecycle claim."""
import unittest
import test_ui as ui

class CliJobsTest(unittest.TestCase):
    setUpClass=classmethod(ui.UITest.setUpClass.__func__)
    tearDownClass=classmethod(ui.UITest.tearDownClass.__func__)
    setUp=ui.UITest.setUp
    tearDown=ui.UITest.tearDown
    set_configured_model=ui.UITest.set_configured_model
    def bridge(self):
        self.page.evaluate('''()=>{
            window.cliPackets=[];window.fixtureJob={id:'job_'+ 'a'.repeat(32),goal:'실제 fixture 작업',parentSession:'fixture-session',status:'running',phase:'모델 요청 1',result:'',usageKnown:false};
            window.NativeBridge={postMessage(raw){const p=JSON.parse(raw);cliPackets.push(p);let data={};
                const jobs=()=>({active:['running','queued','cancelling'].includes(fixtureJob.status)?1:0,jobs:[fixtureJob],available:true});
                if(p.method==='jobs')data=jobs();
                if(p.method==='startBackgroundTask')data={job_id:fixtureJob.id,jobs:jobs()};
                if(p.method==='jobResult')data=fixtureJob;
                if(p.method==='cancelJob'){fixtureJob.status='cancelling';data={cancelRequested:true,job:fixtureJob};}
                if(p.method==='boot')data={version:'0.13',config:{model:'fixture'},availableTools:{names:['delegate_task','task_result']},jobs:jobs(),terminal:{},busy:state.busy};
                setTimeout(()=>PocketNative({id:p.id,ok:true,data}),0);
            }};
        }''')
    def test_local_help_works_without_a_model_and_does_not_send_a_chat(self):
        self.page.locator('#messageInput').fill('/help')
        self.assertFalse(self.page.locator('#sendMessage').is_disabled())
        self.page.locator('#sendMessage').click()
        self.assertIn('/bg',self.page.locator('#resultBody').inner_text())
        self.assertEqual(self.page.evaluate('state.messages.length'),0)
        self.assertEqual(self.errors,[])
    def test_bg_uses_real_native_job_entry_and_does_not_fake_chat_completion(self):
        self.bridge();self.page.locator('#messageInput').fill('/bg 전달된 내용을 비교해 줘')
        self.page.locator('#sendMessage').click()
        self.page.wait_for_function("()=>document.querySelectorAll('.cli-job-row').length===1")
        self.assertEqual(self.page.evaluate("cliPackets.filter(p=>p.method==='startBackgroundTask').length"),1)
        self.assertEqual(self.page.evaluate("cliPackets.filter(p=>p.method==='startChat').length"),0)
        self.assertEqual(self.page.evaluate('state.messages.length'),0)
        self.assertIn('실행 중',self.page.locator('#privateJobRows').inner_text())
        self.assertEqual(self.errors,[])
    def test_job_panel_is_usable_during_foreground_chat(self):
        self.bridge();self.set_configured_model();self.page.evaluate("PocketNative({event:'started',data:{session:'fixture-session',runId:'r',text:'foreground'}})")
        self.assertFalse(self.page.locator('#privateJobsButton').is_disabled())
        self.page.locator('#privateJobsButton').click();self.page.wait_for_selector('.cli-job-form textarea')
        self.page.locator('.cli-job-form textarea').fill('독립적으로 검토')
        self.page.locator('.cli-job-form button').click()
        self.page.wait_for_function("()=>cliPackets.some(p=>p.method==='startBackgroundTask')")
        self.assertTrue(self.page.evaluate('state.busy'))
        self.assertEqual(self.errors,[])
    def test_job_result_uses_safe_markdown_not_html_and_unknown_usage_is_not_invented(self):
        self.bridge();self.page.evaluate("fixtureJob.status='completed';fixtureJob.result='<img src=x onerror=alert(1)> 결과';handleCliCommand('/result '+fixtureJob.id)")
        self.page.wait_for_selector('.cli-job-result')
        self.assertEqual(self.page.locator('.cli-job-result img,.cli-job-result script').count(),0)
        self.assertIn('토큰 사용량 미확인',self.page.locator('#resultBody').inner_text())
        self.assertEqual(self.errors,[])
    def test_cancel_keeps_cancelling_until_native_reports_terminal_state(self):
        self.bridge();self.page.locator('#privateJobsButton').click();self.page.wait_for_selector('.cli-job-row')
        self.page.locator('.cli-job-row button').filter(has_text='중단').click()
        self.page.wait_for_function("()=>jobUi.state.jobs[0].status==='cancelling'")
        self.assertIn('중단 처리 중',self.page.locator('#privateJobRows').inner_text())
        self.assertNotIn('완료',self.page.locator('#privateJobRows').inner_text())
        self.assertEqual(self.errors,[])
    def test_job_update_does_not_destroy_partially_typed_new_task(self):
        self.bridge();self.page.locator('#privateJobsButton').click();self.page.wait_for_selector('.cli-job-form textarea')
        self.page.locator('.cli-job-form textarea').fill('아직 작성 중인 요청')
        self.page.evaluate("PocketNative({event:'jobs',data:{active:1,jobs:[{...fixtureJob,phase:'새 진행 상태'}]}})")
        self.assertEqual(self.page.locator('.cli-job-form textarea').input_value(),'아직 작성 중인 요청')
        self.assertIn('새 진행 상태',self.page.locator('#privateJobRows').inner_text())
        self.assertEqual(self.errors,[])
    def test_cli_transcript_and_job_controls_fit_narrow_phone_widths(self):
        self.bridge();self.set_configured_model()
        for width in (320,360,393):
            self.page.set_viewport_size({'width':width,'height':852})
            self.assertLessEqual(self.page.evaluate('document.documentElement.scrollWidth'),width)
        self.page.evaluate("state.messages=[{role:'user',content:'메시지'},{role:'assistant',content:'결과'}];renderConversation()")
        self.assertIn('you ›',self.page.locator('#conversation').inner_text())
        self.assertIn('hermes ›',self.page.locator('#conversation').inner_text())
        self.assertEqual(self.errors,[])

if __name__=='__main__':unittest.main()
