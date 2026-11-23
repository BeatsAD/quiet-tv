package home.quiet.tv;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Debug;
import android.os.Process;
import android.os.SystemClock;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared, on-demand memory sample. Call only from a background thread. */
final class AboutRuntime {
    static final int REFRESH_MS=5000;
    private static final String MAIN_PROCESS="Quiet TV 桌面主进程";
    private static Snapshot last;
    private static long sampledAt;
    private static AdbController sampledAdb;

    static final class App {
        final String packageName;
        final String name;
        final long bytes;
        App(String packageName,String name,long bytes){this.packageName=packageName;this.name=name;this.bytes=bytes;}
        double percent(long total){return total>0?100d*bytes/total:0;}
    }
    static final class Snapshot {
        final AboutDeviceInfo.Capacity memory;
        final App desktop;
        final List<App> apps;
        final String note;
        Snapshot(AboutDeviceInfo.Capacity memory,App desktop,List<App> apps,String note){
            this.memory=memory;this.desktop=desktop;this.apps=apps;this.note=note;
        }
    }

    static synchronized Snapshot read(Context context){
        ManagementAdb management=ManagementAdb.existing();
        AdbController adb=Build.VERSION.SDK_INT>=26&&management!=null&&!management.paused()&&management.adb.connected()
                ?management.adb:null;
        long now=SystemClock.elapsedRealtime();
        if(last!=null&&adb==sampledAdb&&now-sampledAt<REFRESH_MS-100)return last;
        last=sample(context,adb);
        sampledAt=SystemClock.elapsedRealtime();
        sampledAdb=adb;
        return last;
    }

    private static Snapshot sample(Context context,AdbController adb){
        AboutDeviceInfo.Capacity memory=AboutDeviceInfo.memory(context);
        String pkg=context.getPackageName();
        boolean timedOut=false;
        if(adb!=null){
            try{
                PackageManager packages=context.getPackageManager();
                Map<String,Long> totals=AboutAdbMemory.parse(adb.runtimeMemoryShell(),pkg,
                        Process.myPid(),Process.myUid(),packages::getPackagesForUid);
                Long desktop=totals.remove(pkg);
                if(desktop!=null&&adb.connected()){
                    List<App> apps=appRows(packages,totals);
                    String note=apps.isEmpty()?"ADB 未读到其他运行中的应用":"通过 ADB 读取可识别的运行中应用";
                    return new Snapshot(memory,new App(pkg,"Quiet TV 桌面",desktop),apps,note);
                }
            }catch(SocketTimeoutException timeout){timedOut=true;}
            catch(Exception unavailable){/* Keep local memory visible if the ADB query is unavailable. */}
        }
        long primary=0;
        try{primary=Debug.getPss()*1024L;}catch(RuntimeException unavailable){/* Retain the unavailable state. */}
        boolean limited=Build.VERSION.SDK_INT>=29;
        List<App> apps=new ArrayList<>();
        long own=primary;
        boolean complete=primary>0;
        boolean foundMain=false;
        try{
            ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> processes=manager==null?null:manager.getRunningAppProcesses();
            if(processes!=null){
                Map<Integer,ActivityManager.RunningAppProcessInfo> targets=new HashMap<>();
                for(ActivityManager.RunningAppProcessInfo item:processes){
                    if(item.pid==Process.myPid()){foundMain=true;continue;}
                    if(item.uid==Process.myUid()||!limited&&item.pkgList!=null&&item.pkgList.length==1)targets.put(item.pid,item);
                }
                if(!targets.isEmpty()){
                    int[] pids=new int[targets.size()];
                    int index=0;
                    for(int pid:targets.keySet())pids[index++]=pid;
                    Debug.MemoryInfo[] result=manager.getProcessMemoryInfo(pids);
                    Map<String,Long> totals=new HashMap<>();
                    for(int i=0;i<pids.length;i++){
                        ActivityManager.RunningAppProcessInfo item=targets.get(pids[i]);
                        long bytes=result!=null&&i<result.length&&result[i]!=null?result[i].getTotalPss()*1024L:0;
                        if(item.uid==Process.myUid()){
                            if(bytes>0)own+=bytes;
                            else complete=false;
                        }else if(bytes>0&&!pkg.equals(item.pkgList[0])){
                            String name=item.pkgList[0];
                            Long previous=totals.get(name);
                            totals.put(name,(previous==null?0:previous)+bytes);
                        }
                    }
                    apps=appRows(context.getPackageManager(),totals);
                }
            }
        }catch(RuntimeException unavailable){complete=false;}
        App desktop=complete&&foundMain?new App(pkg,"Quiet TV 桌面",own):new App(pkg,MAIN_PROCESS,primary);
        String note;
        if(adb!=null&&adb.connected())note=timedOut?"ADB 应用内存查询超时，显示系统可读取的数据":"ADB 暂未读到应用内存，显示系统可读取的数据";
        else if(limited)note="连接 Quiet TV 的本机 ADB 后可查看其他应用内存";
        else note="仅显示可读取内存的应用";
        return new Snapshot(memory,desktop,apps,note);
    }

    private static List<App> appRows(PackageManager packages,Map<String,Long> totals){
        List<App> apps=new ArrayList<>();
        for(Map.Entry<String,Long> item:totals.entrySet()){
            try{
                String label=AboutDeviceInfo.clean(packages.getApplicationInfo(item.getKey(),0).loadLabel(packages).toString());
                if(!label.isEmpty())apps.add(new App(item.getKey(),label,item.getValue()));
            }catch(PackageManager.NameNotFoundException unavailable){/* The app may have been removed. */}
        }
        Collections.sort(apps,(left,right)->{
            if(left.bytes==right.bytes)return left.name.compareTo(right.name);
            return left.bytes>right.bytes?-1:1;
        });
        return apps;
    }
}
