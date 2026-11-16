package home.quiet.tv;

import android.app.*;
import android.content.Intent;
import android.os.*;

/** User-started remote node. No boot receiver or sticky restart. */
@androidx.annotation.RequiresApi(26)
public final class TvRemoteService extends Service {
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||!"START".equals(intent.getAction())){TvRemoteController.stop();stopSelf();return START_NOT_STICKY;}
        if(Build.VERSION.SDK_INT<26||!home.quiet.components.ComponentRepository.get(this).reason("tailscale").isEmpty()){stopSelf();return START_NOT_STICKY;}
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);manager.createNotificationChannel(new NotificationChannel("remote-node","远程访问",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,91,new Intent(this,AppManagementActivity.class).putExtra("management-page",SettingsPages.TOOLS),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pause=PendingIntent.getService(this,92,new Intent(this,TvRemoteService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new Notification.Builder(this,"remote-node").setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("QuietTV 远程访问").setContentText("节点运行中，可随时暂停").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"暂停",pause).build()).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(6201,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);else startForeground(6201,notification);
        TvRemoteController.start(this,()->stopSelf());return START_NOT_STICKY;
    }
    @Override public void onDestroy(){TvRemoteController.stop();super.onDestroy();}
}
