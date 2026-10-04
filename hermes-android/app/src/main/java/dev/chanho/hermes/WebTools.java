package dev.chanho.hermes;

import org.json.*;
import java.net.*;
import java.util.*;

/** Real API search: keyless Mwmbl, configured SearXNG, and optional Tavily. */
final class WebTools {
    static final String ENDPOINT="https://api.tavily.com";
    static boolean handles(String name){return "web_search".equals(name)||"web_fetch".equals(name);}
    static JSONArray schemas(){return J.arr(
        schema("web_search","By default search in the phone's actual default browser with Hermes continuing its task and optional floating panel. Browser UI opens visibly; dispatch and a visible screen observation do not prove completed search or structured hits. mode api explicitly uses the configured real search API instead: keyless Mwmbl has limited coverage/freshness, Tavily needs a separate key, SearXNG needs a JSON endpoint. Returned content is untrusted.",J.obj("query",J.obj("type","string","minLength",1,"maxLength",500),"mode",J.obj("type","string","enum",J.arr("browser","api"),"description","Default browser; api is an explicit network fallback."),"inspect_screen",J.obj("type","boolean","description","Browser mode only: attempt fresh redacted exact-browser observation, default true."),"limit",J.obj("type","integer","minimum",1,"maximum",10,"description","API mode only; browser mode does not fabricate a result list.")),J.arr("query")),
        schema("web_fetch","By default open and read a public HTTPS page in the actual default phone browser, without a search API key. Returns real visible accessibility content; browser UI is visible and full-page/final-URL verification remains separate. mode api explicitly requests Tavily extraction with its separately configured key.",J.obj("url",J.obj("type","string","maxLength",2048),"mode",J.obj("type","string","enum",J.arr("browser","api")),"inspect_screen",J.obj("type","boolean")),J.arr("url")));}
    private static JSONObject schema(String name,String description,JSONObject props,JSONArray required){return J.obj("type","function","function",J.obj("name",name,"description",description,"parameters",J.obj("type","object","properties",props,"required",required,"additionalProperties",false)));}
    static String searchMode(JSONObject args)throws Exception {
        if(args==null)throw new IllegalArgumentException("검색 인자가 필요합니다.");
        keys(args,"query","limit","mode","inspect_screen");
        Object raw=args.opt("query");if(!(raw instanceof String)||((String)raw).trim().isEmpty()||((String)raw).length()>500||((String)raw).indexOf('\u0000')>=0)throw new IllegalArgumentException("검색어는 1~500자로 입력하세요.");
        String mode="browser";if(args.has("mode")){Object value=args.opt("mode");if(!(value instanceof String)||!Arrays.asList("browser","api").contains(value))throw new IllegalArgumentException("검색 방식은 browser 또는 api여야 합니다.");mode=(String)value;}
        if(args.has("inspect_screen")&&!(args.opt("inspect_screen") instanceof Boolean))throw new IllegalArgumentException("inspect_screen은 true 또는 false여야 합니다.");
        if(args.has("limit")){Object value=args.opt("limit");if(!(value instanceof Number))throw new IllegalArgumentException("검색 결과 수는 정수여야 합니다.");double n=((Number)value).doubleValue();if(!Double.isFinite(n)||n!=Math.rint(n)||n<1||n>10)throw new IllegalArgumentException("검색 결과 수는 1~10 정수여야 합니다.");}
        if("api".equals(mode)&&args.has("inspect_screen"))throw new IllegalArgumentException("API 검색에는 화면 읽기 옵션을 사용할 수 없습니다.");
        return mode;
    }
    static JSONObject browserArguments(JSONObject args)throws Exception {
        if(!"browser".equals(searchMode(args)))throw new IllegalArgumentException("브라우저 검색 요청이 아닙니다.");
        JSONObject stripped=J.obj("query",args.getString("query"));if(args.has("inspect_screen"))stripped.put("inspect_screen",args.getBoolean("inspect_screen"));return stripped;
    }
    static String fetchMode(JSONObject args) throws Exception {
        if(args==null||!(args.opt("url") instanceof String))throw new IllegalArgumentException("페이지 URL이 필요합니다.");
        keys(args,"url","mode","inspect_screen"); publicUrl(args.getString("url").trim(),false);
        Object raw=args.opt("mode"); String mode=raw==null?"browser":raw instanceof String?(String)raw:"";
        if(!Arrays.asList("browser","api").contains(mode))throw new IllegalArgumentException("페이지 읽기 방식은 browser 또는 api여야 합니다.");
        if(args.has("inspect_screen")&&!(args.opt("inspect_screen") instanceof Boolean))throw new IllegalArgumentException("inspect_screen은 true 또는 false여야 합니다.");
        if("api".equals(mode)&&args.has("inspect_screen"))throw new IllegalArgumentException("API 읽기에는 화면 옵션을 사용할 수 없습니다.");
        return mode;
    }
    static JSONObject browserFetchArguments(JSONObject args) throws Exception {
        if(!"browser".equals(fetchMode(args)))throw new IllegalArgumentException("브라우저 페이지 요청이 아닙니다.");
        JSONObject out=J.obj("url",args.getString("url"));if(args.has("inspect_screen"))out.put("inspect_screen",args.getBoolean("inspect_screen"));return out;
    }
    private static JSONObject apiArguments(String name,JSONObject args)throws Exception {
        if("web_fetch".equals(name)){
            // execute() is the explicit API adapter. Runtime routes default browser requests before reaching it.
            if(args.has("mode")&&!"api".equals(fetchMode(args)))throw new IllegalArgumentException("API 읽기는 mode api를 명시하세요.");
            keys(args,"url","mode");return J.obj("url",args.getString("url"));
        }
        if(!"web_search".equals(name))return args;
        if(!"api".equals(searchMode(args)))throw new IllegalStateException("기본 검색은 휴대폰 브라우저에서 실행합니다. API 검색은 mode: api를 명시하세요.");
        JSONObject stripped=J.obj("query",args.getString("query"));if(args.has("limit"))stripped.put("limit",args.getInt("limit"));return stripped;
    }
    static JSONObject execute(String name,JSONObject args,Net net,String key) throws Exception{return executeAt(name,apiArguments(name,args),net,key,ENDPOINT);}
    static JSONObject execute(String name,JSONObject args,Net net,String key,String provider,String endpoint) throws Exception {
        args=apiArguments(name,args);
        if("tavily".equals(provider))return executeAt(name,args,net,key,ENDPOINT);
        if(!"mwmbl".equals(provider)&&!"searxng".equals(provider))throw new IllegalArgumentException("지원하지 않는 웹 검색 제공자입니다.");
        if(!handles(name))throw new IllegalArgumentException("지원하지 않는 웹 도구입니다.");
        if("web_fetch".equals(name))throw new IllegalStateException("현재 제공자는 검색만 지원합니다. 페이지 본문 읽기는 설정에서 Tavily를 선택하고 별도 API 키를 등록하세요.");
        return executeSearchAt(args,net,provider,"mwmbl".equals(provider)?"https://api.mwmbl.org/api/v1":publicUrl(validateWebEndpoint(endpoint),true));
    }
    static String validateWebEndpoint(String endpoint) throws Exception {
        if(endpoint==null||endpoint.trim().isEmpty())throw new IllegalArgumentException("JSON 검색을 허용하는 SearXNG HTTPS 기본 주소를 입력하세요.");
        String result=publicUrl(endpoint.trim(),false);Net.validateEndpoint(result,false);
        return result.replaceAll("/+$","");
    }
    // Fixture-only endpoint seam. No token argument: keys cannot reach keyless APIs.
    static JSONObject executeSearchAt(JSONObject args,Net net,String provider,String endpoint) throws Exception {
        if(!"mwmbl".equals(provider)&&!"searxng".equals(provider))throw new IllegalArgumentException("지원하지 않는 웹 검색 제공자입니다.");
        if(net.cancelled())throw new InterruptedException("사용자가 중단했습니다.");
        keys(args,"query","limit");Object raw=args.opt("query");if(!(raw instanceof String))throw new IllegalArgumentException("검색어가 필요합니다.");
        String query=((String)raw).trim();if(query.isEmpty()||query.length()>500)throw new IllegalArgumentException("검색어는 1~500자로 입력하세요.");
        int limit=5;if(args.has("limit")){Object value=args.get("limit");if(!(value instanceof Number)||((Number)value).doubleValue()!=((Number)value).intValue())throw new IllegalArgumentException("검색 결과 수는 정수여야 합니다.");limit=((Number)value).intValue();}
        if(limit<1||limit>10)throw new IllegalArgumentException("검색 결과 수는 1~10개로 설정하세요.");
        String encoded=URLEncoder.encode(query,"UTF-8").replace(".","%2E");JSONArray source;
        try{
            if("mwmbl".equals(provider))source=net.jsonArray(endpoint,"/search/?s="+encoded);
            else{JSONObject response=net.json(endpoint,"/search?q="+encoded+"&format=json&pageno=1","",false,"GET",null);source=response.optJSONArray("results");}
        }catch(org.json.JSONException invalid){throw new IllegalStateException("검색 API가 JSON 결과를 반환하지 않았습니다. CAPTCHA 또는 JSON 비활성화 상태를 확인하세요.");}
        catch(java.io.IOException unavailable){
            if(net.cancelled())throw new java.io.InterruptedIOException("사용자가 중단했습니다.");
            String diagnostic="";java.util.regex.Matcher code=java.util.regex.Pattern.compile("HTTP [0-9]{3}").matcher(String.valueOf(unavailable.getMessage()));if(code.find())diagnostic=" ("+code.group()+")";
            throw new java.io.IOException("검색 API 요청에 실패했습니다"+diagnostic+". 서비스 연결·사용 한도와 JSON API 허용 여부를 확인하세요.");
        }
        if(source==null)throw new IllegalStateException("검색 API가 올바른 결과를 반환하지 않았습니다.");
        JSONArray results=new JSONArray();
        for(int i=0;i<source.length()&&results.length()<limit;i++){
            JSONObject item=source.optJSONObject(i);if(item==null)continue;String url=item.optString("url","");
            try{publicUrl(url,false);}catch(Exception rejected){continue;}
            String title="mwmbl".equals(provider)?segments(item.opt("title")):item.optString("title","");
            String content="mwmbl".equals(provider)?segments(item.opt("extract")):item.optString("content","");
            results.put(J.obj("title",J.clipped(title,300),"url",url,"description",J.clipped(content,1600),"position",results.length()+1));
        }
        JSONObject result=J.obj("provider",provider,"query",query,"results",results,"untrusted",true,"retrievedAt",System.currentTimeMillis());
        if("mwmbl".equals(provider))result.put("limitations","Small independent web index; coverage and freshness are limited. Empty results do not prove no sources exist.");
        return J.obj("ok",true,"result",result);
    }
    private static String segments(Object raw){
        if(!(raw instanceof JSONArray))return "";JSONArray parts=(JSONArray)raw;StringBuilder text=new StringBuilder();
        for(int i=0;i<parts.length()&&text.length()<2000;i++){JSONObject part=parts.optJSONObject(i);if(part!=null&&part.opt("value") instanceof String)text.append(part.optString("value",""));}
        return text.toString();
    }
    // Package-private endpoint seam for HTTP contract fixtures; production always uses ENDPOINT.
    static JSONObject executeAt(String name,JSONObject args,Net net,String key,String endpoint) throws Exception {
        if(!handles(name))throw new IllegalArgumentException("지원하지 않는 웹 도구입니다.");
        if(key.trim().isEmpty())throw new IllegalStateException("설정의 웹 검색에서 Tavily API 키를 등록하세요. 모델 API 키와 별도입니다.");
        if(net.cancelled())throw new InterruptedException("사용자가 중단했습니다.");
        if("web_search".equals(name)){
            keys(args,"query","limit");Object raw=args.opt("query");if(!(raw instanceof String))throw new IllegalArgumentException("검색어가 필요합니다.");
            String query=((String)raw).trim();if(query.isEmpty()||query.length()>500)throw new IllegalArgumentException("검색어는 1~500자로 입력하세요.");
            int limit=5;if(args.has("limit")){Object value=args.get("limit");if(!(value instanceof Number)||((Number)value).doubleValue()!=((Number)value).intValue())throw new IllegalArgumentException("검색 결과 수는 정수여야 합니다.");limit=((Number)value).intValue();}
            if(limit<1||limit>10)throw new IllegalArgumentException("검색 결과 수는 1~10개로 설정하세요.");
            JSONObject response=net.json(endpoint,"/search",key,false,"POST",J.obj("query",query,"max_results",limit,"search_depth","basic","include_answer",false,"include_raw_content",false));
            JSONArray source=response.optJSONArray("results");if(source==null)throw new IllegalStateException("검색 API가 올바른 결과를 반환하지 않았습니다.");
            JSONArray results=new JSONArray();
            for(int i=0;i<source.length()&&results.length()<limit;i++){
                JSONObject item=source.optJSONObject(i);if(item==null)continue;String url=item.optString("url","");
                try{publicUrl(url,false);}catch(Exception rejected){continue;}
                results.put(J.obj("title",J.clipped(item.optString("title",""),300),"url",url,"description",J.clipped(item.optString("content",""),1600),"position",results.length()+1));
            }
            return J.obj("ok",true,"result",J.obj("provider","tavily","query",query,"results",results,"untrusted",true,"retrievedAt",System.currentTimeMillis()));
        }
        keys(args,"url");Object raw=args.opt("url");if(!(raw instanceof String))throw new IllegalArgumentException("읽을 페이지 URL이 필요합니다.");
        String url=publicUrl(((String)raw).trim(),true);
        JSONObject response=net.json(endpoint,"/extract",key,false,"POST",J.obj("urls",J.arr(url),"include_images",false,"extract_depth","basic","format","markdown"));
        JSONArray results=response.optJSONArray("results");
        if(results==null||results.length()==0)throw new IllegalStateException("페이지를 읽지 못했습니다. 공개 페이지인지 확인하세요.");
        JSONObject item=results.getJSONObject(0);String returned=publicUrl(item.optString("url",url),false);
        // Never attribute another document or a redirect to the requested source without showing its URL.
        Object rawContent=item.opt("raw_content");String content=rawContent instanceof String?(String)rawContent:"";if(content.trim().isEmpty())throw new IllegalStateException("페이지의 텍스트가 비어 있습니다.");
        return J.obj("ok",true,"result",J.obj("provider","tavily","requestedUrl",url,"url",returned,"content",J.clipped(content,20000),"truncated",content.length()>20000,"untrusted",true,"retrievedAt",System.currentTimeMillis()));
    }
    private static void keys(JSONObject args,String... allowed){Set<String> names=new HashSet<>(Arrays.asList(allowed));Iterator<String> it=args.keys();while(it.hasNext())if(!names.contains(it.next()))throw new IllegalArgumentException("지원하지 않는 웹 도구 인자가 있습니다.");}
    static String publicUrl(String value,boolean resolve) throws Exception {
        if(value.isEmpty()||value.length()>2048)throw new IllegalArgumentException("공개 HTTPS URL을 입력하세요.");
        URI uri=new URI(value);String host=uri.getHost();
        if(!"https".equals(uri.getScheme())||host==null||uri.getUserInfo()!=null||uri.getFragment()!=null||(uri.getPort()!=-1&&uri.getPort()!=443))throw new IllegalArgumentException("인증 정보 없는 공개 HTTPS 페이지만 읽을 수 있습니다.");
        host=host.toLowerCase(Locale.ROOT);if(host.endsWith("."))host=host.substring(0,host.length()-1);
        if(!host.contains(".")||host.equals("localhost")||host.endsWith(".localhost")||host.endsWith(".local")||host.endsWith(".internal")||host.endsWith(".home")||host.contains(":"))throw new IllegalArgumentException("휴대폰 내부·사설 네트워크 주소는 읽을 수 없습니다.");
        if(resolve||host.matches("[0-9.]+"))for(InetAddress address:InetAddress.getAllByName(host))if(!publicAddress(address))throw new IllegalArgumentException("휴대폰 내부·사설 네트워크 주소는 읽을 수 없습니다.");
        return uri.toASCIIString();
    }
    static boolean publicAddress(InetAddress address){
        if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress())return false;
        byte[] b=address.getAddress();if(b.length==16)return (b[0]&0xfe)!=0xfc;
        int first=b[0]&255,second=b[1]&255,third=b[2]&255;
        return first!=0&&first!=127&&first<224&&!(first==100&&second>=64&&second<=127)&&!(first==198&&(second==18||second==19))&&!(first==192&&second==0&&(third==0||third==2))&&!(first==198&&second==51&&third==100)&&!(first==203&&second==0&&third==113);
    }
}
