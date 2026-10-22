package home.quiet.tv;

import java.util.Locale;

/** One fresh, exact home-window arrival permits one delayed handover, never a polling loop. */
final class HomeGuardPolicy {
    static final long SETTLE_MS=1500, MAX_AGE_MS=3000;
    private String window="";
    private long arrived;
    private boolean consumed;

    static boolean supported(String manufacturer,String brand,int sdk,boolean legacy){
        if(legacy || sdk<21)return false;
        String vendor=(manufacturer+" "+brand).toLowerCase(Locale.ROOT);
        for(String name:vendor.split("\\s+"))
            if(name.equals("xiaomi") || name.equals("redmi") || name.equals("skyworth") || name.equals("coocaa"))return true;
        return false;
    }
    static boolean vendorHome(String category){
        return "android.intent.category.HOME.CC".equals(category)
            || "android.intent.category.HOME.CC_MAIN".equals(category)
            || "smartdevice.intent.category.LITE_HOME".equals(category);
    }
    static boolean matches(String selected,String pkg,String cls){
        if(selected.isEmpty() || pkg==null || cls==null || cls.isEmpty())return false;
        String full=cls.startsWith(".")?pkg+cls:cls;
        return selected.equals(pkg+"/"+full);
    }
    boolean observe(String selected,String pkg,String cls,long now){
        if(!matches(selected,pkg,cls)){clear();return false;}
        if(window.equals(selected))return false;
        window=selected;arrived=now;consumed=false;return true;
    }
    boolean consume(String selected,long now,boolean enabled,boolean interactive,boolean unlocked){
        long age=now-arrived;
        if(!enabled || !interactive || !unlocked || consumed || window.isEmpty() || !window.equals(selected)
                || age<SETTLE_MS || age>MAX_AGE_MS)return false;
        consumed=true;return true;
    }
    void clear(){window="";arrived=0;consumed=false;}
}
