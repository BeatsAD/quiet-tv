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
    // Already runs on the ADB worker; keep completed grants durable before continuing/canceling.
    @android.annotation.SuppressLint("ApplySharedPref")
    static void configureAdb(Context c,AdbController adb,SetupOperations.Ticket task) throws Exception {
        String step="守护条件核对";
        try{
        HomeGuardState state=new HomeGuardState(c);state.configure();if(!state.recoveryReady())throw new java.io.IOException(state.enableProblem());
        step="当前用户核对";
        task.check();adb.validateUser();String pkg=c.getPackageName(),user=Integer.toString(adb.user);
        step="开机接收组件核对";
        repairBootReceiver(c,adb,task);
        step="写安全设置命令检测";
        String pmUser=writeAllowed(c)?"":ShellCapabilities.userOption(ShellCapabilities.permissionHelp("pm",adb::shell),"grant",Build.VERSION.SDK_INT,adb.user);
        step="写安全设置授权";
        if(!writeAllowed(c)){adb.shell("pm grant"+pmUser+" "+pkg+" android.permission.WRITE_SECURE_SETTINGS");if(!writeAllowed(c))throw new java.io.IOException("写安全设置权限未授予");
        state.preferences.edit().putBoolean("grantedWrite",true).commit();}
        step="应用操作授权命令检测";
        task.check();adb.validateUser();
        String opsUser=systemWriteAllowed(c) && (!HomeGuardService.usageRequired(c) || HomeGuardService.usageAllowed(c)) && (Build.VERSION.SDK_INT<23 || Settings.canDrawOverlays(c))?""
                :ShellCapabilities.userOption(ShellCapabilities.permissionHelp("appops",adb::shell),"set",Build.VERSION.SDK_INT,adb.user);
        step="通知恢复授权";
        task.check();adb.validateUser();if(!new HomeGuardState(c).recoveryReady())throw new java.io.IOException("守护已暂停或配置已改变");
        // These grants are independent: unsupported notification commands do not undo accessibility.
        String note="";
        if(!notificationSupported(c) && !HomeGuardSync.supported(c))note="此设备不支持通知恢复";
        else if(Build.VERSION.SDK_INT>=27 && !notificationAllowed(c)){
            String help="";try{help=adb.shell("cmd notification help 2>&1 || true");}catch(java.io.IOException unsupported){note="此系统需手动授权通知访问";}
            if(help.contains("allow_listener") && help.contains("user_id")){
                task.check();adb.validateUser();
                try{adb.shell("cmd notification allow_listener "+listener(c)+" "+user);}
                catch(AdbController.CommandFailure refused){note="通知访问未授予";}
                if(notificationAllowed(c))state.preferences.edit().putBoolean("grantedListener",true).commit();else note="通知访问未授予";
            }else note="此系统需手动授权通知访问";
        }
        task.check();adb.validateUser();if(!new HomeGuardState(c).recoveryReady())throw new java.io.IOException("守护已暂停或配置已改变");
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(c)){
            step="后台启动授权";
            adb.shell("appops set"+opsUser+" "+pkg+" SYSTEM_ALERT_WINDOW allow");
            if(Settings.canDrawOverlays(c))state.preferences.edit().putBoolean("grantedOverlay",true).commit();else note="后台启动权限未确认";
        }
        if(HomeGuardService.usageRequired(c) && !HomeGuardService.usageAllowed(c)){
            step="桌面前台检测授权";
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
            if(!HomeGuardService.usageAllowed(c))note="桌面前台检测未授权";
        }
        task.check();if(!new HomeGuardState(c).recoveryReady())throw new java.io.IOException("守护已暂停或配置已改变");
        if(!systemWriteAllowed(c) && Build.VERSION.SDK_INT>=23){
            step="写系统设置授权";
            task.check();adb.validateUser();adb.shell("appops set"+opsUser+" "+pkg+" WRITE_SETTINGS allow");
            if(!systemWriteAllowed(c))note="写系统设置权限未授予";
        }
        step="厂商启动配置";configureVendorAdb(c,adb,task);
        if(HomeGuardAppOps.tcl(Build.MANUFACTURER,Build.BRAND))TclHomePolicy.configure(c,adb,task);
        step="海信无障碍配置读回与刷新";HisenseGuardRefresh.configure(c,adb,task);
        step="守护恢复配置";configureGranted(c);
        step="补偿配置读回";
        if(HomeGuardSync.ready(c) && (note.equals("此设备不支持通知恢复") || note.equals("此系统需手动授权通知访问") || note.equals("通知访问未授予")))note="";
        if(note.isEmpty())note=permissionProblem(c);
        if(!note.isEmpty())throw new java.io.IOException(note);
        DiagnosticLog.info("home_guard.configure","result=permissions_ready serviceBound="+HomeGuardService.connected()+" observationReady="+new HomeGuardState(c,false).observationReady()+" syncReady="+HomeGuardSync.ready(c)+" scenesVerified=false");
        }catch(Exception failure){
            String detail=DiagnosticJournal.clean(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());
            if(detail.length()>240)detail=detail.substring(0,240);
            DiagnosticLog.info("home_guard.configure","step="+step+" result=failed exit="+(failure instanceof AdbController.CommandFailure?((AdbController.CommandFailure)failure).exit:"unknown")+" error="+detail);
            throw new java.io.IOException(step+"未完成："+detail+"；已完成的授权保留",failure);
        }
    }
    static String configuredSummary(Context c){
        return new HomeGuardState(c,false).observationReady()?"补偿权限已配置，守护已连接；开机和唤醒待验证":"补偿权限已配置，等待守护连接；开机和唤醒待验证";
    }
    private static void repairBootReceiver(Context c,AdbController adb,SetupOperations.Ticket task) throws Exception {
        if(BuildConfig.LEGACY_19 || Build.VERSION.SDK_INT<21)return;
        ComponentName boot=new ComponentName(c,HomeGuardBootReceiver.class);PackageManager pm=c.getPackageManager();
        int before=pm.getComponentEnabledSetting(boot);
        if(before!=PackageManager.COMPONENT_ENABLED_STATE_DISABLED && before!=PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                && before!=PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED)return;
        task.check();adb.validateUser();
        if(adb.capabilities.enableCommand.isEmpty())throw new java.io.IOException("系统关闭了开机接收组件，缺少恢复命令");
        SetupStore store=new SetupStore(c);if(!store.get().has("bootReceiverBefore"))store.put("bootReceiverBefore",before);
        adb.shell(adb.capabilities.enableCommand+" --user "+adb.user+" "+boot.flattenToString());
        if(pm.getComponentEnabledSetting(boot)!=PackageManager.COMPONENT_ENABLED_STATE_ENABLED)throw new java.io.IOException("开机接收组件恢复未读回");
        DiagnosticLog.info("home_guard.boot_component","enabled_readback physical_boot_unverified");
    }
    private static void configureVendorAdb(Context c,AdbController adb,SetupOperations.Ticket task) throws Exception {
        if(!HomeGuardAppOps.tcl(Build.MANUFACTURER,Build.BRAND))return;
        String help=adb.shell("appops help"),pkg=c.getPackageName();
        String getUser=ShellCapabilities.userOption(help,"get",Build.VERSION.SDK_INT,adb.user);
        String setUser=ShellCapabilities.userOption(help,"set",Build.VERSION.SDK_INT,adb.user);
        HomeGuardState state=new HomeGuardState(c);String selected=state.selected();int supported=0;
        state.preferences.edit().putString("vendorAutoStartStatus","正在核对，结果尚未确认").apply();
        for(String operation:HomeGuardAppOps.AUTO_START){
            task.check();adb.validateUser();
            if(!state.ready() || !selected.equals(state.selected()))throw new java.io.IOException("守护已暂停或目标已改变");
            String before;
            try{before=adb.shell("appops get"+getUser+" "+pkg+" "+operation);}
            catch(java.io.IOException unavailable){if(HomeGuardAppOps.unknown(unavailable.getMessage()))continue;throw unavailable;}
            if(HomeGuardAppOps.unknown(before))continue;
            supported++;
            DiagnosticLog.info("home_guard.vendor_op.before",operation+" "+before);
            if(!HomeGuardAppOps.allowed(before,operation)){
                task.check();adb.validateUser();
                if(!state.ready() || !selected.equals(state.selected()))throw new java.io.IOException("守护已暂停或目标已改变");
                adb.shell("appops set"+setUser+" "+pkg+" "+operation+" allow");
            }
            String after=adb.shell("appops get"+getUser+" "+pkg+" "+operation);
            DiagnosticLog.info("home_guard.vendor_op.after",operation+" "+after);
            if(!HomeGuardAppOps.allowed(after,operation)){
                state.preferences.edit().putString("vendorAutoStartStatus",operation+" 未读回 allow").apply();
                throw new java.io.IOException("厂商自启动权限未读回："+operation+"；已完成的授权保留");
            }
        }
        if(supported>0 && java.util.regex.Pattern.compile("(?m)^\\s*write-settings(?:\\s|$)").matcher(help).find()){
            task.check();adb.validateUser();
            if(!state.ready() || !selected.equals(state.selected()))throw new java.io.IOException("守护已暂停或目标已改变");
            adb.shell("appops write-settings");
        }
        state.preferences.edit().putString("vendorAutoStartStatus",supported==0?"固件未提供已知命名 AppOp，重启保持待验证":"已读回 "+supported+" 项 allow，重启保持待验证").apply();
    }
}
