package dev.chanho.hermes;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.security.keystore.*;
import android.util.Base64;
import org.json.*;
import java.security.KeyStore;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

final class Store extends SQLiteOpenHelper {
    private final Context context;
    private final android.content.SharedPreferences prefs;
    Store(Context c){
        super(c,"pocket.db",null,4);context=c;prefs=c.getSharedPreferences("pocket_settings",0);
        // Legacy adapter credentials must never be reused for a model API.
        // Explicit direct configurations retain their endpoint and encrypted key.
        if(!"direct".equals(get("mode",""))){
            boolean migrated=prefs.edit().remove("secret_token").remove("endpoint").remove("model")
                .putString("mode","direct").commit();
            if(!migrated)throw new IllegalStateException("연결 설정을 이전하지 못했습니다. 앱을 다시 실행해 주세요.");
        }
        // Preserve legacy Tavily users; new installations can search without a separate search key.
        if(!prefs.contains("web_provider")&&!prefs.edit().putString("web_provider",get("secret_web_token","").isEmpty()?"mwmbl":"tavily").commit())throw new IllegalStateException("웹 검색 설정을 이전하지 못했습니다.");
        // v009 explicitly enables the newly requested module once; subsequent owner changes persist.
        if(!prefs.getBoolean("terminal_module_migrated_v009",false)){
            JSONArray modules;try{modules=new JSONArray(get("enabled_plugins",LocalCapabilities.defaultPlugins().toString()));}catch(JSONException invalid){modules=new JSONArray();}
            boolean found=false;for(int i=0;i<modules.length();i++)if("terminal".equals(modules.optString(i)))found=true;
            if(!found)modules.put("terminal");
            if(!prefs.edit().putString("enabled_plugins",modules.toString()).putBoolean("terminal_module_migrated_v009",true).commit())throw new IllegalStateException("터미널 설정을 이전하지 못했습니다.");
        }
    }
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE sessions(id TEXT PRIMARY KEY,title TEXT NOT NULL,created INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT,sid TEXT NOT NULL,role TEXT NOT NULL,body TEXT NOT NULL,created INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX messages_sid ON messages(sid,id)");
        db.execSQL("CREATE TABLE transcripts(sid TEXT PRIMARY KEY,body TEXT NOT NULL)");
        createActivities(db);createProviderThoughts(db);createTimeline(db);
        db.execSQL("CREATE TABLE audit(id INTEGER PRIMARY KEY AUTOINCREMENT,tool TEXT NOT NULL,status TEXT NOT NULL,detail TEXT NOT NULL,created INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<1||oldVersion>3||newVersion!=4)throw new IllegalStateException("명시적 데이터 이전이 필요합니다.");
        if(oldVersion<2)createActivities(db);if(oldVersion<3)createProviderThoughts(db);createTimeline(db);
    }
    private void createTimeline(SQLiteDatabase db){
        db.execSQL("CREATE TABLE timeline_clock(id INTEGER PRIMARY KEY AUTOINCREMENT,sid TEXT NOT NULL,source TEXT NOT NULL,reference TEXT NOT NULL,UNIQUE(source,reference))");
        for(String table:new String[]{"messages","activities","provider_thoughts"})db.execSQL("ALTER TABLE "+table+" ADD COLUMN timeline_order INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE messages ADD COLUMN run_id TEXT NOT NULL DEFAULT ''");
        // Native timestamps determine legacy order. Ties put the owner's message first,
        // then provider thoughts/tool activity, and finally the completed answer.
        // Older event timestamps could be provider-supplied; clamp any pre-user clock
        // skew to the first actual native owner turn without inspecting private text.
        String union="SELECT 'messages' AS source,id,sid,created,CASE WHEN role='user' THEN 0 ELSE 3 END AS priority,id AS sequence FROM messages UNION ALL SELECT 'provider_thoughts',t.id,t.sid,MAX(t.created,COALESCE((SELECT MIN(m.created) FROM messages m WHERE m.sid=t.sid AND m.role='user'),t.created)),1,t.id FROM provider_thoughts t UNION ALL SELECT 'activities',a.id,a.sid,MAX(a.created,COALESCE((SELECT MIN(m.created) FROM messages m WHERE m.sid=a.sid AND m.role='user'),a.created)),2,a.id FROM activities a ORDER BY created,priority,sequence";
        try(Cursor c=db.rawQuery(union,null)){while(c.moveToNext()){
            String table=c.getString(0),reference=String.valueOf(c.getLong(1));long order=allocateTimeline(db,c.getString(2),table,reference);
            ContentValues v=new ContentValues();v.put("timeline_order",order);db.update(table,v,"id=?",new String[]{reference});
        }}
    }
    private long allocateTimeline(SQLiteDatabase db,String sid,String source,String reference){
        ContentValues v=new ContentValues();v.put("sid",sid);v.put("source",source);v.put("reference",reference);return db.insertOrThrow("timeline_clock",null,v);
    }
    synchronized long reserveResponseOrder(String sid,String run,int round){
        SQLiteDatabase db=getWritableDatabase();String reference=run+":"+round;
        try(Cursor c=db.rawQuery("SELECT id FROM timeline_clock WHERE source='public_response' AND reference=?",new String[]{reference})){if(c.moveToFirst())return c.getLong(0);}
        return allocateTimeline(db,sid,"public_response",reference);
    }
    private JSONObject orderedBody(Cursor c) throws JSONException {JSONObject result=new JSONObject(c.getString(0));result.put("timelineOrder",c.getLong(1));return result;}
    private void createProviderThoughts(SQLiteDatabase db){db.execSQL("CREATE TABLE provider_thoughts(id INTEGER PRIMARY KEY AUTOINCREMENT,sid TEXT NOT NULL,run_id TEXT NOT NULL,round INTEGER NOT NULL,body TEXT NOT NULL,created INTEGER NOT NULL,UNIQUE(run_id,round))");db.execSQL("CREATE INDEX provider_thoughts_sid ON provider_thoughts(sid,id)");}
    synchronized JSONObject recordProviderThought(JSONObject event){
        if(!"mimo".equals(event.optString("provider"))||!"mimo.reasoning_content".equals(event.optString("source"))||!(event.opt("text") instanceof String))throw new IllegalArgumentException("공개 모델 생각 기록 형식이 올바르지 않습니다.");
        String run=event.optString("runId"),sid=event.optString("session");int round=event.optInt("round",-1);if(run.isEmpty()||run.length()>200||sid.isEmpty()||sid.length()>200||round<1||round>64)throw new IllegalArgumentException("공개 모델 생각 식별자가 올바르지 않습니다.");
        long created=System.currentTimeMillis(),order=0;SQLiteDatabase db=getWritableDatabase();try(Cursor c=db.rawQuery("SELECT created,timeline_order FROM provider_thoughts WHERE run_id=? AND round=?",new String[]{run,String.valueOf(round)})){if(c.moveToFirst()){created=c.getLong(0);order=c.getLong(1);}}
        if(order==0)order=allocateTimeline(db,sid,"provider_thoughts",run+":"+round);
        String text=event.optString("text");JSONObject safe=J.obj("provider","mimo","source","mimo.reasoning_content","session",sid,"runId",run,"round",round,"text",J.clipped(text,32000),"truncated",event.optBoolean("truncated")||text.length()>32000,"elapsedMs",Math.max(0,event.optLong("elapsedMs",0)),"timestamp",event.optLong("timestamp",created),"firstTimestamp",created,"timelineOrder",order);
        ContentValues values=new ContentValues();values.put("sid",sid);values.put("run_id",run);values.put("round",round);values.put("timeline_order",order);values.put("body",safe.toString());values.put("created",created);int updated=db.update("provider_thoughts",values,"run_id=? AND round=?",new String[]{run,String.valueOf(round)});if(updated==0)db.insertOrThrow("provider_thoughts",null,values);return safe;
    }
    synchronized JSONArray providerThoughts(String sid){JSONArray out=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT body,timeline_order FROM provider_thoughts WHERE sid=? ORDER BY timeline_order LIMIT 500",new String[]{sid})){while(c.moveToNext())try{out.put(orderedBody(c));}catch(JSONException invalid){throw new IllegalStateException("공개 모델 생각 기록을 읽지 못했습니다.",invalid);}}return out;}

    private void createActivities(SQLiteDatabase db){db.execSQL("CREATE TABLE activities(id INTEGER PRIMARY KEY AUTOINCREMENT,sid TEXT NOT NULL,run_id TEXT NOT NULL,call_id TEXT NOT NULL,kind TEXT NOT NULL,body TEXT NOT NULL,created INTEGER NOT NULL,UNIQUE(run_id,call_id,kind))");db.execSQL("CREATE INDEX activities_sid ON activities(sid,id)");}
    synchronized JSONObject recordActivity(JSONObject event){
        String run=event.optString("runId"),call=event.optString("toolCallId"),kind=event.optString("kind");if(run.isEmpty()||run.length()>200||call.length()>200||!Arrays.asList("tool","response").contains(kind))throw new IllegalArgumentException("작업 기록 식별자가 올바르지 않습니다.");
        String status=event.optString("status");if(!Arrays.asList("started","approval","completed","failed","cancelled").contains(status))throw new IllegalArgumentException("작업 기록 상태가 올바르지 않습니다.");
        String sid=J.clipped(event.optString("session"),200);long created=System.currentTimeMillis(),seq=event.optLong("seq",0),order=0;SQLiteDatabase db=getWritableDatabase();
        try(Cursor c=db.rawQuery("SELECT created,body,timeline_order FROM activities WHERE run_id=? AND call_id=? AND kind=?",new String[]{run,call,kind})){if(c.moveToFirst()){created=c.getLong(0);order=c.getLong(2);try{seq=new JSONObject(c.getString(1)).optLong("firstSeq",seq);}catch(JSONException ignored){}}}
        if(order==0){if("response".equals(kind)&&call.matches("response_[0-9]+"))order=reserveResponseOrder(sid,run,Integer.parseInt(call.substring(9)));else order=allocateTimeline(db,sid,"activities",run+":"+call+":"+kind);}
        JSONObject safe=J.obj("runId",run,"session",sid,"toolCallId",call,"seq",event.optLong("seq",0),"firstSeq",seq,"kind",kind,"name",J.clipped(event.optString("name"),150),"status",status,"summary",J.clipped(event.opt("summary") instanceof String?event.optString("summary"):"",2000),"argsSummary",J.clipped(event.opt("argsSummary") instanceof String?event.optString("argsSummary"):"",2000),"timestamp",event.optLong("timestamp",created),"firstTimestamp",created,"timelineOrder",order);
        if("response".equals(kind))try{safe.put("text",J.clipped(event.optString("text"),16000));safe.put("textTruncated",event.optBoolean("textTruncated")||event.optString("text").length()>16000);}catch(JSONException impossible){throw new IllegalStateException(impossible);}
        ContentValues values=new ContentValues();values.put("timeline_order",order);values.put("sid",sid);values.put("run_id",run);values.put("call_id",call);values.put("kind",kind);values.put("body",safe.toString());values.put("created",created);
        int updated=db.update("activities",values,"run_id=? AND call_id=? AND kind=?",new String[]{run,call,kind});if(updated==0)db.insertOrThrow("activities",null,values);return safe;
    }
    synchronized JSONArray activities(String sid){JSONArray out=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT body,timeline_order FROM activities WHERE sid=? ORDER BY timeline_order LIMIT 1000",new String[]{sid})){while(c.moveToNext())try{out.put(orderedBody(c));}catch(JSONException invalid){throw new IllegalStateException("작업 기록을 읽지 못했습니다.",invalid);}}return out;}

    String get(String key,String fallback){return prefs.getString(key,fallback);}
    boolean flag(String key){return prefs.getBoolean(key,false);}
    void put(String key,String value){prefs.edit().putString(key,value).apply();}
    synchronized void putDurable(String key,String value){if(!prefs.edit().putString(key,value).commit())throw new IllegalStateException("로컬 상태를 저장하지 못했습니다.");}
    void flag(String key,boolean value){prefs.edit().putBoolean(key,value).apply();}
    boolean isFloatingEnabled(){return prefs.getBoolean("floating_enabled",true);}
    synchronized void setFloatingEnabled(boolean enabled){
        if(!prefs.edit().putBoolean("floating_enabled",enabled).commit())throw new IllegalStateException("작은 창 설정을 저장하지 못했습니다.");
    }
    String fileTreeUri(){return get("file_tree_uri","");}
    synchronized void setFileTreeUri(String value){
        if(!value.isEmpty())PhoneFiles.checkedTree(value);
        if(!prefs.edit().putString("file_tree_uri",value).commit())throw new IllegalStateException("폴더 접근 설정을 저장하지 못했습니다.");
    }
    private SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        String alias="hermes-pocket-credentials-v1";
        if(!ks.containsAlias(alias)){
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();
        }
        return (SecretKey)ks.getKey(alias,null);
    }
    synchronized void secret(String name,String value) throws Exception {
        if(value.isEmpty()){prefs.edit().remove("secret_"+name).apply();return;}
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        put("secret_"+name,Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(value.getBytes("UTF-8")),Base64.NO_WRAP));
    }
    synchronized String secret(String name) throws Exception {
        String s=get("secret_"+name,"");if(s.isEmpty())return "";
        String[] parts=s.split(":",2);if(parts.length!=2)throw new IllegalStateException("저장된 인증 정보를 다시 입력해 주세요.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),"UTF-8");
    }
    String providerId(){return LocalCapabilities.providerId(get("provider_id",LocalCapabilities.inferProvider(get("endpoint",""))));}
    String endpoint(){String saved=get("endpoint","");return saved.isEmpty()?LocalCapabilities.providerEndpoint(providerId()):saved;}
    String webProvider(){String saved=get("web_provider","");if(saved.isEmpty())return get("secret_web_token","").isEmpty()?"mwmbl":"tavily";return webProviderValue(saved);}
    String webEndpoint(){return get("web_endpoint","");}
    private String webProviderValue(String provider){if(Arrays.asList("mwmbl","tavily","searxng").contains(provider))return provider;throw new IllegalArgumentException("웹 검색 제공자는 Mwmbl, Tavily 또는 SearXNG를 선택하세요.");}
    String deviceScope(){try{return OwnerApprovalPolicy.scope(get("device_scope","all"));}catch(IllegalArgumentException e){return "none";}}
    String approvalMode(){try{return OwnerApprovalPolicy.mode(get("approval_mode","ask"));}catch(IllegalArgumentException e){return "ask";}}
    JSONObject config(){JSONObject config=J.obj("mode","direct","providerId",providerId(),"endpoint",endpoint(),"model",get("model",""),"hasToken",!get("secret_token","").isEmpty(),"webProvider",webProvider(),"webEndpoint",webEndpoint(),"hasWebToken",!get("secret_web_token","").isEmpty(),"activeSkillName",get("active_skill_name",""),"allowLan",flag("allow_lan"),"rootEnabled",flag("root_enabled"),"allowedApps",allowedApps(),"memory",get("memory",""),"maxRounds",Integer.parseInt(get("max_rounds","24")),"contextChars",Integer.parseInt(get("context_chars","60000")),"skillId",get("skill_id","general"),"enabledPlugins",enabledPlugins(),"reasoningEffort",get("reasoning_effort","auto"),"effectiveReasoningEffort",LocalCapabilities.effectiveEffort(J.obj("reasoningEffort",get("reasoning_effort","auto"),"providerId",providerId(),"endpoint",endpoint())));
        try{addModelMetadata(config);config.put("modelLimits",ModelLimits.resolve(config).json());config.put("availableTools",LocalCapabilities.inventory(config));config.put("fileTreeConfigured",!fileTreeUri().isEmpty());config.put("deviceScope",deviceScope());config.put("approvalMode",approvalMode());config.put("floatingEnabled",isFloatingEnabled());config.put("shizukuEnabled",flag("shizuku_enabled"));}
        catch(JSONException e){throw new IllegalStateException("활성화된 도구를 확인하지 못했습니다.",e);}
        return config;
    }
    synchronized void rememberModelMetadata(String endpoint,JSONObject normalized) throws JSONException {
        JSONArray source=normalized.optJSONArray("models"),safe=new JSONArray();if(source!=null)for(int i=0;i<source.length()&&i<2000;i++){
            JSONObject item=source.optJSONObject(i);if(item==null)continue;long context=ModelLimits.positive(item,"contextTokens"),output=ModelLimits.positive(item,"outputTokens");
            if(context>0||output>0)safe.put(J.obj("id",J.clipped(item.optString("id"),200),"contextTokens",context,"outputTokens",output));
        }
        put("model_limits_catalog",J.obj("endpoint",endpoint,"models",safe).toString());
    }
    private void addModelMetadata(JSONObject config) throws JSONException {
        JSONObject saved;try{saved=new JSONObject(get("model_limits_catalog","{}"));}catch(JSONException invalid){return;}
        if(!config.optString("endpoint").equals(saved.optString("endpoint")))return;
        JSONArray models=saved.optJSONArray("models");if(models==null)return;for(int i=0;i<models.length();i++){
            JSONObject item=models.optJSONObject(i);if(item==null||!config.optString("model").equals(item.optString("id")))continue;
            long context=ModelLimits.positive(item,"contextTokens"),output=ModelLimits.positive(item,"outputTokens");if(context>0)config.put("modelContextTokens",context);if(output>0)config.put("modelOutputTokens",output);if(context>0||output>0)config.put("modelLimitsSource","api");break;
        }
    }
    synchronized JSONObject saveConfig(JSONObject p) throws Exception {
        String scope=OwnerApprovalPolicy.scope(p.has("deviceScope")?p.getString("deviceScope"):deviceScope());
        String approval=OwnerApprovalPolicy.mode(p.has("approvalMode")?p.getString("approvalMode"):approvalMode());
        if(p.has("floatingEnabled")&&!(p.get("floatingEnabled") instanceof Boolean))throw new IllegalArgumentException("작은 창 설정은 켜짐 또는 꺼짐이어야 합니다.");
        validateWebSettings(p);
        String activeSkill=activeSkill(p);
        String mode=p.optString("mode","direct");if(!mode.equals("direct"))throw new IllegalArgumentException("휴대폰 내부 실행과 모델 API 직접 연결만 지원합니다.");
        String oldProvider=providerId();
        String provider=LocalCapabilities.providerId(p.has("providerId")?p.getString("providerId"):p.has("endpoint")?LocalCapabilities.inferProvider(p.getString("endpoint")):oldProvider);
        String url="custom".equals(provider)?p.optString("endpoint",endpoint()).trim():LocalCapabilities.providerEndpoint(provider);
        Net.validateEndpoint(url,p.optBoolean("allowLan",false));
        String normalized=url.replaceAll("/+$","");
        boolean destinationChanged=!endpoint().equals(normalized)||!oldProvider.equals(provider);
        int rounds=p.optInt("maxRounds",Integer.parseInt(get("max_rounds","24")));
        int contextChars=p.optInt("contextChars",Integer.parseInt(get("context_chars","60000")));
        if(rounds<2||rounds>64)throw new IllegalArgumentException("실행 한도는 2~64회로 설정하세요.");
        if(contextChars<16000||contextChars>240000)throw new IllegalArgumentException("문맥 크기는 16,000~240,000자로 설정하세요.");
        String model=J.clipped(p.optString("model",destinationChanged?"":get("model","")).trim(),200);
        String skill=LocalCapabilities.skill(p.optString("skillId",get("skill_id","general")));
        String effort=LocalCapabilities.effort(p.optString("reasoningEffort",destinationChanged?"auto":get("reasoning_effort","auto")));
        JSONArray plugins=LocalCapabilities.plugins(p.has("enabledPlugins")?p.getJSONArray("enabledPlugins"):enabledPlugins());
        // Credentials never silently follow a different endpoint or execution mode.
        if(destinationChanged){secret("token","");put("model_limits_catalog","{}");}
        put("max_rounds",String.valueOf(rounds));put("context_chars",String.valueOf(contextChars));
        put("mode",mode);put("provider_id",provider);put("endpoint",normalized);put("model",model);flag("allow_lan",p.optBoolean("allowLan",false));
        if(p.optBoolean("clearToken",false))secret("token","");
        else if(p.has("token")&&!p.optString("token").isEmpty())secret("token",p.getString("token"));
        put("active_skill_name",activeSkill);put("skill_id",skill);put("reasoning_effort",effort);put("enabled_plugins",plugins.toString());
        persistWebSettings(p);
        prefs.edit().putString("device_scope",scope).putString("approval_mode",approval).apply();
        if(p.has("floatingEnabled"))setFloatingEnabled(p.getBoolean("floatingEnabled"));
        return config();
    }
    synchronized JSONObject saveCapabilities(JSONObject p) throws Exception {
        String scope=OwnerApprovalPolicy.scope(p.has("deviceScope")?p.getString("deviceScope"):deviceScope());
        String approval=OwnerApprovalPolicy.mode(p.has("approvalMode")?p.getString("approvalMode"):approvalMode());
        if(p.has("floatingEnabled")&&!(p.get("floatingEnabled") instanceof Boolean))throw new IllegalArgumentException("작은 창 설정은 켜짐 또는 꺼짐이어야 합니다.");
        validateWebSettings(p);
        String activeSkill=activeSkill(p);
        int rounds=p.optInt("maxRounds",Integer.parseInt(get("max_rounds","24")));
        int contextChars=p.optInt("contextChars",Integer.parseInt(get("context_chars","60000")));
        if(rounds<2||rounds>64)throw new IllegalArgumentException("실행 한도는 2~64회로 설정하세요.");
        if(contextChars<16000||contextChars>240000)throw new IllegalArgumentException("문맥 크기는 16,000~240,000자로 설정하세요.");
        boolean allowLan=p.has("allowLan")?p.getBoolean("allowLan"):flag("allow_lan");
        String model=p.has("model")?p.getString("model").trim():get("model","");
        if(model.length()>200)throw new IllegalArgumentException("모델 ID는 200자 이하로 입력하세요.");
        String skill=LocalCapabilities.skill(p.optString("skillId",get("skill_id","general")));
        String effort=LocalCapabilities.effort(p.optString("reasoningEffort",get("reasoning_effort","auto")));
        JSONArray plugins=LocalCapabilities.plugins(p.has("enabledPlugins")?p.getJSONArray("enabledPlugins"):enabledPlugins());
        persistWebSettings(p);
        prefs.edit().putString("device_scope",scope).putString("approval_mode",approval).putString("active_skill_name",activeSkill).putString("model",model).putString("skill_id",skill).putString("reasoning_effort",effort).putString("enabled_plugins",plugins.toString()).putString("max_rounds",String.valueOf(rounds)).putString("context_chars",String.valueOf(contextChars)).putBoolean("allow_lan",allowLan).apply();
        if(p.has("floatingEnabled"))setFloatingEnabled(p.getBoolean("floatingEnabled"));
        return config();
    }
    private String activeSkill(JSONObject p) throws JSONException {
        String name=p.has("activeSkillName")?p.getString("activeSkillName"):get("active_skill_name","");
        return name.isEmpty()?"":LocalSkillStore.validName(name);
    }
    private void validateWebSettings(JSONObject p) throws Exception {
        String provider=webProviderValue(p.has("webProvider")?p.getString("webProvider"):webProvider());
        String endpoint=p.has("webEndpoint")?p.getString("webEndpoint").trim():webEndpoint();
        if(!endpoint.isEmpty())WebTools.validateWebEndpoint(endpoint);
        if("searxng".equals(provider)&&endpoint.isEmpty())throw new IllegalArgumentException("SearXNG JSON API 주소를 직접 입력하세요. 검증된 공개 기본 서버는 제공하지 않습니다.");
        if(p.has("webToken")){String token=p.getString("webToken");if(token.length()>4096||token.contains("\n")||token.contains("\r"))throw new IllegalArgumentException("검색 API 키가 올바르지 않습니다.");}
        if(p.has("clearWebToken"))p.getBoolean("clearWebToken");
    }
    private void persistWebSettings(JSONObject p) throws Exception {
        android.content.SharedPreferences.Editor web=prefs.edit();
        if(p.has("webProvider"))web.putString("web_provider",webProviderValue(p.getString("webProvider")));
        if(p.has("webEndpoint"))web.putString("web_endpoint",p.getString("webEndpoint").trim());
        web.apply();
        if(p.optBoolean("clearWebToken",false))secret("web_token","");
        else if(p.has("webToken")&&!p.getString("webToken").trim().isEmpty())secret("web_token",p.getString("webToken").trim());
    }
    synchronized JSONObject saveWebSettings(JSONObject p) throws Exception {validateWebSettings(p);persistWebSettings(p);return config();}
    JSONArray enabledPlugins(){try{return LocalCapabilities.plugins(new JSONArray(get("enabled_plugins",LocalCapabilities.defaultPlugins().toString())));}catch(Exception e){return new JSONArray();}}
    JSONArray allowedApps(){try{return new JSONArray(get("allowed_apps","[]"));}catch(Exception e){return new JSONArray();}}
    boolean allowed(String pkg){return OwnerApprovalPolicy.allowsApp(deviceScope(),pkg,PhoneAccessibilityService.protectedPackage(pkg));}
    // Package scope only: Settings screen reads/actions must still pass the service's fresh surface checks.
    // Keep allowed() unchanged for launching and force-stop protection.
    boolean allowedScreen(String pkg){return OwnerApprovalPolicy.allowsApp(deviceScope(),pkg,!"com.android.settings".equals(pkg)&&PhoneAccessibilityService.protectedPackage(pkg));}
    synchronized String newSession(String title){String id=UUID.randomUUID().toString();ContentValues v=new ContentValues();v.put("id",id);v.put("title",J.clipped(title.isEmpty()?"새로운 대화":title,70));v.put("created",System.currentTimeMillis());getWritableDatabase().insertOrThrow("sessions",null,v);return id;}
    synchronized boolean exists(String sid){try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM sessions WHERE id=?",new String[]{sid})){return c.moveToFirst();}}
    synchronized JSONObject message(String sid,String role,String body){return message(sid,role,body,"");}
    synchronized JSONObject message(String sid,String role,String body,String runId){return message(sid,role,body,runId,0);}
    synchronized JSONObject message(String sid,String role,String body,String runId,long reservedOrder){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            long created=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("sid",sid);v.put("role",role);v.put("body",J.clipped(body,150000));v.put("created",created);v.put("run_id",runId);
            long id=db.insertOrThrow("messages",null,v),order=reservedOrder>0?reservedOrder:allocateTimeline(db,sid,"messages",String.valueOf(id));v=new ContentValues();v.put("timeline_order",order);db.update("messages",v,"id=?",new String[]{String.valueOf(id)});db.setTransactionSuccessful();
            return J.obj("messageId",id,"id",id,"runId",runId,"role",role,"content",J.clipped(body,150000),"created",created,"timelineOrder",order);
        }finally{db.endTransaction();}
    }
    synchronized JSONArray messages(String sid){JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT role,body,created,id,timeline_order,run_id FROM messages WHERE sid=? ORDER BY timeline_order",new String[]{sid})){while(c.moveToNext())a.put(J.obj("role",c.getString(0),"content",c.getString(1),"created",c.getLong(2),"id",c.getLong(3),"messageId",c.getLong(3),"timelineOrder",c.getLong(4),"runId",c.getString(5)));}return a;}
    synchronized JSONArray sessions(){JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT s.id,s.title,s.created,(SELECT COUNT(*) FROM messages m WHERE m.sid=s.id) FROM sessions s ORDER BY s.created DESC LIMIT 200",null)){while(c.moveToNext())a.put(J.obj("id",c.getString(0),"title",c.getString(1),"created",c.getLong(2),"count",c.getInt(3)));}return a;}
    synchronized JSONArray searchSessions(String query,int limit){
        query=query.trim();if(query.isEmpty()||query.length()>200)throw new IllegalArgumentException("기록 검색어는 1~200자로 입력하세요.");
        if(limit<1||limit>10)throw new IllegalArgumentException("기록 검색 결과는 1~10개로 설정하세요.");
        String escaped=query.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
        JSONArray results=new JSONArray();
        String sql="SELECT m.sid,s.title,m.role,m.body,m.created FROM messages m JOIN sessions s ON s.id=m.sid WHERE m.body LIKE ? ESCAPE '\\' ORDER BY m.id DESC LIMIT ?";
        try(Cursor cursor=getReadableDatabase().rawQuery(sql,new String[]{"%"+escaped+"%",String.valueOf(limit)})){
            while(cursor.moveToNext()){
                String body=cursor.getString(3);int position=body.toLowerCase(Locale.ROOT).indexOf(query.toLowerCase(Locale.ROOT));
                int start=Math.max(0,position-200);String excerpt=body.substring(start,Math.min(body.length(),start+800));
                results.put(J.obj("sid",cursor.getString(0),"title",cursor.getString(1),"role",cursor.getString(2),"excerpt",excerpt,"created",cursor.getLong(4)));
            }
        }
        return results;
    }
    synchronized JSONArray transcript(String sid){try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM transcripts WHERE sid=?",new String[]{sid})){if(c.moveToFirst())return new JSONArray(c.getString(0));}catch(JSONException e){throw new IllegalStateException("대화 기록이 손상되었습니다.",e);}return new JSONArray();}
    synchronized void transcript(String sid,JSONArray a){ContentValues v=new ContentValues();v.put("sid",sid);v.put("body",a.toString());getWritableDatabase().insertWithOnConflict("transcripts",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    synchronized void removeSession(String sid){prefs.edit().remove("compact:"+sid).remove("plan:"+sid).apply();SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.delete("timeline_clock","sid=?",new String[]{sid});db.delete("provider_thoughts","sid=?",new String[]{sid});db.delete("activities","sid=?",new String[]{sid});db.delete("messages","sid=?",new String[]{sid});db.delete("transcripts","sid=?",new String[]{sid});db.delete("sessions","id=?",new String[]{sid});db.setTransactionSuccessful();}finally{db.endTransaction();}}
    synchronized void audit(String tool,String status,String detail){ContentValues v=new ContentValues();v.put("tool",tool);v.put("status",status);v.put("detail",J.clipped(detail,500));v.put("created",System.currentTimeMillis());SQLiteDatabase db=getWritableDatabase();db.insertOrThrow("audit",null,v);db.execSQL("DELETE FROM audit WHERE id NOT IN (SELECT id FROM audit ORDER BY id DESC LIMIT 500)");}
    synchronized JSONArray audit(){JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT tool,status,detail,created FROM audit ORDER BY id DESC LIMIT 100",null)){while(c.moveToNext())a.put(J.obj("tool",c.getString(0),"status",c.getString(1),"detail",c.getString(2),"created",c.getLong(3)));}return a;}
}
