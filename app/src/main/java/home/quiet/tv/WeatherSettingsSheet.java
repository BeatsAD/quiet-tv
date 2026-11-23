package home.quiet.tv;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** Native weather location and source settings. */
final class WeatherSettingsSheet {
    static final int LOCATION_REQUEST=7431;
    private final AppManagementActivity ui;
    private final WeatherStore store;
    private final WeatherClient sources;
    private final WeatherController controller;
    private final WeatherCityPicker cities;
    private final Runnable changed,closed;
    private ManagementUi.Sheet sheet;
    private final Map<String,LinearLayout> providers=new LinkedHashMap<>();
    private DesktopSettingsRow automatic,city,serviceRow;
    private TextView status,autoSummary,serviceCopy;
    private String root="sources",error="";
    private boolean active,destroyed,wallpaperPreview,serviceExpanded;
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
    private void show(String target){if(sheet!=null||destroyed)return;root=target;serviceExpanded=false;create();}
    private void create(){
        origin=ui.getCurrentFocus();sheet=ui.new Sheet(root.equals("location")?tr("天气位置","Weather location"):tr("来源与服务","Sources and services"));ManagementUi.Sheet current=sheet;
        sheet.settingsLayout(tr("关闭","Close"),this::close);sheet.settingsActions(false);if(root.equals("sources"))sheet.contentSize(520,365);sheet.dialog.setOnKeyListener((d,key,event)->KeyFeedback.backKey(ui,event,sheet!=null,this::close));
        sheet.dialog.setOnDismissListener(d->{if(sheet==current){sheet=null;updateDemand();if(active)closed.run();if(active&&origin!=null&&origin.isShown())origin.requestFocusFromTouch();}});render();sheet.show(null);updateDemand();
    }
    void showCity(){
        boolean restore=sheet!=null;if(restore)sheet.dialog.hide();cities.show(()->{if(destroyed)return;if(restore&&sheet!=null&&active){sheet.dialog.show();render();}changed.run();});
    }
    private void render(){
        if(sheet==null)return;int version=++renderGeneration;ManagementUi.Sheet current=sheet;View focused=sheet.dialog.getCurrentFocus();Object tag=focused==null?null:focused.getTag();
        sheet.body.removeAllViews();providers.clear();automatic=city=serviceRow=null;autoSummary=serviceCopy=null;
        if(root.equals("location")){
            LinearLayout group=ui.group();sheet.body.addView(group,new LinearLayout.LayoutParams(-1,-2));
            automatic=row(group,tr("自动定位","Automatic location"),"weather-sheet-auto",this::automatic,true);
            city=row(group,tr("手动选择位置","Choose location manually"),"weather-sheet-city",this::showCity,false);
        }else renderSources();
        status=copy("");
        androidx.core.view.ViewCompat.setAccessibilityLiveRegion(status,androidx.core.view.ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);
        sheet.body.addView(status);
        refresh();
        sheet.body.post(()->{
            if(sheet!=current||version!=renderGeneration||!sheet.dialog.isShowing())return;
            View target=tag==null?null:sheet.box.findViewWithTag(tag);
            if(target==null||!target.isShown()||!target.isEnabled())
                target=root.equals("location")?automatic:providers.get(store.provider());
            if(target!=null)target.requestFocusFromTouch();
        });
    }
    private void renderSources(){
        sheet.body.addView(sourceCard("auto"),new LinearLayout.LayoutParams(-1,ui.px(58)));
        for(String[] pair:new String[][]{{"qweather","uapi"},{"openweather","openmeteo"}}){
            LinearLayout line=ui.row();
            LinearLayout.LayoutParams rowLayout=new LinearLayout.LayoutParams(-1,ui.px(51));
            rowLayout.topMargin=ui.px(8);
            sheet.body.addView(line,rowLayout);
            for(int i=0;i<pair.length;i++){
                LinearLayout.LayoutParams tileLayout=new LinearLayout.LayoutParams(0,-1,1);
                if(i>0)tileLayout.leftMargin=ui.px(8);
                line.addView(sourceCard(pair[i]),tileLayout);
            }
        }
        serviceRow=row(sheet.body,tr("服务说明","Service information"),"weather-sheet-service",()->{
            serviceExpanded=!serviceExpanded;
            refresh();
        },false);
        serviceCopy=copy("");
        serviceCopy.setPadding(ui.px(12),0,ui.px(12),ui.px(6));
        sheet.body.addView(serviceCopy);
    }
    private LinearLayout sourceCard(String id){
        boolean auto=id.equals("auto");
        String title=auto?tr("自动切换","Automatic fallback"):id.equals("qweather")?tr("和风天气","QWeather"):WeatherClient.providerName(id);
        LinearLayout card=ui.row();
        card.setPadding(ui.px(12),0,ui.px(12),0);
        card.setTag("appearance-weather-sheet-provider-"+id);
        card.setFocusable(true);
        card.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,12,UiPalette.CONTROL,false,UiPalette.EDGE));

        ImageView mark=new ImageView(ui);
        mark.setDuplicateParentStateEnabled(true);
        if(auto)mark.setImageDrawable(new QuickControlIcon("refresh-cw"));
        else try(InputStream input=ui.getAssets().open("phone-settings/assets/weather-"+id+".png")){
            Bitmap bitmap=BitmapFactory.decodeStream(input);
            if("uapi".equals(id)&&bitmap!=null){
                BitmapDrawable normal=new BitmapDrawable(ui.getResources(),bitmap);
                BitmapDrawable focused=new BitmapDrawable(ui.getResources(),bitmap);
                focused.setColorFilter(UiPalette.INK,PorterDuff.Mode.SRC_IN);
                StateListDrawable icon=new StateListDrawable();
                icon.addState(new int[]{android.R.attr.state_focused,android.R.attr.state_window_focused},focused);
                icon.addState(new int[0],normal);
                mark.setImageDrawable(icon);
            }else mark.setImageBitmap(bitmap);
        }catch(IOException e){DiagnosticLog.info("weather.icon.unavailable",id);}
        LinearLayout.LayoutParams markLayout=new LinearLayout.LayoutParams(ui.px(28),ui.px(28));
        markLayout.rightMargin=ui.px(10);
        card.addView(mark,markLayout);

        LinearLayout labels=ui.column();
        labels.setDuplicateParentStateEnabled(true);
        labels.addView(sourceLabel(title,15,UiPalette.TEXT,UiPalette.INK));
        if(auto){
            autoSummary=sourceLabel(tr("Open-Meteo 最后尝试","Tries Open-Meteo last"),12,UiPalette.SECONDARY,UiPalette.INK_SECONDARY);
            labels.addView(autoSummary);
        }else if(!sources.available(id)){
            labels.addView(sourceLabel(tr("未配置","Not configured"),11,UiPalette.SECONDARY,UiPalette.INK_SECONDARY));
        }
        card.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        ImageView check=new ImageView(ui);
        check.setDuplicateParentStateEnabled(true);
        check.setImageDrawable(new QuickControlIcon("check"));
        check.setTag("weather-provider-check");
        card.addView(check,new LinearLayout.LayoutParams(ui.px(17),ui.px(17)));
        card.setOnClickListener(v->chooseProvider(id));
        providers.put(id,card);
        return card;
    }
    private TextView sourceLabel(String text,int size,int normal,int focused){
        TextView label=ui.label(text,size);
        label.setDuplicateParentStateEnabled(true);
        label.setTextColor(SettingsFocusDrawable.textColors(normal,focused));
        return label;
    }
    private void chooseProvider(String value){
        if(!sources.available(value)){
            error=tr("此来源尚未配置，当前来源已保留","This source is not configured. The current source is retained.");
            refresh();
            return;
        }
        if(value.equals(store.provider())){error="";refresh();return;}
        try{
            store.savePhoneSetting("provider",value);
            error="";
            controller.settingsChanged();refresh();changed.run();
            ui.success(tr("天气来源已更新","Weather source updated"));
        }catch(IOException e){error=tr("天气来源保存未确认","Weather source could not be saved");refresh();}
    }
    private void automatic(){
        if(store.automatic()){if(!store.fixAutomaticCity(ui.chinese())){showCity();return;}}
        else{store.automaticMode();if(Build.VERSION.SDK_INT>=23&&ui.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){controller.pause();ui.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_REQUEST);refresh();changed.run();return;}}
        controller.settingsChanged();refresh();changed.run();
    }
    private void refresh(){
        if(sheet==null)return;
        if(city!=null){WeatherData data=store.cached(ui.chinese());city.value(store.automatic()?(data==null?tr("未定位","Not located"):data.city):store.city());}
        if(automatic!=null)automatic.checked(store.automatic());
        String selected=store.provider();
        for(Map.Entry<String,LinearLayout> entry:providers.entrySet()){
            boolean current=entry.getKey().equals(selected);
            entry.getValue().setSelected(current);
            entry.getValue().findViewWithTag("weather-provider-check").setVisibility(current?View.VISIBLE:View.INVISIBLE);
        }
        if(autoSummary!=null)autoSummary.setVisibility(store.meteoFallback()?View.VISIBLE:View.GONE);
        if(serviceRow!=null){
            serviceRow.tail(serviceExpanded?"down":"arrow");
            serviceCopy.setVisibility(serviceExpanded?View.VISIBLE:View.GONE);
            if(serviceExpanded){
                WeatherData data=store.cached(ui.chinese());
                boolean meteoData=data!=null&&"openmeteo".equals(data.provider);
                String information=data==null?"":tr("当前来源  ","Current source  ")+WeatherClient.providerName(data.provider)+"\n";
                information+=tr("天气请求会向所选来源发送地点或坐标。","Weather requests send the location or coordinates to the selected service.");
                if("openmeteo".equals(selected)||("auto".equals(selected)&&store.meteoFallback())||meteoData)
                    information+="\n"+tr("Open-Meteo 提供模型估算。免费接口仅限非商业使用，有请求限额。","Open-Meteo provides model estimates. Free API access is for non-commercial use and has request limits.");
                if(meteoData)information+="\n"+tr("天气数据由 Open-Meteo 提供，按 CC BY 4.0 使用。","Weather data by Open-Meteo, under CC BY 4.0.");
                serviceCopy.setText(information);
            }
        }
        if(status!=null){status.setText(error);status.setVisibility(error.isEmpty()?View.GONE:View.VISIBLE);}
    }
    void permissionResult(int request){if(request==LOCATION_REQUEST&&sheet!=null&&active){controller.resume();controller.settingsChanged();}}
    boolean isOpen(){return sheet!=null;}
    void settingsChanged(){if(sheet!=null){controller.settingsChanged();refresh();}}
    private void updateDemand(){controller.wallpaperWeather(sheet!=null||wallpaperPreview);if(active&&(sheet!=null||wallpaperPreview))controller.resume();else controller.pause();}
    void wallpaperPreview(boolean value){if(wallpaperPreview==value)return;wallpaperPreview=value;updateDemand();}
    void resume(){active=true;if(sheet!=null){sheet.dialog.show();refresh();}updateDemand();}
    void pause(){active=false;controller.pause();cities.pause();if(sheet!=null)sheet.dialog.hide();}
    private void close(){if(sheet!=null)sheet.dialog.dismiss();}
    void save(Bundle state){if(sheet!=null){Bundle value=new Bundle();value.putString("root",root);value.putBoolean("service-expanded",serviceExpanded);value.putString("error",error);View focus=sheet.dialog.getCurrentFocus();if(focus!=null&&focus.getTag() instanceof String)value.putString("focus",(String)focus.getTag());value.putFloat("scroll",sheet.scroll.getScrollY()/ui.unit);state.putBundle("wall-weather-sheet",value);}}
    void restore(Bundle state){Bundle value=state==null?null:state.getBundle("wall-weather-sheet");if(value==null)return;root=value.getString("root","sources");if(!root.equals("location"))root="sources";serviceExpanded=value.getBoolean("service-expanded");error=value.getString("error","");create();sheet.body.post(()->{if(sheet==null)return;View focus=sheet.box.findViewWithTag(value.getString("focus",""));if(focus!=null)focus.requestFocusFromTouch();sheet.scroll.scrollTo(0,Math.round(value.getFloat("scroll")*ui.unit));});}
    void destroy(){destroyed=true;pause();if(sheet!=null){sheet.dialog.setOnDismissListener(null);sheet.dialog.dismiss();sheet=null;}cities.destroy();controller.destroy();}
}
