package dev.chanho.hermes;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;

/** Real DirectAgent and HTTP; capture pixels are an explicit test substitute. */
public final class ScreenVisionHarness {
    static final String PNG="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aY9sAAAAASUVORK5CYII=";
    static final String IMAGE="data:image/png;base64,"+PNG;
    static String capture(String id){
        return DirectAgentHarness.frame(J.obj("choices",J.arr(J.obj("delta",J.obj("tool_calls",J.arr(J.obj("index",0,"id",id,"function",J.obj("name","capture_screen","arguments","{}")))),"finish_reason","tool_calls"))))+"data: [DONE]\n\n";
    }
    static JSONObject captured(){return J.obj("ok",true,"result",J.obj("package","dev.hermesfixture.android","snapshot","fresh-008","imageDataURL",IMAGE,"redacted",true));}
    static void check(boolean value,String why){DirectAgentHarness.check(value,why);}
    static int imageParts(JSONArray messages){
        int count=0;
        for(int i=0;i<messages.length();i++){
            JSONArray content=messages.optJSONObject(i).optJSONArray("content");if(content==null)continue;
            for(int j=0;j<content.length();j++)if("image_url".equals(content.optJSONObject(j).optString("type")))count++;
        }return count;
    }
    static void privateState(AgentRuntime runtime,String sid){
        String saved=runtime.store.transcript(sid).toString();
        check(!saved.contains(PNG)&&!saved.contains("imageDataURL")&&!saved.contains("image_url"),"pixels persisted in transcript");
        check(!runtime.events.toString().contains(PNG)&&!runtime.store.audits.toString().contains(PNG),"pixels exposed to UI/audit");
    }
    public static void main(String[] args)throws Exception{
        DirectAgentHarness.passed=0;
        DirectAgentHarness.test("screen pixels travel as typed image content and never persist or replay next run",()->{
            try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(capture("vision-1"),DirectAgentHarness.text("pixels inspected","stop"),DirectAgentHarness.text("followup","stop"))){
                AgentRuntime r=new AgentRuntime();r.tools.responder=(name,a)->captured();
                DirectAgent agent=new DirectAgent(r);agent.run("vision","inspect canvas",api.config(),"");
                JSONArray messages=api.requests.get(1).getJSONArray("messages");
                JSONObject observation=messages.getJSONObject(messages.length()-1);
                check(observation.getString("role").equals("user")&&!observation.has("_screenSupplement"),"private marker exposed or image role wrong");
                JSONArray parts=observation.getJSONArray("content");
                check(parts.getJSONObject(0).getString("type").equals("text"),"image lacks text context");
                JSONObject image=parts.getJSONObject(1);check(image.getString("type").equals("image_url"),"pixels sent as plain text");
                String url=image.getJSONObject("image_url").getString("url");
                byte[] bytes=Base64.getDecoder().decode(url.substring(url.indexOf(',')+1));
                check(bytes.length>8&&bytes[0]==(byte)137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71,"typed image bytes corrupted");
                check(!messages.getJSONObject(messages.length()-2).getString("content").contains(PNG),"pixels leaked in tool text");
                privateState(r,"vision");agent.run("vision","continue",api.config(),"");
                check(!api.requests.get(2).toString().contains(PNG),"prior capture replayed on subsequent run");privateState(r,"vision");
            }
        });
        DirectAgentHarness.test("image unsupported API stops with truthful error without tree fallback retry",()->{
            try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(capture("unsupported"))){
                api.validator=request->request.toString().contains("image_url")?"fixture model does not support images":null;
                AgentRuntime r=new AgentRuntime();r.tools.responder=(name,a)->captured();
                try{new DirectAgent(r).run("unsupported","inspect",api.config(),"");throw new AssertionError("image rejection accepted");}
                catch(IllegalStateException expected){check(expected.getMessage().contains("이미지 입력 지원")&&expected.getMessage().contains("화면을 보았다고 간주할 수 없습니다"),"unsupported image falsely reported as seen");}
                check(api.requests.size()==2&&r.tools.executions==1,"image API rejection retried or silently fell back");privateState(r,"unsupported");
            }
        });
        DirectAgentHarness.test("cancel immediately after capture sends no pixel HTTP request or success answer",()->{
            try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(capture("cancelled"),DirectAgentHarness.text("must not arrive","stop"))){
                AgentRuntime r=new AgentRuntime();r.tools.responder=(name,a)->{r.net.cancel();return captured();};
                try{new DirectAgent(r).run("cancelled","inspect",api.config(),"");throw new AssertionError("capture cancellation ignored");}
                catch(InterruptedException expected){}
                check(api.requests.size()==1&&!r.store.transcript("cancelled").toString().contains("must not arrive"),"cancelled capture uploaded or completed");privateState(r,"cancelled");
            }
        });
        DirectAgentHarness.test("multiple captures send only fresh pixels and do not treat supplements as user turn boundaries",()->{
            try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(capture("first"),capture("second"),DirectAgentHarness.text("fresh inspected","stop"))){
                AgentRuntime r=new AgentRuntime();AtomicInteger n=new AtomicInteger();
                r.tools.responder=(name,a)->J.obj("ok",true,"result",J.obj("snapshot","snapshot-"+n.incrementAndGet(),"imageDataURL",IMAGE));
                new DirectAgent(r).run("fresh","inspect twice",api.config(),"");
                JSONArray messages=api.requests.get(2).getJSONArray("messages");check(imageParts(messages)==1,"more than the latest capture image was transmitted");
                check(messages.getJSONObject(messages.length()-1).getJSONArray("content").getJSONObject(0).getString("text").contains("second"),"image bound to stale observation");
                privateState(r,"fresh");
            }
            JSONArray oversized=J.arr(J.obj("role","system","content","required"),J.obj("role","user","content","x".repeat(2000)),J.obj("role","user","_screenSupplement","capture","content","pixels context"));
            DirectAgentHarness.fails(()->DirectAgent.trimHistory(oversized,400),"capture observation discarded original instruction as an old user turn");
        });
        DirectAgentHarness.test("capture followed by UI action in same batch discards pre-action pixels",()->{
            JSONArray calls=J.arr(J.obj("index",0,"id","before-action","function",J.obj("name","capture_screen","arguments","{}")),
                J.obj("index",1,"id","mutation","function",J.obj("name","press_back","arguments","{}")));
            String batch=DirectAgentHarness.frame(J.obj("choices",J.arr(J.obj("delta",J.obj("tool_calls",calls),"finish_reason","tool_calls"))))+"data: [DONE]\n\n";
            try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(batch,DirectAgentHarness.text("need fresh capture","stop"))){
                AgentRuntime r=new AgentRuntime();r.tools.responder=(name,a)->name.equals("capture_screen")?captured():J.obj("ok",true,"result",J.obj("dispatched",true,"snapshot","after-action"));
                new DirectAgent(r).run("batch","inspect then navigate",api.config(),"");
                check(r.tools.executions==2,"batch fixture did not execute capture and UI action");
                String request=api.requests.get(1).toString();check(!request.contains(PNG)&&!request.contains("image_url"),"pre-action image uploaded after UI changed");
                check(request.contains("after-action"),"fresh action result missing");privateState(r,"batch");
            }
        });
        DirectAgentHarness.test("UI action followed by capture in same batch sends new pixels after complete tool replies",()->{
            JSONArray calls=J.arr(J.obj("index",0,"id","mutation","function",J.obj("name","press_back","arguments","{}")),
                J.obj("index",1,"id","after-action","function",J.obj("name","capture_screen","arguments","{}")));
            String batch=DirectAgentHarness.frame(J.obj("choices",J.arr(J.obj("delta",J.obj("tool_calls",calls),"finish_reason","tool_calls"))))+"data: [DONE]\n\n";
            try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(batch,DirectAgentHarness.text("fresh pixels","stop"))){
                AgentRuntime r=new AgentRuntime();r.tools.responder=(name,a)->name.equals("capture_screen")?captured():J.obj("ok",true,"result",J.obj("dispatched",true));
                new DirectAgent(r).run("batch-fresh","navigate then inspect",api.config(),"");
                JSONArray messages=api.requests.get(1).getJSONArray("messages");int length=messages.length();
                check(messages.getJSONObject(length-3).getString("role").equals("tool")&&messages.getJSONObject(length-2).getString("role").equals("tool"),"image supplement interrupts required tool reply group");
                JSONObject observation=messages.getJSONObject(length-1);check(observation.getJSONArray("content").getJSONObject(1).getJSONObject("image_url").getString("url").equals(IMAGE),"fresh post-action capture lost");privateState(r,"batch-fresh");
            }
        });
        DirectAgentHarness.test("canonical terminal and process input mutations invalidate earlier capture in same batch",()->{
            for(String action:new String[]{"terminal","write","submit","kill","close"}){
                String name=action.equals("terminal")?"terminal":"process_manage";JSONObject terminalArgs=action.equals("terminal")?J.obj("command","printf fixture"):J.obj("action",action,"session_id","proc_fixture");
                JSONArray calls=J.arr(J.obj("index",0,"id","before-terminal","function",J.obj("name","capture_screen","arguments","{}")),
                    J.obj("index",1,"id","mutation","function",J.obj("name",name,"arguments",terminalArgs.toString())));
                String batch=DirectAgentHarness.frame(J.obj("choices",J.arr(J.obj("delta",J.obj("tool_calls",calls),"finish_reason","tool_calls"))))+"data: [DONE]\n\n";
                try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(batch,DirectAgentHarness.text("capture again","stop"))){
                    AgentRuntime r=new AgentRuntime();r.tools.responder=(tool,a)->tool.equals("capture_screen")?captured():J.obj("ok",true,"result",J.obj("status","fixture-mutated"));
                    new DirectAgent(r).run("terminal-"+action,"inspect then execute",api.config(),"");
                    check(r.tools.executions==2&&!api.requests.get(1).toString().contains(PNG),"terminal/process mutation retained stale image: "+action);
                }
            }
        });
        DirectAgentHarness.test("all eight new phone and intent tools discard pixels captured before a changing action",()->{
            for(String name:new String[]{"long_click_element","set_element_progress","perform_phone_action","open_link","open_map","share_text","compose_message","set_alarm"}){
                JSONArray calls=J.arr(J.obj("index",0,"id","before-action","function",J.obj("name","capture_screen","arguments","{}")),J.obj("index",1,"id","phone-action","function",J.obj("name",name,"arguments","{}")));
                String batch=DirectAgentHarness.frame(J.obj("choices",J.arr(J.obj("delta",J.obj("tool_calls",calls),"finish_reason","tool_calls"))))+"data: [DONE]\n\n";
                try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(batch,DirectAgentHarness.text("fresh screen required","stop"))){
                    AgentRuntime r=new AgentRuntime();r.tools.responder=(tool,a)->tool.equals("capture_screen")?captured():J.obj("ok",true,"result",J.obj("dispatched",true,"verified",false));
                    new DirectAgent(r).run("phone-"+name,"inspect then act",api.config(),"");
                    check(r.tools.executions==2&&!api.requests.get(1).toString().contains(PNG),"changing phone tool retained stale pixels: "+name);
                }
            }
        });
        DirectAgentHarness.test("canonical process polling preserves capture because observing output does not mutate screen",()->{
            JSONArray calls=J.arr(J.obj("index",0,"id","fresh","function",J.obj("name","capture_screen","arguments","{}")),
                J.obj("index",1,"id","poll","function",J.obj("name","process_manage","arguments",J.obj("action","poll","session_id","proc_fixture").toString())));
            String batch=DirectAgentHarness.frame(J.obj("choices",J.arr(J.obj("delta",J.obj("tool_calls",calls),"finish_reason","tool_calls"))))+"data: [DONE]\n\n";
            try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(batch,DirectAgentHarness.text("pixels inspected","stop"))){
                AgentRuntime r=new AgentRuntime();r.tools.responder=(tool,a)->tool.equals("capture_screen")?captured():J.obj("ok",true,"result",J.obj("status","fixture-observed"));
                new DirectAgent(r).run("poll-keeps-image","inspect and poll",api.config(),"");
                check(r.tools.executions==2&&imageParts(api.requests.get(1).getJSONArray("messages"))==1,"read-only process polling discarded current image");
            }
        });
        System.out.println("ScreenVision: "+DirectAgentHarness.passed+" production agent checks passed; Android pixel capture is a substitute.");
    }
}
