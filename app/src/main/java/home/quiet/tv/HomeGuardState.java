package home.quiet.tv;

import android.app.KeyguardManager;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.provider.Settings;
import java.util.*;

/** User intent survives service loss. Minimal recovery preferences are available before unlock. */
final class HomeGuardState {
    static final String PREFS="home-guard";
    final SharedPreferences preferences;
    private final Context context;
    private final PackageManager pm;
    private final TargetCache eventHomeCache=new TargetCache(this::resolveEventHome);
    static final class TargetCache {
        interface Resolver {String resolve(String selected);}
        private final Resolver resolver;
        private String selected,value;
        TargetCache(Resolver resolver){this.resolver=resolver;}
        String get(String target){if(!target.equals(selected)){String resolved=resolver.resolve(target);selected=target;value=resolved;}return value;}
        void invalidate(){selected=null;value=null;}
    }
    static final class Candidate {
        final ComponentName component;final String label;
        Candidate(ComponentName component,String label){this.component=component;this.label=label;}
    }
    // Migration must be durable before other components consume the new configuration.
    HomeGuardState(Context supplied){this(supplied,true);}
    @android.annotation.SuppressLint("ApplySharedPref")
    HomeGuardState(Context supplied,boolean migrate){
        context=supplied.getApplicationContext();pm=context.getPackageManager();
        Context storage=Build.VERSION.SDK_INT>=24?context.createDeviceProtectedStorageContext():context;
        preferences=storage.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        if(!migrate)return;
        if(Build.VERSION.SDK_INT>=24 && unlocked(context) && !preferences.getBoolean("migrated",false)){
            SharedPreferences old=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            SharedPreferences.Editor edit=preferences.edit();
            if(!preferences.contains("mode")){
                boolean auto=HomeGuardPolicy.migrateAuto(old.contains("enabled"),old.getBoolean("enabled",false),old.getString("status",""));
                edit.putString("mode",auto?"AUTO":"OFF").putBoolean("configured",old.contains("home"));
                for(String key:new String[]{"home","firmware","status"})if(old.contains(key))edit.putString(key,old.getString(key,""));
            }
            edit.putBoolean("migrated",true).commit();
        }else if(Build.VERSION.SDK_INT<24 && !preferences.contains("mode")){
            boolean auto=HomeGuardPolicy.migrateAuto(preferences.contains("enabled"),preferences.getBoolean("enabled",false),preferences.getString("status",""));
            preferences.edit().putString("mode",auto?"AUTO":"OFF").putBoolean("configured",preferences.contains("home")).commit();
        }
    }
    static boolean unlocked(Context context){
        if(Build.VERSION.SDK_INT<24)return true;
        UserManager user=(UserManager)context.getSystemService(Context.USER_SERVICE);return user!=null && user.isUserUnlocked();
    }
    static boolean interactive(Context context){PowerManager power=(PowerManager)context.getSystemService(Context.POWER_SERVICE);return power!=null && (Build.VERSION.SDK_INT>=20?power.isInteractive():power.isScreenOn());}
    static boolean canShow(Context context){KeyguardManager keyguard=(KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);return !BootAppLifecycle.dreaming() && unlocked(context) && interactive(context) && keyguard!=null && !UiCompat.keyguardLocked(keyguard);}
    boolean supported(){return HomeGuardPolicy.supported(Build.MANUFACTURER,Build.BRAND,Build.VERSION.SDK_INT,BuildConfig.LEGACY_19);}
    boolean automatic(){return supported() && preferences.getBoolean("configured",false) && "AUTO".equals(preferences.getString("mode","AUTO"));}
    boolean enabled(){return automatic() && preferences.getBoolean("observeHome",true);}
    String selected(){return preferences.getString("home","");}
    boolean protects(String pkg){return protects(pkg,SetupPresets.load(context));}
    boolean protects(String pkg,SetupPresets rules){
        ComponentName name=ComponentName.unflattenFromString(selected()),fallback=ComponentName.unflattenFromString(preferences.getString("proxyOriginal",""));boolean selected=name!=null && name.getPackageName().equals(pkg);
        return DesktopRoutePolicy.preserve(rules.protectedHomePackage(pkg) || fallback!=null && fallback.getPackageName().equals(pkg) || !BuildConfig.DECLARES_HOME && selected,selected && selected().equals(rules.home()) && rules.homeAllows("disableVerified"),selected,enabled());
    }
    // Persist disabled mode before canceling scheduled recovery.
    @android.annotation.SuppressLint("ApplySharedPref")
    void pause(String reason){DiagnosticLog.info("home_guard.pause",reason);preferences.edit().putString("mode","OFF").putBoolean("enabled",false).putString("status",reason).commit();HomeGuardRecovery.cancel();HomeGuardService.cancelPending();HomeGuardSync.pause(context);HomeGuardUsageService.pause(context);DesktopRoutes.disableVendor(context);if(!new HomeProxy(context).pause())status(reason+"；代理暂停回执待确认，主程序启动入口已关闭");}
    // Persist the selected HOME and firmware before subsequent recovery requests.
    @android.annotation.SuppressLint("ApplySharedPref")
    void select(ComponentName name){if(!name.flattenToString().equals(selected()))new HomeProxy(context).pause();HomeGuardRecovery.cancel();HomeGuardService.cancelPending();preferences.edit().putBoolean("configured",true).putString("home",name.flattenToString()).putString("firmware",Build.FINGERPRINT).commit();new DesktopRoutes(context).reconcileVendor();}
    void configure(){
        if(!supported() || !unlocked(context))return;
        preferences.edit().putBoolean("configured",true).apply();
        if(validSelection())return;
        List<Candidate> homes=candidates();ResolveInfo resolved=pm.resolveActivity(HomeController.homeIntent(),PackageManager.MATCH_DEFAULT_ONLY);
        if(resolved!=null && resolved.activityInfo!=null){ComponentName actual=new ComponentName(resolved.activityInfo.packageName,resolved.activityInfo.name);
            for(Candidate home:homes)if(home.component.equals(actual)){select(actual);return;}}
        // Reuse a verified local identity before asking the user to distinguish system components.
        SetupPresets presets=SetupPresets.load(context);
        String original="";try{original=new SetupStore(context).originalHome();}catch(Exception unavailable){DiagnosticLog.info("home_guard.selection","original_record_unavailable");}
        for(String saved:new String[]{original,presets.home()})
            for(Candidate home:homes)if(home.component.flattenToString().equals(saved)){select(home.component);return;}
        List<String> available=new ArrayList<>();for(Candidate home:homes)available.add(home.component.flattenToString());
        String privateHome=HomeGuardRecovery.knownPrivateHome()?CoocaaHomeRoutes.unique(CoocaaHomeRoutes.targets(CoocaaHomeRoutes::read),available):"";
        if(!privateHome.isEmpty()){select(ComponentName.unflattenFromString(privateHome));return;}
        if(homes.size()==1)select(homes.get(0).component);
    }
    void status(String text){if(!text.equals(preferences.getString("status",""))){preferences.edit().putString("status",text).apply();DiagnosticLog.info("home_guard.status",text);}}
    boolean serviceEnabled(){
        if(Settings.Secure.getInt(context.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0)!=1)return false;
        String current=Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName own=new ComponentName(context,HomeGuardService.class);
        if(current!=null)for(String entry:current.split(":"))if(own.equals(ComponentName.unflattenFromString(entry)))return true;
        return false;
    }
    List<Candidate> candidates(){
        Map<String,Candidate> homes=new LinkedHashMap<>();collect(homes,pm.queryIntentActivities(HomeController.homeIntent(),0));
        if(HomeGuardRecovery.knownPrivateHome())for(String category:new String[]{"android.intent.category.HOME.CC","android.intent.category.HOME.CC_MAIN","smartdevice.intent.category.LITE_HOME"})
            collect(homes,pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0));
        // These firmware activities omit HOME intent filters. Never infer a target from brand alone.
        for(String component:new String[]{"com.skyworth.panasonichome/com.skyworth.panasonichome.MainActivity",
                "com.coocaa.aigc.home/com.coocaa.aigc.home.home.rootpage.HomeActivity"}){
            try{ResolveInfo value=new ResolveInfo();value.activityInfo=pm.getActivityInfo(ComponentName.unflattenFromString(component),0);
                collect(homes,Collections.singletonList(value));}
            catch(PackageManager.NameNotFoundException|SecurityException ignored){}
        }
        if(HomeGuardRecovery.knownPrivateHome()){
            List<String> routes=CoocaaHomeRoutes.targets(CoocaaHomeRoutes::read);routes.add(CoocaaHomeRoutes.STOCK);
            for(String route:routes)try{
                ResolveInfo value=new ResolveInfo();value.activityInfo=pm.getActivityInfo(ComponentName.unflattenFromString(route),0);
                collect(homes,Collections.singletonList(value));
            }catch(PackageManager.NameNotFoundException|SecurityException ignored){}
        }
        return new ArrayList<>(homes.values());
    }
    boolean privateHomeAvailable(){
        if(!HomeGuardRecovery.knownPrivateHome())return false;
        List<String> routes=CoocaaHomeRoutes.targets(CoocaaHomeRoutes::read);routes.add(CoocaaHomeRoutes.STOCK);
        for(Candidate home:candidates())if(routes.contains(home.component.flattenToString()))return true;
        return false;
    }
    private void collect(Map<String,Candidate> homes,List<ResolveInfo> values){
        for(ResolveInfo value:values){ActivityInfo info=value.activityInfo;
            if(info==null || context.getPackageName().equals(info.packageName) || !info.enabled || !info.applicationInfo.enabled
                    || (info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0 || "android".equals(info.packageName) || info.name.endsWith("FallbackHome"))continue;
            ComponentName name=new ComponentName(info.packageName,info.name);homes.put(name.flattenToString(),new Candidate(name,info.loadLabel(pm).toString()));
        }
    }
    String eventHome(){return eventHomeCache.get(selected());}
    void invalidateEventHome(){eventHomeCache.invalidate();}
    private String resolveEventHome(String selected){
        ComponentName name=ComponentName.unflattenFromString(selected);if(name==null)return "";
        try{ActivityInfo info=pm.getActivityInfo(name,0);String target=info.targetActivity;
            if(target!=null)return new ComponentName(name.getPackageName(),target.startsWith(".")?name.getPackageName()+target:target).flattenToString();
        }catch(PackageManager.NameNotFoundException|SecurityException ignored){}return selected;
    }
    boolean validSelection(){
        // Observer recovery follows the installed HOME component across firmware updates.
        // Firmware-specific vendor entry points and the optional proxy keep their own checks.
        if(!unlocked(context))return false;
        for(Candidate home:candidates())if(home.component.flattenToString().equals(selected()))return true;return false;
    }
    boolean ready(){return enabled() && validSelection() && !helperActive();}
    boolean directHomeAvailable(){
        if(!BuildConfig.DECLARES_HOME || !HomeGuardPolicy.directHomeDevice(Build.MANUFACTURER,Build.BRAND,Build.MODEL,Build.DEVICE,Build.VERSION.SDK_INT,BuildConfig.LEGACY_19)
                || !unlocked(context) || helperActive() || preferences.getBoolean("proxyPausePending",false)
                || "PROXY".equals(preferences.getString("relaySource","")))return false;
        try{
            ResolveInfo resolved=pm.resolveActivity(HomeController.homeIntent(),PackageManager.MATCH_DEFAULT_ONLY);
            ActivityInfo home=resolved==null?null:resolved.activityInfo;
            return home!=null && context.getPackageName().equals(home.packageName) && HomeActivity.class.getName().equals(home.name)
                    && home.exported && home.enabled && home.applicationInfo.enabled && AppAvailability.installed(context.getPackageManager(),home.applicationInfo);
        }catch(SecurityException unavailable){return false;}
    }
    boolean directHomeSelected(){return preferences.getBoolean("directHomeKeys",false) && "DIRECT".equals(preferences.getString("routeHome",""));}
    boolean directHomeConfigured(){
        return HomeGuardPolicy.directHomeConfigured(enabled(),preferences.getBoolean("directHomeKeys",false),
                new DesktopRoutes(context,false).current(),preferences.getString("routeHome",""));
    }
    boolean directHomeReady(){return directHomeConfigured() && directHomeAvailable();}
    // This broader gate is only for an explicit HOME action or restoring its service, never a window observation.
    boolean homeActionReady(){return HomeGuardPolicy.homeActionReady(!directHomeSelected() && ready(),directHomeConfigured(),directHomeAvailable(),helperActive());}
    boolean recoveryReady(){return directHomeSelected()?directHomeReady():ready();}
    String homeActionProblem(){
        if(!enabled())return "disabled_or_paused";
        if(helperActive())return "helper_active";
        if(directHomeSelected())return directHomeReady()?"":"direct_home_changed";
        return ready()?"":"invalid_home_or_firmware";
    }
    String enableProblem(){
        if(!supported())return "此系统档位未开放桌面守护";
        if(!unlocked(context))return "等待系统解锁";
        if(directHomeSelected()){
            if(!directHomeConfigured() || !directHomeAvailable())return "直接 HOME 身份已变化，请重新检测";
            return serviceEnabled()?"":"请开启无障碍以接收 HOME";
        }
        if(!validSelection())return "请选择本机检测到的原主页";
        if(helperActive())return "请先暂停独立中转助手";
        if(!serviceEnabled() && !HomeGuardUsageService.connected())return "等待无障碍授权或绑定";
        return "";
    }
    // Persist enabled mode before requesting recovery.
    @android.annotation.SuppressLint("ApplySharedPref")
    String enable(){if(new DesktopRoutes(context).proxySelected() && !new HomeProxy(context).pause())return "代理暂停回执待确认，请先恢复原桌面";preferences.edit().putString("mode","AUTO").putBoolean("configured",true).commit();configure();new DesktopRoutes(context).observe();HomeGuardSync.refresh(context);HomeGuardAdb.ensure(context);HomeGuardRecovery.request(context,"用户启用");return enableProblem();}
    // A boot-only entry does not establish that pressing HOME can be redirected.
    boolean observationReady(){return ready() && (serviceEnabled() && HomeGuardService.connected() || HomeGuardUsageService.connected());}
    boolean compatibleReady(){return new HomeProxy(context).ready() || observationReady();}
    String summary(){
        if(!enabled())return preferences.getString("status","未配置");
        String problem=enableProblem();if(!problem.isEmpty())return problem;
        if(directHomeReady())return !HomeGuardService.connected()?"等待系统连接无障碍":"HOME 按键补偿已连接，实体效果待验证";
        if(!HomeGuardService.connected() && !HomeGuardUsageService.connected())return "等待系统绑定无障碍";
        if(HomeGuardService.usageRequired(context) && !HomeGuardService.usageAllowed(context))return "无障碍已连接  兼容检测未授权，可通过电脑配置";
        return "守护已连接";
    }
    boolean helperActive(){
        String enabled=Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if(enabled!=null)for(String entry:enabled.split(":")){ComponentName name=ComponentName.unflattenFromString(entry);if(name!=null && "home.quiet.tv.xiaomihelper".equals(name.getPackageName()))return true;}
        try{PackageInfo helper=pm.getPackageInfo("com.ccos.tv.homepage",0);return helper.applicationInfo!=null && helper.applicationInfo.enabled;}
        catch(PackageManager.NameNotFoundException ignored){return false;}
    }
}
