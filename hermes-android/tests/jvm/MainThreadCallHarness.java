package dev.chanho.hermes;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public final class MainThreadCallHarness {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        AtomicInteger actions=new AtomicInteger();AtomicBoolean cancelled=new AtomicBoolean();BlockingQueue<Runnable> queue=new LinkedBlockingQueue<>();
        try{MainThreadCall.call(queue::add,cancelled::get,actions::incrementAndGet,30);throw new AssertionError("timeout succeeded");}catch(TimeoutException expected){}
        queue.remove().run();check(actions.get()==0,"timed out action ran after queue resumed");
        System.out.println("PASS queued timeout prevents late action");
        AtomicReference<Throwable> error=new AtomicReference<>();
        Thread worker=new Thread(()->{try{MainThreadCall.call(queue::add,cancelled::get,actions::incrementAndGet,2000);}catch(Throwable e){error.set(e);}});worker.start();Runnable task=queue.poll(1,TimeUnit.SECONDS);check(task!=null,"no queued cancellation task");cancelled.set(true);task.run();worker.join(1000);check(!worker.isAlive()&&actions.get()==0,"cancelled action ran or waiter stuck");check(error.get() instanceof InterruptedException,"cancelled dispatch missing interruption cause");
        System.out.println("PASS cancellation before dispatch prevents action");
        cancelled.set(false);error.set(null);worker=new Thread(()->{try{MainThreadCall.call(queue::add,cancelled::get,actions::incrementAndGet,2000);}catch(Throwable e){error.set(e);}});worker.start();task=queue.poll(1,TimeUnit.SECONDS);check(task!=null,"no queued interruption task");worker.interrupt();worker.join(1000);task.run();check(!worker.isAlive()&&actions.get()==0&&error.get() instanceof InterruptedException,"interrupted wait still executed queued action");
        System.out.println("PASS interrupted waiting caller prevents late action");
        cancelled.set(true);try{MainThreadCall.call(queue::add,cancelled::get,actions::incrementAndGet,100);throw new AssertionError("already cancelled queued");}catch(InterruptedException expected){}check(queue.isEmpty(),"cancelled call queued work");
        cancelled.set(false);check(MainThreadCall.call(Runnable::run,cancelled::get,actions::incrementAndGet,100)==1&&actions.get()==1,"normal dispatch not exactly once");
        System.out.println("PASS pre-cancel rejection and normal exactly-once dispatch");
        System.out.println("MainThreadCall: 4 meaningful production queue tests passed");
    }
}
