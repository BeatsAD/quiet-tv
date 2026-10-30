package home.quiet.tv;

import android.content.Context;

/** Install secondary dex before the shared startup logger on Android 4.x. */
public final class QuietLegacyApplication extends QuietApplication {
    @Override protected void attachBaseContext(Context context){super.attachBaseContext(context);androidx.multidex.MultiDex.install(this);}
    @Override protected boolean playbackProcess(){
        // Do not start HOME recovery, ADB, or the shared log writer in the codec process.
        try(java.io.FileInputStream input=new java.io.FileInputStream("/proc/self/cmdline")){
            byte[] name=new byte[256];int count=input.read(name),end=0;
            while(end<count&&name[end]!=0)end++;
            return new String(name,0,end,"UTF-8").equals(getPackageName()+":wallpaper");
        }catch(java.io.IOException|SecurityException e){
            android.app.ActivityManager manager=(android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);
            java.util.List<android.app.ActivityManager.RunningAppProcessInfo> processes=manager==null?null:manager.getRunningAppProcesses();
            if(processes!=null)for(android.app.ActivityManager.RunningAppProcessInfo process:processes)
                if(process.pid==android.os.Process.myPid())return (getPackageName()+":wallpaper").equals(process.processName);
            return false;
        }
    }
}
