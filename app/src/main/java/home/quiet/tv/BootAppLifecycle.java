package home.quiet.tv;

import android.content.*;
import android.os.Build;
import java.lang.ref.WeakReference;

/** Observe real screen transitions while the process lives; never invent a wake on creation. */
final class BootAppLifecycle {
    private static boolean registered,homeEligible;
    private static volatile boolean dreaming;
    static boolean dreaming(){return dreaming;}
    private static WeakReference<HomeActivity> home=new WeakReference<>(null);
    static void observe(Context supplied){
        if(registered || !HomeGuardState.unlocked(supplied))return;
        Context context=supplied.getApplicationContext();
        BroadcastReceiver receiver=new BroadcastReceiver(){
            @Override public void onReceive(Context c,Intent intent){
                BootAppStore store=new BootAppStore(c);String action=intent.getAction();
                if(Intent.ACTION_DREAMING_STARTED.equals(action) || Intent.ACTION_DREAMING_STOPPED.equals(action)){
                    dreaming=Intent.ACTION_DREAMING_STARTED.equals(action);HomeGuardRecovery.cancel();HomeGuardService.dreamChanged();HomeGuardUsageService.dreamChanged();return;
                }
                if(Intent.ACTION_SCREEN_OFF.equals(action)){store.screenOff(homeEligible);return;}
                if(Intent.ACTION_SCREEN_ON.equals(action))store.screenOn();
                HomeActivity activity=home.get();if(activity!=null)activity.checkBootApp();
            }
        };
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_DREAMING_STARTED);filter.addAction(Intent.ACTION_DREAMING_STOPPED);
        if(Build.VERSION.SDK_INT>=24)filter.addAction(Intent.ACTION_USER_UNLOCKED);
        androidx.core.content.ContextCompat.registerReceiver(context,receiver,filter,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        registered=true;
    }
    static void visible(HomeActivity activity){observe(activity);home=new WeakReference<>(activity);homeEligible=true;}
    static void left(HomeActivity activity){
        if(home.get()!=activity)return;
        if(HomeGuardState.interactive(activity)){homeEligible=false;new BootAppStore(activity).cancelWake("left home");}
    }
    static void destroyed(HomeActivity activity){if(home.get()==activity)home.clear();}
    static void foreground(Context context,boolean eligible){
        if(!HomeGuardState.interactive(context))return;
        homeEligible=eligible;
        if(!eligible)new BootAppStore(context).cancelWake("other foreground");
    }
    private BootAppLifecycle(){}
}
