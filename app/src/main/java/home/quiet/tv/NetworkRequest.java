package home.quiet.tv;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.HttpsURLConnection;

/** Cancelling detaches one request; HTTPS shutdown never runs on the lifecycle caller. */
final class NetworkRequest {
    private static final Executor cleanup=new ThreadPoolExecutor(0,1,5,TimeUnit.SECONDS,new LinkedBlockingQueue<>());
    static final class Handle {
        private final HttpsURLConnection connection;
        private final AtomicBoolean closed=new AtomicBoolean();
        Handle(HttpsURLConnection connection){this.connection=connection;}
        void close(){if(closed.compareAndSet(false,true))connection.disconnect();}
    }
    private final Executor closer;
    private Handle active;
    NetworkRequest(){this(cleanup);}
    NetworkRequest(Executor closer){this.closer=closer;}
    synchronized Handle begin(HttpsURLConnection connection){active=new Handle(connection);return active;}
    void finish(Handle request){
        synchronized(this){if(active==request)active=null;}
        request.close(); // The request worker owns normal completion.
    }
    void cancel(){
        Handle previous;
        synchronized(this){previous=active;active=null;}
        if(previous!=null)closer.execute(previous::close);
    }
}
