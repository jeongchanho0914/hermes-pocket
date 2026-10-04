package dev.chanho.hermes;
import android.app.*;
import android.content.*;
import android.os.*;
import android.content.pm.ServiceInfo;
public final class AgentForegroundService extends Service {
    private final AgentRuntime.Observer observer=(type,data)->{
        if("jobs".equals(type)||"terminal".equals(type)||"status".equals(type)||"started".equals(type)||"settled".equals(type))promote();
    };
    @Override public void onCreate(){
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("agent","진행 중인 에이전트 작업",NotificationManager.IMPORTANCE_LOW));
        AgentRuntime runtime=AgentRuntime.get(this);
        runtime.foregroundServiceCreated(this);
        runtime.addObserver(observer);
        promote();
        AgentOverlay.refresh(this);
    }
    private void promote(){
        PendingIntent open=PendingIntent.getActivity(this,4200,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,4201,new Intent(this,AgentForegroundService.class).setAction("STOP"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        AgentRuntime runtime=AgentRuntime.get(this);
        String summary=runtime.jobs.hasActive()?"독립 작업 "+runtime.jobs.activeCount()+"개 · 알림에서 모두 중단할 수 있습니다.":runtime.terminalHasJobs()?runtime.terminalStatus():"사용자가 시작한 작업을 실행 중입니다. 언제든 중단할 수 있습니다.";
        Notification n=new Notification.Builder(this,"agent").setSmallIcon(R.drawable.ic_launcher).setContentTitle("Hermes Pocket 실행 중")
            .setContentText(summary).setContentIntent(open).setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).addAction(new Notification.Action.Builder(null,"즉시 중단",stop).build()).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(4200,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);else startForeground(4200,n);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        // Confirm every start, including reuse of an existing service instance.
        promote();
        AgentRuntime.get(this).foregroundReady(this);
        AgentOverlay.refresh(this);
        if(intent!=null&&"STOP".equals(intent.getAction())){AgentRuntime.get(this).stopAll();AgentOverlay.dismiss();stopSelf();}
        return START_NOT_STICKY;
    }
    @Override public void onTimeout(int startId,int fgsType){AgentRuntime.get(this).stopAll();AgentOverlay.dismiss();stopSelf();}
    @Override public void onDestroy(){
        AgentRuntime runtime=AgentRuntime.get(this);runtime.removeObserver(observer);
        boolean expected=runtime.foregroundServiceDestroyed(this);
        if(!expected&&runtime.anyWork())runtime.stopAll();
        AgentOverlay.refresh(this);super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
