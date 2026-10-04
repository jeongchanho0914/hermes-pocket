package dev.chanho.hermes;
import org.json.*;
import java.lang.reflect.*;
import java.util.*;
import sun.misc.Unsafe;
/** Runs real Android-source validators against SDK types; never invokes Android framework behavior. */
public final class PhoneContractsHarness {
 interface Work{void run()throws Exception;} static int passed;static DeviceTools device;static Method validate;
 static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
 static void test(String name,Work work)throws Exception{work.run();passed++;System.out.println("PASS "+name);}
 static void reject(Work w)throws Exception{try{w.run();}catch(IllegalArgumentException|SecurityException expected){return;}throw new AssertionError("Invalid native arguments accepted");}
 static void device(String name,JSONObject args)throws Exception{try{validate.invoke(device,name,args);}catch(InvocationTargetException e){throw (Exception)e.getCause();}}
 public static void main(String[] unused)throws Exception{
  Field field=Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);device=(DeviceTools)((Unsafe)field.get(null)).allocateInstance(DeviceTools.class);validate=DeviceTools.class.getDeclaredMethod("validate",String.class,JSONObject.class);validate.setAccessible(true);
  test("actual production inventory advertises the new phone tools only through their enabled native plugins",()->{
   Set<String> enabled=new HashSet<>();JSONArray tools=LocalCapabilities.schemas(new JSONObject());for(int i=0;i<tools.length();i++)enabled.add(tools.getJSONObject(i).getJSONObject("function").getString("name"));
   for(String name:new String[]{"long_click_element","set_element_progress","perform_phone_action","open_link","open_map","share_text","compose_message","set_alarm"}){
    check(enabled.contains(name)&&LocalCapabilities.allowed(name,new JSONObject()),"actual native inventory omitted new tool: "+name);
    check(!LocalCapabilities.allowed(name,J.obj("enabledPlugins",J.arr())),"disabled plugin retained new tool: "+name);
   }
   for(String name:new String[]{"long_click_element","set_element_progress"})check(LocalCapabilities.allowed(name,J.obj("enabledPlugins",J.arr("screen")))&&!LocalCapabilities.allowed(name,J.obj("enabledPlugins",J.arr("device"))),"element action mapped to wrong plugin");
   for(String name:new String[]{"perform_phone_action","open_link","open_map","share_text","compose_message","set_alarm"})check(LocalCapabilities.allowed(name,J.obj("enabledPlugins",J.arr("device")))&&!LocalCapabilities.allowed(name,J.obj("enabledPlugins",J.arr("screen"))),"ordinary phone action mapped to wrong plugin");
  });
  test("real browser search validator accepts bounded Unicode query but rejects private router extras and wrong typed options",()->{
   BrowserSearch.validate(J.obj("query","서울 a&b + literal?","inspect_screen",false));BrowserSearch.validate(J.obj("query","a".repeat(500)));
   for(JSONObject bad:new JSONObject[]{J.obj("query"," "),J.obj("query","a".repeat(501)),J.obj("query","x\u0000y"),J.obj("query",1),J.obj("query","x","inspect_screen","true"),J.obj("query","x","headers",new JSONObject()),J.obj("query","x","mode","api")})reject(()->BrowserSearch.validate(bad));
   check(LocalCapabilities.allowed("browser_search",J.obj("enabledPlugins",J.arr("web")))&&!LocalCapabilities.allowed("browser_search",J.obj("enabledPlugins",J.arr("device"))),"browser tool schema plugin mapping wrong");
  });
  test("production volume validator accepts four ordinary streams and default media but rejects calls aliases and noninteger bounds",()->{
   device("set_volume",J.obj("percent",0));device("set_volume",J.obj("percent",100));for(String stream:new String[]{"media","ring","alarm","notification"})device("set_volume",J.obj("percent",50,"stream",stream));
   for(Object value:new Object[]{-1,101,0.5,"50",true,JSONObject.NULL})reject(()->device("set_volume",J.obj("percent",value)));
   for(String stream:new String[]{"call","voice_call","music","system","","MEDIA"})reject(()->device("set_volume",J.obj("percent",50,"stream",stream)));
   reject(()->device("set_volume",J.obj("percent",50,"package","private")));
  });
  test("production phone settings enforce rotation orientation and disjoint boolean timeout arguments",()->{
   for(String orientation:new String[]{"portrait","landscape"})device("set_phone_setting",J.obj("setting","rotation_lock","orientation",orientation));
   for(JSONObject a:new JSONObject[]{J.obj("setting","rotation_lock"),J.obj("setting","rotation_lock","orientation","reverse"),J.obj("setting","rotation_lock","orientation","portrait","enabled",false),J.obj("setting","rotation_lock","orientation","portrait","seconds",15),J.obj("setting","auto_rotate","enabled",true,"orientation","portrait"),J.obj("setting","screen_timeout_seconds","seconds",14),J.obj("setting","screen_timeout_seconds","seconds",1801),J.obj("setting","brightness_auto","enabled","true")})reject(()->device("set_phone_setting",a));
   for(int seconds:new int[]{15,1800})device("set_phone_setting",J.obj("setting","screen_timeout_seconds","seconds",seconds));for(String setting:new String[]{"auto_rotate","brightness_auto"})device("set_phone_setting",J.obj("setting",setting,"enabled",false));
  });
  test("real accessibility schemas require fresh snapshot identity and finite slider value and allow only ordinary phone actions",()->{
   Set<String> names=new HashSet<>();JSONArray tools=DeviceTools.schemas();for(int i=0;i<tools.length();i++)names.add(tools.getJSONObject(i).getJSONObject("function").getString("name"));check(names.containsAll(Arrays.asList("long_click_element","set_element_progress","perform_phone_action")),"new native schemas missing");
   device("long_click_element",J.obj("snapshot","fresh-id","element","e1"));device("set_element_progress",J.obj("snapshot","fresh-id","element","e1","value",0.125));
   for(Object value:new Object[]{"0.125",true,JSONObject.NULL})reject(()->device("set_element_progress",J.obj("snapshot","fresh-id","element","e1","value",value)));
   JSONObject infinity=J.obj("snapshot","fresh-id","element","e1");infinity.put("value",new Number(){public int intValue(){return 0;}public long longValue(){return 0;}public float floatValue(){return Float.POSITIVE_INFINITY;}public double doubleValue(){return Double.POSITIVE_INFINITY;}});reject(()->device("set_element_progress",infinity));
   for(String action:new String[]{"recent_apps","notifications","quick_settings","dismiss_shade"})device("perform_phone_action",J.obj("snapshot","fresh-id","action",action));
   for(String action:new String[]{"unlock","accept_permission","power","screenshot","toggle_wifi"})reject(()->device("perform_phone_action",J.obj("snapshot","fresh-id","action",action)));reject(()->device("perform_phone_action",J.obj("action","notifications")));
  });
  test("actual ordinary intent validation rejects unsafe link schemes credentials and ports",()->{
   for(String url:new String[]{"https://example.org/path?q=한글","http://example.org:8080"})PhoneIntentTools.validate("open_link",J.obj("url",url));
   for(String url:new String[]{"file:///sdcard/private","intent://launch","javascript:alert(1)","https://user:secret@example.org","https://example.org:65536","https:///missing","https://example.org\nprivate"})reject(()->PhoneIntentTools.validate("open_link",J.obj("url",url)));
  });
  test("real map and share validators enforce coordinate bounds query exclusivity text types and resource limits",()->{
   PhoneIntentTools.validate("open_map",J.obj("query","서울 station"));PhoneIntentTools.validate("open_map",J.obj("latitude",-90,"longitude",180));PhoneIntentTools.validate("share_text",J.obj("text","한글 <literal>","subject","Owner choice"));
   for(JSONObject a:new JSONObject[]{new JSONObject(),J.obj("query"," "),J.obj("latitude",1),J.obj("latitude",91,"longitude",0),J.obj("latitude",0,"longitude",181),J.obj("latitude","0","longitude",0),J.obj("query","place","latitude",0,"longitude",0)})reject(()->PhoneIntentTools.validate("open_map",a));
   for(Object text:new Object[]{" ","x".repeat(16001),true,JSONObject.NULL,"with\0NUL"})reject(()->PhoneIntentTools.validate("share_text",J.obj("text",text)));
  });
  test("real draft and alarm validators allow safe SMS email dial and bounded integers while refusing calls sending MMI and recipient injection",()->{
   PhoneIntentTools.validate("compose_message",J.obj("kind","sms","recipient","+82 10-1234-5678","text","draft"));PhoneIntentTools.validate("compose_message",J.obj("kind","email","recipient","owner@example.org","subject","Draft","text","hello"));PhoneIntentTools.validate("compose_message",J.obj("kind","dial","recipient","+821012345678"));
   for(JSONObject a:new JSONObject[]{J.obj("kind","call","recipient","123"),J.obj("kind","dial","recipient","*#06#"),J.obj("kind","dial","recipient","123;456"),J.obj("kind","dial","recipient","123","text","send"),J.obj("kind","sms","recipient","123","subject","unsupported"),J.obj("kind","email","recipient","a@example.org,b@example.org"),J.obj("kind","sms","recipient","123","send",true)})reject(()->PhoneIntentTools.validate("compose_message",a));
   PhoneIntentTools.validate("set_alarm",J.obj("hour",0,"minute",0));PhoneIntentTools.validate("set_alarm",J.obj("hour",23,"minute",59,"label","Owner confirmation"));
   for(JSONObject a:new JSONObject[]{J.obj("hour",24,"minute",0),J.obj("hour",0,"minute",60),J.obj("hour",0.1,"minute",0),J.obj("hour","1","minute",0),J.obj("hour",1,"minute",1,"skip_ui",true)})reject(()->PhoneIntentTools.validate("set_alarm",a));
  });
  System.out.println("PhoneContracts: "+passed+" actual production validator/schema checks; no Android dispatch or permission proof.");
 }
}
