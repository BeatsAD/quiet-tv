package home.quiet.tv;

import android.content.Context;
import android.content.pm.*;
import android.graphics.drawable.Drawable;
import java.io.File;
import java.util.*;

/** PackageManager provides metadata; the connected shell is authoritative for current-user state. */
final class AppRepository {
    static final class App {
        String pkg,label,version="未知";
        long versionCode=-1;
        Drawable icon;
        boolean system,installed,enabled,recoverable,verified,metadataKnown;
        int enabledSetting=-1;
        long apkSizeBytes=-1;
        ShellCapabilities commands=ShellCapabilities.NONE;
        String state(){return !installed?(recoverable?"可恢复安装":"未安装"):enabled?"已启用":"已禁用";}
        String summary(){return (system?"预装应用":"用户应用")+"  "+state();}
    }
    private final Context context;
    AppRepository(Context context){this.context=context;}
    private long apkSize(ApplicationInfo info){
        if(info.sourceDir==null)return -1;
        long size=new File(info.sourceDir).length();if(size<=0)return -1;
        if(android.os.Build.VERSION.SDK_INT>=21 && info.splitSourceDirs!=null)for(String path:info.splitSourceDirs){
            long splitSize=new File(path).length();if(splitSize<=0)return -1;size+=splitSize;
        }
        return size;
    }
    static Set<String> parsePackages(String output){
        LinkedHashSet<String> result=new LinkedHashSet<>();
        for(String line:output.split("\\r?\\n"))if(line.startsWith("package:")){
            String pkg=line.substring(8).trim();if(pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+" )||pkg.equals("android"))result.add(pkg);
        }
        return result;
    }
    List<App> load(AdbController adb) throws Exception {
        PackageManager pm=context.getPackageManager();
        Map<String,PackageInfo> metadata=new HashMap<>();
        for(PackageInfo info:pm.getInstalledPackages(HomeController.METADATA))metadata.put(info.packageName,info);
        boolean online=adb!=null && adb.connected();
        if(online)adb.validateUser();
        Set<String> all,installed=new HashSet<>(),disabled=new HashSet<>(),system=new HashSet<>();
        if(online){
            String user=" --user "+adb.user;
            all=parsePackages(adb.shell("pm list packages -u"+user));
            installed=parsePackages(adb.shell("pm list packages"+user));
            disabled=parsePackages(adb.shell("pm list packages -d"+user));
            system=parsePackages(adb.shell("pm list packages -s -u"+user));
        }else all=metadata.keySet();
        ArrayList<App> apps=new ArrayList<>();
        for(String pkg:all){
            App app=new App();app.pkg=pkg;app.label=pkg;app.verified=online;if(online)app.commands=adb.capabilities;
            PackageInfo info=metadata.get(pkg);
            if(info!=null && info.applicationInfo!=null){
                ApplicationInfo ai=info.applicationInfo;app.label=ai.loadLabel(pm).toString();app.icon=ai.icon==0?UiCompat.drawable(context,R.drawable.ic_manage_apps):ai.loadIcon(pm);app.version=info.versionName==null?String.valueOf(android.os.Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode):info.versionName;
                app.metadataKnown=true;app.apkSizeBytes=apkSize(ai);
                app.versionCode=android.os.Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;
                app.system=(ai.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;app.installed=(ai.flags&ApplicationInfo.FLAG_INSTALLED)!=0;app.enabled=ai.enabled;
                try{app.enabledSetting=pm.getApplicationEnabledSetting(app.pkg);}catch(IllegalArgumentException ignored){}
                app.recoverable=app.system && !app.installed && ai.sourceDir!=null && new java.io.File(ai.sourceDir).isFile();
            }
            if(online){app.installed=installed.contains(pkg);app.enabled=app.installed && !disabled.contains(pkg);app.system=system.contains(pkg);}
            // Only retained system APKs are candidates; operation always rechecks before install-existing.
            if(app.system && !app.installed && info!=null && info.applicationInfo!=null){String source=info.applicationInfo.sourceDir;app.recoverable=source!=null && new java.io.File(source).isFile();}
            if(app.icon==null)app.icon=UiCompat.drawable(context,R.drawable.ic_manage_apps);apps.add(app);
        }
        if(online)adb.validateUser();
        Collections.sort(apps,(a,b)->java.text.Collator.getInstance(Locale.SIMPLIFIED_CHINESE).compare(a.label,b.label));return apps;
    }
    App find(AdbController adb,String pkg) throws Exception {for(App app:load(adb))if(app.pkg.equals(pkg))return app;return null;}
}
