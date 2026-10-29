package home.quiet.tv;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Existing display preferences, presented as one scrolling list beside a local preview. */
final class DesktopDisplayPanel extends ManagementPanel implements SharedPreferences.OnSharedPreferenceChangeListener {
    private DesktopSettingsStore store;
    private WeatherStore weather;
    private GreetingEditorPanel greetingEditor;
    private HomePreviewView preview;
    private ScrollView scroll;
    private DesktopSettingsRow greeting,city;
    private Button classic,gallery;
    private String focusTag;
    private int scrollY;
    private final LinkedHashMap<String,DesktopSettingsRow> choices=new LinkedHashMap<>();
    private final ArrayList<View> navigation=new ArrayList<>();
    private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable changed=this::refresh;
    DesktopDisplayPanel(AppManagementActivity activity){super(activity);}
    private String tr(String zh,String en){return activity.tr(zh,en);}
    @Override public void onCreate(Bundle state){
        store=new DesktopSettingsStore(this);weather=new WeatherStore(this);LegacyHomeKeyService.prepare(activity);
        pageRoot=column();activity.attachPanel(pageRoot);
        LinearLayout columns=row();columns.setGravity(Gravity.TOP);pageRoot.addView(columns,new LinearLayout.LayoutParams(-1,-1));
        scroll=new ScrollView(activity);scroll.setFocusable(false);scroll.setVerticalScrollBarEnabled(false);
        columns.addView(scroll,new LinearLayout.LayoutParams(px(310),-1));
        LinearLayout controls=column();controls.setPadding(0,0,px(3),px(8));scroll.addView(controls,new ScrollView.LayoutParams(-1,-2));
        controls.addView(activity.section(tr("布局方式","Layout")));
        LinearLayout layouts=row();controls.addView(layouts,new LinearLayout.LayoutParams(-1,px(38)));
        classic=layout(layouts,tr("经典布局","Classic"),"classic");gallery=layout(layouts,tr("艺术画廊","Gallery"),"gallery");
        LinearLayout information=group(controls,tr("首页信息","Home information"));
        choice(information,"time",tr("时间","Time"),"clock");choice(information,"date",tr("日期","Date"),"desktop");
        choice(information,"weather",tr("天气","Weather"),"weather");choice(information,"greeting",tr("问候语","Greeting"),"info");
        LinearLayout personal=group(controls,tr("问候语与天气","Greeting and weather"));
        greeting=setting(personal,tr("首页问候语","Home greeting"),"info","greeting");greeting.tail("arrow");
        greetingEditor=new GreetingEditorPanel(activity,store,activity.fixedGreeting()?tr("欢迎回家","Welcome home"):"",this::refresh);greeting.setOnClickListener(v->greetingEditor.show());
        city=setting(personal,tr("天气城市","Weather city"),"pin","weather-city-entry");city.tail("arrow");city.setOnClickListener(v->activity.openWeather());
        LinearLayout homeApps=group(controls,tr("首页应用","Home apps"));
        choice(homeApps,"home-apps",tr("首屏应用","Home apps"),"desktop");choice(homeApps,"dock-background",tr("Dock 背景","Dock background"),"desktop");
        choice(homeApps,"app-title",tr("应用标题","App title"),"info");
        LinearLayout allApps=group(controls,tr("第二屏应用","Second-screen apps"));choice(allApps,"all-apps-names",tr("应用名称","App names"),"info");
        LinearLayout shortcuts=group(controls,tr("快捷按钮","Quick buttons"));
        choice(shortcuts,"input",tr("信号源","Inputs"),"desktop");choice(shortcuts,"network",tr("网络","Network"),"tools");choice(shortcuts,"settings",tr("设置","Settings"),"tools");
        LinearLayout right=column();LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-1,1);rp.leftMargin=px(26);columns.addView(right,rp);
        TextView heading=label(tr("首页预览","Home preview"),19);right.addView(heading,new LinearLayout.LayoutParams(-1,px(32)));gap(right,6);
        preview=new HomePreviewView(activity,activity.chinese(),activity.fixedGreeting());preview.setBackgroundDrawable(surface(0xff101116,0xff101116,0x30ffffff,10));UiCompat.clipOutline(preview);right.addView(preview,new LinearLayout.LayoutParams(-1,-2));
        gap(right,8);right.addView(muted(tr("调整即时生效","Changes apply immediately"),11));
        wire();refresh();
        if(state!=null){focusTag=state.getString("focus");scrollY=state.getInt("scroll");greetingEditor.restoreState(state);}
    }
    private LinearLayout group(LinearLayout parent,String title){gap(parent,12);parent.addView(activity.section(title));LinearLayout group=activity.group();parent.addView(group,new LinearLayout.LayoutParams(-1,-2));return group;}
    private DesktopSettingsRow setting(LinearLayout group,String title,String icon,String tag){
        if(group.getChildCount()>0){View divider=new View(activity);divider.setBackgroundColor(0x12ffffff);group.addView(divider,new LinearLayout.LayoutParams(-1,px(1)));}
        DesktopSettingsRow setting=activity.setting(title,icon,tag);setting.displayStyle();group.addView(setting,new LinearLayout.LayoutParams(-1,px(30)));navigation.add(setting);return setting;
    }
    private Button layout(LinearLayout parent,String title,String name){
        Button button=button(title,()->{store.layout(name);refresh();});button.setId(androidx.core.view.ViewCompat.generateViewId());button.setTag("layout-"+name);button.setFocusableInTouchMode(true);
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(14));button.setPadding(px(8),0,px(8),0);ControlStyle.choice(button,unit,10,0x10ffffff,0x22ffffff);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(parent.getChildCount()>0)p.leftMargin=px(8);parent.addView(button,p);return button;
    }
    private void choice(LinearLayout parent,String key,String title,String icon){
        DesktopSettingsRow choice=setting(parent,title,icon,"display-"+key);choices.put(key,choice);
        choice.setOnClickListener(v->{boolean value=!choice.checked();switch(key){
            case "time":store.showTime(value);break;case "date":store.showDate(value);break;
            case "weather":weather.enabled(value);activity.weatherSettingsChanged();break;case "greeting":store.greetingEnabled(value);break;
            case "app-title":store.showAppTitle(value);break;case "home-apps":store.showHomeApps(value);break;case "dock-background":store.showDockBackground(value);break;
            case "all-apps-names":store.showAllAppsNames(value);break;case "input":store.showInput(value);break;case "network":store.showNetwork(value);break;case "settings":store.showSettings(value);break;
        }refresh();});
    }
    private void wire(){
        for(Button button:new Button[]{classic,gallery}){button.setNextFocusUpId(managementTab().getId());button.setNextFocusDownId(navigation.get(0).getId());}
        classic.setNextFocusLeftId(classic.getId());classic.setNextFocusRightId(gallery.getId());gallery.setNextFocusLeftId(classic.getId());gallery.setNextFocusRightId(gallery.getId());
        for(int i=0;i<navigation.size();i++){View row=navigation.get(i);row.setNextFocusUpId(i==0?classic.getId():navigation.get(i-1).getId());row.setNextFocusDownId(navigation.get(Math.min(navigation.size()-1,i+1)).getId());row.setNextFocusLeftId(row.getId());row.setNextFocusRightId(row.getId());}
    }
    private void refresh(){
        classic.setSelected("classic".equals(store.layout()));gallery.setSelected("gallery".equals(store.layout()));
        choices.get("time").checked(store.showTime());choices.get("date").checked(store.showDate());choices.get("weather").checked(weather.enabled());choices.get("greeting").checked(store.greetingEnabled());
        choices.get("app-title").checked(store.showAppTitle());choices.get("home-apps").checked(store.showHomeApps());choices.get("dock-background").checked(store.showDockBackground());choices.get("all-apps-names").checked(store.showAllAppsNames());
        choices.get("input").checked(store.showInput());choices.get("network").checked(store.showNetwork());choices.get("settings").checked(store.showSettings());
        String text=store.greetingText(activity.fixedGreeting()?tr("欢迎回家","Welcome home"):"");greeting.value(!store.greetingEnabled()?(store.avatarEnabled()?tr("仅头像","Avatar only"):tr("已关闭","Off")):text.isEmpty()?tr("跟随时间","By time"):text);
        WeatherData cached=weather.cached(activity.chinese());String name=weather.automatic()?(cached==null?"":cached.city):weather.city();city.value(name.isEmpty()?tr("未设置","Not set"):name);
        preview.refresh();
    }
    private void remember(){View focus=getCurrentFocus();if(focus!=null&&pageRoot.hasFocus()&&focus.getTag() instanceof String)focusTag=(String)focus.getTag();scrollY=scroll.getScrollY();}
    @Override void focusManagementContent(){View target=focusTag==null?null:pageRoot.findViewWithTag(focusTag);if(target==null||!target.isShown()||!target.isEnabled())target=classic;target.requestFocus();}
    @Override protected void onResume(){super.onResume();getSharedPreferences("desktop-settings",0).registerOnSharedPreferenceChangeListener(this);getSharedPreferences("weather",0).registerOnSharedPreferenceChangeListener(this);preview.resume();refresh();scroll.post(()->{if(showing)scroll.scrollTo(0,scrollY);});greetingEditor.restoreFocus();}
    @Override protected void onPause(){remember();getSharedPreferences("desktop-settings",0).unregisterOnSharedPreferenceChangeListener(this);getSharedPreferences("weather",0).unregisterOnSharedPreferenceChangeListener(this);main.removeCallbacks(changed);preview.pause();greetingEditor.pause();super.onPause();}
    @Override protected void onDestroy(){preview.destroy();greetingEditor.destroy();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){remember();state.putString("focus",focusTag);state.putInt("scroll",scrollY);greetingEditor.saveState(state);}
    @Override public void onSharedPreferenceChanged(SharedPreferences preferences,String key){main.removeCallbacks(changed);main.post(changed);}
    @Override public void handleBack(){if(pageRoot.hasFocus())managementTab().requestFocus();else super.handleBack();}
}
