package home.quiet.tv;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;

/** The computer script's observation loop, running on the TV's verified shared ADB queue. */
@android.annotation.TargetApi(19)
public final class LegacyAdbHomeGuardService extends Service {
    private static volatile LegacyAdbHomeGuardService instance;
    private static volatile String detail="尚未启动";
    private static volatile long lastVerifiedAt;
    private final AdbHomeGuardPolicy policy=new AdbHomeGuardPolicy();
    private ManagementAdb connection;
    private volatile boolean active;
    private ScheduledFuture<?> poll;
    static boolean running(){return instance!=null && instance.active;}
    static String status(){return detail;}
    static long lastVerifiedAt(){return lastVerifiedAt;}
    private static void status(String value){if(!value.equals(detail)){detail=value;DiagnosticLog.info("adb_home_guard.status",value);}}
    @android.annotation.SuppressLint("UnspecifiedImmutableFlag") // This service only runs on API19.
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(!LegacyAdbHomeGuard.enabled(this)){stopSelf();return START_NOT_STICKY;}
        if(active)return START_STICKY;
        Notification notification=new Notification.Builder(this).setSmallIcon(R.drawable.ic_manage_shield)
                .setContentTitle("Quiet TV 桌面守护").setContentText("点击查看状态或暂停接管")
                .setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MaintenanceHelpActivity.class),PendingIntent.FLAG_UPDATE_CURRENT))
                .setOngoing(true).build();
        startForeground(2602,notification);active=true;instance=this;
        BootAppLifecycle.observe(this);connection=ManagementAdb.get(this);connection.request();
        poll=connection.worker.scheduleWithFixedDelay(this::check,0,5,TimeUnit.SECONDS);
        status("等待本机 ADB 连接，实体场景尚未验收");return START_STICKY;
    }
    private void check(){
        if(!active)return;
        if(!LegacyAdbHomeGuard.enabled(this)){stopSelf();return;}
        if(!HomeGuardState.canShow(this))return;
        if(!connection.adb.connected()){status("等待本机 ADB 授权或端口恢复");return;}
        try{
            AdbController adb=connection.adb;
            if(!"1".equals(adb.shell("getprop sys.boot_completed").trim()))return;
            int user=android.os.Process.myUid()/100000;
            String home=LegacyAdbHomeGuard.original(this);
            String front=AdbHomeGuardPolicy.foreground(adb.diagnosticShell("dumpsys activity activities"),user);
            if(front.isEmpty()){status("前台状态未确认，未跳转");return;}
            if(!home.equals(front)){policy.take(home,front);status("守护运行中，实体场景尚未验收");return;}
            if(!home.equals(AdbHomeGuardPolicy.focused(adb.diagnosticShell("dumpsys window windows"),user)))return;
            if(!policy.take(home,front))return;
            adb.validateUser();
            // Recheck after the user/connection query so pause, sleep and app changes do not replay a write.
            if(!active || !LegacyAdbHomeGuard.enabled(this) || !HomeGuardState.canShow(this)
                    || !LegacyAdbHomeGuard.usable(this,home))return;
            front=AdbHomeGuardPolicy.foreground(adb.diagnosticShell("dumpsys activity activities"),user);
            if(!home.equals(front) || !home.equals(AdbHomeGuardPolicy.focused(adb.diagnosticShell("dumpsys window windows"),user)))return;
            String own=getPackageName()+"/home.quiet.tv.HomeActivity";
            String result=adb.shell("am start --user "+user+" -W -n "+own);
            String after=AdbHomeGuardPolicy.foreground(adb.diagnosticShell("dumpsys activity activities"),user);
            boolean verified=result.contains("Status: ok") && own.equals(after)
                    && own.equals(AdbHomeGuardPolicy.focused(adb.diagnosticShell("dumpsys window windows"),user));
            policy.take(home,after);
            if(verified)lastVerifiedAt=System.currentTimeMillis();
            DiagnosticLog.info("adb_home_guard.launch","result="+(verified?"foreground_verified":"unconfirmed"));
            status(verified?"已核对 Quiet TV 前台，实体 HOME 与开机待验收":"启动结果未确认，本次原桌面事件不重复启动");
        }catch(Exception failure){
            status("守护检查失败，未自动重放启动");DiagnosticLog.error("adb_home_guard.check",failure);
            connection.request();
        }
    }
    @Override public void onDestroy(){
        active=false;if(poll!=null)poll.cancel(false);if(instance==this)instance=null;
        status("守护已停止");stopForeground(true);super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
