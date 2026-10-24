package home.quiet.tv;

import android.app.role.RoleManager;
import android.os.Build;
import android.provider.Settings;
import android.content.*;
import android.content.pm.*;
import java.io.IOException;
import java.util.*;

/** HOME identity and transport availability are read before any user-confirmed mutation. */
final class HomeController {
    static final int METADATA=Build.VERSION.SDK_INT>=24?PackageManager.MATCH_DISABLED_COMPONENTS|PackageManager.MATCH_UNINSTALLED_PACKAGES:PackageManager.GET_DISABLED_COMPONENTS|PackageManager.GET_UNINSTALLED_PACKAGES;
    static final class State {
        String actual="",original="",reason="默认桌面状态未确认",resolved="";
        final List<String> claimants=new ArrayList<>();
        boolean known,ownUsable,roleAvailable,connected;
        boolean isOwn(Context context){return known && actual.equals(new ComponentName(context,HomeActivity.class).flattenToString());}
    }
    private final Context context;
    private final PackageManager pm;
    final SetupPresets presets;
    HomeController(Context context){this(context,SetupPresets.load(context));}
    HomeController(Context context,SetupPresets presets){this.context=context;pm=context.getPackageManager();this.presets=presets;}
    static Intent homeIntent(){return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);}
    boolean component(String flattened,boolean requireEnabled){
        ComponentName name=ComponentName.unflattenFromString(flattened);if(name==null)return false;
        try{
            ActivityInfo activity=pm.getActivityInfo(name,METADATA);
            if(!activity.exported)return false;
            if(requireEnabled && (!activity.enabled || !activity.applicationInfo.enabled || (activity.applicationInfo.flags&ApplicationInfo.FLAG_INSTALLED)==0))return false;
            for(ResolveInfo candidate:pm.queryIntentActivities(homeIntent().setPackage(name.getPackageName()),requireEnabled?0:METADATA))
                if(candidate.activityInfo!=null && name.equals(new ComponentName(candidate.activityInfo.packageName,candidate.activityInfo.name)))return true;
        }catch(PackageManager.NameNotFoundException|SecurityException ignored){}return false;
    }
    State inspect(AdbController adb) throws Exception {
        State state=new State();state.ownUsable=component(new ComponentName(context,HomeActivity.class).flattenToString(),true);
        if(component(presets.home(),false))state.original=presets.home();
        state.roleAvailable=requestIntent()!=null;
        state.connected=adb!=null && adb.connected();
        for(ResolveInfo candidate:pm.queryIntentActivities(homeIntent(),0))if(candidate.activityInfo!=null)
            state.claimants.add(new ComponentName(candidate.activityInfo.packageName,candidate.activityInfo.name).flattenToString());
        if(state.connected){
            adb.validateUser();
            String output=adb.capabilities.resolve.isEmpty()?"":adb.shell(adb.capabilities.resolve+" --brief --user "+adb.user+" -a android.intent.action.MAIN -c android.intent.category.HOME");
            String name=parseComponent(output);
            state.resolved=name;
            if(!name.isEmpty() && component(name,true)){state.actual=ComponentName.unflattenFromString(name).flattenToString();state.known=true;}
        }
        if(!state.known){
            ComponentName name=null;ResolveInfo resolved=pm.resolveActivity(homeIntent(),PackageManager.MATCH_DEFAULT_ONLY);if(resolved!=null && resolved.activityInfo!=null)name=new ComponentName(resolved.activityInfo.packageName,resolved.activityInfo.name);
            if(name!=null && component(name.flattenToString(),true)){state.actual=name.flattenToString();state.known=true;}
            if(name!=null)state.resolved=name.flattenToString();
        }
        if(state.known){
            String label="其他桌面";try{label=pm.getActivityInfo(ComponentName.unflattenFromString(state.actual),0).loadLabel(pm).toString();}catch(PackageManager.NameNotFoundException ignored){}
            state.reason=state.isOwn(context)?"系统 HOME 已指向 Quiet TV":"当前系统 HOME："+label;
        }
        String conflict=homeProblem(state.resolved);
        if(!conflict.isEmpty())state.reason=conflict;
        if(!state.ownUsable)state.reason="Quiet TV HOME 组件不可用";
        return state;
    }
    static String parseComponent(String output){for(String line:output.split("\\r?\\n")){String value=line.trim();if(value.matches("(?:android|[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)/[A-Za-z0-9_.$]+"))return value;}return "";}
    static String homeProblem(String component){
        if(component.startsWith("android/"))return "系统尚未选定默认桌面，请在电视确认；不会自动禁用候选应用";
        if(component.startsWith("com.google.android.tungsten.setupwraith/"))return "Google TV 设置向导正在接管 HOME；当前固件需单独适配，不会自动禁用";
        return "";
    }
    private Intent requestIntent(){
        try{
            if(Build.VERSION.SDK_INT>=29){Intent role=Api29.request(context);if(role!=null && role.resolveActivity(pm)!=null)return role;}
            Intent settings=new Intent(Settings.ACTION_HOME_SETTINGS);
            if(settings.resolveActivity(pm)!=null)return settings;
        }catch(RuntimeException ignored){}return null;
    }
    @android.annotation.TargetApi(29)
    private static final class Api29 {
        static Intent request(Context context){RoleManager roles=context.getSystemService(RoleManager.class);return roles!=null && roles.isRoleAvailable(RoleManager.ROLE_HOME)?roles.createRequestRoleIntent(RoleManager.ROLE_HOME):null;}
    }
    static String unavailableSwitchReason(boolean systemEntry,boolean connected,String command){
        if(!connected || !command.isEmpty())return "";
        return systemEntry?"当前固件未提供 ADB 桌面切换命令，请使用系统默认桌面设置"
            :"当前固件未提供默认桌面设置入口或 ADB 切换命令；可临时使用 Quiet TV，请保留原桌面";
    }
    Intent request(){Intent intent=requestIntent();if(intent==null)throw new IllegalStateException("系统默认桌面入口不可用");return intent;}
    void remember(State state) throws Exception {new SetupStore(context).baseline(state.known?state.actual:"",state.original);}
    void switchToOwn(AdbController adb) throws Exception {
        State before=inspect(adb);if(!before.ownUsable)throw new IOException("Quiet TV HOME 组件不可用");remember(before);
        if(before.isOwn(context))return;
        adb.validateUser();if(adb.capabilities.home.isEmpty())throw new IOException(unavailableSwitchReason(before.roleAvailable,true,""));SetupStore store=new SetupStore(context);
        String desired=new ComponentName(context,HomeActivity.class).flattenToString();
        store.homeSwitch(before.actual,desired,adb.user,"running",before.resolved);
        try{
            State after=setOwnAndVerify(adb);
            store.homeSwitch(before.actual,desired,adb.user,"complete",after.actual);
        }catch(Exception failure){
            String observed="未确认",status="incomplete";
            // A disconnected/cancelled client is never reconnected or replayed for rollback.
            if(adb.connected())try{
                adb.validateUser();
                if(before.known && component(before.actual,true)){
                    adb.shell(adb.capabilities.home+" --user "+adb.user+" "+before.actual);
                    State restored=inspect(adb);observed=restored.resolved;
                    if(restored.known && before.actual.equals(restored.actual))status="rolled-back";
                }else observed=inspect(adb).resolved;
            }catch(Exception rollback){failure.addSuppressed(rollback);}
            store.homeSwitch(before.actual,desired,android.os.Process.myUid()/100000,status,observed);
            throw failure;
        }
    }
    /** Reapply even when resolution is ours: changing HOME candidates clears the saved preference. */
    State setOwnAndVerify(AdbController adb) throws Exception {
        if(!component(new ComponentName(context,HomeActivity.class).flattenToString(),true))throw new IOException("Quiet TV HOME 组件不可用");
        adb.validateUser();
        if(adb.capabilities.home.isEmpty())throw new IOException("当前系统未提供可核对的桌面切换命令");
        adb.shell(adb.capabilities.home+" --user "+adb.user+" "+new ComponentName(context,HomeActivity.class).flattenToString());
        State after=inspect(adb);
        if(!after.isOwn(context))throw new IOException("未设为系统 HOME："+after.reason);
        return after;
    }
    void switchToOriginal(AdbController adb,String original) throws Exception {
        new HomeGuardState(context).pause("恢复原桌面，守护已暂停");
        if(!original.equals(presets.home()) || !component(original,true))throw new IOException("原桌面组件不可用或适配不匹配");
        adb.validateUser();if(adb.capabilities.home.isEmpty())throw new IOException("当前系统未提供可核对的桌面切换命令，请使用系统默认桌面设置");adb.shell(adb.capabilities.home+" --user "+adb.user+" "+original);
        State after=inspect(adb);if(!after.known || !after.actual.equals(original))throw new IOException("原桌面默认状态未确认");
    }
}
