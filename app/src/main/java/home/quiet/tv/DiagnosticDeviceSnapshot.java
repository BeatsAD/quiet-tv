package home.quiet.tv;

import android.content.*;
import android.content.pm.*;
import android.os.Build;
import android.provider.Settings;
import org.json.JSONObject;
import java.util.Arrays;

/** Read-only state at export time, separate from the bounded historical event journal. */
final class DiagnosticDeviceSnapshot {
    private final Context context;
    private final PackageManager pm;
    private final StringBuilder text=new StringBuilder();
    private interface Read {void run() throws Exception;}
    private DiagnosticDeviceSnapshot(Context context){this.context=context;pm=context.getPackageManager();}
    static String collect(Context context,String adb){
        DiagnosticDeviceSnapshot snapshot=new DiagnosticDeviceSnapshot(context);
        snapshot.section("设备与软件",snapshot::device);
        snapshot.text.append("\n[ADB 与调试接口]\n").append(adb);
        snapshot.section("调试设置入口",snapshot::debugSettings);
        snapshot.section("系统默认桌面",snapshot::home);
        snapshot.section("原桌面记录",snapshot::original);
        snapshot.section("桌面守护",snapshot::guard);
        return snapshot.text.toString();
    }
    // Called on the main thread: AdbSession's displayed state is main-thread owned.
    static String adb(Context context,AdbSession session){
        DiagnosticDeviceSnapshot snapshot=new DiagnosticDeviceSnapshot(context);
        snapshot.section("连接状态",()->{
            if(session==null){snapshot.line("本应用 ADB 会话","无活动管理会话，连接状态未确认");return;}
            AdbState state=session.state();AdbController controller=session.adb;
            snapshot.line("系统调试观测",state.debug.name());
            snapshot.line("本应用连接",controller.connected()?"已连接，已通过本机、shell 和用户核对":state.connection.name());
            snapshot.line("连接说明",state.detail);
            snapshot.line("连接阶段",controller.stage().name());snapshot.line("最近失败阶段",controller.failure().name());
            snapshot.line("自动连接",state.paused?"已暂停":"已开启");
            if(controller.connected()){
                snapshot.line("已核对 Android 用户",Integer.toString(controller.user));
                snapshot.line("当前连接端口",Integer.toString(controller.connectedPort()));
                snapshot.line("HOME 查询命令",controller.capabilities.resolve);
                snapshot.line("HOME 切换命令",controller.capabilities.home);
            }
            SharedPreferences preferences=context.getSharedPreferences("adb",0);
            snapshot.line("最近成功连接方式（历史记录）",preferences.getString("lastTransport","未记录"));
            snapshot.line("TCP 配置端口（非可达性结论）",Integer.toString(preferences.getInt("port",5555)));
            snapshot.line("无线配置端口（非可达性结论）",preferences.getString("tlsPort","未记录"));
        });
        return snapshot.text.toString();
    }
    @android.annotation.SuppressLint("ObsoleteSdkInt") // Shared with the API19 build.
    private void device(){
        line("品牌",Build.BRAND);line("制造商",Build.MANUFACTURER);line("型号",Build.MODEL);
        line("Android 版本",Build.VERSION.RELEASE);line("Android API",Integer.toString(Build.VERSION.SDK_INT));
        line("固件显示版本",Build.DISPLAY);line("系统构建号",Build.ID);line("固件指纹",Build.FINGERPRINT);
        line("CPU 架构",Build.VERSION.SDK_INT>=21?Arrays.toString(Build.SUPPORTED_ABIS):Build.CPU_ABI);
        line("Quiet TV 版本",BuildConfig.VERSION_NAME);line("Quiet TV 构建号",Integer.toString(BuildConfig.VERSION_CODE));
        line("Quiet TV 包名",context.getPackageName());line("兼容档位",BuildConfig.LEGACY_19?"API19 兼容档位":"com.coocaa.quiettv".equals(context.getPackageName())?"酷开档位":"标准档位");
        line("安装包最低 API",Build.VERSION.SDK_INT>=24?Integer.toString(context.getApplicationInfo().minSdkVersion):"当前系统不支持直接读取安装包最低 API");
    }
    private void debugSettings(){
        setting("USB 调试开关",Settings.Global.ADB_ENABLED);
        setting("无线调试开关",AdbStateReader.WIFI);
        setting("开发者选项开关",Settings.Global.DEVELOPMENT_SETTINGS_ENABLED);
        entry("开发者设置入口",Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        entry("无线调试设置入口","android.settings.WIRELESS_DEBUGGING_SETTINGS");
        line("接口范围","入口可解析不代表页面可正常打开；USB 调试、网络 ADB 和本应用授权分别判断；未检测物理 USB 插口或线缆");
    }
    private void setting(String label,String key){
        try{int value=Settings.Global.getInt(context.getContentResolver(),key,-1);
            line(label,value==1?"已观测到开启（系统返回 1）":"无法确认（系统返回 "+value+"，可能被固件隐藏，不能据此判定关闭）");
        }catch(SecurityException e){line(label,"无法读取：权限受限");}
    }
    private void entry(String label,String action){
        ResolveInfo value=pm.resolveActivity(new Intent(action),0);
        ActivityInfo info=value==null?null:value.activityInfo;
        if(info==null){line(label,"未解析到标准入口，厂商隐藏入口未确认");return;}
        boolean usable=info.exported&&info.enabled&&info.applicationInfo.enabled
            &&(info.permission==null||pm.checkPermission(info.permission,context.getPackageName())==PackageManager.PERMISSION_GRANTED);
        line(label,new ComponentName(info.packageName,info.name).flattenToString()+"  "+(usable?"可解析，未启动验证":"不可直接访问"));
    }
    private void home() throws Exception {
        HomeController.State state=new HomeController(context).inspect(null);
        line("检测来源","PackageManager HOME 解析；未发起或新建 ADB 连接");
        line("默认 HOME 结论",state.known?state.reason:"无法确认  "+state.reason);
        line("HOME 原始解析",state.resolved);
        if(state.known)component("当前默认桌面",state.actual);
        line("说明","系统默认 HOME 与当前前台应用、桌面守护接管是不同状态");
    }
    private void original() throws Exception {
        JSONObject baseline=new SetupStore(context).get().optJSONObject("baseline");
        line("记录来源","本 Android 用户、当前固件的首次设置基线");
        if(baseline==null){line("原桌面","未记录，不能由其他桌面候选推定");return;}
        line("切换前 HOME（历史记录）",baseline.optString("previousHome"));
        component("原桌面",baseline.optString("originalHome"));
    }
    private void guard() throws Exception {
        // Diagnostics must not perform preference migrations or request recovery.
        HomeGuardState state=new HomeGuardState(context,false);
        line("系统档位支持",yes(state.supported()));
        line("已配置",yes(state.preferences.getBoolean("configured",false)));
        line("用户模式",state.preferences.getString("mode","未记录"));line("有效启用",yes(state.enabled()));
        component("守护目标桌面",state.selected());
        line("目标与固件有效",yes(state.validSelection()));
        line("无障碍设置授权",yes(state.serviceEnabled()));line("无障碍服务已连接",yes(HomeGuardService.connected()));
        line("独立中转助手占用",yes(state.helperActive()));
        line("兼容检测所需用量权限",HomeGuardService.usageRequired()?yes(HomeGuardService.usageAllowed(context)):"本档位不需要");
        line("恢复所需写安全设置权限",yes(HomeGuardRecovery.writeAllowed(context)));
        line("通知辅助恢复支持",yes(HomeGuardRecovery.notificationSupported(context)));
        line("通知访问授权",yes(HomeGuardRecovery.notificationAllowed(context)));
        line("系统已解锁",yes(HomeGuardState.unlocked(context)));line("屏幕交互中",yes(HomeGuardState.interactive(context)));
        String problem=state.enableProblem();
        line("当前守护结论",!state.enabled()?"未有效启用，不执行守护":!problem.isEmpty()?problem
            :!HomeGuardService.connected()?"等待系统绑定无障碍":"无障碍服务已连接，守护已启用");
        line("最近记录状态（历史记录）",state.preferences.getString("status","未记录"));
    }
    private void component(String label,String flattened){
        line(label+"组件",flattened);ComponentName name=ComponentName.unflattenFromString(flattened);
        if(name==null){line(label+"状态","身份未记录或无效");return;}
        try{
            ActivityInfo info=pm.getActivityInfo(name,HomeController.METADATA);
            PackageInfo pkg=pm.getPackageInfo(name.getPackageName(),HomeController.METADATA);
            line(label+"名称",info.loadLabel(pm).toString());line(label+"版本",pkg.versionName+"  构建号 "+pkg.versionCode);
            boolean installed=(info.applicationInfo.flags&ApplicationInfo.FLAG_INSTALLED)!=0;
            int app=pm.getApplicationEnabledSetting(name.getPackageName()),activity=pm.getComponentEnabledSetting(name);
            boolean enabled=(app==0?info.applicationInfo.enabled:app==1)&&(activity==0?info.enabled:activity==1);
            line(label+"状态",installed?(enabled?"已安装，已启用":"已安装，已禁用"):"当前用户未安装");
        }catch(PackageManager.NameNotFoundException e){line(label+"状态","当前包管理器未找到，卸载或不可见原因未确认");}
        catch(SecurityException e){line(label+"状态","无法读取：权限受限");}
    }
    private void section(String title,Read read){
        text.append("\n[").append(title).append("]\n");
        try{read.run();}catch(Exception e){line("采集状态","部分信息无法读取："+e.getClass().getSimpleName());}
    }
    private void line(String label,String value){
        String cleaned=DiagnosticJournal.clean(value);if(cleaned.length()>160)cleaned=cleaned.substring(0,160)+"…";
        text.append(label).append("：").append(cleaned.isEmpty()?"未记录或未检测到":cleaned).append('\n');
    }
    private static String yes(boolean value){return value?"是":"否";}
}
