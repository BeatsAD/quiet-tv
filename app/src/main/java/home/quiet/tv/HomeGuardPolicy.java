package home.quiet.tv;

import java.util.LinkedHashSet;
import java.util.Locale;

/** Fresh home events are immediate; lifecycle recovery has a separate, bounded settle path. */
final class HomeGuardPolicy {
    static boolean keepAdbReady(boolean supported,boolean selected,boolean writeAllowed){return supported && selected && writeAllowed;}
    static boolean keepAdbPort(boolean supported,int sdk,int port){return supported && sdk>=21 && sdk<30 && port>0 && port<=65535;}
    static final long SETTLE_MS=1500, MAX_AGE_MS=3000, BOOT_WINDOW_MS=180000;
    // The submitted H6 log crosses the original HOME up to 5.5s after selecting an input.
    static final long INPUT_TRANSITION_MS=8000;
    static final long USAGE_POLL_MS=1000, USAGE_LOOKBACK_MS=10000;
    static final long BOOT_RECHECK_FIRST_MS=30000, BOOT_RECHECK_SECOND_MS=45000, DEAF_CHECK_MS=20000;
    private String window="";
    private long arrived,usageTakenAt=Long.MIN_VALUE;
    private boolean consumed,unknownUsed;
    private long inputStarted=-1;

    void inputSelected(long now){inputStarted=now;}
    void cancelInput(){inputStarted=-1;}
    long inputDelay(long now){long age=now-inputStarted;return inputStarted>=0 && age>=0 && age<INPUT_TRANSITION_MS?INPUT_TRANSITION_MS-age:0;}
    boolean inputTransition(long now){return inputDelay(now)>0;}

