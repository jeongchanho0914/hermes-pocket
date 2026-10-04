package dev.chanho.hermes;

import org.json.*;

/** Native observe/select/approve/act/observe path on the existing single device lane. */
final class SemanticActions {
    private final AgentRuntime runtime;
    SemanticActions(AgentRuntime runtime){this.runtime=runtime;}
    JSONObject execute(JSONObject request) throws Exception {
        SemanticTarget.validate(request);runtime.checkCancelled();
        if(!runtime.busy()||!runtime.isUnlocked())throw new SecurityException("활성 사용자 요청과 잠금 해제가 필요합니다.");
        String action=SemanticTarget.nativeAction(request.getString("action"));
        for(String name:new String[]{"act_on_screen","read_screen",action})if(!LocalCapabilities.allowed(name,runtime.store.config()))throw new SecurityException("화면 관찰·조작 도구가 꺼져 있습니다.");
        // These are the same approved production tools used by normal agent calls.
        // No model/network round trip occurs between this observation and selection.
        JSONObject observed=runtime.tools.execute("read_screen",J.obj());
        if(!observed.optBoolean("ok"))return observed;
        runtime.checkCancelled();JSONObject screen=observed.getJSONObject("result");
        JSONArray matches=SemanticTarget.matches(request,screen);
        if(matches.length()!=1)return J.obj("ok",false,"errorCode",matches.length()==0?"TARGET_NOT_FOUND":"TARGET_AMBIGUOUS",
            "error","정확히 하나의 조작 가능한 요소가 필요합니다. 새 화면에서 리소스 ID와 라벨을 함께 지정하세요.",
            "matchedCount",matches.length(),"dispatched",false,"verified",false,"untrusted",true,"observation",screen);
        JSONObject target=matches.getJSONObject(0);
        runtime.checkCancelled();
        JSONObject result=runtime.tools.execute(action,SemanticTarget.arguments(request,screen,target));
        result.put("selection",J.obj("method","exact_match_on_fresh_native_observation","package",screen.optString("package"),
            "element",target.optString("id"),"resourceId",target.optString("resourceId"),"coordinateFallback",false));
        return result;
    }
}
