package home.quiet.tv;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.text.*;
import android.util.TypedValue;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** List, editor and a single-note deletion confirmation share one settings window. */
final class MemoPanel {
    private final ManagementUi ui;
    private final MemoStore store;
    private final Runnable changed;
    private final Runnable closed;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<View> controls=new ArrayList<>();
    private ManagementUi.Sheet sheet;
    private Button close,save;
    private TextView error,count;
    private EditText edit;
    private List<MemoStore.Note> notes=Collections.emptyList();
    private boolean editing,existing,confirm,busy,destroyed,readable;
    private String editId,draft="",original="",listFocus="memo-add";
    private int listScroll;private long phoneEpoch=-1;
    private final Runnable phoneChanged=this::phoneChanged;
    private void phoneChanged(){long epoch=MemoStore.changeEpoch();if(epoch==phoneEpoch||destroyed)return;phoneEpoch=epoch;if(sheet==null)refreshCount();else if(!editing&&!busy)load();}
    MemoPanel(ManagementUi ui,Runnable changed){this(ui,changed,()->{View origin=ui.root.findViewWithTag("quick-memos");if(origin!=null)origin.requestFocusFromTouch();});}
    MemoPanel(ManagementUi ui,Runnable changed,Runnable closed){this.ui=ui;this.changed=changed;this.closed=closed;store=new MemoStore(new File(ui.getFilesDir(),"memos"));PhoneSettingsSession.get(ui).observeConfiguration(phoneChanged);}
    private String tr(String zh,String en){return ui.tr(zh,en);}
    private int px(float value){return ui.px(value);}
    private void identify(View view,String tag){view.setTag(tag);view.setId(androidx.core.view.ViewCompat.generateViewId());controls.add(view);}
    void show(){
        if(sheet!=null)return;sheet=ui.new Sheet(tr("备忘录","My notes"));close=sheet.settingsLayout(tr("关闭","Close"),this::back);sheet.contentSize(320,360);sheet.actions.setVisibility(View.GONE);
        error=ui.muted("",12);error.setTextColor(UiPalette.TEXT);error.setTextSize(TypedValue.COMPLEX_UNIT_PX,11.52f*ui.unit);error.setPadding(px(13.44f),px(9.6f),px(13.44f),px(9.6f));error.setMinimumHeight(px(40.608f));error.setBackgroundDrawable(ui.surface(0x1affffff,0x1affffff,UiPalette.EDGE_STRONG,6.72f));error.setVisibility(View.GONE);error.setMaxLines(2);androidx.core.view.ViewCompat.setAccessibilityLiveRegion(error,androidx.core.view.ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);LinearLayout.LayoutParams errorParams=new LinearLayout.LayoutParams(-1,-2);errorParams.topMargin=px(11.52f);errorParams.bottomMargin=px(11.52f);sheet.box.addView(error,sheet.box.indexOfChild(sheet.actions),errorParams);
        sheet.dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        sheet.dialog.setOnKeyListener((d,key,event)->{if(key!=KeyEvent.KEYCODE_BACK&&key!=KeyEvent.KEYCODE_ESCAPE)return false;if(event.getAction()==KeyEvent.ACTION_UP&&!busy){back();KeyFeedback.back(ui);}return true;});
        sheet.dialog.setOnDismissListener(d->{hideKeyboard();sheet=null;edit=null;closed.run();});
        sheet.show(close);load();
    }
    private void load(){
        sheet.body.removeAllViews();sheet.actions.removeAllViews();sheet.settingsActions(false);controls.clear();sheet.body.addView(ui.muted(tr("正在读取记事…","Loading notes…"),13));enable(close,true);
        worker.execute(()->{List<MemoStore.Note> loaded=null;try{loaded=store.list();}catch(IOException failure){/* Preserve the original file and offer an explicit retry. */}List<MemoStore.Note> result=loaded;main.post(()->{
            if(destroyed||sheet==null)return;
            if(result==null){readable=false;changed.run();sheet.body.removeAllViews();Button retry=button(tr("重新读取","Retry"),"memo-reload",this::load,false);sheet.body.addView(retry,new LinearLayout.LayoutParams(-2,px(34.56f)));failure(tr("记事无法读取，原文件已保留","Cannot read notes. The original file is retained."));retry.requestFocusFromTouch();return;}
            notes=result;readable=true;changed.run();if(editing&&(!existing||find(editId)!=null))renderEditor("memo-text");else{editing=false;renderList(listFocus);}
        });});
    }
    private void reset(boolean actions){String label=editing?tr("返回","Back"):tr("关闭","Close");close.setText(label);close.setContentDescription(label);close.setVisibility(confirm?View.GONE:View.VISIBLE);controls.clear();edit=null;save=null;sheet.body.removeAllViews();sheet.actions.removeAllViews();sheet.settingsActions(actions);error.setVisibility(View.GONE);sheet.box.setEnabled(true);sheet.dialog.setCancelable(true);enable(close,true);}
    private Button button(String text,String tag,Runnable action,boolean primary){
        Button button=ui.button(text,action);identify(button,tag);button.setTextSize(TypedValue.COMPLEX_UNIT_PX,11.712f*ui.unit);button.setPadding(px(13.71f),0,px(13.71f),0);button.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,999,primary?UiPalette.SELECTED:UiPalette.CONTROL,false,primary?0x4dffffff:UiPalette.EDGE));button.setTextColor(new android.content.res.ColorStateList(new int[][]{{-android.R.attr.state_enabled},{android.R.attr.state_focused,android.R.attr.state_window_focused},{}},new int[]{UiPalette.FAINT,UiPalette.INK,UiPalette.TEXT}));return button;
    }
    private void renderList(String focus){
        editing=false;confirm=false;reset(false);sheet.heading.setText(tr("备忘录","My notes"));
        LinearLayout heading=ui.row();TextView hint=ui.muted(tr("大字记事，完成后直接勾选。","Large notes. Check them when done."),11);hint.setTextSize(TypedValue.COMPLEX_UNIT_PX,10.848f*ui.unit);heading.addView(hint,new LinearLayout.LayoutParams(0,-2,1));Button add=button(tr("添加记事","Add note"),"memo-add",()->openEditor(null),true);heading.addView(add,new LinearLayout.LayoutParams(-2,px(34.56f)));sheet.body.addView(heading,new LinearLayout.LayoutParams(-1,px(34.56f)));ui.gap(sheet.body,9);
        if(notes.isEmpty()){
            TextView empty=ui.center(tr("暂无记事，添加一条需要记住的事。","No notes yet. Add something to remember."),12);empty.setTextSize(TypedValue.COMPLEX_UNIT_PX,11.52f*ui.unit);empty.setTextColor(UiPalette.FAINT);empty.setPadding(px(19.2f),px(19.2f),px(19.2f),px(19.2f));empty.setMinimumHeight(px(59.82f));android.graphics.drawable.GradientDrawable outline=new SurfaceDrawable(ui.unit);outline.setColor(Color.TRANSPARENT);outline.setCornerRadius(px(9.6f));outline.setStroke(px(.75f),0x20ffffff,px(3),px(3));empty.setBackgroundDrawable(outline);LinearLayout.LayoutParams emptyParams=new LinearLayout.LayoutParams(-1,-2);emptyParams.topMargin=px(11.52f);emptyParams.bottomMargin=px(11.52f);sheet.body.addView(empty,emptyParams);
        }
        ArrayList<Button> toggles=new ArrayList<>(),editors=new ArrayList<>();
        for(MemoStore.Note note:notes){
            if(!toggles.isEmpty())ui.gap(sheet.body,9.6f);
            LinearLayout row=ui.row();row.setPadding(px(12.435f),px(12.435f),px(12.435f),px(12.435f));row.setMinimumHeight(px(59.42f));row.setBackgroundDrawable(ui.surface(UiPalette.CONTROL,UiPalette.CONTROL,UiPalette.EDGE,12.48f));row.setClipChildren(false);row.setClipToPadding(false);
            Button toggle=button(note.text,"memo-toggle-"+note.id,()->{rememberList("memo-toggle-"+note.id);submit(()->store.toggle(note.id,note),()->renderList(listFocus));},false);toggle.setTextSize(TypedValue.COMPLEX_UNIT_PX,16.32f*ui.unit);toggle.setSingleLine(false);toggle.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);toggle.setPadding(px(5.4f),0,px(5.4f),0);toggle.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,12,Color.TRANSPARENT));toggle.setTextColor(SettingsFocusDrawable.textColors(note.done?UiPalette.FAINT:UiPalette.TEXT));if(note.done)toggle.setPaintFlags(toggle.getPaintFlags()|Paint.STRIKE_THRU_TEXT_FLAG);toggle.setContentDescription((note.done?tr("标为未完成","Mark unfinished "):tr("完成","Complete "))+note.text);
            Paint.FontMetrics fm=toggle.getPaint().getFontMetrics();toggle.setLineSpacing(26.112f*ui.unit-(fm.descent-fm.ascent),1);Drawable check=androidx.core.graphics.drawable.DrawableCompat.wrap(new QuickControlIcon(note.done?"square-check":"square")).mutate();androidx.core.graphics.drawable.DrawableCompat.setTintList(check,toggle.getTextColors());check.setBounds(0,0,px(12),px(12));toggle.setCompoundDrawables(check,null,null,null);toggle.setCompoundDrawablePadding(px(9.6f));row.addView(toggle,new LinearLayout.LayoutParams(0,-2,1));
            Button editor=button(tr("编辑","Edit"),"memo-edit-"+note.id,()->{rememberList("memo-edit-"+note.id);openEditor(note);},false);editor.setContentDescription(tr("编辑","Edit ")+note.text);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-2,px(34.56f));ep.leftMargin=px(9.6f);row.addView(editor,ep);sheet.body.addView(row,new LinearLayout.LayoutParams(-1,-2));toggles.add(toggle);editors.add(editor);
        }
        close.setNextFocusDownId(add.getId());add.setNextFocusUpId(close.getId());add.setNextFocusDownId(toggles.isEmpty()?add.getId():toggles.get(0).getId());
        for(int i=0;i<toggles.size();i++){Button toggle=toggles.get(i),editor=editors.get(i);toggle.setNextFocusRightId(editor.getId());editor.setNextFocusLeftId(toggle.getId());toggle.setNextFocusLeftId(toggle.getId());editor.setNextFocusRightId(editor.getId());toggle.setNextFocusUpId(i==0?add.getId():toggles.get(i-1).getId());editor.setNextFocusUpId(i==0?add.getId():editors.get(i-1).getId());toggle.setNextFocusDownId(toggles.get(Math.min(i+1,toggles.size()-1)).getId());editor.setNextFocusDownId(editors.get(Math.min(i+1,editors.size()-1)).getId());}
        focus(focus,"memo-add",true);
    }
    private void rememberList(String focus){listFocus=focus;listScroll=sheet.scroll.getScrollY();}
    private MemoStore.Note find(String id){for(MemoStore.Note note:notes)if(note.id.equals(id))return note;return null;}
    int unfinished(){if(!readable)return -1;int count=0;for(MemoStore.Note note:notes)if(!note.done)count++;return count;}
    void refreshCount(){if(sheet!=null)return;worker.execute(()->{List<MemoStore.Note> loaded=null;try{loaded=store.list();}catch(IOException error){/* An unreadable store is not an empty list. */}List<MemoStore.Note> result=loaded;main.post(()->{if(destroyed||sheet!=null)return;readable=result!=null;if(readable)notes=result;changed.run();});});}
    private void openEditor(MemoStore.Note note){if(note==null)rememberList("memo-add");editing=true;existing=note!=null;editId=note==null?UUID.randomUUID().toString():note.id;original=draft=note==null?"":note.text;confirm=false;renderEditor("memo-text");}
    private void captureDraft(){if(edit!=null)draft=edit.getText().toString();}
    private void renderEditor(String focus){
        reset(true);
        if(confirm){
            sheet.heading.setText(tr("删除记事？","Delete note?"));
            sheet.text(draft);
            Button cancel=action(tr("取消","Cancel"),"memo-cancel-delete",this::cancelDelete,false);
            save=action(tr("确认删除","Confirm delete"),"memo-save",this::save,true);
            cancel.setNextFocusRightId(save.getId());save.setNextFocusLeftId(cancel.getId());
            focus("memo-cancel-delete","memo-cancel-delete",false);
            return;
        }
        sheet.heading.setText(existing?tr("编辑记事","Edit note"):tr("添加记事","Add note"));
        LinearLayout field=ui.column();LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.topMargin=px(10.56f);fp.bottomMargin=px(14.4f);sheet.body.addView(field,fp);TextView label=ui.label(tr("记事内容","Note"),13);label.setTextColor(UiPalette.SECONDARY);label.setTextSize(TypedValue.COMPLEX_UNIT_PX,12.48f*ui.unit);field.addView(label,new LinearLayout.LayoutParams(-1,px(17.45f)));ui.gap(field,7.68f);
        edit=ui.input(draft,tr("写一件需要记住的事","Write something to remember"));identify(edit,"memo-text");edit.setContentDescription(tr("记事内容","Note"));edit.setSingleLine(false);edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);edit.setGravity(Gravity.TOP|Gravity.START);edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MemoStore.MAX_TEXT)});edit.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,12,0x1f000000,false,UiPalette.EDGE));edit.setImeOptions(EditorInfo.IME_ACTION_DONE|EditorInfo.IME_FLAG_NO_EXTRACT_UI);field.addView(edit,new LinearLayout.LayoutParams(-1,px(86.4f)));
        count=ui.label(edit.length()+" / 60",10);count.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);count.setTextSize(TypedValue.COMPLEX_UNIT_PX,9.6f*ui.unit);count.setTextColor(UiPalette.SECONDARY);sheet.body.addView(count,new LinearLayout.LayoutParams(-1,px(13.32f)));
        Button remove=null;if(existing){remove=action(tr("删除","Delete"),"memo-delete",()->{captureDraft();confirm=true;hideKeyboard();renderEditor("memo-cancel-delete");},false);}
        Button cancel=action(tr("取消","Cancel"),"memo-cancel",this::cancelEdit,false);save=action(tr("保存记事","Save note"),"memo-save",this::save,true);enable(save,!draft.trim().isEmpty());
        Button first=remove==null?cancel:remove;close.setNextFocusDownId(edit.getId());edit.setNextFocusUpId(close.getId());edit.setNextFocusDownId(first.getId());if(remove!=null){remove.setNextFocusLeftId(remove.getId());remove.setNextFocusRightId(cancel.getId());remove.setNextFocusUpId(edit.getId());remove.setNextFocusDownId(remove.getId());}cancel.setNextFocusLeftId(first.getId());cancel.setNextFocusRightId(save.getId());cancel.setNextFocusUpId(edit.getId());cancel.setNextFocusDownId(cancel.getId());save.setNextFocusLeftId(cancel.getId());save.setNextFocusRightId(save.getId());save.setNextFocusUpId(edit.getId());save.setNextFocusDownId(save.getId());
        edit.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int after){draft=s.toString();count.setText(s.length()+" / 60");enable(save,!busy&&(confirm||!draft.trim().isEmpty()));}public void afterTextChanged(Editable text){}});
        edit.setOnKeyListener((v,key,event)->{if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_DOWN&&edit.getSelectionEnd()==edit.length()){first.requestFocusFromTouch();return true;}if(key==KeyEvent.KEYCODE_DPAD_UP&&edit.getSelectionStart()==0){close.requestFocusFromTouch();return true;}return false;});
        edit.setOnEditorActionListener((v,id,event)->{if(id!=EditorInfo.IME_ACTION_DONE)return false;hideKeyboard();save.requestFocusFromTouch();return true;});focus(focus,"memo-text",false);
    }
    private Button action(String text,String tag,Runnable action,boolean primary){Button result=button(text,tag,action,primary);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-1);if(sheet.actions.getChildCount()>0)p.leftMargin=px(6);sheet.actions.addView(result,p);return result;}
    private void save(){
        captureDraft();hideKeyboard();String id=editId,text=draft;
        if(confirm){int index=0;while(index<notes.size()&&!notes.get(index).id.equals(id))index++;final int next=index;submit(tr("记事已删除","Note deleted"),()->store.remove(id,find(id)),()->{String target=notes.isEmpty()?"memo-add":"memo-edit-"+notes.get(Math.min(next,notes.size()-1)).id;renderList(target);});}
        else submit(tr("记事已保存","Note saved"),()->store.save(id,text,existing?find(id):null),()->renderList("memo-edit-"+id));
    }
    private interface Operation {List<MemoStore.Note> run()throws IOException;}
    private void submit(Operation operation,Runnable applied){
        submit("",operation,applied);
    }
    private void submit(String resultText,Operation operation,Runnable applied){
        if(busy)return;busy=true;error.setVisibility(View.GONE);sheet.box.setEnabled(false);sheet.dialog.setCancelable(false);enable(close,false);for(View view:controls)enable(view,false);
        if(!resultText.isEmpty())ui.progress(tr("正在保存…","Saving…"));
        worker.execute(()->{List<MemoStore.Note> result=null;String conflict="";try{result=operation.run();}catch(IOException failure){if(failure instanceof MemoStore.Changed)conflict=failure.getMessage();}List<MemoStore.Note> committed=result;String message=conflict;main.post(()->{
            busy=false;if(destroyed||sheet==null)return;sheet.box.setEnabled(true);sheet.dialog.setCancelable(true);enable(close,true);for(View view:controls)enable(view,true);
            if(committed==null){if(save!=null)enable(save,confirm||!draft.trim().isEmpty());ui.error(message.isEmpty()?tr("保存失败，记事与草稿已保留，请重试","Save failed. Notes and draft are retained. Retry."):message);if(save!=null)save.requestFocusFromTouch();else focus(listFocus,"memo-add",true);return;}
            notes=committed;changed.run();applied.run();if(!resultText.isEmpty())ui.success(resultText);
        });});
    }
    private void enable(View view,boolean enabled){view.setEnabled(enabled);if(view instanceof Button)view.setAlpha(1f);}
    private void failure(String message){error.setText(message);OperationFeedback.status(error,OperationFeedback.Kind.ERROR);error.setVisibility(View.VISIBLE);sheet.settingsActions(sheet.actions.getChildCount()>0);sheet.bodyMaximum-=82;}
    private void focus(String tag,String fallback,boolean restoreScroll){sheet.box.post(()->{if(sheet==null)return;if(restoreScroll)sheet.scroll.scrollTo(0,listScroll);else sheet.scroll.scrollTo(0,0);View target=sheet.box.findViewWithTag(tag);if(target==null||!target.isEnabled())target=sheet.box.findViewWithTag(fallback);if(target!=null)target.requestFocusFromTouch();});}
    private void cancelEdit(){hideKeyboard();editing=false;load();}
    private void cancelDelete(){confirm=false;renderEditor("memo-delete");}
    private void back(){if(busy)return;if(confirm){cancelDelete();return;}if(editing&&readable){captureDraft();if(!draft.equals(original)){ManagementUi.Sheet confirm=ui.new Sheet(tr("放弃未保存的修改？","Discard unsaved changes?"));confirm.text(tr("本次输入尚未保存。","Your edits have not been saved."));Button keep=confirm.action(tr("取消","Cancel"),confirm.dialog::dismiss);confirm.action(tr("放弃修改","Discard"),()->{confirm.dialog.dismiss();cancelEdit();});confirm.show(keep);}else cancelEdit();}else if(sheet!=null)sheet.dialog.dismiss();}
    private void hideKeyboard(){if(edit!=null)((InputMethodManager)ui.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(edit.getWindowToken(),0);}
    void saveState(Bundle state){if(sheet==null)return;captureDraft();state.putBoolean("memos-open",true);state.putBoolean("memos-editing",editing);state.putBoolean("memos-existing",existing);state.putBoolean("memos-confirm",confirm);state.putString("memos-id",editId);state.putString("memos-draft",draft);state.putString("memos-original",original);state.putString("memos-focus",listFocus);state.putInt("memos-scroll",editing?listScroll:sheet.scroll.getScrollY());}
    void restoreState(Bundle state){if(state==null||!state.getBoolean("memos-open"))return;editing=state.getBoolean("memos-editing");existing=state.getBoolean("memos-existing");confirm=state.getBoolean("memos-confirm");editId=state.getString("memos-id");draft=state.getString("memos-draft","");original=state.getString("memos-original","");listFocus=state.getString("memos-focus","memo-add");listScroll=state.getInt("memos-scroll");show();}
    void destroy(){destroyed=true;PhoneSettingsSession.get(ui).forgetConfiguration(phoneChanged);if(sheet!=null)sheet.dialog.dismiss();worker.shutdown();}
}
