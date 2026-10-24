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
    final LinearLayout view;
    private final DesktopSettingsActivity ui;
    private final WeatherStore store;
    private final WeatherController controller;
    private final DesktopSettingsRow automatic,city,refresh,service;
    private final TextView status;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final WeatherCityPicker cityPicker;
    private ManagementUi.Sheet information;
    private boolean active;
    private WeatherData snapshot;
    DesktopWeatherPanel(DesktopSettingsActivity ui){
        this.ui=ui;store=new WeatherStore(ui);controller=new WeatherController(ui,ui.chinese(),this::render);cityPicker=new WeatherCityPicker(ui,store,controller::settingsChanged);view=ui.column();view.addView(ui.section(tr("位置与更新","Location and updates")));
        LinearLayout group=ui.group();view.addView(group);
        automatic=ui.setting(tr("自动定位","Automatic location"),"locate","automatic");automatic.checked(store.automatic());automatic.setOnClickListener(v->toggleAutomatic());ui.addSetting(group,automatic);
        city=ui.setting(tr("选择城市","Choose city"),"pin","city");city.tail("arrow");city.setOnClickListener(v->chooseCity());ui.addSetting(group,city);
        refresh=ui.setting(tr("刷新天气","Refresh weather"),"refresh","refresh");refresh.tail("refresh");refresh.setOnClickListener(v->{if(store.enabled())controller.refresh(true);});ui.addSetting(group,refresh);
        ui.gap(view,16);LinearLayout secondary=ui.group();view.addView(secondary);
        service=ui.setting(tr("服务说明","Service information"),"info","service");service.tail("arrow");service.setOnClickListener(v->serviceInformation());secondary.addView(service,new LinearLayout.LayoutParams(-1,ui.px(56)));
        status=ui.muted("",12);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=ui.px(12);view.addView(status,sp);
        render(store.cached(ui.chinese()),"",false);
    }
    private String tr(String zh,String en){return ui.tr(zh,en);}
    DesktopSettingsRow firstRow(){return automatic;}
    DesktopSettingsRow[] rows(){return new DesktopSettingsRow[]{automatic,city,refresh,service};}
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
        WeatherData cached=data!=null?data:store.cached(ui.chinese());
        String name=store.automatic()?(cached==null?"":cached.city):store.city();
        city.value(name.isEmpty()?tr("未设置","Not set"):name);
        refresh.available(store.enabled());
        city.setNextFocusDownId(store.enabled()?refresh.getId():service.getId());service.setNextFocusUpId(store.enabled()?refresh.getId():city.getId());
        if(!store.enabled())refresh.value(tr("天气已关闭","Weather is off"));
        else if(loading)refresh.value(tr("刷新中…","Updating…"));
        else if(data!=null){SimpleDateFormat time=new SimpleDateFormat("HH:mm",Locale.US);refresh.value(time.format(new Date(data.fetchedAt))+tr(" 更新"," updated"));}
        else refresh.value(message.isEmpty()?tr("尚未更新","Not updated"):tr("更新失败","Update failed"));
        String failure=controller.errorMessage();status.setText(store.enabled()?(failure.isEmpty()?message:failure):"");
    }
    private void chooseCity(){cityPicker.show();}
    private void serviceInformation(){
        ManagementUi.Sheet sheet=ui.new Sheet(tr("服务说明","Service information"));information=sheet;
        boolean primary=snapshot==null?WeatherConfiguration.load(ui).configured():"qweather".equals(snapshot.provider);
        sheet.body.addView(ui.label(tr("天气来源","Weather source"),15));
        TextView attribution=ui.muted(snapshot==null?(primary?tr("和风天气优先，UAPI 备用","QWeather first, UAPI fallback"):"UAPI")
            :primary?tr("天气服务由和风天气提供","Weather service provided by QWeather"):tr("天气服务由 UAPI 提供","Weather service provided by UAPI"),12);
        attribution.setTextColor(0xffb0b4be);attribution.setLineSpacing(ui.px(3),1);sheet.body.addView(attribution,new LinearLayout.LayoutParams(-1,-2));
        detail(sheet,tr("自动定位","Automatic location"),tr("优先使用系统位置；不可用时根据网络 IP 推测城市，可手动选择城市。","Uses system location when available, otherwise estimates the city from the network IP. You can choose a city manually."));
        detail(sheet,tr("隐私与条款","Privacy and terms"),tr("通过 HTTPS 查询和风天气，失败时尝试 UAPI。地区选择和搜索在本地完成，手机输入仅在当前局域网会话中接收。","Queries QWeather over HTTPS with UAPI fallback. Region search is local; phone input is limited to the current LAN session."));
        if(snapshot!=null){SimpleDateFormat time=new SimpleDateFormat("MM-dd HH:mm",Locale.US);detail(sheet,tr("数据时间","Observation time"),snapshot.observedAt>0?time.format(new Date(snapshot.observedAt)):tr("接口未提供可识别时间","Observation time unavailable"));}
        sheet.action(primary?tr("和风天气官网","QWeather site"):tr("UAPI 官网","UAPI website"),()->open(primary?"https://www.qweather.com":"https://uapis.cn"));sheet.action(tr("隐私与条款","Privacy and terms"),()->open(primary?"https://dev.qweather.com/docs/terms/tos/":"https://uapis.cn/docs/getting-started/privacy-policy"));Button close=sheet.action(tr("关闭","Close"),sheet.dialog::dismiss);
        sheet.dialog.setOnDismissListener(d->{information=null;if(active)service.requestFocus();});sheet.show(close);
    }
    private void detail(ManagementUi.Sheet sheet,String heading,String text){
        if(sheet.body.getChildCount()>0)ui.gap(sheet.body,12);sheet.body.addView(ui.label(heading,15));TextView body=ui.muted(text,13);body.setLineSpacing(ui.px(3),1);sheet.body.addView(body);
    }
    private void open(String url){try{ui.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){ui.feedback(tr("当前电视没有可用的浏览器","No browser is available on this TV"));}}
}
