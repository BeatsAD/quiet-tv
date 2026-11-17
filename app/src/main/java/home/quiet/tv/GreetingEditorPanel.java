package home.quiet.tv;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.*;
import android.text.InputFilter;
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

/** One greeting draft and one modal window; phone input and avatar upload expand in place. */
final class GreetingEditorPanel {
    private final AppManagementActivity ui;
    private final DesktopSettingsStore store;
    private final String fallback;
    private final Runnable saved;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final GreetingAvatarStore avatars;
    private final GreetingAvatarUploadPanel avatarUpload;
    private ManagementUi.Sheet editor;
    private DesktopSettingsRow toggle,avatarToggle;
    private LinearLayout phone,phoneSlot,avatarRow,avatarSlot;
    private GreetingAvatarView avatar;
    private File draftAvatar;
    private boolean enabled,avatarEnabled,saving,destroyed,inlineBack;
    private Button upload,remove,scan,cancel,save,reconnect,phoneBack,close;
    private TextView draftStatus,address,code,status;
    private EditText edit;
    private ImageView qr;
    private volatile int generation;
    private GreetingInputServer server;
    private String host,restoredFocus,baseText;
    private final Runnable check=new Runnable(){public void run(){
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
        if(editor!=null)return;baseText=store.greetingText("");enabled=store.greetingEnabled();draftAvatar=avatars.existing(store.avatarFile());avatarEnabled=store.avatarEnabled()&&draftAvatar!=null;
        editor=ui.new Sheet(tr("问候语与头像","Greeting and avatar"));close=editor.settingsLayout(tr("关闭","Close"),()->editor.dialog.dismiss());editor.fixedSize(480,440);
        toggle=makeSwitch(tr("显示问候语","Show greeting"),"greeting-toggle");toggle.setOnClickListener(v->{enabled=!enabled;updateEnabled();});editor.body.addView(toggle,new LinearLayout.LayoutParams(-1,ui.px(42)));
        LinearLayout field=ui.column();LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.topMargin=ui.px(10.56f);fp.bottomMargin=ui.px(14.4f);editor.body.addView(field,fp);
        TextView label=ui.label(tr("问候语（最多8个字）","Greeting (max 8 characters)"),13);label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,12.48f*ui.unit);label.setTextColor(UiPalette.SECONDARY);field.addView(label,new LinearLayout.LayoutParams(-1,ui.px(17.45f)));ui.gap(field,7.68f);
        edit=ui.input(store.greetingText(fallback),tr("留空时自动问候","Leave empty for automatic greetings"));identify(edit,"greeting-text");edit.setContentDescription(tr("问候语内容","Greeting text"));edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(GreetingInputServer.MAX_TEXT)});
        edit.setImeOptions(EditorInfo.IME_ACTION_DONE|EditorInfo.IME_FLAG_NO_EXTRACT_UI);edit.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,12,0x1f000000,false,UiPalette.EDGE));field.addView(edit,new LinearLayout.LayoutParams(-1,ui.px(44.06f)));
        scan=inline(tr("手机输入","Phone input"),"greeting-scan",this::showPhone,false);editor.body.addView(scan,new LinearLayout.LayoutParams(-2,ui.px(34.56f)));
        phoneSlot=ui.column();editor.body.addView(phoneSlot,new LinearLayout.LayoutParams(-1,-2));
        avatarToggle=makeSwitch(tr("显示圆形头像","Show round avatar"),"greeting-avatar-toggle");avatarToggle.setOnClickListener(v->{avatarEnabled=!avatarEnabled;if(!avatarEnabled)avatarUpload.close();updateEnabled();});editor.body.addView(avatarToggle,new LinearLayout.LayoutParams(-1,ui.px(42)));
        avatarRow=ui.row();avatarRow.setPadding(ui.px(9.6f),ui.px(9.6f),ui.px(9.6f),ui.px(9.6f));avatar=new GreetingAvatarView(ui);identify(avatar,"greeting-avatar-preview");avatarRow.addView(avatar,new LinearLayout.LayoutParams(ui.px(48),ui.px(48)));
        TextView instructions=ui.muted(tr("头像使用手机上传并裁剪","Upload and crop on your phone"),12);instructions.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.52f*ui.unit);instructions.setMaxWidth(ui.px(180));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-2,-2);ip.leftMargin=ui.px(9.6f);avatarRow.addView(instructions,ip);
        upload=inline(tr("手机上传","Phone upload"),"greeting-avatar-upload",()->{hideKeyboard();closePhone(false);avatarUpload.show(avatarSlot,()->{if(editor!=null&&!saving)upload.requestFocusFromTouch();});},false);
        remove=inline(tr("移除头像","Remove avatar"),"greeting-avatar-remove",()->{avatarUpload.close();discardDraft();draftAvatar=null;avatarEnabled=false;draftStatus.setVisibility(View.GONE);updateEnabled();avatarToggle.requestFocusFromTouch();},false);
        for(Button button:new Button[]{upload,remove}){LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,ui.px(34.56f));bp.leftMargin=ui.px(9.6f);avatarRow.addView(button,bp);}editor.body.addView(avatarRow,new LinearLayout.LayoutParams(-1,-2));
        avatarSlot=ui.column();editor.body.addView(avatarSlot,new LinearLayout.LayoutParams(-1,-2));
        draftStatus=ui.muted("",12);draftStatus.setVisibility(View.GONE);androidx.core.view.ViewCompat.setAccessibilityLiveRegion(draftStatus,androidx.core.view.ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);editor.body.addView(draftStatus,new LinearLayout.LayoutParams(-1,-2));
        cancel=inline(tr("取消","Cancel"),"greeting-cancel",()->editor.dialog.dismiss(),false);save=inline(tr("保存","Save"),"greeting-save",this::save,true);
        editor.actions.addView(cancel,new LinearLayout.LayoutParams(-2,-1));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-1);sp.leftMargin=ui.px(6);editor.actions.addView(save,sp);
        edit.setOnKeyListener((v,key,event)->{
            if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)((InputMethodManager)ui.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).showSoftInput(edit,InputMethodManager.SHOW_IMPLICIT);return true;}
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_DOWN){scan.requestFocusFromTouch();return true;}if(key==KeyEvent.KEYCODE_DPAD_UP){toggle.requestFocusFromTouch();return true;}return false;
        });
        edit.setOnEditorActionListener((v,action,event)->{if(action!=EditorInfo.IME_ACTION_DONE)return false;hideKeyboard();save.requestFocusFromTouch();return true;});
        editor.dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        editor.dialog.setOnKeyListener((d,key,event)->{
            if(key!=KeyEvent.KEYCODE_BACK&&key!=KeyEvent.KEYCODE_ESCAPE)return false;
            if(!inlineBack&&phone==null&&!avatarUpload.showing()){if(key!=KeyEvent.KEYCODE_ESCAPE)return false;if(event.getAction()==KeyEvent.ACTION_UP&&!saving)editor.dialog.cancel();return true;}
            if(event.getAction()==KeyEvent.ACTION_DOWN)inlineBack=true;
            if(event.getAction()==KeyEvent.ACTION_UP){inlineBack=false;if(phone!=null){closePhone(false);scan.requestFocusFromTouch();}else avatarUpload.close();KeyFeedback.back(ui);}return true;
        });
        editor.dialog.setOnDismissListener(d->{hideKeyboard();avatarUpload.close();closePhone(false);stopServer(false);if(!saving&&!ui.isChangingConfigurations())discardDraft();editor=null;edit=null;View entry=ui.root.findViewWithTag("greeting");if(entry!=null)entry.requestFocusFromTouch();});
        updateEnabled();editor.show(toggle);
    }
    private DesktopSettingsRow makeSwitch(String text,String tag){DesktopSettingsRow result=ui.setting(text,"",tag);result.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,12,UiPalette.CONTROL,true));result.checked(false);return result;}
    private Button inline(String text,String tag,Runnable action,boolean primary){
        Button button=ui.button(text,action);identify(button,tag);button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.712f*ui.unit);button.setPadding(ui.px(13.71f),0,ui.px(13.71f),0);button.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,999,primary?UiPalette.SELECTED:UiPalette.CONTROL,false,primary?0x4dffffff:UiPalette.EDGE));button.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));return button;
    }
    private void discardDraft(){File file=draftAvatar;if(file!=null&&!file.getName().equals(store.avatarFile())){draftAvatar=null;worker.execute(()->GreetingAvatarStore.discard(file));}}
    private boolean receiveAvatar(File candidate){
        if(editor==null||saving||destroyed)return false;discardDraft();draftAvatar=candidate;avatarEnabled=true;draftStatus.setText(tr("头像已接收，请保存","Avatar received. Select Save to apply."));draftStatus.setVisibility(View.VISIBLE);updateEnabled();return true;
    }
    private void save(){
        if(saving)return;String text=edit.getText().toString();
        if(text.length()>GreetingInputServer.MAX_TEXT){draftStatus.setText(tr("问候语最多8个字","Greeting is limited to 8 characters"));draftStatus.setVisibility(View.VISIBLE);edit.requestFocusFromTouch();return;}
        if(avatarEnabled&&draftAvatar==null){draftStatus.setText(tr("请先上传头像，或关闭显示圆形头像","Upload an avatar, or turn off Show round avatar"));draftStatus.setVisibility(View.VISIBLE);upload.requestFocusFromTouch();return;}
        closePhone(false);avatarUpload.close();saving=true;editor.dialog.setCancelable(false);updateEnabled();File selected=draftAvatar;boolean showText=enabled,showAvatar=avatarEnabled;
        worker.execute(()->{
            String error=null;try{
                if(selected!=null&&(!selected.isFile()||selected.length()==0))throw new IOException("Avatar unavailable");
                File old=avatars.file(store.avatarFile());store.saveGreeting(showText,text,showAvatar,selected==null?"":selected.getName(),baseText);
                if(old!=null&&(selected==null||!old.equals(selected)))GreetingAvatarStore.discard(old);
            }catch(IOException e){error=tr("保存失败，请检查电视存储空间后重试","Unable to save. Check storage and retry.");}
            String failure=error;main.post(()->{saving=false;if(destroyed||editor==null){if(selected!=null&&!selected.getName().equals(store.avatarFile()))GreetingAvatarStore.discard(selected);return;}
                editor.dialog.setCancelable(true);if(failure!=null){draftStatus.setText(failure);draftStatus.setVisibility(View.VISIBLE);updateEnabled();save.requestFocusFromTouch();return;}
                draftAvatar=null;saved.run();editor.dialog.dismiss();ui.feedback(tr("问候语设置已保存","Greeting settings saved"));
            });
        });
    }
    private void updateEnabled(){
        editor.box.setEnabled(!saving);
        toggle.checked(enabled);avatarToggle.checked(avatarEnabled);for(View view:new View[]{toggle,edit,scan,avatarToggle,upload,remove,cancel,save,close})view.setEnabled(!saving);
        avatarRow.setVisibility(avatarEnabled?View.VISIBLE:View.GONE);boolean has=draftAvatar!=null;upload.setText(has?tr("更换照片","Change photo"):tr("手机上传","Phone upload"));avatar.show(draftAvatar,true);
        toggle.setNextFocusUpId(close.getId());toggle.setNextFocusDownId(edit.getId());edit.setNextFocusUpId(toggle.getId());edit.setNextFocusDownId(scan.getId());scan.setNextFocusUpId(edit.getId());scan.setNextFocusDownId(phone==null?avatarToggle.getId():phoneBack.getId());
        avatarToggle.setNextFocusUpId(phone==null?scan.getId():phoneBack.getId());avatarToggle.setNextFocusDownId(avatarEnabled?upload.getId():cancel.getId());upload.setNextFocusUpId(avatarToggle.getId());upload.setNextFocusRightId(remove.getId());upload.setNextFocusDownId(cancel.getId());remove.setNextFocusLeftId(upload.getId());remove.setNextFocusUpId(avatarToggle.getId());remove.setNextFocusDownId(save.getId());
        close.setNextFocusDownId(toggle.getId());cancel.setNextFocusLeftId(cancel.getId());cancel.setNextFocusRightId(save.getId());save.setNextFocusLeftId(cancel.getId());save.setNextFocusRightId(save.getId());cancel.setNextFocusUpId(avatarEnabled?upload.getId():avatarToggle.getId());save.setNextFocusUpId(avatarEnabled?remove.getId():avatarToggle.getId());cancel.setNextFocusDownId(cancel.getId());save.setNextFocusDownId(save.getId());
    }
    private void hideKeyboard(){if(edit!=null)((InputMethodManager)ui.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(edit.getWindowToken(),0);}
    private void showPhone(){
        if(phone!=null){closePhone(false);return;}hideKeyboard();avatarUpload.close();phone=ui.row();phone.setPadding(ui.px(13.44f),ui.px(13.44f),ui.px(13.44f),ui.px(13.44f));phone.setBackgroundDrawable(ui.surface(UiPalette.CONTROL,UiPalette.CONTROL,UiPalette.EDGE,12));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.topMargin=ui.px(10.56f);pp.bottomMargin=ui.px(14.4f);phoneSlot.addView(phone,pp);
        qr=new QrCodeView(ui,ui.unit);qr.setContentDescription(tr("问候语手机输入二维码","Greeting phone input QR code"));qr.setScaleType(ImageView.ScaleType.FIT_CENTER);qr.setPadding(ui.px(4),ui.px(4),ui.px(4),ui.px(4));phone.addView(qr,new LinearLayout.LayoutParams(ui.px(86.4f),ui.px(86.4f)));
        LinearLayout details=ui.column();LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,-2,1);dp.leftMargin=ui.px(19.2f);phone.addView(details,dp);TextView title=ui.label(tr("手机扫码输入问候语","Scan to enter a greeting"),14);details.addView(title);address=ui.muted("",11);details.addView(address);code=ui.label("",12);details.addView(code);status=ui.muted(tr("同一局域网连接  接收后仍需保存","Same local network  Save after receiving"),11);details.addView(status);
        LinearLayout buttons=ui.row();details.addView(buttons);reconnect=inline(tr("重新连接","Reconnect"),"greeting-reconnect",this::startServer,false);buttons.addView(reconnect,new LinearLayout.LayoutParams(-2,ui.px(34.56f)));phoneBack=inline(tr("返回编辑","Back to edit"),"greeting-phone-back",()->{closePhone(false);scan.requestFocusFromTouch();},false);buttons.addView(phoneBack,new LinearLayout.LayoutParams(-2,ui.px(34.56f)));
        phoneBack.setNextFocusUpId(scan.getId());phoneBack.setNextFocusDownId(avatarToggle.getId());phoneBack.setNextFocusLeftId(reconnect.getId());reconnect.setNextFocusRightId(phoneBack.getId());reconnect.setNextFocusUpId(scan.getId());reconnect.setNextFocusDownId(avatarToggle.getId());updateEnabled();phoneBack.requestFocusFromTouch();startServer();
    }
    private void closePhone(boolean graceful){if(phone==null)return;phone=null;qr.setImageDrawable(null);phoneSlot.removeAllViews();stopServer(graceful);if(editor!=null)updateEnabled();}
    private void startServer(){
        stopServer(false);int attempt=++generation;reconnect.setVisibility(View.GONE);qr.setImageDrawable(null);code.setText("");host=WallpaperUploadPanel.lanAddress(ui);
        if(host==null){address.setText(tr("请先连接 Wi-Fi 或网线","Connect Wi-Fi or Ethernet first"));reconnect.setVisibility(View.VISIBLE);return;}
        String selectedHost=host,draft=edit.getText().toString();address.setText(tr("正在连接…","Connecting…"));
        worker.execute(()->{
            GreetingInputServer candidate=new GreetingInputServer(selectedHost,0,draft,name->ui.getAssets().open("greeting-input/"+name),text->receive(attempt,text));
            try{
                candidate.listen();int size=384;BitMatrix matrix=new QRCodeWriter().encode(candidate.address(),BarcodeFormat.QR_CODE,size,size,Collections.singletonMap(EncodeHintType.MARGIN,1));
                int[] pixels=new int[size*size];for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels[y*size+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
                Bitmap bitmap=Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);
                main.post(()->{if(phone==null||attempt!=generation){candidate.stop();bitmap.recycle();return;}server=candidate;address.setText(candidate.shortAddress());code.setText(String.format(tr(ui.getString(R.string.greeting_connection_code_zh),ui.getString(R.string.greeting_connection_code_en)),candidate.connectionCode()));qr.setImageBitmap(bitmap);main.postDelayed(check,2000);});
            }catch(IOException|WriterException e){candidate.stop();main.post(()->{if(phone!=null&&attempt==generation){address.setText(tr("无法连接，请检查网络后重试","Unable to connect. Check the network and retry."));reconnect.setVisibility(View.VISIBLE);}});}
        });
    }
    private boolean receive(int attempt,String text)throws IOException{
        FutureTask<Boolean> accepted=new FutureTask<>(()->{
            if(attempt!=generation||phone==null||editor==null||ui.isFinishing())return false;
            edit.setText(text);edit.setSelection(edit.length());closePhone(true);edit.requestFocusFromTouch();draftStatus.setText(tr("文字已接收，请保存","Text received. Select Save to apply."));draftStatus.setVisibility(View.VISIBLE);return true;
        });main.post(accepted);
        try{return accepted.get(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();accepted.cancel(false);throw new IOException("Input interrupted",e);}catch(ExecutionException|TimeoutException e){accepted.cancel(false);throw new IOException("Input unavailable",e);}
    }
    private void stopServer(boolean graceful){generation++;main.removeCallbacks(check);GreetingInputServer old=server;server=null;if(old!=null){old.invalidate();worker.execute(()->{if(graceful)old.finishAfterResponse();else old.stop();});}}
    void pause(){avatarUpload.close();closePhone(false);stopServer(false);}
    void destroy(){destroyed=true;avatarUpload.destroy();if(editor!=null)editor.dialog.dismiss();stopServer(false);worker.shutdown();}
    void saveState(Bundle state){if(editor==null||saving)return;state.putString("greeting-base-text",baseText);state.putString("greeting-draft-avatar",draftAvatar==null?"":draftAvatar.getName());state.putBoolean("greeting-draft-avatar-enabled",avatarEnabled);state.putBoolean("greeting-editor",true);state.putBoolean("greeting-draft-enabled",enabled);state.putString("greeting-draft-text",edit.getText().toString());View focus=editor.dialog.getCurrentFocus();state.putString("greeting-draft-focus",focus!=null&&focus.getTag() instanceof String?(String)focus.getTag():"greeting-toggle");}
    void restoreState(Bundle state){if(!state.getBoolean("greeting-editor"))return;show();baseText=state.getString("greeting-base-text",store.greetingText(""));enabled=state.getBoolean("greeting-draft-enabled");draftAvatar=avatars.existing(state.getString("greeting-draft-avatar",""));avatarEnabled=state.getBoolean("greeting-draft-avatar-enabled");InputFilter[] filters=edit.getFilters();edit.setFilters(new InputFilter[0]);edit.setText(state.getString("greeting-draft-text",""));edit.setFilters(filters);updateEnabled();restoredFocus=state.getString("greeting-draft-focus");}
    void restoreFocus(){if(editor==null)return;View target=editor.box.findViewWithTag(restoredFocus==null?"greeting-toggle":restoredFocus);if(target==null||!target.isEnabled()||!target.isShown())target=toggle;target.requestFocusFromTouch();}
}
