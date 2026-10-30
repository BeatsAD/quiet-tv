package home.quiet.tv;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;

/** The current native desktop UI, hosted unchanged inside the unified settings window. */
final class DesktopDisplayPanel extends ManagementPanel implements SharedPreferences.OnSharedPreferenceChangeListener {
    private boolean chinese,fixedGreeting;
    private DesktopSettingsStore desktopStore;
    private WeatherStore weatherStore;
    private GreetingEditorPanel greetingEditor;
    private HomePreviewView preview;
    private TextView previewTitle;
    private boolean previewSecond;
    private LinearLayout desktop;
    private ScrollView desktopScroll;
    private DesktopSettingsRow greeting,legacyHomeKey,keySounds;
    private Button classic,gallery;
    private SeekBar homeBlur,secondBlur,homeBrightness,secondBrightness,homeFade,secondFade;
    private String focusTag;
    private int scrollY;
    private final Map<String,DisplayChoice> choices=new LinkedHashMap<>();
    private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable changed=this::refreshControls;
    private final Runnable previewFade=()->{if(preview!=null&&showing)preview.fadeIn(previewSecond?desktopStore.secondWallpaperFade():desktopStore.homeWallpaperFade());};
    DesktopDisplayPanel(AppManagementActivity activity){super(activity);}
    private String tr(String zh,String en){return activity.tr(zh,en);}
    @Override public void onCreate(Bundle state){
        chinese=activity.chinese();fixedGreeting=activity.fixedGreeting();desktopStore=new DesktopSettingsStore(this);weatherStore=new WeatherStore(this);LegacyHomeKeyService.prepare(this);
        pageRoot=column();activity.attachPanel(pageRoot);makeDesktop();
        if(state!=null){focusTag=state.getString("focus");scrollY=state.getInt("scroll");greetingEditor.restoreState(state);}
    }
    private DesktopSettingsRow setting(String name,String icon,String tag){return activity.setting(name,icon,tag);}
    private TextView desktopLabel(String text){TextView v=muted(text,13);v.setLayoutParams(new LinearLayout.LayoutParams(-1,px(28)));return v;}
    private void makeDesktop(){
        desktop=row();desktop.setGravity(Gravity.TOP);pageRoot.addView(desktop,new LinearLayout.LayoutParams(-1,-1));
        desktopScroll=new ScrollView(this);desktopScroll.setFocusable(false);desktopScroll.setVerticalScrollBarEnabled(false);desktop.addView(desktopScroll,new LinearLayout.LayoutParams(px(344),-1));
        LinearLayout controls=column();controls.setPadding(0,0,px(3),px(2));desktopScroll.addView(controls,new ScrollView.LayoutParams(-1,-2));
        controls.addView(desktopLabel(tr("布局方式","Layout")));gap(controls,8);LinearLayout layouts=row();controls.addView(layouts,new LinearLayout.LayoutParams(-1,px(38)));
        classic=layoutChoice(layouts,tr("经典布局","Classic"),"classic");gallery=layoutChoice(layouts,tr("艺术画廊","Gallery"),"gallery");
        gap(controls,16);controls.addView(desktopLabel(tr("首页信息","Home information")));gap(controls,8);
        LinearLayout first=row();controls.addView(first,new LinearLayout.LayoutParams(-1,px(38)));choice(first,"greeting",tr("问候语","Greeting"));choice(first,"time",tr("时间","Time"));choice(first,"date",tr("日期","Date"));choice(first,"weather",tr("天气","Weather"));
        greeting=setting(tr("首页问候语","Home greeting"),"desktop","greeting");greeting.compact();greeting.setBackgroundDrawable(ControlStyle.action(unit,9,0x10ffffff,0x22ffffff));greeting.tail("arrow");
        greetingEditor=new GreetingEditorPanel(activity,desktopStore,fixedGreeting?tr("欢迎回家","Welcome home"):"",this::refreshControls);greeting.setOnClickListener(v->greetingEditor.show());LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,px(38));gp.topMargin=px(8);controls.addView(greeting,gp);
        gap(controls,16);controls.addView(desktopLabel(tr("首页应用","Home apps")));gap(controls,8);LinearLayout homeApps=row();controls.addView(homeApps,new LinearLayout.LayoutParams(-1,px(38)));choice(homeApps,"home-apps",tr("首屏应用","Home apps"));choice(homeApps,"dock-background",tr("Dock 背景","Dock background"));choice(homeApps,"app-title",tr("应用标题","App title"));
        homeBlur=wallpaperControl(controls,false,"blur");homeBrightness=wallpaperControl(controls,false,"brightness");homeFade=wallpaperControl(controls,false,"fade");
        gap(controls,16);controls.addView(desktopLabel(tr("第二屏应用","Second-screen apps")));gap(controls,8);LinearLayout allApps=row();controls.addView(allApps,new LinearLayout.LayoutParams(-1,px(38)));choice(allApps,"all-apps-names",tr("应用名称","App names"));
        secondBlur=wallpaperControl(controls,true,"blur");secondBrightness=wallpaperControl(controls,true,"brightness");secondFade=wallpaperControl(controls,true,"fade");
        gap(controls,16);controls.addView(desktopLabel(tr("快捷按钮","Quick buttons")));gap(controls,8);LinearLayout shortcuts=row();controls.addView(shortcuts,new LinearLayout.LayoutParams(-1,px(38)));choice(shortcuts,"input",tr("信号源","Inputs"));choice(shortcuts,"network",tr("网络","Network"));choice(shortcuts,"settings",tr("设置","Settings"));
        gap(controls,16);controls.addView(desktopLabel(tr("操作反馈","Interaction feedback")));gap(controls,8);
        keySounds=setting(tr("按键音","Key sounds"),"sound","key-sounds");keySounds.compact();keySounds.setBackgroundDrawable(ControlStyle.action(unit,9,0x10ffffff,0x22ffffff));
        keySounds.setOnClickListener(v->{desktopStore.keySounds(!desktopStore.keySounds());refreshControls();});
        controls.addView(keySounds,new LinearLayout.LayoutParams(-1,px(38)));
        LinearLayout right=column();right.setGravity(Gravity.TOP);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-1,1);rp.leftMargin=px(24);desktop.addView(right,rp);
        previewTitle=desktopLabel(tr("首页实时预览","Live Home preview"));right.addView(previewTitle);gap(right,8);
        preview=new HomePreviewView(this,chinese,fixedGreeting);preview.setBackgroundDrawable(surface(0xff0e0f13,0xff0e0f13,0x26ffffff,10));UiCompat.clipOutline(preview);right.addView(preview,new LinearLayout.LayoutParams(-1,-2));
        wireDesktop();refreshControls();
    }
    private Button layoutChoice(LinearLayout parent,String label,String name){Button b=button(label,()->{desktopStore.layout(name);refreshControls();});b.setFocusableInTouchMode(true);b.setId(androidx.core.view.ViewCompat.generateViewId());b.setTag("layout-"+name);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(14));b.setPadding(px(7),0,px(7),0);ControlStyle.choice(b,unit,9,0x10ffffff,0x22ffffff);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(parent.getChildCount()>0)p.leftMargin=px(8);parent.addView(b,p);return b;}
    private SeekBar wallpaperControl(LinearLayout parent,boolean second,String property){
        boolean fade="fade".equals(property),brightness="brightness".equals(property);int minimum=brightness?20:0,scale=fade?100:1,increment=fade?1:5;
        String caption=fade?tr("淡入时间","Fade in"):brightness?tr("亮度","Brightness"):tr("模糊度","Blur");
        LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,px(38));lp.topMargin=px(8);parent.addView(line,lp);
        TextView name=label(caption,14);line.addView(name,new LinearLayout.LayoutParams(px(chinese?56:74),-2));
        SeekBar slider=new SeekBar(activity);slider.setId(androidx.core.view.ViewCompat.generateViewId());slider.setTag((second?"second":"home")+"-wallpaper-"+property);slider.setMax(fade?12:100-minimum);slider.setKeyProgressIncrement(increment);slider.setFocusable(true);slider.setFocusableInTouchMode(true);
        slider.setContentDescription((second?tr("第二屏应用","Second-screen apps"):tr("首页应用","Home apps"))+"  "+caption);slider.setPadding(px(14),0,px(14),0);slider.setBackgroundDrawable(ControlStyle.action(unit,9,Color.TRANSPARENT,0));
        GradientDrawable track=new GradientDrawable();track.setColor(0xff484b53);track.setCornerRadius(px(2));track.setSize(px(1),px(4));
        GradientDrawable progress=new GradientDrawable();progress.setColor(0xffe6e8ec);progress.setCornerRadius(px(2));progress.setSize(px(1),px(4));
        LayerDrawable rail=new LayerDrawable(new Drawable[]{track,new ClipDrawable(progress,Gravity.START,ClipDrawable.HORIZONTAL)}){
            @Override protected void onBoundsChange(Rect bounds){int top=bounds.centerY()-px(2);super.onBoundsChange(new Rect(bounds.left,top,bounds.right,top+px(4)));}
        };rail.setId(0,android.R.id.background);rail.setId(1,android.R.id.progress);slider.setProgressDrawable(rail);
        GradientDrawable thumb=new GradientDrawable();thumb.setColor(0xfff5f6f8);thumb.setCornerRadius(px(10));thumb.setSize(px(24),px(18));slider.setThumb(thumb);slider.setThumbOffset(px(12));if(android.os.Build.VERSION.SDK_INT>=21)slider.setSplitTrack(false);
        line.addView(slider,new LinearLayout.LayoutParams(0,-1,1));TextView value=muted(fade?"0.0s":minimum+"%",13);value.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);line.addView(value,new LinearLayout.LayoutParams(px(34),-1));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int amount,boolean fromUser){int actual=minimum+amount*scale;value.setText(fade?String.format(java.util.Locale.ROOT,"%.1fs",actual/1000f):String.format(java.util.Locale.ROOT,"%d%%",actual));if(fromUser)saveWallpaperSetting(second,property,actual);}
            public void onStartTrackingTouch(SeekBar bar){bar.requestFocus();showPreview(bar);}public void onStopTrackingTouch(SeekBar bar){}
        });
        slider.setOnKeyListener((v,key,event)->{
            if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT)return false;
            if(event.getAction()==KeyEvent.ACTION_DOWN){int amount=Math.max(0,Math.min(slider.getMax(),slider.getProgress()+(key==KeyEvent.KEYCODE_DPAD_RIGHT?increment:-increment)));if(amount!=slider.getProgress()){slider.setProgress(amount);saveWallpaperSetting(second,property,minimum+amount*scale);KeyFeedback.moved(activity);}}
            return true;
        });return slider;
    }
    private void saveWallpaperSetting(boolean second,String property,int value){
        if("blur".equals(property)){if(second)desktopStore.secondWallpaperBlur(value);else desktopStore.homeWallpaperBlur(value);}
        else if("brightness".equals(property)){if(second)desktopStore.secondWallpaperBrightness(value);else desktopStore.homeWallpaperBrightness(value);}
        else{if(second)desktopStore.secondWallpaperFade(value);else desktopStore.homeWallpaperFade(value);main.removeCallbacks(previewFade);main.postDelayed(previewFade,200);}
        if(preview!=null)preview.refresh();
    }
    private void choice(LinearLayout parent,String key,String title){DisplayChoice b=new DisplayChoice(title);b.setTag("display-"+key);b.setOnClickListener(v->{showPreview(v);boolean value=!b.isChecked();switch(key){case "greeting":desktopStore.greetingEnabled(value);break;case "time":desktopStore.showTime(value);break;case "date":desktopStore.showDate(value);break;case "app-title":desktopStore.showAppTitle(value);break;case "home-apps":desktopStore.showHomeApps(value);break;case "dock-background":desktopStore.showDockBackground(value);break;case "all-apps-names":desktopStore.showAllAppsNames(value);break;case "weather":weatherStore.enabled(value);activity.weatherSettingsChanged();break;case "input":desktopStore.showInput(value);break;case "network":desktopStore.showNetwork(value);break;case "settings":desktopStore.showSettings(value);break;}refreshControls();});choices.put(key,b);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(parent.getChildCount()>0)p.leftMargin=px(8);parent.addView(b,p);}
    private void wireDesktop(){
        View[][] rows={{classic,gallery},{choices.get("greeting"),choices.get("time"),choices.get("date"),choices.get("weather")},{greeting},{choices.get("home-apps"),choices.get("dock-background"),choices.get("app-title")},{homeBlur},{homeBrightness},{homeFade},{choices.get("all-apps-names")},{secondBlur},{secondBrightness},{secondFade},{choices.get("input"),choices.get("network"),choices.get("settings")},{keySounds}};
        for(int r=0;r<rows.length;r++)for(int c=0;c<rows[r].length;c++){View v=rows[r][c];v.setOnFocusChangeListener((view,focused)->{if(focused)showPreview(view);});v.setNextFocusLeftId(rows[r][Math.max(0,c-1)].getId());v.setNextFocusRightId(rows[r][Math.min(rows[r].length-1,c+1)].getId());v.setNextFocusUpId(r==0?managementTab().getId():rows[r-1][Math.min(c,rows[r-1].length-1)].getId());v.setNextFocusDownId(r==rows.length-1?v.getId():rows[r+1][Math.min(c,rows[r+1].length-1)].getId());}
        if(legacyHomeKey!=null){
            View[] shortcuts=rows[rows.length-1];for(View v:shortcuts)v.setNextFocusDownId(legacyHomeKey.getId());
            legacyHomeKey.setNextFocusUpId(shortcuts[0].getId());legacyHomeKey.setNextFocusDownId(legacyHomeKey.getId());
            legacyHomeKey.setNextFocusLeftId(legacyHomeKey.getId());legacyHomeKey.setNextFocusRightId(legacyHomeKey.getId());
        }
    }
    private void showPreview(View control){
        boolean second=control==choices.get("all-apps-names")||control==secondBlur||control==secondBrightness||control==secondFade;
        if(preview==null||previewSecond==second)return;
        main.removeCallbacks(previewFade);previewSecond=second;
        previewTitle.setText(second?tr("第二屏实时预览","Live second-screen preview"):tr("首页实时预览","Live Home preview"));
        preview.showSecondScreen(second);
    }
    private final class DisplayChoice extends KeyFeedback.Button implements Checkable {
        private boolean checked;
        DisplayChoice(String title){super(DesktopDisplayPanel.this.activity);setText(title);setAllCaps(false);setSingleLine(true);setEllipsize(android.text.TextUtils.TruncateAt.END);UiCompat.letterSpacing(this,0);setTextColor(TEXT);setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(14));setGravity(Gravity.CENTER);setIncludeFontPadding(false);setMinWidth(0);setMinimumWidth(0);setMinHeight(0);setMinimumHeight(0);setPadding(px(8),0,px(8),0);setId(androidx.core.view.ViewCompat.generateViewId());setFocusable(true);setFocusableInTouchMode(true);ControlStyle.choice(this,unit,9,0x10ffffff,0x22ffffff);if(android.os.Build.VERSION.SDK_INT>=21)setStateListAnimator(null);setCompoundDrawablePadding(px(6));}
        @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){
            super.onSizeChanged(width,height,oldWidth,oldHeight);
            Drawable mark=getCompoundDrawables()[0];float content=getPaint().measureText(getText().toString())+(mark==null?0:mark.getBounds().width()+getCompoundDrawablePadding());
            int padding=Math.max(px(8),(int)Math.floor((width-content)/2));setPadding(padding,0,padding,0);
        }
        public boolean isChecked(){return checked;}
        public void toggle(){setChecked(!checked);}
        public void setChecked(boolean value){checked=value;setSelected(value);Drawable mark=new Drawable(){final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);public void draw(Canvas c){Rect b=getBounds();p.setColor(checked?0xff535860:0xff777d88);c.drawRoundRect(new RectF(b),px(2),px(2),p);if(checked){p.setColor(0xfff5f6f8);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(px(1.6f));Path path=new Path();path.moveTo(b.left+b.width()*.2f,b.top+b.height()*.5f);path.lineTo(b.left+b.width()*.43f,b.top+b.height()*.73f);path.lineTo(b.left+b.width()*.82f,b.top+b.height()*.25f);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}};mark.setBounds(0,0,px(13),px(13));setCompoundDrawables(mark,null,null,null);}
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(CheckBox.class.getName());info.setCheckable(true);info.setChecked(checked);}
    }
    private void refreshControls(){
        homeBlur.setProgress(desktopStore.homeWallpaperBlur());secondBlur.setProgress(desktopStore.secondWallpaperBlur());
        homeBrightness.setProgress(desktopStore.homeWallpaperBrightness()-20);secondBrightness.setProgress(desktopStore.secondWallpaperBrightness()-20);homeFade.setProgress(desktopStore.homeWallpaperFade()/100);secondFade.setProgress(desktopStore.secondWallpaperFade()/100);
        keySounds.checked(desktopStore.keySounds());
        classic.setSelected("classic".equals(desktopStore.layout()));gallery.setSelected("gallery".equals(desktopStore.layout()));
        boolean[] checked={desktopStore.greetingEnabled(),desktopStore.showTime(),desktopStore.showDate(),weatherStore.enabled(),desktopStore.showHomeApps(),desktopStore.showDockBackground(),desktopStore.showAppTitle(),desktopStore.showAllAppsNames(),desktopStore.showInput(),desktopStore.showNetwork(),desktopStore.showSettings()};int i=0;for(DisplayChoice b:choices.values())b.setChecked(checked[i++]);
        String text=desktopStore.greetingText(fixedGreeting?tr("欢迎回家","Welcome home"):"");greeting.value(!desktopStore.greetingEnabled()?(desktopStore.avatarEnabled()?tr("仅头像","Avatar only"):tr("已关闭","Off")):text.isEmpty()?tr("跟随时间","By time"):text);
        if(legacyHomeKey!=null)legacyHomeKey.value(!LegacyHomeKeyService.defaultHome(this)?tr("未设默认桌面","Set default Home first")
                :!LegacyHomeKeyService.authorized(this)?tr("待开启","Off"):!LegacyHomeKeyService.connected()?tr("等待连接","Connecting")
                :!LegacyHomeKeyService.filtering()?tr("按键过滤不可用","Key filter unavailable"):tr("已连接，待实测","Connected, test HOME"));
        if(preview!=null&&showing)preview.refresh();
    }
    private void remember(){View focus=getCurrentFocus();if(focus!=null&&pageRoot.hasFocus()&&focus.getTag() instanceof String)focusTag=(String)focus.getTag();scrollY=desktopScroll.getScrollY();}
    @Override void focusManagementContent(){View target=focusTag==null?null:pageRoot.findViewWithTag(focusTag);if(target==null||!target.isShown()||!target.isEnabled())target=classic;target.requestFocus();}
    @Override protected void onResume(){super.onResume();getSharedPreferences("desktop-settings",0).registerOnSharedPreferenceChangeListener(this);getSharedPreferences("weather",0).registerOnSharedPreferenceChangeListener(this);preview.resume();showPreview(getCurrentFocus());refreshControls();desktopScroll.post(()->{if(showing)desktopScroll.scrollTo(0,scrollY);});greetingEditor.restoreFocus();}
    @Override protected void onPause(){remember();getSharedPreferences("desktop-settings",0).unregisterOnSharedPreferenceChangeListener(this);getSharedPreferences("weather",0).unregisterOnSharedPreferenceChangeListener(this);main.removeCallbacks(changed);main.removeCallbacks(previewFade);preview.pause();greetingEditor.pause();super.onPause();}
    @Override protected void onDestroy(){preview.destroy();greetingEditor.destroy();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){remember();state.putString("focus",focusTag);state.putInt("scroll",scrollY);greetingEditor.saveState(state);}
    @Override public void onSharedPreferenceChanged(SharedPreferences prefs,String key){main.removeCallbacks(changed);main.post(changed);}
}
