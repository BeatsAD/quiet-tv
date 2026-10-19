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
        String actual="",original="",reason="默认桌面状态未确认";
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
        if(state.connected){
            adb.validateUser();
            String output=adb.capabilities.resolve.isEmpty()?"":adb.shell(adb.capabilities.resolve+" --brief --user "+adb.user+" -a android.intent.action.MAIN -c android.intent.category.HOME");
            String name=parseComponent(output);
            if(!name.isEmpty() && component(name,true)){state.actual=ComponentName.unflattenFromString(name).flattenToString();state.known=true;}
        }
        if(!state.known){
            ComponentName name=null;ResolveInfo resolved=pm.resolveActivity(homeIntent(),PackageManager.MATCH_DEFAULT_ONLY);if(resolved!=null && resolved.activityInfo!=null)name=new ComponentName(resolved.activityInfo.packageName,resolved.activityInfo.name);
            if(name!=null && component(name.flattenToString(),true)){state.actual=name.flattenToString();state.known=true;}
        }
        if(state.known){
            String label="其他桌面";try{label=pm.getActivityInfo(ComponentName.unflattenFromString(state.actual),0).loadLabel(pm).toString();}catch(PackageManager.NameNotFoundException ignored){}
            state.reason=state.isOwn(context)?"Quiet TV 已是默认桌面":"当前默认桌面："+label;
        }
        if(!state.ownUsable)state.reason="Quiet TV HOME 组件不可用";
        return state;
    }
    static String parseComponent(String output){for(String line:output.split("\\r?\\n")){String value=line.trim();if(value.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+"))return value;}return "";}
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
    Intent request(){Intent intent=requestIntent();if(intent==null)throw new IllegalStateException("系统默认桌面入口不可用");return intent;}
    void remember(State state) throws Exception {new SetupStore(context).baseline(state.known?state.actual:"",state.original);}
    void switchToOwn(AdbController adb) throws Exception {
        State before=inspect(adb);if(!before.ownUsable)throw new IOException("Quiet TV HOME 组件不可用");remember(before);
        if(before.isOwn(context))return;
        adb.validateUser();if(adb.capabilities.home.isEmpty())throw new IOException("当前系统未提供可核对的桌面切换命令，请使用系统默认桌面设置");adb.shell(adb.capabilities.home+" --user "+adb.user+" "+new ComponentName(context,HomeActivity.class).flattenToString());
        if(!inspect(adb).isOwn(context))throw new IOException("未设为默认桌面，请重新查询实际状态");
    }
    void switchToOriginal(AdbController adb,String original) throws Exception {
        if(!original.equals(presets.home()) || !component(original,true))throw new IOException("原桌面组件不可用或适配不匹配");
        adb.validateUser();if(adb.capabilities.home.isEmpty())throw new IOException("当前系统未提供可核对的桌面切换命令，请使用系统默认桌面设置");adb.shell(adb.capabilities.home+" --user "+adb.user+" "+original);
        State after=inspect(adb);if(!after.known || !after.actual.equals(original))throw new IOException("原桌面默认状态未确认");
    }
}
