package home.quiet.tv;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Weather content is mounted once; callbacks never rebuild focusable settings rows. */
final class DesktopWeatherPanel {
    final ScrollView view;
    private final AppManagementActivity ui;
    private final WeatherStore store;
    private final WeatherController controller;
    private final DesktopSettingsRow automatic,city,provider,meteoFallback,refresh,service;
    private final TextView status;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final WeatherCityPicker cityPicker;
    private final WeatherClient sources;
    private ManagementUi.Sheet information;
    private boolean active;
    private WeatherData snapshot;
    DesktopWeatherPanel(AppManagementActivity ui){
        this.ui=ui;store=new WeatherStore(ui);sources=new WeatherClient(ui);controller=new WeatherController(ui,ui.chinese(),this::render);cityPicker=new WeatherCityPicker(ui,store,controller::settingsChanged);view=new ScrollView(ui);view.setFocusable(false);view.setVerticalScrollBarEnabled(false);LinearLayout body=ui.column();view.addView(body);body.addView(ui.section(tr("位置与更新","Location and updates")));ui.gap(body,8);
        LinearLayout group=ui.group();body.addView(group);
        automatic=ui.setting(tr("自动定位","Automatic location"),"locate","automatic");automatic.checked(store.automatic());automatic.setOnClickListener(v->toggleAutomatic());ui.addSetting(group,automatic);
        city=ui.setting(tr("选择城市","Choose city"),"pin","city");city.tail("arrow");city.setOnClickListener(v->chooseCity());ui.addSetting(group,city);
        provider=ui.setting(tr("天气来源","Weather source"),"weather","provider");provider.tail("arrow");provider.setOnClickListener(v->chooseProvider());ui.addSetting(group,provider);
        meteoFallback=ui.setting(tr("Open-Meteo 备用","Open-Meteo fallback"),"weather","meteo-fallback");meteoFallback.checked(store.meteoFallback());meteoFallback.setOnClickListener(v->{if(store.meteoFallback()){store.meteoFallback(false);controller.settingsChanged();}else meteoConsent(()->{store.meteoFallback(true);controller.settingsChanged();});});ui.addSetting(group,meteoFallback);
        refresh=ui.setting(tr("刷新天气","Refresh weather"),"refresh","refresh");refresh.tail("refresh");refresh.setOnClickListener(v->{if(store.enabled())controller.refresh(true);});ui.addSetting(group,refresh);
        ui.gap(body,16);LinearLayout secondary=ui.group();body.addView(secondary);
        service=ui.setting(tr("服务说明","Service information"),"info","service");service.tail("arrow");service.setOnClickListener(v->serviceInformation());secondary.addView(service,new LinearLayout.LayoutParams(-1,ui.px(56)));
        status=ui.muted("",12);androidx.core.view.ViewCompat.setAccessibilityLiveRegion(status,androidx.core.view.ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=ui.px(12);body.addView(status,sp);
        render(store.cached(ui.chinese()),"",false);
    }
    private String tr(String zh,String en){return ui.tr(zh,en);}
    private String sourceName(String value){return "qweather".equals(value)?tr("和风天气","QWeather"):WeatherClient.providerName(value);}
    DesktopSettingsRow firstRow(){return automatic;}
    DesktopSettingsRow[] rows(){return new DesktopSettingsRow[]{automatic,city,provider,meteoFallback,refresh,service};}
    void resume(){if(active)return;active=true;controller.resume();}
    void pause(){active=false;controller.pause();cityPicker.pause();if(information!=null)information.dialog.dismiss();}
    void destroy(){pause();controller.destroy();cityPicker.destroy();main.removeCallbacksAndMessages(null);}
    void settingsChanged(){controller.settingsChanged();}
    private void toggleAutomatic(){
        if(store.automatic()){
            if(!store.fixAutomaticCity(ui.chinese())){chooseCity();return;}
            controller.settingsChanged();return;
        }
        store.automaticMode();automatic.checked(true);
        if(store.enabled()&&Build.VERSION.SDK_INT>=23&&ui.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            ui.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},201);
            // The result callback resumes the controller; no request runs behind the permission sheet.
            controller.pause();return;
        }
        controller.settingsChanged();
    }
    void permissionResult(int code){if(code==201){controller.settingsChanged();if(active)controller.resume();}}
    private void render(WeatherData data,String message,boolean loading){
        snapshot=data;automatic.checked(store.automatic());
        provider.value("auto".equals(store.provider())?tr("自动切换","Automatic fallback"):sourceName(store.provider()));
        meteoFallback.checked(store.meteoFallback());meteoFallback.setVisibility("auto".equals(store.provider())?View.VISIBLE:View.GONE);
        WeatherData cached=data!=null?data:store.cached(ui.chinese());
        String name=store.automatic()?(cached==null?"":cached.city):store.city();
        city.value(name.isEmpty()?tr("未设置","Not set"):name);
        refresh.available(store.enabled());
        refresh.setNextFocusUpId("auto".equals(store.provider())?meteoFallback.getId():provider.getId());
        provider.setNextFocusDownId("auto".equals(store.provider())?meteoFallback.getId():store.enabled()?refresh.getId():service.getId());
        meteoFallback.setNextFocusDownId(store.enabled()?refresh.getId():service.getId());
        service.setNextFocusUpId(store.enabled()?refresh.getId():"auto".equals(store.provider())?meteoFallback.getId():provider.getId());
        if(!store.enabled())refresh.value(tr("天气已关闭","Weather is off"));
        else if(loading)refresh.value(tr("刷新中…","Updating…"));
        else if(data!=null){SimpleDateFormat time=new SimpleDateFormat("HH:mm",Locale.US);refresh.value(time.format(new Date(data.fetchedAt))+tr(" 更新"," updated"));}
        else refresh.value(message.isEmpty()?tr("尚未更新","Not updated"):tr("更新失败","Update failed"));
        String failure=controller.errorMessage();status.setText(store.enabled()?(failure.isEmpty()?message:failure):"");
    }
    private void chooseCity(){cityPicker.show();}
    private void chooseProvider(){
        ManagementUi.Sheet sheet=ui.new Sheet(tr("天气来源","Weather source"));information=sheet;
        for(String value:new String[]{"auto","qweather","uapi","openweather","openmeteo"}){
            String title="auto".equals(value)?tr("自动切换","Automatic fallback"):"qweather".equals(value)?tr("和风天气","QWeather"):WeatherClient.providerName(value);
            Button option=ui.button(title+(!sources.available(value)?tr("  未配置","  Not configured"):""),()->{
                sheet.dialog.dismiss();Runnable save=()->{store.provider(value);controller.settingsChanged();};
                if("openmeteo".equals(value))meteoConsent(save);else save.run();
            });option.setEnabled(sources.available(value));sheet.body.addView(option,new LinearLayout.LayoutParams(-1,ui.px(44)));
        }
        Button close=sheet.action(tr("取消","Cancel"),sheet.dialog::dismiss);sheet.dialog.setOnDismissListener(d->{information=null;provider.requestFocus();});sheet.show(close);
    }
    private void meteoConsent(Runnable accepted){
        ManagementUi.Sheet sheet=ui.new Sheet("Open-Meteo");information=sheet;
        sheet.body.addView(ui.muted(tr("免费接口仅限非商业使用，有请求限额。天气为模型估算，境外服务的连接情况因网络而异。","The free API is for non-commercial use with request limits. Weather is modelled; overseas service connectivity varies by network."),13));
        sheet.action(tr("取消","Cancel"),sheet.dialog::dismiss);Button use=sheet.action(tr("启用","Enable"),()->{accepted.run();sheet.dialog.dismiss();});
        sheet.dialog.setOnDismissListener(d->{information=null;provider.requestFocus();});sheet.show(use);
    }
    private void serviceInformation(){
        ManagementUi.Sheet sheet=ui.new Sheet(tr("服务说明","Service information"));information=sheet;
        String actual=snapshot==null?store.provider():snapshot.provider;
        detail(sheet,tr("天气来源","Weather source"),snapshot==null?("auto".equals(actual)?tr("自动选择可用来源","Automatically selects an available source"):sourceName(actual)):tr("天气服务由 ","Weather service provided by ")+sourceName(snapshot.provider));
        if("auto".equals(store.provider()))detail(sheet,tr("切换顺序","Fallback order"),tr("和风天气  UAPI  OpenWeather，跳过未配置来源。启用后最后尝试 Open-Meteo。","QWeather, UAPI, OpenWeather; skips unconfigured sources. Open-Meteo is tried last when enabled."));
        detail(sheet,tr("自动定位","Automatic location"),tr("优先使用系统坐标；不可用时根据网络 IP 推测城市，可手动选择地点。","Uses system coordinates when available, otherwise estimates the city from the network IP. You can choose a place manually."));
        detail(sheet,tr("隐私与条款","Privacy and terms"),tr("联网搜索向 UAPI 查询地点；天气请求会向所选来源发送地点或坐标。手机输入仅在当前局域网会话中接收。","Online place search queries UAPI. Weather requests send the place or coordinates to the selected service. Phone input stays within the current LAN session."));
        if("openmeteo".equals(actual)||store.meteoFallback()){
            detail(sheet,"Open-Meteo",tr("天气数据由 Open-Meteo 提供，依据 CC BY 4.0 使用。当前天气为模型估算；免费接口仅限非商业使用。","Weather data by Open-Meteo, used under CC BY 4.0. Current conditions are model estimates; the free API is for non-commercial use only."));
            sheet.action(tr("CC BY 4.0 许可","CC BY 4.0 license"),()->open("weather.license","https://creativecommons.org/licenses/by/4.0/"));
        }
        if(snapshot!=null){SimpleDateFormat time=new SimpleDateFormat("MM-dd HH:mm",Locale.US);detail(sheet,tr("数据时间","Data time"),snapshot.observedAt>0?time.format(new Date(snapshot.observedAt)):tr("接口未提供可识别时间","Data time unavailable"));}
        sheet.action(tr("天气服务官网","Weather service website"),()->open("weather.website",WeatherClient.website(actual)));sheet.action(tr("隐私与条款","Privacy and terms"),()->open("weather.terms",WeatherClient.terms(actual)));Button close=sheet.action(tr("关闭","Close"),sheet.dialog::dismiss);
        sheet.dialog.setOnDismissListener(d->{information=null;if(active)service.requestFocus();});sheet.show(close);
    }
    private void detail(ManagementUi.Sheet sheet,String heading,String text){
        if(sheet.body.getChildCount()>0)ui.gap(sheet.body,12);sheet.body.addView(ui.label(heading,15));TextView body=ui.muted(text,13);body.setLineSpacing(ui.px(3),1);sheet.body.addView(body);
    }
    private void open(String entry,String url){if(!ExternalActivityLauncher.open(ui,entry,new Intent(Intent.ACTION_VIEW,Uri.parse(url))))ui.feedback(tr("链接暂时无法打开","Unable to open this link"));}
}
