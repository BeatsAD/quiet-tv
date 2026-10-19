package home.quiet.tv;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.util.Map;

/** Identical public-API scene measurements for debug and optimized binaries. Emulator only. */
final class HomeMemoryChecks implements Application.ActivityLifecycleCallbacks {
    private final Instrumentation test;
    private volatile Activity home;
    HomeMemoryChecks(Instrumentation test){this.test=test;}
    public void onActivityResumed(Activity activity){if(activity.getClass().getName().equals("home.quiet.tv.HomeActivity"))home=activity;}
    public void onActivityCreated(Activity a,Bundle b){} public void onActivityStarted(Activity a){}
    public void onActivityPaused(Activity a){} public void onActivityStopped(Activity a){}
    public void onActivitySaveInstanceState(Activity a,Bundle b){} public void onActivityDestroyed(Activity a){}
    void run() throws Exception {run(false);}
    void run(boolean checkRotation) throws Exception {
        if(!"ranchu".equals(Build.HARDWARE) && !"goldfish".equals(Build.HARDWARE))throw new AssertionError("Dedicated emulator only");
        Application app=(Application)test.getTargetContext().getApplicationContext();app.registerActivityLifecycleCallbacks(this);
        SharedPreferences prefs=app.getSharedPreferences("wallpapers",0);Map<String,?> original=prefs.getAll();
        try{
            for(String scene:checkRotation?new String[]{"rotating"}:new String[]{"default","fixed","rotating"}){
                Activity previous=home;home=null;if(previous!=null)test.runOnMainSync(previous::finish);
                if(!prefs.edit().putBoolean("default",scene.equals("default")).putBoolean("rotating",scene.equals("rotating")).putInt("index",0).commit())throw new IOException("Cannot set scene");
                test.runOnMainSync(()->app.startActivity(new Intent().setClassName(app.getPackageName(),"home.quiet.tv.HomeActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
                long until=android.os.SystemClock.elapsedRealtime()+15000;
                while(home==null && android.os.SystemClock.elapsedRealtime()<until)Thread.sleep(100);
                if(home==null)throw new AssertionError("HOME did not resume");
                test.waitForIdleSync();Thread.sleep(2500);
                if(checkRotation){
                    long start=android.os.SystemClock.elapsedRealtime(),deadline=start+315000;
                    while(prefs.getInt("index",0)==0 && android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(1000);
                    if(prefs.getInt("index",0)!=1 || android.os.SystemClock.elapsedRealtime()-start<290000)throw new AssertionError("Formal five-minute rotation missing or interval shortened");
                }
                try(InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(test.getUiAutomation().executeShellCommand("dumpsys meminfo home.quiet.tv"));ByteArrayOutputStream output=new ByteArrayOutputStream()){
                    byte[] bytes=new byte[8192];int count;while((count=input.read(bytes))!=-1)output.write(bytes,0,count);
                    File dir=new File(app.getExternalFilesDir(null),"optimization-memory");if(!dir.isDirectory() && !dir.mkdirs())throw new IOException("Cannot create report directory");
                    try(FileOutputStream report=new FileOutputStream(new File(dir,scene+".txt"))){output.writeTo(report);}
                }
            }
        }finally{
            SharedPreferences.Editor restore=prefs.edit();
            for(String key:new String[]{"default","rotating","index"}){
                Object value=original.get(key);if(value instanceof Boolean)restore.putBoolean(key,(Boolean)value);else if(value instanceof Integer)restore.putInt(key,(Integer)value);else restore.remove(key);
            }
            if(!restore.commit())throw new IOException("Scene preferences not restored");
            Activity previous=home;if(previous!=null)test.runOnMainSync(previous::finish);
            app.unregisterActivityLifecycleCallbacks(this);
        }
    }
}
