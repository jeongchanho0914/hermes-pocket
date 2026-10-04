package dev.chanho.hermes;

import android.content.Context;
import org.json.*;
import java.lang.reflect.*;

/** Embedded upstream AIAgent entry point. No relay process or remote execution server. */
final class HermesEngine {
    private static final Object PYTHON_START_LOCK=new Object();
    private final AgentRuntime runtime;
    HermesEngine(AgentRuntime runtime){this.runtime=runtime;}

    String run(String session,String message,JSONObject config,String apiKey) throws Exception {
        if(runtime.cancelled())throw new InterruptedException("사용자가 중단했습니다.");
        Net.validateEndpoint(config.getString("endpoint"),config.optBoolean("allowLan"));
        if(config.optString("model").isEmpty())throw new IllegalArgumentException("기본 모델을 먼저 설정해 주세요.");
        PythonPhoneBridge bridge=new PythonPhoneBridge(runtime,session,config);
        JSONObject request=new JSONObject(config.toString());
        request.put("sessionId",session);request.put("message",message);
        request.put("apiKey",apiKey); // Ephemeral argument only; never write to preferences or a file.
        Object raw;
        try{
            Object python=python(runtime.context);
            Object module=python.getClass().getMethod("getModule",String.class).invoke(python,"hermes_android");
            raw=module.getClass().getMethod("callAttr",String.class,Object[].class)
                .invoke(module,"run",new Object[]{request.toString(),bridge});
        }catch(InvocationTargetException e){
            if(runtime.cancelled())throw new InterruptedException("사용자가 중단했습니다.");
            // Python/API exceptions may echo the in-memory request or authorization. Do not expose them.
            throw new IllegalStateException("원본 Hermes 엔진 실행에 실패했습니다. API 설정과 엔진 설치 상태를 확인해 주세요.");
        }catch(ReflectiveOperationException e){
            throw new IllegalStateException("이 앱 빌드에는 원본 Hermes Python 실행 환경이 준비되지 않았습니다.");
        }finally{request.remove("apiKey");}
        if(runtime.cancelled())throw new InterruptedException("사용자가 중단했습니다.");
        String text=String.valueOf(raw);
        if(text.length()>4000000)throw new IllegalStateException("엔진 응답이 허용 크기를 초과했습니다.");
        JSONObject result=new JSONObject(text);
        if(result.optBoolean("cancelled"))throw new InterruptedException("사용자가 중단했습니다.");
        boolean failed=result.has("error")&&!result.isNull("error")&&!result.optString("error").isEmpty();
        if(!result.optBoolean("completed")||failed)
            throw new IllegalStateException("원본 Hermes 엔진이 작업을 완료하지 못했습니다. 실행 기록을 확인해 주세요.");
        Object response=result.opt("final_response");
        if(!(response instanceof String))throw new IllegalStateException("원본 Hermes 엔진의 최종 응답 형식이 올바르지 않습니다.");
        return (String)response;
    }
    private static Object python(Context context) throws ReflectiveOperationException {
        Class<?> python=Class.forName("com.chaquo.python.Python");
        synchronized(PYTHON_START_LOCK){
            if(!(Boolean)python.getMethod("isStarted").invoke(null)){
                Class<?> platform=Class.forName("com.chaquo.python.Python$Platform");
                Object android=Class.forName("com.chaquo.python.android.AndroidPlatform").getConstructor(Context.class).newInstance(context.getApplicationContext());
                python.getMethod("start",platform).invoke(null,android);
            }
        }
        return python.getMethod("getInstance").invoke(null);
    }
}
