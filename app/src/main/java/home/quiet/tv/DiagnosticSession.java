package home.quiet.tv;

import android.content.*;
import android.content.pm.*;
import android.os.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Stable identity within a process and an anonymous boot, not a persistent device identifier. */
final class DiagnosticSession {
    static final int SCHEMA=2;
    final String id=UUID.randomUUID().toString().replace("-","").substring(0,16),boot,sourceId;
    final long startedAt=System.currentTimeMillis(),startedElapsed=SystemClock.elapsedRealtime();
    private final JSONObject build;
    private final String identitySalt;
    private final Context context;
    DiagnosticSession(Context context){
        this(context,true);
    }
    DiagnosticSession(Context context,boolean persistSalt){
        this.context=context.getApplicationContext();
        String bootIdentity="unknown",savedSalt=UUID.randomUUID().toString();
        try{
            Context storage=Build.VERSION.SDK_INT>=24?Api24.storage(context):context;
            SharedPreferences prefs=storage.getSharedPreferences("diagnostic-session",0);String salt=prefs.getString("salt","");
            if(salt.isEmpty()){salt=UUID.randomUUID().toString();if(persistSalt)prefs.edit().putString("salt",salt).apply();}
            savedSalt=salt;
            String cycle=BootAppStore.bootId(context);if(!cycle.isEmpty())bootIdentity=hash((salt+":"+cycle).getBytes(Charsets.UTF_8)).substring(0,24);
        }catch(RuntimeException unavailable){}
        boot=bootIdentity;
        identitySalt=savedSalt;
        JSONObject info=new JSONObject();
        try(InputStream input=context.getAssets().open("diagnostic-build.json")){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int count;
            while((count=input.read(buffer))!=-1){if(bytes.size()+count>4096)throw new IOException("Build identity too large");bytes.write(buffer,0,count);}
            info=new JSONObject(bytes.toString("UTF-8"));
        }catch(IOException|JSONException unavailable){}
        build=info;String sha=info.optString("sourceSha256");sourceId=sha.matches("[a-f0-9]{64}")?sha.substring(0,24):"unknown";
    }
    String anonymous(String scope,String value){return scope+"-"+hash((identitySalt+":"+scope+":"+value).getBytes(Charsets.UTF_8)).substring(0,20);}
    String forwardedOrigin(){return "originRole=wallpaper originSession="+id+" originPid="+android.os.Process.myPid()+" originBuild="+BuildConfig.VERSION_CODE+" originSource="+sourceId
        +" originWallMs="+System.currentTimeMillis()+" originElapsedMs="+SystemClock.elapsedRealtime();}
    String envelope(String detail,long elapsed){
        String prefix="diagSession="+id+" diagBoot="+boot+" diagBuild="+BuildConfig.VERSION_CODE+" diagSource="+sourceId+" diagElapsed="+elapsed+" diagPid="+android.os.Process.myPid()+" ";
        // Reserve room for the journal timestamp, level and bounded event name before clean().
        int remaining=850-prefix.length();String value=detail==null?"":detail;
        if(value.length()>remaining)value=value.substring(0,Math.max(0,remaining-22))+" detailTruncated=true";
        return prefix+value;
    }
    JSONObject json()throws JSONException{
        JSONObject value=new JSONObject().put("schemaVersion",SCHEMA).put("processSession",id).put("bootSession",boot).put("role","main")
            .put("pid",android.os.Process.myPid()).put("startedAtMs",startedAt).put("startedElapsedMs",startedElapsed)
            .put("versionName",BuildConfig.VERSION_NAME).put("versionCode",BuildConfig.VERSION_CODE).put("package",context.getPackageName())
            .put("compatibility",BuildConfig.UPDATE_COMPATIBILITY).put("abi",BuildConfig.UPDATE_ABI).put("buildType",BuildConfig.BUILD_TYPE)
            .put("homeMode",BuildConfig.DECLARES_HOME?"standard":"relay").put("source",build)
            .put("capabilities",new JSONArray().put("event-session-v2").put("incident-window-v1").put("coverage-v1").put("install-evidence-v1").put("launch-evidence-v1")
                .put("bounded-input-probes-v1").put("blur-stages-v1").put("operation-links-v1").put("resource-window-v1").put("media-network-evidence-v1")
                .put("unified-export-v1").put("phone-operations-v1").put("desktop-operations-v1").put("media-origin-v1"));
        try{
            @SuppressWarnings("deprecation") PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNATURES);
            value.put("firstInstallTimeMs",info.firstInstallTime).put("lastUpdateTimeMs",info.lastUpdateTime);
            JSONArray signers=new JSONArray();if(info.signatures!=null)for(Signature signature:info.signatures)signers.put(hash(signature.toByteArray()));
            value.put("signerSha256",signers);
        }catch(PackageManager.NameNotFoundException|RuntimeException unavailable){value.put("packageIdentity","unavailable");}
        return value;
    }
    static String hash(byte[] bytes){try{
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder value=new StringBuilder();
        for(byte item:digest)value.append(String.format(Locale.ROOT,"%02x",item&255));return value.toString();
    }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    @androidx.annotation.RequiresApi(24) private static final class Api24 {static Context storage(Context context){return context.createDeviceProtectedStorageContext();}}
}
