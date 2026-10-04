"""Document UI exercises native packets and uncertain replies; disk persistence is JVM/native proof."""
import unittest
import test_ui as ui
class MemoryDocumentsUITest(unittest.TestCase):
 setUpClass=classmethod(ui.UITest.setUpClass.__func__)
 tearDownClass=classmethod(ui.UITest.tearDownClass.__func__)
 setUp=ui.UITest.setUp;tearDown=ui.UITest.tearDown
 navigate=ui.UITest.navigate
 def bridge(self):
  self.page.evaluate('''()=>{window.documentPackets=[];window.documentFixture=[{name:'USER.md',bytes:10},{name:'MEMORY.md',bytes:20},{name:'notes/task.md',bytes:30}];NativeBridge={postMessage(raw){const p=JSON.parse(raw);documentPackets.push(p);let data=[];if(p.method==='memoryDocumentsList')data=documentFixture;if(p.method==='memoryDocumentsRead')data={name:p.data.name,content:'# '+p.data.name+'\\nLiteral full owner document'};if(p.method==='memoryDocumentsSave')data=window.rejectDocumentSave?{saved:false}:{saved:true,name:p.data.name,documents:documentFixture};if(p.method==='memoryDocumentsDelete')data={deleted:true,documents:documentFixture.filter(d=>d.name!==p.data.name)};setTimeout(()=>PocketNative({id:p.id,ok:true,data}),0)}}}''')
  self.navigate('memory');self.page.wait_for_function('()=>document.querySelectorAll("[data-memory-name]").length===3')
 def test_collapsed_document_reads_full_text_only_on_open_and_core_delete_stays_forbidden(self):
  self.bridge();card=self.page.locator('[data-memory-name="USER.md"]');self.assertFalse(card.evaluate('e=>e.open'))
  self.assertEqual(self.page.evaluate('documentPackets.filter(p=>p.method==="memoryDocumentsRead").length'),0)
  card.locator('summary').click();self.page.wait_for_function('()=>documentPackets.some(p=>p.method==="memoryDocumentsRead")')
  self.assertIn('Literal full owner document',card.locator('.owned-document-content').inner_text())
  self.assertEqual(card.get_by_role('button',name='삭제',exact=True).count(),0)
  card.get_by_role('button',name='편집',exact=True).click();self.assertTrue(self.page.locator('#deleteMemoryDocument').is_disabled())
  self.assertEqual(self.page.evaluate('documentPackets.find(p=>p.method==="memoryDocumentsRead").data'),{'name':'USER.md'})
  self.assertEqual(self.errors,[])
 def test_save_uncertain_reply_preserves_draft_and_native_confirmed_save_carries_exact_name_content(self):
  self.bridge();self.page.locator('#newMemoryDocument').click();self.assertTrue(self.page.locator('#deleteMemoryDocument').is_disabled())
  self.page.locator('#memoryDocumentName').fill('notes/new.md');self.page.locator('#memoryDocumentContent').fill('# Literal draft\nKeep my text')
  self.page.evaluate('window.rejectDocumentSave=true');self.page.locator('#saveMemoryDocument').click();self.page.wait_for_function('()=>!memoryDocumentBusy')
  self.assertEqual(self.page.locator('#memoryDocumentContent').input_value(),'# Literal draft\nKeep my text')
  self.assertIn('확인하지 못',self.page.locator('#memoryDocumentStatus').inner_text())
  self.page.evaluate('window.rejectDocumentSave=false');self.page.locator('#saveMemoryDocument').click();self.page.wait_for_function('()=>document.getElementById("memoryDocumentStatus").textContent==="저장했습니다."')
  self.assertEqual(self.page.evaluate('documentPackets.filter(p=>p.method==="memoryDocumentsSave").at(-1).data'),{'name':'notes/new.md','content':'# Literal draft\nKeep my text'})
  self.assertEqual(self.errors,[])
