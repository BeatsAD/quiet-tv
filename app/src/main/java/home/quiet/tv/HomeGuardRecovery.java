package home.quiet.tv;

import android.Manifest;
import android.app.PendingIntent;
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
    private static boolean accessibilityRestarted;
    private static boolean revivalNoted,restoredInStandby;
    private static long restoredAt=-1;
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
    private static DiagnosticOperation diagnostic(String source,HomeGuardState state){return diagnostic(source,state,state.selected());}
    private static DiagnosticOperation diagnostic(String source,HomeGuardState state,String target){
        DiagnosticOperation trace=new DiagnosticOperation("home_guard.handover",System.currentTimeMillis(),SystemClock.elapsedRealtime(),DiagnosticLog::info);
        String observation="原主页事件".equals(source)?lastAccessibilityObservation:"用量统计原主页".equals(source)?lastUsageObservation:"";
        DiagnosticLog.info("home_guard.source","attempt="+trace.id+" source="+source+" observation="+observation+" target="+target);
        trace.stage("ELIGIBILITY",SystemClock.elapsedRealtime());return trace;
    }
    private static void finishDiagnostic(DiagnosticOperation trace,String result,String reason){
        if(trace!=null)trace.finish(result,reason,System.currentTimeMillis(),SystemClock.elapsedRealtime());
    }
    static String lastDiagnosticAttempt(){return handoverDiagnostic==null?"":handoverDiagnostic.id;}
    static boolean handoverPending(){return requestedAt!=0;}
    static final String TAG="QuietTVGuard";
    static String guard(Context c){return new ComponentName(c,HomeGuardService.class).flattenToString();}
    static String listener(Context c){return new ComponentName(c,HomeGuardRevivalListener.class).flattenToString();}
    static boolean knownPrivateHome(){
        return HomeGuardPolicy.coocaa(Build.MANUFACTURER,Build.BRAND);
    }
    static boolean writeAllowed(Context c){return c.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)==PackageManager.PERMISSION_GRANTED;}
    static boolean systemWriteAllowed(Context c){return Build.VERSION.SDK_INT>=23?Settings.System.canWrite(c)
            :c.checkCallingOrSelfPermission(Manifest.permission.WRITE_SETTINGS)==PackageManager.PERMISSION_GRANTED;}
    static boolean notificationSupported(Context c){
        android.app.ActivityManager manager=(android.app.ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
        return Build.VERSION.SDK_INT>29 || manager==null || !UiCompat.lowRam(manager);
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
        restoredInStandby=false;
        restoredAt=-1;
        if(check!=null)handler.removeCallbacks(check);if(readd!=null)handler.removeCallbacks(readd);if(confirmation!=null)handler.removeCallbacks(confirmation);
        check=null;readd=null;confirmation=null;recovering=false;requestedAt=0;requestedHome="";
    }
    static void request(Context supplied,String reason){
        Context c=supplied.getApplicationContext();if(Looper.myLooper()!=Looper.getMainLooper()){handler.post(()->request(c,reason));return;}
        HomeGuardState state=new HomeGuardState(c);
        if(!state.enabled() || !HomeGuardState.unlocked(c))return;
        if(state.selected().isEmpty())state.configure();
        if(!state.recoveryReady()){state.status(state.enableProblem());return;}
        if(writeAllowed(c))HomeGuardSync.ensure(c);
        if(HomeGuardService.connected()){HomeGuardService.lifecycle(reason);return;}
        if(HomeGuardUsageService.eligible(c)){HomeGuardUsageService.ensure(c);return;}
        if(recovering)return;
        if(!writeAllowed(c) || !systemWriteAllowed(c)){state.status(state.enableProblem().isEmpty()?"等待系统绑定无障碍":state.enableProblem());return;}
        recovering=true;
        try{restoreEntry(c);}
        catch(SecurityException|IllegalStateException failure){recovering=false;state.status(HisenseGuardRefresh.supported()?"固件未接受无障碍配置，请连接 ADB 修复":"无法恢复无障碍配置");Log.w(TAG,"restore unavailable");return;}
        check=()->{
            check=null;HomeGuardState now=new HomeGuardState(c);
            if(!now.recoveryReady() || !writeAllowed(c)){recovering=false;return;}
            if(HomeGuardService.connected()){recovering=false;return;}
            try{
                String current=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if(!Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,HomeGuardPolicy.services(current,guard(c),false)))throw new IllegalStateException();
                readd=()->{
                    readd=null;recovering=false;
                    if(!new HomeGuardState(c).recoveryReady() || !writeAllowed(c))return;
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
                if(!new HomeGuardState(c).recoveryReady() || !writeAllowed(c))return;
                try{restoreEntry(c);}catch(SecurityException|IllegalStateException failure){new HomeGuardState(c).status("无障碍重新绑定未完成");}
            },HomeGuardPolicy.SETTLE_MS);
            DiagnosticLog.info("home_guard.rebind","source=no_window_events scope=own_service");
        }catch(SecurityException|IllegalStateException failure){state.status("无障碍重新绑定未完成");}
    }
    private static void restoreEntry(Context c){
        boolean missing=!new HomeGuardState(c).serviceEnabled();
        String current=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String desired=HomeGuardPolicy.services(current,guard(c),true);
        if(!desired.equals(current) && !Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,desired))throw new IllegalStateException();
        if(!Settings.Secure.putInt(c.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,1))throw new IllegalStateException();
        if(HisenseGuardRefresh.supported() && !new HomeGuardState(c,false).serviceEnabled())throw new IllegalStateException("固件未接受无障碍配置，请连接 ADB 修复");
        if(missing)recovered(c);
    }
    static void recovered(Context c){restoredAt=SystemClock.elapsedRealtime();if(!HomeGuardState.interactive(c))restoredInStandby=true;}
    // Aerial's Android 6 Xiaomi recovery: a bound but silent service needs the global switch.
    // Restore it even if the guard is paused meanwhile, so other accessibility services recover.
    static boolean restartSilentAccessibility(Context c){
        if(accessibilityRestarted || !HomeGuardPolicy.xiaomiRecovery(Build.MANUFACTURER,Build.BRAND,Build.VERSION.SDK_INT,BuildConfig.LEGACY_19)
                || Build.VERSION.SDK_INT>23 || !new HomeGuardState(c).ready() || !HomeGuardState.canShow(c) || !writeAllowed(c))return false;
        if(Settings.Secure.getInt(c.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0)!=1)return false;
        try{
            if(!Settings.Secure.putInt(c.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0))return false;
            accessibilityRestarted=true;
            handler.postDelayed(()->{
                try{if(!Settings.Secure.putInt(c.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,1))throw new IllegalStateException();
                    DiagnosticLog.info("home_guard.accessibility_restart","restored=true");}
                catch(SecurityException|IllegalStateException failure){DiagnosticLog.error("home_guard.accessibility_restart.failed",failure);}
            },HomeGuardPolicy.SETTLE_MS);
            DiagnosticLog.info("home_guard.accessibility_restart","reason=no_window_events once_per_process=true");return true;
        }catch(SecurityException|IllegalStateException failure){DiagnosticLog.error("home_guard.accessibility_restart.failed",failure);return false;}
    }
    static void noteRevival(Context c){
        HomeGuardState state=new HomeGuardState(c);if(!state.enabled())return;
        long updated=0;try{updated=c.getPackageManager().getPackageInfo(c.getPackageName(),0).lastUpdateTime;}catch(PackageManager.NameNotFoundException ignored){}
        if(!HomeGuardPolicy.countRevival(HomeGuardState.interactive(c),SystemClock.elapsedRealtime(),revivalNoted,System.currentTimeMillis()-updated))return;
        revivalNoted=true;
        // Android 5/6 has no boot_count. A wall-clock event before this boot cannot suppress it.
        long now=System.currentTimeMillis(),start=state.preferences.getLong("revivalWallStart",-1);
        boolean same=start>=now-SystemClock.elapsedRealtime() && start<=now;
        int count=same && now-start<600000?state.preferences.getInt("revivalCount",0):0;
        state.preferences.edit().putLong("revivalWallStart",count==0?now:start).putInt("revivalCount",count+1).apply();
    }
    static boolean unknownAllowed(Context c){
        HomeGuardState state=new HomeGuardState(c);long now=System.currentTimeMillis(),uptime=SystemClock.elapsedRealtime();
        long start=state.preferences.getLong("revivalWallStart",-1);boolean same=start>=now-uptime && start<=now;
        if(!restoredInStandby && same && now-start<600000 && state.preferences.getInt("revivalCount",0)>=3)return false;
        if(uptime<HomeGuardPolicy.BOOT_WINDOW_MS)return true;
        return state.preferences.getBoolean("homeInFront",false) && (restoredInStandby || restoredAt>=0 && uptime>=restoredAt && uptime-restoredAt<60000 || same && state.preferences.getInt("revivalCount",0)>0 && now-start<60000);
    }
    static void launch(Context c,String reason){
        HomeGuardState state=new HomeGuardState(c);DiagnosticOperation trace=diagnostic(reason,state);
        if(!state.ready()){finishDiagnostic(trace,"SKIPPED",!state.enabled()?"disabled_or_paused":!state.validSelection()?"invalid_home_or_firmware":"helper_active");return;}
        if(!HomeGuardState.canShow(c)){finishDiagnostic(trace,"SKIPPED","screen_or_unlock_unavailable");return;}
        launch(c,reason,state,trace);
    }
    static void launchHomeAction(Context c,String reason,String observation){
        HomeGuardService.cancelInputTransition();
        HomeGuardState state=new HomeGuardState(c);String target=new ComponentName(c,HomeActivity.class).flattenToString();
        DiagnosticOperation trace=diagnostic(reason,state,target);
        DiagnosticLog.info("home_guard.home_trigger","attempt="+trace.id+" observation="+observation+" direct="+state.directHomeConfigured());
        if(!state.homeActionReady()){
            finishDiagnostic(trace,"SKIPPED",state.homeActionProblem());return;
        }
        if(!HomeGuardState.canShow(c)){finishDiagnostic(trace,"SKIPPED","screen_or_unlock_unavailable");return;}
        launch(c,reason,state,trace,target);
    }
    static void launchVendor(Context c){
        HomeGuardState state=new HomeGuardState(c);DiagnosticOperation trace=diagnostic("vendor_boot",state);
        if(!new DesktopRoutes(c).vendorReady() || !HomeGuardState.canShow(c)){finishDiagnostic(trace,"SKIPPED","vendor_or_screen_unavailable");return;}
        launch(c,"厂商开机入口",state,trace);
    }
    private static void launch(Context c,String reason,HomeGuardState state,DiagnosticOperation trace){launch(c,reason,state,trace,state.selected());}
    private static void launch(Context c,String reason,HomeGuardState state,DiagnosticOperation trace,String target){
        if(HomeGuardService.inputTransition()){finishDiagnostic(trace,"SKIPPED","input_transition");return;}
        if(HomeGuardPolicy.handoverBlocked(requestedAt!=0,HomeGuardService.homeResumed())){
            finishDiagnostic(trace,"SKIPPED",requestedAt!=0?"request_pending":"home_already_resumed");return;
        }
        handoverDiagnostic=trace;pendingVisibilityEvidence=true;trace.stage("LAUNCH_REQUEST",SystemClock.elapsedRealtime());
        HomeGuardService.homeRequested();
        requestedAt=SystemClock.uptimeMillis();requestedHome=target;state.status("已请求接管，等待桌面显示");Log.i(TAG,"home requested: "+reason+" uptime="+requestedAt);
        confirmation=()->{finishDiagnostic(trace,"TIMEOUT","no_visibility_callback_5000ms");confirmation=null;requestedAt=0;requestedHome="";if(new HomeGuardState(c).automatic())new HomeGuardState(c).status("未确认进入桌面，请检查系统限制");};
        handler.postDelayed(confirmation,5000);
        try{if(!resumeOwnFireTask(c,state.selected()))startHome(c);trace.stage("DISPATCHED_UNVERIFIED",SystemClock.elapsedRealtime());}
        catch(ActivityNotFoundException|SecurityException failure){finishDiagnostic(trace,"FAILED",failure.getClass().getSimpleName());pendingVisibilityEvidence=false;handler.removeCallbacks(confirmation);confirmation=null;requestedAt=0;requestedHome="";state.status("系统拒绝启动桌面");}
    }
    private static void startHome(Context c){
        Intent intent=HomeController.launchIntent(c);
        if(Build.VERSION.SDK_INT<=23 && HomeGuardPolicy.xiaomiRecovery(Build.MANUFACTURER,Build.BRAND,Build.VERSION.SDK_INT,BuildConfig.LEGACY_19)){
            // Android 5/6 defers an ordinary startActivity for five seconds after HOME.
            // PendingIntent dispatch uses the system caller for the app-switch check.
            // Keep the destination explicit and the token single-use; eligibility is checked above.
            int flags=PendingIntent.FLAG_ONE_SHOT|PendingIntent.FLAG_UPDATE_CURRENT;
            if(Build.VERSION.SDK_INT>=23)flags|=PendingIntent.FLAG_IMMUTABLE;
            try{
                PendingIntent.getActivity(c,0,intent,flags).send();
                DiagnosticLog.info("home_guard.dispatch","route=pending_intent screenVerified=false");return;
            }catch(PendingIntent.CanceledException unavailable){DiagnosticLog.info("home_guard.dispatch","pending_intent_cancelled fallback=activity");}
        }
        c.startActivity(intent);
    }
    private static boolean resumeOwnFireTask(Context c,String selected){
        if(Build.VERSION.SDK_INT<23 || !HomeGuardPolicy.fireHome(Build.VERSION.SDK_INT,selected))return false;
        android.app.ActivityManager manager=(android.app.ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
        if(manager==null)return false;
        try{for(android.app.ActivityManager.AppTask task:manager.getAppTasks()){
            android.app.ActivityManager.RecentTaskInfo info=task.getTaskInfo();
            // Never bring settings, players or another task in our package to the front.
            if(info!=null && new ComponentName(c,HomeActivity.class).equals(info.baseActivity)
                    && new ComponentName(c,HomeActivity.class).equals(info.topActivity)){task.moveToFront();return true;}
        }}catch(RuntimeException unavailable){DiagnosticLog.info("home_guard.task","own_task_unavailable");}
        return false;
    }
    static void visible(Context c){
        restoredInStandby=false;
        restoredAt=-1;
        HomeGuardState state=new HomeGuardState(c);if(!state.automatic())return;
        state.preferences.edit().putBoolean("homeInFront",true).apply();
        HomeGuardService.homeVisible();
        // A slow first frame may arrive after the confirmation deadline; actual visibility wins.
        state.status("已进入 Quiet TV");
        if(requestedAt!=0){
            long elapsed=SystemClock.uptimeMillis()-requestedAt;boolean same=requestedHome.equals(state.selected()) || requestedHome.equals(new ComponentName(c,HomeActivity.class).flattenToString());
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
        if(!state.recoveryReady())throw new java.io.IOException(state.enableProblem());
        if(!writeAllowed(c))throw new java.io.IOException("写安全设置权限未授予");
        if(!systemWriteAllowed(c))throw new java.io.IOException("写系统设置权限未授予");
        HomeGuardAdb.watch(c);
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
        String problem=permissionProblem(c);if(!problem.isEmpty())return problem;
        if(!new HomeGuardState(c,false).serviceEnabled() && !HomeGuardUsageService.connected())return "无障碍权限未开启";
        return "";
    }
    // Recovery is posted to the main thread; grant completion must not race service restoration.
    private static String permissionProblem(Context c){
        if(!writeAllowed(c))return "写安全设置权限未授予";
        if(!systemWriteAllowed(c))return "写系统设置权限未授予";
        if(HomeGuardService.usageRequired(c) && !HomeGuardService.usageAllowed(c))return "桌面前台检测未授权";
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(c))return "后台启动权限未确认";
        if(notificationSupported(c) && !notificationAllowed(c) && !HomeGuardSync.ready(c))return "通知访问未授予";
        if(!notificationSupported(c) && HomeGuardSync.supported(c) && !HomeGuardSync.ready(c))return "同步恢复："+HomeGuardSync.summary(c);
        return "";
    }
    /** Called only from the foreground setup's already-authorized local ADB worker. */
    static String configuredSummary(Context c){
        return new HomeGuardState(c,false).observationReady()?"补偿权限已配置，守护已连接；开机和唤醒待验证":"补偿权限已配置，等待守护连接；开机和唤醒待验证";
    }
}
