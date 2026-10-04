package dev.chanho.hermes;

import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

final class Net {
    private volatile HttpURLConnection active;
    private final AtomicBoolean cancelled=new AtomicBoolean(false);
    static final class ApiError extends IOException {
        final int status; final boolean contextOverflow; final long contextLimit;
        ApiError(int status,boolean overflow,long limit){super(overflow?"모델 문맥 한도에 도달했습니다.":"HTTP "+status+": 모델 API의 주소·API 키·모델 ID와 사용 한도를 확인하세요.");this.status=status;contextOverflow=overflow;contextLimit=limit;}
    }
    static ApiError apiError(int status,String body){
        boolean overflow=false;long limit=0;
        try{JSONObject json=new JSONObject(body);JSONObject error=json.optJSONObject("error");if(error==null)error=json;String code=error.optString("code","").toLowerCase(java.util.Locale.ROOT);String type=error.optString("type","").toLowerCase(java.util.Locale.ROOT);String message=error.optString("message","").toLowerCase(java.util.Locale.ROOT);
            overflow=(status==400||status==413||status==422)&&(code.equals("context_length_exceeded")||code.equals("context_window_exceeded")||type.equals("context_length_exceeded")||message.contains("maximum context length")||message.contains("exceeds the context window")||message.contains("context window exceeded")||message.contains("input tokens exceed"));
        if(overflow){java.util.regex.Matcher match=java.util.regex.Pattern.compile("maximum context length(?: is| of)?\\s*([0-9,]+)").matcher(message);if(match.find())try{limit=Long.parseLong(match.group(1).replace(",",""));}catch(Exception ignored){}}
        }catch(Exception ignored){}
        return new ApiError(status,overflow,limit);
    }
    interface Frame { void accept(String event,JSONObject data) throws Exception; }
    static void validateEndpoint(String value,boolean allowLan) throws Exception {
        URI uri=new URI(value);String host=uri.getHost();
        if(host==null||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getQuery()!=null)throw new IllegalArgumentException("주소는 인증 정보·쿼리 없는 HTTP(S) 기본 URL이어야 합니다.");
        String scheme=uri.getScheme();boolean loopback=host.equals("localhost")||host.equals("127.0.0.1")||host.equals("::1")||host.equals("[::1]");
        boolean lan=host.matches("10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")||host.matches("192\\.168\\.\\d{1,3}\\.\\d{1,3}")||host.matches("172\\.(1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3}");
        if(!"https".equals(scheme)&&!("http".equals(scheme)&&(loopback||(allowLan&&lan))))throw new IllegalArgumentException("HTTPS 주소를 사용하세요. HTTP는 휴대폰 내부 또는 명시적으로 허용한 사설 LAN 주소만 지원합니다.");
    }
    void reset(){cancelled.set(false);}
    void cancel(){cancelled.set(true);HttpURLConnection c=active;if(c!=null)c.disconnect();}
    boolean cancelled(){return cancelled.get();}
    JSONObject json(String endpoint,String path,String token,boolean allowLan,String method,JSONObject body) throws Exception {
        HttpURLConnection c=open(endpoint,path,token,allowLan,method,body,false);
        try{
            int code=c.getResponseCode();String text=read(c,code);
            if(code<200||code>=300)throw apiError(code,text);
            if(text.trim().isEmpty())return new JSONObject();return new JSONObject(text);
        }finally{c.disconnect();if(active==c)active=null;}
    }
    // Keyless search API array response; shares cancellation and bounded network reads.
    JSONArray jsonArray(String endpoint,String path) throws Exception {
        HttpURLConnection c=open(endpoint,path,"",false,"GET",null,false);
        try{int code=c.getResponseCode();String text=read(c,code);
            if(code<200||code>=300)throw new IOException("검색 API HTTP "+code);
            return new JSONArray(text);
        }finally{c.disconnect();if(active==c)active=null;}
    }
    void stream(String endpoint,String path,String token,boolean allowLan,JSONObject body,Frame handler) throws Exception {
        HttpURLConnection c=open(endpoint,path,token,allowLan,"POST",body,true);
        try{
            int code=c.getResponseCode();if(code<200||code>=300)throw apiError(code,read(c,code));
            String ct=c.getContentType();if(ct==null||!ct.contains("text/event-stream"))throw new IOException("서버가 SSE 스트림을 반환하지 않았습니다. API 호환 설정을 확인하세요.");
            try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){
                parseStream(r,handler,cancelled);
            }
        }finally{c.disconnect();if(active==c)active=null;}
    }
    // A disconnected socket is not a completed model response. Never dispatch its partial tools.
    static void parseStream(BufferedReader r,Frame handler,AtomicBoolean cancelled) throws Exception {
        String line,event="message";StringBuilder data=new StringBuilder();long received=0;boolean done=false;
        while((line=r.readLine())!=null){
            if(cancelled.get())throw new InterruptedIOException("사용자가 중단했습니다.");
            received+=line.length();if(received>4000000)throw new IOException("응답이 허용 크기를 초과했습니다.");
            if(line.isEmpty()){
                if(data.length()>0){String d=data.toString();if(d.equals("[DONE]")){done=true;break;}handler.accept(event,new JSONObject(d));}
                event="message";data.setLength(0);
            }else if(line.startsWith("event:"))event=line.substring(6).trim();
            else if(line.startsWith("data:")){if(data.length()>0)data.append('\n');String value=line.substring(5);data.append(value.startsWith(" ")?value.substring(1):value);}
        }
        if(!done&&data.length()>0){if(data.toString().equals("[DONE]"))done=true;else handler.accept(event,new JSONObject(data.toString()));}
        if(cancelled.get())throw new InterruptedIOException("사용자가 중단했습니다.");
        if(!done)throw new IOException("모델 응답이 끝나기 전에 연결이 끊겼습니다. 부분 도구 요청은 실행하지 않았습니다.");
    }
    private HttpURLConnection open(String endpoint,String path,String token,boolean allowLan,String method,JSONObject body,boolean stream) throws Exception {
        if(cancelled())throw new InterruptedIOException("사용자가 중단했습니다.");
        validateEndpoint(endpoint,allowLan);if(!path.startsWith("/")||path.contains(".."))throw new IllegalArgumentException("잘못된 API 경로입니다.");
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint.replaceAll("/+$","")+path).openConnection();active=c;
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(90000);c.setRequestMethod(method);
        c.setRequestProperty("Accept",stream?"text/event-stream":"application/json");c.setRequestProperty("User-Agent","HermesPocket/"+BuildConfig.VERSION_NAME);
        if(!token.isEmpty()){if(token.contains("\n")||token.contains("\r"))throw new IllegalArgumentException("잘못된 토큰입니다.");c.setRequestProperty("Authorization","Bearer "+token);}
        if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
        return c;
    }
    private String read(HttpURLConnection c,int status) throws Exception {
        InputStream stream=status>=400?c.getErrorStream():c.getInputStream();if(stream==null)return "";
        try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(cancelled())throw new InterruptedIOException("사용자가 중단했습니다.");if(out.size()+n>4000000)throw new IOException("응답이 너무 큽니다.");out.write(b,0,n);}return out.toString("UTF-8");
        }
    }
    private String safeError(String text){
        // Provider errors may echo authorization headers or user data. Keep diagnostics local and generic.
        return "모델 API의 주소·API 키·모델 ID와 사용 한도를 확인하세요.";
    }
}
