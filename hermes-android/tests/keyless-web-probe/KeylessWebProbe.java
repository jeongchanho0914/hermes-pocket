import org.json.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;

/** Standalone app_process diagnostic. No app context, credentials or phone storage. */
public final class KeylessWebProbe {
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("One public HTTPS JSON search API URL required");
        final URI uri=new URI(args[0]);
        if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getPort()!=-1)throw new IllegalArgumentException("Public HTTPS only");
        ExecutorService worker=Executors.newSingleThreadExecutor();
        Future<JSONObject> result=worker.submit(() -> probe(uri));
        try{System.out.println(result.get(8,TimeUnit.SECONDS).toString());}
        catch(TimeoutException timeout){System.out.println(new JSONObject().put("endpoint",uri.getHost()).put("ok",false).put("error","deadline_8_seconds"));}
        catch(Exception error){System.out.println(new JSONObject().put("endpoint",uri.getHost()).put("ok",false).put("error",error.getClass().getSimpleName()));}
        finally{result.cancel(true);worker.shutdownNow();}
        System.exit(0);
    }
    private static JSONObject probe(URI uri) throws Exception {
        HttpURLConnection c=(HttpURLConnection)uri.toURL().openConnection();
        c.setConnectTimeout(4000);c.setReadTimeout(4000);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","HermesPocket/0.07 (keyless API compatibility check)");
        JSONObject out=new JSONObject().put("endpoint",uri.getHost()).put("authorizationSent",false);
        try{
            int status=c.getResponseCode();out.put("http",status);
            if(status!=200)return out.put("ok",false).put("error","HTTP_"+status);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){if(bytes.size()+n>500000)return out.put("ok",false).put("error","response_too_large");bytes.write(buf,0,n);}}
            Object parsed=new JSONTokener(bytes.toString("UTF-8")).nextValue();
            JSONArray results=parsed instanceof JSONArray?(JSONArray)parsed:parsed instanceof JSONObject?((JSONObject)parsed).optJSONArray("results"):null;
            if(results==null)return out.put("ok",false).put("error","not_search_JSON");
            out.put("ok",true).put("count",results.length());
            for(int i=0;i<results.length();i++){
                JSONObject item=results.optJSONObject(i);if(item==null)continue;
                String url=item.optString("url","");URI source=new URI(url);
                if(!"https".equals(source.getScheme())||source.getUserInfo()!=null||source.getHost()==null)continue;
                Object title=item.opt("title");String text=title instanceof String?(String)title:"";
                if(title instanceof JSONArray){JSONArray parts=(JSONArray)title;for(int j=0;j<parts.length();j++){JSONObject part=parts.optJSONObject(j);if(part!=null)text+=part.optString("value","");}}
                return out.put("first",new JSONObject().put("title",text.substring(0,Math.min(220,text.length()))).put("url",url));
            }
            return out;
        }catch(JSONException invalid){return out.put("ok",false).put("error","not_search_JSON");}
        finally{c.disconnect();}
    }
}
