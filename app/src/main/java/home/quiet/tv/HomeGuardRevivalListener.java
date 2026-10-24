package home.quiet.tv;

import android.content.*;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Only the system binding is used; notifications are never read. */
public final class HomeGuardRevivalListener extends NotificationListenerService {
    private static volatile boolean connected;
    private BroadcastReceiver unlock;
    private boolean rebindRequested;
    static boolean connected(){return connected;}
    // Required on API19 although the legacy profile disables this component.
    @Override public void onNotificationPosted(StatusBarNotification notification){}
    @Override public void onNotificationRemoved(StatusBarNotification notification){}
    @Override public void onListenerConnected(){
        super.onListenerConnected();connected=true;rebindRequested=false;new HomeGuardState(this).preferences.edit().putBoolean("listenerConnected",true).apply();
        HomeGuardRecovery.noteRevival(this);
        if(HomeGuardState.unlocked(this)){HomeGuardRecovery.request(this,"通知监听连接");return;}
        if(Build.VERSION.SDK_INT>=24 && unlock==null){
            unlock=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){
                if(!Intent.ACTION_USER_UNLOCKED.equals(intent.getAction()))return;
                unregisterReceiver(this);unlock=null;HomeGuardRecovery.request(context,"用户解锁");
            }};
            registerReceiver(unlock,new IntentFilter(Intent.ACTION_USER_UNLOCKED));
            if(HomeGuardState.unlocked(this)){unregisterReceiver(unlock);unlock=null;HomeGuardRecovery.request(this,"用户解锁");}
        }
    }
    @Override public void onListenerDisconnected(){
        connected=false;new HomeGuardState(this).preferences.edit().putBoolean("listenerConnected",false).apply();
        if(Build.VERSION.SDK_INT>=24 && !rebindRequested && new HomeGuardState(this).enabled() && HomeGuardRecovery.notificationAllowed(this)){
            rebindRequested=true;requestRebind(new ComponentName(this,HomeGuardRevivalListener.class));
        }
        super.onListenerDisconnected();
    }
    @Override public void onDestroy(){connected=false;if(unlock!=null){unregisterReceiver(unlock);unlock=null;}super.onDestroy();}
}
