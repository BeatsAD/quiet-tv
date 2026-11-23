package home.quiet.tv;

import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.zxing.WriterException;
import java.io.*;
import java.util.concurrent.*;
import org.json.*;

/** One target, one phone-confirmed save. The completed session retains its HTTP receipt. */
final class IconUploadSession {
    private final AppManagementActivity activity;
    private final AppCoverStore store;
    private final String pkg,label,shape,expected;
    private final Runnable updated;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private ManagementUi.Sheet sheet;
    private volatile WallpaperUploadServer server;
    private boolean cancelled,completed;
    private Bitmap qr;
    private QrCodeView picture;
    private TextView connection,status;
    private String host;
    private final Runnable check=new Runnable(){@Override public void run(){
        WallpaperUploadServer current=server;if(current==null)return;
        if(current.expired()||!host.equals(WallpaperUploadPanel.lanAddress(activity))){close();return;}
        main.postDelayed(this,2000);
    }};
    IconUploadSession(AppManagementActivity activity,String pkg,String label,Runnable updated){
        this.activity=activity;this.pkg=pkg;this.label=label;this.shape=new DesktopSettingsStore(activity).iconShape();this.updated=updated;store=AppCoverStore.get(activity);expected=store.key(pkg);
    }
    private String tr(String zh,String en){return activity.tr(zh,en);}
    void show(){
        sheet=activity.new Sheet(tr("扫码上传图标","Upload icon from phone"));sheet.settingsLayout(tr("关闭","Close"),this::close);sheet.contentWidth(420,560);sheet.settingsActions(false);
        sheet.dialog.setOnDismissListener(dialog->close());
        LinearLayout transfer=activity.row();
        picture=new QrCodeView(activity,activity.unit);
        picture.setContentDescription(tr("图标上传二维码","Icon upload QR code"));
        picture.setTag("icon-upload-qr");
        transfer.addView(picture,new LinearLayout.LayoutParams(activity.px(QrCodeView.SIDE),activity.px(QrCodeView.SIDE)));
        LinearLayout info=activity.column();
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-2,1);
        ip.leftMargin=activity.px(24);
        transfer.addView(info,ip);
        TextView target=activity.label(label,16);
        target.setSingleLine(true);
        target.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(target);
        activity.gap(info,8);
        connection=activity.muted("",12);
        connection.setTag("icon-upload-connection");
        info.addView(connection);
        sheet.body.addView(transfer,new LinearLayout.LayoutParams(-1,-2));
        status=activity.label(tr("正在开启上传…","Starting upload…"),12);status.setTag("icon-upload-status");status.setTextColor(UiPalette.SECONDARY);sheet.body.addView(status);
        sheet.show(sheet.box.findViewWithTag("sheet-close"));start();
    }
    private void start(){
        host=WallpaperUploadPanel.lanAddress(activity);
        if(host==null){status.setText(tr("请先让手机与电视连接同一个局域网，再重新打开扫码上传","Connect both devices to the same network, then reopen upload"));return;}
        worker.execute(()->{
            WallpaperUploadServer candidate=null;
            try{
                AppCoverPolicy.require(activity,pkg);
                String info=new JSONObject().put("mode","icon").put("label",label).put("shape",shape).put("maxBytes",AppCoverStore.MAX_UPLOAD).put("applyOnUpload",true).toString();
                WallpaperUploadServer.Importer importer=new WallpaperUploadServer.Importer(){
                    @Override public String accept(InputStream input,long length,String name,WallpaperUploadServer.Active active)throws IOException{throw new IOException(tr("请在图标上传页确认替换","Confirm the replacement on the icon upload page"));}
                    @Override public String acceptImage(InputStream input,long length,String name,String selectedShape,WallpaperUploadServer.Active active)throws IOException{return receive(input,length,selectedShape,active);}
                };
                candidate=new WallpaperUploadServer(host,8765,asset->activity.getAssets().open("app-cover-upload/"+asset),importer,AppCoverStore.MAX_UPLOAD,info,false,8);
                try{candidate.listen();}catch(IOException occupied){candidate.stop();candidate=new WallpaperUploadServer(host,0,asset->activity.getAssets().open("app-cover-upload/"+asset),importer,AppCoverStore.MAX_UPLOAD,info,false,8);candidate.listen();}
                Bitmap image=QrCodeView.encode(candidate.address());
                WallpaperUploadServer ready=candidate;
                synchronized(this){if(cancelled){ready.stop();image.recycle();return;}server=ready;}
                main.post(()->{synchronized(this){if(cancelled){image.recycle();return;}}qr=image;picture.setImageBitmap(image);connection.setText(tr("配对码  ","Pairing code  ")+ready.connectionCode());status.setVisibility(View.GONE);main.postDelayed(check,2000);});
            }catch(IOException|JSONException|WriterException failure){if(candidate!=null)candidate.stop();main.post(()->{if(sheet!=null)status.setText(tr("无法开启上传，请检查网络与应用状态后重新打开","Unable to start upload. Check the network and app, then reopen"));});}
        });
    }
    private String receive(InputStream input,long length,String selectedShape,WallpaperUploadServer.Active active)throws IOException{
        File incoming=null,ready=null;
        try{
            if(!IconLibraryCatalog.contains(IconLibraryCatalog.SHAPES,selectedShape))throw new IOException("图标形状无效 / Invalid icon shape");
            if(!shape.equals(selectedShape))throw new AppCoverPolicy.ShapeChanged();
            AppCoverPolicy.requireDesktopShape(activity,shape);
            synchronized(this){requireActive(active);if(completed)throw new IOException("本次图标已替换，请重新打开扫码上传 / Icon already applied. Open a new session");}
            AppCoverPolicy.require(activity,pkg);incoming=store.incoming();ready=store.prepared();
            try(FileOutputStream output=new FileOutputStream(incoming)){byte[] bytes=new byte[16384];long remaining=length;while(remaining>0){synchronized(this){requireActive(active);}int count=input.read(bytes,0,(int)Math.min(bytes.length,remaining));if(count<0)throw new IOException("图片传输未完成 / Incomplete image");output.write(bytes,0,count);remaining-=count;}}
            AppCoverImages.prepareIcon(incoming,ready,selectedShape);
            String result;
            synchronized(this){
                requireActive(active);
                AppCoverPolicy.require(activity,pkg);
                synchronized(DesktopSettingsStore.MUTATIONS){
                    requireActive(active);
                    AppCoverPolicy.requireDesktopShape(activity,shape);
                    store.applyUpload(pkg,ready,shape,expected);
                    completed=true;
                    result=new JSONObject().put("applied",true).put("package",pkg).put("revision",store.revision()).toString();
                }
            }
            main.post(()->{if(!activity.isFinishing()){PhoneSettingsSession.get(activity).configurationChanged();updated.run();}dismiss();});
            return result;
        }catch(AppCoverPolicy.ShapeChanged conflict){
            throw new IOException(tr("桌面图标形状已变化，请在电视重新打开扫码上传","The desktop icon shape changed. Reopen icon upload on the TV."),conflict);
        }catch(JSONException failure){throw new IOException("无法读取替换结果 / Unable to read saved result",failure);}
        catch(OutOfMemoryError failure){throw new IOException("电视无法处理这张图片，请选择较小图片 / Image too large for this TV",failure);}
        finally{AppCoverStore.discard(incoming);store.discardCandidate(ready);}
    }
    private void requireActive(WallpaperUploadServer.Active active)throws IOException{if(cancelled||!active.valid())throw new IOException("上传会话已结束，请重新扫码 / Upload session ended");}
    private void dismiss(){if(sheet!=null){ManagementUi.Sheet current=sheet;sheet=null;current.dialog.setOnDismissListener(null);current.dialog.dismiss();}if(picture!=null)picture.setImageDrawable(null);if(qr!=null){qr.recycle();qr=null;}}
    void close(){WallpaperUploadServer current;synchronized(this){cancelled=true;current=server;server=null;}main.removeCallbacks(check);dismiss();if(current!=null){current.invalidate();worker.execute(current::stop);}}
    void destroy(){close();worker.shutdown();}
}
