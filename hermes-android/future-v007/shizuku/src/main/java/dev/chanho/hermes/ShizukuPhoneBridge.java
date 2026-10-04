package dev.chanho.hermes;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import org.json.JSONObject;
import rikka.shizuku.Shizuku;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Optional local privileged transport. Existing native approval remains the caller's responsibility. */
public final class ShizukuPhoneBridge implements AutoCloseable {
    public interface Listener { void onStatusChanged(JSONObject status); }
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService calls=Executors.newCachedThreadPool();
    private final Object operationLock=new Object();
    private volatile boolean enabled, connecting, closed;
    private volatile int uid=-1;
    private volatile IBinder service;
    private volatile Future<?> currentCall;
    private volatile String currentRequest;
    private volatile String failure="";
    private volatile Listener listener;
    private volatile boolean globalScope;
    private volatile int connectionAttempt;
    private final Shizuku.UserServiceArgs userServiceArgs;
    private final Shizuku.OnBinderReceivedListener received=()->{failure="";if(enabled)connect();publish();};
    private final Shizuku.OnBinderDeadListener dead=()->{drop("Shizuku 연결이 종료되었습니다. 휴대폰에서 다시 시작해 주세요.");};
    private final Shizuku.OnRequestPermissionResultListener permission=(requestCode,result)->{
        if(result==PackageManager.PERMISSION_GRANTED&&enabled){failure="";connect();}
        else drop("Shizuku 기기 제어 권한이 허용되지 않았습니다.");
        publish();
    };
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){
            connecting=false;
            if(closed||!enabled){removeService();return;}
            calls.execute(()->{
                try {
                    JSONObject identity=PhonePrivilegeProtocol.call(binder,PhonePrivilegeProtocol.STATUS,null);
                    int actual=identity.getInt("uid");
                    if(actual!=2000&&actual!=0)throw new SecurityException("Shizuku 서비스의 shell/root UID를 확인하지 못했습니다.");
                    if(identity.optInt("ownerUid",-1)!=context.getApplicationInfo().uid)throw new SecurityException("Shizuku 서비스의 소유 앱 UID가 다릅니다.");
                    if(!authorized())throw new SecurityException("Shizuku 권한 또는 연결이 종료되었습니다.");
                    binder.linkToDeath(()->{if(service==binder)drop("Shizuku 기기 서비스가 종료되었습니다.");},0);
                    if(closed||!enabled)return;
                    service=binder;uid=actual;failure="";publish();
                }catch(Exception error){drop(message(error));}
            });
        }
        @Override public void onServiceDisconnected(ComponentName name){drop("Shizuku 기기 서비스 연결이 종료되었습니다.");}
    };
    public ShizukuPhoneBridge(Context context){
        this.context=context.getApplicationContext();
        userServiceArgs=new Shizuku.UserServiceArgs(new ComponentName(this.context,PrivilegedPhoneService.class))
                .tag("hermes-phone-v1").version(1).daemon(false).debuggable(false).processNameSuffix("hermes_privileged");
        Shizuku.addBinderReceivedListenerSticky(received,main);
        Shizuku.addBinderDeadListener(dead,main);
        Shizuku.addRequestPermissionResultListener(permission,main);
    }
    public void setListener(Listener listener){this.listener=listener;publish();}
    public void setEnabled(boolean enabled){this.enabled=enabled;failure="";if(enabled)connect();else{cancel();removeService();service=null;uid=-1;connecting=false;}publish();}
    public boolean enabled(){return enabled;}
    public JSONObject status(){
        boolean alive=binderAlive(), granted=alive&&permissionGranted();
        IBinder remote=service;boolean connected=remote!=null&&remote.isBinderAlive();
        boolean available=enabled&&!closed&&alive&&granted&&connected&&(uid==2000||uid==0);
        String state=closed||!enabled?"disabled":!installed()?"not_installed":!alive?"not_running":!granted?"permission_required":connecting?"connecting":available?(uid==0?"ready_root":"ready_shell"):"disconnected";
        String reason=available?"":!failure.isEmpty()?failure:state.equals("disabled")?"Shizuku 연동이 꺼져 있습니다.":state.equals("not_installed")?"Shizuku 앱이 설치되어 있지 않습니다.":state.equals("not_running")?"휴대폰에서 Shizuku를 먼저 시작해 주세요.":state.equals("permission_required")?"Shizuku 권한을 허용해 주세요.":"Shizuku 기기 서비스 연결을 확인해 주세요.";
        return PhonePrivilegeProtocol.object("enabled",enabled,"installed",installed(),"binderAlive",alive,"permissionGranted",granted,"serviceConnected",connected,"uid",available?uid:-1,"available",available,"state",state,"reason",reason,"transport","shizuku");
    }
    public void requestPermission(int requestCode){
        if(closed||!enabled){failure="먼저 Shizuku 연동을 켜 주세요.";publish();return;}
        main.post(()->{
            try{
                if(!binderAlive()||Shizuku.isPreV11())throw new IllegalStateException("지원되는 Shizuku 서비스를 휴대폰에서 먼저 시작해 주세요.");
                if(permissionGranted()){connect();return;}
                if(Shizuku.shouldShowRequestPermissionRationale())throw new SecurityException("Shizuku 앱에서 Hermes의 권한을 직접 허용해 주세요.");
                Shizuku.requestPermission(requestCode);
            }catch(Exception error){failure=message(error);publish();}
        });
    }
    public synchronized void connect(){
        if(closed||!enabled||connecting||service!=null)return;
        if(!authorized()){publish();return;}
        connecting=true;failure="";int attempt=++connectionAttempt;publish();
        main.postDelayed(()->{if(connecting&&connectionAttempt==attempt){drop("Shizuku 기기 서비스 연결 시간이 초과되었습니다. 다시 연결해 주세요.");removeService();}},10000);
        main.post(()->{try{if(!closed&&enabled)Shizuku.bindUserService(userServiceArgs,connection);else connecting=false;}catch(Exception error){drop(message(error));}});
    }
    public void setGlobalScope(boolean globalScope){
        this.globalScope=globalScope;
        if(!globalScope)cancel();
    }
    public JSONObject wifi(boolean on)throws Exception{return run(PhonePrivilegeProtocol.WIFI,PhonePrivilegeProtocol.object("enabled",on));}
    public JSONObject processes()throws Exception{return run(PhonePrivilegeProtocol.PROCESSES,new JSONObject());}
    public JSONObject forceStop(String packageName)throws Exception{
        PhonePrivilegePolicy.requireAppControl(globalScope,packageName);
        ApplicationInfo info=context.getPackageManager().getApplicationInfo(packageName,0);
        if((info.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0)throw new SecurityException("시스템 앱은 강제 종료할 수 없습니다.");
        return run(PhonePrivilegeProtocol.FORCE_STOP,PhonePrivilegeProtocol.object("package",packageName));
    }
    public JSONObject readSystemSetting(String name)throws Exception{PhonePrivilegePolicy.validateSetting(name);return run(PhonePrivilegeProtocol.READ_SYSTEM_SETTING,PhonePrivilegeProtocol.object("name",name));}
    public JSONObject writeSystemSetting(String name,int value)throws Exception{PhonePrivilegePolicy.validateSettingValue(name,value);return run(PhonePrivilegeProtocol.WRITE_SYSTEM_SETTING,PhonePrivilegeProtocol.object("name",name,"value",value));}
    private JSONObject run(int operation,JSONObject args)throws Exception{
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("기기 제어는 작업 스레드에서 실행해야 합니다.");
        synchronized(operationLock){
            IBinder remote=requireService();String id=UUID.randomUUID().toString();currentRequest=id;args.put("requestId",id);
            Future<JSONObject> job=calls.submit(()->{
                if(Thread.currentThread().isInterrupted())throw new InterruptedException("기기 작업이 취소되었습니다.");
                if(operation==PhonePrivilegeProtocol.FORCE_STOP){
                    PhonePrivilegePolicy.requireAppControl(globalScope,args.getString("package"));
                    PhonePrivilegeProtocol.call(remote,PhonePrivilegeProtocol.CONFIGURE,PhonePrivilegeProtocol.object("globalScope",globalScope));
                    if(Thread.currentThread().isInterrupted())throw new InterruptedException("기기 작업이 취소되었습니다.");
                }
                return PhonePrivilegeProtocol.call(remote,operation,args);
            });
            currentCall=job;
            try{return job.get(15,TimeUnit.SECONDS);}
            catch(Exception error){cancel();Throwable cause=error.getCause();if(cause instanceof Exception)throw(Exception)cause;throw error;}
            finally{if(currentCall==job)currentCall=null;if(id.equals(currentRequest))currentRequest=null;publish();}
        }
    }
    public void cancel(){
        Future<?> pending=currentCall;if(pending!=null)pending.cancel(true);
        String id=currentRequest;IBinder remote=service;
        if(id!=null&&remote!=null&&!calls.isShutdown())calls.execute(()->{try{PhonePrivilegeProtocol.call(remote,PhonePrivilegeProtocol.CANCEL,PhonePrivilegeProtocol.object("requestId",id));}catch(Exception ignored){}});
    }
    private IBinder requireService(){IBinder remote=service;if(!enabled||closed||!authorized()||remote==null||!remote.isBinderAlive()||(uid!=2000&&uid!=0))throw new SecurityException(status().optString("reason","Shizuku 기기 제어 연결이 필요합니다."));return remote;}
    private boolean installed(){try{context.getPackageManager().getApplicationInfo("moe.shizuku.privileged.api",0);return true;}catch(Exception ignored){return binderAlive();}}
    private boolean binderAlive(){try{return Shizuku.pingBinder();}catch(Exception ignored){return false;}}
    private boolean permissionGranted(){try{return!Shizuku.isPreV11()&&Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;}catch(Exception ignored){return false;}}
    private boolean authorized(){return binderAlive()&&permissionGranted();}
    private void drop(String reason){cancel();service=null;uid=-1;connecting=false;connectionAttempt++;failure=reason;publish();}
    private void publish(){Listener callback=listener;if(callback!=null){JSONObject state=status();main.post(()->{if(listener==callback)callback.onStatusChanged(state);});}}
    private void removeService(){try{Shizuku.unbindUserService(userServiceArgs,connection,true);}catch(Exception ignored){}}
    private static String message(Exception error){String text=error.getMessage();return text==null?error.getClass().getSimpleName():text.substring(0,Math.min(text.length(),500));}
    @Override public void close(){if(closed)return;enabled=false;cancel();removeService();closed=true;service=null;uid=-1;listener=null;Shizuku.removeBinderReceivedListener(received);Shizuku.removeBinderDeadListener(dead);Shizuku.removeRequestPermissionResultListener(permission);calls.shutdownNow();}
}
