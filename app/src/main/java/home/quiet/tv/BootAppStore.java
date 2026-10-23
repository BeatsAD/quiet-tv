package home.quiet.tv;

import android.content.*;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.Settings;
import java.io.*;

/** One foreground launch attempt per system boot, persisted across Home/process recreation. */
final class BootAppStore {
    private final Context context;
    private final SharedPreferences preferences;
    BootAppStore(Context context){this.context=context;preferences=context.getSharedPreferences("boot-app",0);}
    String target(){return preferences.getString("package","");}
    String label(){return preferences.getString("label",target());}
    Intent launchIntent(String pkg){
        if(pkg.isEmpty() || pkg.equals(context.getPackageName()) || "home.quiet.tv.test".equals(pkg))return null;
        ResolveInfo info=new HomeAppCatalog(context).launchable().get(pkg);
        return info==null?null:new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(new ComponentName(pkg,info.activityInfo.name))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }
    // Saving also consumes this boot: enabling/changing a target takes effect on the next boot.
    synchronized void select(String pkg,String label) throws IOException {
        String boot=bootId();
        if(!pkg.isEmpty() && (boot.isEmpty() || launchIntent(pkg)==null))throw new IOException("此应用或当前系统暂不支持开机自启");
        if(!preferences.edit().putString("package",pkg).putString("label",label).putString("handled-boot",boot).commit())throw new IOException("自启设置保存失败，请重试");
    }
    String consume(){
        synchronized(BootAppStore.class){
            String boot=bootId();
            if(boot.isEmpty() || boot.equals(preferences.getString("handled-boot","")))return "";
            // Consume before starting, including unavailable/blocked targets, so Home remains recoverable.
            return preferences.edit().putString("handled-boot",boot).commit()?target():"";
        }
    }
    private String bootId(){
        if(Build.VERSION.SDK_INT>=24){
            try{int count=Settings.Global.getInt(context.getContentResolver(),"boot_count",-1);if(count>=0)return "count:"+count;}
            catch(SecurityException ignored){}
        }
        // Android 4.4–6 has no public boot counter. The kernel UUID survives process death and sleep.
        try(BufferedReader input=new BufferedReader(new FileReader("/proc/sys/kernel/random/boot_id"))){
            String id=input.readLine();if(id!=null && id.matches("[0-9a-fA-F-]{36}"))return "id:"+id;
        }catch(IOException|SecurityException ignored){}
        return "";
    }
}
