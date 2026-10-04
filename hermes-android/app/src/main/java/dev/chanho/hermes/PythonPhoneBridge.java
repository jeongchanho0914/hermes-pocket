package dev.chanho.hermes;

import org.json.*;
import java.util.HashSet;
import java.util.Set;

/** In-process interface for the embedded upstream engine. Not a Javascript bridge. */
public final class PythonPhoneBridge {
    private final AgentRuntime runtime;
    private final JSONObject config;
    private final String session;
    private final Set<String> denied=new HashSet<>();

    PythonPhoneBridge(AgentRuntime runtime,String session,JSONObject config) throws JSONException {
        this.runtime=runtime;this.session=session;this.config=new JSONObject(config.toString());
    }
    public String filesDir(){return runtime.context.getFilesDir().getAbsolutePath();}
    public boolean isCancelled(){return runtime.cancelled();}
    public String getToolSchemas() throws JSONException {return LocalCapabilities.schemas(config).toString();}
    public boolean askApproval(String title,String detail){
        if(isCancelled()||title==null||detail==null||title.length()>200||detail.length()>12000)return false;
        try{return runtime.approvals.ask(title,detail,false)&&!isCancelled();}
        catch(InterruptedException e){Thread.currentThread().interrupt();return false;}
        catch(Exception e){return false;}
    }
    public synchronized String executeTool(String name,String arguments){
        try{
            if(isCancelled())throw new InterruptedException("사용자가 중단했습니다.");
            if(name==null||LocalCapabilities.pluginFor(name).isEmpty()){
                runtime.store.audit(LocalCapabilities.auditToolName(name==null?"":name),"failed","원본 엔진의 미등록 도구 요청 거부 · 인자는 기록하지 않음");
                return J.obj("ok",false,"error","등록되지 않은 도구입니다. 제공된 함수 이름을 그대로 사용하세요.").toString();
            }
            if(!LocalCapabilities.allowed(name,config)){
                runtime.store.audit(name,"failed","원본 엔진의 꺼진 플러그인 요청 거부 · 인자는 기록하지 않음");
                return J.obj("ok",false,"error","이 도구의 플러그인이 꺼져 있습니다.").toString();
            }
            if(denied.contains(name))return J.obj("ok",false,"denied",true,"error","이번 작업에서 이미 거부된 도구는 자동 재시도할 수 없습니다.").toString();
            if(arguments==null||arguments.length()>20000)throw new IllegalArgumentException("도구 인자 크기가 올바르지 않습니다.");
            // Run directly inside the existing busy worker; localTool would reenter its busy gate.
            JSONObject result=runtime.executeTool(name,new JSONObject(arguments));
            if(result.optBoolean("denied")||result.optString("error","").contains("승인하지 않았습니다"))denied.add(name);
            return result.toString();
        }catch(InterruptedException e){return J.obj("ok",false,"cancelled",true,"error","사용자가 중단했습니다.").toString();}
        catch(Exception e){return J.obj("ok",false,"error",J.error(e)).toString();}
    }
    public void emitEvent(String eventJson) throws JSONException {
        if(eventJson==null||eventJson.length()>200000)throw new IllegalArgumentException("엔진 이벤트가 너무 큽니다.");
        JSONObject event=new JSONObject(eventJson),data=event.optJSONObject("data");
        if(data==null)data=new JSONObject();
        String type=event.optString("event");
        if("delta".equals(type)){
            if(!isCancelled())runtime.append(data.optString("text",""));
        }else if("status".equals(type)||"tool".equals(type)||"notice".equals(type))runtime.emit(type,data);
        else throw new IllegalArgumentException("지원하지 않는 엔진 이벤트입니다.");
    }
    public String getTranscript(String sessionId) throws JSONException {
        requireSession(sessionId);return runtime.store.transcript(session).toString();
    }
    public void saveTranscript(String sessionId,String transcript) throws JSONException {
        requireSession(sessionId);
        if(transcript==null||transcript.length()>4000000)throw new IllegalArgumentException("대화 기록이 너무 큽니다.");
        runtime.store.transcript(session,new JSONArray(transcript));
    }
    private void requireSession(String sessionId){
        if(!session.equals(sessionId))throw new SecurityException("현재 실행 중인 대화만 접근할 수 있습니다.");
    }
}
