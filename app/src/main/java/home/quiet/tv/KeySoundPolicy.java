package home.quiet.tv;

/** Navigation is expendable; accepted actions replace it and never queue behind it. */
final class KeySoundPolicy {
    static final int MOVE=0,CONFIRM=1,BACK=2;
    private long lastMove=Long.MIN_VALUE;
    boolean allow(int sound,long now,boolean enabled,boolean audible,boolean loaded){
        if(!enabled||!audible||!loaded)return false;
        if(sound==MOVE){if(lastMove!=Long.MIN_VALUE&&now-lastMove<100)return false;lastMove=now;}
        return true;
    }
}
