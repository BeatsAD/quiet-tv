package home.quiet.tv;

import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Single source tool over the existing discovery, dispatch, confirmation and recovery engine. */
final class SourceToolsSheet {
    private interface Write {void run()throws Exception;}
    private final ManagementUi ui;
    private final Runnable closed;
    private final SourcePairing pairing;
    private final BootAppStore boot;
    private final DiagnosticExportUi diagnostics;
    private final InputEntryTask inputWork=new InputEntryTask();
    private final Handler main=new Handler(Looper.getMainLooper());
    private ManagementUi.Sheet sheet;
    private View origin;
    private Button close;
    private TextView progress;
    private JSONObject snapshot=new JSONObject();
    private String screen="home",focus="source-target",error="",candidateId="",groupKey="",request="",port="",lastKey="",expectedTarget="",expectedStartup="",pendingWrite="",dismissedPending="",searchParent="home",logsParent="quality";
    private boolean active,destroyed,busy,raw,defaults,enableAfterTest,calibratingSaved;
    private final Runnable poll=new Runnable(){public void run(){if(!open()||!active)return;refresh();main.postDelayed(this,400);}};
    SourceToolsSheet(ManagementUi ui,Runnable closed){this.ui=ui;this.closed=closed;pairing=SourcePairing.get(ui);boot=new BootAppStore(ui);diagnostics=new DiagnosticExportUi(ui);}
    private boolean open(){return !destroyed&&sheet!=null;}
    private int p(float value){return ui.px(value*.75f);}
    private TextView text(String value,float size,float line,int color){TextView view=ui.label(value,12);QuickControlPreviewView.type(view,size,line,ui.unit,color);return view;}
    private void block(View view,float top,float bottom){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);int previous=sheet.body.getChildCount()==0?0:((LinearLayout.LayoutParams)sheet.body.getChildAt(sheet.body.getChildCount()-1).getLayoutParams()).bottomMargin;lp.topMargin=Math.max(0,p(top)-previous);lp.bottomMargin=p(bottom);sheet.body.addView(view,lp);}
    private void copy(String value,boolean muted){block(text(value,muted?14.464f:17.28f,muted?23.8656f:29.376f,UiPalette.SECONDARY),muted?10.24f:17.28f,muted?19.2f:17.28f);}
    private void heading(String value){TextView view=text(value,17.28f,25.92f,UiPalette.SECONDARY);QuickControlPreviewView.weight(view,500);block(view,0,10.24f);}
    private void identify(View view,String tag){view.setTag(tag);view.setId(androidx.core.view.ViewCompat.generateViewId());view.setFocusableInTouchMode(true);view.setOnFocusChangeListener((v,on)->{if(on)focus=tag;});}
    private Button button(String title,String tag,Runnable action,boolean primary){Button view=ui.button(title,()->{if(!busy)action.run();});identify(view,tag);view.setEnabled(!busy);view.setAlpha(1f);view.setTextColor(SettingsFocusDrawable.textColors(primary&&!(ui instanceof AppManagementActivity)?UiPalette.INK:UiPalette.TEXT));view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.712f*ui.unit);view.setPadding(p(17.28f),p(9.6f),p(17.28f),p(9.6f));view.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,21.12f,primary?(ui instanceof AppManagementActivity?UiPalette.SELECTED:0xe6ffffff):UiPalette.CONTROL,false,primary?0x4dffffff:UiPalette.EDGE,false,12));return view;}
    private LinearLayout actions(){LinearLayout view=ui.row();view.setGravity(Gravity.END);view.setClipChildren(false);view.setClipToPadding(false);block(view,24,0);view.getLayoutParams().height=p(46.08f);return view;}
    private Button action(LinearLayout parent,String title,String tag,Runnable operation,boolean primary){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-1);if(parent.getChildCount()>0)lp.leftMargin=p(8);Button view=button(title,tag,operation,primary);parent.addView(view,lp);return view;}
    private Button option(String title,String tag,Runnable operation,boolean primary){Button view=button(title,tag,operation,primary);view.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,14.4f*ui.unit);view.setMinimumHeight(p(52.48f));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=p(9.6f);sheet.body.addView(view,lp);return view;}
    private LinearLayout group(String title){if(!title.isEmpty())heading(title);LinearLayout group=ui.listGroup();group.setBackgroundDrawable(ui.surface(UiPalette.CONTROL,UiPalette.CONTROL,UiPalette.EDGE,15));block(group,0,16);return group;}
    private DesktopSettingsRow row(LinearLayout group,String title,String value,String tag,Runnable operation,boolean enabled){DesktopSettingsRow row=new DesktopSettingsRow(ui,title,"",false);row.hideIcon();row.setTag(tag);row.value(value);row.tail("arrow");row.available(enabled&&!busy);row.setAlpha(1f);row.textSizes(13.92f,11.52f);identify(row,tag);row.setOnClickListener(v->{if(!busy)operation.run();});group.addView(row,new LinearLayout.LayoutParams(-1,p(screen.equals("more")?50:59.629f)));return row;}
    private void save(Write write){try{write.run();error="";render();}catch(Exception failure){fail(failure.getMessage());}}
    private void fail(String value){error=value==null||value.isEmpty()?"信号源操作未完成，请重试":value;busy=false;render();ui.error("信号源操作未完成");}
    void show(){if(open())return;screen=searchParent="home";focus="source-target";error=port=request=candidateId=groupKey=lastKey=pendingWrite=dismissedPending="";raw=enableAfterTest=busy=calibratingSaved=false;active=true;create();}
    private void create(){
        origin=ui.getCurrentFocus();sheet=ui.new Sheet("信号源配置");ManagementUi.Sheet own=sheet;close=sheet.settingsLayout("关闭",this::back);identify(close,"sheet-close");sheet.settingsActions(false);
        sheet.dialog.setOnKeyListener((dialog,key,event)->{if(key!=KeyEvent.KEYCODE_BACK&&key!=KeyEvent.KEYCODE_ESCAPE)return false;if(event.getAction()==KeyEvent.ACTION_UP)back();return true;});
        sheet.dialog.setOnDismissListener(dialog->{if(sheet!=own)return;stopUi();sheet=null;if(origin!=null&&origin.isShown())origin.requestFocusFromTouch();closed.run();});
        try{snapshot=pairing.snapshot();}catch(Exception failure){error="无法读取当前信号源状态";}String target=focus;render();sheet.show(null);focus=target;restoreFocus();resume();
    }
    private void restoreFocus(){if(!open())return;String target=focus;sheet.body.post(()->{if(!open())return;View view=sheet.box.findViewWithTag(target);if(view==null||!view.isEnabled()||!view.isShown())view=close;view.requestFocusFromTouch();android.graphics.Rect rect=new android.graphics.Rect();view.getDrawingRect(rect);view.requestRectangleOnScreen(rect,false);});}
    private void go(String next,String target){OperationFeedback.get(ui).hide();if(next.equals("logs"))logsParent=screen;if(next.equals("home"))calibratingSaved=false;diagnostics.hide();screen=next;focus=target;error="";render();}
    private void render(){
        if(!open())return;String navigation=screen.equals("home")?"关闭":"返回";close.setText(navigation);close.setContentDescription(navigation);sheet.contentSize(448,screen.equals("home")?224:screen.equals("more")?228:440);if(screen.equals("logs")){diagnostics.show(sheet,true);return;}
        String target=focus;sheet.heading.setText(screen.equals("more")?"更多设置":screen.equals("restore-options")?"恢复信号源配置":screen.equals("diagnostics")?"诊断与反馈":screen.equals("calibrate")&&calibratingSaved?"校正端口名称":"信号源配置");sheet.body.removeAllViews();progress=null;sheet.settingsActions(false);
        if(screen.equals("home"))home();else if(screen.equals("more"))more();else if(screen.equals("restore-options"))restoreOptions();else if(screen.equals("search"))searchBody();else if(screen.equals("methods"))methods();else if(screen.equals("test"))testBody();else if(screen.equals("confirm"))confirmation();else if(screen.equals("calibrate"))calibration(false);else if(screen.equals("quality")||screen.equals("diagnostics"))quality();else if(screen.equals("observe"))observationStart();else if(screen.equals("observing"))observing();else if(screen.equals("observed"))observed();else if(screen.equals("observe-port"))calibration(true);else if(screen.equals("finishing"))copy("正在保存记录…",true);else if(screen.equals("restore"))restoreBody();else if(screen.equals("boot"))bootConfirmation();else if(screen.equals("saved-confirm"))savedConfirmation();
        if(busy)copy("正在处理…",true);if(!error.isEmpty()){TextView problem=text(error,15.36f,23.04f,UiPalette.TEXT);problem.setPadding(p(17.92f),p(12.8f),p(17.92f),p(12.8f));problem.setBackgroundDrawable(ui.surface(0x1affffff,0x1affffff,UiPalette.EDGE_STRONG,6.72f));block(problem,16,0);}focus=target;restoreFocus();
    }
    private void home(){
        SourcePairingStore sources=new SourcePairingStore(ui);LinearLayout group=group("");
        row(group,"默认信号源",boot.watchTarget().isEmpty()?(sources.entry()==null?"请选择":"原厂信号源菜单"):boot.watchLabel(),"source-target",()->searchFrom("home"),true);
        DesktopSettingsRow auto=row(group,"开机自动进入","","source-boot",this::toggleBoot,!boot.watchTarget().isEmpty());auto.checked(boot.hdmiBoot());
        String hint=!boot.watchTarget().isEmpty()?"首页点击信号源，直接进入 "+boot.watchLabel():sources.entry()!=null?"首页点击信号源，直接打开原厂菜单":"选择并确认后，首页可直接进入该信号源";
        block(text(hint,14.464f,23.8656f,UiPalette.SECONDARY),0,12);
        Button more=button("更多设置","source-more",()->go("more","source-research"),false);more.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);more.setPadding(p(6.4f),0,p(6.4f),0);more.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,6.72f,android.graphics.Color.TRANSPARENT,false,android.graphics.Color.TRANSPARENT));block(more,0,0);more.getLayoutParams().height=p(36);
    }
    private void more(){
        LinearLayout group=group("");android.graphics.drawable.GradientDrawable divider=new android.graphics.drawable.GradientDrawable();divider.setColor(UiPalette.EDGE);divider.setSize(1,ui.px(.5f));group.setDividerDrawable(divider);group.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        row(group,"重新检测信号源","","source-research",()->searchFrom("more"),true);
        row(group,"校正端口名称",boot.watchTarget().isEmpty()?"":boot.watchLabel(),"source-calibration",this::calibrateSaved,!boot.watchTarget().isEmpty());
        row(group,"诊断与反馈","","source-diagnostics",()->go("diagnostics","source-quality-standard"),true);
        row(group,"恢复信号源配置","","source-restore",()->go("restore-options","source-previous"),true);
    }
    private void calibrateSaved(){searchParent="more";expectedTarget=boot.watchTarget();calibratingSaved=true;search();}
    private void restoreOptions(){
        SourcePairingStore sources=new SourcePairingStore(ui);LinearLayout group=group("");
        row(group,"恢复上次配对前配置","","source-previous",()->{defaults=false;go("restore","source-restore-cancel");},sources.previous().has("fingerprint"));
        row(group,"恢复配对默认设置","","source-defaults",()->{defaults=true;go("restore","source-restore-cancel");},true);
    }
    private void searchFrom(String parent){searchParent=parent;calibratingSaved=false;search();}
    private void search(){try{pairing.cancelSearchFromUi();pairing.search("tv");lastKey="";go("search","source-search-back");refresh();}catch(Exception occupied){fail(occupied.getMessage());}}
    private void searchBody(){
        copy("选择调用方式，测试后确认实际画面和声音。",true);if(snapshot.optString("searchState").equals("SEARCHING")){progress=text(snapshot.optString("stage","正在读取电视输入"),17.28f,29.376f,UiPalette.SECONDARY);block(progress,10.24f,19.2f);}
        LinkedHashMap<String,List<SourceSearch.Candidate>> groups=new LinkedHashMap<>();for(SourceSearch.Candidate item:pairing.candidates()){if(!groups.containsKey(item.key))groups.put(item.key,new ArrayList<>());groups.get(item.key).add(item);}LinearLayout inputs=null,menus=null;
        for(List<SourceSearch.Candidate> values:groups.values()){SourceSearch.Candidate item=values.get(0);LinearLayout parent;if(item.panel==null){if(inputs==null)inputs=group("电视输入");parent=inputs;}else{if(menus==null)menus=group("信号源菜单");parent=menus;}row(parent,item.label,(SourceSearch.confirmed(ui,item)?"已确认":item.connection==0?"已连接":item.connection==1?"待机":"待测试"),"source-candidate-"+item.id,()->select(item.key),!snapshot.optString("searchState").equals("SEARCHING"));}
        if(groups.isEmpty()&&!snapshot.optString("searchState").equals("SEARCHING"))copy("暂未找到可配置的信号源，可记录一次原厂操作辅助识别。",true);
        if(!snapshot.optString("error").isEmpty())copy(snapshot.optString("error"),true);LinearLayout actions=actions();action(actions,"查看检测结果","source-quality",()->go("quality","source-quality-back"),false);action(actions,"辅助识别","source-observe",()->{pairing.cancelSearchFromUi();go("observe","source-observe-start");},false);action(actions,"重新搜索","source-research",this::search,false);action(actions,"返回","source-search-back",()->{pairing.cancelSearchFromUi();go(searchParent,searchParent.equals("more")?"source-research":"source-target");},false);
    }
    private void select(String key){groupKey=key;List<SourceSearch.Candidate> values=groupCandidates();if(values.size()==1){candidateId=values.get(0).id;go("test","source-test-start");}else go("methods","source-method-back");}
    private List<SourceSearch.Candidate> groupCandidates(){List<SourceSearch.Candidate> values=new ArrayList<>();for(SourceSearch.Candidate item:pairing.candidates())if(item.key.equals(groupKey))values.add(item);return values;}
    private void methods(){int number=0;for(SourceSearch.Candidate item:groupCandidates())option("测试方式 "+(++number),"source-method-"+item.id,()->{candidateId=item.id;go("test","source-test-start");},false);LinearLayout actions=actions();action(actions,"返回","source-method-back",()->go("search","source-search-back"),false);}
    private SourceSearch.Candidate candidate(){return pairing.candidate(candidateId);}
    private void testBody(){SourceSearch.Candidate item=candidate();if(item==null){copy("入口已变化，请重新搜索。",true);action(actions(),"返回配对","source-test-back",this::search,false);return;}heading(item.label);copy("测试后返回，核对实际端口、画面和声音。",false);LinearLayout actions=actions();action(actions,"返回","source-test-back",()->go("search","source-candidate-"+item.id),false);action(actions,"开始测试","source-test-start",()->requestTest(""),true);}
    private void requestTest(String label){SourceSearch.Candidate item=candidate();if(item==null){fail("入口已变化，请重新搜索");return;}try{request=pairing.request("tv",item.id,item.scope,pairing.snapshot().getString("revision"),label);calibratingSaved=false;screen="test";error="";ui.feedback("观看后按 HOME 返回，再进入信号源配置确认实际端口、画面和声音");}catch(Exception failure){fail(failure.getMessage());}}
    private void confirmation(){SourceSearch.Candidate item=candidate();if(item==null){copy("测试入口已变化，请重新搜索。",true);action(actions(),"重新搜索","source-research",this::search,false);return;}heading(item.panel!=null?"信号源菜单正常打开了吗？":"端口、画面和声音正常吗？");copy(item.panel!=null?"确认后保存首页信号源入口，端口仍在原厂菜单中选择。":item.label+"  请核对电视实际接线端口。",false);if(enableAfterTest)copy("确认后开启开机自动看电视，并替换当前应用自启。",true);
        option(item.panel!=null?"正常，保存入口":item.target!=null?"正确且正常，保存":"正常，记录结果","source-confirm",()->accept(true),true);if(item.target!=null)option("进入了其他端口","source-calibrate",()->{port="";go("calibrate","source-port");},false);option("没有打开或画面声音异常","source-reject",()->accept(false),false);option("暂不确定","source-uncertain",()->{cancelOwn();enableAfterTest=false;go("home","source-target");},false);
    }
    private void accept(boolean worked){if(busy)return;try{pairing.confirm("tv",request,worked,candidate()!=null&&candidate().panel==null,"user",worked?"用户确认实际端口、画面和声音正常":"用户报告入口或画面声音异常");if(!worked){enableAfterTest=false;go("search","source-search-back");error="测试未通过，原有配置已保留";render();}else{pendingWrite="confirm";busy=true;render();}}catch(Exception failure){fail(failure.getMessage());}}
    private void calibration(boolean observation){copy(observation?"按电视插口标识填写实际编号。记录结果后仍需测试 Quiet TV 的调用方式。":"按电视插口标识填写实际编号，再次测试。",false);TextView label=text("实际 HDMI 编号",16.64f,24.96f,UiPalette.SECONDARY);block(label,12.8f,6.4f);EditText input=ui.input(port,"实际 HDMI 编号");identify(input,"source-port");input.setInputType(InputType.TYPE_CLASS_NUMBER);input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){port=s.toString();}public void afterTextChanged(android.text.Editable value){}});block(input,0,0);LinearLayout actions=actions();action(actions,"返回","source-port-back",()->go(observation?"observed":calibratingSaved?"more":"confirm",observation?"source-observed-hdmi":calibratingSaved?"source-calibration":"source-calibrate"),false);Button confirm=action(actions,observation?"确认并结束记录":"重新测试","source-port-confirm",()->{try{int number=Integer.parseInt(port.trim());if(number<1||number>32)throw new NumberFormatException();if(observation)finishObservation("hdmi",number,true);else{cancelOwn();requestTest("HDMI "+number);}}catch(NumberFormatException invalid){fail("请输入 1–32 的实际 HDMI 编号");}},true);if(observation&&!observationActive()){confirm.setEnabled(false);confirm.setAlpha(.32f);copy("记录已结束，编号已保留，请返回配对重新记录。",true);}}
    private void quality(){LinearLayout group=group("检测结果");JSONObject channels=snapshot.optJSONObject("channels");for(String key:new String[]{"standard","vendor","panels","adb"}){JSONObject value=channels==null?null:channels.optJSONObject(key);String state=value==null?"":value.optString("state");String reason=value==null?"本次尚未完成":state.equals("READ")||state.equals("READY")?"已读取":state.equals("EMPTY")?"没有返回输入":state.equals("FILTERED")?"输入已隐藏或合并":state.equals("RESTRICTED")?"系统限制访问":state.equals("UNSUPPORTED")||state.equals("UNAVAILABLE")?"当前系统未提供此接口":state.equals("ADB_UNAVAILABLE")?"本机调试连接不可用":state.equals("TIMEOUT")?"读取超时":state.equals("DISABLED")?"相关组件已停用":"读取未完成";if(key.equals("vendor")&&value!=null&&value.optBoolean("sdkUnavailable")&&value.optInt("count")==0)reason="未找到可读取的厂商端口协议";row(group,key.equals("standard")?"标准输入":key.equals("vendor")?"厂商端口":key.equals("panels")?"原厂菜单":"调试读取",reason,"source-quality-"+key,()->{},false);}
        LinearLayout actions=actions();action(actions,"导出诊断包","source-diagnostics",()->go("logs","diagnostic-retry"),false);if(!InputAdb.connected())action(actions,"检查本机连接","source-adb",()->{sheet.dialog.dismiss();ui.selectManagementPage(SettingsPages.APPS);},false);action(actions,"返回","source-quality-back",this::back,false);
    }
    private void observationStart(){heading("辅助识别");copy("开启记录后，用原遥控器打开信号源并选择正常播放的端口，返回结束。",true);DesktopSettingsRow rawRow=row(sheet.body,"附加相关原始系统片段","","source-raw",()->{raw=!raw;render();},true);rawRow.checked(raw);copy("原始片段可能含私人信息，导出前请检查。",true);LinearLayout controls=ui.row();controls.setClipChildren(false);block(controls,0,0);controls.getLayoutParams().height=p(46.08f);controls.addView(button("开始记录","source-observe-start",()->{try{pairing.observe("tv",raw);snapshot=pairing.snapshot();go("observing","source-observe-done");}catch(Exception failure){fail(failure.getMessage());}},true),new LinearLayout.LayoutParams(-2,-1));controls.addView(button("返回配对","source-observe-back",()->go("search","source-observe"),false),new LinearLayout.LayoutParams(-2,-1));}
    private void observing(){heading("正在记录原厂操作");copy("请操作原厂信号源，完成后返回此页。",true);progress=text("正在记录…",14.464f,23.8656f,UiPalette.SECONDARY);block(progress,0,19.2f);LinearLayout actions=actions();action(actions,"停止记录","source-observe-stop",()->{cancelOwn();go("finishing","sheet-close");},false);action(actions,"已完成，结束并分析","source-observe-done",()->go("observed","source-observed-menu"),true);}
    private boolean observationActive(){JSONObject value=snapshot.optJSONObject("observation");return value!=null&&(value.optString("state").equals("RUNNING")||value.optString("state").equals("PREPARING"))&&!value.optBoolean("finalizing");}
    private void observed(){heading("原厂操作结果");copy("记录原厂操作结果后，仍需测试 Quiet TV 的调用方式。",true);boolean running=observationActive();if(!running)copy("记录已结束，请返回配对重新记录，或导出已有结果。",true);Button menu=option("正常打开信号源菜单","source-observed-menu",()->finishObservation("menu",0,false),false);Button hdmi=option("已进入 HDMI，画面声音正常","source-observed-hdmi",()->{port="";go("observe-port","source-port");},false);Button failed=option("未打开或画面声音异常","source-observed-failed",()->finishObservation("failed",0,false),false);for(Button button:new Button[]{menu,hdmi,failed}){button.setEnabled(running&&!busy);button.setAlpha(1f);}if(InputSourceObservation.hasEvidence(ui))option("导出信号源诊断","source-diagnostics",()->go("logs","diagnostic-retry"),false);action(actions(),"返回配对","source-observe-back",()->{cancelOwn();go("search","source-observe");},false);}
    private void finishObservation(String outcome,int number,boolean physical){try{InputSourceObservation.stopWithResult("user",outcome,number,physical);go("finishing","sheet-close");}catch(Exception failure){fail(failure.getMessage());}}
    private void restoreBody(){heading(defaults?"恢复配对默认设置？":"恢复上次配对前配置？");copy("调整本功能入口、端口与配对名称，关闭 HDMI 开机自动进入，保留其他桌面设置。",false);LinearLayout actions=actions();action(actions,"取消","source-restore-cancel",()->go("restore-options",defaults?"source-defaults":"source-previous"),false);Button confirm=button("恢复","source-restore-confirm",()->{try{pairing.restore("tv",pairing.snapshot().getString("revision"),defaults);pendingWrite="restore";busy=true;lastKey="";render();}catch(Exception failure){fail(failure.getMessage());}},true);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-1);cp.leftMargin=p(8);actions.addView(confirm,cp);}
    private void toggleBoot(){if(boot.hdmiBoot()){save(()->boot.hdmiBoot(false));return;}if(!boot.tested()){watch(true);return;}expectedTarget=boot.watchTarget();expectedStartup=boot.target();go("boot","source-boot-cancel");}
    private void bootConfirmation(){heading("开启开机自动看电视？");copy("下次完整开机进入 Quiet TV 后自动进入 "+boot.watchLabel()+"。待机唤醒不主动切源。",false);if(boot.enabled())copy("将替换当前开机目标："+boot.label(),false);LinearLayout actions=actions();action(actions,"取消","source-boot-cancel",()->go("home","source-boot"),false);action(actions,"开启","source-boot-confirm",()->prepareSaved(()->{if(!expectedTarget.equals(boot.watchTarget())||!expectedStartup.equals(boot.target()))throw new IOException("开机目标已变化，请重新选择");boot.hdmiBoot(true);go("home","source-boot");}),true);}
    private void savedConfirmation(){String pending=boot.pendingTest();heading("电视画面和声音正常吗？");copy("请确认已进入 "+boot.watchLabel()+"，且实际端口、画面和声音正常。",false);option(enableAfterTest?"正常，开启自动进入":"画面和声音正常","source-saved-confirm",()->prepareSaved(()->{synchronized(BootAppStore.class){if(pending.isEmpty()||!pending.equals(boot.pendingTest())||!pending.equals(boot.watchTarget()))throw new IOException("测试目标已变化，请重新测试");if(enableAfterTest&&!expectedStartup.equals(boot.target()))throw new IOException("开机目标已变化，请重新设置");boot.confirmTest(true);if(enableAfterTest)boot.hdmiBoot(true);}enableAfterTest=false;go("home","source-target");}),true);option("画面或声音异常","source-saved-reject",()->save(()->{boot.confirmTest(false);enableAfterTest=false;go("home","source-target");}),false);option("稍后确认","source-saved-later",()->{dismissedPending=pending;go("home","source-target");},false);}
    private void prepareSaved(Write ready){if(busy)return;String target=boot.watchTarget();busy=true;render();inputWork.submit(()->{HdmiTarget input=HdmiTarget.decode(target);return input==null?null:input.prepare(ui.getApplicationContext());},(launch,failure)->{busy=false;if(!open()||!active)return;if(failure!=null||launch==null||!target.equals(boot.watchTarget())||!launch.current(ui)){fail("信号源或固件已变化，请重新配对");return;}try{prepared=launch;ready.run();}catch(Exception unavailable){fail(unavailable.getMessage());}finally{prepared=null;}});}
    private SavedInputLaunch prepared;
    private void watch(boolean enable){
        if(boot.watchTarget().isEmpty()){if(busy)return;busy=true;render();inputWork.submit(()->{SourceSearch.Candidate item=SourcePairing.preferred(ui,new SourcePairingStore(ui).entry());return item==null?null:item.prepare(ui.getApplicationContext(),null);},(launch,failure)->{busy=false;if(!open()||!active)return;if(launch==null||!launch.current(ui)){fail("入口已变化，请重新配对");return;}dispatch(launch);});return;}
        expectedTarget=boot.watchTarget();expectedStartup=boot.target();prepareSaved(()->{enableAfterTest=enable;if(!boot.tested())boot.testDispatched(expectedTarget);HomeGuardService.bootSourceUserAction(ui,"saved_hdmi");dispatch(prepared);});
    }
    private void dispatch(SavedInputLaunch launch){HomeGuardService.savedInputSelected(ui);if(launch.blocking()){busy=true;inputWork.submit(()->launch.dispatch(ui.getApplicationContext()),(opened,failure)->{busy=false;if(open()&&active)finishLaunch(Boolean.TRUE.equals(opened));});}else finishLaunch(launch.dispatch(ui));}
    private void finishLaunch(boolean opened){if(!opened){HomeGuardService.cancelInputTransition();enableAfterTest=false;dismissedPending=boot.pendingTest();try{boot.testDispatched("");}catch(IOException unavailable){fail("信号源未打开，测试记录未清除，请重新核对");return;}fail("信号源暂时无法打开，请使用遥控器信号源键");return;}HomeGuardService.savedInputSelected(ui);if(!boot.pendingTest().isEmpty())ui.feedback("观看后按 HOME 返回，再进入信号源配置确认画面和声音");}
    private void refresh(){
        if(!open()||screen.equals("logs"))return;
        try{
            JSONObject value=pairing.snapshot();snapshot=value;
            JSONObject test=value.optJSONObject("test"),observation=value.optJSONObject("observation");
            String state=test==null?"":test.optString("state"),observed=observation==null?"":observation.optString("state");
            String key=value.optString("searchId")+value.optString("searchState")+value.optString("stage")+value.optString("error")+value.optBoolean("saving")+state+observed+":"+value.optString("revision")+":"+boot.target()+":"+boot.hdmiBoot();
            if(calibratingSaved&&screen.equals("search")&&!value.optString("searchState").equals("SEARCHING")){
                HdmiTarget saved=HdmiTarget.decode(expectedTarget);SourceSearch.Candidate selected=null;
                if(expectedTarget.equals(boot.watchTarget())&&saved!=null)for(SourceSearch.Candidate item:pairing.candidates())if(saved.matches(item.target)){selected=item;break;}
                if(selected==null){calibratingSaved=false;go("more","source-calibration");fail("当前信号源已变化，请重新选择默认信号源");}
                else{candidateId=selected.id;groupKey=selected.key;port="";go("calibrate","source-port");}
                lastKey=key;return;
            }
            if(screen.equals("finishing")){if(observation==null||!observation.optBoolean("finalizing"))go("search","source-observe");lastKey=key;return;}
            if(screen.equals("observing")&&!observed.equals("RUNNING")&&!observed.equals("PREPARING")){if(observation!=null&&observation.optBoolean("finalizing"))go("finishing","sheet-close");else{go("search","source-observe");fail(observed.equals("prepare_timeout")?"记录准备超时，请检查本机连接后重试":"记录已结束，请重新开始或导出已有结果");}lastKey=key;return;}
            if(!pendingWrite.isEmpty()){
                if(value.optBoolean("saving")){if(!busy){busy=true;render();}}
                else{
                    String operation=pendingWrite;pendingWrite="";busy=false;
                    boolean same="tv".equals(value.optString("owner"))&&(!operation.equals("confirm")||test!=null&&request.equals(test.optString("request")));
                    if(!same){enableAfterTest=false;go("home","source-target");fail("上次任务已结束或发生变化，请核对当前信号源配置");}
                    else if(!value.optString("error").isEmpty())fail(value.optString("error"));
                    else if(operation.equals("confirm")&&!state.equals("SAVED"))fail("测试结果未保存，请重新核对");
                    else{boolean conflict=enableAfterTest&&!expectedStartup.equals(boot.target());if(enableAfterTest&&!conflict&&boot.tested())boot.hdmiBoot(true);enableAfterTest=false;go("home","source-target");if(conflict)fail("信号源已保存，开机目标已变化，请重新设置");else ui.success("信号源配置已保存");}
                }
            }
            else if("tv".equals(value.optString("owner"))&&test!=null&&state.equals("DISPATCHED_UNVERIFIED")&&(!request.equals(test.optString("request"))||screen.equals("test"))){request=test.optString("request");candidateId=test.optString("candidate");screen="confirm";focus="source-uncertain";busy=false;render();}
            else if(test!=null&&state.equals("SAVED")&&request.equals(test.optString("request"))&&(screen.equals("confirm")||screen.equals("test"))){busy=false;enableAfterTest=false;go("home","source-target");}
            else if(test!=null&&request.equals(test.optString("request"))&&(state.equals("FAILED")||state.equals("CANCELLED")||state.equals("EXPIRED"))&&(screen.equals("test")||screen.equals("confirm"))){enableAfterTest=false;go("search","source-search-back");String failure=test.optString("failure");if(failure.isEmpty())failure=value.optString("error");fail(failure.isEmpty()?"测试已结束，请重新测试":failure);}
            else if(!boot.pendingTest().isEmpty()&&!boot.pendingTest().equals(dismissedPending)&&screen.equals("home")){screen="saved-confirm";focus="source-saved-later";render();}
            else if(!key.equals(lastKey)&&(screen.equals("home")||screen.equals("more")||screen.equals("search")||screen.equals("observing")||screen.equals("observed")||screen.equals("observe-port")))render();
            if(progress!=null&&observation!=null&&screen.equals("observing"))progress.setText((observed.equals("PREPARING")?"正在读取操作前的信息":"剩余 "+((observation.optLong("remainingMs")+999)/1000)+" 秒  已记录 "+observation.optInt("samples")+" 次")+(!observed.equals("RUNNING")&&!observed.equals("PREPARING")?"  记录已结束":""));lastKey=key;
        }catch(Exception failure){if(error.isEmpty())fail("读取信号源状态失败，请重新进入");}
    }
    private void cancelOwn(){if(pairing.owner().equals("tv"))pairing.cancel("tv",true);}
    private void back(){
        if(!open())return;if(!pendingWrite.isEmpty()){refresh();if(!pendingWrite.isEmpty()){ui.feedback("正在保存，请稍候");return;}}
        inputWork.cancel();busy=false;
        switch(screen){
            case "home":cancelOwn();sheet.dialog.dismiss();return;
            case "more":go("home","source-more");return;
            case "saved-confirm":dismissedPending=boot.pendingTest();enableAfterTest=false;go("home","source-boot");return;
            case "search":pairing.cancelSearchFromUi();calibratingSaved=false;go(searchParent,searchParent.equals("more")?"source-research":"source-target");return;
            case "methods":case "test":cancelOwn();go("search","source-search-back");return;
            case "calibrate":go(calibratingSaved?"more":"confirm",calibratingSaved?"source-calibration":"source-calibrate");return;
            case "quality":go("search","source-quality");return;
            case "diagnostics":go("more","source-diagnostics");return;
            case "logs":go(logsParent,"source-diagnostics");return;
            case "restore-options":go("more","source-restore");return;
            case "restore":go("restore-options",defaults?"source-defaults":"source-previous");return;
            case "observe-port":go("observed","source-observed-hdmi");return;
            case "observe":case "observing":case "observed":case "finishing":cancelOwn();go("search","source-observe");return;
            default:cancelOwn();enableAfterTest=false;go("home","source-target");
        }
    }
    void resume(){if(!active)dismissedPending="";active=true;if(!open())return;diagnostics.resume();main.removeCallbacks(poll);main.post(poll);}
    void pause(){active=false;inputWork.cancel();busy=false;pairing.cancelSearchFromUi();main.removeCallbacks(poll);diagnostics.pause();}
    private void stopUi(){pause();diagnostics.hide();}
    void save(Bundle state){if(!open())return;Bundle value=new Bundle();value.putString("screen",screen);value.putString("focus",focus);value.putString("error",error);value.putString("candidate",candidateId);value.putString("group",groupKey);value.putString("request",request);value.putString("port",port);value.putString("target",expectedTarget);value.putString("startup",expectedStartup);value.putString("write",pendingWrite);value.putString("dismissed",dismissedPending);value.putString("search-parent",searchParent);value.putString("logs-parent",logsParent);value.putBoolean("calibrating-saved",calibratingSaved);value.putBoolean("raw",raw);value.putBoolean("defaults",defaults);value.putBoolean("enable",enableAfterTest);state.putBundle("source-tools",value);}
    void restore(Bundle state){Bundle value=state==null?null:state.getBundle("source-tools");if(value==null)return;screen=value.getString("screen","home");focus=value.getString("focus","source-target");error=value.getString("error","");candidateId=value.getString("candidate","");groupKey=value.getString("group","");request=value.getString("request","");port=value.getString("port","");expectedTarget=value.getString("target","");expectedStartup=value.getString("startup","");pendingWrite=value.getString("write","");dismissedPending=value.getString("dismissed","");searchParent=value.getString("search-parent","home");logsParent=value.getString("logs-parent","quality");calibratingSaved=value.getBoolean("calibrating-saved");raw=value.getBoolean("raw");defaults=value.getBoolean("defaults");enableAfterTest=value.getBoolean("enable");busy=!pendingWrite.isEmpty();active=true;create();}
    void destroy(){destroyed=true;stopUi();diagnostics.destroy();inputWork.close();if(sheet!=null){sheet.dialog.setOnDismissListener(null);sheet.dialog.dismiss();sheet=null;}}
}
