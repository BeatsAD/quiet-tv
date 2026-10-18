package home.quiet.tv;

import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.*;
import java.io.IOException;
import java.util.*;

/** HOME identity and transport availability are read before any user-confirmed mutation. */
final class HomeController {
    static final int METADATA=PackageManager.MATCH_DISABLED_COMPONENTS|PackageManager.MATCH_UNINSTALLED_PACKAGES;
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
        RoleManager roles=context.getSystemService(RoleManager.class);
        try{state.roleAvailable=roles!=null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && roles.createRequestRoleIntent(RoleManager.ROLE_HOME).resolveActivity(pm)!=null;}catch(RuntimeException ignored){}
        state.connected=adb!=null && adb.connected();
        if(state.connected){
            adb.validateUser();
            String output=adb.shell("cmd package resolve-activity --brief --user "+adb.user+" -a android.intent.action.MAIN -c android.intent.category.HOME");
            String name=parseComponent(output);
            if(!name.isEmpty() && component(name,true)){state.actual=ComponentName.unflattenFromString(name).flattenToString();state.known=true;}
        }else{
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
    Intent request(){RoleManager roles=context.getSystemService(RoleManager.class);if(roles==null || !roles.isRoleAvailable(RoleManager.ROLE_HOME))throw new IllegalStateException("系统默认桌面入口不可用");return roles.createRequestRoleIntent(RoleManager.ROLE_HOME);}
    void remember(State state) throws Exception {new SetupStore(context).baseline(state.known?state.actual:"",state.original);}
    void switchToOwn(AdbController adb) throws Exception {
        State before=inspect(adb);if(!before.ownUsable)throw new IOException("Quiet TV HOME 组件不可用");remember(before);
        if(before.isOwn(context))return;
        adb.validateUser();adb.shell("cmd package set-home-activity --user "+adb.user+" "+new ComponentName(context,HomeActivity.class).flattenToString());
        if(!inspect(adb).isOwn(context))throw new IOException("未设为默认桌面，请重新查询实际状态");
    }
    void switchToOriginal(AdbController adb,String original) throws Exception {
        if(!original.equals(presets.home()) || !component(original,true))throw new IOException("原桌面组件不可用或适配不匹配");
        adb.validateUser();adb.shell("cmd package set-home-activity --user "+adb.user+" "+original);
        State after=inspect(adb);if(!after.known || !after.actual.equals(original))throw new IOException("原桌面默认状态未确认");
    }
}
