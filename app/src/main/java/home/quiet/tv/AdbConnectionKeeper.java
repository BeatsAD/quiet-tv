package home.quiet.tv;

import java.util.concurrent.*;

/** Cancel handshakes immediately; release established sessions after queued management commands. */
final class AdbConnectionKeeper {
    interface Client {
        boolean available();
        boolean connected();
        int prepare();
        void connect(int attempt) throws Exception;
        void check() throws Exception;
        void disconnect();
    }
    enum State { DISCONNECTED, CONNECTING, CONNECTED }
    private final Client client;
    private final ExecutorService worker;
    private final Runnable changed;
    private boolean running,connecting;
    private int generation;
    private volatile State state=State.DISCONNECTED;
    private volatile String detail="";
    AdbConnectionKeeper(Client client,ExecutorService worker,Runnable changed){this.client=client;this.worker=worker;this.changed=changed;}
    State state(){return state==State.CONNECTED && !client.connected()?State.DISCONNECTED:state;}
    String detail(){return detail;}
    synchronized void request(){
        if(running)return;
        running=true;int request=++generation;
        if(!client.connected()){state=State.CONNECTING;detail="正在连接本机 ADB";changed.run();}
        worker.execute(()->check(request));
    }
    synchronized void disconnect(){
        generation++;running=false;state=State.DISCONNECTED;detail="";
        // A handshake must not block its own cancellation. Package writes keep deferred release.
        if(connecting)client.disconnect();else worker.execute(client::disconnect);
        changed.run();
    }
    private void check(int request){
        synchronized(this){if(request!=generation)return;}
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
                if(connected)client.check();else client.connect(attempt);
                result=State.CONNECTED;explanation="已核对本机与当前用户";
            }
        }catch(Exception failure){
            client.disconnect();result=State.DISCONNECTED;
            explanation=failure.getMessage()==null?"ADB 连接未完成":failure.getMessage();
        }finally{synchronized(this){connecting=false;}}
        synchronized(this){if(request!=generation){client.disconnect();return;}running=false;state=result;detail=explanation;}
        changed.run();
    }
}
