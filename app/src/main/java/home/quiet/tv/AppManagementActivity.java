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
    private int page=-1,pageRequest;
    private int pendingTab=-1;
    private int pendingTabSteps;
    private boolean enterPendingTab;
    private AutoDiagnosticOperation tabOperation;
    private Runnable tabDraw,tabFrames;
    private void cancelTabEvidence(){
        if(tabDraw!=null){tabDraw.run();tabDraw=null;}
        if(tabFrames!=null){tabFrames.run();tabFrames=null;}
        if(tabOperation!=null){tabOperation.cancel();tabOperation=null;}
    }
    private void watchTabDraw(View controls,AutoDiagnosticOperation operation){
        tabDraw=UiCompat.onDraw(controls,()->{
            if(tabOperation!=operation||!controls.isShown()||!hasWindowFocus())return;
            controls.post(()->{
                if(tabOperation!=operation||!resumed||!controls.isShown()||!hasWindowFocus())return;
                if(tabDraw!=null){tabDraw.run();tabDraw=null;}
                operation.stage("FOCUSED_READY_DRAW");
                operation.finish();tabOperation=null;
            });
        });
    }
    private final Runnable applyPendingTab=()->{
        int target=pendingTab;
        int steps=pendingTabSteps;
        boolean enter=enterPendingTab;
        pendingTab=-1;pendingTabSteps=0;enterPendingTab=false;
        if(target>=0&&this.resumed&&hasWindowFocus()&&managementTabFocused()&&!isFinishing()&&!UiCompat.destroyed(this))navigate(target,enter,steps);
    };
    private FileToolsSheet files;
    private BootToolsSheet boot;
    private DesktopBackupSheet backups;
    private DesktopGuardSheet guard;
    private AdbManagementSheet adbManagement;
    private SourceToolsSheet sources;
    private HelpToolsSheet help;
    private boolean resumed,chinese=true,fixedGreeting;
    @Override boolean localManagement(){return false;}
    @Override boolean unifiedSettings(){return true;}
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
        panelHost=new FrameLayout(this){@Override protected void dispatchDraw(android.graphics.Canvas canvas){int save=canvas.save();canvas.clipRect(-getLeft(),0,root.getWidth()-getLeft(),getHeight()+root.getPaddingBottom());super.dispatchDraw(canvas);canvas.restoreToCount(save);}};panelHost.setPadding(0,px(18),0,0);panelHost.setClipChildren(false);panelHost.setClipToPadding(false);root.addView(panelHost,new LinearLayout.LayoutParams(-1,0,1));
        files=new FileToolsSheet(this,()->{if(current instanceof ToolsSettingsPanel)((ToolsSettingsPanel)current).filesToolReturned();});
        boot=new BootToolsSheet(this,()->{if(current instanceof WeatherSettingsPanel)((WeatherSettingsPanel)current).startupToolReturned();});
        backups=new DesktopBackupSheet(this,()->{if(current instanceof WeatherSettingsPanel)((WeatherSettingsPanel)current).backupToolReturned();});
        guard=new DesktopGuardSheet(this,()->{if(current instanceof WeatherSettingsPanel)((WeatherSettingsPanel)current).guardToolReturned();});
        adbManagement=new AdbManagementSheet(this,()->{if(current instanceof WeatherSettingsPanel)((WeatherSettingsPanel)current).adbToolReturned();});
        sources=new SourceToolsSheet(this,()->{if(current instanceof WeatherSettingsPanel)((WeatherSettingsPanel)current).sourceToolReturned();});
        help=new HelpToolsSheet(this,()->{if(current instanceof SettingsHubPanel)((SettingsHubPanel)current).toolReturned(help.entry());});
        if(saved!=null)for(int i=0;i<panels.length;i++)savedPanels[i]=saved.getBundle("panel-"+i);
        selectManagementPage(SettingsPages.valid(saved==null?initialPage():saved.getInt("management-page",initialPage())));
        if(saved==null||saved.getInt("management-page")==SettingsPages.ICONS)openAppEntry(getIntent());
        if(saved!=null){files.restorePhonePermission(saved);files.restore(saved);}
        if(saved!=null)boot.restore(saved);
        if(saved!=null)backups.restore(saved);
        if(saved!=null)guard.restore(saved);
        if(saved!=null)adbManagement.restore(saved);
        if(saved!=null)sources.restore(saved);
        if(saved!=null)help.restore(saved);
    }
    private BootAdSheet bootAds;
    void bootAdTool(){if(bootAds==null)bootAds=new BootAdSheet(this);bootAds.show();}
    void startupTool(){boot.show();}
    void backupTool(){backups.show();}
    void guardTool(){guard.show();}
    void adbTool(){adbManagement.show();}
    void sourceTool(){sources.show();}
    void helpTool(String kind){help.show(kind);}
    void startupApp(String pkg){selectManagementPage(0);((AppListPanel)panels[0]).editStartupApp(pkg);}
    void weatherSettingsChanged(){for(int route:new int[]{SettingsPages.WEATHER,SettingsPages.GENERAL})if(panels[route]!=null)((WeatherSettingsPanel)panels[route]).settingsChanged();}
    void wireRows(DesktopSettingsRow[] rows,View tab){for(int i=0;i<rows.length;i++){DesktopSettingsRow row=rows[i];row.setNextFocusUpId(i==0?tab.getId():rows[i-1].getId());row.setNextFocusDownId(rows[Math.min(rows.length-1,i+1)].getId());row.setNextFocusLeftId(row.getId());row.setNextFocusRightId(row.getId());}}
    TextView section(String name){TextView label=muted(name,13);label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,12.96f*unit);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(19.44f));p.bottomMargin=px(6);label.setLayoutParams(p);return label;}
    LinearLayout group(){return listGroup();}
    DesktopSettingsRow setting(String label,String icon,String tag){DesktopSettingsRow row=new DesktopSettingsRow(this,label,icon,false);row.hideIcon();row.setTag(tag);return row;}
    void addSetting(LinearLayout group,DesktopSettingsRow row){group.addView(row,new LinearLayout.LayoutParams(-1,px(row.checkedControl()?42:44.535f)));}
    void addSettingsScroll(LinearLayout parent,ScrollView scroll){
        scroll.setClipToPadding(false);scroll.setClipChildren(false);scroll.setPadding(px(12.36f),px(11.88f),px(12.36f),px(14.76f));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-1);p.setMargins(px(-9),px(-9),px(-9),px(-9));parent.setClipChildren(false);parent.setClipToPadding(false);parent.addView(scroll,p);
    }
    void attachPanel(View view){view.setVisibility(View.GONE);panelHost.addView(view,new FrameLayout.LayoutParams(-1,-1));}
    @Override void selectManagementPage(int next){cancelPendingTab();if(next==SettingsPages.FILES||next==SettingsPages.STARTUP||next==SettingsPages.ABOUT||next==SettingsPages.DIAGNOSTICS){int request=++pageRequest;Runnable open=()->{if(request!=pageRequest||isFinishing()||UiCompat.destroyed(this))return;int destination=next==SettingsPages.FILES?SettingsPages.TOOLS:next==SettingsPages.STARTUP?SettingsPages.GENERAL:SettingsPages.HELP;if(page!=destination)showPage(destination);if(next==SettingsPages.FILES)openFileTool();else if(next==SettingsPages.STARTUP)sourceTool();else helpTool(next==SettingsPages.ABOUT?"about":"logs");};if(current!=null)current.leaveManagement(open);else open.run();return;}navigate(next,false);}
    private void openFileTool(){
        String job=getIntent().getStringExtra(PhoneSettingsFiles.PERMISSION_JOB);
        if(job==null){files.show();return;}
        getIntent().removeExtra(PhoneSettingsFiles.PERMISSION_JOB);
        panelHost.post(()->{if(!isFinishing()&&!UiCompat.destroyed(this))files.authorizeFromPhone(job);});
    }
    void componentSettings(String id){cancelPendingTab();++pageRequest;if("weather".equals(id)){if(page!=SettingsPages.DESKTOP)showPage(SettingsPages.DESKTOP);((DesktopDisplayPanel)current).weatherComponent();}else{showPage(SettingsPages.TOOLS);OptionalComponentsUi.show(this,id);}}
    // Keep panel creation out of key dispatch, and resolve a burst against its latest requested tab.
    @Override void shiftManagementTab(int focusedTab,int direction){
        KeyFeedback.ownMove(panelHost);
        int next=adjacentManagementTab(pendingTab<0?focusedTab:pendingTab,direction);
        boolean posted=pendingTab>=0;
        pendingTab=next;pendingTabSteps+=direction;enterPendingTab=false;++pageRequest;
        if(next==SettingsPages.section(page)){cancelPendingTab();return;}
        if(!posted)androidx.core.view.ViewCompat.postOnAnimation(panelHost,applyPendingTab);
    }
    @Override void enterManagementTab(){
        if(pendingTab<0)super.enterManagementTab();
        else{
            KeyFeedback.ownMove(panelHost);
            enterPendingTab=true;
        }
    }
    private void cancelPendingTab(){
        if(pendingTab<0)return;
        pendingTab=-1;pendingTabSteps=0;enterPendingTab=false;
        panelHost.removeCallbacks(applyPendingTab);
    }
    private void navigate(int next,boolean restoreFocus){navigate(next,restoreFocus,0);}
    private void navigate(int next,boolean restoreFocus,int tabSteps){
        if(next<0||next>=panels.length)return;
        int target=SettingsPages.valid(next);
        int request=++pageRequest;
        if(target==page){managementTab().requestFocus();return;}
        Runnable apply=()->{
            if(request!=pageRequest||isFinishing()||UiCompat.destroyed(this))return;
            showPage(target,tabSteps);
            if(tabSteps!=0)KeyFeedback.movedTo(managementTab());
            if(restoreFocus)focusManagementContent();
        };
        if(current!=null)current.leaveManagement(apply);else apply.run();
    }
    private void showPage(int next){showPage(next,0);}
    private void showPage(int next,int tabSteps){
        if(isFinishing()||UiCompat.destroyed(this))return;
        long started=android.os.SystemClock.uptimeMillis();
        cancelTabEvidence();
        if(current!=null&&resumed&&hasWindowFocus()){
            tabOperation=AutoDiagnosticOperation.begin("settings_tab",350);
            tabFrames=DiagnosticFrameWindow.watch(getWindow(),tabOperation);
        }
        OperationFeedback.get(this).hide();
        ManagementPanel previous=current;int oldPage=page;boolean first=panels[next]==null;
        if(previous!=null){rememberContentFocus();if(resumed){previous.onPause();previous.onStop();}}
        page=next;
        managementPageSelected(next);
        if(panels[next]==null){
            panels[next]=next==SettingsPages.QUICK?new QuickControlEditorPanel(this):next==SettingsPages.APPS?new AppListPanel(this):next==SettingsPages.CHILD?new ChildModePanel(this):next==SettingsPages.TOOLS?new ToolsSettingsPanel(this):next==SettingsPages.ABOUT?new VersionUpdatePanel(this):next==SettingsPages.FILES?new FileTransferPanel(this):next==SettingsPages.DESKTOP?new DesktopDisplayPanel(this):next==SettingsPages.STARTUP?new StartupDestinationPanel(this):next==SettingsPages.WEATHER||next==SettingsPages.GENERAL?new WeatherSettingsPanel(this,next==SettingsPages.GENERAL):new SettingsHubPanel(this,next);
            current=panels[next];current.onCreate(savedPanels[next]);
        }else current=panels[next];
        current.pageRoot.setVisibility(View.VISIBLE);
        if(previous!=null){UiMotion.settle(previous.pageRoot);previous.pageRoot.setVisibility(View.GONE);UiMotion.panel(current.pageRoot,px(tabPosition(next)>tabPosition(oldPage)?10:-10));}
        View tab=managementTab();
        if(tabSteps!=0&&tab.getParent() instanceof SettingsTabBar){
            // Multiple queued arrows can skip tabs within the row; wrapping still lands directly.
            ((SettingsTabBar)tab.getParent()).requestKeyFocus(tab,tabPosition(next)-tabPosition(oldPage)==tabSteps);
        }else tab.requestFocus();
        if(resumed)current.onResume();
        if(previous==null)watchSettingsEntry(current.pageRoot);
        else if(tabOperation!=null){
            tabOperation.stage("PANEL_SETUP_COMPLETE");
            watchTabDraw(current.pageRoot,tabOperation);
        }
        long elapsed=android.os.SystemClock.uptimeMillis()-started;
        if(elapsed>=100)DiagnosticLog.info("settings.tab.slow","from="+oldPage+" to="+next+" first="+first+" elapsedMs="+elapsed+" evidence=panel_setup_not_presentation");
    }
    private int tabPosition(int target){for(int i=0;i<SettingsPages.ORDER.length;i++)if(SettingsPages.ORDER[i]==SettingsPages.section(target))return i;return 0;}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==UpdateInstaller.REQUEST_INSTALL){UpdateRepository.get(this).fileInstallReturned(result,data);return;}
        if(files!=null)files.activityResult(request,result,data);
        if(backups!=null)backups.activityResult(request,result,data);
        for(ManagementPanel panel:panels)if(panel!=null)panel.activityResult(request,result,data);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(files!=null)files.permissionResult(request);if(backups!=null)backups.permissionResult(request);if(help!=null)help.permissionResult(request);for(ManagementPanel panel:panels)if(panel!=null)panel.permissionResult(request,results);}
    @Override protected void onResume(){super.onResume();resumed=true;if(current!=null)current.onResume();if(files!=null)files.resume();if(boot!=null)boot.resume();if(backups!=null)backups.resume();if(guard!=null)guard.resume();if(adbManagement!=null)adbManagement.resume();if(sources!=null)sources.resume();if(help!=null)help.resume();openPhoneUpdate();}
    private void openPhoneUpdate(){
        String hash=getIntent().getStringExtra(PhoneSettingsUpdates.INSTALL_TARGET);
        if(hash==null||help==null)return;
        getIntent().removeExtra(PhoneSettingsUpdates.INSTALL_TARGET);
        help.installFromPhone(hash);
    }
    @Override protected void onPause(){cancelTabEvidence();cancelPendingTab();++pageRequest;resumed=false;rememberContentFocus();if(current!=null){UiMotion.settle(current.pageRoot);current.onPause();}if(files!=null)files.pause();if(boot!=null)boot.pause();if(backups!=null)backups.pause();if(guard!=null)guard.pause();if(adbManagement!=null)adbManagement.pause();if(sources!=null)sources.pause();if(help!=null)help.pause();super.onPause();}
    @Override protected void onStop(){if(current!=null)current.onStop();super.onStop();}
    @Override protected void onDestroy(){cancelTabEvidence();cancelPendingTab();if(files!=null)files.destroy();if(boot!=null)boot.destroy();if(bootAds!=null)bootAds.destroy();if(backups!=null)backups.destroy();if(guard!=null)guard.destroy();if(adbManagement!=null)adbManagement.destroy();if(sources!=null)sources.destroy();if(help!=null)help.destroy();for(ManagementPanel panel:panels)if(panel!=null)panel.onDestroy();super.onDestroy();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);selectManagementPage(initialPage());openAppEntry(intent);}
    private void openAppEntry(Intent intent){
        String pkg=intent.getStringExtra("selected-package");
        if(!(current instanceof AppListPanel)||pkg==null||pkg.isEmpty())return;
        boolean picker=intent.getBooleanExtra("open-app-icon",false)||intent.getIntExtra("management-page",SettingsPages.APPS)==SettingsPages.ICONS;
        ((AppListPanel)current).openAppEntry(pkg,picker);
    }
    @Override protected void onSaveInstanceState(Bundle saved){
        super.onSaveInstanceState(saved);saved.putInt("management-page",page);
        files.save(saved);
        files.savePhonePermission(saved);
        boot.save(saved);
        backups.save(saved);
        guard.save(saved);
        adbManagement.save(saved);
        sources.save(saved);
        help.save(saved);
        for(int i=0;i<panels.length;i++){Bundle state=savedPanels[i];if(panels[i]!=null){state=new Bundle();panels[i].onSaveInstanceState(state);}if(state!=null)saved.putBundle("panel-"+i,state);}
    }
    private void rememberContentFocus(){if(current!=null&&current.pageRoot.hasFocus())contentFocus[page]=getCurrentFocus();}
    @Override void focusManagementContent(){if(current!=null){View focus=contentFocus[page];if(focus==null||!focus.isShown()||!focus.isEnabled()||!focus.requestFocus())current.focusManagementContent();}}
    @Override void managementConnected(){for(ManagementPanel panel:panels)if(panel!=null)panel.managementConnected();}
    @Override void connectionPanel(){if(current==null)super.connectionPanel();else current.connectionPanel();}
    void defaultConnectionPanel(){guardTool();}
    @Override void managementMore(){if(current==null)super.managementMore();else current.managementMore();}
    void defaultManagementMore(){super.managementMore();}
    @Override void managementMoreOptions(LinearLayout menu){if(current==null)super.managementMoreOptions(menu);else current.managementMoreOptions(menu);}
    void defaultManagementMoreOptions(LinearLayout menu){super.managementMoreOptions(menu);}
    @Override boolean useSettingListStyle(){return current==null||current.useSettingListStyle();}
    @Override public void handleBack(){
        cancelPendingTab();++pageRequest;
        if(current==null){super.handleBack();return;}
        UiMotion.settle(current.pageRoot);
        if(managementTabFocused()&&page!=SettingsPages.section(page))navigate(SettingsPages.section(page),true);
        else current.handleBack();
    }
    @android.annotation.SuppressLint("RestrictedApi")
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        return InputDiagnostics.dispatch(this,event,"management_panel",()->KeyFeedback.dispatch(getWindow().getDecorView(),event,()->{
            boolean handled=current!=null&&current.dispatchKeyEvent(event);
            if(handled)DiagnosticLog.navigationKey("quiet_tv_management",event);
            return handled||super.dispatchKeyEvent(event);
        }));
    }
}
