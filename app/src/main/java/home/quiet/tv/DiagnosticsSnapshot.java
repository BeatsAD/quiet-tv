package home.quiet.tv;

import android.app.ActivityManager;
import android.content.*;
import android.content.pm.*;
import android.os.Build;
import android.util.DisplayMetrics;
import org.json.*;
import java.io.*;
import java.util.*;

/** Explicit read-only probes. Each missing result remains visible in the export. */
final class DiagnosticsSnapshot {
    interface Probe { Object read() throws Exception; }
    private static void probe(JSONObject result,String name,Probe probe){
        JSONObject item=new JSONObject();
        try{Object value=probe.read();item.put("status",value==null || "".equals(value)?"empty":"ok");item.put("value",value==null?JSONObject.NULL:value);}
        catch(Exception failure){try{item.put("status","unavailable").put("reason",failure.getClass().getSimpleName()+": "+failure.getMessage());}catch(JSONException ignored){}}
        try{result.put(name,item);}catch(JSONException impossible){throw new IllegalStateException(impossible);}
    }
    static void capture(Context context,AdbController adb,File directory,String name) throws Exception {
        JSONObject root=new JSONObject().put("time",System.currentTimeMillis());
        JSONObject device=new JSONObject().put("manufacturer",Build.MANUFACTURER).put("brand",Build.BRAND).put("model",Build.MODEL)
            .put("product",Build.PRODUCT).put("board",Build.BOARD).put("hardware",Build.HARDWARE).put("android",Build.VERSION.RELEASE)
            .put("api",Build.VERSION.SDK_INT).put("display",Build.DISPLAY).put("fingerprint",Build.FINGERPRINT).put("incremental",Build.VERSION.INCREMENTAL)
            .put("abis",new JSONArray(Build.VERSION.SDK_INT>=21?Arrays.asList(Build.SUPPORTED_ABIS):Arrays.asList(Build.CPU_ABI,Build.CPU_ABI2)));
        DisplayMetrics metrics=context.getResources().getDisplayMetrics();
        device.put("width",metrics.widthPixels).put("height",metrics.heightPixels).put("densityDpi",metrics.densityDpi).put("dataFreeBytes",context.getFilesDir().getUsableSpace());
        ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();((ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memory);
        device.put("memoryTotal",memory.totalMem).put("memoryAvailable",memory.availMem).put("lowMemory",memory.lowMemory);root.put("device",device);
        probe(root,"quietTv",()->{PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),0);return new JSONObject().put("package",context.getPackageName()).put("versionName",info.versionName).put("versionCode",info.versionCode).put("compatibility",BuildConfig.LEGACY_19?"legacy19":"standard").put("applicationUser",android.os.Process.myUid()/100000);});
        probe(root,"homeResolution",()->{
            HomeController.State home=new HomeController(context).inspect(null);
            return new JSONObject().put("source","PackageManager").put("known",home.known).put("actual",home.actual).put("resolved",home.resolved).put("quietUsable",home.ownUsable).put("reason",home.reason).put("physicalHomeVerified",false);
        });
        JSONObject categories=new JSONObject();
        for(String category:new String[]{Intent.CATEGORY_HOME,"android.intent.category.HOME.CC","android.intent.category.HOME.CC_MAIN","smartdevice.intent.category.LITE_HOME"})probe(categories,category,()->{
            JSONArray homes=new JSONArray();
            for(ResolveInfo resolved:context.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),HomeController.METADATA)){
                ActivityInfo info=resolved.activityInfo;if(info==null)continue;
                homes.put(new JSONObject().put("component",new ComponentName(info.packageName,info.name).flattenToShortString()).put("enabled",info.enabled).put("appEnabled",info.applicationInfo.enabled).put("exported",info.exported).put("permission",info.permission==null?JSONObject.NULL:info.permission));
            }
            return homes;
        });root.put("homeCandidates",categories);
        JSONObject packages=new JSONObject();
        for(String pkg:new String[]{context.getPackageName(),"com.tianci.system","com.tianci.movieplatform","com.ccos.tv.homepage","com.coocaa.quiettv","home.quiet.tv.xiaomihelper"})probe(packages,pkg,()->{
            PackageInfo info=context.getPackageManager().getPackageInfo(pkg,HomeController.METADATA);ApplicationInfo app=info.applicationInfo;
            return new JSONObject().put("versionName",info.versionName).put("versionCode",info.versionCode).put("installed",(app.flags&ApplicationInfo.FLAG_INSTALLED)!=0).put("enabled",app.enabled).put("enabledSetting",context.getPackageManager().getApplicationEnabledSetting(pkg));
        });root.put("packages",packages);
        probe(root,"guard",()->{HomeGuardState guard=new HomeGuardState(context);return new JSONObject().put("enabled",guard.enabled()).put("selected",guard.selected()).put("summary",guard.summary());});
        JSONObject shell=new JSONObject();root.put("adb",shell);
        if(adb.connected()){
            try{adb.validateUser();shell.put("status","connected").put("currentUser",adb.user);}
            catch(Exception failure){shell.put("status","unavailable").put("reason",failure.getMessage());}
        }else shell.put("status","unavailable").put("reason","未连接本机 ADB；未取得厂商主页属性、全系统前台状态和 shell 命令能力");
        if("connected".equals(shell.optString("status"))){
            String[][] commands={
                {"pmHelp","pm help"},{"packageHelp","cmd package help"},
                {"homepagePackage","getprop persist.service.homepage.pkg"},{"homepageClass","getprop persist.service.homepage.cls"},
                {"activities","dumpsys activity activities"},{"windows","dumpsys window windows"},
                {"quietMemory","dumpsys meminfo "+context.getPackageName()},
                {"stockPackage","dumpsys package com.tianci.movieplatform"},{"shimPackage","dumpsys package com.ccos.tv.homepage"}
            };
            for(String[] command:commands)probe(shell,command[0],()->{String text=adb.shell(command[1]);if(text.length()>512*1024)return text.substring(0,512*1024)+"\n[TRUNCATED at 512 Ki characters]";return text;});
            if(!adb.capabilities.resolve.isEmpty())probe(shell,"homeResolved",()->adb.shell(adb.capabilities.resolve+" --brief --user "+adb.user+" -a android.intent.action.MAIN -c android.intent.category.HOME"));
            else probe(shell,"homeResolved",()->{throw new IOException("设备帮助未提供带用户参数的 HOME 解析命令");});
        }
        JSONObject records=new JSONObject();
        for(String record:new String[]{"setup-state.json","app-operations.jsonl"})probe(records,record,()->{
            File original=new File(context.getFilesDir(),record);if(!original.isFile())throw new FileNotFoundException("暂无记录");
            try(InputStream input=new FileInputStream(original);ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] bytes=new byte[4096];int size;while((size=input.read(bytes))!=-1){if(output.size()+size>2*1024*1024)throw new IOException("记录超过 2 MiB，未复制；原件保留");output.write(bytes,0,size);}
                DiagnosticsRecorder.write(new File(directory,record),output.toString("UTF-8"));return "copied";
            }
        });root.put("privateRecords",records);
        DiagnosticsRecorder.write(new File(directory,name),root.toString(2));
    }
}
