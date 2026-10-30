package home.quiet.tv;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Build;
import androidx.core.content.FileProvider;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.zip.ZipFile;

final class UpdateInstaller {
    static UpdateManifest.Device device(Context context)throws IOException{
        try{
            PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),flags());
            List<String> abis=Build.VERSION.SDK_INT>=21?Arrays.asList(Build.SUPPORTED_ABIS):Arrays.asList(Build.CPU_ABI,Build.CPU_ABI2);
            return new UpdateManifest.Device(context.getPackageName(),info.versionName,code(info),BuildConfig.UPDATE_CHANNEL,
                    BuildConfig.UPDATE_COMPATIBILITY,BuildConfig.DECLARES_HOME?"standard":"relay",BuildConfig.UPDATE_ABI,Build.VERSION.SDK_INT,abis,signers(info));
        }catch(PackageManager.NameNotFoundException e){throw new IOException("Installed application unavailable",e);}
    }
    @SuppressWarnings("deprecation")
    private static int flags(){return Build.VERSION.SDK_INT>=28?(PackageManager.GET_SIGNING_CERTIFICATES|PackageManager.GET_SIGNATURES):PackageManager.GET_SIGNATURES;}
    @SuppressWarnings("deprecation")
    static long code(PackageInfo info){return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}
    @SuppressWarnings("deprecation")
    private static List<String> signers(PackageInfo info)throws IOException{
        android.content.pm.Signature[] signatures=Build.VERSION.SDK_INT>=28&&info.signingInfo!=null?info.signingInfo.getApkContentsSigners():info.signatures;
        if(signatures==null||signatures.length==0)signatures=info.signatures;
        if(signatures==null||signatures.length==0)throw new IOException("APK signer unavailable");
        List<String> result=new ArrayList<>();try{for(android.content.pm.Signature signature:signatures)result.add(UpdateTransfer.hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));}
        catch(NoSuchAlgorithmException e){throw new IOException(e);}return result;
    }
    static void validate(Context context,UpdateManifest.Artifact artifact,File file)throws IOException{
        UpdateTransfer.verify(file,artifact.bytes,artifact.sha256);
        UpdateManifest.Device device=device(context);if(!artifact.matches(device))throw new IOException("Update no longer matches installed application");
        PackageInfo info=context.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),flags());
        if(info==null||!artifact.pkg.equals(info.packageName)||artifact.code!=code(info)||!artifact.version().equals(info.versionName)
                ||!new HashSet<>(signers(info)).equals(new HashSet<>(artifact.signers)))throw new IOException("APK metadata or signer mismatch");
        if(Build.VERSION.SDK_INT>=24&&info.applicationInfo!=null&&info.applicationInfo.minSdkVersion!=artifact.minSdk)throw new IOException("APK SDK mismatch");
        Set<String> abis=new HashSet<>();try(ZipFile zip=new ZipFile(file)){
            Enumeration<? extends java.util.zip.ZipEntry> entries=zip.entries();while(entries.hasMoreElements()){String path=entries.nextElement().getName();if(path.startsWith("lib/")&&path.endsWith(".so")){String[] fields=path.split("/");if(fields.length>=3)abis.add(fields[1]);}}
        }
        if(!abis.equals(new HashSet<>(artifact.abis)))throw new IOException("APK ABI mismatch");
    }
    static boolean permitted(Activity activity){try{return Build.VERSION.SDK_INT<26||activity.getPackageManager().canRequestPackageInstalls();}catch(RuntimeException failure){DiagnosticLog.error("update.permission.failed",failure);return false;}}
    static File prepare(Context context,UpdateManifest.Artifact artifact,File source)throws IOException{
        if(Build.VERSION.SDK_INT>=24)return source;
        // Android 4.0-6 installers accept file://, but cannot read our private files directory.
        // API14-18 request storage access in the legacy manifest; API19+ need no permission here.
        try{return prepareExternal(context,artifact,source,true);}
        catch(UpdateInstallPreparation.Failure failure){
            if(!"directory_unavailable".equals(failure.reason))throw failure;
            // Some old TV firmwares do not expose an external cache. The app-specific
            // files directory is also installer-readable without broad storage access.
            DiagnosticLog.info("update.install.storage_fallback",failure.details());
            return prepareExternal(context,artifact,source,false);
        }
    }
    private static File prepareExternal(Context context,UpdateManifest.Artifact artifact,File source,boolean cache)throws UpdateInstallPreparation.Failure{
        String location=cache?"external_cache":"external_files";File root;
        try{root=cache?context.getExternalCacheDir():context.getExternalFilesDir(null);}
        catch(RuntimeException failure){throw new UpdateInstallPreparation.Failure("directory_unavailable","directory",location,artifact.bytes,-1,failure);}
        UpdateInstallPreparation.Prepared prepared=UpdateInstallPreparation.prepare(root,location,source,artifact.bytes,artifact.sha256);
        DiagnosticLog.info("update.install.storage_ready","location="+location+" requiredBytes="+artifact.bytes+" availableBytes="+prepared.availableBytes+" reused="+prepared.reused);
        return prepared.file;
    }
    static boolean launch(Activity activity,File file){
        try{
            boolean legacy=Build.VERSION.SDK_INT<24;
            Uri uri=legacy?Uri.fromFile(file):FileProvider.getUriForFile(activity,activity.getPackageName()+".updates",file);
            // ACTION_VIEW always delegates confirmation to the platform installer. No unattended sessions.
            Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive");
            if(!legacy){intent.setClipData(ClipData.newRawUri("Quiet TV update",uri));intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);}
            return ExternalActivityLauncher.open(activity,"update.install",intent);
        }catch(RuntimeException e){DiagnosticLog.error("update.install.failed",e);return false;}
    }
}
