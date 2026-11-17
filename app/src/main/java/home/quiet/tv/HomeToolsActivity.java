package home.quiet.tv;

import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

/** A translucent HOME surface owns focus; closing it never revokes an approved phone. */
public final class HomeToolsActivity extends ManagementUi implements HomeQuickControlView.Host {
    static final int REQUEST=7203;
    private HomeQuickControlView controls;
    private HomeButtonEditor buttons;
    private MemoPanel memos;
    private FileToolsSheet files;
    private BootToolsSheet boot;
    private SourceToolsSheet sources;
    private Bitmap backdrop;
    private HomeHeaderView quickHeader;
    private boolean headerFocus=true,quickNavigation;
    private int headerIndex;
    private boolean closing;
    private String mode="control",origin="";
    private final Runnable phoneChanged=this::refreshQuickHeader;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    @Override boolean unifiedSettings(){return true;}
    @Override boolean loadWallpaper(){return false;}
    @Override boolean managementChrome(){return false;}
    @Override boolean chinese(){return getIntent().getBooleanExtra("chinese",true);}
    @Override public void glassChanged(){super.glassChanged();if(!GlassStyle.get(this).rendering()){backdrop=null;getIntent().removeExtra("backdrop");}if(stage!=null)stage.invalidate();}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);stage.setBackgroundColor(Color.TRANSPARENT);
        backdrop=GlassStyle.get(this).rendering()?getIntent().getParcelableExtra("backdrop"):null;if(backdrop==null)getIntent().removeExtra("backdrop");mode=saved==null?getIntent().getStringExtra("mode"):saved.getString("home-tool-mode");if(mode==null)mode="control";
        origin=saved==null?getIntent().getStringExtra("home-button"):saved.getString("home-tool-origin","");if(origin==null)origin="";
        quickNavigation=saved==null?"control".equals(mode)&&origin.isEmpty():saved.getBoolean("quick-navigation");
        headerFocus=saved==null?false:saved.getBoolean("quick-header-focus",true);
        controls=new HomeQuickControlView(this,unit,chinese(),this,this::execute);stage.addView(controls,controls.position());controls.restore(saved);
        if(quickNavigation)createQuickHeader(saved);
        buttons=new HomeButtonEditor(this,()->{},()->{if(!closing&&"buttons".equals(mode))finishButtons();});
        memos=new MemoPanel(this,()->controls.memoCount(memos.unfinished()),()->{if(!closing){if(returnToInline())return;mode="control";controls.setVisibility(View.VISIBLE);controls.reload();controls.focus("memoShortcut");}});
        files=new FileToolsSheet(this,()->{if(!closing){if(returnToInline())return;if("transfer".equals(getIntent().getStringExtra("home-button"))){setResult(RESULT_OK,new Intent().putExtra("focus-button","transfer"));finish();}else{mode="control";controls.setVisibility(View.VISIBLE);controls.reload("transfer");controls.focus("transfer");}}});
        boot=new BootToolsSheet(this,()->{if(!closing){if(returnToInline())return;if("boot".equals(getIntent().getStringExtra("home-button"))){setResult(RESULT_OK,new Intent().putExtra("focus-button","boot"));finish();}else{mode="control";controls.setVisibility(View.VISIBLE);controls.reload("boot");controls.focus("boot");}}});
        sources=new SourceToolsSheet(this,()->{if(!closing){if(returnToInline())return;String direct=getIntent().getStringExtra("home-button");if("source".equals(direct)||"watchTv".equals(direct)){setResult(RESULT_OK,"source".equals(direct)?new Intent().putExtra("focus-button","source"):new Intent());finish();}else{mode="control";controls.setVisibility(View.VISIBLE);controls.reload("source");controls.focus("source");}}});
        if(saved!=null&&saved.containsKey("source-tools")){controls.setVisibility(View.GONE);mode="source";sources.restore(saved);}
        else if(saved!=null&&saved.containsKey("boot-tools")){controls.setVisibility(View.GONE);mode="boot";boot.restore(saved);}
        else if(saved!=null&&saved.containsKey("file-tools")){controls.setVisibility(View.GONE);mode="files";files.restore(saved);}
        else if(saved!=null&&saved.getBoolean("home-button-open")){controls.setVisibility(View.GONE);buttons.restore(saved);}
        else if(saved!=null&&saved.getBoolean("memos-open")){controls.setVisibility(View.GONE);memos.restoreState(saved);}
        else if("buttons".equals(mode)){controls.setVisibility(View.GONE);buttons.show(origin);}
        else{controls.reload();memos.refreshCount();if(getIntent().getBooleanExtra("inline-quick-origin",false))controls.setVisibility(View.GONE);if(saved==null){String first=origin;controls.post(()->{if(!first.isEmpty())execute(first);else if(!quickNavigation||!headerFocus)controls.focus(getIntent().getStringExtra("quick-focus"));});}}
    }
    private boolean returnToInline(){if(!getIntent().getBooleanExtra("inline-quick-origin",false))return false;finish();return true;}
    private void createQuickHeader(Bundle saved){
        quickHeader=new HomeHeaderView(this,unit,chinese(),Typeface.DEFAULT);
        quickHeader.getChildAt(0).setVisibility(View.GONE);
        quickHeader.buttonBackdrop((canvas,paint)->drawSheetBackdrop(canvas),stage);
        stage.addView(quickHeader,new FrameLayout.LayoutParams(-1,-1));
        int control=quickHeader.buttonIds.indexOf("control");if(control<0){stage.removeView(quickHeader);quickHeader=null;quickNavigation=false;return;}headerIndex=saved==null?control:saved.getInt("quick-header-index",control);
        headerIndex=Math.max(0,Math.min(headerIndex,quickHeader.buttonIds.size()-1));
        for(int i=0;i<quickHeader.pills.size();i++){final int index=i;TextView pill=quickHeader.pills.get(i);pill.setFocusable(true);pill.setFocusableInTouchMode(true);pill.setOnClickListener(v->{headerIndex=index;activateHeader();});pill.setOnFocusChangeListener((v,on)->{if(on){headerFocus=true;headerIndex=index;syncQuickHeader();}});if(i<quickHeader.buttonIds.size()&&"network".equals(quickHeader.buttonIds.get(i)))quickHeader.setPillIcon(pill,currentNetworkIcon());}
        if(headerFocus&&!"control".equals(quickHeader.buttonIds.get(headerIndex)))controls.setVisibility(View.GONE);
        HomeActivity.coverToolHeader(true);syncQuickHeader();if(headerFocus)quickHeader.post(()->focusHeader(headerIndex));
    }
    @Override public boolean quickPanelFocusAllowed(){return !quickNavigation||!headerFocus;}
    @Override public void quickContentReady(View content){contentReady("quick_controls",content);}
    @Override public SettingsSheetBackdrop quickBackdrop(View panel){return new SettingsSheetBackdrop(this,panel);}
    private int currentNetworkIcon(){android.net.ConnectivityManager manager=(android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);android.net.NetworkInfo info=manager==null?null:manager.getActiveNetworkInfo();return info!=null&&info.isConnected()?(info.getType()==android.net.ConnectivityManager.TYPE_WIFI?R.drawable.ic_wifi:R.drawable.ic_ethernet):R.drawable.ic_wifi_off;}
    private void refreshQuickHeader(){if(quickHeader==null)return;String previous=headerIndex<quickHeader.buttonIds.size()?quickHeader.buttonIds.get(headerIndex):"control";quickHeader.apply();quickHeader.getChildAt(0).setVisibility(View.GONE);int next=quickHeader.buttonIds.indexOf(previous);headerIndex=next>=0?next:Math.max(0,quickHeader.buttonIds.indexOf("control"));for(int i=0;i<quickHeader.buttonIds.size();i++)if("network".equals(quickHeader.buttonIds.get(i)))quickHeader.setPillIcon(quickHeader.pills.get(i),currentNetworkIcon());syncQuickHeader();if(headerFocus&&hasWindowFocus())focusHeader(headerIndex);}
    @Override public void quickPanelFocused(){if(headerFocus){headerFocus=false;syncQuickHeader();}}
    private void syncQuickHeader(){
        if(quickHeader==null)return;boolean shown="control".equals(mode)&&controls.getVisibility()==View.VISIBLE;
        for(int i=0;i<quickHeader.buttonIds.size();i++){boolean open=shown&&"control".equals(quickHeader.buttonIds.get(i));View pill=quickHeader.pills.get(i);pill.setSelected(open);pill.setActivated(headerFocus&&i==headerIndex);}
        quickHeader.controlVisibility();
    }
    private void focusHeader(int index){headerFocus=true;headerIndex=index;controls.cancelAdjustments();syncQuickHeader();quickHeader.pills.get(index).requestFocusFromTouch();}
    private void closeQuick(){setResult(RESULT_OK,new Intent().putExtra("focus-button","control"));finish();}
    private void closeToDock(){controls.cancelAdjustments();setResult(RESULT_OK,new Intent().putExtra("focus-dock",true));finish();}
    private void activateHeader(){String id=quickHeader.buttonIds.get(headerIndex);if("settings".equals(id))ManagementUi.performanceAction("header_settings");if("control".equals(id)){if(controls.getVisibility()==View.VISIBLE){closeQuick();return;}controls.setVisibility(View.VISIBLE);syncQuickHeader();}else if("settings".equals(id)){controls.cancelAdjustments();setResult(RESULT_OK,new Intent().putExtra("focus-button",id));startActivity(new Intent(this,DesktopSettingsActivity.class).putExtra("backdrop",backdrop).putExtra("home-network-icon",getIntent().getIntExtra("home-network-icon",R.drawable.ic_wifi_off)));finish();}else{setResult(RESULT_OK,new Intent().putExtra("focus-button",id).putExtra("home-action",id));finish();}}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(quickHeader!=null&&"control".equals(mode)){
            int key=event.getKeyCode();boolean confirm=key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER;
            if(quickHeader.hasFocus()&&(confirm||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN)){
                if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){
                    if(confirm)activateHeader();
                    else if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){int next=Math.max(0,Math.min(quickHeader.buttonIds.size()-1,headerIndex+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1)));controls.setVisibility("control".equals(quickHeader.buttonIds.get(next))?View.VISIBLE:View.GONE);focusHeader(next);}
                    else if(key==KeyEvent.KEYCODE_DPAD_DOWN){if("control".equals(quickHeader.buttonIds.get(headerIndex))){headerFocus=false;controls.setVisibility(View.VISIBLE);syncQuickHeader();controls.focus(null);}else closeToDock();}
                }return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    @Override void drawSheetBackdrop(Canvas canvas){
        if(backdrop!=null)canvas.drawBitmap(backdrop,null,new RectF(0,0,getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels),new Paint(3));else canvas.drawColor(0xff000000);
    }
    private void finishButtons(){setResult(RESULT_OK,new Intent().putExtra("focus-button",buttons.focusId()));finish();}
    private void settings(int page){settings(page,"");}
    private void settings(int page,String selected){settings(page,selected,false);}
    private void settings(int page,String selected,boolean wallpaper){origin=wallpaper?"wallpaper":selected.isEmpty()?controls.focusId():selected;controls.cancelAdjustments();controls.setVisibility(View.GONE);startActivityForResult(new Intent(this,AppManagementActivity.class).putExtra("management-page",page).putExtra("quick-selected",selected).putExtra("desktop-background-entry",wallpaper).putExtra("desktop-clock-entry","clock".equals(origin)).putExtra("backdrop",backdrop),7204);}
    private void execute(String id){
        origin=id;
        if(QuickControlStore.SETTINGS.equals(id)){controls.cancelAdjustments();controls.setVisibility(View.GONE);startActivityForResult(new Intent(this,DesktopSettingsActivity.class).putExtra("backdrop",backdrop).putExtra("home-network-icon",getIntent().getIntExtra("home-network-icon",R.drawable.ic_wifi_off)),7204);return;}
        if(QuickControlStore.range(id)){controls.beginRange(id);return;}
        if("child".equals(id)||"apps".equals(id)||"clock".equals(id)||"weather".equals(id)){settings("child".equals(id)?SettingsPages.CHILD:"apps".equals(id)?SettingsPages.APPS:"weather".equals(id)?SettingsPages.WEATHER:SettingsPages.DESKTOP,id);return;}
        if("memoShortcut".equals(id)){controls.cancelAdjustments();controls.setVisibility(View.GONE);mode="memos";memos.show();return;}
        if("transfer".equals(id)){controls.cancelAdjustments();controls.setVisibility(View.GONE);mode="files";files.show();return;}
        if("wallpaper".equals(id)){settings(SettingsPages.DESKTOP,"",true);return;}
        if("boot".equals(id)){controls.cancelAdjustments();controls.setVisibility(View.GONE);mode="boot";boot.show();return;}
        if("source".equals(id)||"watchTv".equals(id)){controls.cancelAdjustments();controls.setVisibility(View.GONE);mode="source";sources.show();return;}
        if("network".equals(id)||"bluetooth".equals(id)||"systemSettings".equals(id)){controls.cancelAdjustments();new SystemSettingsRouter(this).open("network".equals(id)?Settings.ACTION_WIFI_SETTINGS:"bluetooth".equals(id)?Settings.ACTION_BLUETOOTH_SETTINGS:Settings.ACTION_SETTINGS);return;}
        if("original".equals(id)){controls.cancelAdjustments();new HomeProxy(this).original(this);return;}
        if("keySounds".equals(id)||"mute".equals(id)){
            try{if("keySounds".equals(id)){DesktopSettingsStore store=new DesktopSettingsStore(this);store.savePhoneValue("key-sounds",!store.keySounds());}
                else{WallpaperStore store=WallpaperStore.get(this);WallpaperCatalog state=store.snapshot();if(state.weather||state.rotating){feedback(state.weather?"天气联动保持静音":"轮换背景保持静音");return;}store.sound(!state.sound);}
                PhoneSettingsSession.get(this).configurationChanged();controls.refresh();
            }catch(IOException error){error(error.getMessage());}return;
        }
        if("screensaver".equals(id)){
            controls.cancelAdjustments();setResult(RESULT_OK,new Intent().putExtra("home-action",id).putExtra("quick-origin",true));finish();
        }
    }
    @Override public QuickControlStore.Snapshot cachedShortcuts(){return new QuickControlStore(new File(getFilesDir(),"quick-controls")).cached();}
    @Override public void readShortcuts(HomeQuickControlView.Value<QuickControlStore.Snapshot> ready,HomeQuickControlView.Value<String> failed){
        worker.execute(()->{try{QuickControlStore.Snapshot snapshot=new QuickControlStore(new File(getFilesDir(),"quick-controls")).read();runOnUiThread(()->{if(!closing)ready.accept(snapshot);});}catch(IOException error){runOnUiThread(()->{if(!closing)failed.accept(error.getMessage());});}});
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(files!=null)files.activityResult(request,result,data);if(request==7204){headerFocus=false;controls.setVisibility(View.VISIBLE);syncQuickHeader();controls.reload(origin);controls.focus(origin);}}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(files!=null)files.permissionResult(request);}
    @Override protected void onResume(){super.onResume();PhoneSettingsSession.get(this).observeConfiguration(phoneChanged);refreshQuickHeader();if(controls!=null)controls.resume();if(files!=null)files.resume();if(boot!=null)boot.resume();if(sources!=null)sources.resume();}
    @Override protected void onPause(){PhoneSettingsSession.get(this).forgetConfiguration(phoneChanged);if(controls!=null)controls.pause();if(files!=null)files.pause();if(boot!=null)boot.pause();if(sources!=null)sources.pause();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putBoolean("quick-navigation",quickNavigation);state.putBoolean("quick-header-focus",headerFocus);state.putInt("quick-header-index",headerIndex);state.putString("home-tool-mode",mode);state.putString("home-tool-origin",origin);buttons.saveState(state);memos.saveState(state);controls.save(state);files.save(state);boot.save(state);sources.save(state);}
    @Override public void handleBack(){if(controls!=null&&controls.cancelAdjustments())return;closeQuick();}
    @Override protected void onDestroy(){closing=true;if(quickNavigation)HomeActivity.coverToolHeader(false);if(controls!=null)controls.destroy();if(buttons!=null)buttons.destroy();if(memos!=null)memos.destroy();if(files!=null)files.destroy();if(boot!=null)boot.destroy();if(sources!=null)sources.destroy();worker.shutdown();super.onDestroy();}
}
