package home.quiet.tv;

import android.content.Context;
import java.io.IOException;
import java.io.File;
import java.util.*;
import org.json.*;

/** Explicit phone fields over shared TV configuration. No arbitrary preference keys are accepted. */
final class PhoneSettingsConfiguration implements PhoneSettingsState.Source {
    private final Context context;
    private final String fallback;
    private final boolean chinese;
    private final Runnable changed,mediaNamesChanged;
    private final PhoneSettingsPreview preview;
    private final PhoneSettingsIcons icons;
    private final String scope=UUID.randomUUID().toString();
    private final PhoneSettingsFiles files;private final PhoneSettingsBackups backups;private final PhoneSettingsUpdates updates;private final PhoneSettingsHelp help;private final PhoneSettingsGeneral general;private final PhoneSettingsChildren children;private final PhoneSettingsApplications applications;private final PhoneSettingsActions actions;private final PhoneSettingsTasks tasks;private final PhoneSettingsMedia media;
    PhoneSettingsConfiguration(Context context,boolean chinese,String fallback,PhoneSettingsTasks tasks,Runnable changed,Runnable mediaNamesChanged,Runnable ended){this.context=context;this.chinese=chinese;this.fallback=fallback;this.tasks=tasks;this.changed=changed;this.mediaNamesChanged=mediaNamesChanged;icons=new PhoneSettingsIcons(context,tasks,scope,changed);actions=new PhoneSettingsActions(context);children=new PhoneSettingsChildren(context,changed);applications=new PhoneSettingsApplications(context,tasks,scope,changed,children.parent);general=new PhoneSettingsGeneral(context,tasks,scope,changed);preview=PhoneSettingsPreview.get(context);files=new PhoneSettingsFiles(context,tasks,scope,changed);backups=new PhoneSettingsBackups(context,tasks,scope,changed,ended,files);updates=new PhoneSettingsUpdates(context);help=new PhoneSettingsHelp(context,tasks,scope);media=new PhoneSettingsMedia(context,tasks,scope,fallback,changed,files,backups);}
    void offerFile(String id)throws IOException{files.offer(id);}
    public String[] domains(){return new String[]{"appearance","feedback","greeting","wallpaper","dock","buttons","weather","catalog","quick","screensaver","uploads","tasks","audio","apps","child","childUsage","quiz","parent","weatherInfo","boot","guard","management","inputs","files","memos","backup","update","diagnostics","components","desktopSwitch","bootAds","icons"};}
    public Object lock(String domain){switch(domain){case "icons":return AppCoverStore.get(context);case "desktopSwitch":return DesktopSwitch.get(context);case "bootAds":return BootAdSettings.get(context);case "boot":return BootAppStore.class;case "inputs":case "guard":case "management":return general;case "files":return files;case "memos":return MemoStore.LOCK;case "backup":return backups;case "update":return updates;case "diagnostics":return help;case "child":case "childUsage":case "parent":return ChildModeStore.LOCK;case "quiz":return ChildQuizStore.LOCK;case "wallpaper":case "screensaver":return WallpaperStore.get(context);case "buttons":return HomeButtonStore.LOCK;case "quick":return QuickControlStore.LOCK;case "catalog":return preview;case "tasks":return tasks;default:return DesktopSettingsStore.MUTATIONS;}}
    public JSONObject read(String domain)throws IOException {
        DesktopSettingsStore desktop=new DesktopSettingsStore(context);
        try{
            switch(domain){
                case "appearance":Calendar clockDay=Calendar.getInstance();return new JSONObject().put("homeBrightness",desktop.homeWallpaperBrightness()).put("homeBlur",desktop.homeWallpaperBlur()).put("homeBlurMode",WallpaperGpuSupport.available(context)?desktop.homeBlurMode():"uniform").put("homeFade",desktop.homeWallpaperFade())
                    .put("secondBlur",desktop.secondWallpaperBlur()).put("secondBrightness",desktop.secondWallpaperBrightness()).put("secondFade",desktop.secondWallpaperFade()).put("secondNames",desktop.showAllAppsNames()).put("secondHideHomeApps",desktop.hideHomeAppsOnSecondScreen()).put("showWatchTv",desktop.showWatchTv())
                    .put("layout",desktop.layout()).put("iconSize",desktop.iconSize()).put("iconShape",desktop.iconShape()).put("showTime",desktop.showTime()).put("clockStyle",desktop.clockStyle()).put("showDate",desktop.showDate()).put("showSeconds",desktop.showSeconds()).put("showLunarDate",desktop.showLunarDate()).put("showMovieInfo",desktop.showMovieInfo()).put("showHomeQuote",desktop.showHomeQuote()).put("showScreensaverQuote",desktop.showScreensaverQuote()).put("dailyQuote",dailyQuote()).put("lunarDate",desktop.showLunarDate()?LunarDate.format(clockDay):"").put("lunarDateFor",desktop.showLunarDate()?LunarDate.key(clockDay):"").put("homeApps",desktop.showHomeApps()).put("appNames",desktop.appTitlePosition());
                case "child":return children.configuration();case "childUsage":return children.usage();case "quiz":return children.quiz();case "parent":return children.parent.state();
                case "apps":return applications.state();case "icons":return icons.state();
                case "audio":return actions.audio();
                case "feedback":return new JSONObject().put("keySounds",desktop.keySounds()).put("keySoundTheme",desktop.keySoundTheme().id);
                case "greeting":return new JSONObject().put("greeting",desktop.greetingText(fallback)).put("automaticGreeting",DesktopSettingsStore.automaticGreeting(chinese,Calendar.getInstance().get(Calendar.HOUR_OF_DAY))).put("showGreeting",desktop.greetingEnabled()).put("showAvatar",desktop.avatarEnabled()).put("avatar",desktop.avatarFile().isEmpty()?"":"api/resources/avatar/"+desktop.avatarFile());
                case "wallpaper":{
                    WallpaperStore store=WallpaperStore.get(context);WallpaperCatalog catalog=store.snapshot();String mode=catalog.weather&&WallpaperGpuSupport.available(context)?"weather":WallpaperCatalog.DAILY.equals(catalog.current)?"daily":catalog.rotating?"rotation":"fixed";
                    return new JSONObject().put("wallpaperBuiltinHidden",catalog.wallpaperBuiltinHidden).put("wallpaper",catalog.current).put("wallMode",mode).put("dailySource",DailyWallpaperLoader.source(catalog)).put("dailyPending",store.dailyPending()).put("dailyWallpaper",dailyInfo()).put("rotation",catalog.rotationMinutes).put("wallVideo",catalog.dynamic()).put("wallSound",catalog.audible()).put("dayNight",catalog.weatherDayNight).put("manualVersion",store.manualVersion()).put("previewKey",store.imageKey(catalog.current));
                }
                case "dock":return new JSONObject().put("dockScale",desktop.appearance("dock-scale")).put("dockMagnification",desktop.appearance("dock-magnification")).put("dockLimit",desktop.appearance("dock-limit")).put("dockMagnify",desktop.dockMagnify()).put("globalGlass",desktop.globalGlass()).put("dockGlass",desktop.globalGlass()).put("dockBackground",desktop.showDockBackground()).put("dockApps",new JSONArray(new DockStore(context).load())).put("gridOrder",new JSONArray(new HomeOrderStore(context).load()));
                case "buttons":return new JSONObject().put("homeButtons",new JSONArray(new HomeButtonStore(context).read().ids)).put("quickControlVisibility",desktop.quickControlVisibility());
                case "files":return files.state();case "memos":return files.notes();case "backup":return backups.state();case "update":return updates.state();case "diagnostics":return help.diagnosticState();
                case "weather":return general.weather();case "weatherInfo":return general.weatherInfo();case "boot":return general.startup();case "guard":return general.guard();case "management":return general.management();case "inputs":return general.inputs();
                case "components":return home.quiet.components.ComponentRepository.get(context).snapshot();
                case "desktopSwitch":return DesktopSwitch.get(context).state();
                case "bootAds":return BootAdSettings.get(context).snapshot();
                case "catalog":return preview.signature().put("media",PhoneSettingsState.revision(library()));
                case "quick":return new JSONObject().put("quickControls",new JSONArray(quick().read().ids));
                case "screensaver":{WallpaperCatalog screensaverCatalog=WallpaperStore.get(context).snapshot();return saverValues(ScreensaverStore.get(context).snapshot(screensaverCatalog),screensaverCatalog);}
                case "uploads":return new JSONObject().put("policyVersion",1);
                case "tasks":return tasks.snapshot(scope);
                default:throw new IllegalArgumentException();
            }
        }catch(JSONException e){throw new IOException(e);}
    }
    public JSONObject metadata()throws IOException {
        try{
            JSONObject fields=new JSONObject()
                .put("homeBrightness",field("appearance","背景明暗","range").put("min",20).put("max",100).put("step",1).put("unit","%"))
                .put("keySounds",field("feedback","按键音","toggle"))
                .put("greeting",field("greeting","问候语","text").put("maxLength",GreetingInputServer.MAX_TEXT))
                .put("wallpaper",field("wallpaper","桌面背景","select"));
            desktopFields(fields);
            choice(fields,"keySoundTheme","feedback","按键音色",KeySoundTheme.ids(),KeySoundTheme.titles(true));
            JSONArray quickCatalog=new JSONArray(),buttonCatalog=new JSONArray();JSONObject quickIcons=new JSONObject();java.util.Set<String> glyphs=new java.util.LinkedHashSet<>();
            for(String id:QuickControlStore.ALL){String glyph=QuickControlPreviewView.glyph(id);glyphs.add(glyph);quickCatalog.put(new JSONObject().put("id",id).put("title",QuickControlStore.title(id,true)).put("shortTitle",QuickControlStore.shortTitle(id,true)).put("glyph",glyph));}
            java.util.Collections.addAll(glyphs,"range-sun-low","range-sun-high","range-speaker-low","range-speaker-high","speaker-off","device-mobile","header-control");for(String glyph:glyphs)quickIcons.put(glyph,QuickControlIcon.description(glyph));
            for(String id:HomeButtonStore.EDITABLE)buttonCatalog.put(new JSONObject().put("id",id).put("title",HomeButtonStore.title(id,true)));
            mediaFields(fields);childFields(fields);generalFields(fields);JSONObject audio=actions.audio();range(fields,"volume","audio","音量",0,audio.getInt("maximum"),1,"",1);if(!audio.getBoolean("available"))fields.getJSONObject("volume").put("disabledReason","此电视音量由外部设备或系统控制");
            JSONObject metadata=new JSONObject().put("device",new JSONObject().put("name",android.os.Build.MODEL).put("version",BuildConfig.VERSION_NAME).put("build",BuildConfig.VERSION_CODE).put("sdk",android.os.Build.VERSION.SDK_INT).put("channel",BuildConfig.UPDATE_CHANNEL))
                .put("fields",fields).put("wallpapers",library()).put("uploadLimits",media.limits()).put("homeApps",preview.entries()).put("apps",applications.entries()).put("childApps",children.apps()).put("quickCatalog",quickCatalog).put("quickIcons",quickIcons).put("homeButtonCatalog",buttonCatalog)
                .put("iconLibrary",icons.metadata()).put("dailyWallpaper",dailyInfo()).put("actions",new JSONObject().put("backup.list",true).put("diagnostics.export",true).put("guard.open",true).put("bootAds.open",true).put("inputs.open",true).put("quick.run",true).put("quick.reset",true).put("quick.add",true).put("wallpaper.daily",true)).put("pollIntervalMs",1000);
            JSONObject extra=help.metadata();
            for(Iterator<String> keys=extra.keys();keys.hasNext();){
                String key=keys.next();metadata.put(key,extra.get(key));
            }
            long now=System.currentTimeMillis();
            Calendar local=new GregorianCalendar(TimeZone.getDefault());
            local.setTimeInMillis(now);
            JSONObject lunar=new JSONObject().put("date",LunarDate.key(local))
                .put("text",LunarDate.screenText(local)).put("monthStarts",new JSONArray(LunarDate.monthStarts(local)));
            return metadata.put("serverTimeMs",now).put("timeZone",local.getTimeZone().getID()).put("saverLunar",lunar);
        }catch(JSONException e){throw new IOException(e);}
    }
    void connectionEnded(){children.parent.end();}
    void closed(){connectionEnded();general.closed();updates.close();}
    void delivered(String id){backups.delivered(id);}
    PhoneSettingsServer.Download download(String route)throws IOException{String[] parts=route.split("/",2);if(parts.length!=2)throw new PhoneSettingsServer.BadInput();switch(parts[0]){case "file":return files.download(parts[1]);case "backup":return backups.download(parts[1]);case "diagnostics":return help.download(parts[1]);default:throw new PhoneSettingsServer.BadInput();}}
    JSONObject about(boolean runtime)throws IOException{return help.about(runtime);}
    JSONObject places(String query)throws IOException{return general.places(query);}
    JSONObject parent(JSONObject value,PhoneSettingsServer.Active active)throws IOException{requireRestoreIdle();JSONObject result=children.parent.execute(value,active);changed.run();return result;}
    PhoneSettingsServer.Download previewVideo(String id)throws IOException{
        if(!id.matches("user-[a-f0-9-]{36}"))throw new PhoneSettingsServer.BadInput();
        WallpaperStore store=WallpaperStore.get(context);synchronized(store){WallpaperCatalog.Entry entry=store.snapshot().find(id);if(entry==null||!entry.video())throw new PhoneSettingsServer.BadInput();java.io.File file=store.file(id);if(file==null||!file.isFile())throw new PhoneSettingsServer.BadInput();String mime=PhoneSettingsVideo.mime(file);return new PhoneSettingsServer.Download(new java.io.FileInputStream(file),file.length(),id,mime);}
    }
    byte[] resource(String route)throws IOException{return route.startsWith("icon-library/")?icons.resource(route.substring(13)):preview.resource(route);}
    JSONObject task(String id)throws IOException{PhoneSettingsTasks.Job job=tasks.find(id);if(job==null||!job.key.startsWith(scope+"/"))throw new PhoneSettingsServer.BadInput();try{return job.json();}catch(JSONException e){throw new IOException(e);}}
    JSONObject upload(String id,java.io.InputStream input,long length,PhoneSettingsServer.Active active)throws IOException{requireRestoreIdle();return media.upload(id,input,length,active);}
    private JSONArray library()throws JSONException{
        WallpaperStore store=WallpaperStore.get(context);WallpaperCatalog catalog=store.snapshot();JSONArray entries=new JSONArray();
        for(WallpaperCatalog.Entry entry:catalog.entries()){
            int width=entry.width,height=entry.height;long bytes=entry.bytes;String kind=WallpaperCatalog.DEFAULT.equals(entry.id)?"color":WallpaperCatalog.DAILY.equals(entry.id)?"daily":entry.video()?"video":"image";
            if("daily".equals(kind)){WallpaperCatalog.DailyImage image=catalog.dailyImages.get(catalog.dailySource);if(store.cached(image)){width=image.width;height=image.height;bytes=store.dailyFile(image.source,image.slot).length();}}
            entries.put(new JSONObject().put("id",entry.id).put("name",entry.label(true)).put("kind",kind).put("video",entry.video()).put("canRename",entry.custom()).put("width",width).put("height",height).put("bytes",bytes).put("durationMs",entry.durationMs));
        }return entries;
    }
    private JSONObject dailyInfo()throws JSONException{
        WallpaperStore store=WallpaperStore.get(context);WallpaperCatalog catalog=store.snapshot();WallpaperCatalog.DailyImage image=catalog.dailyImages.get(catalog.dailySource);
        JSONObject info=new JSONObject().put("source",WallpaperCatalog.sourceLabel(catalog.dailySource,true)).put("sourceId",DailyWallpaperLoader.source(catalog)).put("cached",store.cached(image));
        if(image!=null)info.put("title",image.title).put("copyright",image.copyright).put("day",image.day).put("width",image.width).put("height",image.height).put("bytes",store.cached(image)?store.dailyFile(image.source,image.slot).length():0);
        if(image!=null&&image.movie!=null)info.put("movie",image.movie.json().getJSONObject("movie")).put("contentDate",image.movie.contentDate).put("revision",image.movie.revision);
        return info;
    }
    private static JSONObject field(String domain,String title,String type)throws JSONException{return new JSONObject().put("domain",domain).put("title",title).put("type",type);}
    private static void range(JSONObject fields,String id,String domain,String title,int min,int max,int step,String unit,int divisor)throws JSONException{
        fields.put(id,field(domain,title,"range").put("min",min).put("max",max).put("step",step).put("unit",unit).put("divisor",divisor));
    }
    private static void choice(JSONObject fields,String id,String domain,String title,Object[] values,String[] titles)throws JSONException{
        JSONArray options=new JSONArray();for(int i=0;i<values.length;i++)options.put(new JSONObject().put("value",values[i]).put("title",titles[i]));fields.put(id,field(domain,title,"select").put("options",options));
    }
    private void desktopFields(JSONObject fields)throws JSONException{
        for(String prefix:new String[]{"home","second"}){
            range(fields,prefix+"Blur","appearance","模糊度",0,100,5,"%",1);
            range(fields,prefix+"Brightness","appearance","亮度",20,100,5,"%",1);
            range(fields,prefix+"Fade","appearance","淡入时间",0,1200,100,"秒",1000);
        }
        choice(fields,"homeBlurMode","appearance","首页模糊方式",new Object[]{"uniform","progressive"},new String[]{"均匀模糊","渐进模糊"});
        if(!WallpaperGpuSupport.available(context))fields.getJSONObject("homeBlurMode").put("disabledReason","此设备不支持渐进模糊，保留均匀模糊");
        choice(fields,"clockStyle","appearance","时钟样式",DesktopSettingsStore.CLOCK_STYLES,new String[]{"数字","白色表盘","深色表盘"});
        choice(fields,"layout","appearance","排版",new Object[]{"classic","gallery"},new String[]{"经典布局","艺术画廊"});
        choice(fields,"iconSize","appearance","图标大小",new Object[]{"small","medium","large"},new String[]{"小","中","大"});
        choice(fields,"iconShape","appearance","图标外形",new Object[]{"rectangle","square","circle"},new String[]{"长方形","方形","圆形"});
        choice(fields,"appNames","appearance","首页应用名称",new Object[]{"hidden","below","top-left"},new String[]{"不显示","下方","左上角"});
        String[][] toggles={{"showHomeQuote","首屏显示"},{"showScreensaverQuote","屏保显示"},{"showTime","时间"},{"showDate","日期"},{"showSeconds","显示秒数"},{"showLunarDate","显示农历"},{"homeApps","首页应用区域"},{"showWatchTv","在桌面显示「看电视」"},{"secondNames","第二屏应用名称"},{"secondHideHomeApps","第二屏隐藏首屏应用"}};
        for(String[] toggle:toggles)fields.put(toggle[0],field("appearance",toggle[1],"toggle"));
        fields.put("showGreeting",field("greeting","问候语","toggle")).put("showAvatar",field("greeting","显示头像","toggle"));
        fields.put("weatherEnabled",field("weather","天气","toggle"));
        DesktopSettingsStore desktop=new DesktopSettingsStore(context);
        if(!desktop.avatarEnabled()&&new GreetingAvatarStore(context).existing(desktop.avatarFile())==null)fields.getJSONObject("showAvatar").put("disabledReason","请先上传头像");
        range(fields,"dockScale","dock","Dock 栏大小",70,130,5,"%",1);range(fields,"dockMagnification","dock","放大幅度",100,160,5,"%",1);
        choice(fields,"dockLimit","dock","显示应用上限",new Object[]{5,7,10,12},new String[]{"5","7","10","12"});
        for(String[] toggle:new String[][]{{"dockMagnify","聚焦放大"},{"globalGlass","全局玻璃效果"},{"dockBackground","Dock 背景"}})fields.put(toggle[0],field("dock",toggle[1],"toggle"));
        fields.put("dockGlass",field("dock","全局玻璃效果","toggle")); // Compatibility for phone pages loaded before the global field was added.
        choice(fields,"quickControlVisibility","buttons","快捷控制按钮",DesktopSettingsStore.CONTROL_VISIBILITY,new String[]{"始终显示","聚焦时显示"});
        fields.put("homeButtons",field("buttons","首页按钮","order")).put("quickControls",field("quick","快捷控制","order")).put("dockApps",field("dock","Dock 应用","order")).put("gridOrder",field("dock","第二屏应用顺序","order"));
    }
    private void childFields(JSONObject fields)throws JSONException,IOException{
        fields.put("childEnabled",field("child","儿童模式","toggle"));choice(fields,"restTheme","child","休息屏保",new Object[]{1,0,2},new String[]{"云朵","森林","星空"});
        choice(fields,"quizInterval","quiz","答题间隔",new Object[]{10,15,20},new String[]{"10 分钟","15 分钟","20 分钟"});
        choice(fields,"quizGrade","quiz","年级",ChildQuizBank.GRADES,ChildQuizBank.GRADES);
    }
    private void generalFields(JSONObject fields)throws JSONException{
        fields.put("autoLocation",field("weather","自动定位","toggle")).put("cityCode",field("weather","天气城市","select"));
        List<Object> providers=new ArrayList<>();List<String> names=new ArrayList<>();WeatherClient client=new WeatherClient(context);for(String id:new String[]{"auto","qweather","uapi","openweather","openmeteo"})if(client.available(id)){providers.add(id);names.add("auto".equals(id)?"自动切换":WeatherClient.providerName(id));}choice(fields,"provider","weather","天气来源",providers.toArray(),names.toArray(new String[0]));
        List<Object> ids=new ArrayList<>();List<String> labels=new ArrayList<>();ids.add("");labels.add("QuietTV 首页");BootAppStore boot=new BootAppStore(context);for(android.content.pm.ResolveInfo app:new HomeAppCatalog(context).launchable().values()){String pkg=app.activityInfo.packageName;if(boot.launchIntent(pkg)!=null){ids.add(pkg);labels.add(new AppNameStore(context).display(pkg,app.loadLabel(context.getPackageManager()).toString()));}}if(boot.tested()){ids.add(BootAppStore.HDMI);labels.add(boot.watchLabel());}choice(fields,"boot","boot","开机去向",ids.toArray(),labels.toArray(new String[0]));
    }
    private QuickControlStore quick(){return new QuickControlStore(new File(context.getFilesDir(),"quick-controls"));}
    private void mediaFields(JSONObject fields)throws JSONException,IOException{
        boolean weather=WallpaperGpuSupport.available(context);choice(fields,"wallMode","wallpaper","切换方式",weather?new Object[]{"fixed","rotation","daily","weather"}:new Object[]{"fixed","rotation","daily"},weather?new String[]{"固定一张","自动轮换","每日更新","随天气变化"}:new String[]{"固定一张","自动轮换","每日更新"});
        choice(fields,"dailySource","wallpaper","图片来源",new Object[]{WallpaperCatalog.BING,WallpaperCatalog.MICROSOFT,WallpaperCatalog.MOVIE},new String[]{"必应","微软壁纸","每日电影"});
        choice(fields,"rotation","wallpaper","轮换间隔",new Object[]{1,5,10,15,30,60,"custom"},new String[]{"1 分钟","5 分钟","10 分钟","15 分钟","30 分钟","60 分钟","自定义"});
        WallpaperCatalog catalog=WallpaperStore.get(context).snapshot();fields.put("dayNight",field("wallpaper","随昼夜变化","toggle"));fields.put("showMovieInfo",field("appearance","显示影片信息","toggle"));
        if(catalog.dynamic()){fields.put("wallSound",field("wallpaper","背景声音","toggle"));if(catalog.rotating||catalog.weather)fields.getJSONObject("wallSound").put("disabledReason","当前方式保持静音");}
        Object[] waits=new Object[ScreensaverSettings.WAITS.length];String[] labels=new String[waits.length];for(int i=0;i<waits.length;i++){waits[i]=ScreensaverSettings.WAITS[i];labels[i]=ScreensaverSettings.WAITS[i]==0?"关闭":waits[i]+" 分钟后";}
        choice(fields,"idle","screensaver","闲置等待时间",waits,labels);choice(fields,"saverContent","screensaver","屏保内容",new Object[]{ScreensaverSettings.FOLLOW,ScreensaverSettings.CLOCK,ScreensaverSettings.FLIP,ScreensaverSettings.CUSTOM,ScreensaverSettings.WALL},new String[]{"跟随桌面","数字时间","翻页时钟","自定义","壁纸墙"});
        ScreensaverSettings saver=ScreensaverStore.get(context).snapshot(catalog);fields.put("saverSound",field("screensaver","屏保声音","toggle"));if(!saver.video(catalog))fields.getJSONObject("saverSound").put("disabledReason","选择视频屏保后可开启");
        fields.put("saverFlipSeconds",field("screensaver","显示秒","toggle")).put("saverFlipSound",field("screensaver","翻页声音","toggle"));
        fields.put("saverAnalogClock",field("screensaver","模拟时钟","toggle"))
            .put("saverCalendar",field("screensaver","日历","toggle"))
            .put("saverLunarDate",field("screensaver","显示农历","toggle"))
            .put("saverSeconds",field("screensaver","显示秒","toggle"));
        choice(fields,"saverAnalogStyle","screensaver","表盘样式",ScreensaverSettings.ClockOptions.STYLES,new String[]{"圆形","方形"});
        choice(fields,"saverDigitalLayout","screensaver","数字排版",ScreensaverSettings.ClockOptions.LAYOUTS,new String[]{"标准时间","大字日期"});
        fields.put("saverClock",field("screensaver","显示时钟","toggle")).put("saverWeather",field("screensaver","显示天气","toggle")).put("saverMedia",field("screensaver","屏保素材","select"));
        choice(fields,"saverClockPosition","screensaver","时钟位置",ScreensaverSettings.POSITIONS,new String[]{"左上角","右上角","居中","左下角","右下角"});choice(fields,"saverWeatherPosition","screensaver","天气位置",ScreensaverSettings.POSITIONS,new String[]{"左上角","右上角","居中","左下角","右下角"});
    }
    static JSONObject saverValues(ScreensaverSettings saver,WallpaperCatalog catalog)throws JSONException{
        return new JSONObject().put("saverBuiltinHidden",catalog.saverBuiltinHidden).put("idle",saver.idleMinutes).put("saverContent",saver.mode).put("saverClock",saver.showTime).put("saverWeather",saver.showWeather).put("saverSound",saver.sound).put("saverSoundActive",saver.sound&&saver.video(catalog)).put("saverFlipSeconds",saver.flipShowSeconds).put("saverFlipSound",saver.flipSound).put("saverMedia",saver.customMediaId(catalog)).put("saverClockPosition",saver.timePosition).put("saverWeatherPosition",saver.weatherPosition)
            .put("saverAnalogClock",saver.clock.analog).put("saverAnalogStyle",saver.clock.style)
            .put("saverDigitalLayout",saver.clock.layout).put("saverCalendar",saver.clock.calendar)
            .put("saverLunarDate",saver.clock.lunar).put("saverSeconds",saver.clock.seconds);}
    private void requireRestoreIdle()throws PhoneSettingsServer.Rejected{
        if(tasks.restoring()||DesktopBackups.get(context).restoreState().running())
            throw new PhoneSettingsServer.Rejected("桌面正在恢复，请等待完成后核对配置");
    }
    public PhoneSettingsState.Result write(String domain,String field,Object value,String expected,PhoneSettingsServer.Active active)throws IOException {
        if(!active.valid())throw new PhoneSettingsServer.Ended();
        requireRestoreIdle();
        if(domain.equals("bootAds")){
            JSONObject receipt=general.bootAds(field,value,active);
            return new PhoneSettingsState.Result(read(domain),receipt);
        }
        if(domain.equals("desktopSwitch"))return new PhoneSettingsState.Result(read(domain),general.desktopSwitch(field,value,active));
        if(domain.equals("components")){if(!"weather".equals(value)||!(field.equals("start")||field.equals("cancel")||field.equals("uninstall")))throw new PhoneSettingsServer.Rejected("组网功能请在手机助手App中使用");home.quiet.components.ComponentRepository repository=home.quiet.components.ComponentRepository.get(context);if(field.equals("start"))repository.start("weather");else if(field.equals("uninstall"))WeatherComponentActions.uninstall(context);else repository.cancel("weather");return new PhoneSettingsState.Result(repository.snapshot());}
        DesktopSettingsStore desktop=new DesktopSettingsStore(context);
        if(Arrays.asList("showHomeQuote","showScreensaverQuote","keySounds","showTime","showDate","showSeconds","showLunarDate","showMovieInfo","homeApps","showWatchTv","secondNames","secondHideHomeApps","showGreeting","showAvatar","dockMagnify","dockGlass","globalGlass","dockBackground","weatherEnabled","saverClock","saverWeather","saverSound","saverFlipSeconds","saverFlipSound","saverAnalogClock","saverCalendar","saverLunarDate","saverSeconds","dayNight","wallSound").contains(field)&&!(value instanceof Boolean))throw new IllegalArgumentException();
        if("catalog".equals(domain)&&"renameMedia".equals(field)){
            if(!(value instanceof JSONObject))throw new PhoneSettingsServer.BadInput();JSONObject request=(JSONObject)value;
            if(request.length()!=3||!(request.opt("id") instanceof String)||!(request.opt("originalName") instanceof String))throw new PhoneSettingsServer.BadInput();
            String id=(String)request.opt("id"),name=PhoneSettingsMediaName.read(request.opt("name"));
            if(!id.matches("user-[a-f0-9-]{36}"))throw new PhoneSettingsServer.BadInput();
            boolean renamed=WallpaperStore.get(context).rename(id,(String)request.opt("originalName"),name,active);if(renamed)mediaNamesChanged.run();
            try{return new PhoneSettingsState.Result(read(domain),new JSONObject().put("mediaId",id).put("name",name).put("renamed",true));}catch(JSONException error){throw new IOException(error);}
        }
        if("icons".equals(domain)){if(!(value instanceof JSONObject))throw new PhoneSettingsServer.BadInput();return new PhoneSettingsState.Result(read(domain),icons.command(field,(JSONObject)value,active));}
        if("files".equals(domain)&&"wallpaper".equals(field)&&value instanceof String)return new PhoneSettingsState.Result(read(domain),media.fromFile((String)value,active));
        if("files".equals(domain)){JSONObject operation=files.command(field,value,active);return new PhoneSettingsState.Result(read(domain),operation);}
        if("memos".equals(domain)){files.note(field,value,active);return new PhoneSettingsState.Result(read(domain));}
        if("backup".equals(domain)){JSONObject operation=backups.command(field,value,active);return new PhoneSettingsState.Result(read(domain),operation);}
        if("update".equals(domain)){JSONObject operation=updates.command(field,value,active);return new PhoneSettingsState.Result(read(domain),operation);}
        if("diagnostics".equals(domain)){
            if("recording".equals(field)&&value instanceof Boolean){
                if(!active.valid())throw new PhoneSettingsServer.Ended();
                DiagnosticLog.setRecording(context,(Boolean)value);
                changed.run();
                return new PhoneSettingsState.Result(read(domain));
            }
            if(Boolean.TRUE.equals(value)&&("export".equals(field)||"exportInputs".equals(field)))
                return new PhoneSettingsState.Result(read(domain),help.export(field,active));
            throw new PhoneSettingsServer.BadInput();
        }
        if(Arrays.asList("weather","boot","guard","management","inputs").contains(domain)){JSONObject operation=general.write(domain,field,value,active);return new PhoneSettingsState.Result(read(domain),operation);}
        if("child".equals(domain)||"quiz".equals(domain)){children.write(domain,field,value,active);return new PhoneSettingsState.Result(read(domain));}
        if("apps".equals(domain)){if("renameApp".equals(field)&&value instanceof JSONObject){JSONObject operation=applications.rename((JSONObject)value,active);return new PhoneSettingsState.Result(read(domain),operation);}if("refresh".equals(field)&&Boolean.TRUE.equals(value))return new PhoneSettingsState.Result(read(domain),applications.refresh(active));if(value instanceof JSONObject)return new PhoneSettingsState.Result(read(domain),applications.perform(field,(JSONObject)value,active));throw new IllegalArgumentException();}
        if("audio".equals(domain)&&"volume".equals(field)){actions.volume(value,active);changed.run();return new PhoneSettingsState.Result(read(domain));}
        if("quick".equals(domain)&&"run".equals(field)&&value instanceof String)return new PhoneSettingsState.Result(read(domain),actions.run((String)value,active));
        if("uploads".equals(domain)&&"begin".equals(field)&&value instanceof JSONObject)return new PhoneSettingsState.Result(read(domain),media.begin((JSONObject)value,active));
        if("uploads".equals(domain)&&"cancel".equals(field)&&value instanceof String){PhoneSettingsTasks.Job job=tasks.find((String)value);if(job==null||!job.key.startsWith(scope+"/"))throw new IllegalArgumentException();tasks.cancel(job.id);return new PhoneSettingsState.Result(read(domain));}
        if("screensaver".equals(domain))return writeSaver(field,value,expected,active);
        if("appearance".equals(domain)&&"homeBrightness".equals(field))desktop.savePhoneValue("home-wallpaper-brightness",integer(value,20,100));
        else if("feedback".equals(domain)&&"keySounds".equals(field)&&value instanceof Boolean)desktop.savePhoneValue("key-sounds",value);
        else if("feedback".equals(domain)&&"keySoundTheme".equals(field)){desktop.keySoundTheme(value instanceof String?(String)value:"");KeySounds.play(context,KeySoundPolicy.CONFIRM);}
        else if("greeting".equals(domain)&&"greeting".equals(field))desktop.savePhoneValue("greeting-text",greeting(value));
        else if("wallpaper".equals(domain))return writeWallpaper(field,value,active);
        else if("appearance".equals(domain)){
            switch(field){
                case "homeBlur":case "homeBrightness":case "homeFade":case "secondBlur":case "secondBrightness":case "secondFade":boolean second=field.startsWith("second");String property=field.endsWith("Blur")?"blur":field.endsWith("Fade")?"fade":"brightness";desktop.saveWallpaperEffect(second,property,integer(value,property.equals("brightness")?20:0,property.equals("fade")?1200:100));break;
                case "homeBlurMode":if(!(value instanceof String)||"progressive".equals(value)&&!WallpaperGpuSupport.available(context))throw new IllegalArgumentException();desktop.homeBlurMode((String)value);break;
                case "clockStyle":if(!desktop.galleryLayout())throw new PhoneSettingsServer.Rejected("时钟样式仅在艺术画廊布局中可设置");desktop.saveAppearance("clock-style",value);break;
                case "layout":desktop.savePhoneValue("home-layout",value);break;
                case "iconSize":desktop.saveAppearance("icon-size",value);break;case "iconShape":desktop.saveAppearance("icon-shape",value);break;
                case "showWatchTv":desktop.showWatchTv((Boolean)value);break;
                case "secondHideHomeApps":desktop.savePhoneValue("hide-home-apps-on-second-screen",value);break;
                case "showHomeQuote":desktop.savePhoneValue(DailyQuote.HOME,value);break;
                case "showScreensaverQuote":desktop.savePhoneValue(DailyQuote.SAVER,value);break;
                case "showTime":desktop.savePhoneValue("show-time",value);break;case "showDate":desktop.savePhoneValue("show-date",value);break;case "showSeconds":desktop.showSeconds((Boolean)value);break;case "showLunarDate":desktop.showLunarDate((Boolean)value);break;case "showMovieInfo":desktop.showMovieInfo((Boolean)value);break;case "homeApps":desktop.savePhoneValue("show-home-apps",value);break;case "appNames":desktop.saveAppearance("app-title-position",value);break;case "secondNames":desktop.savePhoneValue("show-all-apps-names",value);break;
                default:throw new IllegalArgumentException();
            }
        }else if("greeting".equals(domain)&&"showGreeting".equals(field))desktop.savePhoneValue("greeting-enabled",value);
        else if("greeting".equals(domain)&&"showAvatar".equals(field)){if(Boolean.TRUE.equals(value)&&new GreetingAvatarStore(context).existing(desktop.avatarFile())==null)throw new IllegalArgumentException();desktop.savePhoneValue("greeting-avatar-enabled",value);}
        else if("weather".equals(domain)&&"weatherEnabled".equals(field))new WeatherStore(context).savePhoneEnabled((Boolean)value);
        else if("dock".equals(domain))writeDock(field,value,active);
        else if("buttons".equals(domain)&&"quickControlVisibility".equals(field))desktop.saveAppearance("quick-control-visibility",value);
        else if("buttons".equals(domain)&&"homeButtons".equals(field)){HomeButtonStore store=new HomeButtonStore(context);store.save(store.read().revision,ids(value,HomeButtonStore.EDITABLE,3));}
        else if("quick".equals(domain)){
            QuickControlStore store=quick();QuickControlStore.Snapshot before=store.read();List<String> next=new ArrayList<>(before.ids);
            if("quickControls".equals(field))next=ids(value,QuickControlStore.ALL,QuickControlStore.ALL.size());
            else if("reset".equals(field)&&Boolean.TRUE.equals(value))next=new ArrayList<>(QuickControlStore.DEFAULT);
            else if("add".equals(field)&&value instanceof String){if(!QuickControlStore.ALL.contains(value)||next.contains(value))throw new IllegalArgumentException();next.add((String)value);}
            else if("remove".equals(field)&&value instanceof String){if(QuickControlStore.SETTINGS.equals(value))throw new PhoneSettingsServer.Rejected("桌面设置必须保留，可调整位置");if(!next.remove(value))throw new IllegalArgumentException();}
            else throw new IllegalArgumentException();
            try{QuickControlStore.validateLimit(before.ids,next);}catch(IOException invalid){throw new PhoneSettingsServer.Rejected(invalid.getMessage());}store.save(before.revision,next);
        }else throw new IllegalArgumentException();
        changed.run();return new PhoneSettingsState.Result(read(domain));
    }
    private JSONObject dailyQuote()throws JSONException{
        DailyQuote.Entry quote=DailyQuote.get(context).current(DailyQuote.date(System.currentTimeMillis()));
        return quote==null?new JSONObject():quote.json();
    }
    private PhoneSettingsState.Result writeWallpaper(String field,Object value,PhoneSettingsServer.Active active)throws IOException{
        WallpaperStore store=WallpaperStore.get(context);WallpaperCatalog catalog=store.snapshot();
        switch(field){
            case "wallpaper":{
                if(!(value instanceof String)||catalog.find((String)value)==null||catalog.builtinHidden((String)value,false))throw new IllegalArgumentException();
                if(WallpaperCatalog.DAILY.equals(value))return new PhoneSettingsState.Result(read("wallpaper"),media.daily(true,active));store.select((String)value);break;
            }
            case "wallMode":{
                if(!(value instanceof String)||!Arrays.asList("fixed","rotation","daily","weather").contains(value))throw new IllegalArgumentException();String mode=(String)value;
                if(mode.equals("daily"))return new PhoneSettingsState.Result(read("wallpaper"),media.daily(true,active));
                if(mode.equals("weather")&&!home.quiet.components.ComponentRepository.get(context).isReady("weather"))throw new PhoneSettingsServer.Rejected("需要先下载天气资源才能使用");
                if(mode.equals("weather")&&!WallpaperGpuSupport.available(context))throw new PhoneSettingsServer.Rejected("此电视不支持天气动态效果");
                String id=mode.equals("fixed")?catalog.fixedCandidate(catalog.current):catalog.current;
                if(mode.equals("rotation")&&WallpaperCatalog.DAILY.equals(id))id=catalog.dailyFallback();
                if(mode.equals("rotation")&&catalog.count(catalog.dynamic())<2)throw new PhoneSettingsServer.Rejected("同类素材至少需要两张图片或两个视频才能轮换");
                store.background(mode,id,catalog.rotationMinutes,catalog.weatherDayNight);break;
            }
            case "rotation":if(!catalog.rotating)throw new PhoneSettingsServer.Rejected("请先开启自动轮换");store.background("rotation",catalog.current,integer(value,1,1440),catalog.weatherDayNight);break;
            case "dayNight":if(!catalog.weather||!WallpaperGpuSupport.available(context))throw new IllegalArgumentException();store.background("weather",catalog.current,catalog.rotationMinutes,(Boolean)value);break;
            case "wallSound":if(!catalog.dynamic()||(catalog.rotating||catalog.weather)&&Boolean.TRUE.equals(value))throw new PhoneSettingsServer.Rejected("当前背景方式保持静音");store.sound((Boolean)value);break;
            case "dailySource":if(!(value instanceof String))throw new PhoneSettingsServer.BadInput();return new PhoneSettingsState.Result(read("wallpaper"),media.dailySource((String)value,active));
            case "dailyRefresh":return new PhoneSettingsState.Result(read("wallpaper"),media.daily(false,active));
            case "removeMedia":if(!(value instanceof String)||catalog.find((String)value)==null||!catalog.find((String)value).custom())throw new IllegalArgumentException();store.remove((String)value);break;
            default:throw new IllegalArgumentException();
        }
        changed.run();return new PhoneSettingsState.Result(read("wallpaper"));
    }
    private PhoneSettingsState.Result writeSaver(String field,Object value,String expected,PhoneSettingsServer.Active active)throws IOException{
        WallpaperCatalog catalog=WallpaperStore.get(context).snapshot();
        ScreensaverStore store=ScreensaverStore.get(context);
        synchronized(store){
            ScreensaverSettings current=store.snapshot(catalog);
            try{
                if(!PhoneSettingsState.revision(saverValues(current,catalog)).equals(expected))throw new PhoneSettingsServer.Conflict();
                ScreensaverSettings next;
                switch(field){
                    case "idle":next=current.waitMinutes(integer(value,0,30));break;
                    case "saverContent":if(!(value instanceof String))throw new IllegalArgumentException();next=current.content((String)value);break;
                    case "saverClock":next=current.time((Boolean)value);break;
                    case "saverFlipSeconds":next=current.flipSeconds((Boolean)value);break;
                    case "saverFlipSound":next=current.flipSound((Boolean)value);break;
                    case "saverAnalogClock":next=current.clock(current.clock.analog((Boolean)value));break;
                    case "saverCalendar":next=current.clock(current.clock.calendar((Boolean)value));break;
                    case "saverLunarDate":next=current.clock(current.clock.lunar((Boolean)value));break;
                    case "saverSeconds":next=current.clock(current.clock.seconds((Boolean)value));break;
                    case "saverAnalogStyle":next=current.clock(current.clock.style(value instanceof String?(String)value:null));break;
                    case "saverDigitalLayout":next=current.clock(current.clock.layout(value instanceof String?(String)value:null));break;
                    case "saverWeather":next=current.weather((Boolean)value);break;
                    case "saverSound":
                        if(!current.video(catalog))throw new PhoneSettingsServer.Rejected("选择视频屏保后可开启");
                        next=current.sound((Boolean)value);break;
                    case "saverClockPosition":if(!(value instanceof String))throw new IllegalArgumentException();next=current.timePosition((String)value);break;
                    case "saverWeatherPosition":if(!(value instanceof String))throw new IllegalArgumentException();next=current.weatherPosition((String)value);break;
                    case "saverMedia":if(!(value instanceof String)||WallpaperCatalog.DEFAULT.equals(value)||catalog.builtinHidden((String)value,true)||catalog.find((String)value)==null)throw new IllegalArgumentException();next=current.media((String)value);break;
                    default:throw new IllegalArgumentException();
                }
                if(!active.valid())throw new PhoneSettingsServer.Ended();store.save(next);changed.run();return new PhoneSettingsState.Result(saverValues(next,catalog));
            }catch(JSONException e){throw new IOException(e);}
        }
    }
    private void writeDock(String field,Object value,PhoneSettingsServer.Active active)throws IOException{
        DesktopSettingsStore desktop=new DesktopSettingsStore(context);
        switch(field){
            case "dockScale":desktop.saveAppearance("dock-scale",integer(value,70,130));return;case "dockMagnification":desktop.saveAppearance("dock-magnification",integer(value,100,160));return;case "dockLimit":desktop.saveAppearance("dock-limit",integer(value,5,12));return;
            case "dockMagnify":desktop.saveAppearance("dock-magnify",value);return;case "globalGlass":case "dockGlass":desktop.saveAppearance(GlassStyle.KEY,value);return;case "dockBackground":desktop.savePhoneValue("show-dock-background",value);return;
            case "dockApps":{
                DockStore store=new DockStore(context);List<String> existing=store.load();List<HomeAppCatalog.Entry> available=new HomeAppCatalog(context).load(existing);List<String> allowed=new ArrayList<>(existing);for(HomeAppCatalog.Entry app:available)if(!allowed.contains(app.id))allowed.add(app.id);
                List<String> next=ids(value,allowed,DockStore.CAPACITY);if(next.size()>store.capacity()&&!new HashSet<>(existing).containsAll(next))throw new IllegalArgumentException();if(!active.valid())throw new PhoneSettingsServer.Ended();if(!store.saveOrder(next))throw new IOException("Dock 顺序保存未确认");return;
            }
            case "gridOrder":{
                List<String> available=HomeAppCatalog.gridIds(new HomeAppCatalog(context).load(new DockStore(context).load()));List<String> next=ids(value,available,available.size());if(next.size()!=available.size())throw new PhoneSettingsServer.Conflict();if(!active.valid())throw new PhoneSettingsServer.Ended();if(!new HomeOrderStore(context).save(next,available,false))throw new IOException("应用顺序保存未确认");return;
            }
            default:throw new IllegalArgumentException();
        }
    }
    static List<String> ids(Object value,List<String> allowed,int maximum){
        if(!(value instanceof JSONArray))throw new IllegalArgumentException();JSONArray array=(JSONArray)value;if(array.length()>maximum)throw new IllegalArgumentException();List<String> out=new ArrayList<>();
        for(int i=0;i<array.length();i++){Object item=array.opt(i);if(!(item instanceof String)||!allowed.contains(item)||out.contains(item))throw new IllegalArgumentException();out.add((String)item);}return out;
    }
    static int integer(Object value,int min,int max){if(!(value instanceof Number))throw new IllegalArgumentException();double n=((Number)value).doubleValue();int result=((Number)value).intValue();if(n!=result||result<min||result>max)throw new IllegalArgumentException();return result;}
    static String greeting(Object value){
        if(!(value instanceof String))throw new IllegalArgumentException();String text=((String)value).replace('\r',' ').replace('\n',' ').replace('\t',' ').trim();if(text.length()>GreetingInputServer.MAX_TEXT)throw new IllegalArgumentException();
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c<32||c==127)throw new IllegalArgumentException();if(Character.isHighSurrogate(c)){if(++i>=text.length()||!Character.isLowSurrogate(text.charAt(i)))throw new IllegalArgumentException();}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException();}return text;
    }
}
