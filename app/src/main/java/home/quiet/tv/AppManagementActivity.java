package home.quiet.tv;

import android.content.*;
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

public final class AppManagementActivity extends ManagementUi {
    private static final String[] FILTERS={"全部","用户应用","预装应用","已禁用","可恢复"};
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<AppRepository.App> all=new ArrayList<>(),filtered=new ArrayList<>();
    private AdbController adb;
    private AdbSession session;
    private AdbConnectionPanel connection;
    private AppRepository repository;
    private AppOperations operations;
    private Button status;
    private final ArrayList<Button> filterButtons=new ArrayList<>();
    private AppCoverUploadPanel coverUploads;
    private TextView count;
    private LinearLayout details;
    private RecyclerView list;
    private AppAdapter adapter;
    private View firstAction;
    private String selected="",connectionState="未连接";
    private int filter;
    private boolean busy,reading,refreshAfterConnection,active,returningToList,initialFocus=true;
    private volatile int refreshGeneration;
    private final BroadcastReceiver packagesChanged=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction()) && !intent.getBooleanExtra(Intent.EXTRA_REPLACING,false) && intent.getData()!=null){String pkg=intent.getData().getSchemeSpecificPart();new DockStore(context).remove(pkg);new HomeOrderStore(context).remove(pkg);worker.execute(()->{try{AppCoverStore.get(context).restore(pkg);}catch(java.io.IOException e){android.util.Log.w("QuietTV","Cover cleanup deferred");}});}
            if(active && !intent.getBooleanExtra(Intent.EXTRA_REPLACING,false))refresh();
        }
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);repository=new AppRepository(this);operations=new AppOperations(this);
        session=new AdbSession(this,new AdbSession.Listener(){
            public void changed(AdbState state){updateConnection(state);}
            public void connected(){if(active){if(busy)refreshAfterConnection=true;else refresh();}}
        });adb=session.adb;
        connection=new AdbConnectionPanel(this,adb,worker,session);coverUploads=new AppCoverUploadPanel(this);
        status=button("ADB 未连接",this::connectionPanel);status.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(12));status.setPadding(px(12),0,px(12),0);header.addView(status,new LinearLayout.LayoutParams(-2,px(30)));
        marginAdd(header,iconText("更多",R.drawable.ic_manage_more,this::morePanel),-2,30,8);
        marginAdd(header,iconText("关于",R.drawable.ic_about,()->startActivity(new Intent(this,AboutActivity.class))),-2,30,8);
        toolbar(false);
        LinearLayout panels=row();panels.setGravity(Gravity.TOP);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,0,1);pp.topMargin=px(23);root.addView(panels,pp);
        LinearLayout left=column();panels.addView(left,new LinearLayout.LayoutParams(0,-1,40));
        LinearLayout filters=row();LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,px(30));fp.bottomMargin=px(12);left.addView(filters,fp);
        for(int i=0;i<FILTERS.length;i++){final int chosen=i;Button option=button(FILTERS[i],()->{filter=chosen;applyFilter();});option.setId(View.generateViewId());styleTab(option,true);
            option.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(event.getAction()==KeyEvent.ACTION_DOWN)focusList();return true;}return false;});
            filterButtons.add(option);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-1);if(i>0)p.leftMargin=px(10);filters.addView(option,p);}
        for(int i=0;i<filterButtons.size();i++){Button b=filterButtons.get(i);if(i>0)b.setNextFocusLeftId(filterButtons.get(i-1).getId());if(i+1<filterButtons.size())b.setNextFocusRightId(filterButtons.get(i+1).getId());}
        LinearLayout caption=row();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,px(24));cp.bottomMargin=px(6);left.addView(caption,cp);
        count=muted("正在读取应用…",11);caption.addView(count,new LinearLayout.LayoutParams(0,-1,1));
        list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));adapter=new AppAdapter();list.setAdapter(adapter);list.setItemAnimator(null);list.setClipToPadding(false);list.setPadding(px(2),px(2),px(2),px(2));left.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);details=column();details.setGravity(Gravity.CENTER_HORIZONTAL);details.setPadding(px(25),px(27),px(25),px(22));details.setBackground(surface(0x12ffffff,0x06ffffff,0x1cffffff,20));scroll.addView(details);
        LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,-1,57);dp.leftMargin=px(28);panels.addView(scroll,dp);
        if(state!=null){selected=state.getString("selected","");filter=state.getInt("filter",0);}
        IntentFilter packages=new IntentFilter(Intent.ACTION_PACKAGE_ADDED);packages.addAction(Intent.ACTION_PACKAGE_REMOVED);packages.addAction(Intent.ACTION_PACKAGE_CHANGED);packages.addDataScheme("package");registerReceiver(packagesChanged,packages);
    }
    private void ui(Runnable action){runOnUiThread(()->{if(!isDestroyed() && !isFinishing())action.run();});}
    private void updateConnection(AdbState state){connectionState=state.description();status.setText(state.label());status.setTextColor(state.connection==AdbState.Connection.CONNECTED?0xffbacabd:TEXT);status.setContentDescription(state.description());}
    @Override protected void onResume(){super.onResume();active=true;refresh();session.resume();}
    @Override protected void onStop(){active=false;refreshAfterConnection=false;refreshGeneration++;if(reading){reading=false;busy=false;}connection.suspend();coverUploads.close();session.suspend();super.onStop();}
    @Override protected void onDestroy(){unregisterReceiver(packagesChanged);coverUploads.destroy();session.close();worker.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("selected",selected);state.putInt("filter",filter);super.onSaveInstanceState(state);}
    private void refresh(){
        if(busy)return;busy=true;reading=true;final int run=++refreshGeneration;count.setText("正在读取应用…");renderDetails();
        worker.execute(()->{if(run!=refreshGeneration)return;try{List<AppRepository.App> values=repository.load(adb);ui(()->{if(!active || run!=refreshGeneration)return;reading=false;busy=false;all.clear();all.addAll(values);applyFilter();if(refreshAfterConnection){refreshAfterConnection=false;refresh();}});}catch(Exception e){ui(()->{if(!active || run!=refreshGeneration)return;reading=false;busy=false;adbFailure(e);});}});
    }
    private void adbFailure(Exception e){session.reportFailure(e.getMessage()==null?"读取失败":e.getMessage());count.setText("读取失败，请重试或检查 ADB 连接");renderDetails();message(e.getMessage()==null?e.toString():e.getMessage());}
    private void morePanel(){Sheet sheet=new Sheet("更多");Button refresh=sheet.option("刷新应用列表",R.drawable.ic_manage_refresh,()->{sheet.dialog.dismiss();refresh();});sheet.option("开机自启",()->{sheet.dialog.dismiss();bootAppPanel();});sheet.option("开机广告",()->{sheet.dialog.dismiss();startActivity(new Intent(this,BootAdActivity.class));});sheet.option("首次设置",()->{sheet.dialog.dismiss();startActivity(new Intent(this,SetupActivity.class));});sheet.option("恢复原桌面",()->{sheet.dialog.dismiss();startActivity(new Intent(this,SetupActivity.class).putExtra("restore",true));});sheet.option("操作记录",R.drawable.ic_manage_history,()->{sheet.dialog.dismiss();showHistory();});sheet.action("关闭",sheet.dialog::dismiss);sheet.show(refresh);}
    // Each repository snapshot replaces app metadata as well as list membership/order.
    @android.annotation.SuppressLint("NotifyDataSetChanged")
    private void applyFilter(){
        boolean listFocused=initialFocus || list.hasFocus();filtered.clear();
        for(AppRepository.App app:all){boolean match=filter==0 || filter==1 && !app.system && app.installed || filter==2 && app.system || filter==3 && app.installed && !app.enabled || filter==4 && app.recoverable;if(match)filtered.add(app);}
        for(int i=0;i<filterButtons.size();i++)filterButtons.get(i).setSelected(i==filter);
        boolean present=false;for(AppRepository.App app:filtered)if(app.pkg.equals(selected))present=true;if(!present)selected=filtered.isEmpty()?"":filtered.get(0).pkg;
        count.setText(getString(R.string.management_app_count,filtered.size(),!adb.connected()?getString(R.string.management_local_suffix):""));adapter.notifyDataSetChanged();renderDetails();if(listFocused){initialFocus=false;focusList();}
    }
    private AppRepository.App current(){for(AppRepository.App app:filtered)if(app.pkg.equals(selected))return app;return null;}
    private CharSequence coloredState(AppRepository.App app,String text){
        String state=app.state();int start=text.indexOf(state);
        int color=!app.installed?(app.recoverable?0xff9cbfef:0xffe5b384):app.enabled?0xff9ccfae:0xffa7adb8;
        SpannableString label=new SpannableString(text);
        label.setSpan(new ForegroundColorSpan(color),start,start+state.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);return label;
    }
    private void focusList(){list.post(()->{for(int i=0;i<filtered.size();i++)if(filtered.get(i).pkg.equals(selected)){final int position=i;list.scrollToPosition(position);list.post(()->{RecyclerView.ViewHolder holder=list.findViewHolderForAdapterPosition(position);if(holder!=null)holder.itemView.requestFocus();});return;}filterButtons.get(filter).requestFocus();});}
    private void renderDetails(){
        if(details==null)return;details.removeAllViews();firstAction=null;AppRepository.App app=current();
        if(app==null){details.setGravity(Gravity.CENTER);details.addView(center(busy?"正在读取应用…":"没有符合条件的应用",20));return;}details.setGravity(Gravity.CENTER_HORIZONTAL);
        details.addView(icon(app.icon,67));gap(details,16);TextView title=center(app.label,26);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);details.addView(title,new LinearLayout.LayoutParams(-1,-2));
        gap(details,10);TextView state=center("",12);state.setText(coloredState(app,app.state()+"  "+(app.system?"预装应用":"用户应用")));state.setTextColor(0xffaab0b8);details.addView(state);
        String size=app.apkSizeBytes>=0?Formatter.formatShortFileSize(this,app.apkSizeBytes):"大小未知";
        gap(details,20);TextView meta=center(app.pkg+"\n版本 "+app.version+"  "+size,12);meta.setTextColor(MUTED);meta.setLineSpacing(px(5),1);details.addView(meta,new LinearLayout.LayoutParams(-1,-2));
        details.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));gap(details,18);
        if(!busy)bootAppAction(app);
        String protectedReason=operations.protection(app);
        if(AppCoverPolicy.ownReadOnly(this,app.pkg)){protectionNote(protectedReason,true);return;}
        boolean coverAllowed=AppCoverPolicy.allowed(this,app.pkg);
        if(!coverAllowed&&!protectedReason.isEmpty()){protectionNote(protectedReason,true);return;}
        LinearLayout actions=row();actions.setGravity(Gravity.CENTER);details.addView(actions,new LinearLayout.LayoutParams(-1,px(42)));
        if(busy){details.addView(center("正在核对实际状态…",12));return;}
        if(coverAllowed){
            addAction(actions,button("更换封面",()->coverUploads.show(app.pkg,app.label,()->coverUpdated(app.pkg))));
            Button restore=button("恢复默认",()->restoreCover(app));boolean custom=AppCoverStore.get(this).custom(app.pkg);restore.setEnabled(custom);restore.setFocusable(custom);restore.setAlpha(custom?1f:.4f);addAction(actions,restore);
        }
        if(protectedReason.isEmpty()){
            if(!adb.connected())addAction(actions,button("连接后管理",this::connectionPanel));
            else for(AppOperations.Action action:operations.actions(app)){
                String text=action==AppOperations.Action.UNINSTALL&&app.system?"为当前用户卸载":action==AppOperations.Action.RESTORE?action.label:action.label+"应用";
                Button b=button(text,()->confirm(app,action));if(action==AppOperations.Action.UNINSTALL)b.setTextColor(0xffe3aaa6);addAction(actions,b);
            }
        }
        gap(details,16);String text=!protectedReason.isEmpty()?protectedReason:adb.connected()&&operations.actions(app).isEmpty()?"当前系统没有已确认可用的应用状态操作。":app.recoverable?"恢复安装不会恢复此前删除的数据。":!adb.connected()?"连接本机 ADB 后，可管理此应用的状态。":"禁用可重新启用  卸载会删除当前用户的应用数据。";
        TextView hint=center(text,11);hint.setTextColor(MUTED);hint.setLineSpacing(px(4),1);details.addView(hint,new LinearLayout.LayoutParams(-1,-2));
    }
    private void bootAppAction(AppRepository.App app){
        BootAppStore store=new BootAppStore(this);boolean chosen=app.pkg.equals(store.target());
        if(!chosen && (!app.installed || !app.enabled || store.launchIntent(app.pkg)==null))return;
        LinearLayout row=row();row.setGravity(Gravity.CENTER);details.addView(row,new LinearLayout.LayoutParams(-1,px(38)));
        addAction(row,button(chosen?"关闭开机自启":"设为开机自启",()->{
            if(chosen){saveBootApp("","");return;}
            Sheet sheet=new Sheet("设为开机自启？");
            sheet.text("下次开机进入 Quiet TV 后，将自动打开 "+app.label+"。"+(store.target().isEmpty()?"":"将替换当前自启应用："+store.label()+"。")+"每次开机仅打开一次，儿童模式限制仍然有效。");
            Button cancel=sheet.action("取消",sheet.dialog::dismiss);
            sheet.action("开启",()->{sheet.dialog.dismiss();saveBootApp(app.pkg,app.label);});sheet.show(cancel);
        }));gap(details,12);
    }
    private void saveBootApp(String pkg,String label){
        try{new BootAppStore(this).select(pkg,label);renderDetails();if(firstAction!=null)firstAction.requestFocus();feedback(pkg.isEmpty()?"已关闭开机自启":"已设置，下次开机生效");}
        catch(java.io.IOException e){message(e.getMessage());}
    }
    private void bootAppPanel(){
        BootAppStore store=new BootAppStore(this);Sheet sheet=new Sheet("开机自启");
        sheet.text(store.target().isEmpty()?"未开启。选择应用后，在详情中设置开机自启。":"当前自启应用："+store.label()+"。可选择其他应用替换。");
        sheet.text("需让电视开机进入 Quiet TV。按 Home 返回桌面或待机唤醒时不会再次打开应用。");
        Button close=sheet.action("关闭",sheet.dialog::dismiss);
        if(!store.target().isEmpty())sheet.action("关闭开机自启",()->{sheet.dialog.dismiss();saveBootApp("","");});sheet.show(close);
    }
    private void protectionNote(String reason,boolean readOnly){TextView note=center(reason,12);note.setTextColor(0xffb9bec8);details.addView(note);if(readOnly){gap(details,8);details.addView(center("为保证电视正常使用，此应用仅供查看。",11));}}
    private void addAction(LinearLayout row,Button action){
        action.setSingleLine();action.setPadding(px(10),0,px(10),0);action.setId(View.generateViewId());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-1);if(row.getChildCount()>0)p.leftMargin=px(6);row.addView(action,p);
        if(action.isEnabled()&&firstAction==null)firstAction=action;
    }
    private void coverUpdated(String pkg){if(isDestroyed()||!selected.equals(pkg))return;renderDetails();if(firstAction!=null)firstAction.requestFocus();}
    private void restoreCover(AppRepository.App app){
        Sheet sheet=new Sheet("恢复默认封面？");sheet.text(app.label+" 将使用应用自带的封面。");sheet.action("取消",sheet.dialog::dismiss);
        sheet.action("恢复默认",()->{sheet.dialog.dismiss();busy=true;renderDetails();worker.execute(()->{String failure=null;try{AppCoverPolicy.require(this,app.pkg);AppCoverStore.get(this).restore(app.pkg);}catch(java.io.IOException e){failure=e.getMessage();}String error=failure;ui(()->{busy=false;coverUpdated(app.pkg);if(error==null)feedback("已恢复默认封面");else message(error);});});});sheet.show(null);
    }
    private void confirm(AppRepository.App app,AppOperations.Action action){
        if(busy)return;Sheet sheet=new Sheet(action.label+"  "+app.label);sheet.text(operations.explanation(app,action));gap(sheet.body,16);TextView meta=center(app.pkg+"\n当前用户 "+adb.user,11);meta.setTextColor(MUTED);sheet.body.addView(meta);
        Button cancel=sheet.action("取消",sheet.dialog::dismiss);Button confirm=sheet.action("确认"+action.label,()->{sheet.dialog.dismiss();perform(app,action);});if(action==AppOperations.Action.UNINSTALL)confirm.setTextColor(0xffe3aaa6);sheet.show(cancel);
    }
    private void perform(AppRepository.App app,AppOperations.Action action){
        if(busy)return;busy=true;count.setText(getString(R.string.management_action_progress,action.label));renderDetails();
        worker.execute(()->{try{String result=operations.perform(adb,app,action);ui(()->{busy=false;feedback(result);refresh();focusList();});}catch(Exception e){ui(()->{busy=false;message(e.getMessage());refresh();focusList();});}});
    }
    private void showHistory(){worker.execute(()->{try{String history=operations.history();ui(()->{Sheet sheet=new Sheet("操作记录");TextView text=muted(history,12);text.setLineSpacing(px(5),1);sheet.body.addView(text);Button close=sheet.action("关闭",sheet.dialog::dismiss);sheet.show(close);});}catch(Exception e){ui(()->message(e.getMessage()));}});}
    private void connectionPanel(){
        if(busy){message("正在读取或执行操作，请稍候");return;}
        if(adb.connected()){Sheet sheet=new Sheet("本机 ADB 连接");sheet.text("已连接本机  当前用户 "+adb.user);Button cancel=sheet.action("取消",sheet.dialog::dismiss);sheet.action("断开连接",()->{sheet.dialog.dismiss();session.pause();refresh();});sheet.show(cancel);}
        else connection.show(this::refresh);
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
        }return super.dispatchKeyEvent(event);
    }
    private final class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {
        final class Holder extends RecyclerView.ViewHolder {final LinearLayout box;final ImageView icon;final TextView title,summary;final ImageView marker;Holder(LinearLayout box,ImageView icon,TextView title,TextView summary,ImageView marker){super(box);this.box=box;this.icon=icon;this.title=title;this.summary=summary;this.marker=marker;}}
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout box=row();box.setPadding(px(12),px(8),px(12),px(8));focusable(box,12);box.setBackground(listSurface());box.setClickable(true);box.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            RecyclerView.LayoutParams p=new RecyclerView.LayoutParams(-1,px(60));p.bottomMargin=px(6);box.setLayoutParams(p);ImageView art=icon(null,39);box.addView(art);
            LinearLayout copy=column();copy.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-1,1);cp.leftMargin=px(14);box.addView(copy,cp);
            TextView title=label("",15);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(title);gap(copy,5);TextView summary=muted("",10);copy.addView(summary);ImageView marker=icon(UiCompat.drawable(AppManagementActivity.this,R.drawable.ic_manage_apps),14);marker.setAlpha(.6f);box.addView(marker);return new Holder(box,art,title,summary,marker);
        }
        @Override public void onBindViewHolder(Holder holder,int position){AppRepository.App app=filtered.get(position);holder.icon.setImageDrawable(app.icon);holder.title.setText(app.label);holder.summary.setText(coloredState(app,app.summary()));holder.marker.setVisibility(new DockStore(AppManagementActivity.this).load().contains(app.pkg)?View.VISIBLE:View.GONE);holder.box.setSelected(app.pkg.equals(selected));holder.box.setContentDescription(app.label+" "+app.pkg+" "+app.summary());
            holder.box.setOnFocusChangeListener((v,focus)->{if(focus && !selected.equals(app.pkg)){selected=app.pkg;for(int i=0;i<list.getChildCount();i++)list.getChildAt(i).setSelected(list.getChildAt(i)==v);renderDetails();}});
            holder.box.setOnClickListener(v->{selected=app.pkg;renderDetails();if(firstAction!=null)firstAction.requestFocus();});
        }
        @Override public int getItemCount(){return filtered.size();}
    }
}
