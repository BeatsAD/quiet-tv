package home.quiet.tv;

import android.annotation.*;
import android.app.PendingIntent;
import android.content.*;
import android.content.pm.PackageInstaller;
import java.io.*;
import java.util.UUID;

/** API21-23 only. Results belong to this file and attempt; foreground UI owns confirmation. */
@TargetApi(21)
final class ApkInstallSession {
    static Intent confirmation;
    static Runnable changed;
    static SharedPreferences prefs(Context context){return context.getSharedPreferences("received-apk-install",0);}
    static int id(Context context){return prefs(context).getInt("session",-1);}
    static int status(Context context){return prefs(context).getInt("status",ApkInstallPhase.PREPARING.status);}
    static ApkInstallPhase phase(Context context){return ApkInstallPhase.read(status(context));}
    static boolean active(Context context){
        int id=id(context);if(id<0||status(context)>=0)return false;
        try{PackageInstaller.SessionInfo info=context.getPackageManager().getPackageInstaller().getSessionInfo(id);return info!=null&&info.isActive();}
        catch(RuntimeException unavailable){return true;}
    }
    static boolean owns(Context context,String file){return file.equals(prefs(context).getString("file",""))&&id(context)>=0;}
    static void abandon(Context context)throws IOException{
        int id=id(context);if(id>=0)try{PackageInstaller installer=context.getPackageManager().getPackageInstaller();if(installer.getSessionInfo(id)!=null)installer.abandonSession(id);}
        catch(RuntimeException error){throw new IOException("上次安装任务无法结束，请稍后再试");}
        prefs(context).edit().clear().commit();confirmation=null;
    }
    @SuppressLint("UnspecifiedImmutableFlag") // This class is only loaded on API21-23.
    static void start(Context context,String file,ApkPackage apk,File source,FileTransferPolicy.Active active)throws IOException{
        start(context,file,apk,source,active,UUID.randomUUID().toString(),android.os.SystemClock.elapsedRealtime());
    }
    @SuppressLint("UnspecifiedImmutableFlag")
    static void start(Context context,String file,ApkPackage apk,File source,FileTransferPolicy.Active active,String attempt,long started)throws IOException{
        String identity=InstallDiagnostics.identity(apk);
        InstallDiagnostics.stage("received_apk",attempt,"session","begin",-1,started,identity,"");
        abandon(context);PackageInstaller installer=context.getPackageManager().getPackageInstaller();
        if(installer==null)throw new UnsupportedOperationException();
        PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(apk.pkg);params.setSize(apk.bytes);
        int id=installer.createSession(params);String action=context.getPackageName()+".RECEIVED_APK."+UUID.randomUUID();
        if(!prefs(context).edit().putInt("session",id).putString("file",file).putString("action",action).putInt("status",ApkInstallPhase.PREPARING.status).putLong("baseline",apk.previousUpdate).putString("hash",apk.hash)
                .putString("diagnosticAttempt",attempt).putString("diagnosticIdentity",identity).putLong("diagnosticStarted",started).commit()){
            installer.abandonSession(id);throw new IOException("无法保存安装状态");
        }
        try(PackageInstaller.Session session=installer.openSession(id)){
            InstallDiagnostics.stage("received_apk",attempt,"session","created",id,started,identity,"");
            try(InputStream input=new FileInputStream(source);OutputStream output=session.openWrite("base.apk",0,apk.bytes)){
                UpdateInstallPolicy.copy(input,output,apk.bytes,apk.hash);session.fsync(output);
                InstallDiagnostics.stage("received_apk",attempt,"session","written",id,started,identity,"fsync=true");
            }
            if(!active.valid())throw new InterruptedIOException();
            Intent callback=new Intent(context,ApkInstallReceiver.class).setAction(action);
            PendingIntent pending=PendingIntent.getBroadcast(context,id,callback,PendingIntent.FLAG_UPDATE_CURRENT);
            if(!prefs(context).edit().putInt("status",ApkInstallPhase.SUBMITTED.status).commit())throw new IOException("无法保存安装状态");
            session.commit(pending.getIntentSender());
            InstallDiagnostics.stage("received_apk",attempt,"session","submitted",id,started,identity,"");
        }catch(IOException|RuntimeException failure){InstallDiagnostics.stage("received_apk",attempt,"session","failed",id,started,identity,"type="+failure.getClass().getSimpleName());abandon(context);throw failure;}
    }
    static void result(Context context,Intent intent){
        SharedPreferences record=prefs(context);int id=id(context);
        if(id<0||intent==null||!record.getString("action","").equals(intent.getAction())||intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,id)!=id||status(context)>=0)return;
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,Integer.MIN_VALUE);if(status==Integer.MIN_VALUE)return;
        confirmation=status==PackageInstaller.STATUS_PENDING_USER_ACTION?intent.getParcelableExtra(Intent.EXTRA_INTENT):null;
        record.edit().putInt("status",status).putString("reason",UpdateInstallPolicy.result(status,intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))).apply();
        DiagnosticLog.info("received_apk.result","session="+id+" status="+status);
        int legacy=intent.getIntExtra("android.content.pm.extra.LEGACY_STATUS",Integer.MIN_VALUE);
        InstallDiagnostics.stage("received_apk",record.getString("diagnosticAttempt","unknown"),"session","result",id,record.getLong("diagnosticStarted",-1),record.getString("diagnosticIdentity","identity=unknown"),InstallDiagnostics.result(status,legacy,intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)));
        if(changed!=null)changed.run();
    }
    private ApkInstallSession(){}
}
