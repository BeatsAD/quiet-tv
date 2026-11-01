package home.quiet.tv;

import java.util.concurrent.*;

/** Process-owned connection; checks and management commands share one queue. */
final class AdbConnectionKeeper {
    interface Client {
        boolean available();
        boolean connected();
        int prepare();
        void connect(int attempt,boolean requestAuthorization) throws Exception;
        void check() throws Exception;
        void disconnect();
        boolean retryable();
    }
    enum State { DISCONNECTED, CONNECTING, CONNECTED }
    private final Client client;
    private final ScheduledExecutorService worker;
    private final Runnable changed;
    private ScheduledFuture<?> next;
    private boolean running,connecting;
    private int generation;
    private volatile State state=State.DISCONNECTED;
    private volatile String detail="";
    AdbConnectionKeeper(Client client,ScheduledExecutorService worker,Runnable changed){this.client=client;this.worker=worker;this.changed=changed;}
    State state(){return state==State.CONNECTED && !client.connected()?State.DISCONNECTED:state;}
    String detail(){return detail;}
    void request(){requestConnection(false);}
    void requestAuthorization(){requestConnection(true);}
    private synchronized void requestConnection(boolean requestAuthorization){
        if(running)return;
        if(next!=null){next.cancel(false);next=null;}
        running=true;int request=++generation;
        if(!client.connected()){state=State.CONNECTING;detail="正在连接本机 ADB";changed.run();}
        worker.execute(()->check(request,requestAuthorization));
    }
    synchronized void disconnect(){
        if(next!=null){next.cancel(false);next=null;}
        generation++;running=false;state=State.DISCONNECTED;detail="";
        // A handshake must not block its own cancellation. Package writes keep deferred release.
        if(connecting)client.disconnect();else worker.execute(client::disconnect);
        changed.run();
    }
    private void check(int request,boolean requestAuthorization){
        synchronized(this){if(request!=generation)return;}
        State before=state;String previousDetail=detail;boolean retry=false;
        State result;String explanation;
        try{
            if(!client.available()){
                client.disconnect();result=State.DISCONNECTED;explanation="系统 ADB 未开启或用户尚未解锁";
            }else{
                boolean connected;int attempt;
                synchronized(this){
                    if(request!=generation)return;
                    connected=client.connected();attempt=connected?0:client.prepare();connecting=!connected;
                }
                // Preparation and cancellation share the lock; a cancelled token cannot reopen a transport.
                if(connected)client.check();else client.connect(attempt,requestAuthorization);
                result=State.CONNECTED;explanation="已核对本机与当前用户";retry=true;
            }
        }catch(Exception failure){
            retry=client.retryable();client.disconnect();result=State.DISCONNECTED;
            explanation=failure.getMessage()==null?"ADB 连接未完成":failure.getMessage();
        }finally{synchronized(this){connecting=false;}}
        synchronized(this){
            if(request!=generation){client.disconnect();return;}
            running=false;state=result;detail=explanation;
            // Port failures may recover after boot; authorization and identity failures wait.
            if(retry)next=worker.schedule(this::request,30,TimeUnit.SECONDS);
        }
        if(state!=before || !detail.equals(previousDetail))changed.run();
    }
}
