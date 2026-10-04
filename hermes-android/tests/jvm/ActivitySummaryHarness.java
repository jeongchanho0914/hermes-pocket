package dev.chanho.hermes;

import org.json.*;

/** Production public timeline summaries; native database persistence is separate Android evidence. */
public final class ActivitySummaryHarness {
    interface Work{void run()throws Exception;}
    static int passed;
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void test(String name,Work work)throws Exception{work.run();passed++;System.out.println("PASS "+name);}
    public static void main(String[] unused)throws Exception{
        test("argument summaries exclude raw input document content credentials and pixel payloads",()->{
            JSONObject args=J.obj("action","write_file","name","community","file_path","scripts/run.sh","file_content","raw-script-private","text","raw-input-private","data","raw-stdin-private","headers",J.obj("Authorization","Bearer fixture-secret"),"imageDataURL","data:image/png;base64,private-pixels","operations",J.arr(J.obj("content","private-doc")));
            String summary=DirectAgent.toolArgumentSummary("skill_manage",args);
            check(summary.contains("community")&&summary.contains("scripts/run.sh"),"safe activity identity omitted");
            for(String secret:new String[]{"raw-script-private","raw-input-private","raw-stdin-private","fixture-secret","private-pixels","private-doc"})check(!summary.contains(secret),"private field leaked in timeline: "+secret);
            for(String command:new String[]{"export API_KEY=fixture-secret","printf 'Bearer fixture-secret'","printf 'data:image/png;base64,private-pixels'","password=private-value","curl https://user:private@example.com"})check(!DirectAgent.toolArgumentSummary("terminal",J.obj("command",command)).contains(command),"sensitive command exposed");
            check(DirectAgent.toolArgumentSummary("terminal",J.obj("command","printf done")).contains("printf done"),"ordinary command summary unhelpfully missing");
            check(DirectAgent.toolArgumentSummary("terminal",J.obj("command","x".repeat(2000),"name","n".repeat(2000))).length()<=640,"argument summary not bounded");
        });
        test("public response summary is bounded single-line and does not expose credential or image patterns",()->{
            String summary=DirectAgent.publicResponseSummary("public answer\nwith\tspacing "+"x".repeat(500));check(summary.length()<=241&&!summary.contains("\n")&&!summary.contains("\t"),"public summary unbounded or multiline");
            for(String value:new String[]{"API key fixture-private","Bearer fixture-private","data:image/png;base64,fixture-private","password fixture-private"})check(!DirectAgent.publicResponseSummary(value).contains("fixture-private"),"public summary exposes sensitive material");
        });
        test("activity completion distinguishes nonzero exit denied timeout background and unverified dispatch",()->{
            for(JSONObject failure:new JSONObject[]{J.obj("ok",false,"error","private-error"),J.obj("denied",true),J.obj("exit_code",7),J.obj("ok",true,"result",J.obj("status","timeout")),J.obj("result",J.obj("status","cancelled"))})check(DirectAgent.toolResultFailed(failure),"failure result shown as completed");
            JSONObject success=J.obj("ok",true,"result",J.obj("exit_code",0,"output","private-output","imageDataURL","data:image/png;base64,private-pixels","reasoning_content","private-reasoning"));
            check(!DirectAgent.toolResultFailed(success),"zero exit falsely shown as failure");String summary=DirectAgent.toolResultSummary(success);
            check(!summary.contains("private-"),"raw output pixels or reasoning included in activity summary");
            check(DirectAgent.toolResultSummary(J.obj("result",J.obj("running",true))).contains("백그라운드"),"running background job shown as completed");
            check(DirectAgent.toolResultSummary(J.obj("result",J.obj("verified",false))).contains("확인 필요"),"dispatch-only result falsely confirmed");
            check(DirectAgent.toolResultSummary(J.obj("denied",true)).contains("실행하지 않았습니다"),"denial falsely indicates execution");
        });
        test("concrete search and terminal summaries retain observed metadata while rejecting sensitive queries and output",()->{
            JSONObject search=J.obj("ok",true,"result",J.obj("query","Android background jobs","results",J.arr(J.obj("url","https://developer.android.com/path?private=value","content","private-page-body"),J.obj("url","https://example.org/doc"))));
            String summary=DirectAgent.toolResultSummary("web_search",J.obj("query","Android background jobs","mode","api"),search);
            check(summary.contains("Android background jobs")&&summary.contains("결과 2개")&&summary.contains("developer.android.com")&&!summary.contains("private-"),"concrete search evidence missing or raw source leaked");
            check(DirectAgent.toolStartSummary("web_search",J.obj("query","Android background jobs")).contains("Android background jobs"),"public start omits safe task");
            check(!DirectAgent.toolStartSummary("web_search",J.obj("query","password fixture-private")).contains("fixture-private"),"sensitive query in pending card");
            check(!DirectAgent.toolArgumentSummary("web_fetch",J.obj("url","https://example.org/private?token=secret")).contains("secret"),"fetch URL query leaked");
            JSONObject terminal=J.obj("ok",true,"result",J.obj("backend","app","session_id","actual-job-7","exit_code",0,"output","Verified file count: 3\n"));
            String shell=DirectAgent.toolResultSummary("terminal",J.obj("backend","app"),terminal);
            check(shell.contains("actual-job-7")&&shell.contains("종료 코드 0")&&shell.contains("Verified file count: 3"),"observed terminal metadata absent");
            for(String output:new String[]{"Authorization: Bearer fixture-private","API_KEY=fixture-private","data:image/png;base64,fixture-private","A".repeat(100)}){
                terminal.getJSONObject("result").put("output",output);String safe=DirectAgent.toolResultSummary("terminal",new JSONObject(),terminal);check(!safe.contains("fixture-private")&&!safe.contains("A".repeat(80)),"private/blob terminal output leaked");
            }
        });
        test("screen and skill summaries expose actual identities and counts without contents or unconfirmed success",()->{
            String screen=DirectAgent.toolResultSummary("capture_screen",new JSONObject(),J.obj("ok",true,"result",J.obj("package","dev.fixture","width",393,"height",852,"elements",J.arr(J.obj("text","private-screen-text")),"imageDataURL","private-pixels","verified",false)));
            check(screen.contains("dev.fixture")&&screen.contains("화면 요소 1개")&&screen.contains("393×852")&&screen.contains("확인 필요")&&!screen.contains("private-"),"screen summary leaks pixels/text or confirms dispatch");
            JSONArray operations=J.arr(J.obj("action","write_file","name","community","file_content","private-script"));
            String skill=DirectAgent.toolResultSummary("skill_manage",J.obj("operations",operations),J.obj("ok",true,"result",J.obj("atomic",true,"operations",operations)));
            check(skill.contains("스킬 작업 1개")&&skill.contains("write_file community")&&!skill.contains("private-script"),"skill summary lacks observed action or exposes script");
        });
        test("intent activity metadata excludes private recipients coordinates URLs and shared bodies while retaining dispatch uncertainty",()->{
            JSONObject privateArgs=J.obj("recipient","private-recipient","text","private-body","subject","private-subject","label","private-label","query","private-place","url","https://example.org/private-path","latitude",12.34567,"longitude",76.54321,"kind","sms","hour",7,"minute",30);
            for(String name:new String[]{"open_link","open_map","share_text","compose_message","set_alarm"}){
                String summary=DirectAgent.toolArgumentSummary(name,privateArgs)+DirectAgent.toolStartSummary(name,privateArgs)+DirectAgent.toolResultSummary(name,privateArgs,J.obj("ok",true,"result",J.obj("dispatched",true,"verified",false,"handlerPackage","dev.fixture.handler","requiresUserCompletion",true)));
                for(String value:new String[]{"private-","12.34567","76.54321"})check(!summary.contains(value),"private intent data persisted in activity: "+name);
                check(summary.contains("확인 필요"),"intent dispatch falsely confirms target completion");
            }
            String volume=DirectAgent.toolResultSummary("set_volume",J.obj("stream","alarm","percent",47),J.obj("ok",true,"result",J.obj("stream","alarm","actual",50,"verified",true)));
            check(volume.contains("alarm")&&volume.contains("50"),"actual rounded Android volume omitted from summary");
        });
        test("real provider call IDs reach execution or rejection hooks and only public content reaches response hook",()->{
            String publicPart=DirectAgentHarness.frame(J.obj("choices",J.arr(J.obj("delta",J.obj("content","Checking actual state","reasoning_content","private hidden reasoning")))));
            try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(publicPart+DirectAgentHarness.tool("actual-provider-call","{}"),DirectAgentHarness.text("finished","stop"))){
                AgentRuntime r=new AgentRuntime();new DirectAgent(r).run("activity-callback","inspect",api.config(),"");
                check(r.executedCallIds.equals(java.util.Arrays.asList("actual-provider-call")),"execution hook lost actual provider call ID");
                check(r.publicResponses.equals(java.util.Arrays.asList("Checking actual state")),"hidden reasoning or invented response reached timeline hook");
            }
            try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(DirectAgentHarness.tool("actual-rejected-call","{}"),DirectAgentHarness.text("disabled tool unavailable","stop"))){
                AgentRuntime r=new AgentRuntime();new DirectAgent(r).run("activity-rejected","inspect",api.config().put("enabledPlugins",new JSONArray()),"");
                check(r.executedCallIds.isEmpty()&&r.rejectedCallIds.equals(java.util.Arrays.asList("actual-rejected-call")),"rejected call invented execution or lost provider ID");
            }
        });
        System.out.println("ActivitySummary: "+passed+" production privacy/status checks passed; no Android database claim.");
    }
}
