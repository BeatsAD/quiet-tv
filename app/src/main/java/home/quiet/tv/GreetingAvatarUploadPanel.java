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

/** A one-shot upload hands one immutable candidate to the greeting draft, never to preferences. */
final class GreetingAvatarUploadPanel {
    interface Receiver { boolean accept(File candidate); }
    private final AppManagementActivity ui;
    private final GreetingAvatarStore store;
    private final Receiver receiver;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean receiving=new AtomicBoolean();
    private UploadView view;
    private WallpaperUploadServer server;
    private volatile int generation;
    private boolean received;
    private String host;
    private final Runnable check=new Runnable(){public void run(){
        if(view==null||server==null)return;
        if(server.expired()||!host.equals(WallpaperUploadPanel.lanAddress(ui))){
            stop(false);view.resetConnection();view.address.setVisibility(View.GONE);view.status.setText(tr("会话已结束，请重新连接","Session ended. Reconnect to continue."));
            view.status.setVisibility(View.VISIBLE);view.retry.setVisibility(View.VISIBLE);
        }else main.postDelayed(this,2000);
    }};
    GreetingAvatarUploadPanel(AppManagementActivity ui,GreetingAvatarStore store,Receiver receiver){this.ui=ui;this.store=store;this.receiver=receiver;}
    private String tr(String zh,String en){return ui.tr(zh,en);}
    void show(LinearLayout container,Runnable closed){
        if(view!=null)return;received=false;
        view=new UploadView(container,closed);view.back.requestFocusFromTouch();start();
    }
    private void start(){
        stop(false);int attempt=generation;received=false;receiving.set(false);if(view==null)return;
        view.status.setVisibility(View.GONE);view.retry.setVisibility(View.GONE);view.resetConnection();
        host=WallpaperUploadPanel.lanAddress(ui);
        if(host==null){view.address.setText(R.string.upload_network_unavailable);view.retry.setVisibility(View.VISIBLE);return;}
        String selected=host;view.address.setText(tr("正在开启上传…","Starting upload…"));
        worker.execute(()->{
            WallpaperUploadServer candidate=new WallpaperUploadServer(selected,0,name->ui.getAssets().open("greeting-avatar-upload/"+name),
                (input,length,name,active)->receive(attempt,input,length,active),GreetingAvatarStore.MAX_UPLOAD,"{\"width\":512,\"height\":512,\"maxBytes\":2097152}");
            try{
                candidate.listen();int size=Math.max(384,ui.getResources().getDisplayMetrics().widthPixels/4);BitMatrix matrix=new QRCodeWriter().encode(candidate.address(),BarcodeFormat.QR_CODE,size,size,Collections.singletonMap(EncodeHintType.MARGIN,1));
                int[] pixels=new int[size*size];for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels[y*size+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
                Bitmap qr=Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);
                main.post(()->{if(view==null||attempt!=generation){candidate.stop();qr.recycle();return;}server=candidate;view.connected(candidate.shortAddress(),candidate.connectionCode());view.qr.setImageBitmap(qr);main.postDelayed(check,2000);});
            }catch(IOException|WriterException e){candidate.stop();main.post(()->{if(view!=null&&attempt==generation){view.address.setText(tr("无法开启上传，请检查网络后重试","Unable to start upload. Check the network."));view.retry.setVisibility(View.VISIBLE);}});}
        });
    }
    private static final class Handoff { boolean accepted,cancelled; }
    private String receive(int attempt,InputStream input,long length,WallpaperUploadServer.Active active) throws IOException {
        if(!receiving.compareAndSet(false,true))throw new IOException("已收到头像，请在电视上保存 / Save the received avatar on TV");
        File incoming=null,ready=null;Handoff handoff=new Handoff();
        try{
            incoming=store.incoming();ready=store.candidate();
            try(FileOutputStream out=new FileOutputStream(incoming)){
                byte[] buffer=new byte[16384];long remaining=length;
                while(remaining>0){if(attempt!=generation||!active.valid())throw new IOException("上传会话已结束 / Session ended");int n=input.read(buffer,0,(int)Math.min(buffer.length,remaining));if(n<0)throw new IOException("图片传输未完成 / Incomplete image");out.write(buffer,0,n);remaining-=n;}
            }
            GreetingAvatarStore.prepare(incoming,ready);
            if(attempt!=generation||!active.valid())throw new IOException("上传会话已结束 / Session ended");
            File candidate=ready;CountDownLatch delivered=new CountDownLatch(1);
            main.post(()->{try{synchronized(handoff){
                if(handoff.cancelled||view==null||attempt!=generation||ui.isFinishing())return;
                if(receiver.accept(candidate)){handoff.accepted=true;received=true;close();}
            }}finally{delivered.countDown();}});
            try{delivered.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Upload interrupted",e);}
            synchronized(handoff){if(!handoff.accepted){handoff.cancelled=true;throw new IOException("上传会话已结束 / Session ended");}}
            return "{\"received\":true}";
        }catch(OutOfMemoryError e){throw new IOException("无法处理图片，请选择较小的照片 / Image too large",e);}
        finally{
            GreetingAvatarStore.discard(incoming);
            synchronized(handoff){if(!handoff.accepted){handoff.cancelled=true;GreetingAvatarStore.discard(ready);receiving.set(false);}}
        }
    }
    private void stop(boolean graceful){
        ++generation;main.removeCallbacks(check);WallpaperUploadServer old=server;server=null;
        if(old!=null){old.invalidate();worker.execute(()->{if(graceful)old.finishAfterResponse();else old.stop();});}
    }
    boolean showing(){return view!=null;}
    void close(){UploadView old=view;view=null;if(old!=null){stop(received);old.qr.setImageDrawable(null);old.container.removeView(old.box);old.closed.run();}else stop(false);}
    void destroy(){close();worker.shutdown();}
    private final class UploadView {
        final LinearLayout container,box;
        final ImageView qr;
        final TextView address,status,code;
        final Button retry,back;
        final Runnable closed;
        UploadView(LinearLayout container,Runnable closed){
            this.container=container;this.closed=closed;box=ui.row();box.setPadding(ui.px(13.44f),ui.px(13.44f),ui.px(13.44f),ui.px(13.44f));box.setBackgroundDrawable(ui.surface(UiPalette.CONTROL,UiPalette.CONTROL,UiPalette.EDGE,12));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=ui.px(10.56f);bp.bottomMargin=ui.px(14.4f);container.addView(box,bp);
            qr=new QrCodeView(ui,ui.unit);qr.setContentDescription(tr("头像上传二维码","Avatar upload QR code"));qr.setPadding(ui.px(4),ui.px(4),ui.px(4),ui.px(4));qr.setScaleType(ImageView.ScaleType.FIT_CENTER);box.addView(qr,new LinearLayout.LayoutParams(ui.px(86.4f),ui.px(86.4f)));
            LinearLayout details=ui.column();LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,-2,1);dp.leftMargin=ui.px(19.2f);box.addView(details,dp);details.addView(ui.label(tr("手机上传头像","Upload avatar"),14));address=ui.muted("",11);details.addView(address);code=ui.label("",12);details.addView(code);status=ui.muted(tr("选择照片并裁剪  接收后仍需保存","Crop a photo  Save after receiving"),11);details.addView(status);
            LinearLayout actions=ui.row();details.addView(actions);retry=action(tr("重新连接","Reconnect"),"greeting-avatar-reconnect",GreetingAvatarUploadPanel.this::start);actions.addView(retry,new LinearLayout.LayoutParams(-2,ui.px(34.56f)));back=action(tr("返回编辑","Back to edit"),"greeting-avatar-back",GreetingAvatarUploadPanel.this::close);actions.addView(back,new LinearLayout.LayoutParams(-2,ui.px(34.56f)));back.setNextFocusLeftId(retry.getId());retry.setNextFocusRightId(back.getId());
            View entry=container.getRootView().findViewWithTag("greeting-avatar-upload"),cancel=container.getRootView().findViewWithTag("greeting-cancel");if(entry!=null){back.setNextFocusUpId(entry.getId());retry.setNextFocusUpId(entry.getId());}if(cancel!=null){back.setNextFocusDownId(cancel.getId());retry.setNextFocusDownId(cancel.getId());}
        }
        private Button action(String title,String tag,Runnable action){Button result=ui.button(title,action);result.setId(androidx.core.view.ViewCompat.generateViewId());result.setTag(tag);result.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.712f*ui.unit);result.setPadding(ui.px(13.71f),0,ui.px(13.71f),0);result.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,999,UiPalette.CONTROL,false,UiPalette.EDGE));result.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));return result;}
        void resetConnection(){address.setText(tr("正在开启上传…","Starting upload…"));address.setVisibility(View.VISIBLE);code.setText("");qr.setImageDrawable(null);}
        void connected(String url,String digits){address.setText(url);code.setText(tr("连接码  ","Connection code  ")+digits);}
    }
}
