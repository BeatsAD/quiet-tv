package home.quiet.tv;

import android.annotation.TargetApi;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.*;
import android.content.pm.PackageInstaller;
import android.os.StatFs;
import java.io.*;

/** Loaded only on API21-23. The caller records and releases any session reported by Failure. */
@TargetApi(21)
final class UpdateSessionInstaller {
    interface Recorder {boolean save(int session);}
    static final class Failure extends IOException {
        final String reason,step;final int session;final long available;
        Failure(String reason,String step,int session,long available,Throwable cause){super("Update session failed",cause);this.reason=reason;this.step=step;this.session=session;this.available=available;}
        String details(){return "route=session reason="+reason+" step="+step+" session="+session+" location=internal availableBytes="+available;}
    }
    static int prepare(Context context,UpdateManifest.Artifact artifact,File source,Recorder recorder)throws Failure{
        int id=-1;String step="create";long available=available(context);
        try{
            PackageInstaller installer=context.getPackageManager().getPackageInstaller();
            if(installer==null)throw new UnsupportedOperationException();
            PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(artifact.pkg);params.setSize(artifact.bytes);
            id=installer.createSession(params);
            if(!recorder.save(id))throw new IOException("Session record unavailable");
            DiagnosticLog.info("update.install.session","step=create session="+id+" requiredBytes="+artifact.bytes+" location=internal availableBytes="+available);
            step="write";
            try(PackageInstaller.Session session=installer.openSession(id)){
                try(InputStream input=new FileInputStream(source);OutputStream output=session.openWrite("base.apk",0,artifact.bytes)){
                    UpdateInstallPolicy.copy(input,output,artifact.bytes,artifact.sha256);
                    step="fsync";session.fsync(output);
                }
            }
            return id;
        }catch(IOException|RuntimeException error){
            String reason=error instanceof UnsupportedOperationException?"session_unsupported":error instanceof UpdateTransfer.IntegrityException?"invalid":UpdateInstallPolicy.ioReason(error);
            throw new Failure(reason,step,id,available,error);
        }
    }
    @SuppressLint("UnspecifiedImmutableFlag") // API21-23 callbacks must allow the system to fill result extras.
    static void commit(Context context,int id)throws Failure{
        try(PackageInstaller.Session session=context.getPackageManager().getPackageInstaller().openSession(id)){
            Intent callback=new Intent(context,UpdateInstallReceiver.class).setAction(action(context,id));
            PendingIntent pending=PendingIntent.getBroadcast(context,id,callback,PendingIntent.FLAG_UPDATE_CURRENT);
            session.commit(pending.getIntentSender());
        }catch(IOException|RuntimeException error){throw new Failure(error instanceof UnsupportedOperationException?"session_unsupported":UpdateInstallPolicy.ioReason(error),"commit",id,available(context),error);}
    }
    static String action(Context context,int id){return context.getPackageName()+".UPDATE_INSTALL."+id;}
    static boolean abandon(Context context,int id){
        if(id<0)return true;
        try{
            PackageInstaller installer=context.getPackageManager().getPackageInstaller();
            if(installer.getSessionInfo(id)!=null)installer.abandonSession(id);
            DiagnosticLog.info("update.install.session","step=abandon session="+id);return true;
        }catch(RuntimeException error){DiagnosticLog.error("update.install.abandon_failed","session="+id,error);return false;}
    }
    private static long available(Context context){try{return new StatFs(context.getFilesDir().getAbsolutePath()).getAvailableBytes();}catch(RuntimeException error){return -1;}}
}
