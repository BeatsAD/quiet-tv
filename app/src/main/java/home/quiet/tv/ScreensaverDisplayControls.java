package home.quiet.tv;

import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Confirmed screensaver media applies inside the desktop settings column. */
final class ScreensaverDisplayControls {
    private interface Edit {ScreensaverSettings apply(ScreensaverSettings current);}
    interface Preview {void show(ScreensaverSettings settings,String candidate);}
    private final ManagementUi ui;
    private final LinearLayout group;
    private final ScreensaverStore store;
    private final WallpaperStore media;
    private final Preview preview;
    private final Runnable rewired;
    private final DesktopMediaImportPanel importer;
    private ScreensaverSettings settings;
    private DisplayAppearanceChoice waiting,content,timePosition,weatherPosition,analogStyle,digitalLayout;
    private DesktopSettingsRow choose,time,weather,sound,analog,calendar,lunar,seconds,flipSeconds,flipSound;
    private LinearLayout library;
    private DesktopMediaLibrary materials;
    private String candidate="",draftMode="",draftTimePosition="",draftWeatherPosition="",draftAnalogStyle="",draftDigitalLayout="",error="";
    private boolean expanded;
    ScreensaverDisplayControls(ManagementUi ui,LinearLayout group,DesktopMediaImportPanel importer,Preview preview,Runnable rewired){this.ui=ui;this.group=group;this.importer=importer;this.preview=preview;this.rewired=rewired;store=ScreensaverStore.get(ui);media=WallpaperStore.get(ui);read();}
    private int p(float value){return ui.px(value*.75f);}
    private String tr(String zh,String en){return ui.tr(zh,en);}
    private TextView label(String text,float size){TextView view=ui.label(text,12);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,p(size));view.setTextColor(UiPalette.SECONDARY);return view;}
    private void read(){try{settings=store.snapshot(media.snapshot());error="";build();}catch(IOException error){this.error=tr("屏保设置无法读取，原配置已保留","Unable to read screensaver settings");group.removeAllViews();TextView message=label(tr("屏保设置无法读取，原配置已保留","Unable to read screensaver settings"),15.36f);group.addView(message);group.addView(ui.button(tr("重新读取","Read again"),this::read));}}
    private void build(){
        group.removeAllViews();String[] minutes={"0","1","2","5","10","15","30"},waitNames={tr("关闭","Off"),"1 "+tr("分钟","min"),"2 "+tr("分钟","min"),"5 "+tr("分钟","min"),"10 "+tr("分钟","min"),"15 "+tr("分钟","min"),"30 "+tr("分钟","min")};
        DisplayAppearanceChoice.Preview bridge=new DisplayAppearanceChoice.Preview(){public void value(String key,Object value){if(key.equals("saver-content"))draftMode=String.valueOf(value);if(key.equals("saver-clock-position"))draftTimePosition=String.valueOf(value);if(key.equals("saver-weather-position"))draftWeatherPosition=String.valueOf(value);if(key.equals("saver-analog-style"))draftAnalogStyle=String.valueOf(value);if(key.equals("saver-digital-layout"))draftDigitalLayout=String.valueOf(value);scene();}public void clear(String key){if(key.equals("saver-content"))draftMode="";if(key.equals("saver-clock-position"))draftTimePosition="";if(key.equals("saver-weather-position"))draftWeatherPosition="";if(key.equals("saver-analog-style"))draftAnalogStyle="";if(key.equals("saver-digital-layout"))draftDigitalLayout="";scene();}};
        waiting=new DisplayAppearanceChoice(ui,"saver-wait",tr("闲置等待时间","Idle timeout"),minutes,waitNames,new DisplayAppearanceChoice.Access(){public String read(){return String.valueOf(settings.idleMinutes);}public void save(String value)throws IOException{saveSettings(current->current.waitMinutes(Integer.parseInt(value)));}},false,bridge,rewired).twoColumns();group.addView(waiting);
        content=new DisplayAppearanceChoice(ui,"saver-content",tr("屏保内容","Screensaver content"),new String[]{ScreensaverSettings.FOLLOW,ScreensaverSettings.CLOCK,ScreensaverSettings.FLIP,ScreensaverSettings.CUSTOM},new String[]{tr("跟随桌面","Follow desktop"),tr("数字时间","Digital time"),tr("翻页时钟","Flip clock"),tr("自定义","Custom")},new DisplayAppearanceChoice.Access(){public String read(){return settings.mode;}public void save(String value)throws IOException{writeSettings(current->current.content(value));candidate="";draftMode="";expanded=false;renderLibrary();refresh();scene();}},false,bridge,rewired).twoColumns();group.addView(content);
        flipSeconds=toggle(tr("显示秒","Show seconds"),"saver-flip-seconds",current->current.flipSeconds(!current.flipShowSeconds));
        flipSound=toggle(tr("翻页声音","Flip sound"),"saver-flip-sound",current->current.flipSound(!current.flipSound));
        analog=toggle(tr("模拟时钟","Analog clock"),"saver-analog",current->current.clock(current.clock.analog(!current.clock.analog)));
        analogStyle=clockChoice(true,bridge);
        group.addView(analogStyle);
        digitalLayout=clockChoice(false,bridge);
        group.addView(digitalLayout);
        calendar=toggle(tr("日历","Calendar"),"saver-calendar",current->current.clock(current.clock.calendar(!current.clock.calendar)));
        lunar=toggle(tr("显示农历","Show lunar date"),"saver-lunar",current->current.clock(current.clock.lunar(!current.clock.lunar)));
        seconds=toggle(tr("显示秒","Show seconds"),"saver-seconds",current->current.clock(current.clock.seconds(!current.clock.seconds)));
        choose=row(tr("选择屏保素材","Screensaver media"),"saver-media");choose.tail("arrow");choose.setOnClickListener(v->{expanded=!expanded;renderLibrary();refresh();scene();});group.addView(choose,new LinearLayout.LayoutParams(-1,ui.px(44.72f)));
        materials=new DesktopMediaLibrary(ui,"screensaver",importer,new DesktopMediaLibrary.Actions(){public String selected(){return ScreensaverDisplayControls.this.selected();}public boolean select(String id){candidate=id;return apply();}public void imported(String id,boolean apply)throws IOException{ScreensaverDisplayControls.this.imported(id,apply);}public void removed(){candidate="";settings=store.current();refresh();scene();choose.requestFocusFromTouch();}},rewired);library=materials.library;group.addView(library);
        time=toggle(tr("显示时钟","Show clock"),"saver-clock",current->current.time(!current.showTime));
        timePosition=positionChoice(true,bridge);group.addView(timePosition);
        weather=toggle(tr("显示天气","Show weather"),"saver-weather",current->current.weather(!current.showWeather));
        weatherPosition=positionChoice(false,bridge);group.addView(weatherPosition);
        sound=row(tr("屏保声音","Screensaver sound"),"saver-sound");sound.setOnClickListener(v->{try{saveSettings(current->current.sound(!current.sound));PhoneSettingsSession.get(ui).configurationChanged();}catch(IOException error){ui.error(error.getMessage());}});group.addView(sound,new LinearLayout.LayoutParams(-1,ui.px(44.72f)));
        refresh();
    }
    private DisplayAppearanceChoice positionChoice(boolean clock,DisplayAppearanceChoice.Preview bridge){return new DisplayAppearanceChoice(ui,clock?"saver-clock-position":"saver-weather-position",clock?tr("时钟位置","Clock position"):tr("天气位置","Weather position"),ScreensaverSettings.POSITIONS,new String[]{tr("左上角","Top left"),tr("右上角","Top right"),tr("居中","Center"),tr("左下角","Bottom left"),tr("右下角","Bottom right")},new DisplayAppearanceChoice.Access(){public String read(){return clock?settings.timePosition:settings.weatherPosition;}public void save(String value)throws IOException{saveSettings(current->clock?current.timePosition(value):current.weatherPosition(value));}},false,bridge,rewired).twoColumns();}
    private DisplayAppearanceChoice clockChoice(boolean style,DisplayAppearanceChoice.Preview bridge){
        String[] values=style?ScreensaverSettings.ClockOptions.STYLES:ScreensaverSettings.ClockOptions.LAYOUTS;
        String[] labels=style?new String[]{tr("圆形","Round"),tr("方形","Square")}:
            new String[]{tr("标准时间","Standard time"),tr("大字日期","Large date")};
        return new DisplayAppearanceChoice(ui,style?"saver-analog-style":"saver-digital-layout",
            style?tr("表盘样式","Clock face"):tr("数字排版","Digital layout"),values,labels,
            new DisplayAppearanceChoice.Access(){
                public String read(){return style?settings.clock.style:settings.clock.layout;}
                public void save(String value)throws IOException{
                    saveSettings(current->current.clock(style?current.clock.style(value):current.clock.layout(value)));
                }
            },false,bridge,rewired).twoColumns();
    }
    private DesktopSettingsRow toggle(String title,String tag,Edit edit){
        DesktopSettingsRow row=row(title,tag);
        row.setOnClickListener(v->{try{saveSettings(edit);}catch(IOException failure){ui.error(failure.getMessage());}});
        group.addView(row,new LinearLayout.LayoutParams(-1,ui.px(44.72f)));
        return row;
    }
    private DesktopSettingsRow row(String name,String tag){DesktopSettingsRow row=new DesktopSettingsRow(ui,name,"",false);row.displayStyle();row.hideIcon();row.setTag(tag);row.setId(androidx.core.view.ViewCompat.generateViewId());return row;}
    private void writeSettings(Edit edit)throws IOException{
        try{
            synchronized(store){
                ScreensaverSettings next=edit.apply(store.current());
                store.save(next);
                settings=next;
            }
            error="";
        }catch(IOException failure){
            error=failure.getMessage();
            refresh();
            scene();
            throw failure;
        }
    }
    private void saveSettings(Edit edit)throws IOException{writeSettings(edit);draftMode="";refresh();scene();}
    void refresh(){
        if(settings==null||choose==null)return;ScreensaverSettings current=store.current();if(current!=null)settings=current;
        boolean custom=ScreensaverSettings.CUSTOM.equals(settings.mode);choose.setVisibility(custom?View.VISIBLE:View.GONE);choose.tail(expanded?"settings-up":"arrow");library.setVisibility(custom&&expanded?View.VISIBLE:View.GONE);time.setVisibility(settings.clockOnly()?View.GONE:View.VISIBLE);time.checked(settings.showTime);weather.checked(settings.showWeather);
        boolean digital=settings.digitalMode();
        analog.setVisibility(digital?View.VISIBLE:View.GONE);
        analog.checked(settings.clock.analog);
        analogStyle.setVisibility(settings.analogVisible()?View.VISIBLE:View.GONE);
        digitalLayout.setVisibility(digital&&!settings.clock.analog?View.VISIBLE:View.GONE);
        calendar.setVisibility(digital?View.VISIBLE:View.GONE);
        calendar.checked(settings.clock.calendar);
        lunar.setVisibility(settings.lunarAvailable()?View.VISIBLE:View.GONE);
        lunar.checked(settings.clock.lunar);
        seconds.setVisibility(digital&&!settings.largeDateVisible()?View.VISIBLE:View.GONE);
        seconds.checked(settings.clock.seconds);
        sound.setVisibility(settings.video(media.snapshot())?View.VISIBLE:View.GONE);sound.checked(settings.sound);
        flipSeconds.setVisibility(settings.flipping()?View.VISIBLE:View.GONE);flipSeconds.checked(settings.flipShowSeconds);flipSound.setVisibility(settings.flipping()?View.VISIBLE:View.GONE);flipSound.checked(settings.flipSound);
        boolean positionFocused=timePosition.hasFocus();if(settings.flipping()&&timePosition.getVisibility()==View.VISIBLE){timePosition.cancel(false);draftTimePosition="";}
        timePosition.setVisibility(!settings.flipping()&&!settings.composedClock()&&(settings.showTime||settings.clockOnly())?View.VISIBLE:View.GONE);weatherPosition.setVisibility(settings.showWeather?View.VISIBLE:View.GONE);
        WallpaperCatalog.Entry entry=media.snapshot().find(selected());choose.value(ScreensaverSettings.NO_MEDIA.equals(selected())?tr("空白背景","Blank background"):entry==null?tr("素材不可用","Unavailable"):entry.label(ui.chinese()));waiting.refresh();content.refresh();timePosition.refresh();weatherPosition.refresh();analogStyle.refresh();digitalLayout.refresh();rewired.run();if(positionFocused&&settings.flipping())flipSeconds.requestFocus();
    }
    private void scene(){if(settings==null)return;ScreensaverSettings shown=draftMode.isEmpty()?settings:settings.content(draftMode);if(!draftTimePosition.isEmpty())shown=shown.timePosition(draftTimePosition);if(!draftWeatherPosition.isEmpty())shown=shown.weatherPosition(draftWeatherPosition);if(!draftAnalogStyle.isEmpty())shown=shown.clock(shown.clock.style(draftAnalogStyle));if(!draftDigitalLayout.isEmpty())shown=shown.clock(shown.clock.layout(draftDigitalLayout));preview.show(shown,candidate);}
    void showPreview(){scene();}
    boolean drafting(){if(settings==null||content==null)return false;return !candidate.isEmpty()&&!candidate.equals(settings.mediaId)||content.drafting()||timePosition.drafting()||weatherPosition.drafting()||analogStyle.drafting()||digitalLayout.drafting();}
    private DisplayAppearanceChoice[] choices(){return new DisplayAppearanceChoice[]{content,waiting,timePosition,weatherPosition,analogStyle,digitalLayout};}
    String error(){if(!error.isEmpty())return error;for(DisplayAppearanceChoice choice:choices())if(choice!=null&&!choice.error().isEmpty())return choice.error();return "";}
    boolean contains(View view){while(view!=null){if(view==group)return true;view=view.getParent() instanceof View?(View)view.getParent():null;}return false;}
    private boolean apply(){
        if(candidate.isEmpty())return false;
        try{
            writeSettings(current->current.media(candidate));
            finishMediaSelection();
            ui.success(tr("屏保素材已应用","Screensaver media applied"));
            return true;
        }catch(IOException error){
            ui.error(error.getMessage());
            return false;
        }
    }
    private void finishMediaSelection(){
        candidate="";
        draftMode="";
        expanded=false;
        refresh();
        scene();
        choose.post(choose::requestFocusFromTouch);
    }
    void imported(String id,boolean apply)throws IOException{
        WallpaperCatalog.Entry entry=media.snapshot().find(id);
        if(entry==null)throw new IOException("素材不可用");
        if(apply){
            writeSettings(current->current.media(id));
            finishMediaSelection();
            return;
        }
        candidate=id;
        expanded=true;
        materials.chooseType(id);
        renderLibrary();
        refresh();
        scene();
        materials.focus(id);
    }
    boolean back(){for(DisplayAppearanceChoice choice:choices())if(choice!=null&&choice.back())return true;if(expanded){expanded=false;renderLibrary();refresh();choose.requestFocusFromTouch();return true;}if(!candidate.isEmpty()){candidate="";refresh();scene();return true;}return false;}
    void pause(){for(DisplayAppearanceChoice choice:choices())if(choice!=null)choice.cancel(false);draftMode=draftTimePosition=draftWeatherPosition=draftAnalogStyle=draftDigitalLayout="";}
    private String selected(){return candidate.isEmpty()?settings.customMediaId(media.snapshot()):candidate;}
    private void renderLibrary(){if(materials!=null&&expanded)materials.render();}
    private void refreshSelection(){if(materials!=null)materials.refreshSelection();}
    void refreshNames(){if(materials!=null)materials.refreshNames();WallpaperCatalog.Entry entry=media.snapshot().find(selected());if(choose!=null&&entry!=null)choose.value(entry.label(ui.chinese()));}
    void refreshLibrary(){if(materials!=null&&expanded)materials.refreshThumbnails();}
    void save(Bundle state){state.putString("saver-media-candidate",candidate);state.putBoolean("saver-library-open",expanded);if(materials!=null)materials.save(state);}
    void restore(Bundle state){if(state==null)return;candidate=state.getString("saver-media-candidate","");WallpaperCatalog catalog=media.snapshot();if(WallpaperCatalog.DEFAULT.equals(candidate)||catalog.find(candidate)==null||catalog.builtinHidden(candidate,true))candidate="";expanded=state.getBoolean("saver-library-open");if(materials!=null)materials.restore(state);refresh();}

    void destroy(){if(materials!=null)materials.destroy();}
}