    static boolean supported(String manufacturer,String brand,int sdk,boolean legacy){return !legacy && sdk>=21;}
    static boolean coocaa(String manufacturer,String brand){
        for(String name:(manufacturer+" "+brand).toLowerCase(Locale.ROOT).split("\\s+"))
            if("skyworth".equals(name) || "coocaa".equals(name))return true;
        return false;
    }
    static boolean xiaomi(String manufacturer,String brand){
        for(String name:(manufacturer+" "+brand).toLowerCase(Locale.ROOT).split("\\s+"))
            if("xiaomi".equals(name) || "redmi".equals(name))return true;
        return false;
    }
    static boolean xiaomiRecovery(String manufacturer,String brand,int sdk,boolean legacy){return supported(manufacturer,brand,sdk,legacy) && xiaomi(manufacturer,brand);}
    static boolean usageFallback(int sdk,boolean legacy){return !legacy && sdk>=21 && sdk<=23;}
    static boolean usageFallback(int sdk,boolean legacy,String selected){
        // VIDAA's launcher may omit accessibility window events, including on newer Android.
        // Selection is separately checked against an installed system activity before acting.
        return usageFallback(sdk,legacy) || !legacy && sdk>=21 && (selected.startsWith("com.jamdeo.tv.vod/") || fireHome(sdk,selected));
    }
    static boolean fireHome(int sdk,String selected){return sdk==25 && selected.startsWith("com.amazon.tv.launcher/");}
    static boolean usageEvidence(String manufacturer,String brand,int sdk,boolean legacy){
        return usageFallback(sdk,legacy) || xiaomiRecovery(manufacturer,brand,sdk,legacy);
    }
    static boolean usageEvidence(String manufacturer,String brand,int sdk,boolean legacy,String selected){
        return usageFallback(sdk,legacy,selected) || xiaomiRecovery(manufacturer,brand,sdk,legacy);
    }
    static boolean filterHome(boolean xiaomi,boolean enabled,boolean canShow,int sdk,boolean defaultHome,boolean homeResumed){
        return filterHome(xiaomi,enabled,canShow,sdk,defaultHome,homeResumed,false);
    }
    static boolean filterHome(boolean xiaomi,boolean enabled,boolean canShow,int sdk,boolean defaultHome,boolean homeResumed,boolean directHomeReady){
        return xiaomi && enabled && canShow && sdk>=21 && (directHomeReady || homeResumed || sdk<=30 && !defaultHome);
    }
    static boolean directHomeDevice(String manufacturer,String brand,String model,String device,int sdk,boolean legacy){
        return !legacy && sdk==22 && xiaomi(manufacturer,brand) && "MiBOX3".equals(model) && "JurassicPark".equals(device);
    }
    static boolean directHomeConfigured(boolean enabled,boolean optedIn,boolean current,String route){
        return enabled && optedIn && current && "DIRECT".equals(route);
    }
    static boolean homeActionReady(boolean observerReady,boolean directConfigured,boolean ownHomeUsable,boolean helperActive){
        return !helperActive && (directConfigured?ownHomeUsable:observerReady);
    }
    static boolean handoverBlocked(boolean pending,boolean homeResumed){return pending || homeResumed;}
    static boolean bootRecheck(long uptime){return uptime>=0 && uptime<BOOT_WINDOW_MS;}
    static boolean recheckTarget(String selected,String front,long at,long now){
        return !selected.isEmpty() && selected.equals(front) && at>0 && now>=at && now-at<=BOOT_WINDOW_MS;
    }
    static boolean countRevival(boolean interactive,long uptime,boolean alreadyNoted,long sinceUpdate){return interactive && uptime>=120000 && !alreadyNoted && (sinceUpdate<0 || sinceUpdate>=120000);}
    static boolean vendorHome(String category){
        return "android.intent.category.HOME.CC".equals(category)
            || "android.intent.category.HOME.CC_MAIN".equals(category)
            || "smartdevice.intent.category.LITE_HOME".equals(category);
    }
    static boolean matches(String selected,String pkg,String cls){
        if(selected.isEmpty() || pkg==null || cls==null || cls.isEmpty())return false;
        return selected.equals(pkg+"/"+(cls.startsWith(".")?pkg+cls:cls));
    }
    static boolean migrateAuto(boolean present,boolean enabled,String status){
        if(!present || enabled)return true;
        return status.startsWith("无障碍服务已断开") || status.startsWith("无障碍权限已关闭")
            || status.startsWith("启动被系统拒绝") || status.startsWith("原主页或适用条件已变化")
            || status.startsWith("检测到独立中转助手");
    }
    static String services(String current,String own,boolean include){
        LinkedHashSet<String> entries=new LinkedHashSet<>();
        if(current!=null)for(String entry:current.split(":")){
            if(entry.isEmpty() || "null".equals(entry))continue;
            String[] parts=entry.split("/",2);
            String canonical=parts.length==2 && parts[1].startsWith(".")?parts[0]+"/"+parts[0]+parts[1]:entry;
            if(!own.equals(canonical))entries.add(entry);
        }
        if(include)entries.add(own);
        StringBuilder joined=new StringBuilder();for(String entry:entries){if(joined.length()>0)joined.append(':');joined.append(entry);}return joined.toString();
    }
    boolean observe(String selected,String pkg,String cls,long now){
        String next=pkg+"/"+(cls.startsWith(".")?pkg+cls:cls);
        if(window.equals(next))return false;
        window=next;arrived=now;consumed=false;
        return matches(selected,pkg,cls);
    }
    boolean consume(String selected,long now,boolean enabled,boolean interactive,boolean unlocked){
        long age=now-arrived;
        if(!enabled || !interactive || !unlocked || consumed || !window.equals(selected) || selected.isEmpty()
                || age<0 || age>MAX_AGE_MS)return false;
        consumed=true;return true;
    }
    boolean settle(String selected,boolean allowUnknown){
        if(window.isEmpty()){
            if(!allowUnknown || unknownUsed)return false;
            unknownUsed=true;return true;
        }
        if(!window.equals(selected) || consumed)return false;
        consumed=true;return true;
    }
    // Wall-clock timestamps from UsageEvents are separate from accessibility's uptime.
    boolean consumeUsage(String selected,String pkg,String cls,long at,long now){
        if(at<=usageTakenAt || now<at || now-at>USAGE_LOOKBACK_MS || !matches(selected,pkg,cls))return false;
        usageTakenAt=at;return true;
    }
    void ignoreUsageThrough(long now){usageTakenAt=Math.max(usageTakenAt,now);}
    boolean unknown(){return window.isEmpty();}
    // A second boot check may answer a previously consumed home window, but never another app.
    boolean bootRecheck(String selected,boolean allowUnknown){return window.isEmpty()?allowUnknown:!selected.isEmpty() && window.equals(selected);}
    void confirmed(){unknownUsed=true;}
    void clear(){window="";arrived=0;consumed=false;cancelInput();}
}
