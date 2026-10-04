package dev.chanho.hermes;
import org.json.*;import java.util.*;
/** Real production API loop commits public pre-tool segments separately and returns the final round only. */
public final class TimelineSegmentsHarness {
 interface Work{void run()throws Exception;}static int passed;
 static void check(boolean v,String why){if(!v)throw new AssertionError(why);}static void test(String name,Work work)throws Exception{work.run();passed++;System.out.println("PASS "+name);}
 static String publicText(String text){return DirectAgentHarness.frame(J.obj("choices",J.arr(J.obj("delta",J.obj("content",text,"reasoning_content","unsupported-hidden-thought")))));}
 public static void main(String[] ignored)throws Exception{
  test("three genuine SSE rounds commit complete public explanations separately and final result contains only last answer",()->{
   String first="First explanation "+"한글 설명 ".repeat(1000),second="Second explanation before another actual tool";
   try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(publicText(first)+DirectAgentHarness.tool("round-one","{}"),publicText(second)+DirectAgentHarness.tool("round-two","{}"),DirectAgentHarness.text("Final answer only","stop"))){
    api.chunkDelayMs=1;AgentRuntime r=new AgentRuntime();String answer=new DirectAgent(r).run("ordered","request",api.config(),"");check(answer.equals("Final answer only"),"pre-tool segments duplicated in final result");check(r.publicResponses.equals(Arrays.asList(first,second)),"public segments truncated to summaries or invented");check(r.executedCallIds.equals(Arrays.asList("round-one","round-two")),"actual tool call order changed");check(r.liveText().equals("Final answer only"),"runtime current-round cursor retained previous public rounds");check(!r.events.toString().contains("unsupported-hidden-thought"),"unsupported reasoning entered timeline");
    JSONArray history=r.store.transcript("ordered");int publicMessages=0;for(int i=0;i<history.length();i++){JSONObject m=history.getJSONObject(i);if(m.optString("role").equals("assistant")&&!m.isNull("content"))publicMessages++;}check(publicMessages==3,"provider tool-call history lost public context");
   }
  });
  test("one public segment precedes actual multi-tool batch without invented per-tool commentary",()->{
   JSONArray calls=J.arr(J.obj("index",0,"id","batch-one","function",J.obj("name","get_device_state","arguments","{}")),J.obj("index",1,"id","batch-two","function",J.obj("name","list_apps","arguments","{}")));
   String batch=publicText("Inspect both actual sources")+DirectAgentHarness.frame(J.obj("choices",J.arr(J.obj("delta",J.obj("tool_calls",calls),"finish_reason","tool_calls"))))+"data: [DONE]\n\n";
   try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(batch,DirectAgentHarness.text("Combined final answer","stop"))){AgentRuntime r=new AgentRuntime();check(new DirectAgent(r).run("batch","request",api.config(),"").equals("Combined final answer"),"batch commentary duplicated into answer");check(r.publicResponses.equals(Arrays.asList("Inspect both actual sources"))&&r.executedCallIds.equals(Arrays.asList("batch-one","batch-two")),"timeline invented per-tool public commentary");}
  });
  test("silent structured tool calls create no fabricated public explanation segment",()->{
   try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(DirectAgentHarness.tool("silent-real","{}"),DirectAgentHarness.text("Actual final answer","stop"))){AgentRuntime r=new AgentRuntime();new DirectAgent(r).run("silent","request",api.config(),"");check(r.publicResponses.isEmpty()&&r.tools.executions==1,"silent model got invented public reasoning/commentary");}
  });
  test("followup HTTP failure preserves committed public explanation and never presents it as final completed answer",()->{
   try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(publicText("Before actual inspection")+DirectAgentHarness.tool("first-tool","{}"))){api.validator=request->api.requests.size()>1?"Fixture followup connection rejected":null;AgentRuntime r=new AgentRuntime();try{new DirectAgent(r).run("failure","request",api.config(),"");throw new AssertionError("Failed followup falsely returned final answer");}catch(Net.ApiError expected){check(expected.getMessage().contains("HTTP 400"),"wrong followup API failure");}check(r.publicResponses.equals(Arrays.asList("Before actual inspection"))&&r.liveText().isEmpty(),"public explanation lost or replayed as partial final");}
  });
  System.out.println("TimelineSegments: "+passed+" production SSE/public-segment checks; SQLite/native timeline order is separate proof.");
 }
}
