package home.quiet.tv;

import android.content.*;
import android.content.pm.*;
import android.os.SystemClock;
import java.util.*;

/** Evidence only. In particular, detailed reasons never replace the existing install policy. */
final class InstallDiagnostics {
    static String identity(String pkg,String version,long code,String hash,Collection<String> signers){
        String signer=signers==null||signers.isEmpty()?"unknown":signers.iterator().next();
        return "targetPackage="+safe(pkg)+" targetVersion="+safe(version)+" targetCode="+code+" apkSha256="+sha(hash)+" targetSigner="+sha(signer)+" signerCount="+(signers==null?0:signers.size());
    }
    static String identity(ApkPackage apk){return identity(apk.pkg,apk.version,apk.code,apk.hash,apk.signers);}
    static String identity(UpdateManifest.Artifact apk){return identity(apk.pkg,apk.version(),apk.code,apk.sha256,apk.signers);}
    static String safe(String value){return value!=null&&value.matches("[A-Za-z0-9_.-]{1,100}")?value:"unknown";}
    private static String sha(String value){return value!=null&&value.matches("[a-f0-9]{64}")?value:"unknown";}
    static void stage(String source,String attempt,String route,String stage,int session,long started,String identity,String detail){
        DiagnosticLog.info("install.evidence."+stage,"source="+source+" attempt="+safe(attempt)+" route="+route+" stage="+stage+" session="+session
            +" startedElapsedMs="+started+" observedElapsedMs="+SystemClock.elapsedRealtime()+" "+identity+" "+detail);
    }
    static String result(int status,int legacy,String message){
        return "statusNamespace=package_installer status="+number(status)+" legacyStatus="+number(legacy)+" platformReason="+UpdateInstallPolicy.platformReason(legacy,message)
            +" systemOutcome="+(status==0?"reported_success_unverified":status==-1?"pending_confirmation":status==Integer.MIN_VALUE?"unknown":UpdateInstallPolicy.result(status,legacy,message));
    }
    static String number(int value){return value==Integer.MIN_VALUE?"unknown":Integer.toString(value);}
    @SuppressWarnings("deprecation")
    static void readback(Context context,String source,String attempt,String route,String pkg,String version,long code,Collection<String> signers,long baseline,Boolean originalCheck){
        observe(context,source,attempt,route,pkg,version,code,signers,baseline,originalCheck,"readback");
    }
    static void baseline(Context context,String source,String attempt,String route,String pkg,String version,long code,Collection<String> signers,long baseline){
        observe(context,source,attempt,route,pkg,version,code,signers,baseline,null,"baseline");
    }
    @SuppressWarnings("deprecation")
    private static void observe(Context context,String source,String attempt,String route,String pkg,String version,long code,Collection<String> signers,long baseline,Boolean originalCheck,String phase){
        String detail;
        try{
            PackageInfo info=context.getPackageManager().getPackageInfo(pkg,PackageManager.GET_SIGNATURES);Set<String> actual=new HashSet<>();
            if(info.signatures!=null)for(Signature signature:info.signatures)actual.add(DiagnosticSession.hash(signature.toByteArray()));
            detail="readback=observed installed=true observedVersion="+safe(info.versionName)+" observedCode="+UpdateInstaller.code(info)+" observedUpdateTimeMs="+info.lastUpdateTime
                +" versionMatches="+(code==UpdateInstaller.code(info)&&version.equals(info.versionName))+" signerMatches="+(signers==null?"unknown":new HashSet<>(signers).equals(actual))+" updatedAfterBaseline="+(baseline<0?"unknown":info.lastUpdateTime>baseline);
        }catch(PackageManager.NameNotFoundException absent){detail="readback=observed installed=false";}
        catch(RuntimeException unavailable){detail="readback=unknown type="+unavailable.getClass().getSimpleName();}
        DiagnosticLog.info("install.evidence."+phase,"source="+source+" attempt="+safe(attempt)+" route="+route+" targetPackage="+safe(pkg)+" targetCode="+code+" existingCheck="+(originalCheck==null?"not_run":originalCheck)+" "+detail);
    }
    private InstallDiagnostics(){}
}
