package dev.chanho.hermes;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** MCP Streamable HTTP client. No server socket, redirects, credential forwarding or effectful retries. */
final class McpClient {
    static final String PROTOCOL="2025-06-18";
    private final String endpoint,token;
    private final boolean allowLan;
    private final AtomicLong ids=new AtomicLong();
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private volatile HttpURLConnection active;
    private String session="",protocol=PROTOCOL;
    private JSONObject serverInfo=new JSONObject();
    private boolean initialized;
    McpClient(String endpoint,String token,boolean allowLan) throws Exception {
        Net.validateEndpoint(endpoint,allowLan);
        if(token==null||token.length()>8192||token.indexOf('\r')>=0||token.indexOf('\n')>=0)throw new IllegalArgumentException("MCP 인증 정보 형식이 올바르지 않습니다.");
        this.endpoint=endpoint;this.token=token;this.allowLan=allowLan;
    }
    boolean cancelled(){return cancelled.get();}
    void cancel(){cancelled.set(true);HttpURLConnection c=active;if(c!=null)c.disconnect();}
    private void check() throws InterruptedIOException {if(cancelled()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("MCP 요청을 중단했습니다.");}
    synchronized JSONObject initialize() throws Exception {
        check();if(initialized)return new JSONObject(serverInfo.toString());
        JSONObject result=rpc("initialize",J.obj("protocolVersion",PROTOCOL,"capabilities",J.obj(),"clientInfo",J.obj("name","hermes-pocket","version",BuildConfig.VERSION_NAME)),false);
        String negotiated=result.optString("protocolVersion");
        if(!Arrays.asList(PROTOCOL,"2025-03-26").contains(negotiated))throw new IOException("지원하지 않는 MCP 프로토콜 버전입니다.");
        protocol=negotiated;
        rpc("notifications/initialized",new JSONObject(),true);
        serverInfo=J.obj("protocolVersion",protocol,"serverInfo",result.optJSONObject("serverInfo"),"capabilities",result.optJSONObject("capabilities"),"transport","streamable-http","clientCapabilities",J.obj());
        initialized=true;return new JSONObject(serverInfo.toString());
    }
    synchronized JSONObject request(String method,JSONObject params) throws Exception {
        if(!Arrays.asList("tools/list","tools/call","resources/list","resources/read","resources/templates/list","prompts/list","prompts/get","ping").contains(method))throw new IllegalArgumentException("지원하지 않는 MCP 메서드입니다.");
        initialize();return rpc(method,params,false);
    }
    synchronized JSONObject list(String method,String arrayKey) throws Exception {
        JSONArray items=new JSONArray();Set<String> seen=new HashSet<>();String cursor="";boolean more=false;
        for(int page=0;page<10;page++){
            JSONObject result=request(method,cursor.isEmpty()?J.obj():J.obj("cursor",cursor));
            JSONArray incoming=result.optJSONArray(arrayKey);if(incoming==null)throw new IOException("MCP 목록 응답 형식이 올바르지 않습니다.");
            for(int i=0;i<incoming.length()&&items.length()<1000;i++)items.put(incoming.get(i));
            Object next=result.opt("nextCursor");if(next!=null&&next!=JSONObject.NULL&&!(next instanceof String))throw new IOException("MCP 페이지 커서가 올바르지 않습니다.");
            cursor=next instanceof String?(String)next:"";more=!cursor.isEmpty()||items.length()>=1000;
            if(cursor.isEmpty())break;
            if(cursor.length()>4096||!seen.add(cursor))throw new IOException("MCP 페이지 커서가 반복되거나 너무 큽니다.");
            if(items.length()>=1000)break;
        }
        return J.obj(arrayKey,items,"truncated",more,"server",serverInfo,"untrusted",true);
    }
    private JSONObject rpc(String method,JSONObject params,boolean notification) throws Exception {
        check();Object id=notification?null:ids.incrementAndGet();
        JSONObject payload=J.obj("jsonrpc","2.0","method",method,"params",params);
        if(!notification)payload.put("id",id);
        byte[] body=payload.toString().getBytes(StandardCharsets.UTF_8);if(body.length>512*1024)throw new IllegalArgumentException("MCP 요청이 너무 큽니다.");
        Net.validateEndpoint(endpoint,allowLan);
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();active=c;
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(90000);c.setRequestMethod("POST");c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setRequestProperty("Accept","application/json, text/event-stream");
        c.setRequestProperty("MCP-Protocol-Version",protocol);c.setRequestProperty("User-Agent","HermesPocket/"+BuildConfig.VERSION_NAME);
        if(!session.isEmpty())c.setRequestProperty("Mcp-Session-Id",session);
        if(!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);
        try{
            check();c.setFixedLengthStreamingMode(body.length);try(OutputStream out=c.getOutputStream()){out.write(body);}
            check();int status=c.getResponseCode();
            if(status<200||status>=300){if(status==404){initialized=false;session="";}throw new IOException("MCP HTTP "+status+". 인증·주소·서버 상태를 확인하세요. 요청은 자동 재실행하지 않았습니다.");}
            if("initialize".equals(method)){
                String issued=c.getHeaderField("Mcp-Session-Id");
                if(issued!=null){if(issued.isEmpty()||issued.length()>256||!issued.matches("[\\x21-\\x7E]+"))throw new IOException("MCP 세션 헤더가 올바르지 않습니다.");session=issued;}
            }
            if(notification){if(status!=202&&status!=200&&status!=204)throw new IOException("MCP 알림 응답 상태가 올바르지 않습니다.");return new JSONObject();}
            if(status==202||status==204)throw new IOException("MCP 요청의 실제 결과가 반환되지 않았습니다.");
            String contentType=c.getContentType();if(contentType==null)throw new IOException("MCP 응답 형식이 없습니다.");
            try(InputStream bounded=new LimitedInputStream(c.getInputStream(),this)){
                if(contentType.toLowerCase(Locale.ROOT).contains("text/event-stream"))return parseEvents(new BufferedReader(new InputStreamReader(bounded,StandardCharsets.UTF_8)),id);
                if(!contentType.toLowerCase(Locale.ROOT).contains("application/json"))throw new IOException("MCP 서버가 JSON/SSE 대신 다른 내용을 반환했습니다.");
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
                while((count=bounded.read(buffer))!=-1)out.write(buffer,0,count);
                return response(new JSONObject(out.toString("UTF-8")),id);
            }
        }finally{c.disconnect();if(active==c)active=null;}
    }
    private JSONObject parseEvents(BufferedReader reader,Object id) throws Exception {
        String line;StringBuilder data=new StringBuilder();int events=0;
        while((line=reader.readLine())!=null){
            check();
            if(line.isEmpty()){
                if(data.length()>0){JSONObject frame=new JSONObject(data.toString());data.setLength(0);if(++events>4096)throw new IOException("MCP 이벤트가 너무 많습니다.");
                    if(frame.has("id")&&!frame.has("method"))return response(frame,id);
                    // No sampling/elicitation capabilities were advertised; server requests are not permissions.
                    if(frame.has("method")&&frame.has("id"))throw new IOException("지원하지 않는 서버 발신 MCP 요청입니다. 사용자 동의를 대신하지 않습니다.");
                }
            }else if(line.startsWith("data:")){if(data.length()>0)data.append('\n');String value=line.substring(5);data.append(value.startsWith(" ")?value.substring(1):value);}
        }
        if(data.length()>0)return response(new JSONObject(data.toString()),id);
        throw new IOException("MCP 결과 수신 전에 연결이 끝났습니다. 도구 요청은 재실행하지 않았습니다.");
    }
    private static JSONObject response(JSONObject frame,Object id) throws Exception {
        if(!"2.0".equals(frame.optString("jsonrpc"))||!frame.has("id")||!String.valueOf(id).equals(String.valueOf(frame.get("id")))||frame.has("result")==frame.has("error"))throw new IOException("MCP 응답 ID 또는 JSON-RPC 형식이 올바르지 않습니다.");
        if(frame.has("error")){JSONObject error=frame.optJSONObject("error");int code=error==null?0:error.optInt("code");throw new IOException("MCP 서버 오류 "+code+". 요청은 자동 재실행하지 않았습니다.");}
        JSONObject result=frame.optJSONObject("result");if(result==null)throw new IOException("MCP 결과는 객체여야 합니다.");return result;
    }
    private static final class LimitedInputStream extends FilterInputStream {
        private final McpClient client;private long count;
        LimitedInputStream(InputStream input,McpClient client){super(input);this.client=client;}
        private void used(int n) throws IOException {client.check();if(n>0){count+=n;if(count>4*1024*1024)throw new IOException("MCP 응답이 4 MiB 제한을 넘었습니다.");}}
        @Override public int read() throws IOException {int value=in.read();used(value<0?0:1);return value;}
        @Override public int read(byte[] b,int off,int len) throws IOException {int n=in.read(b,off,len);used(n);return n;}
    }
}
