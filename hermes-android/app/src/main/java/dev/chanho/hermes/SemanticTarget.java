package dev.chanho.hermes;

import org.json.*;
import java.util.*;

/** Exact semantic selection on a just-observed screen. No fuzzy match or coordinate fallback. */
final class SemanticTarget {
    static JSONObject schema(){
        return ToolArgs.schema("act_on_screen",
            "Observe the current allowed app and act on exactly one matching live element in a single native transaction. Prefer this over old snapshot coordinates after long model reasoning. Match expected_package plus exact resource_id and/or exact label; no fuzzy matching. Reads and actions retain the existing approval policy. Returns observed post-state, not a promise that the user goal succeeded. Ambiguous/missing/changed targets do not execute.",
            J.obj("action",ToolArgs.choice("click","long_click","type","scroll","progress"),
                "expected_package",ToolArgs.text(240),"resource_id",ToolArgs.text(256),"label",ToolArgs.text(180),
                "input",ToolArgs.text(2000),"direction",ToolArgs.choice("up","down"),"value",J.obj("type","number")),
            "action","expected_package");
    }
    static void validate(JSONObject args) throws Exception {
        ToolArgs.validate("act_on_screen",args,J.arr(schema()));
        if(!args.getString("expected_package").matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))throw new IllegalArgumentException("정확한 앱 패키지가 필요합니다.");
        if(args.optString("resource_id").isEmpty()&&args.optString("label").isEmpty())throw new IllegalArgumentException("정확한 리소스 ID 또는 화면 라벨이 필요합니다.");
        for(String field:new String[]{"resource_id","label","input"})if(args.has(field)&&args.getString(field).indexOf('\0')>=0)throw new IllegalArgumentException("제어 문자를 포함한 대상은 지원하지 않습니다.");
        String action=args.getString("action");
        if("type".equals(action)!=args.has("input")||"scroll".equals(action)!=args.has("direction")||"progress".equals(action)!=args.has("value"))throw new IllegalArgumentException("입력·스크롤·슬라이더 인자는 해당 동작에만 사용하세요.");
        if(args.has("value")&&!Double.isFinite(args.getDouble("value")))throw new IllegalArgumentException("슬라이더 값은 유한한 수여야 합니다.");
    }
    static String nativeAction(String action){
        switch(action){case "click":return "click_element";case "long_click":return "long_click_element";case "type":return "type_text";case "scroll":return "scroll_element";case "progress":return "set_element_progress";default:throw new IllegalArgumentException("지원하지 않는 동작입니다.");}
    }
    static JSONArray matches(JSONObject args,JSONObject screen) throws Exception {
        validate(args);
        if(!args.getString("expected_package").equals(screen.optString("package")))throw new SecurityException("현재 앱이 요청한 앱과 다릅니다. 새 화면을 확인하세요.");
        if(screen.optString("snapshot").isEmpty())throw new IllegalStateException("새 화면의 식별자가 없습니다.");
        JSONArray elements=screen.getJSONArray("elements"),matches=new JSONArray();String action=args.getString("action");
        for(int i=0;i<elements.length();i++){
            JSONObject element=elements.getJSONObject(i);
            if(!element.optBoolean("enabled")||element.optString("id").isEmpty())continue;
            if(args.has("resource_id")&&!args.getString("resource_id").equals(element.optString("resourceId")))continue;
            if(args.has("label")&&!args.getString("label").equals(element.optString("text"))&&!args.getString("label").equals(element.optString("description")))continue;
            boolean actionable="click".equals(action)?element.optBoolean("clickable"):
                "long_click".equals(action)?element.optBoolean("longClickable"):
                "type".equals(action)?element.optBoolean("editable"):
                "scroll".equals(action)?element.optBoolean("scrollable"):element.optJSONObject("range")!=null;
            if(actionable)matches.put(element);
        }
        return matches;
    }
    static JSONObject arguments(JSONObject request,JSONObject screen,JSONObject target) throws Exception {
        JSONObject args=J.obj("snapshot",screen.getString("snapshot"),"element",target.getString("id"));
        switch(request.getString("action")){
            case "type":args.put("text",request.getString("input"));break;
            case "scroll":args.put("direction",request.getString("direction"));break;
            case "progress":args.put("value",request.getDouble("value"));break;
        }
        return args;
    }
}
