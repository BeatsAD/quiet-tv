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
    private static DiagnosticOperation handoverDiagnostic;
    private static boolean pendingVisibilityEvidence;
    private static String lastAccessibilityDecision="",lastUsageDecision="",lastAccessibilityObservation="",lastUsageObservation="";
    // Only exact stock-home matches, and only decision changes. Never log the foreground app stream.
    static void observed(String source,String decision,long age){
        String previous="usage".equals(source)?lastUsageDecision:lastAccessibilityDecision;
        if(previous.equals(decision))return;
        String id=DiagnosticOperation.nextId(System.currentTimeMillis());
        if("usage".equals(source)){lastUsageDecision=decision;lastUsageObservation=id;}
        else{lastAccessibilityDecision=decision;lastAccessibilityObservation=id;}
        DiagnosticLog.info("home_guard.observation","observation="+id+" source="+source+" target=selected_home decision="+decision+" ageMs="+age);
    }
    private static DiagnosticOperation diagnostic(String source,HomeGuardState state){
        DiagnosticOperation trace=new DiagnosticOperation("home_guard.handover",System.currentTimeMillis(),SystemClock.elapsedRealtime(),DiagnosticLog::info);
        String observation="原主页事件".equals(source)?lastAccessibilityObservation:"用量统计原主页".equals(source)?lastUsageObservation:"";
        DiagnosticLog.info("home_guard.source","attempt="+trace.id+" source="+source+" observation="+observation+" target="+state.selected());
        trace.stage("ELIGIBILITY",SystemClock.elapsedRealtime());return trace;
    }
    private static void finishDiagnostic(DiagnosticOperation trace,String result,String reason){
        if(trace!=null)trace.finish(result,reason,System.currentTimeMillis(),SystemClock.elapsedRealtime());
    }
    static String lastDiagnosticAttempt(){return handoverDiagnostic==null?"":handoverDiagnostic.id;}
    static final String TAG="QuietTVGuard";
    static String guard(Context c){return new ComponentName(c,HomeGuardService.class).flattenToString();}
    static String listener(Context c){return new ComponentName(c,HomeGuardRevivalListener.class).flattenToString();}
    static boolean knownPrivateHome(){
        String vendor=(Build.MANUFACTURER+" "+Build.BRAND).toLowerCase(Locale.ROOT);
        for(String token:vendor.split("\\s+"))if(token.equals("skyworth") || token.equals("coocaa"))return true;
        return false;
    }
    static boolean writeAllowed(Context c){return c.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)==PackageManager.PERMISSION_GRANTED;}
    static boolean systemWriteAllowed(Context c){return Build.VERSION.SDK_INT>=23?Settings.System.canWrite(c)
            :c.checkCallingOrSelfPermission(Manifest.permission.WRITE_SETTINGS)==PackageManager.PERMISSION_GRANTED;}
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
        finishDiagnostic(handoverDiagnostic,"CANCELLED","pending_recovery_cancelled");
        pendingVisibilityEvidence=false;
        if(check!=null)handler.removeCallbacks(check);if(readd!=null)handler.removeCallbacks(readd);if(confirmation!=null)handler.removeCallbacks(confirmation);
        check=null;readd=null;confirmation=null;recovering=false;requestedAt=0;requestedHome="";
    }
    static void request(Context supplied,String reason){
        Context c=supplied.getApplicationContext();if(Looper.myLooper()!=Looper.getMainLooper()){handler.post(()->request(c,reason));return;}
        HomeGuardState state=new HomeGuardState(c);
        if(!state.enabled() || !HomeGuardState.unlocked(c))return;
        if(state.selected().isEmpty())state.configure();
        if(!state.ready()){state.status(state.enableProblem());return;}
        if(writeAllowed(c))HomeGuardSync.ensure(c);
        if(HomeGuardService.connected()){HomeGuardService.lifecycle(reason);return;}
        if(recovering)return;
        if(!writeAllowed(c) || !systemWriteAllowed(c)){state.status(state.enableProblem().isEmpty()?"等待系统绑定无障碍":state.enableProblem());return;}
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
    static void rebindIfSilent(Context c){
        HomeGuardState state=new HomeGuardState(c);String boot=new BootAppStore(c).bootId();
        if(!state.ready() || !writeAllowed(c) || !systemWriteAllowed(c) || boot.isEmpty() || boot.equals(state.preferences.getString("silentRebindBoot","")))return;
        if(!state.preferences.edit().putString("silentRebindBoot",boot).commit())return;
        try{
            String current=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if(!Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,HomeGuardPolicy.services(current,guard(c),false)))throw new IllegalStateException();
            handler.postDelayed(()->{
                if(!new HomeGuardState(c).ready() || !writeAllowed(c))return;
                try{restoreEntry(c);}catch(SecurityException|IllegalStateException failure){new HomeGuardState(c).status("无障碍重新绑定未完成");}
            },HomeGuardPolicy.SETTLE_MS);
            DiagnosticLog.info("home_guard.rebind","source=no_window_events scope=own_service");
        }catch(SecurityException|IllegalStateException failure){state.status("无障碍重新绑定未完成");}
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
        HomeGuardState state=new HomeGuardState(c);DiagnosticOperation trace=diagnostic(reason,state);
        if(!state.ready()){finishDiagnostic(trace,"SKIPPED",!state.enabled()?"disabled_or_paused":!state.validSelection()?"invalid_home_or_firmware":"helper_active");return;}
        if(!HomeGuardState.canShow(c)){finishDiagnostic(trace,"SKIPPED","screen_or_unlock_unavailable");return;}
        launch(c,reason,state,trace);
    }
    static void launchVendor(Context c){
        HomeGuardState state=new HomeGuardState(c);DiagnosticOperation trace=diagnostic("vendor_boot",state);
        if(!new DesktopRoutes(c).vendorReady() || !HomeGuardState.canShow(c)){finishDiagnostic(trace,"SKIPPED","vendor_or_screen_unavailable");return;}
        launch(c,"厂商开机入口",state,trace);
    }
    private static void launch(Context c,String reason,HomeGuardState state,DiagnosticOperation trace){
        if(requestedAt!=0){finishDiagnostic(trace,"SKIPPED","request_pending");return;}
        handoverDiagnostic=trace;pendingVisibilityEvidence=true;trace.stage("LAUNCH_REQUEST",SystemClock.elapsedRealtime());
        HomeGuardService.homeRequested();
        requestedAt=SystemClock.uptimeMillis();requestedHome=state.selected();state.status("已请求接管，等待桌面显示");Log.i(TAG,"home requested: "+reason+" uptime="+requestedAt);
        confirmation=()->{finishDiagnostic(trace,"TIMEOUT","no_visibility_callback_5000ms");confirmation=null;requestedAt=0;requestedHome="";if(new HomeGuardState(c).automatic())new HomeGuardState(c).status("未确认进入桌面，请检查系统限制");};
        handler.postDelayed(confirmation,5000);
        try{c.startActivity(HomeController.launchIntent(c));trace.stage("DISPATCHED_UNVERIFIED",SystemClock.elapsedRealtime());}
        catch(ActivityNotFoundException|SecurityException failure){finishDiagnostic(trace,"FAILED",failure.getClass().getSimpleName());pendingVisibilityEvidence=false;handler.removeCallbacks(confirmation);confirmation=null;requestedAt=0;requestedHome="";state.status("系统拒绝启动桌面");}
    }
    static void visible(Context c){
        HomeGuardState state=new HomeGuardState(c);if(!state.automatic())return;
        state.preferences.edit().putBoolean("homeInFront",true).apply();
        HomeGuardService.homeVisible();
        // A slow first frame may arrive after the confirmation deadline; actual visibility wins.
        state.status("已进入 Quiet TV");
        if(requestedAt!=0){
            long elapsed=SystemClock.uptimeMillis()-requestedAt;boolean same=requestedHome.equals(state.selected());
            finishDiagnostic(handoverDiagnostic,same?"VISIBILITY_CALLBACK":"CANCELLED",same?"home_callback_not_screen_proof":"target_changed");
            pendingVisibilityEvidence=false;
            if(confirmation!=null)handler.removeCallbacks(confirmation);confirmation=null;requestedAt=0;requestedHome="";
            if(same){state.status("已进入 Quiet TV");Log.i(TAG,"home visible after "+elapsed+"ms");}
        }else if(pendingVisibilityEvidence && handoverDiagnostic!=null){pendingVisibilityEvidence=false;DiagnosticLog.info("home_guard.visibility","attempt="+handoverDiagnostic.id+" result=callback_without_pending_request causalityVerified=false screenVerified=false");}
    }
    static void leftHome(Context c){
        if(HomeGuardState.canShow(c))new HomeGuardState(c).preferences.edit().putBoolean("homeInFront",false).apply();
    }
    static void configureGranted(Context c) throws java.io.IOException {
        HomeGuardState state=new HomeGuardState(c);
        if(!state.ready())throw new java.io.IOException(state.enableProblem());
        if(!writeAllowed(c))throw new java.io.IOException("写安全设置权限未授予");
        if(!systemWriteAllowed(c))throw new java.io.IOException("写系统设置权限未授予");
        if(Build.VERSION.SDK_INT<27 && notificationSupported(c) && !notificationAllowed(c)){
            String current=Settings.Secure.getString(c.getContentResolver(),"enabled_notification_listeners");
            String desired=HomeGuardPolicy.services(current,listener(c),true);
            try{
                if(Settings.Secure.putString(c.getContentResolver(),"enabled_notification_listeners",desired))state.preferences.edit().putBoolean("grantedListener",true).apply();
            }catch(SecurityException|IllegalStateException unavailable){DiagnosticLog.info("home_guard.notification","configuration_unavailable");}
        }
        HomeGuardSync.ensure(c);
        request(c,"授权配置完成");
    }
    static String configurationProblem(Context c){
        if(!writeAllowed(c))return "写安全设置权限未授予";
        if(!systemWriteAllowed(c))return "写系统设置权限未授予";
        if(!new HomeGuardState(c).serviceEnabled())return "无障碍权限未开启";
        if(HomeGuardService.usageRequired() && !HomeGuardService.usageAllowed(c))return "旧系统兼容检测未授权";
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(c))return "后台启动权限未确认";
        if(!(notificationSupported(c) && notificationAllowed(c)) && !HomeGuardSync.ready(c))return "通知恢复或系统同步恢复尚未就绪";
        return "";
    }
    /** Called only from the foreground setup's already-authorized local ADB worker. */
    // Already runs on the ADB worker; keep completed grants durable before continuing/canceling.
    @android.annotation.SuppressLint("ApplySharedPref")
    static void configureAdb(Context c,AdbController adb,SetupOperations.Ticket task) throws Exception {
        HomeGuardState state=new HomeGuardState(c);state.configure();if(!state.ready())throw new java.io.IOException(state.enableProblem());
        task.check();adb.validateUser();String pkg=c.getPackageName(),user=Integer.toString(adb.user);
        String pmUser=writeAllowed(c)?"":ShellCapabilities.userOption(adb.shell("pm help"),"grant",Build.VERSION.SDK_INT,adb.user);
        String opsUser=systemWriteAllowed(c) && (!HomeGuardService.usageRequired() || HomeGuardService.usageAllowed(c)) && (Build.VERSION.SDK_INT<23 || Settings.canDrawOverlays(c))?""
                :ShellCapabilities.userOption(adb.shell("appops help"),"set",Build.VERSION.SDK_INT,adb.user);
        if(!writeAllowed(c)){adb.shell("pm grant"+pmUser+" "+pkg+" android.permission.WRITE_SECURE_SETTINGS");if(!writeAllowed(c))throw new java.io.IOException("写安全设置权限未授予");
        if(!systemWriteAllowed(c))throw new java.io.IOException("写系统设置权限未授予");state.preferences.edit().putBoolean("grantedWrite",true).commit();}
        task.check();adb.validateUser();if(!new HomeGuardState(c).ready())throw new java.io.IOException("守护已暂停或配置已改变");
        // These grants are independent: unsupported notification commands do not undo accessibility.
        String note="";
        if(!notificationSupported(c))note="此设备不支持通知恢复";
        else if(Build.VERSION.SDK_INT>=27 && !notificationAllowed(c)){
            String help="";try{help=adb.shell("cmd notification help 2>&1 || true");}catch(java.io.IOException unsupported){note="此系统需手动授权通知访问";}
            if(help.contains("allow_listener") && help.contains("user_id")){
                task.check();adb.validateUser();
                try{adb.shell("cmd notification allow_listener "+listener(c)+" "+user);}
                catch(AdbController.CommandFailure refused){note="通知访问未授予";}
                if(notificationAllowed(c))state.preferences.edit().putBoolean("grantedListener",true).commit();else note="通知访问未授予";
            }else note="此系统需手动授权通知访问";
        }
        task.check();adb.validateUser();if(!new HomeGuardState(c).ready())throw new java.io.IOException("守护已暂停或配置已改变");
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(c)){
            adb.shell("appops set"+opsUser+" "+pkg+" SYSTEM_ALERT_WINDOW allow");
            if(Settings.canDrawOverlays(c))state.preferences.edit().putBoolean("grantedOverlay",true).commit();else note="后台启动权限未确认";
        }
        if(HomeGuardService.usageRequired() && !HomeGuardService.usageAllowed(c)){
            task.check();adb.validateUser();
            String output;
            try{output=adb.shell("appops set"+opsUser+" "+pkg+" GET_USAGE_STATS allow");}
            catch(java.io.IOException failure){
                if(failure.getMessage()==null || !failure.getMessage().contains("Unknown operation"))throw failure;
                output=failure.getMessage();
            }
            if(output.contains("Unknown operation")){
                task.check();adb.validateUser();adb.shell("appops set"+opsUser+" "+pkg+" android:get_usage_stats allow");
            }
            if(!HomeGuardService.usageAllowed(c))note="旧系统兼容检测未授权";
        }
        task.check();if(!new HomeGuardState(c).ready())throw new java.io.IOException("守护已暂停或配置已改变");
        if(!systemWriteAllowed(c) && Build.VERSION.SDK_INT>=23){
            task.check();adb.validateUser();adb.shell("appops set"+opsUser+" "+pkg+" WRITE_SETTINGS allow");
            if(!systemWriteAllowed(c))note="写系统设置权限未授予";
        }
        configureGranted(c);
        if(HomeGuardSync.ready(c) && (note.equals("此设备不支持通知恢复") || note.equals("此系统需手动授权通知访问") || note.equals("通知访问未授予")))note="";
        if(note.isEmpty())note=configurationProblem(c);
        if(!note.isEmpty())throw new java.io.IOException(note+"；已完成的授权保留");
    }
}
