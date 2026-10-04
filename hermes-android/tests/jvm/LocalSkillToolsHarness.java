package dev.chanho.hermes;

import java.nio.file.*;
import org.json.*;

/** Real package mutations with explicit owner-state fixtures; script execution uses host shell. */
public final class LocalSkillToolsHarness {
    interface Work{void run()throws Exception;}
    static int passed;
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void test(String name,Work work)throws Exception{work.run();passed++;System.out.println("PASS "+name);}
    static void rejected(Work work)throws Exception{try{work.run();}catch(Exception expected){return;}throw new AssertionError("Unsafe skill mutation accepted");}
    static String doc(String name){return "---\nname: "+name+"\ndescription: Owner workflow\n---\n\n# Owner workflow\n";}
    static JSONObject operations(JSONObject... ops){return J.obj("operations",J.arr((Object[])ops));}
    public static void main(String[] unused)throws Exception{
        test("canonical create write patch read remove delete use real files and one approval per batch",()->{
            AgentRuntime r=new AgentRuntime();LocalAgentTools tools=r.localAgentTools;
            JSONObject result=tools.execute("skill_manage",operations(J.obj("action","create","name","canonical","content",doc("canonical")),J.obj("action","write_file","name","canonical","file_path","references/guide.txt","file_content","first first")));
            check(result.getBoolean("ok")&&result.getJSONObject("result").getBoolean("atomic")&&r.approvals.calls==1,"canonical batch not one approved atomic operation");
            tools.execute("skill_manage",operations(J.obj("action","patch","name","canonical","file_path","references/guide.txt","old_string","first","new_string","second","replace_all",true)));
            JSONObject read=tools.execute("skill_view",J.obj("name","canonical","file_path","references/guide.txt")).getJSONObject("result");check(read.getString("content").equals("second second")&&read.getBoolean("untrusted"),"linked resource unavailable to actual skill_view");
            JSONObject full=tools.execute("skill_view",J.obj("name","canonical")).getJSONObject("result");check(full.getJSONArray("linked_files").length()==1&&Files.isRegularFile(Path.of(full.getString("path"))),"skill package paths/files fabricated");
            tools.execute("skill_manage",operations(J.obj("action","remove_file","name","canonical","file_path","references/guide.txt"),J.obj("action","delete","name","canonical")));rejected(()->tools.skills.read("canonical"));
        });
        test("legacy saved body and delete remain usable",()->{
            AgentRuntime r=new AgentRuntime();r.localAgentTools.execute("skill_manage",J.obj("action","save","name","legacy","description","Existing user workflow","content","# Legacy body"));
            check(r.localAgentTools.skills.read("legacy").getString("content").contains("# Legacy body"),"existing saved-body client broken");
            r.localAgentTools.execute("skill_manage",J.obj("action","delete","name","legacy"));rejected(()->r.localAgentTools.skills.read("legacy"));
        });
        test("denied or invalid later operation leaves all packages untouched without command execution",()->{
            AgentRuntime r=new AgentRuntime();int initial=r.localAgentTools.skills.list().length();r.approvals.allow=false;
            JSONObject create=operations(J.obj("action","create","name","denied","content",doc("denied")));check(r.localAgentTools.execute("skill_manage",create).getBoolean("denied")&&r.localAgentTools.skills.list().length()==initial,"denied package appeared");
            r.approvals.allow=true;int approvals=r.approvals.calls;
            rejected(()->r.localAgentTools.execute("skill_manage",operations(J.obj("action","create","name","partial","content",doc("partial")),J.obj("action","write_file","name","partial","file_path","../escape","file_content","bad"))));
            check(r.approvals.calls==approvals&&r.localAgentTools.skills.list().length()==initial&&r.tools.executions==0,"invalid batch prompted or partially executed");
        });
        test("native approval revocation cancellation lock owner plugin and approval mode are rechecked",()->{
            for(int kind=0;kind<5;kind++){
                AgentRuntime r=new AgentRuntime();final int change=kind;r.approvals.onAsk=()->{if(change==0)r.net.cancel();if(change==1)r.unlocked=false;if(change==2)r.ownerBusy=false;if(change==3)r.store.put("fixture_config",J.obj("enabledPlugins",J.arr("device")).toString());if(change==4)r.store.put("approval_mode","auto");};
                rejected(()->r.localAgentTools.execute("skill_manage",operations(J.obj("action","create","name","revoked","content",doc("revoked")))));rejected(()->r.localAgentTools.skills.read("revoked"));check(r.approvals.calls==1,"revocation did not occur across actual approval wait");
            }
        });
        test("concurrent resource edit during approval is preserved and refuses stale transaction",()->{
            AgentRuntime r=new AgentRuntime();r.localAgentTools.skills.create("concurrent",doc("concurrent"));r.localAgentTools.skills.writeFile("concurrent","references/guide.txt","owner original");
            r.approvals.onAsk=()->{try{r.localAgentTools.skills.writeFile("concurrent","references/guide.txt","owner concurrent");}catch(Exception e){throw new RuntimeException(e);}};
            rejected(()->r.localAgentTools.execute("skill_manage",operations(J.obj("action","write_file","name","concurrent","file_path","references/guide.txt","file_content","proposed unreviewed"))));
            check(r.localAgentTools.skills.read("concurrent","references/guide.txt").getString("content").equals("owner concurrent"),"approval overwrote concurrent owner resource");
        });
        test("canonical operation types mixed legacy fields ambiguous patch and overlong batches reject before approval",()->{
            AgentRuntime r=new AgentRuntime();
            JSONObject[] bad={J.obj("operations",new JSONArray(),"action","save"),J.obj("operations","not-array"),operations(J.obj("action","write_file","name","x","file_path","references/x","file_content",17)),operations(J.obj("action","patch","name","x","content",doc("x"),"old_string","old","new_string","new")),operations(J.obj("action","patch","name","x","old_string","old","new_string","new","replace_all","true"))};
            for(JSONObject args:bad)rejected(()->r.localAgentTools.execute("skill_manage",args));JSONArray tooMany=new JSONArray();for(int i=0;i<33;i++)tooMany.put(J.obj("action","create","name","x"+i,"content",doc("x"+i)));rejected(()->r.localAgentTools.execute("skill_manage",J.obj("operations",tooMany)));
            String oversizedPath="references/"+"a/".repeat(260)+"file.txt";
            rejected(()->LocalAgentTools.skillOperations(operations(J.obj("action","write_file","name","x","file_path",oversizedPath,"file_content","bounded text"))));
            check(r.approvals.calls==0,"malformed canonical request reached approval");
        });
        test("read linked shell script never executes but separately approved real terminal invocation does",()->{
            AgentRuntime r=new AgentRuntime();Path marker=r.context.getFilesDir().toPath().resolve("script-executed-marker");
            r.localAgentTools.skills.create("scripted",doc("scripted"));r.localAgentTools.skills.writeFile("scripted","scripts/run.sh","printf 'actual script execution' > "+marker+"\n");
            JSONObject script=r.localAgentTools.execute("skill_view",J.obj("name","scripted","file_path","scripts/run.sh")).getJSONObject("result");check(!Files.exists(marker)&&r.approvals.calls==0,"reading skill script executed or granted approval");
            try(TerminalTools terminal=new TerminalTools(r,new TerminalSessions(Files.createTempDirectory("hermes-linked-script-shell-").toFile(),"/bin/sh","script"))){
                r.approvals.allow=false;check(terminal.execute("terminal",J.obj("command","/bin/sh "+script.getString("path"))).getBoolean("denied")&&!Files.exists(marker),"script bypassed separate terminal approval");
                r.approvals.allow=true;JSONObject result=terminal.execute("terminal",J.obj("command","/bin/sh "+script.getString("path")));check(result.getInt("exit_code")==0&&Files.readString(marker).equals("actual script execution"),"approved real host script failed to execute");
            }
        });
        test("real streamed provider tool call carries full 100k Unicode SKILL document into approved native adapter",()->{
            String prefix="---\nname: streamed\ndescription: Streamed document\n---\n";String document=prefix+"한".repeat(100000-prefix.length());
            JSONObject args=operations(J.obj("action","create","name","streamed","content",document));
            String stream=DirectAgentHarness.frame(J.obj("choices",J.arr(J.obj("delta",J.obj("tool_calls",J.arr(J.obj("index",0,"id","provider-skill-call","function",J.obj("name","skill_manage","arguments",args.toString())))),"finish_reason","tool_calls"))))+"data: [DONE]\n\n";
            try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(stream,DirectAgentHarness.text("saved real document","stop"))){
                AgentRuntime r=new AgentRuntime();new DirectAgent(r).run("streamed-skill","create owner skill",api.config().put("modelContextTokens",1000000).put("modelOutputTokens",8192).put("modelLimitsSource","api"),"");
                check(r.approvals.calls==1&&r.localAgentTools.skills.read("streamed").getString("content").equals(document),"large provider tool arguments lost or failed actual native package mutation");
                check(api.requests.size()==2,"large document prevented matching tool result followup");
            }
        });
        System.out.println("LocalSkillTools: "+passed+" production operation/approval checks passed; explicit script execution uses HOST /bin/sh, not Android proof.");
    }
}
