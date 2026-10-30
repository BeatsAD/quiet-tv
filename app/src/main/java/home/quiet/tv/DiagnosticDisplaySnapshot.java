package home.quiet.tv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.os.Build;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import androidx.core.view.DisplayCompat;
import java.util.Locale;

/** Numeric display evidence only. Never changes a mode, opens ADB or reads screen content. */
@android.annotation.SuppressLint("ObsoleteSdkInt") // Shared with the API14 compatibility source set.
final class DiagnosticDisplaySnapshot {
    private static final int MODE_LIMIT=16;
    private static volatile String home;
    private interface Read {String get() throws Exception;}
    private final StringBuilder text=new StringBuilder();

    // Called on the UI thread after the home has dispatched a draw. Retain values, never Views.
    static void home(Activity activity,View root,Canvas canvas){
        DiagnosticDisplaySnapshot sample=new DiagnosticDisplaySnapshot();
        sample.window(activity,root);
        sample.line("首页 Canvas 尺寸",size(canvas.getWidth(),canvas.getHeight()));
        sample.line("首页 Canvas 硬件加速",Boolean.toString(canvas.isHardwareAccelerated()));
        home=sample.text.toString();
    }
    // Capture the export window on the UI thread, before the worker starts collecting system data.
    static String window(Activity activity){
        DiagnosticDisplaySnapshot sample=new DiagnosticDisplaySnapshot();
        sample.window(activity,activity.getWindow().getDecorView());
        return sample.text.toString();
    }
    private void window(Activity activity,View root){
        line("采样时间 Unix 毫秒",Long.toString(System.currentTimeMillis()));
        line("采样时间 uptimeMs",Long.toString(SystemClock.uptimeMillis()));
        line("页面",activity.getClass().getSimpleName());
        read("窗口显示 ID",()->Integer.toString(activity.getWindowManager().getDefaultDisplay().getDisplayId()));
        line("视图布局尺寸",size(root.getWidth(),root.getHeight()));
        View decor=activity.getWindow().getDecorView();
        line("Decor 布局尺寸",size(decor.getWidth(),decor.getHeight()));
        line("视图硬件加速",Boolean.toString(root.isHardwareAccelerated()));
        line("窗口焦点",Boolean.toString(decor.hasWindowFocus()));
        if(Build.VERSION.SDK_INT>=23){
            read("窗口请求模式 ID（0=无偏好）",()->Integer.toString(Api23.preferred(activity)));
            read("窗口采样时原始当前模式",()->Api23.current(activity.getWindowManager().getDefaultDisplay()));
        }
        if(Build.VERSION.SDK_INT>=30)read("WindowMetrics bounds",()->Api30.bounds(activity));
        read("页面 Resources DisplayMetrics",()->metrics(activity.getResources().getDisplayMetrics()));
    }
    static String collect(Context context,String window){
        DiagnosticDisplaySnapshot result=new DiagnosticDisplaySnapshot();
        result.line("系统采样时间 Unix 毫秒",Long.toString(System.currentTimeMillis()));
        WindowManager manager=(WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        Display display=manager==null?null:manager.getDefaultDisplay();
        if(display==null)result.line("显示接口","系统未提供默认 Display");
        else{
            result.read("默认显示 ID",()->Integer.toString(display.getDisplayId()));
            result.read("Display.getSize（应用可用尺寸）",()->{Point point=new Point();display.getSize(point);return size(point.x,point.y);});
            result.read("Display.getMetrics",()->{DisplayMetrics value=new DisplayMetrics();display.getMetrics(value);return metrics(value);});
            if(Build.VERSION.SDK_INT>=17){
                result.read("Display.getRealSize（系统报告，非面板保证）",()->Api17.size(display));
                result.read("Display.getRealMetrics",()->Api17.metrics(display));
            }else result.line("RealSize 与 RealMetrics","当前系统低于 API17，不支持");
            if(Build.VERSION.SDK_INT>=23){
                result.read("原始当前模式",()->Api23.current(display));
                result.read("原始支持模式",()->Api23.modes(display));
            }else result.line("原始显示模式","当前系统低于 API23，无标准模式列表");
            if(Build.VERSION.SDK_INT>=17){
                result.read("DisplayCompat 当前尺寸",()->Api17.compatSize(context,display));
                result.read("DisplayCompat 支持尺寸",()->compatModes(context,display));
                if(Build.VERSION.SDK_INT<23)result.line("兼容尺寸范围","API17–22 仅返回尺寸估计，不是完整显示模式列表");
            }else result.line("DisplayCompat","当前系统低于 API17，不调用依赖 RealSize 的兼容接口");
        }
        for(String key:new String[]{"vendor.display-size","sys.display-size"})result.read(key,()->property(key));
        result.text.append("\n[导出时诊断窗口]\n").append(window==null?"未采集\n":window);
        String latest=home;
        result.text.append("\n[本进程首页最近绘制]\n").append(latest==null?"未采集；请先返回首页，再进入诊断日志重新生成\n":latest);
        result.line("底层 Surface 缓冲区尺寸","未采集；普通 View 无公开读取接口，需电脑侧核对本应用图层");
        result.line("面板原生分辨率","未确认；当前模式、兼容尺寸及最大支持模式均不等同于面板规格");
        result.line("判断范围","视图、WindowMetrics 和 Canvas 尺寸不是底层缓冲区尺寸；4K 模式或 4K 视频输入不能单独证明原生 4K 界面。未请求切换模式，未修改系统尺寸，未采集画面或显示设备名称。");
        return result.text.toString();
    }
    private static String compatModes(Context context,Display display){
        DisplayCompat.ModeCompat[] modes=DisplayCompat.getSupportedModes(context,display);
        int uhd=0;for(DisplayCompat.ModeCompat mode:modes)if(uhd(mode.getPhysicalWidth(),mode.getPhysicalHeight()))uhd++;
        StringBuilder result=new StringBuilder("数量=").append(modes.length).append("  报告的 UHD 或以上模式数=").append(uhd);
        for(int i=0;i<Math.min(MODE_LIMIT,modes.length);i++){
            DisplayCompat.ModeCompat mode=modes[i];
            result.append("\n  ").append(i+1).append(": ").append(size(mode.getPhysicalWidth(),mode.getPhysicalHeight()));
            if(Build.VERSION.SDK_INT>=23)result.append("  原始 ").append(Api23.underlying(mode));
        }
        return result.append("\n  未展开模式数=").append(Math.max(0,modes.length-MODE_LIMIT)).toString();
    }
    @android.annotation.SuppressLint("PrivateApi") // Optional, read-only size allowlist; blocked reads remain explicit.
    private static String property(String key)throws Exception{
        String value=(String)Class.forName("android.os.SystemProperties").getMethod("get",String.class).invoke(null,key);
        return propertyValue(value);
    }
    static String propertyValue(String value){
        if(value==null||value.isEmpty())return "空值（未提供尺寸）";
        return value.matches("[1-9][0-9]{0,4}x[1-9][0-9]{0,4}")?value:"非标准尺寸值，已省略";
    }
    static String size(int width,int height){return width>0&&height>0?width+"x"+height:"未布局或尺寸无效（"+width+"x"+height+"）";}
    static boolean uhd(int width,int height){return Math.max(width,height)>=3840&&Math.min(width,height)>=2160;}
    private static String metrics(DisplayMetrics value){return size(value.widthPixels,value.heightPixels)
        +"  densityDpi="+value.densityDpi+"  density="+value.density+"  scaledDensity="+value.scaledDensity;}
    private void read(String label,Read read){
        try{line(label,read.get());}catch(Exception unavailable){line(label,"无法读取："+unavailable.getClass().getSimpleName());}
    }
    private void line(String label,String value){text.append(label).append("：").append(value).append('\n');}

    @androidx.annotation.RequiresApi(17) private static final class Api17 {
        static String size(Display display){Point point=new Point();display.getRealSize(point);return DiagnosticDisplaySnapshot.size(point.x,point.y);}
        static String metrics(Display display){DisplayMetrics value=new DisplayMetrics();display.getRealMetrics(value);return DiagnosticDisplaySnapshot.metrics(value);}
        static String compatSize(Context context,Display display){DisplayCompat.ModeCompat mode=DisplayCompat.getMode(context,display);return DiagnosticDisplaySnapshot.size(mode.getPhysicalWidth(),mode.getPhysicalHeight());}
    }
    @androidx.annotation.RequiresApi(23) private static final class Api23 {
        static int preferred(Activity activity){return activity.getWindow().getAttributes().preferredDisplayModeId;}
        static String current(Display display){return mode(display.getMode());}
        static String mode(Display.Mode mode){return "id="+mode.getModeId()+"  "+size(mode.getPhysicalWidth(),mode.getPhysicalHeight())+"  "+String.format(Locale.ROOT,"%.3f Hz",mode.getRefreshRate());}
        static String underlying(DisplayCompat.ModeCompat mode){Display.Mode raw=mode.toMode();return raw==null?"无原始模式 ID":mode(raw);}
        static String modes(Display display){
            Display.Mode[] modes=display.getSupportedModes();
            int uhd=0;for(Display.Mode mode:modes)if(uhd(mode.getPhysicalWidth(),mode.getPhysicalHeight()))uhd++;
            StringBuilder result=new StringBuilder("数量=").append(modes.length).append("  报告的 UHD 或以上模式数=").append(uhd);
            for(int i=0;i<Math.min(MODE_LIMIT,modes.length);i++)result.append("\n  ").append(i+1).append(": ").append(mode(modes[i]));
            return result.append("\n  未展开模式数=").append(Math.max(0,modes.length-MODE_LIMIT)).toString();
        }
    }
    @androidx.annotation.RequiresApi(30) private static final class Api30 {
        static String bounds(Activity activity){android.graphics.Rect bounds=activity.getWindowManager().getCurrentWindowMetrics().getBounds();return size(bounds.width(),bounds.height());}
    }
}
