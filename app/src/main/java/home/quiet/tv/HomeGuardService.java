package home.quiet.tv;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;

/** Immediate exact-home handover, with a separate finite boot/wake recovery path. */
public final class HomeGuardService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static volatile HomeGuardService instance;
    private static volatile boolean homeResumed;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final HomeGuardPolicy policy=new HomeGuardPolicy();
    private final HomeKeyPolicy homeKeys=new HomeKeyPolicy();
    private HomeGuardState state;
    private BootSourcePolicy bootSource;
    private boolean registered,allowStock,sawActivityEvent,bootRechecksScheduled,keyFiltering;
    private String lastActivity="";
    private long lastActivityAt;
    private long transitionUntil;
    private int filteredHomeCode=KeyEvent.KEYCODE_HOME;
    private String lastPackage="",settleReason="",homeObservation="",broadcastObservation="";
    private boolean defaultHome(){
        ResolveInfo resolved=getPackageManager().resolveActivity(HomeController.homeIntent(),PackageManager.MATCH_DEFAULT_ONLY);
        return resolved!=null && resolved.activityInfo!=null && getPackageName().equals(resolved.activityInfo.packageName)
                && HomeActivity.class.getName().equals(resolved.activityInfo.name);
    }
    private boolean filterHome(){
        if(!HomeGuardPolicy.xiaomi(Build.MANUFACTURER,Build.BRAND) || !homeActionUsable() || !HomeGuardState.canShow(this))return false;
        return HomeGuardPolicy.filterHome(true,true,true,Build.VERSION.SDK_INT,!homeResumed && defaultHome(),homeResumed,state.directHomeReady());
    }
    private void updateKeyFilter(){
        if(Build.VERSION.SDK_INT<21)return;
        AccessibilityServiceInfo info=getServiceInfo();if(info==null)return;
        int flag=AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        boolean observe=observeBootSource();
        RemoteKeyMap remote=new RemoteKeyMap(this);
        boolean wanted=filterHome() || homeKeys.pressed() || observe || remote.observingFor()>0
            || state!=null && state.ready() && (bootSourceDevice() || remote.customHome());int flags=wanted?info.flags|flag:info.flags&~flag;
        int events=observe?info.eventTypes|AccessibilityEvent.TYPE_VIEW_CLICKED:info.eventTypes&~AccessibilityEvent.TYPE_VIEW_CLICKED;
        if(flags!=info.flags || events!=info.eventTypes){info.flags=flags;info.eventTypes=events;setServiceInfo(info);DiagnosticLog.info("home_guard.key_filter","enabled="+wanted+" bootObserve="+observe);}
        keyFiltering=wanted;
        if(!wanted)homeKeys.reset();
    }
    @Override protected boolean onKeyEvent(KeyEvent event){
        RemoteKeyMap.observe(this,event,"accessibility");
        DiagnosticLog.navigationKey("existing_accessibility_callback",event);
        String remoteAction=new RemoteKeyMap(this).action(event);
        if(event.getAction()==KeyEvent.ACTION_DOWN && (navigationKey(event.getKeyCode()) || !remoteAction.isEmpty() && !"system".equals(remoteAction)))stopBootSource("remote_navigation");
        if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0){
            if(event.getKeyCode()==KeyEvent.KEYCODE_TV_INPUT || "inputs".equals(remoteAction))inputSelected(this);
            if(event.getKeyCode()==KeyEvent.KEYCODE_HOME || "home".equals(remoteAction))cancelInputTransition();
        }
        // Every other key passes through, including volume, directions and power.
        boolean mappedHome="home".equals(remoteAction);
        if(event.getKeyCode()!=KeyEvent.KEYCODE_HOME && !mappedHome && !(homeKeys.pressed() && event.getKeyCode()==filteredHomeCode))return false;
        boolean eligible=event.getKeyCode()==KeyEvent.KEYCODE_HOME?filterHome():mappedHome && homeActionUsable() && HomeGuardState.canShow(this);
        if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0)homeObservation=DiagnosticOperation.nextId(System.currentTimeMillis());
        if(event.getRepeatCount()==0 || event.getAction()==KeyEvent.ACTION_UP){
            String problem=state==null?"service_unavailable":state.homeActionProblem();
            if(!HomeGuardState.canShow(this))problem="screen_or_unlock_unavailable";
            else if(problem.isEmpty() && !eligible)problem="system_handles_home";
            DiagnosticLog.info("home_guard.key_observed","observation="+homeObservation+" source=accessibility keyCode="+event.getKeyCode()
                +" scanCode="+event.getScanCode()+" action="+event.getAction()+" repeat="+event.getRepeatCount()+" cancelled="+event.isCanceled()
                +" uptimeMs="+SystemClock.uptimeMillis()+" filterRequested="+keyFiltering+" eligible="+eligible+" reason="+(problem.isEmpty()?"eligible":problem));
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN && homeKeys.passed(event.getDownTime()))return false;
        if(!homeKeys.pressed() && !eligible){if(event.getAction()==KeyEvent.ACTION_DOWN)homeKeys.pass(event.getDownTime());return false;}
        if(event.getAction()==KeyEvent.ACTION_DOWN){filteredHomeCode=event.getKeyCode();homeKeys.down(event.getDownTime());return true;}
        if(event.getAction()!=KeyEvent.ACTION_UP)return homeKeys.pressed();
        if(!homeKeys.pressed())return false;
        int action=homeKeys.up(event.getEventTime(),event.isCanceled() || !eligible || !homeActionUsable() || !HomeGuardState.canShow(this),
                ViewConfiguration.getLongPressTimeout(),ViewConfiguration.getDoubleTapTimeout());
        updateKeyFilter();
        if(action==HomeKeyPolicy.RECENTS && Build.VERSION.SDK_INT>=21){cancel();boolean accepted=performGlobalAction(GLOBAL_ACTION_RECENTS);DiagnosticLog.info("home_guard.recents","observation="+homeObservation+" accepted="+accepted);}
        else if(action==HomeKeyPolicy.HOME){
            allowStock=false;cancel();policy.clear();transitionUntil=0;
            DiagnosticLog.info("home_guard.home_key","source=filter shortPress=true");HomeGuardRecovery.launchHomeAction(this,"HOME 按键",homeObservation);
        }
        return true;
    }
    private final BroadcastReceiver packageChanges=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            ComponentName selected=state==null?null:ComponentName.unflattenFromString(state.selected());
            if(selected!=null && intent.getData()!=null && selected.getPackageName().equals(intent.getData().getSchemeSpecificPart()))state.invalidateEventHome();
        }
    };
    private final Runnable remoteKeysExpiry=this::updateKeyFilter;
    static void remoteKeysChanged(){HomeGuardService service=instance;if(service==null)return;
        service.handler.post(()->{service.handler.removeCallbacks(service.remoteKeysExpiry);service.updateKeyFilter();
            long delay=new RemoteKeyMap(service).refreshFor();if(delay>0)service.handler.postDelayed(service.remoteKeysExpiry,delay);});
    }
    private static boolean bootSourceDevice(){return BootSourcePolicy.supported(Build.MANUFACTURER,Build.MODEL,Build.DEVICE,Build.FINGERPRINT,Build.VERSION.SDK_INT,BuildConfig.LEGACY_19);}
    private void restoreBootSource(){
        if(!bootSourceDevice() || !state.ready() || !HomeGuardPolicy.bootRecheck(SystemClock.elapsedRealtime()))return;
        if(!TvInputController.canOpen(this,ComponentName.unflattenFromString(BootSourcePolicy.PLAYER)))return;
        String boot=new BootAppStore(this).bootId();
        bootSource=new BootSourcePolicy(boot,state.preferences.getString("sourceBoot",""),state.preferences.getInt("sourceBootAttempts",0),state.preferences.getBoolean("sourceBootStopped",false),"9S63_H6".equals(Build.MODEL));
        saveBootSource();
    }
    @android.annotation.SuppressLint("ApplySharedPref")
    private void saveBootSource(){
        if(bootSource==null)return;
        if(!state.preferences.edit().putString("sourceBoot",bootSource.boot).putInt("sourceBootAttempts",bootSource.attempts()).putBoolean("sourceBootStopped",bootSource.stopped()).commit())bootSource.stop();
    }
    private boolean observeBootSource(){
        if(Build.VERSION.SDK_INT<21)return false;
        if(bootSource==null || !bootSource.active(SystemClock.elapsedRealtime()) || !state.ready())return false;
        AccessibilityServiceInfo info=getServiceInfo();
        return info!=null && (info.getCapabilities()&AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS)!=0;
    }
    private final Runnable bootSourceCheck=()->{
        if(!observeBootSource() || !keyFiltering || !HomeGuardState.canShow(this) || allowStock || homeResumed || HomeGuardRecovery.handoverPending())return;
        // Never replace an observed settings/app window with a guess about the input player.
        if(!bootSource.take(SystemClock.elapsedRealtime(),lastActivity))return;
        saveBootSource();
        if(!bootSource.stopped()){
            DiagnosticLog.info("home_guard.boot_source","decision=handover attempt="+bootSource.attempts()+" foreground="+(lastActivity.isEmpty()?"unknown":"input_player"));
            HomeGuardRecovery.launch(this,"开机信号源恢复");
        }
        updateKeyFilter();
    };
    private final Runnable bootSourceExpiry=()->{handler.removeCallbacks(bootSourceCheck);updateKeyFilter();};
    private void scheduleBootSource(){
        if(!observeBootSource())return;
        handler.removeCallbacks(bootSourceCheck);handler.postDelayed(bootSourceCheck,HomeGuardPolicy.SETTLE_MS);
        handler.removeCallbacks(bootSourceExpiry);handler.postDelayed(bootSourceExpiry,Math.max(0,HomeGuardPolicy.BOOT_WINDOW_MS-SystemClock.elapsedRealtime()));
    }
    private void stopBootSource(String reason){
        if(bootSource==null || bootSource.stopped())return;
        bootSource.stop();saveBootSource();cancel();cancelRecoveryChecks();
        DiagnosticLog.info("home_guard.boot_source","decision=stop reason="+reason);updateKeyFilter();
    }
    static boolean navigationKey(int key){
        return key!=KeyEvent.KEYCODE_UNKNOWN && key!=KeyEvent.KEYCODE_POWER && key!=KeyEvent.KEYCODE_SLEEP && key!=KeyEvent.KEYCODE_WAKEUP
            && key!=KeyEvent.KEYCODE_VOLUME_UP && key!=KeyEvent.KEYCODE_VOLUME_DOWN && key!=KeyEvent.KEYCODE_VOLUME_MUTE;
    }
    @android.annotation.SuppressLint("ApplySharedPref")
    static void bootSourceUserAction(Context c){
        bootSourceUserAction(c,"desktop_navigation");
    }
    @android.annotation.SuppressLint("ApplySharedPref")
    static void bootSourceUserAction(Context c,String reason){
        if(!bootSourceDevice() || !HomeGuardState.unlocked(c) || !HomeGuardPolicy.bootRecheck(SystemClock.elapsedRealtime()))return;
        HomeGuardService service=instance;
        if(service!=null && service.bootSource!=null){service.stopBootSource(reason);return;}
        // A click may arrive while the system is rebinding accessibility; retain it for this boot.
        String boot=new BootAppStore(c).bootId();if(boot.isEmpty())return;
        new HomeGuardState(c,false).preferences.edit().putString("sourceBoot",boot).putBoolean("sourceBootStopped",true).commit();
    }
    static void inputSelected(Context c){
        if(!bootSourceDevice())return;
        bootSourceUserAction(c,"input_selection");
        // The vendor source transition can briefly pass through its original HOME.
        HomeGuardService service=instance;
        if(service!=null){service.policy.inputSelected(SystemClock.uptimeMillis());service.cancel();}
        HomeGuardRecovery.cancel();
    }
    static boolean inputTransition(){HomeGuardService service=instance;return service!=null && service.policy.inputTransition(SystemClock.uptimeMillis());}
    static void cancelInputTransition(){HomeGuardService service=instance;if(service!=null){service.policy.cancelInput();service.transitionUntil=0;service.cancel();}}
    // Aerial 1.49: a vendor boot page may cover the first successful takeover much later.
    @android.annotation.SuppressLint("InlinedApi")
    private final Runnable bootRecheck=()->{
        if(!HomeGuardPolicy.bootRecheck(SystemClock.elapsedRealtime()) || !usable() || !HomeGuardState.canShow(this) || allowStock)return;
        String front=lastActivity;long at=lastActivityAt;
        if(usageAllowed(this))try{
            UsageEvents.Event latest=latestUsage(this,HomeGuardPolicy.BOOT_WINDOW_MS);
            if(latest!=null && latest.getTimeStamp()>=at && latest.getPackageName()!=null && latest.getClassName()!=null){
                front=new ComponentName(latest.getPackageName(),latest.getClassName().startsWith(".")?latest.getPackageName()+latest.getClassName():latest.getClassName()).flattenToString();at=latest.getTimeStamp();
            }
        }catch(SecurityException|IllegalStateException ignored){}
        // An unknown/other foreground is never converted into a stock-home observation.
        boolean take=HomeGuardPolicy.recheckTarget(state.eventHome(),front,at,System.currentTimeMillis());
        DiagnosticLog.info("home_guard.boot_recheck","decision="+(take?"handover":"foreground_not_stock"));
        if(take)HomeGuardRecovery.launch(this,"开机后原主页复查");
        scheduleBootSource();
    };
    private final Runnable silentCheck=()->{
        if(sawActivityEvent || state!=null && state.directHomeSelected() || !usable() || !HomeGuardState.canShow(this) || allowStock)return;
        if(HomeGuardPolicy.xiaomiRecovery(Build.MANUFACTURER,Build.BRAND,Build.VERSION.SDK_INT,BuildConfig.LEGACY_19))HomeGuardRecovery.restartSilentAccessibility(this);
        else HomeGuardRecovery.rebindIfSilent(this);
    };
    private void scheduleSilentCheck(){
        handler.removeCallbacks(silentCheck);
        if(Build.VERSION.SDK_INT<=23 && state!=null && state.enabled() && !sawActivityEvent)handler.postDelayed(silentCheck,HomeGuardPolicy.DEAF_CHECK_MS);
    }
    private void cancelRecoveryChecks(){handler.removeCallbacks(bootRecheck);handler.removeCallbacks(silentCheck);handler.removeCallbacks(bootSourceCheck);handler.removeCallbacks(bootSourceExpiry);bootRechecksScheduled=false;}
    private void scheduleBootRechecks(){
        if(bootRechecksScheduled || state==null || !state.enabled() || !HomeGuardPolicy.bootRecheck(SystemClock.elapsedRealtime()))return;
        bootRechecksScheduled=true;handler.postDelayed(bootRecheck,HomeGuardPolicy.BOOT_RECHECK_FIRST_MS);handler.postDelayed(bootRecheck,HomeGuardPolicy.BOOT_RECHECK_SECOND_MS);
    }
    private final Runnable usageCheck=new Runnable(){
        // Shared with API19; the usage service exists on API21, before its public constant in API22.
        @android.annotation.SuppressLint({"ObsoleteSdkInt","InlinedApi"})
        @Override public void run(){
            if(Build.VERSION.SDK_INT<21 || !watchUsage())return;
            handler.postDelayed(this,HomeGuardPolicy.USAGE_POLL_MS);
            if(!HomeGuardState.canShow(HomeGuardService.this) || !usable())return;
            long now=System.currentTimeMillis();UsageEvents.Event latest=latestUsage(HomeGuardService.this,HomeGuardPolicy.USAGE_LOOKBACK_MS);
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
            if(policy.consumeUsage(state.eventHome(),pkg,cls,latest.getTimeStamp(),now)){
                HomeGuardRecovery.observed("usage","handover",now-latest.getTimeStamp());
                DiagnosticLog.info("home_guard.usage_takeover","原主页");HomeGuardRecovery.launch(HomeGuardService.this,"用量统计原主页");
            }else HomeGuardRecovery.observed("usage","stale_or_consumed",now-latest.getTimeStamp());
        }
    };
    @android.annotation.SuppressLint({"ObsoleteSdkInt","InlinedApi"})
    private UsageEvents.Event latestUsage(){return latestUsage(this,HomeGuardPolicy.bootRecheck(SystemClock.elapsedRealtime())?HomeGuardPolicy.BOOT_WINDOW_MS:HomeGuardPolicy.USAGE_LOOKBACK_MS);}
    @android.annotation.SuppressLint({"ObsoleteSdkInt","InlinedApi"})
    static UsageEvents.Event latestUsage(Context context,long lookback){
        if(Build.VERSION.SDK_INT<21 || !usageAllowed(context))return null;
        long now=System.currentTimeMillis();UsageEvents.Event latest=null;
        try{
            UsageStatsManager manager=(UsageStatsManager)context.getSystemService(Context.USAGE_STATS_SERVICE);
            UsageEvents events=manager==null?null:manager.queryEvents(now-lookback,now);
            if(events==null)return null;
            UsageEvents.Event event=new UsageEvents.Event();
            while(events.hasNextEvent()){
                events.getNextEvent(event);
                if(event.getEventType()==UsageEvents.Event.MOVE_TO_FOREGROUND && event.getTimeStamp()<=now
                        && (latest==null || event.getTimeStamp()>=latest.getTimeStamp())){latest=event;event=new UsageEvents.Event();}
            }
        }catch(SecurityException|IllegalStateException failure){return null;}
        return latest;
    }
    static boolean usageRequired(Context c){if(new HomeGuardState(c,false).directHomeReady())return false;return HomeGuardPolicy.usageEvidence(Build.MANUFACTURER,Build.BRAND,Build.VERSION.SDK_INT,BuildConfig.LEGACY_19,new HomeGuardState(c,false).selected());}
    static boolean usageAllowed(Context c){
        if(Build.VERSION.SDK_INT<21)return false;
        if(!usageRequired(c))return false;
        AppOpsManager manager=(AppOpsManager)c.getSystemService(Context.APP_OPS_SERVICE);
        try{return manager!=null && manager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),c.getPackageName())==AppOpsManager.MODE_ALLOWED;}
        catch(SecurityException failure){return false;}
    }
    private boolean watchUsage(){return state!=null && state.enabled() && HomeGuardState.canShow(this)
            && HomeGuardPolicy.usageFallback(Build.VERSION.SDK_INT,BuildConfig.LEGACY_19,state.selected()) && usageAllowed(this);}
    static boolean bootHomeObserved(Context context){
        if(Build.VERSION.SDK_INT<21)return false;
        HomeGuardState state=new HomeGuardState(context,false);UsageEvents.Event latest=latestUsage(context,HomeGuardPolicy.BOOT_WINDOW_MS);
        return latest!=null && HomeGuardPolicy.matches(state.eventHome(),latest.getPackageName(),latest.getClassName());
    }
    private void refreshUsageWatch(){handler.removeCallbacks(usageCheck);if(watchUsage())handler.post(usageCheck);}
    static void homeRequested(){HomeGuardService service=instance;if(service!=null)service.policy.ignoreUsageThrough(System.currentTimeMillis());}
    private boolean homeActionEligible(){
        if(HomeGuardPolicy.xiaomi(Build.MANUFACTURER,Build.BRAND))return true;
        ComponentName original=ComponentName.unflattenFromString(state.selected());
        return original!=null && original.getPackageName().equals(lastPackage);
    }
    private final Runnable homeKeyHandover=()->{
        if(!homeActionUsable() || !HomeGuardState.canShow(this) || allowStock || !homeActionEligible())return;
        // This was an explicit HOME action. The old app may still be the last observed window.
        HomeGuardRecovery.launchHomeAction(this,"HOME 动作",broadcastObservation);
    };
    private final Runnable handover=()->{
        if(Build.VERSION.SDK_INT<21 || !usable() || !HomeGuardState.canShow(this) || allowStock){DiagnosticLog.info("home_guard.settle","source="+settleReason+" decision=conditions_unavailable allowOriginal="+allowStock);return;}
        UsageEvents.Event latest=latestUsage();
        boolean fresh=latest!=null && latest.getTimeStamp()>=lastActivityAt && latest.getPackageName()!=null && latest.getClassName()!=null;
        if(fresh)
            policy.observe(state.eventHome(),latest.getPackageName(),latest.getClassName(),SystemClock.uptimeMillis());
        boolean other=fresh && !HomeGuardPolicy.matches(state.eventHome(),latest.getPackageName(),latest.getClassName());
        boolean unknown=HomeGuardPolicy.xiaomi(Build.MANUFACTURER,Build.BRAND) && latest==null && policy.unknown();
        boolean accepted=!other && !unknown && policy.settle(state.eventHome(),HomeGuardRecovery.unknownAllowed(this));
        DiagnosticLog.info("home_guard.settle","source="+settleReason+" decision="+(accepted?"handover":"policy_skip")+" windowUnknown="+policy.unknown());
        if(accepted)HomeGuardRecovery.launch(this,settleReason);
    };
    private final BroadcastReceiver lifecycle=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            String action=intent.getAction();
            if(!Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action))DiagnosticLog.info("home_guard.lifecycle","action="+action+" elapsedRealtimeMs="+SystemClock.elapsedRealtime());
            if(Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)){
                if(!"homekey".equals(intent.getStringExtra("reason")))return;
                cancelInputTransition();
                boolean available=homeActionUsable() && HomeGuardState.canShow(HomeGuardService.this);
                broadcastObservation=DiagnosticOperation.nextId(System.currentTimeMillis());
                String problem=HomeGuardState.canShow(HomeGuardService.this)?state.homeActionProblem():"screen_or_unlock_unavailable";
                DiagnosticLog.info("home_guard.home_action","observation="+broadcastObservation+" received=true conditionsAvailable="+available+" reason="+(problem.isEmpty()?"eligible":problem)+" physicalKeyVerified=false");
                if(!available || !homeActionEligible())return;
                allowStock=false;cancel();policy.clear();transitionUntil=0;
                // Let Android dispatch HOME first. A foreground or pending Home is deduplicated centrally.
                handler.postDelayed(homeKeyHandover,150);
                return;
            }
            if(Intent.ACTION_SCREEN_OFF.equals(action)){stopBootSource("screen_off");handler.removeCallbacks(usageCheck);cancel();cancelRecoveryChecks();HomeGuardRecovery.cancel();policy.clear();lastPackage="";updateKeyFilter();return;}
            if(Intent.ACTION_USER_UNLOCKED.equals(action)){state=new HomeGuardState(HomeGuardService.this);BootAppLifecycle.observe(HomeGuardService.this);restoreBootSource();}
            HomeGuardRecovery.request(context,"屏幕或解锁");schedule("亮屏或解锁");refreshUsageWatch();scheduleBootRechecks();scheduleSilentCheck();updateKeyFilter();
        }
    };
    @Override protected void onServiceConnected(){
        super.onServiceConnected();DiagnosticLog.info("home_guard.service","connected");instance=this;state=new HomeGuardState(this);
        BootAppLifecycle.observe(this);HomeGuardAdb.watch(this);
        state.preferences.registerOnSharedPreferenceChangeListener(this);
        if(!registered){IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            if(Build.VERSION.SDK_INT>=24)filter.addAction(Intent.ACTION_USER_UNLOCKED);androidx.core.content.ContextCompat.registerReceiver(this,lifecycle,filter,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
            IntentFilter packages=new IntentFilter(Intent.ACTION_PACKAGE_CHANGED);packages.addAction(Intent.ACTION_PACKAGE_REPLACED);packages.addAction(Intent.ACTION_PACKAGE_REMOVED);packages.addAction(Intent.ACTION_PACKAGE_ADDED);packages.addDataScheme("package");
            androidx.core.content.ContextCompat.registerReceiver(this,packageChanges,packages,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);registered=true;}
        restoreBootSource();refreshUsageWatch();scheduleBootRechecks();updateKeyFilter();
        remoteKeysChanged();
        scheduleSilentCheck();
        if(state.enabled())state.status(state.directHomeReady()?"HOME 按键补偿已连接，实体效果待验证":"守护已连接，等待原主页");
        if(SystemClock.elapsedRealtime()<HomeGuardPolicy.BOOT_WINDOW_MS || HomeGuardRecovery.unknownAllowed(this))schedule("服务连接");
    }
    static boolean connected(){return instance!=null;}
    static void dreamChanged(){HomeGuardService service=instance;if(service!=null){service.stopBootSource("dream_changed");service.cancel();service.policy.ignoreUsageThrough(System.currentTimeMillis());service.updateKeyFilter();service.refreshUsageWatch();}}
    static boolean keyFiltering(){HomeGuardService service=instance;return service!=null && service.keyFiltering;}
    static boolean keyFilterCapable(){
        if(Build.VERSION.SDK_INT<21)return false;
        HomeGuardService service=instance;AccessibilityServiceInfo info=service==null?null:service.getServiceInfo();
        return info!=null && (info.getCapabilities()&AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS)!=0;
    }
    static boolean homeResumed(){return homeResumed;}
    static void homeResumed(boolean value){
        homeResumed=value;HomeGuardService service=instance;
        if(service!=null){if(value)service.cancel();service.updateKeyFilter();}
    }
    static void cancelPending(){HomeGuardService service=instance;if(service!=null){Runnable cancel=()->{service.cancel();service.cancelRecoveryChecks();};if(Looper.myLooper()==Looper.getMainLooper())cancel.run();else service.handler.post(cancel);}}
    static void lifecycle(String reason){HomeGuardService service=instance;if(service!=null){service.schedule(reason);service.refreshUsageWatch();service.scheduleBootRechecks();service.scheduleSilentCheck();}}
    static void allowOriginal(){HomeGuardUsageService.allowOriginal();HomeGuardService service=instance;if(service!=null){service.allowStock=true;service.cancel();}}
    static void homeVisible(){HomeGuardUsageService.visible();HomeGuardService service=instance;if(service!=null){service.allowStock=false;service.lastActivity=new ComponentName(service,HomeActivity.class).flattenToString();service.lastActivityAt=System.currentTimeMillis();service.policy.confirmed();service.cancel();}}
    private void cancel(){handler.removeCallbacks(handover);handler.removeCallbacks(homeKeyHandover);}
    private void schedule(String reason){
        cancel();if(state==null || !state.enabled())return;
        long delay=Math.max(HomeGuardPolicy.SETTLE_MS,policy.inputDelay(SystemClock.uptimeMillis()));
        settleReason=reason;transitionUntil=SystemClock.uptimeMillis()+delay;handler.postDelayed(handover,delay);
        scheduleBootSource();
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences preferences,String key){
        if("mode".equals(key) || "home".equals(key) || "firmware".equals(key) || "configured".equals(key) || "observeHome".equals(key) || "directHomeKeys".equals(key) || "routeHome".equals(key) || "routeRevision".equals(key)){
            if("home".equals(key) || "firmware".equals(key))state.invalidateEventHome();
            cancel();cancelRecoveryChecks();policy.clear();lastPackage="";allowStock=false;
            if(state.enabled()){schedule("配置恢复");scheduleBootRechecks();scheduleSilentCheck();}refreshUsageWatch();updateKeyFilter();
        }
    }
    private boolean homeActionUsable(){return state!=null && HomeGuardState.unlocked(this) && state.homeActionReady();}
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
        if(event!=null && event.getEventType()==AccessibilityEvent.TYPE_VIEW_CLICKED){stopBootSource("view_click");return;}
        if(event==null || event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || state==null)return;
        if(!state.enabled() || !HomeGuardState.canShow(this)){cancel();return;}
        String pkg=event.getPackageName()==null?null:event.getPackageName().toString(),cls=event.getClassName()==null?null:event.getClassName().toString();
        if(pkg==null || cls==null || !activity(pkg,cls))return;
        long now=SystemClock.uptimeMillis(),age=now-event.getEventTime();
        if(age<0 || age>HomeGuardPolicy.MAX_AGE_MS){if(HomeGuardPolicy.matches(state.eventHome(),pkg,cls))HomeGuardRecovery.observed("accessibility","stale_event",age);return;}
        sawActivityEvent=true;lastActivity=new ComponentName(pkg,cls.startsWith(".")?pkg+cls:cls).flattenToString();lastActivityAt=System.currentTimeMillis()-age;
        if(BootSourcePolicy.PLAYER.equals(lastActivity))scheduleBootSource();
        // A delayed usage event must not override a newer settings/player accessibility event.
        policy.ignoreUsageThrough(System.currentTimeMillis()-age);
        BootAppLifecycle.foreground(this,(pkg.equals(getPackageName()) && cls.equals(HomeActivity.class.getName()))
            || (state.validSelection() && HomeGuardPolicy.matches(state.eventHome(),pkg,cls)));
        String previous=lastPackage;lastPackage=pkg;
        if(bootSourceDevice() && BootSourcePolicy.PLAYER.equals(lastActivity) && !pkg.equals(previous)
                && (bootSource==null || !bootSource.active(SystemClock.elapsedRealtime())))inputSelected(this);
        boolean target=policy.observe(state.eventHome(),pkg,cls,now);
        ComponentName original=ComponentName.unflattenFromString(state.selected());
        if(allowStock && original!=null && !pkg.equals(original.getPackageName()) && !pkg.equals(getPackageName()))allowStock=false;
        if(!HomeGuardPolicy.matches(state.eventHome(),pkg,cls)){
            cancel();if(!pkg.equals(getPackageName()))state.preferences.edit().putBoolean("homeInFront",false).apply();return;
        }
        if(!usable() || allowStock || !target){HomeGuardRecovery.observed("accessibility",allowStock?"allow_original":!target?"duplicate_window":"configuration_unavailable",age);return;}
        if(policy.inputTransition(now)){HomeGuardRecovery.observed("accessibility","input_transition",age);schedule("信号源切换");return;}
        // Skyworth's source service may briefly show Home before the actual input player.
        if("com.tianci.tv".equals(previous) || "com.tianci.tv".equals(pkg) || bootSourceDevice() && "com.skyworth.tv".equals(previous) || now<transitionUntil){HomeGuardRecovery.observed("accessibility","settling",age);schedule("主页稳定期");return;}
        if(policy.consume(state.eventHome(),now,true,true,true)){HomeGuardRecovery.observed("accessibility","handover",age);HomeGuardRecovery.launch(this,"原主页事件");}
        else HomeGuardRecovery.observed("accessibility","stale_or_consumed",age);
    }
    @Override public void onInterrupt(){DiagnosticLog.info("home_guard.service","interrupted");cancel();cancelRecoveryChecks();}
    @Override public boolean onUnbind(Intent intent){
        handler.removeCallbacks(remoteKeysExpiry);
        DiagnosticLog.info("home_guard.service","unbound");
        cancel();cancelRecoveryChecks();handler.removeCallbacks(usageCheck);if(instance==this)instance=null;
        if(state!=null && state.enabled())state.status("无障碍已断开，等待系统恢复");
        return super.onUnbind(intent);
    }
    @Override public void onDestroy(){
        handler.removeCallbacks(remoteKeysExpiry);
        cancel();cancelRecoveryChecks();handler.removeCallbacks(usageCheck);if(instance==this)instance=null;
        if(state!=null)state.preferences.unregisterOnSharedPreferenceChangeListener(this);
        if(registered){unregisterReceiver(lifecycle);unregisterReceiver(packageChanges);}super.onDestroy();
    }
}
