package home.quiet.tv;

/** The reported API19 platform needs a key filter, not an API21+ window/usage guard. */
final class LegacyHomeKeyPolicy {
    static final int PASS=-1;
    private final HomeKeyPolicy keys=new HomeKeyPolicy();
    private long gesture=-1;
    private boolean consuming;

    static boolean supported(boolean legacy,int sdk,String manufacturer,String brand,String model,String device,String hardware){
        return legacy && sdk==19 && ("Hisense".equalsIgnoreCase(manufacturer) || "Hisense".equalsIgnoreCase(brand))
                && "VIDAA_TV".equalsIgnoreCase(model) && "Hi3751".equalsIgnoreCase(device) && "hi3751".equalsIgnoreCase(hardware);
    }
    boolean down(long downTime,int repeat,boolean eligible){
        if(gesture!=downTime){
            // Never take over a repeat whose original DOWN was not delivered to this filter.
            if(repeat!=0)return false;
            gesture=downTime;consuming=eligible;
            if(keys.pressed() || !consuming)keys.reset();
            if(consuming)keys.down(downTime);
        }
        return consuming;
    }
    int up(long downTime,long at,boolean cancelled,long hold,long twice){
        if(gesture!=downTime)return PASS;
        gesture=-1;
        if(!consuming)return PASS;
        consuming=false;return keys.up(at,cancelled,hold,twice);
    }
    // Keep swallowing the matching UP after an interrupt, but never execute that gesture.
    void interrupt(){keys.reset();}
}
