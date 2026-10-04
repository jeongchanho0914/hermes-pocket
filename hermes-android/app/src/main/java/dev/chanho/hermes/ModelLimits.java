package dev.chanho.hermes;

import org.json.*;
import java.util.Locale;

/** Model-specific token budgets. Unknown models remain explicitly unverified. */
final class ModelLimits {
    final long context,output;
    final boolean verified;
    final String source;
    ModelLimits(long context,long output,boolean verified,String source){this.context=context;this.output=output;this.verified=verified;this.source=source;}
    static long positive(JSONObject o,String...keys){if(o==null)return 0;for(String key:keys){Object value=o.opt(key);if(value instanceof Number){long n=((Number)value).longValue();if(n>=1024&&n<=100000000)return n;}}return 0;}
    static JSONObject metadata(JSONObject item) throws JSONException {
        long context=positive(item,"context_length","context_window","max_context_tokens","contextWindow","input_token_limit");
        long output=positive(item,"max_output_tokens","max_completion_tokens","output_token_limit");
        JSONObject top=item.optJSONObject("top_provider"),limits=item.optJSONObject("limits");
        if(context==0)context=positive(top,"context_length");if(context==0)context=positive(limits,"context","max_context_tokens","input_tokens");
        if(output==0)output=positive(top,"max_completion_tokens");if(output==0)output=positive(limits,"output","max_output_tokens","output_tokens");
        JSONObject result=new JSONObject();if(context>0)result.put("contextTokens",context);if(output>0)result.put("outputTokens",output);if(context>0||output>0)result.put("limitsSource","api");return result;
    }
    static ModelLimits resolve(JSONObject config){
        long context=positive(config,"modelContextTokens"),output=positive(config,"modelOutputTokens");
        String id=config.optString("model","").toLowerCase(Locale.ROOT);
        if(context>0)return new ModelLimits(context,output>0?Math.min(output,context/2):Math.min(8192,context/4),true,config.optString("modelLimitsSource","api"));
        // Official Xiaomi model specification, checked 2026-10-04. Decimal 1M/128K;
        // do not apply this promise to unrelated self-hosted deployments of the model.
        if(officialHost(config,"api.xiaomimimo.com")&&LocalCapabilities.isMiMo(config)&&id.equals("mimo-v2.6-flash"))return new ModelLimits(1000000,128000,true,"https://mimo.mi.com/models/en-US/mimo-v2.6-flash");
        if(officialHost(config,"api.openai.com")&&"openai-api".equals(config.optString("providerId"))){
            if(id.equals("gpt-5.2"))return new ModelLimits(400000,128000,true,"https://developers.openai.com/api/docs/models/gpt-5.2");
            if(id.equals("gpt-4.1"))return new ModelLimits(1047576,32768,true,"https://developers.openai.com/api/docs/models/gpt-4.1");
        }
        return new ModelLimits(128000,8192,false,"unverified-estimate");
    }
    private static boolean officialHost(JSONObject config,String expected){String endpoint=config.optString("endpoint","");if(endpoint.isEmpty())return true;try{return expected.equalsIgnoreCase(new java.net.URI(endpoint).getHost());}catch(Exception e){return false;}}
    int responseReserve(){return (int)Math.max(1024,Math.min(output,8192));}
    long inputBudget(){return Math.max(1024,context-responseReserve());}
    JSONObject json() throws JSONException{return J.obj("contextTokens",context,"outputTokens",output,"verified",verified,"source",source,"responseReserve",responseReserve());}
    // Conservative multilingual approximation, calibrated upward from real API usage.
    // Pixel payloads are budgeted separately, never tokenized as a base64 string.
    static long estimate(String text){long ascii=0,other=0;for(int i=0;i<text.length();){int cp=text.codePointAt(i);i+=Character.charCount(cp);if(cp<128)ascii++;else other++;}return Math.max(1,(ascii+2)/3+other*2);}
    static long estimate(JSONArray messages,JSONArray tools){long count=tools==null?0:estimate(tools.toString());for(int i=0;i<messages.length();i++){JSONObject msg=messages.optJSONObject(i);if(msg==null)continue;count+=12;Object content=msg.opt("content");if(content instanceof JSONArray){JSONArray a=(JSONArray)content;for(int j=0;j<a.length();j++){JSONObject p=a.optJSONObject(j);if(p!=null)count+="image_url".equals(p.optString("type"))?4096:estimate(p.optString("text"));}}else if(content instanceof String)count+=estimate((String)content);JSONArray calls=msg.optJSONArray("tool_calls");if(calls!=null)count+=estimate(calls.toString());String reasoning=msg.optString("reasoning_content","");if(!reasoning.isEmpty())count+=estimate(reasoning);}return count;}
}
