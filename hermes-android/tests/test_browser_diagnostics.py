"""Browser error listeners send metadata-only packets; native persistence is checked separately."""
import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
import test_ui as ui
class BrowserDiagnosticsUITest(unittest.TestCase):
 setUpClass=classmethod(ui.UITest.setUpClass.__func__)
 tearDownClass=classmethod(ui.UITest.tearDownClass.__func__)
 setUp=ui.UITest.setUp;tearDown=ui.UITest.tearDown
 def bridge(self,fail=False):
  self.page.evaluate('''fail=>{window.browserErrorPackets=[];NativeBridge={postMessage(raw){const p=JSON.parse(raw);browserErrorPackets.push(p);setTimeout(()=>PocketNative({id:p.id,ok:!fail,data:fail?{message:'fixture recorder unavailable'}:{recorded:true}}),0)}}}''',fail)
 def test_browser_error_and_unhandled_rejection_forward_only_known_source_coordinates_without_raw_exception_data(self):
  self.bridge()
  self.page.evaluate("window.dispatchEvent(new ErrorEvent('error',{message:'private-message API_KEY=private-key',filename:'https://private.example/app.js?token=private-url',lineno:7,colno:9,error:new Error('private-stack')}))")
  self.page.wait_for_function('()=>browserErrorPackets.some(p=>p.method==="recordBrowserProblem")')
  packet=self.page.evaluate('browserErrorPackets[0].data')
  self.assertEqual(set(packet),{'code','source','line','column'});self.assertEqual(packet['code'],'webview_error');self.assertIn(packet['source'],['app.js','index.html']);self.assertEqual(packet['line'],7);self.assertEqual(packet['column'],9)
  self.assertNotIn('private-',str(self.page.evaluate('browserErrorPackets')))
  self.page.wait_for_timeout(1050)
  self.page.evaluate("window.dispatchEvent(new PromiseRejectionEvent('unhandledrejection',{promise:Promise.resolve(),reason:{token:'private-reason-key',text:'private-chat-body'}}))")
  self.page.wait_for_function('()=>browserErrorPackets.length===2')
  self.assertEqual(self.page.evaluate('browserErrorPackets[1].data'),{'code':'unhandled_rejection','source':'app.js','line':0,'column':0})
  self.assertNotIn('private-',str(self.page.evaluate('browserErrorPackets')));self.assertEqual(self.errors,[])
 def test_repeated_errors_and_recorder_rejection_do_not_loop_or_replace_chat_with_private_failure(self):
  self.bridge(True)
  self.page.evaluate("for(let i=0;i<10;i++)window.dispatchEvent(new ErrorEvent('error',{message:'private-user-message',filename:'private-url',lineno:-1,colno:Infinity,error:new Error('private-stack')}))")
  self.page.wait_for_timeout(50)
  self.assertEqual(self.page.evaluate('browserErrorPackets.length'),1)
  self.assertEqual(self.page.evaluate('browserErrorPackets[0].data'),{'code':'webview_error','source':'app.js','line':0,'column':0})
  self.assertNotIn('private-',str(self.page.evaluate('browserErrorPackets')));self.assertNotIn('fixture recorder unavailable',self.page.locator('#conversation').inner_text());self.assertEqual(self.errors,[])
