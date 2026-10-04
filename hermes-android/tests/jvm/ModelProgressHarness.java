package dev.chanho.hermes;
import org.json.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
/** Real loopback SSE observes production public MiMo output, distinct from unsupported reasoning fields. */
public final class ModelProgressHarness {
 interface Work{void run()throws Exception;}static int passed;
 static void check(boolean v,String why){if(!v)throw new AssertionError(why);}static void test(String name,Work w)throws Exception{w.run();passed++;System.out.println("PASS "+name);}
 static String thought(String value){return DirectAgentHarness.frame(J.obj("choices",J.arr(J.obj("delta",J.obj("reasoning_content",value)))));}
 static List<String> phases(AgentRuntime r){List<String> out=new ArrayList<>();for(JSONObject e:r.events)if(e.getString("event").equals("modelProgress"))out.add(e.getJSONObject("data").getString("phase"));return out;}
 static List<JSONObject> thoughts(AgentRuntime r){List<JSONObject> out=new ArrayList<>();for(JSONObject e:r.events)if(e.getString("event").equals("providerThought"))out.add(e.getJSONObject("data"));return out;}
 static void failure(Work w)throws Exception{try{w.run();}catch(Exception expected){return;}throw new AssertionError("Expected actual stream failure");}
 public static void main(String[] unused)throws Exception{
  test("documented MiMo public thought arrives over 50ms SSE chunks before answer bytes and latest thought is flushed",()->{
   try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(thought("먼저 현재 화면을 확인합니다. ")+thought("확인한 결과로 답변합니다.")+DirectAgentHarness.text("공개 답변","stop"))){
    api.chunkDelayMs=50;AgentRuntime r=new AgentRuntime();AtomicBoolean early=new AtomicBoolean();r.eventObserver=e->{if(e.optString("event").equals("providerThought")&&!api.answerFrameSent.get())early.set(true);};
    new DirectAgent(r).run("mimo-live","request",api.config().put("providerId","xiaomi"),"");
    check(early.get(),"first public thought was buffered until answer");List<JSONObject> publicText=thoughts(r);check(!publicText.isEmpty()&&publicText.get(publicText.size()-1).getString("text").equals("먼저 현재 화면을 확인합니다. 확인한 결과로 답변합니다."),"final public thought delta lost");
    check(publicText.get(0).getString("source").equals("mimo.reasoning_content"),"thought provenance absent");check(r.liveText().equals("공개 답변"),"thought mixed into public answer");
    check(phases(r).equals(Arrays.asList("sending","receiving","thinking","answering","completed")),"observed stream phases wrong");
   }
  });
  test("non MiMo reasoning-only first bytes update phase without exposing text while actual tool preparation is observed",()->{
   try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(thought("unsupported-private-reasoning")+DirectAgentHarness.tool("actual-call","{}"),DirectAgentHarness.text("actual answer","stop"))){
    api.chunkDelayMs=50;AgentRuntime r=new AgentRuntime();new DirectAgent(r).run("ordinary","request",api.config(),"");
    check(thoughts(r).isEmpty()&&!r.events.toString().contains("unsupported-private-reasoning"),"unsupported provider reasoning leaked");check(phases(r).containsAll(Arrays.asList("sending","receiving","thinking","tool_preparing","answering","completed")),"real reasoning/tool phase omitted");check(r.tools.executions==1,"actual tool dispatch absent");
   }
  });
  test("actual stream progress bounds public text redacts credentials and throttles repetitive phases without losing final delta",()->{
   List<String> phase=new ArrayList<>();List<String> text=new ArrayList<>();AtomicBoolean truncated=new AtomicBoolean();DirectAgent.StreamProgress progress=new DirectAgent.StreamProgress(true,new DirectAgent.ProgressSink(){public void phase(String p,long ms){check(ms>=0,"negative elapsed");phase.add(p);}public void thought(String t,boolean cut,long ms){text.add(t);truncated.set(cut);}});
   progress.begin();String latest="";for(int i=0;i<100;i++){latest+="chunk ";JSONObject frame=J.obj("choices",J.arr(J.obj("delta",J.obj("reasoning_content","chunk "))));progress.accept(frame,latest);}latest="API_KEY=fixture-private "+"한".repeat(33000);progress.accept(J.obj("choices",J.arr(J.obj("delta",J.obj("reasoning_content","final")))),latest);progress.end("completed");
   check(phase.equals(Arrays.asList("sending","receiving","thinking","completed")),"same-phase updates flooded events");check(text.size()<=2&&text.get(text.size()-1).length()<=32000&&truncated.get(),"public thought cap or immediate/final coalescing failed");check(!text.toString().contains("fixture-private"),"credential-like provider output exposed");
   check(!DirectAgent.providerThoughtText("Authorization: Bearer fixture-private").contains("fixture-private"),"authorization output redaction failed");
  });
  test("real HTTP and empty model response failures do not leave completed progress",()->{
   try(DirectAgentHarness.Api api=new DirectAgentHarness.Api("fixture unavailable")){api.status=400;api.contentType="application/json";AgentRuntime r=new AgentRuntime();failure(()->new DirectAgent(r).run("http-error","request",api.config(),""));check(phases(r).get(phases(r).size()-1).equals("failed"),"HTTP error falsely complete");}
   try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(thought("unsupported-only-reasoning")+"data: [DONE]\n\n")){AgentRuntime r=new AgentRuntime();failure(()->new DirectAgent(r).run("empty","request",api.config(),""));check(phases(r).get(phases(r).size()-1).equals("failed"),"empty answer falsely complete");}
  });
  test("cancellation during real streamed reasoning emits cancelled and no answer or invented thought text",()->{
   try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(thought("unsupported-private-reasoning")+DirectAgentHarness.text("must not answer","stop"))){api.chunkDelayMs=50;AgentRuntime r=new AgentRuntime();r.eventObserver=e->{if(e.optString("event").equals("modelProgress")&&e.getJSONObject("data").optString("phase").equals("thinking"))r.net.cancel();};failure(()->new DirectAgent(r).run("cancel","request",api.config(),""));check(phases(r).get(phases(r).size()-1).equals("cancelled"),"cancelled stream progress wrong");check(r.liveText().isEmpty()&&thoughts(r).isEmpty(),"cancelled unsupported stream exposes text");}
  });
  System.out.println("ModelProgress: "+passed+" actual production streaming/progress checks; native provider-thought persistence is separate Android proof.");
 }
}
