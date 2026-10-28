package home.quiet.tv;

import java.util.concurrent.*;

/** Process-owned connection; its queue also serializes management commands with health checks. */
final class AdbConnectionKeeper {
    interface Client {
        boolean available();
        boolean connected();
        void connect() throws Exception;
        void check() throws Exception;
        void disconnect();
        boolean retryable();
    }
    enum State { DISCONNECTED, CONNECTING, CONNECTED }
    private final Client client;
    private final ScheduledExecutorService worker;
    private final Runnable changed;
    private ScheduledFuture<?> next;
    private boolean running;
    private volatile State state=State.DISCONNECTED;
    private volatile String detail="";
    AdbConnectionKeeper(Client client,ScheduledExecutorService worker,Runnable changed){this.client=client;this.worker=worker;this.changed=changed;}
    State state(){return state==State.CONNECTED && !client.connected()?State.DISCONNECTED:state;}
    String detail(){return detail;}
    synchronized void request(){
        if(running)return;
        if(next!=null){next.cancel(false);next=null;}
        running=true;
        if(!client.connected()){state=State.CONNECTING;detail="正在连接本机 ADB";changed.run();}
        worker.execute(this::check);
    }
    private void check(){
        State before=state;String previousDetail=detail;
        boolean retry=false;
        try{
            if(!client.available()){
                client.disconnect();state=State.DISCONNECTED;detail="系统 ADB 未开启或用户尚未解锁";
            }else{
                if(client.connected())client.check();else client.connect();
                state=State.CONNECTED;detail="已核对本机与当前用户";retry=true;
            }
        }catch(Exception failure){
            retry=client.retryable();client.disconnect();state=State.DISCONNECTED;
            detail=failure.getMessage()==null?"ADB 连接未完成":failure.getMessage();
        }finally{
            synchronized(this){
                running=false;
                // A dead port may recover after boot/wake. Authorization and identity failures
                // wait for a user/lifecycle request rather than repeatedly opening prompts.
                if(retry)next=worker.schedule(this::request,30,TimeUnit.SECONDS);
            }
            if(state!=before || !detail.equals(previousDetail))changed.run();
        }
    }
}
