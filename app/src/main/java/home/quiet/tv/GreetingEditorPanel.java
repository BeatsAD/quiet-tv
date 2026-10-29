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
import java.io.File;
import java.util.Collections;
import java.util.concurrent.*;

/** Keeps both input methods in one draft; only the TV Save button persists it. */
final class GreetingEditorPanel {
    private final AppManagementActivity ui;
    private final DesktopSettingsStore store;
    private final String fallback;
    private final Runnable saved;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private ManagementUi.Sheet editor,phone;
    private Switch toggle,avatarToggle;
    private final GreetingAvatarStore avatars;
    private final GreetingAvatarUploadPanel avatarUpload;
    private GreetingAvatarView avatar;
    private File draftAvatar;
    private boolean avatarEnabled,saving,destroyed;
    private Button upload,remove;
    private TextView draftStatus;
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
    GreetingEditorPanel(AppManagementActivity ui,DesktopSettingsStore store,String fallback,Runnable saved){this.ui=ui;this.store=store;this.fallback=fallback;this.saved=saved;avatars=new GreetingAvatarStore(ui);avatarUpload=new GreetingAvatarUploadPanel(ui,avatars,this::receiveAvatar);}
    private String tr(String zh,String en){return ui.tr(zh,en);}
    private void identify(View view,String tag){view.setId(androidx.core.view.ViewCompat.generateViewId());view.setTag(tag);}
    void show(){
        if(editor!=null)return;
        enabled=store.greetingEnabled();draftAvatar=avatars.existing(store.avatarFile());avatarEnabled=store.avatarEnabled()&&draftAvatar!=null;
        editor=ui.new Sheet(tr("首页问候语","Home greeting"));editor.heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);editor.heading.setHeight(ui.px(31));
        toggle=makeSwitch(tr("显示问候语","Show greeting"),"greeting-toggle");toggle.setChecked(enabled);
        toggle.setOnCheckedChangeListener((v,value)->{enabled=value;updateEnabled();});editor.body.addView(toggle,new LinearLayout.LayoutParams(-1,ui.px(28)));ui.gap(editor.body,12);
        edit=ui.input(store.greetingText(fallback),tr("输入想在首页显示的文字","Enter text to show on Home"));identify(edit,"greeting-text");
        edit.setBackgroundDrawable(ui.focusSurface(10));edit.setImeOptions(EditorInfo.IME_ACTION_DONE|EditorInfo.IME_FLAG_NO_EXTRACT_UI);editor.body.addView(edit,new LinearLayout.LayoutParams(-1,ui.px(46)));
        LinearLayout assist=ui.row();TextView hint=ui.muted(tr("留空时自动问候","Leave empty for automatic greetings"),11);assist.addView(hint,new LinearLayout.LayoutParams(0,-1,1));
        scan=inline(tr("手机输入","Phone input"),"greeting-scan",this::showPhone);assist.addView(scan,new LinearLayout.LayoutParams(-2,-1));editor.body.addView(assist,new LinearLayout.LayoutParams(-1,ui.px(36)));
        ui.gap(editor.body,16);View line=new View(ui);line.setBackgroundColor(0x12ffffff);editor.body.addView(line,new LinearLayout.LayoutParams(-1,ui.px(1)));ui.gap(editor.body,16);
        LinearLayout avatarRow=ui.row();avatar=new GreetingAvatarView(ui);identify(avatar,"greeting-avatar-preview");avatarRow.addView(avatar,new LinearLayout.LayoutParams(ui.px(52),ui.px(52)));
        LinearLayout details=ui.column();TextView caption=ui.label(tr("头像","Avatar"),14);details.addView(caption,new LinearLayout.LayoutParams(-1,ui.px(22)));
        LinearLayout actions=ui.row();upload=inline(tr("更换","Change"),"greeting-avatar-upload",()->{hideKeyboard();avatarUpload.show();});
        remove=inline(tr("移除","Remove"),"greeting-avatar-remove",()->{discardDraft();draftAvatar=null;avatarEnabled=false;draftStatus.setVisibility(View.GONE);updateEnabled();upload.requestFocus();});
        actions.addView(upload,new LinearLayout.LayoutParams(-2,ui.px(30)));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-2,ui.px(30));rp.leftMargin=ui.px(18);actions.addView(remove,rp);details.addView(actions);
        LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,-2,1);dp.leftMargin=ui.px(14);avatarRow.addView(details,dp);
        avatarToggle=makeSwitch("","greeting-avatar-toggle");avatarToggle.setContentDescription(tr("显示头像","Show avatar"));avatarToggle.setChecked(avatarEnabled);
        avatarToggle.setOnCheckedChangeListener((v,value)->avatarEnabled=value);avatarRow.addView(avatarToggle,new LinearLayout.LayoutParams(ui.px(48),ui.px(36)));editor.body.addView(avatarRow,new LinearLayout.LayoutParams(-1,ui.px(56)));
        draftStatus=ui.muted("",11);draftStatus.setVisibility(View.GONE);androidx.core.view.ViewCompat.setAccessibilityLiveRegion(draftStatus,androidx.core.view.ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);editor.body.addView(draftStatus,new LinearLayout.LayoutParams(-1,ui.px(22)));
        editor.actions.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        cancel=editor.action(tr("取消","Cancel"),()->editor.dialog.dismiss());identify(cancel,"greeting-cancel");save=editor.action(tr("保存","Save"),this::save);identify(save,"greeting-save");
        for(Button action:new Button[]{cancel,save}){action.setMinWidth(ui.px(76));action.setMinimumWidth(ui.px(76));action.setBackgroundDrawable(ui.focusSurface(24));}
        save.setBackgroundDrawable(ControlStyle.action(ui.unit,24,0xffe6e8ec,0));save.setTextColor(new android.content.res.ColorStateList(new int[][]{{-android.R.attr.state_enabled},{}},new int[]{0xff686d76,0xff191d23}));
        edit.setOnKeyListener((v,key,event)->{
            if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)((InputMethodManager)ui.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).showSoftInput(edit,InputMethodManager.SHOW_IMPLICIT);return true;}
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_DOWN){scan.requestFocus();return true;}if(key==KeyEvent.KEYCODE_DPAD_UP){toggle.requestFocus();return true;}return false;
        });
        edit.setOnEditorActionListener((v,action,event)->{if(action!=EditorInfo.IME_ACTION_DONE)return false;hideKeyboard();save.requestFocus();return true;});
        editor.dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        editor.dialog.setOnDismissListener(d->{hideKeyboard();avatarUpload.close();if(phone!=null)phone.dialog.dismiss();stopServer(false);if(!saving&&!ui.isChangingConfigurations())discardDraft();editor=null;edit=null;View entry=ui.root.findViewWithTag("greeting");if(entry!=null)entry.requestFocus();});
        updateEnabled();editor.show(toggle);editor.dialog.getWindow().setLayout(ui.px(460),-2);
    }
    private Switch makeSwitch(String text,String tag){
        Switch result=new Switch(ui);identify(result,tag);result.setText(text);result.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,ui.px(14));result.setTextColor(ManagementUi.TEXT);result.setIncludeFontPadding(false);
        result.setTextOn("");result.setTextOff("");if(Build.VERSION.SDK_INT>=16){result.setThumbTextPadding(0);result.setSwitchMinWidth(ui.px(44));result.setSwitchPadding(ui.px(12));}result.setMinimumHeight(0);result.setPadding(ui.px(2),0,ui.px(2),0);result.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);result.setBackgroundColor(Color.TRANSPARENT);result.setFocusable(true);result.setFocusableInTouchMode(true);
        StateListDrawable track=new StateListDrawable();track.addState(new int[]{android.R.attr.state_focused,android.R.attr.state_checked},switchTrack(0xffe1e6ed,true));track.addState(new int[]{android.R.attr.state_focused},switchTrack(0xff535860,true));track.addState(new int[]{android.R.attr.state_checked},switchTrack(0xffe1e6ed,false));track.addState(new int[]{},switchTrack(0xff535860,false));if(Build.VERSION.SDK_INT>=16)result.setTrackDrawable(track);
        StateListDrawable thumb=new StateListDrawable();thumb.addState(new int[]{android.R.attr.state_checked},switchThumb(0xff26303b));thumb.addState(new int[]{},switchThumb(0xffe1e3e5));if(Build.VERSION.SDK_INT>=16)result.setThumbDrawable(thumb);if(Build.VERSION.SDK_INT>=21)result.setSplitTrack(false);return result;
    }
    private Button inline(String text,String tag,Runnable action){Button b=ui.button(text,action);identify(b,tag);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,ui.px(12));b.setPadding(ui.px(3),0,ui.px(3),0);b.setMinWidth(0);b.setMinimumWidth(0);b.setBackgroundDrawable(ControlStyle.action(ui.unit,4,Color.TRANSPARENT,0));return b;}
    private void discardDraft(){File file=draftAvatar;if(file!=null&&!file.getName().equals(store.avatarFile())){draftAvatar=null;worker.execute(()->GreetingAvatarStore.discard(file));}}
    private boolean receiveAvatar(File candidate){
        if(editor==null||saving||destroyed)return false;boolean first=draftAvatar==null;discardDraft();draftAvatar=candidate;if(first)avatarEnabled=true;
        draftStatus.setText(tr("头像已接收，请保存","Avatar received. Select Save to apply."));draftStatus.setVisibility(View.VISIBLE);updateEnabled();upload.requestFocus();return true;
    }
    private void save(){
        if(saving)return;saving=true;editor.dialog.setCancelable(false);updateEnabled();File selected=draftAvatar;boolean showText=enabled,showAvatar=avatarEnabled;String text=edit.getText().toString();
        worker.execute(()->{
            String error=null;try{
                if(selected!=null&&(!selected.isFile()||selected.length()==0))throw new IOException(tr("头像已不可用，请重新上传","Avatar unavailable. Upload again."));
                File old=avatars.file(store.avatarFile());store.saveGreeting(showText,text,showAvatar,selected==null?"":selected.getName());
                if(old!=null&&(selected==null||!old.equals(selected)))GreetingAvatarStore.discard(old);
            }catch(IOException e){error=tr("保存失败，请检查电视存储空间后重试","Unable to save. Check storage and retry.");}
            String failure=error;main.post(()->{saving=false;if(destroyed||editor==null){if(selected!=null&&!selected.getName().equals(store.avatarFile()))GreetingAvatarStore.discard(selected);return;}
                editor.dialog.setCancelable(true);if(failure!=null){draftStatus.setText(failure);draftStatus.setVisibility(View.VISIBLE);updateEnabled();save.requestFocus();return;}
                draftAvatar=null;saved.run();editor.dialog.dismiss();ui.feedback(tr("问候语设置已保存","Greeting settings saved"));
            });
        });
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
        boolean text=enabled&&!saving,has=draftAvatar!=null;edit.setEnabled(text);edit.setAlpha(text?1f:.4f);scan.setEnabled(text);scan.setAlpha(text?1f:.4f);toggle.setEnabled(!saving);
        upload.setEnabled(!saving);upload.setText(has?tr("更换","Change"):tr("手机上传","Phone upload"));upload.setContentDescription(tr("手机上传头像","Upload avatar from phone"));remove.setVisibility(has?View.VISIBLE:View.GONE);remove.setEnabled(!saving);avatarToggle.setEnabled(has&&!saving);avatarToggle.setChecked(avatarEnabled&&has);avatar.show(draftAvatar,true);cancel.setEnabled(!saving);save.setEnabled(!saving);
        toggle.setNextFocusLeftId(toggle.getId());toggle.setNextFocusRightId(toggle.getId());toggle.setNextFocusUpId(toggle.getId());toggle.setNextFocusDownId(text?edit.getId():upload.getId());
        edit.setNextFocusUpId(toggle.getId());edit.setNextFocusDownId(scan.getId());scan.setNextFocusUpId(edit.getId());scan.setNextFocusDownId(upload.getId());scan.setNextFocusLeftId(scan.getId());scan.setNextFocusRightId(scan.getId());
        for(View v:new View[]{upload,remove,avatarToggle}){v.setNextFocusUpId(text?scan.getId():toggle.getId());v.setNextFocusDownId(v==avatarToggle?save.getId():cancel.getId());}
        upload.setNextFocusLeftId(upload.getId());upload.setNextFocusRightId(has?remove.getId():upload.getId());remove.setNextFocusLeftId(upload.getId());remove.setNextFocusRightId(avatarToggle.getId());avatarToggle.setNextFocusLeftId(has?remove.getId():upload.getId());avatarToggle.setNextFocusRightId(avatarToggle.getId());
        cancel.setNextFocusLeftId(cancel.getId());cancel.setNextFocusRightId(save.getId());cancel.setNextFocusUpId(upload.getId());save.setNextFocusLeftId(cancel.getId());save.setNextFocusRightId(save.getId());save.setNextFocusUpId(has?avatarToggle.getId():upload.getId());cancel.setNextFocusDownId(cancel.getId());save.setNextFocusDownId(save.getId());
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
    void pause(){avatarUpload.close();if(phone!=null)phone.dialog.dismiss();stopServer(false);}
    void destroy(){destroyed=true;avatarUpload.destroy();if(editor!=null)editor.dialog.dismiss();stopServer(false);worker.shutdown();}
    void saveState(Bundle state){if(editor==null||saving)return;state.putString("greeting-draft-avatar",draftAvatar==null?"":draftAvatar.getName());state.putBoolean("greeting-draft-avatar-enabled",avatarEnabled);state.putBoolean("greeting-editor",true);state.putBoolean("greeting-draft-enabled",enabled);state.putString("greeting-draft-text",edit.getText().toString());View focus=editor.dialog.getCurrentFocus();state.putString("greeting-draft-focus",focus!=null&&focus.getTag() instanceof String?(String)focus.getTag():"greeting-toggle");}
    void restoreState(Bundle state){if(!state.getBoolean("greeting-editor"))return;show();enabled=state.getBoolean("greeting-draft-enabled");toggle.setChecked(enabled);draftAvatar=avatars.existing(state.getString("greeting-draft-avatar",""));avatarEnabled=state.getBoolean("greeting-draft-avatar-enabled")&&draftAvatar!=null;edit.setText(state.getString("greeting-draft-text",""));updateEnabled();restoredFocus=state.getString("greeting-draft-focus");}
    void restoreFocus(){if(editor==null)return;View target=editor.box.findViewWithTag(restoredFocus==null?"greeting-toggle":restoredFocus);if(target==null||!target.isEnabled())target=toggle;target.requestFocus();}
}
