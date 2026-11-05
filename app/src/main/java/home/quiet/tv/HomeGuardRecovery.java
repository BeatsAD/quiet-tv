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
    private static Runnable check,readd;
    private static boolean recovering;
    private static long bindingGeneration;
    private static String bindingServices;
    private static int bindingEnabled;
    private static HomeGuardRecoveryWindow bindingWindow;
    private static String bindingReason="";
    private static Runnable bootFallback,bootCompletion;
    private static boolean bootCompleted;
    static long bindingAttempt(){return recovering?bindingGeneration:0;}
    static boolean bindingBlocked(Context c){return new HomeGuardState(c,false).preferences.getBoolean("guardBindingChanged",false);}
    static void cancelBindingAttempt(long attempt){
        if(attempt==0 || attempt!=bindingAttempt())return;
        if(check!=null)handler.removeCallbacks(check);if(readd!=null)handler.removeCallbacks(readd);
        check=null;readd=null;recovering=false;
    }
    static void finishBindingAttempt(Context c,long attempt){
        if(attempt==0 || attempt!=bindingAttempt())return;
        // A connection callback can race the remove/re-add settle period. Do not cancel
        // the re-add while our entry is still absent from the secure service list.
        if(readd!=null){
            if(!writeAllowed(c) || !new HomeGuardState(c).recoveryReady() || !bindingUnchanged(c,attempt))return;
            try{restoreEntry(c);}
            catch(SecurityException|IllegalStateException unavailable){return;}
        }
        cancelBindingAttempt(attempt);
    }
    private static boolean accessibilityRestarted;
    private static boolean revivalNoted,restoredInStandby;
    private static long restoredAt=-1;
    private static final class Handover {
        final Context context;final String selected;
        String reason,target;long wall,requestedAt;int retries;
        boolean retryAllowed=true,visibilityExpected;
        Runnable confirmation,retry;DiagnosticOperation trace;
        Handover(Context c,String source,String destination,HomeGuardState state,DiagnosticOperation diagnostic){
            context=c.getApplicationContext();reason=source;target=destination;selected=state.selected();
            wall=System.currentTimeMillis();trace=diagnostic;
        }
    }
    private static Handover handover;
    private static String lastAccessibilityDecision="",lastUsageDecision="",lastAccessibilityObservation="",lastUsageObservation="";
    // Only exact stock-home matches, and only decision changes. Never log the foreground app stream.
    static void observed(String source,String decision,long age){observed(source,"selected_home",decision,age);}
    static void observed(String source,String target,String decision,long age){
        String previous="usage".equals(source)?lastUsageDecision:lastAccessibilityDecision;
        String signature=decision+"|"+target;if(previous.equals(signature))return;
        String id=DiagnosticOperation.nextId(System.currentTimeMillis());
        if("usage".equals(source)){lastUsageDecision=signature;lastUsageObservation=id;}
        else{lastAccessibilityDecision=signature;lastAccessibilityObservation=id;}
        DiagnosticLog.info("home_guard.observation","observation="+id+" source="+source+" target="+target+" decision="+decision+" ageMs="+age);
    }
    private static DiagnosticOperation diagnostic(String source,HomeGuardState state){return diagnostic(source,state,state.selected());}
    private static DiagnosticOperation diagnostic(String source,HomeGuardState state,String target){
        DiagnosticOperation trace=new DiagnosticOperation("home_guard.handover",System.currentTimeMillis(),SystemClock.elapsedRealtime(),DiagnosticLog::info);
        String observation="原主页事件".equals(source)?lastAccessibilityObservation:("用量统计原主页".equals(source) || "HOME 后原主页前台证据".equals(source))?lastUsageObservation:("已确认的厂商开机页面".equals(source) || "开机目标被原桌面覆盖".equals(source))?lastAccessibilityObservation:"";
        DiagnosticLog.info("home_guard.source","attempt="+trace.id+" source="+source+" observation="+observation+" target="+target);
        trace.stage("ELIGIBILITY",SystemClock.elapsedRealtime());return trace;
    }
    private static void finishDiagnostic(DiagnosticOperation trace,String result,String reason){
        if(trace!=null)trace.finish(result,reason,System.currentTimeMillis(),SystemClock.elapsedRealtime());
    }
    static String lastDiagnosticAttempt(){return handover==null?"":handover.trace.id;}
    static boolean handoverPending(){return handover!=null && (handover.confirmation!=null || handover.retry!=null);}
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
        if(bindingWindow!=null){bindingWindow.destroy();bindingWindow=null;}
        cancelBindingAttempt(bindingAttempt());
        cancelHandover();
    }
    static void cancelHandover(){
        if(Looper.myLooper()!=Looper.getMainLooper()){handler.post(HomeGuardRecovery::cancelHandover);return;}
        finishBootFallback();
        Handover current=handover;
        if(current!=null){
            finishDiagnostic(current.trace,"CANCELLED","pending_handover_cancelled");
            if(current.confirmation!=null)handler.removeCallbacks(current.confirmation);
            if(current.retry!=null)handler.removeCallbacks(current.retry);
            handover=null;
        }
        restoredInStandby=false;restoredAt=-1;
    }
    static void cancelRetry(){
        if(Looper.myLooper()!=Looper.getMainLooper()){handler.post(HomeGuardRecovery::cancelRetry);return;}
        if(handover==null)return;
        handover.retryAllowed=false;
        if(handover.retry!=null)handler.removeCallbacks(handover.retry);
        handover.retry=null;
    }
    static void request(Context c,String reason){ensureBinding(c,reason);}
    static void ensureBinding(Context supplied,String reason){
        Context c=supplied.getApplicationContext();if(Looper.myLooper()!=Looper.getMainLooper()){handler.post(()->ensureBinding(c,reason));return;}
        if(!new HomeGuardState(c).enabled()){bindingChanged();return;}
        bindingReason=reason;
        if(bindingWindow==null)bindingWindow=new HomeGuardRecoveryWindow(new HomeGuardRecoveryWindow.Host(){
            public long now(){return SystemClock.uptimeMillis();}
            public void postAt(Runnable task,long time){handler.postAtTime(task,time);}
            public void remove(Runnable task){handler.removeCallbacks(task);}
            public String state(){
                HomeGuardState current=new HomeGuardState(c,false);
                if("OFF".equals(current.preferences.getString("mode","AUTO")))return "disabled_or_paused";
                if(!HomeGuardState.unlocked(c))return "locked";
                if(bindingBlocked(c))return "secure_settings_changed";
                if(!current.enabled())return "disabled_or_paused";
                if(current.selected().isEmpty())current.configure();
                if(!current.recoveryReady())return "invalid_target";
                if(HomeGuardService.connected())return "bound";
                if(HomeGuardUsageService.connected())return "usage_bound";
                if(!writeAllowed(c) && !HomeGuardUsageService.eligible(c))return "permission_unavailable";
                return bindingAttempt()!=0?"recovering":"ready";
            }
            public void recover(){requestBinding(c,bindingReason);}
            public void note(String event,int attempt){
                new HomeGuardState(c,false).preferences.edit().putString("listenerRecovery",event).putInt("listenerRecoveryAttempt",attempt).apply();
                DiagnosticLog.info("home_guard.binding_recovery","event="+event+" attempt="+attempt+" physicalHomeVerified=false");
            }
            public void stopped(String reason){
                if("bound".equals(reason))finishBindingAttempt(c,bindingAttempt());
                else if(!"expired".equals(reason))cancelBindingAttempt(bindingAttempt());
            }
        });
        bindingWindow.request();
    }
    static void bindingConnected(Context c){
        finishBootFallback();
        finishBindingAttempt(c,bindingAttempt());
        if(bindingWindow!=null)bindingWindow.changed();
    }
    static void bindingChanged(){if(bindingWindow!=null)bindingWindow.changed();}
    static void bindingDisconnected(Context c){
        if(bindingWindow!=null)bindingWindow.restart();else ensureBinding(c,"守护断开");
    }
    static void bootCompleted(Context c,Runnable completed){
        if(bootCompleted || !new HomeGuardState(c).ready()){completed.run();return;}
        bootCompleted=true;
        if(HomeGuardService.connected()){HomeGuardService.bootCompleted();completed.run();return;}
        // The receiver's fallback has weaker activity-start privileges than the bound service.
        if(Build.VERSION.SDK_INT>=29 && !Settings.canDrawOverlays(c)){completed.run();return;}
        bootCompletion=completed;
        bootFallback=()->{
            try{
                if(!HomeGuardService.connected() && unknownAllowed(c)
                        && (!HomeGuardPolicy.xiaomi(Build.MANUFACTURER,Build.BRAND) || HomeGuardService.bootHomeObserved(c)))launch(c,"开机兜底");
            }finally{finishBootFallback();}
        };
        handler.postDelayed(bootFallback,HomeGuardPolicy.SETTLE_MS);
    }
    private static void finishBootFallback(){
        if(bootFallback!=null)handler.removeCallbacks(bootFallback);bootFallback=null;
        Runnable done=bootCompletion;bootCompletion=null;if(done!=null)done.run();
    }
    private static void requestBinding(Context c,String reason){
        HomeGuardState state=new HomeGuardState(c);
        if(!state.enabled() || !HomeGuardState.unlocked(c))return;
        if(bindingBlocked(c)){state.status("无障碍设置已变化，请通过电脑核对后恢复");return;}
        if(state.selected().isEmpty())state.configure();
        if(!state.recoveryReady()){state.status(state.enableProblem());return;}
        if(HomeGuardService.connected())return;
        if(HomeGuardUsageService.eligible(c)){HomeGuardUsageService.ensure(c);return;}
        if(recovering)return;
        if(!writeAllowed(c)){state.status("写安全设置权限未授予");return;}
        recovering=true;long generation=++bindingGeneration;
        try{restoreEntry(c);bindingServices=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);bindingEnabled=Settings.Secure.getInt(c.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0);}
        catch(SecurityException|IllegalStateException failure){recovering=false;state.status(HisenseGuardRefresh.supported()?"固件未接受无障碍配置，请连接 ADB 修复":"无法恢复无障碍配置");Log.w(TAG,"restore unavailable");return;}
        check=()->{
            check=null;HomeGuardState now=new HomeGuardState(c);
            if(!bindingUnchanged(c,generation))return;
            if(!now.recoveryReady() || !writeAllowed(c)){recovering=false;return;}
            if(HomeGuardService.connected()){recovering=false;return;}
            try{
                String current=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                String removed=HomeGuardPolicy.services(current,guard(c),false);
                if(!Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,removed))throw new IllegalStateException();
                bindingServices=removed;bindingEnabled=Settings.Secure.getInt(c.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0);
                readd=()->{
                    readd=null;
                    if(!bindingUnchanged(c,generation))return;
                    if(!new HomeGuardState(c).recoveryReady() || !writeAllowed(c)){cancelBindingAttempt(generation);return;}
                    try{restoreEntry(c);new HomeGuardState(c).status("已请求重新绑定，等待系统连接");}
                    catch(SecurityException|IllegalStateException failure){new HomeGuardState(c).status("无法恢复无障碍配置");}
                    finally{cancelBindingAttempt(generation);}
                };
                handler.postDelayed(readd,HomeGuardPolicy.SETTLE_MS);
            }catch(SecurityException|IllegalStateException failure){recovering=false;now.status("无法请求无障碍重新绑定");}
        };
        handler.postDelayed(check,5000);Log.i(TAG,"recovery requested: "+reason);
    }
    static void rebindIfSilent(Context c){
        HomeGuardState state=new HomeGuardState(c);String boot=new BootAppStore(c).bootId();
        if(recovering || bindingBlocked(c) || !state.ready() || !writeAllowed(c) || boot.isEmpty() || boot.equals(state.preferences.getString("silentRebindBoot","")))return;
        if(!state.preferences.edit().putString("silentRebindBoot",boot).commit())return;
        recovering=true;long generation=++bindingGeneration;
        try{
            String current=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String removed=HomeGuardPolicy.services(current,guard(c),false);
            if(!Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,removed))throw new IllegalStateException();
            bindingServices=removed;bindingEnabled=Settings.Secure.getInt(c.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0);
            readd=()->{
                readd=null;if(!bindingUnchanged(c,generation))return;
                if(!new HomeGuardState(c).recoveryReady() || !writeAllowed(c)){cancelBindingAttempt(generation);return;}
                try{restoreEntry(c);}catch(SecurityException|IllegalStateException failure){new HomeGuardState(c).status("无障碍重新绑定未完成");}
                finally{cancelBindingAttempt(generation);}
            };
            handler.postDelayed(readd,HomeGuardPolicy.SETTLE_MS);
            DiagnosticLog.info("home_guard.rebind","source=no_window_events scope=own_service");
        }catch(SecurityException|IllegalStateException failure){recovering=false;state.status("无障碍重新绑定未完成");}
    }
    @android.annotation.SuppressLint("ApplySharedPref") // Preserve an observed settings change across process recreation.
    private static boolean bindingUnchanged(Context c,long generation){
        if(generation!=bindingAttempt())return false;
        try{
            if(android.text.TextUtils.equals(bindingServices,Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))
                    && bindingEnabled==Settings.Secure.getInt(c.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0))return true;
        }catch(SecurityException|IllegalStateException unavailable){
            cancelBindingAttempt(generation);DiagnosticLog.error("home_guard.rebind.settings_unavailable",unavailable);return false;
        }
        cancelBindingAttempt(generation);
        new HomeGuardState(c,false).preferences.edit().putBoolean("guardBindingChanged",true).commit();
        DiagnosticLog.info("home_guard.rebind","decision=cancelled secure_settings_changed");return false;
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
    // Keep the historical single-service Xiaomi path without toggling other registered services.
    // Release our global write even after a pause, but only if no newer settings replaced it.
    @android.annotation.SuppressLint("ApplySharedPref")
    static boolean restartSilentAccessibility(Context c){
        if(accessibilityRestarted || !HomeGuardPolicy.xiaomiRecovery(Build.MANUFACTURER,Build.BRAND,Build.VERSION.SDK_INT,BuildConfig.LEGACY_19)
                || Build.VERSION.SDK_INT>23 || bindingBlocked(c) || !new HomeGuardState(c).ready() || !HomeGuardState.canShow(c) || !writeAllowed(c))return false;
        if(Settings.Secure.getInt(c.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0)!=1)return false;
        try{
            String services=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if(!HomeGuardPolicy.services(services,guard(c),false).isEmpty()){
                DiagnosticLog.info("home_guard.accessibility_restart","skipped=true reason=other_services_enabled");return false;
            }
            if(!Settings.Secure.putInt(c.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0))return false;
            accessibilityRestarted=true;
            handler.postDelayed(()->{
                try{
                    if(!android.text.TextUtils.equals(services,Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))){
                        new HomeGuardState(c,false).preferences.edit().putBoolean("guardBindingChanged",true).commit();
                        DiagnosticLog.info("home_guard.accessibility_restart","restored=false reason=secure_settings_changed");return;
                    }
                    if(Settings.Secure.getInt(c.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0)!=0)return;
                    if(!Settings.Secure.putInt(c.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,1))throw new IllegalStateException();
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
        if(uptime<HomeGuardPolicy.BOOT_WINDOW_MS)return HomeGuardState.unlocked(c) && state.bootStockMs()==0 && !new BootAppStore(c).userNavigated();
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
        if(handoverPending()){
            if(target.equals(new ComponentName(c,HomeActivity.class).flattenToString())){
                // The explicit action upgrades this attempt's retry evidence, without a second launch.
                handover.target=target;handover.reason=reason;handover.wall=System.currentTimeMillis();handover.retryAllowed=true;
            }
            finishDiagnostic(trace,"SKIPPED","request_pending");return;
        }
        if(HomeGuardService.homeResumed()){finishDiagnostic(trace,"SKIPPED","home_already_resumed");return;}
        dispatch(new Handover(c,reason,target,state,trace));
    }
    private static void dispatch(Handover attempt){
        Context c=attempt.context;handover=attempt;attempt.visibilityExpected=true;
        attempt.trace.stage("LAUNCH_REQUEST",SystemClock.elapsedRealtime());HomeGuardService.homeRequested();
        attempt.requestedAt=SystemClock.uptimeMillis();
        new HomeGuardState(c).status("已请求接管，等待桌面显示");Log.i(TAG,"home requested: "+attempt.reason+" uptime="+attempt.requestedAt);
        attempt.confirmation=()->{
            if(handover!=attempt)return;
            attempt.confirmation=null;attempt.requestedAt=0;
            finishDiagnostic(attempt.trace,"TIMEOUT","no_visibility_callback_5000ms");
            if(new HomeGuardState(c).automatic())new HomeGuardState(c).status("未确认进入桌面，请检查系统限制");
            retry(attempt);
        };
        handler.postDelayed(attempt.confirmation,5000);
        try{if(!resumeOwnFireTask(c,attempt.selected))startHome(c);attempt.trace.stage("DISPATCHED_UNVERIFIED",SystemClock.elapsedRealtime());}
        catch(ActivityNotFoundException|SecurityException failure){
            finishDiagnostic(attempt.trace,"FAILED",failure.getClass().getSimpleName());attempt.visibilityExpected=false;
            handler.removeCallbacks(attempt.confirmation);attempt.confirmation=null;attempt.requestedAt=0;
            new HomeGuardState(c).status("系统拒绝启动桌面");retry(attempt);
        }
    }
    private static void retry(Handover attempt){
        if(attempt.retries!=0 || !attempt.retryAllowed)return;
        attempt.retry=()->{
            attempt.retry=null;Context c=attempt.context;HomeGuardState state=new HomeGuardState(c);
            boolean explicit=attempt.target.equals(new ComponentName(c,HomeActivity.class).flattenToString());
            if(handover!=attempt || !attempt.retryAllowed || !attempt.selected.equals(state.selected())
                    || !(explicit?state.homeActionReady():state.ready()) || !HomeGuardService.retryAllowed(explicit,attempt.wall)){
                DiagnosticLog.info("home_guard.retry","decision=cancelled conditions_changed");return;
            }
            DiagnosticLog.info("home_guard.retry","decision=retry attempt=1");attempt.retries++;
            attempt.trace=diagnostic(attempt.reason+"有限补偿",state,attempt.target);dispatch(attempt);
        };
        handler.postDelayed(attempt.retry,500);
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
        cancelRetry();
        restoredInStandby=false;
        restoredAt=-1;
        HomeGuardState state=new HomeGuardState(c);if(!state.automatic())return;
        state.preferences.edit().putBoolean("homeInFront",true).apply();
        HomeGuardService.homeVisible();
        // A slow first frame may arrive after the confirmation deadline; actual visibility wins.
        state.status("已进入 Quiet TV");
        Handover attempt=handover;
        if(attempt!=null && attempt.confirmation!=null){
            long elapsed=SystemClock.uptimeMillis()-attempt.requestedAt;
            DiagnosticLog.info("home_guard.visibility","attempt="+lastDiagnosticAttempt()+" requestToCallbackMs="+elapsed+" source=home_callback screenVerified=false");
            boolean same=attempt.target.equals(state.selected()) || attempt.target.equals(new ComponentName(c,HomeActivity.class).flattenToString());
            finishDiagnostic(attempt.trace,same?"VISIBILITY_CALLBACK":"CANCELLED",same?"home_callback_not_screen_proof":"target_changed");
            attempt.visibilityExpected=false;
            if(attempt.confirmation!=null)handler.removeCallbacks(attempt.confirmation);attempt.confirmation=null;attempt.requestedAt=0;
            if(same){state.status("已进入 Quiet TV");Log.i(TAG,"home visible after "+elapsed+"ms");}
        }else if(attempt!=null && attempt.visibilityExpected){attempt.visibilityExpected=false;DiagnosticLog.info("home_guard.visibility","attempt="+attempt.trace.id+" result=callback_without_pending_request causalityVerified=false screenVerified=false");}
    }

    static void leftHome(Context c){
        if(HomeGuardState.canShow(c))new HomeGuardState(c).preferences.edit().putBoolean("homeInFront",false).apply();
    }
    static void configureGranted(Context c) throws java.io.IOException {
        HomeGuardState state=new HomeGuardState(c);
        if(!state.recoveryReady())throw new java.io.IOException(state.enableProblem());
        if(!writeAllowed(c))throw new java.io.IOException("写安全设置权限未授予");
        state.preferences.edit().remove("guardBindingChanged").apply();
        HomeGuardAdb.watch(c);
        if(Build.VERSION.SDK_INT<27 && notificationSupported(c) && !notificationAllowed(c)){
            String current=Settings.Secure.getString(c.getContentResolver(),"enabled_notification_listeners");
            String desired=HomeGuardPolicy.services(current,listener(c),true);
            try{
                if(Settings.Secure.putString(c.getContentResolver(),"enabled_notification_listeners",desired))state.preferences.edit().putBoolean("grantedListener",true).apply();
            }catch(SecurityException|IllegalStateException unavailable){DiagnosticLog.info("home_guard.notification","configuration_unavailable");}
        }
        HomeGuardSync.ensure(c);
        ensureBinding(c,"授权配置完成");
    }
    static String configurationProblem(Context c){
        String problem=permissionProblem(c);if(!problem.isEmpty())return problem;
        if(!new HomeGuardState(c,false).serviceEnabled() && !HomeGuardUsageService.connected())return "无障碍权限未开启";
        return "";
    }
    // Recovery is posted to the main thread; grant completion must not race service restoration.
    private static String permissionProblem(Context c){
        if(!writeAllowed(c))return "写安全设置权限未授予";
        if(HomeGuardService.usageRequired(c) && !HomeGuardService.usageAllowed(c))return "桌面前台检测未授权";
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(c))return "后台启动权限未确认";
        if(notificationSupported(c) && !notificationAllowed(c) && !HomeGuardSync.ready(c))return "通知访问未授予";
        if(!notificationSupported(c) && HomeGuardSync.supported(c) && !HomeGuardSync.ready(c))return "同步恢复："+HomeGuardSync.summary(c);
        return "";
    }
    static org.json.JSONObject permissions(Context c) throws org.json.JSONException {
        return new org.json.JSONObject().put("recoveryWriteAllowed",writeAllowed(c))
                .put("bindingRecoveryPending",bindingAttempt()!=0).put("homeRequestPending",handoverPending())
                .put("recoverySettingsChanged",bindingBlocked(c)).put("keyFilterReason",HomeGuardService.keyFilterReason())
                .put("usageRequired",HomeGuardService.usageRequired(c)).put("usageAllowed",HomeGuardService.usageAllowed(c))
                .put("backgroundLaunchAllowed",Build.VERSION.SDK_INT<23 || Settings.canDrawOverlays(c))
                .put("notificationSupported",notificationSupported(c)).put("notificationAllowed",notificationAllowed(c))
                .put("syncReady",HomeGuardSync.ready(c)).put("problem",permissionProblem(c));
    }
    /** Called only from the foreground setup's already-authorized local ADB worker. */
    static String configuredSummary(Context c){
        return new HomeGuardState(c,false).observationReady()?"补偿权限已配置，守护已连接；开机和唤醒待验证":"补偿权限已配置，等待守护连接；开机和唤醒待验证";
    }
}
