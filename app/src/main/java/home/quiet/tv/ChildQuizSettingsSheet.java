package home.quiet.tv;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** All surfaces edit the same settings; this local draft starts with the next question. */
final class ChildQuizSettingsSheet {
    interface Authorize {void run(Runnable action);}
    private interface Choice {void choose(String value);}
    private final Authorize authorize;
    private final ManagementUi ui;
    private final Runnable closed;
    private ManagementUi.Sheet sheet;
    private ChildQuizStore store;
    private ChildQuizState.Settings draft;
    private ChildQuizQuestionsSheet questions;
    private String pkg="",before="",focus="",error="";
    private boolean pending;
    ChildQuizSettingsSheet(ManagementUi ui,Runnable closed,Authorize authorize){this.ui=ui;this.closed=closed;this.authorize=authorize;questions=new ChildQuizQuestionsSheet(ui,authorize,()->{if(sheet!=null){sheet.dialog.show();render();}else closed.run();});}
    private int p(float value){return ui.px(value*.75f);}
    private TextView text(String value){TextView view=ui.label(value,12);QuickControlPreviewView.type(view,14.72f,24,ui.unit,UiPalette.SECONDARY);return view;}
    void show(){show("");}
    void show(String app){if(sheet!=null){sheet.dialog.show();return;}pkg=app;try{store=ChildQuizStore.get(ui);ChildQuizState state=store.read();draft=state.settings(pkg);before=draft.json().toString();focus="";pending=state.session()!=null;create();}catch(IOException|JSONException failure){ui.message(failure.getMessage());}}
    void showQuestions(){questions.show();}
    private void create(){sheet=ui.new Sheet("答题设置");sheet.settingsLayout("关闭",this::close);sheet.contentSize(448,480);sheet.dialog.setOnDismissListener(dialog->{sheet=null;closed.run();});sheet.dialog.setOnKeyListener((dialog,key,event)->KeyFeedback.backKey(ui,event,sheet!=null,this::close));render();sheet.show(focusTarget(focus));}
    private View focusTarget(String tag){View value=sheet.box.findViewWithTag(tag);return value!=null&&value.isFocusable()&&value.isEnabled()&&value.getVisibility()==View.VISIBLE?value:sheet.box.findViewWithTag("quiz-interval-"+draft.interval+" 分钟");}
    private Button button(String label,String tag,Runnable action,boolean chosen){Button value=ui.button(label,action);value.setTag(tag);value.setId(androidx.core.view.ViewCompat.generateViewId());value.setFocusableInTouchMode(true);value.setOnFocusChangeListener((view,on)->{if(on)focus=tag;});value.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,14.08f*ui.unit);value.setPadding(p(10),0,p(10),0);ChildModeUi.styleChoice(value,ui.unit);value.setSelected(chosen);value.setText(SettingsSelectionMark.label(label,chosen,ui.unit));value.setSingleLine(true);value.setGravity(Gravity.CENTER);value.setContentDescription(label+(chosen?"  已选择":""));return value;}
    private void choices(String title,String[] labels,String prefix,java.util.Set<String> selected,Choice change){sheet.body.addView(text(title));for(int start=0;start<labels.length;start+=3){LinearLayout row=ui.row();for(int index=start;index<Math.min(start+3,labels.length);index++){String label=labels[index],tag=prefix+label;Button value=button(label,tag,()->{focus=tag;change.choose(label);},selected.contains(label));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,p(45),1);if(index>start)lp.leftMargin=p(9);row.addView(value,lp);}LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=p(8);lp.bottomMargin=p(start+3>=labels.length?18:0);sheet.body.addView(row,lp);}}
    private void edit(int interval,String grade,String[] subjects){try{draft=new ChildQuizState.Settings(interval,grade,subjects);error="";}catch(IOException e){error=e.getMessage();}render();}
    private void render(){
        if(sheet==null)return;String target=focus;sheet.body.removeAllViews();sheet.actions.removeAllViews();sheet.settingsActions(true);
        choices("答题间隔",new String[]{"10 分钟","15 分钟","20 分钟"},"quiz-interval-",Collections.singleton(draft.interval+" 分钟"),value->edit(Integer.parseInt(value.split(" ")[0]),draft.grade,draft.subjects));
        choices("科目",ChildQuizBank.SUBJECTS,"quiz-subject-",new HashSet<>(Arrays.asList(draft.subjects)),value->{ArrayList<String> next=new ArrayList<>(Arrays.asList(draft.subjects));if(next.contains(value)){if(next.size()==1)return;next.remove(value);}else next.add(value);edit(draft.interval,draft.grade,next.toArray(new String[0]));});
        if(!draft.customOnly()){
            choices("年级",ChildQuizBank.GRADES,"quiz-grade-",Collections.singleton(draft.grade),value->edit(draft.interval,value,draft.subjects));
        }
        sheet.text("答对后才能继续观看，观看总额度保持不变。");if(pending)sheet.text("本轮题目保持不变，新设置从下一轮生效。");if(!error.isEmpty())sheet.text(error);
        sheet.action("取消",this::close);Button save=sheet.action("保存",()->authorize.run(this::commit));save.setTag("quiz-save");if(sheet.dialog.isShowing())sheet.body.post(()->{if(sheet==null)return;View value=focusTarget(target);if(value!=null&&value.isShown())value.requestFocusFromTouch();});
    }
    private void commit(){try{ChildQuizState.Settings next=draft;store.change(state->{if(!before.equals(state.settings(pkg).json().toString()))throw new IOException("答题设置已变化，请重新打开后修改");state.configure(pkg,next);});ChildUsageService.changed();PhoneSettingsSession.get(ui).configurationChanged();close();ui.success("答题设置已保存");}catch(IOException failure){error=failure.getMessage();render();}}
    void close(){if(sheet!=null)sheet.dialog.dismiss();}
    void pause(){if(sheet!=null)sheet.dialog.hide();questions.pause();}
    void resume(){if(!questions.resume()&&sheet!=null){sheet.dialog.show();render();}}
    void save(Bundle state){questions.save(state);if(sheet==null)return;try{Bundle value=new Bundle();value.putString("package",pkg);value.putString("draft",draft.json().toString());value.putString("before",before);value.putString("focus",focus);value.putBoolean("pending",pending);state.putBundle("quiz-settings",value);}catch(JSONException ignored){}}
    void restore(Bundle state){if(state==null)return;Bundle value=state.getBundle("quiz-settings");if(value==null){try{questions.restore(state);}catch(IOException failure){ui.feedback("原题目草稿无法读取，已保存题目保留");}return;}try{pkg=value.getString("package","");store=ChildQuizStore.get(ui);draft=new ChildQuizState.Settings(new JSONObject(value.getString("draft")));before=new ChildQuizState.Settings(new JSONObject(value.getString("before"))).json().toString();focus=value.getString("focus","");pending=value.getBoolean("pending");create();if(questions.restore(state))sheet.dialog.hide();}catch(IOException|JSONException invalid){ui.feedback("原答题草稿无法读取，已保存设置保留");}}
    void destroy(){questions.destroy();if(sheet!=null){sheet.dialog.setOnDismissListener(null);sheet.dialog.dismiss();sheet=null;}}
}
