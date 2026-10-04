package dev.chanho.hermes;

import org.json.JSONArray;
import org.json.JSONObject;

final class J {
    private J() {}
    static JSONObject obj(Object... pairs) {
        JSONObject out = new JSONObject();
        try { for (int i=0;i<pairs.length;i+=2) out.put(String.valueOf(pairs[i]), pairs[i+1]==null?JSONObject.NULL:pairs[i+1]); }
        catch (Exception e) { throw new IllegalArgumentException(e); }
        return out;
    }
    static JSONArray arr(Object... values) { JSONArray a=new JSONArray(); for(Object v:values)a.put(v); return a; }
    static JSONObject parse(String s) { try{return new JSONObject(s);}catch(Exception e){throw new IllegalArgumentException("잘못된 JSON 데이터입니다.",e);} }
    static String clipped(String s,int n){return s==null?"":s.length()>n?s.substring(0,n)+"…":s;}
    static String error(Throwable t){String m=t.getMessage();return clipped(m==null?t.getClass().getSimpleName():m,1200);}
}
