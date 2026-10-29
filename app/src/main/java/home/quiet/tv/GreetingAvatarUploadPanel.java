package home.quiet.tv;

import android.graphics.*;
import android.os.*;
import android.view.View;
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
    private ImageUploadDialog view;
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
    void show(){
        if(view!=null)return;received=false;
        view=new ImageUploadDialog(ui,ui.chinese(),WallpaperUi.backdrop(ui.stage,ui.root),tr("手机上传头像","Upload avatar"),
            tr("JPG / PNG / 静态 WebP  原图不超过 10 MB","JPG / PNG / static WebP  Original up to 10 MB"),null,this::close,this::start,
            tr("选择照片并裁剪，发送后在电视上保存","Choose and crop a photo, then save on your TV"));
        view.dialog.setOnDismissListener(d->{ImageUploadDialog old=view;view=null;stop(received);old.release();});
        view.dialog.show();start();
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
                candidate.listen();BitMatrix matrix=new QRCodeWriter().encode(candidate.address(),BarcodeFormat.QR_CODE,384,384,Collections.singletonMap(EncodeHintType.MARGIN,1));
                int[] pixels=new int[384*384];for(int y=0;y<384;y++)for(int x=0;x<384;x++)pixels[y*384+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
                Bitmap qr=Bitmap.createBitmap(pixels,384,384,Bitmap.Config.ARGB_8888);
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
    void close(){if(view!=null)view.dialog.dismiss();else stop(false);}
    void destroy(){close();worker.shutdown();}
}
