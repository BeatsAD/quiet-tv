package home.quiet.tv;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** One window, header and ADB session; tabs retain their content instead of launching Activities. */
public class AppManagementActivity extends ManagementUi {
    private final ManagementPanel[] panels=new ManagementPanel[SettingsPages.COUNT];
    private final Bundle[] savedPanels=new Bundle[SettingsPages.COUNT];
    private FrameLayout panelHost;
    private ManagementPanel current;
    private final View[] contentFocus=new View[SettingsPages.COUNT];
    private int page=-1;
    private boolean resumed,chinese=true,fixedGreeting;
    @Override boolean localManagement(){return false;}
    String tr(String zh,String en){return chinese?zh:en;}
    boolean chinese(){return chinese;}
    boolean fixedGreeting(){return fixedGreeting;}
    int initialPage(){return SettingsPages.valid(getIntent().getIntExtra("management-page",0));}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        try(java.io.InputStream input=getAssets().open("launcher.json");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            byte[] bytes=new byte[4096];int count;while((count=input.read(bytes))!=-1)out.write(bytes,0,count);
            org.json.JSONObject config=new org.json.JSONObject(out.toString("UTF-8"));chinese=!"en".equals(config.optString("language","zh"));fixedGreeting="home".equals(config.optString("greeting"));
        }catch(java.io.IOException|org.json.JSONException error){throw new IllegalStateException("Invalid bundled launcher configuration",error);}
        ((TextView)header.getChildAt(1)).setText(tr("桌面设置","Desktop Settings"));
        toolbar(SettingsPages.valid(saved==null?initialPage():saved.getInt("management-page",initialPage())));
        panelHost=new FrameLayout(this);root.addView(panelHost,new LinearLayout.LayoutParams(-1,0,1));
        if(saved!=null)for(int i=0;i<panels.length;i++)savedPanels[i]=saved.getBundle("panel-"+i);
        selectManagementPage(SettingsPages.valid(saved==null?initialPage():saved.getInt("management-page",initialPage())));
    }
    void weatherSettingsChanged(){if(panels[SettingsPages.WEATHER]!=null)((WeatherSettingsPanel)panels[SettingsPages.WEATHER]).settingsChanged();}
    void wireRows(DesktopSettingsRow[] rows,View tab){for(int i=0;i<rows.length;i++){DesktopSettingsRow row=rows[i];row.setNextFocusUpId(i==0?tab.getId():rows[i-1].getId());row.setNextFocusDownId(rows[Math.min(rows.length-1,i+1)].getId());row.setNextFocusLeftId(row.getId());row.setNextFocusRightId(row.getId());}}
    TextView section(String name){TextView label=muted(name,13);label.setLayoutParams(new LinearLayout.LayoutParams(-1,px(38)));return label;}
    LinearLayout group(){return listGroup();}
    DesktopSettingsRow setting(String label,String icon,String tag){DesktopSettingsRow row=new DesktopSettingsRow(this,label,icon,false);row.setTag(tag);return row;}
    void addSetting(LinearLayout group,DesktopSettingsRow row){group.addView(row,new LinearLayout.LayoutParams(-1,px(60)));}
    void attachPanel(View view){panelHost.addView(view,new FrameLayout.LayoutParams(-1,-1));}
    @Override void selectManagementPage(int next){
        if(next<0||next>=panels.length)return;
        final int target=SettingsPages.valid(next);
        if(target==page){managementTab().requestFocus();return;}
        if(current!=null){current.leaveManagement(()->showPage(target));return;}showPage(target);
    }
    private void showPage(int next){
        if(isFinishing()||UiCompat.destroyed(this))return;
        if(current!=null){rememberContentFocus();if(resumed){current.onPause();current.onStop();}current.pageRoot.setVisibility(View.GONE);}
        page=next;managementPageSelected(next);
        if(panels[next]==null){
            panels[next]=next==0?new AppListPanel(this):next==3?new ChildModePanel(this):next==4?new VersionUpdatePanel(this):next==5?new DiagnosticLogPanel(this):next==6?new FileTransferPanel(this):next==SettingsPages.DESKTOP?new DesktopDisplayPanel(this):new WeatherSettingsPanel(this);
            current=panels[next];current.onCreate(savedPanels[next]);
        }else current=panels[next];
        current.pageRoot.setVisibility(View.VISIBLE);managementTab().requestFocus();
        if(resumed)current.onResume();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);for(ManagementPanel panel:panels)if(panel!=null)panel.activityResult(request,result,data);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);for(ManagementPanel panel:panels)if(panel!=null)panel.permissionResult(request,results);}
    @Override protected void onResume(){super.onResume();resumed=true;if(current!=null)current.onResume();}
    @Override protected void onPause(){resumed=false;rememberContentFocus();if(current!=null)current.onPause();super.onPause();}
    @Override protected void onStop(){if(current!=null)current.onStop();super.onStop();}
    @Override protected void onDestroy(){for(ManagementPanel panel:panels)if(panel!=null)panel.onDestroy();super.onDestroy();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);selectManagementPage(initialPage());}
    @Override protected void onSaveInstanceState(Bundle saved){
        super.onSaveInstanceState(saved);saved.putInt("management-page",page);
        for(int i=0;i<panels.length;i++){Bundle state=savedPanels[i];if(panels[i]!=null){state=new Bundle();panels[i].onSaveInstanceState(state);}if(state!=null)saved.putBundle("panel-"+i,state);}
    }
    private void rememberContentFocus(){if(current!=null&&current.pageRoot.hasFocus())contentFocus[page]=getCurrentFocus();}
    @Override void focusManagementContent(){if(current!=null){View focus=contentFocus[page];if(focus==null||!focus.isShown()||!focus.isEnabled()||!focus.requestFocus())current.focusManagementContent();}}
    @Override void managementConnected(){for(ManagementPanel panel:panels)if(panel!=null)panel.managementConnected();}
    @Override void connectionPanel(){if(current==null)super.connectionPanel();else current.connectionPanel();}
    void defaultConnectionPanel(){super.connectionPanel();}
    @Override void managementMore(){if(current==null)super.managementMore();else current.managementMore();}
    void defaultManagementMore(){super.managementMore();}
    @Override void managementMoreOptions(LinearLayout menu){if(current==null)super.managementMoreOptions(menu);else current.managementMoreOptions(menu);}
    void defaultManagementMoreOptions(LinearLayout menu){super.managementMoreOptions(menu);}
    @Override boolean useSettingListStyle(){return current==null||current.useSettingListStyle();}
    @Override public void handleBack(){if(current==null)super.handleBack();else current.handleBack();}
    @android.annotation.SuppressLint("RestrictedApi")
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        return KeyFeedback.dispatch(getWindow().getDecorView(),event,()->{
            boolean handled=current!=null&&current.dispatchKeyEvent(event);
            if(handled)DiagnosticLog.navigationKey("quiet_tv_management",event);
            return handled||super.dispatchKeyEvent(event);
        });
    }
}
