package home.quiet.tv;

import android.app.Dialog;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
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
        ChildModeStore store=new ChildModeStore(ui);boolean setup=reset || !store.configured();
        ManagementUi.Sheet sheet=ui.new Sheet(setup?"设置家长密码":"家长验证");
        EditText pin=field(ui,setup?"输入 6 位数字密码":"输入家长密码");sheet.body.addView(pin,new LinearLayout.LayoutParams(-1,ui.px(40)));
        EditText repeat=field(ui,"再次输入密码");if(setup){ui.gap(sheet.body,8);sheet.body.addView(repeat,new LinearLayout.LayoutParams(-1,ui.px(40)));}
        TextView error=ui.muted("",11);sheet.body.addView(error,new LinearLayout.LayoutParams(-1,ui.px(26)));
        keypad(ui,sheet,pin,repeat,setup);
        ExecutorService worker=Executors.newSingleThreadExecutor();
        if(!setup)sheet.action("忘记密码",()->{sheet.dialog.dismiss();recover(ui,authorized);});
        sheet.action("取消",sheet.dialog::dismiss);
        Button confirm=sheet.action(setup?"设置密码":"确认",()->{
            String value=pin.getText().toString();
            if(!ChildModeCredentials.numeric(value)){error.setText("请输入 6 位数字");return;}
            if(setup && !value.equals(repeat.getText().toString())){error.setText("两次输入的密码不一致");return;}
            if(!setup && store.retryAfter()>SystemClock.elapsedRealtime()){error.setText("输入次数过多，请稍后重试");return;}
            sheet.actions.setEnabled(false);for(int i=0;i<sheet.actions.getChildCount();i++)sheet.actions.getChildAt(i).setEnabled(false);
            error.setText("正在验证…");
            worker.execute(()->{String prepared=null,problem=null;boolean valid=false;
                try{if(setup){prepared=ChildModeCredentials.prepare(value);valid=true;}else valid=ChildModeCredentials.verify(value,store.credential());}
                catch(RuntimeException e){problem=e.getMessage();}
                final String result=prepared,failure=problem;final boolean matched=valid;
                ui.runOnUiThread(()->{
                    if(!sheet.dialog.isShowing() || ui.isFinishing() || ui.isDestroyed() || !ui.getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))return;
                    for(int i=0;i<sheet.actions.getChildCount();i++)sheet.actions.getChildAt(i).setEnabled(true);
                    if(failure!=null){error.setText(failure);return;}
                    if(!matched){store.failed();pin.setText("");error.setText("密码不正确，请重试");pin.requestFocus();return;}
                    if(setup && !store.saveCredential(result,value)){error.setText("保存失败，请重试");return;}
                    store.verified();sheet.dialog.dismiss();authorized.run();
                });
            });
        });
        sheet.dialog.setOnDismissListener(d->{worker.shutdownNow();if(OPEN.get(ui)==sheet.dialog)OPEN.remove(ui);});OPEN.put(ui,sheet.dialog);sheet.show(pin);confirm.setContentDescription(setup?"设置家长密码":"确认家长密码");return sheet.dialog;
    }
    private static void recover(ManagementUi ui,Runnable authorized){
        ChildModeStore store=new ChildModeStore(ui);ManagementUi.Sheet sheet=ui.new Sheet("使用恢复码");
        sheet.text("在关于页末行选中恢复码，连续按三次确定可查看");ui.gap(sheet.body,10);
        EditText input=field(ui,"输入 6 位数字恢复码");input.setInputType(InputType.TYPE_CLASS_NUMBER);sheet.body.addView(input,new LinearLayout.LayoutParams(-1,ui.px(40)));
        TextView error=ui.muted("",11);sheet.body.addView(error,new LinearLayout.LayoutParams(-1,ui.px(25)));keypad(ui,sheet,input,input,false);
        sheet.action("取消",sheet.dialog::dismiss);sheet.action("确认",()->{if(!store.verifyRecovery(input.getText().toString())){error.setText("恢复码不正确");return;}sheet.dialog.dismiss();show(ui,authorized,true);});OPEN.put(ui,sheet.dialog);sheet.dialog.setOnDismissListener(d->{if(OPEN.get(ui)==sheet.dialog)OPEN.remove(ui);});sheet.show(input);
    }
    private static EditText field(ManagementUi ui,String hint){
        EditText view=ui.input("",hint);view.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);view.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        if(android.os.Build.VERSION.SDK_INT>=21)view.setShowSoftInputOnFocus(false);return view;
    }
    private static void keypad(ManagementUi ui,ManagementUi.Sheet sheet,EditText first,EditText second,boolean setup){
        final EditText[] target={first};first.setOnFocusChangeListener((v,focused)->{if(focused)target[0]=first;});second.setOnFocusChangeListener((v,focused)->{if(focused)target[0]=second;});
        for(int row=0;row<4;row++){LinearLayout line=ui.row();sheet.body.addView(line,new LinearLayout.LayoutParams(-1,ui.px(32)));
            for(int column=0;column<3;column++){int position=row*3+column;String value=position<9?String.valueOf(position+1):position==9?"清空":position==10?"0":"退格";
                Button b=ui.button(value,()->{String text=target[0].getText().toString();target[0].setText(value.equals("清空")?"":value.equals("退格")?text.isEmpty()?"":text.substring(0,text.length()-1):(text+value).substring(0,Math.min(6,text.length()+1)));});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ui.px(29),1);p.setMargins(ui.px(2),0,ui.px(2),0);line.addView(b,p);
            }
        }
        sheet.dialog.setOnKeyListener((dialog,key,event)->{if(key<KeyEvent.KEYCODE_0 || key>KeyEvent.KEYCODE_9)return false;if(event.getAction()==KeyEvent.ACTION_UP && target[0].length()<6)target[0].append(String.valueOf(key-KeyEvent.KEYCODE_0));return true;});
    }
}
