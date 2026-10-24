package home.quiet.tv;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.*;
import android.graphics.drawable.shapes.OvalShape;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.*;

/** Keeps both input methods in one draft; only the TV Save button persists it. */
final class GreetingEditorPanel {
    private final DesktopSettingsActivity ui;
    private final DesktopSettingsStore store;
    private final String fallback;
    private final Runnable saved;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private ManagementUi.Sheet editor,phone;
    private Switch toggle;
    private EditText edit;
    private Button scan,cancel,save,reconnect;
    private TextView address,code,status;
    private ImageView qr;
    private boolean enabled,received;
    private volatile int generation;
    private GreetingInputServer server;
    private String host,restoredFocus;
    private final Runnable check=new Runnable(){@Override public void run(){
        if(phone==null||server==null)return;
        if(server.expired()||!host.equals(WallpaperUploadPanel.lanAddress(ui))){
            stopServer(false);qr.setImageDrawable(null);code.setText("");
            status.setText(tr("会话已结束，请重新连接","Session ended. Reconnect to continue."));reconnect.setVisibility(View.VISIBLE);
        }else main.postDelayed(this,2000);
    }};
    GreetingEditorPanel(DesktopSettingsActivity ui,DesktopSettingsStore store,String fallback,Runnable saved){this.ui=ui;this.store=store;this.fallback=fallback;this.saved=saved;}
    private String tr(String zh,String en){return ui.tr(zh,en);}
    private void identify(View view,String tag){view.setId(View.generateViewId());view.setTag(tag);}
    void show(){
        if(editor!=null)return;
        enabled=store.greetingEnabled();editor=ui.new Sheet(tr("首页问候语","Home greeting"));
        editor.heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);editor.heading.setHeight(ui.px(31));
        toggle=new Switch(ui);identify(toggle,"greeting-toggle");toggle.setText(tr("显示问候语","Show greeting"));toggle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,ui.px(14));toggle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);toggle.setTextColor(ManagementUi.TEXT);toggle.setIncludeFontPadding(false);
        toggle.setTextOn("");toggle.setTextOff("");toggle.setThumbTextPadding(0);toggle.setSwitchMinWidth(ui.px(44));toggle.setSwitchPadding(ui.px(12));toggle.setMinimumHeight(0);toggle.setPadding(ui.px(2),0,ui.px(2),0);toggle.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);toggle.setBackgroundColor(Color.TRANSPARENT);toggle.setFocusable(true);toggle.setFocusableInTouchMode(true);
        StateListDrawable track=new StateListDrawable();track.addState(new int[]{android.R.attr.state_focused,android.R.attr.state_checked},switchTrack(0xffe1e6ed,true));track.addState(new int[]{android.R.attr.state_focused},switchTrack(0xff535860,true));track.addState(new int[]{android.R.attr.state_checked},switchTrack(0xffe1e6ed,false));track.addState(new int[]{},switchTrack(0xff535860,false));toggle.setTrackDrawable(track);
        StateListDrawable thumb=new StateListDrawable();thumb.addState(new int[]{android.R.attr.state_checked},switchThumb(0xff26303b));thumb.addState(new int[]{},switchThumb(0xffe1e3e5));toggle.setThumbDrawable(thumb);toggle.setChecked(enabled);
        if(Build.VERSION.SDK_INT>=21)toggle.setSplitTrack(false);
        toggle.setOnCheckedChangeListener((v,checked)->{enabled=checked;updateEnabled();});editor.body.addView(toggle,new LinearLayout.LayoutParams(-1,ui.px(42)));ui.gap(editor.body,13);
        edit=ui.input(store.greetingText(fallback),tr("输入想在首页显示的文字","Enter text to show on Home"));identify(edit,"greeting-text");
        edit.setTypeface(Typeface.DEFAULT,Typeface.BOLD);StateListDrawable inputBackground=new StateListDrawable();inputBackground.addState(new int[]{android.R.attr.state_focused},ui.surface(0x07ffffff,0x07ffffff,Color.WHITE,10));inputBackground.addState(new int[]{},ui.surface(0x07ffffff,0x07ffffff,0x35ffffff,10));edit.setBackground(inputBackground);
        edit.setImeOptions(EditorInfo.IME_ACTION_DONE|EditorInfo.IME_FLAG_NO_EXTRACT_UI);editor.body.addView(edit,new LinearLayout.LayoutParams(-1,ui.px(44)));ui.gap(editor.body,10);
        TextView hint=ui.muted(tr("留空时，按时间显示早上好、下午好或晚上好","Leave empty for a greeting based on the time of day"),11);hint.setTextColor(0xffaeb3bf);hint.setTypeface(Typeface.DEFAULT,Typeface.BOLD);hint.setMinimumHeight(ui.px(19));editor.body.addView(hint,new LinearLayout.LayoutParams(-1,-2));
        scan=editor.action(tr("手机扫码输入","Phone input"),this::showPhone);identify(scan,"greeting-scan");
        editor.actions.addView(new View(ui),new LinearLayout.LayoutParams(0,1,1));
        cancel=editor.action(tr("取消","Cancel"),()->editor.dialog.dismiss());identify(cancel,"greeting-cancel");
        save=editor.action(tr("保存","Save"),()->{store.saveGreeting(enabled,edit.getText().toString());saved.run();editor.dialog.dismiss();});identify(save,"greeting-save");
        for(Button action:new Button[]{scan,cancel,save}){action.setTypeface(Typeface.DEFAULT,Typeface.BOLD);action.setMinWidth(ui.px(70));action.setMinimumWidth(ui.px(70));LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)action.getLayoutParams();p.leftMargin=action==scan?0:ui.px(5);p.rightMargin=action==save?0:ui.px(5);action.setLayoutParams(p);action.setBackground(ui.focusSurface(24));}
        toggle.setNextFocusUpId(toggle.getId());toggle.setNextFocusLeftId(toggle.getId());toggle.setNextFocusRightId(toggle.getId());
        edit.setNextFocusUpId(toggle.getId());edit.setNextFocusDownId(scan.getId());
        edit.setOnKeyListener((v,key,event)->{
            if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)((InputMethodManager)ui.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).showSoftInput(edit,InputMethodManager.SHOW_IMPLICIT);return true;}
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){scan.requestFocus();return true;}if(key==KeyEvent.KEYCODE_DPAD_UP){toggle.requestFocus();return true;}return false;
        });
        edit.setOnEditorActionListener((v,action,event)->{if(action!=EditorInfo.IME_ACTION_DONE)return false;hideKeyboard();save.requestFocus();return true;});
        scan.setNextFocusLeftId(scan.getId());scan.setNextFocusRightId(cancel.getId());cancel.setNextFocusRightId(save.getId());save.setNextFocusLeftId(cancel.getId());save.setNextFocusRightId(save.getId());
        for(Button b:new Button[]{scan,cancel,save})b.setNextFocusDownId(b.getId());
        editor.dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        editor.dialog.setOnDismissListener(d->{hideKeyboard();if(phone!=null)phone.dialog.dismiss();stopServer(false);editor=null;edit=null;ui.root.findViewWithTag("greeting").requestFocus();});
        updateEnabled();editor.show(toggle);
    }
    private GradientDrawable switchTrack(int color,boolean focused){
        GradientDrawable shape=new GradientDrawable(){@Override public boolean getPadding(Rect padding){padding.set(ui.px(3),0,ui.px(3),0);return true;}};
        shape.setColor(color);shape.setCornerRadius(ui.px(12));shape.setSize(ui.px(44),ui.px(24));if(focused)shape.setStroke(ui.px(2),Color.WHITE);return shape;
    }
    private ShapeDrawable switchThumb(int color){
        ShapeDrawable shape=new ShapeDrawable(new OvalShape(){@Override public void draw(Canvas canvas,Paint paint){canvas.drawCircle(getWidth()/2,getHeight()/2,Math.min(getWidth(),getHeight())/2-ui.px(3),paint);}});
        shape.getPaint().setColor(color);shape.setIntrinsicWidth(ui.px(24));shape.setIntrinsicHeight(ui.px(24));shape.setPadding(ui.px(3),ui.px(3),ui.px(3),ui.px(3));return shape;
    }
    private void updateEnabled(){
        edit.setEnabled(enabled);edit.setAlpha(enabled?1f:.4f);scan.setEnabled(enabled);scan.setAlpha(enabled?1f:.4f);
        toggle.setNextFocusDownId(enabled?edit.getId():cancel.getId());cancel.setNextFocusLeftId(enabled?scan.getId():cancel.getId());
        for(Button b:new Button[]{scan,cancel,save})b.setNextFocusUpId(enabled?edit.getId():toggle.getId());
        if(!enabled)hideKeyboard();
    }
    private void hideKeyboard(){if(edit!=null)((InputMethodManager)ui.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(edit.getWindowToken(),0);}
    private void showPhone(){
        if(!enabled||phone!=null)return;hideKeyboard();received=false;phone=ui.new Sheet(tr("手机扫码输入","Phone input"));
        LinearLayout row=ui.row(),details=ui.column();
        TextView instructions=ui.label(tr("手机与电视连接同一局域网\n扫码后输入文字，发送到电视","Connect your phone to the TV’s local network.\nScan the code, then send your text."),13);instructions.setLineSpacing(ui.px(6),1);details.addView(instructions);
        ui.gap(details,14);address=ui.label("",14);details.addView(address);ui.gap(details,6);
        code=ui.label("",13);details.addView(code);ui.gap(details,12);
        status=ui.muted(tr("也可在手机浏览器打开地址，输入连接码","Or open the address in your browser and enter the code"),11);details.addView(status);
        row.addView(details,new LinearLayout.LayoutParams(0,-2,1));qr=new ImageView(ui);qr.setContentDescription(tr("问候语输入二维码","Greeting input QR code"));qr.setBackgroundColor(Color.WHITE);qr.setPadding(ui.px(8),ui.px(8),ui.px(8),ui.px(8));
        LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(ui.px(150),ui.px(150));qp.leftMargin=ui.px(22);row.addView(qr,qp);phone.body.addView(row,new LinearLayout.LayoutParams(-1,-2));
        reconnect=phone.action(tr("重新连接","Reconnect"),this::startServer);identify(reconnect,"greeting-reconnect");
        Button back=phone.action(tr("返回编辑","Back to edit"),()->phone.dialog.dismiss());identify(back,"greeting-phone-back");
        phone.dialog.setOnDismissListener(d->{phone=null;stopServer(received);if(edit!=null){if(received)edit.requestFocus();else scan.requestFocus();}});
        phone.show(back);phone.dialog.getWindow().setLayout(ui.px(610),-2);startServer();
    }
    private void startServer(){
        stopServer(false);int attempt=++generation;received=false;reconnect.setVisibility(View.GONE);qr.setImageDrawable(null);code.setText("");host=WallpaperUploadPanel.lanAddress(ui);
        if(host==null){address.setText(tr("请先连接 Wi-Fi 或网线","Connect Wi-Fi or Ethernet first"));reconnect.setVisibility(View.VISIBLE);return;}
        String selectedHost=host,draft=edit.getText().toString();address.setText(tr("正在连接…","Connecting…"));
        worker.execute(()->{
            GreetingInputServer candidate=new GreetingInputServer(selectedHost,0,draft,name->ui.getAssets().open("greeting-input/"+name),text->receive(attempt,text));
            try{
                candidate.listen();BitMatrix matrix=new QRCodeWriter().encode(candidate.address(),BarcodeFormat.QR_CODE,384,384,Collections.singletonMap(EncodeHintType.MARGIN,1));
                int[] pixels=new int[384*384];for(int y=0;y<384;y++)for(int x=0;x<384;x++)pixels[y*384+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
                Bitmap bitmap=Bitmap.createBitmap(pixels,384,384,Bitmap.Config.ARGB_8888);
                main.post(()->{if(phone==null||attempt!=generation){candidate.stop();bitmap.recycle();return;}server=candidate;address.setText(candidate.shortAddress());code.setText(String.format(tr(ui.getString(R.string.greeting_connection_code_zh),ui.getString(R.string.greeting_connection_code_en)),candidate.connectionCode()));qr.setImageBitmap(bitmap);main.postDelayed(check,2000);});
            }catch(IOException|WriterException e){candidate.stop();main.post(()->{if(phone!=null&&attempt==generation){address.setText(tr("无法连接，请检查网络后重试","Unable to connect. Check the network and retry."));reconnect.setVisibility(View.VISIBLE);}});}
        });
    }
    private boolean receive(int attempt,String text)throws IOException{
        FutureTask<Boolean> accepted=new FutureTask<>(()->{
            if(attempt!=generation||phone==null||editor==null||!enabled||ui.isFinishing())return false;
            edit.setText(text);edit.setSelection(edit.length());received=true;phone.dialog.dismiss();ui.feedback(tr("文字已接收，请保存","Text received. Select Save to apply."));return true;
        });main.post(accepted);
        try{return accepted.get(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();accepted.cancel(false);throw new IOException("Input interrupted",e);}catch(ExecutionException|TimeoutException e){accepted.cancel(false);throw new IOException("Input unavailable",e);}
    }
    private void stopServer(boolean graceful){
        generation++;main.removeCallbacks(check);GreetingInputServer old=server;server=null;
        if(old!=null){old.invalidate();worker.execute(()->{if(graceful)old.finishAfterResponse();else old.stop();});}
    }
    void pause(){if(phone!=null)phone.dialog.dismiss();stopServer(false);}
    void destroy(){if(editor!=null)editor.dialog.dismiss();stopServer(false);worker.shutdown();}
    void saveState(Bundle state){if(editor==null)return;state.putBoolean("greeting-editor",true);state.putBoolean("greeting-draft-enabled",enabled);state.putString("greeting-draft-text",edit.getText().toString());View focus=editor.dialog.getCurrentFocus();state.putString("greeting-draft-focus",focus!=null&&focus.getTag() instanceof String?(String)focus.getTag():"greeting-toggle");}
    void restoreState(Bundle state){if(!state.getBoolean("greeting-editor"))return;show();enabled=state.getBoolean("greeting-draft-enabled");toggle.setChecked(enabled);edit.setText(state.getString("greeting-draft-text",""));updateEnabled();restoredFocus=state.getString("greeting-draft-focus");}
    void restoreFocus(){if(editor==null)return;View target=editor.box.findViewWithTag(restoredFocus==null?"greeting-toggle":restoredFocus);if(target==null||!target.isEnabled())target=toggle;target.requestFocus();}
}
