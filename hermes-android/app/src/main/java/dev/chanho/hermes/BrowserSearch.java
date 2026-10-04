package dev.chanho.hermes;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.*;
import java.util.*;

/** The owner's real default browser, never an invisible WebView or an invented search API. */
final class BrowserSearch {
    private final AgentRuntime runtime;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    BrowserSearch(AgentRuntime runtime) { this.runtime = runtime; context = runtime.context; }
    static boolean handles(String name) { return Arrays.asList("browser_search", "browser_open", "browser_snapshot").contains(name); }
    private static JSONObject schema(String name, String description, JSONObject properties, JSONArray required) {
        return J.obj("type","function","function",J.obj("name",name,"description",description,"parameters",J.obj("type","object","properties",properties,"required",required,"additionalProperties",false)));
    }
    static JSONArray schemas() {
        JSONObject inspect = J.obj("type","boolean","description","Read fresh redacted visible content in the exact default browser after dispatch; default true.");
        return J.arr(
            schema("browser_search", "Search in the actual default phone browser. Browser stays visible. Returns real redacted accessibility content and element IDs, not fabricated hits. Use fresh snapshot IDs with click_element/type_text/scroll_element; never reuse a snapshot after another observation or action.", J.obj("query",J.obj("type","string","minLength",1,"maxLength",500),"inspect_screen",inspect), J.arr("query")),
            schema("browser_open", "Open a public HTTPS URL in the actual default browser and observe its visible content. Works without a Tavily key. requestedUrl is not a verified redirect/final URL. Return to browser_snapshot/read_screen after navigation and inspect actual evidence before citing.", J.obj("url",J.obj("type","string","maxLength",2048),"inspect_screen",inspect), J.arr("url")),
            schema("browser_snapshot", "Read the current default browser only; does not navigate, click or search. Returns fresh element IDs and visible text. Offscreen content, the DOM, cookies and login secrets are not read. A successful observation does not establish that navigation/search completed.", J.obj(), J.arr())
        );
    }
    static void validate(JSONObject args) throws Exception { validateArguments("browser_search", args); }
    static void validateArguments(String name, JSONObject args) throws Exception {
        if(args == null || !handles(name)) throw new IllegalArgumentException("지원하지 않는 브라우저 요청입니다.");
        String required = "browser_search".equals(name) ? "query" : "browser_open".equals(name) ? "url" : "";
        Set<String> allowed = required.isEmpty() ? Collections.emptySet() : new HashSet<>(Arrays.asList(required,"inspect_screen"));
        Iterator<String> keys = args.keys();
        while(keys.hasNext()) if(!allowed.contains(keys.next())) throw new IllegalArgumentException("알 수 없는 브라우저 인자입니다.");
        if(!required.isEmpty()) {
            Object raw = args.opt(required);
            int limit = "query".equals(required) ? 500 : 2048;
            if(!(raw instanceof String) || ((String)raw).trim().isEmpty() || ((String)raw).length()>limit || ((String)raw).indexOf('\u0000')>=0)
                throw new IllegalArgumentException("브라우저 검색어 또는 URL 형식이 올바르지 않습니다.");
            if("url".equals(required)) WebTools.publicUrl(((String)raw).trim(), false);
        }
        if(args.has("inspect_screen") && !(args.opt("inspect_screen") instanceof Boolean)) throw new IllegalArgumentException("inspect_screen은 true 또는 false여야 합니다.");
    }
    static Intent nativeQuery(String query,String browserPackage) { return new Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY,query).putExtra(SearchManager.EXTRA_NEW_SEARCH,true).setPackage(browserPackage); }
    static Intent urlQuery(String query,String browserPackage) { return new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+Uri.encode(query))).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(browserPackage); }
    private void gate(String mode) throws Exception {
        runtime.checkCancelled();
        if(!runtime.busy() || !runtime.isUnlocked()) throw new SecurityException("활성 요청과 잠금 해제 상태가 필요합니다.");
        if(!"all".equals(runtime.store.deviceScope())) throw new SecurityException("브라우저 조작에는 전체 기기 범위가 필요합니다.");
        JSONObject config=runtime.store.config();
        if(!LocalCapabilities.allowed("web_search",config) || !LocalCapabilities.allowed("launch_app",config)) throw new SecurityException("웹 검색과 기기 도구를 먼저 켜 주세요.");
        if(mode!=null && !mode.equals(runtime.store.approvalMode())) throw new SecurityException("승인 중 승인 방식이 변경되었습니다.");
    }
    private String defaultBrowser() throws Exception {
        Intent generic=new Intent(Intent.ACTION_VIEW,Uri.parse("https://example.com/")).addCategory(Intent.CATEGORY_BROWSABLE);
        ResolveInfo resolved=context.getPackageManager().resolveActivity(generic,PackageManager.MATCH_DEFAULT_ONLY);
        if(resolved==null || resolved.activityInfo==null || PhoneAccessibilityService.protectedPackage(resolved.activityInfo.packageName)) throw new IllegalStateException("Android 설정에서 기본 브라우저를 먼저 선택해 주세요.");
        return resolved.activityInfo.packageName;
    }
    private Intent searchIntent(String query,String browserPackage) {
        Intent nativeIntent=nativeQuery(query,browserPackage);
        ResolveInfo resolved=context.getPackageManager().resolveActivity(nativeIntent,PackageManager.MATCH_DEFAULT_ONLY);
        return resolved!=null && resolved.activityInfo!=null && browserPackage.equals(resolved.activityInfo.packageName) ? nativeIntent : urlQuery(query,browserPackage);
    }
    JSONObject execute(JSONObject args) throws Exception { return execute("browser_search",args); }
    JSONObject execute(String name, JSONObject args) throws Exception {
        validateArguments(name,args);
        String mode=runtime.store.approvalMode(); gate(mode);
        final String browserPackage=defaultBrowser();
        if("browser_snapshot".equals(name)) {
            JSONObject result=baseResult(browserPackage); result.put("dispatched",false);
            inspectBrowser(result,browserPackage,mode,1500);
            boolean observed=result.optBoolean("browserForegroundObserved");
            return J.obj("ok",observed,"result",result);
        }
        boolean searching="browser_search".equals(name);
        final String value=args.getString(searching?"query":"url").trim();
        if(!searching) WebTools.publicUrl(value,true);
        boolean inspect=args.optBoolean("inspect_screen",true);
        runtime.emit("tool",J.obj("name",name,"status","승인 대기"));
        String detail=browserPackage+"\n"+value+"\n\n기본 브라우저가 화면에 열립니다. Hermes 작업은 알림에서 중단할 수 있습니다.";
        if(!runtime.approvals.ask(searching?"브라우저 검색 승인":"브라우저 페이지 열기 승인",detail,false)) {
            runtime.store.audit(name,"denied","사용자가 승인하지 않음 · URL/검색어는 기록하지 않음");
            return J.obj("ok",false,"denied",true,"error","사용자가 승인하지 않았습니다. 자동 재시도하지 마세요.");
        }
        gate(mode);
        JSONObject result=MainThreadCall.call(task->main.post(task),runtime::cancelled,()->{
            gate(mode);
            if(!browserPackage.equals(defaultBrowser())) throw new SecurityException("승인 중 기본 브라우저가 변경되었습니다.");
            Intent intent=searching?searchIntent(value,browserPackage):new Intent(Intent.ACTION_VIEW,Uri.parse(value)).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(browserPackage);
            ResolveInfo actual=context.getPackageManager().resolveActivity(intent,PackageManager.MATCH_DEFAULT_ONLY);
            if(actual==null || actual.activityInfo==null || !browserPackage.equals(actual.activityInfo.packageName)) throw new IllegalStateException("기본 브라우저 처리기를 확인하지 못했습니다.");
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            JSONObject dispatched=baseResult(browserPackage);
            dispatched.put("dispatched",true); dispatched.put("handlerActivity",actual.activityInfo.name);
            dispatched.put("dispatchMethod",searching?(Intent.ACTION_WEB_SEARCH.equals(intent.getAction())?"browser_native_web_search":"browser_https_google_search"):"browser_https_navigation");
            if(searching) dispatched.put("searchEngine",Intent.ACTION_WEB_SEARCH.equals(intent.getAction())?"browser_handler_defined":"google");
            else dispatched.put("requestedUrl",value);
            return dispatched;
        },10000);
        if(inspect) inspectBrowser(result,browserPackage,mode,6500); else result.put("observationStatus","not_requested");
        runtime.store.audit(name,"completed","기본 브라우저에 요청 전달 · 페이지/검색 완료와 구분");
        return J.obj("ok",true,"result",result);
    }
    private static JSONObject baseResult(String browserPackage) {
        return J.obj("provider","android_default_browser","browserPackage",browserPackage,"verified",false,"verification","android_activity_dispatch_only","browserForegroundObserved",false,"invisibleBackgroundBrowser",false,"structuredResultsAvailable",false,"untrusted",true,"limitations","Visible content only, not the full DOM or headless browsing. Inspect the exact page and source URLs; requested navigation is not verified navigation. Never fabricate search hits.");
    }
    private void inspectBrowser(JSONObject result,String browserPackage,String mode,long timeoutMs) throws Exception {
        if(!LocalCapabilities.allowed("read_screen",runtime.store.config())) { result.put("observationStatus","screen_module_disabled"); return; }
        if(PhoneAccessibilityService.current()==null) { result.put("observationStatus","accessibility_unavailable"); return; }
        long start=SystemClock.elapsedRealtime(), until=start+timeoutMs;
        String previous=""; int stable=0; JSONObject latest=null;
        while(SystemClock.elapsedRealtime()<until) {
            gate(mode);
            if(!LocalCapabilities.allowed("read_screen",runtime.store.config())) { result.put("observationStatus","screen_module_disabled"); return; }
            JSONObject observed=MainThreadCall.call(task->main.post(task),runtime::cancelled,()->{
                gate(mode);
                if(!LocalCapabilities.allowed("read_screen",runtime.store.config())) throw new SecurityException("화면 도구가 꺼졌습니다.");
                PhoneAccessibilityService service=PhoneAccessibilityService.current();
                if(service==null) return J.obj("status","accessibility_unavailable");
                try {
                    if(!browserPackage.equals(service.requireForegroundApp())) return J.obj("status","waiting_for_exact_browser");
                    JSONObject snapshot=service.snapshot();
                    if(!browserPackage.equals(snapshot.optString("package"))) return J.obj("status","browser_changed");
                    return J.obj("status","observed","snapshot",snapshot);
                } catch(SecurityException|IllegalStateException blocked) { return J.obj("status","screen_not_available"); }
            },2500);
            String status=observed.optString("status");
            if("observed".equals(status)) {
                latest=observed.getJSONObject("snapshot");
                String content=visibleText(latest);
                stable=content.equals(previous)?stable+1:0; previous=content;
                // Observe only while the browser is loading. Never click/reload to force success.
                if(content.length()>100 && stable>=2 && SystemClock.elapsedRealtime()-start>=900) break;
            } else {
                latest=null; stable=0; previous="";
                if("accessibility_unavailable".equals(status)) { result.put("observationStatus",status); return; }
            }
            result.put("observationStatus",status); Thread.sleep(200);
        }
        if(latest==null) { result.put("observationStatus","browser_screen_not_observed_within_timeout"); return; }
        result.put("browserForegroundObserved",true); result.put("observationStatus","observed");
        result.put("visibleScreenObservation",latest); result.put("content",visibleText(latest));
        result.put("contentScope","visible_redacted_accessibility_nodes_only"); result.put("stableObservation",stable>=2);
        result.put("retrievedAt",System.currentTimeMillis()); result.put("verification","exact_browser_visible_redacted_accessibility_observation_only");
        result.put("notice","현재 표시된 기본 브라우저의 실제 내용입니다. 전체 본문·최종 URL·검색 완료는 보증하지 않습니다. 필요하면 새 snapshot으로 스크롤·클릭하고 다시 읽으세요.");
    }
    static String visibleText(JSONObject snapshot) {
        JSONArray elements=snapshot.optJSONArray("elements"); if(elements==null) return "";
        StringBuilder text=new StringBuilder(); Set<String> seen=new LinkedHashSet<>();
        for(int i=0;i<elements.length() && text.length()<32000;i++) {
            JSONObject e=elements.optJSONObject(i); if(e==null || e.optBoolean("editable")) continue;
            String value=e.optString("text","").trim(); if(value.isEmpty()) value=e.optString("description","").trim();
            if(value.isEmpty() || !seen.add(value)) continue;
            text.append('[').append(e.optString("id")).append("] ").append(value).append('\n');
        }
        return J.clipped(text.toString(),32000);
    }
}
