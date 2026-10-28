package home.quiet.tv;

import android.content.Context;

/** Read-only protection shared by ordinary app management and computer maintenance. */
final class AppOperations {
    private final Context context;
    private final SetupPresets presets;
    AppOperations(Context context){this(context,SetupPresets.load(context));}
    AppOperations(Context context,SetupPresets presets){this.context=context;this.presets=presets;}
    String protection(AppRepository.App app){
        if(app.pkg.equals(context.getPackageName()) || app.pkg.equals("home.quiet.tv.test"))return "Quiet TV 与管理验证组件只读";
        if(home.quiet.proxy.ProxyContract.isPackage(app.pkg))return "HOME 代理请先恢复系统原桌面，再通过系统设置处理";
        if(new HomeGuardState(context,false).protects(app.pkg,presets))return "此原桌面或依赖必须保留";
        if(app.system && presets.app(app.pkg)==null)return "关键组件或用途未确认的预装包只读";
        if(isHome(app.pkg))return "桌面组件请通过桌面配置与恢复原桌面处理";
        return "";
    }
    private boolean isHome(String pkg){
        if(presets.home().startsWith(pkg+"/"))return true;
        for(android.content.pm.ResolveInfo value:context.getPackageManager().queryIntentActivities(HomeController.homeIntent(),HomeController.METADATA))
            if(value.activityInfo!=null && pkg.equals(value.activityInfo.packageName))return true;
        return false;
    }
}
