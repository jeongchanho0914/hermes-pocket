package dev.chanho.hermes;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.webkit.*;
import android.view.*;
import org.json.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static WeakReference<MainActivity> reference=new WeakReference<>(null);
    static MainActivity current(){return reference.get();}
    private volatile WebView web;
    private volatile boolean closing;
    private android.widget.FrameLayout shell;
    private AgentRuntime runtime;
    private BuiltinSkillLibrary builtinSkills;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newFixedThreadPool(2);
    volatile boolean resumed;
    private static final String ORIGIN="https://appassets.androidplatform.net";
    private static final int FILE_TREE_REQUEST=81;
    private String pendingFileRequest="";
    private static final int SKILL_IMPORT_REQUEST=82,SKILL_EXPORT_REQUEST=83;
    private String pendingSkillRequest="",pendingSkillName="";
    private boolean pendingSkillReplace;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);reference=new WeakReference<>(this);runtime=AgentRuntime.get(this);builtinSkills=new BuiltinSkillLibrary(getAssets());
        if(saved!=null){pendingFileRequest=saved.getString("pending_file_request","");pendingSkillRequest=saved.getString("pending_skill_request","");pendingSkillName=saved.getString("pending_skill_name","");pendingSkillReplace=saved.getBoolean("pending_skill_replace",false);}
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS|WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        if(Build.VERSION.SDK_INT>=29){getWindow().setStatusBarContrastEnforced(false);getWindow().setNavigationBarContrastEnforced(false);}
        shell=new android.widget.FrameLayout(this);shell.setBackgroundColor(0xffffffff);
        setContentView(shell);
        if(Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            shell.setOnApplyWindowInsetsListener((view,insets)->{
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                android.graphics.Insets keyboard=insets.getInsets(WindowInsets.Type.ime());
                view.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,keyboard.bottom));
                return insets;
            });shell.post(()->shell.requestApplyInsets());
        }
        createWebView();
    }
    private void createWebView(){
        if(closing||isDestroyed()||isFinishing())return;
        web=new WebView(this);web.setBackgroundColor(0xffffffff);
        shell.addView(web,new android.widget.FrameLayout.LayoutParams(-1,-1));
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);s.setAllowUniversalAccessFromFileURLs(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportMultipleWindows(false);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setSaveFormData(false);s.setMediaPlaybackRequiresUserGesture(true);
        WebView.setWebContentsDebuggingEnabled(false);CookieManager.getInstance().setAcceptCookie(false);
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                Uri u=request.getUrl();String path=u.getPath();
                if(!ORIGIN.equals(u.getScheme()+"://"+u.getHost())||path==null)return denied();
                String asset=path.equals("/")?"index.html":path.substring(1);
                if(!Arrays.asList("index.html","app.css","app.js","cli.js","logo.svg").contains(asset))return denied();
                try{String mime=asset.endsWith("css")?"text/css":asset.endsWith("js")?"application/javascript":asset.endsWith("svg")?"image/svg+xml":"text/html";
                    Map<String,String> h=new HashMap<>();h.put("Cache-Control","no-store");h.put("X-Content-Type-Options","nosniff");
                    return new WebResourceResponse(mime,"UTF-8",200,"OK",h,getAssets().open(asset));
                }catch(IOException e){return denied();}
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest r){return true;}
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){
                // A renderer belongs to the view, not the phone-local agent runtime.
                // Never reuse the unusable view or stop an independently running task.
                boolean currentView=web==view;
                if(currentView)web=null;
                releaseWebView(view);
                if(currentView&&!closing&&!isDestroyed()&&!isFinishing())createWebView();
                return true;
            }
            private WebResourceResponse denied(){return new WebResourceResponse("text/plain","UTF-8",403,"Forbidden",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
        });
        web.addJavascriptInterface(new Bridge(web),"NativeBridge");web.loadUrl(ORIGIN+"/index.html");
    }
    @Override public void onResume(){super.onResume();resumed=true;reference=new WeakReference<>(this);if(web!=null)event("device",runtime.tools.state());try{io.submit(()->event("files",runtime.phoneFiles.status()));}catch(RejectedExecutionException ignored){}}
    @Override public void onSaveInstanceState(Bundle state){state.putString("pending_file_request",pendingFileRequest);state.putString("pending_skill_request",pendingSkillRequest);state.putString("pending_skill_name",pendingSkillName);state.putBoolean("pending_skill_replace",pendingSkillReplace);super.onSaveInstanceState(state);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent result){
        super.onActivityResult(requestCode,resultCode,result);if(requestCode==SKILL_IMPORT_REQUEST||requestCode==SKILL_EXPORT_REQUEST){finishSkillPicker(requestCode,resultCode,result);return;}if(requestCode!=FILE_TREE_REQUEST)return;
        String id=pendingFileRequest;pendingFileRequest="";
        if(resultCode!=RESULT_OK||result==null||result.getData()==null){JSONObject status=runtime.phoneFiles.status();try{status.put("cancelled",true);}catch(JSONException ignored){}if(!id.isEmpty())reply(id,true,status);event("files",status);return;}
        final Uri chosen=result.getData();final int flags=result.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try{io.submit(()->{
            try{
                if(runtime.anyWork())throw new IllegalStateException("작업이 끝난 뒤 폴더를 다시 선택해 주세요.");
                PhoneFiles.checkedTree(chosen.toString());if((flags&Intent.FLAG_GRANT_READ_URI_PERMISSION)==0)throw new SecurityException("선택한 폴더의 읽기 권한을 받지 못했습니다.");
                String old=runtime.store.fileTreeUri();getContentResolver().takePersistableUriPermission(chosen,flags);
                runtime.store.setFileTreeUri(chosen.toString());
                if(!old.isEmpty()&&!old.equals(chosen.toString()))try{Uri previous=PhoneFiles.checkedTree(old);for(UriPermission permission:getContentResolver().getPersistedUriPermissions())if(previous.equals(permission.getUri())){getContentResolver().releasePersistableUriPermission(previous,(permission.isReadPermission()?Intent.FLAG_GRANT_READ_URI_PERMISSION:0)|(permission.isWritePermission()?Intent.FLAG_GRANT_WRITE_URI_PERMISSION:0));break;}}catch(Exception ignored){}
                JSONArray plugins=runtime.store.enabledPlugins();boolean found=false;for(int i=0;i<plugins.length();i++)if("files".equals(plugins.optString(i)))found=true;if(!found)plugins.put("files");
                JSONObject config=runtime.store.saveCapabilities(J.obj("enabledPlugins",plugins));JSONObject status=runtime.phoneFiles.status();status.put("config",config);
                if(!id.isEmpty())reply(id,true,status);event("files",status);
            }catch(Exception e){if(!id.isEmpty())reply(id,false,J.obj("message",J.error(e)));event("files",runtime.phoneFiles.status());}
        });}catch(RejectedExecutionException ignored){}
    }
    @Override public void onPause(){resumed=false;super.onPause();}
    private void releaseWebView(WebView view){
        if(view==null)return;
        view.removeJavascriptInterface("NativeBridge");
        if(view.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)view.getParent()).removeView(view);
        view.destroy();
    }
    private boolean activeView(WebView owner){return !closing&&owner!=null&&web==owner&&!isDestroyed()&&!isFinishing();}
    private void postUi(WebView owner,Runnable action){main.post(()->{if(activeView(owner))action.run();});}
    @Override public void onDestroy(){closing=true;resumed=false;if(current()==this)reference.clear();WebView old=web;web=null;releaseWebView(old);io.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){if(web!=null)web.evaluateJavascript("window.PocketBack && window.PocketBack()",null);}
    void event(String type,JSONObject data){main.post(()->{if(!closing&&web!=null&&!isDestroyed()&&!isFinishing())web.evaluateJavascript("window.PocketNative && window.PocketNative("+J.obj("event",type,"data",data).toString()+")",null);});}
    void reply(String id,boolean ok,Object result){main.post(()->{if(!closing&&web!=null&&!isDestroyed()&&!isFinishing())web.evaluateJavascript("window.PocketNative && window.PocketNative("+J.obj("id",id,"ok",ok,"data",result).toString()+")",null);});}
    static void replyStatic(String id,boolean ok,Object result){MainActivity a=current();if(a!=null)a.reply(id,ok,result);}
    private final class Bridge {
        private final WebView owner;
        Bridge(WebView owner){this.owner=owner;}
        @JavascriptInterface public void postMessage(String input){
            if(!activeView(owner)||input==null||input.length()>180000)return;
            final JSONObject p;try{p=new JSONObject(input);}catch(Exception e){return;}
            String id=p.optString("id");if(!id.matches("[A-Za-z0-9_-]{1,80}"))return;
            String method=p.optString("method");JSONObject data=p.optJSONObject("data");if(data==null)data=new JSONObject();final JSONObject args=data;
            if(method.equals("background")){
                postUi(owner,()->{boolean moved=moveTaskToBack(true);reply(id,true,J.obj("backgrounded",moved,"busy",runtime.busy(),"retained",runtime.anyWork()));});return;
            }
            if(method.equals("stop")){runtime.stopAll();reply(id,true,J.obj("stopped",true));return;}
            if(method.equals("appearance")){
                String color=args.optString("background");boolean light=args.optBoolean("light",true);
                if(!(args.opt("light") instanceof Boolean)){reply(id,false,J.obj("message","화면 모드는 밝음 또는 어두움이어야 합니다."));return;}
                if(!color.matches("#[0-9a-fA-F]{6}")){reply(id,false,J.obj("message","잘못된 화면 색상입니다."));return;}
                postUi(owner,()->{
                    int value=android.graphics.Color.parseColor(color);web.setBackgroundColor(value);shell.setBackgroundColor(value);
                    getWindow().getDecorView().setBackgroundColor(value);
                    if(Build.VERSION.SDK_INT>=30){
                        WindowInsetsController controller=getWindow().getInsetsController();
                        int mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                        if(controller!=null)controller.setSystemBarsAppearance(light?mask:0,mask);
                    }else{
                        getWindow().setStatusBarColor(value);getWindow().setNavigationBarColor(value);
                        int flags=getWindow().getDecorView().getSystemUiVisibility();
                        int mask=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                        getWindow().getDecorView().setSystemUiVisibility(light?flags|mask:flags&~mask);
                    }
                    runtime.store.put("native_theme",light?"white":"black");AgentOverlay.refresh(MainActivity.this);
                    reply(id,true,J.obj("applied",true));
                });return;
            }
            if(method.equals("hideKeyboard")){
                postUi(owner,()->{
                    android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                    if(keyboard!=null)keyboard.hideSoftInputFromWindow(owner.getWindowToken(),0);
                    owner.clearFocus();reply(id,true,J.obj("hidden",true));
                });return;
            }
            if(method.equals("skillsLibraryInstall")){chooseBuiltinSkill(owner,id,args);return;}
            if(method.equals("skillsImport")){postUi(owner,()->chooseSkillPackage(id,args,false));return;}
            if(method.equals("skillsExport")){postUi(owner,()->chooseSkillPackage(id,args,true));return;}
            if(method.equals("allowedApps")){postUi(owner,()->chooseApps(id));return;}
            if(method.equals("chooseFilesFolder")){postUi(owner,()->chooseFilesFolder(id));return;}
            if(method.equals("permission")){postUi(owner,()->permission(id,args.optString("kind")));return;}
            if(method.equals("rootToggle")){postUi(owner,()->rootToggle(id,args.optBoolean("enabled")));return;}
            if(method.equals("deleteSession")){postUi(owner,()->deleteSession(id,args.optString("session"),Boolean.TRUE.equals(args.opt("gesture"))));return;}
            if(method.equals("quit")){postUi(owner,()->moveTaskToBack(true));reply(id,true,J.obj());return;}
            try{io.submit(()->{
                try{
                    if(!activeView(owner))return;
                    Object result;
                    switch(method){
                        case "boot":result=runtime.snapshot();break;
                        case "shizukuStatus":result=runtime.shizuku.status();break;
                        case "setShizuku":result=runtime.setShizuku(args.getBoolean("enabled"));break;
                        case "requestShizukuPermission":{
                            if(runtime.anyWork())throw new IllegalStateException("작업이 끝난 뒤 권한을 요청해 주세요.");
                            runtime.shizuku.requestPermission(7300);result=J.obj("requested",true,"shizuku",runtime.shizuku.status());break;
                        }
                        case "providerThoughts":result=runtime.store.providerThoughts(args.getString("session"));break;
                        case "activities":result=runtime.store.activities(args.getString("session"));break;
                        case "messages":result=runtime.store.messages(args.optString("session"));break;
                        case "sessions":result=runtime.store.sessions();break;
                        case "audit":result=runtime.store.audit();break;
                        case "recordBrowserProblem":{
                            String code=args.getString("code"),source=args.getString("source");
                            if(!("webview_error".equals(code)||"unhandled_rejection".equals(code))||!("app.js".equals(source)||"index.html".equals(source)))throw new IllegalArgumentException("화면 오류 기록 형식이 올바르지 않습니다.");
                            int line=browserPosition(args,"line"),column=browserPosition(args,"column");
                            Diagnostics.recordBrowserProblem(MainActivity.this,code,source,line,column);result=J.obj("recorded",true);break;
                        }
                        case "diagnostics":requireDeveloperDiagnostics();result=Diagnostics.snapshot(MainActivity.this);break;
                        case "diagnosticsAck":requireDeveloperDiagnostics();Diagnostics.acknowledge(MainActivity.this,args.getJSONArray("ids"));result=J.obj("acknowledged",true);break;
                        case "device":result=runtime.tools.state();break;
                        case "floatingStatus":result=AgentOverlay.state();break;
                        case "terminalStatus":result=runtime.terminalTools.state();break;
                        case "setFloating":if(!(args.get("enabled") instanceof Boolean))throw new IllegalArgumentException("작은 창 설정은 켜짐 또는 꺼짐이어야 합니다.");runtime.store.setFloatingEnabled(args.getBoolean("enabled"));AgentOverlay.refresh(MainActivity.this);result=J.obj("config",runtime.store.config(),"floating",AgentOverlay.state());break;
                        case "filesStatus":result=runtime.phoneFiles.status();break;
                        case "revokeFilesFolder":if(runtime.anyWork())throw new IllegalStateException("작업 중에는 폴더 접근을 해제할 수 없습니다.");result=runtime.phoneFiles.revoke();event("files",(JSONObject)result);break;
                        case "apps":result=runtime.tools.apps();break;
                        case "saveSettings":if(runtime.anyWork())throw new IllegalStateException("작업 중에는 연결 설정을 바꿀 수 없습니다.");result=runtime.store.saveConfig(args);AgentOverlay.refresh(MainActivity.this);break;
                        case "setCapabilities":if(runtime.anyWork())throw new IllegalStateException("작업이 끝난 뒤 모델과 기능을 바꿔 주세요.");result=runtime.store.saveCapabilities(args);AgentOverlay.refresh(MainActivity.this);break;
                        case "listModels":result=runtime.listModels();break;
                        case "saveWebSettings":if(runtime.anyWork())throw new IllegalStateException("작업 중에는 검색 연결을 바꿀 수 없습니다.");result=runtime.store.saveWebSettings(args);break;
                        case "skillsLibrary":result=J.obj("skills",builtinSkills.list(),"summary",builtinSkills.summary());break;
                        case "skillsLibraryRead":result=args.has("file_path")?builtinSkills.read(args.getString("id"),args.getString("file_path")):builtinSkills.read(args.getString("id"));break;
                        case "skillsList":result=runtime.localAgentTools.skills.list();break;
                        case "skillsRead":result=args.has("file_path")?runtime.localAgentTools.skills.read(args.getString("name"),args.getString("file_path")):runtime.localAgentTools.skills.read(args.getString("name"));break;
                        case "skillsResources":result=runtime.localAgentTools.skills.resources(args.getString("name"));break;
                        case "skillsSave":{
                            if(runtime.anyWork())throw new IllegalStateException("작업 중에는 스킬을 바꿀 수 없습니다.");
                            JSONObject saved=runtime.localAgentTools.skills.save(args.getString("name"),args.getString("description"),args.getString("content"));
                            saved.put("skills",runtime.localAgentTools.skills.list());saved.put("config",runtime.store.config());result=saved;break;
                        }
                        case "skillsDelete":{
                            if(runtime.anyWork())throw new IllegalStateException("작업 중에는 스킬을 바꿀 수 없습니다.");
                            String name=args.getString("name");JSONObject removed=runtime.localAgentTools.skills.delete(name);
                            if(name.equals(runtime.store.get("active_skill_name","")))runtime.store.put("active_skill_name","");
                            removed.put("skills",runtime.localAgentTools.skills.list());removed.put("config",runtime.store.config());result=removed;break;
                        }
                        case "testConnection":result=runtime.testConnection();break;
                        case "saveMemory":{if(runtime.anyWork())throw new IllegalStateException("작업이 끝난 뒤 메모리를 바꿔 주세요.");String text=args.optString("text");if(text.length()>4000)throw new IllegalArgumentException("메모리는 4,000자까지 저장됩니다.");runtime.localAgentTools.documents.save("MEMORY.md",text);result=J.obj("saved",true);break;}
                        case "compactConversation":runtime.compactConversation(args.getString("session"),id);return;
                        case "memoryDocumentsList":result=runtime.localAgentTools.documents.list();break;
                        case "memoryDocumentsRead":result=runtime.localAgentTools.documents.read(args.getString("name"));break;
                        case "memoryDocumentsSave":{if(runtime.anyWork())throw new IllegalStateException("작업이 끝난 뒤 메모리를 바꿔 주세요.");JSONObject saved=runtime.localAgentTools.documents.save(args.getString("name"),args.getString("content"));saved.put("documents",runtime.localAgentTools.documents.list());result=saved;break;}
                        case "memoryDocumentsDelete":{if(runtime.anyWork())throw new IllegalStateException("작업이 끝난 뒤 메모리를 바꿔 주세요.");JSONObject deleted=runtime.localAgentTools.documents.delete(args.getString("name"));deleted.put("documents",runtime.localAgentTools.documents.list());result=deleted;break;}
                        case "jobs":result=runtime.jobs.state();break;
                        case "startBackgroundTask":result=J.obj("job_id",runtime.jobs.startUserTask(args.getString("task"),args.optString("context",""),args.optString("session","")),"jobs",runtime.jobs.state());break;
                        case "jobResult":result=runtime.jobs.ownerResult(args.getString("job_id"));break;
                        case "cancelJob":result=runtime.jobs.ownerCancel(args.getString("job_id"));break;
                        case "plan":result=new JSONArray(runtime.store.get("plan:"+args.optString("session"),"[]"));break;
                        case "startChat":runtime.start(args.optString("text"),args.optString("session",""));result=J.obj("started",true);break;
                        case "runTool":runtime.localTool(args.getString("name"),args.optJSONObject("arguments")==null?J.obj():args.getJSONObject("arguments"),id);return;
                        case "probeRoot":
                            result=runtime.probeRoot();break;
                        default:throw new SecurityException("지원하지 않는 앱 요청입니다.");
                    }
                    reply(id,true,result);
                }catch(Exception e){if(AgentRuntime.unexpectedFailure(e)&&!(e instanceof IllegalStateException))Diagnostics.record(MainActivity.this,"bridge",e);reply(id,false,J.obj("message",J.error(e)));}
            });}catch(RejectedExecutionException closed){/* Activity teardown: the removed view has no reply target. */}
        }
    }
    private static int browserPosition(JSONObject args,String key) throws JSONException {
        Object value=args.get(key);if(!(value instanceof Number))throw new IllegalArgumentException("화면 오류 위치가 올바르지 않습니다.");
        double number=((Number)value).doubleValue();if(Double.isNaN(number)||Double.isInfinite(number)||number<0||number>1000000||number!=Math.floor(number))throw new IllegalArgumentException("화면 오류 위치가 올바르지 않습니다.");return (int)number;
    }
    private void requireDeveloperDiagnostics(){
        if((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)==0)throw new SecurityException("개발 검증에서만 오류 기록을 읽을 수 있습니다.");
    }
    private void chooseBuiltinSkill(WebView owner,String request,JSONObject args){
        try{io.submit(()->{try{
            if(runtime.anyWork())throw new IllegalStateException("작업이 끝난 뒤 원본 스킬을 가져오세요.");
            String id=args.getString("id");JSONObject entry=builtinSkills.read(id);
            if(!entry.getBoolean("installable"))throw new IllegalStateException("이 패키지는 현재 Android에서 원본 그대로 가져올 수 없습니다: "+entry.optJSONArray("install_blockers"));
            postUi(owner,()->new AlertDialog.Builder(this).setTitle("원본 스킬을 가져올까요?").setMessage(entry.optString("name")+"의 원본 SKILL.md와 지원 파일을 저장합니다. 기존 패키지를 덮어쓰거나 스킬을 자동 선택·실행하지 않습니다.")
                .setNegativeButton("취소",(d,w)->reply(request,true,J.obj("cancelled",true,"imported",false)))
                .setPositiveButton("가져오기",(d,w)->{try{io.submit(()->{try{
                    if(runtime.anyWork())throw new IllegalStateException("작업이 끝난 뒤 다시 시도하세요.");
                    JSONObject imported=builtinSkills.install(id,runtime.localAgentTools.skills);imported.put("skills",runtime.localAgentTools.skills.list());imported.put("config",runtime.store.config());reply(request,true,imported);
                }catch(Exception e){reply(request,false,J.obj("message",J.error(e)));}});}catch(RejectedExecutionException closed){reply(request,false,J.obj("message","앱을 다시 열고 스킬을 가져오세요."));}}).show());
        }catch(Exception e){reply(request,false,J.obj("message",J.error(e)));}});}catch(RejectedExecutionException closed){reply(request,false,J.obj("message","앱을 다시 열고 스킬을 가져오세요."));}
    }
    private void chooseSkillPackage(String id,JSONObject args,boolean exporting){
        try{
            if(runtime.anyWork())throw new IllegalStateException("작업이 끝난 뒤 스킬 파일을 선택하세요.");
            if(!pendingSkillRequest.isEmpty()||!pendingFileRequest.isEmpty())throw new IllegalStateException("이미 파일 선택기가 열려 있습니다.");
            String name=LocalSkillStore.validName(args.getString("name"));
            if(args.has("replace")&&!(args.get("replace") instanceof Boolean))throw new IllegalArgumentException("교체 설정은 켜짐 또는 꺼짐이어야 합니다.");
            boolean replace=!exporting&&args.optBoolean("replace",false);
            Runnable launch=()->{
                Intent intent;
                if(exporting)intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE,name+".zip");
                else intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/zip","application/x-zip-compressed","text/markdown","text/plain","application/octet-stream"});
                pendingSkillRequest=id;pendingSkillName=name;pendingSkillReplace=replace;
                try{startActivityForResult(intent,exporting?SKILL_EXPORT_REQUEST:SKILL_IMPORT_REQUEST);}catch(Exception e){pendingSkillRequest="";pendingSkillName="";reply(id,false,J.obj("message",J.error(e)));}
            };
            if(replace)new AlertDialog.Builder(this).setTitle("기존 스킬을 교체할까요?").setMessage(name+"의 SKILL.md와 리소스 파일을 선택한 패키지로 교체합니다.").setNegativeButton("취소",(d,w)->reply(id,true,J.obj("cancelled",true,"imported",false))).setPositiveButton("교체할 파일 선택",(d,w)->launch.run()).show();else launch.run();
        }catch(Exception e){if(AgentRuntime.unexpectedFailure(e)&&!(e instanceof IllegalStateException))Diagnostics.record(MainActivity.this,"bridge",e);reply(id,false,J.obj("message",J.error(e)));}
    }
    private void finishSkillPicker(int requestCode,int resultCode,Intent result){
        final String id=pendingSkillRequest,name=pendingSkillName;final boolean replace=pendingSkillReplace,exporting=requestCode==SKILL_EXPORT_REQUEST;
        pendingSkillRequest="";pendingSkillName="";pendingSkillReplace=false;
        if(id.isEmpty())return;
        if(resultCode!=RESULT_OK||result==null||result.getData()==null){reply(id,true,J.obj("cancelled",true,exporting?"exported":"imported",false));return;}
        final Uri uri=result.getData();
        try{io.submit(()->{try{
            if(runtime.anyWork())throw new IllegalStateException("작업이 끝난 뒤 스킬 파일을 다시 선택하세요.");
            if(exporting){try(OutputStream stream=getContentResolver().openOutputStream(uri,"w")){if(stream==null)throw new IOException("저장할 파일을 열지 못했습니다.");runtime.localAgentTools.skills.exportPackage(name,stream);}reply(id,true,J.obj("exported",true,"name",name));}
            else{
                String filename="";try(android.database.Cursor cursor=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())filename=cursor.getString(0);}
                String mime=getContentResolver().getType(uri);String lower=filename.toLowerCase(java.util.Locale.ROOT);boolean markdown=lower.endsWith(".md")||lower.endsWith(".markdown")||"text/markdown".equals(mime)||"text/plain".equals(mime);
                JSONObject imported;try(InputStream stream=getContentResolver().openInputStream(uri)){if(stream==null)throw new IOException("스킬 파일을 열지 못했습니다.");imported=markdown?runtime.localAgentTools.skills.importMarkdown(name,stream,replace):runtime.localAgentTools.skills.importPackage(name,stream,replace);}
                imported.put("imported",true);imported.put("skills",runtime.localAgentTools.skills.list());imported.put("config",runtime.store.config());reply(id,true,imported);
            }
        }catch(Exception e){if(AgentRuntime.unexpectedFailure(e)&&!(e instanceof IllegalStateException))Diagnostics.record(MainActivity.this,"bridge",e);reply(id,false,J.obj("message",J.error(e)));}});}catch(RejectedExecutionException closed){reply(id,false,J.obj("message","앱을 다시 열고 파일을 선택하세요."));}
    }
    private void chooseFilesFolder(String id){
        if(runtime.busy()){reply(id,false,J.obj("message","작업이 끝난 뒤 사용할 폴더를 선택하세요."));return;}
        if(!pendingFileRequest.isEmpty()){reply(id,false,J.obj("message","이미 폴더 선택기가 열려 있습니다."));return;}
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        pendingFileRequest=id;try{startActivityForResult(intent,FILE_TREE_REQUEST);}catch(Exception e){pendingFileRequest="";reply(id,false,J.obj("message",J.error(e)));}
    }
    private void deleteSession(String id,String sid,boolean gesture){
        if(runtime.busy()){reply(id,false,J.obj("message","작업이 끝난 뒤 대화를 삭제해 주세요."));return;}
        if(sid.isEmpty()||sid.length()>200||!runtime.store.exists(sid)){reply(id,false,J.obj("message","삭제할 대화를 찾을 수 없습니다."));return;}
        if(gesture){performSessionDeletion(id,sid);return;}
        new AlertDialog.Builder(this).setTitle("이 대화를 삭제할까요?").setMessage("이 휴대폰에 저장된 대화와 전송 기록을 삭제합니다. 모델 제공자의 데이터 보관 정책은 별도로 적용됩니다.")
            .setNegativeButton("취소",(d,w)->reply(id,true,J.obj("deleted",false)))
            .setPositiveButton("삭제",(d,w)->performSessionDeletion(id,sid)).show();
    }
    private void performSessionDeletion(String id,String sid){
        try{
            if(runtime.anyWork())throw new IllegalStateException("작업이 끝난 뒤 대화를 삭제해 주세요.");
            if(!runtime.store.exists(sid))throw new IllegalArgumentException("삭제할 대화를 찾을 수 없습니다.");
            runtime.store.removeSession(sid);
            if(runtime.store.exists(sid))throw new IllegalStateException("대화 삭제 결과를 확인하지 못했습니다.");
            reply(id,true,J.obj("deleted",true,"sessions",runtime.store.sessions()));
        }catch(Exception e){if(AgentRuntime.unexpectedFailure(e)&&!(e instanceof IllegalStateException))Diagnostics.record(MainActivity.this,"bridge",e);reply(id,false,J.obj("message",J.error(e)));}
    }
    private void rootToggle(String id,boolean on){
        if(!on){runtime.store.flag("root_enabled",false);runtime.root.revoke();reply(id,true,runtime.tools.state());return;}
        new AlertDialog.Builder(this).setTitle("기존 Root 권한 연동").setMessage("이미 Root 권한을 가진 본인 테스트 기기에만 사용하세요. UID 확인, 프로세스 조회, Wi-Fi 변경, 허용한 일반 앱 종료만 노출합니다. 임의 쉘·시스템 파일 변경은 제공하지 않습니다. 각 작업에 별도 승인이 필요합니다.")
            .setNegativeButton("취소",(d,w)->reply(id,true,runtime.tools.state())).setPositiveButton("연동 사용",(d,w)->{runtime.store.flag("root_enabled",true);reply(id,true,runtime.tools.state());}).show();
    }
    private void permission(String id,String kind){
        try{
            switch(kind){
                case "accessibility":
                    new AlertDialog.Builder(this).setTitle("화면 제어에 대한 안내").setMessage("접근성 서비스는 허용된 앱의 화면 텍스트와 화면 이미지를 읽고 클릭·입력·스크롤합니다. 작업에 필요한 화면 내용과 이미지는 설정한 모델 API로 전송될 수 있습니다. 화면 이미지는 Android 11 이상에서 지원하며 보안 화면은 캡처할 수 없습니다. 비밀번호 입력란과 시스템 권한·Hermes 화면은 제어에서 제외됩니다. 저장한 승인 방식(승인 요청 또는 자동 승인)을 따르며 다른 앱에서는 작은 창으로 상태와 입력을 제공합니다. 업데이트 후 화면 이미지 권한이 연결되지 않으면 서비스를 껐다 켜세요. Android 설정에서 언제든 끌 수 있습니다.")
                        .setNegativeButton("취소",(d,w)->reply(id,true,J.obj("opened",false))).setPositiveButton("설정에서 직접 켜기",(d,w)->{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));reply(id,true,J.obj("opened",true));}).show();return;
                case "brightness":startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,Uri.parse("package:"+getPackageName())));break;
                case "notifications":if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},7);else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));break;
                default:throw new IllegalArgumentException("알 수 없는 권한입니다.");
            }
            reply(id,true,J.obj("opened",true));
        }catch(Exception e){if(AgentRuntime.unexpectedFailure(e)&&!(e instanceof IllegalStateException))Diagnostics.record(MainActivity.this,"bridge",e);reply(id,false,J.obj("message",J.error(e)));}
    }
    private void chooseApps(String id){
        if(runtime.busy()){reply(id,false,J.obj("message","작업 중에는 허용 목록을 바꿀 수 없습니다."));return;}
        JSONArray apps=runtime.tools.apps();String[] labels=new String[apps.length()];boolean[] chosen=new boolean[apps.length()];
        for(int i=0;i<apps.length();i++){JSONObject a=apps.optJSONObject(i);labels[i]=a.optString("label")+"\n"+a.optString("package");chosen[i]=a.optBoolean("allowed");}
        new AlertDialog.Builder(this).setTitle("제어할 앱을 직접 선택하세요").setMultiChoiceItems(labels,chosen,(d,which,value)->chosen[which]=value)
            .setNegativeButton("취소",(d,w)->reply(id,true,runtime.store.allowedApps())).setPositiveButton("저장",(d,w)->{JSONArray allow=new JSONArray();for(int i=0;i<chosen.length;i++)if(chosen[i])allow.put(apps.optJSONObject(i).optString("package"));runtime.store.put("allowed_apps",allow.toString());reply(id,true,allow);}).show();
    }
}
