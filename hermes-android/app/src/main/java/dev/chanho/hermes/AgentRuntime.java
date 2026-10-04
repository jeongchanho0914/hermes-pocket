package dev.chanho.hermes;

import android.content.*;
import org.json.*;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.*;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

final class AgentRuntime {
    private static AgentRuntime instance;
    static synchronized AgentRuntime get(Context c){if(instance==null)instance=new AgentRuntime(c.getApplicationContext());return instance;}
    final Context context;
    final Store store;
    final RootGateway root;
    final ShizukuPhoneBridge shizuku;
    final ApprovalGate approvals;
    final DeviceTools tools;
    final PhoneIntentTools phoneIntents;
    final LocalAgentTools localAgentTools;
    final TerminalTools terminalTools;
    final PhoneFiles phoneFiles;
    final RuntimeJobs jobs;
    final Net net=new Net();
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy=new AtomicBoolean(false),stop=new AtomicBoolean(false);
    private final StringBuilder live=new StringBuilder();
    private volatile long liveTimelineOrder=0;
    private volatile String sid="",status="",runId="";
    private final AtomicLong activitySequence=new AtomicLong();
    private final ThreadLocal<ToolActivity> toolActivity=new ThreadLocal<>();
    private static final class ToolActivity {
        final String run,session,call,name,args;
        ToolActivity(String run,String session,String call,String name,String args){this.run=run;this.session=session;this.call=call;this.name=name;this.args=args;}
    }
    private volatile boolean ownerTask=false;
    interface Observer { void event(String type,JSONObject data); }
    private final CopyOnWriteArrayList<Observer> observers=new CopyOnWriteArrayList<>();
    private final Handler events=new Handler(Looper.getMainLooper());
    static final class CurrentState {
        final String sessionId,status,liveText;
        final boolean busy,ownerTask,cancelled;
        CurrentState(String session,String status,String live,boolean busy,boolean ownerTask,boolean cancelled){this.sessionId=session;this.status=status;this.liveText=live;this.busy=busy;this.ownerTask=ownerTask;this.cancelled=cancelled;}
    }
    void addObserver(Observer observer){observers.addIfAbsent(observer);}
    void removeObserver(Observer observer){observers.remove(observer);}
    CurrentState currentState(){return new CurrentState(sid,status,liveText(),busy(),ownerTask,cancelled());}
    private volatile JSONObject runConfig;
    private volatile JSONObject currentModelProgress=new JSONObject();
    private volatile String runToken="";
    private volatile CountDownLatch foregroundReady;
    private Object activeForegroundService;
    private boolean serviceAcknowledged;
    private volatile int pendingWorkerStarts;
    private long workerStopGeneration;

    private final Set<Object> expectedForegroundStops=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
    synchronized void foregroundServiceCreated(Object service){activeForegroundService=service;serviceAcknowledged=false;}
    synchronized boolean foregroundServiceDestroyed(Object service){
        boolean expected=expectedForegroundStops.remove(service);
        if(activeForegroundService==service){activeForegroundService=null;serviceAcknowledged=false;notifyAll();}
        return expected;
    }
    synchronized void foregroundReady(Object service){
        // A service whose stop transaction is already queued cannot acknowledge a new run.
        if(expectedForegroundStops.contains(service))return;
        serviceAcknowledged=true;notifyAll();
        CountDownLatch ready=foregroundReady;if(ready!=null)ready.countDown();
    }
    private AgentRuntime(Context c){context=c;store=new Store(c);shizuku=new ShizukuPhoneBridge(c);root=new RootGateway(store,shizuku);approvals=new ApprovalGate(c);tools=new DeviceTools(c,this);phoneIntents=new PhoneIntentTools(this);terminalTools=new TerminalTools(this);terminalTools.setJobsListener(this::terminalJobsChanged);localAgentTools=new LocalAgentTools(this);phoneFiles=new PhoneFiles(this);jobs=new RuntimeJobs(this);shizuku.setListener(status->{emit("shizuku",status);emit("device",tools.state());});shizuku.setGlobalScope("all".equals(store.deviceScope()));shizuku.setEnabled(store.flag("shizuku_enabled"));}
    boolean cancelled(){return stop.get();}
    boolean busy(){return busy.get();}
    private synchronized boolean acquireBusy(){return busy.compareAndSet(false,true);}
    boolean terminalHasJobs(){return terminalTools.hasRunningJobs();}
    boolean anyWork(){return busy()||terminalHasJobs()||jobs.hasActive()||pendingWorkerStarts>0;}
    synchronized long beginWorkerStart(){pendingWorkerStarts++;return workerStopGeneration;}
    synchronized void checkWorkerStart(long generation) throws InterruptedException {
        if(generation!=workerStopGeneration)throw new InterruptedException("작업 시작 중 사용자가 중단했습니다.");
    }
    void startWorkerService(long generation) throws Exception {
        synchronized(this){checkWorkerStart(generation);}
        context.startForegroundService(new Intent(context,AgentForegroundService.class));
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        synchronized(this){
            while(activeForegroundService==null||!serviceAcknowledged||expectedForegroundStops.contains(activeForegroundService)){
                checkWorkerStart(generation);long remaining=deadline-System.nanoTime();
                if(remaining<=0)throw new IllegalStateException("독립 작업 알림 서비스를 시작하지 못했습니다.");
                TimeUnit.NANOSECONDS.timedWait(this,remaining);
            }
            checkWorkerStart(generation);
        }
    }
    synchronized void endWorkerStart(){pendingWorkerStarts=Math.max(0,pendingWorkerStarts-1);releaseForegroundIfIdle();}
    void privateJobsChanged(){emit("jobs",jobs.state());releaseForegroundIfIdle();}

