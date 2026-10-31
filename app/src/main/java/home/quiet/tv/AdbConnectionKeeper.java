package home.quiet.tv;

import java.util.concurrent.*;

/** Explicit management connection; release stays behind any already queued management command. */
final class AdbConnectionKeeper {
    interface Client {
        boolean available();
        boolean connected();
        void connect() throws Exception;
        void check() throws Exception;
        void disconnect();
    }
    enum State { DISCONNECTED, CONNECTING, CONNECTED }
    private final Client client;
    private final ExecutorService worker;
    private final Runnable changed;
    private boolean running;
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
        // Do not interrupt a package write before its state verification has completed.
        worker.execute(client::disconnect);changed.run();
    }
    private void check(int request){
        synchronized(this){if(request!=generation)return;}
        State result;String explanation;
        try{
            if(!client.available()){
                client.disconnect();result=State.DISCONNECTED;explanation="系统 ADB 未开启或用户尚未解锁";
            }else{
                if(client.connected())client.check();else client.connect();
                result=State.CONNECTED;explanation="已核对本机与当前用户";
            }
        }catch(Exception failure){
            client.disconnect();result=State.DISCONNECTED;
            explanation=failure.getMessage()==null?"ADB 连接未完成":failure.getMessage();
        }
        synchronized(this){if(request!=generation)return;running=false;state=result;detail=explanation;}
        changed.run();
    }
}
