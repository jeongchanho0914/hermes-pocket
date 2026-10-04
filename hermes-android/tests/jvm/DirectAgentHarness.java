package dev.chanho.hermes;

import com.sun.net.httpserver.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;

/** Exercises production network and agent loop over real loopback HTTP.
 * The server simulates the API; this is not a real provider or Android test. */
public final class DirectAgentHarness {
    interface Checked { void run() throws Exception; }
    static int passed;
    static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    static void fails(Checked work,String message) throws Exception {
        try { work.run(); } catch(Exception expected) { return; }
        throw new AssertionError(message);
    }
    static void test(String name,Checked work) throws Exception {
        work.run(); passed++;System.out.println("PASS "+name);
    }
    static String frame(JSONObject object) { return "data: "+object+"\n\n"; }
    static String text(String value,String reason) {
        return frame(J.obj("choices",J.arr(J.obj("delta",J.obj("content",value),"finish_reason",JSONObject.NULL))))
            +frame(J.obj("choices",J.arr(J.obj("delta",new JSONObject(),"finish_reason",reason))))+"data: [DONE]\n\n";
    }
    static String tool(String id,String args) {
        int split=args.length()/2;
        return frame(J.obj("choices",J.arr(J.obj("delta",J.obj("tool_calls",J.arr(J.obj("index",0,"id",id,"type","function","function",J.obj("name","get_device_","arguments",args.substring(0,split)))))))))
            +frame(J.obj("choices",J.arr(J.obj("delta",J.obj("tool_calls",J.arr(J.obj("index",0,"function",J.obj("name","state","arguments",args.substring(split)))))))))
            +frame(J.obj("choices",J.arr(J.obj("delta",new JSONObject(),"finish_reason","tool_calls"))))+"data: [DONE]\n\n";
    }
    /** Android org.json optString converts JSONObject.NULL to literal "null".
     * The official host org.json artifact instead applies the fallback, so this
     * explicit compatibility object makes the nullable-fragment regression fail
     * against the old Android parser instead of silently masking it. */
    static final class AndroidNullObject extends JSONObject {
        @Override public String optString(String key,String fallback){Object value=opt(key);return value==null?fallback:String.valueOf(value);}
        @Override public String optString(String key){return optString(key,"");}
    }
    static final class Api implements AutoCloseable {
        final HttpServer server;
        final Queue<String> responses=new ConcurrentLinkedQueue<>();
        final List<JSONObject> requests=Collections.synchronizedList(new ArrayList<>());
        int status=200;
        String contentType="text/event-stream";
        int chunkDelayMs;
        final AtomicBoolean answerFrameSent=new AtomicBoolean();
        java.util.function.Function<JSONObject,String> validator;
        Api(String... replies) throws Exception {
            responses.addAll(Arrays.asList(replies));server=HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/v1/chat/completions",exchange->{
                JSONObject request=new JSONObject(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));requests.add(request);
                String invalid=validator==null?null:validator.apply(request);
                if(invalid!=null){byte[] error=J.obj("error",J.obj("message",invalid)).toString().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(400,error.length);try(OutputStream output=exchange.getResponseBody()){output.write(error);}return;}
                String reply=responses.poll();if(reply==null)reply=text("fixture exhausted","stop");
                byte[] bytes=reply.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type",contentType);exchange.sendResponseHeaders(status,chunkDelayMs>0?0:bytes.length);
                try(OutputStream output=exchange.getResponseBody()){
                    if(chunkDelayMs>0){for(String part:reply.split("\n\n")){if(part.contains("\"content\""))answerFrameSent.set(true);output.write((part+"\n\n").getBytes(StandardCharsets.UTF_8));output.flush();try{Thread.sleep(chunkDelayMs);}catch(InterruptedException stopped){Thread.currentThread().interrupt();break;}}}
                    else output.write(bytes);
                }
            });server.start();
        }
        String endpoint(){return "http://127.0.0.1:"+server.getAddress().getPort()+"/v1";}
        JSONObject config(){return J.obj("model","fixture-model","endpoint",endpoint(),"maxRounds",24,"contextChars",60000);}
        public void close(){server.stop(0);}
    }
    public static void main(String[] args) throws Exception {
        test("terminal plugin advertises canonical upstream terminal and process_manage contracts only",()->{
            JSONObject config=J.obj("enabledPlugins",J.arr("terminal"));JSONArray schema=LocalCapabilities.schemas(config);
            Set<String> names=new HashSet<>();for(int i=0;i<schema.length();i++)names.add(schema.getJSONObject(i).getJSONObject("function").getString("name"));
            check(names.equals(new HashSet<>(Arrays.asList("terminal","process_manage"))),"canonical terminal tool inventory missing or advertises process alias");
            check(LocalCapabilities.allowed("terminal",config)&&LocalCapabilities.allowed("process_manage",config),"terminal plugin cannot execute its advertised tools");
            JSONObject disabled=J.obj("enabledPlugins",J.arr("device"));check(!LocalCapabilities.allowed("terminal",disabled)&&!LocalCapabilities.allowed("process_manage",disabled),"disabled terminal tools still allowed");
        });
        test("valid SSE conversation and persisted transcript",()->{
            try(Api api=new Api(text("안녕하세요","stop"),text("계속 답변","stop"))){
                AgentRuntime runtime=new AgentRuntime();DirectAgent agent=new DirectAgent(runtime);
                check(agent.run("saved","hello",api.config(),"fixture-token").contains("안녕하세요"),"answer missing");
                agent.run("saved","followup",api.config(),"fixture-token");
                JSONArray messages=api.requests.get(1).getJSONArray("messages");
                check(messages.length()==4,"saved history not reopened");
                check(messages.getJSONObject(2).getString("content").equals("안녕하세요"),"prior response lost");
                check(runtime.store.transcript("saved").length()==5,"final persisted transcript incomplete");
            }
        });
        test("fragmented tool name and JSON execute exactly once",()->{
            try(Api api=new Api(tool("call_1","{\"label\":\"한국어\"}"),text("완료","stop"))){
                AgentRuntime runtime=new AgentRuntime();new DirectAgent(runtime).run("tool","status",api.config(),"");
                check(runtime.tools.executions==1,"fragmented tool executed incorrect times");
                JSONArray messages=api.requests.get(1).getJSONArray("messages");
                check(messages.getJSONObject(3).getString("role").equals("tool"),"tool result missing");
                check(messages.getJSONObject(3).getString("content").contains("한국어"),"tool args fragments lost");
            }
        });
        test("MiMo null name id and argument fragments never append literal null",()->{
            AndroidNullObject androidFunction=new AndroidNullObject();androidFunction.put("name",JSONObject.NULL);androidFunction.put("arguments",JSONObject.NULL);
            AndroidNullObject androidPiece=new AndroidNullObject();androidPiece.put("index",0);androidPiece.put("id",JSONObject.NULL);androidPiece.put("function",androidFunction);
            check(androidFunction.optString("name","").equals("null"),"Android null compatibility fixture incorrectly hides bug");
            DirectAgent.StreamTurn direct=new DirectAgent.StreamTurn();
            direct.accept("message",J.obj("choices",J.arr(J.obj("delta",J.obj("tool_calls",J.arr(J.obj("index",0,"id","android-call","function",J.obj("name","get_device_","arguments","{"))))))));
            direct.accept("message",J.obj("choices",J.arr(J.obj("delta",J.obj("tool_calls",J.arr(androidPiece))))));
            direct.accept("message",J.obj("choices",J.arr(J.obj("delta",J.obj("tool_calls",J.arr(J.obj("index",0,"function",J.obj("name","state","arguments","}")))),"finish_reason","tool_calls"))));
            JSONObject exact=direct.finish().getJSONObject(0);check(exact.getString("id").equals("android-call")&&exact.getJSONObject("function").getString("name").equals("get_device_state")&&exact.getJSONObject("function").getString("arguments").equals("{}"),"Android JSONnull corrupted completed call");
            String original=tool("null-fragment-call","{}");int split=original.indexOf("\n\n")+2;
            String empty=frame(J.obj("choices",J.arr(J.obj("delta",J.obj("tool_calls",J.arr(J.obj("index",0,"id",JSONObject.NULL,"type",JSONObject.NULL,"function",J.obj("name",JSONObject.NULL,"arguments",JSONObject.NULL))))))));
            try(Api api=new Api(original.substring(0,split)+empty+original.substring(split),text("null fragments handled","stop"))){
                AgentRuntime r=new AgentRuntime();new DirectAgent(r).run("null-fragments","status",api.config().put("providerId","xiaomi"),"");
                check(r.tools.executions==1,"null function fragment corrupted exact tool name or arguments");
                JSONObject call=api.requests.get(1).getJSONArray("messages").getJSONObject(2).getJSONArray("tool_calls").getJSONObject(0);
                check(call.getString("id").equals("null-fragment-call"),"null id fragment appended");
                check(call.getJSONObject("function").getString("name").equals("get_device_state"),"null name fragment appended");
                check(call.getJSONObject("function").getString("arguments").equals("{}"),"null arguments fragment appended");
            }
        });
        test("non string streamed function name id or arguments reject before execution",()->{
            for(String field:new String[]{"name","id","arguments"}){
                JSONObject function=J.obj("name","get_device_state","arguments","{}");JSONObject call=J.obj("index",0,"id","call","function",function);
                if(field.equals("id"))call.put("id",17);else function.put(field,field.equals("arguments")?new JSONObject():17);
                String stream=frame(J.obj("choices",J.arr(J.obj("delta",J.obj("tool_calls",J.arr(call))))))+frame(J.obj("choices",J.arr(J.obj("delta",new JSONObject(),"finish_reason","tool_calls"))))+"data: [DONE]\n\n";
                try(Api api=new Api(stream)){AgentRuntime r=new AgentRuntime();fails(()->new DirectAgent(r).run("bad-fragment","status",api.config(),""),"non-string "+field+" fragment accepted");check(r.tools.executions==0,"invalid fragment dispatched a tool");}
            }
        });
        test("truncated SSE without DONE is rejected before tool execution",()->{
            try(Api api=new Api(tool("call_1","{}").replace("data: [DONE]\n\n",""))){
                AgentRuntime runtime=new AgentRuntime();fails(()->new DirectAgent(runtime).run("x","go",api.config(),""),"truncation accepted");
                check(runtime.tools.executions==0,"partial stream executed tool");
            }
        });
        test("length finish reason rejected",()->{
            try(Api api=new Api(text("partial","length"))){fails(()->new DirectAgent(new AgentRuntime()).run("x","go",api.config(),""),"length accepted");}
        });
        test("missing finish reason rejected",()->{
            try(Api api=new Api(frame(J.obj("choices",J.arr(J.obj("delta",J.obj("content","partial")))))+"data: [DONE]\n\n")){
                fails(()->new DirectAgent(new AgentRuntime()).run("x","go",api.config(),""),"missing finish reason accepted");
            }
        });
        test("malformed tool arguments never execute",()->{
            try(Api api=new Api(tool("call_1","not-json"),text("invalid tool noticed","stop"))){
                AgentRuntime runtime=new AgentRuntime();try{new DirectAgent(runtime).run("x","go",api.config(),"");}catch(Exception allowed){}
                check(runtime.tools.executions==0,"malformed arguments executed");
            }
        });
        test("entire tool batch validates before any side effect",()->{
            JSONArray calls=J.arr(J.obj("index",0,"id","valid","function",J.obj("name","get_device_state","arguments","{}")),
                J.obj("index",1,"id","invalid","function",J.obj("name","get_device_state","arguments","broken-json")));
            String stream=frame(J.obj("choices",J.arr(J.obj("delta",J.obj("tool_calls",calls)))))
                +frame(J.obj("choices",J.arr(J.obj("delta",new JSONObject(),"finish_reason","tool_calls"))))+"data: [DONE]\n\n";
            try(Api api=new Api(stream)){
                AgentRuntime runtime=new AgentRuntime();fails(()->new DirectAgent(runtime).run("x","go",api.config(),""),"invalid batch accepted");
                check(runtime.tools.executions==0,"earlier valid tool executed before later malformed tool validation");
            }
        });
        test("HTTP auth failure has no retry",()->{
            try(Api api=new Api("{\"error\":{\"message\":\"fixture unauthorized\"}}")){
                api.status=401;api.contentType="application/json";AgentRuntime runtime=new AgentRuntime();
                fails(()->new DirectAgent(runtime).run("x","go",api.config(),""),"HTTP error accepted");
                check(api.requests.size()==1,"failed API retried");check(runtime.tools.executions==0,"error executed tool");
            }
        });
        test("API error in SSE rejected",()->{
            try(Api api=new Api(frame(J.obj("error",J.obj("message","fixture failure")))+"data: [DONE]\n\n")){
                fails(()->new DirectAgent(new AgentRuntime()).run("x","go",api.config(),""),"SSE error accepted");
            }
        });
        test("incorrect response content type rejected",()->{
            try(Api api=new Api(text("hello","stop"))){api.contentType="application/json";fails(()->new DirectAgent(new AgentRuntime()).run("x","go",api.config(),""),"non SSE accepted");}
        });
        test("pre cancelled run sends no HTTP request",()->{
            try(Api api=new Api(text("hello","stop"))){AgentRuntime runtime=new AgentRuntime();runtime.net.cancel();
                fails(()->new DirectAgent(runtime).run("x","go",api.config(),""),"cancel ignored");check(api.requests.isEmpty(),"cancelled request transmitted");}
        });
        test("public HTTP, URL credentials and query are rejected",()->{
            for(String url:Arrays.asList("http://example.com/v1","https://user:secret@example.com/v1","https://example.com/v1?token=secret"))
                fails(()->Net.validateEndpoint(url,false),"unsafe endpoint accepted");
            Net.validateEndpoint("https://example.com/v1",false);Net.validateEndpoint("http://127.0.0.1:123/v1",false);
        });
        test("denied tool cannot be retried automatically",()->{
            try(Api api=new Api(tool("call_1","{}"),tool("call_2","{}"),text("denied","stop"))){
                AgentRuntime runtime=new AgentRuntime();runtime.tools.denied=true;
                try{new DirectAgent(runtime).run("x","go",api.config(),"");}catch(Exception allowed){}
                check(runtime.tools.executions==1,"denied action executed again");
            }
        });
        test("context trimming preserves system latest turn and complete tool groups",()->{
            JSONArray history=J.arr(J.obj("role","system","content","required prompt"),
                J.obj("role","user","content","old question "+"x".repeat(3000)),
                J.obj("role","assistant","content",JSONObject.NULL,"tool_calls",J.arr(J.obj("id","old-tool"))),
                J.obj("role","tool","tool_call_id","old-tool","content","old result"),
                J.obj("role","assistant","content","old answer"),
                J.obj("role","user","content","latest question"),
                J.obj("role","assistant","content",JSONObject.NULL,"tool_calls",J.arr(J.obj("id","latest-tool"))),
                J.obj("role","tool","tool_call_id","latest-tool","content","latest result"));
            JSONArray trimmed=DirectAgent.trimHistory(history,1000);
            check(trimmed.toString().length()<=1000,"trim exceeds budget");
            check(trimmed.getJSONObject(0).getString("content").equals("required prompt"),"system lost");
            check(trimmed.getJSONObject(2).getString("content").equals("latest question"),"latest turn lost");
            check(!trimmed.toString().contains("old-tool"),"old tool group partly retained");
            check(trimmed.getJSONObject(4).getString("tool_call_id").equals("latest-tool"),"latest tool group broken");
            check(history.length()==8,"local transcript mutated by request trimming");
            fails(()->DirectAgent.trimHistory(J.arr(J.obj("role","system","content","prompt"),J.obj("role","user","content","x".repeat(3000))),1000),"oversized current turn accepted");
        });
        test("edited memory refreshes existing session prompt",()->{
            try(Api api=new Api(text("first","stop"),text("second","stop"))){
                AgentRuntime runtime=new AgentRuntime();runtime.localAgentTools.documents.save("MEMORY.md","memory before");
                new DirectAgent(runtime).run("same","hi",api.config(),"");runtime.localAgentTools.documents.save("MEMORY.md","memory after");
                new DirectAgent(runtime).run("same","hi again",api.config(),"");
                String prompt=api.requests.get(1).getJSONArray("messages").getJSONObject(0).getString("content");
                check(prompt.contains("memory after")&&!prompt.contains("memory before"),"stale memory persisted in prompt");
            }
        });
        test("last round requests no tools and does not dispatch ignored API tool choice",()->{
            try(Api api=new Api(tool("call_1","{}"),tool("call_2","{}"))){
                AgentRuntime runtime=new AgentRuntime();JSONObject config=api.config().put("maxRounds",2);
                fails(()->new DirectAgent(runtime).run("limit","go",config,""),"round limit ignored");
                check(api.requests.size()==2,"round limit exceeded");
                check(api.requests.get(1).getString("tool_choice").equals("none"),"last round still invited tool calls");
                check(runtime.tools.executions==1,"last round tool dispatched");
                JSONArray transcript=runtime.store.transcript("limit");
                check(transcript.getJSONObject(transcript.length()-1).getString("role").equals("tool"),"aborted tool batch persisted incomplete");
            }
        });
        test("SSE cancellation during reading stops parsing",()->{
            AtomicBoolean cancelled=new AtomicBoolean(false);AtomicInteger handled=new AtomicInteger();
            String stream=frame(J.obj("delta",1))+frame(J.obj("delta",2))+"data: [DONE]\n\n";
            fails(()->Net.parseStream(new BufferedReader(new StringReader(stream)),(event,data)->{handled.incrementAndGet();cancelled.set(true);},cancelled),"stream cancellation ignored");
            check(handled.get()==1,"frames parsed after cancellation");
        });
        test("local catalog validates presets and preserves saved device plugin groups",()->{
            check(LocalCapabilities.catalog().getJSONArray("skills").length()==4,"skills missing");
            check(LocalCapabilities.schemas(new JSONObject()).length()==41+ExtensionSchemas.all().length(),"fresh config missing real web/local/terminal tools");
            check(LocalCapabilities.schemas(J.obj("enabledPlugins",J.arr("device","screen","root"))).length()==31,"device/screen/root must add only the native semantic action");
            JSONArray screenOnly=LocalCapabilities.schemas(J.obj("enabledPlugins",J.arr("screen")));
            check(screenOnly.length()==12,"screen filter must retain 11 original tools and act_on_screen");
            Set<String> screenNames=new HashSet<>();for(int i=0;i<screenOnly.length();i++)screenNames.add(screenOnly.getJSONObject(i).getJSONObject("function").getString("name"));
            check(screenNames.contains("act_on_screen")&&screenNames.contains("read_screen")&&!screenNames.contains("delegate_task")&&!screenNames.contains("browser_open"),"screen plugin leaked worker or browser authority");
            check(LocalCapabilities.schemas(J.obj("enabledPlugins",J.arr("device"))).length()==15,"device filter incorrect");
            check(LocalCapabilities.schemas(J.obj("enabledPlugins",J.arr("root"))).length()==4,"root filter incorrect");
            check(LocalCapabilities.plugins(J.arr("root","device","root")).toString().equals("[\"device\",\"root\"]"),"duplicates not normalized");
            fails(()->LocalCapabilities.plugins(J.arr("remote-download")),"unknown plugin accepted");
            fails(()->LocalCapabilities.skill("remote"),"unknown skill accepted");
            fails(()->LocalCapabilities.effort("maximum"),"unknown reasoning accepted");
        });
        test("file plugin requires explicit grant-enabled group and precise native names",()->{
            JSONObject off=new JSONObject(),on=J.obj("enabledPlugins",J.arr("files"));
            check(!LocalCapabilities.allowed("phone_read_file",off),"files enabled before owner picks folder");
            JSONArray tools=LocalCapabilities.schemas(on);check(tools.length()==3,"file schemas not filtered");
            for(String name:new String[]{"phone_list_files","phone_read_file","phone_write_file"})check(LocalCapabilities.allowed(name,on),"missing precise file name "+name);
            check(!LocalCapabilities.allowed("file_read",on)&&!LocalCapabilities.allowed("files_list",on),"invented file alias accepted");
            check(!LocalCapabilities.allowed("get_device_state",on),"file-only config enabled device");
        });
        test("selected skill refreshes persisted session prompt",()->{
            try(Api api=new Api(text("first","stop"),text("second","stop"))){
                AgentRuntime runtime=new AgentRuntime();new DirectAgent(runtime).run("skill","draft",api.config().put("skillId","write"),"");
                new DirectAgent(runtime).run("skill","debug",api.config().put("skillId","code"),"");
                String first=api.requests.get(0).getJSONArray("messages").getJSONObject(0).getString("content");
                String second=api.requests.get(1).getJSONArray("messages").getJSONObject(0).getString("content");
                check(first.contains("Active local skill: Writing"),"write preset absent");
                check(second.contains("Active local skill: Coding")&&!second.contains("Active local skill: Writing"),"changed skill stale");
            }
        });
        test("reasoning effort auto omitted explicit options transmitted once",()->{
            for(String effort:new String[]{"auto","none","minimal","low","medium","high","xhigh","max"})try(Api api=new Api(text("answer","stop"))){
                new DirectAgent(new AgentRuntime()).run("reasoning","question",api.config().put("reasoningEffort",effort),"");
                JSONObject request=api.requests.get(0);
                check(effort.equals("auto")?!request.has("reasoning_effort"):request.getString("reasoning_effort").equals(effort),"reasoning mismatch "+effort);
                check(api.requests.size()==1,"reasoning retry unexpectedly performed");
            }
        });
        test("disabled plugins omit schemas and unsolicited tools never dispatch",()->{
            try(Api api=new Api(tool("disabled","{}"),text("disabled understood","stop"))){
                AgentRuntime runtime=new AgentRuntime();new DirectAgent(runtime).run("disabled","hello",api.config().put("enabledPlugins",new JSONArray()),"");
                check(!api.requests.get(0).has("tools")&&!api.requests.get(0).has("tool_choice"),"disabled schemas sent");
                check(runtime.tools.executions==0,"disabled tool dispatched");
                JSONArray messages=api.requests.get(1).getJSONArray("messages");
                check(messages.getJSONObject(3).getString("role").equals("tool"),"disabled response unbalanced");
                check(messages.getJSONObject(3).getString("content").contains("플러그인이 꺼져 있어"),"disabled refusal missing");
            }
        });
        test("DeepSeek thinking maps none safely and omits automatic parameters",()->{
            for(String effort:new String[]{"auto","none","low","medium","high"})try(Api api=new Api(text("answer","stop"))){
                new DirectAgent(new AgentRuntime()).run("deepseek","question",api.config().put("providerId","deepseek").put("reasoningEffort",effort),"");
                JSONObject request=api.requests.get(0);
                if(effort.equals("auto"))check(!request.has("thinking")&&!request.has("reasoning_effort"),"auto overrides provider defaults");
                else if(effort.equals("none"))check(request.getJSONObject("thinking").getString("type").equals("disabled")&&!request.has("reasoning_effort"),"none sent invalid effort to DeepSeek");
                else check(request.getJSONObject("thinking").getString("type").equals("enabled")&&request.getString("reasoning_effort").equals(effort),"thinking option mismatch");
            }
        });
        test("Hermes reasoning ladder preserves defaults and maps provider limits",()->{
            JSONArray efforts=LocalCapabilities.catalog().getJSONArray("reasoningEfforts");
            for(String value:new String[]{"auto","none","minimal","low","medium","high","xhigh","max","ultra"}){
                check(LocalCapabilities.effort(value).equals(value),"Hermes ladder rejected "+value);
                check(efforts.toList().contains(value),"native catalog omits "+value);
            }
            fails(()->LocalCapabilities.effort("hgih"),"misspelled effort accepted");
            for(String provider:new String[]{"custom","gemini","deepseek"})for(String chosen:new String[]{"minimal","xhigh","max","ultra"})try(Api api=new Api(text("answer","stop"))){
                JSONObject config=api.config().put("providerId",provider).put("reasoningEffort",chosen);
                new DirectAgent(new AgentRuntime()).run("mapped","question",config,"");
                String expected=chosen;
                if(provider.equals("custom")&&chosen.equals("ultra"))expected="max";
                if(provider.equals("gemini")&&Arrays.asList("xhigh","max","ultra").contains(chosen))expected="high";
                if(provider.equals("deepseek")){if(chosen.equals("minimal"))expected="low";if(chosen.equals("xhigh")||chosen.equals("ultra"))expected="max";}
                check(api.requests.get(0).getString("reasoning_effort").equals(expected),"provider mapping mismatch "+provider+" "+chosen);
                check(config.getString("reasoningEffort").equals(chosen),"saved selection mutated to effective level");
            }
        });
        test("DeepSeek hidden reasoning survives tools and strips across provider switch",()->{
            String toolStream=frame(J.obj("choices",J.arr(J.obj("delta",J.obj("reasoning_content","private-","tool_calls",J.arr(J.obj("index",0,"id","deep-call","function",J.obj("name","get_device_state","arguments","{}"))))))))
                +frame(J.obj("choices",J.arr(J.obj("delta",J.obj("reasoning_content","trace"),"finish_reason","tool_calls"))))+"data: [DONE]\n\n";
            String finalStream=frame(J.obj("choices",J.arr(J.obj("delta",J.obj("reasoning_content","private-final","content","Public answer")))))
                +frame(J.obj("choices",J.arr(J.obj("delta",new JSONObject(),"finish_reason","stop"))))+"data: [DONE]\n\n";
            try(Api api=new Api(toolStream,finalStream)){
                AgentRuntime runtime=new AgentRuntime();JSONObject config=api.config().put("providerId","deepseek");new DirectAgent(runtime).run("deep","question",config,"");
                JSONArray next=api.requests.get(1).getJSONArray("messages");
                check(next.getJSONObject(2).getString("reasoning_content").equals("private-trace"),"fragmented tool reasoning lost");
                check(runtime.tools.executions==1,"reasoning altered tool dispatch");
                check(!runtime.liveText().contains("private-"),"private reasoning leaked to live chat");
                JSONArray saved=runtime.store.transcript("deep");
                check(saved.getJSONObject(saved.length()-1).getString("reasoning_content").equals("private-final"),"final hidden reasoning lost");
                JSONArray switched=LocalCapabilities.wireHistory(saved,J.obj("providerId","custom"));
                check(!switched.toString().contains("reasoning_content"),"hidden provider fields leaked across switch");
                check(saved.toString().contains("private-final"),"wire conversion mutated stored transcript");
                JSONArray older=LocalCapabilities.wireHistory(J.arr(J.obj("role","assistant","content","old")),config);
                check(older.getJSONObject(0).getString("reasoning_content").isEmpty(),"older assistant missing DeepSeek reasoning field");
            }
        });
        test("MiMo fixture rejects missing echoed tool reasoning and native agent supplies it",()->{
            String toolStream=frame(J.obj("choices",J.arr(J.obj("delta",J.obj("reasoning_content","private-mimo-","tool_calls",J.arr(J.obj("index",0,"id","mimo-call","function",J.obj("name","get_device_state","arguments","{}"))))))))
                +frame(J.obj("choices",J.arr(J.obj("delta",J.obj("reasoning_content","trace"),"finish_reason","tool_calls"))))+"data: [DONE]\n\n";
            String finalStream=frame(J.obj("choices",J.arr(J.obj("delta",J.obj("reasoning_content","private-mimo-final","content","Verified fixture response")))))
                +frame(J.obj("choices",J.arr(J.obj("delta",new JSONObject(),"finish_reason","stop"))))+"data: [DONE]\n\n";
            try(Api api=new Api(toolStream,finalStream)){
                api.validator=request->{
                    JSONArray messages=request.getJSONArray("messages");
                    for(int i=0;i<messages.length();i++){JSONObject m=messages.getJSONObject(i);if(m.has("tool_calls")&&!"private-mimo-trace".equals(m.optString("reasoning_content")))return "MiMo fixture requires echoed reasoning_content on assistant tool calls";}
                    return null;
                };
                JSONObject invalid=J.obj("messages",J.arr(J.obj("role","assistant","tool_calls",new JSONArray())));
                check(api.validator.apply(invalid)!=null,"fixture does not enforce documented requirement");
                fails(()->new Net().stream(api.endpoint(),"/chat/completions","",false,invalid,(event,data)->{}),"TCP fixture accepted missing echoed reasoning");
                check(api.requests.size()==1,"invalid fixture request not transmitted");api.requests.clear();
                AgentRuntime runtime=new AgentRuntime();JSONObject config=api.config().put("providerId","xiaomi").put("reasoningEffort","medium");
                String answer=new DirectAgent(runtime).run("mimo","status",config,"");
                check(answer.contains("Verified fixture response"),"MiMo loop failed before final response");
                check(api.requests.size()==2&&runtime.tools.executions==1,"MiMo tool loop incorrect");
                check(api.requests.get(1).getJSONArray("messages").getJSONObject(2).getString("reasoning_content").equals("private-mimo-trace"),"MiMo reasoning fragments not echoed");
                check(!runtime.liveText().contains("private-mimo"),"MiMo thoughts leaked to visible answer");
                JSONArray saved=runtime.store.transcript("mimo");
                check(saved.getJSONObject(saved.length()-1).getString("reasoning_content").equals("private-mimo-final"),"MiMo final reasoning not retained");
                check(LocalCapabilities.wireHistory(saved,J.obj("providerId","xiaomi")).toString().contains("private-mimo-trace"),"MiMo reopen wire drops reasoning");
                check(!LocalCapabilities.wireHistory(saved,J.obj("providerId","custom")).toString().contains("reasoning_content"),"MiMo fields leak to different provider");
            }
        });
        test("MiMo none disables thinking and enabled tiers avoid unsupported effort strength",()->{
            for(String effort:new String[]{"auto","none","minimal","low","medium","high","xhigh","max","ultra"})try(Api api=new Api(text("answer","stop"))){
                new DirectAgent(new AgentRuntime()).run("mimo-level","question",api.config().put("providerId","xiaomi").put("reasoningEffort",effort),"");
                JSONObject request=api.requests.get(0);check(!request.has("reasoning_effort"),"MiMo unsupported strength parameter sent");
                if(effort.equals("auto"))check(!request.has("thinking"),"MiMo auto overrides provider default");
                else check(request.getJSONObject("thinking").getString("type").equals(effort.equals("none")?"disabled":"enabled"),"MiMo thinking switch mismatch "+effort);
            }
        });
        test("MiMo exact hostname detection rejects spoofed hosts and preserves selected tier",()->{
            for(String endpoint:new String[]{"https://api.xiaomimimo.com/v1","https://xiaomimimo.com/v1"}){
                JSONObject config=J.obj("providerId","custom","endpoint",endpoint,"reasoningEffort","ultra");
                check(LocalCapabilities.isMiMo(config)&&LocalCapabilities.replaysReasoning(config),"official MiMo host not detected");
                check(LocalCapabilities.effectiveEffort(config).equals("enabled"),"MiMo incorrectly claims strength support");
                JSONObject request=new JSONObject();LocalCapabilities.configureReasoning(request,config);
                check(request.getJSONObject("thinking").getString("type").equals("enabled")&&!request.has("reasoning_effort"),"custom official endpoint thinking unsupported strength");
                check(config.getString("reasoningEffort").equals("ultra"),"user tier overwritten by effective switch");
            }
            for(String endpoint:new String[]{"https://evil-xiaomimimo.com/v1","https://xiaomimimo.com.attacker.example/v1","https://example.com/xiaomimimo.com/v1"})
                check(!LocalCapabilities.isMiMo(J.obj("providerId","custom","endpoint",endpoint)),"spoofed MiMo hostname matched");
            check(LocalCapabilities.isMiMo(J.obj("providerId","xiaomi","endpoint","http://127.0.0.1/v1")),"selected provider not detected in loopback fixture");
        });
        test("available tool inventory prompt and auto choice follow enabled plugins",()->{
            try(Api api=new Api(text("device","stop"),text("screen","stop"))){
                AgentRuntime runtime=new AgentRuntime();DirectAgent agent=new DirectAgent(runtime);
                JSONObject device=api.config().put("enabledPlugins",J.arr("device"));
                agent.run("inventory","list tools",device,"");
                JSONObject first=api.requests.get(0);
                check(first.getString("tool_choice").equals("auto"),"ordinary request did not explicitly invite tools");
                String prompt=first.getJSONArray("messages").getJSONObject(0).getString("content");
                JSONArray names=LocalCapabilities.inventory(device).getJSONArray("names");
                check(LocalCapabilities.inventory(device).getInt("count")==15&&first.getJSONArray("tools").length()==15,"device snapshot/schema inventory mismatch");
                for(int i=0;i<names.length();i++)check(prompt.contains(names.getString(i)),"prompt omits enabled tool "+names.getString(i));
                String inventory=prompt.substring(prompt.indexOf("Currently enabled native tools"),prompt.indexOf(". For a requested task"));
                check(!inventory.contains("read_screen")&&!inventory.contains("capture_screen")&&!inventory.contains("root_processes"),"current inventory lists disabled tools");
                JSONObject screen=api.config().put("enabledPlugins",J.arr("screen"));agent.run("inventory","list tools again",screen,"");
                String changed=api.requests.get(1).getJSONArray("messages").getJSONObject(0).getString("content");
                check(changed.contains(LocalCapabilities.inventory(screen).getJSONArray("names").toString()),"saved session inventory stale after plugin change");
                check(LocalCapabilities.inventory(screen).getInt("count")==12,"screen inventory must include the added semantic action");
                check(api.requests.get(1).getJSONArray("tools").length()==12,"actual wire schema omitted semantic action");
                check(LocalCapabilities.inventory(J.obj("enabledPlugins",new JSONArray())).getInt("count")==0,"empty plugin inventory falsely lists tools");
            }
        });
        test("selected real SKILL.md enters system context and clears on preference change",()->{
            try(Api api=new Api(text("first","stop"),text("second","stop"))){
                AgentRuntime runtime=new AgentRuntime();runtime.localAgentTools.skills.save("owner-skill","Owner document","Distinct private instruction fixture");
                new DirectAgent(runtime).run("skill-document","question",api.config().put("activeSkillName","owner-skill"),"");
                String prompt=api.requests.get(0).getJSONArray("messages").getJSONObject(0).getString("content");
                check(prompt.contains("Distinct private instruction fixture")&&prompt.contains("never authorization"),"real skill not passed as bounded untrusted context");
                new DirectAgent(runtime).run("skill-document","followup",api.config().put("activeSkillName",""),"");
                check(!api.requests.get(1).getJSONArray("messages").getJSONObject(0).getString("content").contains("Distinct private instruction fixture"),"deselected skill remains in system prompt");
                check(runtime.tools.executions==0,"knowledge document dispatched device actions");
            }
        });
        test("unknown structured function is distinguished from disabled plugin and may correct name",()->{
            String unknown=tool("unknown","{\"fixtureArgument\":\"do-not-audit-arguments\"}").replace("get_device_","functions.get_device_");
            try(Api api=new Api(unknown,tool("correct","{}"),text("corrected","stop"))){
                AgentRuntime r=new AgentRuntime();new DirectAgent(r).run("unknown-name","status",api.config(),"");
                check(r.tools.executions==1&&api.requests.size()==3,"unknown call dispatched or valid correction blocked");
                String result=api.requests.get(1).getJSONArray("messages").getJSONObject(3).getString("content");
                check(result.contains("등록되지 않은")&&!result.contains("플러그인이 꺼져"),"unknown function falsely reported disabled plugin");
                check(!r.store.audits.isEmpty()&&r.store.audits.get(0).getString("status").equals("failed"),"unknown request failure not audited");
                check(!r.store.audits.toString().contains("do-not-audit-arguments"),"untrusted arguments leaked into audit");
                check(LocalCapabilities.auditToolName("x".repeat(100)).length()<=81,"unbounded audit name");
                check(!LocalCapabilities.auditToolName("sk-fixture-sensitive").contains("fixture-sensitive"),"key-looking function exposed in audit");
            }
        });
        test("provider catalog pins official endpoints and preserves custom migration",()->{
            JSONArray providers=LocalCapabilities.providers();check(providers.length()>=7,"provider catalog missing");
            Set<String> ids=new HashSet<>();for(int i=0;i<providers.length();i++){
                JSONObject p=providers.getJSONObject(i);String id=p.getString("id");check(ids.add(id),"duplicate provider");
                check(LocalCapabilities.providerId(id).equals(id),"catalog id rejected");
                String endpoint=LocalCapabilities.providerEndpoint(id);
                check(endpoint.equals(p.getString("endpoint")),"pinned catalog endpoint mismatch");
                if(!id.equals("custom")){Net.validateEndpoint(endpoint,false);check(LocalCapabilities.inferProvider(endpoint+"/").equals(id),"official migration lost");}
            }
            check(LocalCapabilities.providerEndpoint("openai-api").equals("https://api.openai.com/v1"),"OpenAI pin changed");
            check(LocalCapabilities.providerEndpoint("gemini").equals("https://generativelanguage.googleapis.com/v1beta/openai"),"Gemini pin changed");
            check(LocalCapabilities.providerEndpoint("custom").equals(""),"custom silently assigned provider");
            check(LocalCapabilities.inferProvider("").equals("openrouter"),"initial provider default wrong");
            for(String custom:Arrays.asList("https://old-private.example/v1","https://api.openai.com.evil.example/v1","https://api.openai.com/v1?redirect=evil"))
                check(LocalCapabilities.inferProvider(custom).equals("custom"),"custom/spoof migrated to built-in");
            fails(()->LocalCapabilities.providerId("chatgpt-login"),"unsupported auth provider accepted");
            fails(()->LocalCapabilities.providerEndpoint("unknown"),"unknown endpoint mapped");
            check(LocalCapabilities.catalog().getJSONArray("providers").length()==providers.length(),"native catalog omits providers");
            check(ids.containsAll(Arrays.asList("openrouter","openai-api","gemini","deepseek","groq","nvidia","custom")),"required providers missing");
            check(providers.getJSONObject(providers.length()-1).getString("id").equals("custom"),"custom connection is not last");
        });
        test("model catalog rejects malformed envelope deduplicates and preserves unknown support",()->{
            fails(()->LocalCapabilities.normalizeModels(new JSONObject()),"missing data accepted");
            JSONObject parsed=LocalCapabilities.normalizeModels(J.obj("data",J.arr(
                J.obj("id"," a ","name","Friendly"),J.obj("id","a"),J.obj("id",""),JSONObject.NULL,
                J.obj("id","reasoner","supported_parameters",J.arr("reasoning_effort")),
                J.obj("id","capability","capabilities",J.obj("reasoning",true)),
                J.obj("id","unknown","name","<script>literal</script>"),J.obj("id","x".repeat(201)))));
            JSONArray models=parsed.getJSONArray("models");check(models.length()==4,"invalid or duplicate model retained");
            check(models.getJSONObject(0).getString("id").equals("a"),"model id not trimmed");
            check(models.getJSONObject(0).getString("reasoningSupportSource").equals("unknown"),"unsupported falsely claimed");
            check(models.getJSONObject(1).getBoolean("reasoningSupported"),"API supported_parameters lost");
            check(models.getJSONObject(2).getBoolean("reasoningSupported"),"API capabilities lost");
            check(models.getJSONObject(3).getString("name").contains("<script>"),"model literal unexpectedly interpreted");
            JSONArray many=new JSONArray();for(int i=0;i<2001;i++)many.put(J.obj("id","model-"+i));
            JSONObject bounded=LocalCapabilities.normalizeModels(J.obj("data",many));
            check(bounded.getInt("count")==2000&&bounded.getBoolean("truncated"),"catalog bound missing");
        });
        System.out.println("Production Net/DirectAgent JVM checks: "+passed+" passed. API and Android capabilities are test substitutes.");
    }
}
