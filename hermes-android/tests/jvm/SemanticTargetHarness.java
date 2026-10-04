package dev.chanho.hermes;
import org.json.*;

/** Tests the production selector and recoverable guard, not a fabricated Android tap. */
public final class SemanticTargetHarness {
    interface Checked {void run() throws Exception;}
    static int passed;
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static void test(String name,Checked work) throws Exception {work.run();passed++;System.out.println("PASS "+name);}
    static void rejects(Checked work) throws Exception {try{work.run();throw new AssertionError("expected rejection");}catch(IllegalArgumentException|SecurityException expected){}}
    static JSONObject request(){return J.obj("action","click","expected_package","dev.fixture","resource_id","dev.fixture:id/button");}
    static JSONObject node(String id,String text){return J.obj("id",id,"text",text,"description","","resourceId","dev.fixture:id/button","enabled",true,"clickable",true);}
    static JSONObject screen(JSONObject... nodes){JSONArray e=new JSONArray();for(JSONObject n:nodes)e.put(n);return J.obj("package","dev.fixture","snapshot","fresh-native-snapshot","elements",e);}
    public static void main(String[] args) throws Exception {
        test("exact target selection carries the new native snapshot, never old coordinates",()->{
            JSONObject r=request(),s=screen(node("e7","Go"));JSONArray m=SemanticTarget.matches(r,s);
            check(m.length()==1,"unique match");JSONObject a=SemanticTarget.arguments(r,s,m.getJSONObject(0));
            check(a.getString("snapshot").equals("fresh-native-snapshot")&&a.getString("element").equals("e7")&&!a.has("x"),"new identity");
        });
        test("resource ID plus exact label disambiguates repeated native IDs",()->{
            JSONObject r=request();r.put("label","Second");JSONArray m=SemanticTarget.matches(r,screen(node("e0","First"),node("e1","Second")));
            check(m.length()==1&&m.getJSONObject(0).getString("id").equals("e1"),"label constraint");
        });
        test("ambiguous matches remain ambiguous instead of selecting the first",()->check(SemanticTarget.matches(request(),screen(node("e0","Same"),node("e1","Same"))).length()==2,"two candidates"));
        test("a different foreground package is rejected",()->{JSONObject s=screen(node("e0","Go"));s.put("package","dev.other");rejects(()->SemanticTarget.matches(request(),s));});
        test("disabled and nonactionable nodes cannot be clicked",()->{
            JSONObject disabled=node("e0","Go"),passive=node("e1","Go");disabled.put("enabled",false);passive.put("clickable",false);
            check(SemanticTarget.matches(request(),screen(disabled,passive)).length()==0,"no clickable match");
        });
        test("old snapshot and coordinate parameters cannot enter the semantic tool",()->{
            JSONObject r=request();r.put("snapshot","old");rejects(()->SemanticTarget.validate(r));r.remove("snapshot");r.put("x",10);rejects(()->SemanticTarget.validate(r));
        });
        test("type and scroll arguments are disjoint and translated to real native tools",()->{
            JSONObject r=request();r.put("action","type");rejects(()->SemanticTarget.validate(r));r.put("input","새 입력");SemanticTarget.validate(r);
            JSONObject a=SemanticTarget.arguments(r,screen(node("e0","")),node("e0",""));check(a.getString("text").equals("새 입력"),"input mapping");
            r.put("direction","down");rejects(()->SemanticTarget.validate(r));check(SemanticTarget.nativeAction("scroll").equals("scroll_element"),"scroll mapping");
        });
        test("freshness guard keeps its exact 45000 millisecond boundary",()->{
            ObservationRequired.checkSnapshot("a","a",45000);
            for(long age:new long[]{45001,-1}){try{ObservationRequired.checkSnapshot("a","a",age);throw new AssertionError("expiry bypass");}catch(ObservationRequired expected){check(expected.result().getString("reason").equals("snapshot_expired"),"expiry reason");}}
        });
        test("replaced snapshots report typed recovery without claiming all prior effects undone",()->{
            try{ObservationRequired.checkSnapshot("old","new",0);throw new AssertionError("identity bypass");}catch(ObservationRequired e){JSONObject r=e.result();check(!r.getBoolean("ok")&&r.getBoolean("recoverable")&&!r.has("dispatched"),"honest partial effect boundary");check(!r.getJSONObject("recovery").getBoolean("retrySameArguments"),"no blind retry");}
            check(ObservationRequired.changed().result().getString("reason").equals("surface_changed"),"surface reason");
        });
        test("semantic actions invalidate old pixels and summaries omit typed private input",()->{
            check(DirectAgent.invalidatesScreenObservation("act_on_screen"),"pixel invalidation");JSONObject r=request();r.put("action","type");r.put("input","private-fixture-text");r.put("label","private-label");
            String text=DirectAgent.toolArgumentSummary("act_on_screen",r);check(!text.contains("private-fixture-text")&&!text.contains("private-label"),"summary privacy");
        });
        test("screen plugin authority is required even when agent workers are enabled",()->{
            check(!LocalCapabilities.allowed("act_on_screen",J.obj("enabledPlugins",J.arr("agent"))),"worker plugin cannot enable screen");
            check(LocalCapabilities.allowed("act_on_screen",J.obj("enabledPlugins",J.arr("screen"))),"screen plugin gates schema");
        });
        test("partial and case-folded label matches are not silently accepted",()->{
            JSONObject r=request();r.put("label","go");check(SemanticTarget.matches(r,screen(node("e0","Go"))).length()==0,"case-sensitive exact match");
            r.put("label","G");check(SemanticTarget.matches(r,screen(node("e0","Go"))).length()==0,"no prefix fallback");
        });
        System.out.println("SemanticTargetHarness: "+passed+" production selector/guard checks. Actual Android action dispatch is tested separately.");
    }
}
