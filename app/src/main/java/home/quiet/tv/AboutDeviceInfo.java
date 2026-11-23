package home.quiet.tv;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.StatFs;
import android.view.Display;
import android.view.WindowManager;
import javax.microedition.khronos.egl.*;
import javax.microedition.khronos.opengles.GL10;
import java.util.LinkedHashSet;
import java.util.Locale;

/** Read-only values for the About overview; never probes ADB or infers vendor capabilities. */
final class AboutDeviceInfo {
    static final String UNAVAILABLE="系统未提供";
    static final String PROJECT="https://github.com/BeatsAD/quiet-tv";
    static final String THANKS="感谢 Gathin 与开源贡献者";
    static final String INTRO="简洁、可自定义的 Android 电视桌面";
    private static String gpu;
    final String model,brand,android,chipset,abis,firmware,compatibility;

    AboutDeviceInfo(Context context){
        model=value(Build.MODEL,"系统未提供型号");
        String supplied=clean(Build.BRAND);
        brand=supplied.isEmpty()?"制造商  "+value(Build.MANUFACTURER,UNAVAILABLE):"品牌  "+supplied;
        android="Android "+value(Build.VERSION.RELEASE,UNAVAILABLE)+"  API "+Build.VERSION.SDK_INT;
        chipset=Build.VERSION.SDK_INT>=31?Api31.chipset():"系统未提供芯片型号";
        abis=architectures(Build.VERSION.SDK_INT>=21?Api21.abis():new String[]{Build.CPU_ABI,Build.CPU_ABI2});
        firmware=value(Build.DISPLAY,"系统未提供固件版本");
        int min=Build.VERSION.SDK_INT>=24?Api24.minSdk(context):BuildConfig.LEGACY_19?("legacy14".equals(BuildConfig.UPDATE_COMPATIBILITY)?14:19):21;
        String system=min==14?"安卓4.0及以上":min==19?"安卓4.4":min==21?"安卓5及以上":"最低 API "+min;
        compatibility="com.coocaa.quiettv".equals(context.getPackageName())?(min==26?"酷开安卓8":"酷开专用  "+system):system;
    }

