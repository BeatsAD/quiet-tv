package home.quiet.tv;

import android.content.*;
import android.os.*;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Only the system binding is used; notifications are never read. */
@android.annotation.TargetApi(18)
public final class HomeGuardRevivalListener extends NotificationListenerService implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static volatile boolean connected;
    private static HomeGuardRevivalListener instance;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private BroadcastReceiver unlock;
    private boolean rebindRequested,wasEnabled;
    private long ownedAttempt;
    private SharedPreferences preferences;
    private HomeGuardRecoveryWindow window;
    static boolean connected(){return connected;}
    static void guardConnected(){if(instance!=null && instance.window!=null)instance.window.changed();}
    // Required on API19 although the legacy profile disables this component.
    @Override public void onNotificationPosted(StatusBarNotification notification){}
    @Override public void onNotificationRemoved(StatusBarNotification notification){}
    @Override public void onCreate(){
        super.onCreate();instance=this;
        HomeGuardState state=new HomeGuardState(this);preferences=state.preferences;wasEnabled=state.enabled();
        preferences.registerOnSharedPreferenceChangeListener(this);
        window=new HomeGuardRecoveryWindow(new HomeGuardRecoveryWindow.Host(){
            public long now(){return SystemClock.uptimeMillis();}
            public void postAt(Runnable task,long time){handler.postAtTime(task,time);}
            public void remove(Runnable task){handler.removeCallbacks(task);}
            public String state(){
                // No candidate/package/credential-protected state is touched before unlock.
                if("OFF".equals(preferences.getString("mode","AUTO")))return "disabled_or_paused";
                if(!HomeGuardState.unlocked(HomeGuardRevivalListener.this))return "locked";
                if(HomeGuardRecovery.bindingBlocked(HomeGuardRevivalListener.this))return "secure_settings_changed";
                HomeGuardState current=new HomeGuardState(HomeGuardRevivalListener.this);
                if(!current.enabled())return "disabled_or_paused";
                if(!current.recoveryReady())return "invalid_target";
                if(HomeGuardService.connected())return "bound";
                if(HomeGuardUsageService.connected())return "usage_bound";
                if(!HomeGuardRecovery.writeAllowed(HomeGuardRevivalListener.this))return "permission_unavailable";
                return HomeGuardRecovery.bindingAttempt()!=0?"recovering":"ready";
            }
            public void recover(){
                long before=HomeGuardRecovery.bindingAttempt();
                HomeGuardRecovery.request(HomeGuardRevivalListener.this,"通知服务有限恢复");
                if(before==0)ownedAttempt=HomeGuardRecovery.bindingAttempt();
            }
            public void note(String event,int attempt){
                preferences.edit().putString("listenerRecovery",event).putInt("listenerRecoveryAttempt",attempt).apply();
                DiagnosticLog.info("home_guard.listener_recovery","event="+event+" attempt="+attempt+" physicalHomeVerified=false");
            }
            public void stopped(String reason){
                // The final request may finish its existing 5s + settle path. Other stops cancel only our request.
                if("bound".equals(reason))HomeGuardRecovery.finishBindingAttempt(HomeGuardRevivalListener.this,ownedAttempt);
                else if(!"expired".equals(reason))HomeGuardRecovery.cancelBindingAttempt(ownedAttempt);
            }
        });
        window.start();
        if(Build.VERSION.SDK_INT>=24 && !HomeGuardState.unlocked(this)){
            unlock=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){
                if(!Intent.ACTION_USER_UNLOCKED.equals(intent.getAction()))return;
                unregisterReceiver(this);unlock=null;window.unlocked();
            }};
            registerReceiver(unlock,new IntentFilter(Intent.ACTION_USER_UNLOCKED));
            if(HomeGuardState.unlocked(this)){unregisterReceiver(unlock);unlock=null;window.unlocked();}
        }
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences prefs,String key){
        if(!"mode".equals(key) && !"configured".equals(key) && !"home".equals(key) && !"firmware".equals(key)
                && !"observeHome".equals(key) && !"routeRevision".equals(key))return;
        boolean enabled=new HomeGuardState(this,false).enabled();
        if(enabled && !wasEnabled)window.restart();else window.changed();
        wasEnabled=enabled;
    }
    @Override public void onListenerConnected(){
        super.onListenerConnected();connected=true;rebindRequested=false;
        preferences.edit().putBoolean("listenerConnected",true).apply();
        if(HomeGuardState.unlocked(this))HomeGuardRecovery.noteRevival(this);
        window.connected();
    }
    @Override public void onListenerDisconnected(){
        boolean disconnected=connected;connected=false;preferences.edit().putBoolean("listenerConnected",false).apply();
        if(disconnected)window.restart();
        if(Build.VERSION.SDK_INT>=24 && !rebindRequested && HomeGuardState.unlocked(this)
                && new HomeGuardState(this).enabled() && HomeGuardRecovery.notificationAllowed(this)){
            rebindRequested=true;requestRebind(new ComponentName(this,HomeGuardRevivalListener.class));
        }
        super.onListenerDisconnected();
    }
    @Override public void onDestroy(){
        window.destroy();preferences.unregisterOnSharedPreferenceChangeListener(this);
        preferences.edit().putBoolean("listenerConnected",false).apply();
        connected=false;if(instance==this)instance=null;
        if(unlock!=null){unregisterReceiver(unlock);unlock=null;}super.onDestroy();
    }
}
