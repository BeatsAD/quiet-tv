package home.quiet.tv;

import android.content.Context;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.core.view.ViewCompat;
import java.io.*;
import java.util.*;
import org.json.*;

/** Parent-owned single-question editing. Current TV rounds use their own frozen copy. */
final class ChildQuizQuestionsSheet {
    private final ManagementUi ui;
    private final ChildQuizSettingsSheet.Authorize authorize;
    private final Runnable closed;
    private ManagementUi.Sheet sheet;
    private ChildQuizStore store;
    private final LinkedHashSet<String> selected=new LinkedHashSet<>();
    private String baseline="",editing="",prompt="",error="",remove="";
    private String[] choices={"","","",""};
    private int answer;
    private long revision;
    private boolean editor;
    private Button closeButton;
    private EditText question;
    private EditText[] options;
    ChildQuizQuestionsSheet(ManagementUi ui,ChildQuizSettingsSheet.Authorize authorize,Runnable closed){this.ui=ui;this.authorize=authorize;this.closed=closed;}
    private int p(int value){return ui.px(value*.75f);}
    void show(){try{store=ChildQuizStore.get(ui);ChildQuizState state=store.read();JSONArray ids=state.data.getJSONArray("selectedCustomIds");baseline=ids.toString();selected.clear();selected.addAll(Arrays.asList(ChildQuizState.strings(ids,new String[0])));editor=false;create();}catch(IOException|JSONException e){ui.message(e.getMessage());closed.run();}}
    private void create(){
        sheet=ui.new Sheet(editor?"编辑题目":"自定义题目");
        closeButton=sheet.settingsLayout(editor?"返回":"关闭",this::back);
        sheet.contentSize(448,440);
        sheet.scroll.setFocusable(false);
        sheet.dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        sheet.dialog.setOnDismissListener(d->{sheet=null;closed.run();});
        sheet.dialog.setOnKeyListener((d,key,event)->KeyFeedback.backKey(ui,event,true,this::back));
        BackNavigation.install(sheet.dialog,this::back);
        // Set initial focus before attaching the window while the password dialog may still be closing.
        render().requestFocus();
        sheet.show(null);
    }
    private void back(){
        if(!editor){close();return;}
        keyboard().hideSoftInputFromWindow(question.getWindowToken(),0);
        editor=false;
        error="";
        render();
    }
    private View render(){
        sheet.body.removeAllViews();
        sheet.actions.removeAllViews();
        sheet.heading.setText(editor?"编辑题目":"自定义题目");
        closeButton.setText(editor?"返回":"关闭");
        Button cancel=sheet.action(editor?"返回":"取消",this::back);
        Button save=sheet.action(editor?"保存题目":"使用所选题目",()->authorize.run(()->{if(editor)saveQuestion();else saveSelection();}));
        View first=closeButton;
        try{
            if(editor){editView(cancel,save);first=question;}
            else first=listView();
        }catch(IOException|JSONException e){error=e.getMessage();}
        if(!error.isEmpty())sheet.text(error);
        if(sheet.dialog.isShowing())first.requestFocusFromTouch();
        return first;
    }
    private View listView()throws IOException,JSONException{
        sheet.text("建议返回儿童模式页面，用手机扫码编辑题目。");
        Button add=sheet.option("新增题目",()->edit(null));
        add.setId(ViewCompat.generateViewId());
        add.setNextFocusUpId(closeButton.getId());
        closeButton.setNextFocusDownId(add.getId());
        View first=add;
        JSONArray rows=store.read().data.getJSONArray("customQuestions");
        if(rows.length()==0)sheet.text("尚未添加自定义题目");
        for(int i=0;i<rows.length();i++){
            JSONObject item=rows.getJSONObject(i);String id=item.getString("id");
            CheckBox check=new CheckBox(ui);check.setText(item.getString("prompt"));
            check.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,12,UiPalette.CONTROL));
            check.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));
            check.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,15.36f*ui.unit);check.setChecked(selected.contains(id));check.setPadding(p(6),p(8),p(6),p(8));check.setOnCheckedChangeListener((v,on)->{if(on)selected.add(id);else selected.remove(id);});sheet.body.addView(check,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout actions=ui.row();Button edit=ui.button("编辑",()->edit(item)),delete=ui.button("删除",()->{remove=id;render();});actions.addView(edit,new LinearLayout.LayoutParams(0,p(42),1));actions.addView(delete,new LinearLayout.LayoutParams(0,p(42),1));sheet.body.addView(actions);
            if(id.equals(editing))first=edit;
            if(id.equals(remove)){sheet.text("删除这道题目？");sheet.option("删除题目",()->authorize.run(()->delete(item)));first=sheet.option("保留题目",()->{remove="";render();});}
        }
        return first;
    }
    private void edit(JSONObject item){try{editing=item==null?"":item.getString("id");revision=item==null?0:item.getLong("revision");prompt=item==null?"":item.getString("prompt");choices=item==null?new String[]{"","","",""}:ChildQuizState.strings(item.getJSONArray("choices"),new String[0]);answer=item==null?0:item.getInt("answer");editor=true;error="";render();}catch(JSONException e){error="题目无法读取";render();}}
    private InputMethodManager keyboard(){return (InputMethodManager)ui.getSystemService(Context.INPUT_METHOD_SERVICE);}
    private void editView(Button cancel,Button save){
        sheet.text("题目");question=ui.input(prompt,"填写题目");question.setSingleLine(false);question.setMinLines(2);question.setMaxLines(4);question.setFilters(new InputFilter[]{new InputFilter.LengthFilter(160)});sheet.body.addView(question,new LinearLayout.LayoutParams(-1,-2));sheet.text("单选项  选中一项作为正确答案");options=new EditText[4];RadioButton[] radios=new RadioButton[4];
        question.setId(ViewCompat.generateViewId());
        cancel.setId(ViewCompat.generateViewId());
        save.setId(ViewCompat.generateViewId());
        for(int i=0;i<4;i++){
            final int index=i;
            LinearLayout row=ui.row();
            RadioButton radio=new RadioButton(ui);
            radio.setId(ViewCompat.generateViewId());
            radio.setText(""+"ABCD".charAt(i));
            radio.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,12,UiPalette.CONTROL));
            radio.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));
            radio.setChecked(answer==i);
            radio.setOnClickListener(v->{
                if(answer==index)return;
                answer=index;
                for(int n=0;n<4;n++)radios[n].setChecked(n==index);
                KeyFeedback.confirm(ui);
            });
            radios[i]=radio;
            row.addView(radio,new LinearLayout.LayoutParams(p(80),p(48)));
            EditText value=ui.input(choices[i],"选项 "+"ABCD".charAt(i));
            value.setId(ViewCompat.generateViewId());
            value.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64)});
            value.setContentDescription("选项 "+"ABCD".charAt(i));options[i]=value;
            row.addView(value,new LinearLayout.LayoutParams(0,p(48),1));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=p(9);
            sheet.body.addView(row,lp);
        }
        closeButton.setNextFocusDownId(question.getId());
        fieldKeys(question,closeButton,options[0],null);
        for(int i=0;i<options.length;i++){
            View above=i==0?question:options[i-1], below=i+1==options.length?save:options[i+1];
            fieldKeys(options[i],above,below,radios[i]);
            radios[i].setNextFocusRightId(options[i].getId());
            radios[i].setNextFocusUpId((i==0?question:radios[i-1]).getId());
            radios[i].setNextFocusDownId((i+1==radios.length?cancel:radios[i+1]).getId());
        }
        cancel.setNextFocusUpId(radios[radios.length-1].getId());
        save.setNextFocusUpId(options[options.length-1].getId());
    }
    private void fieldKeys(EditText field,View above,View below,RadioButton answerButton){
        field.setImeOptions(field.getImeOptions()|EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setOnKeyListener((view,key,event)->{
            if(key==KeyEvent.KEYCODE_DPAD_CENTER){
                if(event.getAction()==KeyEvent.ACTION_DOWN)keyboard().showSoftInput(field,InputMethodManager.SHOW_IMPLICIT);
                return true;
            }
            View next=key==KeyEvent.KEYCODE_DPAD_UP?above:key==KeyEvent.KEYCODE_DPAD_DOWN?below:
                    key==KeyEvent.KEYCODE_DPAD_LEFT&&field.getSelectionStart()==0&&field.getSelectionEnd()==0?answerButton:null;
            if(next==null)return false;
            if(event.getAction()==KeyEvent.ACTION_DOWN)next.requestFocus();
            return true;
        });
    }
    private void capture(){if(editor&&question!=null){prompt=question.getText().toString();for(int i=0;i<4;i++)choices[i]=options[i].getText().toString();}}
    private void saveQuestion(){capture();try{JSONObject edit=new JSONObject().put("id",editing).put("revision",revision).put("prompt",prompt).put("choices",new JSONArray(Arrays.asList(choices))).put("answer",answer);store.change(state->state.putQuestion(edit));back();notifyChanged();ui.success("题目已保存");}catch(IOException|JSONException e){error=e.getMessage();render();}}
    private void saveSelection(){try{JSONArray ids=new JSONArray(selected);store.change(state->{if(!baseline.equals(state.data.getJSONArray("selectedCustomIds").toString()))throw new IOException("所选题目已变化，请重新打开核对");state.selectQuestions(ids);});notifyChanged();close();ui.success("已使用所选题目");}catch(IOException e){error=e.getMessage();render();}}
    private void delete(JSONObject item){try{String id=item.getString("id");long revision=item.getLong("revision");store.change(state->state.deleteQuestion(id,revision));selected.remove(id);baseline=store.read().data.getJSONArray("selectedCustomIds").toString();remove="";error="";notifyChanged();render();ui.success("题目已删除");}catch(IOException|JSONException e){error=e.getMessage();render();}}
    private void notifyChanged(){ChildUsageService.changed();PhoneSettingsSession.get(ui).configurationChanged();}
    private void close(){if(sheet!=null)sheet.dialog.dismiss();}
    void pause(){capture();if(sheet!=null)sheet.dialog.hide();}
    boolean resume(){if(sheet==null)return false;sheet.dialog.show();return true;}
    void save(Bundle out){if(sheet==null)return;capture();Bundle value=new Bundle();value.putString("baseline",baseline);value.putStringArrayList("selected",new ArrayList<>(selected));value.putString("editing",editing);value.putLong("revision",revision);value.putString("prompt",prompt);value.putStringArray("choices",choices);value.putInt("answer",answer);value.putBoolean("editor",editor);out.putBundle("quiz-questions",value);}
    boolean restore(Bundle out)throws IOException{Bundle value=out.getBundle("quiz-questions");if(value==null)return false;store=ChildQuizStore.get(ui);baseline=value.getString("baseline","[]");selected.clear();selected.addAll(value.getStringArrayList("selected"));editing=value.getString("editing","");revision=value.getLong("revision");prompt=value.getString("prompt","");choices=value.getStringArray("choices");answer=value.getInt("answer");editor=value.getBoolean("editor");create();return true;}
    void destroy(){if(sheet!=null){sheet.dialog.setOnDismissListener(null);sheet.dialog.dismiss();sheet=null;}}
}
