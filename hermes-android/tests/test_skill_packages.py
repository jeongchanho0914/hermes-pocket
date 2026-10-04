"""Skill package editor bridge fixtures. Native ZIP/filesystem proof is separate."""
import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
import test_ui as ui

class SkillPackageUITest(unittest.TestCase):
    setUpClass=classmethod(ui.UITest.setUpClass.__func__)
    tearDownClass=classmethod(ui.UITest.tearDownClass.__func__)
    setUp=ui.UITest.setUp;tearDown=ui.UITest.tearDown
    navigate=ui.UITest.navigate;open_provider_details=ui.UITest.open_provider_details
    def bridge(self):
        self.page.evaluate('''()=>{
          window.testPackets=[];window.docFixture='---\\nname: community\\ndescription: Original description\\nmetadata:\\n  owner: fixture\\n---\\n\\n# Owner body\\n';
          window.resourceFixture={'references/guide.md':'original reference','scripts/run.sh':'echo explicit-only\\n'};
          window.importResponse={cancelled:true};window.exportResponse={cancelled:true};window.resourceResponse=null;
          window.testConfig={mode:'direct',providerId:'custom',endpoint:'https://fixture.example/v1',model:'fixture-model',enabledPlugins:['agent','terminal']};
          const files=()=>Object.entries(resourceFixture).map(([file_path,content])=>({file_path,bytes:content.length}));
          NativeBridge={postMessage(raw){const p=JSON.parse(raw);testPackets.push(p);let data={};
            if(p.method==='boot')data={config:testConfig,sessions:[],audit:[],device:{},version:'0.10',busy:false};
            if(p.method==='skillsList')data=[{name:'community',description:'Original description'}];
            if(p.method==='skillsRead')data=p.data.file_path?{file_path:p.data.file_path,content:resourceFixture[p.data.file_path],encoding:'utf-8'}:{name:p.data.name,description:'Original description',content:docFixture,files:files(),skill_directory:'/fixture/skills/community'};
            if(p.method==='skillsResources')data=files();
            if(p.method==='skillsSave'){docFixture=p.data.content;data={saved:true,name:p.data.name,skills:[{name:p.data.name,description:'Original description'}]};}
            if(p.method==='skillsImport')data=importResponse;
            if(p.method==='skillsExport')data=exportResponse;
            if(p.method==='runTool'){
              const op=p.data.arguments.operations[0];data=resourceResponse;
              if(data===null){if(op.action==='write_file')resourceFixture[op.file_path]=op.file_content;else delete resourceFixture[op.file_path];
                data={ok:true,result:{atomic:true,count:1,operations:[{action:op.action,name:op.name,file_path:op.file_path,written:op.action==='write_file',removed:op.action==='remove_file'}]}};}
            }
            setTimeout(()=>PocketNative({id:p.id,ok:true,data}),0);
          }};boot();
        }''')
        self.page.wait_for_function('()=>state.config.model==="fixture-model"')
    def open_skill(self):
        self.navigate('settings');self.page.locator('#settingsSkills').click()
        self.page.locator('[data-skill-name=community] summary').click()
        self.page.locator('[data-skill-name=community] [data-skill-action=edit]').click()
        self.page.wait_for_function('()=>editingSkill==="community"')
        self.page.locator('#skillResourcesPanel').evaluate('e=>e.open=true')
        self.page.locator('#skillPackageName').evaluate('e=>e.closest("details").open=true')
    def test_full_yaml_editor_preserves_metadata_in_native_save(self):
        self.bridge();self.open_skill();original=self.page.evaluate('docFixture')
        self.assertEqual(self.page.locator('#skillContent').input_value(),original)
        self.assertTrue(self.page.locator('#skillDescription').evaluate('e=>e.readOnly'))
        revised=original.replace('# Owner body','# Updated body')
        self.page.locator('#skillContent').fill(revised);self.page.locator('#saveSkill').click()
        self.page.wait_for_function('()=>!skillSaving && testPackets.some(p=>p.method==="skillsSave")')
        packet=self.page.evaluate('testPackets.find(p=>p.method==="skillsSave").data')
        self.assertEqual(packet,{'name':'community','description':'Original description','content':revised})
        self.assertEqual(self.errors,[])
    def test_resource_read_write_and_remove_send_canonical_operation_arrays_without_model_key(self):
        self.bridge();self.navigate('settings');self.page.locator('#settingsConnection').click();self.open_provider_details()
        self.page.locator('#token').fill('unsaved-resource-model-fixture-key');self.open_skill()
        self.page.locator('[data-file-path="references/guide.md"]').click()
        self.page.wait_for_function('()=>!skillResourceBusy && document.getElementById("skillResourceContent").value==="original reference"')
        self.assertEqual(self.page.evaluate('testPackets.filter(p=>p.method==="skillsRead").at(-1).data'),{'name':'community','file_path':'references/guide.md'})
        self.page.locator('#skillResourceContent').fill('new reference 한글 <literal>');self.page.locator('#saveSkillResource').click()
        self.page.wait_for_function('()=>!skillResourceBusy && testPackets.some(p=>p.method==="runTool")')
        self.assertEqual(self.page.evaluate('testPackets.filter(p=>p.method==="runTool").at(-1).data'),{'name':'skill_manage','arguments':{'operations':[{'action':'write_file','name':'community','file_path':'references/guide.md','file_content':'new reference 한글 <literal>'}]}})
        self.page.once('dialog',lambda d:d.accept());self.page.locator('#deleteSkillResource').click()
        self.page.wait_for_function('()=>!skillResourceBusy && testPackets.filter(p=>p.method==="runTool").length===2')
        self.assertEqual(self.page.evaluate('testPackets.filter(p=>p.method==="runTool").at(-1).data'),{'name':'skill_manage','arguments':{'operations':[{'action':'remove_file','name':'community','file_path':'references/guide.md'}]}})
        self.assertEqual(self.page.locator('#token').input_value(),'unsaved-resource-model-fixture-key')
        self.assertNotIn('unsaved-resource-model-fixture-key',str(self.page.evaluate('testPackets.filter(p=>p.method==="runTool")')))
        self.assertEqual(self.errors,[])
    def test_import_export_cancel_and_unverified_response_never_claim_saved_package(self):
        self.bridge();self.open_skill();original=self.page.locator('#skillContent').input_value()
        self.page.locator('#skillPackageName').fill('incoming');self.page.locator('#skillImportReplace').check();self.page.locator('#importSkill').click()
        self.page.wait_for_function('()=>!skillPackageBusy && testPackets.some(p=>p.method==="skillsImport")')
        self.assertEqual(self.page.evaluate('testPackets.find(p=>p.method==="skillsImport").data'),{'name':'incoming','replace':True})
        self.assertIn('취소',self.page.locator('#skillStatus').inner_text());self.assertEqual(self.page.locator('#skillContent').input_value(),original)
        self.page.locator('#exportSkill').click();self.page.wait_for_function('()=>!skillPackageBusy && testPackets.some(p=>p.method==="skillsExport")')
        self.assertEqual(self.page.evaluate('testPackets.find(p=>p.method==="skillsExport").data'),{'name':'community'})
        self.assertIn('취소',self.page.locator('#skillStatus').inner_text())
        self.page.evaluate('importResponse={};exportResponse={}')
        self.page.locator('#importSkill').click();self.page.wait_for_function('()=>!skillPackageBusy')
        self.assertIn('확인하지 못했습니다',self.page.locator('#skillStatus').inner_text())
        self.page.locator('#exportSkill').click();self.page.wait_for_function('()=>!skillPackageBusy')
        self.assertIn('확인하지 못했습니다',self.page.locator('#skillStatus').inner_text())
        self.assertEqual(self.errors,[])
    def test_failed_resource_save_preserves_draft_and_disabled_plugin_blocks_editor(self):
        self.bridge();self.open_skill()
        self.page.locator('#skillResourcePath').fill('references/new.txt');self.page.locator('#skillResourceContent').fill('unsaved owner draft')
        self.page.evaluate("resourceResponse={ok:false,error:'fixture resource approval denied'}")
        self.page.locator('#saveSkillResource').click();self.page.wait_for_function('()=>!skillResourceBusy && testPackets.some(p=>p.method==="runTool")')
        self.assertIn('fixture resource approval denied',self.page.locator('#skillResourceStatus').inner_text())
        self.assertEqual(self.page.locator('#skillResourceContent').input_value(),'unsaved owner draft')
        self.page.evaluate("state.config.availableTools={names:[]};renderSkillPackageControls()")
        self.assertTrue(self.page.locator('#saveSkillResource').is_disabled())
        self.assertTrue(self.page.locator('#deleteSkillResource').is_disabled())
        self.assertEqual(self.errors,[])
    def test_unverified_resource_response_does_not_claim_write_succeeded(self):
        self.bridge();self.open_skill();self.page.locator('#skillResourcePath').fill('references/new.txt')
        self.page.locator('#skillResourceContent').fill('owner draft');self.page.evaluate('resourceResponse={}')
        self.page.locator('#saveSkillResource').click();self.page.wait_for_function('()=>!skillResourceBusy && testPackets.some(p=>p.method==="runTool")')
        self.assertNotIn('파일을 저장했습니다',self.page.locator('#skillResourceStatus').inner_text())
        self.assertEqual(self.page.locator('#skillResourceContent').input_value(),'owner draft')
        self.assertEqual(self.errors,[])
