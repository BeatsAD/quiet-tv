package home.quiet.tv;

import java.util.LinkedHashSet;

/** Fresh home events are immediate; lifecycle recovery has a separate, bounded settle path. */
final class HomeGuardPolicy {
    static final long SETTLE_MS=1500, MAX_AGE_MS=3000, BOOT_WINDOW_MS=180000;
    static final long USAGE_POLL_MS=1000, USAGE_LOOKBACK_MS=10000;
    private String window="";
    private long arrived,usageTakenAt=Long.MIN_VALUE;
    private boolean consumed,unknownUsed;

    static boolean supported(String manufacturer,String brand,int sdk,boolean legacy){return !legacy && sdk>=21;}
    static boolean usageFallback(int sdk,boolean legacy){return !legacy && sdk>=21 && sdk<=23;}
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
    void confirmed(){unknownUsed=true;}
    void clear(){window="";arrived=0;consumed=false;}
}
