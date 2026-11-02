package home.quiet.tv;

import android.os.*;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Remote-only UI over the same pairing operations exposed to the computer. */
final class SourcePairingUi {
    private final StartupDestinationPanel host;
    private final SourcePairing pairing;
    private final Handler main=new Handler(Looper.getMainLooper());
    private android.app.Dialog dialog;
    private boolean active,watching;
    private String rendered="",confirmed="",notified="";
    private TextView captureText;
    private final Runnable poll=new Runnable(){public void run(){if(!active)return;refresh();if(watching)main.postDelayed(this,400);}};
    SourcePairingUi(StartupDestinationPanel host){this.host=host;pairing=SourcePairing.get(host);}
    void resume(){active=true;refresh();if(watching){main.removeCallbacks(poll);main.post(poll);}}
    void pause(){active=false;main.removeCallbacks(poll);pairing.cancelSearchFromUi();dismiss();}
    void close(){pause();}
    boolean showing(){return dialog!=null&&dialog.isShowing();}
    private void dismiss(){if(dialog!=null){dialog.dismiss();dialog=null;}captureText=null;}
    private ManagementPanel.Sheet sheet(String title){dismiss();ManagementPanel.Sheet sheet=host.new Sheet(title);dialog=sheet.dialog;sheet.dialog.setOnDismissListener(d->{if(dialog==sheet.dialog){dialog=null;captureText=null;}});return sheet;}
    void open(){
        try{JSONObject state=pairing.snapshot(),test=state.optJSONObject("test");
            if("tv".equals(state.optString("owner"))&&test!=null&&"DISPATCHED_UNVERIFIED".equals(test.optString("state"))){confirmed="";watching=true;refresh();return;}
            if("tv".equals(state.optString("owner"))&&("RUNNING".equals(state.getJSONObject("observation").optString("state"))||"PREPARING".equals(state.getJSONObject("observation").optString("state")))){watching=true;showCapture();main.removeCallbacks(poll);main.post(poll);return;}
            pairing.search("tv");watching=true;rendered="";showProgress();main.removeCallbacks(poll);main.post(poll);}
        catch(Exception busy){showBusy(busy.getMessage());}
    }
    private void showBusy(String reason){
        ManagementPanel.Sheet sheet=sheet("信号源任务");sheet.text(reason==null?"另一项任务正在执行":reason);
        Button back=sheet.action("返回",sheet.dialog::dismiss);
        sheet.option("停止当前任务",()->{pairing.cancel("tv",true);sheet.dialog.dismiss();host.render();});sheet.show(back);
    }
    private void showProgress(){
        ManagementPanel.Sheet sheet=sheet("搜索信号源");captureText=host.muted("正在读取电视输入",14);sheet.body.addView(captureText);
        Button cancel=sheet.action("取消",()->{pairing.cancel("tv",true);watching=false;sheet.dialog.dismiss();});
        sheet.dialog.setOnCancelListener(d->{pairing.cancelSearchFromUi();watching=false;});sheet.show(cancel);
    }
    private void refresh(){
        if(!active||!host.showing)return;
        try{
            JSONObject snapshot=pairing.snapshot(),test=snapshot.optJSONObject("test"),observation=snapshot.getJSONObject("observation");
            if(test!=null&&"tv".equals(snapshot.optString("owner"))&&"DISPATCHED_UNVERIFIED".equals(test.optString("state"))){
                if(!test.optString("request").equals(confirmed)){confirmed=test.getString("request");watching=true;confirm(test);}
                return;
            }
            if(test!=null&&"SAVED".equals(test.optString("state"))&&!test.optString("request").equals(notified)){
                notified=test.optString("request");dismiss();watching=false;host.render();host.feedback("observed_input".equals(test.optString("savedKind"))?"输入测试结果已记录，常用 HDMI 未改变":"信号源配置已保存");return;
            }
            if(!watching)return;
            if("RUNNING".equals(observation.optString("state"))||"PREPARING".equals(observation.optString("state"))){
                if(captureText==null)showCapture();
                if(captureText!=null)captureText.setText("PREPARING".equals(observation.optString("state"))?"正在读取操作前的信息，请稍候…":"请打开原厂信号源并选择端口，完成后返回\n剩余 "+((observation.optLong("remainingMs")+999)/1000)+" 秒  已记录 "+observation.optInt("samples")+" 次");
                return;
            }
            if("SEARCHING".equals(snapshot.optString("searchState"))){if(captureText!=null)captureText.setText(snapshot.optString("stage"));return;}
            if(snapshot.optBoolean("saving"))return;
            String key=snapshot.optString("searchId")+snapshot.optString("searchState")+snapshot.optString("error")+(test==null?"":test.optString("state"))+observation.optString("state");
            if(!key.equals(rendered)){rendered=key;results(snapshot);}
        }catch(Exception failure){host.message("读取信号源任务失败，请重新进入");}
    }
    private void results(JSONObject snapshot){
        ManagementPanel.Sheet sheet=sheet("搜索与配对");
        if(!snapshot.optString("error").isEmpty())sheet.text(snapshot.optString("error"));
        List<SourceSearch.Candidate> values=pairing.candidates();LinkedHashMap<String,List<SourceSearch.Candidate>> groups=new LinkedHashMap<>();
        for(SourceSearch.Candidate candidate:values){if(!groups.containsKey(candidate.key))groups.put(candidate.key,new ArrayList<>());groups.get(candidate.key).add(candidate);}
        if(groups.isEmpty())sheet.text("暂未找到可配置的信号源，可记录一次原厂操作辅助识别。");
        Button first=null;String section="";
        for(List<SourceSearch.Candidate> group:groups.values()){
            SourceSearch.Candidate item=group.get(0);String heading=item.panel==null?"电视输入":"信号源菜单";
            if(!heading.equals(section)){sheet.text(heading);section=heading;}
            Button button=sheet.option(item.label+(SourceSearch.confirmed(host,item)?"  已确认":"")+connection(item.connection),()->select(group));if(first==null)first=button;
        }
        if(!"COMPLETE".equals(snapshot.optString("searchState"))&&!"IDLE".equals(snapshot.optString("searchState")))sheet.text("部分检测未完成，已保留可用结果。");
        JSONObject observation=snapshot.optJSONObject("observation");
        if(observation!=null&&!observation.optString("captureId").isEmpty()){sheet.text(observation.optString("analysis"));sheet.option("导出信号源诊断",this::diagnostic);}
        sheet.option("查看检测结果",()->quality(snapshot));
        sheet.option("重新搜索",this::open);sheet.option("辅助识别",this::observe);
        Button back=sheet.action("返回",()->{watching=false;sheet.dialog.dismiss();host.render();});sheet.show(first==null?back:first);
    }
    private void quality(JSONObject snapshot){
        ManagementPanel.Sheet sheet=sheet("检测结果");JSONObject channels=snapshot.optJSONObject("channels");
        if(channels!=null)for(String key:new String[]{"standard","vendor","panels","adb"}){
            JSONObject value=channels.optJSONObject(key);if(value==null){sheet.text(channelName(key)+"：本次尚未完成");continue;}
            String state=value.optString("state");
            String reason="READ".equals(state)||"READY".equals(state)?"已读取":"EMPTY".equals(state)?"没有返回输入":"FILTERED".equals(state)?"输入已隐藏或合并"
                :"RESTRICTED".equals(state)?"系统限制访问":"UNSUPPORTED".equals(state)||"UNAVAILABLE".equals(state)?"当前系统未提供此接口"
                :"ADB_UNAVAILABLE".equals(state)?"本机调试连接不可用":"TIMEOUT".equals(state)?"读取超时":"DISABLED".equals(state)?"相关组件已停用":"读取未完成";
            if("vendor".equals(key)&&value.optBoolean("sdkUnavailable")&&value.optInt("count")==0)reason="未找到可读取的厂商端口协议";
            sheet.text(channelName(key)+"："+reason);
        }
        if(!InputAdb.connected()){
            sheet.text("可进入应用管理，点击“ADB 未连接”并确认电视授权，再返回看电视重新搜索。");
            sheet.option("检查本机连接",()->{watching=false;sheet.dialog.dismiss();host.activity.selectManagementPage(0);});
        }
        if(InputSourceObservation.hasEvidence(host))sheet.option("导出信号源诊断",this::diagnostic);
        Button back=sheet.action("返回",()->{rendered="";refresh();});sheet.show(back);
    }
    private String channelName(String key){return "standard".equals(key)?"标准输入":"vendor".equals(key)?"厂商端口":"panels".equals(key)?"原厂菜单":"调试读取";}
    private String connection(int value){return value==0?"  已连接":value==1?"  待机":value==2?"  未连接":"";}
    private void select(List<SourceSearch.Candidate> group){
        if(group.size()==1){testSheet(group.get(0),"");return;}
        ManagementPanel.Sheet sheet=sheet(group.get(0).label);int number=0;Button first=null;
        for(SourceSearch.Candidate candidate:group){Button button=sheet.option("测试方式 "+(++number),()->testSheet(candidate,""));if(first==null)first=button;}
        sheet.action("返回",()->{rendered="";refresh();});sheet.show(first);
    }
    private void testSheet(SourceSearch.Candidate candidate,String label){
        ManagementPanel.Sheet sheet=sheet(candidate.label);sheet.text(candidate.panel!=null?"测试后返回“看电视”，确认是否打开了信号源菜单。":"请开启外接设备。测试后返回“看电视”，确认实际端口、画面和声音。");
        Button cancel=sheet.action("返回",()->{rendered="";refresh();});
        sheet.option("开始测试",()->{
            try{String revision=pairing.snapshot().getString("revision");pairing.request("tv",candidate.id,candidate.scope,revision,label);
                sheet.dialog.dismiss();watching=true;confirmed="";}
            catch(Exception failure){host.message(failure.getMessage());}
        });sheet.show(cancel);
    }
    private void confirm(JSONObject test)throws Exception{
        SourceSearch.Candidate candidate=pairing.candidate(test.getString("candidate"));if(candidate==null)return;
        String request=test.getString("request");
        ManagementPanel.Sheet sheet=sheet(candidate.panel!=null?"信号源菜单正常打开了吗？":"端口、画面和声音正常吗？");
        if(candidate.panel!=null)sheet.text("确认后将保存为首页信号源入口，仍需在原厂菜单内选择端口。");
        else sheet.text(candidate.target!=null?"请核对实际接线端口。保存后作为常用信号源，开机自动进入需要单独开启。":"此输入可从首页信号源打开。本次只记录测试结果，不保存为常用 HDMI 或开机目标。");
        Button uncertain=sheet.action("暂不确定",()->{sheet.dialog.dismiss();watching=false;});
        sheet.option(candidate.panel!=null?"正常，保存入口":candidate.target!=null?"正确且正常，保存":"正常，记录结果",()->{
            try{pairing.confirm("tv",request,true,candidate.panel==null,"user",candidate.panel!=null?"用户确认原厂信号源菜单正常打开":"用户确认实际端口、画面与声音正常");
                sheet.dialog.dismiss();watching=true;main.removeCallbacks(poll);main.post(poll);}
            catch(Exception failure){host.message(failure.getMessage());}
        });
        if(candidate.target!=null)sheet.option("进入了其他端口",()->calibrate(candidate));
        sheet.option("没有打开或画面声音异常",()->{
            try{pairing.confirm("tv",request,false,false,"user","用户报告入口或画面声音异常");sheet.dialog.dismiss();rendered="";watching=true;refresh();}
            catch(Exception failure){host.message(failure.getMessage());}
        });sheet.show(uncertain);
    }
    private void calibrate(SourceSearch.Candidate candidate){
        ManagementPanel.Sheet sheet=sheet("确认实际 HDMI 插口");sheet.text("按电视插口标识填写实际编号，接下来会重新测试；这不会增加输入端口。");
        EditText port=host.input("","实际 HDMI 编号");port.setInputType(InputType.TYPE_CLASS_NUMBER);sheet.body.addView(port);
        Button back=sheet.action("返回",()->{confirmed="";sheet.dialog.dismiss();refresh();});
        sheet.option("重新测试",()->{try{
            int number=Integer.parseInt(port.getText().toString().trim());if(number<1||number>32)throw new NumberFormatException();
            pairing.cancel("tv",true);testSheet(candidate,"HDMI "+number);
        }catch(NumberFormatException invalid){host.message("请输入电视插口上的 HDMI 编号");}});sheet.show(back);
    }
    void details(){
        ManagementPanel.Sheet sheet=sheet("电视信号源");BootAppStore boot=new BootAppStore(host);
        sheet.text(boot.watchTarget().isEmpty()?"尚未保存直接观看的 HDMI。":boot.watchLabel()+(boot.tested()?"  已确认":"  待确认"));
        Button first=sheet.option("搜索与配对",this::open);
        sheet.option("重新确认测试结果",()->{confirmed="";watching=true;sheet.dialog.dismiss();refresh();});
        sheet.option("恢复上次配对前配置",()->restore(false));sheet.option("恢复配对默认设置",()->restore(true));
        sheet.action("返回",sheet.dialog::dismiss);sheet.show(first);
    }
    private void restore(boolean defaults){
        ManagementPanel.Sheet sheet=sheet(defaults?"恢复配对默认设置？":"恢复上次配对前配置？");
        sheet.text("将调整本功能的入口、端口与配对名称，关闭 HDMI 开机自动进入。原有厂商维护规则和其他桌面设置保留。");
        Button cancel=sheet.action("取消",sheet.dialog::dismiss);
        sheet.option("恢复",()->{try{pairing.restore("tv",pairing.snapshot().getString("revision"),defaults);sheet.dialog.dismiss();watching=true;rendered="";main.post(poll);}
            catch(Exception failure){host.message(failure.getMessage());}});sheet.show(cancel);
    }
    private void observe(){
        ManagementPanel.Sheet sheet=sheet("辅助识别");sheet.text("开始后请按原遥控器信号源键，或从原厂设置打开菜单并选择一个正常播放的 HDMI。最多记录两分钟，完成后返回此页结束。");
        CheckBox raw=new CheckBox(host);raw.setText("附加相关原始系统片段");raw.setTextColor(ManagementUi.MUTED);raw.setTextSize(13);sheet.body.addView(raw);
        sheet.text("原始片段可能含页面私人信息，仅保存在电视，导出前请检查。");
        Button back=sheet.action("返回",()->{rendered="";refresh();});
        sheet.option("开始记录",()->{try{pairing.observe("tv",raw.isChecked());watching=true;showCapture();main.removeCallbacks(poll);main.post(poll);}
            catch(Exception failure){host.message(failure.getMessage());}});sheet.show(back);
    }
    private void showCapture(){
        ManagementPanel.Sheet sheet=sheet("正在记录原厂操作");captureText=host.muted("正在读取操作前的信息，请稍候…",14);sheet.body.addView(captureText);
        Button done=sheet.option("已完成，结束并分析",this::observationResult);
        sheet.option("停止记录",()->{InputSourceObservation.stop("cancelled");sheet.dialog.dismiss();rendered="";refresh();});
        sheet.dialog.setOnCancelListener(d->{watching=false;});sheet.show(done);
    }
    private void observationResult(){
        ManagementPanel.Sheet sheet=sheet("原厂操作的实际结果");sheet.text("只记录原厂操作结果，仍需测试 Quiet TV 的调用方式才能保存配对。");
        Button unsure=sheet.action("暂不确定",()->finishObservation("unknown",0,false));
        sheet.option("正常打开信号源菜单",()->finishObservation("menu",0,false));
        sheet.option("已进入 HDMI，画面声音正常",()->{
            ManagementPanel.Sheet portSheet=sheet("确认实际 HDMI 编号");EditText port=host.input("","电视插口上的编号");port.setInputType(InputType.TYPE_CLASS_NUMBER);portSheet.body.addView(port);
            Button back=portSheet.action("返回",this::observationResult);
            portSheet.option("确认并结束记录",()->{try{int number=Integer.parseInt(port.getText().toString().trim());if(number<1||number>32)throw new NumberFormatException();finishObservation("hdmi",number,true);}
                catch(NumberFormatException invalid){host.message("请填写电视实际 HDMI 插口编号");}});portSheet.show(back);
        });
        sheet.option("未打开或画面声音异常",()->finishObservation("failed",0,false));sheet.show(unsure);
    }
    private void finishObservation(String outcome,int port,boolean physical){
        try{InputSourceObservation.stopWithResult("user",outcome,port,physical);dismiss();rendered="";watching=true;refresh();}
        catch(Exception failure){host.message(failure.getMessage());}
    }
    void diagnostic(){host.activity.selectManagementPage(5);}
}
