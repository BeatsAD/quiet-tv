package home.quiet.tv;

import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.Formatter;
import android.text.style.TextAppearanceSpan;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.util.*;
import java.util.concurrent.*;

final class AppListPanel extends ManagementPanel {
    @Override boolean useSettingListStyle(){return false;}
    private static final String[] FILTERS={"全部","用户应用","预装应用","隐藏应用","已禁用","可恢复"};
    private static final int HIDDEN_FILTER=3;
    private final ManagementAdb session;
    private final AppListCache cache;
    private long cacheVersion=-1;
    private Button refreshButton,batchButton;
    private TextView empty;
    private String shape;
    private IconLibraryPanel picker;
    private IconLibraryBatchSheet batch;
    private Parcelable restoreScroll;
    private String contentFocus="";
    private final Runnable cacheChanged=()->{dirty=true;if(this.active)refresh();};
    private final ViewTreeObserver.OnGlobalFocusChangeListener focusChanged=(before,after)->{if(after!=null&&inside(after)&&after.getTag() instanceof String)contentFocus=(String)after.getTag();};
    private final ExecutorService worker;
    private final ThreadPoolExecutor artworkWorker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(48),new ThreadPoolExecutor.DiscardOldestPolicy());
    private final android.util.LruCache<String,Bitmap> artworkCache=new android.util.LruCache<String,Bitmap>(4*1024*1024){
        @Override protected int sizeOf(String key,Bitmap image){return image.getByteCount();}
    };
    private static final int[][] STATUS_STATES={{android.R.attr.state_focused,android.R.attr.state_window_focused},{}};
    private static final ColorStateList ENABLED_STATUS=new ColorStateList(STATUS_STATES,new int[]{0xff146c2e,UiPalette.STATUS_SUCCESS});
    private static final ColorStateList DISABLED_STATUS=new ColorStateList(STATUS_STATES,new int[]{0xffb3261e,UiPalette.STATUS_ERROR});
    private Artwork detailArtwork;
    private final ArrayList<AppRepository.App> all=new ArrayList<>(),filtered=new ArrayList<>();
    private AppRepository repository;
    private AdbController adb;
    private TextView managementConnection;
    private boolean connecting;
    private final ManagementAdb.Listener connectionChanged=this::managementConnectionChanged;
    private void managementConnectionChanged(){
        if(!active)return;updateManagementConnection();if(cache.stale(adb)){dirty=true;refresh();}
    }
    private AppOperations operations;
    private final ArrayList<Button> filterButtons=new ArrayList<>();
    private AppRenameSheet renameSheet;
    private String namesSignature="";
    private final SharedPreferences.OnSharedPreferenceChangeListener namesListener=(prefs,key)->{if(AppNameStore.KEY.equals(key))ui(this::namesChanged);};
    private void namesChanged(){namesChanged(false);}
    private void namesChanged(boolean keepDetails){if(updateNames()&&active){rememberActionFocus();applyFilter(keepDetails);}}
    private boolean updateNames(){
        AppNameStore names=new AppNameStore(this);String signature=names.signature();if(signature.equals(namesSignature))return false;namesSignature=signature;
        for(int i=0;i<all.size();i++){AppRepository.App app=all.get(i).copy();app.label=names.display(app.pkg,app.originalName);all.set(i,app);}
        java.text.Collator collator=java.text.Collator.getInstance(Locale.SIMPLIFIED_CHINESE);
        Collections.sort(all,(a,b)->collator.compare(a.label,b.label));return true;
    }
    private void renamed(String pkg){
        namesChanged();
        if(active&&selected.equals(pkg))success("名称已更新");
    }
    private LinearLayout details;
    private RecyclerView list;
    private AppAdapter adapter;
    private View firstAction;
    private Sheet appSheet,detailSheet;
    private Button appSheetClose;
    private String restoreAction="",restorePackage="",requestedPackage="";
    private String selected="";
    private boolean requestedPicker;
    private View confirmView;
    private int confirmKey;
    private long confirmDownTime;
    private boolean confirmHeld;
    private String pendingUninstall="";
    private static final int UNINSTALL_RESULT=7412;
    private int filter;
    private final Runnable phoneChanged=this::phoneChanged;
    private String phoneKey="";
    private long coverRevision=-1;
    private String legacyArtworkRevision="";
    private void phoneChanged(){
        long revision=AppCoverStore.get(activity).revision();
        String currentShape=new DesktopSettingsStore(activity).iconShape();
        String legacyRevision=AppArtwork.previewRevision(activity);
        if(revision!=coverRevision||!currentShape.equals(shape)||!legacyRevision.equals(legacyArtworkRevision)){
            boolean shapeChanged=!currentShape.equals(shape);
            coverRevision=revision;shape=currentShape;legacyArtworkRevision=legacyRevision;
            if(active){
                refreshArtwork(null,shapeChanged);
                if(shapeChanged&&appSheet!=null&&detailSheet==null){rememberActionFocus();renderDetails();restoreActionFocus();}
            }
        }
        String key;try{org.json.JSONObject signature=PhoneSettingsPreview.get(activity).signature();signature.remove("covers");key=signature.toString()+new DockStore(activity).load()+new BootAppStore(activity).target();}
        catch(java.io.IOException unavailable){return;}
        if(key.equals(phoneKey))return;
        phoneKey=key;if(active){rememberActionFocus();updateNames();applyFilter();}
    }
    private boolean dirty=true;
    private boolean busy,reading,refreshAfterConnection;
    private volatile boolean active;
    private volatile int refreshGeneration;
    private final BroadcastReceiver packagesChanged=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction()) && !intent.getBooleanExtra(Intent.EXTRA_REPLACING,false) && intent.getData()!=null){String pkg=intent.getData().getSchemeSpecificPart();new DockStore(context).remove(pkg);new HomeOrderStore(context).remove(pkg);new HomeVisibilityStore(context).remove(pkg);worker.execute(()->{try{AppCoverStore.get(context).restore(pkg);}catch(java.io.IOException e){android.util.Log.w("QuietTV","Cover cleanup deferred");}});}
            // The process cache observes package changes even while this panel is closed.
        }
    };
    AppListPanel(AppManagementActivity activity){super(activity);session=ManagementAdb.get(activity);cache=AppListCache.get(activity);worker=session.worker;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);repository=new AppRepository(this);operations=new AppOperations(this);
        adb=session.adb;
        shape=new DesktopSettingsStore(activity).iconShape();renameSheet=new AppRenameSheet(activity);
        LinearLayout body=managementContent(),filters=managementInfo;pageRoot.setClipChildren(false);pageRoot.setClipToPadding(false);body.setClipChildren(false);body.setClipToPadding(false);
        filters.getLayoutParams().height=px(34.56f);((LinearLayout.LayoutParams)body.getLayoutParams()).topMargin=px(12);
        SettingsTabBar filterTabs=activity.settingsTabs();filters.addView(filterTabs,new LinearLayout.LayoutParams(-2,-1));
        for(int i=0;i<FILTERS.length;i++){final int chosen=i;SettingsTabBar.Tab option=activity.settingsTab(filterTabs,FILTERS[i],()->{filterButtons.get(chosen).requestFocusFromTouch();if(filter!=chosen){filter=chosen;applyFilter();}});option.setId(androidx.core.view.ViewCompat.generateViewId());option.setTag("app-filter-"+i);option.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,12*unit);option.setPadding(px(9),0,px(9),px(3));option.underlineSelection();option.setOnFocusChangeListener((v,focused)->{if(focused&&filter!=chosen){filter=chosen;applyFilter();}});
            option.setOnKeyListener((v,key,event)->{
                if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT&&key!=KeyEvent.KEYCODE_DPAD_UP&&key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;
                if(event.getAction()==KeyEvent.ACTION_UP)return true;
                if(key==KeyEvent.KEYCODE_DPAD_DOWN)focusList(false,true);
                else if(key==KeyEvent.KEYCODE_DPAD_UP)managementTab().requestFocus();
                else{
                    int next=Math.max(0,Math.min(filterButtons.size()-1,chosen+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1)));
                    if(next!=chosen){Button target=filterButtons.get(next);target.requestFocusFromTouch();}else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)batchButton.requestFocusFromTouch();
                }
                return true;
            });
            option.setNextFocusUpId(managementTab().getId());filterButtons.add(option);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,px(33));if(i>0)p.leftMargin=px(12);filterTabs.addView(option,p);}
        for(int i=0;i<filterButtons.size();i++){Button b=filterButtons.get(i);if(i>0)b.setNextFocusLeftId(filterButtons.get(i-1).getId());if(i+1<filterButtons.size())b.setNextFocusRightId(filterButtons.get(i+1).getId());}
        filters.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        batchButton=toolbarButton(filters,activity.tr("批量换图标","Replace icons"),"apps-batch",this::openBatch);
        refreshButton=toolbarButton(filters,activity.tr("刷新","Refresh"),"apps-refresh",()->{if(!reading&&!busy){cache.invalidate();dirty=true;refresh();}});
        batchButton.setNextFocusRightId(refreshButton.getId());
        refreshButton.setNextFocusLeftId(batchButton.getId());

        managementConnection=muted("ADB 未连接",12);managementConnection.setTag("apps-connection");managementConnection.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);managementConnection.setFocusable(false);managementConnection.setClickable(false);
        filters.addView(managementConnection,new LinearLayout.LayoutParams(-2,-1));
        updateManagementConnection();
        FrameLayout grid=new FrameLayout(activity);
        body.addView(grid,new LinearLayout.LayoutParams(-1,-1));
        list=new RecyclerView(activity);
        list.setTag("managed-app-grid");
        list.setItemAnimator(null);
        list.setVerticalScrollBarEnabled(false);
        list.setLayoutManager(new GridLayoutManager(this,IconGridTile.COLUMNS));
        adapter=new AppAdapter();
        list.setAdapter(adapter);
        grid.addView(list,new FrameLayout.LayoutParams(-1,-1));
        empty=center("",14);
        empty.setTextColor(UiPalette.SECONDARY);
        grid.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        Bundle previous=state==null?cache.navigation():state;
        if(previous!=null){
            restoreScroll=previous.getParcelable("app-scroll");
            contentFocus=previous.getString("app-focus","");
            phoneKey=previous.getString("app-phone-key","");
            selected=previous.getString("selected","");
            pendingUninstall=previous.getString("app-uninstall-pending","");
            filter=Math.max(0,Math.min(FILTERS.length-1,previous.getInt("filter",0)));
        }
        if(state!=null){
            requestedPackage=state.getString("app-open-package","");
            requestedPicker=state.getBoolean("app-open-picker",false);
        }
        if(!cache.available())updateEmpty();
        if(cache.available()){readCache();namesChanged();applyFilter();}
        pageRoot.getViewTreeObserver().addOnGlobalFocusChangeListener(focusChanged);
        IntentFilter packages=new IntentFilter(Intent.ACTION_PACKAGE_ADDED);packages.addAction(Intent.ACTION_PACKAGE_REMOVED);packages.addAction(Intent.ACTION_PACKAGE_CHANGED);packages.addDataScheme("package");registerReceiver(packagesChanged,packages);
    }
    private Button toolbarButton(LinearLayout toolbar,String title,String tag,Runnable action){
        Button button=button(title,action);
        button.setTag(tag);
        button.setId(androidx.core.view.ViewCompat.generateViewId());
        button.setSingleLine(true);
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,12*unit);
        button.setPadding(px(12),0,px(12),0);
        button.setBackgroundDrawable(new SettingsFocusDrawable(unit,999,UiPalette.CONTROL,false,UiPalette.EDGE));
        button.setNextFocusUpId(managementTab().getId());
        button.setOnKeyListener((v,key,event)->{
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){focusList(false,true);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_LEFT&&button==batchButton){filterButtons.get(filter).requestFocusFromTouch();return true;}
            return key==KeyEvent.KEYCODE_DPAD_RIGHT&&button==refreshButton;
        });
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-2,px(33));
        size.rightMargin=px(9);
        toolbar.addView(button,size);
        return button;
    }
    private void ui(Runnable action){runOnUiThread(()->{if(!isDestroyed() && !isFinishing())action.run();});}
    private void updateManagementConnection(){
        if(managementConnection==null)return;connecting=session.state()==AdbConnectionKeeper.State.CONNECTING;boolean connected=session.state()==AdbConnectionKeeper.State.CONNECTED;
        managementConnection.setText(connecting?"ADB 连接中…":connected?"ADB 已连接":"ADB 未连接");
        managementConnection.setTextColor(connecting?0xffe5b384:connected?0xff64c49a:UiPalette.SECONDARY);
        managementConnection.setContentDescription(managementConnection.getText()+"  "+session.detail());
    }
    @Override void managementConnected(){managementConnectionChanged();}
    @Override void managementMoreOptions(LinearLayout menu){managementMoreOption(menu,"应用自启",this::startupSettings);super.managementMoreOptions(menu);}
    @Override protected void onResume(){
        super.onResume();active=true;cache.addListener(cacheChanged);getSharedPreferences("dock",0).registerOnSharedPreferenceChangeListener(namesListener);
        if(cache.available()&&cacheVersion!=cache.version()){readCache();namesSignature="";namesChanged(true);}
        namesChanged();phoneChanged();refreshArtwork(null,true);PhoneSettingsSession.get(activity).observeConfiguration(phoneChanged);session.addListener(connectionChanged);session.request();updateManagementConnection();dirty=cache.stale(adb);if(dirty)refresh();
        openRequestedApp();
    }
    private boolean inside(View view){for(View v=view;v!=null;v=v.getParent() instanceof View?(View)v.getParent():null)if(v==pageRoot)return true;return false;}
    private void saveNavigation(Bundle state){state.putString("selected",selected);state.putString("app-uninstall-pending",pendingUninstall);state.putInt("filter",filter);state.putString("app-focus",contentFocus);state.putString("app-phone-key",phoneKey);if(list.getLayoutManager()!=null)state.putParcelable("app-scroll",list.getLayoutManager().onSaveInstanceState());}
    @Override protected void onPause(){Bundle state=new Bundle();saveNavigation(state);cache.navigation(state);super.onPause();}
    @Override void focusManagementContent(){View target=pageRoot.findViewWithTag(contentFocus);if(target!=null&&target.isShown()&&target.isEnabled()&&target.requestFocusFromTouch())return;if(contentFocus.startsWith("managed-app-")||contentFocus.startsWith("app-action-"))focusList(true,KeyFeedback.isDirectionPress());else filterButtons.get(filter).requestFocus();}
    @Override protected void onStop(){
        active=false;
        cancelConfirm();
        closeAppDialogs();
        artworkWorker.getQueue().clear();
        cache.removeListener(cacheChanged);
        getSharedPreferences("dock",0).unregisterOnSharedPreferenceChangeListener(namesListener);
        PhoneSettingsSession.get(activity).forgetConfiguration(phoneChanged);
        session.removeListener(connectionChanged);
        refreshAfterConnection=false;
        refreshGeneration++;
        if(reading)busy=false;
        reading=false;
        updateRefresh();
        super.onStop();
    }
    private void closeAppDialogs(){
        renameSheet.close();
        ChildModeAuth.dismiss(activity);
        if(detailSheet!=null)detailSheet.dialog.dismiss();
        if(appSheet!=null)appSheet.dialog.dismiss();
        if(picker!=null)picker.close();
        if(batch!=null){batch.destroy();batch=null;}
    }
    @Override protected void onDestroy(){
        active=false;
        closeAppDialogs();
        artworkWorker.shutdownNow();
        artworkCache.evictAll();
        cache.removeListener(cacheChanged);
        pageRoot.getViewTreeObserver().removeOnGlobalFocusChangeListener(focusChanged);
        PhoneSettingsSession.get(activity).forgetConfiguration(phoneChanged);
        session.removeListener(connectionChanged);
        unregisterReceiver(packagesChanged);
        getSharedPreferences("dock",0).unregisterOnSharedPreferenceChangeListener(namesListener);
        super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle state){
        saveNavigation(state);
        state.putString("app-open-package",picker!=null||appSheet!=null?selected:requestedPackage);
        state.putBoolean("app-open-picker",picker!=null||requestedPicker);
        super.onSaveInstanceState(state);
    }
    private void readCache(){synchronized(cache){all.clear();all.addAll(cache.snapshot());cacheVersion=cache.version();}}
    private void updateRefresh(){refreshButton.setText(reading?"刷新中…":"刷新");}
    private void refresh(){
        if(!active){dirty=true;return;}if(reading)return;if(busy){refreshAfterConnection=true;return;}
        rememberActionFocus();reading=true;busy=!cache.available();final int run=++refreshGeneration;updateRefresh();if(busy)updateEmpty();
        worker.execute(()->{try{cache.load(adb);ui(()->{if(!active||run!=refreshGeneration)return;boolean initial=busy;reading=false;busy=false;dirty=cache.stale(adb);readCache();operations=new AppOperations(this);namesSignature="";namesChanged(!initial);updateRefresh();if(dirty||refreshAfterConnection){refreshAfterConnection=false;if(cache.stale(adb))refresh();}});}catch(Exception e){ui(()->{if(!active||run!=refreshGeneration)return;reading=false;busy=false;dirty=true;updateRefresh();loadFailure(e);});}});
    }
    private void loadFailure(Exception e){if(cache.available()){error("刷新失败，已保留现有列表");restoreActionFocus();}else{updateEmpty();message(e.getMessage()==null?e.toString():e.getMessage());}}
    // Each repository snapshot replaces app metadata as well as list membership/order.
    private void applyFilter(){applyFilter(false);}
    private void applyFilter(boolean keepDetails){
        boolean listFocused=list.hasFocus();
        AppRepository.App previous=current();
        String previousSelection=selected;
        int previousPosition=position(selected);
        ArrayList<AppRepository.App> before=new ArrayList<>(filtered);
        filtered.clear();
        HomeVisibilityStore visibility=new HomeVisibilityStore(this);ChildModeStore children=new ChildModeStore(this);
        for(AppRepository.App app:all){boolean match=filter==0 || filter==1 && !app.system && app.installed || filter==2 && app.system || filter==4 && app.installed && !app.enabled || filter==5 && app.recoverable && !app.installed || filter==HIDDEN_FILTER && app.installed && visibility.hidden(app.pkg) && !children.hidden(app.pkg);if(match)filtered.add(app);}
        for(int i=0;i<filterButtons.size();i++){filterButtons.get(i).setSelected(i==filter);filterButtons.get(i).setTypeface(activity.navigationTypeface(i==filter));}
        if(position(selected)<0)selected=filtered.isEmpty()?"":filtered.get(Math.min(Math.max(0,previousPosition),filtered.size()-1)).pkg;
        DiffUtil.calculateDiff(new DiffUtil.Callback(){public int getOldListSize(){return before.size();}public int getNewListSize(){return filtered.size();}public boolean areItemsTheSame(int old,int next){return before.get(old).pkg.equals(filtered.get(next).pkg);}public boolean areContentsTheSame(int old,int next){return keepDetails&&before.get(old).same(filtered.get(next));}}).dispatchUpdatesTo(adapter);
        if(restoreScroll!=null){list.getLayoutManager().onRestoreInstanceState(restoreScroll);restoreScroll=null;}
        updateEmpty();
        if(appSheet!=null){
            if(!previousSelection.equals(selected)&&detailSheet==null)appSheet.dialog.dismiss();
            else if(detailSheet==null&&(!keepDetails||previous==null||!previous.same(current())))renderDetails();
        }else if(listFocused)focusList();
        restoreActionFocus();
        openRequestedApp();
    }
    private int position(String pkg){
        for(int i=0;i<filtered.size();i++)if(filtered.get(i).pkg.equals(pkg))return i;
        return -1;
    }
    private void updateEmpty(){
        empty.setVisibility(filtered.isEmpty()?View.VISIBLE:View.GONE);
        empty.setText(reading||!cache.available()&&!active?activity.tr("正在读取应用…","Loading apps…"):activity.tr("没有符合条件的应用","No matching apps"));
    }
    void openAppEntry(String pkg,boolean icons){
        requestedPackage=pkg;
        requestedPicker=icons;
        if(filter!=0){filter=0;applyFilter();}
        else openRequestedApp();
    }
    private void openRequestedApp(){
        if(!active||busy||!cache.available()||requestedPackage.isEmpty())return;
        String pkg=requestedPackage;
        requestedPackage="";
        int index=position(pkg);
        if(index<0){error(activity.tr("此应用已不可用，请重新选择","The app is no longer available"));return;}
        selected=pkg;
        list.scrollToPosition(index);
        AppRepository.App app=filtered.get(index);
        if(requestedPicker&&canChangeIcon(app)){openPicker(app);}
        else openAppSheet(app);
    }
    private boolean canChangeIcon(AppRepository.App app){
        return app.installed&&app.enabled&&AppCoverPolicy.allowed(this,app.pkg);
    }
    private void openPicker(AppRepository.App app){
        if(busy||!active||picker!=null||appSheet!=null)return;
        picker=new IconLibraryPanel(activity,app.pkg,app.label,()->refreshArtwork(app.pkg,false),()->{
            picker=null;
            focusList(true);
        });
        picker.show();
    }
    private void openBatch(){
        if(busy||!active)return;
        if(batch!=null)batch.close();
        batch=new IconLibraryBatchSheet(activity,activity,"apple",shape,"icon",()->refreshArtwork(null,false));
        batch.show();
    }
    private void activate(AppRepository.App app,boolean manage){
        if(busy||!active)return;
        selected=app.pkg;
        if(!manage&&canChangeIcon(app))openPicker(app);
        else openAppSheet(app);
    }
    private void openAppSheet(AppRepository.App app){
        if(busy||!active||appSheet!=null||picker!=null)return;
        selected=app.pkg;
        Sheet sheet=new Sheet(activity.tr("管理此应用","Manage this app"));
        appSheet=sheet;
        appSheetClose=sheet.delegate.settingsLayout(activity.tr("关闭","Close"),sheet.dialog::dismiss);
        sheet.delegate.settingsActions(false);
        sheet.delegate.contentSize(345,420);
        sheet.delegate.box.setTag("manage-app-sheet");
        details=sheet.body;
        sheet.dialog.setOnDismissListener(dialog->{
            if(appSheet!=sheet)return;
            appSheet=null;details=null;appSheetClose=null;firstAction=null;
            if(detailArtwork!=null)detailArtwork.cancelDecode();
            detailArtwork=null;
            restoreAction="";restorePackage="";
            focusList(true);
        });
        renderDetails();
        sheet.show(firstAction==null?appSheetClose:firstAction);
    }
    private AppRepository.App current(){for(AppRepository.App app:filtered)if(app.pkg.equals(selected))return app;return null;}
    private void focusList(){focusList(false);}
    private void focusList(boolean restoring){focusList(restoring,false);}
    private void focusList(boolean restoring,boolean directionKey){
        if(directionKey)KeyFeedback.ownMove(list);
        list.post(()->{
            if(!active||appSheet!=null||picker!=null||!restoring&&managementTabFocused())return;
            String requested=selected;
            for(int i=0;i<filtered.size();i++){
                if(!filtered.get(i).pkg.equals(requested))continue;
                int position=i;
                if(list.findViewHolderForAdapterPosition(position)==null)list.scrollToPosition(position);
                list.post(()->{
                    if(!active||appSheet!=null||picker!=null||!restoring&&managementTabFocused()||!requested.equals(selected))return;
                    RecyclerView.ViewHolder holder=list.findViewHolderForAdapterPosition(position);
                    if(holder==null||!("managed-app-"+requested).equals(holder.itemView.getTag()))return;
                    focusListTarget(holder.itemView,directionKey);
                });
                return;
            }
            focusListTarget(filterButtons.get(filter),directionKey);
        });
    }
    private void focusListTarget(View target,boolean directionKey){
        boolean changed=!target.isFocused();
        if(target.requestFocus()&&changed&&directionKey)KeyFeedback.movedTo(target);
    }
    private void renderDetails(){
        if(details==null||appSheet==null)return;
        View previousFocus=details.findFocus();
        Object previousTag=previousFocus==null?null:previousFocus.getTag();
        if(detailArtwork!=null){detailArtwork.cancelDecode();detailArtwork=null;}
        details.removeAllViews();
        firstAction=null;
        AppRepository.App app=current();
        if(app==null)return;

        HomeVisibilityStore visibility=new HomeVisibilityStore(this);
        boolean hidden=visibility.hidden(app.pkg),childHidden=new ChildModeStore(this).hidden(app.pkg);
        LinearLayout header=row();
        details.addView(header,new LinearLayout.LayoutParams(-1,-2));
        detailArtwork=new Artwork();
        int height=px(51),width="rectangle".equals(shape)?Math.round(height*16/9f):height;
        header.addView(detailArtwork,new LinearLayout.LayoutParams(width,height));
        detailArtwork.bind(app);
        LinearLayout info=column();
        LinearLayout.LayoutParams infoSize=new LinearLayout.LayoutParams(0,-2,1);
        infoSize.leftMargin=px(12);
        header.addView(info,infoSize);
        TextView title=label(app.label,16);
        title.setTypeface(activity.navigationTypeface(true));
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(title);
        if(!new AppNameStore(this).alias(app.pkg).isEmpty())info.addView(muted("原名称："+app.originalName,11));
        TextView state=muted("",12);
        state.setText(statusText(app,hidden&&!childHidden));
        info.addView(state);
        info.addView(muted(app.pkg,11));
        String size=app.apkSizeBytes>=0?Formatter.formatShortFileSize(this,app.apkSizeBytes):"大小未知";
        info.addView(muted("版本 "+app.version+"  "+size,11));
        gap(details,10);

        String protectedReason=operations.protection(app);
        if(AppCoverPolicy.ownReadOnly(this,app.pkg)){protectionNote(protectedReason);return;}
        if(busy){details.addView(muted("正在核对实际状态…",12));return;}

        List<AppOperations.Action> available=adb.connected()?operations.actions(app):Collections.emptyList();
        if(hidden&&!childHidden&&app.installed)addAction(details,button("恢复显示",()->restoreHiddenApp(app)));
        if(available.contains(AppOperations.Action.ENABLE))addOperation(app,AppOperations.Action.ENABLE);
        if(AppNameStore.allowed(this,app))addAction(details,button("重命名",()->renameSheet.show(app,()->renamed(app.pkg))));
        if(!hidden&&!childHidden&&canHide(app))addAction(details,button("隐藏应用",()->confirmHideApp(app)));
        bootAppAction(app,details);
        for(AppOperations.Action action:available)if(action!=AppOperations.Action.ENABLE)addOperation(app,action);
        if(!adb.connected()&&!app.system&&app.installed&&protectedReason.isEmpty())addAction(details,button("卸载应用",()->confirmSystemUninstall(app)));
        if(!protectedReason.isEmpty())protectionNote(protectedReason);
        else if(app.system&&available.isEmpty())protectionNote(adb.connected()?"本机规则或系统暂不支持此操作":"连接管理后可查看可用操作");
        if(appSheet.dialog.isShowing()&&detailSheet==null){
            View target=previousTag==null?null:details.findViewWithTag(previousTag);
            if(target==null)target=firstAction==null?appSheetClose:firstAction;
            if(target!=null)target.requestFocus();
        }
        refreshArtwork(app.pkg,false);
    }

    private void addOperation(AppRepository.App app,AppOperations.Action action){
        String title=action==AppOperations.Action.UNINSTALL?"卸载应用":action.label;
        addAction(details,button(title,()->confirmOperation(app,action)));
    }
    private boolean canHide(AppRepository.App app){return operations.hidingProtection(app).isEmpty()&&new ChildModeStore(this).eligible(app.pkg)&&AppCoverPolicy.allowed(this,app.pkg);}
    private void confirmHideApp(AppRepository.App app){
        if(busy)return;
        if(!canHide(app)||new ChildModeStore(this).hidden(app.pkg)){error("此应用必须保留或已不可用");return;}
        Sheet sheet=settingsSheet("隐藏应用？",true);AppActionConfirmationUi.content(activity,sheet.delegate,app.pkg,app.label,AppActionConfirmationUi.HIDE,false);Button cancel=sheet.action("取消",sheet.dialog::dismiss);
        Button confirm=sheet.action("确认隐藏",()->{
            if(!active||busy)return;busy=true;sheetBusy(sheet,true);
            worker.execute(()->{
                String failure=null;try{if(!active||!canHide(app)||new ChildModeStore(this).hidden(app.pkg))throw new java.io.IOException("应用状态已变化，请刷新后重试");if(!new HomeVisibilityStore(this).hide(app.pkg))throw new java.io.IOException("保存失败，请重试");}catch(Exception error){failure=error.getMessage()==null?"隐藏未完成，请重试":error.getMessage();}
                String error=failure;ui(()->{busy=false;if(!active)return;sheetBusy(sheet,false);if(error==null){sheet.dialog.dismiss();applyFilter();focusList(true);success("已隐藏，可在隐藏应用中恢复");}else inlineFailure(sheet,new java.io.IOException(error));});
            });
        });AppActionConfirmationUi.actions(activity,sheet.delegate,cancel,confirm,false);sheet.show(cancel);
    }
    private void restoreHiddenApp(AppRepository.App app){
        if(busy)return;busy=true;renderDetails();
        worker.execute(()->{
            String result;boolean restored=false;
            try{
                HomeVisibilityStore visibility=new HomeVisibilityStore(this);
                if(!visibility.hidden(app.pkg)||new ChildModeStore(this).hidden(app.pkg)||!AppAvailability.installed(getPackageManager(),getPackageManager().getApplicationInfo(app.pkg,0)))result="应用状态已变化，请刷新后重试";
                else{boolean full=visibility.wasPinned(app.pkg)&&new DockStore(this).loadAvailable().size()>=new DockStore(this).capacity();
                    restored=visibility.restore(app.pkg);result=restored?(full?"已恢复到第二屏，首页已满":"已恢复显示"):"保存失败，请重试";}
            }catch(Exception e){result="无法恢复显示，请重试";}
            String outcome=result;boolean success=restored;ui(()->{busy=false;if(active){applyFilter();focusList(true);if(success)success(outcome);else error(outcome);if(cache.stale(adb))refresh();}});
        });
    }
    private Sheet settingsSheet(String title,boolean confirmation){
        View origin=appSheet==null?getCurrentFocus():appSheet.dialog.getCurrentFocus();restoreAction=origin!=null&&origin.getTag() instanceof String?(String)origin.getTag():"";restorePackage=selected;
        Sheet sheet=new Sheet(title,appSheet==null?null:appSheet.delegate);detailSheet=sheet;if(confirmation)sheet.delegate.confirmationLayout(sheet.dialog::dismiss);else sheet.delegate.settingsLayout("关闭",sheet.dialog::dismiss);sheet.dialog.setOnDismissListener(d->{if(detailSheet==sheet)detailSheet=null;if(appSheet!=null){if(!restorePackage.equals(selected)&&!restorePackage.isEmpty())appSheet.dialog.dismiss();else renderDetails();}restoreActionFocus();});return sheet;
    }
    private void rememberActionFocus(){if(details==null)return;View focused=details.findFocus();if(focused!=null&&focused.getTag() instanceof String){restoreAction=(String)focused.getTag();restorePackage=selected;}}
    private void restoreActionFocus(){
        if(details==null||busy||detailSheet!=null||restoreAction.isEmpty()||!active)return;
        details.post(()->{if(details==null||busy||detailSheet!=null||!active)return;View target=selected.equals(restorePackage)?details.findViewWithTag(restoreAction):null;restoreAction="";
            if(target!=null&&!target.isEnabled()&&target.getParent() instanceof ViewGroup){ViewGroup group=(ViewGroup)target.getParent();int index=group.indexOfChild(target);target=null;for(int offset=1;offset<group.getChildCount()&&target==null;offset++){int before=index-offset,after=index+offset;if(before>=0&&group.getChildAt(before).isEnabled()&&group.getChildAt(before).isFocusable())target=group.getChildAt(before);else if(after<group.getChildCount()&&group.getChildAt(after).isEnabled()&&group.getChildAt(after).isFocusable())target=group.getChildAt(after);}}
            if(target!=null&&target.isEnabled())target.requestFocusFromTouch();else focusList(true);});
    }
    private Button primary(Sheet sheet,String text,Runnable action){Button b=sheet.action(text,action);b.setBackgroundDrawable(new SettingsFocusDrawable(unit,999,UiPalette.SELECTED,false,0x4dffffff));return b;}
    private void sheetBusy(Sheet sheet,boolean value){sheet.dialog.setCancelable(!value);sheet.delegate.box.setEnabled(!value);View close=sheet.delegate.box.findViewWithTag("sheet-close");if(close!=null)close.setEnabled(!value);for(int i=0;i<sheet.delegate.actions.getChildCount();i++)sheet.delegate.actions.getChildAt(i).setEnabled(!value);}
    private void confirmOperation(AppRepository.App app,AppOperations.Action action){
        Sheet sheet=settingsSheet(action.label+"应用？",true);AppActionConfirmationUi.content(activity,sheet.delegate,app.pkg,app.label,operations.explanation(app,action),action==AppOperations.Action.UNINSTALL);
        Button cancel=sheet.action("取消",sheet.dialog::dismiss);
        Button confirm=sheet.action("确认"+action.label,()->{
            if(busy)return;
            if(action==AppOperations.Action.UNINSTALL)ChildModeAuth.uninstall(activity,app.pkg,stamp->performOperation(sheet,app,action,stamp),()->cancel.requestFocusFromTouch());
            else performOperation(sheet,app,action,"");
        });AppActionConfirmationUi.actions(activity,sheet.delegate,cancel,confirm,action==AppOperations.Action.UNINSTALL);sheet.show(cancel);
    }
    private void performOperation(Sheet sheet,AppRepository.App app,AppOperations.Action action,String stamp){
        if(!active||busy)return;busy=true;sheetBusy(sheet,true);sheet.body.removeAllViews();sheet.text("正在核对并执行，请稍候…");
        worker.execute(()->{
            String result;
            try{result=new AppOperations(this).perform(adb,app,action,()->{if(action==AppOperations.Action.UNINSTALL)new ChildModeStore(this).requireUninstallAuthorization(app.pkg,stamp);return active;});}catch(Exception e){result=e.getMessage()==null?"操作未完成":e.getMessage();}
            String outcome=result;ui(()->{busy=false;dirty=true;cache.invalidate();if(active){sheetBusy(sheet,false);sheet.body.removeAllViews();sheet.text(outcome);sheet.delegate.actions.removeAllViews();Button close=sheet.action("关闭",sheet.dialog::dismiss);close.requestFocus();refresh();}});
        });
    }
    private void confirmSystemUninstall(AppRepository.App app){
        Sheet sheet=settingsSheet("卸载应用？",true);AppActionConfirmationUi.content(activity,sheet.delegate,app.pkg,app.label,operations.explanation(app,AppOperations.Action.UNINSTALL),true);Button cancel=sheet.action("取消",sheet.dialog::dismiss);
        Button confirm=sheet.action("确认卸载",()->ChildModeAuth.uninstall(activity,app.pkg,stamp->{
            if(!active)return;
            try{AppRepository.App current=repository.find(app.pkg);if(current==null||!current.installed||current.system||current.versionCode!=app.versionCode||!operations.protection(current).isEmpty())throw new java.io.IOException("应用状态已变化，请刷新后重新确认");new ChildModeStore(this).requireUninstallAuthorization(app.pkg,stamp);
                Intent intent=new Intent(Intent.ACTION_DELETE,android.net.Uri.parse("package:"+app.pkg)).putExtra(Intent.EXTRA_RETURN_RESULT,true);pendingUninstall=app.pkg;if(ExternalActivityLauncher.dispatch("entry=app.uninstall "+DiagnosticLog.intent(intent),()->activity.startActivityForResult(intent,UNINSTALL_RESULT))){dirty=true;cache.invalidate();sheet.dialog.dismiss();}else{pendingUninstall="";message("系统卸载入口不可用。");}
            }catch(Exception error){inlineFailure(sheet,error);}
        },()->cancel.requestFocusFromTouch()));AppActionConfirmationUi.actions(activity,sheet.delegate,cancel,confirm,true);sheet.show(cancel);
    }
    @Override void activityResult(int request,int result,Intent data){
        if(request!=UNINSTALL_RESULT||pendingUninstall.isEmpty())return;String pkg=pendingUninstall;pendingUninstall="";
        worker.execute(()->{Boolean removed=null;try{AppRepository.App current=repository.find(pkg);removed=current==null||!current.installed;}catch(Exception unavailable){}Boolean verified=removed;activity.runOnUiThread(()->{if(activity.isFinishing()||isDestroyed())return;if(Boolean.TRUE.equals(verified))activity.success("应用已卸载");else if(verified==null)activity.error("卸载结果暂无法核对，请刷新应用列表");else activity.feedback(result==android.app.Activity.RESULT_CANCELED?"已取消卸载":"卸载尚未完成，请核对应用状态");});});
    }
    private void bootAppAction(AppRepository.App app,ViewGroup actions){
        BootAppStore store=new BootAppStore(this);boolean chosen=app.pkg.equals(store.target());
        if(!chosen && (!app.installed || !app.enabled || store.launchIntent(app.pkg)==null))return;
        addAction(actions,button(chosen?"关闭开机自启":"设为开机自启",()->editStartupApp(app.pkg)));
    }
    void editStartupApp(String pkg){
        BootAppStore store=new BootAppStore(this);
        if(pkg.equals(store.target())){startupSettings();return;}
        if(store.launchIntent(pkg)==null){message("此应用暂不可用，请重新选择。");return;}
        String label=pkg;
        try{label=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}
        catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
        final String name=label;Sheet sheet=settingsSheet("设为开机自启？",true);
        sheet.text("开机或待机唤醒进入 Quiet TV 后，将自动打开 "+name+"。"
            +(store.hdmiBoot()?"将关闭开机自动看电视。":store.target().isEmpty()?"":"将替换当前自启应用："+store.label()+"。"));
        sheet.body.addView(muted("按 HOME 返回不重复，儿童模式限制仍然有效。",11));
        Button cancel=sheet.action("取消",sheet.dialog::dismiss);
        primary(sheet,"开启",()->{try{store.select(pkg,name);renderDetails();sheet.dialog.dismiss();success("开机自启设置已保存");}catch(java.io.IOException failure){inlineFailure(sheet,failure);}});sheet.show(cancel);
    }
    private void inlineFailure(Sheet sheet,Exception error){View old=sheet.body.findViewWithTag("app-sheet-error");if(old!=null)sheet.body.removeView(old);TextView text=label(error.getMessage()==null?"保存失败，请重试":error.getMessage(),11);text.setTag("app-sheet-error");text.setTextColor(UiPalette.SECONDARY);OperationFeedback.status(text,OperationFeedback.Kind.ERROR);sheet.body.addView(text);UiCompat.announce(text,text.getText().toString());}
    private void startupSettings(){
        BootAppStore store=new BootAppStore(this);Sheet sheet=settingsSheet("应用自启",false);
        sheet.text(store.target().isEmpty()?"未设置自启应用。可在应用详情选择“设为开机自启”。":"当前自启应用："+store.label()+"。可在其他应用详情替换。");
        Button close=(Button)sheet.delegate.box.findViewWithTag("sheet-close");
        if(!store.target().isEmpty())primary(sheet,"关闭自启",()->{try{store.select("","");renderDetails();sheet.dialog.dismiss();success("开机自启设置已保存");}catch(java.io.IOException failure){inlineFailure(sheet,failure);}});
        sheet.show(close);
    }
    private void protectionNote(String reason){details.addView(muted(reason.isEmpty()?"此应用仅供查看":reason,11));}
    private void actionStyle(Button action){
        SettingsTouchClick.attach(action);
        action.setSingleLine();
        action.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        action.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,13*unit);
        action.setPadding(px(12),0,px(12),0);
        action.setBackgroundDrawable(new SettingsFocusDrawable(unit,999,Color.TRANSPARENT));
        action.setTextColor("卸载应用".contentEquals(action.getText())
            ?SettingsFocusDrawable.textColors(UiPalette.STATUS_ERROR,0xffb3261e)
            :SettingsFocusDrawable.textColors(UiPalette.TEXT));
    }
    private CharSequence statusText(AppRepository.App app,boolean hidden){
        String text=(hidden?"已隐藏":app.state())+"  "+(app.system?"预装应用":"用户应用");
        if(hidden||!app.installed)return text;
        SpannableString styled=new SpannableString(text);
        styled.setSpan(new TextAppearanceSpan(null,0,-1,app.enabled?ENABLED_STATUS:DISABLED_STATUS,null),0,app.state().length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return styled;
    }
    private final class Artwork extends ImageView {
        private AppRepository.App app;
        private String key="";
        private int binding;
        private Runnable task;

        Artwork(){super(activity);setScaleType(ScaleType.FIT_CENTER);}
        void cancelDecode(){if(task!=null){artworkWorker.remove(task);task=null;}}
        private String artworkKey(AppRepository.App value){
            return value.pkg+":"+value.updatedAt+":"+value.versionCode+":"+value.enabled+":"+shape+":"+AppCoverStore.get(activity).key(value.pkg)+":"+legacyArtworkRevision;
        }
        void bind(AppRepository.App value){
            cancelDecode();
            int current=++binding;
            app=value;
            key="";
            setImageDrawable(null);
            if(value==null||!active)return;
            String request=artworkKey(value);
            key=request;
            Bitmap existing=artworkCache.get(request);
            if(existing!=null){show(existing,value.pkg);return;}
            String pkg=value.pkg;
            Runnable decode=()->{
                Bitmap ready=null;
                try{ready=AppArtwork.thumbnail(activity,pkg,value.icon,android.os.Build.VERSION.SDK_INT<=19?160:320);}
                catch(RuntimeException|OutOfMemoryError unavailable){}
                Bitmap bitmap=ready;
                activity.runOnUiThread(()->{
                    if(isDestroyed()||isFinishing()||!active||current!=binding||!request.equals(artworkKey(value))){
                        if(bitmap!=null)bitmap.recycle();
                        return;
                    }
                    task=null;
                    if(bitmap==null){setImageDrawable(new QuickControlIcon("app-window"));return;}
                    artworkCache.put(request,bitmap);
                    show(bitmap,pkg);
                });
            };
            task=decode;
            artworkWorker.execute(decode);
        }
        void refresh(boolean force){if(app!=null&&(force||!key.equals(artworkKey(app))))bind(app);}
        private void show(Bitmap image,String pkg){
            setImageDrawable(new AppShapeDrawable(image,"circle".equals(shape),AppArtwork.displayedAsContain(activity,pkg)));
        }
        @Override protected void onDetachedFromWindow(){cancelDecode();++binding;key="";super.onDetachedFromWindow();}
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(app!=null&&key.isEmpty())bind(app);}
    }
    private void refreshArtwork(String pkg,boolean force){
        if(detailArtwork!=null&&detailArtwork.app!=null&&(pkg==null||pkg.equals(detailArtwork.app.pkg)))detailArtwork.refresh(force);
        if(list==null)return;
        for(int i=0;i<list.getChildCount();i++){
            RecyclerView.ViewHolder child=list.getChildViewHolder(list.getChildAt(i));
            if(child instanceof AppAdapter.Holder){
                AppAdapter.Holder holder=(AppAdapter.Holder)child;
                holder.tile.squareArt(!"rectangle".equals(shape));
                if(holder.icon.app!=null&&(pkg==null||pkg.equals(holder.icon.app.pkg)))holder.icon.refresh(force);
            }
        }
    }
    private void addAction(ViewGroup row,Button action){
        actionStyle(action);
        action.setId(androidx.core.view.ViewCompat.generateViewId());
        action.setTag("app-action-"+action.getText());
        row.addView(action,new LinearLayout.LayoutParams(-1,px(34.56f)));
        if(action.isEnabled()&&firstAction==null)firstAction=action;
    }
    @Override public void handleBack(){cancelConfirm();super.handleBack();}
    private void cancelConfirm(){
        if(confirmView!=null)confirmView.setPressed(false);
        confirmView=null;
    }

    private boolean activateKey(View view,int key,KeyEvent event){
        if(event.getAction()==KeyEvent.ACTION_DOWN){
            if(event.getRepeatCount()==0){
                cancelConfirm();
                confirmView=view;
                confirmDownTime=event.getDownTime();
                confirmKey=key;
                confirmHeld=false;
                view.setPressed(true);
            }else if(confirmView==view&&event.getDownTime()==confirmDownTime){
                confirmHeld|=event.isLongPress();
            }
        }else if(event.getAction()==KeyEvent.ACTION_UP&&key==confirmKey&&event.getDownTime()==confirmDownTime){
            boolean accept=confirmView==view&&view.hasWindowFocus()&&!event.isCanceled();
            boolean longPress=confirmHeld||event.getEventTime()-confirmDownTime>=ViewConfiguration.getLongPressTimeout();
            cancelConfirm();
            if(accept){if(longPress)view.performLongClick();else view.performClick();}
        }
        return true;
    }
    private final class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {
        final class Holder extends RecyclerView.ViewHolder {
            final IconGridTile tile;
            final Artwork icon;
            Holder(IconGridTile tile,Artwork icon){super(tile);this.tile=tile;this.icon=icon;}
        }
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            Artwork art=new Artwork();
            IconGridTile tile=new IconGridTile(activity,art);
            tile.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));
            Holder holder=new Holder(tile,art);
            tile.setOnClickListener(view->open(holder,false));
            tile.setOnLongClickListener(view->{open(holder,true);return true;});
            tile.setOnKeyListener((view,key,event)->{
                if(KeyFeedback.confirmKey(key))return activateKey(view,key,event);
                if(key==KeyEvent.KEYCODE_DPAD_UP&&event.getAction()==KeyEvent.ACTION_DOWN
                        &&holder.getBindingAdapterPosition()>=0&&holder.getBindingAdapterPosition()<IconGridTile.COLUMNS){
                    filterButtons.get(filter).requestFocusFromTouch();return true;
                }
                return false;
            });
            tile.setOnFocusChangeListener((view,focused)->{
                if(!focused&&confirmView==view)cancelConfirm();
                int position=holder.getBindingAdapterPosition();
                if(focused&&position>=0&&position<filtered.size())selected=filtered.get(position).pkg;
            });
            return holder;
        }
        private void open(Holder holder,boolean manage){
            int position=holder.getBindingAdapterPosition();
            if(position<0||position>=filtered.size())return;
            holder.tile.requestFocusFromTouch();
            activate(filtered.get(position),manage);
            KeyFeedback.confirm(activity);
        }
        @Override public void onBindViewHolder(Holder holder,int position){
            AppRepository.App app=filtered.get(position);
            holder.tile.squareArt(!"rectangle".equals(shape));
            holder.icon.bind(app);
            holder.tile.name.setText(app.label);
            holder.tile.name.setTypeface(activity.navigationTypeface(holder.tile.isFocused()));
            holder.tile.setTag("managed-app-"+app.pkg);
            holder.tile.setContentDescription(app.label+"  "+app.pkg+"  "+app.summary());
        }
        @Override public void onViewRecycled(Holder holder){
            if(confirmView==holder.tile)cancelConfirm();
            holder.icon.bind(null);
            super.onViewRecycled(holder);
        }
        @Override public int getItemCount(){return filtered.size();}
    }
}
