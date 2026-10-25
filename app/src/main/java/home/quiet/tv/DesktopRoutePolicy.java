package home.quiet.tv;

/** A transport error or cancellation never proves that the firmware refused HOME. */
final class DesktopRoutePolicy {
    enum Result { DIRECT, RETAINED, UNSUPPORTED, UNKNOWN, CANCELLED }
    static boolean fallback(Result result,boolean active,boolean connected,boolean paused){
        return active && connected && !paused && (result==Result.RETAINED || result==Result.UNSUPPORTED);
    }
    static boolean preserve(boolean explicit,boolean disableVerified,boolean selected,boolean observing){
        return explicit || selected && (observing || !disableVerified);
    }
    static boolean bridgeAllowed(boolean enabled,boolean matched,boolean canShow,boolean otherHelper,
                                 long uptime,boolean consumed){
        return enabled && matched && canShow && !otherHelper && uptime>=0
                && uptime<HomeGuardPolicy.BOOT_WINDOW_MS && !consumed;
    }
    private DesktopRoutePolicy(){}
}
