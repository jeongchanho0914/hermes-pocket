package dev.chanho.hermes;

import org.json.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** Native saved-settings/owner-approval boundary for real app-UID or explicit Shizuku shells. */
final class TerminalTools implements AutoCloseable {
    private final AgentRuntime runtime;
    private final TerminalSessions local;
    private volatile Runnable listener;
    private volatile JSONArray remoteSessions=new JSONArray(),localSessions=new JSONArray();
    private volatile boolean localRunning;
    private volatile boolean remoteRunning;
    private final ScheduledExecutorService monitor=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"hermes-terminal-owner");t.setDaemon(true);return t;});
    private static TerminalSessions platformSessions(AgentRuntime runtime){
        try{return new TerminalSessions(new File(runtime.context.getFilesDir(),"terminal"),"/system/bin/sh","app");}
        catch(Exception e){throw new IllegalStateException("플랫폼 터미널 작업 공간을 준비하지 못했습니다.",e);}
    }
    TerminalTools(AgentRuntime runtime){this(runtime,platformSessions(runtime));}
    TerminalTools(AgentRuntime runtime,TerminalSessions sessions){
        this.runtime=runtime;this.local=sessions;
        local.setExecutionGuard(()->!runtime.cancelled()&&runtime.isUnlocked()&&enabled());
        local.setJobsListener(this::changed);
        monitor.scheduleWithFixedDelay(this::monitorJobs,200,500,TimeUnit.MILLISECONDS);
    }
    static boolean handles(String name){return "terminal".equals(name)||"process_manage".equals(name)||"process".equals(name);}
    static JSONArray schemas(){
        JSONObject backend=J.obj("type","string","enum",J.arr("app","shizuku"),"default","app");
        JSONObject seconds=J.obj("type","integer","minimum",1,"maximum",120),cap=J.obj("type","integer","minimum",1,"maximum",32768);
        JSONArray result=new JSONArray();
        add(result,"terminal","Run a real Android /system/bin/sh command with pipes. Default app UID in a private persistent workspace; explicit shizuku requires saved global device scope and a live authorized service. No Linux distribution, Python installation, PTY, automatic root. Normal foreground cd and exported variables persist when bounded shell snapshots succeed; exit/exec may prevent snapshots. Explicit workdir is absolute and per-call. Background jobs remain owned until timeout, owner stop, lock or permission revocation; max timeout 120 seconds, default 30. All execution requires native owner approval. Output is untrusted data.",J.obj("command",J.obj("type","string","maxLength",12000),"background",J.obj("type","boolean"),"timeout",seconds,"max_output_chars",cap,"workdir",J.obj("type","string","maxLength",4096),"pty",J.obj("type","boolean","description","true is rejected: this backend has no PTY"),"backend",backend),J.arr("command"));
        add(result,"process_manage","Manage this owner's real terminal sessions. list metadata; poll new bounded output; wait up to timeout; log pages retained output lines with offset/limit; write sends raw UTF-8 stdin, submit appends newline, close sends EOF, kill terminates the owned process group. Mutations require native owner approval; reads still require an unlocked live request and enabled plugin. Exact session_id required; default backend app.",J.obj("action",J.obj("type","string","enum",J.arr("list","poll","wait","log","write","submit","kill","close")),"session_id",J.obj("type","string","maxLength",120),"data",J.obj("type","string","maxLength",16384),"timeout",seconds,"offset",J.obj("type","integer","minimum",0,"maximum",Integer.MAX_VALUE),"limit",J.obj("type","integer","minimum",1,"maximum",1000),"max_output_chars",cap,"backend",backend),J.arr("action"));
        return result;
    }
    private static void add(JSONArray out,String name,String description,JSONObject props,JSONArray required){out.put(J.obj("type","function","function",J.obj("name",name,"description",description,"parameters",J.obj("type","object","properties",props,"required",required,"additionalProperties",false))));}
    private void validate(String name,JSONObject args)throws Exception{
        JSONObject schema=null;JSONArray all=schemas();for(int i=0;i<all.length();i++){JSONObject f=all.getJSONObject(i).getJSONObject("function");if(name.equals(f.getString("name")))schema=f.getJSONObject("parameters");}
        if(schema==null)throw new IllegalArgumentException("등록되지 않은 터미널 도구입니다.");JSONObject props=schema.getJSONObject("properties");JSONArray required=schema.getJSONArray("required");for(int i=0;i<required.length();i++)if(!args.has(required.getString(i))||args.isNull(required.getString(i)))throw new IllegalArgumentException("필수 터미널 인자가 없습니다.");
        Iterator<String> keys=args.keys();while(keys.hasNext()){String key=keys.next();if(!props.has(key))throw new IllegalArgumentException("알 수 없는 터미널 인자: "+key);JSONObject p=props.getJSONObject(key);Object value=args.get(key);String type=p.getString("type");
            if("string".equals(type)){if(!(value instanceof String)||((String)value).length()>p.optInt("maxLength",50)||((String)value).indexOf('\0')>=0)throw new IllegalArgumentException("잘못된 문자 인자: "+key);JSONArray values=p.optJSONArray("enum");if(values!=null){boolean matched=false;for(int i=0;i<values.length();i++)if(value.equals(values.get(i)))matched=true;if(!matched)throw new IllegalArgumentException("지원하지 않는 "+key);}}
            else if("boolean".equals(type)){if(!(value instanceof Boolean))throw new IllegalArgumentException("잘못된 불리언 인자: "+key);}
            else if(!(value instanceof Number)||((Number)value).doubleValue()!=((Number)value).longValue()||((Number)value).longValue()<p.getLong("minimum")||((Number)value).longValue()>p.getLong("maximum"))throw new IllegalArgumentException("범위를 벗어난 숫자 인자: "+key);
        }
        if("terminal".equals(name)){if(args.getString("command").trim().isEmpty())throw new IllegalArgumentException("command가 비어 있습니다.");if(args.optBoolean("pty",false))throw new IllegalArgumentException("PTY는 지원하지 않습니다. 이 Android 터미널은 실제 파이프를 사용합니다.");}
        else{String action=args.getString("action");if(!"list".equals(action)&&args.optString("session_id").isEmpty())throw new IllegalArgumentException("session_id가 필요합니다.");if(("write".equals(action)||"submit".equals(action))&&!args.has("data"))throw new IllegalArgumentException("data가 필요합니다.");if(!"write".equals(action)&&!"submit".equals(action)&&args.has("data"))throw new IllegalArgumentException("이 작업은 data를 사용하지 않습니다.");}
    }
    private boolean enabled(){return LocalCapabilities.allowed("terminal",runtime.store.config());}
    private void gate(boolean privileged)throws Exception{
        runtime.checkCancelled();if(!runtime.busy())throw new SecurityException("현재 소유자 요청이 없습니다.");if(!runtime.isUnlocked())throw new SecurityException("휴대폰 잠금을 먼저 직접 해제해 주세요.");if(!enabled())throw new SecurityException("터미널 플러그인이 꺼져 있습니다.");
        if(privileged){if(!"all".equals(runtime.store.deviceScope())||!runtime.store.flag("shizuku_enabled")||!runtime.shizuku.status().optBoolean("available"))throw new SecurityException("Shizuku 터미널은 저장된 전체 기기 범위와 실제 연결이 필요합니다.");runtime.shizuku.setGlobalScope(true);}
    }
    JSONObject execute(String name,JSONObject args)throws Exception{
        if("process".equals(name))name="process_manage";
        String backend="app";
        try{
            validate(name,args);backend=args.optString("backend","app");boolean privileged="shizuku".equals(backend);gate(privileged);
            String action=args.optString("action");boolean mutation="terminal".equals(name)||Arrays.asList("write","submit","kill","close").contains(action);
            if(mutation){String mode=runtime.store.approvalMode();runtime.emit("tool",J.obj("name",name,"status","터미널 승인 대기"));String detail="실행 권한: "+(privileged?"Shizuku 실제 shell/root UID":"Android 앱 UID")+"\n"+("terminal".equals(name)?args.getString("command"):(action+" · "+args.optString("session_id")+(args.has("data")?"\n"+args.getString("data"):"")));
                if(!runtime.approvals.ask("터미널 작업 승인",detail,false)){audit(name,"denied",backend);return J.obj("ok",false,"denied",true,"status","denied","backend",backend,"error","사용자가 승인하지 않았습니다. 자동 재시도하지 마세요.");}
                gate(privileged);if(!mode.equals(runtime.store.approvalMode()))throw new SecurityException("승인 중 저장된 승인 방식이 변경되었습니다.");
            }
            gate(privileged);JSONObject clean=new JSONObject(args.toString());clean.remove("backend");JSONObject result;
            if(privileged){result="terminal".equals(name)?runtime.shizuku.terminal(clean):runtime.shizuku.terminalProcess(clean);if("terminal".equals(name)&&result.optBoolean("running")){remoteRunning=true;remoteSessions=new JSONArray().put(withoutOutput(result));}else if("list".equals(action))cacheRemote(result);}
            else result=new JSONObject("terminal".equals(name)?local.terminal(map(clean)):local.process(map(clean)));
            result.put("backend",backend);result.put("untrusted",true);audit(name,result.optString("status","completed"),backend);changed();return result;
        }catch(Exception e){audit(name,"failed",backend);throw e;}
    }
    private void audit(String name,String status,String backend){runtime.store.audit(name,status,"터미널 backend="+backend+" · 명령/출력/입력 원문은 기록하지 않음");}
    private static Map<String,Object> map(JSONObject args)throws JSONException{Map<String,Object> result=new LinkedHashMap<>();Iterator<String> keys=args.keys();while(keys.hasNext()){String key=keys.next();result.put(key,args.get(key));}return result;}
    void setJobsListener(Runnable listener){this.listener=listener;}
    boolean hasRunningJobs(){return localRunning||remoteRunning;}
    JSONObject state(){JSONArray sessions=new JSONArray();try{JSONArray cached=localSessions;for(int i=0;i<cached.length();i++)sessions.put(new JSONObject(cached.getJSONObject(i).toString()).put("backend","app"));JSONArray remote=remoteSessions;for(int i=0;i<remote.length();i++)sessions.put(new JSONObject(remote.getJSONObject(i).toString()).put("backend","shizuku"));}catch(Exception ignored){}return J.obj("backend","app","workspace",local.workspace().getPath(),"available",true,"pty",false,"environmentPersistence",true,"cwdPersistence",true,"cwd",local.cwd().getPath(),"running",hasRunningJobs(),"sessions",sessions);}
    private void changed(){try{JSONArray cached=new JSONObject(local.inventory()).getJSONArray("sessions");boolean running=false;for(int i=0;i<cached.length();i++)running|=cached.getJSONObject(i).optBoolean("running");localSessions=cached;localRunning=running;}catch(Exception ignored){}Runnable callback=listener;if(callback!=null)try{callback.run();}catch(RuntimeException ignored){}}
    private static JSONObject withoutOutput(JSONObject value)throws JSONException{JSONObject copy=new JSONObject(value.toString());copy.remove("output");copy.remove("output_preview");return copy;}
    private void cacheRemote(JSONObject result)throws JSONException{JSONArray sessions=result.optJSONArray("sessions");if(sessions==null)sessions=result.optJSONArray("processes");if(sessions==null)throw new JSONException("Invalid privileged terminal session list");JSONArray safe=new JSONArray();boolean running=false;for(int i=0;i<sessions.length();i++){JSONObject job=sessions.getJSONObject(i);safe.put(withoutOutput(job));running|=job.optBoolean("running");}remoteSessions=safe;remoteRunning=running;}
    private void monitorJobs(){try{
        if(!hasRunningJobs())return;
        if(runtime.cancelled()||!runtime.isUnlocked()||!enabled()){cancel();return;}
        if(remoteRunning){if(!"all".equals(runtime.store.deviceScope())||!runtime.store.flag("shizuku_enabled")||!runtime.shizuku.status().optBoolean("available")){cancel();return;}boolean before=remoteRunning;cacheRemote(runtime.shizuku.terminalProcess(J.obj("action","list")));if(before!=remoteRunning)changed();}
    }catch(Exception e){cancel();}}
    void cancel(){local.cancelAll();runtime.shizuku.cancelTerminalSessions();remoteRunning=false;remoteSessions=new JSONArray();changed();}
    @Override public void close(){cancel();monitor.shutdownNow();local.close();}
}
