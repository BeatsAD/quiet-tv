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
    private TextView timerStatus,usageStatus;
    private android.app.Dialog editing;
    private View first;
    private String selected="";
    private boolean authorized,loading,returning;
    private FrameLayout gate;
    private LinearLayout manager;
    private Button unlockButton,managementAction;
    private ChildModeUnlock unlock;
    private android.os.Parcelable listState;
    private int loadGeneration;
    private ChildModeUi visual;
    private final Runnable timerChanged=()->runOnUiThread(()->{if(showing&&authorized&&timerStatus!=null&&!selected.isEmpty()){ChildModePolicy.Rule rule=store.rule(selected);timerStatus.setText(rule.locked?"已锁定":rule.limited()&&!ChildUsageService.ready(this)?"计时未生效":describeTime(rule));if(usageStatus!=null)usageStatus.setText(usage(rule));}});
    private final android.os.Handler clock=new android.os.Handler(android.os.Looper.getMainLooper());
    private String shownDay="";
    private final Runnable refreshDay=new Runnable(){public void run(){if(!showing||!authorized)return;String today=ChildModeStore.today();if(!today.equals(shownDay)){shownDay=today;cards.notifyItemRangeChanged(0,visible.size());render();}clock.postDelayed(this,1000);}};
    ChildModePanel(AppManagementActivity activity){super(activity);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new ChildModeStore(this);visual=new ChildModeUi(activity);
        if(state!=null){selected=state.getString("selected","");listState=state.getParcelable("list-state");}
        LinearLayout body=managementContent();managementInfo.setVisibility(View.GONE);((LinearLayout.LayoutParams)body.getLayoutParams()).topMargin=0;
        gate=new FrameLayout(activity);body.addView(gate,new LinearLayout.LayoutParams(-1,0,1));
        manager=column();body.addView(manager,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=row();manager.addView(actions,new LinearLayout.LayoutParams(-1,px(32)));
        for(Button action:new Button[]{button("修改家长密码",()->{if(authorized)ChildModeAuth.change(activity,()->feedback("家长密码已修改"));}),button("计时服务",()->{if(authorized)permission();})}){
            if(managementAction==null)managementAction=action;LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-2,-1);ap.rightMargin=px(10);actions.addView(action,ap);action.setNextFocusUpId(managementTab().getId());
            action.setOnKeyListener((v,key,event)->{if(key!=KeyEvent.KEYCODE_DPAD_UP&&key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;if(event.getAction()==KeyEvent.ACTION_DOWN){if(key==KeyEvent.KEYCODE_DPAD_UP)managementTab().requestFocus();else list.requestFocus();}return true;});
        }
        LinearLayout panels=row();panels.setGravity(Gravity.TOP);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,0,1);lp.topMargin=px(8);manager.addView(panels,lp);
        list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));cards=new Cards();list.setAdapter(cards);list.setItemAnimator(null);list.setClipToPadding(false);list.setPadding(visual.p(5),visual.p(5),visual.p(5),visual.p(5));panels.addView(list,new LinearLayout.LayoutParams(0,-1,40));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setBackgroundDrawable(visual.shape(0x06ffffff,0x15ffffff,31.68f,1));
        details=column();details.setClipToPadding(false);details.setClipChildren(false);details.setPadding(visual.p(58.6f),visual.p(47.08f),visual.p(58.6f),visual.p(47.08f));scroll.addView(details);
        LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,-1,57);dp.leftMargin=px(28);panels.addView(scroll,dp);
        locked();
    }
    @Override protected void onResume(){super.onResume();ChildUsageService.listen(timerChanged);if(authorized)load();}
    @Override void focusManagementContent(){if(unlock!=null)unlock.focus();else if(!authorized)unlockButton.requestFocus();else managementAction.requestFocus();}
    private void locked(){
        if(unlock!=null){unlock.close();unlock=null;}gate.removeAllViews();manager.setVisibility(View.GONE);gate.setVisibility(View.VISIBLE);
        LinearLayout card=column();card.setGravity(Gravity.CENTER);card.addView(center("儿童模式",24));gap(card,12);TextView hint=center("验证家长身份后管理应用",13);hint.setTextColor(MUTED);card.addView(hint);gap(card,22);
        unlockButton=button(store.configured()?"家长解锁":"设置家长密码",this::authenticate);unlockButton.setTag("child-unlock");card.addView(unlockButton,new LinearLayout.LayoutParams(px(170),px(40)));unlockButton.setNextFocusUpId(managementTab().getId());
        gate.addView(card,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
    }
    private void authenticate(){
        if(!showing||authorized||unlock!=null)return;gate.removeAllViews();
        unlock=new ChildModeUnlock(activity,()->showing&&!authorized,()->{
            unlock.close();unlock=null;authorized=true;gate.removeAllViews();gate.setVisibility(View.GONE);manager.setVisibility(View.VISIBLE);managementAction.requestFocus();load();
            shownDay=ChildModeStore.today();clock.post(refreshDay);
        },()->{locked();managementTab().requestFocus();});
        gate.addView(unlock.view,new FrameLayout.LayoutParams(px(330),-2,Gravity.CENTER));unlock.focus();
    }
    @Override protected void onPause(){
        ChildUsageService.unlisten(timerChanged);clock.removeCallbacks(refreshDay);
        if(authorized)listState=list.getLayoutManager().onSaveInstanceState();authorized=false;manager.setVisibility(View.GONE);loadGeneration++;loading=false;ChildModeAuth.dismiss(activity);if(editing!=null)editing.dismiss();
        int removed=visible.size();visible.clear();cards.notifyItemRangeRemoved(0,removed);details.removeAllViews();first=null;returning=false;locked();super.onPause();
    }
    @Override protected void onDestroy(){if(unlock!=null)unlock.close();worker.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("selected",selected);state.putParcelable("list-state",authorized?list.getLayoutManager().onSaveInstanceState():listState);super.onSaveInstanceState(state);}
    @Override public void handleBack(){if(unlock!=null){locked();managementTab().requestFocus();}else if(!managementTabFocused())managementTab().requestFocus();else super.handleBack();}
    private void load(){
        if(!authorized || loading)return;loading=true;int request=++loadGeneration;
        worker.execute(()->{ArrayList<ResolveInfo> found=new ArrayList<>();String failure=null;
            try{for(ResolveInfo info:new HomeAppCatalog(this).launchable().values())if(store.eligible(info.activityInfo.packageName))found.add(info);Collections.sort(found,(a,b)->java.text.Collator.getInstance(Locale.SIMPLIFIED_CHINESE).compare(a.loadLabel(getPackageManager()).toString(),b.loadLabel(getPackageManager()).toString()));}
            catch(RuntimeException e){failure="无法读取应用列表";}
            final String problem=failure;runOnUiThread(()->{if(request!=loadGeneration||isDestroyed()||!authorized||!showing)return;loading=false;if(problem!=null){message(problem);return;}apps.clear();apps.addAll(found);filter();});
        });
    }
    // Catalog reloads can replace the entire ordered list.
    @android.annotation.SuppressLint("NotifyDataSetChanged")
    private void filter(){
        boolean listFocused=list.hasFocus();visible.clear();visible.addAll(apps);
        boolean found=false;for(ResolveInfo info:visible)if(info.activityInfo.packageName.equals(selected))found=true;
        if(!found)selected=visible.isEmpty()?"":visible.get(0).activityInfo.packageName;cards.notifyDataSetChanged();render();
        if(listState!=null){list.getLayoutManager().onRestoreInstanceState(listState);listState=null;}
        if(listFocused)list.post(()->{if(managementTabFocused() || !authorized)return;for(int i=0;i<visible.size();i++)if(visible.get(i).activityInfo.packageName.equals(selected)){list.scrollToPosition(i);RecyclerView.ViewHolder holder=list.findViewHolderForAdapterPosition(i);if(holder!=null)holder.itemView.requestFocus();break;}});
    }
    private ResolveInfo current(){for(ResolveInfo info:visible)if(selected.equals(info.activityInfo.packageName))return info;return null;}
    private void render(){
        details.removeAllViews();first=null;usageStatus=null;if(!authorized)return;ResolveInfo info=current();
        if(info==null){details.addView(center("没有可管理的应用",20));return;}
        ChildModePolicy.Rule rule=store.rule(selected);LinearLayout heading=row();ImageView app=visual.appIcon(info.loadIcon(getPackageManager()),94.08f,23.04f);heading.addView(app,new LinearLayout.LayoutParams(visual.p(94.08f),visual.p(94.08f)));TextView name=visual.text(info.loadLabel(getPackageManager()).toString(),37.44f,94.08f,0xffeeeff2);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,visual.p(94.08f),1);np.leftMargin=visual.p(27.84f);heading.addView(name,np);details.addView(heading,new LinearLayout.LayoutParams(-1,visual.p(94.08f)));visual.gap(details,42.24f);
        first=toggle("隐藏应用","从首页和常用栏隐藏",rule.hidden,()->{ChildModePolicy.Rule r=store.rule(selected);r.hidden=!r.hidden;save(r,0);});
        toggle("锁定应用","打开时需要家长密码",rule.locked,()->{ChildModePolicy.Rule r=store.rule(selected);r.locked=!r.locked;save(r,1);});
        LinearLayout time=optionRow(),copy=column();TextView label=visual.text("使用时长",26.88f,37.5f,0xffeeeff2);copy.addView(label);if(rule.locked){visual.gap(copy,12.48f);copy.addView(visual.text("解锁后可设置",18.43f,26,0xff848e9c));}else if(rule.mode==ChildModePolicy.DAILY){visual.gap(copy,12.48f);usageStatus=visual.text(usage(rule),16.9f,52,0xff848e9c);usageStatus.setMaxLines(2);copy.addView(usageStatus);}time.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        timerStatus=visual.text(rule.locked?"已锁定":rule.limited()&&!ChildUsageService.ready(this)?"计时未生效":describeTime(rule),22.08f,32,0xffacb6c3);time.addView(timerStatus,new LinearLayout.LayoutParams(-2,visual.p(37.5f)));ImageView arrow=visual.image(R.drawable.ic_child_arrow,0xff7c8897);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(visual.p(26.88f),visual.p(26.88f));ap.leftMargin=visual.p(19.2f);time.addView(arrow,ap);time.setFocusable(!rule.locked);time.setClickable(!rule.locked);time.setEnabled(!rule.locked);time.setAlpha(rule.locked?.4f:1);time.setBackgroundDrawable(visual.focus(0,0,0,false));time.setContentDescription("使用时长  "+(rule.locked?"解锁后可设置":describeTime(rule)));time.setOnClickListener(v->KeyFeedback.clicked(v,this::time));
        TextView scope=visual.text("仅用于 Quiet TV 内的应用入口",16.9f,23.5f,0xff697583);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,visual.p(48.46f));scope.setPadding(0,visual.p(24.96f),0,0);details.addView(scope,sp);
    }
    private LinearLayout optionRow(){
        View line=new View(this);line.setBackgroundColor(0x12ffffff);details.addView(line,new LinearLayout.LayoutParams(-1,visual.p(1)));
        LinearLayout row=row();row.setClipChildren(false);row.setClipToPadding(false);row.setPadding(visual.p(3.84f),0,visual.p(3.84f),0);details.addView(row,new LinearLayout.LayoutParams(-1,visual.p(135.31f)));return row;
    }
    private View toggle(String title,String hint,boolean checked,Runnable action){
        LinearLayout row=optionRow(),copy=column();copy.addView(visual.text(title,26.88f,37.5f,0xffeeeff2));visual.gap(copy,12.48f);copy.addView(visual.text(hint,18.43f,26,0xff848e9c));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        View toggle=new View(this){private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);private final android.graphics.RectF track=new android.graphics.RectF(0,0,72.96f,40.32f),border=new android.graphics.RectF(1,1,71.96f,39.32f),focusRing=new android.graphics.RectF(-5,-5,77.96f,45.32f);@Override protected void onDraw(android.graphics.Canvas c){super.onDraw(c);float scale=visual.p(72.96f)/72.96f;c.save();c.scale(scale,scale);c.translate(6,6);paint.setStyle(android.graphics.Paint.Style.FILL);paint.setColor(checked?0xffbdccc1:0x10ffffff);c.drawRoundRect(track,30,30,paint);paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(checked?0xffbdccc1:0x26ffffff);c.drawRoundRect(border,30,30,paint);paint.setStyle(android.graphics.Paint.Style.FILL);paint.setColor(checked?0xff263d33:0xff9099a4);c.drawCircle(19.72f+(checked?29.76f:0),19.72f,12.96f,paint);if(isFocused()){paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(0xfff6f6f8);c.drawRoundRect(focusRing,35,35,paint);}c.restore();}};
        toggle.setFocusable(true);toggle.setClickable(true);toggle.setContentDescription(title+"  "+(checked?"开启":"关闭"));toggle.setOnClickListener(v->KeyFeedback.clicked(v,action));row.setOnClickListener(v->toggle.performClick());toggle.setOnFocusChangeListener((v,f)->v.invalidate());LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(visual.p(84.96f),visual.p(52.32f));tp.rightMargin=-visual.p(6);row.addView(toggle,tp);return toggle;
    }
    private void save(ChildModePolicy.Rule rule,int focus){
        if(!store.saveSettings(selected,rule)){feedback("保存失败，请重试");return;}refreshSelectedCard();render();if(focus==0 && first!=null)first.requestFocus();else if(focus==1 && details.getChildCount()>5)((ViewGroup)details.getChildAt(5)).getChildAt(1).requestFocus();
    }
    private void refreshSelectedCard(){
        for(int i=0;i<visible.size();i++)if(selected.equals(visible.get(i).activityInfo.packageName)){cards.notifyItemChanged(i);return;}
    }
    private void time(){
        if(!authorized || selected.isEmpty())return;
        ChildModePolicy.Rule edited=store.rule(selected);if(edited.locked)return;
        final int originalDays=edited.repeatDays;
        final boolean[] customTime={!Arrays.asList(30L,60L,120L).contains(edited.limit/ChildModePolicy.MINUTE)},customDays={edited.repeatDays!=127&&edited.repeatDays!=31&&edited.repeatDays!=96};
        ChildModeUi.Sheet sheet=visual.new Sheet("使用时长",current().loadLabel(getPackageManager()).toString(),R.drawable.ic_child_time,825.6f,true);
        sheet.box.setTag("child-time-sheet");sheet.scrollBody(570);
        final Runnable[] refresh={null};ArrayList<Button> modeButtons=new ArrayList<>(),repeatButtons=new ArrayList<>(),dayButtons=new ArrayList<>(),durationButtons=new ArrayList<>();
        visual.gap(sheet.body,22);LinearLayout modes=row();sheet.body.addView(modes,new LinearLayout.LayoutParams(-1,visual.p(56)));
        String[] labels={"不限制","单次限时","循环限时"};for(int i=0;i<3;i++){
            final int mode=i;Button b=visual.button(labels[i],()->{edited.mode=mode;refresh[0].run();},false);b.setId(androidx.core.view.ViewCompat.generateViewId());b.setTag("child-mode-"+i);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,visual.font(20.16f));visual.choice(b,12);modeButtons.add(b);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);if(i>0)lp.leftMargin=visual.p(12);modes.addView(b,lp);
        }
        LinearLayout repeats=column();repeats.setTag("child-repeat-section");sheet.body.addView(repeats,new LinearLayout.LayoutParams(-1,-2));visual.gap(repeats,22);
        repeats.addView(visual.text("重复日期",21,30,ManagementUi.TEXT));visual.gap(repeats,10);LinearLayout presets=row();repeats.addView(presets,new LinearLayout.LayoutParams(-1,visual.p(54)));
        String[] names={"每天","周一至周五","周六和周日","自定义"};for(int i=0;i<4;i++){
            final int which=i;Button b=visual.button(names[i],()->{customDays[0]=which==3;if(which<3)edited.repeatDays=new int[]{127,31,96}[which];refresh[0].run();},false);b.setId(androidx.core.view.ViewCompat.generateViewId());b.setTag("child-repeat-preset-"+i);b.setPadding(0,0,0,0);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,visual.font(18));visual.choice(b,12);repeatButtons.add(b);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);if(i>0)lp.leftMargin=visual.p(9);presets.addView(b,lp);
        }
        LinearLayout customDates=column();repeats.addView(customDates);customDates.setTag("child-repeat-custom");visual.gap(customDates,18);LinearLayout weekdays=row();customDates.addView(weekdays,new LinearLayout.LayoutParams(-1,visual.p(56)));
        for(int i=0;i<7;i++){
            final int day=i;String name="周"+"一二三四五六日".charAt(i);Button b=visual.button(name,()->{edited.repeatDays^=1<<day;refresh[0].run();},false);b.setId(androidx.core.view.ViewCompat.generateViewId());b.setTag("child-repeat-day-"+i);b.setPadding(0,0,0,0);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,visual.font(18.5f));visual.choice(b,12);dayButtons.add(b);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);if(i>0)lp.leftMargin=visual.p(9);weekdays.addView(b,lp);
        }
        LinearLayout timeSection=column();sheet.body.addView(timeSection);visual.gap(timeSection,20);View divider=new View(this);divider.setBackgroundColor(0x20ffffff);timeSection.addView(divider,new LinearLayout.LayoutParams(-1,visual.p(1)));visual.gap(timeSection,16);
        TextView timeLabel=visual.text("每日可用",21,30,ManagementUi.TEXT);timeSection.addView(timeLabel);visual.gap(timeSection,10);LinearLayout durations=row();timeSection.addView(durations,new LinearLayout.LayoutParams(-1,visual.p(54)));
        LinearLayout inputSection=column();timeSection.addView(inputSection);visual.gap(inputSection,14);LinearLayout inputRow=row();inputRow.setGravity(Gravity.CENTER);inputSection.addView(inputRow,new LinearLayout.LayoutParams(-1,visual.p(58)));
        EditText hours=visual.number(String.valueOf(edited.limit/ChildModePolicy.MINUTE/60),"小时",2),minutes=visual.number(String.valueOf(edited.limit/ChildModePolicy.MINUTE%60),"分钟",2);hours.setTag("child-hours");minutes.setTag("child-minutes");
        inputRow.addView(hours,new LinearLayout.LayoutParams(visual.p(112),visual.p(58)));inputRow.addView(visual.text(" 小时",17.28f,32,0xff9ca5b1),new LinearLayout.LayoutParams(visual.p(65),-1));inputRow.addView(minutes,new LinearLayout.LayoutParams(visual.p(112),visual.p(58)));inputRow.addView(visual.text(" 分钟",17.28f,32,0xff9ca5b1),new LinearLayout.LayoutParams(visual.p(65),-1));
        visual.gap(inputSection,14);LinearLayout pad=visual.numberPad(sheet,hours,minutes);inputSection.addView(pad);
        String[] choices={"30 分钟","1 小时","2 小时","自定义"};for(int i=0;i<4;i++){
            final int which=i;Button b=visual.button(choices[i],()->{customTime[0]=which==3;if(which<3)edited.limit=new int[]{30,60,120}[which]*ChildModePolicy.MINUTE;else{hours.setText(String.valueOf(edited.limit/ChildModePolicy.MINUTE/60));minutes.setText(String.valueOf(edited.limit/ChildModePolicy.MINUTE%60));}refresh[0].run();if(which==3)hours.requestFocus();},false);b.setId(androidx.core.view.ViewCompat.generateViewId());b.setTag("child-duration-"+i);b.setPadding(0,0,0,0);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,visual.font(19));visual.choice(b,12);durationButtons.add(b);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);if(i>0)lp.leftMargin=visual.p(9);durations.addView(b,lp);
        }
        LinearLayout notice=column();visual.gap(notice,18);TextView hint=visual.text("",15.5f,32,0xff7c8a9b);hint.setTag("child-time-hint");hint.setGravity(Gravity.CENTER);hint.setMaxLines(2);notice.addView(hint);sheet.box.addView(notice,sheet.box.indexOfChild(sheet.actions)-1,new LinearLayout.LayoutParams(-1,-2));
        Button cancel=sheet.action("取消",sheet.dialog::dismiss,false),save=sheet.action("保存",()->{
            if(!authorized)return;
            if(edited.mode==ChildModePolicy.DAILY&&edited.repeatDays==0){hint.setText("请至少选择一天");hint.setTextColor(0xffe3aba1);dayButtons.get(0).requestFocus();return;}
            if(edited.limited()&&customTime[0])try{int h=Integer.parseInt(hours.getText().toString()),m=Integer.parseInt(minutes.getText().toString());if(h<0||h>24||m<0||m>59)throw new NumberFormatException();edited.limit=(h*60L+m)*ChildModePolicy.MINUTE;}catch(NumberFormatException error){hint.setText("请输入 1 分钟至 24 小时，分钟填写 0–59");hint.setTextColor(0xffe3aba1);return;}
            if(edited.repeatDays==0)edited.repeatDays=originalDays;
            try{edited.validate();}catch(IllegalArgumentException error){hint.setText("时长需为 1 分钟至 24 小时");hint.setTextColor(0xffe3aba1);return;}
            if(!store.saveSettings(selected,edited)){hint.setText("保存失败，请重试");return;}
            sheet.dialog.dismiss();refreshSelectedCard();render();details.getChildAt(7).requestFocus();if(edited.limited()&&!ChildUsageService.ready(this)){feedback("时长已保存，计时服务尚未就绪");permission();}
        },true);
        cancel.setTag("child-time-cancel");save.setTag("child-time-save");visual.numberActions(sheet,pad);
        refresh[0]=()->{
            for(int i=0;i<modeButtons.size();i++)modeButtons.get(i).setSelected(edited.mode==i);
            repeats.setVisibility(edited.mode==ChildModePolicy.DAILY?View.VISIBLE:View.GONE);customDates.setVisibility(customDays[0]?View.VISIBLE:View.GONE);
            for(int i=0;i<repeatButtons.size();i++)repeatButtons.get(i).setSelected(i==3?customDays[0]:!customDays[0]&&edited.repeatDays==new int[]{127,31,96}[i]);
            for(int i=0;i<dayButtons.size();i++){Button b=dayButtons.get(i);boolean checked=(edited.repeatDays&(1<<i))!=0;b.setSelected(checked);b.setContentDescription(b.getText()+(checked?"  已选":"  未选"));}
            timeSection.setVisibility(edited.limited()?View.VISIBLE:View.GONE);timeLabel.setText(edited.mode==ChildModePolicy.DAILY?"每日可用":"本次可用");divider.setVisibility(edited.mode==ChildModePolicy.DAILY?View.VISIBLE:View.GONE);
            inputSection.setVisibility(edited.limited()&&customTime[0]?View.VISIBLE:View.GONE);pad.setVisibility(inputSection.getVisibility());
            for(int i=0;i<durationButtons.size();i++)durationButtons.get(i).setSelected(i==3?customTime[0]:!customTime[0]&&edited.limit/ChildModePolicy.MINUTE==new int[]{30,60,120}[i]);
            notice.setVisibility(edited.limited()?View.VISIBLE:View.GONE);hint.setTextColor(0xff7c8a9b);hint.setText(edited.mode==ChildModePolicy.DAILY?"选中日期自动恢复额度，未选日期不可使用":"退出后保留剩余时间，用完需家长授权");
            ArrayList<List<Button>> rows=new ArrayList<>();rows.add(modeButtons);if(edited.mode==ChildModePolicy.DAILY){rows.add(repeatButtons);if(customDays[0])rows.add(dayButtons);}if(edited.limited())rows.add(durationButtons);
            for(int row=0;row<rows.size();row++)for(int column=0;column<rows.get(row).size();column++){
                final int r=row,c=column;Button b=rows.get(row).get(column);View up=row==0?sheet.close:rows.get(row-1).get(Math.min(column,rows.get(row-1).size()-1));View down=row+1<rows.size()?rows.get(row+1).get(Math.min(column,rows.get(row+1).size()-1)):inputSection.getVisibility()==View.VISIBLE?hours:column==0?cancel:save;
                b.setNextFocusUpId(up.getId());b.setNextFocusDownId(down.getId());b.setNextFocusLeftId(rows.get(row).get(Math.max(0,column-1)).getId());b.setNextFocusRightId(rows.get(row).get(Math.min(rows.get(row).size()-1,column+1)).getId());
                b.setOnKeyListener((v,key,event)->{if(key<KeyEvent.KEYCODE_DPAD_UP||key>KeyEvent.KEYCODE_DPAD_RIGHT)return false;if(event.getAction()==KeyEvent.ACTION_DOWN){if(key==KeyEvent.KEYCODE_DPAD_UP)up.requestFocus();else if(key==KeyEvent.KEYCODE_DPAD_DOWN)down.requestFocus();else rows.get(r).get(Math.max(0,Math.min(rows.get(r).size()-1,c+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1)))).requestFocus();}return true;});
            }
            hours.setNextFocusUpId(durationButtons.get(3).getId());minutes.setNextFocusUpId(durationButtons.get(3).getId());
            for(int i=0;i<sheet.actions.getChildCount();i++){View action=sheet.actions.getChildAt(i);action.setNextFocusUpId(inputSection.getVisibility()==View.VISIBLE?((Button[])pad.getTag())[i==0?9:11].getId():rows.get(rows.size()-1).get(edited.limited()?(i==0?0:3):edited.mode).getId());action.setNextFocusLeftId(cancel.getId());action.setNextFocusRightId(save.getId());action.setNextFocusDownId(action.getId());}
            sheet.close.setNextFocusDownId(modeButtons.get(edited.mode).getId());
        };
        refresh[0].run();editing=sheet.dialog;sheet.dialog.setOnDismissListener(d->{sheet.release();if(editing==sheet.dialog)editing=null;});sheet.show(modeButtons.get(edited.mode));
    }
    private void show(Sheet sheet,View focus){editing=sheet.dialog;sheet.dialog.setOnDismissListener(d->{if(editing==sheet.dialog)editing=null;});sheet.show(focus);}
    private void permission(){
        ChildTimerState state=ChildUsageService.status(this);Sheet sheet=new Sheet("儿童模式计时");sheet.text(state.message);
        if(state==ChildTimerState.FAULT)sheet.text(store.fault());
        sheet.text("进入儿童模式管理页不会开启服务。请在系统无障碍中启用“儿童模式计时”，返回后会重新核对。");
        sheet.action("关闭",sheet.dialog::dismiss);Button open=sheet.action("系统无障碍设置",()->{
            sheet.dialog.dismiss();DiagnosticLog.info("child.timer.settings","state="+state.name());
            if(!new SystemSettingsRouter(this).open(Settings.ACTION_ACCESSIBILITY_SETTINGS))message("未找到可用设置入口，请从电视系统设置进入无障碍；已保存的时长不会丢失。");
        });show(sheet,open);
    }

    static String duration(long value){long minutes=value/ChildModePolicy.MINUTE;return value>0&&minutes==0?"不足 1 分钟":minutes>=60?minutes/60+" 小时"+(minutes%60==0?"":" "+minutes%60+" 分钟"):minutes+" 分钟";}
    static String describeTime(ChildModePolicy.Rule r){return !r.limited()?"不限制":(r.mode==ChildModePolicy.DAILY?(r.repeatDays==127?"每天 ":"循环 "):"单次 ")+duration(r.limit);}
    static String usage(ChildModePolicy.Rule r){return r.repeatLabel()+"\n"+(!r.scheduled()&&r.remaining()==0?"今天不可用  下次 "+r.nextDate():"今日已用 "+duration(r.dailyUsed)+"  剩余 "+duration(r.remaining()));}
    static String summary(ChildModePolicy.Rule r){return (r.hidden?"已隐藏  ":"")+(r.locked?"已上锁":r.mode==ChildModePolicy.DAILY&&!r.scheduled()&&r.remaining()==0?"今天不可用":r.limited() && r.remaining()==0?"额度已用完":describeTime(r));}
    @android.annotation.SuppressLint("RestrictedApi")
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(unlock!=null&&gate.hasFocus()&&unlock.dispatchKeyEvent(event))return true;int key=event.getKeyCode();if(event.getAction()==KeyEvent.ACTION_DOWN && list.hasFocus() && key==KeyEvent.KEYCODE_DPAD_RIGHT && first!=null){first.requestFocus();return true;}
        if(event.getAction()==KeyEvent.ACTION_DOWN && list.hasFocus() && key==KeyEvent.KEYCODE_DPAD_UP){RecyclerView.ViewHolder holder=list.findContainingViewHolder(getCurrentFocus());if(holder==null || holder.getBindingAdapterPosition()==0){managementAction.requestFocus();return true;}}
        if(key==KeyEvent.KEYCODE_DPAD_LEFT){if(event.getAction()==KeyEvent.ACTION_DOWN && getCurrentFocus()==first){returning=true;return true;}if(returning){if(event.getAction()==KeyEvent.ACTION_UP){returning=false;list.requestFocus();}return true;}}return false;}
    private final class Cards extends RecyclerView.Adapter<Cards.Holder>{
        final class Holder extends RecyclerView.ViewHolder{final LinearLayout box;final ImageView icon,hidden,policy;final TextView title,state;Holder(LinearLayout box,ImageView icon,TextView title,TextView state,ImageView hidden,ImageView policy){super(box);this.box=box;this.icon=icon;this.title=title;this.state=state;this.hidden=hidden;this.policy=policy;}}
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){LinearLayout box=row();box.setPadding(visual.p(24.04f),visual.p(20.16f),visual.p(24.04f),visual.p(20.16f));box.setFocusable(true);box.setClickable(true);android.graphics.drawable.StateListDrawable bg=new android.graphics.drawable.StateListDrawable();bg.addState(new int[]{android.R.attr.state_focused},visual.shape(0x0cffffff,0xfff6f6f8,21.12f,2));bg.addState(new int[]{android.R.attr.state_selected},visual.shape(0x0cffffff,0x36ffffff,21.12f,1));bg.addState(new int[]{},visual.shape(0,0,21.12f,1));box.setBackgroundDrawable(bg);box.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,visual.p(117.37f));lp.bottomMargin=visual.p(9.6f);box.setLayoutParams(lp);
            ImageView art=visual.appIcon(null,74.88f,15.36f);box.addView(art,new LinearLayout.LayoutParams(visual.p(74.88f),visual.p(74.88f)));LinearLayout copy=column();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=visual.p(26.88f);box.addView(copy,cp);TextView title=visual.text("",27.84f,39,0xffeeeff2);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(title);visual.gap(copy,10.56f);TextView state=visual.text("",18.24f,25.5f,0xff8d96a2);state.setSingleLine();state.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(state);
            LinearLayout statuses=row();statuses.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(visual.p(60),visual.p(24));sp.leftMargin=visual.p(26.88f);box.addView(statuses,sp);ImageView hidden=visual.image(R.drawable.ic_child_hidden,0xff9ca6b4),policy=visual.image(R.drawable.ic_child_unlimited,0xff9ca6b4);statuses.addView(hidden,new LinearLayout.LayoutParams(visual.p(24),visual.p(24)));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(visual.p(24),visual.p(24));pp.leftMargin=visual.p(12);statuses.addView(policy,pp);return new Holder(box,art,title,state,hidden,policy);}
        @Override public void onBindViewHolder(Holder h,int position){ResolveInfo info=visible.get(position);String pkg=info.activityInfo.packageName;ChildModePolicy.Rule rule=store.rule(pkg);h.title.setText(info.loadLabel(getPackageManager()));h.icon.setImageDrawable(info.loadIcon(getPackageManager()));h.state.setText(summary(rule));h.hidden.setVisibility(rule.hidden?View.VISIBLE:View.INVISIBLE);h.policy.setTag(rule.locked?"locked":rule.limited()?"limited":"unlimited");h.policy.setImageDrawable(UiCompat.drawable(activity,rule.locked?R.drawable.ic_child_lock:rule.limited()?R.drawable.ic_child_time:R.drawable.ic_child_unlimited));h.box.setContentDescription(info.loadLabel(getPackageManager())+"  "+summary(rule));h.box.setSelected(selected.equals(pkg));h.box.setTag(pkg);h.box.setOnFocusChangeListener((v,focused)->{if(focused && !selected.equals(pkg)){selected=pkg;for(int i=0;i<list.getChildCount();i++)list.getChildAt(i).setSelected(list.getChildAt(i)==v);render();}});h.box.setOnClickListener(v->{selected=pkg;render();if(first!=null)first.requestFocus();KeyFeedback.confirm(activity);});}
        @Override public int getItemCount(){return visible.size();}
    }
}
