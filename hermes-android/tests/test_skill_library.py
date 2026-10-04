"""Original library browser packets; actual asset/hash/store checks are in the JVM harness."""
import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
import test_ui as ui
class SkillLibraryUITest(unittest.TestCase):
 setUpClass=classmethod(ui.UITest.setUpClass.__func__)
 tearDownClass=classmethod(ui.UITest.tearDownClass.__func__)
 setUp=ui.UITest.setUp;tearDown=ui.UITest.tearDown
 navigate=ui.UITest.navigate
 def library(self):
  self.page.evaluate('''()=>{
   window.libraryPackets=[];window.installResponse={cancelled:true};
   const items=[{id:'skills/browser/agent-browser',name:'agent-browser',scope:'skills',category:'browser',description:'Original browser instructions',installable:true},{id:'skills/ml/oversize',name:'oversize',scope:'skills',category:'ml',description:'Large original package',installable:false}];
   NativeBridge={postMessage(raw){const p=JSON.parse(raw);libraryPackets.push(p);let data={};
    if(p.method==='boot')data={config:{mode:'direct',model:'fixture-model',providerId:'custom',endpoint:'https://fixture.example/v1',enabledPlugins:['agent'],activeSkillName:'owner-selected'},sessions:[],audit:[],device:{},busy:false,version:'0.10'};
    if(p.method==='skillsList')data=[];
    if(p.method==='skillsLibrary')data={skills:items,summary:{package_count:210,installable_count:209}};
    if(p.method==='skillsLibraryRead'){const item=items.find(s=>s.id===p.data.id);data=p.data.file_path?{id:item.id,file_path:p.data.file_path,bytes:6,encoding:'base64',content_base64:'AP/+Kg0K'}:{...item,content:'---\\nname: '+item.name+'\\ndescription: Original document\\n---\\n# <literal original>\\n',files:[{path:'SKILL.md',bytes:100},{path:'assets/sample.bin',bytes:6}],install_blockers:item.installable?[]:['Original file exceeds 1 MiB']};}
    if(p.method==='skillsLibraryInstall')data=installResponse;
    setTimeout(()=>PocketNative({id:p.id,ok:true,data}),0);
   }};boot();
  }''')
  self.page.wait_for_function('()=>state.config.model==="fixture-model"')
  self.navigate('settings');self.page.locator('#settingsSkills').click()
  self.page.locator('#skillLibraryPanel').evaluate('e=>e.open=true')
  self.page.wait_for_function('()=>skillLibraryUi.items.length===2')
 def select(self,id='skills/browser/agent-browser'):
  self.page.locator('[data-library-id="'+id+'"]').click()
  self.page.wait_for_function('(id)=>skillLibraryUi.selected?.id===id',arg=id)
 def test_source_search_and_original_resource_read_use_exact_ids_without_install_or_activation(self):
  self.library();self.assertIn('원본 210개',self.page.locator('#skillLibraryStatus').inner_text())
  self.page.locator('#skillLibrarySearch').fill('browser');self.assertEqual(self.page.locator('[data-library-id]').count(),1)
  self.select();self.assertIn('# <literal original>',self.page.locator('#skillLibraryContent').inner_text())
  self.page.locator('[data-library-file="assets/sample.bin"]').click()
  self.page.wait_for_function('()=>!skillLibraryUi.reading && !document.getElementById("skillLibraryResource").classList.contains("hidden")')
  self.assertIn('바이너리',self.page.locator('#skillLibraryResource').inner_text())
  self.assertEqual(self.page.evaluate('libraryPackets.filter(p=>p.method==="skillsLibraryRead").at(-1).data'),{'id':'skills/browser/agent-browser','file_path':'assets/sample.bin'})
  self.assertEqual(self.page.evaluate('libraryPackets.filter(p=>p.method==="skillsLibraryInstall").length'),0)
  self.assertEqual(self.page.evaluate('state.config.activeSkillName'),'owner-selected')
  self.assertEqual(self.page.locator('#skillLibraryContent script,#skillLibraryContent img').count(),0)
  self.assertEqual(self.errors,[])
 def test_cancel_unverified_and_capacity_failure_do_not_claim_original_saved_or_change_active_skill(self):
  self.library();self.select()
  for response,expected in [({'cancelled':True},'취소'),({},'확인하지 못했습니다'),({'imported':True,'builtin_id':'wrong-id','activated':False},'확인하지 못했습니다')]:
   self.page.evaluate('(response)=>installResponse=response',response);self.page.locator('#skillLibraryInstall').click()
   self.page.wait_for_function('()=>!skillPackageBusy');self.assertIn(expected,self.page.locator('#skillLibraryReason').inner_text())
  self.page.evaluate('''()=>{NativeBridge={postMessage(raw){const p=JSON.parse(raw);libraryPackets.push(p);setTimeout(()=>PocketNative({id:p.id,ok:false,data:{message:'256 skill capacity exceeded; owner packages preserved'}}),0)}}}''')
  self.page.locator('#skillLibraryInstall').click();self.page.wait_for_function('()=>!skillPackageBusy')
  self.assertIn('256 skill capacity exceeded',self.page.locator('#skillLibraryReason').inner_text())
  self.assertEqual(self.page.evaluate('state.config.activeSkillName'),'owner-selected')
  self.assertEqual(self.page.evaluate('libraryPackets.filter(p=>p.method==="skillsLibraryInstall").at(-1).data'),{'id':'skills/browser/agent-browser'})
  self.assertEqual(self.errors,[])
 def test_blocked_original_and_busy_owner_disable_install_but_success_requires_explicit_inactive_ack(self):
  self.library();self.select('skills/ml/oversize');self.assertTrue(self.page.locator('#skillLibraryInstall').is_disabled())
  self.assertIn('1 MiB',self.page.locator('#skillLibraryReason').inner_text())
  self.select();self.page.evaluate('state.busy=true;renderSkillLibraryControls()');self.assertTrue(self.page.locator('#skillLibraryInstall').is_disabled())
  self.page.evaluate("state.busy=false;renderSkillLibraryControls();installResponse={imported:true,builtin_id:'skills/browser/agent-browser',activated:false,skills:[{name:'agent-browser',description:'Original browser instructions'}]}")
  self.page.locator('#skillLibraryInstall').click();self.page.wait_for_function('()=>!skillPackageBusy')
  self.assertIn('직접 선택',self.page.locator('#skillLibraryReason').inner_text())
  self.assertEqual(self.page.evaluate('state.config.activeSkillName'),'owner-selected')
  self.assertEqual(self.errors,[])
