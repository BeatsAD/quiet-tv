package home.quiet.tv;

/** One bounded lifecycle window. Times are absolute uptime offsets, never extended by callbacks. */
final class HomeGuardRecoveryWindow {
    interface Host {
        long now();
        void postAt(Runnable task,long time);
        void remove(Runnable task);
        String state(); // ready, recovering, locked, or a terminal stop reason
        void recover();
        void note(String event,int attempt);
        void stopped(String reason);
    }
    private static final long[] OFFSETS={5000,10000,20000,35000,65000,125000};
    private final Host host;
    private final Runnable tick=this::tick;
    private boolean started,active,destroyed,connectionChecked,waitingForUnlock;
    private long began;
    private int next,attempts;
    HomeGuardRecoveryWindow(Host host){this.host=host;}
    void start(){if(!started && !destroyed)restart();}
    // A new external signal may check once after expiry, but never moves the deadline.
    void request(){if(destroyed)return;if(!started)start();else check();}
    void restart(){
        if(destroyed)return;
        host.remove(tick);started=true;active=true;connectionChecked=false;waitingForUnlock=false;
        began=host.now();next=0;attempts=0;check();schedule();
    }
    void connected(){
        if(destroyed || connectionChecked)return;
        start();connectionChecked=true;
        if(active)check();
    }
    void unlocked(){
        // Unlock after expiry permits one immediate check, not another two-minute window.
        if(destroyed || !waitingForUnlock)return;
        waitingForUnlock=false;check();
    }
    void changed(){
        if(!started || destroyed)return;
        String state=host.state();
        if(!"ready".equals(state) && !"recovering".equals(state) && !"locked".equals(state))stop(state);
    }
    void destroy(){destroyed=true;stop("destroyed");}
    private void tick(){
        if(!active)return;
        if(host.now()-began>OFFSETS[OFFSETS.length-1]){stop("expired");return;}
        check();schedule();
    }
    private void check(){
        String state=host.state();host.note(state,attempts);
        if("locked".equals(state)){waitingForUnlock=true;return;}
        if("recovering".equals(state))return;
        if(!"ready".equals(state)){stop(state);return;}
        attempts++;host.note("request",attempts);host.recover();
    }
    private void schedule(){
        if(!active)return;
        while(next<OFFSETS.length && began+OFFSETS[next]<=host.now())next++;
        if(next==OFFSETS.length){stop("expired");return;}
        host.postAt(tick,began+OFFSETS[next++]);
    }
    private void stop(String reason){
        active=false;host.remove(tick);host.note("stop_"+reason,attempts);host.stopped(reason);
    }
}