    static final class Capacity {
        final long total,available;
        Capacity(long total,long available){this.total=total;this.available=available;}
        boolean valid(){return total>0&&available>=0&&available<=total;}
        long used(){return total-available;}
        int percent(){return percentage(used(),total);}
        String compact(){
            if(!valid())return UNAVAILABLE;
            double unit=total>=1_000_000_000L?1_000_000_000d:1_000_000d;
            return String.format(Locale.ROOT,unit==1_000_000_000d?"%.1f / %.1f GB":"%.0f / %.0f MB",used()/unit,total/unit);
        }
    }
    private static final Capacity UNKNOWN=new Capacity(-1,-1);
    static final class Resources {
        final String memory,storage;
        final Capacity ram,internal;
        Resources(Capacity ram,Capacity internal){
            this.ram=ram;this.internal=internal;
            memory=storage(ram.total,ram.available);storage=storage(internal.total,internal.available);
        }
    }
    static int percentage(long used,long total){return total<=0?0:(int)Math.round(100d*used/total);}
    static Capacity memory(Context context){
        if(Build.VERSION.SDK_INT>=16)try{
            ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            if(manager!=null)return Api16.memory(manager);
        }catch(RuntimeException unavailable){/* Some firmware omits these values. */}
        return UNKNOWN;
    }
    static Resources resources(Context context){
        Capacity internal=UNKNOWN;
        try{
            StatFs fs=new StatFs(context.getFilesDir().getAbsolutePath());
            long total,available;
            if(Build.VERSION.SDK_INT>=18){total=Api18.total(fs);available=Api18.available(fs);}
            else {total=blockBytes(fs.getBlockSize(),fs.getBlockCount());available=blockBytes(fs.getBlockSize(),fs.getAvailableBlocks());}
            internal=new Capacity(total,available);
        }catch(RuntimeException unavailable){/* Keep the failed field separate from memory. */}
        return new Resources(memory(context),internal);
    }
    static Screen screen(Context context){
        try{
            if(Build.VERSION.SDK_INT>=17)return screen(Api17.display(context));
            WindowManager window=(WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
            return screen(window==null?null:window.getDefaultDisplay());
        }catch(RuntimeException unavailable){return screen((Display)null);}
    }
    static synchronized String gpu(){
        if(gpu==null){
            try{gpu=renderer();}catch(RuntimeException unavailable){gpu=UNAVAILABLE;}
        }
        return UNAVAILABLE.equals(gpu)?"未读取到型号":gpu;
    }
    private static String renderer(){
        // A small offscreen context lets us query the renderer without starting the wallpaper.
        EGL10 egl=(EGL10)EGLContext.getEGL();
        EGLDisplay display=egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if(EGL10.EGL_NO_DISPLAY.equals(display)||!egl.eglInitialize(display,new int[2]))return UNAVAILABLE;
        EGLContext context=EGL10.EGL_NO_CONTEXT;
        EGLSurface surface=EGL10.EGL_NO_SURFACE;
        try{
            EGLConfig[] configs=new EGLConfig[1];
            int[] count=new int[1];
            int[] config={EGL10.EGL_SURFACE_TYPE,EGL10.EGL_PBUFFER_BIT,EGL10.EGL_RENDERABLE_TYPE,4,EGL10.EGL_NONE};
            if(!egl.eglChooseConfig(display,config,configs,1,count)||count[0]==0)return UNAVAILABLE;
            context=egl.eglCreateContext(display,configs[0],EGL10.EGL_NO_CONTEXT,new int[]{0x3098,2,EGL10.EGL_NONE});
            if(EGL10.EGL_NO_CONTEXT.equals(context))return UNAVAILABLE;
            surface=egl.eglCreatePbufferSurface(display,configs[0],new int[]{EGL10.EGL_WIDTH,1,EGL10.EGL_HEIGHT,1,EGL10.EGL_NONE});
            if(EGL10.EGL_NO_SURFACE.equals(surface)||!egl.eglMakeCurrent(display,surface,surface,context))return UNAVAILABLE;
            String model=clean(((GL10)context.getGL()).glGetString(GL10.GL_RENDERER));
            String name=model.toLowerCase(Locale.ROOT);
            return name.isEmpty()||name.contains("swiftshader")||name.contains("llvmpipe")||name.contains("softpipe")
                    ||name.contains("software")||name.contains("translator")||name.contains("emulator")?UNAVAILABLE:model;
        }finally{
            egl.eglMakeCurrent(display,EGL10.EGL_NO_SURFACE,EGL10.EGL_NO_SURFACE,EGL10.EGL_NO_CONTEXT);
            if(!EGL10.EGL_NO_SURFACE.equals(surface))egl.eglDestroySurface(display,surface);
            if(!EGL10.EGL_NO_CONTEXT.equals(context))egl.eglDestroyContext(display,context);
            egl.eglTerminate(display);
        }
    }
    static final class Screen {
        final String label,value;
        Screen(String label,String value){this.label=label;this.value=value;}
    }
    static Screen screen(Display display){
        String label=Build.VERSION.SDK_INT>=23?"电视分辨率":"界面渲染分辨率";
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
    static String capacity(long bytes){return bytes<0?UNAVAILABLE:bytes<1_000_000_000L?String.format(Locale.ROOT,"%.0f MB",bytes/1_000_000d):String.format(Locale.ROOT,"%.1f GB",bytes/1_000_000_000d);}
    static String storage(long total,long available){
        return total<=0 || available<0 || available>total?UNAVAILABLE:"已使用 "+capacity(total-available)+" / 共 "+capacity(total);
    }
    static String screenValue(int width,int height,float refresh){
        if(width<=0 || height<=0)return UNAVAILABLE;
        String resolution=width+" × "+height;
        if(Float.isNaN(refresh)||Float.isInfinite(refresh)||refresh<=0)return resolution+"  刷新率未提供";
        String rate=String.format(Locale.ROOT,"%.2f",refresh).replaceAll("0+$","").replaceAll("\\.$","");
        return resolution+"  "+rate+" Hz";
    }
    @android.annotation.TargetApi(16) private static final class Api16 {
        static Capacity memory(ActivityManager manager){ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();manager.getMemoryInfo(info);return new Capacity(info.totalMem,info.availMem);}
    }
    @android.annotation.TargetApi(17) private static final class Api17 {
        static Display display(Context context){
            DisplayManager manager=(DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
            return manager==null?null:manager.getDisplay(Display.DEFAULT_DISPLAY);
        }
    }
    @android.annotation.TargetApi(18) private static final class Api18 {
        static long total(StatFs fs){return fs.getTotalBytes();}
        static long available(StatFs fs){return fs.getAvailableBytes();}
    }
    @android.annotation.TargetApi(21) private static final class Api21 {
        static String[] abis(){return Build.SUPPORTED_ABIS;}
    }
    @android.annotation.TargetApi(23) private static final class Api23 {
        static Screen screen(Display display){Display.Mode mode=display.getMode();return new Screen("电视分辨率",screenValue(mode.getPhysicalWidth(),mode.getPhysicalHeight(),mode.getRefreshRate()));}
    }
    @android.annotation.TargetApi(24) private static final class Api24 {
        static int minSdk(Context context){return context.getApplicationInfo().minSdkVersion;}
    }
    @android.annotation.TargetApi(31) private static final class Api31 {
        static String chipset(){
            String model=clean(Build.SOC_MODEL),manufacturer=clean(Build.SOC_MANUFACTURER);
            return model.isEmpty()?"系统未提供芯片型号":manufacturer.isEmpty()?model:manufacturer+"  "+model;
        }
    }
}
