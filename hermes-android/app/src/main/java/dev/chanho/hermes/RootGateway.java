package dev.chanho.hermes;
import org.json.*;
import java.io.*;
import java.util.concurrent.*;

final class RootGateway {
    private final Store store;
    private final ShizukuPhoneBridge shizuku;
    private volatile boolean verified;
    private volatile String checkState="unchecked",checkMessage="Root 상태를 아직 확인하지 않았습니다.";
    private volatile Process process;
    RootGateway(Store s,ShizukuPhoneBridge shizuku){store=s;this.shizuku=shizuku;}
    boolean verified(){return verified&&store.flag("root_enabled");}
    boolean privilegeReady(){return verified()||shizuku.status().optBoolean("available");}
    JSONObject status(){return J.obj("enabled",store.flag("root_enabled"),"root",verified(),"state",store.flag("root_enabled")?checkState:"disabled","message",store.flag("root_enabled")?checkMessage:"Root 연동이 꺼져 있습니다. Root는 기기의 실제 su 권한이 별도로 필요합니다.");}
    void cancel(){Process p=process;if(p!=null)p.destroyForcibly();}
    void revoke(){verified=false;checkState="unchecked";checkMessage="Root 상태를 아직 확인하지 않았습니다.";cancel();}
    JSONObject probe() throws Exception {
        if(!store.flag("root_enabled"))throw new SecurityException("먼저 설정에서 Root 연동을 직접 켜 주세요.");
        verified=false;String out=runFixed("id -u");verified=out.trim().equals("0");
        if(!verified){checkState="non_root";checkMessage="su 실행 결과가 UID 0이 아닙니다. 실제 Root 권한이 부여되지 않았습니다.";throw new SecurityException(checkMessage);}
        checkState="verified";checkMessage="실제 su 실행에서 UID 0을 확인했습니다.";
        return J.obj("root",true,"uid",0,"message","현재 su 실행에서 UID 0을 확인했습니다. SELinux 및 기기 정책은 여전히 적용됩니다.");
    }
    JSONObject processes() throws Exception {if(!verified())return shizuku.processes();requireRoot();return J.obj("processes",runFixed("ps -A"),"transport","root","uid",0);}
    JSONObject wifi(boolean on) throws Exception {if(!verified())return shizuku.wifi(on);requireRoot();String result=runFixed(on?"svc wifi enable":"svc wifi disable");return J.obj("requested",on,"commandOutput",result,"verified",false,"transport","root","uid",0);}
    JSONObject forceStop(String pkg) throws Exception {
        if(!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")||!store.allowed(pkg))throw new SecurityException("전체 앱 제어가 꺼져 있거나 보호된 패키지입니다.");
        if(!verified()){shizuku.setGlobalScope("all".equals(store.deviceScope()));return shizuku.forceStop(pkg);}
        requireRoot();
        return J.obj("requestedPackage",pkg,"commandOutput",runFixed("am force-stop --user current "+pkg),"verified",false,"transport","root","uid",0);
    }
    private void requireRoot(){if(!verified())throw new SecurityException("Root 확인 버튼으로 실제 승인을 먼저 확인해 주세요.");}
    private String runFixed(String command) throws Exception {
        // Callers choose fixed commands; no generic shell or model-generated command is exposed.
        Process p;
        try{p=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();}
        catch(IOException e){
            verified=false;
            String message=String.valueOf(e.getMessage());
            if(message.contains("error=2")||message.contains("No such file")){checkState="su_missing";checkMessage="이 기기에서 실행 가능한 su를 찾지 못했습니다. 앱의 Root 연동 설정만 켜도 실제 Root 권한이 생기지는 않습니다.";}
            else if(message.contains("error=13")||message.contains("Permission denied")){checkState="su_not_executable";checkMessage="su 실행 권한이 거부되었습니다. 기기의 Root 권한 관리자에서 허용 상태를 확인하세요.";}
            else{checkState="su_start_failed";checkMessage="su 프로세스를 실행하지 못했습니다. 기기의 실제 Root 설치와 실행 권한을 확인하세요.";}
            throw new IOException(checkMessage,e);
        }
        process=p;
        ExecutorService reader=Executors.newSingleThreadExecutor();
        Future<String> output=reader.submit(()->{
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[2048];int n;
            try(InputStream in=p.getInputStream()){while((n=in.read(buf))!=-1){int take=Math.min(n,24000-out.size());if(take>0)out.write(buf,0,take);}}
            return out.toString("UTF-8");
        });
        try{
            if(!p.waitFor(30,TimeUnit.SECONDS)){p.destroyForcibly();verified=false;checkState="timeout";checkMessage="Root 요청 시간이 초과되었습니다. Root 권한 관리자의 승인 상태를 확인하세요.";throw new IOException(checkMessage);}
            String text=output.get(3,TimeUnit.SECONDS);if(p.exitValue()!=0){verified=false;checkState="rejected";checkMessage="su 요청이 거부되었거나 해당 명령을 실행하지 못했습니다. Root 권한 관리자의 승인 상태를 확인하세요.";throw new IOException(checkMessage);}return text;
        }finally{reader.shutdownNow();p.destroy();if(process==p)process=null;}
    }
}
