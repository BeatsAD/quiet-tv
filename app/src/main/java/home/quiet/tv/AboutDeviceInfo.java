package home.quiet.tv;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.os.StatFs;
import android.view.Display;
import java.util.LinkedHashSet;
import java.util.Locale;

/** Read-only values for the About overview; never probes ADB or infers vendor capabilities. */
final class AboutDeviceInfo {
    static final String UNAVAILABLE="系统未提供";
    final String model,brand,android,cpu,abis,firmware,compatibility;

    AboutDeviceInfo(Context context){
        model=value(Build.MODEL,"系统未提供型号");
        String supplied=clean(Build.BRAND);
        brand=supplied.isEmpty()?"制造商  "+value(Build.MANUFACTURER,UNAVAILABLE):"品牌  "+supplied;
        android="Android "+value(Build.VERSION.RELEASE,UNAVAILABLE)+"  API "+Build.VERSION.SDK_INT;
        cpu=Build.VERSION.SDK_INT>=31?Api31.cpu():"系统未提供芯片型号";
        abis=architectures(Build.VERSION.SDK_INT>=21?Api21.abis():new String[]{Build.CPU_ABI,Build.CPU_ABI2});
        firmware=value(Build.DISPLAY,"系统未提供固件版本");
        int min=Build.VERSION.SDK_INT>=24?Api24.minSdk(context):BuildConfig.LEGACY_19?("legacy14".equals(BuildConfig.UPDATE_COMPATIBILITY)?14:19):21;
        String system=min==14?"安卓4.0及以上":min==19?"安卓4.4":min==21?"安卓5及以上":"最低 API "+min;
        compatibility="com.coocaa.quiettv".equals(context.getPackageName())?(min==26?"酷开安卓8":"酷开专用  "+system):system;
    }

    static final class Resources {
        final String memory,storage;
        Resources(String memory,String storage){this.memory=memory;this.storage=storage;}
    }
    static Resources resources(Context context){
        String memory="系统不支持读取总量",storage=UNAVAILABLE;
        if(Build.VERSION.SDK_INT>=16)try{
            ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            long bytes=manager==null?0:Api16.memory(manager);
            memory=bytes>0?"系统可用总量  "+capacity(bytes):UNAVAILABLE;
        }catch(RuntimeException unavailable){memory=UNAVAILABLE;}
        try{
            StatFs fs=new StatFs(context.getFilesDir().getAbsolutePath());
            long total,available;
            if(Build.VERSION.SDK_INT>=18){total=Api18.total(fs);available=Api18.available(fs);}
            else {total=blockBytes(fs.getBlockSize(),fs.getBlockCount());available=blockBytes(fs.getBlockSize(),fs.getAvailableBlocks());}
            storage=storage(total,available);
        }catch(RuntimeException unavailable){/* Keep the failed field separate from memory. */}
        return new Resources(memory,storage);
    }
    static final class Screen {
        final String label,value;
        Screen(String label,String value){this.label=label;this.value=value;}
    }
    static Screen screen(Display display){
        String label=Build.VERSION.SDK_INT>=23?"当前显示模式":"界面渲染";
        if(display!=null)try{
            if(Build.VERSION.SDK_INT>=23)return Api23.screen(display);
            Point size=new Point();display.getSize(size);
            return new Screen(label,screenValue(size.x,size.y,display.getRefreshRate()));
        }catch(RuntimeException unavailable){/* Do not substitute UI dimensions for a failed physical mode. */}
        return new Screen(label,UNAVAILABLE);
    }
    static String clean(String value){
        if(value==null)return "";
        String result=value.replaceAll("[\\p{Cntrl}]"," ").trim();
        return "unknown".equalsIgnoreCase(result)?"":result;
    }
    private static String value(String value,String missing){String result=clean(value);return result.isEmpty()?missing:result;}
    static String architectures(String[] values){
        LinkedHashSet<String> unique=new LinkedHashSet<>();
        if(values!=null)for(String value:values){String abi=clean(value);if(!abi.isEmpty())unique.add(abi);}
        StringBuilder result=new StringBuilder();for(String abi:unique){if(result.length()>0)result.append("  ");result.append(abi);}
        return result.length()==0?UNAVAILABLE:result.toString();
    }
    static long blockBytes(int blockSize,int count){return blockSize>0 && count>=0?(long)blockSize*count:-1;}
    static String capacity(long bytes){return bytes<0?UNAVAILABLE:String.format(Locale.ROOT,"%.1f GB",bytes/1_000_000_000d);}
    static String storage(long total,long available){
        return total<=0 || available<0 || available>total?UNAVAILABLE:"可用 "+capacity(available)+" / 共 "+capacity(total);
    }
    static String screenValue(int width,int height,float refresh){
        if(width<=0 || height<=0)return UNAVAILABLE;
        String resolution=width+" × "+height;
        if(Float.isNaN(refresh)||Float.isInfinite(refresh)||refresh<=0)return resolution+"  刷新率未提供";
        String rate=String.format(Locale.ROOT,"%.2f",refresh).replaceAll("0+$","").replaceAll("\\.$","");
        return resolution+"  "+rate+" Hz";
    }
    @android.annotation.TargetApi(16) private static final class Api16 {
        static long memory(ActivityManager manager){ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();manager.getMemoryInfo(info);return info.totalMem;}
    }
    @android.annotation.TargetApi(18) private static final class Api18 {
        static long total(StatFs fs){return fs.getTotalBytes();}
        static long available(StatFs fs){return fs.getAvailableBytes();}
    }
    @android.annotation.TargetApi(21) private static final class Api21 {
        static String[] abis(){return Build.SUPPORTED_ABIS;}
    }
    @android.annotation.TargetApi(23) private static final class Api23 {
        static Screen screen(Display display){Display.Mode mode=display.getMode();return new Screen("当前显示模式",screenValue(mode.getPhysicalWidth(),mode.getPhysicalHeight(),mode.getRefreshRate()));}
    }
    @android.annotation.TargetApi(24) private static final class Api24 {
        static int minSdk(Context context){return context.getApplicationInfo().minSdkVersion;}
    }
    @android.annotation.TargetApi(31) private static final class Api31 {
        static String cpu(){
            String model=clean(Build.SOC_MODEL),manufacturer=clean(Build.SOC_MANUFACTURER);
            return model.isEmpty()?"系统未提供芯片型号":manufacturer.isEmpty()?model:manufacturer+"  "+model;
        }
    }
}
