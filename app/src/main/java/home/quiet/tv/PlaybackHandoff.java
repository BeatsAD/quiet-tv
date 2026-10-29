package home.quiet.tv;

import java.util.ArrayDeque;

/** Serializes decoder replacement until the retiring decoder acknowledges completion. */
final class PlaybackHandoff {
    interface Operation {void run(Runnable completed);}
    private final ArrayDeque<Operation> pending=new ArrayDeque<>();
    private boolean busy,draining;
    void submit(Operation operation){pending.add(operation);drain();}
    private void drain(){
        if(draining)return;draining=true;
        try{
            while(!busy&&!pending.isEmpty()){
                busy=true;boolean[] completed={false};
                pending.remove().run(()->{if(completed[0])return;completed[0]=true;busy=false;drain();});
            }
        }finally{draining=false;}
    }
    static Thread awaitTermination(Thread decoderThread,Runnable completed){
        Thread retirement=new Thread(()->{
            boolean interrupted=false;
            while(decoderThread.isAlive())try{decoderThread.join();}catch(InterruptedException e){interrupted=true;}
            if(interrupted)Thread.currentThread().interrupt();
            completed.run();
        },"QuietTV-codec-retirement");
        retirement.setDaemon(true);retirement.start();return retirement;
    }

}
