package home.quiet.tv;

import android.app.*;
import android.app.usage.UsageEvents;
import android.content.*;
import android.os.*;

/** Fire OS 6 alternative observer when the firmware refuses to bind accessibility. */
public final class HomeGuardUsageService extends Service {
    private static volatile HomeGuardUsageService instance;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final HomeGuardPolicy policy=new HomeGuardPolicy();
    private boolean allowOriginal;
    static boolean eligible(Context c){HomeGuardState state=new HomeGuardState(c,false);return !BuildConfig.LEGACY_19
            && HomeGuardPolicy.fireHome(Build.VERSION.SDK_INT,state.selected()) && state.ready() && HomeGuardService.usageAllowed(c);}
    static boolean connected(){return instance!=null;}
    static void ensure(Context c){
        if(!eligible(c) || HomeGuardService.connected())return;
        try{c.startService(new Intent(c,HomeGuardUsageService.class));}
        catch(RuntimeException refused){new HomeGuardState(c).status("前台检测服务未启动，请重新配置");}
    }
    static void pause(Context c){if(Build.VERSION.SDK_INT==25)c.stopService(new Intent(c,HomeGuardUsageService.class));}
    static void allowOriginal(){if(instance!=null)instance.allowOriginal=true;}
    static void visible(){if(instance!=null){instance.allowOriginal=false;instance.policy.ignoreUsageThrough(System.currentTimeMillis());}}
    static void dreamChanged(){if(instance!=null)instance.policy.ignoreUsageThrough(System.currentTimeMillis());}
    private final Runnable poll=new Runnable(){public void run(){
        if(Build.VERSION.SDK_INT<21){stopSelf();return;}
        if(!eligible(HomeGuardUsageService.this) || HomeGuardService.connected()){stopSelf();return;}
        handler.postDelayed(this,HomeGuardPolicy.USAGE_POLL_MS);
        if(!HomeGuardState.canShow(HomeGuardUsageService.this) || HomeGuardService.homeResumed())return;
        HomeGuardState state=new HomeGuardState(HomeGuardUsageService.this,false);
        UsageEvents.Event event=HomeGuardService.latestUsage(HomeGuardUsageService.this,HomeGuardPolicy.USAGE_LOOKBACK_MS);
        if(event==null)return;
        ComponentName selected=ComponentName.unflattenFromString(state.selected());
        if(allowOriginal && selected!=null && !selected.getPackageName().equals(event.getPackageName()) && !getPackageName().equals(event.getPackageName()))allowOriginal=false;
        if(!allowOriginal && policy.consumeUsage(state.eventHome(),event.getPackageName(),event.getClassName(),event.getTimeStamp(),System.currentTimeMillis()))
            HomeGuardRecovery.launch(HomeGuardUsageService.this,"Fire OS 原主页事件");
    }};
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(Build.VERSION.SDK_INT<25 || Build.VERSION.SDK_INT>25 || !eligible(this) || HomeGuardService.connected()){stopSelf();return START_NOT_STICKY;}
        BootAppLifecycle.observe(this);
        PendingIntent settings=PendingIntent.getActivity(this,0,new Intent(this,MaintenanceHelpActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this).setSmallIcon(R.drawable.ic_manage_shield)
                .setContentTitle("Quiet TV 桌面中转").setContentText("点击查看帮助与恢复").setContentIntent(settings).setOngoing(true).build();
        startForeground(2601,notification);instance=this;handler.removeCallbacks(poll);handler.post(poll);
        new HomeGuardState(this).status("前台检测已连接，等待原主页");return START_NOT_STICKY;
    }
    @Override public void onDestroy(){handler.removeCallbacks(poll);if(instance==this)instance=null;stopForeground(true);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
