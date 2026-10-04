package dev.chanho.hermes;

import org.json.*;
import java.util.*;

/** Phone-local instruction presets and tool modules. No remote plugin loader. */
final class LocalCapabilities {
    private static final String[] SKILLS={"general","write","plan","code"};
    private static final String[] PLUGINS={"device","screen","root","agent","web","files","terminal"};
    static JSONArray defaultPlugins(){return J.arr("device","screen","root","agent","web","terminal");}
    static String skill(String id){for(String item:SKILLS)if(item.equals(id))return id;throw new IllegalArgumentException("지원하지 않는 스킬입니다.");}
    static String effort(String id){for(String item:new String[]{"auto","none","minimal","low","medium","high","xhigh","max","ultra"})if(item.equals(id))return id;throw new IllegalArgumentException("Hermes의 생각 수준에서 선택하세요: none, minimal, low, medium, high, xhigh, max, ultra 또는 자동.");}
    static JSONArray plugins(JSONArray values) throws JSONException {
        if(values==null)throw new IllegalArgumentException("플러그인 목록이 올바르지 않습니다.");
        Set<String> selected=new HashSet<>();
        for(int i=0;i<values.length();i++){
            String id=values.getString(i);boolean known=false;for(String item:PLUGINS)if(item.equals(id))known=true;
            if(!known)throw new IllegalArgumentException("지원하지 않는 플러그인입니다.");selected.add(id);
        }
        JSONArray result=new JSONArray();for(String id:PLUGINS)if(selected.contains(id))result.put(id);return result;
    }
    static String instructions(String id){
        switch(skill(id)){
            case "write":return "Active local skill: Writing. Clarify audience and purpose when essential. Draft clear, natural prose, preserve facts, and label unknowns. Revise into a useful final artifact without inventing quotations or sources.";
            case "plan":return "Active local skill: Planning. Turn the user's objective into concrete steps, dependencies, and a realistic priority order. Ask only for essential missing constraints. Distinguish estimates from known facts and show a small actionable next step.";
            case "code":return "Active local skill: Coding. Explain and produce code with clear assumptions. Inspect only resources exposed by available tools. Never claim code was run or tested without a matching tool result. Identify meaningful checks and give usable code or debugging steps.";
            default:return "Active local skill: General assistance. Answer directly and concisely. Use available device tools only when the user requests a device task. Ask for essential missing information, and distinguish facts from assumptions.";
        }
    }
    static String pluginFor(String tool){
        if("act_on_screen".equals(tool))return "screen";
        if(ExtensionSchemas.handles(tool))return "agent";
        if(PhoneIntentTools.handles(tool))return "device";
        if(TerminalTools.handles(tool))return "terminal";
        if(PhoneFiles.handles(tool))return "files";
        if(BrowserSearch.handles(tool)||WebTools.handles(tool))return "web";
        if(LocalAgentTools.handles(tool))return "agent";
        if(Arrays.asList("get_device_state","list_apps","launch_app","open_settings","perform_phone_action","open_link","open_map","share_text","compose_message","set_alarm","set_volume","set_brightness","get_phone_settings","set_phone_setting","privileged_status").contains(tool))return "device";
        if(Arrays.asList("read_screen","capture_screen","click_element","long_click_element","set_element_progress","type_text","scroll_element","press_back","press_home","tap_screen","swipe_screen").contains(tool))return "screen";
        if(Arrays.asList("root_processes","set_wifi","force_stop_app","probe_root").contains(tool))return "root";
        return "";
    }
    static String auditToolName(String name){
        String safe=J.clipped(name.replaceAll("[^a-zA-Z0-9_.-]","?"),80);
        if(safe.startsWith("sk-")||safe.startsWith("tp-")||safe.startsWith("tvly-"))return "unregistered_tool";
        return safe.isEmpty()?"unregistered_tool":safe;
    }
    static boolean allowed(String tool,JSONObject config){
        String plugin=pluginFor(tool);if(plugin.isEmpty())return false;
        JSONArray enabled=config.optJSONArray("enabledPlugins");if(enabled==null)enabled=defaultPlugins();
        for(int i=0;i<enabled.length();i++)if(plugin.equals(enabled.optString(i)))return true;return false;
    }
    static JSONArray schemas(JSONObject config) throws JSONException {
        JSONArray all=DeviceTools.schemas(),result=new JSONArray();
        JSONArray web=WebTools.schemas(),agent=LocalAgentTools.schemas(),files=PhoneFiles.schemas(),terminal=TerminalTools.schemas(),intents=PhoneIntentTools.schemas(),browser=BrowserSearch.schemas();
        for(int i=0;i<intents.length();i++)all.put(intents.get(i));
        for(int i=0;i<browser.length();i++)all.put(browser.get(i));
        for(int i=0;i<terminal.length();i++)all.put(terminal.get(i));
        for(int i=0;i<files.length();i++)all.put(files.get(i));
        for(int i=0;i<web.length();i++)all.put(web.get(i));
        for(int i=0;i<agent.length();i++)all.put(agent.get(i));
        JSONArray extra=ExtensionSchemas.all();for(int i=0;i<extra.length();i++)all.put(extra.get(i));
        for(int i=0;i<all.length();i++){JSONObject schema=all.getJSONObject(i);if(allowed(schema.getJSONObject("function").getString("name"),config))result.put(schema);}
        return result;
    }
    static JSONObject inventory(JSONObject config){
        try{
            JSONArray tools=schemas(config),names=new JSONArray();
            for(int i=0;i<tools.length();i++)names.put(tools.getJSONObject(i).getJSONObject("function").getString("name"));
            return J.obj("count",names.length(),"names",names);
        }catch(JSONException e){throw new IllegalStateException("등록된 기기 도구 목록을 읽지 못했습니다.",e);}
    }
    static boolean isMiMo(JSONObject config){
        if("xiaomi".equals(config.optString("providerId")))return true;
        try{String host=new java.net.URI(config.optString("endpoint")).getHost();return host!=null&&(host.equalsIgnoreCase("xiaomimimo.com")||host.toLowerCase(java.util.Locale.ROOT).endsWith(".xiaomimimo.com"));}
        catch(Exception ignored){return false;}
    }
    static boolean replaysReasoning(JSONObject config){return "deepseek".equals(config.optString("providerId"))||isMiMo(config);}
    static JSONObject normalizeModels(JSONObject response) throws JSONException {
        JSONArray data=response.optJSONArray("data");
        if(data==null)throw new IllegalStateException("API가 올바른 모델 목록을 반환하지 않았습니다. 모델 ID를 직접 입력할 수 있습니다.");
        JSONArray models=new JSONArray();java.util.HashSet<String> seen=new java.util.HashSet<>();
        for(int i=0;i<data.length()&&models.length()<2000;i++){
            JSONObject item=data.optJSONObject(i);if(item==null||item.isNull("id"))continue;String id=item.optString("id","").trim();
            if(id.isEmpty()||id.length()>200||!seen.add(id))continue;
            JSONArray supported=item.optJSONArray("supported_parameters");boolean reasoning=false;
            if(supported!=null)for(int j=0;j<supported.length();j++)if("reasoning".equals(supported.optString(j))||"reasoning_effort".equals(supported.optString(j)))reasoning=true;
            JSONObject capabilities=item.optJSONObject("capabilities");if(capabilities!=null&&capabilities.optBoolean("reasoning",false))reasoning=true;
            String name=item.isNull("name")?id:item.optString("name",id).trim();if(name.isEmpty())name=id;
            JSONObject normalized=J.obj("id",id,"name",J.clipped(name,200),"reasoningSupported",reasoning,"reasoningSupportSource",reasoning?"api":"unknown");
            JSONObject limits=ModelLimits.metadata(item);java.util.Iterator<String> keys=limits.keys();while(keys.hasNext()){String key=keys.next();normalized.put(key,limits.get(key));}models.put(normalized);
        }
        return J.obj("models",models,"count",models.length(),"truncated",data.length()>2000);
    }
    // Hermes internal ladder includes ultra; its wire ladder ends at max.
    // See upstream agent/reasoning_effort.py EFFORT_LADDER and DEEPSEEK_V4_OVERRIDES.
    static String effectiveEffort(String selected,String provider){
        selected=effort(selected);
        if("auto".equals(selected)||"none".equals(selected))return selected;
        if("xiaomi".equals(provider))return "enabled";
        if("gemini".equals(provider)&&Arrays.asList("xhigh","max","ultra").contains(selected))return "high";
        if("deepseek".equals(provider)){
            if("minimal".equals(selected))return "low";
            if("xhigh".equals(selected)||"ultra".equals(selected))return "max";
        }
        return "ultra".equals(selected)?"max":selected;
    }
    static String effectiveEffort(JSONObject config){return effectiveEffort(config.optString("reasoningEffort","auto"),isMiMo(config)?"xiaomi":config.optString("providerId"));}
    static void configureReasoning(JSONObject request,JSONObject config) throws JSONException {
        String selected=effectiveEffort(config);if("auto".equals(selected))return;
        if(isMiMo(config)){
            request.put("thinking",J.obj("type","none".equals(selected)?"disabled":"enabled"));
        }else if("deepseek".equals(config.optString("providerId"))){
            request.put("thinking",J.obj("type","none".equals(selected)?"disabled":"enabled"));
            if(!"none".equals(selected))request.put("reasoning_effort",selected);
        }else request.put("reasoning_effort",selected);
    }
    static JSONArray wireHistory(JSONArray history,JSONObject config) throws JSONException {
        JSONArray wire=new JSONArray(history.toString());boolean echo=replaysReasoning(config);
        for(int i=0;i<wire.length();i++){
            JSONObject message=wire.getJSONObject(i);
            if(echo&&"assistant".equals(message.optString("role"))){if(!message.has("reasoning_content"))message.put("reasoning_content","");}
            else message.remove("reasoning_content");
        }
        return wire;
    }
    static JSONArray providers(){return J.arr(
        provider("openrouter","OpenRouter","https://openrouter.ai/api/v1","Claude·GPT·Gemini 등 여러 제공자의 모델을 하나의 API 키로 사용합니다."),
        provider("openai-api","OpenAI API","https://api.openai.com/v1","OpenAI의 Chat Completions 지원 모델 · ChatGPT 구독과 별도 API 키"),
        provider("gemini","Google Gemini","https://generativelanguage.googleapis.com/v1beta/openai","Google AI Studio API 키 · 공식 OpenAI 호환 API"),
        provider("deepseek","DeepSeek","https://api.deepseek.com/v1","DeepSeek API 키 · 모델에 따라 생각 수준 지원이 다릅니다."),
        provider("groq","Groq","https://api.groq.com/openai/v1","Groq API 키 · 계정에서 제공되는 모델 목록을 불러옵니다."),
        provider("nvidia","NVIDIA NIM","https://integrate.api.nvidia.com/v1","NVIDIA API 키 · 계정에서 제공되는 모델 목록을 불러옵니다."),
        provider("zai","Z.AI / GLM","https://api.z.ai/api/paas/v4","Z.AI API 키 · GLM 모델 API"),
        provider("kimi-coding","Kimi / Moonshot","https://api.moonshot.ai/v1","Moonshot 플랫폼 API 키 · Kimi Coding 전용 키는 별도 연결이 필요합니다."),
        provider("kimi-coding-cn","Kimi / Moonshot 중국","https://api.moonshot.cn/v1","Moonshot 중국 플랫폼 API 키"),
        provider("alibaba","Qwen Cloud","https://dashscope-intl.aliyuncs.com/compatible-mode/v1","Alibaba Model Studio 국제 API 키 · OpenAI 호환 연결"),
        provider("arcee","Arcee AI","https://api.arcee.ai/api/v1","Arcee API 키 · 계정에서 제공되는 모델을 사용합니다."),
        provider("gmi","GMI Cloud","https://api.gmi-serving.com/v1","GMI API 키 · OpenAI 호환 모델 API"),
        provider("huggingface","Hugging Face","https://router.huggingface.co/v1","Hugging Face 추론 권한이 있는 토큰 · 제공되는 모델 목록"),
        provider("xiaomi","Xiaomi MiMo","https://api.xiaomimimo.com/v1","Xiaomi MiMo API 키 · OpenAI 호환 모델 API"),
        J.obj("id","custom","name","직접 연결","endpoint","","requiresKey",false,"apiMode","chat_completions","description","OpenAI 호환 모델 API 주소를 직접 입력합니다."));}
    private static JSONObject provider(String id,String name,String endpoint,String description){return J.obj("id",id,"name",name,"endpoint",endpoint,"requiresKey",true,"apiMode","chat_completions","description",description);}
    static String providerId(String id){JSONArray catalog=providers();for(int i=0;i<catalog.length();i++)if(id.equals(catalog.optJSONObject(i).optString("id")))return id;throw new IllegalArgumentException("지원하지 않는 제공자입니다. OpenAI 호환 API는 직접 연결을 선택하세요.");}
    static String providerEndpoint(String id){providerId(id);JSONArray catalog=providers();for(int i=0;i<catalog.length();i++)if(id.equals(catalog.optJSONObject(i).optString("id")))return catalog.optJSONObject(i).optString("endpoint");return "";}
    static String inferProvider(String endpoint){
        String normalized=endpoint.trim().replaceAll("/+$","");if(normalized.isEmpty())return "openrouter";
        JSONArray catalog=providers();for(int i=0;i<catalog.length();i++){JSONObject p=catalog.optJSONObject(i);if(!p.optString("endpoint").isEmpty()&&normalized.equals(p.optString("endpoint")))return p.optString("id");}
        return "custom";
    }
    static JSONObject catalog(){return J.obj("providers",providers(),"skills",J.arr(
        J.obj("id","general","name","일반","description","질문에 답하고 필요한 기기 작업을 도와줍니다."),
        J.obj("id","write","name","글쓰기","description","초안 작성, 문장 다듬기, 요약을 돕습니다."),
        J.obj("id","plan","name","계획","description","목표를 실행 가능한 순서와 단계로 정리합니다."),
        J.obj("id","code","name","코딩","description","코드 작성과 오류 분석을 돕습니다.")),
        "plugins",J.arr(
        J.obj("id","device","name","기기 제어","description","앱 실행, 설정, 볼륨과 밝기 · 동작마다 승인","tools",J.arr("get_device_state","list_apps","launch_app","open_settings","perform_phone_action","open_link","open_map","share_text","compose_message","set_alarm","set_volume","set_brightness","get_phone_settings","set_phone_setting","privileged_status")),
        J.obj("id","screen","name","화면 도우미","description","허용한 앱의 화면 읽기·탭·입력 · 접근성 및 승인 필요","tools",J.arr("read_screen","capture_screen","click_element","long_click_element","set_element_progress","type_text","scroll_element","press_back","press_home","tap_screen","swipe_screen")),
        J.obj("id","root","name","Root·Shizuku 도구","description","실제 Root 또는 Shizuku로 프로세스·Wi-Fi·앱 종료 · 소유자 승인 방식 적용","tools",J.arr("root_processes","set_wifi","force_stop_app","probe_root")),
        J.obj("id","agent","name","에이전트 도구","description","영구 메모리·이전 대화 검색·실제 SKILL.md 관리 · 변경은 승인 필요","tools",J.arr("memory","session_search","skills_list","skill_view","skill_manage")),
        J.obj("id","web","name","웹 검색","description","기본 브라우저 검색·페이지 열기·실제 화면 읽기 · API 방식은 명시적 선택","tools",J.arr("web_search","web_fetch","browser_search","browser_open","browser_snapshot")),
        J.obj("id","terminal","name","터미널·프로세스","description","Android 실제 셸 실행·작업 관리 · 앱 UID, 실제 Shizuku 또는 Root 권한 사용","tools",J.arr("terminal","process_manage")),
        J.obj("id","files","name","휴대폰 파일","description","직접 선택한 폴더의 파일 목록·UTF-8 읽기·승인 후 쓰기","tools",J.arr("phone_list_files","phone_read_file","phone_write_file"))),
        "webProviders",J.arr(J.obj("id","mwmbl","name","Mwmbl","endpoint","https://api.mwmbl.org","requiresKey",false,"description","무료 검색 API · 검색 범위와 최신성은 제한될 수 있습니다."),J.obj("id","tavily","name","Tavily","endpoint",WebTools.ENDPOINT,"requiresKey",true),J.obj("id","searxng","name","SearXNG","endpoint","","requiresKey",false,"requiresEndpoint",true,"description","사용자 지정 HTTPS JSON API 서버가 필요합니다.")),
        "reasoningEfforts",J.arr("auto","none","minimal","low","medium","high","xhigh","max","ultra"));}
}
