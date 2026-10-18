package home.quiet.tv;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.text.*;
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
    private AppRepository repository;
    private AppOperations operations;
    private Button status,filterButton;
    private EditText search;
    private TextView count;
    private LinearLayout details;
    private RecyclerView list;
    private AppAdapter adapter;
    private String selected="",query="";
    private int filter;
    private boolean busy,connecting,active;
    private final BroadcastReceiver packagesChanged=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction()) && !intent.getBooleanExtra(Intent.EXTRA_REPLACING,false) && intent.getData()!=null)new DockStore(context).remove(intent.getData().getSchemeSpecificPart());
            if(active && !intent.getBooleanExtra(Intent.EXTRA_REPLACING,false))refresh();
        }
    };

    @Override public void onCreate(Bundle state){
        super.onCreate(state);repository=new AppRepository(this);operations=new AppOperations(this);
        adb=new AdbController(this,value->ui(()->status.setText(value)));
        header.addView(label("应用管理",26),new LinearLayout.LayoutParams(0,-1,1));
        header.addView(button("Dock 管理",()->startActivity(new Intent(this,DockEditorActivity.class))));
        status=button("ADB · 未连接",this::connectionPanel);header.addView(status,new LinearLayout.LayoutParams(px(320),px(42)));
        LinearLayout tools=row();root.addView(tools,new LinearLayout.LayoutParams(-1,px(48)));
        search=new EditText(this);search.setSingleLine(true);search.setTextColor(-1);search.setHintTextColor(0xff8e99b0);search.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(15));search.setHint("搜索应用名或包名");
        tools.addView(search,new LinearLayout.LayoutParams(0,-1,1));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int start,int before,int after){query=s.toString().toLowerCase(Locale.ROOT);applyFilter();}public void afterTextChanged(Editable e){}});
        filterButton=button("筛选 · 全部",this::chooseFilter);tools.addView(filterButton);
        tools.addView(button("刷新",this::refresh));tools.addView(button("操作记录",this::showHistory));
        count=label("正在读取应用…",13);root.addView(count);
        LinearLayout panels=row();panels.setGravity(Gravity.TOP);root.addView(panels,new LinearLayout.LayoutParams(-1,0,1));
        list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));adapter=new AppAdapter();list.setAdapter(adapter);list.setItemAnimator(null);
        panels.addView(list,new LinearLayout.LayoutParams(0,-1,1));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);details=column();details.setPadding(px(16),0,0,0);scroll.addView(details);
        panels.addView(scroll,new LinearLayout.LayoutParams(0,-1,1));
        if(state!=null){selected=state.getString("selected","");filter=state.getInt("filter",0);search.setText(state.getString("query",""));}
        IntentFilter packages=new IntentFilter(Intent.ACTION_PACKAGE_ADDED);packages.addAction(Intent.ACTION_PACKAGE_REMOVED);packages.addAction(Intent.ACTION_PACKAGE_CHANGED);packages.addDataScheme("package");registerReceiver(packagesChanged,packages);
        status.requestFocus();
    }
    private void ui(Runnable action){runOnUiThread(()->{if(!isDestroyed() && !isFinishing())action.run();});}
    @Override protected void onResume(){super.onResume();active=true;refresh();}
    @Override protected void onStop(){active=false;if(!connecting)worker.execute(()->{adb.disconnect();ui(()->status.setText("ADB · 未连接"));});super.onStop();}
    @Override protected void onDestroy(){unregisterReceiver(packagesChanged);adb.close();worker.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("selected",selected);state.putString("query",search.getText().toString());state.putInt("filter",filter);super.onSaveInstanceState(state);}
    private void refresh(){
        if(busy)return;busy=true;count.setText("正在读取应用…");renderDetails();
        worker.execute(()->{
            try{List<AppRepository.App> values=repository.load(adb);ui(()->{busy=false;all.clear();all.addAll(values);applyFilter();});}
            catch(Exception e){ui(()->{busy=false;adbFailure(e);});}
        });
    }
    private void adbFailure(Exception e){status.setText("ADB · 连接失败");count.setText("读取或操作失败，请重新连接后刷新");renderDetails();message(e.getMessage()==null?e.toString():e.getMessage());}
    private void chooseFilter(){new AlertDialog.Builder(this).setTitle("筛选应用").setSingleChoiceItems(FILTERS,filter,(d,which)->{filter=which;d.dismiss();applyFilter();}).setNegativeButton("取消",null).show();}
    private void applyFilter(){
        filtered.clear();
        for(AppRepository.App app:all){
            boolean match=filter==0 || filter==1 && !app.system && app.installed || filter==2 && app.system || filter==3 && app.installed && !app.enabled || filter==4 && app.recoverable;
            if(match && (app.label.toLowerCase(Locale.ROOT).contains(query)||app.pkg.toLowerCase(Locale.ROOT).contains(query)))filtered.add(app);
        }
        filterButton.setText("筛选 · "+FILTERS[filter]);
        boolean present=false;for(AppRepository.App app:filtered)if(app.pkg.equals(selected))present=true;
        if(!present)selected=filtered.isEmpty()?"":filtered.get(0).pkg;
        count.setText(filtered.size()+" 个应用 · "+(adb.connected()?"当前用户 "+adb.user+" 的 ADB 状态":"本地信息；启用、禁用、卸载与恢复需要连接 ADB"));
        adapter.notifyDataSetChanged();renderDetails();
    }
    private AppRepository.App current(){for(AppRepository.App app:filtered)if(app.pkg.equals(selected))return app;return null;}
    private void renderDetails(){
        if(details==null)return;details.removeAllViews();AppRepository.App app=current();
        if(app==null){details.addView(label("没有符合条件的应用",21));return;}
        ImageView icon=new ImageView(this);icon.setImageDrawable(app.icon);icon.setContentDescription(app.label);details.addView(icon,new LinearLayout.LayoutParams(px(44),px(44)));
        TextView title=label(app.label,23);title.setSingleLine(false);details.addView(title);
        TextView info=label(app.pkg+"\n版本 "+app.version+"\n"+app.summary(),14);info.setTextIsSelectable(false);details.addView(info);
        String protectedReason=operations.protection(app);
        if(!protectedReason.isEmpty())details.addView(label(protectedReason,14));
        if(!adb.connected()){addButton(details,"连接 ADB 后管理此应用",this::connectionPanel);return;}
        if(busy){details.addView(label("正在读取或核对状态，请稍候…",14));return;}
        for(AppOperations.Action action:operations.actions(app))addButton(details,action==AppOperations.Action.UNINSTALL && app.system?"为当前用户卸载":action.label,()->confirm(app,action));
        if(app.recoverable)details.addView(label("系统仍保留安装包；恢复安装不恢复已删除数据。",13));
    }
    private void confirm(AppRepository.App app,AppOperations.Action action){
        if(busy)return;
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(action.label+" · "+app.label).setMessage(app.pkg+"\n当前用户 "+adb.user+"\n\n"+operations.explanation(app,action))
            .setNegativeButton("取消",null).setPositiveButton("确认"+action.label,(d,w)->perform(app,action)).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus());dialog.show();
    }
    private void perform(AppRepository.App app,AppOperations.Action action){
        if(busy)return;busy=true;count.setText("正在"+action.label+"并核对实际状态…");renderDetails();
        worker.execute(()->{
            String result;
            try{result=operations.perform(adb,app,action);}catch(Exception e){result=e.getMessage();}
            final String message=result;
            ui(()->{busy=false;message(message);refresh();});
        });
    }
    private void showHistory(){worker.execute(()->{try{String history=operations.history();ui(()->{TextView text=label(history,14);ScrollView scroll=new ScrollView(this);scroll.addView(text);new AlertDialog.Builder(this).setTitle("操作记录").setView(scroll).setPositiveButton("关闭",null).show();});}catch(Exception e){ui(()->message(e.getMessage()));}});}
    private void connectionPanel(){
        if(busy){message("正在读取或执行操作，请稍候");return;}
        LinearLayout panel=column();panel.setPadding(px(16),px(8),px(16),px(8));
        panel.addView(label("开启电视开发者选项中的网络／ADB 调试，连接后在电视弹窗允许 QuietTV。仅支持传统 TCP；配对码式无线调试尚未适配。",15));
        EditText port=new EditText(this);port.setSingleLine(true);port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);port.setText(String.valueOf(getSharedPreferences("adb",0).getInt("port",5555)));panel.addView(port);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("本机 ADB 设置").setView(panel).setNegativeButton("取消",null)
            .setNeutralButton(adb.connected()?"断开连接":"开发者设置",null).setPositiveButton("连接",null).create();
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                if(adb.connected()){worker.execute(()->{adb.disconnect();ui(()->{status.setText("ADB · 未连接");refresh();});});dialog.dismiss();}
                else try{startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}catch(ActivityNotFoundException e){message("请使用遥控器进入电视设置 → 关于 → 开发者选项，开启调试。");}
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                int value;try{value=Integer.parseInt(port.getText().toString());if(value<1||value>65535)throw new NumberFormatException();}catch(NumberFormatException e){port.setError("端口必须在 1～65535 之间");return;}
                getSharedPreferences("adb",0).edit().putInt("port",value).apply();dialog.dismiss();connect(value);
            });
        });dialog.show();
    }
    private void connect(int port){
        busy=true;connecting=true;renderDetails();
        worker.execute(()->{try{adb.connect(port);ui(()->{busy=false;connecting=false;if(active)refresh();else worker.execute(adb::disconnect);});}catch(Exception e){ui(()->{busy=false;connecting=false;adbFailure(e);});}});
    }
    private final class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {
        final class Holder extends RecyclerView.ViewHolder {final Button button;Holder(Button button){super(button);this.button=button;}}
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){Button button=button("",()->{});button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);button.setMaxLines(3);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(66));p.bottomMargin=px(6);button.setLayoutParams(p);return new Holder(button);}
        @Override public void onBindViewHolder(Holder holder,int position){AppRepository.App app=filtered.get(position);holder.button.setText(app.label+"\n"+app.pkg+"\n"+app.summary());holder.button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(13));holder.button.setContentDescription(app.label+" "+app.pkg+" "+app.summary());
            holder.button.setOnFocusChangeListener((v,focus)->{if(focus){selected=app.pkg;renderDetails();}});
            holder.button.setOnClickListener(v->{selected=app.pkg;renderDetails();for(int i=0;i<details.getChildCount();i++)if(details.getChildAt(i).isFocusable()){details.getChildAt(i).requestFocus();break;}});
        }
        @Override public int getItemCount(){return filtered.size();}
    }
}
