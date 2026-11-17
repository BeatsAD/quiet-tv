package home.quiet.tv;

import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Applications use the same master/detail geometry and remote contract as app management. */
final class ChildModePanel extends ManagementPanel {
    @Override boolean useSettingListStyle(){return false;}
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final android.os.Handler clock=new android.os.Handler(android.os.Looper.getMainLooper());
    private final ArrayList<ResolveInfo> apps=new ArrayList<>(),visible=new ArrayList<>();
    private final Map<String,String> appNames=new HashMap<>(),appStates=new HashMap<>();
    private final Map<String,Drawable> appIcons=new HashMap<>();
    private final ArrayList<DesktopSettingsRow> ruleRows=new ArrayList<>(),sharedRows=new ArrayList<>();
    private final ArrayList<Button> filters=new ArrayList<>();
    private RecyclerView list;
    private Cards cards;
    private LinearLayout details,shared;
    private ScrollView rulesScroll;
    private Button parentButton;
    private ChildModeStore store;
    private ChildModeUi visual;
    private ChildQuizSettingsSheet quizSettings;
    private ChildMobileConnectionSheet mobileConnection;
    private ChildQuizState learning;
    private HomePreviewCache catalog;
    private long catalogPackages=-1;
    private String catalogAliases="";
    private Map<String,?> catalogGuard=Collections.emptyMap();
    private boolean catalogLoaded,catalogRebind;
    private TextView ruleError;
    private String selected="",focusTag="",shownDay="",learningError="";
    private int loadGeneration,filter;
    private boolean authorized,loading;private String authorizationStamp="";
    private android.os.Parcelable listState;
    private android.app.Dialog editing,parents;
    private final Runnable timerChanged=()->runOnUiThread(()->{if(showing)refresh();});
    private final Runnable phoneChanged=()->{if(showing){load();if(editing==null)refresh();else cards.notifyDataSetChanged();}};
    private final Runnable catalogChanged=()->runOnUiThread(()->{if(showing)load();});
    private final Runnable refreshDay=new Runnable(){public void run(){if(!showing)return;String today=ChildModeStore.today();if(!today.equals(shownDay)){shownDay=today;refresh();}clock.postDelayed(this,1000);}};
    ChildModePanel(AppManagementActivity activity){super(activity);}
    private int p(float value){return px(value*.75f);}
    private TextView copy(String value,float size,float line,int color){TextView view=label(value,12);QuickControlPreviewView.type(view,size,line,unit,color);return view;}
    @Override public void onCreate(Bundle state){
        store=new ChildModeStore(this);catalog=HomePreviewCache.get(this);visual=new ChildModeUi(activity);mobileConnection=new ChildMobileConnectionSheet(activity,this::authorize);
        if(state!=null){selected=state.getString("selected","");focusTag=state.getString("focus","");filter=state.getInt("filter");listState=state.getParcelable("list-state");}filter=Math.max(0,Math.min(2,filter));
        LinearLayout body=managementContent();pageRoot.setClipChildren(false);pageRoot.setClipToPadding(false);managementInfo.getLayoutParams().height=px(34.56f);((LinearLayout.LayoutParams)body.getLayoutParams()).topMargin=px(12);
        String[] names={"全部应用","已开启","未开启"};for(int i=0;i<names.length;i++){final int index=i;Button option=button(names[i],()->selectFilter(index));option.setTag("child-filter-"+i);option.setId(androidx.core.view.ViewCompat.generateViewId());option.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,12*unit);option.setPadding(px(9),0,px(9),px(3));option.setBackgroundDrawable(new SelectionSurface(true));option.setTextColor(SettingsFocusDrawable.textColors(UiPalette.SECONDARY));option.setOnFocusChangeListener((v,on)->{if(on){focusTag="child-filter-"+index;if(filter!=index)selectFilter(index);}});filters.add(option);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-2,px(33));if(i>0)fp.leftMargin=px(12);managementInfo.addView(option,fp);}
        managementInfo.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));parentButton=button("家长设置",()->authorize(this::parentSettings));parentButton.setTag("child-parents");parentButton.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,12*unit);parentButton.setPadding(px(14),0,px(14),0);parentButton.setBackgroundDrawable(new SettingsFocusDrawable(unit,999,UiPalette.CONTROL,false,UiPalette.EDGE));managementInfo.addView(parentButton,new LinearLayout.LayoutParams(-2,-1));
        LinearLayout columns=row();columns.setGravity(Gravity.TOP);columns.setClipChildren(false);columns.setClipToPadding(false);body.addView(columns,new LinearLayout.LayoutParams(-1,-1));
        FrameLayout left=new FrameLayout(activity);left.setClipChildren(false);left.setClipToPadding(false);columns.addView(left,new LinearLayout.LayoutParams(0,-1,40));
        list=new RecyclerView(this){@Override protected void dispatchDraw(Canvas canvas){int save=canvas.save();canvas.clipRect(0,0,getWidth(),getHeight());super.dispatchDraw(canvas);canvas.restoreToCount(save);}};list.setLayoutManager(new LinearLayoutManager(this));list.setItemAnimator(null);list.setClipToPadding(false);list.setClipChildren(true);list.setVerticalScrollBarEnabled(false);list.setPadding(px(12.36f),px(11.88f),px(12.36f),px(14.76f));cards=new Cards();list.setAdapter(cards);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-1);lp.setMargins(px(-9),px(-9),px(-9),px(-9));left.addView(list,lp);
        LinearLayout right=column();right.setClipChildren(false);right.setClipToPadding(false);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-1,57);rp.setMargins(px(28),px(2.88f),0,0);columns.addView(right,rp);
        rulesScroll=activity.settingsScroll();rulesScroll.setFillViewport(true);rulesScroll.setVerticalScrollBarEnabled(false);rulesScroll.setClipToPadding(false);rulesScroll.setClipChildren(false);rulesScroll.setBackgroundDrawable(surface(UiPalette.CONTROL,UiPalette.CONTROL,UiPalette.EDGE,15));details=column();details.setClipChildren(false);details.setClipToPadding(false);details.setPadding(px(16),px(8),px(16),px(8));rulesScroll.addView(details);right.addView(rulesScroll,new LinearLayout.LayoutParams(-1,0,1));rulesScroll.addOnLayoutChangeListener((v,a,b,c,d,e,f,g,h)->sizeRows());
        LinearLayout.LayoutParams qr=new LinearLayout.LayoutParams(-1,-2);qr.topMargin=px(10);right.addView(mobileConnection.view(),qr);
        quizSettings=new ChildQuizSettingsSheet(activity,()->{if(showing){refresh();focusRule("child-questions".equals(focusTag)?6:2);}},this::authorize);quizSettings.restore(state);refresh();
    }
    @Override protected void onResume(){super.onResume();catalog.listen(catalogChanged);PhoneSettingsSession.get(activity).observeConfiguration(phoneChanged);ChildUsageService.listen(timerChanged);shownDay=ChildModeStore.today();clock.post(refreshDay);load();refresh();quizSettings.resume();mobileConnection.resume();}
    @Override protected void onPause(){catalog.unlisten(catalogChanged);PhoneSettingsSession.get(activity).forgetConfiguration(phoneChanged);remember();authorized=false;loadGeneration++;loading=false;ChildUsageService.unlisten(timerChanged);clock.removeCallbacks(refreshDay);quizSettings.pause();mobileConnection.pause();ChildModeAuth.dismiss(activity);if(editing!=null)editing.dismiss();if(parents!=null)parents.dismiss();super.onPause();}
    @Override protected void onDestroy(){quizSettings.destroy();mobileConnection.destroy();worker.shutdown();super.onDestroy();}
    private void remember(){if(list!=null)listState=list.getLayoutManager().onSaveInstanceState();View value=getCurrentFocus();if(value!=null&&pageRoot.hasFocus()&&value.getTag() instanceof String)focusTag=(String)value.getTag();}
    @Override protected void onSaveInstanceState(Bundle state){remember();state.putString("selected",selected);state.putString("focus",focusTag);state.putInt("filter",filter);state.putParcelable("list-state",listState);quizSettings.save(state);}
    private void authorize(Runnable operation){if(!showing)return;if(authorized&&authorizationStamp.equals(store.credential())){operation.run();return;}authorized=false;quizSettings.pause();ChildModeAuth.show(activity,()->{if(!showing)return;authorized=true;authorizationStamp=store.credential();quizSettings.resume();operation.run();},()->{if(showing){quizSettings.resume();focusManagementContent();}});}
    private String appName(ResolveInfo info){return appNames.get(info.activityInfo.packageName);}
    private void load(){
        if(loading)return;long packages=catalog.packages();String aliases=new AppNameStore(this).signature();Map<String,?> guard=new HomeGuardState(this).preferences.getAll();
        if(catalogLoaded&&catalogPackages==packages&&catalogAliases.equals(aliases)&&catalogGuard.equals(guard))return;
        loading=true;int version=++loadGeneration;
        worker.execute(()->{ArrayList<ResolveInfo> found=new ArrayList<>();Map<String,String> names=new HashMap<>();Map<String,Drawable> icons=new HashMap<>();String error="";
            try{store.moveHiddenToAppManagement();AppNameStore naming=new AppNameStore(this);for(ResolveInfo info:new HomeAppCatalog(this).launchable().values())if(store.eligible(info.activityInfo.packageName)){String pkg=info.activityInfo.packageName;names.put(pkg,naming.display(pkg,info.loadLabel(getPackageManager()).toString()));icons.put(pkg,info.loadIcon(getPackageManager()));found.add(info);}java.text.Collator order=java.text.Collator.getInstance(Locale.SIMPLIFIED_CHINESE);Collections.sort(found,(a,b)->{int compared=order.compare(names.get(a.activityInfo.packageName),names.get(b.activityInfo.packageName));return compared==0?a.activityInfo.packageName.compareTo(b.activityInfo.packageName):compared;});}
            catch(RuntimeException unavailable){error=unavailable.getMessage()==null?"应用列表读取失败，请重新进入":unavailable.getMessage();}
            String failure=error;runOnUiThread(()->{if(!showing||version!=loadGeneration)return;loading=false;apps.clear();apps.addAll(found);appNames.clear();appNames.putAll(names);appIcons.clear();appIcons.putAll(icons);catalogRebind=true;catalogLoaded=failure.isEmpty();if(catalogLoaded){catalogPackages=packages;catalogAliases=aliases;catalogGuard=guard;}refresh();if(!failure.isEmpty())feedback(failure);else load();});
        });
    }
    private boolean enabled(String pkg){return store.appEnabled(pkg,learning!=null&&(learning.enabled(pkg)||learning.session()!=null&&learning.pending(pkg)));}
    private void selectFilter(int next){filter=next;applyFilter();render();}
    @android.annotation.SuppressLint("NotifyDataSetChanged") private void applyFilter(){
        ArrayList<ResolveInfo> previous=new ArrayList<>(visible);visible.clear();for(ResolveInfo app:apps){boolean on=enabled(app.activityInfo.packageName);if(filter==0||filter==1&&on||filter==2&&!on)visible.add(app);}
        if(current()==null)selected=visible.isEmpty()?"":visible.get(0).activityInfo.packageName;for(int i=0;i<filters.size();i++)filters.get(i).setSelected(i==filter);
        boolean replaced=catalogRebind||!previous.equals(visible);catalogRebind=false;
        for(int i=0;i<visible.size();i++){String pkg=visible.get(i).activityInfo.packageName;String text=enabled(pkg)?"儿童模式已开启  "+summary(store.rule(pkg)):"儿童模式未开启";String old=appStates.put(pkg,text);if(!replaced&&!text.equals(old))cards.notifyItemChanged(i,Boolean.TRUE);}
        if(replaced)cards.notifyDataSetChanged();if(listState!=null){list.getLayoutManager().onRestoreInstanceState(listState);listState=null;}
    }
    private ResolveInfo current(){for(ResolveInfo info:visible)if(selected.equals(info.activityInfo.packageName))return info;return null;}
    private void refresh(){try{learning=ChildQuizStore.get(this).read();learningError="";}catch(IOException failure){learning=null;learningError=failure.getMessage();}boolean inList=list.hasFocus();String pkg=selected;applyFilter();render();if(inList&&!pkg.equals(selected))focusApp();if(parents!=null&&parents.isShowing())renderParents();}
    private void render(){
        boolean had=details.hasFocus();String old=getCurrentFocus()!=null&&getCurrentFocus().getTag() instanceof String?(String)getCurrentFocus().getTag():"";ResolveInfo app=current();if(app==null){details.removeAllViews();ruleRows.clear();ruleError=null;details.addView(copy(loading?"正在读取应用…":"没有符合条件的应用",15.36f,23.04f,UiPalette.SECONDARY));return;}if(ruleRows.isEmpty())details.removeAllViews();
        String pkg=selected;ChildModePolicy.Rule rule=store.rule(pkg);boolean managed=enabled(pkg),active=managed&&store.enabled();
        DesktopSettingsRow mode=rule("对此应用开启儿童模式","child-app-enabled",()->authorize(()->toggleApp(pkg)));mode.checked(managed);mode.value(store.enabled()?"":"总开关已关闭");
        DesktopSettingsRow quiz=rule("定时答题","child-quiz",()->authorize(()->{try{ChildQuizStore.get(this).change(value->value.enable(pkg,!value.enabled(pkg)));refresh();focusRule(1);}catch(IOException failure){error(failure.getMessage());}}));quiz.checked(learning!=null&&(learning.enabled(pkg)||learning.session()!=null&&learning.pending(pkg)));quiz.available(active&&!rule.locked&&learning!=null);
        DesktopSettingsRow settings=rule("答题设置","child-settings",()->authorize(()->quizSettings.show(pkg)));settings.tail("arrow");try{ChildQuizState.Settings value=learning==null?null:learning.settings(pkg);settings.value(value==null?"读取失败":value.grade+"  每 "+value.interval+" 分钟");}catch(IOException error){settings.value("读取失败");}settings.available(active&&!rule.locked&&learning!=null);
        DesktopSettingsRow time=rule("使用时长","child-time",()->authorize(this::time));time.value(rule.locked?"已锁定":rule.limited()&&!ChildUsageService.ready(this)?"计时未生效":describeTime(rule));time.tail("arrow");time.available(active&&!rule.locked);
        DesktopSettingsRow locked=rule("锁定应用","child-locked",()->authorize(()->{ChildModePolicy.Rule next=store.rule(pkg);next.locked=!next.locked;save(next,4);}));locked.checked(rule.locked);locked.available(active);
        DesktopSettingsRow now=rule("立即答题","child-quiz-now",()->authorize(()->{try{ChildUsageService.startNow(this,java.util.UUID.randomUUID().toString(),pkg);}catch(IOException error){error(error.getMessage());}}));now.tail("arrow");now.available(active&&!rule.locked&&learning!=null&&ChildUsageService.ready(this));
        DesktopSettingsRow questions=rule("自定义题目","child-questions",()->authorize(()->quizSettings.showQuestions()));questions.tail("arrow");questions.available(active&&!rule.locked&&learning!=null);
        if(!learningError.isEmpty()){if(ruleError==null){ruleError=copy(learningError,14.08f,23.94f,UiPalette.TEXT);details.addView(ruleError);}ruleError.setText(learningError);ruleError.setVisibility(View.VISIBLE);}else if(ruleError!=null)ruleError.setVisibility(View.GONE);sizeRows();if(had){View restored=details.findViewWithTag(old);if(restored!=null&&restored.isEnabled()){if(!restored.hasFocus())restored.requestFocusFromTouch();}else focusRule(0);}
    }
    private void toggleApp(String pkg){
        boolean next=!enabled(pkg);try{if(!next)ChildQuizStore.get(this).change(value->value.cancelPending(pkg));else{ChildQuizStore.get(this).change(value->value.configure(pkg,value.settings(pkg)));}ChildModePolicy.Rule rule=store.rule(pkg);rule.enabled=next;if(!store.saveSettings(pkg,rule)){error("保存失败，请重试");return;}PhoneSettingsSession.get(activity).configurationChanged();refresh();if(selected.equals(pkg))focusRule(0);else focusApp();}catch(IOException error){error(error.getMessage());}
    }
    private DesktopSettingsRow rule(String title,String tag,Runnable action){DesktopSettingsRow row=details.findViewWithTag(tag);if(row==null){row=new DesktopSettingsRow(activity,title,"",false);row.hideIcon();row.childStyle();row.setTag(tag);row.setOnFocusChangeListener((v,on)->{if(on)focusTag=tag;});ruleRows.add(row);details.addView(row,new LinearLayout.LayoutParams(-1,px(36)));}row.setOnClickListener(v->action.run());return row;}
    private void sizeRows(){if(rulesScroll==null||ruleRows.isEmpty()||rulesScroll.getHeight()==0)return;int height=Math.max(px(30),Math.min(px(42),(rulesScroll.getHeight()-details.getPaddingTop()-details.getPaddingBottom())/7));for(DesktopSettingsRow row:ruleRows)if(row.getLayoutParams().height!=height){row.getLayoutParams().height=height;row.requestLayout();}}
    private void parentSettings(){ManagementUi.Sheet sheet=activity.new Sheet("家长设置");sheet.settingsLayout("关闭",sheet.dialog::dismiss);sheet.settingsActions(false);shared=column();sheet.body.addView(shared);parents=sheet.dialog;sheet.dialog.setOnDismissListener(d->{parents=null;if(showing)parentButton.requestFocusFromTouch();});renderParents();sheet.show(sharedRows.get(0));}
    private void renderParents(){
        if(shared==null)return;View focused=parents==null?null:parents.getCurrentFocus();Object old=focused==null?null:focused.getTag();boolean had=shared.hasFocus();shared.removeAllViews();sharedRows.clear();
        DesktopSettingsRow mode=sharedRow("儿童模式总开关","child-mode-toggle",()->authorize(this::toggleMode));mode.checked(store.enabled());
        sharedRow("休息屏保","child-rest",()->authorize(this::rest)).value(new String[]{"森林","云朵","星空"}[store.restTheme()]);
        sharedRow("家长密码","child-password",()->{if(store.configured())authorize(()->ChildModeAuth.change(activity,()->{refresh();focusShared(3);}));else authorize(()->{refresh();focusShared(3);});}).value(store.configured()?"已设置":"未设置");
        sharedRow("计时服务","child-service",this::permission).value(timerLabel(ChildUsageService.status(this)));
        sharedRow("已连接手机","child-phones",()->authorize(mobileConnection::show));
        sharedRow("家长密码恢复码","child-recovery",()->ChildModeAuth.show(activity,()->ChildModeAuth.backup(activity,store,()->{if(parents!=null){View row=shared.findViewWithTag("child-recovery");if(row!=null)row.requestFocusFromTouch();}}))).value("验证后查看");
        if(had&&old instanceof String){View value=shared.findViewWithTag(old);if(value!=null)value.requestFocusFromTouch();}
    }
    private DesktopSettingsRow sharedRow(String title,String tag,Runnable action){DesktopSettingsRow row=new DesktopSettingsRow(activity,title,"",false);row.hideIcon();row.childStyle();row.setTag(tag);row.tail("arrow");row.setOnClickListener(v->action.run());sharedRows.add(row);shared.addView(row,new LinearLayout.LayoutParams(-1,px(44.16f)));return row;}
    private String timerLabel(ChildTimerState state){switch(state){case OFF:return "未授权";case CONNECTING:return "连接中";case READY:return "已连接";case FAULT:return "服务异常";default:return "状态未知";}}
    private void toggleMode(){if(!store.setEnabled(!store.enabled())){error("保存失败，请重试");return;}refresh();focusShared(0);}
    private void save(ChildModePolicy.Rule rule,int focus){if(!store.saveSettings(selected,rule)){error("保存失败，请重试");return;}PhoneSettingsSession.get(activity).configurationChanged();refresh();focusRule(focus);}
    private void refreshSelectedCard(){for(int i=0;i<visible.size();i++)if(selected.equals(visible.get(i).activityInfo.packageName)){cards.notifyItemChanged(i);return;}}
    private void focusRule(int index){for(int i=Math.max(0,index);i<ruleRows.size();i++)if(ruleRows.get(i).isEnabled()){ruleRows.get(i).requestFocusFromTouch();return;}}
    private void focusShared(int oldIndex){int index=oldIndex==0?0:oldIndex-1;if(parents!=null&&index>=0&&index<sharedRows.size())sharedRows.get(index).requestFocusFromTouch();else if(showing)parentButton.requestFocusFromTouch();}
    private void focusApp(){for(int i=0;i<list.getChildCount();i++)list.getChildAt(i).setSelected(("child-app-"+selected).equals(list.getChildAt(i).getTag()));for(int i=0;i<visible.size();i++)if(selected.equals(visible.get(i).activityInfo.packageName)){int position=i;list.scrollToPosition(i);list.post(()->{if(!showing)return;RecyclerView.ViewHolder holder=list.findViewHolderForAdapterPosition(position);if(holder!=null)holder.itemView.requestFocusFromTouch();});return;}filters.get(filter).requestFocusFromTouch();}
    @Override void focusManagementContent(){View value=focusTag.isEmpty()?null:pageRoot.findViewWithTag(focusTag);if(value!=null&&value.isEnabled()&&value.isShown())value.requestFocusFromTouch();else focusApp();}
    @Override public void handleBack(){if(details.hasFocus()||mobileConnection.view().hasFocus())focusApp();else if(list.hasFocus())filters.get(filter).requestFocusFromTouch();else if(!managementTabFocused())managementTab().requestFocusFromTouch();else super.handleBack();}
    private void rest(){Sheet sheet=new Sheet("休息屏保");sheet.delegate.settingsLayout("关闭",sheet.dialog::dismiss);sheet.delegate.settingsActions(false);sheet.text("达到使用时长后进入休息屏保。");LinearLayout options=row();sheet.body.addView(options);String[] names={"云朵","森林","星空"},files={"cloud","forest","space"};int[] values={1,0,2};for(int i=0;i<3;i++){int index=i;LinearLayout option=column();option.setGravity(Gravity.CENTER);option.setPadding(p(6.4f),p(6.4f),p(6.4f),p(6.4f));option.setFocusable(true);option.setFocusableInTouchMode(true);option.setClickable(true);option.setContentDescription("休息屏保"+names[i]);option.setBackgroundDrawable(new SettingsFocusDrawable(unit,12,store.restTheme()==values[i]?UiPalette.SELECTED:UiPalette.CONTROL,false,store.restTheme()==values[i]?UiPalette.TEXT:0x29ffffff));ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);try(InputStream input=getAssets().open("child-rest/"+files[i]+".webp")){BitmapFactory.Options sample=new BitmapFactory.Options();sample.inSampleSize=4;image.setImageBitmap(BitmapFactory.decodeStream(input,null,sample));}catch(IOException ignored){}option.addView(image,new LinearLayout.LayoutParams(-1,p(99.84f)));TextView title=copy(names[i],15.36f,23.04f,UiPalette.TEXT);title.setText(SettingsSelectionMark.label(names[i],store.restTheme()==values[i],unit));title.setGravity(Gravity.CENTER);option.addView(title);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(i>0)lp.leftMargin=p(12.8f);options.addView(option,lp);SettingsTouchClick.attach(option);option.setOnClickListener(view->{if(!store.restTheme(values[index])){sheet.text("保存失败，请重试");return;}for(int item=0;item<options.getChildCount();item++){LinearLayout card=(LinearLayout)options.getChildAt(item);card.setBackgroundDrawable(new SettingsFocusDrawable(unit,12,UiPalette.CONTROL,false,values[item]==values[index]?UiPalette.TEXT:0x29ffffff));((TextView)card.getChildAt(1)).setText(SettingsSelectionMark.label(names[item],values[item]==values[index],unit));}});}editing=sheet.dialog;sheet.dialog.setOnDismissListener(dialog->{if(editing==sheet.dialog)editing=null;if(showing)focusShared(2);});sheet.show(options.getChildAt(store.restTheme()==0?1:store.restTheme()==1?0:2));}
    private void permission(){boolean fromRules=parents==null;ChildTimerState state=ChildUsageService.status(this);Sheet sheet=new Sheet("儿童模式计时");sheet.delegate.settingsLayout("关闭",sheet.dialog::dismiss);sheet.text(state.message);if(state==ChildTimerState.FAULT)sheet.text(store.fault());sheet.text("请在系统无障碍中启用“儿童模式计时”，返回后会重新核对。");Button close=sheet.action("关闭",sheet.dialog::dismiss);sheet.action("系统无障碍设置",()->{sheet.dialog.dismiss();DiagnosticLog.info("child.timer.settings","state="+state.name());if(!new SystemSettingsRouter(this).open(Settings.ACTION_ACCESSIBILITY_SETTINGS))feedback("未找到可用设置入口，请从电视系统设置进入无障碍");});editing=sheet.dialog;sheet.dialog.setOnDismissListener(dialog->{if(editing==sheet.dialog)editing=null;if(showing){if(fromRules)focusRule(3);else focusShared(4);}});sheet.show(close);}
    private void time(){
        if(!authorized || selected.isEmpty())return;
        ChildModePolicy.Rule edited=store.rule(selected);if(edited.locked)return;ChildModePolicy.Rule expectedSettings=edited.copy();String expectedCredential=store.credential();
        final int originalDays=edited.repeatDays;
        final boolean[] customTime={!Arrays.asList(30L,60L,120L).contains(edited.limit/ChildModePolicy.MINUTE)},customDays={edited.repeatDays!=127&&edited.repeatDays!=31&&edited.repeatDays!=96};
        ChildModeUi.Sheet sheet=visual.new Sheet("使用时长",appName(current()),"timer",825.6f,true);
        sheet.box.setTag("child-time-sheet");sheet.scrollBody(840);
        final Runnable[] refresh={null};ArrayList<Button> modeButtons=new ArrayList<>(),repeatButtons=new ArrayList<>(),dayButtons=new ArrayList<>(),durationButtons=new ArrayList<>();
        visual.gap(sheet.body,22);LinearLayout modes=row();sheet.body.addView(modes,new LinearLayout.LayoutParams(-1,visual.p(56)));
        String[] labels={"不限制","单次限时","循环限时"};for(int i=0;i<3;i++){
            final int mode=i;Runnable select=()->{edited.mode=mode;refresh[0].run();};Button b=visual.button(labels[i],select,false);b.setId(androidx.core.view.ViewCompat.generateViewId());b.setTag("child-mode-"+i);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,visual.font(20.16f));visual.choice(b,12);b.setOnFocusChangeListener((v,on)->{if(on)select.run();});modeButtons.add(b);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);if(i>0)lp.leftMargin=visual.p(12);modes.addView(b,lp);
        }
        LinearLayout repeats=column();repeats.setTag("child-repeat-section");sheet.body.addView(repeats,new LinearLayout.LayoutParams(-1,-2));visual.gap(repeats,22);
        repeats.addView(visual.text("重复日期",21,30,ManagementUi.TEXT));visual.gap(repeats,10);LinearLayout presets=row();repeats.addView(presets,new LinearLayout.LayoutParams(-1,visual.p(54)));
        String[] names={"每天","周一至周五","周六和周日","自定义"};for(int i=0;i<4;i++){
            final int which=i;Runnable select=()->{customDays[0]=which==3;if(which<3)edited.repeatDays=new int[]{127,31,96}[which];refresh[0].run();};Button b=visual.button(names[i],select,false);b.setId(androidx.core.view.ViewCompat.generateViewId());b.setTag("child-repeat-preset-"+i);b.setPadding(0,0,0,0);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,visual.font(18));visual.choice(b,12);b.setOnFocusChangeListener((v,on)->{if(on)select.run();});repeatButtons.add(b);
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
        inputRow.addView(hours,new LinearLayout.LayoutParams(visual.p(112),visual.p(58)));inputRow.addView(visual.text(" 小时",17.28f,32,UiPalette.SECONDARY),new LinearLayout.LayoutParams(visual.p(65),-1));inputRow.addView(minutes,new LinearLayout.LayoutParams(visual.p(112),visual.p(58)));inputRow.addView(visual.text(" 分钟",17.28f,32,UiPalette.SECONDARY),new LinearLayout.LayoutParams(visual.p(65),-1));
        visual.gap(inputSection,14);LinearLayout pad=visual.numberPad(sheet,hours,minutes);inputSection.addView(pad);
        String[] choices={"30 分钟","1 小时","2 小时","自定义"};for(int i=0;i<4;i++){
            final int which=i;Runnable select=()->{boolean enteringCustom=which==3&&!customTime[0];customTime[0]=which==3;if(which<3)edited.limit=new int[]{30,60,120}[which]*ChildModePolicy.MINUTE;else if(enteringCustom){hours.setText(String.valueOf(edited.limit/ChildModePolicy.MINUTE/60));minutes.setText(String.valueOf(edited.limit/ChildModePolicy.MINUTE%60));}refresh[0].run();};Button b=visual.button(choices[i],()->{select.run();if(which==3)hours.requestFocus();},false);b.setId(androidx.core.view.ViewCompat.generateViewId());b.setTag("child-duration-"+i);b.setPadding(0,0,0,0);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,visual.font(19));visual.choice(b,12);b.setOnFocusChangeListener((v,on)->{if(on)select.run();});durationButtons.add(b);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);if(i>0)lp.leftMargin=visual.p(9);durations.addView(b,lp);
        }
        LinearLayout notice=column();visual.gap(notice,18);TextView hint=visual.text("",15.5f,32,UiPalette.FAINT);hint.setTag("child-time-hint");hint.setGravity(Gravity.CENTER);hint.setMaxLines(2);notice.addView(hint);sheet.box.addView(notice,sheet.box.indexOfChild(sheet.actions)-1,new LinearLayout.LayoutParams(-1,-2));
        Button cancel=sheet.action("取消",sheet.dialog::dismiss,false),save=sheet.action("保存",()->{
            if(!authorized)return;
            if(edited.mode==ChildModePolicy.DAILY&&edited.repeatDays==0){hint.setText("请至少选择一天");hint.setTextColor(UiPalette.TEXT);dayButtons.get(0).requestFocus();return;}
            if(edited.limited()&&customTime[0])try{int h=Integer.parseInt(hours.getText().toString()),m=Integer.parseInt(minutes.getText().toString());if(h<0||h>24||m<0||m>59)throw new NumberFormatException();edited.limit=(h*60L+m)*ChildModePolicy.MINUTE;}catch(NumberFormatException error){hint.setText("请输入 1 分钟至 24 小时，分钟填写 0–59");hint.setTextColor(UiPalette.TEXT);return;}
            if(edited.repeatDays==0)edited.repeatDays=originalDays;
            try{edited.validate();}catch(IllegalArgumentException error){hint.setText("时长需为 1 分钟至 24 小时");hint.setTextColor(UiPalette.TEXT);return;}
            if(!expectedCredential.equals(store.credential())){hint.setText("家长凭据已变化，请取消后重新验证");return;}if(!store.saveSettings(selected,edited,expectedSettings)){hint.setText("配置已变化或保存未完成，请取消后核对当前设置");return;}
            sheet.dialog.dismiss();refreshSelectedCard();render();focusRule(3);success("使用时长已保存");if(edited.limited()&&!ChildUsageService.ready(this)){feedback("时长已保存，计时服务尚未就绪");permission();}
        },true);
        cancel.setTag("child-time-cancel");save.setTag("child-time-save");visual.numberActions(sheet,pad);
        refresh[0]=()->{
            for(int i=0;i<modeButtons.size();i++)visual.selected(modeButtons.get(i),labels[i],edited.mode==i);
            repeats.setVisibility(edited.mode==ChildModePolicy.DAILY?View.VISIBLE:View.GONE);customDates.setVisibility(customDays[0]?View.VISIBLE:View.GONE);
            for(int i=0;i<repeatButtons.size();i++)visual.selected(repeatButtons.get(i),names[i],i==3?customDays[0]:!customDays[0]&&edited.repeatDays==new int[]{127,31,96}[i]);
            for(int i=0;i<dayButtons.size();i++){Button b=dayButtons.get(i);boolean checked=(edited.repeatDays&(1<<i))!=0;String label="周"+"一二三四五六日".charAt(i);visual.selected(b,label,checked);b.setContentDescription(label+(checked?"  已选":"  未选"));}
            timeSection.setVisibility(edited.limited()?View.VISIBLE:View.GONE);timeLabel.setText(edited.mode==ChildModePolicy.DAILY?"每日可用":"本次可用");divider.setVisibility(edited.mode==ChildModePolicy.DAILY?View.VISIBLE:View.GONE);
            inputSection.setVisibility(edited.limited()&&customTime[0]?View.VISIBLE:View.GONE);pad.setVisibility(inputSection.getVisibility());
            for(int i=0;i<durationButtons.size();i++)visual.selected(durationButtons.get(i),choices[i],i==3?customTime[0]:!customTime[0]&&edited.limit/ChildModePolicy.MINUTE==new int[]{30,60,120}[i]);
            notice.setVisibility(edited.limited()?View.VISIBLE:View.GONE);hint.setTextColor(UiPalette.FAINT);hint.setText(edited.mode==ChildModePolicy.DAILY?"选中日期自动恢复额度，未选日期不可使用":"退出后保留剩余时间，用完需家长授权");
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
        refresh[0].run();editing=sheet.dialog;sheet.dialog.setOnDismissListener(d->{sheet.release();if(editing==sheet.dialog)editing=null;if(showing)focusRule(3);});sheet.show(modeButtons.get(edited.mode));
    }
    static String duration(long value){long minutes=value/ChildModePolicy.MINUTE;return value>0&&minutes==0?"不足 1 分钟":minutes>=60?minutes/60+" 小时"+(minutes%60==0?"":" "+minutes%60+" 分钟"):minutes+" 分钟";}
    static String describeTime(ChildModePolicy.Rule r){return !r.limited()?"不限时":(r.mode==ChildModePolicy.DAILY?(r.repeatDays==127?"每天 ":"循环 "):"单次 ")+duration(r.limit);}
    static String usage(ChildModePolicy.Rule r){return r.repeatLabel()+"\n"+(!r.scheduled()&&r.remaining()==0?"今天不可用  下次 "+r.nextDate():"今日已用 "+duration(r.dailyUsed)+"  剩余 "+duration(r.remaining()));}
    static String summary(ChildModePolicy.Rule r){return r.locked?"已锁定":r.mode==ChildModePolicy.DAILY&&!r.scheduled()&&r.remaining()==0?"今天不可用":r.limited()&&r.remaining()==0?"额度已用完":describeTime(r);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();if(key<KeyEvent.KEYCODE_DPAD_UP||key>KeyEvent.KEYCODE_DPAD_RIGHT)return false;View focused=getCurrentFocus();if(focused==null)return false;
        int option=filters.indexOf(focused);boolean parent=focused==parentButton,rail=list.hasFocus(),rules=details.hasFocus(),qr=mobileConnection.view().hasFocus();if(option<0&&!parent&&!rail&&!rules&&!qr)return false;
        if(event.getAction()!=KeyEvent.ACTION_DOWN)return true;
        if(option>=0||parent){if(key==19)managementTab().requestFocusFromTouch();else if(key==20){if(parent&&!ruleRows.isEmpty())focusRule(0);else focusApp();}else if(key==21){if(parent)filters.get(filters.size()-1).requestFocusFromTouch();else if(option>0)filters.get(option-1).requestFocusFromTouch();}else if(key==22){if(!parent){if(option+1<filters.size())filters.get(option+1).requestFocusFromTouch();else parentButton.requestFocusFromTouch();}}return true;}
        if(rail){if(key==22){focusRule(0);return true;}if(key==21)return true;int index=0;for(int i=0;i<visible.size();i++)if(selected.equals(visible.get(i).activityInfo.packageName))index=i;if(key==19&&index==0){filters.get(filter).requestFocusFromTouch();return true;}int next=index+(key==19?-1:1);if(next>=0&&next<visible.size()){selected=visible.get(next).activityInfo.packageName;render();focusApp();}return true;}
        if(key==21){focusApp();return true;}if(qr){if(key==19){for(int i=ruleRows.size()-1;i>=0;i--)if(ruleRows.get(i).isEnabled()){ruleRows.get(i).requestFocusFromTouch();break;}}return true;}
        int at=ruleRows.indexOf(focused);if(at<0)return true;if(key==19||key==20){int step=key==19?-1:1,next=at+step;while(next>=0&&next<ruleRows.size()&&!ruleRows.get(next).isEnabled())next+=step;if(next>=0&&next<ruleRows.size())ruleRows.get(next).requestFocusFromTouch();else if(key==19)parentButton.requestFocusFromTouch();else if(mobileConnection.view().isFocusable())mobileConnection.view().requestFocusFromTouch();}return true;
    }
    private final class SelectionSurface extends LayerDrawable {
        private final boolean filter;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean selected,focused;
        SelectionSurface(boolean filter){super(new Drawable[]{new SettingsFocusDrawable(unit,filter?999:12,Color.TRANSPARENT)});this.filter=filter;}
        @Override protected boolean onStateChange(int[] states){boolean window=false,focus=false;selected=false;int count=0;for(int value:states){if(value==android.R.attr.state_selected)selected=true;else count++;if(value==android.R.attr.state_focused)focus=true;if(value==android.R.attr.state_window_focused)window=true;}focused=focus&&window;int[] base=new int[count];int index=0;for(int value:states)if(value!=android.R.attr.state_selected)base[index++]=value;super.onStateChange(base);invalidateSelf();return true;}
        @Override public void draw(Canvas canvas){Rect bounds=getBounds();RectF area=new RectF(bounds);if(selected&&!filter&&!focused){paint.setStyle(Paint.Style.FILL);paint.setColor(0x0bffffff);SurfaceCorners.draw(canvas,area,px(12),px(12),paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.75f*unit);paint.setColor(UiPalette.EDGE);area.inset(.375f*unit,.375f*unit);SurfaceCorners.draw(canvas,area,px(12),px(12),paint);paint.setStyle(Paint.Style.FILL);}super.draw(canvas);if(selected&&filter){paint.setColor(focused?UiPalette.INK:UiPalette.TEXT);float width=px(18),height=px(3);area.set(bounds.exactCenterX()-width/2,bounds.bottom-height,bounds.exactCenterX()+width/2,bounds.bottom);SurfaceCorners.draw(canvas,area,height/2,height/2,paint);}}
    }
    private final class Cards extends RecyclerView.Adapter<Cards.Holder>{
        final class Holder extends RecyclerView.ViewHolder {final LinearLayout box;final ImageView icon;final TextView title,state;Holder(LinearLayout box,ImageView icon,TextView title,TextView state){super(box);this.box=box;this.icon=icon;this.title=title;this.state=state;}}
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout box=row();box.setGravity(Gravity.CENTER_VERTICAL);box.setPadding(px(12),px(8),px(12),px(8));box.setFocusable(true);box.setFocusableInTouchMode(true);box.setClickable(true);box.setClipChildren(false);box.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);box.setBackgroundDrawable(new SelectionSurface(false));SettingsTouchClick.attach(box);box.setLayoutParams(new RecyclerView.LayoutParams(-1,px(58.32f)));
            ImageView art=icon(null,38);LinearLayout.LayoutParams ip=(LinearLayout.LayoutParams)art.getLayoutParams();ip.width=ip.height=px(38.4f);ip.rightMargin=px(12);art.setLayoutParams(ip);box.addView(art);
            LinearLayout copy=column();copy.setGravity(Gravity.CENTER_VERTICAL);TextView name=label("",14),state=label("",11);name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,14.4f*unit);state.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,10.56f*unit);for(TextView text:new TextView[]{name,state}){text.setSingleLine();text.setEllipsize(android.text.TextUtils.TruncateAt.END);text.setDuplicateParentStateEnabled(true);text.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));}state.setAlpha(.7f);copy.addView(name);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=px(5);copy.addView(state,sp);copy.setDuplicateParentStateEnabled(true);box.addView(copy,new LinearLayout.LayoutParams(0,-1,1));return new Holder(box,art,name,state);
        }
        @Override public void onBindViewHolder(Holder holder,int position){
            ResolveInfo info=visible.get(position);String pkg=info.activityInfo.packageName;holder.title.setText(appName(info));Drawable art=appIcons.get(pkg);holder.icon.setImageDrawable(art.getConstantState()==null?art:art.getConstantState().newDrawable(getResources()));holder.box.setTag("child-app-"+pkg);bindState(holder,pkg);
            holder.box.setOnFocusChangeListener((view,focused)->{if(focused){focusTag="child-app-"+pkg;if(!selected.equals(pkg)){selected=pkg;for(int i=0;i<list.getChildCount();i++)list.getChildAt(i).setSelected(("child-app-"+pkg).equals(list.getChildAt(i).getTag()));render();}}});holder.box.setOnClickListener(view->{holder.box.requestFocusFromTouch();if(!selected.equals(pkg)){selected=pkg;render();}focusRule(0);KeyFeedback.confirm(activity);});
        }
        @Override public void onBindViewHolder(Holder holder,int position,List<Object> payloads){if(payloads.isEmpty())onBindViewHolder(holder,position);else bindState(holder,visible.get(position).activityInfo.packageName);}
        private void bindState(Holder holder,String pkg){holder.state.setText(appStates.get(pkg));holder.box.setSelected(pkg.equals(selected));holder.box.setContentDescription(holder.title.getText()+"  "+holder.state.getText());}
        @Override public int getItemCount(){return visible.size();}
    }
}
