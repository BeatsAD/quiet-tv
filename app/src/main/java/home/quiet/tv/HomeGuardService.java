package home.quiet.tv;

import android.accessibilityservice.AccessibilityService;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.view.accessibility.AccessibilityEvent;

/** Immediate exact-home handover, with a separate finite boot/wake recovery path. */
public final class HomeGuardService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static volatile HomeGuardService instance;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final HomeGuardPolicy policy=new HomeGuardPolicy();
    private HomeGuardState state;
    private boolean registered,allowStock,sawWindow,bootChecksScheduled;
    private long transitionUntil;
    private String lastPackage="",settleReason="";
    private final BroadcastReceiver packageChanges=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            ComponentName selected=state==null?null:ComponentName.unflattenFromString(state.selected());
            if(selected!=null && intent.getData()!=null && selected.getPackageName().equals(intent.getData().getSchemeSpecificPart()))state.invalidateEventHome();
        }
    };
    private final Runnable usageCheck=new Runnable(){
        // Shared with API19; the usage service exists on API21, before its public constant in API22.
        @android.annotation.SuppressLint({"ObsoleteSdkInt","InlinedApi"})
        @Override public void run(){
            if(Build.VERSION.SDK_INT<21 || !watchUsage())return;
            handler.postDelayed(this,HomeGuardPolicy.USAGE_POLL_MS);
            if(!HomeGuardState.canShow(HomeGuardService.this) || !usable())return;
            long now=System.currentTimeMillis();UsageEvents.Event latest=latestUsage();
            // Read the latest activity across all apps, never the latest stock-home event alone.
            if(latest==null)return;
            String pkg=latest.getPackageName(),cls=latest.getClassName();
            if(pkg==null || cls==null)return;
            ComponentName original=ComponentName.unflattenFromString(state.selected());
            if(allowStock && original!=null && !pkg.equals(original.getPackageName()) && !pkg.equals(getPackageName()))allowStock=false;
            if(allowStock){if(HomeGuardPolicy.matches(state.eventHome(),pkg,cls))HomeGuardRecovery.observed("usage","allow_original",now-latest.getTimeStamp());return;}
            String previous=lastPackage;lastPackage=pkg;
            if(!HomeGuardPolicy.matches(state.eventHome(),pkg,cls))return;
            if("com.tianci.tv".equals(previous))transitionUntil=SystemClock.uptimeMillis()+HomeGuardPolicy.SETTLE_MS;
            if(SystemClock.uptimeMillis()<transitionUntil){HomeGuardRecovery.observed("usage","settling",now-latest.getTimeStamp());return;}
            ResolveInfo home=getPackageManager().resolveActivity(HomeController.homeIntent(),PackageManager.MATCH_DEFAULT_ONLY);
            if(home!=null && home.activityInfo!=null && getPackageName().equals(home.activityInfo.packageName)){HomeGuardRecovery.observed("usage","own_default_home",now-latest.getTimeStamp());return;}
            if(policy.consumeUsage(state.eventHome(),pkg,cls,latest.getTimeStamp(),now)){
                HomeGuardRecovery.observed("usage","handover",now-latest.getTimeStamp());
                DiagnosticLog.info("home_guard.usage_takeover","原主页");HomeGuardRecovery.launch(HomeGuardService.this,"用量统计原主页");
            }else HomeGuardRecovery.observed("usage","stale_or_consumed",now-latest.getTimeStamp());
        }
    };
    @android.annotation.SuppressLint({"ObsoleteSdkInt","InlinedApi"})
    private UsageEvents.Event latestUsage(){
        if(Build.VERSION.SDK_INT<21 || !usageAllowed(this))return null;
        long now=System.currentTimeMillis();UsageEvents.Event latest=null;
        try{
            UsageStatsManager manager=(UsageStatsManager)getSystemService(Context.USAGE_STATS_SERVICE);
            UsageEvents events=manager==null?null:manager.queryEvents(now-HomeGuardPolicy.USAGE_LOOKBACK_MS,now);
            if(events==null)return null;
            UsageEvents.Event event=new UsageEvents.Event();
            while(events.hasNextEvent()){
                events.getNextEvent(event);
                if(event.getEventType()==UsageEvents.Event.MOVE_TO_FOREGROUND
                        && (latest==null || event.getTimeStamp()>=latest.getTimeStamp())){latest=event;event=new UsageEvents.Event();}
            }
        }catch(SecurityException|IllegalStateException failure){return null;}
        return latest;
    }
    private boolean xiaomiRecovery(){return HomeGuardPolicy.xiaomiRecovery(Build.MANUFACTURER,Build.BRAND,Build.VERSION.SDK_INT,BuildConfig.LEGACY_19);}
    private final Runnable bootRecheck=this::recheckBoot;
    private final Runnable deafCheck=()->{
        if(!sawWindow && usable() && HomeGuardState.canShow(this) && !allowStock)
            HomeGuardRecovery.restartSilentAccessibility(this);
    };
    private void scheduleBootChecks(){
        if(bootChecksScheduled || !xiaomiRecovery() || state==null || !state.enabled() || SystemClock.elapsedRealtime()>=HomeGuardPolicy.BOOT_WINDOW_MS)return;
        bootChecksScheduled=true;
        handler.postDelayed(bootRecheck,HomeGuardPolicy.BOOT_RECHECK_FIRST_MS);
        handler.postDelayed(bootRecheck,HomeGuardPolicy.BOOT_RECHECK_SECOND_MS);
        DiagnosticLog.info("home_guard.boot_rechecks","scheduled=true firstMs=30000 secondMs=45000");
    }
    private void cancelRecoveryChecks(){handler.removeCallbacks(bootRecheck);handler.removeCallbacks(deafCheck);bootChecksScheduled=false;}
    private void recheckBoot(){
        if(!xiaomiRecovery() || SystemClock.elapsedRealtime()>=HomeGuardPolicy.BOOT_WINDOW_MS || !usable() || !HomeGuardState.canShow(this) || allowStock)return;
        // Prefer fresh usage evidence on old Xiaomi; do not replay an old home behind a player.
        UsageEvents.Event latest=latestUsage();
        boolean accepted=latest!=null?latest.getTimeStamp()<=System.currentTimeMillis() && HomeGuardPolicy.matches(state.eventHome(),latest.getPackageName(),latest.getClassName())
                :policy.bootRecheck(state.eventHome(),HomeGuardRecovery.unknownAllowed(this));
        DiagnosticLog.info("home_guard.boot_recheck","decision="+(accepted?"handover":"other_or_unknown_front")+" usage="+(latest!=null));
        if(accepted)HomeGuardRecovery.launch(this,"开机延后复查");
    }
    private void scheduleDeafCheck(){
        handler.removeCallbacks(deafCheck);
        if(xiaomiRecovery() && Build.VERSION.SDK_INT<=23 && state!=null && state.enabled() && !sawWindow)handler.postDelayed(deafCheck,HomeGuardPolicy.DEAF_CHECK_MS);
    }
    static boolean usageRequired(){return HomeGuardPolicy.usageFallback(Build.VERSION.SDK_INT,BuildConfig.LEGACY_19);}
    static boolean usageAllowed(Context c){
        if(!usageRequired())return false;
        AppOpsManager manager=(AppOpsManager)c.getSystemService(Context.APP_OPS_SERVICE);
        try{return manager!=null && manager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),c.getPackageName())==AppOpsManager.MODE_ALLOWED;}
        catch(SecurityException failure){return false;}
    }
    private boolean watchUsage(){return state!=null && state.enabled() && HomeGuardState.canShow(this) && usageRequired() && usageAllowed(this);}
    private void refreshUsageWatch(){handler.removeCallbacks(usageCheck);if(watchUsage())handler.post(usageCheck);}
    static void homeRequested(){HomeGuardService service=instance;if(service!=null)service.policy.ignoreUsageThrough(System.currentTimeMillis());}
    private final Runnable homeKeyHandover=()->{
        if(!usable() || !HomeGuardState.canShow(this) || allowStock)return;
        ComponentName original=ComponentName.unflattenFromString(state.selected());
        if(original!=null && original.getPackageName().equals(lastPackage))HomeGuardRecovery.launch(this,"HOME 动作");
    };
    private final Runnable handover=()->{
        if(!usable() || !HomeGuardState.canShow(this) || allowStock){DiagnosticLog.info("home_guard.settle","source="+settleReason+" decision=conditions_unavailable allowOriginal="+allowStock);return;}
        UsageEvents.Event latest=latestUsage();
        boolean other=latest!=null && (latest.getTimeStamp()>System.currentTimeMillis() || !HomeGuardPolicy.matches(state.eventHome(),latest.getPackageName(),latest.getClassName()));
        boolean accepted=!other && policy.settle(state.eventHome(),HomeGuardRecovery.unknownAllowed(this));
        DiagnosticLog.info("home_guard.settle","source="+settleReason+" decision="+(accepted?"handover":"policy_skip")+" windowUnknown="+policy.unknown());
        if(accepted)HomeGuardRecovery.launch(this,settleReason);
    };
    private final BroadcastReceiver lifecycle=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            String action=intent.getAction();
            if(!Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action))DiagnosticLog.info("home_guard.lifecycle","action="+action+" elapsedRealtimeMs="+SystemClock.elapsedRealtime());
            if(Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)){
                if(!"homekey".equals(intent.getStringExtra("reason")))return;
                boolean available=usable() && HomeGuardState.canShow(HomeGuardService.this);
                DiagnosticLog.info("home_guard.home_action","received=true conditionsAvailable="+available+" physicalKeyVerified=false");
                if(!available)return;
                ComponentName original=ComponentName.unflattenFromString(state.selected());
                ResolveInfo resolved=getPackageManager().resolveActivity(HomeController.homeIntent(),PackageManager.MATCH_DEFAULT_ONLY);
                boolean own=resolved!=null && resolved.activityInfo!=null && getPackageName().equals(resolved.activityInfo.packageName);
                DiagnosticLog.info("home_guard.home_action","decision="+(own?"own_default_home":original!=null && original.getPackageName().equals(lastPackage)?"defer_handover":"original_not_observed"));
                if(!own && original!=null && original.getPackageName().equals(lastPackage)){
                    allowStock=false;cancel();policy.clear();transitionUntil=0;
                    // Let Android dispatch HOME first; otherwise its subsequent stock launch can cover ours.
                    handler.postDelayed(homeKeyHandover,150);
                }
                return;
            }
            if(Intent.ACTION_SCREEN_OFF.equals(action)){handler.removeCallbacks(usageCheck);cancel();cancelRecoveryChecks();HomeGuardRecovery.cancel();policy.clear();lastPackage="";return;}
            if(Intent.ACTION_USER_UNLOCKED.equals(action)){state=new HomeGuardState(HomeGuardService.this);BootAppLifecycle.observe(HomeGuardService.this);}
            HomeGuardRecovery.request(context,"屏幕或解锁");schedule("亮屏或解锁");refreshUsageWatch();scheduleBootChecks();scheduleDeafCheck();
        }
    };
    @Override protected void onServiceConnected(){
        super.onServiceConnected();DiagnosticLog.info("home_guard.service","connected");instance=this;state=new HomeGuardState(this);
        BootAppLifecycle.observe(this);
        HomeGuardAdb.watch(this);
        state.preferences.registerOnSharedPreferenceChangeListener(this);
        if(!registered){IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            if(Build.VERSION.SDK_INT>=24)filter.addAction(Intent.ACTION_USER_UNLOCKED);androidx.core.content.ContextCompat.registerReceiver(this,lifecycle,filter,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
            IntentFilter packages=new IntentFilter(Intent.ACTION_PACKAGE_CHANGED);packages.addAction(Intent.ACTION_PACKAGE_REPLACED);packages.addAction(Intent.ACTION_PACKAGE_REMOVED);packages.addAction(Intent.ACTION_PACKAGE_ADDED);packages.addDataScheme("package");
            androidx.core.content.ContextCompat.registerReceiver(this,packageChanges,packages,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);registered=true;}
        refreshUsageWatch();
        scheduleBootChecks();scheduleDeafCheck();
        if(state.enabled())state.status("守护已连接，等待原主页");
        if(SystemClock.elapsedRealtime()<HomeGuardPolicy.BOOT_WINDOW_MS || HomeGuardRecovery.unknownAllowed(this))schedule("服务连接");
    }
    static boolean connected(){return instance!=null;}
    static void cancelPending(){HomeGuardService service=instance;if(service!=null){Runnable cancel=()->{service.cancel();service.cancelRecoveryChecks();};if(Looper.myLooper()==Looper.getMainLooper())cancel.run();else service.handler.post(cancel);}}
    static void lifecycle(String reason){HomeGuardService service=instance;if(service!=null){service.schedule(reason);service.refreshUsageWatch();service.scheduleBootChecks();service.scheduleDeafCheck();}}
    static void allowOriginal(){HomeGuardService service=instance;if(service!=null){service.allowStock=true;service.cancel();}}
    static void homeVisible(){HomeGuardService service=instance;if(service!=null){service.allowStock=false;service.policy.confirmed();service.cancel();}}
    private void cancel(){handler.removeCallbacks(handover);handler.removeCallbacks(homeKeyHandover);}
    private void schedule(String reason){
        cancel();if(state==null || !state.enabled())return;
        settleReason=reason;transitionUntil=SystemClock.uptimeMillis()+HomeGuardPolicy.SETTLE_MS;handler.postDelayed(handover,HomeGuardPolicy.SETTLE_MS);
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences preferences,String key){
        if("mode".equals(key) || "home".equals(key) || "firmware".equals(key) || "configured".equals(key) || "observeHome".equals(key)){
            if("home".equals(key) || "firmware".equals(key))state.invalidateEventHome();
            cancel();cancelRecoveryChecks();policy.clear();lastPackage="";allowStock=false;
            if(state.enabled()){schedule("配置恢复");scheduleBootChecks();scheduleDeafCheck();}refreshUsageWatch();
        }
    }
    private boolean usable(){
        if(state==null || !state.enabled())return false;
        if(!HomeGuardState.unlocked(this))return false;
        if(!state.validSelection()){state.status("原主页不可用，请重新配置");return false;}
        if(state.helperActive()){state.status("独立中转助手正在接管");return false;}return true;
    }
    private boolean activity(String pkg,String cls){
        try{getPackageManager().getActivityInfo(new ComponentName(pkg,cls.startsWith(".")?pkg+cls:cls),0);return true;}
        catch(PackageManager.NameNotFoundException|SecurityException ignored){return false;}
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null || event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || state==null)return;
        sawWindow=true;handler.removeCallbacks(deafCheck);
        if(!state.enabled() || !HomeGuardState.canShow(this)){cancel();return;}
        String pkg=event.getPackageName()==null?null:event.getPackageName().toString(),cls=event.getClassName()==null?null:event.getClassName().toString();
        if(pkg==null || cls==null || !activity(pkg,cls))return;
        long now=SystemClock.uptimeMillis(),age=now-event.getEventTime();
        if(age<0 || age>HomeGuardPolicy.MAX_AGE_MS){if(HomeGuardPolicy.matches(state.eventHome(),pkg,cls))HomeGuardRecovery.observed("accessibility","stale_event",age);return;}
        // A delayed usage event must not override a newer settings/player accessibility event.
        policy.ignoreUsageThrough(System.currentTimeMillis()-age);
        BootAppLifecycle.foreground(this,(pkg.equals(getPackageName()) && cls.equals(HomeActivity.class.getName()))
            || (state.validSelection() && HomeGuardPolicy.matches(state.eventHome(),pkg,cls)));
        String previous=lastPackage;lastPackage=pkg;
        boolean target=policy.observe(state.eventHome(),pkg,cls,now);
        ComponentName original=ComponentName.unflattenFromString(state.selected());
        if(allowStock && original!=null && !pkg.equals(original.getPackageName()) && !pkg.equals(getPackageName()))allowStock=false;
        if(!HomeGuardPolicy.matches(state.eventHome(),pkg,cls)){
            cancel();if(!pkg.equals(getPackageName()))state.preferences.edit().putBoolean("homeInFront",false).apply();return;
        }
        if(!usable() || allowStock || !target){HomeGuardRecovery.observed("accessibility",allowStock?"allow_original":!target?"duplicate_window":"configuration_unavailable",age);return;}
        // Skyworth's source service may briefly show Home before the actual input player.
        if("com.tianci.tv".equals(previous) || "com.tianci.tv".equals(pkg) || now<transitionUntil){HomeGuardRecovery.observed("accessibility","settling",age);schedule("主页稳定期");return;}
        if(policy.consume(state.eventHome(),now,true,true,true)){HomeGuardRecovery.observed("accessibility","handover",age);HomeGuardRecovery.launch(this,"原主页事件");}
        else HomeGuardRecovery.observed("accessibility","stale_or_consumed",age);
    }
    @Override public void onInterrupt(){DiagnosticLog.info("home_guard.service","interrupted");cancel();cancelRecoveryChecks();}
    @Override public boolean onUnbind(Intent intent){
        DiagnosticLog.info("home_guard.service","unbound");
        cancel();cancelRecoveryChecks();handler.removeCallbacks(usageCheck);if(instance==this)instance=null;
        if(state!=null && state.enabled())state.status("无障碍已断开，等待系统恢复");
        return super.onUnbind(intent);
    }
    @Override public void onDestroy(){
        cancel();cancelRecoveryChecks();handler.removeCallbacks(usageCheck);if(instance==this)instance=null;
        if(state!=null)state.preferences.unregisterOnSharedPreferenceChangeListener(this);
        if(registered){unregisterReceiver(lifecycle);unregisterReceiver(packageChanges);}super.onDestroy();
    }
}
