package dev.chanho.hermes;

import android.content.Context;
import android.os.Binder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Runs in Shizuku's shell/root UserService, never in the UI process. */
public final class PrivilegedPhoneService extends Binder {
    private final int ownerUid;
    private final String ownerPackage;
    private volatile boolean globalScope;
    private final ConcurrentHashMap<String,java.lang.Process> running=new ConcurrentHashMap<>();
    private final Set<String> cancelled=Collections.newSetFromMap(new ConcurrentHashMap<String,Boolean>());
    private volatile boolean destroyed;
    public PrivilegedPhoneService(Context context) {
        ownerUid=context.getApplicationInfo().uid;
        ownerPackage=context.getPackageName();
        attachInterface(null,PhonePrivilegeProtocol.DESCRIPTOR);
    }
    /** An old manager without a valid app context cannot authorize mutation. */
    public PrivilegedPhoneService() {
        ownerUid=-1; ownerPackage="";
        attachInterface(null,PhonePrivilegeProtocol.DESCRIPTOR);
    }
    @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags) throws RemoteException {
        if(code==INTERFACE_TRANSACTION){reply.writeString(PhonePrivilegeProtocol.DESCRIPTOR);return true;}
        // Shizuku removes UserServices using this reserved transaction, without our JSON payload.
        if(code==PhonePrivilegeProtocol.DESTROY){
            int caller=Binder.getCallingUid();
            if(caller!=ownerUid&&caller!=Process.myUid())throw new SecurityException("서비스 종료 권한이 없습니다.");
            destroyed=true;for(java.lang.Process p:running.values())p.destroyForcibly();
            if(reply!=null){reply.writeNoException();reply.writeString(PhonePrivilegeProtocol.object("ok",true,"stopped",true).toString());}
            new Thread(()->{try{Thread.sleep(50);}catch(InterruptedException ignored){}System.exit(0);},"HermesShizukuStop").start();return true;
        }
        if(!supported(code))return super.onTransact(code,data,reply,flags);
        try {
            data.enforceInterface(PhonePrivilegeProtocol.DESCRIPTOR);
            int caller=Binder.getCallingUid();
            if(ownerUid<0 || caller!=ownerUid)throw new SecurityException("기기 제어 요청자의 앱 UID를 확인하지 못했습니다.");
            if(destroyed)throw new SecurityException("기기 제어 서비스가 종료되었습니다.");
            JSONObject args=new JSONObject(data.readString());
            if(args.toString().length()>8192)throw new IllegalArgumentException("기기 제어 인자가 너무 큽니다.");
            JSONObject result;
            try{result=dispatch(code,args);}finally{if(code!=PhonePrivilegeProtocol.CANCEL)cancelled.remove(args.optString("requestId"));}
            reply.writeNoException();reply.writeString(result.toString());
        } catch(Exception error) {
            reply.writeNoException();reply.writeString(PhonePrivilegeProtocol.object("ok",false,"error",error.getMessage()==null?error.getClass().getSimpleName():error.getMessage()).toString());
        }
        return true;
    }
    private static boolean supported(int code){return(code>=PhonePrivilegeProtocol.STATUS&&code<=PhonePrivilegeProtocol.WRITE_SYSTEM_SETTING)||code==PhonePrivilegeProtocol.DESTROY;}
    private JSONObject dispatch(int code,JSONObject args)throws Exception {
        int uid=Process.myUid();
        if(code==PhonePrivilegeProtocol.STATUS)return PhonePrivilegeProtocol.object("ok",true,"uid",uid,"ownerUid",ownerUid,"ownerPackage",ownerPackage,"transport","shizuku","operations",new JSONArray(new String[]{"wifi","processes","force_stop","read_system_setting","write_system_setting"}));
        if(uid!=2000&&uid!=0)throw new SecurityException("Shizuku 서비스가 shell 또는 root 권한으로 실행되지 않습니다.");
        if(code==PhonePrivilegeProtocol.CONFIGURE){
            if(!(args.get("globalScope") instanceof Boolean))throw new IllegalArgumentException("앱 제어 범위가 올바르지 않습니다.");
            globalScope=args.getBoolean("globalScope");
            return PhonePrivilegeProtocol.object("ok",true,"deviceScope",globalScope?"all":"none");
        }
        if(code==PhonePrivilegeProtocol.CANCEL){String id=requestId(args);cancelled.add(id);java.lang.Process p=running.get(id);if(p!=null)p.destroyForcibly();return PhonePrivilegeProtocol.object("ok",true,"cancelled",true);}
        String id=requestId(args);
        if(code==PhonePrivilegeProtocol.WIFI){boolean enabled=args.getBoolean("enabled");return command(id,new String[]{"/system/bin/svc","wifi",enabled?"enable":"disable"},"requested",enabled);}
        if(code==PhonePrivilegeProtocol.PROCESSES)return command(id,new String[]{"/system/bin/ps","-A"},"operation","processes");
        if(code==PhonePrivilegeProtocol.FORCE_STOP){
            String pkg=args.getString("package");PhonePrivilegePolicy.requireAppControl(globalScope,pkg);
            String listed=command(id,new String[]{"/system/bin/cmd","package","list","packages","-s",pkg},"operation","package_policy").optString("commandOutput");
            for(String line:listed.split("\\r?\\n"))if(line.trim().equals("package:"+pkg))throw new SecurityException("시스템 앱은 강제 종료 대상에서 제외됩니다.");
            PhonePrivilegePolicy.requireAppControl(globalScope,pkg);
            return command(id,new String[]{"/system/bin/am","force-stop","--user","current",pkg},"requestedPackage",pkg);
        }
        String name=args.getString("name");PhonePrivilegePolicy.validateSetting(name);
        Long requested=null;
        if(code==PhonePrivilegeProtocol.WRITE_SYSTEM_SETTING){
            Object number=args.get("value");if(!(number instanceof Integer)&&!(number instanceof Long))throw new IllegalArgumentException("시스템 설정 값은 정수여야 합니다.");
            requested=((Number)number).longValue();PhonePrivilegePolicy.validateSettingValue(name,requested);
            command(id,new String[]{"/system/bin/settings","--user","current","put","system",name,Long.toString(requested)},"requested",requested);
        }
        JSONObject result=command(id,new String[]{"/system/bin/settings","--user","current","get","system",name},"name",name);
        String actual=result.optString("commandOutput").trim();result.put("value",actual);
        if(requested!=null){result.put("requested",requested);result.put("verified",Long.toString(requested).equals(actual));}
        return result;
    }
    private JSONObject command(String id,String[] argv,String resultName,Object resultValue)throws Exception {
        if(cancelled.contains(id)||destroyed)throw new InterruptedException("기기 작업이 취소되었습니다.");
        java.lang.Process process=new ProcessBuilder(argv).redirectErrorStream(true).start();
        running.put(id,process);
        if(cancelled.contains(id)||destroyed){process.destroyForcibly();running.remove(id);throw new InterruptedException("기기 작업이 취소되었습니다.");}
        ExecutorService reader=Executors.newSingleThreadExecutor();
        Future<String> output=reader.submit(()->{ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[2048];try(InputStream input=process.getInputStream()){int n;while((n=input.read(buffer))!=-1){int take=Math.min(n,24000-bytes.size());if(take>0)bytes.write(buffer,0,take);}}return bytes.toString("UTF-8");});
        try {
            if(!process.waitFor(10,TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("기기 제어 요청 시간이 초과되었습니다.");}
            String text=output.get(2,TimeUnit.SECONDS);
            if(cancelled.contains(id)||destroyed)throw new InterruptedException("기기 작업이 취소되었습니다.");
            if(process.exitValue()!=0)throw new SecurityException("기기 정책이 요청을 거절했습니다: "+text.substring(0,Math.min(text.length(),300)));
            return PhonePrivilegeProtocol.object("ok",true,resultName,resultValue,"commandOutput",text,"verified",false,"transport","shizuku","uid",Process.myUid());
        } finally {reader.shutdownNow();process.destroy();running.remove(id,process);}
    }
    private static String requestId(JSONObject args)throws Exception{String id=args.getString("requestId");if(!id.matches("[A-Za-z0-9-]{1,64}"))throw new IllegalArgumentException("잘못된 기기 요청 ID입니다.");return id;}
}
