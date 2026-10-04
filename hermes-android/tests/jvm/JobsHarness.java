package dev.chanho.hermes;

import org.json.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Production ledger, real worker threads and real localhost SSE; never a live model or phone account. */
public final class JobsHarness {
    interface Work{void run()throws Exception;}
    static int passed;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void test(String name,Work work)throws Exception{work.run();passed++;System.out.println("PASS "+name);}
    static void fails(Work work)throws Exception{try{work.run();}catch(Exception expected){return;}throw new AssertionError("Expected rejection");}
    static TaskLedger ledger()throws Exception{return new TaskLedger(Files.createTempDirectory("pocket-jobs-").toFile());}
    static void idle(TaskPool pool)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(pool.hasActive()&&System.nanoTime()<end)Thread.sleep(10);check(!pool.hasActive(),"Worker retained after completion");}
    static WorkerAgent.Tools tools(AtomicInteger calls){return new WorkerAgent.Tools(){
        public JSONArray schemas(){return J.arr(ToolArgs.schema("get_device_state","Fixture read-only metadata",J.obj()));}
        public JSONObject execute(String name,JSONObject args)throws Exception{calls.incrementAndGet();return J.obj("ok",true,"result","fixture read-only fact");}
    };}
    static TaskPool.Control control(TaskLedger ledger)throws Exception{return new TaskPool.Control(ledger.create("fixture","parent","worker:read-only"),10000,ledger,()->{});}
    public static void main(String[] args)throws Exception{
        test("logical job schema advertises bounded workers and no recursive or device permissions",()->{
            JSONArray all=ExtensionSchemas.jobs();check(all.length()==5,"Five implemented extensions expected");
            ToolArgs.validate("delegate_task",J.obj("task","한국어 작업","wait_seconds",30),all);
            fails(()->ToolArgs.validate("delegate_task",J.obj("task","x","wait_seconds",31),all));
            fails(()->ToolArgs.validate("delegate_task",J.obj("task","x","wait_seconds",1.2),all));
            fails(()->ToolArgs.validate("delegate_task",J.obj("task","x","device",true),all));
            fails(()->ToolArgs.validate("task_result",J.obj(),all));
        });
        test("durable job result survives reload with exact Unicode and no implicit restart",()->{
            File directory=Files.createTempDirectory("pocket-ledger-").toFile();TaskLedger ledger=new TaskLedger(directory);
            String done=ledger.create("완료 요청","session","worker:read-only"),running=ledger.create("진행 중","session","worker:read-only");
            ledger.update(done,"completed","완료","결과 한글 😀","",J.obj("modelRequests",2));
            ledger.update(running,"running","대기",null,null,null);TaskLedger restored=new TaskLedger(directory);
            check(restored.get(done).getString("result").equals("결과 한글 😀"),"Result changed");
            check(restored.get(running).getString("status").equals("interrupted"),"Uncertain job was not interrupted");
            check(restored.get(done).optInt("modelRequests")==2,"Usage metadata lost");
        });
        test("trusted Android-style parent alias is allowed but job-directory symlink is refused",()->{
            Path root=Files.createTempDirectory("pocket-alias-"),real=Files.createDirectory(root.resolve("real")),alias=root.resolve("alias");Files.createSymbolicLink(alias,real);
            TaskLedger valid=new TaskLedger(Files.createDirectories(alias.resolve("jobs")).toFile());check(valid.list(false).length()==0,"Trusted parent alias rejected");
            Path malicious=root.resolve("jobs-link");Files.createSymbolicLink(malicious,real.resolve("jobs"));fails(()->new TaskLedger(malicious.toFile()));
        });
        test("ledger rejects corrupt state without deleting evidence",()->{
            Path directory=Files.createTempDirectory("pocket-corrupt-");Files.writeString(directory.resolve("jobs.json"),"corrupt fixture");
            fails(()->new TaskLedger(directory.toFile()));check(Files.readString(directory.resolve("jobs.json")).equals("corrupt fixture"),"Corrupt evidence overwritten");
        });
        test("terminal job state cannot be overwritten by late completion or stale progress",()->{
            TaskLedger ledger=ledger();String id=ledger.create("task","s","worker:read-only");ledger.update(id,"cancelling","stop",null,null,null);ledger.update(id,"running","late",null,null,null);
            check(ledger.get(id).optString("status").equals("cancelling"),"Late progress undid cancellation");
            ledger.update(id,"cancelled","done",null,null,null);ledger.update(id,"completed","late","wrong",null,null);
            check(ledger.get(id).optString("status").equals("cancelled")&&ledger.get(id).optString("result").isEmpty(),"Late result overwrote cancellation");
        });
        test("active queue has a hard eight-job limit",()->{TaskLedger ledger=ledger();for(int i=0;i<8;i++)ledger.create("job"+i,"p","worker:read-only");fails(()->ledger.create("ninth","p","worker:read-only"));check(ledger.list(true).length()==8,"Queue grew beyond limit");});
        test("only two workers execute at once and queued cancellation never executes its body",()->{
            TaskPool pool=new TaskPool(ledger(),()->{});CountDownLatch entered=new CountDownLatch(2),release=new CountDownLatch(1);AtomicInteger running=new AtomicInteger(),maximum=new AtomicInteger(),thirdCalls=new AtomicInteger();
            TaskPool.Work work=c->{int count=running.incrementAndGet();maximum.accumulateAndGet(count,Math::max);entered.countDown();try{release.await();c.check();return c.id;}finally{running.decrementAndGet();}};
            try{pool.submit("one","p","worker:read-only",5000,work);pool.submit("two","p","worker:read-only",5000,work);check(entered.await(2,TimeUnit.SECONDS),"Two workers did not start");
                String third=pool.submit("third","p","worker:read-only",5000,c->{thirdCalls.incrementAndGet();return "bad";});check(pool.cancel(third),"Queued job did not cancel");release.countDown();idle(pool);
                check(maximum.get()==2&&thirdCalls.get()==0,"Concurrency limit or queued cancellation broken");check(pool.get(third).optString("status").equals("cancelled"),"Queued cancellation not durable");
            }finally{release.countDown();pool.shutdown();}
        });
        test("cancel acknowledgement does not release ownership before the actual worker unwinds",()->{
            TaskPool pool=new TaskPool(ledger(),()->{});CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger cancellation=new AtomicInteger();
            try{String id=pool.submit("unwind","p","worker:read-only",10000,c->{c.onCancel(cancellation::incrementAndGet);entered.countDown();while(release.getCount()>0){try{release.await();}catch(InterruptedException ignored){}}return "must not become success";});
                check(entered.await(2,TimeUnit.SECONDS),"Worker did not start");pool.cancel(id);check(pool.hasActive(),"Ownership dropped while code still running");check(pool.get(id).optString("status").equals("cancelling"),"Cancellation falsely reported finished");
                release.countDown();idle(pool);check(cancellation.get()>=1&&pool.get(id).optString("status").equals("cancelled"),"Unwound cancellation not recorded");
            }finally{release.countDown();pool.shutdown();}
        });
        test("one job cancellation leaves the other result intact",()->{
            TaskPool pool=new TaskPool(ledger(),()->{});CountDownLatch entered=new CountDownLatch(1);
            try{String one=pool.submit("cancel","p1","worker:read-only",5000,c->{entered.countDown();Thread.sleep(4000);return "bad";});check(entered.await(1,TimeUnit.SECONDS),"First not started");
                String two=pool.submit("keep","p2","worker:read-only",5000,c->"independent result");pool.cancel(one);idle(pool);check(pool.get(two).getString("result").equals("independent result"),"Other result lost");check(pool.get(two).getString("parentSession").equals("p2"),"Parent isolation lost");
            }finally{pool.shutdown();}
        });
        test("deadline stops a sleeping worker and records cancellation",()->{TaskPool pool=new TaskPool(ledger(),()->{});try{String id=pool.submit("timeout","p","worker:read-only",1000,c->{Thread.sleep(10000);return "bad";});idle(pool);check(pool.get(id).optString("status").equals("cancelled"),"Deadline ignored");}finally{pool.shutdown();}});
        test("real worker performs SSE read-tool-result roundtrip and records actual API request count",()->{
            AtomicInteger calls=new AtomicInteger();TaskLedger ledger=ledger();TaskPool.Control control=control(ledger);
            try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(DirectAgentHarness.tool("worker-call","{}"),DirectAgentHarness.text("실제 결과","stop"))){
                String result=WorkerAgent.run(control,"fixture task","fixture context",api.config(),"fixture-only-not-user-secret",tools(calls));
                check(result.equals("실제 결과")&&calls.get()==1&&api.requests.size()==2,"Worker loop did not execute exactly once");
                check(api.requests.get(1).getJSONArray("messages").toString().contains("fixture read-only fact"),"Real result not fed back");
                check(ledger.get(control.id).optInt("modelRequests")==2&&!ledger.get(control.id).optBoolean("usageKnown"),"Unknown usage was fabricated");
                check(!ledger.list(true).toString().contains("fixture-only-not-user-secret"),"Credential persisted in ledger");
            }
        });
        test("unknown later call rejects the whole worker batch before the first tool executes",()->{
            AtomicInteger calls=new AtomicInteger();JSONArray batch=J.arr(J.obj("index",0,"id","a","function",J.obj("name","get_device_state","arguments","{}")),J.obj("index",1,"id","b","function",J.obj("name","terminal","arguments","{}")));
            String wire=DirectAgentHarness.frame(J.obj("choices",J.arr(J.obj("delta",J.obj("tool_calls",batch),"finish_reason","tool_calls"))))+"data: [DONE]\n\n";
            try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(wire)){fails(()->WorkerAgent.run(control(ledger()),"fixture","",api.config(),"",tools(calls)));check(calls.get()==0,"Partial effect occurred before name validation");}
        });
        test("truncated worker stream never dispatches partial tools",()->{
            AtomicInteger calls=new AtomicInteger();String wire=DirectAgentHarness.tool("partial","{}").replace("data: [DONE]\n\n","");
            try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(wire)){fails(()->WorkerAgent.run(control(ledger()),"fixture","",api.config(),"",tools(calls)));check(calls.get()==0,"Partial SSE tool executed");}
        });
        test("HTTP authentication error is not automatically retried",()->{
            try(DirectAgentHarness.Api api=new DirectAgentHarness.Api("{}")){api.status=401;fails(()->WorkerAgent.run(control(ledger()),"fixture","",api.config(),"",tools(new AtomicInteger())));check(api.requests.size()==1,"Authentication request was replayed");}
        });
        test("cancelled worker does not send any API request",()->{try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(DirectAgentHarness.text("bad","stop"))){TaskPool.Control control=control(ledger());control.cancel();fails(()->WorkerAgent.run(control,"fixture","",api.config(),"",tools(new AtomicInteger())));check(api.requests.isEmpty(),"Cancelled worker sent API request");}});
        test("worker maximum rounds cannot be bypassed by a model ignoring tool_choice none",()->{
            String[] replies=new String[12];for(int i=0;i<replies.length;i++)replies[i]=DirectAgentHarness.tool("bounded-"+i,"{}");AtomicInteger calls=new AtomicInteger();
            try(DirectAgentHarness.Api api=new DirectAgentHarness.Api(replies)){fails(()->WorkerAgent.run(control(ledger()),"fixture","",api.config(),"",tools(calls)));check(api.requests.size()==12&&calls.get()==11,"Round cap changed or last forbidden call ran");check(api.requests.get(11).optString("tool_choice").equals("none"),"Last round did not disable tools");}
        });
        System.out.println("JobsHarness: "+passed+" production checks. Host/local API fixtures only; phone lifecycle is separate evidence.");
    }
}
