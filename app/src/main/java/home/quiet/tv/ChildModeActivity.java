package home.quiet.tv;

import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.util.*;
import java.util.concurrent.*;

public final class ChildModeActivity extends ManagementUi {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<ResolveInfo> apps=new ArrayList<>(),visible=new ArrayList<>();
    private ChildModeStore store;
    private RecyclerView list;
    private Cards cards;
    private LinearLayout details;
    private TextView count;
    private android.app.Dialog editing;
    private View first;
    private String selected="",query="";
    private boolean authorized,loading,returning;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new ChildModeStore(this);
        LinearLayout tools=toolbar(3);marginAdd(tools,iconText("搜索",R.drawable.ic_manage_search,this::search),-2,33,0);
        marginAdd(tools,iconButton("更多",R.drawable.ic_manage_more,this::more),40,33,8);
        marginAdd(tools,iconButton("关于",R.drawable.ic_about,()->startActivity(new Intent(this,AboutActivity.class))),40,33,8);
        LinearLayout panels=row();LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,0,1);pp.topMargin=px(22);root.addView(panels,pp);
        LinearLayout left=column();panels.addView(left,new LinearLayout.LayoutParams(0,-1,40));count=muted("请输入家长密码",11);left.addView(count,new LinearLayout.LayoutParams(-1,px(28)));
        list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));cards=new Cards();list.setAdapter(cards);list.setItemAnimator(null);left.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        details=column();details.setPadding(px(27),px(25),px(27),px(18));details.setBackground(surface(0x12ffffff,0x06ffffff,0x1cffffff,20));LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,-1,57);dp.leftMargin=px(28);panels.addView(details,dp);
        footer("方向键移动  确定选择  返回上一级");
    }
    @Override protected void onResume(){super.onResume();if(!authorized)ChildModeAuth.show(this,()->{authorized=true;load();});else load();}
    @Override protected void onStop(){authorized=false;ChildModeAuth.dismiss(this);if(editing!=null)editing.dismiss();visible.clear();cards.notifyDataSetChanged();details.removeAllViews();count.setText("请输入家长密码");super.onStop();}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    private void load(){
        if(!authorized || loading)return;loading=true;
        worker.execute(()->{ArrayList<ResolveInfo> found=new ArrayList<>();String failure=null;
            try{for(ResolveInfo info:new HomeAppCatalog(this).launchable().values())if(store.eligible(info.activityInfo.packageName))found.add(info);Collections.sort(found,(a,b)->java.text.Collator.getInstance(Locale.SIMPLIFIED_CHINESE).compare(a.loadLabel(getPackageManager()).toString(),b.loadLabel(getPackageManager()).toString()));}
            catch(RuntimeException e){failure="无法读取应用列表";}
            final String problem=failure;runOnUiThread(()->{loading=false;if(isDestroyed() || !authorized)return;if(problem!=null){message(problem);return;}apps.clear();apps.addAll(found);filter();});
        });
    }
    private void filter(){
        visible.clear();for(ResolveInfo info:apps)if((info.loadLabel(getPackageManager())+" "+info.activityInfo.packageName).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))visible.add(info);
        boolean found=false;for(ResolveInfo info:visible)if(info.activityInfo.packageName.equals(selected))found=true;
        if(!found)selected=visible.isEmpty()?"":visible.get(0).activityInfo.packageName;count.setText(visible.size()+" 个应用  家长设置");cards.notifyDataSetChanged();render();
        list.post(()->{for(int i=0;i<visible.size();i++)if(visible.get(i).activityInfo.packageName.equals(selected)){list.scrollToPosition(i);RecyclerView.ViewHolder holder=list.findViewHolderForAdapterPosition(i);if(holder!=null)holder.itemView.requestFocus();break;}});
    }
    private ResolveInfo current(){for(ResolveInfo info:visible)if(selected.equals(info.activityInfo.packageName))return info;return null;}
    private void render(){
        details.removeAllViews();first=null;if(!authorized)return;ResolveInfo info=current();
        if(info==null){details.addView(center("没有可管理的应用",20));return;}
        ChildModePolicy.Rule rule=store.rule(selected);LinearLayout heading=row();heading.addView(icon(info.loadIcon(getPackageManager()),50));TextView name=label(info.loadLabel(getPackageManager()).toString(),21);name.setPadding(px(16),0,0,0);heading.addView(name,new LinearLayout.LayoutParams(0,px(52),1));details.addView(heading);gap(details,12);
        first=toggle("隐藏应用","从首页和常用栏隐藏",rule.hidden,()->{ChildModePolicy.Rule r=store.rule(selected);r.hidden=!r.hidden;save(r,0);});
        toggle("锁定应用","打开时需要家长密码",rule.locked,()->{ChildModePolicy.Rule r=store.rule(selected);r.locked=!r.locked;save(r,1);});
        LinearLayout row=optionRow();LinearLayout copy=column();copy.addView(label("使用时长",16));if(rule.locked){TextView hint=muted("解锁后可设置",11);copy.addView(hint);}row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(muted(rule.locked?"已锁定":describeTime(rule)+"  ›",13));row.setEnabled(!rule.locked);row.setAlpha(rule.locked?.4f:1);row.setContentDescription("使用时长  "+(rule.locked?"解锁后可设置":describeTime(rule)));row.setOnClickListener(v->time());
        details.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));TextView scope=muted("仅用于 Quiet TV 内的应用入口",10);details.addView(scope);
    }
    private LinearLayout optionRow(){
        LinearLayout line=row();line.setPadding(px(3),px(8),px(3),px(8));focusable(line,8);line.setBackground(listSurface());line.setClickable(true);details.addView(line,new LinearLayout.LayoutParams(-1,px(64)));return line;
    }
    private View toggle(String title,String hint,boolean checked,Runnable action){
        LinearLayout row=optionRow(),copy=column();copy.addView(label(title,16));gap(copy,6);copy.addView(muted(hint,11));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        Switch control=new Switch(this);control.setChecked(checked);control.setFocusable(false);control.setClickable(false);row.addView(control);row.setContentDescription(title+"  "+(checked?"开启":"关闭"));row.setOnClickListener(v->action.run());return row;
    }
    private void save(ChildModePolicy.Rule rule,int focus){
        if(!store.save(selected,rule)){feedback("保存失败，请重试");return;}cards.notifyDataSetChanged();render();if(details.getChildCount()>focus+2)details.getChildAt(focus+2).requestFocus();
    }
    private void time(){
        if(!authorized || selected.isEmpty())return;
        ChildModePolicy.Rule edited=store.rule(selected);if(edited.locked)return;final boolean[] custom={!Arrays.asList(30L,60L,120L).contains(edited.limit/ChildModePolicy.MINUTE)};
        Sheet sheet=new Sheet("使用时长");LinearLayout modes=row(),durations=row();sheet.body.addView(modes,new LinearLayout.LayoutParams(-1,px(40)));gap(sheet.body,12);sheet.body.addView(durations,new LinearLayout.LayoutParams(-1,px(38)));
        LinearLayout inputRow=row();gap(sheet.body,12);sheet.body.addView(inputRow,new LinearLayout.LayoutParams(-1,px(40)));EditText hours=input(String.valueOf(edited.limit/ChildModePolicy.MINUTE/60),"小时"),minutes=input(String.valueOf(edited.limit/ChildModePolicy.MINUTE%60),"分钟");hours.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);minutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);inputRow.addView(hours,new LinearLayout.LayoutParams(0,-1,1));inputRow.addView(muted(" 小时  ",12));inputRow.addView(minutes,new LinearLayout.LayoutParams(0,-1,1));inputRow.addView(muted(" 分钟",12));
        TextView hint=muted("",11);gap(sheet.body,12);sheet.body.addView(hint);ArrayList<Button> modeButtons=new ArrayList<>(),durationButtons=new ArrayList<>();
        Runnable refresh=()->{for(int i=0;i<modeButtons.size();i++)modeButtons.get(i).setSelected(edited.mode==i);for(int i=0;i<durationButtons.size();i++)durationButtons.get(i).setSelected(i==3?custom[0]:!custom[0] && edited.limit/ChildModePolicy.MINUTE==new int[]{30,60,120}[i]);durations.setVisibility(edited.limited()?View.VISIBLE:View.GONE);inputRow.setVisibility(edited.limited() && custom[0]?View.VISIBLE:View.GONE);hint.setText(edited.mode==ChildModePolicy.DAILY?"当天累计使用，次日恢复额度":edited.mode==ChildModePolicy.SINGLE?"退出保留剩余时间，用完需家长授权":"不限制使用时间");};
        String[] labels={"不限制","单次限时","每日累计"};for(int i=0;i<3;i++){final int mode=i;Button b=button(labels[i],()->{edited.mode=mode;refresh.run();});modeButtons.add(b);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,px(36),1);p.setMargins(px(3),0,px(3),0);modes.addView(b,p);}
        String[] choices={"30 分钟","1 小时","2 小时","自定义"};for(int i=0;i<4;i++){final int which=i;Button b=button(choices[i],()->{custom[0]=which==3;if(which<3)edited.limit=new int[]{30,60,120}[which]*ChildModePolicy.MINUTE;refresh.run();});b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(11));b.setPadding(0,0,0,0);durationButtons.add(b);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,px(34),1);p.setMargins(px(3),0,px(3),0);durations.addView(b,p);}
        sheet.action("取消",sheet.dialog::dismiss);sheet.action("确定",()->{
            if(!authorized)return;
            if(edited.limited() && custom[0])try{int h=Integer.parseInt(hours.getText().toString()),m=Integer.parseInt(minutes.getText().toString());if(h<0 || h>24 || m<0 || m>59)throw new NumberFormatException();edited.limit=(h*60L+m)*ChildModePolicy.MINUTE;}catch(NumberFormatException e){feedback("请输入 1 分钟至 24 小时，分钟填写 0–59");return;}
            try{edited.validate();}catch(IllegalArgumentException e){feedback("时长需为 1 分钟至 24 小时");return;}
            if(edited.limited() && !ChildUsageService.ready(this)){sheet.dialog.dismiss();permission();return;}
            if(!store.save(selected,edited)){feedback("保存失败，请重试");return;}sheet.dialog.dismiss();cards.notifyDataSetChanged();render();details.getChildAt(4).requestFocus();
        });refresh.run();show(sheet,modeButtons.get(edited.mode));
    }
    private void show(Sheet sheet,View focus){editing=sheet.dialog;sheet.dialog.setOnDismissListener(d->{if(editing==sheet.dialog)editing=null;});sheet.show(focus);}
    private void permission(){Sheet sheet=new Sheet("开启计时服务");sheet.text(store.fault().isEmpty()?"需在无障碍设置开启“儿童模式计时”。到时限制的效果仍需在本机验证。":store.fault());sheet.action("取消",sheet.dialog::dismiss);Button open=sheet.action("去开启",()->{sheet.dialog.dismiss();try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(ActivityNotFoundException e){message("当前电视没有可用的无障碍设置入口");}});show(sheet,open);}
    private void search(){if(!authorized){ChildModeAuth.show(this,()->{authorized=true;load();});return;}Sheet sheet=new Sheet("搜索应用");EditText input=input(query,"应用名称或包名");sheet.body.addView(input);sheet.action("清空",()->{query="";sheet.dialog.dismiss();filter();});sheet.action("搜索",()->{query=input.getText().toString().trim();sheet.dialog.dismiss();filter();});show(sheet,input);}
    private void more(){if(!authorized)return;Sheet sheet=new Sheet("更多");Button password=sheet.option("修改家长密码",()->{sheet.dialog.dismiss();ChildModeAuth.change(this,()->feedback("家长密码已修改"));});sheet.option("计时服务",()->{sheet.dialog.dismiss();permission();});sheet.action("关闭",sheet.dialog::dismiss);show(sheet,password);}
    static String describeTime(ChildModePolicy.Rule r){return !r.limited()?"不限制":(r.mode==ChildModePolicy.DAILY?"每日 ":"单次 ")+(r.limit/ChildModePolicy.MINUTE)+" 分钟";}
    static String summary(ChildModePolicy.Rule r){return (r.hidden?"已隐藏  ":"")+(r.locked?"已上锁":describeTime(r));}
    @android.annotation.SuppressLint("RestrictedApi")
    @Override public boolean dispatchKeyEvent(KeyEvent event){int key=event.getKeyCode();if(event.getAction()==KeyEvent.ACTION_DOWN && list.hasFocus() && key==KeyEvent.KEYCODE_DPAD_RIGHT && first!=null){first.requestFocus();return true;}if(key==KeyEvent.KEYCODE_DPAD_LEFT){if(event.getAction()==KeyEvent.ACTION_DOWN && getCurrentFocus()==first){returning=true;return true;}if(returning){if(event.getAction()==KeyEvent.ACTION_UP){returning=false;list.requestFocus();}return true;}}return super.dispatchKeyEvent(event);}
    private final class Cards extends RecyclerView.Adapter<Cards.Holder>{
        final class Holder extends RecyclerView.ViewHolder{final LinearLayout box;final ImageView icon;final TextView title,state;Holder(LinearLayout box,ImageView icon,TextView title,TextView state){super(box);this.box=box;this.icon=icon;this.title=title;this.state=state;}}
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){LinearLayout box=row();box.setPadding(px(12),px(8),px(12),px(8));focusable(box,12);box.setClickable(true);box.setBackground(listSurface());box.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);RecyclerView.LayoutParams p=new RecyclerView.LayoutParams(-1,px(64));p.bottomMargin=px(5);box.setLayoutParams(p);ImageView art=icon(null,39);box.addView(art);LinearLayout copy=column();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=px(14);box.addView(copy,cp);TextView title=label("",15);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(title);gap(copy,5);TextView state=muted("",10);copy.addView(state);return new Holder(box,art,title,state);}
        @Override public void onBindViewHolder(Holder h,int position){ResolveInfo info=visible.get(position);String pkg=info.activityInfo.packageName;h.title.setText(info.loadLabel(getPackageManager()));h.icon.setImageDrawable(info.loadIcon(getPackageManager()));h.state.setText(summary(store.rule(pkg)));h.box.setSelected(selected.equals(pkg));h.box.setOnFocusChangeListener((v,focused)->{if(focused && !selected.equals(pkg)){selected=pkg;for(int i=0;i<list.getChildCount();i++)list.getChildAt(i).setSelected(list.getChildAt(i)==v);render();}});h.box.setOnClickListener(v->{selected=pkg;render();if(first!=null)first.requestFocus();});}
        @Override public int getItemCount(){return visible.size();}
    }
}
