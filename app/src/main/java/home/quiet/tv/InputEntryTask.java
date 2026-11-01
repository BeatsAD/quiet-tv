package home.quiet.tv;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.*;

/** One lifecycle-owned input request. Cancellation also fences non-interruptible OEM replies. */
final class InputEntryTask {
    interface Work<T> { T run() throws Exception; }
    interface Reply<T> { void accept(T value,Exception failure); }
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>());
    private final Handler main=new Handler(Looper.getMainLooper());
    private static final class Request<T> {
        Reply<T> reply;
        Request(Reply<T> reply){this.reply=reply;}
    }
    private Request<?> active;
    private Future<?> pending;
    private volatile int generation;
    private boolean closed;
    <T> void submit(Work<T> work,Reply<T> reply){
        cancel();if(closed)return;final int request=generation;
        Request<T> delivery=new Request<>(reply);active=delivery;
        pending=worker.submit(()->{
            T value=null;Exception failure=null;
            try{if(request!=generation)return;value=work.run();}catch(Exception caught){failure=caught;}
            final T result=value;final Exception error=failure;
            if(request!=generation)return;
            main.postAtTime(()->{
                if(request!=generation||closed)return;
                Reply<T> callback=delivery.reply;delivery.reply=null;active=null;pending=null;
                if(callback!=null)callback.accept(result,error);
            },this,android.os.SystemClock.uptimeMillis());
        });
    }
    void cancel(){++generation;if(active!=null){active.reply=null;active=null;}if(pending!=null){pending.cancel(true);pending=null;worker.purge();}main.removeCallbacksAndMessages(this);}
    void close(){cancel();closed=true;worker.shutdownNow();}
}
