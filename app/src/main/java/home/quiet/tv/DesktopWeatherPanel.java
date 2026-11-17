package home.quiet.tv;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Frequent weather tasks stay on the page; location and source tools share the desktop editor. */
final class DesktopWeatherPanel {
    final ScrollView view;
    final LinearLayout body;
    private final AppManagementActivity ui;
    private final WeatherStore store;
    private final WeatherController controller;
    private final WeatherSettingsSheet editor;
    private final DesktopSettingsRow city,refresh,service;
    private final TextView status;
    private boolean active,manualRefresh,sawLoading;
    DesktopWeatherPanel(AppManagementActivity ui){
        this.ui=ui;store=new WeatherStore(ui);controller=new WeatherController(ui,ui.chinese(),this::render);editor=new WeatherSettingsSheet(ui,controller::settingsChanged,()->{if(active)controller.resume();});view=ui.settingsScroll();view.setFocusable(false);view.setVerticalScrollBarEnabled(false);body=ui.column();view.addView(body);body.addView(ui.section(tr("天气","Weather")));
        LinearLayout group=ui.group();body.addView(group);
        city=ui.setting(tr("天气位置","Weather location"),"pin","city");city.tail("arrow");city.setOnClickListener(v->{controller.pause();editor.showLocation();});ui.addSetting(group,city);
        refresh=ui.setting(tr("刷新天气","Refresh weather"),"refresh","refresh");refresh.setOnClickListener(v->{if(!store.enabled())return;if(manualRefresh){ui.feedback(tr("正在刷新天气，请稍候","Updating weather. Please wait."));return;}manualRefresh=true;sawLoading=false;controller.refresh(true);if(manualRefresh&&!sawLoading){manualRefresh=false;String error=controller.errorMessage();if(error.isEmpty())ui.feedback(tr("请稍后再次刷新","Please try refreshing again shortly."));else ui.error(error);}});ui.addSetting(group,refresh);
        service=ui.setting(tr("来源与服务","Sources and services"),"weather","provider");service.tail("arrow");service.setOnClickListener(v->{controller.pause();editor.showSources();});ui.addSetting(group,service);
        status=ui.muted("",12);androidx.core.view.ViewCompat.setAccessibilityLiveRegion(status,androidx.core.view.ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=ui.px(12);body.addView(status,sp);
        render(store.cached(ui.chinese()),"",false);
    }
    private String tr(String zh,String en){return ui.tr(zh,en);}
    DesktopSettingsRow firstRow(){return city;}
    DesktopSettingsRow[] rows(){return new DesktopSettingsRow[]{city,refresh,service};}
    void resume(){active=true;editor.resume();if(!editor.isOpen())controller.resume();}
    void pause(){active=false;manualRefresh=false;editor.pause();controller.pause();}
    void destroy(){editor.destroy();controller.destroy();}
    void settingsChanged(){controller.settingsChanged();editor.settingsChanged();}
    void permissionResult(int code){editor.permissionResult(code);}
    void save(Bundle state){editor.save(state);}
    void restore(Bundle state){editor.restore(state);}
    private void render(WeatherData data,String message,boolean loading){
        if(manualRefresh){if(loading)sawLoading=true;else if(sawLoading){manualRefresh=false;if(controller.errorMessage().isEmpty()&&data!=null)ui.success(tr("天气已更新","Weather updated"));else ui.error(controller.errorMessage().isEmpty()?tr("天气更新未完成","Weather update incomplete"):controller.errorMessage());}}
        WeatherData cached=data!=null?data:store.cached(ui.chinese());String name=store.automatic()?(cached==null?"":cached.city):store.city();
        city.value((store.automatic()?tr("自动定位","Automatic"):tr("手动选择","Manual"))+(name.isEmpty()?"":"  "+name));
        service.value("auto".equals(store.provider())?tr("自动切换","Automatic fallback"):WeatherClient.providerName(store.provider()));
        refresh.available(store.enabled());city.setNextFocusDownId(store.enabled()?refresh.getId():service.getId());service.setNextFocusUpId(store.enabled()?refresh.getId():city.getId());
        if(!store.enabled())refresh.value(tr("天气已关闭","Weather is off"));
        else if(loading)refresh.value(tr("刷新中…","Updating…"));
        else if(data!=null)refresh.value(new SimpleDateFormat("HH:mm",Locale.US).format(new Date(data.fetchedAt))+tr(" 更新"," updated"));
        else refresh.value(message.isEmpty()?tr("尚未更新","Not updated"):tr("更新失败","Update failed"));
        String failure=controller.errorMessage();status.setText(store.enabled()?(failure.isEmpty()?message:failure):"");status.setVisibility(status.length()==0?View.GONE:View.VISIBLE);
    }
}
