package dev.chanho.hermes;
import org.json.*;
import java.util.*;

/** Strict bounded validation shared by the Android runtime extensions. Not a permissive JSON coercer. */
final class ToolArgs {
    static JSONObject text(int max){return J.obj("type","string","maxLength",max);}
    static JSONObject integer(int min,int max){return J.obj("type","integer","minimum",min,"maximum",max);}
    static JSONObject choice(String... values){return J.obj("type","string","enum",new JSONArray(Arrays.asList(values)));}
    static JSONObject object(JSONObject properties,JSONArray required){return J.obj("type","object","properties",properties,"required",required,"additionalProperties",false);}
    static JSONObject schema(String name,String description,JSONObject properties,String... required){return J.obj("type","function","function",J.obj("name",name,"description",description,"parameters",object(properties,new JSONArray(Arrays.asList(required)))));}
    static void validate(String name,JSONObject args,JSONArray schemas) throws Exception {
        for(int i=0;i<schemas.length();i++){JSONObject f=schemas.getJSONObject(i).getJSONObject("function");if(name.equals(f.getString("name"))){validateValue(args,f.getJSONObject("parameters"),0);return;}}
        throw new IllegalArgumentException("등록되지 않은 도구입니다.");
    }
    private static void validateValue(Object value,JSONObject schema,int depth) throws Exception {
        if(depth>16||value==null||value==JSONObject.NULL)throw new IllegalArgumentException("도구 인자 구조가 올바르지 않습니다.");
        String type=schema.optString("type");
        if("object".equals(type)){
            if(!(value instanceof JSONObject))throw new IllegalArgumentException("객체 인자가 필요합니다.");
            JSONObject o=(JSONObject)value,props=schema.optJSONObject("properties");JSONArray req=schema.optJSONArray("required");
            if(o.length()>128)throw new IllegalArgumentException("인자 필드가 너무 많습니다.");
            if(req!=null)for(int i=0;i<req.length();i++)if(!o.has(req.getString(i)))throw new IllegalArgumentException("필수 인자가 없습니다: "+req.getString(i));
            Iterator<String> keys=o.keys();while(keys.hasNext()){String key=keys.next();if(props==null||!props.has(key)){if(!schema.optBoolean("additionalProperties",false))throw new IllegalArgumentException("허용되지 않는 인자: "+key);}else validateValue(o.get(key),props.getJSONObject(key),depth+1);}
        } else if("string".equals(type)){
            if(!(value instanceof String))throw new IllegalArgumentException("문자 인자가 필요합니다.");String s=(String)value;
            if(s.length()<schema.optInt("minLength",0)||s.length()>schema.optInt("maxLength",100000)||s.indexOf('\u0000')>=0)throw new IllegalArgumentException("문자 인자의 길이 또는 내용이 올바르지 않습니다.");
        } else if("integer".equals(type)||"number".equals(type)){
            if(!(value instanceof Number))throw new IllegalArgumentException("숫자 인자가 필요합니다.");double n=((Number)value).doubleValue();
            if(!Double.isFinite(n)||("integer".equals(type)&&n!=Math.rint(n))||n<schema.optDouble("minimum",-Double.MAX_VALUE)||n>schema.optDouble("maximum",Double.MAX_VALUE))throw new IllegalArgumentException("숫자 인자의 범위가 올바르지 않습니다.");
        } else if("boolean".equals(type)){if(!(value instanceof Boolean))throw new IllegalArgumentException("불리언 인자가 필요합니다.");}
        else if("array".equals(type)){
            if(!(value instanceof JSONArray))throw new IllegalArgumentException("배열 인자가 필요합니다.");JSONArray a=(JSONArray)value;
            if(a.length()<schema.optInt("minItems",0)||a.length()>schema.optInt("maxItems",64))throw new IllegalArgumentException("배열 길이가 올바르지 않습니다.");
            JSONObject item=schema.optJSONObject("items");if(item!=null)for(int i=0;i<a.length();i++)validateValue(a.get(i),item,depth+1);
        }
        JSONArray choices=schema.optJSONArray("enum");if(choices!=null){boolean found=false;for(int i=0;i<choices.length();i++)if(value.equals(choices.get(i)))found=true;if(!found)throw new IllegalArgumentException("지원하지 않는 인자 값입니다.");}
    }
}
