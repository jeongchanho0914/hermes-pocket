package dev.chanho.hermes;

import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded, independently cancellable workers. No network listening ports are created. */
final class TaskPool {
    interface Work { String run(Control control) throws Exception; }
    interface Listener { void changed(); }
    static final class Control {
        final String id;
        final long deadlineNanos;
        private final AtomicBoolean cancelled=new AtomicBoolean();
        private final CopyOnWriteArrayList<Runnable> cancellations=new CopyOnWriteArrayList<>();
        private final TaskLedger ledger;
        private final Listener listener;
        private JSONObject metrics=new JSONObject();
        Control(String id,long timeoutMs,TaskLedger ledger,Listener listener){this.id=id;deadlineNanos=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMs);this.ledger=ledger;this.listener=listener;}
        boolean cancelled(){return cancelled.get()||System.nanoTime()>=deadlineNanos||Thread.currentThread().isInterrupted();}
        void check() throws InterruptedException {if(cancelled())throw new InterruptedException("독립 작업이 중단되었거나 제한 시간에 도달했습니다.");}
        void onCancel(Runnable callback){cancellations.add(callback);if(cancelled())callback.run();}
        void cancel(){cancelled.set(true);for(Runnable callback:cancellations)try{callback.run();}catch(RuntimeException ignored){}}
        synchronized void progress(String phase,JSONObject usage) throws Exception {check();if(usage!=null)metrics=new JSONObject(usage.toString());ledger.update(id,"running",phase,null,null,metrics);listener.changed();}
        synchronized JSONObject metrics(){try{return new JSONObject(metrics.toString());}catch(JSONException impossible){return new JSONObject();}}
    }
    private final TaskLedger ledger;
    private final Listener listener;
    private final ExecutorService workers=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"Hermes-private-worker");t.setDaemon(true);return t;});
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"Hermes-job-deadline");t.setDaemon(true);return t;});
    private final ConcurrentHashMap<String,Control> active=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,Future<?>> futures=new ConcurrentHashMap<>();
    TaskPool(TaskLedger ledger,Listener listener){this.ledger=ledger;this.listener=listener;}
    synchronized String submit(String goal,String parent,String channel,long timeoutMs,Work work) throws Exception {
        if(timeoutMs<1000||timeoutMs>15*60*1000)throw new IllegalArgumentException("작업 제한 시간은 1초~15분이어야 합니다.");
        String id=ledger.create(goal,parent,channel);Control control=new Control(id,timeoutMs,ledger,listener);active.put(id,control);
        FutureTask<Void> task=new FutureTask<Void>(()->{
            try {
                control.progress("독립 작업 실행 중",null);String result=work.run(control);control.check();
                ledger.update(id,"completed","완료",result,"",control.metrics());
            }catch(InterruptedException e){Thread.currentThread().interrupt();ledger.update(id,"cancelled","중단됨 · 실행 결과를 확인하세요.",null,"작업 중단 또는 시간 제한",control.metrics());}
            catch(Exception e){ledger.update(id,control.cancelled()?"cancelled":"failed",control.cancelled()?"중단됨":"오류",null,safeError(e),control.metrics());}
            finally{active.remove(id);futures.remove(id);listener.changed();}
            return null;
        }) {
            @Override protected void done(){
                // Future.cancel may win before the queued runnable ever begins.
                if(isCancelled()){
                    try{ledger.update(id,"cancelling","중단 처리 중",null,"사용자가 중단했습니다.",control.metrics());}catch(Exception ignored){}
                    listener.changed();
                }
            }
            @Override public void run(){
                try{super.run();}
                finally{
                    if(isCancelled())try{ledger.update(id,"cancelled","중단 완료",null,"사용자가 중단했습니다.",control.metrics());}catch(Exception ignored){}
                    active.remove(id);futures.remove(id);listener.changed();
                }
            }
        };
        futures.put(id,task);
        try{workers.execute(task);timer.schedule(()->{if(active.containsKey(id))cancel(id);},timeoutMs,TimeUnit.MILLISECONDS);}
        catch(RuntimeException rejected){active.remove(id);futures.remove(id);ledger.update(id,"failed","실행기를 시작하지 못함",null,"작업 실행기를 사용할 수 없습니다.",null);throw rejected;}
        listener.changed();return id;
    }
    private static String safeError(Exception error){
        if(error instanceof Net.ApiError)return error.getMessage();
        if(error instanceof InterruptedException||error instanceof java.io.InterruptedIOException)return "독립 작업이 중단되었습니다.";
        if(error instanceof IllegalArgumentException||error instanceof SecurityException)return "도구 인자·허용 범위 또는 작업 설정을 확인하세요.";
        return "독립 작업을 완료하지 못했습니다 ("+error.getClass().getSimpleName()+"). 요청은 자동으로 재전송하지 않았습니다.";
    }
    boolean hasActive(){return !active.isEmpty();}
    int activeCount(){return active.size();}
    boolean cancel(String id){
        Control control=active.get(id);if(control==null)return false;
        try{ledger.update(id,"cancelling","중단 요청됨",null,null,control.metrics());}catch(Exception ignored){}
        control.cancel();Future<?> future=futures.get(id);if(future!=null)future.cancel(true);listener.changed();return true;
    }
    void cancelAll(){for(String id:new ArrayList<>(active.keySet()))cancel(id);}
    JSONObject get(String id) throws Exception {return ledger.get(id);}
    JSONArray list() throws Exception {return ledger.list(false);}
    JSONObject waitFor(String id,long timeoutMs) throws Exception {
        if(timeoutMs<0||timeoutMs>30000)throw new IllegalArgumentException("한 번의 작업 대기는 최대 30초입니다.");
        Future<?> future=futures.get(id);if(future!=null&&timeoutMs>0)try{future.get(timeoutMs,TimeUnit.MILLISECONDS);}catch(TimeoutException|CancellationException|ExecutionException ignored){}
        return ledger.get(id);
    }
    void shutdown(){cancelAll();workers.shutdownNow();timer.shutdownNow();}
}
