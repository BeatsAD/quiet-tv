package home.quiet.tv;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** One window, header and ADB session; tabs retain their content instead of launching Activities. */
public class AppManagementActivity extends ManagementUi {
    private final ManagementPanel[] panels=new ManagementPanel[4];
    private final Bundle[] savedPanels=new Bundle[4];
    private FrameLayout panelHost;
    private ManagementPanel current;
    private int page=-1;
    private boolean resumed;
    int initialPage(){return 0;}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);toolbar(saved==null?initialPage():saved.getInt("management-page",initialPage()));
        panelHost=new FrameLayout(this);root.addView(panelHost,new LinearLayout.LayoutParams(-1,0,1));
        if(saved!=null)for(int i=0;i<4;i++)savedPanels[i]=saved.getBundle("panel-"+i);
        selectManagementPage(saved==null?initialPage():saved.getInt("management-page",initialPage()));
    }
    void attachPanel(View view){panelHost.addView(view,new FrameLayout.LayoutParams(-1,-1));}
    @Override void selectManagementPage(int next){
        if(next<0||next>=panels.length)return;
        if(next==page){managementTab().requestFocus();return;}
        if(current!=null){current.leaveManagement(()->showPage(next));return;}showPage(next);
    }
    private void showPage(int next){
        if(isFinishing()||isDestroyed())return;
        if(current!=null){if(resumed){current.onPause();current.onStop();}current.pageRoot.setVisibility(View.GONE);}
        page=next;managementPageSelected(next);
        if(panels[next]==null){
            panels[next]=next==0?new AppListPanel(this):next==1?new DockEditorPanel(this):next==2?new HomeSortPanel(this):new ChildModePanel(this);
            current=panels[next];current.onCreate(savedPanels[next]);
        }else current=panels[next];
        current.pageRoot.setVisibility(View.VISIBLE);managementTab().requestFocus();
        if(resumed)current.onResume();
    }
    @Override protected void onResume(){super.onResume();resumed=true;if(current!=null)current.onResume();}
    @Override protected void onPause(){resumed=false;if(current!=null)current.onPause();super.onPause();}
    @Override protected void onStop(){if(current!=null)current.onStop();super.onStop();}
    @Override protected void onDestroy(){for(ManagementPanel panel:panels)if(panel!=null)panel.onDestroy();super.onDestroy();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);selectManagementPage(initialPage());}
    @Override protected void onSaveInstanceState(Bundle saved){
        super.onSaveInstanceState(saved);saved.putInt("management-page",page);
        for(int i=0;i<4;i++){Bundle state=savedPanels[i];if(panels[i]!=null){state=new Bundle();panels[i].onSaveInstanceState(state);}if(state!=null)saved.putBundle("panel-"+i,state);}
    }
    @Override void focusManagementContent(){if(current!=null)current.focusManagementContent();}
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
    @Override public boolean dispatchKeyEvent(KeyEvent event){return current!=null&&current.dispatchKeyEvent(event)||super.dispatchKeyEvent(event);}
}
