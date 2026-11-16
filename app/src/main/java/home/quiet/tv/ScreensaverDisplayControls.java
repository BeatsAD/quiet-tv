package home.quiet.tv;

import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Screensaver choices and its shared-catalog draft remain inside the desktop settings column. */
final class ScreensaverDisplayControls {
    interface Preview {void show(ScreensaverSettings settings,String candidate,boolean pending);}
    private final ManagementUi ui;
    private final LinearLayout group;
    private final ScreensaverStore store;
    private final WallpaperStore media;
    private final Preview preview;
    private final Runnable rewired;
    private final DesktopMediaImportPanel importer;
    private ScreensaverSettings settings;
    private DisplayAppearanceChoice waiting,content;
    private DesktopSettingsRow choose,time,weather;
    private LinearLayout library;
    private DesktopMediaLibrary materials;
    private String candidate="",draftMode="",error="";
    private boolean expanded;
    ScreensaverDisplayControls(ManagementUi ui,LinearLayout group,DesktopMediaImportPanel importer,Preview preview,Runnable rewired){this.ui=ui;this.group=group;this.importer=importer;this.preview=preview;this.rewired=rewired;store=ScreensaverStore.get(ui);media=WallpaperStore.get(ui);read();}
    private int p(float value){return ui.px(value*.75f);}
    private String tr(String zh,String en){return ui.tr(zh,en);}
    private TextView label(String text,float size){TextView view=ui.label(text,12);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,p(size));view.setTextColor(UiPalette.SECONDARY);return view;}
    private void read(){try{settings=store.snapshot(media.snapshot());error="";build();}catch(IOException error){this.error=tr("屏保设置无法读取，原配置已保留","Unable to read screensaver settings");group.removeAllViews();TextView message=label(tr("屏保设置无法读取，原配置已保留","Unable to read screensaver settings"),15.36f);group.addView(message);group.addView(ui.button(tr("重新读取","Read again"),this::read));}}
    private void build(){
        group.removeAllViews();String[] minutes={"0","1","2","5","10","15","30"},waitNames={tr("关闭","Off"),"1 "+tr("分钟","min"),"2 "+tr("分钟","min"),"5 "+tr("分钟","min"),"10 "+tr("分钟","min"),"15 "+tr("分钟","min"),"30 "+tr("分钟","min")};
        DisplayAppearanceChoice.Preview bridge=new DisplayAppearanceChoice.Preview(){public void value(String key,Object value){if(key.equals("saver-content"))draftMode=String.valueOf(value);scene();}public void clear(String key){draftMode="";scene();}};
        waiting=new DisplayAppearanceChoice(ui,"saver-wait",tr("闲置等待时间","Idle timeout"),minutes,waitNames,new DisplayAppearanceChoice.Access(){public String read(){return String.valueOf(settings.idleMinutes);}public void save(String value)throws IOException{saveSettings(settings.waitMinutes(Integer.parseInt(value)));}},false,bridge,rewired).twoColumns();group.addView(waiting);
        content=new DisplayAppearanceChoice(ui,"saver-content",tr("屏保内容","Screensaver content"),new String[]{ScreensaverSettings.FOLLOW,ScreensaverSettings.CLOCK,ScreensaverSettings.CUSTOM},new String[]{tr("跟随桌面","Follow desktop"),tr("时钟","Clock"),tr("自定义","Custom")},new DisplayAppearanceChoice.Access(){public String read(){return settings.mode;}public void save(String value)throws IOException{ScreensaverSettings next=settings.content(value);writeSettings(next);candidate="";draftMode="";expanded=false;renderLibrary();refresh();scene();}},false,bridge,rewired).twoColumns();group.addView(content);
        choose=row(tr("选择屏保素材","Screensaver media"),"saver-media");choose.tail("arrow");choose.setOnClickListener(v->{expanded=!expanded;renderLibrary();refresh();scene();});group.addView(choose,new LinearLayout.LayoutParams(-1,ui.px(44.72f)));
        materials=new DesktopMediaLibrary(ui,"screensaver",importer,new DesktopMediaLibrary.Actions(){public String selected(){return ScreensaverDisplayControls.this.selected();}public void select(String id){candidate=id;refresh();scene();}public void imported(String id,boolean apply)throws IOException{ScreensaverDisplayControls.this.imported(id,apply);}public void removed(){candidate="";settings=store.current();refresh();scene();choose.requestFocusFromTouch();}},rewired);library=materials.library;group.addView(library);LinearLayout indicators=ui.row();indicators.setClipToPadding(false);indicators.setPadding(ui.px(9.6f),ui.px(3.2f),ui.px(9.6f),ui.px(9.6f));group.addView(indicators);time=row(tr("显示时钟","Show clock"),"saver-clock");time.compactTile();time.setOnClickListener(v->{try{saveSettings(settings.time(!settings.showTime));}catch(IOException error){ui.feedback(error.getMessage());}});indicators.addView(time,new LinearLayout.LayoutParams(0,ui.px(42),1));
        weather=row(tr("显示天气","Show weather"),"saver-weather");weather.compactTile();weather.setOnClickListener(v->{try{saveSettings(settings.weather(!settings.showWeather));}catch(IOException error){ui.feedback(error.getMessage());}});LinearLayout.LayoutParams weatherPosition=new LinearLayout.LayoutParams(0,ui.px(42),1);weatherPosition.leftMargin=ui.px(6);indicators.addView(weather,weatherPosition);
        refresh();
    }
    private DesktopSettingsRow row(String name,String tag){DesktopSettingsRow row=new DesktopSettingsRow(ui,name,"",false);row.displayStyle();row.hideIcon();row.setTag(tag);row.setId(androidx.core.view.ViewCompat.generateViewId());return row;}
    private void writeSettings(ScreensaverSettings next)throws IOException{try{store.save(next);settings=next;error="";}catch(IOException failure){error=failure.getMessage();scene();throw failure;}}
    private void saveSettings(ScreensaverSettings next)throws IOException{writeSettings(next);draftMode="";refresh();scene();}
    void refresh(){
        if(settings==null||choose==null)return;ScreensaverSettings current=store.current();if(current!=null)settings=current;
        boolean custom=ScreensaverSettings.CUSTOM.equals(settings.mode);choose.setVisibility(custom?View.VISIBLE:View.GONE);choose.tail(expanded?"settings-up":"arrow");library.setVisibility(custom&&expanded?View.VISIBLE:View.GONE);time.setVisibility(ScreensaverSettings.CLOCK.equals(settings.mode)?View.GONE:View.VISIBLE);time.checked(settings.showTime);weather.checked(settings.showWeather);
        WallpaperCatalog.Entry entry=media.snapshot().find(candidate.isEmpty()?settings.mediaId:candidate);choose.value(entry==null?tr("素材不可用","Unavailable"):entry.label(ui.chinese()));waiting.refresh();content.refresh();rewired.run();
    }
    private void scene(){if(settings!=null)preview.show(draftMode.isEmpty()?settings:settings.content(draftMode),candidate,!candidate.isEmpty()&&!candidate.equals(settings.mediaId));}
    void showPreview(){scene();}
    boolean drafting(){if(settings==null||content==null)return false;return !candidate.isEmpty()&&!candidate.equals(settings.mediaId)||content.drafting();}
    String error(){return !error.isEmpty()?error:content==null?"":!content.error().isEmpty()?content.error():waiting.error();}
    boolean contains(View view){while(view!=null){if(view==group)return true;view=view.getParent() instanceof View?(View)view.getParent():null;}return false;}
    void apply(){if(candidate.isEmpty())return;try{saveSettings(settings.media(candidate));candidate="";refresh();scene();View card=library.findViewWithTag("saver-media-"+settings.mediaId);View target=card==null?choose:card;target.post(target::requestFocusFromTouch);ui.feedback(tr("屏保素材已应用","Screensaver media applied"));}catch(IOException error){ui.feedback(error.getMessage());}}
    void imported(String id,boolean apply)throws IOException{WallpaperCatalog.Entry entry=media.snapshot().find(id);if(entry==null)throw new IOException("素材不可用");if(apply){writeSettings(settings.media(id));candidate="";}else candidate=id;expanded=true;materials.chooseType(id);renderLibrary();refresh();scene();materials.focus(id);}
    boolean back(){if(waiting!=null&&waiting.back()||content!=null&&content.back())return true;if(expanded){expanded=false;renderLibrary();refresh();choose.requestFocusFromTouch();return true;}if(!candidate.isEmpty()){candidate="";refresh();scene();return true;}return false;}
    void pause(){if(waiting!=null)waiting.cancel(false);if(content!=null)content.cancel(false);draftMode="";}
    private String selected(){return candidate.isEmpty()?settings.mediaId:candidate;}
    private void renderLibrary(){if(materials!=null&&expanded)materials.render();}
    private void refreshSelection(){if(materials!=null)materials.refreshSelection();}
    void refreshNames(){if(materials!=null)materials.refreshNames();WallpaperCatalog.Entry entry=media.snapshot().find(selected());if(choose!=null&&entry!=null)choose.value(entry.label(ui.chinese()));}
    void refreshLibrary(){if(materials!=null&&expanded)materials.refreshThumbnails();}
    void save(Bundle state){state.putString("saver-media-candidate",candidate);state.putBoolean("saver-library-open",expanded);if(materials!=null)materials.save(state);}
    void restore(Bundle state){if(state==null)return;candidate=state.getString("saver-media-candidate","");if(media.snapshot().find(candidate)==null)candidate="";expanded=state.getBoolean("saver-library-open");if(materials!=null)materials.restore(state);refresh();}

    void destroy(){if(materials!=null)materials.destroy();}
}
