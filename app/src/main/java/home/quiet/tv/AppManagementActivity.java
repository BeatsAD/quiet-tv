package home.quiet.tv;

import android.content.*;
import android.os.*;
import android.provider.Settings;
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
    private AdbConnectionPanel connection;
    private AppRepository repository;
    private AppOperations operations;
    private Button status,filterButton,searchButton,queryChip;
    private TextView count;
    private LinearLayout details;
    private RecyclerView list;
    private AppAdapter adapter;
    private View firstAction;
    private String selected="",query="",connectionState="未连接";
    private int filter;
    private boolean busy,connecting,active,returningToList,initialFocus=true;
    private final BroadcastReceiver packagesChanged=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction()) && !intent.getBooleanExtra(Intent.EXTRA_REPLACING,false) && intent.getData()!=null)new DockStore(context).remove(intent.getData().getSchemeSpecificPart());
            if(active && !intent.getBooleanExtra(Intent.EXTRA_REPLACING,false))refresh();
        }
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);repository=new AppRepository(this);operations=new AppOperations(this);
        adb=new AdbController(this,value->ui(()->updateConnection(value)));
        connection=new AdbConnectionPanel(this,adb,worker);
        status=button("●  ADB 未连接",this::connectionPanel);status.setText(dotted("ADB 未连接"));status.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(12));header.addView(status,new LinearLayout.LayoutParams(px(150),px(36)));
        LinearLayout tools=toolbar(false);
        searchButton=iconText("搜索",R.drawable.ic_manage_search,this::searchPanel);tools.addView(searchButton,new LinearLayout.LayoutParams(-2,px(33)));
        filterButton=iconText("筛选",R.drawable.ic_manage_filter,this::chooseFilter);marginAdd(tools,filterButton,-2,33,8);
        marginAdd(tools,iconButton("更多",R.drawable.ic_manage_more,this::morePanel),40,33,8);
        LinearLayout panels=row();panels.setGravity(Gravity.TOP);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,0,1);pp.topMargin=px(23);root.addView(panels,pp);
        LinearLayout left=column();panels.addView(left,new LinearLayout.LayoutParams(0,-1,40));
        LinearLayout caption=row();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,px(24));cp.bottomMargin=px(6);left.addView(caption,cp);
        count=muted("正在读取应用…",11);caption.addView(count,new LinearLayout.LayoutParams(0,-1,1));queryChip=button("",()->{query="";applyFilter();focusList();});queryChip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(10));queryChip.setSingleLine();queryChip.setEllipsize(android.text.TextUtils.TruncateAt.END);queryChip.setMaxWidth(px(185));queryChip.setVisibility(View.GONE);caption.addView(queryChip,new LinearLayout.LayoutParams(-2,px(24)));
        list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));adapter=new AppAdapter();list.setAdapter(adapter);list.setItemAnimator(null);list.setClipToPadding(false);list.setPadding(px(2),px(2),px(2),px(2));left.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);details=column();details.setGravity(Gravity.CENTER_HORIZONTAL);details.setPadding(px(25),px(27),px(25),px(22));details.setBackground(surface(0x12ffffff,0x06ffffff,0x1cffffff,20));scroll.addView(details);
        LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,-1,57);dp.leftMargin=px(28);panels.addView(scroll,dp);
        footer("方向键选择  ·  确定操作  ·  返回退出");
        if(state!=null){selected=state.getString("selected","");filter=state.getInt("filter",0);query=state.getString("query","");}
        IntentFilter packages=new IntentFilter(Intent.ACTION_PACKAGE_ADDED);packages.addAction(Intent.ACTION_PACKAGE_REMOVED);packages.addAction(Intent.ACTION_PACKAGE_CHANGED);packages.addDataScheme("package");registerReceiver(packagesChanged,packages);
    }
    private void ui(Runnable action){runOnUiThread(()->{if(!isDestroyed() && !isFinishing())action.run();});}
    private void updateConnection(String value){connectionState=value;String shortState=adb.connected()?"已连接":value.startsWith("等待授权")?"等待授权":value.startsWith("连接中")?"连接中":value.contains("失败")?"连接失败":"未连接";status.setText(dotted("ADB "+shortState));status.setTextColor(adb.connected()?0xffbacabd:TEXT);status.setContentDescription("ADB "+value);}
    @Override protected void onResume(){super.onResume();active=true;updateConnection(adb.connected()?"已连接":"未连接");refresh();}
    @Override protected void onStop(){active=false;connection.close();if(!connecting)worker.execute(()->{adb.disconnect();ui(()->updateConnection("未连接"));});super.onStop();}
    @Override protected void onDestroy(){unregisterReceiver(packagesChanged);adb.close();worker.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("selected",selected);state.putString("query",query);state.putInt("filter",filter);super.onSaveInstanceState(state);}
    private void refresh(){
        if(busy)return;busy=true;count.setText("正在读取应用…");renderDetails();
        worker.execute(()->{try{List<AppRepository.App> values=repository.load(adb);ui(()->{busy=false;all.clear();all.addAll(values);applyFilter();});}catch(Exception e){ui(()->{busy=false;adbFailure(e);});}});
    }
    private void adbFailure(Exception e){updateConnection("连接失败");count.setText("读取失败，请重新连接后刷新");renderDetails();message(e.getMessage()==null?e.toString():e.getMessage());}
    private void searchPanel(){
        Sheet sheet=new Sheet("搜索应用");sheet.text("输入应用名称或包名");gap(sheet.body,16);EditText input=input(query,"搜索应用名或包名");sheet.body.addView(input,new LinearLayout.LayoutParams(-1,px(44)));
        sheet.action("清空",()->{query="";sheet.dialog.dismiss();applyFilter();focusList();});
        sheet.action("搜索",()->{query=input.getText().toString().trim();sheet.dialog.dismiss();applyFilter();focusList();});
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);input.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){query=input.getText().toString().trim();sheet.dialog.dismiss();applyFilter();focusList();return true;}return false;});sheet.show(input);
    }
    private void chooseFilter(){Sheet sheet=new Sheet("筛选应用");View focus=null;for(int i=0;i<FILTERS.length;i++){final int which=i;Button option=sheet.option(FILTERS[i]+(i==filter?"  ✓":""),()->{filter=which;sheet.dialog.dismiss();applyFilter();focusList();});if(i==filter)focus=option;}sheet.action("取消",sheet.dialog::dismiss);sheet.show(focus);}
    private void morePanel(){Sheet sheet=new Sheet("更多");Button refresh=sheet.option("刷新应用列表",R.drawable.ic_manage_refresh,()->{sheet.dialog.dismiss();refresh();});sheet.option("首次设置",()->{sheet.dialog.dismiss();startActivity(new Intent(this,SetupActivity.class));});sheet.option("恢复原桌面",()->{sheet.dialog.dismiss();startActivity(new Intent(this,SetupActivity.class).putExtra("restore",true));});sheet.option("操作记录",R.drawable.ic_manage_history,()->{sheet.dialog.dismiss();showHistory();});sheet.action("关闭",sheet.dialog::dismiss);sheet.show(refresh);}
    private void applyFilter(){
        boolean listFocused=initialFocus || list.hasFocus();filtered.clear();String matchQuery=query.toLowerCase(Locale.ROOT);
        for(AppRepository.App app:all){boolean match=filter==0 || filter==1 && !app.system && app.installed || filter==2 && app.system || filter==3 && app.installed && !app.enabled || filter==4 && app.recoverable;if(match && (app.label.toLowerCase(Locale.ROOT).contains(matchQuery)||app.pkg.toLowerCase(Locale.ROOT).contains(matchQuery)))filtered.add(app);}
        filterButton.setText(filter==0?"筛选":FILTERS[filter]);searchButton.setText(query.isEmpty()?"搜索":"搜索 · 已筛选");
        boolean present=false;for(AppRepository.App app:filtered)if(app.pkg.equals(selected))present=true;if(!present)selected=filtered.isEmpty()?"":filtered.get(0).pkg;
        queryChip.setVisibility(query.isEmpty()?View.GONE:View.VISIBLE);queryChip.setText(query+"  ×");queryChip.setContentDescription("清除搜索："+query);count.setText(filtered.size()+" 个应用"+(!adb.connected() && query.isEmpty()?" · 本地信息":""));adapter.notifyDataSetChanged();renderDetails();if(listFocused){initialFocus=false;focusList();}
    }
    private AppRepository.App current(){for(AppRepository.App app:filtered)if(app.pkg.equals(selected))return app;return null;}
    private void focusList(){list.post(()->{for(int i=0;i<filtered.size();i++)if(filtered.get(i).pkg.equals(selected)){final int position=i;list.scrollToPosition(position);list.post(()->{RecyclerView.ViewHolder holder=list.findViewHolderForAdapterPosition(position);if(holder!=null)holder.itemView.requestFocus();});return;}searchButton.requestFocus();});}
    private void renderDetails(){
        if(details==null)return;details.removeAllViews();firstAction=null;AppRepository.App app=current();
        if(app==null){details.setGravity(Gravity.CENTER);details.addView(center(busy?"正在读取应用…":"没有符合条件的应用",20));if(!busy){gap(details,12);details.addView(muted("试试其他名称或筛选条件",12));}return;}details.setGravity(Gravity.CENTER_HORIZONTAL);
        details.addView(icon(app.icon,67));gap(details,16);TextView title=center(app.label,26);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);details.addView(title,new LinearLayout.LayoutParams(-1,-2));
        gap(details,10);TextView state=center("●  "+app.state()+"   ·   "+(app.system?"预装应用":"用户应用"),12);state.setText(dotted(app.state()+"   ·   "+(app.system?"预装应用":"用户应用")));state.setTextColor(0xffaab0b8);details.addView(state);
        gap(details,20);TextView meta=center(app.pkg+"\n版本 "+app.version,12);meta.setTextColor(MUTED);meta.setLineSpacing(px(5),1);details.addView(meta,new LinearLayout.LayoutParams(-1,-2));
        details.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));gap(details,18);
        String protectedReason=operations.protection(app);
        if(!protectedReason.isEmpty()){ImageView shield=icon(getDrawable(R.drawable.ic_manage_shield),22);shield.setAlpha(.65f);details.addView(shield);gap(details,10);TextView note=center(protectedReason,12);note.setTextColor(0xffb9bec8);details.addView(note);gap(details,8);details.addView(center("为保证电视正常使用，此应用仅供查看。",11));return;}
        LinearLayout actions=row();actions.setGravity(Gravity.CENTER);details.addView(actions,new LinearLayout.LayoutParams(-1,px(42)));
        if(busy){details.addView(center(connecting?"正在连接，请在电视弹窗允许授权…":"正在核对实际状态…",12));return;}
        if(!adb.connected()){Button connect=button("连接后管理",this::connectionPanel);actions.addView(connect,new LinearLayout.LayoutParams(-2,-1));firstAction=connect;}
        else for(AppOperations.Action action:operations.actions(app)){
            String text=action==AppOperations.Action.UNINSTALL && app.system?"为当前用户卸载":action==AppOperations.Action.RESTORE?action.label:action.label+"应用";
            Button b=iconText(text,action==AppOperations.Action.UNINSTALL?R.drawable.ic_manage_trash:R.drawable.ic_manage_power,()->confirm(app,action));if(action==AppOperations.Action.UNINSTALL)b.setTextColor(0xffe3aaa6);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-1);p.setMargins(px(5),0,px(5),0);actions.addView(b,p);if(firstAction==null)firstAction=b;
        }
        gap(details,16);TextView hint=center(app.recoverable?"恢复安装不会恢复此前删除的数据。":!adb.connected()?"连接本机 ADB 后，可管理此应用的状态。":"禁用可重新启用；卸载会删除当前用户的应用数据。",11);hint.setTextColor(MUTED);hint.setLineSpacing(px(4),1);details.addView(hint,new LinearLayout.LayoutParams(-1,-2));
    }
    private void confirm(AppRepository.App app,AppOperations.Action action){
        if(busy)return;Sheet sheet=new Sheet(action.label+" · "+app.label);sheet.text(operations.explanation(app,action));gap(sheet.body,16);TextView meta=center(app.pkg+"\n当前用户 "+adb.user,11);meta.setTextColor(MUTED);sheet.body.addView(meta);
        Button cancel=sheet.action("取消",sheet.dialog::dismiss);Button confirm=sheet.action("确认"+action.label,()->{sheet.dialog.dismiss();perform(app,action);});if(action==AppOperations.Action.UNINSTALL)confirm.setTextColor(0xffe3aaa6);sheet.show(cancel);
    }
    private void perform(AppRepository.App app,AppOperations.Action action){
        if(busy)return;busy=true;count.setText("正在"+action.label+"并核对实际状态…");renderDetails();
        worker.execute(()->{try{String result=operations.perform(adb,app,action);ui(()->{busy=false;feedback(result);refresh();focusList();});}catch(Exception e){ui(()->{busy=false;message(e.getMessage());refresh();focusList();});}});
    }
    private void showHistory(){worker.execute(()->{try{String history=operations.history();ui(()->{Sheet sheet=new Sheet("操作记录");TextView text=muted(history,12);text.setLineSpacing(px(5),1);sheet.body.addView(text);Button close=sheet.action("关闭",sheet.dialog::dismiss);sheet.show(close);});}catch(Exception e){ui(()->message(e.getMessage()));}});}
    private void connectionPanel(){
        if(busy){message("正在读取或执行操作，请稍候");return;}
        if(adb.connected()){Sheet sheet=new Sheet("本机 ADB 连接");sheet.text("已连接本机 · 当前用户 "+adb.user);Button cancel=sheet.action("取消",sheet.dialog::dismiss);sheet.action("断开连接",()->{sheet.dialog.dismiss();adb.disconnect();updateConnection("未连接");refresh();});sheet.show(cancel);}
        else connection.show(()->{updateConnection("已连接");refresh();});
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();
        if(event.getAction()==KeyEvent.ACTION_DOWN && list.hasFocus() && key==KeyEvent.KEYCODE_DPAD_RIGHT && firstAction!=null){firstAction.requestFocus();return true;}
        if(key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_BACK){
            if(event.getAction()==KeyEvent.ACTION_DOWN && details.hasFocus() && (key==KeyEvent.KEYCODE_BACK || getCurrentFocus()==firstAction)){returningToList=true;return true;}
            if(returningToList){if(event.getAction()==KeyEvent.ACTION_UP){returningToList=false;focusList();}return true;}
        }return super.dispatchKeyEvent(event);
    }
    private final class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {
        final class Holder extends RecyclerView.ViewHolder {final LinearLayout box;final ImageView icon;final TextView title,summary;final ImageView marker;Holder(LinearLayout box,ImageView icon,TextView title,TextView summary,ImageView marker){super(box);this.box=box;this.icon=icon;this.title=title;this.summary=summary;this.marker=marker;}}
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout box=row();box.setPadding(px(12),px(8),px(12),px(8));focusable(box,12);box.setBackground(listSurface());box.setClickable(true);box.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            RecyclerView.LayoutParams p=new RecyclerView.LayoutParams(-1,px(60));p.bottomMargin=px(6);box.setLayoutParams(p);ImageView art=icon(null,39);box.addView(art);
            LinearLayout copy=column();copy.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-1,1);cp.leftMargin=px(14);box.addView(copy,cp);
            TextView title=label("",15);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(title);gap(copy,5);TextView summary=muted("",10);copy.addView(summary);ImageView marker=icon(getDrawable(R.drawable.ic_manage_apps),14);marker.setAlpha(.6f);box.addView(marker);return new Holder(box,art,title,summary,marker);
        }
        @Override public void onBindViewHolder(Holder holder,int position){AppRepository.App app=filtered.get(position);holder.icon.setImageDrawable(app.icon);holder.title.setText(app.label);holder.summary.setText(app.summary());holder.marker.setVisibility(new DockStore(AppManagementActivity.this).load().contains(app.pkg)?View.VISIBLE:View.GONE);holder.box.setSelected(app.pkg.equals(selected));holder.box.setContentDescription(app.label+" "+app.pkg+" "+app.summary());
            holder.box.setOnFocusChangeListener((v,focus)->{if(focus && !selected.equals(app.pkg)){selected=app.pkg;for(int i=0;i<list.getChildCount();i++)list.getChildAt(i).setSelected(list.getChildAt(i)==v);renderDetails();}});
            holder.box.setOnClickListener(v->{selected=app.pkg;renderDetails();if(firstAction!=null)firstAction.requestFocus();});
        }
        @Override public int getItemCount(){return filtered.size();}
    }
}
