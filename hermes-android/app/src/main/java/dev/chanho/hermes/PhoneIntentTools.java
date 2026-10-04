package dev.chanho.hermes;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import org.json.*;
import java.net.URI;
import java.util.Arrays;
import java.util.Iterator;

/** Typed ordinary Android activities. Dispatch never proves completion in the receiving app. */
final class PhoneIntentTools {
    private final AgentRuntime runtime;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    PhoneIntentTools(AgentRuntime runtime){this.runtime=runtime;this.context=runtime.context;}
    static boolean handles(String name){return Arrays.asList("open_link","open_map","share_text","compose_message","set_alarm").contains(name);}
    private static JSONObject text(int max){return J.obj("type","string","maxLength",max);}
    private static void add(JSONArray out,String name,String description,JSONObject properties,JSONArray required){out.put(J.obj("type","function","function",J.obj("name",name,"description",description,"parameters",J.obj("type","object","properties",properties,"required",required,"additionalProperties",false))));}
    static JSONArray schemas(){
        JSONArray out=new JSONArray();
        add(out,"open_link","Open an HTTP(S) URL in an installed Android handler after native owner approval. Rejects credentials and arbitrary intent/file schemes. Dispatch does not prove page load.",J.obj("url",text(4096)),J.arr("url"));
        add(out,"open_map","Open an installed map app using a search query or latitude/longitude. Supply query OR both coordinates. Native owner approval; dispatch does not prove route or map load.",J.obj("query",text(1000),"latitude",J.obj("type","number","minimum",-90,"maximum",90),"longitude",J.obj("type","number","minimum",-180,"maximum",180)),J.arr());
        add(out,"share_text","Open Android's share chooser with text. Native owner approval. The owner selects the receiving app; never silently sends or proves delivery.",J.obj("text",text(16000),"subject",text(200)),J.arr("text"));
        add(out,"compose_message","Open an SMS/email draft or dialer. kind sms/email/dial; one recipient required. Does not send messages or initiate a call. The owner completes the action in the receiving app. Native approval applies.",J.obj("kind",J.obj("type","string","maxLength",5,"enum",J.arr("sms","email","dial")),"recipient",text(320),"text",text(16000),"subject",text(200)),J.arr("kind","recipient"));
        add(out,"set_alarm","Request an alarm in an installed clock app with its confirmation UI visible. Native owner approval. Dispatch is not proof an alarm was saved; inspect clock UI afterward.",J.obj("hour",J.obj("type","integer","minimum",0,"maximum",23),"minute",J.obj("type","integer","minimum",0,"maximum",59),"label",text(200)),J.arr("hour","minute"));
        return out;
    }
    private void gate(String name,String mode)throws Exception{
        runtime.checkCancelled();
        if(!runtime.busy()||!runtime.isUnlocked())throw new SecurityException("활성 요청과 잠금 해제 상태가 필요합니다.");
        if(!"all".equals(runtime.store.deviceScope()))throw new SecurityException("전체 기기 범위를 먼저 설정해 주세요.");
        if(!LocalCapabilities.allowed(name,runtime.store.config()))throw new SecurityException("기기 도구가 꺼져 있습니다.");
        if(mode!=null&&!mode.equals(runtime.store.approvalMode()))throw new SecurityException("승인 중 승인 방식이 변경되었습니다.");
    }
    JSONObject execute(String name,JSONObject args)throws Exception{
        validate(name,args);
        Intent target=build(name,args);
        String mode=runtime.store.approvalMode();gate(name,mode);
        if(target.resolveActivity(context.getPackageManager())==null)throw new IllegalStateException("이 작업을 처리할 설치된 앱이 없습니다.");
        runtime.emit("tool",J.obj("name",name,"status","승인 대기"));
        if(!runtime.approvals.ask("기기 작업 승인",detail(name,args),false)){
            runtime.store.audit(name,"denied","사용자가 승인하지 않음 · 내용은 기록하지 않음");
            return J.obj("ok",false,"denied",true,"error","사용자가 승인하지 않았습니다. 자동 재시도하지 마세요.");
        }
        gate(name,mode);
        try{
            JSONObject result=MainThreadCall.call(task->main.post(task),runtime::cancelled,()->{
                gate(name,mode);
                ResolveInfo handler=context.getPackageManager().resolveActivity(target,android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
                if(handler==null||handler.activityInfo==null)throw new IllegalStateException("처리할 앱이 더 이상 없습니다.");
                Intent launch="share_text".equals(name)?Intent.createChooser(target,"공유할 앱 선택"):target;
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return J.obj("dispatched",true,"verified",false,"verification","android_activity_dispatch_only","handlerPackage",handler.activityInfo.packageName,"handlerActivity",handler.activityInfo.name,"requiresUserCompletion",Arrays.asList("share_text","compose_message","set_alarm").contains(name),"message","앱으로 요청을 전달했습니다. 실제 완료 여부는 대상 화면에서 확인해야 합니다.");
            },10000);
            runtime.store.audit(name,"completed","Android 앱 요청 전달 · 실제 작업 완료는 미확인 · 인자는 기록하지 않음");
            return J.obj("ok",true,"result",result);
        }catch(Exception e){runtime.store.audit(name,"failed",e.getClass().getSimpleName());throw e;}
    }
    static void validate(String name,JSONObject args)throws Exception{
        if(args==null)throw new IllegalArgumentException("인자가 필요합니다.");
        JSONObject schema=null;JSONArray all=schemas();for(int i=0;i<all.length();i++){JSONObject f=all.getJSONObject(i).getJSONObject("function");if(name.equals(f.getString("name")))schema=f.getJSONObject("parameters");}
        if(schema==null)throw new SecurityException("등록되지 않은 기기 도구입니다.");
        JSONObject properties=schema.getJSONObject("properties");JSONArray required=schema.getJSONArray("required");
        for(int i=0;i<required.length();i++){String key=required.getString(i);if(!args.has(key)||args.isNull(key))throw new IllegalArgumentException("필수 인자가 없습니다: "+key);}
        Iterator<String> keys=args.keys();while(keys.hasNext()){
            String key=keys.next();if(!properties.has(key))throw new IllegalArgumentException("알 수 없는 인자: "+key);
            JSONObject p=properties.getJSONObject(key);Object v=args.get(key);String type=p.getString("type");
            if("string".equals(type)){
                if(!(v instanceof String)||((String)v).length()>p.getInt("maxLength")||((String)v).indexOf('\u0000')>=0)throw new IllegalArgumentException("올바른 텍스트가 필요합니다: "+key);
                JSONArray choices=p.optJSONArray("enum");if(choices!=null){boolean found=false;for(int i=0;i<choices.length();i++)if(v.equals(choices.getString(i)))found=true;if(!found)throw new IllegalArgumentException("지원하지 않는 종류입니다.");}
            }else{
                if(!(v instanceof Number))throw new IllegalArgumentException("숫자가 필요합니다: "+key);
                double n=((Number)v).doubleValue();if(!Double.isFinite(n)||n<p.getDouble("minimum")||n>p.getDouble("maximum")||("integer".equals(type)&&n!=Math.rint(n)))throw new IllegalArgumentException("범위를 벗어난 숫자입니다: "+key);
            }
        }
        if("open_link".equals(name)){
            URI uri;try{uri=new URI(args.getString("url"));}catch(Exception e){throw new IllegalArgumentException("올바른 웹 주소가 필요합니다.");}
            if(!Arrays.asList("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getRawUserInfo()!=null||uri.getPort()<-1||uri.getPort()>65535)throw new IllegalArgumentException("인증 정보가 없는 HTTP 또는 HTTPS 주소만 열 수 있습니다.");
        }
        if("open_map".equals(name)){
            boolean query=args.has("query"),lat=args.has("latitude"),lon=args.has("longitude");
            if(query?(lat||lon||args.getString("query").trim().isEmpty()):(!lat||!lon))throw new IllegalArgumentException("검색어 또는 위도와 경도를 입력해 주세요.");
        }
        if("share_text".equals(name)&&args.getString("text").trim().isEmpty())throw new IllegalArgumentException("공유할 텍스트가 없습니다.");
        if("compose_message".equals(name)){
            String kind=args.getString("kind"),recipient=args.getString("recipient");
            if("email".equals(kind)){
                if(!recipient.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\\.[A-Za-z]{2,}"))throw new IllegalArgumentException("이메일 주소 하나를 입력해 주세요.");
            }else if(!recipient.matches("\\+?[0-9][0-9 ()-]{0,38}"))throw new IllegalArgumentException("일반 전화번호 하나를 입력해 주세요. 서비스 코드와 내선 제어는 지원하지 않습니다.");
            if("dial".equals(kind)&&(args.has("text")||args.has("subject")))throw new IllegalArgumentException("다이얼 도구에는 전화번호만 입력해 주세요.");
            if("sms".equals(kind)&&args.has("subject"))throw new IllegalArgumentException("SMS는 제목을 지원하지 않습니다.");
        }
    }
    static Intent build(String name,JSONObject a)throws Exception{
        switch(name){
            case "open_link":return new Intent(Intent.ACTION_VIEW,Uri.parse(a.getString("url"))).addCategory(Intent.CATEGORY_BROWSABLE);
            case "open_map":return new Intent(Intent.ACTION_VIEW,Uri.parse(a.has("query")?"geo:0,0?q="+Uri.encode(a.getString("query")):"geo:"+a.getDouble("latitude")+","+a.getDouble("longitude")));
            case "share_text":{Intent i=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,a.getString("text"));if(a.has("subject"))i.putExtra(Intent.EXTRA_SUBJECT,a.getString("subject"));return i;}
            case "compose_message":{
                String kind=a.getString("kind"),recipient=a.getString("recipient");
                if("dial".equals(kind))return new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",recipient,null));
                if("sms".equals(kind))return new Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",recipient,null)).putExtra("sms_body",a.optString("text",""));
                String mail="mailto:"+Uri.encode(recipient,"@")+"?subject="+Uri.encode(a.optString("subject",""))+"&body="+Uri.encode(a.optString("text",""));
                return new Intent(Intent.ACTION_SENDTO,Uri.parse(mail)).putExtra(Intent.EXTRA_EMAIL,new String[]{recipient}).putExtra(Intent.EXTRA_SUBJECT,a.optString("subject","")).putExtra(Intent.EXTRA_TEXT,a.optString("text",""));
            }
            case "set_alarm":return new Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR,a.getInt("hour")).putExtra(AlarmClock.EXTRA_MINUTES,a.getInt("minute")).putExtra(AlarmClock.EXTRA_MESSAGE,a.optString("label","")).putExtra(AlarmClock.EXTRA_SKIP_UI,false);
            default:throw new IllegalArgumentException("지원하지 않는 기기 도구입니다.");
        }
    }
    private static String detail(String name,JSONObject args)throws Exception{
        switch(name){
            case "open_link":return "웹 주소 열기\n"+args.getString("url");
            case "open_map":return "지도 열기\n"+(args.has("query")?args.getString("query"):args.getDouble("latitude")+", "+args.getDouble("longitude"));
            case "share_text":return "공유 앱 선택 화면 열기\n"+J.clipped(args.getString("text"),1200);
            case "compose_message":return "작성 화면 열기 (직접 전송하지 않음)\n"+args.getString("kind")+" · "+args.getString("recipient")+"\n"+J.clipped(args.optString("subject","")+"\n"+args.optString("text",""),1200);
            case "set_alarm":return "알람 앱에서 확인\n"+args.getInt("hour")+":"+String.format(java.util.Locale.ROOT,"%02d",args.getInt("minute"))+"\n"+args.optString("label","");
            default:return name;
        }
    }
}
