package dev.chanho.hermes;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.UUID;
import java.util.concurrent.*;

final class ApprovalGate {
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile Pending current;
    private volatile AlertDialog visible;
    private static final int ID=4202;
    private static final class Pending {
        final String nonce=UUID.randomUUID().toString();
        final boolean preserveTarget;
        final String targetPackage;
        Pending(boolean preserve,String target){preserveTarget=preserve;targetPackage=target;}
        final CountDownLatch ready=new CountDownLatch(1);
        volatile boolean approved=false;
    }
    ApprovalGate(Context c){context=c;NotificationManager n=c.getSystemService(NotificationManager.class);n.createNotificationChannel(new NotificationChannel("approval","실행 승인",NotificationManager.IMPORTANCE_HIGH));}
    private boolean locked(){KeyguardManager manager=context.getSystemService(KeyguardManager.class);return manager!=null&&manager.isDeviceLocked();}
    boolean ask(String title,String detail,boolean preserveTarget) throws Exception {
        AgentRuntime runtime=AgentRuntime.get(context);String mode=runtime.store.approvalMode();
        OwnerApprovalPolicy.Decision decision=OwnerApprovalPolicy.evaluate(mode,runtime.busy(),runtime.cancelled(),locked());
        if(decision==OwnerApprovalPolicy.Decision.DENY){if(runtime.cancelled())throw new InterruptedException("사용자가 중단했습니다.");throw new SecurityException("휴대폰 잠금을 먼저 직접 해제해 주세요.");}
        if(decision==OwnerApprovalPolicy.Decision.AUTO){
            runtime.store.audit("automatic_approval","approved","현재 요청에 사용자가 선택한 자동 승인 적용 · 상세 내용은 기록하지 않음");
            return OwnerApprovalPolicy.stillValid(mode,runtime.store.approvalMode(),runtime.busy(),runtime.cancelled(),locked());
        }
        String target="";
        if(preserveTarget){
            target=MainThreadCall.call(task->main.post(task),runtime::cancelled,()->{
                PhoneAccessibilityService service=PhoneAccessibilityService.current();
                if(service==null)throw new SecurityException("접근성 서비스를 먼저 직접 켜 주세요.");
                return service.requireForegroundApp();
            },10000);
        }
        Pending p=new Pending(preserveTarget,target);synchronized(this){if(current!=null)throw new IllegalStateException("다른 작업의 승인을 먼저 처리해 주세요.");current=p;}
        main.post(()->{
            if(current!=p)return;
            MainActivity a=MainActivity.current();
            if(a!=null&&a.resumed&&!preserveTarget){
                AlertDialog dialog=new AlertDialog.Builder(a).setTitle(title).setMessage(detail+"\n\n이번 작업 한 번에만 적용됩니다.")
                    .setNegativeButton("거부",(d,w)->resolve(p.nonce,false)).setPositiveButton("이번만 허용",(d,w)->resolve(p.nonce,true))
                    .setOnCancelListener(d->resolve(p.nonce,false)).create();
                visible=dialog;dialog.show();
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setFilterTouchesWhenObscured(true);
            }else{
                NotificationManager n=context.getSystemService(NotificationManager.class);
                if(!n.areNotificationsEnabled()){resolve(p.nonce,false);AgentRuntime.get(context).emit("notice",J.obj("message","실행 승인을 위해 앱의 알림 권한을 켜 주세요."));return;}
                Intent yes=new Intent(context,ApprovalReceiver.class).setAction("dev.chanho.hermes.APPROVE").putExtra("nonce",p.nonce).putExtra("allow",true);
                Intent no=new Intent(context,ApprovalReceiver.class).setAction("dev.chanho.hermes.DENY").putExtra("nonce",p.nonce).putExtra("allow",false);
                int flags=PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE;
                PendingIntent approve=PendingIntent.getBroadcast(context,4001,yes,flags),deny=PendingIntent.getBroadcast(context,4002,no,flags);
                Notification notification=new Notification.Builder(context,"approval").setSmallIcon(R.drawable.ic_launcher).setContentTitle(title)
                    .setContentText(detail).setStyle(new Notification.BigTextStyle().bigText(detail))
                    .setVisibility(Notification.VISIBILITY_PRIVATE).setTimeoutAfter(120000).setAutoCancel(true)
                    .addAction(new Notification.Action.Builder(null,"거부",deny).build())
                    .addAction(new Notification.Action.Builder(null,"이번만 허용",approve).build()).build();
                n.notify(ID,notification);
                AgentRuntime.get(context).emit("notice",J.obj("message",preserveTarget?"대상 앱을 화면에 열어둔 뒤 Hermes 알림에서 실행을 허용해 주세요.":"Hermes 알림에서 실행을 승인해 주세요."));
            }
        });
        try{if(!p.ready.await(120,TimeUnit.SECONDS))return false;return p.approved&&OwnerApprovalPolicy.stillValid(mode,runtime.store.approvalMode(),runtime.busy(),runtime.cancelled(),locked());}
        finally{synchronized(this){if(current==p)current=null;}context.getSystemService(NotificationManager.class).cancel(ID);main.post(()->{AlertDialog d=visible;if(d!=null){d.dismiss();visible=null;}});}
    }
    synchronized void resolve(String nonce,boolean allow){Pending p=current;if(p!=null&&p.nonce.equals(nonce)){p.approved=allow;context.getSystemService(NotificationManager.class).cancel(ID);if(allow&&p.preserveTarget){main.post(()->{
        PhoneAccessibilityService service=PhoneAccessibilityService.current();
        if(service!=null&&Build.VERSION.SDK_INT>=31)service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
        waitForTarget(p,SystemClock.elapsedRealtime()+5000);
    });}else p.ready.countDown();}}
    private void waitForTarget(Pending p,long deadline){
        if(current!=p)return;
        AgentRuntime runtime=AgentRuntime.get(context);PhoneAccessibilityService service=PhoneAccessibilityService.current();
        String foreground=service==null?"":service.foregroundPackage();
        if(!runtime.cancelled()&&!locked()&&service!=null&&p.targetPackage.equals(foreground)&&runtime.store.allowed(foreground)&&!PhoneAccessibilityService.protectedPackage(foreground)){p.ready.countDown();return;}
        boolean changed=!foreground.isEmpty()&&!PhoneAccessibilityService.protectedPackage(foreground)&&!p.targetPackage.equals(foreground);
        if(runtime.cancelled()||locked()||service==null||changed||SystemClock.elapsedRealtime()>=deadline){
            p.approved=false;p.ready.countDown();
            runtime.emit("notice",J.obj("message",changed?"승인 중 대상 앱이 변경되어 작업을 취소했습니다. 대상 앱을 다시 열고 요청하세요.":"대상 앱 화면으로 돌아오지 못해 작업을 취소했습니다. 알림창을 닫고 대상 앱에서 다시 요청하세요."));return;
        }
        main.postDelayed(()->waitForTarget(p,deadline),100);
    }
    synchronized void cancel(){Pending p=current;if(p!=null){p.approved=false;p.ready.countDown();}context.getSystemService(NotificationManager.class).cancel(ID);}
}
