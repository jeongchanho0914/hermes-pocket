package dev.chanho.hermes;

import android.app.KeyguardManager;
import android.content.*;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;

/** Owner-requested conversation surface. The accessibility service owns its window. */
final class AgentOverlay {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static volatile AgentOverlay instance;
    private static volatile Rect visibleBounds=new Rect();
    private static volatile Set<Integer> windowIds=Collections.emptySet();
    private static final Map<Integer,Long> detachedWindowIds=new HashMap<>();
    private static volatile long phoneActionSettleUntil;
    private final Context context;
    private final AgentRuntime runtime;
    private PhoneAccessibilityService service;
    private WindowManager windows;
    private WindowManager.LayoutParams params;
    private LinearLayout panel;
    private TextView statusView,statusLabel,chip;
    private LinearLayout collapsedControls;
    private EditText input;
    private Button send,stop,chipStop;
    private boolean collapsed=true,editing=false,shown=false,dismissedForRun=false;
    private boolean dark=false;
    private boolean rendered=false,renderedBusy,renderedJobs,renderedCollapsed;
    private String renderedMessage="";
    private String foreground="",session="",status="",draft="";
    private int x=12,y=112;
    private long hiddenForActionUntil;
    private final AgentRuntime.Observer observer=(type,data)->{
        MAIN.post(()->{
            if(type.equals("started")){collapsed=true;dismissedForRun=false;}
            if(type.equals("settled")||type.equals("complete"))collapsed=true;
            update();
        });
    };
    private final BroadcastReceiver screenReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent intent){
            if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction()))detach();else update();
        }
    };
    private AgentOverlay(Context c){
        context=c.getApplicationContext();runtime=AgentRuntime.get(context);
        runtime.addObserver(observer);
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_USER_PRESENT);
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(screenReceiver,filter,Context.RECEIVER_NOT_EXPORTED);else context.registerReceiver(screenReceiver,filter);
    }
    static void refresh(Context context){MAIN.post(()->{if(instance==null)instance=new AgentOverlay(context);instance.update();});}
    static void dismiss(){MAIN.post(()->{if(instance!=null)instance.detach();});}
    static void onForegroundAppChanged(String pkg){
        MAIN.post(()->{if(instance!=null&&pkg!=null&&!pkg.isEmpty()){instance.foreground=pkg;instance.update();}});
    }
    static Rect getVisibleBounds(){return new Rect(visibleBounds);}
    static Set<Integer> getOverlayWindowIds(){return new HashSet<>(windowIds);}
    /** Input-window removal lands on Android's next traversal; gesture workers wait off main. */
    static long phoneActionSettleUntilUptime(){return phoneActionSettleUntil;}
    /** Accessibility can briefly report a window whose touch surface was already removed. */
    static boolean wasOwnWindowRecentlyDetached(int id){
        if(windowIds.contains(id))return false;
        synchronized(detachedWindowIds){
            long now=SystemClock.elapsedRealtime();
            detachedWindowIds.values().removeIf(deadline->deadline<=now);
            Long deadline=detachedWindowIds.get(id);return deadline!=null&&deadline>now;
        }
    }
    static JSONObject state(){
        AgentOverlay a=instance;
        return J.obj("enabled",a!=null&&a.runtime.store.isFloatingEnabled(),"available",PhoneAccessibilityService.current()!=null,
            "visible",a!=null&&a.shown,"collapsed",a!=null&&a.collapsed,"session",a==null?"":a.session,"status",a==null?"":a.status,"terminalRunning",a!=null&&a.runtime.terminalHasJobs(),"theme",a!=null&&a.dark?"black":"white");
    }
    /** Finish moving the touch window before dispatching a device action. Fail closed on timeout. */
    static void prepareForPhoneAction(Rect target){
        if(target==null)return;
        Rect copy=new Rect(target);
        if(Looper.myLooper()==Looper.getMainLooper()){if(instance!=null)instance.avoid(copy);return;}
        FutureTask<Void> action=new FutureTask<>(()->{if(instance!=null)instance.avoid(copy);return null;});
        MAIN.post(action);
        try{action.get(2,TimeUnit.SECONDS);}catch(Exception e){action.cancel(false);throw new IllegalStateException("Hermes 패널 위치를 정리하지 못했습니다. 다시 시도해 주세요.",e);}
    }
    private void update(){
        AgentRuntime.CurrentState state=runtime.currentState();session=state.sessionId;
        status=!state.busy&&runtime.jobs.hasActive()?"독립 작업 "+runtime.jobs.activeCount()+"개":!state.busy&&runtime.terminalHasJobs()?runtime.terminalStatus():state.status;
        PhoneAccessibilityService current=PhoneAccessibilityService.current();
        KeyguardManager keyguard=context.getSystemService(KeyguardManager.class);
        PowerManager power=context.getSystemService(PowerManager.class);
        if(SystemClock.elapsedRealtime()<hiddenForActionUntil){detach();return;}
        if(dismissedForRun||!runtime.store.isFloatingEnabled()||(!state.ownerTask&&!runtime.terminalHasJobs()&&!runtime.jobs.hasActive())||(state.cancelled&&!runtime.jobs.hasActive())||current==null||keyguard.isKeyguardLocked()||!power.isInteractive()) {detach();return;}
        if(foreground.isEmpty())foreground=current.foregroundPackage();
        if(foreground.isEmpty()||foreground.equals(context.getPackageName())||foreground.startsWith("com.android.systemui")||foreground.contains("permissioncontroller")||foreground.contains("packageinstaller")){detach();return;}
        boolean requestedDark="black".equals(runtime.store.get("native_theme","white"));
        if(panel!=null&&dark!=requestedDark&&!detach())return;
        dark=requestedDark;
        if(service!=current){if(!detach())return;service=current;windows=current.getSystemService(WindowManager.class);}
        if(panel==null)build();
        if(!shown){
            try{windows.addView(panel,params);shown=true;panel.post(this::publishBounds);}catch(RuntimeException e){detach();return;}
        }
        render(state.busy);
    }
    private int dp(float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    private int ink(){return dark?0xfff5f5f5:0xff171717;}
    private int muted(){return dark?0xffa8a8a8:0xff757575;}
    private int surface(){return dark?0xff242424:0xfff4f4f4;}
    private int panelColor(){return dark?0xff171717:Color.WHITE;}
    private GradientDrawable background(int color,int radius,boolean stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke)d.setStroke(dp(1),dark?0xff363636:0xffe7e7e7);return d;}
    private TextView text(String value,int size){TextView view=new TextView(service);view.setText(value);view.setTextColor(ink());view.setTextSize(size);view.setIncludeFontPadding(false);return view;}
    private Button button(String label,String description){
        Button b=new Button(service);b.setText(label);b.setTextSize(12);b.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));b.setAllCaps(false);b.setTextColor(ink());b.setContentDescription(description);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setMinWidth(dp(48));b.setMinimumWidth(dp(48));b.setPadding(dp(8),0,dp(8),0);b.setIncludeFontPadding(false);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(dark?0x22ffffff:0x16000000),background(surface(),14,false),null));return b;
    }
    private void addControl(LinearLayout row,Button control){LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(dp(48),dp(48));layout.leftMargin=dp(3);row.addView(control,layout);}
    private void build(){
        rendered=false;
        panel=new LinearLayout(service);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(12),dp(8),dp(12),dp(12));panel.setBackground(background(panelColor(),24,true));panel.setElevation(dp(8));panel.setContentDescription("Hermes 작업 패널");
        panel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        params=new WindowManager.LayoutParams(dp(280),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.LEFT;params.x=dp(x);params.y=dp(y);params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;params.setTitle("Hermes owner task panel");
        panel.setOnTouchListener((view,event)->{if(event.getAction()==MotionEvent.ACTION_OUTSIDE){finishEditing();return true;}return false;});
        LinearLayout gripRow=new LinearLayout(service);gripRow.setGravity(Gravity.CENTER);gripRow.setContentDescription("Hermes 패널 이동");gripRow.setOnTouchListener(new Drag());View grip=new View(service);grip.setBackground(background(dark?0xff555555:0xffcecece,2,false));gripRow.addView(grip,new LinearLayout.LayoutParams(dp(28),dp(3)));panel.addView(gripRow,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(10)));
        LinearLayout header=new LinearLayout(service);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView drag=text("Hermes",15);drag.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));drag.setGravity(Gravity.CENTER_VERTICAL);drag.setPadding(dp(2),0,dp(4),0);drag.setContentDescription("Hermes 패널 이동");drag.setOnTouchListener(new Drag());
        header.addView(drag,new LinearLayout.LayoutParams(0,dp(48),1));
        drag.setOnClickListener(v->{finishEditing();context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));detach();});
        Button fold=button("−","패널 접기");fold.setTextSize(20);fold.setOnClickListener(v->{collapsed=true;finishEditing();render(runtime.currentState().busy);});addControl(header,fold);
        Button close=button("×","이 작업의 Hermes 패널 닫기");close.setTextSize(20);close.setOnClickListener(v->{dismissedForRun=true;detach();});addControl(header,close);
        stop=button("중단","실제 에이전트 작업 즉시 중단");stop.setOnClickListener(v->{runtime.stopAll();detach();});addControl(header,stop);panel.addView(header);
        statusLabel=text("현재 작업",10);statusLabel.setTextColor(muted());statusLabel.setLetterSpacing(.03f);statusLabel.setPadding(dp(2),dp(10),dp(2),dp(6));
        statusView=text("",14);statusView.setMaxLines(1);statusView.setMinHeight(dp(28));statusView.setLineSpacing(dp(3),1f);statusView.setEllipsize(android.text.TextUtils.TruncateAt.END);statusView.setPadding(dp(2),0,dp(2),dp(10));panel.addView(statusView);
        LinearLayout composer=new LinearLayout(service);composer.setGravity(Gravity.CENTER_VERTICAL);
        input=new EditText(service);input.setTextSize(14);input.setSingleLine(true);input.setHint("이 대화에 이어서 요청");input.setContentDescription("이 대화에 이어서 요청");input.setTextColor(ink());input.setHintTextColor(muted());input.setIncludeFontPadding(false);input.setGravity(Gravity.CENTER_VERTICAL);input.setBackground(background(surface(),16,false));input.setPadding(dp(14),0,dp(8),0);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(12000)});input.setFocusableInTouchMode(true);input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);input.setText(draft);
        input.setOnTouchListener((v,event)->{
            if(event.getAction()==MotionEvent.ACTION_DOWN&&params!=null){
                editing=true;params.flags&=~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;applyLayout();
                EditText editor=input;PhoneAccessibilityService owner=service;
                if(editor!=null&&owner!=null){editor.requestFocus();editor.post(()->{if(editing&&service==owner&&editor.isAttachedToWindow())owner.getSystemService(InputMethodManager.class).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT);});}
            }return false;
        });
        input.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEND){submit();return true;}return false;});
        composer.addView(input,new LinearLayout.LayoutParams(0,dp(48),1));send=button("↑","요청 보내기");send.setTextSize(22);send.setTextColor(panelColor());send.setBackground(new RippleDrawable(ColorStateList.valueOf(dark?0x16000000:0x33ffffff),background(ink(),16,false),null));send.setOnClickListener(v->submit());LinearLayout.LayoutParams sendParams=new LinearLayout.LayoutParams(dp(48),dp(48));sendParams.leftMargin=dp(8);composer.addView(send,sendParams);panel.addView(composer);
        collapsedControls=new LinearLayout(service);collapsedControls.setGravity(Gravity.CENTER_VERTICAL);
        chip=text("",12);chip.setGravity(Gravity.CENTER_VERTICAL);chip.setPadding(dp(2),dp(3),dp(8),dp(3));chip.setMaxLines(1);chip.setEllipsize(android.text.TextUtils.TruncateAt.END);chip.setContentDescription("Hermes 패널 펼치기 또는 길게 눌러 이동");chip.setOnClickListener(v->{collapsed=false;render(runtime.currentState().busy);});chip.setOnTouchListener(new Drag());collapsedControls.addView(chip,new LinearLayout.LayoutParams(0,dp(48),1));
        chipStop=button("중단","실행 중인 Hermes 작업 모두 중단");chipStop.setOnClickListener(v->{runtime.stopAll();detach();});collapsedControls.addView(chipStop);panel.addView(collapsedControls);
        panel.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->publishBounds());
    }
    private void render(boolean busy){
        if(panel==null)return;
        boolean jobs=runtime.terminalHasJobs()||runtime.jobs.hasActive();
        String message=status.isEmpty()?(busy?"작업 진행 중":"다음 요청을 입력하세요"):status;
        if(rendered&&renderedBusy==busy&&renderedJobs==jobs&&renderedCollapsed==collapsed&&renderedMessage.equals(message))return;
        rendered=true;renderedBusy=busy;renderedJobs=jobs;renderedCollapsed=collapsed;renderedMessage=message;
        statusLabel.setText(busy||jobs?"현재 작업":"대화 이어가기");statusView.setText(message);chip.setText("Hermes · "+message+"  ›");
        for(int i=0;i<panel.getChildCount()-1;i++)panel.getChildAt(i).setVisibility(collapsed?View.GONE:View.VISIBLE);
        collapsedControls.setVisibility(collapsed?View.VISIBLE:View.GONE);stop.setVisibility(busy||jobs?View.VISIBLE:View.GONE);chipStop.setVisibility(busy||jobs?View.VISIBLE:View.GONE);send.setEnabled(!busy);send.setAlpha(busy?.42f:1f);input.setEnabled(!busy);input.setAlpha(busy?.65f:1f);
        params.width=dp(collapsed?256:280);applyLayout();if(panel!=null)panel.post(this::publishBounds);
    }
    private void submit(){
        String request=input.getText().toString().trim();if(request.isEmpty())return;
        try{runtime.start(request,session);input.setText("");draft="";finishEditing();collapsed=false;update();}
        catch(Exception e){statusView.setText(J.error(e));Toast.makeText(service,J.error(e),Toast.LENGTH_SHORT).show();}
    }
    private void finishEditing(){
        editing=false;if(input!=null){input.clearFocus();if(service!=null)service.getSystemService(InputMethodManager.class).hideSoftInputFromWindow(input.getWindowToken(),0);}
        if(params!=null){params.flags|=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;applyLayout();}
    }
    private void applyLayout(){if(shown&&windows!=null)try{windows.updateViewLayout(panel,params);}catch(RuntimeException e){detach();}}
    private void publishBounds(){
        if(!shown||panel==null){visibleBounds=new Rect();windowIds=Collections.emptySet();return;}
        int[] location=new int[2];panel.getLocationOnScreen(location);visibleBounds=new Rect(location[0],location[1],location[0]+panel.getWidth(),location[1]+panel.getHeight());
        android.view.accessibility.AccessibilityNodeInfo identity=panel.createAccessibilityNodeInfo();
        int id;try{id=identity.getWindowId();}finally{identity.recycle();}
        windowIds=id<0?Collections.emptySet():Collections.singleton(id);
    }
    private void avoid(Rect target){
        if(!shown||!Rect.intersects(getVisibleBounds(),target))return;
        finishEditing();collapsed=true;render(runtime.currentState().busy);
        if(!shown||service==null)return;
        android.util.DisplayMetrics display=service.getResources().getDisplayMetrics();int width=dp(256),height=dp(68),margin=dp(8);
        Rect[] corners={new Rect(margin,margin,margin+width,margin+height),new Rect(display.widthPixels-width-margin,margin,display.widthPixels-margin,margin+height),new Rect(margin,display.heightPixels-height-margin,margin+width,display.heightPixels-margin),new Rect(display.widthPixels-width-margin,display.heightPixels-height-margin,display.widthPixels-margin,display.heightPixels-margin)};
        for(Rect candidate:corners)if(!Rect.intersects(candidate,target)){
            x=Math.round(candidate.left/display.density);y=Math.round(candidate.top/display.density);break;
        }
        // Window layout is asynchronous. Remove it during dispatch rather than treating a
        // requested location as proof of where its touch surface already is.
        hiddenForActionUntil=SystemClock.elapsedRealtime()+1500;
        if(!detach())throw new IllegalStateException("Hermes 패널 창이 아직 닫히지 않았습니다. 다시 시도해 주세요.");
        MAIN.postDelayed(this::update,1500);
    }
    private boolean detach(){
        if(panel!=null&&windows!=null&&shown){
            if(input!=null)draft=input.getText().toString();
            editing=false;if(input!=null){input.clearFocus();if(service!=null)service.getSystemService(InputMethodManager.class).hideSoftInputFromWindow(input.getWindowToken(),0);}
            Set<Integer> removedIds=getOverlayWindowIds();
            android.view.accessibility.AccessibilityNodeInfo identity=panel.createAccessibilityNodeInfo();
            try{if(identity.getWindowId()>=0)removedIds.add(identity.getWindowId());}finally{identity.recycle();}
            try{windows.removeViewImmediate(panel);}catch(RuntimeException failure){return false;}
            if(panel.isAttachedToWindow())return false;
            phoneActionSettleUntil=SystemClock.uptimeMillis()+150;
            synchronized(detachedWindowIds){
                long now=SystemClock.elapsedRealtime();detachedWindowIds.values().removeIf(deadline->deadline<=now);
                for(Integer id:removedIds)detachedWindowIds.put(id,now+2000);
            }
        }
        shown=false;panel=null;input=null;service=null;windows=null;params=null;visibleBounds=new Rect();windowIds=Collections.emptySet();
        return true;
    }
    private final class Drag implements View.OnTouchListener {
        float startX,startY;int originalX,originalY;boolean moved;
        public boolean onTouch(View view,MotionEvent event){
            if(params==null)return false;
            if(event.getAction()==MotionEvent.ACTION_DOWN){startX=event.getRawX();startY=event.getRawY();originalX=params.x;originalY=params.y;moved=false;return true;}
            if(event.getAction()==MotionEvent.ACTION_MOVE){float dx=event.getRawX()-startX,dy=event.getRawY()-startY;if(Math.abs(dx)+Math.abs(dy)>dp(6))moved=true;android.util.DisplayMetrics d=service.getResources().getDisplayMetrics();params.x=Math.max(0,Math.min(d.widthPixels-panel.getWidth(),originalX+(int)dx));params.y=Math.max(dp(24),Math.min(d.heightPixels-panel.getHeight()-dp(24),originalY+(int)dy));x=Math.round(params.x/d.density);y=Math.round(params.y/d.density);applyLayout();publishBounds();return true;}
            if(event.getAction()==MotionEvent.ACTION_UP){if(!moved)view.performClick();return true;}return true;
        }
    }
}
