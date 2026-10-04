package dev.chanho.hermes;

import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Cancels queued UI actions when the caller stops waiting; never interrupts Android's main thread. */
final class MainThreadCall {
    static <T> T call(Executor queue,BooleanSupplier cancelled,Callable<T> action,long timeoutMillis) throws Exception {
        if(cancelled.getAsBoolean())throw new InterruptedException("사용자가 중단했습니다.");
        FutureTask<T> task=new FutureTask<>(()->{
            if(cancelled.getAsBoolean())throw new InterruptedException("사용자가 중단했습니다.");
            return action.call();
        });
        queue.execute(task);
        try{return task.get(timeoutMillis,TimeUnit.MILLISECONDS);}
        catch(TimeoutException|InterruptedException e){task.cancel(false);throw e;}
        catch(ExecutionException e){if(e.getCause() instanceof Exception)throw (Exception)e.getCause();throw e;}
    }
}
