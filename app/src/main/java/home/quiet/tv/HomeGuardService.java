package home.quiet.tv;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.*;
import android.os.*;
import android.view.accessibility.AccessibilityEvent;

/** Only a recent selected-home event can bring Home forward; lifecycle callbacks never guess. */
public final class HomeGuardService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static volatile HomeGuardService instance;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final HomeGuardPolicy policy=new HomeGuardPolicy();
    private HomeGuardState state;
    private boolean registered,requested;
    private final Runnable handover=()->{
        if(!usable())return;
        if(!policy.consume(state.selected(),SystemClock.uptimeMillis(),state.enabled(),interactive(),unlocked()))return;
        try{
            startActivity(new Intent(this,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            requested=true;state.status("已请求中转，仍需遥控器验证");
        }catch(ActivityNotFoundException|SecurityException failure){state.pause("启动被系统拒绝，守护已暂停");}
    };
    private final BroadcastReceiver lifecycle=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            cancel();
            if(state.enabled())state.status("已启用，等待主页事件");
        }
    };
    @Override protected void onServiceConnected(){
        super.onServiceConnected();instance=this;state=new HomeGuardState(this);
        state.preferences.registerOnSharedPreferenceChangeListener(this);
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);
        if(Build.VERSION.SDK_INT>=24)filter.addAction(Intent.ACTION_USER_UNLOCKED);
        if(!registered){if(Build.VERSION.SDK_INT>=33)registerReceiver(lifecycle,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(lifecycle,filter);}
        registered=true;
        if(state.enabled())state.status("已启用，等待主页事件");
        usable();
    }
    static boolean connected(){return instance!=null;}
    static void cancelPending(){HomeGuardService service=instance;if(service!=null){if(Looper.myLooper()==Looper.getMainLooper())service.cancel();else service.handler.post(service::cancel);}}
    private void cancel(){handler.removeCallbacks(handover);policy.clear();requested=false;}
    @Override public void onSharedPreferenceChanged(SharedPreferences preferences,String key){
        if("enabled".equals(key) || "home".equals(key) || "firmware".equals(key)){cancel();if(state.enabled())usable();}
    }
    private boolean interactive(){PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);return power!=null && Build.VERSION.SDK_INT>=20 && power.isInteractive();}
    private boolean unlocked(){
        KeyguardManager keyguard=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if(keyguard==null || keyguard.isKeyguardLocked())return false;
        if(Build.VERSION.SDK_INT>=24){UserManager user=(UserManager)getSystemService(USER_SERVICE);if(user==null || !user.isUserUnlocked())return false;}
        return true;
    }
    private boolean usable(){
        if(state==null || !state.enabled())return false;
        if(!state.supported() || !state.validSelection()){state.pause("原主页或适用条件已变化，守护已暂停");return false;}
        if(state.helperActive()){state.pause("检测到独立中转助手，守护已暂停");return false;}
        return true;
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null || event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return;
        if(state==null || !state.enabled() || !interactive() || !unlocked()){cancel();return;}
        String pkg=event.getPackageName()==null?null:event.getPackageName().toString();
        String cls=event.getClassName()==null?null:event.getClassName().toString();
        long now=SystemClock.uptimeMillis(),age=now-event.getEventTime();
        if(age<0 || age>HomeGuardPolicy.MAX_AGE_MS){cancel();return;}
        if(requested && getPackageName().equals(pkg) && HomeActivity.class.getName().equals(cls))state.status("已返回 Quiet TV，仍需遥控器验证");
        if(!HomeGuardPolicy.matches(state.selected(),pkg,cls)){cancel();return;}
        if(!usable()){cancel();return;}
        if(policy.observe(state.selected(),pkg,cls,now))handler.postDelayed(handover,HomeGuardPolicy.SETTLE_MS);
    }
    @Override public void onInterrupt(){cancel();}
    @Override public boolean onUnbind(Intent intent){
        cancel();if(instance==this)instance=null;
        if(state!=null && state.enabled())state.pause("无障碍服务已断开，请检查后重新启用");
        return super.onUnbind(intent);
    }
    @Override public void onDestroy(){
        cancel();if(instance==this)instance=null;
        if(state!=null)state.preferences.unregisterOnSharedPreferenceChangeListener(this);
        if(registered)unregisterReceiver(lifecycle);
        super.onDestroy();
    }
}
