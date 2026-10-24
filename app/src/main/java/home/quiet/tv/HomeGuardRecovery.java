package home.quiet.tv;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import java.util.Locale;

/** Shared, finite recovery. No background ADB connection or notification content access. */
final class HomeGuardRecovery {
    private static final Handler handler=new Handler(Looper.getMainLooper());
    private static Runnable check,readd,confirmation;
    private static boolean recovering;
    private static long requestedAt;
    private static String requestedHome="";
    static final String TAG="QuietTVGuard";
    static String guard(Context c){return new ComponentName(c,HomeGuardService.class).flattenToString();}
    static String listener(Context c){return new ComponentName(c,HomeGuardRevivalListener.class).flattenToString();}
    static boolean knownPrivateHome(){
        String vendor=(Build.MANUFACTURER+" "+Build.BRAND).toLowerCase(Locale.ROOT);
        for(String token:vendor.split("\\s+"))if(token.equals("skyworth") || token.equals("coocaa"))return true;
        return false;
    }
    static boolean writeAllowed(Context c){return c.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)==PackageManager.PERMISSION_GRANTED;}
    static boolean notificationSupported(Context c){
        android.app.ActivityManager manager=(android.app.ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
        return Build.VERSION.SDK_INT>29 || manager==null || !manager.isLowRamDevice();
    }
    static boolean notificationAllowed(Context c){
        if(Build.VERSION.SDK_INT>=27){
            android.app.NotificationManager manager=(android.app.NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
            return manager!=null && manager.isNotificationListenerAccessGranted(new ComponentName(c,HomeGuardRevivalListener.class));
        }
        String current=Settings.Secure.getString(c.getContentResolver(),"enabled_notification_listeners");
        if(current!=null)for(String entry:current.split(":"))if(new ComponentName(c,HomeGuardRevivalListener.class).equals(ComponentName.unflattenFromString(entry)))return true;
        return false;
    }
    static void cancel(){
        if(Looper.myLooper()!=Looper.getMainLooper()){handler.post(HomeGuardRecovery::cancel);return;}
        if(check!=null)handler.removeCallbacks(check);if(readd!=null)handler.removeCallbacks(readd);if(confirmation!=null)handler.removeCallbacks(confirmation);
        check=null;readd=null;confirmation=null;recovering=false;requestedAt=0;requestedHome="";
    }
    static void request(Context supplied,String reason){
        Context c=supplied.getApplicationContext();if(Looper.myLooper()!=Looper.getMainLooper()){handler.post(()->request(c,reason));return;}
        HomeGuardState state=new HomeGuardState(c);
        if(!state.enabled() || !HomeGuardState.unlocked(c))return;
        if(!state.ready()){state.status(state.enableProblem());return;}
        if(HomeGuardService.connected()){HomeGuardService.lifecycle(reason);return;}
        if(recovering)return;
        if(!writeAllowed(c)){state.status(state.enableProblem().isEmpty()?"等待系统绑定无障碍":state.enableProblem());return;}
        recovering=true;
        try{restoreEntry(c);}
        catch(SecurityException|IllegalStateException failure){recovering=false;state.status("无法恢复无障碍配置");Log.w(TAG,"restore unavailable");return;}
        check=()->{
            check=null;HomeGuardState now=new HomeGuardState(c);
            if(!now.ready() || !writeAllowed(c)){recovering=false;return;}
            if(HomeGuardService.connected()){recovering=false;return;}
            try{
                String current=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if(!Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,HomeGuardPolicy.services(current,guard(c),false)))throw new IllegalStateException();
                readd=()->{
                    readd=null;recovering=false;
                    if(!new HomeGuardState(c).ready() || !writeAllowed(c))return;
                    try{restoreEntry(c);new HomeGuardState(c).status("已请求重新绑定，等待系统连接");}
                    catch(SecurityException|IllegalStateException failure){new HomeGuardState(c).status("无法恢复无障碍配置");}
                };
                handler.postDelayed(readd,HomeGuardPolicy.SETTLE_MS);
            }catch(SecurityException|IllegalStateException failure){recovering=false;now.status("无法请求无障碍重新绑定");}
        };
        handler.postDelayed(check,5000);Log.i(TAG,"recovery requested: "+reason);
    }
    private static void restoreEntry(Context c){
        String current=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String desired=HomeGuardPolicy.services(current,guard(c),true);
        if(!desired.equals(current) && !Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,desired))throw new IllegalStateException();
        if(!Settings.Secure.putInt(c.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,1))throw new IllegalStateException();
    }
    static void noteRevival(Context c){
        HomeGuardState state=new HomeGuardState(c);if(!state.enabled())return;
        long now=SystemClock.elapsedRealtime(),start=state.preferences.getLong("revivalStart",-1);
        int boot=Build.VERSION.SDK_INT>=24?Settings.Global.getInt(c.getContentResolver(),"boot_count",-1):-1;
        boolean same=boot<0 || boot==state.preferences.getInt("revivalBoot",-2);
        int count=same && start>=0 && now>=start && now-start<600000?state.preferences.getInt("revivalCount",0):0;
        state.preferences.edit().putLong("revivalStart",count==0?now:start).putInt("revivalCount",count+1).putInt("revivalBoot",boot).apply();
    }
    static boolean unknownAllowed(Context c){
        HomeGuardState state=new HomeGuardState(c);long now=SystemClock.elapsedRealtime();
        int boot=Build.VERSION.SDK_INT>=24?Settings.Global.getInt(c.getContentResolver(),"boot_count",-1):-1;
        boolean same=boot<0 || boot==state.preferences.getInt("revivalBoot",-2);
        if(same && now>=state.preferences.getLong("revivalStart",Long.MAX_VALUE) && now-state.preferences.getLong("revivalStart",0)<600000 && state.preferences.getInt("revivalCount",0)>=3)return false;
        if(now<HomeGuardPolicy.BOOT_WINDOW_MS)return true;
        return state.preferences.getBoolean("homeInFront",false) && same && state.preferences.getInt("revivalCount",0)>0
            && now-state.preferences.getLong("revivalStart",0)>=0 && now-state.preferences.getLong("revivalStart",0)<60000;
    }
    static void launch(Context c,String reason){
        HomeGuardState state=new HomeGuardState(c);if(!state.ready() || !HomeGuardState.canShow(c))return;
        if(requestedAt!=0)return;
        requestedAt=SystemClock.uptimeMillis();requestedHome=state.selected();state.status("已请求接管，等待桌面显示");Log.i(TAG,"home requested: "+reason+" uptime="+requestedAt);
        confirmation=()->{confirmation=null;requestedAt=0;requestedHome="";if(new HomeGuardState(c).enabled())new HomeGuardState(c).status("未确认进入桌面，请检查系统限制");};
        handler.postDelayed(confirmation,5000);
        try{c.startActivity(new Intent(c,HomeActivity.class).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));}
        catch(ActivityNotFoundException|SecurityException failure){handler.removeCallbacks(confirmation);confirmation=null;requestedAt=0;requestedHome="";state.status("系统拒绝启动桌面");}
    }
    static void visible(Context c){
        HomeGuardState state=new HomeGuardState(c);if(!state.enabled())return;
        state.preferences.edit().putBoolean("homeInFront",true).apply();
        HomeGuardService.homeVisible();
        // A slow first frame may arrive after the confirmation deadline; actual visibility wins.
        state.status("已进入 Quiet TV");
        if(requestedAt!=0){
            long elapsed=SystemClock.uptimeMillis()-requestedAt;boolean same=requestedHome.equals(state.selected());
            if(confirmation!=null)handler.removeCallbacks(confirmation);confirmation=null;requestedAt=0;requestedHome="";
            if(same){state.status("已进入 Quiet TV");Log.i(TAG,"home visible after "+elapsed+"ms");}
        }
    }
    static void leftHome(Context c){
        if(HomeGuardState.canShow(c))new HomeGuardState(c).preferences.edit().putBoolean("homeInFront",false).apply();
    }
    /** Called only from the foreground setup's already-authorized local ADB worker. */
    // Already runs on the ADB worker; keep completed grants durable before continuing/canceling.
    @android.annotation.SuppressLint("ApplySharedPref")
    static void configureAdb(Context c,AdbController adb,SetupOperations.Ticket task) throws Exception {
        HomeGuardState state=new HomeGuardState(c);state.configure();if(!state.ready())throw new java.io.IOException(state.enableProblem());
        task.check();adb.validateUser();String pkg=c.getPackageName(),user=Integer.toString(adb.user);
        if(!writeAllowed(c)){adb.shell("pm grant --user "+user+" "+pkg+" android.permission.WRITE_SECURE_SETTINGS");if(!writeAllowed(c))throw new java.io.IOException("写安全设置权限未授予");state.preferences.edit().putBoolean("grantedWrite",true).commit();}
        task.check();adb.validateUser();if(!new HomeGuardState(c).ready())throw new java.io.IOException("守护已暂停或配置已改变");
        // These grants are independent: unsupported notification commands do not undo accessibility.
        String note="";
        if(!notificationSupported(c))note="此设备不支持通知恢复";
        else if(!notificationAllowed(c)){
            String help="";if(Build.VERSION.SDK_INT>=24)try{help=adb.shell("cmd notification help 2>&1 || true");}catch(java.io.IOException unsupported){note="此系统需手动授权通知访问";}
            if(help.contains("allow_listener") && help.contains("user_id")){
                task.check();adb.validateUser();adb.shell("cmd notification allow_listener "+listener(c)+" "+user);
                if(notificationAllowed(c))state.preferences.edit().putBoolean("grantedListener",true).commit();else note="通知访问未授予";
            }else note="此系统需手动授权通知访问";
        }
        task.check();adb.validateUser();if(!new HomeGuardState(c).ready())throw new java.io.IOException("守护已暂停或配置已改变");
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(c)){
            adb.shell("appops set --user "+user+" "+pkg+" SYSTEM_ALERT_WINDOW allow");
            if(Settings.canDrawOverlays(c))state.preferences.edit().putBoolean("grantedOverlay",true).commit();else note="后台启动权限未确认";
        }
        task.check();if(!new HomeGuardState(c).ready())throw new java.io.IOException("守护已暂停或配置已改变");
        handler.post(()->request(c,"首次配置"));if(!note.isEmpty())throw new java.io.IOException(note+"；已完成的授权保留");
    }
}
