package home.quiet.tv;

import android.accessibilityservice.AccessibilityService;
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
    private boolean registered,allowStock;
    private long transitionUntil;
    private String lastPackage="",settleReason="";
    private final Runnable homeKeyHandover=()->{
        if(!usable() || !HomeGuardState.canShow(this) || allowStock)return;
        ComponentName original=ComponentName.unflattenFromString(state.selected());
        if(original!=null && original.getPackageName().equals(lastPackage))HomeGuardRecovery.launch(this,"HOME 动作");
    };
    private final Runnable handover=()->{
        if(!usable() || !HomeGuardState.canShow(this) || allowStock)return;
        if(policy.settle(state.eventHome(),HomeGuardRecovery.unknownAllowed(this)))HomeGuardRecovery.launch(this,settleReason);
    };
    private final BroadcastReceiver lifecycle=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            String action=intent.getAction();
            if(Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)){
                if(!"homekey".equals(intent.getStringExtra("reason")) || !usable() || !HomeGuardState.canShow(HomeGuardService.this))return;
                ComponentName original=ComponentName.unflattenFromString(state.selected());
                ResolveInfo resolved=getPackageManager().resolveActivity(HomeController.homeIntent(),PackageManager.MATCH_DEFAULT_ONLY);
                boolean own=resolved!=null && resolved.activityInfo!=null && getPackageName().equals(resolved.activityInfo.packageName);
                if(!own && original!=null && original.getPackageName().equals(lastPackage)){
                    allowStock=false;cancel();policy.clear();transitionUntil=0;
                    // Let Android dispatch HOME first; otherwise its subsequent stock launch can cover ours.
                    handler.postDelayed(homeKeyHandover,150);
                }
                return;
            }
            if(Intent.ACTION_SCREEN_OFF.equals(action)){cancel();HomeGuardRecovery.cancel();policy.clear();lastPackage="";return;}
            if(Intent.ACTION_USER_UNLOCKED.equals(action))state=new HomeGuardState(HomeGuardService.this);
            HomeGuardRecovery.request(context,"屏幕或解锁");schedule("亮屏或解锁");
        }
    };
    @Override protected void onServiceConnected(){
        super.onServiceConnected();instance=this;state=new HomeGuardState(this);
        state.preferences.registerOnSharedPreferenceChangeListener(this);
        if(!registered){IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            if(Build.VERSION.SDK_INT>=24)filter.addAction(Intent.ACTION_USER_UNLOCKED);androidx.core.content.ContextCompat.registerReceiver(this,lifecycle,filter,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);registered=true;}
        if(state.enabled())state.status("守护已连接，等待原主页");
        if(SystemClock.elapsedRealtime()<HomeGuardPolicy.BOOT_WINDOW_MS || HomeGuardRecovery.unknownAllowed(this))schedule("服务连接");
    }
    static boolean connected(){return instance!=null;}
    static void cancelPending(){HomeGuardService service=instance;if(service!=null){if(Looper.myLooper()==Looper.getMainLooper())service.cancel();else service.handler.post(service::cancel);}}
    static void lifecycle(String reason){HomeGuardService service=instance;if(service!=null)service.schedule(reason);}
    static void allowOriginal(){HomeGuardService service=instance;if(service!=null){service.allowStock=true;service.cancel();}}
    static void homeVisible(){HomeGuardService service=instance;if(service!=null){service.allowStock=false;service.policy.confirmed();service.cancel();}}
    private void cancel(){handler.removeCallbacks(handover);handler.removeCallbacks(homeKeyHandover);}
    private void schedule(String reason){
        cancel();if(state==null || !state.enabled())return;
        settleReason=reason;transitionUntil=SystemClock.uptimeMillis()+HomeGuardPolicy.SETTLE_MS;handler.postDelayed(handover,HomeGuardPolicy.SETTLE_MS);
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences preferences,String key){
        if("mode".equals(key) || "home".equals(key) || "firmware".equals(key) || "configured".equals(key)){
            cancel();policy.clear();lastPackage="";allowStock=false;
            if(state.enabled())schedule("配置恢复");
        }
    }
    private boolean usable(){
        if(state==null || !state.enabled())return false;
        if(!HomeGuardState.unlocked(this))return false;
        if(!state.validSelection()){state.status("原主页或固件已变化，请重新配置");return false;}
        if(state.helperActive()){state.status("独立中转助手正在接管");return false;}return true;
    }
    private boolean activity(String pkg,String cls){
        try{getPackageManager().getActivityInfo(new ComponentName(pkg,cls.startsWith(".")?pkg+cls:cls),0);return true;}
        catch(PackageManager.NameNotFoundException|SecurityException ignored){return false;}
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null || event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || state==null)return;
        if(!state.enabled() || !HomeGuardState.canShow(this)){cancel();return;}
        String pkg=event.getPackageName()==null?null:event.getPackageName().toString(),cls=event.getClassName()==null?null:event.getClassName().toString();
        if(pkg==null || cls==null || !activity(pkg,cls))return;
        long now=SystemClock.uptimeMillis(),age=now-event.getEventTime();if(age<0 || age>HomeGuardPolicy.MAX_AGE_MS)return;
        String previous=lastPackage;lastPackage=pkg;
        boolean target=policy.observe(state.eventHome(),pkg,cls,now);
        ComponentName original=ComponentName.unflattenFromString(state.selected());
        if(allowStock && original!=null && !pkg.equals(original.getPackageName()) && !pkg.equals(getPackageName()))allowStock=false;
        if(!HomeGuardPolicy.matches(state.eventHome(),pkg,cls)){
            cancel();if(!pkg.equals(getPackageName()))state.preferences.edit().putBoolean("homeInFront",false).apply();return;
        }
        if(!usable() || allowStock || !target)return;
        // Skyworth's source service may briefly show Home before the actual input player.
        if("com.tianci.tv".equals(previous) || "com.tianci.tv".equals(pkg) || now<transitionUntil){schedule("主页稳定期");return;}
        if(policy.consume(state.eventHome(),now,true,true,true))HomeGuardRecovery.launch(this,"原主页事件");
    }
    @Override public void onInterrupt(){cancel();}
    @Override public boolean onUnbind(Intent intent){
        cancel();if(instance==this)instance=null;
        if(state!=null && state.enabled())state.status("无障碍已断开，等待系统恢复");
        return super.onUnbind(intent);
    }
    @Override public void onDestroy(){
        cancel();if(instance==this)instance=null;
        if(state!=null)state.preferences.unregisterOnSharedPreferenceChangeListener(this);
        if(registered)unregisterReceiver(lifecycle);super.onDestroy();
    }
}
