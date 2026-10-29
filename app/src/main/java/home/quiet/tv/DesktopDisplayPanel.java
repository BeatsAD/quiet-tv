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
    private LinearLayout desktop;
    private ScrollView desktopScroll;
    private DesktopSettingsRow greeting,legacyHomeKey,keySounds;
    private Button classic,gallery;
    private String focusTag;
    private int scrollY;
    private final Map<String,DisplayChoice> choices=new LinkedHashMap<>();
    private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable changed=this::refreshControls;
    DesktopDisplayPanel(AppManagementActivity activity){super(activity);}
    private String tr(String zh,String en){return activity.tr(zh,en);}
    @Override public void onCreate(Bundle state){
        chinese=activity.chinese();fixedGreeting=activity.fixedGreeting();desktopStore=new DesktopSettingsStore(this);weatherStore=new WeatherStore(this);LegacyHomeKeyService.prepare(this);
        pageRoot=column();pageRoot.setPadding(0,px(18),0,0);activity.attachPanel(pageRoot);makeDesktop();
        if(state!=null){focusTag=state.getString("focus");scrollY=state.getInt("scroll");greetingEditor.restoreState(state);}
    }
    private TextView section(String name){return activity.section(name);}
    private LinearLayout group(){return activity.group();}
    private DesktopSettingsRow setting(String name,String icon,String tag){return activity.setting(name,icon,tag);}
    private TextView desktopLabel(String text){TextView v=muted(text,13);v.setLayoutParams(new LinearLayout.LayoutParams(-1,px(28)));return v;}
    private void makeDesktop(){
        desktop=row();desktop.setGravity(Gravity.TOP);pageRoot.addView(desktop,new LinearLayout.LayoutParams(-1,-1));
        desktopScroll=new ScrollView(this);desktopScroll.setFocusable(false);desktopScroll.setVerticalScrollBarEnabled(false);desktop.addView(desktopScroll,new LinearLayout.LayoutParams(px(298),-1));
        LinearLayout controls=column();controls.setPadding(0,0,px(3),px(2));desktopScroll.addView(controls,new ScrollView.LayoutParams(-1,-2));
        controls.addView(section(tr("布局方式","Layout")));gap(controls,8);LinearLayout layouts=row();controls.addView(layouts,new LinearLayout.LayoutParams(-1,px(38)));
        classic=layoutChoice(layouts,tr("经典布局","Classic"),"classic");gallery=layoutChoice(layouts,tr("艺术画廊","Gallery"),"gallery");
        gap(controls,16);controls.addView(desktopLabel(tr("首页信息","Home information")));
        LinearLayout first=row();controls.addView(first,new LinearLayout.LayoutParams(-1,px(38)));choice(first,"greeting",tr("问候语","Greeting"));choice(first,"time",tr("时间","Time"));choice(first,"app-title",tr("应用标题","App title"));
        gap(controls,8);LinearLayout second=row();controls.addView(second,new LinearLayout.LayoutParams(-1,px(38)));choice(second,"date",tr("日期","Date"));choice(second,"weather",tr("天气","Weather"));
        greeting=setting(tr("首页问候语","Home greeting"),"desktop","greeting");greeting.compact();greeting.tail("arrow");
        greetingEditor=new GreetingEditorPanel(activity,desktopStore,fixedGreeting?tr("欢迎回家","Welcome home"):"",this::refreshControls);greeting.setOnClickListener(v->greetingEditor.show());LinearLayout greetingGroup=group();greetingGroup.addView(greeting,new LinearLayout.LayoutParams(-1,px(44)));LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,-2);gp.topMargin=px(8);controls.addView(greetingGroup,gp);
        gap(controls,16);controls.addView(desktopLabel(tr("首页应用","Home apps")));LinearLayout homeApps=row();controls.addView(homeApps,new LinearLayout.LayoutParams(-1,px(38)));choice(homeApps,"home-apps",tr("首屏应用","Home apps"));choice(homeApps,"dock-background",tr("Dock 背景","Dock background"));
        gap(controls,16);controls.addView(desktopLabel(tr("第二屏应用","Second-screen apps")));LinearLayout allApps=row();controls.addView(allApps,new LinearLayout.LayoutParams(-1,px(38)));choice(allApps,"all-apps-names",tr("应用名称","App names"));
        gap(controls,16);controls.addView(desktopLabel(tr("快捷按钮","Quick buttons")));LinearLayout shortcuts=row();controls.addView(shortcuts,new LinearLayout.LayoutParams(-1,px(38)));choice(shortcuts,"input",tr("信号源","Inputs"));choice(shortcuts,"network",tr("网络","Network"));choice(shortcuts,"settings",tr("设置","Settings"));
        gap(controls,16);controls.addView(desktopLabel(tr("操作反馈","Interaction feedback")));
        keySounds=setting(tr("按键音","Key sounds"),"sound","key-sounds");
        keySounds.setOnClickListener(v->{desktopStore.keySounds(!desktopStore.keySounds());refreshControls();});
        controls.addView(keySounds,new LinearLayout.LayoutParams(-1,px(48)));
        LinearLayout right=column();right.setGravity(Gravity.TOP);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-1,1);rp.leftMargin=px(24);desktop.addView(right,rp);
        right.addView(section(tr("首页实时预览","Live Home preview")));gap(right,8);
        preview=new HomePreviewView(this,chinese,fixedGreeting);preview.setBackgroundDrawable(surface(0xff0e0f13,0xff0e0f13,0x26ffffff,10));UiCompat.clipOutline(preview);right.addView(preview,new LinearLayout.LayoutParams(-1,-2));
        wireDesktop();refreshControls();
    }
    private Button layoutChoice(LinearLayout parent,String label,String name){Button b=button(label,()->{desktopStore.layout(name);refreshControls();});b.setFocusableInTouchMode(true);b.setId(androidx.core.view.ViewCompat.generateViewId());b.setTag("layout-"+name);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(14));b.setPadding(px(7),0,px(7),0);ControlStyle.choice(b,unit,9,0x10ffffff,0x22ffffff);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(parent.getChildCount()>0)p.leftMargin=px(8);parent.addView(b,p);return b;}
    private void choice(LinearLayout parent,String key,String title){DisplayChoice b=new DisplayChoice(title);b.setTag("display-"+key);b.setOnClickListener(v->{boolean value=!b.isChecked();switch(key){case "greeting":desktopStore.greetingEnabled(value);break;case "time":desktopStore.showTime(value);break;case "date":desktopStore.showDate(value);break;case "app-title":desktopStore.showAppTitle(value);break;case "home-apps":desktopStore.showHomeApps(value);break;case "dock-background":desktopStore.showDockBackground(value);break;case "all-apps-names":desktopStore.showAllAppsNames(value);break;case "weather":weatherStore.enabled(value);activity.weatherSettingsChanged();break;case "input":desktopStore.showInput(value);break;case "network":desktopStore.showNetwork(value);break;case "settings":desktopStore.showSettings(value);break;}refreshControls();});choices.put(key,b);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(parent.getChildCount()>0)p.leftMargin=px(8);parent.addView(b,p);}
    private void wireDesktop(){
        View[][] rows={{classic,gallery},{choices.get("greeting"),choices.get("time"),choices.get("app-title")},{choices.get("date"),choices.get("weather")},{greeting},{choices.get("home-apps"),choices.get("dock-background")},{choices.get("all-apps-names")},{choices.get("input"),choices.get("network"),choices.get("settings")},{keySounds}};
        for(int r=0;r<rows.length;r++)for(int c=0;c<rows[r].length;c++){View v=rows[r][c];v.setNextFocusLeftId(rows[r][Math.max(0,c-1)].getId());v.setNextFocusRightId(rows[r][Math.min(rows[r].length-1,c+1)].getId());v.setNextFocusUpId(r==0?managementTab().getId():rows[r-1][Math.min(c,rows[r-1].length-1)].getId());v.setNextFocusDownId(r==rows.length-1?v.getId():rows[r+1][Math.min(c,rows[r+1].length-1)].getId());}
        if(legacyHomeKey!=null){
            View[] shortcuts=rows[rows.length-1];for(View v:shortcuts)v.setNextFocusDownId(legacyHomeKey.getId());
            legacyHomeKey.setNextFocusUpId(shortcuts[0].getId());legacyHomeKey.setNextFocusDownId(legacyHomeKey.getId());
            legacyHomeKey.setNextFocusLeftId(legacyHomeKey.getId());legacyHomeKey.setNextFocusRightId(legacyHomeKey.getId());
        }
    }
    private final class DisplayChoice extends KeyFeedback.Button implements Checkable {
        private boolean checked;
        DisplayChoice(String title){super(DesktopDisplayPanel.this.activity);setText(title);setAllCaps(false);setSingleLine(true);setEllipsize(android.text.TextUtils.TruncateAt.END);UiCompat.letterSpacing(this,0);setTextColor(TEXT);setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(14));setGravity(Gravity.CENTER);setIncludeFontPadding(false);setMinWidth(0);setMinimumWidth(0);setMinHeight(0);setMinimumHeight(0);setPadding(px(6),0,px(6),0);setId(androidx.core.view.ViewCompat.generateViewId());setFocusable(true);setFocusableInTouchMode(true);ControlStyle.choice(this,unit,9,0x10ffffff,0x22ffffff);if(android.os.Build.VERSION.SDK_INT>=21)setStateListAnimator(null);setCompoundDrawablePadding(px(6));addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int pad=Math.max(px(6),(int)((getWidth()-px(21)-getPaint().measureText(getText().toString()))/2));setPadding(pad,0,pad,0);});}
        public boolean isChecked(){return checked;}
        public void toggle(){setChecked(!checked);}
        public void setChecked(boolean value){checked=value;setSelected(value);Drawable mark=new Drawable(){final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);public void draw(Canvas c){Rect b=getBounds();p.setColor(checked?0xff535860:0xff777d88);c.drawRoundRect(new RectF(b),px(2),px(2),p);if(checked){p.setColor(0xfff5f6f8);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(px(1.6f));Path path=new Path();path.moveTo(b.left+b.width()*.2f,b.top+b.height()*.5f);path.lineTo(b.left+b.width()*.43f,b.top+b.height()*.73f);path.lineTo(b.left+b.width()*.82f,b.top+b.height()*.25f);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}};mark.setBounds(0,0,px(13),px(13));setCompoundDrawables(mark,null,null,null);}
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(CheckBox.class.getName());info.setCheckable(true);info.setChecked(checked);}
    }
    private void refreshControls(){
        keySounds.checked(desktopStore.keySounds());
        classic.setSelected("classic".equals(desktopStore.layout()));gallery.setSelected("gallery".equals(desktopStore.layout()));
        boolean[] checked={desktopStore.greetingEnabled(),desktopStore.showTime(),desktopStore.showAppTitle(),desktopStore.showDate(),weatherStore.enabled(),desktopStore.showHomeApps(),desktopStore.showDockBackground(),desktopStore.showAllAppsNames(),desktopStore.showInput(),desktopStore.showNetwork(),desktopStore.showSettings()};int i=0;for(DisplayChoice b:choices.values())b.setChecked(checked[i++]);
        String text=desktopStore.greetingText(fixedGreeting?tr("欢迎回家","Welcome home"):"");greeting.value(!desktopStore.greetingEnabled()?(desktopStore.avatarEnabled()?tr("仅头像","Avatar only"):tr("已关闭","Off")):text.isEmpty()?tr("跟随时间","By time"):text);
        if(legacyHomeKey!=null)legacyHomeKey.value(!LegacyHomeKeyService.defaultHome(this)?tr("未设默认桌面","Set default Home first")
                :!LegacyHomeKeyService.authorized(this)?tr("待开启","Off"):!LegacyHomeKeyService.connected()?tr("等待连接","Connecting")
                :!LegacyHomeKeyService.filtering()?tr("按键过滤不可用","Key filter unavailable"):tr("已连接，待实测","Connected, test HOME"));
        if(preview!=null&&showing)preview.refresh();
    }
    private void remember(){View focus=getCurrentFocus();if(focus!=null&&pageRoot.hasFocus()&&focus.getTag() instanceof String)focusTag=(String)focus.getTag();scrollY=desktopScroll.getScrollY();}
    @Override void focusManagementContent(){View target=focusTag==null?null:pageRoot.findViewWithTag(focusTag);if(target==null||!target.isShown()||!target.isEnabled())target=classic;target.requestFocus();}
    @Override protected void onResume(){super.onResume();getSharedPreferences("desktop-settings",0).registerOnSharedPreferenceChangeListener(this);getSharedPreferences("weather",0).registerOnSharedPreferenceChangeListener(this);preview.resume();refreshControls();desktopScroll.post(()->{if(showing)desktopScroll.scrollTo(0,scrollY);});greetingEditor.restoreFocus();}
    @Override protected void onPause(){remember();getSharedPreferences("desktop-settings",0).unregisterOnSharedPreferenceChangeListener(this);getSharedPreferences("weather",0).unregisterOnSharedPreferenceChangeListener(this);main.removeCallbacks(changed);preview.pause();greetingEditor.pause();super.onPause();}
    @Override protected void onDestroy(){preview.destroy();greetingEditor.destroy();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){remember();state.putString("focus",focusTag);state.putInt("scroll",scrollY);greetingEditor.saveState(state);}
    @Override public void onSharedPreferenceChanged(SharedPreferences prefs,String key){main.removeCallbacks(changed);main.post(changed);}
}
