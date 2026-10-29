package home.quiet.tv;

import android.os.SystemClock;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;

/** Page-local authentication. Closing the page invalidates every outstanding credential result. */
final class ChildModeUnlock {
    interface Active {boolean current();}
    final LinearLayout view;
    private final AppManagementActivity activity;
    private final ChildModeStore store;
    private final Active active;
    private final Runnable authorized;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final TextView title,error;
    private final EditText pin;
    private final Button confirm,recover;
    private final Button[] keys=new Button[12];
    private int mode,generation; // 0 verify, 1 initial setup, 2 recovery, 3 recovered setup.
    private boolean busy,closed;

    ChildModeUnlock(AppManagementActivity activity,Active active,Runnable authorized,Runnable cancel){
        this.activity=activity;this.active=active;this.authorized=authorized;store=new ChildModeStore(activity);
        view=activity.column();view.setPadding(p(22),p(12),p(22),p(12));view.setBackgroundDrawable(activity.surface(0xff20232a,0xff171a20,0x26ffffff,14));
        LinearLayout fields=view,pad=activity.column();
        title=activity.center("",20);fields.addView(title,new LinearLayout.LayoutParams(-1,p(26)));
        pin=activity.input("","输入 6 位数字");pin.setSaveEnabled(false);pin.setId(androidx.core.view.ViewCompat.generateViewId());pin.setTag("child-pin");pin.setGravity(Gravity.CENTER);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);pin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6),android.text.method.DigitsKeyListener.getInstance("0123456789")});
        if(android.os.Build.VERSION.SDK_INT>=21)pin.setShowSoftInputOnFocus(false);
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,p(34));pp.topMargin=p(8);fields.addView(pin,pp);
        error=activity.muted("",11);error.setGravity(Gravity.CENTER);error.setTextColor(0xffe3aba1);androidx.core.view.ViewCompat.setAccessibilityLiveRegion(error,androidx.core.view.ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);fields.addView(error,new LinearLayout.LayoutParams(-1,p(18)));
        pin.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(Editable value){if(!busy){error.setText("");if(mode==0&&value.length()==6)submit();}}});
        LinearLayout.LayoutParams padLayout=new LinearLayout.LayoutParams(-1,-2);padLayout.topMargin=p(4);fields.addView(pad,padLayout);
        for(int row=0;row<4;row++){
            LinearLayout line=activity.row();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,p(32));if(row>0)lp.topMargin=p(6);pad.addView(line,lp);
            for(int column=0;column<3;column++){
                final int index=row*3+column;String label=index<9?String.valueOf(index+1):index==9?"清空":index==10?"0":"退格";
                Button key=activity.button(label,()->enter(index));key.setId(androidx.core.view.ViewCompat.generateViewId());key.setTag("child-key-"+label);key.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,p(14));key.setPadding(0,0,0,0);
                LinearLayout.LayoutParams kp=new LinearLayout.LayoutParams(0,-1,1);if(column>0)kp.leftMargin=p(6);line.addView(key,kp);keys[index]=key;
                key.setOnKeyListener((v,code,event)->{
                    if(code<KeyEvent.KEYCODE_DPAD_UP||code>KeyEvent.KEYCODE_DPAD_RIGHT)return false;
                    if(event.getAction()==KeyEvent.ACTION_DOWN){
                        if(code==KeyEvent.KEYCODE_DPAD_UP){if(index<3)pin.requestFocus();else keys[index-3].requestFocus();}
                        else if(code==KeyEvent.KEYCODE_DPAD_DOWN){if(index<9)keys[index+3].requestFocus();else view.findViewWithTag(index==9?"child-auth-cancel":"child-auth-confirm").requestFocus();}
                        else keys[index/3*3+Math.max(0,Math.min(2,index%3+(code==KeyEvent.KEYCODE_DPAD_LEFT?-1:1)))].requestFocus();
                    }return true;
                });
            }
        }
        LinearLayout actions=activity.row();LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,p(34));ap.topMargin=p(8);fields.addView(actions,ap);
        Button back=activity.button("取消",cancel);back.setTag("child-auth-cancel");back.setId(androidx.core.view.ViewCompat.generateViewId());actions.addView(back,new LinearLayout.LayoutParams(0,-1,1));
        confirm=activity.button("",this::submit);confirm.setTag("child-auth-confirm");confirm.setId(androidx.core.view.ViewCompat.generateViewId());LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-1,1);cp.leftMargin=p(8);actions.addView(confirm,cp);
        back.setNextFocusUpId(keys[9].getId());confirm.setNextFocusUpId(keys[11].getId());
        recover=activity.button("忘记密码",()->{mode(2);focus();});recover.setId(androidx.core.view.ViewCompat.generateViewId());recover.setTag("child-auth-recover");recover.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,p(11));recover.setBackgroundDrawable(activity.focusSurface(6));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-2,p(27));rp.gravity=Gravity.CENTER_HORIZONTAL;rp.topMargin=p(4);fields.addView(recover,rp);
        // Explicit routes keep every action reachable without relying on spatial focus search.
        for(Button action:new Button[]{back,confirm,recover})action.setOnKeyListener((v,code,event)->{
            if(code<KeyEvent.KEYCODE_DPAD_UP||code>KeyEvent.KEYCODE_DPAD_RIGHT)return false;
            if(event.getAction()==KeyEvent.ACTION_DOWN){
                if(code==KeyEvent.KEYCODE_DPAD_UP){if(v==recover)confirm.requestFocus();else keys[v==back?9:11].requestFocus();}
                else if(code==KeyEvent.KEYCODE_DPAD_DOWN){if(recover.getVisibility()==View.VISIBLE)recover.requestFocus();}
                else if(v!=recover){if(code==KeyEvent.KEYCODE_DPAD_LEFT)back.requestFocus();else confirm.requestFocus();}
            }return true;
        });
        pin.setOnKeyListener((v,code,event)->{
            if(code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN){if(event.getAction()==KeyEvent.ACTION_DOWN){if(code==KeyEvent.KEYCODE_DPAD_UP)activity.managementTab().requestFocus();else keys[1].requestFocus();}return true;}
            return code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT;
        });
        mode(store.configured()?0:1);
    }
    private int p(float value){return activity.px(value);}
    private void mode(int next){
        generation++;mode=next;busy=false;enable(true);pin.setText("");error.setText("");
        title.setText(next==0?"家长验证":next==2?"使用恢复码":"设置家长密码");confirm.setText(next==0?"解锁":next==2?"确认恢复码":"设置密码");
        pin.setContentDescription(next==2?"恢复码":"家长密码");recover.setVisibility(next==0?View.VISIBLE:View.GONE);
        if(next==2)error.setText("请输入家长事先保存的恢复码");
    }
    void focus(){if(busy)activity.managementTab().requestFocus();else pin.requestFocus();}
    private void enter(int index){
        if(busy)return;String old=pin.getText().toString();
        pin.setText(index==9?"":index==11?old.isEmpty()?"":old.substring(0,old.length()-1):old.length()<6?old+(index==10?0:index+1):old);pin.setSelection(pin.length());
    }
    boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode(),digit=key>=KeyEvent.KEYCODE_0&&key<=KeyEvent.KEYCODE_9?key-KeyEvent.KEYCODE_0:key>=KeyEvent.KEYCODE_NUMPAD_0&&key<=KeyEvent.KEYCODE_NUMPAD_9?key-KeyEvent.KEYCODE_NUMPAD_0:-1;
        if(digit<0&&key!=KeyEvent.KEYCODE_DEL)return false;
        if(event.getAction()==KeyEvent.ACTION_UP&&!busy)enter(digit<0?11:digit==0?10:digit-1);return true;
    }
    private void enable(boolean value){pin.setEnabled(value);confirm.setEnabled(value);for(Button key:keys)key.setEnabled(value);}
    private void submit(){
        if(closed||busy||!active.current())return;String value=pin.getText().toString();
        if(!ChildModeCredentials.numeric(value)){error.setText("请输入 6 位数字");return;}
        if(mode==2){if(!store.verifyRecovery(value)){error.setText("恢复码不正确");return;}mode(3);focus();return;}
        if(mode==0&&store.retryAfter()>SystemClock.elapsedRealtime()){error.setText("输入次数过多，请稍后重试");return;}
        int request=++generation;boolean setup=mode!=0,firstSetup=mode==1;String credential=store.credential();busy=true;enable(false);error.setText(setup?"正在保存…":"正在验证…");
        worker.execute(()->{
            String prepared=null,problem=null;boolean valid=false;
            try{if(setup){prepared=ChildModeCredentials.prepare(value);valid=true;}else valid=ChildModeCredentials.verify(value,credential);}
            catch(RuntimeException failure){problem=failure.getMessage();}
            final String result=prepared,failure=problem;final boolean matched=valid;
            activity.runOnUiThread(()->{
                if(closed||request!=generation||!active.current()||UiCompat.destroyed(activity)||activity.isFinishing())return;
                busy=false;enable(true);
                if(failure!=null){error.setText(failure);return;}
                if(!matched){store.failed();pin.setText("");error.setText("密码不正确，请重试");focus();return;}
                if(setup&&!store.saveCredential(result,value)){error.setText("保存失败，请重试");return;}
                store.verified();pin.setText("");if(firstSetup)ChildModeAuth.backup(activity,store,()->{if(!closed&&active.current())authorized.run();});else authorized.run();
            });
        });
    }
    void close(){closed=true;generation++;pin.setText("");worker.shutdownNow();}
}
