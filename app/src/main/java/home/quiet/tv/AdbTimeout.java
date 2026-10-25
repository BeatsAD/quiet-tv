package home.quiet.tv;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** A monotonic deadline closes only the transport captured by this operation. */
final class AdbTimeout implements Closeable {
    private final ScheduledFuture<?> task;
    private boolean finished,expired;
    AdbTimeout(ScheduledExecutorService timer,Closeable transport,long milliseconds){
        task=timer.schedule(()->{
            synchronized(this){if(finished)return;expired=true;finished=true;}
            try{transport.close();}catch(IOException ignored){}
        },milliseconds,TimeUnit.MILLISECONDS);
    }
    synchronized boolean expired(){return expired;}
    @Override public synchronized void close(){finished=true;task.cancel(false);}
}
