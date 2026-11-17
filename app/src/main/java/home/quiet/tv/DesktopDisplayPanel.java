package home.quiet.tv;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Desktop controls share prototype geometry while HomeHeaderView keeps the original layout. */
final class DesktopDisplayPanel extends ManagementPanel implements SharedPreferences.OnSharedPreferenceChangeListener {
    private boolean chinese,fixedGreeting;
    private DesktopSettingsStore desktopStore;
    private WeatherStore weatherStore;
    private GreetingEditorPanel greetingEditor;
    private HomeButtonEditor homeButtons;
    private ScreensaverDisplayControls saverControls;
    private DesktopWallpaperControls wallControls;
    private WeatherSettingsSheet weatherEditor;
    private boolean wallScene;
    private DesktopMediaImportPanel mediaImport;
    private ScreensaverPreviewView saverPreview;
    private FrameLayout previewHost;
    private boolean previewReady;
    private TextView saverError;
    private boolean saverScene,mediaResumed;
    private DesktopSettingsRow homeButtonRow,addDockRow;
    private final ArrayList<DisplayAppearanceChoice> appearanceChoices=new ArrayList<>();
    private DisplayAppearanceChoice clockStyle;
    private final Set<View> wiredFocus=Collections.newSetFromMap(new WeakHashMap<View,Boolean>());
    private final ViewTreeObserver.OnGlobalLayoutListener desktopGeometry=this::wireDesktop;
    private HomePreviewView preview;
    private TextView previewTitle;
    private PreviewFeedbackView previewFeedback;
    private String previewFocus="",saveError="";
    private boolean previewSecond;
    private LinearLayout desktop;
    private ScrollView desktopScroll;
    private DesktopSettingsRow greeting,legacyHomeKey,keySounds;
    private Button classic,gallery;
    private SeekBar homeBlur,secondBlur,homeBrightness,secondBrightness,homeFade,secondFade,dockScale,dockMagnification;
    private String focusTag;
    private final Map<SeekBar,WallpaperAdjustment> adjustments=new LinkedHashMap<>();
    private WallpaperAdjustment activeAdjustment;
    private boolean cancellingBack;
    private int scrollY;
    private final Map<String,DesktopSettingsRow> choices=new LinkedHashMap<>();
    private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable changed=this::refreshControls;
    private final Runnable mediaNamesChanged=()->{wallControls.refreshNames();saverControls.refreshNames();};
    DesktopDisplayPanel(AppManagementActivity activity){super(activity);}
    private String tr(String zh,String en){return activity.tr(zh,en);}
    @Override public void onCreate(Bundle state){
        chinese=activity.chinese();fixedGreeting=activity.fixedGreeting();desktopStore=new DesktopSettingsStore(this);weatherStore=new WeatherStore(this);LegacyHomeKeyService.prepare(this);
        pageRoot=column();pageRoot.setClipChildren(false);pageRoot.setClipToPadding(false);activity.attachPanel(pageRoot);mediaImport=new DesktopMediaImportPanel(activity);weatherEditor=new WeatherSettingsSheet(activity,this::refreshControls);makeDesktop();
        if(state!=null){focusTag=state.getString("focus");scrollY=state.getInt("scroll");greetingEditor.restoreState(state);homeButtons.restore(state);saverControls.restore(state);wallControls.restore(state);weatherEditor.restore(state);mediaImport.restore(state,(target,id,apply)->{if(target.equals("screensaver"))saverControls.imported(id,apply);else wallControls.imported(id,apply);});}
        else if(activity.getIntent().getBooleanExtra("desktop-background-entry",false))focusTag=wallControls.openFromQuick();
        else if(activity.getIntent().getBooleanExtra("desktop-clock-entry",false))focusTag="display-time";
    }
    private DesktopSettingsRow setting(String name,String icon,String tag){return activity.setting(name,icon,tag);}
    private TextView desktopLabel(String text){return activity.section(text);}
    private LinearLayout group(LinearLayout controls,String title){if(controls.getChildCount()>0)gap(controls,12);controls.addView(desktopLabel(title));LinearLayout group=activity.group();controls.addView(group,new LinearLayout.LayoutParams(-1,-2));return group;}
    private void makeDesktop(){
        desktop=row();desktop.setGravity(Gravity.TOP);desktop.setClipChildren(false);desktop.setClipToPadding(false);pageRoot.addView(desktop,new LinearLayout.LayoutParams(-1,-1));
        desktopScroll=activity.settingsScroll();desktopScroll.setFocusable(false);desktopScroll.setVerticalScrollBarEnabled(false);desktopScroll.setClipToPadding(false);desktopScroll.setClipChildren(false);desktopScroll.setPadding(px(12.36f),px(11.88f),px(12.36f),px(14.76f));LinearLayout.LayoutParams scrollPosition=new LinearLayout.LayoutParams(px(286),-1);scrollPosition.setMargins(px(-9),px(-9),px(-9),px(-9));desktop.addView(desktopScroll,scrollPosition);
        LinearLayout controls=column();controls.setClipChildren(false);controls.setClipToPadding(false);desktopScroll.addView(controls,new ScrollView.LayoutParams(-1,-2));
        LinearLayout wallpaper=group(controls,tr("桌面背景","Desktop background"));wallControls=new DesktopWallpaperControls(activity,wallpaper,mediaImport,weatherEditor,this::showWallpaperPreview,this::wireDesktop);
        LinearLayout saver=group(controls,tr("闲置屏保","Idle screensaver"));saverControls=new ScreensaverDisplayControls(activity,saver,mediaImport,this::showSaverPreview,this::wireDesktop);
        LinearLayout homeBackground=group(controls,tr("首页背景","Home background"));DisplayAppearanceChoice blurMode=new DisplayAppearanceChoice(activity,"home-blur-mode",tr("首页模糊方式","Home blur mode"),new String[]{"uniform","progressive"},new String[]{tr("均匀模糊","Uniform blur"),tr("渐进模糊","Progressive blur")},new DisplayAppearanceChoice.Access(){public String read(){return WallpaperGpuSupport.available(activity)?desktopStore.homeBlurMode():"uniform";}public void save(String value)throws java.io.IOException{desktopStore.homeBlurMode(value);}},false,new DisplayAppearanceChoice.Preview(){public void value(String key,Object value){if(preview!=null)preview.previewAppearance(key,value);}public void clear(String key){if(preview!=null)preview.clearAppearancePreview(key);}},this::wireDesktop).twoColumns();appearanceChoices.add(blurMode);homeBackground.addView(blurMode);blurMode.available(WallpaperGpuSupport.available(activity));if(!WallpaperGpuSupport.available(activity)){TextView unsupported=muted(tr("此设备不支持渐进模糊，保留均匀模糊","Progressive blur is unavailable; uniform blur remains available"),11);unsupported.setPadding(px(9.6f),px(5.76f),px(9.6f),px(5.76f));homeBackground.addView(unsupported);}homeBlur=wallpaperControl(homeBackground,false,"blur");homeBrightness=wallpaperControl(homeBackground,false,"brightness");homeFade=wallpaperControl(homeBackground,false,"fade");
        LinearLayout secondBackground=group(controls,tr("第二屏背景","Second-screen background"));secondBlur=wallpaperControl(secondBackground,true,"blur");secondBrightness=wallpaperControl(secondBackground,true,"brightness");secondFade=wallpaperControl(secondBackground,true,"fade");choice(secondBackground,"all-apps-names",tr("第二屏应用名称","Second-screen app names"));
        LinearLayout shortcuts=group(controls,tr("首页入口","Home entries"));homeButtonRow=setting(tr("首页按钮","Home buttons"),"","home-button-config");homeButtonRow.displayStyle();homeButtonRow.tail("arrow");activity.addSetting(shortcuts,homeButtonRow);
        homeButtons=new HomeButtonEditor(activity,this::refreshControls,()->homeButtonRow.requestFocusFromTouch());
        homeButtonRow.setOnClickListener(v->homeButtons.show(null));appearanceChoice(shortcuts,"quick-control-visibility",tr("快捷控制按钮","Quick control button"),DesktopSettingsStore.CONTROL_VISIBILITY,chinese?new String[]{"始终显示","聚焦时显示"}:new String[]{"Always visible","On focus"});
        LinearLayout first=group(controls,tr("首页信息","Home information"));pairSettings(first,choice(first,"greeting",tr("问候语","Greeting")),choice(first,"time",tr("时间","Time")));pairSettings(first,choice(first,"date",tr("日期","Date")),choice(first,"weather",tr("天气","Weather")));
        clockStyle=appearanceChoice(first,"clock-style",tr("时钟样式","Clock style"),DesktopSettingsStore.CLOCK_STYLES,chinese?new String[]{"数字","白色表盘","深色表盘"}:new String[]{"Digital","Light dial","Dark dial"});
        pairSettings(first,choice(first,"seconds",tr("显示秒数","Show seconds")),choice(first,"lunar-date",tr("显示农历","Show lunar date")));
        greeting=setting(tr("问候语与头像","Greeting and avatar"),"desktop","greeting");greeting.displayStyle();greeting.tail("arrow");
        greetingEditor=new GreetingEditorPanel(activity,desktopStore,fixedGreeting?tr("欢迎回家","Welcome home"):"",this::refreshControls);greeting.setOnClickListener(v->greetingEditor.show());activity.addSetting(first,greeting);
        LinearLayout layouts=group(controls,tr("排版","Layout"));LinearLayout layoutOptions=row();layoutOptions.setPadding(px(9.6f),px(9.6f),px(9.6f),px(9.6f));layouts.addView(layoutOptions);classic=layoutChoice(layoutOptions,tr("经典布局","Classic"),"classic");gallery=layoutChoice(layoutOptions,tr("艺术画廊","Gallery"),"gallery");
        LinearLayout icons=group(controls,tr("图标样式","Icon style"));appearanceChoice(icons,"icon-size",tr("图标大小","Icon size"),new String[]{"small","medium","large"},new String[]{"小","中","大"});appearanceChoice(icons,"icon-shape",tr("图标外形","Icon shape"),new String[]{"rectangle","square","circle"},new String[]{"长方形","方形","圆形"});
        LinearLayout dock=group(controls,tr("Dock 外观","Dock appearance"));dockScale=appearanceControl(dock,"dock-scale",tr("Dock 栏大小","Dock size"),70,130,5);appearanceChoice(dock,"dock-limit",tr("显示应用上限","App limit"),new String[]{"5","7","10","12"},new String[]{"5","7","10","12"});DesktopSettingsRow add=setting(tr("添加 Dock 应用","Add Dock app"),"","dock-add");addDockRow=add;add.displayStyle();add.tail("arrow");add.setOnClickListener(v->activity.startActivity(new Intent(activity,HomeAppActionsActivity.class).putExtra("add",true).putExtra("dock",true)));activity.addSetting(dock,add);choice(dock,"dock-magnify",tr("聚焦放大","Focus magnification"));dockMagnification=appearanceControl(dock,"dock-magnification",tr("放大幅度","Magnification"),100,160,5);choice(dock,"global-glass",tr("全局玻璃效果","Global glass effect"));choice(dock,"dock-background",tr("Dock 背景","Dock background"));
        LinearLayout homeApps=group(controls,tr("应用显示","App display"));choice(homeApps,"home-apps",tr("首页应用区域","Home app area"));appearanceChoice(homeApps,"app-title-position",tr("首页应用名称","Home app names"),new String[]{"hidden","below","top-left"},chinese?new String[]{"不显示","下方","左上角"}:new String[]{"Hidden","Below","Top left"});
        LinearLayout feedback=group(controls,tr("操作反馈","Interaction feedback"));
        keySounds=setting(tr("按键音","Key sounds"),"sound","key-sounds");keySounds.displayStyle();
        keySounds.setOnClickListener(v->{desktopStore.keySounds(!desktopStore.keySounds());refreshControls();});
        keySounds.checked(desktopStore.keySounds());activity.addSetting(feedback,keySounds);
        DisplayAppearanceChoice soundTheme=new DisplayAppearanceChoice(activity,"key-sound-theme",tr("按键音色","Key sound style"),KeySoundTheme.ids(),chinese?KeySoundTheme.titles(true):new String[]{"Wood","Digital","Keys"},new DisplayAppearanceChoice.Access(){public String read(){return desktopStore.keySoundTheme().id;}public void save(String value)throws java.io.IOException{desktopStore.keySoundTheme(value);KeySounds.play(activity,KeySoundPolicy.CONFIRM);}},true,new DisplayAppearanceChoice.Preview(){public void value(String key,Object value){}public void clear(String key){}},this::wireDesktop).solidSelection();appearanceChoices.add(soundTheme);feedback.addView(soundTheme,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout right=new LinearLayout(activity){
            @Override protected void onMeasure(int widthSpec,int heightSpec){
                super.onMeasure(widthSpec,heightSpec);if(previewHost==null||previewFeedback==null)return;
                int other=getPaddingTop()+getPaddingBottom();
                for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child==previewHost||child.getVisibility()==View.GONE)continue;LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)child.getLayoutParams();other+=child.getMeasuredHeight()+p.topMargin+p.bottomMargin;}
                int available=Math.max(0,MeasureSpec.getSize(heightSpec)-other),width=Math.min(MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight(),Math.round(available*16f/9));
                previewHost.getLayoutParams().width=width;previewFeedback.getLayoutParams().width=width;super.onMeasure(widthSpec,heightSpec);
            }
        };right.setOrientation(LinearLayout.VERTICAL);right.setPadding(px(3.84f),desktopScroll.getPaddingTop()+scrollPosition.topMargin,px(3.84f),0);right.setGravity(Gravity.TOP);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-1,1);rp.leftMargin=px(8);desktop.addView(right,rp);
        previewTitle=desktopLabel(tr("桌面预览","Desktop preview"));right.addView(previewTitle);
        previewHost=new SurfaceCorners.Frame(activity){private final SettingsFocusDrawable border=new SettingsFocusDrawable(unit,11.52f,Color.TRANSPARENT,false,0x35ffffff);@Override protected void dispatchDraw(Canvas canvas){super.dispatchDraw(canvas);border.setBounds(0,0,getWidth(),getHeight());border.draw(canvas);}};previewHost.setBackgroundDrawable(surface(0xff000000,0xff000000,0,11.52f));UiCompat.clipOutline(previewHost);right.addView(previewHost,new LinearLayout.LayoutParams(-1,-2));
        preview=new HomePreviewView(activity,chinese,fixedGreeting);preview.observeDockLayout(()->{adjustments.get(dockScale).maximum(preview.maximumDockScale());refreshPinnedCount();});previewHost.addView(preview,new FrameLayout.LayoutParams(-1,-2));preview.sync.observe(this::renderPreviewFeedback);
        previewFeedback=new PreviewFeedbackView(activity);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.topMargin=px(12);right.addView(previewFeedback,fp);
        saverError=muted("",12);saverError.setTextColor(UiPalette.TEXT);saverError.setVisibility(View.GONE);right.addView(saverError);
        preview.observeMessages(text->{if(!saverScene)previewNotice(text);});desktopScroll.getViewTreeObserver().addOnGlobalLayoutListener(desktopGeometry);wireDesktop();refreshControls();activity.afterContentReady(()->{previewReady=true;resumeMediaPreview();});activity.contentReady("desktop_settings",pageRoot);
    }
    private void resumeMediaPreview(){if(!mediaResumed||!previewReady)return;if(saverScene){if(saverPreview!=null)saverPreview.resume();}else preview.resume();}
    private void ensureSaverPreview(){if(saverPreview!=null)return;saverPreview=new ScreensaverPreviewView(activity,this::previewNotice,()->saverControls.refreshLibrary());saverPreview.sync.observe(this::renderPreviewFeedback);saverPreview.setVisibility(View.GONE);previewHost.addView(saverPreview,new FrameLayout.LayoutParams(-1,-2));}
    private Button layoutChoice(LinearLayout parent,String label,String name){Button b=button(label,()->{desktopStore.layout(name);refreshControls();});b.setFocusableInTouchMode(true);b.setId(androidx.core.view.ViewCompat.generateViewId());b.setTag("layout-"+name);b.setContentDescription(label);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,12.48f*unit);b.setPadding(px(9.6f),0,px(9.6f),0);b.setGravity(Gravity.CENTER);b.setSingleLine(true);StateListDrawable background=new StateListDrawable();background.addState(new int[]{android.R.attr.state_selected},new SettingsFocusDrawable(unit,12,UiPalette.CONTROL,false,UiPalette.EDGE,true));background.addState(new int[0],new SettingsFocusDrawable(unit,12,UiPalette.CONTROL,false,UiPalette.EDGE));b.setBackgroundDrawable(background);b.setPadding(px(9.6f),0,px(9.6f),0);b.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));LinearLayout.LayoutParams position=new LinearLayout.LayoutParams(0,px(42),1);if(parent.getChildCount()>0)position.leftMargin=px(6);parent.addView(b,position);return b;}
    private SeekBar wallpaperControl(LinearLayout parent,boolean second,String property){
        boolean fade="fade".equals(property),brightness="brightness".equals(property);
        return rangeControl(parent,second,property,fade?tr("淡入时间","Fade in"):brightness?tr("亮度","Brightness"):tr("模糊度","Blur"),brightness?20:0,fade?1200:100,fade?100:5,fade?100:1,false);
    }
    private SeekBar appearanceControl(LinearLayout parent,String key,String caption,int minimum,int maximum,int step){return rangeControl(parent,false,key,caption,minimum,maximum,step,1,true);}
    private SeekBar rangeControl(LinearLayout parent,boolean second,String property,String caption,int minimum,int maximum,int step,int scale,boolean appearance){
        boolean fade="fade".equals(property);int increment=step/scale;
        LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);line.setClipChildren(false);line.setClipToPadding(false);line.setPadding(px(9.6f),0,px(9.6f),0);parent.addView(line,new LinearLayout.LayoutParams(-1,px(42)));
        LinearLayout captionBox=column();captionBox.setGravity(Gravity.CENTER_VERTICAL);TextView name=label(caption,13);name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,12.48f*unit);name.setSingleLine(true);captionBox.addView(name,new LinearLayout.LayoutParams(-1,-2));line.addView(captionBox,new LinearLayout.LayoutParams(0,-1,1));
        SeekBar slider=new SeekBar(activity){@Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);WallpaperAdjustment adjustment=adjustments.get(this);if(adjustment!=null){SettingsFocusMotion.row(adjustment.line,focused&&hasFocus());adjustment.style();}}};slider.setId(androidx.core.view.ViewCompat.generateViewId());slider.setTag(appearance?"appearance-"+property:(second?"second":"home")+"-wallpaper-"+property);slider.setMax((maximum-minimum)/scale);slider.setKeyProgressIncrement(increment);slider.setFocusable(true);slider.setFocusableInTouchMode(true);
        slider.setContentDescription((second?tr("第二屏应用","Second-screen apps"):tr("首页应用","Home apps"))+"  "+caption);slider.setPadding(px(14),0,px(14),0);slider.setBackgroundDrawable(ControlStyle.action(unit,9,Color.TRANSPARENT,0));
        GradientDrawable track=new GradientDrawable();track.setColor(0x33ffffff);track.setCornerRadius(px(2));track.setSize(px(1),px(4));
        GradientDrawable progress=new GradientDrawable();progress.setColor(UiPalette.SLIDER_PROGRESS);progress.setCornerRadius(px(2));progress.setSize(px(1),px(4));
        LayerDrawable rail=new LayerDrawable(new Drawable[]{track,new ClipDrawable(progress,Gravity.START,ClipDrawable.HORIZONTAL)}){
            @Override protected void onBoundsChange(Rect bounds){int top=bounds.centerY()-px(2);super.onBoundsChange(new Rect(bounds.left,top,bounds.right,top+px(4)));}
        };rail.setId(0,android.R.id.background);rail.setId(1,android.R.id.progress);slider.setProgressDrawable(rail);
        GradientDrawable thumb=new GradientDrawable();thumb.setColor(Color.WHITE);thumb.setCornerRadius(px(9));thumb.setSize(px(18),px(18));slider.setThumb(thumb);slider.setThumbOffset(px(9));slider.setPadding(px(9),0,px(9),0);if(android.os.Build.VERSION.SDK_INT>=21)slider.setSplitTrack(false);
        line.addView(slider,new LinearLayout.LayoutParams(px(86.4f),-1));TextView value=muted(fade?"0.0s":minimum+"%",13);value.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,10.08f*unit);value.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(px(30.72f),-1);vp.leftMargin=px(6.24f);line.addView(value,vp);
        line.setBackgroundDrawable(new SettingsFocusDrawable(unit,12,Color.TRANSPARENT,true));slider.setBackgroundColor(Color.TRANSPARENT);
        WallpaperAdjustment adjustment=new WallpaperAdjustment(slider,line,name,value,track,progress,thumb,second,property,caption,minimum,maximum,scale,step,appearance);
        adjustments.put(slider,adjustment);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int amount,boolean fromUser){int actual=minimum+amount*scale;if(fromUser){adjustment.begin();adjustment.preview(minimum+Math.round((actual-minimum)/(float)step)*step);}else adjustment.describe(actual);}
            public void onStartTrackingTouch(SeekBar bar){bar.requestFocus();showPreview(bar);adjustment.begin();}
            public void onStopTrackingTouch(SeekBar bar){adjustment.commit();}
        });
        slider.setOnTouchListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_CANCEL)adjustment.cancel();return false;});
        slider.setOnKeyListener((v,key,event)->{
            boolean confirm=key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER;
            if(confirm){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){if(adjustment.draft.editing())adjustment.commit();else adjustment.begin();}if(event.getAction()==KeyEvent.ACTION_UP)KeyFeedback.confirm(activity);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){
                if(event.getAction()==KeyEvent.ACTION_DOWN){adjustment.begin();int before=adjustment.draft.value();adjustment.draft.move(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1,event.getRepeatCount()>0?event.getEventTime()-event.getDownTime():0);if(before!=adjustment.draft.value()){adjustment.preview(adjustment.draft.value());KeyFeedback.moved(activity);}}return true;
            }
            if(adjustment.draft.editing()&&(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN)){if(event.getAction()==KeyEvent.ACTION_DOWN)return !adjustment.commit();return true;}return false;
        });return slider;
    }
    private final class WallpaperAdjustment {
        final SeekBar slider;final LinearLayout line;final TextView name,value;final GradientDrawable track,progress,thumb;
        final boolean second,appearance;final String property,caption;final int minimum,scale;final SettingsRangeDraft draft;
        int maximum;
        WallpaperAdjustment(SeekBar slider,LinearLayout line,TextView name,TextView value,GradientDrawable track,GradientDrawable progress,GradientDrawable thumb,boolean second,String property,String caption,int minimum,int maximum,int scale,int step,boolean appearance){
            this.slider=slider;this.line=line;this.name=name;this.value=value;this.track=track;this.progress=progress;this.thumb=thumb;this.second=second;this.appearance=appearance;this.property=property;this.caption=caption;this.minimum=minimum;this.maximum=maximum;this.scale=scale;draft=new SettingsRangeDraft(minimum,maximum,step);
        }
        int committed(){if(appearance)return desktopStore.appearance(property);return "blur".equals(property)?(second?desktopStore.secondWallpaperBlur():desktopStore.homeWallpaperBlur()):"brightness".equals(property)?(second?desktopStore.secondWallpaperBrightness():desktopStore.homeWallpaperBrightness()):(second?desktopStore.secondWallpaperFade():desktopStore.homeWallpaperFade());}
        boolean saving;
        void begin(){if(saving||activeAdjustment==this&&draft.editing())return;cancelAdjustment();showPreview(slider);draft.begin(Math.min(committed(),maximum));activeAdjustment=this;saveError="";render(draft.value());renderPreviewFeedback();}
        void preview(int candidate){draft.preview(candidate);render(draft.value());if(preview!=null){if(appearance)preview.previewAppearance(property,draft.value());else preview.previewEffect(second,property,draft.value());}renderPreviewFeedback();}
        void maximum(int limit){if(maximum==limit)return;maximum=limit;int before=draft.value();draft.maximum(limit);slider.setMax((limit-minimum)/scale);render(draft.editing()?draft.value():committed());if(draft.editing()&&before!=draft.value()&&preview!=null)preview.previewAppearance(property,draft.value());}
        void render(int current){current=Math.max(minimum,Math.min(maximum,current));slider.setProgress((current-minimum)/scale);describe(current);style();}
        void describe(int current){String text="fade".equals(property)?String.format(java.util.Locale.ROOT,"%.1fs",current/1000f):current+"%";value.setText(text);slider.setContentDescription((appearance?"":(second?tr("第二屏背景","Second-screen background"):tr("首页背景","Home background"))+"  ")+caption+"  "+text+"  "+(draft.editing()?tr("左右调整  确定保存  上下保存并切换  返回取消","Left/right to adjust, OK to save, up/down to save and move, Back to cancel"):tr("左右调整","Left/right to adjust")));}
        boolean commit(){
            if(activeAdjustment!=this||!draft.editing())return true;int candidate=draft.value(),original=draft.original();
            draft.commit();activeAdjustment=null;saveError="";
            if(candidate==original){finishSave(candidate);return true;}
            saving=true;activity.saveSetting(()->{if(appearance)desktopStore.saveAppearance(property,candidate);else desktopStore.saveWallpaperEffect(second,property,candidate,original);},error->{saving=false;if(error.isEmpty()){finishSave(candidate);success(tr("外观设置已保存","Appearance saved"));}else{if(activeAdjustment==null&&preview!=null){if(appearance)preview.clearAppearancePreview(property);else preview.clearEffectPreview();}render(committed());previewSaveFailed(error);}});
            render(candidate);renderPreviewFeedback();return true;
        }
        private void finishSave(int candidate){if(activeAdjustment==null&&preview!=null){if(appearance)preview.clearAppearancePreview(property);else preview.clearEffectPreview();if("fade".equals(property))preview.fadeIn(candidate);}render(candidate);renderPreviewFeedback();}

        void cancel(){if(activeAdjustment!=this||!draft.editing())return;draft.cancel();activeAdjustment=null;saveError="";if(preview!=null){if(appearance)preview.clearAppearancePreview(property);else preview.clearEffectPreview();}render(committed());renderPreviewFeedback();}
        void focus(boolean focused){if(!focused)cancel();line.setActivated(focused);SettingsFocusMotion.row(line,focused);style();}
        void style(){boolean focused=slider.hasFocus()&&slider.hasWindowFocus(),editing=focused&&draft.editing();name.setTextColor(focused?UiPalette.INK:UiPalette.TEXT);value.setTextColor(focused?UiPalette.INK_SECONDARY:UiPalette.SECONDARY);track.setColor(focused?0x26000000:0x33ffffff);progress.setColor(focused?UiPalette.SLIDER_PROGRESS_FOCUSED:UiPalette.SLIDER_PROGRESS);thumb.setColor(Color.WHITE);thumb.setStroke(px(editing?1.5f:.5f),editing?0x40007aff:focused?0x26000000:0x14000000);}
    }
    private void cancelAdjustment(){if(activeAdjustment!=null)activeAdjustment.cancel();}
    private DisplayAppearanceChoice appearanceChoice(LinearLayout parent,String key,String title,String[] values,String[] names){DisplayAppearanceChoice choice=new DisplayAppearanceChoice(activity,desktopStore,key,title,values,names,new DisplayAppearanceChoice.Preview(){public void value(String property,Object value){if(preview!=null)preview.previewAppearance(property,value);}public void clear(String property){if(preview!=null)preview.clearAppearancePreview(property);}},this::wireDesktop);if("dock-limit".equals(key))choice.twoColumns();appearanceChoices.add(choice);parent.addView(choice,new LinearLayout.LayoutParams(-1,-2));return choice;}
    private boolean checked(String key){switch(key){case "greeting":return desktopStore.greetingEnabled();case "time":return desktopStore.showTime();case "date":return desktopStore.showDate();case "seconds":return desktopStore.showSeconds();case "lunar-date":return desktopStore.showLunarDate();case "weather":return weatherStore.enabled();case "home-apps":return desktopStore.showHomeApps();case "dock-background":return desktopStore.showDockBackground();case "all-apps-names":return desktopStore.showAllAppsNames();case "dock-magnify":return desktopStore.dockMagnify();case "global-glass":return desktopStore.globalGlass();default:return false;}}
    private DesktopSettingsRow choice(LinearLayout parent,String key,String title){DesktopSettingsRow b=setting(title,"","display-"+key);b.displayStyle();b.checked(false);b.setOnClickListener(v->{showPreview(v);saveError="";boolean value=!b.checked();try{switch(key){case "greeting":desktopStore.greetingEnabled(value);break;case "time":desktopStore.showTime(value);break;case "date":desktopStore.showDate(value);break;case "seconds":desktopStore.showSeconds(value);break;case "lunar-date":desktopStore.showLunarDate(value);break;case "home-apps":desktopStore.showHomeApps(value);break;case "dock-background":desktopStore.showDockBackground(value);break;case "all-apps-names":desktopStore.showAllAppsNames(value);break;case "weather":weatherStore.enabled(value);activity.weatherSettingsChanged();break;case "dock-magnify":case "global-glass":desktopStore.saveAppearance(key,value);break;}}catch(java.io.IOException error){previewSaveFailed(error.getMessage());}refreshControls();});choices.put(key,b);activity.addSetting(parent,b);return b;}
    private void pairSettings(LinearLayout parent,DesktopSettingsRow left,DesktopSettingsRow right){parent.removeView(left);parent.removeView(right);LinearLayout pair=row();pair.setClipToPadding(false);pair.setPadding(px(9.6f),px(3.2f),px(9.6f),px(3.2f));parent.addView(pair);left.compactTile();right.compactTile();pair.addView(left,new LinearLayout.LayoutParams(0,px(42),1));LinearLayout.LayoutParams position=new LinearLayout.LayoutParams(0,px(42),1);position.leftMargin=px(6);pair.addView(right,position);}
    private void collectControls(View view,List<View[]> rows){if(view.getVisibility()!=View.VISIBLE)return;if(view instanceof DisplayAppearanceChoice){for(View[] row:((DisplayAppearanceChoice)view).navigation())rows.add(row);return;}if(view.isFocusable()&&view.isEnabled()){rows.add(new View[]{view});return;}if(view instanceof LinearLayout&&((LinearLayout)view).getOrientation()==LinearLayout.HORIZONTAL){ArrayList<View> segment=new ArrayList<>();LinearLayout line=(LinearLayout)view;for(int i=0;i<line.getChildCount();i++){View child=line.getChildAt(i);if(child.getVisibility()==View.VISIBLE&&child.isFocusable()&&child.isEnabled()){segment.add(child);}}if(segment.size()>1){rows.add(segment.toArray(new View[0]));return;}}if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)collectControls(group.getChildAt(i),rows);}}
    private void wireDesktop(){
        if(desktopScroll==null)return;ArrayList<View[]> rows=new ArrayList<>();collectControls(desktopScroll,rows);
        for(int r=0;r<rows.size();r++)for(int c=0;c<rows.get(r).length;c++){View v=rows.get(r)[c];if(v.getId()==View.NO_ID)v.setId(androidx.core.view.ViewCompat.generateViewId());if(wiredFocus.add(v)){View.OnFocusChangeListener original=v.getOnFocusChangeListener();v.setOnFocusChangeListener((view,focused)->{if(original!=null)original.onFocusChange(view,focused);WallpaperAdjustment adjustment=adjustments.get(view);if(adjustment!=null)adjustment.focus(focused);if(focused){showPreview(view);revealDesktopEnd(view);}if(preview!=null&&view==dockMagnification)preview.demoDock(focused);});}v.setNextFocusLeftId(rows.get(r)[Math.max(0,c-1)].getId());v.setNextFocusRightId(rows.get(r)[Math.min(rows.get(r).length-1,c+1)].getId());v.setNextFocusUpId(r==0?managementTab().getId():nearest(v,rows.get(r-1),c).getId());v.setNextFocusDownId(r==rows.size()-1?v.getId():nearest(v,rows.get(r+1),c).getId());}
        renderPreviewFeedback();
    }
    private void revealDesktopEnd(View control){
        if(control.getNextFocusDownId()!=control.getId())return;
        // Focus scrolling reveals the button alone; include the final card's trailing padding.
        desktopScroll.post(()->{if(control.hasFocus()&&control.getNextFocusDownId()==control.getId()){
            View content=desktopScroll.getChildAt(0);
            desktopScroll.smoothScrollTo(0,content.getBottom()+desktopScroll.getPaddingBottom()-desktopScroll.getHeight());
        }});
    }
    private View nearest(View from,View[] row,int fallback){
        // Enter media filters at the current type, regardless of the preceding row's center.
        for(View option:row)if(option.isSelected()&&option.getTag() instanceof String&&((String)option.getTag()).contains("-library-type-"))return option;
        if(from.getWidth()==0)return row[Math.min(fallback,row.length-1)];int[] location=new int[2];from.getLocationOnScreen(location);float x=location[0]+from.getWidth()/2f,best=Float.MAX_VALUE;View target=row[Math.min(fallback,row.length-1)];for(View option:row){if(option.getWidth()==0)continue;option.getLocationOnScreen(location);float distance=Math.abs(location[0]+option.getWidth()/2f-x);if(distance<best){best=distance;target=option;}}return target;
    }
    private void showPreview(View control){
        if(control!=null&&control.getTag() instanceof String)previewFocus=(String)control.getTag();
        if(wallControls!=null&&wallControls.contains(control)){wallScene=true;saverScene=false;wallControls.showPreview();return;}
        if(wallScene&&preview!=null){wallScene=false;preview.clearBackgroundPreview();}
        if(saverControls!=null&&saverControls.contains(control)){saverScene=true;saverControls.showPreview();renderPreviewFeedback();return;}
        weatherEditor.wallpaperPreview(WallpaperStore.get(activity).snapshot().weather);saverScene=false;if(saverPreview!=null){saverPreview.pause();saverPreview.setVisibility(View.GONE);}if(preview==null)return;preview.setVisibility(View.VISIBLE);saverError.setVisibility(View.GONE);resumeMediaPreview();
        boolean second=control==choices.get("all-apps-names")||control==secondBlur||control==secondBrightness||control==secondFade;
        if(preview==null)return;if(previewSecond==second){previewTitle.setText(second?tr("第二屏预览","Second-screen preview"):tr("桌面预览","Desktop preview"));renderPreviewFeedback();return;}
        previewSecond=second;
        previewTitle.setText(second?tr("第二屏预览","Second-screen preview"):tr("桌面预览","Desktop preview"));
        preview.showSecondScreen(second);renderPreviewFeedback();
    }
    private void showWallpaperPreview(String id,boolean weather,boolean dayNight,boolean muted,String error){
        if(preview==null||!wallScene)return;weatherEditor.wallpaperPreview(weather);View focus=getCurrentFocus();if(desktopScroll.hasFocus()&&!wallControls.contains(focus))return;
        if(saverPreview!=null){saverPreview.pause();saverPreview.setVisibility(View.GONE);}preview.setVisibility(View.VISIBLE);previewTitle.setText(tr("桌面预览","Desktop preview"));previewSecond=false;preview.showSecondScreen(false);preview.previewBackground(id,weather,dayNight,muted);resumeMediaPreview();previewNotice(error);renderPreviewFeedback();
    }
    private void showSaverPreview(ScreensaverSettings settings,String candidate){
        if(!saverScene||previewHost==null)return;weatherEditor.wallpaperPreview(settings.showWeather||ScreensaverSettings.FOLLOW.equals(settings.mode)&&WallpaperStore.get(activity).snapshot().weather);View focus=getCurrentFocus();if(desktopScroll.hasFocus()&&!saverControls.contains(focus))return;ensureSaverPreview();
        preview.pause();preview.setVisibility(View.GONE);previewTitle.setText(tr("屏保预览","Screensaver preview"));saverPreview.setVisibility(View.VISIBLE);saverPreview.show(settings,candidate);resumeMediaPreview();renderPreviewFeedback();
    }
    private void previewNotice(String text){if(saverError!=null){saverError.setText(text);saverError.setVisibility(View.GONE);}renderPreviewFeedback();}
    private void previewSaveFailed(String text){saveError=text;error(text);renderPreviewFeedback();}
    private void renderPreviewFeedback(){
        if(previewFeedback==null||preview==null)return;
        PreviewSyncState state=saverScene&&saverPreview!=null?saverPreview.sync:preview.sync;
        String error=saveError;
        if(error.isEmpty()&&wallScene&&wallControls!=null)error=wallControls.error();
        if(error.isEmpty()&&saverScene&&saverControls!=null)error=saverControls.error();
        if(error.isEmpty())for(DisplayAppearanceChoice choice:appearanceChoices)if(choice.hasFocus()&&!choice.error().isEmpty()){error=choice.error();break;}
        if(error.isEmpty())error=state.error();
        boolean busy=wallScene&&wallControls!=null&&wallControls.busy();
        boolean draft=activeAdjustment!=null||wallScene&&wallControls.drafting()||saverScene&&saverControls.drafting();
        for(DisplayAppearanceChoice choice:appearanceChoices)draft|=choice.drafting();
        boolean visual=!previewFocus.equals("key-sounds")&&!previewFocus.startsWith("appearance-key-sound-theme")&&!previewFocus.startsWith("appearance-saver-wait");
        String hint=previewHint();
        if(draft&&!busy)hint=tr("未保存  确定保存  返回取消","Not saved  OK to save  Back to cancel");
        if(activeAdjustment!=null)hint=tr("未保存  左右调整  确定保存  上下保存并切换  返回取消","Not saved  Left/right to adjust  OK to save  Up/down to save and move  Back to cancel");
        if(busy)hint=wallControls.loadingMessage();
        if(!error.isEmpty())hint=error;
        PreviewSyncState.Status phase=!error.isEmpty()?PreviewSyncState.Status.FAILED:busy?PreviewSyncState.Status.SYNCING:state.status();
        previewFeedback.render(hint,phase,mediaResumed&&visual&&(!draft||busy||!error.isEmpty()),busy?tr("获取中","Loading"):null);
    }
    private String previewHint(){
        String tag=previewFocus;
        if(tag.equals("wall-daily-details"))return tr("查看图片信息，也可手动刷新今日壁纸。","View image details or manually refresh today's wallpaper.");
        if(tag.startsWith("appearance-saver-wait"))return tr("无操作达到此时间后进入屏保。","Starts the screensaver after this idle time.");
        if(tag.startsWith("saver-")||tag.startsWith("appearance-saver-content"))return tr("设置闲置时显示的画面。","Choose what appears while the TV is idle.");
        if(tag.contains("wallpaper-blur"))return tr("调整背景模糊程度，预览随之更新。","Adjust background blur and view the result.");
        if(tag.contains("wallpaper-brightness"))return tr("调整背景亮度，文字与应用保持清晰。","Adjust background brightness while keeping text and apps clear.");
        if(tag.contains("wallpaper-fade"))return tr("调整桌面淡入时间。","Adjust the desktop fade-in duration.");
        if(tag.contains("home-blur-mode"))return tr("选择背景的模糊方式。","Choose how the background is blurred.");
        if(tag.equals("display-global-glass"))return tr("统一控制界面玻璃效果，关闭可减少模糊计算。","Control glass effects across the interface. Turn off to reduce blur processing.");
        if(tag.equals("key-sounds"))return tr("控制遥控器操作时的按键音。","Play sounds for remote-control actions.");
        if(tag.startsWith("appearance-key-sound-theme"))return tr("选择按键音色，开启按键音后可试听。","Choose a sound style. Enable key sounds to hear it.");
        if(tag.startsWith("appearance-wall-")||tag.startsWith("wall-"))return tr("选择桌面背景的来源与更新方式。","Choose the desktop background and update mode.");
        if(tag.startsWith("appearance-dock")||tag.startsWith("display-dock")||tag.startsWith("dock-"))return tr("调整 Dock 的大小、外观与应用显示。","Adjust Dock size, appearance and displayed apps.");
        if(tag.startsWith("appearance-icon"))return tr("调整应用图标的大小与外形。","Adjust app icon size and shape.");
        if(tag.equals("greeting"))return tr("设置桌面问候语与头像。","Set the desktop greeting and avatar.");
        return tr("调整左侧设置，查看右侧预览效果。","Adjust the settings and view the preview.");
    }
    private void refreshControls(){
        for(WallpaperAdjustment adjustment:adjustments.values())if(!adjustment.draft.editing())adjustment.render(adjustment.committed());
        keySounds.checked(desktopStore.keySounds());
        classic.setSelected("classic".equals(desktopStore.layout()));gallery.setSelected("gallery".equals(desktopStore.layout()));classic.setText(SettingsSelectionMark.label(tr("经典布局","Classic"),classic.isSelected(),unit));gallery.setText(SettingsSelectionMark.label(tr("艺术画廊","Gallery"),gallery.isSelected(),unit));
        for(Map.Entry<String,DesktopSettingsRow> entry:choices.entrySet())entry.getValue().checked(checked(entry.getKey()));
        choices.get("seconds").available(desktopStore.showTime());choices.get("lunar-date").available(desktopStore.showDate());
        for(DisplayAppearanceChoice choice:appearanceChoices)choice.refresh();
        clockStyle.available(desktopStore.showTime());
        if(dockMagnification!=null)((View)dockMagnification.getParent()).setVisibility(desktopStore.dockMagnify()?View.VISIBLE:View.GONE);
        try{homeButtonRow.value(new HomeButtonStore(this).read().ids.size()+" / 3");}catch(java.io.IOException error){homeButtonRow.value(tr("读取失败","Read failed"));}
        refreshPinnedCount();
        wallControls.refresh();wireDesktop();
        String text=desktopStore.greetingText(fixedGreeting?tr("欢迎回家","Welcome home"):"");greeting.value(!desktopStore.greetingEnabled()?(desktopStore.avatarEnabled()?tr("仅头像","Avatar only"):tr("已关闭","Off")):text.isEmpty()?tr("跟随时间","By time"):text);
        if(legacyHomeKey!=null)legacyHomeKey.value(!LegacyHomeKeyService.defaultHome(this)?tr("未设默认桌面","Set default Home first")
                :!LegacyHomeKeyService.authorized(this)?tr("待开启","Off"):!LegacyHomeKeyService.connected()?tr("等待连接","Connecting")
                :!LegacyHomeKeyService.filtering()?tr("按键过滤不可用","Key filter unavailable"):tr("已连接，待实测","Connected, test HOME"));
        if(preview!=null&&showing){if(wallScene)wallControls.showPreview();else if(saverScene)saverControls.showPreview();else preview.refresh();}renderPreviewFeedback();
    }
    private void refreshPinnedCount(){
        int pinned=preview==null?-1:preview.availablePinnedCount();
        addDockRow.value(pinned<0?"":pinned+tr(" 个已固定"," pinned"));
        addDockRow.available(pinned<0||pinned<desktopStore.appearance("dock-limit"));
    }
    private void remember(){View focus=getCurrentFocus();if(focus!=null&&pageRoot.hasFocus()&&focus.getTag() instanceof String)focusTag=(String)focus.getTag();scrollY=desktopScroll.getScrollY();}
    @Override void managementMoreOptions(LinearLayout menu){managementMoreOption(menu,tr("设为桌面","Set as Home"),()->startActivity(new Intent(activity,MaintenanceHelpActivity.class)));super.managementMoreOptions(menu);}
    void weatherComponent(){wallControls.focusComponent();OptionalComponentsUi.show(activity,"weather");}
    @Override void focusManagementContent(){View target=focusTag==null?null:pageRoot.findViewWithTag(focusTag);if(target==null||!target.isShown()||!target.isEnabled())target=pageRoot.findViewWithTag("appearance-wall-mode");if(target==null)target=homeBlur;target.requestFocus();}
    @Override protected void onResume(){super.onResume();mediaResumed=true;mediaImport.resume();weatherEditor.resume();wallControls.resume();PhoneSettingsSession.get(activity).observeConfiguration(changed);PhoneSettingsSession.get(activity).observeMediaNames(mediaNamesChanged);mediaNamesChanged.run();getSharedPreferences("desktop-settings",0).registerOnSharedPreferenceChangeListener(this);getSharedPreferences("weather",0).registerOnSharedPreferenceChangeListener(this);resumeMediaPreview();saverControls.refresh();showPreview(getCurrentFocus());refreshControls();desktopScroll.post(()->{if(showing){desktopScroll.scrollTo(0,scrollY);View target=focusTag==null?null:pageRoot.findViewWithTag(focusTag);if(target!=null&&target.isShown()&&target.isEnabled())target.requestFocusFromTouch();}});greetingEditor.restoreFocus();}
    @Override protected void onPause(){mediaResumed=false;if(previewFeedback!=null)previewFeedback.stop();mediaImport.pause();weatherEditor.pause();wallControls.pause();PhoneSettingsSession.get(activity).forgetConfiguration(changed);PhoneSettingsSession.get(activity).forgetMediaNames(mediaNamesChanged);if(saverPreview!=null)saverPreview.pause();saverControls.pause();cancelAdjustment();for(DisplayAppearanceChoice choice:appearanceChoices)choice.cancel(false);remember();getSharedPreferences("desktop-settings",0).unregisterOnSharedPreferenceChangeListener(this);getSharedPreferences("weather",0).unregisterOnSharedPreferenceChangeListener(this);main.removeCallbacks(changed);preview.pause();greetingEditor.pause();super.onPause();}
    @Override protected void onDestroy(){desktopScroll.getViewTreeObserver().removeGlobalOnLayoutListener(desktopGeometry);preview.destroy();if(saverPreview!=null)saverPreview.destroy();saverControls.destroy();wallControls.destroy();weatherEditor.destroy();mediaImport.destroy();greetingEditor.destroy();homeButtons.destroy();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){cancelAdjustment();remember();state.putString("focus",focusTag);state.putInt("scroll",scrollY);greetingEditor.saveState(state);homeButtons.saveState(state);saverControls.save(state);wallControls.save(state);weatherEditor.save(state);mediaImport.saveState(state);}
    @Override void activityResult(int request,int result,Intent data){super.activityResult(request,result,data);mediaImport.activityResult(request,result,data);}
    @Override void permissionResult(int request,int[] results){super.permissionResult(request,results);mediaImport.permissionResult(request);weatherEditor.permissionResult(request);}
    @Override public void handleBack(){if(activeAdjustment!=null)cancelAdjustment();else if(cancelChoice())return;else if(!managementTabFocused())managementTab().requestFocus();else super.handleBack();}
    private boolean cancelChoice(){if(wallScene&&wallControls.back())return true;if(saverScene&&saverControls.back())return true;for(DisplayAppearanceChoice choice:appearanceChoices)if(choice.back())return true;return false;}
    @Override void leaveManagement(Runnable action){cancelAdjustment();wallControls.cancel();action.run();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();
        if((key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_ESCAPE)&&event.getAction()==KeyEvent.ACTION_DOWN&&activeAdjustment==null&&cancelChoice())cancellingBack=true;
        if((key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_ESCAPE)&&(activeAdjustment!=null||cancellingBack)){
            if(event.getAction()==KeyEvent.ACTION_DOWN){cancellingBack=true;cancelAdjustment();}else if(event.getAction()==KeyEvent.ACTION_UP)cancellingBack=false;return true;
        }
        if(key==KeyEvent.KEYCODE_ESCAPE){if(event.getAction()==KeyEvent.ACTION_UP)handleBack();return true;}return false;
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences prefs,String key){
        if(preview!=null&&("show-home-apps".equals(key)||"show-watch-tv".equals(key)))preview.reloadScene();
        main.removeCallbacks(changed);main.post(changed);
    }
}
