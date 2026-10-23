package home.quiet.tv;

import android.app.Dialog;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;

/** Password work runs off the UI thread; cancelling cannot install a new credential. */
final class ChildModeAuth {
    private static final java.util.WeakHashMap<ManagementUi,Dialog> OPEN=new java.util.WeakHashMap<>();
    static void dismiss(ManagementUi ui){Dialog dialog=OPEN.remove(ui);if(dialog!=null)dialog.dismiss();}
    static void change(ManagementUi ui,Runnable done){show(ui,done,true);}
    static Dialog show(ManagementUi ui,Runnable authorized){return show(ui,authorized,false);}
    private static Dialog show(ManagementUi ui,Runnable authorized,boolean reset){
        ChildModeStore store=new ChildModeStore(ui);boolean setup=reset || !store.configured();ChildModeUi style=new ChildModeUi(ui);
        ChildModeUi.Sheet sheet=style.new Sheet(setup?"设置家长密码":"家长验证",setup?"所有应用共用一组 6 位数字密码":"请输入 6 位家长密码",R.drawable.ic_child_lock,633.6f);
        EditText pin=field(style,!setup,"输入 6 位数字");style.gap(sheet.body,23.04f);sheet.body.addView(pin,new LinearLayout.LayoutParams(-1,style.p(64.16f)));
        Button[] keys=keypad(style,sheet,pin);TextView error=error(style,sheet,pin);
        ExecutorService worker=Executors.newSingleThreadExecutor();
        Button cancel=sheet.action("取消",sheet.dialog::dismiss,false);
        Button confirm=sheet.action(setup?"设置密码":"确认",()->{
            String value=pin.getText().toString();
            if(!ChildModeCredentials.numeric(value)){error.setText("请输入 6 位数字");return;}
            if(!setup && store.retryAfter()>SystemClock.elapsedRealtime()){error.setText("输入次数过多，请稍后重试");return;}
            for(int i=0;i<sheet.actions.getChildCount();i++)sheet.actions.getChildAt(i).setEnabled(false);pin.setEnabled(false);for(Button key:keys)key.setEnabled(false);
            error.setText(setup?"正在保存…":"正在验证…");
            worker.execute(()->{String prepared=null,problem=null;boolean valid=false;
                try{if(setup){prepared=ChildModeCredentials.prepare(value);valid=true;}else valid=ChildModeCredentials.verify(value,store.credential());}
                catch(RuntimeException e){problem=e.getMessage();}
                final String result=prepared,failure=problem;final boolean matched=valid;
                ui.runOnUiThread(()->{
                    if(!sheet.dialog.isShowing() || ui.isFinishing() || ui.isDestroyed() || !ui.getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))return;
                    for(int i=0;i<sheet.actions.getChildCount();i++)sheet.actions.getChildAt(i).setEnabled(true);pin.setEnabled(true);for(Button key:keys)key.setEnabled(true);
                    if(failure!=null){error.setText(failure);return;}
                    if(!matched){store.failed();pin.setText("");error.setText("密码不正确，请重试");pin.requestFocus();return;}
                    if(setup && !store.saveCredential(result,value)){error.setText("保存失败，请重试");return;}
                    store.verified();sheet.dialog.dismiss();authorized.run();
                });
            });
        },true);
        arrows(style,confirm);
        if(!setup){style.gap(sheet.box,24.96f);Button forgot=style.button("忘记密码",()->{sheet.dialog.dismiss();recover(ui,authorized);},false);forgot.setTextColor(0xff8c9aaa);forgot.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,style.font(16.32f));forgot.setBackground(style.focus(0,0,6,false));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-2,style.p(27));fp.gravity=Gravity.CENTER_HORIZONTAL;sheet.box.addView(forgot,fp);}
        navigate(pin,keys,cancel,confirm);
        sheet.dialog.setOnDismissListener(d->{pin.setText("");worker.shutdownNow();sheet.release();if(OPEN.get(ui)==sheet.dialog)OPEN.remove(ui);});OPEN.put(ui,sheet.dialog);sheet.show(pin);confirm.setContentDescription(setup?"设置家长密码":"确认家长密码");return sheet.dialog;
    }
    private static void recover(ManagementUi ui,Runnable authorized){
        ChildModeStore store=new ChildModeStore(ui);ChildModeUi style=new ChildModeUi(ui);ChildModeUi.Sheet sheet=style.new Sheet("使用恢复码","输入本机的 6 位数字恢复码",R.drawable.ic_child_lock,633.6f);
        EditText input=field(style,false,"输入 6 位数字");style.gap(sheet.body,23.04f);sheet.body.addView(input,new LinearLayout.LayoutParams(-1,style.p(64.16f)));Button[] keys=keypad(style,sheet,input);TextView error=error(style,sheet,input);
        Button cancel=sheet.action("取消",sheet.dialog::dismiss,false),confirm=sheet.action("确认",()->{if(!store.verifyRecovery(input.getText().toString())){error.setText("恢复码不正确");return;}sheet.dialog.dismiss();show(ui,authorized,true);},true);arrows(style,confirm);navigate(input,keys,cancel,confirm);
        style.gap(sheet.box,24.96f);TextView help=style.text("可在关于页面连续点击三次“恢复码”查看",14.98f,26,0xff7e8a9a);help.setGravity(Gravity.CENTER);sheet.box.addView(help);
        OPEN.put(ui,sheet.dialog);sheet.dialog.setOnDismissListener(d->{input.setText("");sheet.release();if(OPEN.get(ui)==sheet.dialog)OPEN.remove(ui);});sheet.show(input);
    }
    private static EditText field(ChildModeUi style,boolean masked,String hint){
        EditText view=style.ui.input("",hint);view.setId(View.generateViewId());view.setContentDescription(masked?"家长密码":"6 位数字");view.setInputType(InputType.TYPE_CLASS_NUMBER|(masked?InputType.TYPE_NUMBER_VARIATION_PASSWORD:0));view.setTypeface(android.graphics.Typeface.DEFAULT);view.setFilters(new InputFilter[]{DigitsKeyListener.getInstance("0123456789"),new InputFilter.LengthFilter(6)});view.setGravity(Gravity.CENTER);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,style.font(23.04f));view.setTextColor(0xffe9edf2);view.setHintTextColor(0xff667385);UiCompat.letterSpacing(view,0);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,style.font(19.2f));view.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(android.text.Editable e){UiCompat.letterSpacing(view,e.length()==0?0:.2f);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,style.font(e.length()==0?19.2f:23.04f));}});view.setPadding(style.p(17.28f),0,style.p(17.28f),0);view.setBackground(style.focus(0x80080e15,0x25ffffff,11.52f,false));
        if(android.os.Build.VERSION.SDK_INT>=21)view.setShowSoftInputOnFocus(false);return view;
    }
    private static TextView error(ChildModeUi style,ChildModeUi.Sheet sheet,EditText input){style.gap(sheet.body,13.44f);TextView text=style.text("",16.32f,25,0xffe3aba1);text.setGravity(Gravity.CENTER);text.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);sheet.body.addView(text);input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(android.text.Editable e){text.setText("");}});return text;}
    private static void arrows(ChildModeUi style,Button button){android.graphics.drawable.Drawable arrow=UiCompat.drawable(style.ui,R.drawable.ic_child_arrow);arrow.setColorFilter(0xff191d23,android.graphics.PorterDuff.Mode.SRC_IN);arrow.setBounds(0,0,style.p(26.88f),style.p(26.88f));button.setCompoundDrawables(null,null,arrow,null);button.setCompoundDrawablePadding(style.p(12.48f));button.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int side=Math.max(0,Math.round((r-l-button.getPaint().measureText(button.getText().toString())-style.p(26.88f)-style.p(12.48f))/2));if(button.getPaddingLeft()!=side)button.setPadding(side,0,side,0);});}
    private static Button[] keypad(ChildModeUi style,ChildModeUi.Sheet sheet,EditText input){
        Button[] keys=new Button[12];style.gap(sheet.body,19.2f);
        for(int row=0;row<4;row++){if(row>0)style.gap(sheet.body,9.6f);LinearLayout line=style.ui.row();line.setClipChildren(false);sheet.body.addView(line,new LinearLayout.LayoutParams(-1,style.p(48)));
            for(int column=0;column<3;column++){int index=row*3+column;String value=index<9?String.valueOf(index+1):index==9?"清空":index==10?"0":"退格";
                Button key=style.button(value,()->{String text=input.getText().toString();input.setText(value.equals("清空")?"":value.equals("退格")?text.isEmpty()?"":text.substring(0,text.length()-1):(text+value).substring(0,Math.min(6,text.length()+1)));input.setSelection(input.length());},false);key.setId(View.generateViewId());key.setContentDescription(value);key.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,style.font(index==9?15.36f:23.04f));key.setPadding(0,0,0,0);key.setBackground(style.focus(0x05ffffff,0x0effffff,9.6f,false));
                if(index==11){key.setText("");android.graphics.drawable.Drawable back=UiCompat.drawable(style.ui,R.drawable.ic_child_backspace);back.setBounds(0,0,style.p(24.96f),style.p(24.96f));key.setCompoundDrawables(back,null,null,null);key.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->key.setPadding((r-l-style.p(24.96f))/2,0,0,0));}
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);if(column>0)lp.leftMargin=style.p(9.6f);line.addView(key,lp);keys[index]=key;
            }
        }
        sheet.dialog.setOnKeyListener((dialog,key,event)->{int digit=key>=KeyEvent.KEYCODE_0 && key<=KeyEvent.KEYCODE_9?key-KeyEvent.KEYCODE_0:key>=KeyEvent.KEYCODE_NUMPAD_0 && key<=KeyEvent.KEYCODE_NUMPAD_9?key-KeyEvent.KEYCODE_NUMPAD_0:-1;if(digit<0)return false;if(event.getAction()==KeyEvent.ACTION_UP && input.length()<6)input.append(String.valueOf(digit));return true;});return keys;
    }
    private static void navigate(EditText input,Button[] keys,Button cancel,Button confirm){cancel.setId(View.generateViewId());confirm.setId(View.generateViewId());input.setNextFocusDownId(keys[1].getId());input.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(event.getAction()==KeyEvent.ACTION_DOWN)keys[1].requestFocus();return true;}return false;});for(int i=0;i<keys.length;i++){keys[i].setNextFocusUpId(i<3?input.getId():keys[i-3].getId());keys[i].setNextFocusDownId(i<9?keys[i+3].getId():i==9?cancel.getId():confirm.getId());}cancel.setNextFocusUpId(keys[9].getId());confirm.setNextFocusUpId(keys[11].getId());}
}
