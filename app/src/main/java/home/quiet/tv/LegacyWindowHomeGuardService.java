package home.quiet.tv;

import android.accessibilityservice.AccessibilityService;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.accessibility.AccessibilityEvent;
import java.util.Set;

/** API18-only events and explicit dispatch, isolated from AppOps and UsageStats classes. */
@android.annotation.TargetApi(18)
public final class LegacyWindowHomeGuardService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static LegacyWindowHomeGuardService instance;
    private static long dispatchedAt,observedAt;
    private final HomeGuardPolicy policy=new HomeGuardPolicy();
    private SharedPreferences preferences;
    private boolean allowOriginal,registered;
    private final BroadcastReceiver lifecycle=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent intent){
            if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction()))policy.clear();
            else if("homekey".equals(intent.getStringExtra("reason")))policy.cancelInput();
        }
    };
    static boolean connected(){return instance!=null;}
    static long dispatchedAt(){return dispatchedAt;}
    static long observedAt(){return observedAt;}
    static void allowOriginal(){if(instance!=null){instance.allowOriginal=true;instance.policy.clear();}}
    static void inputSelected(){if(instance!=null)instance.policy.inputSelected(SystemClock.uptimeMillis());}
    @Override protected void onServiceConnected(){
        super.onServiceConnected();if(!LegacyWindowHomeGuard.supported())return;
        disconnect();
        instance=this;dispatchedAt=0;observedAt=0;preferences=getSharedPreferences(HomeGuardState.PREFS,MODE_PRIVATE);
        preferences.registerOnSharedPreferenceChangeListener(this);
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        androidx.core.content.ContextCompat.registerReceiver(this,lifecycle,filter,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);registered=true;
        DiagnosticLog.info("legacy_window_guard.service","bound=true physicalHomeVerified=false");
    }
    private boolean canShow(){
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        KeyguardManager keyguard=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        return power!=null && power.isScreenOn() && keyguard!=null && !UiCompat.keyguardLocked(keyguard) && !BootAppLifecycle.dreaming();
    }
    static Intent returnIntent(Context c){
        return new Intent(c,HomeActivity.class).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }
    @android.annotation.SuppressLint("UnspecifiedImmutableFlag") // Only API18 binds this service.
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null || event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return;
        if(instance!=this || !LegacyWindowHomeGuard.enabled(this) || !LegacyWindowHomeGuard.authorized(this) || !canShow()){policy.clear();return;}
        long now=SystemClock.uptimeMillis(),age=now-event.getEventTime();
        if(age<0 || age>HomeGuardPolicy.MAX_AGE_MS)return;
        String pkg=event.getPackageName()==null?"":event.getPackageName().toString(),cls=event.getClassName()==null?"":event.getClassName().toString();
        if(pkg.isEmpty() || cls.isEmpty()){policy.clear();return;}
        String name=cls.startsWith(".")?pkg+cls:cls;
        if(pkg.equals(getPackageName()) && name.equals(HomeActivity.class.getName())){observedAt=System.currentTimeMillis();allowOriginal=false;policy.cancelInput();}
        if(allowOriginal && !pkg.equals(getPackageName()) && !pkg.equals(ComponentName.unflattenFromString(LegacyWindowHomeGuard.original(this)).getPackageName()))allowOriginal=false;
        Set<String> targets=LegacyWindowHomeGuard.targets(this);
        policy.observe(targets,pkg,cls,now);
        if(allowOriginal || policy.inputTransition(now) || !policy.consume(targets,now,true,true,true))return;
        // Android 4.3 also defers ordinary activity starts after HOME. The target stays explicit.
        try{
            PendingIntent.getActivity(this,0,returnIntent(this),PendingIntent.FLAG_ONE_SHOT|PendingIntent.FLAG_UPDATE_CURRENT).send();
            dispatchedAt=System.currentTimeMillis();
            DiagnosticLog.info("legacy_window_guard.dispatch","result=dispatched_unverified physicalHomeVerified=false");
        }catch(PendingIntent.CanceledException|RuntimeException failure){DiagnosticLog.error("legacy_window_guard.dispatch",failure);}
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences shared,String key){
        if(key==null || "mode".equals(key) || key.startsWith("windowGuard")){policy.clear();allowOriginal=false;dispatchedAt=0;observedAt=0;}
    }
    @Override public void onInterrupt(){policy.clear();}
    private void disconnect(){
        policy.clear();if(instance==this)instance=null;
        if(preferences!=null){preferences.unregisterOnSharedPreferenceChangeListener(this);preferences=null;}
        if(registered){unregisterReceiver(lifecycle);registered=false;}
    }
    @Override public boolean onUnbind(Intent intent){disconnect();return super.onUnbind(intent);}
    @Override public void onDestroy(){disconnect();super.onDestroy();}
}
