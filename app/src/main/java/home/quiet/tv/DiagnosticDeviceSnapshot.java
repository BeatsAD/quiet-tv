package home.quiet.tv;

import android.content.*;
import android.content.pm.*;
import android.os.Build;
import android.provider.Settings;
import org.json.*;
import java.util.*;

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
        snapshot.section("系统设置页面采集",()->snapshot.text.append(SettingsPageCapture.summary()).append('\n'));
        snapshot.section("系统默认桌面",snapshot::home);
        snapshot.section("原桌面记录",snapshot::original);
        snapshot.section("HOME 候选与状态",snapshot::homeCandidates);
        snapshot.section("厂商私有启动路由",snapshot::privateHome);
        snapshot.section("桌面守护",snapshot::guard);
        snapshot.section("儿童模式计时",()->{
            snapshot.line("服务状态",ChildUsageService.status(context).message);
            Boolean allowed=ChildUsageService.allowed(context);snapshot.line("设置授权",allowed==null?"无法读取":allowed?"是":"否");
            snapshot.line("服务实际连接",ChildUsageService.connected()?"是":"否");
            snapshot.line("故障",new ChildModeStore(context).fault().isEmpty()?"未记录":new ChildModeStore(context).fault());
        });
        snapshot.section("HOME 与输入事件",()->snapshot.text.append(InputDiagnostics.summary()).append('\n'));
        if(LegacyHomeKeyService.supported())snapshot.section("安卓4.4主页键修复",snapshot::legacyHomeKey);
        snapshot.section("开机接收组件",snapshot::bootReceiver);
        snapshot.section("厂商后台管理组件",snapshot::backgroundManagers);
        snapshot.section("本应用进程退出历史",snapshot::processExits);
        return snapshot.text.toString();
    }
    static String adb(Context context){
        ManagementAdb management=ManagementAdb.existing();
        String debug="无法确认";
        try{int value=Build.VERSION.SDK_INT>=17?Settings.Global.getInt(context.getContentResolver(),"adb_enabled",-1):Settings.Secure.getInt(context.getContentResolver(),"adb_enabled",-1);if(value==1)debug="已开启";}catch(SecurityException ignored){}
        return "电脑 ADB 连接：需由电脑核对\n系统调试开关："+debug+"\n应用内管理连接："+(management==null?"本进程尚未建立":management.state().name()+"  原因分类："+management.adb.failure().name())
            +"\n保持 ADB 调试："+HomeGuardAdb.summary(context)+"\n";
    }
    @android.annotation.SuppressLint("ObsoleteSdkInt") // Shared with the API19 build.
    private void device() throws JSONException {
        JSONObject info=new JSONObject().put("schemaVersion",1);
        deviceField(info,"brand","品牌",Build.BRAND);deviceField(info,"manufacturer","制造商",Build.MANUFACTURER);deviceField(info,"model","型号",Build.MODEL);
        deviceField(info,"device","设备代号",Build.DEVICE);deviceField(info,"product","产品代号",Build.PRODUCT);
        deviceField(info,"hardware","硬件平台",Build.HARDWARE);deviceField(info,"board","主板代号",Build.BOARD);
        deviceField(info,"androidVersion","Android 版本",Build.VERSION.RELEASE);deviceField(info,"sdk","Android API",Build.VERSION.SDK_INT);
        deviceField(info,"display","固件显示版本",Build.DISPLAY);deviceField(info,"buildId","系统构建号",Build.ID);
        deviceField(info,"incremental","系统增量版本",Build.VERSION.INCREMENTAL);deviceField(info,"fingerprint","固件指纹",Build.FINGERPRINT);
        deviceField(info,"securityPatch","安全补丁日期",Build.VERSION.SDK_INT>=23?Build.VERSION.SECURITY_PATCH:null);
        JSONArray abis=new JSONArray();for(String abi:Build.VERSION.SDK_INT>=21?Build.SUPPORTED_ABIS:new String[]{Build.CPU_ABI})abis.put(deviceValue(abi));
        deviceField(info,"abis","CPU 架构",abis);
        deviceField(info,"appVersion","Quiet TV 版本",BuildConfig.VERSION_NAME);deviceField(info,"appBuild","Quiet TV 构建号",BuildConfig.VERSION_CODE);
        deviceField(info,"appPackage","Quiet TV 包名",context.getPackageName());
        deviceField(info,"declaresHome","安装包声明 HOME",BuildConfig.DECLARES_HOME);
        deviceField(info,"androidUser","当前进程 Android 用户",android.os.Process.myUid()/100000);
        deviceField(info,"targetSdk","安装包目标 API",context.getApplicationInfo().targetSdkVersion);
        deviceField(info,"compatibility","兼容档位",BuildConfig.LEGACY_19?BuildConfig.UPDATE_COMPATIBILITY:"com.coocaa.quiettv".equals(context.getPackageName())?"coocaa":"standard");
        deviceField(info,"minSdk","安装包最低 API",Build.VERSION.SDK_INT>=24?context.getApplicationInfo().minSdkVersion:null);
        deviceField(info,"homeProxyPackage","HOME 兼容助手包名",new HomeProxy(context).packageName());
        line("设备信息范围","系统 Build 字段；未读取用户设置的设备名称、序列号、Android ID、MAC 或账号。厂商界面的商品名及 HyperOS/MIUI 名称可能未包含在系统版本字段中，缺失值为 null，不能据此推定版本。");
        text.append("\n[设备统计 JSON]\n").append(info).append('\n');
    }
    // These allowlisted Build values include dotted firmware versions; IP redaction would corrupt them.
    static String deviceValue(String value){
        if(value==null)return "";
        return value.substring(0,Math.min(value.length(),512)).replaceAll("[\\p{Cntrl}]"," ").trim();
    }
    private void deviceField(JSONObject info,String key,String label,Object value) throws JSONException {
        if(value instanceof String){value=deviceValue((String)value);if(((String)value).isEmpty() || "unknown".equalsIgnoreCase((String)value))value=null;}
        info.put(key,value==null?JSONObject.NULL:value);
        text.append(label).append("：").append(value==null?"未提供或当前系统不支持读取":value).append('\n');
    }
    private void debugSettings(){
        setting("USB 调试开关",Settings.Global.ADB_ENABLED);
        setting("无线调试开关","adb_wifi_enabled");
        setting("开发者选项开关",Settings.Global.DEVELOPMENT_SETTINGS_ENABLED);
        entry("开发者设置入口",Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        entry("无线调试设置入口","android.settings.WIRELESS_DEBUGGING_SETTINGS");
        line("接口范围","入口可解析不代表页面可正常打开；USB 调试、网络 ADB 和本应用授权分别判断；未检测物理 USB 插口或线缆");
    }
    private void setting(String label,String key){
        try{int value=Build.VERSION.SDK_INT>=17?Settings.Global.getInt(context.getContentResolver(),key,-1):Settings.Secure.getInt(context.getContentResolver(),key,-1);
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
        HomeController.State state=new HomeController(context).inspect();
        line("检测来源","PackageManager HOME 解析；未发起或新建 ADB 连接");
        line("默认 HOME 结论",state.known?state.reason:"无法确认  "+state.reason);
        line("HOME 原始解析",state.resolved);
        if(state.known)component("当前默认桌面",state.actual);
        line("说明","系统默认 HOME 与当前前台应用、桌面守护接管是不同状态");
    }
    private void original() throws Exception {
        SetupPresets presets=SetupPresets.load(context);
        line("当前固件预设匹配",yes(presets.matched()));line("当前预设原桌面组件",presets.home());
        line("记录含义","切换前 HOME 是历史解析；原桌面是经机型预设核实的恢复目标，两者分别记录");
        JSONObject baseline=new SetupStore(context).get().optJSONObject("baseline");
        line("记录来源","本 Android 用户、当前固件的首次设置基线");
        if(baseline==null){line("原桌面","未记录，不能由其他桌面候选推定");return;}
        line("切换前 HOME（历史记录）",baseline.optString("previousHome"));
        component("切换前 HOME 的当前状态",baseline.optString("previousHome"));
        component("原桌面",baseline.optString("originalHome"));
    }
    private void homeCandidates(){
        HomeGuardState guard=new HomeGuardState(context,false);
        Set<String> eligible=new HashSet<>();for(HomeGuardState.Candidate candidate:guard.candidates())eligible.add(candidate.component.flattenToString());
        Map<String,String> homes=new LinkedHashMap<>();
        homeCandidates(homes,Intent.CATEGORY_HOME);
        if(HomeGuardRecovery.knownPrivateHome())for(String category:new String[]{"android.intent.category.HOME.CC","android.intent.category.HOME.CC_MAIN","smartdevice.intent.category.LITE_HOME"})homeCandidates(homes,category);
        for(String candidate:eligible)if(!homes.containsKey(candidate))homes.put(candidate,"已核对的系统私有组件，无标准 HOME 声明");
        line("查询范围","当前用户可见的 HOME 声明，含禁用或保留的未安装记录；不代表页面可启动");
        line("HOME 候选总数",Integer.toString(homes.size()));line("守护可选候选总数",Integer.toString(eligible.size()));
        int shown=0;
        for(Map.Entry<String,String> home:homes.entrySet()){
            if(shown>=8)break;
            String label="候选 "+(++shown);component(label,home.getKey());
            line(label+"声明类别",home.getValue());line(label+"可供守护选择",yes(eligible.contains(home.getKey())));
        }
        line("未展开候选数",Integer.toString(homes.size()-shown));
        line("守护筛选规则","排除本应用、非系统应用、禁用应用或组件、android 包及 FallbackHome；固件变化会重置场景验证，专项入口另核对固件");
    }
    private void privateHome(){
        if(HomeGuardRecovery.knownPrivateHome()){
            for(String key:CoocaaHomeRoutes.KEYS){String value=CoocaaHomeRoutes.read(key);line(key,value==null?"无法读取":value.isEmpty()?"空值":value);}
            line("酷开路由范围","只读属性；候选须另核对本机已安装的系统组件，不代表实体 HOME、开机或唤醒通过");
        }
        // Keep optional router discovery separate from existing HOME candidate diagnostics.
        Map<String,String> routers=new LinkedHashMap<>();homeCandidates(routers,"android.intent.category.HOME.JM");
        line("HOME.JM 可见声明数",Integer.toString(routers.size()));
        int shown=0;for(String router:routers.keySet()){if(shown>=4)break;component("私有路由 "+(++shown),router);}
        line("未展开路由数",Integer.toString(routers.size()-shown));
        line("判断范围","当前用户可见声明，含禁用或保留的未安装记录；仅用于诊断，不自动加入守护。声明不证明实体按键或开机实际路径，无可见声明不证明没有私有路由。");
    }
    private void homeCandidates(Map<String,String> homes,String category){
        for(ResolveInfo value:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),HomeController.METADATA)){
            ActivityInfo info=value.activityInfo;if(info==null)continue;
            String name=new ComponentName(info.packageName,info.name).flattenToString(),previous=homes.get(name);
            homes.put(name,previous==null?category:previous+"  "+category);
        }
    }
    private void legacyHomeKey(){
        line("匹配平台","Hisense VIDAA_TV  Hi3751  API19");
        line("服务组件启用原值",Integer.toString(pm.getComponentEnabledSetting(new ComponentName(context,LegacyHomeKeyService.class))));
        line("默认 HOME 为本应用",yes(LegacyHomeKeyService.defaultHome(context)));
        line("无障碍授权",yes(LegacyHomeKeyService.authorized(context)));
        line("服务已连接",yes(LegacyHomeKeyService.connected()));
        line("按键过滤请求",yes(LegacyHomeKeyService.filtering()));
        line("验证范围","只处理 HOME；不依赖桌面守护、不读取窗口内容。授权、连接、请求过滤和实体 HOME 效果分别判断，实际效果待用户验证。");
    }
    private void guard() throws Exception {
        // Diagnostics must not perform preference migrations or request recovery.
        HomeGuardState state=new HomeGuardState(context,false);
        line("系统档位支持",yes(state.supported()));
        line("已配置",yes(state.preferences.getBoolean("configured",false)));
        line("用户模式",state.preferences.getString("mode","未记录"));line("配置开关启用",yes(state.enabled()));
        line("无障碍守护就绪",yes(state.compatibleReady()));
        component("守护目标桌面",state.selected());
        line("目标组件仍有效",yes(state.validSelection()));
        line("记录固件与当前一致",yes(Build.FINGERPRINT.equals(state.preferences.getString("firmware",""))));
        line("无障碍设置授权",yes(state.serviceEnabled()));line("无障碍服务已连接",yes(HomeGuardService.connected()));
        String configurationProblem=HomeGuardRecovery.configurationProblem(context);
        line("完整配置缺项",!state.enabled()?"当前未使用守护中转":configurationProblem.isEmpty()?"未检测到缺项，场景效果仍待验证":configurationProblem);
        line("保持 ADB 调试",HomeGuardAdb.summary(context));
        line("厂商自启动配置（历史记录）",state.preferences.getString("vendorAutoStartStatus","未配置或未记录"));
        line("直接 HOME 按键补偿已配置",yes(state.directHomeConfigured()));line("显式 HOME 接管条件有效",yes(state.homeActionReady()));
        line("HOME 按键过滤能力",HomeGuardService.connected()?yes(HomeGuardService.keyFilterCapable()):"服务未连接，未读取");
        line("HOME 按键过滤请求",yes(HomeGuardService.keyFiltering()));line("首页生命周期前台",yes(HomeGuardService.homeResumed()));
        line("独立中转助手占用",yes(state.helperActive()));
        line("兼容检测所需用量权限",HomeGuardService.usageRequired(context)?yes(HomeGuardService.usageAllowed(context)):"本档位不需要");
        line("恢复所需写安全设置权限",yes(HomeGuardRecovery.writeAllowed(context)));
        line("通知辅助恢复支持",yes(HomeGuardRecovery.notificationSupported(context)));
        line("通知访问授权",yes(HomeGuardRecovery.notificationAllowed(context)));
        line("系统已解锁",yes(HomeGuardState.unlocked(context)));line("屏幕交互中",yes(HomeGuardState.interactive(context)));
        String problem=state.enableProblem();
        line("当前守护结论",!state.enabled()?"配置开关未开启，不执行守护":!problem.isEmpty()?problem
            :!HomeGuardService.connected()?"等待系统绑定无障碍":state.summary());
        line("最近记录状态（历史记录）",state.preferences.getString("status","未记录"));
    }
    private void bootReceiver() throws PackageManager.NameNotFoundException {
        ComponentName name=new ComponentName(context,HomeGuardBootReceiver.class);
        ActivityInfo info=pm.getReceiverInfo(name,HomeController.METADATA);
        line("守护档位支持",yes(!BuildConfig.LEGACY_19));
        line("接收组件",name.flattenToString());
        line("应用启用设置原值",Integer.toString(pm.getApplicationEnabledSetting(context.getPackageName())));
        line("组件启用设置原值",Integer.toString(pm.getComponentEnabledSetting(name)));
        line("ApplicationInfo.enabled",yes(info.applicationInfo.enabled));line("ReceiverInfo.enabled",yes(info.enabled));
        line("应用停止状态",yes((info.applicationInfo.flags&ApplicationInfo.FLAG_STOPPED)!=0));
        line("说明","只读当前用户的组件状态；启用不证明收到开机广播。API19 档默认关闭守护组件，不应当作厂商禁用。");
    }
    private void backgroundManagers(){
        int found=0;
        for(String pkg:new String[]{"com.tcl.guard","com.tcl.tvweishi","com.tcl.appstatecontroller","com.tcl.providers.config"}){
            try{
                PackageInfo info=pm.getPackageInfo(pkg,HomeController.METADATA);
                line(pkg,"版本 "+info.versionName+"  构建号 "+info.versionCode+"  当前用户安装 "+yes(AppAvailability.installed(pm,info.applicationInfo)));
                line(pkg+"启用设置",Integer.toString(pm.getApplicationEnabledSetting(pkg)));found++;
            }catch(PackageManager.NameNotFoundException ignored){/* Missing or invisible; never infer protection. */}
        }
        line("可见组件数",Integer.toString(found));
        line("判断范围","包存在及自启动授权不证明后台清理豁免；包不可见不证明没有厂商清理。未读取或修改厂商白名单、数据库或系统组件。");
    }
    private void processExits() throws JSONException {
        if(Build.VERSION.SDK_INT<30){line("采集状态","当前系统无标准进程退出历史接口，需结合电脑日志");return;}
        android.app.ActivityManager manager=(android.app.ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        if(manager==null){line("采集状态","系统未提供 ActivityManager");return;}
        line("低内存退出原因支持",yes(android.app.ActivityManager.isLowMemoryKillReportSupported()));
        JSONArray records=new JSONArray();
        for(android.app.ApplicationExitInfo info:manager.getHistoricalProcessExitReasons(context.getPackageName(),0,5)){
            records.put(new JSONObject().put("timestampMs",info.getTimestamp()).put("pid",info.getPid())
                .put("reason",info.getReason()).put("status",info.getStatus()).put("importance",info.getImportance()));
        }
        text.append("退出历史 JSON：").append(records).append('\n');
        line("判断范围","仅本应用最近最多5条系统历史；空列表不证明未退出。原因码和信号不能单独认定厂商清理；未读取堆栈、描述或其他应用历史，需按时间对齐复现。");
    }
    private void component(String label,String flattened){
        line(label+"组件",flattened);ComponentName name=ComponentName.unflattenFromString(flattened);
        if(name==null){line(label+"状态","身份未记录或无效");return;}
        try{
            ActivityInfo info=pm.getActivityInfo(name,HomeController.METADATA);
            PackageInfo pkg=pm.getPackageInfo(name.getPackageName(),HomeController.METADATA);
            line(label+"名称",info.loadLabel(pm).toString());line(label+"版本",pkg.versionName+"  构建号 "+pkg.versionCode);
            boolean installed=AppAvailability.installed(pm,info.applicationInfo);
            int app=pm.getApplicationEnabledSetting(name.getPackageName()),activity=pm.getComponentEnabledSetting(name);
            boolean enabled=(app==0?info.applicationInfo.enabled:app==1)&&(activity==0?info.enabled:activity==1);
            line(label+"状态",installed?(enabled?"已安装，已启用":"已安装，已禁用"):"当前用户未安装");
            line(label+"系统应用",yes((info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0));
            line(label+"导出",yes(info.exported));line(label+"组件所需权限",info.permission==null?"无":info.permission);
            line(label+"本应用具备组件权限",yes(info.permission==null || pm.checkPermission(info.permission,context.getPackageName())==PackageManager.PERMISSION_GRANTED));
            line(label+"应用启用设置原值",Integer.toString(app));line(label+"组件启用设置原值",Integer.toString(activity));
            line(label+" ApplicationInfo.enabled",yes(info.applicationInfo.enabled));line(label+" ActivityInfo.enabled",yes(info.enabled));
            line(label+"别名目标",info.targetActivity==null?"非别名":info.targetActivity);
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
