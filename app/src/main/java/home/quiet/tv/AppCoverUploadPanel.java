package home.quiet.tv;

import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.*;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** A target-bound upload and its confirmation share one sheet; only Apply commits it. */
final class AppCoverUploadPanel {
    private final ManagementUi activity;
    private final AppCoverStore store;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private ManagementUi.Sheet sheet;
    private WallpaperUploadServer server;
    private volatile int generation;
    private volatile File pending;
    private final AtomicBoolean receiving=new AtomicBoolean();
    private String pkg,label,host,address="",code="",notice="";
    private Runnable updated;
    private View origin,previewCard;
    private ImageView qrView;
    private TextView connection,status;
    private Button retry,apply;
    private boolean committing,destroyed,upload,chosen,restoring;
    private boolean retaining;
    private Bundle retained;
    private Bitmap preview,qr;
    private final Runnable check=new Runnable(){@Override public void run(){
        if(sheet==null||server==null)return;
        if(server.expired()||!host.equals(WallpaperUploadPanel.lanAddress(activity))){stopServer();address="";code="";releaseQr();notice="连接已结束，已收到的封面仍可应用";updateConnection();}
        else main.postDelayed(this,2000);
    }};
    AppCoverUploadPanel(ManagementUi activity){this.activity=activity;store=AppCoverStore.get(activity);}
    private int px(float value){return activity.px(value);}
    void show(String pkg,String label,Runnable updated){
        if(sheet!=null||committing||destroyed)return;retaining=false;retained=null;this.pkg=pkg;this.label=label;this.updated=updated;origin=activity.getCurrentFocus();
        sheet=activity.new Sheet("应用封面");ManagementUi.Sheet current=sheet;sheet.settingsLayout("关闭",this::dismiss);sheet.dialog.setOnDismissListener(d->{if(sheet==current)release();});
        sheet.dialog.setOnKeyListener((d,key,event)->{if(!restoring||key!=KeyEvent.KEYCODE_BACK&&key!=KeyEvent.KEYCODE_ESCAPE)return false;if(event.getAction()==KeyEvent.ACTION_UP&&!committing)cancelRestore();return true;});
        BackNavigation.install(sheet.dialog,()->{if(!committing)dismiss();});
        upload=true;chosen=false;restoring=false;notice="";render();sheet.show(sheet.box.findViewWithTag("sheet-close"));start();
    }
    private TextView text(String value,float size,int color){TextView t=activity.label(value,12);t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size*activity.unit);t.setTextColor(color);return t;}
    private void render(){
        if(sheet==null)return;sheet.body.removeAllViews();sheet.actions.removeAllViews();sheet.settingsActions(upload);sheet.contentWidth(upload?390:280,upload?520:380);qrView=null;connection=null;status=null;retry=null;apply=null;previewCard=null;
        sheet.heading.setText(restoring?"恢复默认封面？":"应用封面");
        if(restoring){renderRestoreConfirmation();return;}
        if(!upload){
            Button phone=sheet.option("手机上传封面",()->{upload=true;notice="";render();start();});styleMenuOption(phone,false);
            Button restore=sheet.option("恢复默认封面",this::confirmRestore);restore.setTag("cover-restore");styleMenuOption(restore,true);phone.requestFocus();return;
        }
        LinearLayout transfer=activity.row();transfer.setGravity(Gravity.CENTER_VERTICAL);transfer.setPadding(px(14.35f),px(14.35f),px(14.35f),px(14.35f));transfer.setBackgroundDrawable(activity.surface(UiPalette.CONTROL,UiPalette.CONTROL,UiPalette.EDGE,9.6f));
        qrView=new QrCodeView(activity,activity.unit);qrView.setPadding(px(3.84f),px(3.84f),px(3.84f),px(3.84f));qrView.setScaleType(ImageView.ScaleType.FIT_CENTER);qrView.setContentDescription("上传地址二维码");qrView.setImageBitmap(qr);transfer.addView(qrView,new LinearLayout.LayoutParams(px(86.4f),px(86.4f)));
        LinearLayout info=activity.column();LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-2,1);ip.leftMargin=px(19.2f);transfer.addView(info,ip);
        TextView title=text("手机上传应用封面",14.4f,UiPalette.TEXT);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));info.addView(title);
        TextView requirements=text("推荐 720 × 406  最低 360 × 203\nJPG / PNG / 静态 WebP  不超过 2 MB",11.04f,UiPalette.SECONDARY);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=px(9.6f);info.addView(requirements,rp);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=px(10.56f);tp.bottomMargin=0;sheet.body.addView(transfer,tp);
        connection=text("",9.12f,UiPalette.SECONDARY);connection.setTag("cover-connection");info.addView(connection);
        LinearLayout previews=activity.row();previews.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);previews.setClipChildren(false);previews.setClipToPadding(false);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,px(86.4f));pp.topMargin=px(14.4f);pp.bottomMargin=0;if(preview!=null)sheet.body.addView(previews,pp);
        if(preview!=null){
            FrameLayout card=new FrameLayout(activity);previewCard=card;card.setContentDescription("选择收到的封面");card.setTag("cover-candidate");card.setFocusable(true);card.setFocusableInTouchMode(true);card.setClickable(true);SettingsTouchClick.attach(card);card.setPadding(px(5.7f),px(5.7f),px(5.7f),px(5.7f));candidateStyle();
            ImageView picture=new ImageView(activity);picture.setImageBitmap(preview);picture.setScaleType(ImageView.ScaleType.CENTER_CROP);picture.setBackgroundDrawable(activity.surface(0,0,0,4.8f));UiCompat.clipOutline(picture);card.addView(picture,new FrameLayout.LayoutParams(-1,-1));
            previews.addView(card,new LinearLayout.LayoutParams(0,-1,1));previews.addView(new View(activity),new LinearLayout.LayoutParams(px(19.2f),1));previews.addView(new View(activity),new LinearLayout.LayoutParams(0,1,2));
            card.setOnClickListener(v->{card.requestFocusFromTouch();chosen=true;candidateStyle();updateApply();});
        }
        status=text(notice,10.848f,UiPalette.SECONDARY);status.setTag("cover-status");status.setVisibility(notice.isEmpty()?View.GONE:View.VISIBLE);sheet.body.addView(status);
        retry=activity.button("重新连接",this::start);styleOption(retry);LinearLayout.LayoutParams retryParams=new LinearLayout.LayoutParams(-2,px(30));retryParams.topMargin=px(8);sheet.body.addView(retry,retryParams);
        sheet.action("取消",()->{if(committing)return;stopServer();discardPending();upload=false;render();});
        apply=sheet.action("应用封面",this::commit);apply.setTag("cover-apply");apply.setBackgroundDrawable(new SettingsFocusDrawable(activity.unit,999,UiPalette.SELECTED,false,0x4dffffff));apply.setTextColor(new android.content.res.ColorStateList(new int[][]{{-android.R.attr.state_enabled},{android.R.attr.state_focused,android.R.attr.state_window_focused},{}},new int[]{UiPalette.TEXT,UiPalette.INK,UiPalette.TEXT}));updateApply();updateConnection();
    }
    private void candidateStyle(){if(previewCard!=null){previewCard.setSelected(false);previewCard.setBackgroundDrawable(new SettingsFocusDrawable(activity.unit,9.6f,chosen?0x20ffffff:UiPalette.CONTROL,false,chosen?UiPalette.TEXT:0x35ffffff));}}
    private void styleOption(Button b){b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.712f*activity.unit);b.setBackgroundDrawable(new SettingsFocusDrawable(activity.unit,12,UiPalette.CONTROL,false,UiPalette.EDGE));b.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));}
    private void styleMenuOption(Button b,boolean last){styleOption(b);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,14.4f*activity.unit);b.setBackgroundDrawable(new SettingsFocusDrawable(activity.unit,21.12f,UiPalette.CONTROL,false,UiPalette.EDGE));LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)b.getLayoutParams();p.height=px(39.36f);p.bottomMargin=last?0:px(7.2f);b.setLayoutParams(p);}
    private void updateApply(){if(apply==null)return;boolean enabled=chosen&&pending!=null&&!committing;apply.setEnabled(enabled);apply.setAlpha(1f);}
    private void updateConnection(){
        if(connection==null)return;connection.setText(address.isEmpty()?(server==null?"正在开启上传…":""):address+"  连接码  "+code);if(qrView!=null)qrView.setImageBitmap(qr);
        if(status!=null){status.setText(notice);status.setVisibility(notice.isEmpty()?View.GONE:View.VISIBLE);}if(retry!=null)retry.setVisibility(server==null&&!notice.isEmpty()?View.VISIBLE:View.GONE);
    }
    private void start(){
        if(sheet==null||committing||!upload)return;stopServer();final int attempt=generation;receiving.set(pending!=null);address="";code="";notice="";releaseQr();updateConnection();
        host=WallpaperUploadPanel.lanAddress(activity);if(host==null){notice="请先让手机与电视连接同一个局域网";updateConnection();return;}
        final String selectedHost=host,target=pkg,name=label;
        worker.execute(()->{
            WallpaperUploadServer candidate=null;
            try{
                AppCoverPolicy.require(activity,target);
                String info=new JSONObject().put("label",name).put("width",720).put("height",406).put("minWidth",360).put("minHeight",203).put("maxBytes",AppCoverStore.MAX_UPLOAD).toString();
                candidate=new WallpaperUploadServer(selectedHost,8765,asset->activity.getAssets().open("app-cover-upload/"+asset),(input,length,filename,active)->receive(attempt,target,input,length,active),AppCoverStore.MAX_UPLOAD,info);
                try{candidate.listen();}catch(IOException occupied){candidate.stop();candidate=new WallpaperUploadServer(selectedHost,0,asset->activity.getAssets().open("app-cover-upload/"+asset),(input,length,filename,active)->receive(attempt,target,input,length,active),AppCoverStore.MAX_UPLOAD,info);candidate.listen();}
                int size=384;BitMatrix matrix=new QRCodeWriter().encode(candidate.address(),BarcodeFormat.QR_CODE,size,size,Collections.singletonMap(EncodeHintType.MARGIN,1));int[] pixels=new int[size*size];for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels[y*size+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
                Bitmap bitmap=Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);WallpaperUploadServer ready=candidate;
                main.post(()->{if(sheet==null||attempt!=generation){ready.stop();bitmap.recycle();return;}server=ready;qr=bitmap;address=ready.shortAddress();code=ready.connectionCode();updateConnection();main.postDelayed(check,2000);});
            }catch(IOException|JSONException|WriterException e){if(candidate!=null)candidate.stop();main.post(()->{if(sheet!=null&&attempt==generation){notice="无法开启上传，请检查应用状态与网络后重试";updateConnection();}});}
        });
    }
    private String receive(int attempt,String target,InputStream input,long length,WallpaperUploadServer.Active active)throws IOException {
        if(!receiving.compareAndSet(false,true))throw new IOException("已收到封面，请先在电视确认 / Confirm the received cover on TV");
        File incoming=null,ready=null;boolean staged=false;
        try{
            AppCoverPolicy.require(activity,target);incoming=store.incoming();ready=store.prepared();
            try(FileOutputStream output=new FileOutputStream(incoming)){byte[] buffer=new byte[16384];long remaining=length;while(remaining>0){if(attempt!=generation||!active.valid())throw new IOException("上传会话已结束 / Session ended");int n=input.read(buffer,0,(int)Math.min(buffer.length,remaining));if(n<0)throw new IOException("图片传输未完成 / Incomplete upload");output.write(buffer,0,n);remaining-=n;}}
            AppCoverImages.prepare(incoming,ready);AppCoverPolicy.require(activity,target);if(attempt!=generation||!active.valid())throw new IOException("上传会话已结束 / Session ended");
            File received=ready;staged=true;main.post(()->{if(sheet==null||attempt!=generation){AppCoverStore.discard(received);return;}pending=received;try{preview=AppCoverImages.load(received,360);}catch(RuntimeException|OutOfMemoryError e){preview=null;}
                if(preview==null){discardPending();receiving.set(false);notice="无法预览封面，请重新上传";}else notice="";chosen=false;render();if(previewCard!=null)previewCard.requestFocus();});return "{\"received\":true}";
        }catch(OutOfMemoryError e){throw new IOException("电视无法处理这张图片，请选择较小图片 / Image too large for this TV");}
        finally{AppCoverStore.discard(incoming);if(!staged){AppCoverStore.discard(ready);receiving.set(false);}}
    }
    private void busy(boolean value){committing=value;if(sheet==null)return;sheet.box.setEnabled(!value);sheet.dialog.setCancelable(!value);setChildrenEnabled(sheet.body,!value);setChildrenEnabled(sheet.actions,!value);View close=sheet.box.findViewWithTag("sheet-close");if(close!=null)close.setEnabled(!value);updateApply();}
    private void setChildrenEnabled(ViewGroup group,boolean enabled){for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);child.setEnabled(enabled);if(child instanceof ViewGroup)setChildrenEnabled((ViewGroup)child,enabled);}}
    private void commit(){
        if(committing||!chosen||pending==null)return;File ready=pending;String target=pkg;busy(true);
        worker.execute(()->{String error=null;try{AppCoverPolicy.require(activity,target);store.apply(target,ready);}catch(IOException e){error=e.getMessage();}String failure=error;
            main.post(()->{busy(false);if(failure==null){pending=null;close();if(!destroyed&&!activity.isFinishing())activity.feedback("已更换应用封面");}else if(sheet!=null){notice=failure;updateConnection();updateApply();if(apply!=null)apply.requestFocus();}else if(!retaining)discardPending();});});
    }
    private void confirmRestore(){if(committing)return;restoring=true;notice="";render();}
    private void cancelRestore(){if(committing||sheet==null)return;restoring=false;notice="";render();View restore=sheet.body.findViewWithTag("cover-restore");if(restore!=null)restore.requestFocusFromTouch();}
    private void dismiss(){if(restoring)cancelRestore();else close();}
    private void renderRestoreConfirmation(){
        sheet.settingsActions(true);AppActionConfirmationUi.content(activity,sheet,pkg,label,AppActionConfirmationUi.RESTORE_COVER,false);
        status=text(notice,11,UiPalette.STATUS_ERROR);status.setVisibility(notice.isEmpty()?View.GONE:View.VISIBLE);sheet.body.addView(status);
        Button cancel=sheet.action("取消",this::cancelRestore),confirm=sheet.action("恢复默认封面",this::restore);AppActionConfirmationUi.actions(activity,sheet,cancel,confirm,false);cancel.post(cancel::requestFocusFromTouch);
    }
    private void restore(){
        if(committing)return;busy(true);String target=pkg;worker.execute(()->{String error=null;try{AppCoverPolicy.require(activity,target);store.restore(target);}catch(IOException e){error=e.getMessage();}String failure=error;main.post(()->{busy(false);if(failure==null){close();if(!destroyed&&!activity.isFinishing())activity.feedback("已恢复默认封面");}else if(sheet!=null){notice=failure;status.setText(failure);status.setVisibility(View.VISIBLE);}});});
    }
    private void stopServer(){++generation;main.removeCallbacks(check);WallpaperUploadServer old=server;server=null;if(old!=null){old.invalidate();worker.execute(old::stop);}}
    private void releaseQr(){if(qrView!=null)qrView.setImageDrawable(null);if(qr!=null){qr.recycle();qr=null;}}
    private void discardPending(){File discarded=pending;pending=null;store.discardCandidate(discarded);chosen=false;if(preview!=null){preview.recycle();preview=null;}}
    void saveState(Bundle state){if(sheet!=null){Bundle value=new Bundle();value.putString("package",pkg);value.putString("label",label);value.putBoolean("upload",upload);value.putBoolean("restore-confirmation",restoring);value.putBoolean("chosen",chosen);value.putBoolean("committing",committing);if(pending!=null)value.putString("candidate",pending.getName());retained=value;}if(retained!=null)state.putBundle("app-cover",retained);}
    void restoreState(Bundle state,Runnable updated){
        if(state==null)return;String target=state.getString("package","");if(!AppCoverPolicy.allowed(activity,target))return;
        File ready=store.candidate(state.getString("candidate"));if(ready!=null&&ready.equals(store.file(target))&&state.getBoolean("committing"))return;
        show(target,state.getString("label",target),updated);if(sheet==null)return;
        if(ready!=null&&ready.isFile()){pending=ready;try{preview=AppCoverImages.load(ready,360);}catch(RuntimeException|OutOfMemoryError e){preview=null;}if(preview==null){discardPending();notice="无法读取待确认封面，请重新上传";}}
        chosen=preview!=null&&state.getBoolean("chosen");upload=state.getBoolean("upload",true);restoring=state.getBoolean("restore-confirmation");render();if(!upload)stopServer();
    }
    void stop(boolean configuration){if(configuration&&sheet!=null){saveState(new Bundle());retaining=true;}close();}
    private void release(){sheet=null;stopServer();releaseQr();if(!retaining)retained=null;if(!committing&&!retaining)discardPending();else if(preview!=null){preview.recycle();preview=null;}if(origin!=null&&origin.getWindowToken()!=null)origin.requestFocus();origin=null;if(updated!=null&&!destroyed)updated.run();}
    void close(){if(sheet!=null){ManagementUi.Sheet current=sheet;release();current.dialog.setOnDismissListener(null);current.dialog.dismiss();}else{stopServer();if(!committing&&!retaining)discardPending();}}
    void destroy(){destroyed=true;close();worker.shutdown();}
}
