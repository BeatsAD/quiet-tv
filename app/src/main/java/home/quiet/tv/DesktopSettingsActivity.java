package home.quiet.tv;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/** Desktop and weather share a native shell and one activity back stack. */
public final class DesktopSettingsActivity extends ManagementUi {
    static final String SECTION="section";
    private boolean chinese,resumed,weatherSelected;
    private DesktopSettingsStore desktopStore;
    private WeatherStore weatherStore;
    private DesktopWeatherPanel weather;
    private FrameLayout content;
    private LinearLayout desktop;
    private TextView title;
    private DesktopSettingsRow desktopTab,weatherTab,time,tools,enabled;
    private final List<DesktopSettingsRow> tabs=new ArrayList<>();
    String tr(String zh,String en){return chinese?zh:en;}
    boolean chinese(){return chinese;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        try(java.io.InputStream input=getAssets().open("launcher.json");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            byte[] bytes=new byte[4096];int count;while((count=input.read(bytes))!=-1)out.write(bytes,0,count);
            chinese=!"en".equals(new JSONObject(out.toString("UTF-8")).optString("language","zh"));
        }catch(java.io.IOException|org.json.JSONException e){throw new IllegalStateException("Invalid bundled launcher configuration",e);}
        desktopStore=new DesktopSettingsStore(this);weatherStore=new WeatherStore(this);
        Button back=(Button)header.getChildAt(0);back.setContentDescription(tr("返回","Back"));back.setId(View.generateViewId());back.setTag("back");
        title=(TextView)header.getChildAt(1);title.setText(tr("桌面设置","Desktop Settings"));
        LinearLayout layout=row();layout.setGravity(Gravity.TOP);LinearLayout.LayoutParams body=new LinearLayout.LayoutParams(-1,0,1);body.topMargin=px(34);root.addView(layout,body);
        LinearLayout rail=column();layout.addView(rail,new LinearLayout.LayoutParams(px(160),-1));
        desktopTab=tab(rail,tr("桌面显示","Desktop"),"desktop","nav-desktop",()->showSection(false));
        weatherTab=tab(rail,tr("天气","Weather"),"weather","nav-weather",()->showSection(true));
        if(!BuildConfig.LEGACY_19){
            gap(rail,12);View divider=new View(this);divider.setBackgroundColor(0x16ffffff);LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(-1,px(1));dp.setMargins(px(14),0,px(14),px(12));rail.addView(divider,dp);
            tab(rail,tr("桌面守护","Guard"),"shield","nav-guard",()->startActivity(new Intent(this,HomeGuardActivity.class)));
        }
        content=new FrameLayout(this);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-1,1);cp.leftMargin=px(28);layout.addView(content,cp);
        desktop=column();LinearLayout group=group();desktop.addView(group);
        time=setting(tr("时间与日期","Time and date"),"clock","time");time.checked(desktopStore.showTime());time.setOnClickListener(v->{desktopStore.showTime(!desktopStore.showTime());time.checked(desktopStore.showTime());});addSetting(group,time);
        tools=setting(tr("右侧快捷按钮","Quick buttons"),"tools","tools");tools.checked(desktopStore.showButtons());tools.setOnClickListener(v->{desktopStore.showButtons(!desktopStore.showButtons());tools.checked(desktopStore.showButtons());});addSetting(group,tools);
        enabled=setting(tr("天气","Weather"),"weather","enabled");enabled.checked(weatherStore.enabled());enabled.setOnClickListener(v->{weatherStore.enabled(!weatherStore.enabled());enabled.checked(weatherStore.enabled());if(weather!=null)weather.settingsChanged();});addSetting(group,enabled);
        content.addView(desktop,new FrameLayout.LayoutParams(-1,-1));wireRows(new DesktopSettingsRow[]{time,tools,enabled},desktopTab);
        footer(tr("方向键移动  确定选择  返回上一级","Move with arrows  Select with OK  Back to return"));
        for(int i=0;i<tabs.size();i++){DesktopSettingsRow tab=tabs.get(i);tab.setNextFocusUpId(i==0?back.getId():tabs.get(i-1).getId());tab.setNextFocusDownId(i+1<tabs.size()?tabs.get(i+1).getId():tab.getId());}
        weatherSelected=state!=null?state.getBoolean("weather-section"):"weather".equals(getIntent().getStringExtra(SECTION));
        final String restoreFocus=state==null?null:state.getString("focus");showSection(weatherSelected);
        root.post(()->{View target=restoreFocus==null?null:root.findViewWithTag(restoreFocus);if(target==null||!target.isShown())target=weatherSelected?weatherTab:desktopTab;target.requestFocus();});
    }
    private DesktopSettingsRow tab(LinearLayout rail,String text,String icon,String tag,Runnable action){
        DesktopSettingsRow b=new DesktopSettingsRow(this,text,icon,true);b.setTag(tag);b.setOnClickListener(v->{v.requestFocus();action.run();});
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(50));p.bottomMargin=px(8);rail.addView(b,p);tabs.add(b);
        b.setOnKeyListener((v,key,event)->{if(key!=KeyEvent.KEYCODE_DPAD_RIGHT)return false;if(event.getAction()==KeyEvent.ACTION_DOWN){if(v==desktopTab||v==weatherTab)showSection(v==weatherTab);firstRow().requestFocus();}return true;});return b;
    }
    void showSection(boolean selected){
        if(weather!=null)weather.pause();weatherSelected=selected;
        if(selected&&weather==null){weather=new DesktopWeatherPanel(this);content.addView(weather.view,new FrameLayout.LayoutParams(-1,-1));wireRows(weather.rows(),weatherTab);}
        desktop.setVisibility(selected?View.GONE:View.VISIBLE);if(weather!=null)weather.view.setVisibility(selected?View.VISIBLE:View.GONE);
        desktopTab.setSelected(!selected);weatherTab.setSelected(selected);
        if(resumed&&selected)weather.resume();
    }
    private DesktopSettingsRow firstRow(){return weatherSelected?weather.firstRow():time;}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if("weather".equals(intent.getStringExtra(SECTION))){showSection(true);weatherTab.requestFocus();}}
    void wireRows(DesktopSettingsRow[] rows,View tab){
        for(int i=0;i<rows.length;i++){
            DesktopSettingsRow r=rows[i];r.setNextFocusUpId(rows[Math.max(0,i-1)].getId());r.setNextFocusDownId(rows[Math.min(rows.length-1,i+1)].getId());r.setNextFocusRightId(r.getId());r.setNextFocusLeftId(tab.getId());
        }
    }
    TextView section(String name){TextView label=muted(name,13);label.setLayoutParams(new LinearLayout.LayoutParams(-1,px(30)));return label;}
    LinearLayout group(){LinearLayout box=column();box.setPadding(px(4),px(4),px(4),px(4));box.setBackground(surface(0x12ffffff,0x06ffffff,0x1cffffff,16));UiCompat.clipOutline(box);return box;}
    DesktopSettingsRow setting(String label,String icon,String tag){DesktopSettingsRow r=new DesktopSettingsRow(this,label,icon,false);r.setTag(tag);return r;}
    void addSetting(LinearLayout group,DesktopSettingsRow row){
        if(group.getChildCount()>0){View line=new View(this);line.setBackgroundColor(0x16ffffff);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(1));p.setMargins(px(4),0,px(4),0);group.addView(line,p);}
        group.addView(row,new LinearLayout.LayoutParams(-1,px(60)));
    }
    @Override protected void onResume(){super.onResume();resumed=true;time.checked(desktopStore.showTime());tools.checked(desktopStore.showButtons());enabled.checked(weatherStore.enabled());if(weatherSelected)weather.resume();}
    @Override protected void onPause(){resumed=false;if(weather!=null)weather.pause();super.onPause();}
    @Override protected void onDestroy(){if(weather!=null)weather.destroy();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putBoolean("weather-section",weatherSelected);View focus=getCurrentFocus();if(focus!=null&&focus.getTag() instanceof String)state.putString("focus",(String)focus.getTag());}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(weather!=null)weather.permissionResult(code);}
}