    String terminalStatus(){return terminalHasJobs()?"터미널 작업 실행 중":"터미널 작업 없음";}
    private void terminalJobsChanged(){
        emit("terminal",terminalTools.state());
        releaseForegroundIfIdle();
    }
    private void releaseForegroundIfIdle(){
        // Recheck on the same UI queue used by start requests. Teardown is tied to
        // the old service instance so its later onDestroy cannot cancel a new run.
        events.post(()->{
            synchronized(this){
                if(anyWork()||activeForegroundService==null)return;
                Object service=activeForegroundService;
                expectedForegroundStops.add(service);
                if(!context.stopService(new Intent(context,AgentForegroundService.class)))expectedForegroundStops.remove(service);
            }
        });
    }
    private void awaitForegroundService() throws Exception {
        CountDownLatch ready=new CountDownLatch(1);foregroundReady=ready;
        context.startForegroundService(new Intent(context,AgentForegroundService.class));
        if(!ready.await(10,TimeUnit.SECONDS))throw new IllegalStateException("백그라운드 작업 알림을 시작하지 못했습니다. 앱을 다시 열어 주세요.");
        checkCancelled();
    }
    boolean isUnlocked(){return !context.getSystemService(android.app.KeyguardManager.class).isDeviceLocked();}
    void checkCancelled() throws InterruptedException {if(cancelled())throw new InterruptedException("사용자가 중단했습니다.");}
    synchronized String liveText(){return live.toString();}
    synchronized void append(String text){if(liveTimelineOrder==0)liveTimelineOrder=store.reserveResponseOrder(sid,runId,Math.max(1,currentModelProgress.optInt("round",1)));live.append(text);emit("delta",J.obj("text",text,"session",sid,"runId",runId,"timelineOrder",liveTimelineOrder));}
    void emit(String type,JSONObject data){
        ToolActivity active=toolActivity.get();
        if("tool".equals(type)&&active!=null&&data.optString("status").contains("승인"))recordToolActivity(active,"approval","사용자 승인 대기");
        if("status".equals(type))status=data.optString("message",status);
        if("tool".equals(type))status=data.optString("status",status);
        final JSONObject safe=DirectAgent.sanitizedResult(data);
        events.post(()->{MainActivity a=MainActivity.current();if(a!=null)a.event(type,safe);for(Observer observer:observers)try{observer.event(type,safe);}catch(RuntimeException ignored){}});
    }
    JSONObject snapshot(){JSONObject config=store.config();return J.obj("config",config,"availableTools",LocalCapabilities.inventory(config),"capabilities",LocalCapabilities.catalog(),"files",phoneFiles.status(),"terminal",terminalTools.state(),"jobs",jobs.state(),"shizuku",shizuku.status(),"sessions",store.sessions(),"device",tools.state(),"audit",store.audit(),"busy",busy(),"session",sid,"runId",runId,"modelProgress",currentModelProgress,"live",liveText(),"liveTimelineOrder",liveTimelineOrder,"liveOffset",0,"status",status,"version",BuildConfig.VERSION_NAME,"versionCode",BuildConfig.VERSION_CODE,"releaseChannel",BuildConfig.RELEASE_CHANNEL);}
    void start(String text,String requestedSid) throws Exception {
        if(text.trim().isEmpty()||text.length()>12000)throw new IllegalArgumentException("명령은 1~12,000자로 입력해 주세요.");
        if(!acquireBusy())throw new IllegalStateException("진행 중인 작업을 먼저 중단하거나 완료해 주세요.");
        try{
            stop.set(false);runId=UUID.randomUUID().toString();activitySequence.set(0);currentModelProgress=new JSONObject();net.reset();runConfig=store.config();runToken=store.secret("token");
            ownerTask=true;sid=store.exists(requestedSid)?requestedSid:store.newSession(text);synchronized(this){live.setLength(0);liveTimelineOrder=0;}
            JSONObject userMessage=store.message(sid,"user",text,runId);status="연결 중";
            final CountDownLatch ready=new CountDownLatch(1);foregroundReady=ready;
            context.startForegroundService(new Intent(context,AgentForegroundService.class));
            emit("started",J.obj("session",sid,"runId",runId,"text",text,"message",userMessage,"messageId",userMessage.optLong("messageId"),"created",userMessage.optLong("created"),"timelineOrder",userMessage.optLong("timelineOrder")));
            worker.submit(()->{
                try{
                    // Do not race stopService against Android's still-pending service start transaction.
                    // The service releases this only after startForeground has returned successfully.
                    if(!ready.await(10,TimeUnit.SECONDS))throw new IllegalStateException("백그라운드 작업 알림을 시작하지 못했습니다. 앱을 다시 열어 주세요.");
                    if(cancelled())throw new InterruptedException("사용자가 중단했습니다.");
                    String response=new DirectAgent(this).run(sid,text,runConfig,runToken);
                    if(cancelled())throw new InterruptedException("사용자가 중단했습니다.");
                    JSONObject answer=store.message(sid,"assistant",response,runId,liveTimelineOrder);status="완료";emit("complete",J.obj("session",sid,"runId",runId,"text",response,"message",answer,"messageId",answer.optLong("messageId"),"created",answer.optLong("created"),"timelineOrder",answer.optLong("timelineOrder")));
                }catch(Exception e){
                    if(!cancelled()&&unexpectedFailure(e))Diagnostics.record(context,"runtime",e);
                    String partial=liveText();JSONObject interrupted=partial.isEmpty()?null:store.message(sid,"assistant","[미완료 응답 · 실행 완료를 의미하지 않습니다]\n"+partial,runId,liveTimelineOrder);
                    String message=cancelled()?"작업을 중단했습니다. 이미 실행된 작업은 감사 기록에서 확인하세요.":J.error(e);JSONObject errorMessage=store.message(sid,"error",message,runId);status=cancelled()?"중단됨":"오류";
                    emit("failure",J.obj("session",sid,"runId",runId,"message",message,"messageId",errorMessage.optLong("messageId"),"created",errorMessage.optLong("created"),"timelineOrder",errorMessage.optLong("timelineOrder"),"errorMessage",errorMessage,"partialMessage",interrupted==null?JSONObject.NULL:interrupted));}
                finally{runToken="";foregroundReady=null;busy.set(false);releaseForegroundIfIdle();emit("settled",J.obj("sessions",store.sessions(),"device",tools.state(),"audit",store.audit()));}
            });
        }catch(Exception e){runToken="";foregroundReady=null;busy.set(false);releaseForegroundIfIdle();throw e;}
    }
    static boolean unexpectedFailure(Throwable error){
        int depth=0;for(Throwable cause=error;cause!=null&&depth++<16;cause=cause.getCause())if(cause instanceof InterruptedException||cause instanceof java.util.concurrent.CancellationException||cause instanceof SecurityException||cause instanceof IllegalArgumentException)return false;
        return true;
    }
    void compactConversation(String requestedSid,String request){
        if(!store.exists(requestedSid))throw new IllegalArgumentException("요약할 대화를 선택하세요.");
        if(!acquireBusy())throw new IllegalStateException("진행 중인 작업이 끝난 뒤 요약해 주세요.");
        stop.set(false);ownerTask=false;sid=requestedSid;runId=UUID.randomUUID().toString();activitySequence.set(0);net.reset();currentModelProgress=new JSONObject();status="대화 요약 중";
        synchronized(this){live.setLength(0);liveTimelineOrder=0;}
        try{worker.submit(()->{
            try{awaitForegroundService();JSONObject config=store.config();String token=store.secret("token");String summary=new DirectAgent(this).compact(requestedSid,config,token);checkCancelled();status="대화 요약 완료";MainActivity.replyStatic(request,true,J.obj("compacted",true,"session",requestedSid,"summary",summary));}
            catch(Exception e){status=cancelled()?"중단됨":"요약 오류";if(!cancelled()&&unexpectedFailure(e))Diagnostics.record(context,"runtime",e);MainActivity.replyStatic(request,false,J.obj("message",J.error(e)));}
            finally{foregroundReady=null;busy.set(false);releaseForegroundIfIdle();emit("settled",J.obj("sessions",store.sessions(),"device",tools.state(),"audit",store.audit()));}
        });}catch(RuntimeException failed){busy.set(false);releaseForegroundIfIdle();throw failed;}
    }
    JSONObject listModels() throws Exception {
        if(busy())throw new IllegalStateException("작업이 끝난 뒤 모델 목록을 불러와 주세요.");
        JSONObject cfg=store.config();Net query=new Net();
        JSONObject response=query.json(cfg.getString("endpoint"),"/models",store.secret("token"),cfg.optBoolean("allowLan"),"GET",null);
        JSONObject normalized=LocalCapabilities.normalizeModels(response);store.rememberModelMetadata(cfg.getString("endpoint"),normalized);return normalized;
    }
    JSONObject testConnection() throws Exception {
        if(busy())throw new IllegalStateException("작업이 끝난 뒤 연결을 테스트해 주세요.");
        JSONObject cfg=store.config();Net test=new Net();
        JSONObject response=test.json(cfg.getString("endpoint"),"/models",store.secret("token"),cfg.optBoolean("allowLan"),"GET",null);
        String model=cfg.optString("model","");boolean verified=false;
        if(!model.isEmpty()){
            JSONObject probe=test.json(cfg.getString("endpoint"),"/chat/completions",store.secret("token"),cfg.optBoolean("allowLan"),"POST",
                connectionProbe(cfg));
            JSONArray choices=probe.optJSONArray("choices");
            if(choices==null||choices.length()==0||choices.getJSONObject(0).optJSONObject("message")==null)throw new IllegalStateException("모델이 올바른 채팅 응답을 반환하지 않았습니다.");
            verified=true;
        }
        return J.obj("ok",true,"data",response,"mode",cfg.optString("mode"),"modelVerified",verified);
    }
    private static JSONObject connectionProbe(JSONObject cfg) throws JSONException {
        JSONObject body=J.obj("model",cfg.optString("model"),"messages",new JSONArray().put(J.obj("role","user","content","Reply with OK.")),"stream",false);
        LocalCapabilities.configureReasoning(body,cfg);return body;
    }
    JSONObject probeRoot() throws Exception {
        if(!acquireBusy())throw new IllegalStateException("작업이 끝난 뒤 Root를 확인해 주세요.");
        stop.set(false);ownerTask=false;
        try{
            rootProbePreflight();
            if(!approvals.ask("Root 상태 확인","이미 루팅된 본인 기기에서 su 권한을 요청하고 UID만 확인합니다. Android Root 권한은 별도로 필요합니다.",false))throw new SecurityException("Root 확인을 취소했습니다.");
            if(cancelled())throw new InterruptedException("사용자가 중단했습니다.");
            rootProbePreflight();return root.probe();
        }finally{busy.set(false);releaseForegroundIfIdle();emit("device",tools.state());}
    }
    private void rootProbePreflight(){
        if(cancelled())throw new IllegalStateException("사용자가 중단했습니다.");
        if(!store.flag("root_enabled"))throw new SecurityException("설정에서 Root 연동을 먼저 켜 주세요. 실제 su 권한은 별도로 필요합니다.");
        if(context.getSystemService(android.app.KeyguardManager.class).isDeviceLocked())throw new SecurityException("휴대폰 잠금을 먼저 직접 해제해 주세요.");
    }
    synchronized void stopAll(){
        stop.set(true);workerStopGeneration++;notifyAll();jobs.cancelAll();net.cancel();terminalTools.cancel();root.cancel();shizuku.cancel();approvals.cancel();
        emit("status",J.obj("message","중단 요청됨 · 추가 기기 동작 차단"));
    }
    JSONObject setShizuku(boolean enabled){
        if(busy())throw new IllegalStateException("작업이 끝난 뒤 Shizuku 연결을 변경해 주세요.");
        store.flag("shizuku_enabled",enabled);shizuku.setGlobalScope("all".equals(store.deviceScope()));shizuku.setEnabled(enabled);
        return J.obj("shizuku",shizuku.status(),"config",store.config(),"device",tools.state());
    }
    private void recordToolActivity(ToolActivity active,String phase,String summary){
        JSONObject event=J.obj("runId",active.run,"session",active.session,"toolCallId",active.call,"seq",activitySequence.incrementAndGet(),"kind","tool","name",active.name,"status",phase,"summary",summary,"argsSummary",active.args,"timestamp",System.currentTimeMillis());
        event=store.recordActivity(event);emit("activity",event);
    }
    void modelProgress(String phase,int round,long elapsedMs){
        String label;
        switch(phase){
            case "sending":label="모델 API 요청 중";break;
            case "receiving":label="모델 응답 수신 중";break;
            case "thinking":label="모델 생각 중";break;
            case "tool_preparing":label="도구 요청 준비 중";break;
            case "answering":label="답변 작성 중";break;
            case "completed":label="모델 응답 수신 완료";break;
            case "cancelled":label="모델 응답 중단";break;
            default:label="모델 응답 오류";
        }
        currentModelProgress=J.obj("session",sid,"runId",runId,"phase",phase,"round",round,"elapsedMs",elapsedMs,"timestamp",System.currentTimeMillis());
        emit("modelProgress",currentModelProgress);
        emit("status",J.obj("message",label,"session",sid,"runId",runId,"round",round,"elapsedMs",elapsedMs));
    }
    void providerThought(String text,int round,boolean truncated,long elapsedMs){
        if(runConfig==null||!LocalCapabilities.isMiMo(runConfig))return;
        JSONObject thought=J.obj("session",sid,"runId",runId,"round",round,"provider","mimo","source","mimo.reasoning_content","label","모델이 공개한 생각","text",DirectAgent.providerThoughtText(text),"truncated",truncated,"elapsedMs",elapsedMs,"timestamp",System.currentTimeMillis());
        thought=store.recordProviderThought(thought);emit("providerThought",thought);
    }
    void visibleResponse(String text,int round){
        JSONObject event=J.obj("runId",runId,"session",sid,"toolCallId","response_"+round,"seq",activitySequence.incrementAndGet(),"kind","response","name","assistant","status","completed","summary",DirectAgent.publicResponseSummary(text),"text",J.clipped(text,16000),"textTruncated",text.length()>16000,"argsSummary","","timestamp",System.currentTimeMillis());
        event=store.recordActivity(event);synchronized(this){live.setLength(0);liveTimelineOrder=0;}emit("activity",event);
    }
    void toolRejected(String name,JSONObject args,String callId,JSONObject result){
        ToolActivity active=new ToolActivity(runId,ownerTask?sid:"",callId,LocalCapabilities.auditToolName(name),DirectAgent.toolArgumentSummary(name,args));
        recordToolActivity(active,cancelled()?"cancelled":"failed",result.optBoolean("denied")?"이미 거부된 작업 · 실행하지 않음":"요청 조건 미충족 · 실행하지 않음");
    }
    JSONObject executeTool(String name,JSONObject args) throws Exception {return executeTool(name,args,"local_"+UUID.randomUUID());}
    JSONObject executeTool(String name,JSONObject args,String callId) throws Exception {
        if(cancelled()){toolRejected(name,args,callId,J.obj("ok",false));throw new InterruptedException("사용자가 중단했습니다.");}
        ToolActivity active=new ToolActivity(runId,ownerTask?sid:"",callId,LocalCapabilities.auditToolName(name),DirectAgent.toolArgumentSummary(name,args));
        toolActivity.set(active);recordToolActivity(active,"started",DirectAgent.toolStartSummary(name,args));
        try{
            JSONObject result=dispatchTool(name,args);
            boolean failed=DirectAgent.toolResultFailed(result);
            recordToolActivity(active,cancelled()?"cancelled":failed?"failed":"completed",DirectAgent.toolResultSummary(name,args,result));return result;
        }catch(ObservationRequired changed){recordToolActivity(active,"failed","화면 정보 갱신 필요 · 이전 대상 자동 재시도 안 함");return changed.result();}
        catch(Exception e){if(!cancelled()&&unexpectedFailure(e))Diagnostics.record(context,"tool",e);recordToolActivity(active,cancelled()?"cancelled":"failed",cancelled()?"사용자가 중단했습니다.":"도구 실행 오류 · 응답과 감사 기록을 확인하세요.");throw e;}
        finally{toolActivity.remove();}
    }
    private JSONObject dispatchTool(String name,JSONObject args) throws Exception {
        if(cancelled())throw new InterruptedException("사용자가 중단했습니다.");
        if("act_on_screen".equals(name))return new SemanticActions(this).execute(args);
        if(RuntimeJobs.handles(name))return jobs.execute(name,args);
        if(BrowserSearch.handles(name))return new BrowserSearch(this).execute(name,args);
        if(PhoneIntentTools.handles(name))return phoneIntents.execute(name,args);
        if(TerminalTools.handles(name))return terminalTools.execute(name,args);
        if(PhoneFiles.handles(name))return phoneFiles.execute(name,args);
        if(WebTools.handles(name)){
            if("web_fetch".equals(name)&&"browser".equals(WebTools.fetchMode(args)))return new BrowserSearch(this).execute("browser_open",WebTools.browserFetchArguments(args));
            if("web_search".equals(name)&&"browser".equals(WebTools.searchMode(args)))return new BrowserSearch(this).execute(WebTools.browserArguments(args));
            emit("tool",J.obj("name",name,"status","web_search".equals(name)?"웹 검색 중":"페이지 읽는 중"));
            try{String provider=store.webProvider();JSONObject result=WebTools.execute(name,args,net,"tavily".equals(provider)?store.secret("web_token"):"",provider,store.webEndpoint());store.audit(name,"completed","웹 API 도구 완료 · 검색어와 키는 기록하지 않음");return result;}
            catch(Exception e){store.audit(name,"failed","웹 API 도구 실패 · 상세 내용은 기록하지 않음");throw e;}
        }
        if(LocalAgentTools.handles(name))return localAgentTools.execute(name,args);
        return tools.execute(name,args);
    }
    void localTool(String name,JSONObject args,String request) throws Exception {
        if(!acquireBusy())throw new IllegalStateException("진행 중인 작업이 있습니다.");
        stop.set(false);ownerTask=false;runId=UUID.randomUUID().toString();activitySequence.set(0);net.reset();final JSONObject config=store.config();worker.submit(()->{try{if("act_on_screen".equals(name)||TerminalTools.handles(name)||BrowserSearch.handles(name)||WebTools.handles(name))awaitForegroundService();if(LocalCapabilities.pluginFor(name).isEmpty()){store.audit(LocalCapabilities.auditToolName(name),"failed","미등록 도구 요청 거부 · 인자는 기록하지 않음");throw new IllegalArgumentException("등록되지 않은 도구 이름입니다. 정확한 도구 이름을 사용하세요.");}if(!LocalCapabilities.allowed(name,config)){store.audit(name,"failed","꺼진 플러그인의 도구 요청 거부 · 인자는 기록하지 않음");throw new IllegalStateException("이 도구의 플러그인이 꺼져 있습니다. + 메뉴에서 켜 주세요.");}MainActivity.replyStatic(request,true,DirectAgent.sanitizedResult(executeTool(name,args)));}catch(Exception e){MainActivity.replyStatic(request,false,J.obj("message",J.error(e)));}finally{foregroundReady=null;busy.set(false);releaseForegroundIfIdle();emit("device",tools.state());}});
    }
}
