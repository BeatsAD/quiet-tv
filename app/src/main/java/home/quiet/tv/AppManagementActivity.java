package home.quiet.tv;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** One window, header and ADB session; tabs retain their content instead of launching Activities. */
public class AppManagementActivity extends ManagementUi {
    private final ManagementPanel[] panels=new ManagementPanel[7];
    private final Bundle[] savedPanels=new Bundle[7];
    private FrameLayout panelHost;
    private ManagementPanel current;
    private int page=-1;
    private boolean resumed;
    @Override boolean localManagement(){return false;}
    int initialPage(){return getIntent().getIntExtra("management-page",0);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);toolbar(saved==null?initialPage():saved.getInt("management-page",initialPage()));
        panelHost=new FrameLayout(this);root.addView(panelHost,new LinearLayout.LayoutParams(-1,0,1));
        if(saved!=null)for(int i=0;i<panels.length;i++)savedPanels[i]=saved.getBundle("panel-"+i);
        selectManagementPage(saved==null?initialPage():saved.getInt("management-page",initialPage()));
    }
    void attachPanel(View view){panelHost.addView(view,new FrameLayout.LayoutParams(-1,-1));}
    @Override void selectManagementPage(int next){
        if(next<0||next>=panels.length)return;
        if(next==page){managementTab().requestFocus();return;}
        if(current!=null){current.leaveManagement(()->showPage(next));return;}showPage(next);
    }
    private void showPage(int next){
        if(isFinishing()||UiCompat.destroyed(this))return;
        if(current!=null){if(resumed){current.onPause();current.onStop();}current.pageRoot.setVisibility(View.GONE);}
        page=next;managementPageSelected(next);
        if(panels[next]==null){
            panels[next]=next==0?new AppListPanel(this):next==1?new DockEditorPanel(this):next==2?new HomeSortPanel(this):next==3?new ChildModePanel(this):next==4?new VersionUpdatePanel(this):next==5?new DiagnosticLogPanel(this):new FileTransferPanel(this);
            current=panels[next];current.onCreate(savedPanels[next]);
        }else current=panels[next];
        current.pageRoot.setVisibility(View.VISIBLE);managementTab().requestFocus();
        if(resumed)current.onResume();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(panels[FileTransferPanel.PAGE]!=null)panels[FileTransferPanel.PAGE].activityResult(request,result,data);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(panels[FileTransferPanel.PAGE]!=null)panels[FileTransferPanel.PAGE].permissionResult(request,results);}
    @Override protected void onResume(){super.onResume();resumed=true;if(current!=null)current.onResume();}
    @Override protected void onPause(){resumed=false;if(current!=null)current.onPause();super.onPause();}
    @Override protected void onStop(){if(current!=null)current.onStop();super.onStop();}
    @Override protected void onDestroy(){for(ManagementPanel panel:panels)if(panel!=null)panel.onDestroy();super.onDestroy();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);selectManagementPage(initialPage());}
    @Override protected void onSaveInstanceState(Bundle saved){
        super.onSaveInstanceState(saved);saved.putInt("management-page",page);
        for(int i=0;i<panels.length;i++){Bundle state=savedPanels[i];if(panels[i]!=null){state=new Bundle();panels[i].onSaveInstanceState(state);}if(state!=null)saved.putBundle("panel-"+i,state);}
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
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        boolean handled=current!=null&&current.dispatchKeyEvent(event);
        if(handled)DiagnosticLog.navigationKey("quiet_tv_management",event);
        return handled||super.dispatchKeyEvent(event);
    }
}
