package home.quiet.tv;

import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.format.Formatter;
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
    private Button refreshButton;
    private Parcelable restoreScroll;
    private String contentFocus="";
    private final Runnable cacheChanged=()->{dirty=true;if(this.active)refresh();};
    private final ViewTreeObserver.OnGlobalFocusChangeListener focusChanged=(before,after)->{if(after!=null&&inside(after)&&after.getTag() instanceof String)contentFocus=(String)after.getTag();};
    private final ExecutorService worker;
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
    private AppCoverUploadPanel coverUploads;
    private AppRenameSheet renameSheet;
    private String namesSignature="";
    private final SharedPreferences.OnSharedPreferenceChangeListener namesListener=(prefs,key)->{if(AppNameStore.KEY.equals(key))ui(this::namesChanged);};
    private void namesChanged(){namesChanged(false);}
    private void namesChanged(boolean keepDetails){AppNameStore names=new AppNameStore(this);if(names.signature().equals(namesSignature))return;namesSignature=names.signature();if(active)rememberActionFocus();for(int i=0;i<all.size();i++){AppRepository.App app=all.get(i).copy();app.label=names.display(app.pkg,app.originalName);all.set(i,app);}Collections.sort(all,(a,b)->java.text.Collator.getInstance(Locale.SIMPLIFIED_CHINESE).compare(a.label,b.label));if(active)applyFilter(keepDetails);}
    private void renamed(String pkg){namesChanged();if(!active||!selected.equals(pkg))return;for(int i=0;i<filtered.size();i++)if(filtered.get(i).pkg.equals(pkg)){list.scrollToPosition(i);break;}View action=details.findViewWithTag("app-action-重命名");if(action!=null)action.requestFocusFromTouch();success("名称已更新");}
    private Bundle coverState;
    private LinearLayout details;
    private RecyclerView list;
    private AppAdapter adapter;
    private View firstAction;
    private Sheet detailSheet;
    private String restoreAction="",restorePackage="";
    private String selected="";
    private String pendingUninstall="";
    private static final int UNINSTALL_RESULT=7412;
    private int filter;
    private final Runnable phoneChanged=this::phoneChanged;
    private String phoneKey="";
    private String phoneSignature()throws java.io.IOException{return PhoneSettingsPreview.get(activity).signature().toString()+new DockStore(activity).load().toString()+new BootAppStore(activity).target();}
    private void phoneChanged(){String key;try{key=phoneSignature();}catch(java.io.IOException unavailable){return;}if(key.equals(phoneKey))return;phoneKey=key;if(active){rememberActionFocus();namesChanged();applyFilter();}}
    private boolean dirty=true;
    private boolean busy,reading,refreshAfterConnection,returningToList,focusDetailsOnLoad;
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
        coverUploads=new AppCoverUploadPanel(activity);renameSheet=new AppRenameSheet(activity);
        LinearLayout body=managementContent(),filters=managementInfo;pageRoot.setClipChildren(false);pageRoot.setClipToPadding(false);body.setClipChildren(false);body.setClipToPadding(false);
        filters.getLayoutParams().height=px(34.56f);((LinearLayout.LayoutParams)body.getLayoutParams()).topMargin=px(12);
        for(int i=0;i<FILTERS.length;i++){final int chosen=i;Button option=button(FILTERS[i],()->{filterButtons.get(chosen).requestFocusFromTouch();if(filter!=chosen){filter=chosen;applyFilter();}});option.setId(androidx.core.view.ViewCompat.generateViewId());option.setTag("app-filter-"+i);option.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,12*unit);option.setPadding(px(9),0,px(9),px(3));option.setBackgroundDrawable(new SelectionSurface(true));option.setTextColor(SettingsFocusDrawable.textColors(UiPalette.SECONDARY));option.setOnFocusChangeListener((v,focused)->{if(focused&&filter!=chosen){filter=chosen;applyFilter();}});
            option.setOnKeyListener((v,key,event)->{
                if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT&&key!=KeyEvent.KEYCODE_DPAD_UP&&key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;
                if(event.getAction()==KeyEvent.ACTION_UP)return true;
                if(key==KeyEvent.KEYCODE_DPAD_DOWN)focusList();
                else if(key==KeyEvent.KEYCODE_DPAD_UP)managementTab().requestFocus();
                else{
                    int next=Math.max(0,Math.min(filterButtons.size()-1,chosen+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1)));
                    if(next!=chosen){Button target=filterButtons.get(next);target.requestFocusFromTouch();}else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)refreshButton.requestFocusFromTouch();
                }
                return true;
            });
            option.setNextFocusUpId(managementTab().getId());filterButtons.add(option);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,px(33));if(i>0)p.leftMargin=px(12);filters.addView(option,p);}
        for(int i=0;i<filterButtons.size();i++){Button b=filterButtons.get(i);if(i>0)b.setNextFocusLeftId(filterButtons.get(i-1).getId());if(i+1<filterButtons.size())b.setNextFocusRightId(filterButtons.get(i+1).getId());}
        filters.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        refreshButton=button("刷新",()->{if(!reading&&!busy){cache.invalidate();dirty=true;refresh();}});refreshButton.setTag("apps-refresh");refreshButton.setId(androidx.core.view.ViewCompat.generateViewId());refreshButton.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,12*unit);refreshButton.setPadding(px(9),0,px(9),0);refreshButton.setBackgroundDrawable(new SettingsFocusDrawable(unit,999,UiPalette.CONTROL,false,UiPalette.EDGE));
        refreshButton.setOnKeyListener((v,key,event)->{if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT&&key!=KeyEvent.KEYCODE_DPAD_UP&&key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;if(event.getAction()==KeyEvent.ACTION_DOWN){if(key==KeyEvent.KEYCODE_DPAD_LEFT)filterButtons.get(filter).requestFocusFromTouch();else if(key==KeyEvent.KEYCODE_DPAD_UP)managementTab().requestFocus();else if(key==KeyEvent.KEYCODE_DPAD_DOWN)focusList();}return true;});LinearLayout.LayoutParams refreshPosition=new LinearLayout.LayoutParams(px(72),px(33));refreshPosition.rightMargin=px(12);filters.addView(refreshButton,refreshPosition);

        managementConnection=muted("ADB 未连接",12);managementConnection.setTag("apps-connection");managementConnection.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);managementConnection.setFocusable(false);managementConnection.setClickable(false);
        filters.addView(managementConnection,new LinearLayout.LayoutParams(-2,-1));
        updateManagementConnection();
        LinearLayout panels=row();panels.setGravity(Gravity.TOP);panels.setClipChildren(false);panels.setClipToPadding(false);body.addView(panels,new LinearLayout.LayoutParams(-1,-1));
        FrameLayout left=new FrameLayout(activity);left.setClipChildren(false);left.setClipToPadding(false);panels.addView(left,new LinearLayout.LayoutParams(0,-1,40));
        list=new RecyclerView(this){@Override protected void dispatchDraw(Canvas canvas){int saved=canvas.save();canvas.clipRect(0,0,getWidth(),getHeight());super.dispatchDraw(canvas);canvas.restoreToCount(saved);}};list.setLayoutManager(new LinearLayoutManager(this));adapter=new AppAdapter();list.setAdapter(adapter);list.setItemAnimator(null);list.setClipToPadding(false);list.setClipChildren(true);list.setVerticalScrollBarEnabled(false);list.setPadding(px(12.36f),px(11.88f),px(12.36f),px(14.76f));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-1);lp.setMargins(px(-9),px(-9),px(-9),px(-9));left.addView(list,lp);
        ScrollView scroll=activity.settingsScroll();scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setClipChildren(false);scroll.setBackgroundDrawable(surface(UiPalette.CONTROL,UiPalette.CONTROL,UiPalette.EDGE,15));details=column();details.setClipChildren(false);details.setClipToPadding(false);details.setGravity(Gravity.CENTER_HORIZONTAL);details.setPadding(px(25),px(27),px(25),px(22));scroll.addView(details);
        // The list's drawing inset is 11.88 - 9; align the detail surface to its first row.
        LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,-1,57);dp.setMargins(px(28),px(2.88f),0,0);panels.addView(scroll,dp);
        Bundle previous=state==null?cache.navigation():state;
        if(previous!=null){restoreScroll=previous.getParcelable("app-scroll");contentFocus=previous.getString("app-focus","");phoneKey=previous.getString("app-phone-key","");}
        if(state==null)state=previous;
        if(state!=null){selected=state.getString("selected","");pendingUninstall=state.getString("app-uninstall-pending","");filter=Math.max(0,Math.min(FILTERS.length-1,state.getInt("filter",0)));coverState=state.getBundle("app-cover");focusDetailsOnLoad=state.getBoolean("focus-details-on-load");}
        else{String requested=activity.getIntent().getStringExtra("selected-package");if(requested!=null)selected=requested;focusDetailsOnLoad=activity.getIntent().getBooleanExtra("focus-app-details",false);}
        String requested=activity.getIntent().getStringExtra("selected-package");if(requested!=null&&!requested.isEmpty()){selected=requested;filter=0;focusDetailsOnLoad=activity.getIntent().getBooleanExtra("focus-app-details",false);}
        if(cache.available()){readCache();namesChanged();applyFilter();}
        pageRoot.getViewTreeObserver().addOnGlobalFocusChangeListener(focusChanged);
        IntentFilter packages=new IntentFilter(Intent.ACTION_PACKAGE_ADDED);packages.addAction(Intent.ACTION_PACKAGE_REMOVED);packages.addAction(Intent.ACTION_PACKAGE_CHANGED);packages.addDataScheme("package");registerReceiver(packagesChanged,packages);
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
        namesChanged();phoneChanged();PhoneSettingsSession.get(activity).observeConfiguration(phoneChanged);session.addListener(connectionChanged);session.request();updateManagementConnection();dirty=cache.stale(adb);if(dirty)refresh();
        if(coverState!=null){Bundle value=coverState;coverState=null;coverUploads.restoreState(value,()->coverUpdated(value.getString("package","")));}
    }
    private boolean inside(View view){for(View v=view;v!=null;v=v.getParent() instanceof View?(View)v.getParent():null)if(v==pageRoot)return true;return false;}
    private void saveNavigation(Bundle state){state.putString("selected",selected);state.putString("app-uninstall-pending",pendingUninstall);state.putInt("filter",filter);state.putString("app-focus",contentFocus);state.putString("app-phone-key",phoneKey);if(list.getLayoutManager()!=null)state.putParcelable("app-scroll",list.getLayoutManager().onSaveInstanceState());}
    @Override protected void onPause(){Bundle state=new Bundle();saveNavigation(state);cache.navigation(state);super.onPause();}
    @Override void focusManagementContent(){View target=pageRoot.findViewWithTag(contentFocus);if(target!=null&&target.isShown()&&target.isEnabled()&&target.requestFocusFromTouch())return;if(contentFocus.startsWith("managed-app-")||contentFocus.startsWith("app-action-"))focusList(true);else filterButtons.get(filter).requestFocus();}
    @Override protected void onStop(){active=false;cache.removeListener(cacheChanged);getSharedPreferences("dock",0).unregisterOnSharedPreferenceChangeListener(namesListener);renameSheet.close();ChildModeAuth.dismiss(activity);PhoneSettingsSession.get(activity).forgetConfiguration(phoneChanged);session.removeListener(connectionChanged);refreshAfterConnection=false;refreshGeneration++;if(reading)busy=false;reading=false;updateRefresh();coverUploads.stop(activity.isChangingConfigurations());if(detailSheet!=null)detailSheet.dialog.dismiss();super.onStop();}
    @Override protected void onDestroy(){cache.removeListener(cacheChanged);pageRoot.getViewTreeObserver().removeOnGlobalFocusChangeListener(focusChanged);PhoneSettingsSession.get(activity).forgetConfiguration(phoneChanged);session.removeListener(connectionChanged);unregisterReceiver(packagesChanged);getSharedPreferences("dock",0).unregisterOnSharedPreferenceChangeListener(namesListener);renameSheet.close();coverUploads.destroy();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){saveNavigation(state);state.putBoolean("focus-details-on-load",focusDetailsOnLoad);coverUploads.saveState(state);super.onSaveInstanceState(state);}
    private void readCache(){synchronized(cache){all.clear();all.addAll(cache.snapshot());cacheVersion=cache.version();}}
    private void updateRefresh(){refreshButton.setText(reading?"刷新中…":"刷新");}
    private void refresh(){
        if(!active){dirty=true;return;}if(reading)return;if(busy){refreshAfterConnection=true;return;}
        rememberActionFocus();reading=true;busy=!cache.available();final int run=++refreshGeneration;updateRefresh();if(busy)renderDetails();
        worker.execute(()->{try{cache.load(adb);ui(()->{if(!active||run!=refreshGeneration)return;boolean initial=busy;reading=false;busy=false;dirty=cache.stale(adb);readCache();operations=new AppOperations(this);namesSignature="";namesChanged(!initial);updateRefresh();if(dirty||refreshAfterConnection){refreshAfterConnection=false;if(cache.stale(adb))refresh();}});}catch(Exception e){ui(()->{if(!active||run!=refreshGeneration)return;reading=false;busy=false;dirty=true;updateRefresh();loadFailure(e);});}});
    }
    private void loadFailure(Exception e){if(cache.available()){error("刷新失败，已保留现有列表");restoreActionFocus();}else{renderDetails();message(e.getMessage()==null?e.toString():e.getMessage());}}
    // Each repository snapshot replaces app metadata as well as list membership/order.
    private void applyFilter(){applyFilter(false);}
    private void applyFilter(boolean keepDetails){
        boolean listFocused=list.hasFocus();AppRepository.App previous=current();ArrayList<AppRepository.App> before=new ArrayList<>(filtered);filtered.clear();
        HomeVisibilityStore visibility=new HomeVisibilityStore(this);ChildModeStore children=new ChildModeStore(this);
        for(AppRepository.App app:all){boolean match=filter==0 || filter==1 && !app.system && app.installed || filter==2 && app.system || filter==4 && app.installed && !app.enabled || filter==5 && app.recoverable && !app.installed || filter==HIDDEN_FILTER && app.installed && visibility.hidden(app.pkg) && !children.hidden(app.pkg);if(match)filtered.add(app);}
        for(int i=0;i<filterButtons.size();i++){filterButtons.get(i).setSelected(i==filter);filterButtons.get(i).setTypeface(activity.navigationTypeface(i==filter));}
        boolean present=false;for(AppRepository.App app:filtered)if(app.pkg.equals(selected))present=true;if(!present)selected=filtered.isEmpty()?"":filtered.get(0).pkg;
        DiffUtil.calculateDiff(new DiffUtil.Callback(){public int getOldListSize(){return before.size();}public int getNewListSize(){return filtered.size();}public boolean areItemsTheSame(int old,int next){return before.get(old).pkg.equals(filtered.get(next).pkg);}public boolean areContentsTheSame(int old,int next){return keepDetails&&before.get(old).same(filtered.get(next));}}).dispatchUpdatesTo(adapter);
        if(restoreScroll!=null){list.getLayoutManager().onRestoreInstanceState(restoreScroll);restoreScroll=null;}
        if(!keepDetails||previous==null||!previous.same(current()))renderDetails();if(listFocused)focusList();else restoreActionFocus();if(focusDetailsOnLoad&&!busy&&active){String requested=activity.getIntent().getStringExtra("selected-package");if(selected.equals(requested)&&firstAction!=null){View target=firstAction;target.post(()->{if(active&&!isDestroyed()&&!busy&&target==firstAction&&target.requestFocusFromTouch())focusDetailsOnLoad=false;});}else{focusDetailsOnLoad=false;if(!selected.equals(requested))error("此应用已不可用，请重新选择");focusList(true);}}
    }
    private AppRepository.App current(){for(AppRepository.App app:filtered)if(app.pkg.equals(selected))return app;return null;}
    private void focusList(){focusList(false);}
    private void focusList(boolean restoring){list.post(()->{if(!restoring&&managementTabFocused() || !active)return;for(int i=0;i<filtered.size();i++)if(filtered.get(i).pkg.equals(selected)){final int position=i;if(list.findViewHolderForAdapterPosition(position)==null)list.scrollToPosition(position);list.post(()->{if(!restoring&&managementTabFocused() || !active)return;RecyclerView.ViewHolder holder=list.findViewHolderForAdapterPosition(position);if(holder!=null)holder.itemView.requestFocus();});return;}filterButtons.get(filter).requestFocus();});}
    private void renderDetails(){
        if(details==null)return;updateManagementConnection();details.removeAllViews();firstAction=null;AppRepository.App app=current();
        if(app==null){details.setGravity(Gravity.CENTER);TextView empty=center(busy?"正在读取应用…":"没有符合条件的应用",11);empty.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,10.848f*unit);empty.setTextColor(UiPalette.SECONDARY);details.addView(empty);return;}details.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView artwork=appArt(app.icon,67);artworkPadding(artwork,app,67);details.addView(artwork);gap(details,16);TextView title=center(app.label,26);typography(title,26,36.4f,UiPalette.TEXT);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);details.addView(title,new LinearLayout.LayoutParams(-1,-2));
        if(!new AppNameStore(this).alias(app.pkg).isEmpty()){gap(details,5);TextView original=center("原名称："+app.originalName,12);typography(original,12,20.4f,UiPalette.SECONDARY);details.addView(original);}
        gap(details,10);TextView state=center(app.state()+"  "+(app.system?"预装应用":"用户应用"),12);typography(state,12,20.4f,UiPalette.SECONDARY);details.addView(state);
        String size=app.apkSizeBytes>=0?Formatter.formatShortFileSize(this,app.apkSizeBytes):"大小未知";
        gap(details,20);TextView meta=center(app.pkg+"\n版本 "+app.version+"  "+size,12);typography(meta,12,21.6f,UiPalette.SECONDARY);details.addView(meta,new LinearLayout.LayoutParams(-1,-2));
        details.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));gap(details,18);
        boolean hidden=new HomeVisibilityStore(this).hidden(app.pkg),childHidden=new ChildModeStore(this).hidden(app.pkg);
        if(!busy && hidden && !childHidden){
            ActionFlow restore=new ActionFlow();details.addView(restore,new LinearLayout.LayoutParams(-1,-2));
            addAction(restore,button("恢复显示",()->restoreHiddenApp(app)));gap(details,9);
        }
        ActionFlow actions=new ActionFlow();details.addView(actions,new LinearLayout.LayoutParams(-1,-2));
        if(!busy&&AppNameStore.allowed(this,app))addAction(actions,button("重命名",()->renameSheet.show(app,()->renamed(app.pkg))));
        if(!busy)bootAppAction(app,actions);
        String protectedReason=operations.protection(app);
        if(AppCoverPolicy.ownReadOnly(this,app.pkg)){protectionNote(protectedReason,true);return;}
        boolean coverAllowed=AppCoverPolicy.allowed(this,app.pkg);
        if(!coverAllowed&&!protectedReason.isEmpty()){protectionNote(AppNameStore.allowed(this,app)?"系统操作受保护":protectedReason,true);return;}
        if(busy){details.addView(center("正在核对实际状态…",12));return;}
        if(!hidden&&!childHidden&&canHide(app))addAction(actions,button("隐藏应用",()->confirmHideApp(app)));
        if(coverAllowed){
            addAction(actions,button("更换封面",()->coverUploads.show(app.pkg,app.label,()->coverUpdated(app.pkg))));
            Button restore=button("恢复默认封面",()->restoreCover(app));boolean custom=AppCoverStore.get(this).custom(app.pkg);restore.setEnabled(custom);restore.setFocusable(custom);restore.setAlpha(1f);addAction(actions,restore);
        }
        List<AppOperations.Action> available=operations.actions(app);
        if(!available.isEmpty()){
            for(AppOperations.Action action:available)addAction(actions,button(action.label,()->confirmOperation(app,action)));
        }else if(app.system && protectedReason.isEmpty()){
            gap(details,10);details.addView(center(adb.connected()?"本机规则或系统暂不支持此操作":"连接管理后可查看可用操作",11));
        }
        if(!adb.connected() && !app.system && app.installed && protectedReason.isEmpty()){
            Button uninstall=button("卸载应用",()->confirmSystemUninstall(app));addAction(actions,uninstall);
        }

    }
    private boolean canHide(AppRepository.App app){return operations.hidingProtection(app).isEmpty()&&new ChildModeStore(this).eligible(app.pkg)&&AppCoverPolicy.allowed(this,app.pkg);}
    private void confirmHideApp(AppRepository.App app){
        if(busy)return;
        if(!canHide(app)||new ChildModeStore(this).hidden(app.pkg)){error("此应用必须保留或已不可用");return;}
        Sheet sheet=settingsSheet("隐藏应用？");AppActionConfirmationUi.content(activity,sheet.delegate,app.pkg,app.label,AppActionConfirmationUi.HIDE,false);Button cancel=sheet.action("取消",sheet.dialog::dismiss);
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
    private Sheet settingsSheet(String title){
        View origin=getCurrentFocus();restoreAction=origin!=null&&origin.getTag() instanceof String?(String)origin.getTag():"";restorePackage=selected;
        Sheet sheet=new Sheet(title);detailSheet=sheet;sheet.delegate.settingsLayout("关闭",sheet.dialog::dismiss);sheet.dialog.setOnDismissListener(d->{if(detailSheet==sheet)detailSheet=null;restoreActionFocus();});return sheet;
    }
    private void rememberActionFocus(){View focused=details.findFocus();if(focused!=null&&focused.getTag() instanceof String){restoreAction=(String)focused.getTag();restorePackage=selected;}}
    private void restoreActionFocus(){
        if(busy||detailSheet!=null||restoreAction.isEmpty()||!active)return;
        details.post(()->{if(busy||detailSheet!=null||!active)return;View target=selected.equals(restorePackage)?details.findViewWithTag(restoreAction):null;restoreAction="";
            if(target!=null&&!target.isEnabled()&&target.getParent() instanceof ViewGroup){ViewGroup group=(ViewGroup)target.getParent();int index=group.indexOfChild(target);target=null;for(int offset=1;offset<group.getChildCount()&&target==null;offset++){int before=index-offset,after=index+offset;if(before>=0&&group.getChildAt(before).isEnabled()&&group.getChildAt(before).isFocusable())target=group.getChildAt(before);else if(after<group.getChildCount()&&group.getChildAt(after).isEnabled()&&group.getChildAt(after).isFocusable())target=group.getChildAt(after);}}
            if(target!=null&&target.isEnabled())target.requestFocusFromTouch();else focusList(true);});
    }
    private Button primary(Sheet sheet,String text,Runnable action){Button b=sheet.action(text,action);b.setBackgroundDrawable(new SettingsFocusDrawable(unit,999,UiPalette.SELECTED,false,0x4dffffff));return b;}
    private void sheetBusy(Sheet sheet,boolean value){sheet.dialog.setCancelable(!value);sheet.delegate.box.setEnabled(!value);View close=sheet.delegate.box.findViewWithTag("sheet-close");if(close!=null)close.setEnabled(!value);for(int i=0;i<sheet.delegate.actions.getChildCount();i++)sheet.delegate.actions.getChildAt(i).setEnabled(!value);}
    private void confirmOperation(AppRepository.App app,AppOperations.Action action){
        Sheet sheet=settingsSheet(action.label+"应用？");AppActionConfirmationUi.content(activity,sheet.delegate,app.pkg,app.label,operations.explanation(app,action),action==AppOperations.Action.UNINSTALL);
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
        Sheet sheet=settingsSheet("卸载应用？");AppActionConfirmationUi.content(activity,sheet.delegate,app.pkg,app.label,operations.explanation(app,AppOperations.Action.UNINSTALL),true);Button cancel=sheet.action("取消",sheet.dialog::dismiss);
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
        final String name=label;Sheet sheet=settingsSheet("设为开机自启？");
        sheet.text("开机或待机唤醒进入 Quiet TV 后，将自动打开 "+name+"。"
            +(store.hdmiBoot()?"将关闭开机自动看电视。":store.target().isEmpty()?"":"将替换当前自启应用："+store.label()+"。"));
        TextView note=label("按 HOME 返回不重复，儿童模式限制仍然有效。",11);typography(note,10.848f,17.9f,UiPalette.SECONDARY);sheet.body.addView(note,new LinearLayout.LayoutParams(-1,-2));
        Button cancel=sheet.action("取消",sheet.dialog::dismiss);
        primary(sheet,"开启",()->{try{store.select(pkg,name);renderDetails();sheet.dialog.dismiss();success("开机自启设置已保存");}catch(java.io.IOException failure){inlineFailure(sheet,failure);}});sheet.show(cancel);
    }
    private void inlineFailure(Sheet sheet,Exception error){View old=sheet.body.findViewWithTag("app-sheet-error");if(old!=null)sheet.body.removeView(old);TextView text=label(error.getMessage()==null?"保存失败，请重试":error.getMessage(),11);text.setTag("app-sheet-error");text.setTextColor(UiPalette.SECONDARY);OperationFeedback.status(text,OperationFeedback.Kind.ERROR);sheet.body.addView(text);UiCompat.announce(text,text.getText().toString());}
    private void startupSettings(){
        BootAppStore store=new BootAppStore(this);Sheet sheet=settingsSheet("应用自启");
        sheet.text(store.target().isEmpty()?"未设置自启应用。可在应用详情选择“设为开机自启”。":"当前自启应用："+store.label()+"。可在其他应用详情替换。");
        Button close=sheet.action("返回",sheet.dialog::dismiss);
        if(!store.target().isEmpty())primary(sheet,"关闭自启",()->{try{store.select("","");renderDetails();sheet.dialog.dismiss();success("开机自启设置已保存");}catch(java.io.IOException failure){inlineFailure(sheet,failure);}});
        sheet.show(close);
    }
    private void protectionNote(String reason,boolean readOnly){TextView note=center(reason.isEmpty()?"此应用仅供查看":reason,11);typography(note,10.848f,17.9f,UiPalette.SECONDARY);details.addView(note);}
    private void actionStyle(Button action){SettingsTouchClick.attach(action);action.setSingleLine();action.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.712f*unit);action.setPadding(px(13.71f),0,px(13.71f),0);action.setBackgroundDrawable(new SettingsFocusDrawable(unit,999,UiPalette.CONTROL,false,UiPalette.EDGE));action.setTextColor(new android.content.res.ColorStateList(new int[][]{{-android.R.attr.state_enabled},{android.R.attr.state_focused,android.R.attr.state_window_focused},{}},new int[]{UiPalette.TEXT,UiPalette.INK,UiPalette.TEXT}));}
    private ImageView appArt(Drawable drawable,float size){ImageView image=new SurfaceCorners.Image(activity);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImageDrawable(drawable);image.setPadding(px(.75f),px(.75f),px(.75f),px(.75f));image.setBackgroundDrawable(surface(0xff000000,0xff000000,0x13ffffff,7.68f));UiCompat.clipOutline(image);image.setLayoutParams(new LinearLayout.LayoutParams(px(size),px(size)));return image;}
    private void artworkPadding(ImageView image,AppRepository.App app,float size){int padding=px(app.genericIcon?size*.14f:.75f);image.setPadding(padding,padding,padding,padding);}
    private void typography(TextView text,float size,float height,int color){text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size*unit);text.setTextColor(color);Paint.FontMetrics metrics=text.getPaint().getFontMetrics();float leading=Math.max(0,height*unit-(metrics.descent-metrics.ascent));text.setLineSpacing(leading,1);text.setPadding(0,Math.round(leading/2),0,Math.round(leading/2));text.setMinimumHeight(px(height));}
    private void gap(LinearLayout parent,float height){activity.gap(parent,height);}
    private void addAction(ViewGroup row,Button action){
        actionStyle(action);action.setId(androidx.core.view.ViewCompat.generateViewId());action.setTag("app-action-"+action.getText());
        row.addView(action,new ViewGroup.LayoutParams(-2,px(34.56f)));
        if(action.isEnabled()&&firstAction==null)firstAction=action;
    }
    private void coverUpdated(String pkg){if(isDestroyed()||!active||!selected.equals(pkg))return;renderDetails();View cover=details.findViewWithTag("app-action-更换封面");if(cover!=null)cover.requestFocusFromTouch();else focusList();}
    private void restoreCover(AppRepository.App app){
        Sheet sheet=settingsSheet("恢复默认封面？");AppActionConfirmationUi.content(activity,sheet.delegate,app.pkg,app.label,AppActionConfirmationUi.RESTORE_COVER,false);Button cancel=sheet.action("取消",sheet.dialog::dismiss);
        Button confirm=sheet.action("恢复默认封面",()->{if(busy)return;busy=true;sheetBusy(sheet,true);worker.execute(()->{String failure=null;try{AppCoverPolicy.require(this,app.pkg);AppCoverStore.get(this).restore(app.pkg);}catch(java.io.IOException e){failure=e.getMessage();}String error=failure;ui(()->{busy=false;if(!active)return;sheetBusy(sheet,false);renderDetails();if(error==null){sheet.dialog.dismiss();success("已恢复默认封面");}else inlineFailure(sheet,new java.io.IOException(error));});});});AppActionConfirmationUi.actions(activity,sheet.delegate,cancel,confirm,false);sheet.show(cancel);
    }
    @Override public void handleBack(){if(details.hasFocus())focusList();else super.handleBack();}
    // Public Activity override; AndroidX core marks its inherited bridge class library-only.
    @android.annotation.SuppressLint("RestrictedApi")
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();
        if(event.getAction()==KeyEvent.ACTION_DOWN && list.hasFocus() && key==KeyEvent.KEYCODE_DPAD_RIGHT && firstAction!=null){firstAction.requestFocus();return true;}
        if(key==KeyEvent.KEYCODE_DPAD_UP&&event.getAction()==KeyEvent.ACTION_DOWN&&list.hasFocus()){
            RecyclerView.ViewHolder holder=list.findContainingViewHolder(getCurrentFocus());if(holder!=null&&holder.getBindingAdapterPosition()==0){filterButtons.get(filter).requestFocus();return true;}
        }
        if(key==KeyEvent.KEYCODE_DPAD_LEFT){
            if(event.getAction()==KeyEvent.ACTION_DOWN && details.hasFocus() && getCurrentFocus()==firstAction){returningToList=true;return true;}
            if(returningToList){if(event.getAction()==KeyEvent.ACTION_UP){returningToList=false;focusList();}return true;}
        }return false;
    }
    private final class SelectionSurface extends LayerDrawable {
        private final boolean filter;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean selected,focused;
        SelectionSurface(boolean filter){super(new Drawable[]{new SettingsFocusDrawable(unit,filter?999:12,Color.TRANSPARENT)});this.filter=filter;}
        @Override protected boolean onStateChange(int[] states){boolean window=false,focus=false;selected=false;int count=0;for(int value:states){if(value==android.R.attr.state_selected)selected=true;else count++;if(value==android.R.attr.state_focused)focus=true;if(value==android.R.attr.state_window_focused)window=true;}focused=focus&&window;int[] base=new int[count];int index=0;for(int value:states)if(value!=android.R.attr.state_selected)base[index++]=value;super.onStateChange(base);invalidateSelf();return true;}
        @Override public void draw(Canvas canvas){Rect bounds=getBounds();RectF area=new RectF(bounds);if(selected&&!filter&&!focused){paint.setStyle(Paint.Style.FILL);paint.setColor(0x0bffffff);SurfaceCorners.draw(canvas,area,px(12),px(12),paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.75f*unit);paint.setColor(UiPalette.EDGE);area.inset(.375f*unit,.375f*unit);SurfaceCorners.draw(canvas,area,px(12),px(12),paint);paint.setStyle(Paint.Style.FILL);}super.draw(canvas);if(selected&&filter){paint.setColor(focused?UiPalette.INK:UiPalette.TEXT);float width=px(18),height=px(3);area.set(bounds.exactCenterX()-width/2,bounds.bottom-height,bounds.exactCenterX()+width/2,bounds.bottom);SurfaceCorners.draw(canvas,area,height/2,height/2,paint);}}
    }
    private final class ActionFlow extends ViewGroup {
        private final ArrayList<Rect> positions=new ArrayList<>();
        ActionFlow(){super(activity);setClipChildren(false);setClipToPadding(false);}
        private void center(int first,int end,int width,int available){int offset=Math.max(0,(available-width)/2);for(int i=first;i<end;i++)positions.get(i).offset(offset,0);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){int available=MeasureSpec.getSize(widthSpec),x=0,y=0,rowHeight=0,first=0;positions.clear();for(int i=0;i<getChildCount();i++){View child=getChildAt(i);child.measure(MeasureSpec.makeMeasureSpec(available,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(px(34.56f),MeasureSpec.EXACTLY));int width=child.getMeasuredWidth(),height=child.getMeasuredHeight();if(x>0&&x+width>available){center(first,i,x-px(6),available);y+=rowHeight+px(6);x=0;rowHeight=0;first=i;}positions.add(new Rect(x,y,x+width,y+height));x+=width+px(6);rowHeight=Math.max(rowHeight,height);}center(first,getChildCount(),Math.max(0,x-px(6)),available);setMeasuredDimension(resolveSize(available,widthSpec),resolveSize(y+rowHeight,heightSpec));}
        @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){for(int i=0;i<getChildCount();i++){Rect p=positions.get(i);getChildAt(i).layout(p.left,p.top,p.right,p.bottom);}}
    }
    private final class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {
        final class Holder extends RecyclerView.ViewHolder {final LinearLayout box;final ImageView icon,marker;final TextView title,summary;Holder(LinearLayout box,ImageView icon,TextView title,TextView summary,ImageView marker){super(box);this.box=box;this.icon=icon;this.title=title;this.summary=summary;this.marker=marker;}}
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout box=row();box.setPadding(px(12.44f),px(6),px(12.44f),px(6));focusable(box,12);box.setFocusableInTouchMode(true);box.setBackgroundDrawable(new SelectionSurface(false));box.setClickable(true);box.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);SettingsTouchClick.attach(box);
            // Measure both text lines before applying the usual minimum row height.
            box.setMinimumHeight(px(60.025f));RecyclerView.LayoutParams p=new RecyclerView.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT);p.bottomMargin=px(6);box.setLayoutParams(p);ImageView art=appArt(null,38.4f);box.addView(art);
            LinearLayout copy=column();copy.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);cp.leftMargin=px(13.44f);box.addView(copy,cp);
            TextView title=label("",15);typography(title,15,21,UiPalette.TEXT);title.setDuplicateParentStateEnabled(true);copy.setDuplicateParentStateEnabled(true);title.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(title);gap(copy,4.8f);TextView summary=muted("",10);typography(summary,10.08f,14.1386f,UiPalette.SECONDARY);summary.setDuplicateParentStateEnabled(true);summary.setTextColor(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_focused,android.R.attr.state_window_focused},{}},new int[]{UiPalette.INK_SECONDARY,UiPalette.SECONDARY}));summary.setSingleLine();summary.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(summary);Drawable markerArt=androidx.core.graphics.drawable.DrawableCompat.wrap(new QuickControlIcon("pin")).mutate();androidx.core.graphics.drawable.DrawableCompat.setTintList(markerArt,SettingsFocusDrawable.textColors(UiPalette.TEXT));ImageView marker=icon(markerArt,10);marker.setDuplicateParentStateEnabled(true);marker.setAlpha(.6f);marker.setContentDescription("首页应用");box.addView(marker);return new Holder(box,art,title,summary,marker);
        }
        @Override public void onBindViewHolder(Holder holder,int position){AppRepository.App app=filtered.get(position);holder.icon.setImageDrawable(app.icon);artworkPadding(holder.icon,app,38.4f);holder.title.setText(app.label);holder.summary.setText(app.summary());boolean pinned=new DockStore(activity).load().contains(app.pkg);holder.marker.setVisibility(pinned?View.VISIBLE:View.GONE);holder.box.setTag("managed-app-"+app.pkg);holder.box.setSelected(app.pkg.equals(selected));holder.box.setContentDescription(app.label+"  "+app.pkg+"  "+app.summary()+(pinned?"  首页应用":""));
            holder.box.setOnFocusChangeListener((v,focus)->{if(focus && !selected.equals(app.pkg)){selected=app.pkg;for(int i=0;i<list.getChildCount();i++)list.getChildAt(i).setSelected(list.getChildAt(i)==v);renderDetails();}});
            holder.box.setOnClickListener(v->{holder.box.requestFocusFromTouch();if(!selected.equals(app.pkg)){selected=app.pkg;for(int i=0;i<list.getChildCount();i++)list.getChildAt(i).setSelected(list.getChildAt(i)==v);renderDetails();}if(firstAction!=null)firstAction.requestFocusFromTouch();KeyFeedback.confirm(activity);});
        }
        @Override public int getItemCount(){return filtered.size();}
    }
}
