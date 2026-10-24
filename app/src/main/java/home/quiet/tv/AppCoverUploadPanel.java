package home.quiet.tv;

import android.app.*;
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

/** A target-bound upload stages a cover; only the TV's confirmation can commit it. */
final class AppCoverUploadPanel {
    private final ManagementUi activity;
    private final AppCoverStore store;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private ImageUploadDialog view;
    private WallpaperUploadServer server;
    private ManagementUi.Sheet confirmation;
    private volatile int generation;
    private volatile File pending;
    private final AtomicBoolean receiving=new AtomicBoolean();
    private String pkg,label,host;
    private Runnable updated;
    private boolean committing,destroyed;
    private Bitmap preview;
    private final Runnable check=new Runnable(){@Override public void run(){
        if(view==null||server==null)return;
        if(server.expired()||!host.equals(WallpaperUploadPanel.lanAddress(activity))){
            ImageUploadDialog current=view;stopServer();if(confirmation!=null)confirmation.dialog.dismiss();
            if(view!=current)return;view.resetConnection();view.address.setVisibility(View.GONE);view.status.setText(R.string.upload_session_ended);view.status.setVisibility(View.VISIBLE);view.retry.setVisibility(View.VISIBLE);
        }else main.postDelayed(this,2000);
    }};
    AppCoverUploadPanel(ManagementUi activity){this.activity=activity;store=AppCoverStore.get(activity);}
    void show(String pkg,String label,Runnable updated){
        if(view!=null||committing||destroyed)return;this.pkg=pkg;this.label=label;this.updated=updated;
        view=new ImageUploadDialog(activity,true,WallpaperUi.backdrop(activity.stage,activity.root),"更换 "+label+" 封面",
            "推荐 720 × 406 px  最低 360 × 203 px\nJPG / PNG / 静态 WebP  单张不超过 2 MB",
            "可用 AI 辅助生成应用封面，按上述尺寸导出。",this::close,this::start);
        view.dialog.setOnDismissListener(d->{ImageUploadDialog old=view;view=null;stopServer();if(confirmation!=null)confirmation.dialog.dismiss();old.release();});
        view.dialog.show();start();
    }
    private void start(){
        stopServer();final int attempt=generation;receiving.set(false);ImageUploadDialog current=view;if(current==null)return;
        current.status.setVisibility(View.GONE);current.retry.setVisibility(View.GONE);current.resetConnection();
        host=WallpaperUploadPanel.lanAddress(activity);
        if(host==null){current.address.setText(R.string.upload_network_unavailable);current.retry.setVisibility(View.VISIBLE);return;}
        final String selectedHost=host,target=pkg,name=label;
        worker.execute(()->{
            WallpaperUploadServer candidate=null;
            try{
                AppCoverPolicy.require(activity,target);
                String info=new JSONObject().put("label",name).put("width",720).put("height",406).put("minWidth",360).put("minHeight",203).put("maxBytes",AppCoverStore.MAX_UPLOAD).toString();
                candidate=new WallpaperUploadServer(selectedHost,8765,asset->activity.getAssets().open("app-cover-upload/"+asset),
                    (input,length,filename,active)->receive(attempt,target,input,length,active),AppCoverStore.MAX_UPLOAD,info);
                try{candidate.listen();}catch(IOException occupied){candidate.stop();candidate=new WallpaperUploadServer(selectedHost,0,asset->activity.getAssets().open("app-cover-upload/"+asset),
                    (input,length,filename,active)->receive(attempt,target,input,length,active),AppCoverStore.MAX_UPLOAD,info);candidate.listen();}
                BitMatrix matrix=new QRCodeWriter().encode(candidate.address(),BarcodeFormat.QR_CODE,384,384,Collections.singletonMap(EncodeHintType.MARGIN,1));
                int[] pixels=new int[384*384];for(int y=0;y<384;y++)for(int x=0;x<384;x++)pixels[y*384+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
                Bitmap qr=Bitmap.createBitmap(pixels,384,384,Bitmap.Config.ARGB_8888);WallpaperUploadServer ready=candidate;
                main.post(()->{if(view==null||attempt!=generation){ready.stop();qr.recycle();return;}server=ready;view.connected(ready.shortAddress(),ready.connectionCode());view.qr.setImageBitmap(qr);main.postDelayed(check,2000);});
            }catch(IOException|JSONException|WriterException e){if(candidate!=null)candidate.stop();main.post(()->{if(view!=null&&attempt==generation){view.address.setText("无法开启上传，请检查应用状态与网络后重试");view.retry.setVisibility(View.VISIBLE);}});}
        });
    }
    private String receive(int attempt,String target,InputStream input,long length,WallpaperUploadServer.Active active) throws IOException {
        if(!receiving.compareAndSet(false,true))throw new IOException("已收到封面，请先在电视确认 / Confirm the received cover on TV");
        File incoming=null,ready=null;boolean staged=false;
        try{
            AppCoverPolicy.require(activity,target);incoming=store.incoming();ready=store.prepared();
            try(FileOutputStream output=new FileOutputStream(incoming)){byte[] buffer=new byte[16384];long remaining=length;
                while(remaining>0){if(attempt!=generation||!active.valid())throw new IOException("上传会话已结束 / Session ended");int n=input.read(buffer,0,(int)Math.min(buffer.length,remaining));if(n<0)throw new IOException("图片传输未完成 / Incomplete upload");output.write(buffer,0,n);remaining-=n;}}
            AppCoverImages.prepare(incoming,ready);AppCoverPolicy.require(activity,target);
            if(attempt!=generation||!active.valid())throw new IOException("上传会话已结束 / Session ended");
            File received=ready;pending=received;staged=true;
            main.post(()->{if(view==null||attempt!=generation){AppCoverStore.discard(received);return;}view.status.setText("已收到封面，请在电视确认应用");view.status.setVisibility(View.VISIBLE);confirm(attempt,target,received);});
            return "{\"received\":true}";
        }catch(OutOfMemoryError e){throw new IOException("电视无法处理这张图片，请选择较小图片 / Image too large for this TV");}
        finally{AppCoverStore.discard(incoming);if(!staged){AppCoverStore.discard(ready);receiving.set(false);}}
    }
    private void confirm(int attempt,String target,File ready){
        if(confirmation!=null)return;
        try{preview=AppCoverImages.load(ready,360);}catch(RuntimeException|OutOfMemoryError e){preview=null;}
        if(preview==null){close();activity.message("无法预览封面，请重新上传");return;}
        ManagementUi.Sheet sheet=activity.new Sheet("应用这张封面？");confirmation=sheet;sheet.text(label);activity.gap(sheet.body,12);
        ImageView picture=new ImageView(activity);picture.setScaleType(ImageView.ScaleType.FIT_CENTER);picture.setImageBitmap(preview);sheet.body.addView(picture,new LinearLayout.LayoutParams(-1,activity.px(180)));
        sheet.action("取消",sheet.dialog::dismiss);
        Button apply=sheet.action("应用封面",()->{
            if(committing)return;committing=true;sheet.dialog.setCancelable(false);for(int i=0;i<sheet.actions.getChildCount();i++)sheet.actions.getChildAt(i).setEnabled(false);
            worker.execute(()->{
                String error=null;
                try{if(attempt!=generation)throw new IOException("上传会话已结束");AppCoverPolicy.require(activity,target);store.apply(target,ready);pending=null;}
                catch(IOException e){error=e.getMessage();}
                String failure=error;main.post(()->{committing=false;Runnable done=updated;sheet.dialog.dismiss();close();if(destroyed||activity.isFinishing())return;if(failure==null){if(done!=null)done.run();activity.feedback("已更换应用封面");}else activity.message(failure);});
            });
        });
        sheet.dialog.setOnDismissListener(d->{confirmation=null;picture.setImageDrawable(null);if(preview!=null){preview.recycle();preview=null;}if(!committing){File discarded=pending;pending=null;AppCoverStore.discard(discarded);close();}});
        sheet.show(apply);
    }
    private void stopServer(){++generation;main.removeCallbacks(check);WallpaperUploadServer old=server;server=null;if(old!=null){old.invalidate();worker.execute(old::stop);}}
    void close(){if(view!=null)view.dialog.dismiss();else stopServer();if(committing)return;File discarded=pending;pending=null;AppCoverStore.discard(discarded);}
    void destroy(){destroyed=true;close();worker.shutdown();}
}
