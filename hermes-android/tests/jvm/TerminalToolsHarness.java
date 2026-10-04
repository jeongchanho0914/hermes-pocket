package dev.chanho.hermes;

import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Production approval/gate adapter plus real host shell; Android state is explicit fixture. */
public final class TerminalToolsHarness {
    interface Work{void run()throws Exception;}
    static int passed;
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void test(String name,Work work)throws Exception{work.run();passed++;System.out.println("PASS "+name);}
    static Exception failure(Work work)throws Exception{try{work.run();}catch(Exception expected){return expected;}throw new AssertionError("Terminal boundary accepted rejected request");}
    static TerminalTools tools(AgentRuntime runtime)throws Exception{return new TerminalTools(runtime,new TerminalSessions(Files.createTempDirectory("hermes-gated-host-").toFile(),"/bin/sh","gate"));}
    static JSONObject run(String command){return J.obj("command",command,"timeout",3);}
    static void noAuditText(AgentRuntime runtime,String text){check(!runtime.store.audits.toString().contains(text),"command/output/input persisted in audit");}
    public static void main(String[] unused)throws Exception{
        test("denied native approval starts no process and writes no file",()->{
            AgentRuntime r=new AgentRuntime();r.approvals.allow=false;Path marker=r.context.getFilesDir().toPath().resolve("must-not-exist");
            try(TerminalTools tools=tools(r)){
                String command="printf private-command > "+marker;JSONObject result=tools.execute("terminal",run(command));
                check(result.getBoolean("denied")&&!Files.exists(marker)&&tools.state().getJSONArray("sessions").length()==0,"denied command executed");
                check(r.approvals.calls==1,"mutation did not use owner approval");noAuditText(r,"private-command");noAuditText(r,marker.toString());
            }
        });
        test("approved adapter returns actual output exit backend and untrusted provenance",()->{
            AgentRuntime r=new AgentRuntime();try(TerminalTools tools=tools(r)){
                JSONObject result=tools.execute("terminal",run("printf private-output; exit 9"));
                check(result.getString("output").equals("private-output")&&result.getInt("exit_code")==9,"real shell result fabricated");
                check(result.getString("backend").equals("app")&&result.getBoolean("untrusted")&&!result.getBoolean("pty"),"actual backend/pipes provenance wrong");
                noAuditText(r,"private-output");
            }
        });
        test("absent owner locked cancelled and disabled plugin reject before approval",()->{
            for(int kind=0;kind<4;kind++){
                AgentRuntime r=new AgentRuntime();if(kind==0)r.ownerBusy=false;if(kind==1)r.unlocked=false;if(kind==2)r.net.cancel();if(kind==3)r.store.put("fixture_config",J.obj("enabledPlugins",new JSONArray()).toString());
                try(TerminalTools tools=tools(r)){failure(()->tools.execute("terminal",run("printf forbidden")));check(r.approvals.calls==0&&tools.state().getJSONArray("sessions").length()==0,"gate prompted or spawned without live unlocked enabled owner");}
            }
        });
        test("approval wait rechecks cancellation lock plugin and approval-mode changes",()->{
            for(int kind=0;kind<4;kind++){
                AgentRuntime r=new AgentRuntime();final int change=kind;r.approvals.onAsk=()->{if(change==0)r.net.cancel();if(change==1)r.unlocked=false;if(change==2)r.store.put("fixture_config",J.obj("enabledPlugins",new JSONArray()).toString());if(change==3)r.store.put("approval_mode","auto");};
                try(TerminalTools tools=tools(r)){failure(()->tools.execute("terminal",run("printf stale-approval")));check(r.approvals.calls==1&&tools.state().getJSONArray("sessions").length()==0,"approval stale after native state change but command spawned");}
            }
        });
        test("typed schema malformed arguments and unsupported PTY never prompt approval",()->{
            AgentRuntime r=new AgentRuntime();try(TerminalTools tools=tools(r)){
                for(JSONObject args:Arrays.asList(J.obj("command","printf x","extra","ignored"),J.obj("command",12),J.obj("command","printf x","background","false"),J.obj("command","printf x","timeout",1.2),J.obj("command","printf x","pty",true)))failure(()->tools.execute("terminal",args));
                for(JSONObject args:Arrays.asList(J.obj("action","submit","session_id","absent"),J.obj("action","poll","session_id","absent","data","unexpected"),J.obj("action","kill"),J.obj("action","unknown")))failure(()->tools.execute("process_manage",args));
                check(r.approvals.calls==0&&tools.state().getJSONArray("sessions").length()==0,"invalid schema prompted/spawned");
            }
        });
        test("process reads skip mutation approval while stdin requires approval and explicit Shizuku never falls back",()->{
            AgentRuntime r=new AgentRuntime();try(TerminalTools tools=tools(r)){
                JSONObject job=tools.execute("terminal",J.obj("command","IFS= read -r value; printf '%s' \"$value\"","background",true,"timeout",10));String id=job.getString("session_id");int approvals=r.approvals.calls;
                tools.execute("process_manage",J.obj("action","list"));tools.execute("process_manage",J.obj("action","poll","session_id",id));check(r.approvals.calls==approvals,"process observations unexpectedly approve mutation");
                r.approvals.allow=false;check(tools.execute("process_manage",J.obj("action","submit","session_id",id,"data","secret-denied-input")).getBoolean("denied"),"stdin denial ignored");
                r.approvals.allow=true;tools.execute("process_manage",J.obj("action","submit","session_id",id,"data","real-private-input"));
                JSONObject waited=tools.execute("process_manage",J.obj("action","wait","session_id",id,"timeout",3));check(waited.getString("output").equals("real-private-input"),"denied stdin delivered or approved stdin lost");
                failure(()->tools.execute("terminal",J.obj("command","printf privileged-fallback","backend","shizuku")));
                check(r.shizuku.terminalCalls==0&&tools.state().getJSONArray("sessions").length()==1,"unavailable privileged backend fell back to app or invoked service");
                noAuditText(r,"secret-denied-input");noAuditText(r,"real-private-input");noAuditText(r,"privileged-fallback");
            }
        });
        test("background owner monitor terminates real jobs when phone locks",()->{
            AgentRuntime r=new AgentRuntime();try(TerminalTools tools=tools(r)){
                JSONObject job=tools.execute("terminal",J.obj("command","IFS= read -r value","background",true,"timeout",10));check(tools.hasRunningJobs(),"background job missing ownership");r.unlocked=false;
                long end=System.nanoTime()+3_000_000_000L;while(tools.hasRunningJobs()&&System.nanoTime()<end)Thread.sleep(20);
                check(!tools.hasRunningJobs(),"locked phone retained running background shell");
                JSONObject state=tools.state().getJSONArray("sessions").getJSONObject(0);check(state.getString("session_id").equals(job.getString("session_id"))&&state.getString("status").equals("cancelled"),"revoked job missing cancelled lifecycle");
            }
        });
        System.out.println("TerminalTools: "+passed+" production gate checks passed; owner/lock/Shizuku are fixtures and shell is HOST /bin/sh.");
    }
}
