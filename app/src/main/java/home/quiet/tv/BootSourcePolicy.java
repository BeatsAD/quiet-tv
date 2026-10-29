package home.quiet.tv;

/** Bounded recovery for the reported firmware's boot-to-input route, before user navigation. */
final class BootSourcePolicy {
    static final String PLAYER="com.skyworth.tv/com.skyworth.tv.activity.SkyTVMainActivity";
    static final int MAX_ATTEMPTS=3;
    final String boot;
    private int attempts;
    private boolean stopped;
    private final boolean observedSourceOnly;

    BootSourcePolicy(String boot,String savedBoot,int attempts,boolean stopped,boolean observedSourceOnly){
        this.boot=boot;this.observedSourceOnly=observedSourceOnly;
        if(!boot.isEmpty() && boot.equals(savedBoot)){this.attempts=attempts;this.stopped=stopped;}
    }
    static boolean supported(String manufacturer,String model,String device,String fingerprint,int sdk,boolean legacy){
        return !legacy && sdk==26 && "Skyworth".equalsIgnoreCase(manufacturer) && ("8N32_S81".equals(model)
            && "nt72671D_a32".equals(device)
            && "Skyworth/sky8N30/nt72671D_a32:8.0.0/OPR6.170623.012/022.009.010:user/dev-keys".equals(fingerprint)
            || "9S63_H6".equals(model) && "sky848_9s60".equals(device)
            && "Skyworth/full_sky848_9s60/sky848_9s60:8.0.0/020.012.180:user/dev-keys".equals(fingerprint));
    }
    boolean active(long elapsed){return !boot.isEmpty() && !stopped && attempts<MAX_ATTEMPTS && HomeGuardPolicy.bootRecheck(elapsed);}
    boolean target(String front){return !observedSourceOnly && front.isEmpty() || PLAYER.equals(front);}
    boolean take(long elapsed,String front){
        if(!active(elapsed) || !target(front))return false;
        ++attempts;return true;
    }
    void stop(){stopped=true;}
    boolean stopped(){return stopped;}
    int attempts(){return attempts;}
}
