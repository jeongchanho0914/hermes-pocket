package dev.chanho.hermes;

import org.json.*;
import java.util.*;
import java.io.File;
import java.nio.file.Files;

/** Test-only substitutes for Android storage, approval UI and device tools.
 * Net, DirectAgent and J are compiled unchanged from production sources. */
final class AgentRuntime {
    final Store store = new Store();
    final FixtureContext context=new FixtureContext();
    final FixtureApprovals approvals=new FixtureApprovals();
    final LocalAgentTools localAgentTools=new LocalAgentTools(this);
    final Net net = new Net();
    final DeviceTools tools = new DeviceTools();
    final FixtureShizuku shizuku=new FixtureShizuku();
    boolean ownerBusy=true,unlocked=true;
    final List<JSONObject> events = new ArrayList<>();
    java.util.function.Consumer<JSONObject> eventObserver;
    final List<String> executedCallIds=new ArrayList<>(),rejectedCallIds=new ArrayList<>(),publicResponses=new ArrayList<>();
    private final StringBuilder text = new StringBuilder();
    boolean cancelled() { return net.cancelled(); }
    boolean busy(){return ownerBusy;}
    boolean isUnlocked(){return unlocked;}
    void checkCancelled()throws InterruptedException{if(cancelled())throw new InterruptedException("Fixture owner stopped");}
    JSONObject executeTool(String name,JSONObject args)throws Exception {
        if(LocalAgentTools.handles(name))return localAgentTools.execute(name,args);
        if(WebTools.handles(name))return WebTools.execute(name,args,net,store.get("fixture_web_key",""));
        return tools.execute(name,args);
    }
    JSONObject executeTool(String name,JSONObject args,String callId)throws Exception{executedCallIds.add(callId);return executeTool(name,args);}
    // Native timeline persistence is exercised on Android, not substituted here.
    void toolRejected(String name,JSONObject args,String callId,JSONObject result){rejectedCallIds.add(callId);}
    void visibleResponse(String text,int round){publicResponses.add(text);this.text.setLength(0);}
    static boolean unexpectedFailure(Throwable error){return !(error instanceof InterruptedException||error instanceof SecurityException||error instanceof IllegalArgumentException);}
    void modelProgress(String phase,int round,long elapsedMs){emit("modelProgress",J.obj("phase",phase,"round",round,"elapsedMs",elapsedMs));}
    void providerThought(String text,int round,boolean truncated,long elapsedMs){emit("providerThought",J.obj("provider","mimo","source","mimo.reasoning_content","label","모델이 공개한 생각","text",text,"round",round,"truncated",truncated,"elapsedMs",elapsedMs));}
    void emit(String kind, JSONObject data) { JSONObject event=J.obj("event",kind,"data",data);events.add(event);if(eventObserver!=null)eventObserver.accept(event); }
    void append(String value) { text.append(value); }
    String liveText() { return text.toString(); }
}

final class Store {
    final Map<String,String> values = new HashMap<>();
    final Map<String,String> transcripts = new HashMap<>();
    void put(String key,String value){values.put(key,value);}
    final List<JSONObject> audits=new ArrayList<>();
    void audit(String name,String status,String detail){audits.add(J.obj("name",name,"status",status,"detail",detail));}
    JSONArray searchSessions(String query,int limit){return new JSONArray();}
    String get(String key,String fallback) { return values.getOrDefault(key,fallback); }
    JSONObject config(){return new JSONObject(get("fixture_config","{}"));}
    String deviceScope(){return get("device_scope","all");}
    String approvalMode(){return get("approval_mode","ask");}
    boolean flag(String key){return "true".equals(get(key,"false"));}
    JSONArray transcript(String id) { return new JSONArray(transcripts.getOrDefault(id,"[]")); }
    void transcript(String id,JSONArray value) { transcripts.put(id,value.toString()); }
}

final class DeviceTools {
    int executions;
    boolean denied;
    java.util.function.BiFunction<String,JSONObject,JSONObject> responder;
    JSONObject execute(String name,JSONObject arguments) throws Exception {
        executions++;
        if(responder!=null)return responder.apply(name,arguments);
        return denied ? J.obj("ok",false,"denied",true,"error","User denied approval") : J.obj("ok",true,"result",arguments);
    }
    static JSONArray schemas() {
        JSONArray result=new JSONArray();
        for(String name:new String[]{"get_device_state","list_apps","launch_app","open_settings","perform_phone_action","set_volume","set_brightness","read_screen","capture_screen","click_element","long_click_element","set_element_progress","type_text","scroll_element","press_back","press_home","tap_screen","swipe_screen","privileged_status","get_phone_settings","set_phone_setting","probe_root","root_processes","set_wifi","force_stop_app"})
            result.put(J.obj("type","function","function",J.obj("name",name,"parameters",J.obj("type","object","properties",new JSONObject()))));
        return result;
    }
}

final class FixtureContext {
    private final File root;
    FixtureContext(){try{root=Files.createTempDirectory("hermes-runtime-fixture-").toFile();}catch(Exception e){throw new RuntimeException(e);}}
    File getFilesDir(){return root;}
}
final class FixtureApprovals {
    boolean allow=true;int calls;Runnable onAsk;
    boolean ask(String title,String detail,boolean elevated){calls++;if(onAsk!=null)onAsk.run();return allow;}
}

/** Privileged service is unavailable unless explicitly supplied by a gate test. */
final class FixtureShizuku {
    JSONObject serviceState=J.obj("available",false);
    int terminalCalls,cancelCalls;
    JSONObject status(){return serviceState;}
    void setGlobalScope(boolean value){}
    JSONObject terminal(JSONObject args)throws Exception{terminalCalls++;throw new IllegalStateException("Fixture does not execute privileged Android commands");}
    JSONObject terminalProcess(JSONObject args)throws Exception{terminalCalls++;throw new IllegalStateException("Fixture does not execute privileged Android processes");}
    void cancelTerminalSessions(){cancelCalls++;}
}

/** Schema-only Android SAF substitute. Actual grant/file IO is emulator-tested. */
final class PhoneFiles {
    static boolean handles(String name){return Arrays.asList("phone_list_files","phone_read_file","phone_write_file").contains(name);}
    static JSONArray schemas(){JSONArray result=new JSONArray();for(String name:new String[]{"phone_list_files","phone_read_file","phone_write_file"})result.put(J.obj("type","function","function",J.obj("name",name,"parameters",J.obj("type","object","properties",new JSONObject()))));return result;}
}

/** Schema-only intent fixture; real schemas and validation use the isolated SDK-source harness. */
final class PhoneIntentTools {
 static boolean handles(String name){return Arrays.asList("open_link","open_map","share_text","compose_message","set_alarm").contains(name);}
 static JSONArray schemas(){JSONArray out=new JSONArray();for(String name:new String[]{"open_link","open_map","share_text","compose_message","set_alarm"})out.put(J.obj("type","function","function",J.obj("name",name,"parameters",J.obj("type","object","properties",new JSONObject()))));return out;}
}

/** Callback-only fixture; real production file capture is tested by DiagnosticsHarness. */
final class Diagnostics {static void record(FixtureContext context,String phase,Throwable error){}}

/** Schema-only browser fixture; real Android intent dispatch belongs to native verification. */
final class BrowserSearch {
 static boolean handles(String name){return "browser_search".equals(name);}
 static JSONArray schemas(){return new JSONArray().put(J.obj("type","function","function",J.obj("name","browser_search","parameters",J.obj("type","object","properties",new JSONObject()))));}
}
