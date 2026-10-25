package home.quiet.proxy;

/** No boot-window limit on HOME. One unanswered dispatch fails closed on the next HOME. */
public final class ProxyPolicy {
    private ProxyPolicy(){}
    public static final int DISPATCH=0,WAIT=1,FALLBACK=2;
    public static int next(boolean target,boolean valid,boolean pending,long now,long started){
        if(!target || !valid)return FALLBACK;
        if(!pending)return DISPATCH;
        return now>=started && now-started<5000?WAIT:FALLBACK;
    }
    public static boolean command(String action,long incoming,long saved){
        return ProxyContract.PREPARE.equals(action)||ProxyContract.PAUSE.equals(action)?incoming>saved:
                ProxyContract.ENABLE.equals(action)&&incoming==saved;
    }
}
