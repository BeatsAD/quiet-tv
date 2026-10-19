package home.quiet.tv;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import java.io.File;
import java.io.IOException;

/** API26/28 negative case: an unreadable local challenge must reject the ADB session. */
final class IdentityChecks {
    static void run(Instrumentation test) throws Exception {
        if(!"ranchu".equals(Build.HARDWARE) && !"goldfish".equals(Build.HARDWARE))throw new AssertionError("Dedicated emulator only");
        if(Build.VERSION.SDK_INT>28)throw new AssertionError("Run on the old SELinux image that hides app boot_id");
        try{java.nio.file.Files.readAllBytes(new File("/proc/sys/kernel/random/boot_id").toPath());throw new AssertionError("This image does not exercise the fallback");}
        catch(IOException expected){}
        Context context=test.getTargetContext();
        Context unreadable=new ContextWrapper(context){
            @Override public Context getApplicationContext(){return this;}
            @Override public File getExternalFilesDir(String type){return getFilesDir();}
        };
        try(AdbController adb=new AdbController(unreadable,value->{})){
            boolean refused=false;
            try{adb.connect(5555);}catch(IOException expected){refused=true;}
            if(!refused || adb.connected())throw new AssertionError("Unverifiable identity was accepted");
        }
    }
}
