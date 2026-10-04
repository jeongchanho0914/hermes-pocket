package dev.chanho.hermes;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Runs production process ownership/output code against real HOST /bin/sh.
 * This proves host process behavior, never Android shell or root capabilities. */
public final class TerminalSessionsHarness {
    interface Work{void run()throws Exception;}
    static int passed;
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void test(String name,Work work)throws Exception{work.run();passed++;System.out.println("PASS "+name);}
    static Exception failure(Work work)throws Exception{try{work.run();}catch(Exception expected){return expected;}throw new AssertionError("Invalid terminal operation accepted");}
    static Map<String,Object> args(Object... pairs){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;}
    static TerminalSessions create()throws Exception{return new TerminalSessions(Files.createTempDirectory("hermes-host-shell-").toFile(),"/bin/sh","fixture");}
    static String id(Map<String,Object> job){return(String)job.get("session_id");}
    static String output(Map<String,Object> result){return(String)result.get("output");}
    static Map<String,Object> waitFor(TerminalSessions sessions,String id)throws Exception{return sessions.process(args("action","wait","session_id",id,"timeout",3));}
    static Map<String,Object> pollUntil(TerminalSessions sessions,String id,String text)throws Exception{
        long end=System.nanoTime()+3_000_000_000L;Map<String,Object> result=null;StringBuilder seen=new StringBuilder();
        while(System.nanoTime()<end){result=sessions.process(args("action","poll","session_id",id));seen.append(output(result));if(seen.toString().contains(text)){result.put("output",seen.toString());return result;}Thread.sleep(10);}
        throw new AssertionError("Expected real shell output not received: "+text+"; "+seen);
    }
    public static void main(String[] unused)throws Exception{
        test("real host shell merges stdout stderr and reports nonzero exit without fabricated success",()->{
            try(TerminalSessions sessions=create()){
                Map<String,Object> result=sessions.terminal(args("command","printf 'stdout 한글\\n'; printf 'stderr diagnostic\\n' >&2; exit 7"));
                check(output(result).contains("stdout 한글")&&output(result).contains("stderr diagnostic"),"stdout/stderr missing");
                check(Integer.valueOf(7).equals(result.get("exit_code"))&&Boolean.FALSE.equals(result.get("running")),"exit code/running state fabricated");
                check("exited".equals(result.get("status"))&&Boolean.FALSE.equals(result.get("pty")),"pipe session falsely presented as PTY");
            }
        });
        test("explicit cwd and private HOME TMPDIR are real shell environment",()->{
            try(TerminalSessions sessions=create()){
                Path cwd=Files.createTempDirectory("hermes-host-cwd-");
                Map<String,Object> result=sessions.terminal(args("command","pwd; printf '%s\\n' \"$HOME\" \"$TMPDIR\"","workdir",cwd.toString()));
                check(output(result).equals(cwd.toRealPath()+"\n"+sessions.workspace()+"\n"+sessions.workspace()+"\n"),"cwd or shell HOME/TMPDIR differs from requested workspace");
                check(result.get("workdir").equals(cwd.toRealPath().toString()),"reported cwd differs from actual directory");
            }
        });
        test("foreground cd and exported multiline environment persist across actual shell calls",()->{
            try(TerminalSessions sessions=create()){
                Path changed=Files.createTempDirectory("hermes-host-persist-");
                Map<String,Object> first=sessions.terminal(args("command","cd "+changed+"; export HERMES_FIXTURE_VALUE='first\nsecond 한글'"));
                check(Boolean.TRUE.equals(first.get("cwd_persistence"))&&Boolean.TRUE.equals(first.get("environment_persistence")),"normal foreground state not persisted");
                Map<String,Object> next=sessions.terminal(args("command","pwd; printf '%s' \"$HERMES_FIXTURE_VALUE\""));
                check(output(next).equals(changed.toRealPath()+"\nfirst\nsecond 한글"),"cwd or multiline exported env lost between real commands");
                check(next.get("workdir").equals(changed.toRealPath().toString()),"reported subsequent cwd wrong");
            }
        });
        test("background work explicit cwd and early exit cannot silently clobber foreground state",()->{
            try(TerminalSessions sessions=create()){
                Path main=Files.createTempDirectory("hermes-host-main-");Path other=Files.createTempDirectory("hermes-host-other-");
                sessions.terminal(args("command","cd "+main+"; export HERMES_FIXTURE_VALUE=foreground"));
                Map<String,Object> background=sessions.terminal(args("command","cd "+other+"; export HERMES_FIXTURE_VALUE=background","background",true));waitFor(sessions,id(background));
                Map<String,Object> early=sessions.terminal(args("command","cd "+other+"; export HERMES_FIXTURE_VALUE=early; exit 3"));
                check(Boolean.FALSE.equals(early.get("cwd_persistence"))&&Boolean.FALSE.equals(early.get("environment_persistence")),"early exit falsely claims captured state");
                sessions.terminal(args("command","pwd","workdir",other.toString()));
                Map<String,Object> observed=sessions.terminal(args("command","pwd; printf '%s' \"$HERMES_FIXTURE_VALUE\""));
                check(output(observed).equals(main.toRealPath()+"\nforeground"),"background/explicit cwd/early exit clobbered foreground state");
            }
        });
        test("background job accepts partial stdin write then submit and incremental poll does not duplicate output",()->{
            try(TerminalSessions sessions=create()){
                Map<String,Object> job=sessions.terminal(args("command","printf 'READY\\n'; IFS= read -r value; printf 'RECEIVED:%s\\n' \"$value\"","background",true,"timeout",10));
                String id=id(job);pollUntil(sessions,id,"READY");
                sessions.process(args("action","write","session_id",id,"data","partial "));
                sessions.process(args("action","submit","session_id",id,"data","한글🙂"));
                Map<String,Object> result=waitFor(sessions,id);check(output(result).contains("RECEIVED:partial 한글🙂"),"write/submit did not reach real stdin");
                check(Integer.valueOf(0).equals(result.get("exit_code")),"stdin shell did not complete");
                check(output(sessions.process(args("action","poll","session_id",id))).isEmpty(),"incremental poll replays already delivered output");
                check(output(sessions.process(args("action","log","session_id",id,"offset",0,"limit",10))).contains("READY"),"log cannot retrieve retained output after poll");
                failure(()->sessions.process(args("action","submit","session_id",id,"data","after exit")));
                Map<String,Object> empty=sessions.terminal(args("command","IFS= read -r value; printf 'EMPTY:%s' \"$value\"","background",true));
                sessions.process(args("action","submit","session_id",id(empty),"data",""));check(output(waitFor(sessions,id(empty))).equals("EMPTY:"),"blank submit did not send newline");
            }
        });
        test("deadline terminates real blocked shell and distinguishes timeout from normal exit",()->{
            try(TerminalSessions sessions=create()){
                long start=System.nanoTime();Map<String,Object> result=sessions.terminal(args("command","printf 'before-timeout\\n'; IFS= read -r value","timeout",1));
                check("timeout".equals(result.get("status"))&&Boolean.FALSE.equals(result.get("running")),"timed-out shell reported running or successful");
                check(output(result).contains("before-timeout")&&result.get("exit_code")!=null,"timeout loses observed output or actual exit");
                check(System.nanoTime()-start<4_000_000_000L,"timeout failed to stop real process promptly");
            }
        });
        test("request cancellation stops owned job and cannot leak to independent request or instance",()->{
            try(TerminalSessions sessions=create();TerminalSessions other=create()){
                Map<String,Object> first=sessions.terminal(args("command","IFS= read -r value","background",true,"timeout",10,"requestId","cancel-me"));
                Map<String,Object> survivor=sessions.terminal(args("command","IFS= read -r value; printf 'SURVIVED:%s' \"$value\"","background",true,"timeout",10,"requestId","keep-me"));
                failure(()->other.process(args("action","kill","session_id",id(survivor))));
                sessions.cancelRequest("cancel-me");Map<String,Object> cancelled=waitFor(sessions,id(first));
                check("cancelled".equals(cancelled.get("status"))&&Boolean.FALSE.equals(cancelled.get("running")),"cancelled owned process survives");
                check(failure(()->sessions.terminal(args("command","printf should-not-run","requestId","cancel-me"))) instanceof InterruptedException,"cancelled request restarted");
                sessions.process(args("action","submit","session_id",id(survivor),"data","safe"));check(output(waitFor(sessions,id(survivor))).contains("SURVIVED:safe"),"request cancellation killed independent job");
                check(((List<?>)other.process(args("action","list")).get("sessions")).isEmpty(),"jobs leak across manager instances");
            }
        });
        test("cancellation kills real child and grandchild process group without leaving executing descendants",()->{
            List<ProcessHandle> descendants=new ArrayList<>();
            try(TerminalSessions sessions=create()){
                Map<String,Object> job=sessions.terminal(args("command","sh -c 'sleep 60 & printf \"GRANDCHILD:%s\\n\" \"$!\"; wait' & printf 'CHILD:%s\\n' \"$!\"; wait","background",true,"timeout",10));
                Map<String,Object> observed=pollUntil(sessions,id(job),"GRANDCHILD:");
                String log=output(observed);if(!log.contains("CHILD:"))log+=output(pollUntil(sessions,id(job),"CHILD:"));
                java.util.regex.Matcher pids=java.util.regex.Pattern.compile("(?:CHILD|GRANDCHILD):([0-9]+)").matcher(log);
                while(pids.find()){long pid=Long.parseLong(pids.group(1));descendants.add(ProcessHandle.of(pid).orElseThrow());}
                check(descendants.size()==2,"fixture did not create child and grandchild");sessions.cancelAll();waitFor(sessions,id(job));
                long end=System.nanoTime()+3_000_000_000L;boolean executing;
                do{executing=false;for(ProcessHandle child:descendants){Path stat=Path.of("/proc",Long.toString(child.pid()),"stat");if(Files.exists(stat)){try{String raw=Files.readString(stat);char state=raw.charAt(raw.lastIndexOf(')')+2);executing|=state!='Z'&&state!='X';}catch(NoSuchFileException racedExit){}}}if(executing)Thread.sleep(20);}while(executing&&System.nanoTime()<end);
                check(!executing,"cancelled shell left an executing child/grandchild");
            }finally{for(ProcessHandle child:descendants)if(child.isAlive())child.destroyForcibly();}
        });
        test("bounded retained output reports loss and Unicode clipping never splits surrogate pair",()->{
            try(TerminalSessions sessions=create()){
                Map<String,Object> large=sessions.terminal(args("command","i=0; while [ \"$i\" -lt 22000 ]; do printf abcd; i=$((i+1)); done","max_output_chars",100));
                check(output(large).length()<=100&&Boolean.TRUE.equals(large.get("truncated"))&&((Number)large.get("output_chars_dropped")).longValue()>0,"output retained without bounds/loss reporting");
                Map<String,Object> unicode=sessions.terminal(args("command","printf '🙂🙂🙂'","max_output_chars",3));String text=output(unicode);
                check(text.equals("🙂")&&!Character.isLowSurrogate(text.charAt(0))&&Boolean.TRUE.equals(unicode.get("truncated")),"Unicode tail cap split UTF16 pair");
            }
        });
        test("invalid ids cwd numeric boolean text and PTY requests fail without starting jobs",()->{
            try(TerminalSessions sessions=create()){
                for(Map<String,Object> bad:Arrays.asList(args("command",17),args("command",""),args("command","x\0y"),args("command","printf x","background","true"),args("command","printf x","timeout",1.5),args("command","printf x","timeout",0),args("command","printf x","max_output_chars",32769),args("command","printf x","workdir","relative"),args("command","printf x","workdir","/does-not-exist-hermes-fixture")))failure(()->sessions.terminal(bad));
                Exception pty=failure(()->sessions.terminal(args("command","printf x","pty",true)));check(pty.getMessage().contains("PTY")&&pty.getMessage().contains("unsupported"),"unsupported PTY falsely accepted");
                failure(()->sessions.process(args("action","kill","session_id","proc_not-owned")));
                check(((List<?>)sessions.process(args("action","list")).get("sessions")).isEmpty(),"invalid requests started jobs");
            }
        });
        test("kill and close own lifecycle while completed jobs remain discoverable",()->{
            TerminalSessions sessions=create();
            try{
                Map<String,Object> job=sessions.terminal(args("command","IFS= read -r value","background",true,"timeout",10));
                Map<String,Object> killed=sessions.process(args("action","kill","session_id",id(job)));
                check("killed".equals(killed.get("status"))&&Boolean.FALSE.equals(killed.get("running")),"kill did not reap actual job");
                check(((List<?>)sessions.process(args("action","list")).get("sessions")).size()==1,"completed job omitted from list");
                Map<String,Object> stdin=sessions.terminal(args("command","IFS= read -r value; printf 'EOF:%s' \"$?\"","background",true));
                sessions.process(args("action","close","session_id",id(stdin)));check(output(waitFor(sessions,id(stdin))).equals("EOF:1"),"close did not deliver real EOF to stdin");
                sessions.close();check(failure(()->sessions.terminal(args("command","printf no"))) instanceof InterruptedException,"closed manager started process");
            }finally{sessions.close();}
        });
        System.out.println("TerminalSessions: "+passed+" production process checks passed using real HOST /bin/sh; no Android/root proof.");
    }
}
