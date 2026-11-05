package home.quiet.tv;

import android.content.*;
import android.os.*;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Only the system binding is used; notifications are never read. */
@android.annotation.TargetApi(18)
public final class HomeGuardRevivalListener extends NotificationListenerService implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static volatile boolean connected;
    private BroadcastReceiver unlock;
    private boolean rebindRequested,wasEnabled;
    private SharedPreferences preferences;
    static boolean connected(){return connected;}
    // Required on API19 although the legacy profile disables this component.
    @Override public void onNotificationPosted(StatusBarNotification notification){}
    @Override public void onNotificationRemoved(StatusBarNotification notification){}
    @Override public void onCreate(){
        super.onCreate();
        HomeGuardState state=new HomeGuardState(this);preferences=state.preferences;wasEnabled=state.enabled();
        preferences.registerOnSharedPreferenceChangeListener(this);
        HomeGuardRecovery.ensureBinding(this,"通知服务创建");
        if(Build.VERSION.SDK_INT>=24 && !HomeGuardState.unlocked(this)){
            unlock=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){
                if(!Intent.ACTION_USER_UNLOCKED.equals(intent.getAction()))return;
                unregisterReceiver(this);unlock=null;HomeGuardRecovery.ensureBinding(context,"用户解锁");
            }};
            registerReceiver(unlock,new IntentFilter(Intent.ACTION_USER_UNLOCKED));
            if(HomeGuardState.unlocked(this)){unregisterReceiver(unlock);unlock=null;HomeGuardRecovery.ensureBinding(this,"用户解锁");}
        }
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences prefs,String key){
        if(!"mode".equals(key) && !"configured".equals(key) && !"home".equals(key) && !"firmware".equals(key)
                && !"observeHome".equals(key) && !"routeRevision".equals(key))return;
        boolean enabled=new HomeGuardState(this,false).enabled();
        if(enabled && !wasEnabled)HomeGuardRecovery.ensureBinding(this,"用户恢复");else HomeGuardRecovery.bindingChanged();
        wasEnabled=enabled;
    }
    @Override public void onListenerConnected(){
        super.onListenerConnected();connected=true;rebindRequested=false;
        preferences.edit().putBoolean("listenerConnected",true).apply();
        if(HomeGuardState.unlocked(this))HomeGuardRecovery.noteRevival(this);
        HomeGuardRecovery.ensureBinding(this,"通知服务连接");
    }
    @Override public void onListenerDisconnected(){
        boolean disconnected=connected;connected=false;preferences.edit().putBoolean("listenerConnected",false).apply();
        if(disconnected)HomeGuardRecovery.bindingDisconnected(this);
        if(Build.VERSION.SDK_INT>=24 && !rebindRequested && HomeGuardState.unlocked(this)
                && new HomeGuardState(this).enabled() && HomeGuardRecovery.notificationAllowed(this)){
            rebindRequested=true;requestRebind(new ComponentName(this,HomeGuardRevivalListener.class));
        }
        super.onListenerDisconnected();
    }
    @Override public void onDestroy(){
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        preferences.edit().putBoolean("listenerConnected",false).apply();
        connected=false;
        if(unlock!=null){unregisterReceiver(unlock);unlock=null;}super.onDestroy();
    }
}
