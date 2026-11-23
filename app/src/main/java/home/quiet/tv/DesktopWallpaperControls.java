package home.quiet.tv;

import android.os.Build;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.util.*;

/** Confirmed wallpaper choices apply immediately; focus previews remain temporary. */
final class DesktopWallpaperControls {
    interface Preview {void show(String id,boolean weather,boolean dayNight,boolean muted,String error);}
    private final AppManagementActivity ui;
    private final LinearLayout group;
    private final WallpaperStore store;
    private final DesktopMediaLibrary media;
    private final DailyWallpaperController daily;
    private final Preview preview;
    private final Runnable rewired;
    private final Runnable refreshDailyState=this::refreshDaily;
    private final Runnable dailyStateChanged=this::scheduleDailyRefresh;
    private final WeatherSettingsSheet weatherEditor;
    private DisplayAppearanceChoice modeChoice,intervalChoice,sourceChoice;
    private DesktopSettingsRow choose,dayNight,dailyDetails,movieInfo,city,source,sound,component;
    private boolean componentsListening;
    private final home.quiet.components.ComponentRepository.Listener componentsChanged=this::refreshComponent;
    private TextView rotationNote,lowNote,summary,summaryDetail;
    private LinearLayout weatherSummary;
    private LinearLayout customRow;
    private EditText minutes;
    private ManagementUi.Sheet details;
    private TextView dailyStatus,dailySource,dailyTitle,dailyMetadata,dailyAttribution;
    private LinearLayout dailyPicture;
    private android.widget.ImageView movieLogo;
    private Button dailyRefresh;
    private String mode="",candidate="",interval="",focusedMode="",error="";
    private boolean expanded,followDayNight=true,touched,custom,busy,destroyed;
    private int generation;
    DesktopWallpaperControls(AppManagementActivity ui,LinearLayout group,DesktopMediaImportPanel importer,WeatherSettingsSheet weatherEditor,Preview preview,Runnable rewired){
        this.ui=ui;this.group=group;this.weatherEditor=weatherEditor;this.preview=preview;this.rewired=rewired;store=WallpaperStore.get(ui);
        daily=new DailyWallpaperController(ui,store,ui.chinese(),()->{refresh();dailyStatus();scene();ui.refreshBackdrop();});
        media=new DesktopMediaLibrary(ui,"wallpaper",importer,new DesktopMediaLibrary.Actions(){public String selected(){return candidate;}public boolean select(String id){if(busy)return false;candidate=id;touched=true;error="";refresh();scene();return apply(true);}public void imported(String id,boolean apply)throws IOException{DesktopWallpaperControls.this.imported(id,apply);}public void removed(){reset();refresh();scene();ui.refreshBackdrop();choose.requestFocusFromTouch();}},rewired);
        reset();build();
    }
    private String tr(String zh,String en){return ui.tr(zh,en);}
    private void refreshDaily(){if(!destroyed)refresh();}
    private void scheduleDailyRefresh(){group.removeCallbacks(refreshDailyState);group.post(refreshDailyState);}
    private TextView note(String value){TextView text=ui.muted(value,11);QuickControlPreviewView.type(text,12.16f,19.456f,ui.unit,UiPalette.SECONDARY);if(Build.VERSION.SDK_INT>=28)text.setFallbackLineSpacing(false);text.setPadding(ui.px(10.56f),text.getPaddingTop()+ui.px(5.76f),ui.px(10.56f),text.getPaddingBottom()+ui.px(5.76f));return text;}
    private DesktopSettingsRow row(String title,String tag,Runnable action){DesktopSettingsRow item=new DesktopSettingsRow(ui,title,"",false);item.displayStyle();item.hideIcon();item.setTag(tag);item.setOnClickListener(v->{if(!busy)action.run();});group.addView(item,new LinearLayout.LayoutParams(-1,ui.px(42)));return item;}
    private String actualMode(WallpaperCatalog state){return state.weather&&WallpaperGpuSupport.available(ui)?"weather":WallpaperCatalog.DAILY.equals(state.current)?"daily":state.rotating?"rotation":"fixed";}
    private void reset(){WallpaperCatalog state=store.snapshot();mode=actualMode(state);candidate=state.current;followDayNight=state.weatherDayNight;interval="";custom=false;touched=false;error="";}
    private String staticFallback(WallpaperCatalog state,String id){WallpaperCatalog.Entry item=state.find(id);String next=item!=null&&item.video()?state.staticBeforeVideo:id;if(WallpaperCatalog.DAILY.equals(next))next=state.dailyFallback();return state.find(next)==null?WallpaperCatalog.DEFAULT:next;}
    private void mode(String value){
        if(busy)return;WallpaperCatalog state=store.snapshot();mode=value;focusedMode="";interval="";custom=false;touched=true;error="";
        if(value.equals("daily")){candidate=WallpaperCatalog.DAILY;expanded=false;}
        else if(value.equals("weather")){candidate=staticFallback(state,candidate);expanded=false;}
        else if(value.equals("fixed"))candidate=state.fixedCandidate(candidate);
        else if(WallpaperCatalog.DAILY.equals(candidate))candidate=staticFallback(state,candidate);
        if(value.equals("rotation")){WallpaperCatalog.Entry item=state.find(candidate);List<WallpaperCatalog.Entry> pool=item!=null&&item.video()?state.videos():state.pictures();if((WallpaperCatalog.DEFAULT.equals(candidate)||WallpaperCatalog.DAILY.equals(candidate))&&!pool.isEmpty())candidate=pool.get(0).id;}
        refresh();scene();
    }
    private void build(){
        boolean capable=WallpaperGpuSupport.available(ui);String[] values=capable?new String[]{"fixed","rotation","daily","weather"}:new String[]{"fixed","rotation","daily"};String[] names=capable?new String[]{tr("固定一张","Fixed wallpaper"),tr("自动轮换","Automatic rotation"),tr("每日更新","Daily update"),tr("随天气变化","Follow weather")}:new String[]{tr("固定一张","Fixed wallpaper"),tr("自动轮换","Automatic rotation"),tr("每日更新","Daily update")};
        modeChoice=new DisplayAppearanceChoice(ui,"wall-mode",tr("切换方式","Switching mode"),values,names,new DisplayAppearanceChoice.Access(){public String read(){return mode;}public void save(String value)throws IOException{if(busy){++generation;busy=false;daily.cancel();}if(value.equals("weather")&&!home.quiet.components.ComponentRepository.get(ui).isReady("weather")){OptionalComponentsUi.missing(ui);return;}mode(value);apply();}},false,new DisplayAppearanceChoice.Preview(){public void value(String key,Object value){focusedMode=String.valueOf(value);scene();}public void clear(String key){focusedMode="";scene();}},rewired).twoColumns().errors(this::error);group.addView(modeChoice);
        component=row(tr("动态天气壁纸","Dynamic weather wallpaper"),"wall-weather-component",()->ui.componentSettings("weather"));component.tail("arrow");componentsChanged.changed();
        choose=row(tr("选择壁纸","Choose wallpaper"),"wall-picker",()->{expanded=!expanded;if(expanded)media.render();refresh();scene();});choose.tail("arrow");
        weatherSummary=ui.row();weatherSummary.setGravity(Gravity.CENTER_VERTICAL);weatherSummary.setPadding(ui.px(13.44f),ui.px(10.56f),ui.px(13.44f),ui.px(10.56f));ImageView weatherIcon=new ImageView(ui);weatherIcon.setImageDrawable(new QuickControlIcon("cloud-sun"));LinearLayout.LayoutParams iconPosition=new LinearLayout.LayoutParams(ui.px(18),ui.px(18));iconPosition.rightMargin=ui.px(9.6f);weatherSummary.addView(weatherIcon,iconPosition);LinearLayout weatherCopy=ui.column();summary=ui.label("",14);QuickControlPreviewView.type(summary,19.2f,26.88f,ui.unit,UiPalette.TEXT);QuickControlPreviewView.weight(summary,500);weatherCopy.addView(summary);summaryDetail=ui.muted("",10);QuickControlPreviewView.type(summaryDetail,13.056f,20.8896f,ui.unit,UiPalette.SECONDARY);LinearLayout.LayoutParams detailPosition=new LinearLayout.LayoutParams(-1,-2);detailPosition.topMargin=ui.px(2.4f);weatherCopy.addView(summaryDetail,detailPosition);weatherSummary.addView(weatherCopy,new LinearLayout.LayoutParams(0,-2,1));group.addView(weatherSummary);
        dayNight=row(tr("随昼夜变化","Follow day and night"),"wall-day-night",()->{followDayNight=!followDayNight;touched=true;refresh();scene();apply();});
        city=row(tr("天气位置","Weather location"),"wall-weather-city",weatherEditor::showLocation);city.tail("arrow");source=row(tr("来源与服务","Sources and services"),"wall-weather-source",weatherEditor::showSources);source.tail("arrow");
        lowNote=note(tr("此设备不支持天气动态效果，保留静态背景","Weather effects are unavailable on this device. Static backgrounds remain available."));group.addView(lowNote);
        sourceChoice=new DisplayAppearanceChoice(ui,"wall-daily-source",tr("图片来源","Image source"),new String[]{WallpaperCatalog.BING,WallpaperCatalog.MICROSOFT,WallpaperCatalog.MOVIE},new String[]{tr("必应","Bing"),tr("微软壁纸","Microsoft wallpapers"),tr("每日电影","Daily movie")},new DisplayAppearanceChoice.Access(){public String read(){return DailyWallpaperLoader.source(store.snapshot());}public void save(String source)throws IOException{if(!store.dailyPending().isEmpty())throw new IOException(tr("正在获取壁纸，请稍候","Getting wallpaper. Please wait"));if(!source.equals(read()))switchSource(source);}},false,new DisplayAppearanceChoice.Preview(){public void value(String key,Object value){}public void clear(String key){}},rewired).solidSelection().twoColumns().errors(this::error);group.addView(sourceChoice);
        dailyDetails=row(tr("当前图片详情","Current image details"),"wall-daily-details",this::details);dailyDetails.tail("arrow");
        movieInfo=row(tr("显示影片信息","Show movie information"),"wall-movie-info",()->{try{DesktopSettingsStore settings=new DesktopSettingsStore(ui);settings.showMovieInfo(!settings.showMovieInfo());error="";refresh();scene();}catch(IOException failure){error(failure.getMessage());}});
        for(DesktopSettingsRow arrow:new DesktopSettingsRow[]{choose,city,source,dailyDetails})arrow.getLayoutParams().height=ui.px(44.72f);
        group.addView(media.library);
        intervalChoice=new DisplayAppearanceChoice(ui,"wall-interval",tr("轮换间隔","Rotation interval"),new String[]{"1","5","10","15","30","60","custom"},new String[]{"1 "+tr("分钟","min"),"5 "+tr("分钟","min"),"10 "+tr("分钟","min"),"15 "+tr("分钟","min"),"30 "+tr("分钟","min"),"60 "+tr("分钟","min"),tr("自定义","Custom")},new DisplayAppearanceChoice.Access(){public String read(){return custom||!preset(currentMinutes())?"custom":String.valueOf(currentMinutes());}public void save(String value)throws IOException{if(busy)throw new IOException(tr("正在应用，请稍候","Applying. Please wait."));custom=value.equals("custom");interval=custom?String.valueOf(currentMinutes()):value;touched=true;error="";refresh();scene();if(!custom)apply();}},false,new DisplayAppearanceChoice.Preview(){public void value(String key,Object value){}public void clear(String key){}},rewired).twoColumns().errors(this::error);group.addView(intervalChoice);
        // Keep the 42-unit content area; 4.6 below it brings the field's bottom inset to 9.6.
        customRow=ui.row();customRow.setGravity(Gravity.CENTER_VERTICAL);customRow.setPadding(ui.px(9.6f),0,ui.px(9.6f),ui.px(4.6f));TextView caption=ui.label(tr("自定义分钟","Custom minutes"),13);caption.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,12.48f*ui.unit);customRow.addView(caption,new LinearLayout.LayoutParams(0,-1,1));minutes=ui.input("","");minutes.setContentDescription(tr("壁纸轮换间隔","Wallpaper rotation interval"));minutes.setTag("wall-custom-interval");minutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);minutes.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});customRow.addView(minutes,new LinearLayout.LayoutParams(ui.px(64),ui.px(32)));TextView suffix=ui.muted(tr("分钟","min"),11);suffix.setPadding(ui.px(5),0,0,0);customRow.addView(suffix);group.addView(customRow,new LinearLayout.LayoutParams(-1,ui.px(46.6f)));minutes.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence text,int start,int before,int count){if(!minutes.hasFocus())return;interval=text.toString();custom=true;touched=true;error="";scene();}public void afterTextChanged(Editable e){}});
        minutes.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);minutes.setOnEditorActionListener((view,action,event)->{if(action!=android.view.inputmethod.EditorInfo.IME_ACTION_DONE)return false;apply();return true;});minutes.setOnKeyListener((view,key,event)->{if(key!=KeyEvent.KEYCODE_DPAD_CENTER&&key!=KeyEvent.KEYCODE_ENTER&&key!=KeyEvent.KEYCODE_NUMPAD_ENTER)return false;if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()){
            boolean accepted=!busy&&(!mode.equals("rotation")||WallpaperCatalog.rotationMinutes(currentMinutes()));
            apply();
            if(accepted)KeyFeedback.confirm(ui);
        }return true;});minutes.setOnFocusChangeListener((view,focused)->{if(!focused&&custom&&touched&&!busy&&mode.equals("rotation"))apply();});
        rotationNote=note("");group.addView(rotationNote);sound=row(tr("背景声音","Background sound"),"wall-sound",()->{try{store.sound(!store.snapshot().sound);refresh();scene();}catch(IOException error){error(error.getMessage());}});refresh();
    }
    private static boolean preset(int value){return value==1||value==5||value==10||value==15||value==30||value==60;}
    private boolean video(){WallpaperCatalog.Entry entry=store.snapshot().find(candidate);return entry!=null&&entry.video();}
    private int currentMinutes(){if(!interval.isEmpty())try{return Integer.parseInt(interval);}catch(NumberFormatException invalid){return 0;}WallpaperCatalog state=store.snapshot();return video()?state.videoRotationMinutes:state.imageRotationMinutes;}
    private boolean pending(){WallpaperCatalog state=store.snapshot();return !mode.equals(actualMode(state))||!candidate.equals(state.current)||mode.equals("weather")&&followDayNight!=state.weatherDayNight||mode.equals("rotation")&&currentMinutes()!=(video()?state.videoRotationMinutes:state.imageRotationMinutes);}
    void refresh(){
        if(destroyed||choose==null)return;if(!touched)reset();WallpaperCatalog state=store.snapshot();if(state.find(candidate)==null||state.builtinHidden(candidate,false))candidate=state.current;boolean picking=mode.equals("fixed")||mode.equals("rotation"),weather=mode.equals("weather"),rotating=mode.equals("rotation");
        movieInfo.setVisibility(mode.equals("daily")&&WallpaperCatalog.MOVIE.equals(DailyWallpaperLoader.source(state))?View.VISIBLE:View.GONE);movieInfo.checked(new DesktopSettingsStore(ui).showMovieInfo());
        choose.setVisibility(picking?View.VISIBLE:View.GONE);choose.tail(expanded?"settings-up":"arrow");WallpaperCatalog.Entry item=state.find(candidate);choose.value(item==null?tr("素材不可用","Unavailable"):item.label(ui.chinese()));dailyDetails.setVisibility(mode.equals("daily")?View.VISIBLE:View.GONE);sourceChoice.setVisibility(mode.equals("daily")?View.VISIBLE:View.GONE);dailyDetails.value("");sourceChoice.available(store.dailyPending().isEmpty());sourceChoice.refreshConfirmed();media.visible(picking&&expanded);
        for(View v:new View[]{weatherSummary,dayNight,city,source})v.setVisibility(weather?View.VISIBLE:View.GONE);dayNight.checked(followDayNight);WeatherStore weatherStore=new WeatherStore(ui);WeatherData data=weatherStore.cached(ui.chinese());city.value(weatherStore.automatic()?(data==null?tr("自动定位","Automatic location"):data.city):weatherStore.city());source.value(weatherStore.provider().equals("auto")?tr("自动切换","Automatic fallback"):WeatherClient.providerName(weatherStore.provider()));weatherSummary(data);lowNote.setVisibility(WallpaperGpuSupport.available(ui)?View.GONE:View.VISIBLE);
        intervalChoice.setVisibility(rotating?View.VISIBLE:View.GONE);customRow.setVisibility(rotating&&(custom||!preset(currentMinutes()))?View.VISIBLE:View.GONE);if(!minutes.hasFocus())minutes.setText(interval.isEmpty()?String.valueOf(currentMinutes()):interval);rotationNote.setText(video()?tr("至少需要 2 个视频才能轮换","At least 2 videos are needed for rotation"):tr("至少需要 2 张图片才能轮换","At least 2 images are needed for rotation"));rotationNote.setVisibility(rotating&&store.snapshot().count(video())<2?View.VISIBLE:View.GONE);
        sound.setVisibility(video()?View.VISIBLE:View.GONE);sound.available(!rotating&&!weather&&!busy);sound.checked(state.sound&&!rotating&&!weather);sound.displayDescription(rotating||weather?tr("当前方式保持静音","This mode stays muted"):"");modeChoice.refresh();intervalChoice.refresh();media.refreshSelection();dailyStatus();rewired.run();
    }
    private String cityTitle(){String value=new WeatherStore(ui).city();return value.isEmpty()?tr("当前城市","Current city"):value;}
    private void weatherSummary(WeatherData data){
        boolean usable=data!=null&&data.usable(System.currentTimeMillis());String key=WeatherWallpaperScene.key(data==null?"":data.description,data==null?"999":data.icon,false,12);WeatherIllumination light=WeatherIllumination.at(System.currentTimeMillis(),data==null?Double.NaN:data.latitude,data==null?Double.NaN:data.longitude,followDayNight);
        summary.setText((data==null?cityTitle():data.city)+"  "+WeatherWallpaperScene.name(key,ui.chinese()));android.net.ConnectivityManager manager=(android.net.ConnectivityManager)ui.getApplicationContext().getSystemService(android.content.Context.CONNECTIVITY_SERVICE);android.net.NetworkInfo network=manager==null?null:manager.getActiveNetworkInfo();String state=!usable?tr("暂用静态背景，天气可用后自动恢复","Using a static background until weather is available"):network==null||!network.isConnected()?tr("天气更新暂停，沿用上次天气","Updates paused; using the previous weather"):tr("跟随当前城市天气","Follows the current city's weather");String period=!followDayNight?tr("白天效果","Daytime effect"):light.period(ui.chinese());summaryDetail.setText(state+"  "+period);
    }
    private void scene(){String shownMode=focusedMode.isEmpty()?mode:focusedMode;if(shownMode.equals("weather")&&!home.quiet.components.ComponentRepository.get(ui).isReady("weather"))shownMode="fixed";String id=shownMode.equals("daily")?WallpaperCatalog.DAILY:shownMode.equals("weather")?staticFallback(store.snapshot(),candidate):candidate;boolean rotating=shownMode.equals("rotation");preview.show(id,shownMode.equals("weather"),followDayNight,!store.snapshot().sound||rotating||shownMode.equals("weather"),error);}
    void showPreview(){scene();}
    boolean drafting(){return touched&&pending()||modeChoice.drafting()||intervalChoice.drafting();}
    boolean busy(){return busy||!store.dailyPending().isEmpty();}
    String error(){return !error.isEmpty()?error:!modeChoice.error().isEmpty()?modeChoice.error():!intervalChoice.error().isEmpty()?intervalChoice.error():daily.error();}
    String loadingMessage(){String source=store.dailyPending();String label=source.isEmpty()?tr("壁纸","wallpaper"):WallpaperCatalog.sourceLabel(source,ui.chinese())+(WallpaperCatalog.MICROSOFT.equals(source)?"":tr("壁纸"," wallpaper"));return tr("正在获取"+label+"  完成后自动切换","Getting "+label+"  Switches automatically when ready");}
    void firstDrawReady(){media.firstDrawReady();}
    String openFromQuick(){media.quickEntry();expanded=mode.equals("fixed")||mode.equals("rotation");if(expanded)media.render();refresh();return expanded?"wall-picker":"appearance-wall-mode";}
    private void error(String message){error=message==null||"Unable to save wallpaper settings".equals(message)?tr("背景保存失败，原设置和预览已保留","Unable to apply background. Current settings and preview kept."):message;scene();}
    private void apply(){apply(false);}
    private boolean apply(boolean closeLibrary){
        if(busy)return false;
        if(mode.equals("rotation")&&!WallpaperCatalog.rotationMinutes(currentMinutes())){
            error(tr("请输入 1–1440 的整数分钟","Enter an integer from 1 to 1440 minutes"));
            return false;
        }
        if(mode.equals("daily")){
            switchSource(DailyWallpaperLoader.source(store.snapshot()));
            return false;
        }
        return commit(closeLibrary);
    }
    private void switchSource(String source){busy=true;int request=++generation;error="";scene();daily.apply(source,(success,message)->{if(destroyed||request!=generation)return;busy=false;if(success){reset();refresh();scene();ui.refreshBackdrop();ui.success(tr("每日壁纸已应用","Daily wallpaper applied"));}else{reset();refresh();error(message);}});}
    private boolean commit(boolean closeLibrary){
        try{
            View focused=ui.getCurrentFocus();
            store.background(mode,candidate,currentMinutes(),followDayNight);
            prepareSelectedImage();
            ui.refreshBackdrop();
            reset();
            if(closeLibrary)expanded=false;
            refresh();
            scene();
            ui.success(tr("壁纸设置已保存","Wallpaper settings saved"));
            if(closeLibrary)choose.requestFocusFromTouch();
            else if(focused!=null&&!focused.isShown()){
                if(focused==minutes)intervalChoice.focusTrigger();else modeChoice.focusTrigger();
            }
            return true;
        }catch(IOException failure){
            error(failure.getMessage());
            return false;
        }
    }
    private void prepareSelectedImage(){
        WallpaperCatalog.Entry entry=store.entry(candidate);if(!mode.equals("fixed")||entry==null||entry.video()||WallpaperCatalog.DEFAULT.equals(candidate))return;
        View art=media.library.findViewWithTag("wall-media-art-"+candidate);if(!(art instanceof ImageView))return;
        android.graphics.drawable.Drawable drawable=((ImageView)art).getDrawable();if(!(drawable instanceof android.graphics.drawable.BitmapDrawable))return;
        android.util.DisplayMetrics screen=ui.getResources().getDisplayMetrics();
        store.prepareBackdrop(WallpaperBackdropCache.key(store,candidate,screen.widthPixels,screen.heightPixels),((android.graphics.drawable.BitmapDrawable)drawable).getBitmap(),null,false);
    }
    void imported(String id,boolean apply)throws IOException{
        WallpaperCatalog.Entry entry=store.snapshot().find(id);
        if(entry==null)throw new IOException(tr("素材不可用","Media unavailable"));
        if(apply){
            store.background("fixed",id,0,followDayNight);
            ui.refreshBackdrop();
            reset();
            expanded=false;
            refresh();
            scene();
            choose.post(choose::requestFocusFromTouch);
            return;
        }
        expanded=true;
        media.chooseType(id);
        media.renderAll();
        refresh();
        scene();
        media.focus(id);
    }
    private void details(){
        if(details!=null)return;details=ui.new Sheet(tr("当前图片详情","Current image details"));ManagementUi.Sheet current=details;
        Button close=details.settingsLayout(tr("关闭","Close"),details.dialog::dismiss);details.contentSize(792,480);details.box.setPadding(ui.px(32),ui.px(18),ui.px(32),ui.px(18));View dim=((ViewGroup)details.box.getParent()).getChildAt(0);dim.setBackgroundColor(0xb3000000);dim.setFocusable(false);
        LinearLayout header=(LinearLayout)details.heading.getParent();header.setGravity(Gravity.CENTER_VERTICAL);header.getLayoutParams().height=ui.px(32);details.box.getChildAt(1).getLayoutParams().height=ui.px(8);
        details.heading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,24*ui.unit);details.heading.getLayoutParams().height=-1;
        close.getLayoutParams().width=ui.px(56);close.getLayoutParams().height=ui.px(30);close.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,14*ui.unit);
        LinearLayout.LayoutParams scrollSize=(LinearLayout.LayoutParams)details.scroll.getLayoutParams();scrollSize.setMargins(0,0,0,0);details.scroll.setPadding(0,0,0,0);
        LinearLayout sourceRow=ui.row();sourceRow.setGravity(Gravity.CENTER_VERTICAL);details.body.addView(sourceRow,new LinearLayout.LayoutParams(-1,ui.px(20)));
        movieLogo=new ImageView(ui);movieLogo.setImageDrawable(new TmdbLogoDrawable());movieLogo.setContentDescription("TMDB");sourceRow.addView(movieLogo,new LinearLayout.LayoutParams(ui.px(77),ui.px(17)));
        dailySource=ui.muted("",14);sourceRow.addView(dailySource,new LinearLayout.LayoutParams(-2,-1));
        dailyTitle=ui.label("",26);dailyTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));dailyTitle.setMinHeight(ui.px(34));detailContent(dailyTitle,10);
        dailyMetadata=ui.muted("",16);dailyMetadata.setMinHeight(ui.px(22));detailContent(dailyMetadata,6);
        dailyPicture=ui.column();LinearLayout.LayoutParams overviewSize=new LinearLayout.LayoutParams(-1,-2);overviewSize.topMargin=ui.px(12);details.body.addView(dailyPicture,overviewSize);
        dailyAttribution=ui.muted("This product uses the TMDB API but is not endorsed or certified by TMDB.",12);dailyAttribution.setGravity(Gravity.START);detailContent(dailyAttribution,12);
        details.actions.getLayoutParams().height=ui.px(42);((LinearLayout.LayoutParams)details.actions.getLayoutParams()).topMargin=ui.px(12);
        dailyStatus=ui.muted("",14);dailyStatus.setMaxLines(2);dailyStatus.setEllipsize(TextUtils.TruncateAt.END);details.actions.addView(dailyStatus,new LinearLayout.LayoutParams(0,-1,1));
        dailyRefresh=details.action(tr("刷新壁纸","Refresh wallpaper"),()->{dailyRefresh.setEnabled(false);daily.refresh((ok,message)->{if(details==current){dailyStatus();dailyStatus.setText(message);}});});
        dailyRefresh.setMinWidth(ui.px(114));dailyRefresh.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,16*ui.unit);dailyRefresh.setPadding(ui.px(16),0,ui.px(16),0);
        details.scroll.setId(androidx.core.view.ViewCompat.generateViewId());dailyRefresh.setId(androidx.core.view.ViewCompat.generateViewId());
        close.setNextFocusDownId(details.scroll.getId());details.scroll.setNextFocusUpId(close.getId());details.scroll.setNextFocusDownId(dailyRefresh.getId());dailyRefresh.setNextFocusUpId(details.scroll.getId());
        details.dialog.setOnDismissListener(d->{if(details==current){details=null;dailyStatus=null;dailySource=null;dailyTitle=null;dailyMetadata=null;dailyPicture=null;dailyAttribution=null;dailyRefresh=null;movieLogo=null;if(dailyDetails.isShown())dailyDetails.post(dailyDetails::requestFocusFromTouch);}});dailyStatus();details.show(dailyRefresh);
    }
    private void detailContent(TextView text,int top){LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.topMargin=ui.px(top);details.body.addView(text,size);}
    private String detailOverview(String value){
        if(value.length()<240||value.contains("\n"))return value;
        StringBuilder formatted=new StringBuilder();int length=0,paragraphs=0,target=(value.length()+2)/3;
        for(String sentence:value.split("(?<=[。！？])")){if(length>=target&&paragraphs<2){formatted.append("\n\n");length=0;paragraphs++;}formatted.append(sentence);length+=sentence.length();}
        return formatted.toString();
    }
    private void dailyStatus(){
        if(details==null||dailyStatus==null)return;WallpaperCatalog state=store.snapshot();WallpaperCatalog.DailyImage image=state.dailyImages.get(state.dailySource);MovieWallpaperFeed movie=image==null?null:image.movie;
        dailyStatus.setText(daily.status(state.dailySource,false));movieLogo.setVisibility(movie==null?View.GONE:View.VISIBLE);dailySource.setText(WallpaperCatalog.sourceLabel(state.dailySource,ui.chinese()));
        LinearLayout.LayoutParams sourceSize=(LinearLayout.LayoutParams)dailySource.getLayoutParams();sourceSize.leftMargin=movie==null?0:ui.px(10);dailySource.setLayoutParams(sourceSize);
        dailyTitle.setText(image==null?"":image.title);dailyTitle.setVisibility(image==null?View.GONE:View.VISIBLE);
        if(movie!=null){String score=String.format(Locale.US,"%.1f",movie.rating);SpannableString metadata=new SpannableString(movie.year+"  TMDB "+score+"  "+movie.votes+tr(" 人评分"," ratings"));int start=metadata.toString().indexOf(score);metadata.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),start,start+score.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);metadata.setSpan(new android.text.style.ForegroundColorSpan(UiPalette.TEXT),start,start+score.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);dailyMetadata.setText(metadata);}else dailyMetadata.setText(image==null?"":image.copyright);
        dailyMetadata.setVisibility(dailyMetadata.length()==0?View.GONE:View.VISIBLE);dailyPicture.removeAllViews();if(movie!=null)for(String paragraph:detailOverview(movie.overview).split("\n\n")){TextView text=ui.label(paragraph,14);text.setGravity(Gravity.TOP|Gravity.START);text.setLineSpacing(ui.px(4),1);if(Build.VERSION.SDK_INT>=28)text.setFallbackLineSpacing(false);LinearLayout.LayoutParams spacing=new LinearLayout.LayoutParams(-1,-2);spacing.topMargin=dailyPicture.getChildCount()==0?0:ui.px(8);dailyPicture.addView(text,spacing);}dailyPicture.setVisibility(movie==null?View.GONE:View.VISIBLE);dailyAttribution.setVisibility(movie==null?View.GONE:View.VISIBLE);dailyRefresh.setEnabled(store.dailyPending().isEmpty());
    }
    boolean contains(View view){while(view!=null){if(view==group)return true;view=view.getParent() instanceof View?(View)view.getParent():null;}return false;}
    boolean back(){if(modeChoice.back()||intervalChoice.back()||sourceChoice.back())return true;if(busy){++generation;busy=false;daily.cancel();}if(expanded){expanded=false;refresh();choose.requestFocusFromTouch();return true;}if(touched&&pending()){reset();refresh();scene();return true;}return false;}
    void cancel(){++generation;busy=false;daily.cancel();expanded=false;reset();refresh();scene();}
    void refreshNames(){media.refreshNames();WallpaperCatalog.Entry entry=store.snapshot().find(candidate);if(choose!=null&&entry!=null)choose.value(entry.label(ui.chinese()));}
    private void refreshComponent(){if(destroyed||component==null)return;org.json.JSONObject status=home.quiet.components.ComponentRepository.get(ui).state("weather");component.value(OptionalComponentsUi.label(status));if((status.optString("state").equals("removing")||status.optString("state").equals("remove_failed"))&&mode.equals("weather")){reset();if(mode.equals("weather"))mode="fixed";refresh();scene();}}
    void focusComponent(){component.requestFocusFromTouch();}
    void resume(){if(!componentsListening){componentsListening=true;home.quiet.components.ComponentRepository.get(ui).add(componentsChanged);}store.observeDaily(dailyStateChanged);daily.resume();media.resume();refresh();}
    void pause(){media.pause();store.forgetDaily(dailyStateChanged);group.removeCallbacks(refreshDailyState);if(componentsListening){componentsListening=false;home.quiet.components.ComponentRepository.get(ui).remove(componentsChanged);}modeChoice.cancel(false);intervalChoice.cancel(false);sourceChoice.cancel(false);focusedMode="";++generation;busy=false;daily.pause();}
    void save(Bundle state){state.putString("wall-mode",mode);state.putString("wall-candidate",candidate);state.putString("wall-interval",interval);state.putBoolean("wall-custom",custom);state.putBoolean("wall-touched",touched);state.putBoolean("wall-library",expanded);state.putBoolean("wall-day-night",followDayNight);state.putString("wall-error",error);state.putBoolean("wall-daily-details",details!=null);media.save(state);}
    void restore(Bundle state){if(state==null)return;mode=state.getString("wall-mode",mode);candidate=state.getString("wall-candidate",candidate);interval=state.getString("wall-interval","");custom=state.getBoolean("wall-custom");touched=state.getBoolean("wall-touched");expanded=state.getBoolean("wall-library");followDayNight=state.getBoolean("wall-day-night",true);error=state.getString("wall-error","");media.restore(state);refresh();if(state.getBoolean("wall-daily-details"))details();}
    void destroy(){store.forgetDaily(dailyStateChanged);group.removeCallbacks(refreshDailyState);if(componentsListening){componentsListening=false;home.quiet.components.ComponentRepository.get(ui).remove(componentsChanged);}destroyed=true;++generation;if(details!=null){details.dialog.setOnDismissListener(null);details.dialog.dismiss();details=null;}daily.destroy();media.destroy();}
}
