package home.quiet.tv;

import android.content.*;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Shared weather, startup, HOME and input engines. System scene results remain user evidence. */
final class PhoneSettingsGeneral {
    private final Context context;private final PhoneSettingsTasks tasks;private final String scope;private final Runnable changed;private final Handler main=new Handler(Looper.getMainLooper());private final PhoneSettingsActions actions;
    PhoneSettingsGeneral(Context context,PhoneSettingsTasks tasks,String scope,Runnable changed){this.context=context;this.tasks=tasks;this.scope=scope;this.changed=changed;actions=new PhoneSettingsActions(context);}
    JSONObject weather()throws JSONException{
        WeatherStore store=new WeatherStore(context);WeatherData data=store.cached(true);return new JSONObject().put("weatherEnabled",store.enabled()).put("autoLocation",store.automatic()).put("provider",store.provider()).put("meteoFallback",store.meteoFallback()).put("city",store.city()).put("cityCode",store.adcode()).put("locationKey",store.key(true));
    }
    JSONObject weatherInfo()throws JSONException{
        WeatherData data=new WeatherStore(context).cached(true);long now=System.currentTimeMillis();boolean usable=data!=null&&data.usable(now);
        JSONObject value=new JSONObject().put("weatherText",data==null?"":Math.round(data.temperature)+"°  "+data.description+"  "+data.city).put("weatherFetchedAt",data==null?0:data.fetchedAt).put("weatherSource",data==null?"":WeatherClient.providerName(data.provider)).put("locatedCity",data==null?"":data.city).put("weatherPreviewAvailable",usable);
        WeatherStore store=new WeatherStore(context);String provider=data==null?store.provider():data.provider;
        WeatherIllumination illumination=WeatherIllumination.at(now,data==null?Double.NaN:data.latitude,data==null?Double.NaN:data.longitude,WallpaperStore.get(context).snapshot().weatherDayNight);
        android.net.ConnectivityManager network=(android.net.ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);android.net.NetworkInfo connection=network==null?null:network.getActiveNetworkInfo();
        value.put("weatherSceneLabel",WeatherWallpaperScene.name(WeatherWallpaperScene.key(data==null?"":data.description,data==null?"999":data.icon,false,12),true))
            .put("weatherPeriod",WallpaperStore.get(context).snapshot().weatherDayNight?illumination.period(true):"白天效果")
            .put("weatherBackgroundState",!usable?"暂用静态背景，天气可用后自动恢复":connection==null||!connection.isConnected()?"天气更新暂停，沿用上次天气":"跟随当前城市天气")
            .put("weatherObservedAt",data==null?0:data.observedAt).put("weatherWebsite",WeatherClient.website(provider)).put("weatherTerms",WeatherClient.terms(provider)).put("weatherUsesMeteo",data!=null&&"openmeteo".equals(data.provider));
        if(usable){WeatherIllumination light=illumination;value.put("weatherScene",WeatherWallpaperScene.key(data.description,data.icon,false,12)).put("weatherNight",Math.round(light.night*100)/100d).put("weatherWarm",Math.round(light.warm*100)/100d);double wind=data.atmosphere.windSpeed;if(!Double.isNaN(wind))value.put("weatherWind",wind);}
        return value;
    }
    JSONObject places(String query)throws IOException{if(query==null||query.length()>60)throw new PhoneSettingsServer.BadInput();try{return new JSONObject().put("places",WeatherRegions.load(context).items(WeatherRegions.load(context).search(query,50)));}catch(JSONException e){throw new IOException(e);}}
    JSONObject startup()throws JSONException{BootAppStore boot=new BootAppStore(context);return new JSONObject().put("boot",boot.hdmiBoot()?BootAppStore.HDMI:boot.target()).put("bootLabel",boot.label()).put("watchTarget",boot.watchTarget()).put("watchLabel",boot.watchLabel()).put("watchTested",boot.tested());}
    JSONObject guard()throws JSONException{return DesktopTakeoverStatus.read(context).snapshot();}
    JSONObject management()throws JSONException{ManagementAdb management=ManagementAdb.get(context);return new JSONObject().put("connected",management.state()==AdbConnectionKeeper.State.CONNECTED).put("detail",management.description()).put("state",management.state().name()).put("label",management.label()).put("tone",management.tone()).put("debugging",management.debugging()).put("port",management.port()).put("paused",management.paused()).put("waitingAuthorization",management.waitingAuthorization()).put("authorizationSeconds",management.authorizationSeconds()).put("timedOut",management.timedOut());}
    JSONObject inputs()throws IOException{try{SourcePairing pairing=SourcePairing.get(context);JSONObject state=pairing.snapshot();return state.put("busy",pairing.blocksExternalInput()).put("owned",scope.equals(state.optString("owner"))).put("startupTarget",new BootAppStore(context).target());}catch(Exception e){throw new IOException("信号源状态无法读取",e);}}
    void closed(){SourcePairing pairing=SourcePairing.existing();if(pairing!=null&&scope.equals(pairing.owner()))pairing.cancel(scope,true);}
    JSONObject write(String domain,String field,Object value,PhoneSettingsServer.Active active)throws IOException{
        if(!active.valid())throw new PhoneSettingsServer.Ended();
        {
            if(domain.equals("weather")){
                if(field.equals("refresh")&&Boolean.TRUE.equals(value))return refreshWeather(active);
                new WeatherStore(context).savePhoneSetting(field,value);changed.run();return null;
            }
            if(domain.equals("boot")){
                if(!field.equals("boot")||!(value instanceof String))throw new IllegalArgumentException();BootAppStore store=new BootAppStore(context);String target=(String)value;
                if(target.equals(BootAppStore.HDMI))store.hdmiBoot(true);else{String label="QuietTV 首页";if(!target.isEmpty()){android.content.pm.ResolveInfo app=new HomeAppCatalog(context).find(target);if(app==null||store.launchIntent(target)==null)throw new PhoneSettingsServer.Rejected("此应用已不可用");label=app.loadLabel(context.getPackageManager()).toString();}if(!active.valid())throw new PhoneSettingsServer.Ended();store.select(target,label);}changed.run();return null;
            }
            if(domain.equals("management")){
                if(!Boolean.TRUE.equals(value)||!Arrays.asList("connect","disconnect","check").contains(field))throw new IllegalArgumentException();
                return actions.onMain(()->{
                    ManagementAdb management=ManagementAdb.get(context);String message;
                    if(field.equals("connect")){management.requestAuthorization();message="正在连接当前电视，如出现系统调试授权弹窗，请在电视允许。";}
                    else if(field.equals("disconnect")){management.pause();message="已断开连接，自动重连已暂停。";}
                    else{management.check();message="已重新读取 ADB 状态。";}
                    changed.run();return new JSONObject().put("message",message);
                },active);
            }
            if(domain.equals("guard")&&(field.equals("pause")||field.equals("restore")))return legacyDesktop(field.equals("restore"),active);
            if(domain.equals("guard"))return actions.onMain(()->{
                String message;
                if(field.equals("system")){Intent intent=MaintenanceHelpActivity.homeSettingsIntent(context);if(intent==null)throw new PhoneSettingsServer.Rejected("此电视没有默认桌面选择入口，请沿电视说明通过电脑配置");intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);if(!ExternalActivityLauncher.open(context,"phone.home_settings",intent))throw new PhoneSettingsServer.Rejected("默认桌面选择入口不可用");message="已打开电视系统选择，请完成后检查实际 HOME 行为";}
                else if(("repair".equals(field)||"repairAccessibility".equals(field))&&Boolean.TRUE.equals(value))
                    message=DesktopSwitch.get(context).repairGuard(context,"repairAccessibility".equals(field));
                else if(("accessibility".equals(field)||"notification".equals(field))&&Boolean.TRUE.equals(value)){
                    JSONObject settings=DesktopSwitch.get(context).state().optJSONObject("settings");
                    JSONObject setup=settings==null?null:settings.optJSONObject("guardSetup");
                    if(setup==null||!setup.optBoolean(field+"Settings"))throw new PhoneSettingsServer.Rejected("当前权限入口不可用，请在电视核对桌面状态");
                    Intent intent=new DesktopGuardSetup(context).settings(field);
                    if(intent==null||!ExternalActivityLauncher.open(context,"phone.guard."+field,intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
                        throw new PhoneSettingsServer.Rejected("系统授权入口不可用，请通过电脑继续");
                    message=DesktopGuardSetup.permissionPrompt(context,field);
                }
                else if(field.equals("pause")||field.equals("restore"))message=MaintenanceHelpActivity.perform(context,field.equals("restore"));else throw new IllegalArgumentException();changed.run();return new JSONObject().put("message",message).put("state","waitingTelevision");
            },active);
            if(domain.equals("inputs")&&field.equals("autoEnter"))return inputAutoEnter(value,active);
            if(domain.equals("inputs"))return actions.onMain(()->{
                SourcePairing pairing=SourcePairing.get(context);JSONObject result=new JSONObject();
                if(field.equals("search")&&Boolean.TRUE.equals(value))result.put("searchId",pairing.search(scope));
                else if(field.equals("cancel")&&Boolean.TRUE.equals(value)){if(!scope.equals(pairing.owner()))throw new PhoneSettingsServer.Rejected("当前信号源任务属于电视或其他连接");pairing.cancel(scope,true);}
                else if((field.equals("test")||field.equals("calibrate"))&&value instanceof JSONObject){JSONObject request=(JSONObject)value;if(request.length()!=3)throw new IllegalArgumentException();String id=request.getString("id"),expected=request.getString("revision"),label=request.getString("label");SourceSearch.Candidate candidate=pairing.candidate(id);if(candidate==null)throw new PhoneSettingsServer.Rejected("搜索入口已变化，请重新搜索");if(field.equals("calibrate")){HdmiTarget saved=HdmiTarget.decode(new BootAppStore(context).watchTarget()),target=candidate.target;if(saved==null||target==null||!saved.kind.equals(target.kind)||!saved.id.equals(target.id)||!saved.scope.equals(target.scope))throw new PhoneSettingsServer.Rejected("请选择当前保存的信号源，再测试并校正名称");}result.put("request",pairing.request(scope,id,candidate.scope,expected,label));}
                else if(field.equals("confirm")&&value instanceof JSONObject){JSONObject request=(JSONObject)value;if(request.length()!=3||!(request.opt("worked") instanceof Boolean)||!(request.opt("physical") instanceof Boolean))throw new IllegalArgumentException();pairing.confirm(scope,request.getString("request"),request.getBoolean("worked"),request.getBoolean("physical"),"user",request.getBoolean("worked")?"用户从手机确认实际端口、画面和声音":"用户从手机报告入口或画面异常");}
                else if(field.equals("observe")&&value instanceof Boolean)result.put("observation",pairing.observe(scope,(Boolean)value));
                else if((field.equals("restore")||field.equals("defaults"))&&value instanceof String)pairing.restore(scope,(String)value,field.equals("defaults"));
                else throw new IllegalArgumentException();return result.put("message","信号源请求已受理，请按实际电视画面核对");
            },active);
            throw new IllegalArgumentException();
        }
    }
    JSONObject bootAds(String field,Object value,PhoneSettingsServer.Active active)throws IOException{
        boolean refresh="check".equals(field),restore="restore".equals(field);
        if(!refresh&&!restore&&!"apply".equals(field))throw new PhoneSettingsServer.BadInput();
        if(!(value instanceof String)||!(refresh?((String)value).isEmpty():((String)value).matches("[0-9a-f]{64}")))
            throw new PhoneSettingsServer.BadInput();
        if(tasks.restoring())throw new PhoneSettingsServer.Rejected("桌面正在恢复，请稍后核对。");
        String title=refresh?"检查开机广告":restore?"恢复开机广告设置":"关闭开机广告";
        PhoneSettingsTasks.Job job=tasks.begin(scope+"/bootAds",title,field+":"+value,active,"bootAds",field);
        tasks.start(job,work->{
            work.check();
            work.cancellable=false;
            work.stage("正在核对广告设置",-1);
            BootAdSettings settings=BootAdSettings.get(context);
            ManagementAdb management=ManagementAdb.get(context);
            try{
                management.worker.submit(()->{
                    work.check();
                    if(refresh)settings.refresh();
                    else settings.change(restore,(String)value,work::active);
                    return null;
                }).get();
            }catch(ExecutionException failure){
                Throwable cause=failure.getCause();
                if(cause instanceof IOException)throw (IOException)cause;
                throw new PhoneSettingsTasks.Problem("操作未完成，请重新检测实际设置。",false);
            }finally{changed.run();}
            String message=refresh?"已检查当前广告状态":restore?"已恢复此次修改前的开机广告设置。":"已应用。下次开机时，请确认广告是否消失。";
            if(refresh){
                BootAdSettings.State state=settings.state();
                if(state.needsAuthorization)throw new PhoneSettingsTasks.Problem("请先完成本机 ADB 授权，再重新检测。",false);
                if(!state.detail.isEmpty())throw new PhoneSettingsTasks.Problem(state.detail,false);
            }
            work.stage(message,100);
            return settings.snapshot();
        });
        return tasks.receipt(job);
    }
    JSONObject desktopSwitch(String field,Object value,PhoneSettingsServer.Active active)throws IOException{
        if(!(value instanceof JSONObject)||!Arrays.asList("switch","manage","verify").contains(field))throw new PhoneSettingsServer.BadInput();
        JSONObject request=(JSONObject)value;boolean verify=field.equals("verify");
        if(request.length()!=(verify?3:2)||!(request.opt("revision") instanceof String))throw new PhoneSettingsServer.BadInput();
        String action=verify?request.optString("scene"):request.optString("action"),revision=request.optString("revision");
        if(verify){if(!(request.opt("scene") instanceof String)||!(request.opt("result") instanceof String)||!Arrays.asList("home","reboot","coldBoot","wake").contains(action)||!Arrays.asList("通过","失败","待验证").contains(request.optString("result")))throw new PhoneSettingsServer.BadInput();}
        else if(!(request.opt("action") instanceof String)||!(field.equals("switch")?Arrays.asList("stock","quiet","quiet-disable").contains(action):DesktopSwitchPlan.managementAction(action)))throw new PhoneSettingsServer.BadInput();
        if(tasks.restoring())throw new PhoneSettingsServer.Rejected("桌面正在恢复，请稍后核对。");
        PhoneSettingsTasks.Job job=tasks.begin(scope+"/desktopSwitch",verify?"记录使用验证":"桌面管理",field+":"+request.toString(),active);
        tasks.start(job,work->{work.check();work.cancellable=false;work.stage(verify?"正在记录用户确认结果":"正在核对并处理电视桌面",-1);try{
            DesktopSwitch engine=DesktopSwitch.get(context);JSONObject result=verify?engine.verify(action,request.optString("result"),revision,work::active):field.equals("manage")?engine.manage(action,revision,work::active):engine.execute(action,revision,work::active);
            work.stage(verify?"已记录用户确认的实际结果":result.optString("message","请核对电视画面和 HOME 行为"),100);changed.run();return result;
        }catch(Exception failure){throw new PhoneSettingsTasks.Problem(DesktopSwitch.message(failure),true);}});return tasks.receipt(job);
    }
    private JSONObject legacyDesktop(boolean restore,PhoneSettingsServer.Active active)throws IOException {
        PhoneSettingsTasks.Job job=tasks.begin(scope+"/desktopSwitch",restore?"恢复原桌面":"暂停桌面接管",restore?"legacy-restore":"legacy-pause",active);
        tasks.start(job,work->{work.check();work.cancellable=false;try{String message=DesktopSwitch.get(context).legacy(restore,work::active);changed.run();return new JSONObject().put("message",message).put("state","waitingTelevision");}catch(Exception failure){throw new PhoneSettingsTasks.Problem(DesktopSwitch.message(failure),true);}});return tasks.receipt(job);
    }
    private JSONObject inputAutoEnter(Object value,PhoneSettingsServer.Active active)throws IOException{
        if(!(value instanceof JSONObject))throw new PhoneSettingsServer.BadInput();JSONObject request=(JSONObject)value;
        if(request.length()!=3||!(request.opt("enabled") instanceof Boolean)||!(request.opt("revision") instanceof String)||!(request.opt("startup") instanceof String))throw new PhoneSettingsServer.BadInput();
        boolean enabled=request.optBoolean("enabled");String expected=request.optString("revision");
        PhoneSettingsTasks.Job job=tasks.begin(scope+"/inputs/autoEnter","设置信号源开机自动进入",request.toString(),active);
        tasks.start(job,work->{
            work.check();work.stage("正在核对已保存的信号源",-1);if(SourcePairing.get(context).blocksExternalInput())throw new PhoneSettingsTasks.Problem("另一项信号源任务尚未结束，请先确认或停止",false);SourcePairingStore sources=new SourcePairingStore(context);BootAppStore boot=new BootAppStore(context);
            if(!expected.equals(sources.revision())||!request.optString("startup").equals(boot.target()))throw new PhoneSettingsTasks.Problem("信号源或开机设置已变化，请重新读取",false);
            HdmiTarget target=enabled?HdmiTarget.decode(boot.watchTarget()):null;SavedInputLaunch launch=target==null?null:target.prepare(context);
            JSONObject result=actions.onMain(()->{if(SourcePairing.get(context).blocksExternalInput())throw new PhoneSettingsTasks.Problem("另一项信号源任务尚未结束，请先确认或停止",false);synchronized(BootAppStore.class){
                if(!expected.equals(sources.revision())||!request.optString("startup").equals(boot.target()))throw new PhoneSettingsTasks.Problem("信号源或开机设置已变化，请重新读取",false);
                if(enabled&&(launch==null||!launch.current(context)||!boot.tested()))throw new PhoneSettingsTasks.Problem("请先测试当前信号源并确认画面和声音",false);
                work.check();boot.hdmiBoot(enabled);return new JSONObject().put("enabled",enabled);
            }},work::active);
            changed.run();work.stage(enabled?"已开启开机自动进入":"已关闭开机自动进入",100);return result;
        });return tasks.receipt(job);
    }
    private JSONObject refreshWeather(PhoneSettingsServer.Active active)throws IOException{
        if(!new WeatherStore(context).enabled())throw new PhoneSettingsServer.Rejected("请先开启天气");PhoneSettingsTasks.Job job=tasks.begin(scope+"/weather","更新电视天气","refresh",active);
        tasks.start(job,work->{CountDownLatch done=new CountDownLatch(1);boolean[] armed={false},loading={false};String[] message={""};WeatherController[] controller={null};
            main.post(()->{if(!work.active()){done.countDown();return;}controller[0]=new WeatherController(context,true,(data,error,busy)->{if(busy)loading[0]=true;if(armed[0]&&!busy&&(loading[0]||!error.isEmpty())){message[0]=error;done.countDown();}});controller[0].resume();armed[0]=true;controller[0].refresh(true);});
            try{work.stage("正在按电视位置和来源更新天气",-1);long deadline=SystemClock.elapsedRealtime()+90_000;while(!done.await(200,TimeUnit.MILLISECONDS)){work.check();if(SystemClock.elapsedRealtime()>deadline)throw new PhoneSettingsTasks.Problem("天气刷新尚未确认，已保留可用缓存，请稍后核对",true);}work.check();if(!message[0].isEmpty())throw new PhoneSettingsTasks.Problem("天气未更新，已保留可用缓存："+message[0],false);changed.run();work.stage("电视天气已更新",100);return new JSONObject();}
            finally{main.post(()->{if(controller[0]!=null)controller[0].destroy();});}
        });return tasks.receipt(job);
    }
}
