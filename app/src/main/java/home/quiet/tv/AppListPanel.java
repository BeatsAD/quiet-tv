package home.quiet.tv;

import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.format.Formatter;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.util.*;
import java.util.concurrent.*;

final class AppListPanel extends ManagementPanel {
    @Override boolean useSettingListStyle(){return false;}
    private static final String[] FILTERS={"全部","用户应用","预装应用","已禁用","可恢复","隐藏应用"};
    private static final int HIDDEN_FILTER=5;
    private final ManagementAdb session;
    private final ExecutorService worker;
    private final ArrayList<AppRepository.App> all=new ArrayList<>(),filtered=new ArrayList<>();
    private AppRepository repository;
    private AdbController adb;
    private Button managementConnection;
    private GradientDrawable connectionDot;
    private boolean connecting,manualConnection;
    private final ManagementAdb.Listener connectionChanged=this::managementConnectionChanged;
    private void managementConnectionChanged(){
        if(!active)return;
        updateManagementConnection();dirty=true;
        if(connecting){renderDetails();return;}
        if(busy)refreshAfterConnection=true;else refresh();
        if(manualConnection){manualConnection=false;if(!adb.connected())message(session.detail());}
    }
    private AppOperations operations;
    private final ArrayList<Button> filterButtons=new ArrayList<>();
    private AppCoverUploadPanel coverUploads;
    private LinearLayout details;
    private RecyclerView list;
    private AppAdapter adapter;
    private View firstAction;
    private String selected="";
    private int filter;
    private boolean dirty=true;
    private boolean busy,reading,refreshAfterConnection,active,returningToList;
    private volatile int refreshGeneration;
    private final BroadcastReceiver packagesChanged=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction()) && !intent.getBooleanExtra(Intent.EXTRA_REPLACING,false) && intent.getData()!=null){String pkg=intent.getData().getSchemeSpecificPart();new DockStore(context).remove(pkg);new HomeOrderStore(context).remove(pkg);new HomeVisibilityStore(context).remove(pkg);worker.execute(()->{try{AppCoverStore.get(context).restore(pkg);}catch(java.io.IOException e){android.util.Log.w("QuietTV","Cover cleanup deferred");}});}
            dirty=true;if(active && !intent.getBooleanExtra(Intent.EXTRA_REPLACING,false))refresh();
        }
    };
    AppListPanel(AppManagementActivity activity){super(activity);session=ManagementAdb.get(activity);worker=session.worker;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);repository=new AppRepository(this);operations=new AppOperations(this);
        adb=session.adb;
        coverUploads=new AppCoverUploadPanel(activity);
        LinearLayout body=managementContent(),filters=managementInfo;
        for(int i=0;i<FILTERS.length;i++){final int chosen=i;Button option=button(FILTERS[i],()->{filter=chosen;applyFilter();});option.setId(androidx.core.view.ViewCompat.generateViewId());styleManagementTab(option,true);
            option.setOnKeyListener((v,key,event)->{
                if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT&&key!=KeyEvent.KEYCODE_DPAD_UP&&key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;
                if(event.getAction()==KeyEvent.ACTION_UP)return true;
                if(key==KeyEvent.KEYCODE_DPAD_DOWN)focusList();
                else if(key==KeyEvent.KEYCODE_DPAD_UP)managementTab().requestFocus();
                else{
                    int next=Math.max(0,Math.min(filterButtons.size()-1,chosen+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1)));
                    if(next!=chosen){Button target=filterButtons.get(next);target.requestFocus();target.performClick();}
                    else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)managementConnection.requestFocus();
                }
                return true;
            });
            option.setNextFocusUpId(managementTab().getId());filterButtons.add(option);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-1);if(i>0)p.leftMargin=px(10);filters.addView(option,p);}
        for(int i=0;i<filterButtons.size();i++){Button b=filterButtons.get(i);if(i>0)b.setNextFocusLeftId(filterButtons.get(i-1).getId());if(i+1<filterButtons.size())b.setNextFocusRightId(filterButtons.get(i+1).getId());}
        filters.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        managementConnection=button("ADB 未连接",this::connectManagement);styleManagementTab(managementConnection,true);
        connectionDot=new GradientDrawable();connectionDot.setShape(GradientDrawable.OVAL);connectionDot.setBounds(0,0,px(5),px(5));
        managementConnection.setCompoundDrawables(connectionDot,null,null,null);managementConnection.setCompoundDrawablePadding(px(6));
        managementConnection.setOnKeyListener((v,key,event)->{
            if(key==KeyEvent.KEYCODE_DPAD_LEFT){if(event.getAction()==KeyEvent.ACTION_DOWN)filterButtons.get(filterButtons.size()-1).requestFocus();return true;}return false;
        });
        LinearLayout.LayoutParams connectionParams=new LinearLayout.LayoutParams(-2,-1);connectionParams.leftMargin=px(10);filters.addView(managementConnection,connectionParams);
        updateManagementConnection();
        LinearLayout panels=row();panels.setGravity(Gravity.TOP);body.addView(panels,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout left=column();panels.addView(left,new LinearLayout.LayoutParams(0,-1,40));
        list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));adapter=new AppAdapter();list.setAdapter(adapter);list.setItemAnimator(null);list.setClipToPadding(false);list.setPadding(px(2),px(2),px(2),px(2));left.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setBackgroundDrawable(surface(0x12ffffff,0x06ffffff,0x1cffffff,20));details=column();details.setGravity(Gravity.CENTER_HORIZONTAL);details.setPadding(px(25),px(27),px(25),px(22));scroll.addView(details);
        LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,-1,57);dp.leftMargin=px(28);panels.addView(scroll,dp);
        if(state!=null){selected=state.getString("selected","");filter=Math.max(0,Math.min(FILTERS.length-1,state.getInt("filter",0)));}
        else{String requested=activity.getIntent().getStringExtra("selected-package");if(requested!=null)selected=requested;}
        IntentFilter packages=new IntentFilter(Intent.ACTION_PACKAGE_ADDED);packages.addAction(Intent.ACTION_PACKAGE_REMOVED);packages.addAction(Intent.ACTION_PACKAGE_CHANGED);packages.addDataScheme("package");registerReceiver(packagesChanged,packages);
    }
    private void ui(Runnable action){runOnUiThread(()->{if(!isDestroyed() && !isFinishing())action.run();});}
    private void updateManagementConnection(){
        if(managementConnection==null)return;connecting=session.state()==AdbConnectionKeeper.State.CONNECTING;boolean connected=session.state()==AdbConnectionKeeper.State.CONNECTED;
        managementConnection.setText(connecting?"ADB 连接中…":connected?"ADB 已连接":"ADB 未连接");
        int color=connecting?0xffe5b384:connected?0xff64c49a:MUTED;
        connectionDot.setColor(color);
        managementConnection.setTextColor(color);
        managementConnection.setContentDescription(managementConnection.getText()+"  "+session.detail());
        managementConnection.setEnabled(!busy && !connecting);
    }
    @Override void managementConnected(){dirty=true;if(active){if(busy)refreshAfterConnection=true;else refresh();}}
    @Override void managementMoreOptions(LinearLayout menu){managementMoreOption(menu,"开机自启",this::bootAppPanel);managementMoreOption(menu,"操作记录",()->{try{Sheet sheet=new Sheet("操作记录");sheet.text(operations.history());sheet.action("关闭",sheet.dialog::dismiss);sheet.show(null);}catch(Exception e){message(e.getMessage());}});super.managementMoreOptions(menu);}
    @Override protected void onResume(){super.onResume();active=true;session.addListener(connectionChanged);session.request();if(dirty)refresh();else renderDetails();}
    @Override void focusManagementContent(){filterButtons.get(filter).requestFocus();}
    @Override protected void onStop(){active=false;session.removeListener(connectionChanged);manualConnection=false;refreshAfterConnection=false;refreshGeneration++;dirty=true;if(reading){reading=false;busy=false;}coverUploads.close();super.onStop();}
    @Override protected void onDestroy(){session.removeListener(connectionChanged);unregisterReceiver(packagesChanged);coverUploads.destroy();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("selected",selected);state.putInt("filter",filter);super.onSaveInstanceState(state);}
    private void refresh(){
        if(!active){dirty=true;return;}
        if(busy)return;busy=true;reading=true;final int run=++refreshGeneration;renderDetails();
        worker.execute(()->{if(run!=refreshGeneration)return;try{List<AppRepository.App> values=repository.load(adb);ui(()->{if(!active || run!=refreshGeneration)return;reading=false;busy=false;dirty=false;operations=new AppOperations(this);all.clear();all.addAll(values);applyFilter();if(refreshAfterConnection){refreshAfterConnection=false;refresh();}});}catch(Exception e){ui(()->{if(!active || run!=refreshGeneration)return;reading=false;busy=false;loadFailure(e);});}});
    }
    private void loadFailure(Exception e){renderDetails();message(e.getMessage()==null?e.toString():e.getMessage());}
    // Each repository snapshot replaces app metadata as well as list membership/order.
    @android.annotation.SuppressLint("NotifyDataSetChanged")
    private void applyFilter(){
        boolean listFocused=list.hasFocus();filtered.clear();
        HomeVisibilityStore visibility=new HomeVisibilityStore(this);ChildModeStore children=new ChildModeStore(this);
        for(AppRepository.App app:all){boolean match=filter==0 || filter==1 && !app.system && app.installed || filter==2 && app.system || filter==3 && app.installed && !app.enabled || filter==4 && app.recoverable && !app.installed || filter==HIDDEN_FILTER && app.installed && visibility.hidden(app.pkg) && !children.hidden(app.pkg);if(match)filtered.add(app);}
        for(int i=0;i<filterButtons.size();i++)filterButtons.get(i).setSelected(i==filter);
        boolean present=false;for(AppRepository.App app:filtered)if(app.pkg.equals(selected))present=true;if(!present)selected=filtered.isEmpty()?"":filtered.get(0).pkg;
        adapter.notifyDataSetChanged();renderDetails();if(listFocused)focusList();
    }
    private AppRepository.App current(){for(AppRepository.App app:filtered)if(app.pkg.equals(selected))return app;return null;}
    private CharSequence coloredState(AppRepository.App app,String text){
        String state=app.state();int start=text.indexOf(state);
        int color=!app.installed?(app.recoverable?0xff9cbfef:0xffe5b384):app.enabled?0xff9ccfae:0xffa7adb8;
        SpannableString label=new SpannableString(text);
        label.setSpan(new ForegroundColorSpan(color),start,start+state.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);return label;
    }
    private void focusList(){list.post(()->{if(managementTabFocused() || !active)return;for(int i=0;i<filtered.size();i++)if(filtered.get(i).pkg.equals(selected)){final int position=i;list.scrollToPosition(position);list.post(()->{if(managementTabFocused() || !active)return;RecyclerView.ViewHolder holder=list.findViewHolderForAdapterPosition(position);if(holder!=null)holder.itemView.requestFocus();});return;}filterButtons.get(filter).requestFocus();});}
    private void renderDetails(){
        if(details==null)return;updateManagementConnection();details.removeAllViews();firstAction=null;AppRepository.App app=current();
        if(app==null){details.setGravity(Gravity.CENTER);details.addView(center(busy?"正在读取应用…":"没有符合条件的应用",20));return;}details.setGravity(Gravity.CENTER_HORIZONTAL);
        details.addView(icon(app.icon,67));gap(details,16);TextView title=center(app.label,26);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);details.addView(title,new LinearLayout.LayoutParams(-1,-2));
        gap(details,10);TextView state=center("",12);state.setText(coloredState(app,app.state()+"  "+(app.system?"预装应用":"用户应用")));state.setTextColor(0xffaab0b8);details.addView(state);
        String size=app.apkSizeBytes>=0?Formatter.formatShortFileSize(this,app.apkSizeBytes):"大小未知";
        gap(details,20);TextView meta=center(app.pkg+"\n版本 "+app.version+"  "+size,12);meta.setTextColor(MUTED);meta.setLineSpacing(px(5),1);details.addView(meta,new LinearLayout.LayoutParams(-1,-2));
        details.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));gap(details,18);
        if(!busy && filter==HIDDEN_FILTER){
            LinearLayout restore=row();restore.setGravity(Gravity.CENTER);details.addView(restore,new LinearLayout.LayoutParams(-1,px(42)));
            addAction(restore,button("恢复显示",()->restoreHiddenApp(app)));gap(details,10);
        }
        LinearLayout actions=row();actions.setGravity(Gravity.CENTER);details.addView(actions,new LinearLayout.LayoutParams(-1,px(42)));
        if(!busy)bootAppAction(app,actions);
        String protectedReason=operations.protection(app);
        if(AppCoverPolicy.ownReadOnly(this,app.pkg)){protectionNote(protectedReason,true);return;}
        boolean coverAllowed=AppCoverPolicy.allowed(this,app.pkg);
        if(!coverAllowed&&!protectedReason.isEmpty()){protectionNote(protectedReason,true);return;}
        if(busy){details.addView(center("正在核对实际状态…",12));return;}
        if(coverAllowed){
            addAction(actions,button("更换封面",()->coverUploads.show(app.pkg,app.label,()->coverUpdated(app.pkg))));
            Button restore=button("恢复默认",()->restoreCover(app));boolean custom=AppCoverStore.get(this).custom(app.pkg);restore.setEnabled(custom);restore.setFocusable(custom);restore.setAlpha(custom?1f:.4f);addAction(actions,restore);
        }
        List<AppOperations.Action> available=operations.actions(app);
        if(!available.isEmpty()){
            for(AppOperations.Action action:available)addAction(actions,button(action.label,()->confirmOperation(app,action)));
        }else if(app.system && protectedReason.isEmpty()){
            gap(details,10);details.addView(center(adb.connected()?"本机规则或系统暂不支持此操作":"连接管理后可查看可用操作",11));
        }
        if(!adb.connected() && !app.system && app.installed && protectedReason.isEmpty()){
            Button uninstall=button("卸载应用",()->{
                Intent intent=new Intent(Intent.ACTION_DELETE,android.net.Uri.parse("package:"+app.pkg));
                if(!ExternalActivityLauncher.open(activity,"app.uninstall",intent))message("系统卸载入口不可用。");
                dirty=true;
            });addAction(actions,uninstall);
        }

    }
    private void restoreHiddenApp(AppRepository.App app){
        if(busy)return;busy=true;renderDetails();
        worker.execute(()->{
            String result;
            try{
                HomeVisibilityStore visibility=new HomeVisibilityStore(this);
                if(!visibility.hidden(app.pkg)||new ChildModeStore(this).hidden(app.pkg)||!AppAvailability.installed(getPackageManager(),getPackageManager().getApplicationInfo(app.pkg,0)))result="应用状态已变化，请刷新后重试";
                else{boolean full=visibility.wasPinned(app.pkg)&&new DockStore(this).load().size()>=DockStore.CAPACITY;
                    result=visibility.restore(app.pkg)?full?"已恢复到第二屏，首页已满":"已恢复显示":"保存失败，请重试";}
            }catch(Exception e){result="无法恢复显示，请重试";}
            String outcome=result;ui(()->{busy=false;dirty=true;if(active){applyFilter();focusList();feedback(outcome);}});
        });
    }
    private void connectManagement(){
        if(busy || connecting || !active)return;
        manualConnection=!adb.connected();session.request();updateManagementConnection();
    }
    private void confirmOperation(AppRepository.App app,AppOperations.Action action){
        Sheet sheet=new Sheet(action.label+"应用？");sheet.text(app.label+"\n"+operations.explanation(app,action));
        Button cancel=sheet.action("取消",sheet.dialog::dismiss);
        sheet.action("确认"+action.label,()->{
            sheet.dialog.dismiss();if(busy)return;busy=true;renderDetails();
            worker.execute(()->{
                String result;
                try{result=new AppOperations(this).perform(adb,app,action);}catch(Exception e){result=e.getMessage()==null?"操作未完成":e.getMessage();}
                String outcome=result;ui(()->{busy=false;dirty=true;if(active){refresh();message(outcome);}});
            });
        });sheet.show(cancel);
    }
    private void bootAppAction(AppRepository.App app,LinearLayout actions){
        BootAppStore store=new BootAppStore(this);boolean chosen=app.pkg.equals(store.target());
        if(!chosen && (!app.installed || !app.enabled || store.launchIntent(app.pkg)==null))return;
        addAction(actions,button(chosen?"关闭开机自启":"设为开机自启",()->{
            if(chosen){saveBootApp("","");return;}
            Sheet sheet=new Sheet("设为开机自启？");
            sheet.text("开机或待机唤醒进入 Quiet TV 后，将自动打开 "+app.label+"。"+(store.target().isEmpty()?"":"将替换当前自启应用："+store.label()+"。")+"每次仅打开一次，按 Home 返回不重复，儿童模式限制仍然有效。");
            Button cancel=sheet.action("取消",sheet.dialog::dismiss);
            sheet.action("开启",()->{sheet.dialog.dismiss();saveBootApp(app.pkg,app.label);});sheet.show(cancel);
        }));
    }
    private void saveBootApp(String pkg,String label){
        try{new BootAppStore(this).select(pkg,label);renderDetails();if(firstAction!=null)firstAction.requestFocus();feedback(pkg.isEmpty()?"已关闭开机自启":"已设置，下次开机或待机唤醒生效");}
        catch(java.io.IOException e){message(e.getMessage());}
    }
    private void bootAppPanel(){
        BootAppStore store=new BootAppStore(this);Sheet sheet=new Sheet("开机自启");
        sheet.text(store.target().isEmpty()?"未开启。选择应用后，在详情中设置开机自启。":"当前自启应用："+store.label()+"。可选择其他应用替换。");
        sheet.text("开机或待机唤醒进入 Quiet TV 后生效。按 Home 返回桌面不会重复打开应用。");
        Button close=sheet.action("关闭",sheet.dialog::dismiss);
        if(!store.target().isEmpty())sheet.action("关闭开机自启",()->{sheet.dialog.dismiss();saveBootApp("","");});sheet.show(close);
    }
    private void protectionNote(String reason,boolean readOnly){TextView note=center(reason,12);note.setTextColor(0xffb9bec8);details.addView(note);if(readOnly){gap(details,8);details.addView(center("为保证电视正常使用，此应用仅供查看。",11));}}
    private void addAction(LinearLayout row,Button action){
        action.setSingleLine();action.setPadding(px(10),0,px(10),0);action.setId(androidx.core.view.ViewCompat.generateViewId());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-1);if(row.getChildCount()>0)p.leftMargin=px(6);row.addView(action,p);
        if(action.isEnabled()&&firstAction==null)firstAction=action;
    }
    private void coverUpdated(String pkg){if(isDestroyed()||!selected.equals(pkg))return;renderDetails();if(firstAction!=null)firstAction.requestFocus();}
    private void restoreCover(AppRepository.App app){
        Sheet sheet=new Sheet("恢复默认封面？");sheet.text(app.label+" 将使用应用自带的封面。");sheet.action("取消",sheet.dialog::dismiss);
        sheet.action("恢复默认",()->{sheet.dialog.dismiss();busy=true;renderDetails();worker.execute(()->{String failure=null;try{AppCoverPolicy.require(this,app.pkg);AppCoverStore.get(this).restore(app.pkg);}catch(java.io.IOException e){failure=e.getMessage();}String error=failure;ui(()->{busy=false;coverUpdated(app.pkg);if(error==null)feedback("已恢复默认封面");else message(error);});});});sheet.show(null);
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
    private final class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {
        final class Holder extends RecyclerView.ViewHolder {final LinearLayout box;final ImageView icon;final TextView title,summary;final ImageView marker;Holder(LinearLayout box,ImageView icon,TextView title,TextView summary,ImageView marker){super(box);this.box=box;this.icon=icon;this.title=title;this.summary=summary;this.marker=marker;}}
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout box=row();box.setPadding(px(12),px(8),px(12),px(8));focusable(box,12);box.setBackgroundDrawable(listSurface());box.setClickable(true);box.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            RecyclerView.LayoutParams p=new RecyclerView.LayoutParams(-1,px(60));p.bottomMargin=px(6);box.setLayoutParams(p);ImageView art=icon(null,39);box.addView(art);
            LinearLayout copy=column();copy.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-1,1);cp.leftMargin=px(14);box.addView(copy,cp);
            TextView title=label("",15);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(title);gap(copy,5);TextView summary=muted("",10);copy.addView(summary);ImageView marker=icon(UiCompat.drawable(activity,R.drawable.ic_manage_apps),14);marker.setAlpha(.6f);box.addView(marker);return new Holder(box,art,title,summary,marker);
        }
        @Override public void onBindViewHolder(Holder holder,int position){AppRepository.App app=filtered.get(position);holder.icon.setImageDrawable(app.icon);holder.title.setText(app.label);holder.summary.setText(coloredState(app,app.summary()));holder.marker.setVisibility(new DockStore(activity).load().contains(app.pkg)?View.VISIBLE:View.GONE);holder.box.setSelected(app.pkg.equals(selected));holder.box.setContentDescription(app.label+" "+app.pkg+" "+app.summary());
            holder.box.setOnFocusChangeListener((v,focus)->{if(focus && !selected.equals(app.pkg)){selected=app.pkg;for(int i=0;i<list.getChildCount();i++)list.getChildAt(i).setSelected(list.getChildAt(i)==v);renderDetails();}});
            holder.box.setOnClickListener(v->{selected=app.pkg;renderDetails();if(firstAction!=null)firstAction.requestFocus();KeyFeedback.confirm(activity);});
        }
        @Override public int getItemCount(){return filtered.size();}
    }
}
