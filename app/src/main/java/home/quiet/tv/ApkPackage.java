package home.quiet.tv;

import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.*;

/** Metadata for a user-selected single APK; never relaxes the signed self-update policy. */
final class ApkPackage {
    static final class Invalid extends IOException {Invalid(String message){super(message);}}
    final String label,pkg,version,hash;
    final long code,bytes,previousUpdate;
    final boolean update;
    final Set<String> signers;
    private ApkPackage(String label,PackageInfo info,String hash,long bytes,PackageInfo installed)throws IOException{
        this.label=label;pkg=info.packageName;version=info.versionName==null?"未知版本":info.versionName;
        code=UpdateInstaller.code(info);this.hash=hash;this.bytes=bytes;update=installed!=null;
        previousUpdate=installed==null?-1:installed.lastUpdateTime;signers=signers(info);
    }
    @SuppressWarnings("deprecation")
    private static int flags(){return Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES|PackageManager.GET_SIGNATURES:PackageManager.GET_SIGNATURES;}
    @SuppressWarnings("deprecation")
    private static Set<String> signers(PackageInfo info)throws IOException{
        Signature[] values=Build.VERSION.SDK_INT>=28&&info.signingInfo!=null?info.signingInfo.getApkContentsSigners():info.signatures;
        if(values==null||values.length==0)throw new Invalid("无法读取 APK 签名");
        Set<String> result=new HashSet<>();for(Signature value:values)result.add(digest(value.toByteArray()));return result;
    }
    private static String digest(byte[] value)throws IOException{
        try{return UpdateTransfer.hex(MessageDigest.getInstance("SHA-256").digest(value));}catch(NoSuchAlgorithmException error){throw new Invalid("无法校验安装包");}
    }
    static String hash(File file)throws IOException{
        try(InputStream in=new FileInputStream(file)){
            MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[32768];int count;
            while((count=in.read(buffer))!=-1)digest.update(buffer,0,count);return UpdateTransfer.hex(digest.digest());
        }catch(NoSuchAlgorithmException error){throw new Invalid("无法校验安装包");}
    }
    static ApkPackage inspect(Context context,File file,String name)throws IOException{
        if(!FileTransferPolicy.apk(name))throw new Invalid("请选择普通 APK 安装包");
        PackageManager pm=context.getPackageManager();PackageInfo info=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags());
        if(info==null||info.applicationInfo==null)throw new Invalid("无法解析 APK，请检查文件是否完整及系统是否兼容");
        if(Build.VERSION.SDK_INT>=24&&info.applicationInfo.minSdkVersion>Build.VERSION.SDK_INT)throw new Invalid("安装包要求更高的安卓版本");
        if(Build.VERSION.SDK_INT>=26&&info.applicationInfo.splitNames!=null&&info.applicationInfo.splitNames.length>0)throw new Invalid("暂不支持分包，请使用完整单 APK");
        Set<String> abis=new HashSet<>();try(ZipFile zip=new ZipFile(file)){
            Enumeration<? extends ZipEntry> entries=zip.entries();while(entries.hasMoreElements()){
                String path=entries.nextElement().getName();if(path.startsWith("lib/")&&path.endsWith(".so")){String[] parts=path.split("/");if(parts.length>=3)abis.add(parts[1]);}
            }
        }
        List<String> supported=Build.VERSION.SDK_INT>=21?Arrays.asList(Build.SUPPORTED_ABIS):Arrays.asList(Build.CPU_ABI,Build.CPU_ABI2);
        if(!abis.isEmpty()&&Collections.disjoint(abis,supported))throw new Invalid("安装包与电视支持的处理器架构不兼容");
        PackageInfo installed=installed(pm,info.packageName);
        if(installed!=null){
            if(UpdateInstaller.code(info)<UpdateInstaller.code(installed))throw new Invalid("安装包版本低于已安装版本，无法覆盖安装");
            Set<String> incoming=signers(info),existing=signers(installed),allowed=new HashSet<>(incoming);
            if(Build.VERSION.SDK_INT>=28&&info.signingInfo!=null&&!info.signingInfo.hasMultipleSigners()){
                Signature[] history=info.signingInfo.getSigningCertificateHistory();if(history!=null)for(Signature signer:history)allowed.add(digest(signer.toByteArray()));
            }
            if(!incoming.equals(existing)&&!(incoming.size()==1&&existing.size()==1&&allowed.containsAll(existing)))throw new Invalid("安装包与已安装应用签名不同，无法覆盖安装");
        }
        info.applicationInfo.sourceDir=file.getAbsolutePath();info.applicationInfo.publicSourceDir=file.getAbsolutePath();
        String label=String.valueOf(info.applicationInfo.loadLabel(pm));
        return new ApkPackage(label,info,hash(file),file.length(),installed);
    }
    private static PackageInfo installed(PackageManager pm,String pkg){
        try{return pm.getPackageInfo(pkg,flags());}catch(PackageManager.NameNotFoundException absent){return null;}
    }
    boolean installed(Context context,long baseline){
        try{PackageInfo now=installed(context.getPackageManager(),pkg);return now!=null&&UpdateInstaller.code(now)==code&&signers(now).equals(signers)&&now.lastUpdateTime>baseline;}
        catch(IOException|RuntimeException unavailable){return false;}
    }
    File prepare(Context context,File source)throws IOException{
        UpdateTransfer.verify(source,bytes,hash);
        if(Build.VERSION.SDK_INT>=24)return source;
        try{return UpdateInstallPreparation.prepare(root(context,true),"external_cache",source,bytes,hash).file;}
        catch(UpdateInstallPreparation.Failure error){if(!"directory_unavailable".equals(error.reason))throw error;return UpdateInstallPreparation.prepare(root(context,false),"external_files",source,bytes,hash).file;}
    }
    private File root(Context context,boolean cache){
        try{File root=cache?context.getExternalCacheDir():context.getExternalFilesDir(null);return root==null?null:new File(root,"apk-install");}catch(RuntimeException error){return null;}
    }
}
