package home.quiet.tv;

import android.content.*;
import android.content.pm.*;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.util.*;
import java.util.concurrent.*;

final class ChildModePanel extends ManagementPanel {
    @Override boolean useSettingListStyle(){return false;}
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
    private FrameLayout gate;
    private LinearLayout manager;
    private Button unlockButton,managementAction;
    private ChildModeUnlock unlock;
    private android.os.Parcelable listState;
    private int loadGeneration;
    private ChildModeUi visual;
    ChildModePanel(AppManagementActivity activity){super(activity);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new ChildModeStore(this);visual=new ChildModeUi(activity);
        if(state!=null){selected=state.getString("selected","");query=state.getString("query","");listState=state.getParcelable("list-state");}
        LinearLayout body=managementContent();count=managementHint("");managementInfo.addView(count,new LinearLayout.LayoutParams(-1,-1));
        gate=new FrameLayout(activity);body.addView(gate,new LinearLayout.LayoutParams(-1,0,1));
        manager=column();body.addView(manager,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=row();manager.addView(actions,new LinearLayout.LayoutParams(-1,px(32)));
        for(Button action:new Button[]{button("搜索应用",this::search),button("修改家长密码",()->{if(authorized)ChildModeAuth.change(activity,()->feedback("家长密码已修改"));}),button("计时服务",()->{if(authorized)permission();})}){
            if(managementAction==null)managementAction=action;LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-2,-1);ap.rightMargin=px(10);actions.addView(action,ap);action.setNextFocusUpId(managementTab().getId());
            action.setOnKeyListener((v,key,event)->{if(key!=KeyEvent.KEYCODE_DPAD_UP&&key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;if(event.getAction()==KeyEvent.ACTION_DOWN){if(key==KeyEvent.KEYCODE_DPAD_UP)managementTab().requestFocus();else list.requestFocus();}return true;});
        }
        LinearLayout panels=row();panels.setGravity(Gravity.TOP);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,0,1);lp.topMargin=px(8);manager.addView(panels,lp);
        list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));cards=new Cards();list.setAdapter(cards);list.setItemAnimator(null);list.setClipToPadding(false);list.setPadding(visual.p(5),visual.p(5),visual.p(5),visual.p(5));panels.addView(list,new LinearLayout.LayoutParams(0,-1,40));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setBackground(visual.shape(0x06ffffff,0x15ffffff,31.68f,1));
        details=column();details.setClipToPadding(false);details.setClipChildren(false);details.setPadding(visual.p(58.6f),visual.p(47.08f),visual.p(58.6f),visual.p(47.08f));scroll.addView(details);
        LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,-1,57);dp.leftMargin=px(28);panels.addView(scroll,dp);
        locked();
    }
    @Override protected void onResume(){super.onResume();if(authorized)load();}
    @Override void focusManagementContent(){if(unlock!=null)unlock.focus();else if(!authorized)unlockButton.requestFocus();else managementAction.requestFocus();}
    private void locked(){
        if(unlock!=null){unlock.close();unlock=null;}gate.removeAllViews();manager.setVisibility(View.GONE);gate.setVisibility(View.VISIBLE);count.setText("家长管理已锁定");
        LinearLayout card=column();card.setGravity(Gravity.CENTER);card.addView(center("儿童模式",24));gap(card,12);TextView hint=center("验证家长身份后管理应用",13);hint.setTextColor(MUTED);card.addView(hint);gap(card,22);
        unlockButton=button(store.configured()?"家长解锁":"设置家长密码",this::authenticate);unlockButton.setTag("child-unlock");card.addView(unlockButton,new LinearLayout.LayoutParams(px(170),px(40)));unlockButton.setNextFocusUpId(managementTab().getId());
        gate.addView(card,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
    }
    private void authenticate(){
        if(!showing||authorized||unlock!=null)return;gate.removeAllViews();count.setText("家长验证");
        unlock=new ChildModeUnlock(activity,()->showing&&!authorized,()->{
            unlock.close();unlock=null;authorized=true;gate.removeAllViews();gate.setVisibility(View.GONE);manager.setVisibility(View.VISIBLE);count.setText("正在读取应用…");managementAction.requestFocus();load();
        },()->{locked();managementTab().requestFocus();});
        gate.addView(unlock.view,new FrameLayout.LayoutParams(px(570),-2,Gravity.CENTER));unlock.focus();
    }
    @Override protected void onPause(){
        if(authorized)listState=list.getLayoutManager().onSaveInstanceState();authorized=false;manager.setVisibility(View.GONE);loadGeneration++;loading=false;ChildModeAuth.dismiss(activity);if(editing!=null)editing.dismiss();
        int removed=visible.size();visible.clear();cards.notifyItemRangeRemoved(0,removed);details.removeAllViews();first=null;returning=false;locked();super.onPause();
    }
    @Override protected void onDestroy(){if(unlock!=null)unlock.close();worker.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("selected",selected);state.putString("query",query);state.putParcelable("list-state",authorized?list.getLayoutManager().onSaveInstanceState():listState);super.onSaveInstanceState(state);}
    @Override public void handleBack(){if(unlock!=null){locked();managementTab().requestFocus();}else if(!managementTabFocused())managementTab().requestFocus();else super.handleBack();}
    private void load(){
        if(!authorized || loading)return;loading=true;int request=++loadGeneration;
        worker.execute(()->{ArrayList<ResolveInfo> found=new ArrayList<>();String failure=null;
            try{for(ResolveInfo info:new HomeAppCatalog(this).launchable().values())if(store.eligible(info.activityInfo.packageName))found.add(info);Collections.sort(found,(a,b)->java.text.Collator.getInstance(Locale.SIMPLIFIED_CHINESE).compare(a.loadLabel(getPackageManager()).toString(),b.loadLabel(getPackageManager()).toString()));}
            catch(RuntimeException e){failure="无法读取应用列表";}
            final String problem=failure;runOnUiThread(()->{if(request!=loadGeneration||isDestroyed()||!authorized||!showing)return;loading=false;if(problem!=null){message(problem);return;}apps.clear();apps.addAll(found);filter();});
        });
    }
    // Catalog reloads and searches can replace the entire ordered list.
    @android.annotation.SuppressLint("NotifyDataSetChanged")
    private void filter(){
        boolean listFocused=list.hasFocus();visible.clear();for(ResolveInfo info:apps)if((info.loadLabel(getPackageManager())+" "+info.activityInfo.packageName).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))visible.add(info);
        boolean found=false;for(ResolveInfo info:visible)if(info.activityInfo.packageName.equals(selected))found=true;
        if(!found)selected=visible.isEmpty()?"":visible.get(0).activityInfo.packageName;count.setText(getString(R.string.child_management_app_count,visible.size()));cards.notifyDataSetChanged();render();
        if(listState!=null){list.getLayoutManager().onRestoreInstanceState(listState);listState=null;}
        if(listFocused)list.post(()->{if(managementTabFocused() || !authorized)return;for(int i=0;i<visible.size();i++)if(visible.get(i).activityInfo.packageName.equals(selected)){list.scrollToPosition(i);RecyclerView.ViewHolder holder=list.findViewHolderForAdapterPosition(i);if(holder!=null)holder.itemView.requestFocus();break;}});
    }
    private ResolveInfo current(){for(ResolveInfo info:visible)if(selected.equals(info.activityInfo.packageName))return info;return null;}
    private void render(){
        details.removeAllViews();first=null;if(!authorized)return;ResolveInfo info=current();
        if(info==null){details.addView(center("没有可管理的应用",20));return;}
        ChildModePolicy.Rule rule=store.rule(selected);LinearLayout heading=row();ImageView app=visual.appIcon(info.loadIcon(getPackageManager()),94.08f,23.04f);heading.addView(app,new LinearLayout.LayoutParams(visual.p(94.08f),visual.p(94.08f)));TextView name=visual.text(info.loadLabel(getPackageManager()).toString(),37.44f,94.08f,0xffeeeff2);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,visual.p(94.08f),1);np.leftMargin=visual.p(27.84f);heading.addView(name,np);details.addView(heading,new LinearLayout.LayoutParams(-1,visual.p(94.08f)));visual.gap(details,42.24f);
        first=toggle("隐藏应用","从首页和常用栏隐藏",rule.hidden,()->{ChildModePolicy.Rule r=store.rule(selected);r.hidden=!r.hidden;save(r,0);});
        toggle("锁定应用","打开时需要家长密码",rule.locked,()->{ChildModePolicy.Rule r=store.rule(selected);r.locked=!r.locked;save(r,1);});
        LinearLayout time=optionRow(),copy=column();TextView label=visual.text("使用时长",26.88f,37.5f,0xffeeeff2);copy.addView(label);if(rule.locked){visual.gap(copy,12.48f);copy.addView(visual.text("解锁后可设置",18.43f,26,0xff848e9c));}time.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        time.addView(visual.text(rule.locked?"已锁定":describeTime(rule),22.08f,32,0xffacb6c3),new LinearLayout.LayoutParams(-2,visual.p(37.5f)));ImageView arrow=visual.image(R.drawable.ic_child_arrow,0xff7c8897);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(visual.p(26.88f),visual.p(26.88f));ap.leftMargin=visual.p(19.2f);time.addView(arrow,ap);time.setFocusable(!rule.locked);time.setClickable(!rule.locked);time.setEnabled(!rule.locked);time.setAlpha(rule.locked?.4f:1);time.setBackground(visual.focus(0,0,0,false));time.setContentDescription("使用时长  "+(rule.locked?"解锁后可设置":describeTime(rule)));time.setOnClickListener(v->time());
        TextView scope=visual.text("仅用于 Quiet TV 内的应用入口",16.9f,23.5f,0xff697583);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,visual.p(48.46f));scope.setPadding(0,visual.p(24.96f),0,0);details.addView(scope,sp);
    }
    private LinearLayout optionRow(){
        View line=new View(this);line.setBackgroundColor(0x12ffffff);details.addView(line,new LinearLayout.LayoutParams(-1,visual.p(1)));
        LinearLayout row=row();row.setClipChildren(false);row.setClipToPadding(false);row.setPadding(visual.p(3.84f),0,visual.p(3.84f),0);details.addView(row,new LinearLayout.LayoutParams(-1,visual.p(135.31f)));return row;
    }
    private View toggle(String title,String hint,boolean checked,Runnable action){
        LinearLayout row=optionRow(),copy=column();copy.addView(visual.text(title,26.88f,37.5f,0xffeeeff2));visual.gap(copy,12.48f);copy.addView(visual.text(hint,18.43f,26,0xff848e9c));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        View toggle=new View(this){private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);private final android.graphics.RectF track=new android.graphics.RectF(0,0,72.96f,40.32f),border=new android.graphics.RectF(1,1,71.96f,39.32f),focusRing=new android.graphics.RectF(-5,-5,77.96f,45.32f);@Override protected void onDraw(android.graphics.Canvas c){super.onDraw(c);float scale=visual.p(72.96f)/72.96f;c.save();c.scale(scale,scale);c.translate(6,6);paint.setStyle(android.graphics.Paint.Style.FILL);paint.setColor(checked?0xffbdccc1:0x10ffffff);c.drawRoundRect(track,30,30,paint);paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(checked?0xffbdccc1:0x26ffffff);c.drawRoundRect(border,30,30,paint);paint.setStyle(android.graphics.Paint.Style.FILL);paint.setColor(checked?0xff263d33:0xff9099a4);c.drawCircle(19.72f+(checked?29.76f:0),19.72f,12.96f,paint);if(isFocused()){paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(0xfff6f6f8);c.drawRoundRect(focusRing,35,35,paint);}c.restore();}};
        toggle.setFocusable(true);toggle.setClickable(true);toggle.setContentDescription(title+"  "+(checked?"开启":"关闭"));toggle.setOnClickListener(v->action.run());row.setOnClickListener(v->toggle.performClick());toggle.setOnFocusChangeListener((v,f)->v.invalidate());LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(visual.p(84.96f),visual.p(52.32f));tp.rightMargin=-visual.p(6);row.addView(toggle,tp);return toggle;
    }
    private void save(ChildModePolicy.Rule rule,int focus){
        if(!store.save(selected,rule)){feedback("保存失败，请重试");return;}refreshSelectedCard();render();if(focus==0 && first!=null)first.requestFocus();else if(focus==1 && details.getChildCount()>5)((ViewGroup)details.getChildAt(5)).getChildAt(1).requestFocus();
    }
    private void refreshSelectedCard(){
        for(int i=0;i<visible.size();i++)if(selected.equals(visible.get(i).activityInfo.packageName)){cards.notifyItemChanged(i);return;}
    }
    private void time(){
        if(!authorized || selected.isEmpty())return;
        ChildModePolicy.Rule edited=store.rule(selected);if(edited.locked)return;final boolean[] custom={!Arrays.asList(30L,60L,120L).contains(edited.limit/ChildModePolicy.MINUTE)};
        ChildModeUi.Sheet sheet=visual.new Sheet("使用时长",current().loadLabel(getPackageManager()).toString(),R.drawable.ic_child_time,825.6f);LinearLayout modes=row(),durations=row();modes.setClipChildren(false);durations.setClipChildren(false);visual.gap(sheet.body,34.56f);sheet.body.addView(modes,new LinearLayout.LayoutParams(-1,visual.p(65.1f)));View durationGap=new View(this);sheet.body.addView(durationGap,new LinearLayout.LayoutParams(1,visual.p(21.12f)));sheet.body.addView(durations,new LinearLayout.LayoutParams(-1,visual.p(58)));
        LinearLayout inputRow=row();inputRow.setGravity(Gravity.CENTER);View inputGap=new View(this);sheet.body.addView(inputGap,new LinearLayout.LayoutParams(1,visual.p(24.96f)));sheet.body.addView(inputRow,new LinearLayout.LayoutParams(-1,visual.p(60)));EditText hours=visual.number(String.valueOf(edited.limit/ChildModePolicy.MINUTE/60),"小时",2),minutes=visual.number(String.valueOf(edited.limit/ChildModePolicy.MINUTE%60),"分钟",2);inputRow.addView(hours,new LinearLayout.LayoutParams(visual.p(112),visual.p(58)));TextView hourLabel=visual.text(" 小时",17.28f,32,0xff9ca5b1);inputRow.addView(hourLabel,new LinearLayout.LayoutParams(visual.p(65),-1));inputRow.addView(minutes,new LinearLayout.LayoutParams(visual.p(112),visual.p(58)));inputRow.addView(visual.text(" 分钟",17.28f,32,0xff9ca5b1),new LinearLayout.LayoutParams(visual.p(65),-1));
        View padGap=new View(this);sheet.body.addView(padGap,new LinearLayout.LayoutParams(1,visual.p(19.2f)));LinearLayout pad=visual.numberPad(sheet,hours,minutes);sheet.body.addView(pad);
        View hintGap=new View(this);sheet.body.addView(hintGap,new LinearLayout.LayoutParams(1,visual.p(26.88f)));TextView hint=visual.text("",16.13f,28,0xff7c8a9b);hint.setGravity(Gravity.CENTER);sheet.body.addView(hint);ArrayList<Button> modeButtons=new ArrayList<>(),durationButtons=new ArrayList<>();
        Runnable refresh=()->{for(int i=0;i<modeButtons.size();i++)modeButtons.get(i).setSelected(edited.mode==i);for(int i=0;i<durationButtons.size();i++)durationButtons.get(i).setSelected(i==3?custom[0]:!custom[0] && edited.limit/ChildModePolicy.MINUTE==new int[]{30,60,120}[i]);durations.setVisibility(edited.limited()?View.VISIBLE:View.GONE);durationGap.setVisibility(durations.getVisibility());inputRow.setVisibility(edited.limited() && custom[0]?View.VISIBLE:View.GONE);inputGap.setVisibility(inputRow.getVisibility());pad.setVisibility(inputRow.getVisibility());padGap.setVisibility(inputRow.getVisibility());hint.setVisibility(edited.limited()?View.VISIBLE:View.GONE);hintGap.setVisibility(hint.getVisibility());hint.setText(edited.mode==ChildModePolicy.DAILY?"当天多次使用累计，次日恢复额度":"退出后保留剩余时间，用完需家长授权");};
        String[] labels={"不限制","单次限时","每日累计"};for(int i=0;i<3;i++){final int mode=i;Button b=visual.button(labels[i],()->{edited.mode=mode;refresh.run();},false);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,visual.font(20.16f));visual.choice(b,11.52f);modeButtons.add(b);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,-1,1);if(i>0)mp.leftMargin=visual.p(13.44f);modes.addView(b,mp);}
        String[] choices={"30 分钟","1 小时","2 小时","自定义"};for(int i=0;i<4;i++){final int which=i;Button b=visual.button(choices[i],()->{custom[0]=which==3;if(which<3)edited.limit=new int[]{30,60,120}[which]*ChildModePolicy.MINUTE;else{hours.setText(String.valueOf(edited.limit/ChildModePolicy.MINUTE/60));minutes.setText(String.valueOf(edited.limit/ChildModePolicy.MINUTE%60));}refresh.run();if(which==3)hours.requestFocus();},false);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,visual.font(19.2f));b.setPadding(0,0,0,0);visual.choice(b,12.48f);durationButtons.add(b);LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,-1,1);if(i>0)dp.leftMargin=visual.p(12.48f);durations.addView(b,dp);}
        sheet.action("取消",sheet.dialog::dismiss,false);sheet.action("确定",()->{
            if(!authorized)return;
            if(edited.limited() && custom[0])try{int h=Integer.parseInt(hours.getText().toString()),m=Integer.parseInt(minutes.getText().toString());if(h<0 || h>24 || m<0 || m>59)throw new NumberFormatException();edited.limit=(h*60L+m)*ChildModePolicy.MINUTE;}catch(NumberFormatException e){hint.setText("请输入 1 分钟至 24 小时，分钟填写 0–59");return;}
            try{edited.validate();}catch(IllegalArgumentException e){hint.setText("时长需为 1 分钟至 24 小时");return;}
            if(edited.limited() && !ChildUsageService.ready(this)){sheet.dialog.dismiss();permission();return;}
            if(!store.save(selected,edited)){hint.setText("保存失败，请重试");return;}sheet.dialog.dismiss();refreshSelectedCard();render();details.getChildAt(7).requestFocus();
        },true);visual.numberActions(sheet,pad);refresh.run();editing=sheet.dialog;sheet.dialog.setOnDismissListener(d->{sheet.release();if(editing==sheet.dialog)editing=null;});sheet.show(modeButtons.get(edited.mode));
    }
    private void show(Sheet sheet,View focus){editing=sheet.dialog;sheet.dialog.setOnDismissListener(d->{if(editing==sheet.dialog)editing=null;});sheet.show(focus);}
    private void permission(){Sheet sheet=new Sheet("开启计时服务");sheet.text(store.fault().isEmpty()?"需在无障碍设置开启“儿童模式计时”。到时限制的效果仍需在本机验证。":store.fault());sheet.action("取消",sheet.dialog::dismiss);Button open=sheet.action("去开启",()->{sheet.dialog.dismiss();try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(ActivityNotFoundException e){message("当前电视没有可用的无障碍设置入口");}});show(sheet,open);}
    private void search(){
        if(!authorized)return;
        Sheet sheet=new Sheet("搜索应用");EditText input=input(query,"应用名称或包名");input.setId(View.generateViewId());input.setContentDescription("应用名称或包名");input.setPadding(px(14),0,px(14),0);input.setGravity(Gravity.CENTER_VERTICAL);input.setIncludeFontPadding(false);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH|android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);sheet.body.addView(input,new LinearLayout.LayoutParams(-1,px(44)));
        android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        Runnable showKeyboard=()->{input.requestFocus();keyboard.showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);};
        sheet.action("清空",()->{query="";sheet.dialog.dismiss();filter();});Button search=sheet.action("搜索",()->{query=input.getText().toString().trim();sheet.dialog.dismiss();filter();});search.setId(View.generateViewId());input.setNextFocusDownId(search.getId());search.setNextFocusUpId(input.getId());
        input.setOnClickListener(v->showKeyboard.run());input.setOnKeyListener((v,key,event)->{
            if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER){if(event.getAction()==KeyEvent.ACTION_UP)showKeyboard.run();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(event.getAction()==KeyEvent.ACTION_DOWN){keyboard.hideSoftInputFromWindow(input.getWindowToken(),0);search.requestFocus();}return true;}return false;
        });
        input.setOnEditorActionListener((v,action,event)->{if(action!=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH)return false;search.performClick();return true;});
        sheet.dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        Window window=sheet.dialog.getWindow();window.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);WindowManager.LayoutParams position=window.getAttributes();position.y=px(16);window.setAttributes(position);
        show(sheet,input);sheet.dialog.setOnDismissListener(d->{keyboard.hideSoftInputFromWindow(input.getWindowToken(),0);if(editing==sheet.dialog)editing=null;});
    }
    static String describeTime(ChildModePolicy.Rule r){long minutes=r.limit/ChildModePolicy.MINUTE;return !r.limited()?"不限制":(r.mode==ChildModePolicy.DAILY?"每日 ":"单次 ")+(minutes>=60?minutes/60+" 小时"+(minutes%60==0?"":" "+minutes%60+" 分钟"):minutes+" 分钟");}
    static String summary(ChildModePolicy.Rule r){return (r.hidden?"已隐藏  ":"")+(r.locked?"已上锁":r.limited() && r.remaining()==0?"额度已用完":describeTime(r));}
    @android.annotation.SuppressLint("RestrictedApi")
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(unlock!=null&&gate.hasFocus()&&unlock.dispatchKeyEvent(event))return true;int key=event.getKeyCode();if(event.getAction()==KeyEvent.ACTION_DOWN && list.hasFocus() && key==KeyEvent.KEYCODE_DPAD_RIGHT && first!=null){first.requestFocus();return true;}
        if(event.getAction()==KeyEvent.ACTION_DOWN && list.hasFocus() && key==KeyEvent.KEYCODE_DPAD_UP){RecyclerView.ViewHolder holder=list.findContainingViewHolder(getCurrentFocus());if(holder==null || holder.getBindingAdapterPosition()==0){managementAction.requestFocus();return true;}}
        if(key==KeyEvent.KEYCODE_DPAD_LEFT){if(event.getAction()==KeyEvent.ACTION_DOWN && getCurrentFocus()==first){returning=true;return true;}if(returning){if(event.getAction()==KeyEvent.ACTION_UP){returning=false;list.requestFocus();}return true;}}return false;}
    private final class Cards extends RecyclerView.Adapter<Cards.Holder>{
        final class Holder extends RecyclerView.ViewHolder{final LinearLayout box;final ImageView icon,hidden,policy;final TextView title,state;Holder(LinearLayout box,ImageView icon,TextView title,TextView state,ImageView hidden,ImageView policy){super(box);this.box=box;this.icon=icon;this.title=title;this.state=state;this.hidden=hidden;this.policy=policy;}}
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){LinearLayout box=row();box.setPadding(visual.p(24.04f),visual.p(20.16f),visual.p(24.04f),visual.p(20.16f));box.setFocusable(true);box.setClickable(true);android.graphics.drawable.StateListDrawable bg=new android.graphics.drawable.StateListDrawable();bg.addState(new int[]{android.R.attr.state_focused},visual.shape(0x0cffffff,0xfff6f6f8,21.12f,2));bg.addState(new int[]{android.R.attr.state_selected},visual.shape(0x0cffffff,0x36ffffff,21.12f,1));bg.addState(new int[]{},visual.shape(0,0,21.12f,1));box.setBackground(bg);box.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,visual.p(117.37f));lp.bottomMargin=visual.p(9.6f);box.setLayoutParams(lp);
            ImageView art=visual.appIcon(null,74.88f,15.36f);box.addView(art,new LinearLayout.LayoutParams(visual.p(74.88f),visual.p(74.88f)));LinearLayout copy=column();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=visual.p(26.88f);box.addView(copy,cp);TextView title=visual.text("",27.84f,39,0xffeeeff2);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(title);visual.gap(copy,10.56f);TextView state=visual.text("",18.24f,25.5f,0xff8d96a2);state.setSingleLine();state.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(state);
            LinearLayout statuses=row();statuses.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(visual.p(60),visual.p(24));sp.leftMargin=visual.p(26.88f);box.addView(statuses,sp);ImageView hidden=visual.image(R.drawable.ic_child_hidden,0xff9ca6b4),policy=visual.image(R.drawable.ic_child_unlimited,0xff9ca6b4);statuses.addView(hidden,new LinearLayout.LayoutParams(visual.p(24),visual.p(24)));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(visual.p(24),visual.p(24));pp.leftMargin=visual.p(12);statuses.addView(policy,pp);return new Holder(box,art,title,state,hidden,policy);}
        @Override public void onBindViewHolder(Holder h,int position){ResolveInfo info=visible.get(position);String pkg=info.activityInfo.packageName;ChildModePolicy.Rule rule=store.rule(pkg);h.title.setText(info.loadLabel(getPackageManager()));h.icon.setImageDrawable(info.loadIcon(getPackageManager()));h.state.setText(summary(rule));h.hidden.setVisibility(rule.hidden?View.VISIBLE:View.INVISIBLE);h.policy.setTag(rule.locked?"locked":rule.limited()?"limited":"unlimited");h.policy.setImageDrawable(UiCompat.drawable(activity,rule.locked?R.drawable.ic_child_lock:rule.limited()?R.drawable.ic_child_time:R.drawable.ic_child_unlimited));h.box.setContentDescription(info.loadLabel(getPackageManager())+"  "+summary(rule));h.box.setSelected(selected.equals(pkg));h.box.setTag(pkg);h.box.setOnFocusChangeListener((v,focused)->{if(focused && !selected.equals(pkg)){selected=pkg;for(int i=0;i<list.getChildCount();i++)list.getChildAt(i).setSelected(list.getChildAt(i)==v);render();}});h.box.setOnClickListener(v->{selected=pkg;render();if(first!=null)first.requestFocus();});}
        @Override public int getItemCount(){return visible.size();}
    }
}
