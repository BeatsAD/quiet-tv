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
    static final class SwitchFailure extends IOException {
        final DesktopRoutePolicy.Result result;
        SwitchFailure(DesktopRoutePolicy.Result result,String message){super(message);this.result=result;}
    }
    static final int METADATA=Build.VERSION.SDK_INT>=24?PackageManager.MATCH_DISABLED_COMPONENTS|PackageManager.MATCH_UNINSTALLED_PACKAGES:PackageManager.GET_DISABLED_COMPONENTS|PackageManager.GET_UNINSTALLED_PACKAGES;
    static final class State {
        String actual="",original="",reason="默认桌面状态未确认",resolved="";
        final List<String> claimants=new ArrayList<>();
        boolean known,ownUsable,roleAvailable,connected;
        boolean isProxy(Context context){return known && new HomeProxy(context).isComponent(actual);}
        boolean isOwn(Context context){return BuildConfig.DECLARES_HOME && known && actual.equals(new ComponentName(context,HomeActivity.class).flattenToString());}
    }
    private final Context context;
    private final PackageManager pm;
    final SetupPresets presets;
    HomeController(Context context){this(context,SetupPresets.load(context));}
    HomeController(Context context,SetupPresets presets){this.context=context;pm=context.getPackageManager();this.presets=presets;}
    static Intent homeIntent(){return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);}
    static Intent launchIntent(Context context){return new Intent(context,HomeActivity.class).setAction(Intent.ACTION_MAIN)
            .addCategory(BuildConfig.DECLARES_HOME?Intent.CATEGORY_HOME:Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);}
    private boolean ownEntryUsable(){
        if(BuildConfig.DECLARES_HOME)return component(new ComponentName(context,HomeActivity.class).flattenToString(),true);
        try{ActivityInfo info=pm.getActivityInfo(new ComponentName(context,HomeActivity.class),0);
            return info.exported && info.enabled && info.applicationInfo.enabled && (info.applicationInfo.flags&ApplicationInfo.FLAG_INSTALLED)!=0;
        }catch(PackageManager.NameNotFoundException|SecurityException failure){return false;}
    }
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
        State state=new State();state.ownUsable=ownEntryUsable();
        if(component(presets.home(),false))state.original=presets.home();
        if(state.original.isEmpty()){
            String saved=new SetupStore(context).originalHome();if(component(saved,false))state.original=saved;
        }
        state.roleAvailable=BuildConfig.DECLARES_HOME && requestIntent()!=null;
        state.connected=adb!=null && adb.connected();
        for(ResolveInfo candidate:pm.queryIntentActivities(homeIntent(),0))if(candidate.activityInfo!=null)
            state.claimants.add(new ComponentName(candidate.activityInfo.packageName,candidate.activityInfo.name).flattenToString());
        if(state.connected){
            adb.validateUser();
            String output=adb.capabilities.resolve.isEmpty()?"":adb.shell(adb.capabilities.resolve+" --brief --user "+adb.user+" -a android.intent.action.MAIN -c android.intent.category.HOME");
            String name=parseComponent(output);
            if(!adb.capabilities.resolve.isEmpty() && name.isEmpty())throw new IOException("系统 HOME 查询未返回可核对组件");
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
            state.reason=state.isProxy(context)?"系统 HOME 已指向 Quiet TV 中转助手":state.isOwn(context)?"系统 HOME 已指向 Quiet TV":"当前系统 HOME："+label;
        }
        String conflict=homeProblem(state.resolved);
        if(!conflict.isEmpty())state.reason=conflict;
        if(!state.ownUsable)state.reason="Quiet TV 启动组件不可用";
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
    Intent request(){if(!BuildConfig.DECLARES_HOME)throw new IllegalStateException("此兼容包不声明 HOME，请使用原桌面中转");Intent intent=requestIntent();if(intent==null)throw new IllegalStateException("系统默认桌面入口不可用");return intent;}
    void remember(State state) throws Exception {new SetupStore(context).baseline(state.known?state.actual:"",!state.original.isEmpty()?state.original:state.known && !state.isOwn(context) && !state.isProxy(context)?state.actual:"");}
    void switchToOwn(AdbController adb) throws Exception {
        if(!BuildConfig.DECLARES_HOME)throw new SwitchFailure(DesktopRoutePolicy.Result.UNSUPPORTED,"此兼容包使用原桌面中转，不设置默认 HOME");
        switchTo(adb,new ComponentName(context,HomeActivity.class).flattenToString());
    }
    void switchTo(AdbController adb,String desired) throws Exception {
        State before=inspect(adb);if(!component(desired,true))throw new IOException("目标 HOME 组件不可用");remember(before);
        if(before.known && desired.equals(before.actual))return;
        adb.validateUser();if(adb.capabilities.home.isEmpty())throw new SwitchFailure(before.roleAvailable?DesktopRoutePolicy.Result.UNKNOWN:DesktopRoutePolicy.Result.UNSUPPORTED,unavailableSwitchReason(before.roleAvailable,true,""));SetupStore store=new SetupStore(context);
        int attempt=adb.generation();
        store.homeSwitch(before.actual,desired,adb.user,"running",before.resolved);
        try{
            State after=setAndVerify(adb,desired);
            store.homeSwitch(before.actual,desired,adb.user,"complete",after.actual);
        }catch(Exception failure){
            String observed="未确认",status="incomplete";
            // A disconnected/cancelled client is never reconnected or replayed for rollback.
            if(adb.connected() && adb.generation()==attempt)try{
                adb.validateUser();
                if(before.known && component(before.actual,true)){
                    State restored=inspect(adb);observed=restored.resolved;
                    if(restored.known && before.actual.equals(restored.actual))status="unchanged";
                    else{
                        adb.shell(adb.capabilities.home+" --user "+adb.user+" "+before.actual);
                        restored=inspect(adb);observed=restored.resolved;
                        if(restored.known && before.actual.equals(restored.actual))status="rolled-back";
                    }
                }else observed=inspect(adb).resolved;
            }catch(Exception rollback){failure.addSuppressed(rollback);}
            store.homeSwitch(before.actual,desired,android.os.Process.myUid()/100000,status,observed);
            if(failure instanceof AdbController.CommandFailure || failure instanceof SwitchFailure && ((SwitchFailure)failure).result==DesktopRoutePolicy.Result.RETAINED){
                if("unchanged".equals(status) || "rolled-back".equals(status))throw new SwitchFailure(DesktopRoutePolicy.Result.RETAINED,failure.getMessage());
                throw new SwitchFailure(DesktopRoutePolicy.Result.UNKNOWN,"默认桌面恢复未确认，停止自动回退");
            }
            throw failure;
        }
    }
    /** Reapply even when resolution is ours: changing HOME candidates clears the saved preference. */
    State setOwnAndVerify(AdbController adb) throws Exception {
        if(!BuildConfig.DECLARES_HOME)throw new IOException("此兼容包不声明 HOME");
        return setAndVerify(adb,new ComponentName(context,HomeActivity.class).flattenToString());
    }
    State setAndVerify(AdbController adb,String desired) throws Exception {
        if(!component(desired,true))throw new IOException("目标 HOME 组件不可用");
        adb.validateUser();
        if(adb.capabilities.home.isEmpty())throw new IOException("当前系统未提供可核对的桌面切换命令");
        adb.shell(adb.capabilities.home+" --user "+adb.user+" "+desired);
        State after=inspect(adb);
        if(!after.known || !desired.equals(after.actual))throw new SwitchFailure(after.known?DesktopRoutePolicy.Result.RETAINED:DesktopRoutePolicy.Result.UNKNOWN,"未设为目标 HOME："+after.reason);
        return after;
    }
    void switchToOriginal(AdbController adb,String original) throws Exception {
        new HomeGuardState(context).pause("恢复原桌面，守护已暂停");
        if(!original.equals(new SetupStore(context).originalHome()) || !component(original,true))throw new IOException("原桌面组件不可用或记录不匹配");
        adb.validateUser();if(adb.capabilities.home.isEmpty())throw new IOException("当前系统未提供可核对的桌面切换命令，请使用系统默认桌面设置");adb.shell(adb.capabilities.home+" --user "+adb.user+" "+original);
        State after=inspect(adb);if(!after.known || !after.actual.equals(original))throw new IOException("原桌面默认状态未确认");
    }
}
