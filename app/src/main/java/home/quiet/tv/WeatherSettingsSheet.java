package home.quiet.tv;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.IOException;

/** Location and source tools share preferences and keep consent in the same native sheet. */
final class WeatherSettingsSheet {
    static final int LOCATION_REQUEST=7431;
    private final AppManagementActivity ui;
    private final WeatherStore store;
    private final WeatherClient sources;
    private final WeatherController controller;
    private final WeatherCityPicker cities;
    private final Runnable changed,closed;
    private ManagementUi.Sheet sheet;
    private DisplayAppearanceChoice providers;
    private DesktopSettingsRow automatic,fallback,city;
    private TextView status;
    private String page="sources",root="sources",pendingProvider="",error="";
    private boolean active,destroyed,wallpaperPreview;
    private View origin;
    private int renderGeneration;
    WeatherSettingsSheet(AppManagementActivity ui,Runnable changed){this(ui,changed,()->{});}
    WeatherSettingsSheet(AppManagementActivity ui,Runnable changed,Runnable closed){this.ui=ui;this.changed=changed;this.closed=closed;store=new WeatherStore(ui);sources=new WeatherClient(ui);controller=new WeatherController(ui,ui.chinese(),(data,message,busy)->{error=message;refresh();changed.run();});cities=new WeatherCityPicker(ui,store,()->{controller.settingsChanged();refresh();changed.run();});}
    private String tr(String zh,String en){return ui.tr(zh,en);}
    private TextView copy(String value){TextView text=ui.muted(value,14);text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,13.44f*ui.unit);text.setLineSpacing(ui.px(3.84f),1);return text;}
    private DesktopSettingsRow row(LinearLayout parent,String text,String tag,Runnable action,boolean toggle){DesktopSettingsRow row=new DesktopSettingsRow(ui,text,"",false);row.displayStyle();row.textSizes(14.016f,11.52f);row.hideIcon();row.setTag(tag);row.setOnClickListener(v->action.run());if(!toggle)row.tail("arrow");parent.addView(row,new LinearLayout.LayoutParams(-1,ui.px(toggle?42:45)));return row;}
    void show(){showSources();}
    void showLocation(){show("location");}
    void showSources(){show("sources");}
    private void show(String target){if(sheet!=null||destroyed)return;root=page=target;create();}
    private void create(){
        origin=ui.getCurrentFocus();sheet=ui.new Sheet(root.equals("location")?tr("天气位置","Weather location"):tr("来源与服务","Sources and services"));ManagementUi.Sheet current=sheet;
        if(root.equals("sources"))sheet.contentWidth(520,640);
        sheet.settingsLayout(tr("关闭","Close"),this::close);if(root.equals("sources"))sheet.contentSize(520,340);sheet.dialog.setOnKeyListener((d,key,event)->{if(key!=KeyEvent.KEYCODE_BACK&&key!=KeyEvent.KEYCODE_ESCAPE)return false;if(event.getAction()==KeyEvent.ACTION_UP){if(providers!=null&&providers.back())return true;if(page.equals("consent")){pendingProvider="";page=root;render();}else close();}return true;});
        sheet.dialog.setOnDismissListener(d->{if(sheet==current){sheet=null;pendingProvider="";updateDemand();if(active)closed.run();if(active&&origin!=null&&origin.isShown())origin.requestFocusFromTouch();}});render();sheet.show(null);updateDemand();
    }
    void showCity(){
        boolean restore=sheet!=null;if(restore)sheet.dialog.hide();cities.show(()->{if(destroyed)return;if(restore&&sheet!=null&&active){sheet.dialog.show();render();}changed.run();});
    }
    private void render(){
        if(sheet==null)return;int version=++renderGeneration;ManagementUi.Sheet current=sheet;View focused=sheet.dialog.getCurrentFocus();Object tag=focused==null?null:focused.getTag();
        sheet.settingsActions(page.equals("consent"));sheet.body.removeAllViews();sheet.actions.removeAllViews();providers=null;automatic=fallback=city=null;status=null;
        if(page.equals("consent")){
            sheet.body.addView(ui.label(tr("启用 Open-Meteo？","Enable Open-Meteo?"),15));sheet.body.addView(copy(tr("天气数据为模型估算，免费接口仅限非商业使用且有请求限额。连接情况取决于当前网络。","Model estimates. The free API is for non-commercial use and has request limits. Availability depends on your network.")));
            Button cancel=sheet.action(tr("取消","Cancel"),()->{pendingProvider="";page=root;render();});cancel.setTag("weather-consent-cancel");Button enable=sheet.action(tr("启用","Enable"),()->{store.meteoFallback(true);if(!pendingProvider.isEmpty())store.provider(pendingProvider);pendingProvider="";page=root;render();controller.settingsChanged();changed.run();});enable.setTag("weather-consent-enable");ui.emphasize(enable,true);
        }else{
            LinearLayout group=page.equals("location")?ui.group():ui.column();sheet.body.addView(group,new LinearLayout.LayoutParams(-1,-2));
            if(page.equals("location")){
                automatic=row(group,tr("自动定位","Automatic location"),"weather-sheet-auto",this::automatic,true);
                city=row(group,tr("手动选择位置","Choose location manually"),"weather-sheet-city",this::showCity,false);
            }else{
                providers=new DisplayAppearanceChoice(ui,"weather-sheet-provider",tr("天气来源","Weather source"),new String[]{"auto","qweather","uapi","openweather","openmeteo"},new String[]{tr("自动切换","Automatic fallback"),tr("和风天气","QWeather"),"UAPI","OpenWeather","Open-Meteo"},new DisplayAppearanceChoice.Access(){public String read(){return store.provider();}public void save(String value)throws IOException{chooseProvider(value);}},false,new DisplayAppearanceChoice.Preview(){public void value(String key,Object value){}public void clear(String key){}},()->{}).dialogStyle().optionsOnly().errors(message->{error=message;refresh();});group.addView(providers,new LinearLayout.LayoutParams(-1,-2));
                if(store.provider().equals("auto"))fallback=row(group,tr("Open-Meteo 备用","Open-Meteo fallback"),"weather-sheet-fallback",()->{if(store.meteoFallback()){store.meteoFallback(false);controller.settingsChanged();changed.run();render();}else{page="consent";render();}},true);
            }
            status=copy("");androidx.core.view.ViewCompat.setAccessibilityLiveRegion(status,androidx.core.view.ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);sheet.body.addView(status);refresh();
        }
        sheet.body.post(()->{if(sheet!=current||version!=renderGeneration||!sheet.dialog.isShowing())return;View target=tag==null?null:sheet.box.findViewWithTag(tag);if(target==null||!target.isShown()||!target.isEnabled())target=sheet.box.findViewWithTag(page.equals("consent")?"weather-consent-cancel":page.equals("location")?"weather-sheet-auto":"appearance-weather-sheet-provider-"+store.provider());if(target!=null)target.requestFocusFromTouch();});
    }
    private void chooseProvider(String value)throws IOException{
        if(!sources.available(value))throw new IOException(tr("此来源尚未配置，当前来源已保留","This source is not configured. The current source is retained."));
        if(value.equals("openmeteo")&&!store.meteoFallback()){pendingProvider=value;page="consent";render();return;}
        store.savePhoneSetting("provider",value);controller.settingsChanged();changed.run();ui.success(tr("天气来源已更新","Weather source updated"));ManagementUi.Sheet current=sheet;sheet.body.post(()->{if(sheet==current&&!page.equals("consent"))render();});
    }
    private void automatic(){
        if(store.automatic()){if(!store.fixAutomaticCity(ui.chinese())){showCity();return;}}
        else{store.automaticMode();if(Build.VERSION.SDK_INT>=23&&ui.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){controller.pause();ui.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_REQUEST);refresh();changed.run();return;}}
        controller.settingsChanged();refresh();changed.run();
    }
    private void refresh(){if(sheet==null||page.equals("consent"))return;if(city!=null){WeatherData data=store.cached(ui.chinese());city.value(store.automatic()?(data==null?tr("未定位","Not located"):data.city):store.city());}if(automatic!=null)automatic.checked(store.automatic());if(fallback!=null)fallback.checked(store.meteoFallback());if(providers!=null)providers.refresh();if(status!=null){status.setText(error);status.setVisibility(error.isEmpty()?View.GONE:View.VISIBLE);}}
    void permissionResult(int request){if(request==LOCATION_REQUEST&&sheet!=null&&active){controller.resume();controller.settingsChanged();}}
    boolean isOpen(){return sheet!=null;}
    void settingsChanged(){if(sheet!=null)controller.settingsChanged();}
    private void updateDemand(){controller.wallpaperWeather(sheet!=null||wallpaperPreview);if(active&&(sheet!=null||wallpaperPreview))controller.resume();else controller.pause();}
    void wallpaperPreview(boolean value){if(wallpaperPreview==value)return;wallpaperPreview=value;updateDemand();}
    void resume(){active=true;if(sheet!=null){sheet.dialog.show();refresh();}updateDemand();}
    void pause(){active=false;controller.pause();cities.pause();if(sheet!=null)sheet.dialog.hide();}
    private void close(){if(sheet!=null)sheet.dialog.dismiss();}
    void save(Bundle state){if(sheet!=null){Bundle value=new Bundle();value.putString("root",root);value.putString("page",page);value.putString("pending",pendingProvider);value.putString("error",error);View focus=sheet.dialog.getCurrentFocus();if(focus!=null&&focus.getTag() instanceof String)value.putString("focus",(String)focus.getTag());value.putFloat("scroll",sheet.scroll.getScrollY()/ui.unit);state.putBundle("wall-weather-sheet",value);}}
    void restore(Bundle state){Bundle value=state==null?null:state.getBundle("wall-weather-sheet");if(value==null)return;root=value.getString("root","sources");if(!root.equals("location"))root="sources";page=value.getString("page",root);if(!page.equals("consent"))page=root;pendingProvider=value.getString("pending","");error=value.getString("error","");create();sheet.body.post(()->{if(sheet==null)return;View focus=sheet.box.findViewWithTag(value.getString("focus",""));if(focus!=null)focus.requestFocusFromTouch();sheet.scroll.scrollTo(0,Math.round(value.getFloat("scroll")*ui.unit));});}
    void destroy(){destroyed=true;pause();if(sheet!=null){sheet.dialog.setOnDismissListener(null);sheet.dialog.dismiss();sheet=null;}cities.destroy();controller.destroy();}
}
