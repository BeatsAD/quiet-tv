package home.quiet.tv;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;
import org.json.JSONObject;

/** One settings shell; only Desktop owns the fixed, local Home preview. */
public final class DesktopSettingsActivity extends ManagementUi implements SharedPreferences.OnSharedPreferenceChangeListener {
    static final String SECTION="section";
    private boolean chinese,fixedGreeting,resumed;
    private String selectedSection="desktop";
    private DesktopSettingsStore desktopStore;
    private WeatherStore weatherStore;
    private GreetingEditorPanel greetingEditor;
    private DesktopWeatherPanel weather;
    private HomeGuardPanel guard;
    private HomePreviewView preview;
    private FrameLayout content;
    private LinearLayout desktop;
    private ScrollView desktopScroll;
    private DesktopSettingsRow greeting;
    private Button back,classic,gallery;
    private final LinkedHashMap<String,Button> tabs=new LinkedHashMap<>();
    private final Map<String,DisplayChoice> choices=new LinkedHashMap<>();
    private final Map<String,String> focusTags=new HashMap<>();
    private final Map<String,Integer> scrollPositions=new HashMap<>();
    private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable changed=this::refreshControls;
    String tr(String zh,String en){return chinese?zh:en;}
    boolean chinese(){return chinese;}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        try(java.io.InputStream input=getAssets().open("launcher.json");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            byte[] bytes=new byte[4096];int count;while((count=input.read(bytes))!=-1)out.write(bytes,0,count);
            JSONObject config=new JSONObject(out.toString("UTF-8"));chinese=!"en".equals(config.optString("language","zh"));fixedGreeting="home".equals(config.optString("greeting"));
        }catch(java.io.IOException|org.json.JSONException e){throw new IllegalStateException("Invalid bundled launcher configuration",e);}
        desktopStore=new DesktopSettingsStore(this);weatherStore=new WeatherStore(this);
        back=(Button)header.getChildAt(0);back.setId(View.generateViewId());back.setTag("back");back.setContentDescription(tr("返回","Back"));
        ((TextView)header.getChildAt(1)).setText(tr("桌面设置","Desktop Settings"));
        LinearLayout navigation=row();LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,px(44));np.topMargin=px(22);root.addView(navigation,np);
        tab(navigation,"desktop",tr("桌面显示","Desktop"));tab(navigation,"weather",tr("天气设置","Weather"));
        if(!BuildConfig.LEGACY_19)tab(navigation,"guard",tr("桌面守护","Guard"));
        View line=new View(this);line.setBackgroundColor(0x18ffffff);root.addView(line,new LinearLayout.LayoutParams(-1,px(1)));
        content=new FrameLayout(this);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,0,1);cp.topMargin=px(18);root.addView(content,cp);
        makeDesktop();
        if(saved!=null)for(String name:tabs.keySet()){String tag=saved.getString("focus-"+name);if(tag!=null)focusTags.put(name,tag);scrollPositions.put(name,saved.getInt("scroll-"+name));}
        String section=saved==null?getIntent().getStringExtra(SECTION):saved.getString("desktop-section","desktop");
        select(section,false);
        if(guard!=null)guard.restoreState(saved);
        if(saved!=null)greetingEditor.restoreState(saved);
        final String focused=saved==null?null:saved.getString("focus");
        root.post(()->{View target=focused==null?null:root.findViewWithTag(focused);if(target==null||!target.isShown()||!target.isEnabled())target=tabs.get(selectedSection);target.requestFocus();greetingEditor.restoreFocus();});
    }
    private void tab(LinearLayout navigation,String name,String text){
        Button b=button(text,()->select(name,true));styleTab(b,false);b.setFocusableInTouchMode(true);b.setId(View.generateViewId());b.setTag("tab-"+name);tabs.put(name,b);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,px(36));bp.rightMargin=px(12);navigation.addView(b,bp);
        b.setOnKeyListener((v,key,event)->{
            if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT&&key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;
            if(event.getAction()==KeyEvent.ACTION_UP)return true;
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){select(name,false);focusContent();}
            else{ArrayList<String> names=new ArrayList<>(tabs.keySet());int i=names.indexOf(name),next=Math.max(0,Math.min(names.size()-1,i+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1)));select(names.get(next),true);}
            return true;
        });b.setNextFocusUpId(back.getId());
    }
    private TextView desktopLabel(String text){TextView v=muted(text,13);v.setLayoutParams(new LinearLayout.LayoutParams(-1,px(28)));return v;}
    private void makeDesktop(){
        desktop=row();desktop.setGravity(Gravity.TOP);content.addView(desktop,new FrameLayout.LayoutParams(-1,-1));
        desktopScroll=new ScrollView(this);desktopScroll.setFocusable(false);desktopScroll.setVerticalScrollBarEnabled(false);desktop.addView(desktopScroll,new LinearLayout.LayoutParams(px(298),-1));
        LinearLayout controls=column();controls.setPadding(px(1),px(1),px(3),px(2));desktopScroll.addView(controls,new ScrollView.LayoutParams(-1,-2));
        controls.addView(desktopLabel(tr("布局方式","Layout")));LinearLayout layouts=row();controls.addView(layouts,new LinearLayout.LayoutParams(-1,px(38)));
        classic=layoutChoice(layouts,tr("经典布局","Classic"),"classic",1);gallery=layoutChoice(layouts,tr("艺术画廊","Gallery"),"gallery",1.38f);
        gap(controls,16);controls.addView(desktopLabel(tr("首页信息","Home information")));
        LinearLayout first=row();controls.addView(first,new LinearLayout.LayoutParams(-1,px(38)));choice(first,"greeting",tr("问候语","Greeting"));choice(first,"time",tr("时间","Time"));choice(first,"app-title",tr("应用标题","App title"));
        gap(controls,8);LinearLayout second=row();controls.addView(second,new LinearLayout.LayoutParams(-1,px(38)));choice(second,"date",tr("日期","Date"));choice(second,"weather",tr("天气","Weather"));
        greeting=setting(tr("首页问候语","Home greeting"),"desktop","greeting");greeting.compact();greeting.tail("arrow");
        greetingEditor=new GreetingEditorPanel(this,desktopStore,fixedGreeting?tr("欢迎回家","Welcome home"):"",this::refreshControls);greeting.setOnClickListener(v->greetingEditor.show());LinearLayout greetingGroup=group();greetingGroup.addView(greeting,new LinearLayout.LayoutParams(-1,px(44)));LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,-2);gp.topMargin=px(8);controls.addView(greetingGroup,gp);
        gap(controls,16);controls.addView(desktopLabel(tr("全部应用","All apps")));LinearLayout allApps=row();controls.addView(allApps,new LinearLayout.LayoutParams(-1,px(38)));choice(allApps,"all-apps-names",tr("应用名称","App names"));
        gap(controls,16);controls.addView(desktopLabel(tr("快捷按钮","Quick buttons")));LinearLayout shortcuts=row();controls.addView(shortcuts,new LinearLayout.LayoutParams(-1,px(38)));choice(shortcuts,"input",tr("信号源","Inputs"));choice(shortcuts,"network",tr("网络","Network"));choice(shortcuts,"settings",tr("设置","Settings"));
        LinearLayout right=column();right.setGravity(Gravity.TOP);right.setPadding(0,px(1),0,0);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-1,1);rp.leftMargin=px(24);desktop.addView(right,rp);
        right.addView(desktopLabel(tr("首页实时预览","Live Home preview")));gap(right,8);
        preview=new HomePreviewView(this,chinese,fixedGreeting);preview.setBackground(surface(0xff0e0f13,0xff0e0f13,0x26ffffff,10));UiCompat.clipOutline(preview);right.addView(preview,new LinearLayout.LayoutParams(-1,-2));
        wireDesktop();refreshControls();
    }
    private Button layoutChoice(LinearLayout parent,String label,String name,float weight){Button b=button(label,()->{desktopStore.layout(name);refreshControls();});b.setFocusableInTouchMode(true);b.setId(View.generateViewId());b.setTag("layout-"+name);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(14));b.setPadding(px(7),0,px(7),0);ControlStyle.choice(b,unit,9,0x10ffffff,0x22ffffff);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,weight);if(parent.getChildCount()>0)p.leftMargin=px(8);parent.addView(b,p);return b;}
    private void choice(LinearLayout parent,String key,String title){DisplayChoice b=new DisplayChoice(title);b.setTag("display-"+key);b.setOnClickListener(v->{boolean value=!b.isChecked();switch(key){case "greeting":desktopStore.greetingEnabled(value);break;case "time":desktopStore.showTime(value);break;case "date":desktopStore.showDate(value);break;case "app-title":desktopStore.showAppTitle(value);break;case "all-apps-names":desktopStore.showAllAppsNames(value);break;case "weather":weatherStore.enabled(value);if(weather!=null)weather.settingsChanged();break;case "input":desktopStore.showInput(value);break;case "network":desktopStore.showNetwork(value);break;case "settings":desktopStore.showSettings(value);break;}refreshControls();});choices.put(key,b);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(parent.getChildCount()>0)p.leftMargin=px(8);parent.addView(b,p);}
    private void wireDesktop(){
        View[][] rows={{classic,gallery},{choices.get("greeting"),choices.get("time"),choices.get("app-title")},{choices.get("date"),choices.get("weather")},{greeting},{choices.get("all-apps-names")},{choices.get("input"),choices.get("network"),choices.get("settings")}};
        for(int r=0;r<rows.length;r++)for(int c=0;c<rows[r].length;c++){View v=rows[r][c];v.setNextFocusLeftId(rows[r][Math.max(0,c-1)].getId());v.setNextFocusRightId(rows[r][Math.min(rows[r].length-1,c+1)].getId());v.setNextFocusUpId(r==0?tabs.get("desktop").getId():rows[r-1][Math.min(c,rows[r-1].length-1)].getId());v.setNextFocusDownId(r==rows.length-1?v.getId():rows[r+1][Math.min(c,rows[r+1].length-1)].getId());}
    }
    private final class DisplayChoice extends Button implements Checkable {
        private boolean checked;
        DisplayChoice(String title){super(DesktopSettingsActivity.this);setText(title);setAllCaps(false);setSingleLine(true);setEllipsize(android.text.TextUtils.TruncateAt.END);UiCompat.letterSpacing(this,0);setTextColor(TEXT);setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(14));setGravity(Gravity.CENTER);setIncludeFontPadding(false);setMinWidth(0);setMinimumWidth(0);setMinHeight(0);setMinimumHeight(0);setPadding(px(6),0,px(6),0);setId(View.generateViewId());setFocusable(true);setFocusableInTouchMode(true);ControlStyle.choice(this,unit,9,0x10ffffff,0x22ffffff);if(android.os.Build.VERSION.SDK_INT>=21)setStateListAnimator(null);setCompoundDrawablePadding(px(6));addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int pad=Math.max(px(6),(int)((getWidth()-px(21)-getPaint().measureText(getText().toString()))/2));setPadding(pad,0,pad,0);});}
        public boolean isChecked(){return checked;}
        public void toggle(){setChecked(!checked);}
        public void setChecked(boolean value){checked=value;setSelected(value);Drawable mark=new Drawable(){final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);public void draw(Canvas c){Rect b=getBounds();p.setColor(checked?0xff535860:0xff777d88);c.drawRoundRect(new RectF(b),px(2),px(2),p);if(checked){p.setColor(0xfff5f6f8);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(px(1.6f));Path path=new Path();path.moveTo(b.left+b.width()*.2f,b.top+b.height()*.5f);path.lineTo(b.left+b.width()*.43f,b.top+b.height()*.73f);path.lineTo(b.left+b.width()*.82f,b.top+b.height()*.25f);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}};mark.setBounds(0,0,px(13),px(13));setCompoundDrawables(mark,null,null,null);}
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(CheckBox.class.getName());info.setCheckable(true);info.setChecked(checked);}
    }
    private void refreshControls(){
        classic.setSelected("classic".equals(desktopStore.layout()));gallery.setSelected("gallery".equals(desktopStore.layout()));
        boolean[] checked={desktopStore.greetingEnabled(),desktopStore.showTime(),desktopStore.showAppTitle(),desktopStore.showDate(),weatherStore.enabled(),desktopStore.showAllAppsNames(),desktopStore.showInput(),desktopStore.showNetwork(),desktopStore.showSettings()};int i=0;for(DisplayChoice b:choices.values())b.setChecked(checked[i++]);
        String text=desktopStore.greetingText(fixedGreeting?tr("欢迎回家","Welcome home"):"");greeting.value(!desktopStore.greetingEnabled()?(desktopStore.avatarEnabled()?tr("仅头像","Avatar only"):tr("已关闭","Off")):text.isEmpty()?tr("跟随时间","By time"):text);
        if(preview!=null&&"desktop".equals(selectedSection))preview.refresh();
    }
    private View activeView(){return "desktop".equals(selectedSection)?desktopScroll:"weather".equals(selectedSection)?weather.view:guard.view;}
    private void remember(){View focus=getCurrentFocus();if(focus!=null&&focus.getTag() instanceof String&&!String.valueOf(focus.getTag()).startsWith("tab-"))focusTags.put(selectedSection,(String)focus.getTag());View v=activeView();scrollPositions.put(selectedSection,v.getScrollY());}
    private void select(String name,boolean tabFocus){
        if(!tabs.containsKey(name))name="desktop";
        if(weather!=null)weather.pause();if(guard!=null)guard.pause();if(preview!=null)preview.pause();
        if(resumed&&desktop!=null&&(!"weather".equals(selectedSection)||weather!=null)&&(!"guard".equals(selectedSection)||guard!=null))remember();
        selectedSection=name;
        if("weather".equals(name)&&weather==null){weather=new DesktopWeatherPanel(this);content.addView(weather.view,new FrameLayout.LayoutParams(-1,-1));wireRows(weather.rows(),tabs.get("weather"));}
        if("guard".equals(name)&&guard==null){guard=new HomeGuardPanel(this,tabs.get("guard"),false);content.addView(guard.view,new FrameLayout.LayoutParams(-1,-1));}
        desktop.setVisibility("desktop".equals(name)?View.VISIBLE:View.GONE);if(weather!=null)weather.view.setVisibility("weather".equals(name)?View.VISIBLE:View.GONE);if(guard!=null)guard.view.setVisibility("guard".equals(name)?View.VISIBLE:View.GONE);
        for(String key:tabs.keySet())tabs.get(key).setSelected(key.equals(name));
        if(resumed){if("desktop".equals(name))preview.resume();else if("weather".equals(name))weather.resume();else guard.resume();}
        refreshControls();final int scroll=scrollPositions.containsKey(name)?scrollPositions.get(name):0;final View selectedView=activeView();selectedView.post(()->selectedView.scrollTo(0,scroll));
        if(tabFocus)tabs.get(name).requestFocus();back.setNextFocusDownId(tabs.get(name).getId());
    }
    private void focusContent(){String tag=focusTags.get(selectedSection);View target=tag==null?null:content.findViewWithTag(tag);if(target==null||!target.isShown()||!target.isEnabled())target="desktop".equals(selectedSection)?classic:"weather".equals(selectedSection)?weather.firstRow():guard.firstRow();target.requestFocus();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);select(intent.getStringExtra(SECTION),true);}
    void wireRows(DesktopSettingsRow[] rows,View tab){for(int i=0;i<rows.length;i++){DesktopSettingsRow r=rows[i];r.setNextFocusUpId(i==0?tab.getId():rows[i-1].getId());r.setNextFocusDownId(rows[Math.min(rows.length-1,i+1)].getId());r.setNextFocusLeftId(r.getId());r.setNextFocusRightId(r.getId());}}
    TextView section(String name){TextView label=muted(name,13);label.setLayoutParams(new LinearLayout.LayoutParams(-1,px(30)));return label;}
    LinearLayout group(){return listGroup();}
    DesktopSettingsRow setting(String label,String icon,String tag){DesktopSettingsRow r=new DesktopSettingsRow(this,label,icon,false);r.setTag(tag);return r;}
    void addSetting(LinearLayout group,DesktopSettingsRow row){group.addView(row,new LinearLayout.LayoutParams(-1,px(60)));}
    @Override public void onSharedPreferenceChanged(SharedPreferences prefs,String key){main.removeCallbacks(changed);main.post(changed);}
    @Override protected void onResume(){super.onResume();resumed=true;getSharedPreferences("desktop-settings",0).registerOnSharedPreferenceChangeListener(this);getSharedPreferences("weather",0).registerOnSharedPreferenceChangeListener(this);if("desktop".equals(selectedSection))preview.resume();else if("weather".equals(selectedSection))weather.resume();else guard.resume();refreshControls();}
    @Override protected void onPause(){remember();resumed=false;getSharedPreferences("desktop-settings",0).unregisterOnSharedPreferenceChangeListener(this);getSharedPreferences("weather",0).unregisterOnSharedPreferenceChangeListener(this);main.removeCallbacks(changed);preview.pause();if(weather!=null)weather.pause();if(guard!=null)guard.pause();greetingEditor.pause();super.onPause();}
    @Override protected void onDestroy(){preview.destroy();if(guard!=null)guard.destroy();if(weather!=null)weather.destroy();greetingEditor.destroy();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle saved){super.onSaveInstanceState(saved);remember();saved.putString("desktop-section",selectedSection);View focus=getCurrentFocus();if(focus!=null&&focus.getTag() instanceof String)saved.putString("focus",(String)focus.getTag());for(String name:tabs.keySet()){saved.putString("focus-"+name,focusTags.get(name));saved.putInt("scroll-"+name,scrollPositions.containsKey(name)?scrollPositions.get(name):0);}if(guard!=null)guard.saveState(saved);greetingEditor.saveState(saved);}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(weather!=null)weather.permissionResult(code);}
}
