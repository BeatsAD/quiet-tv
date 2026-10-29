package home.quiet.tv;

import android.app.Activity;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

/** Reuses wallpaper admission and playback; receiving a file never changes the selected wallpaper. */
public final class FileTransferPreviewActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private WallpaperCatalog.Entry entry;
    private VideoWallpaperPlayer video;
    private ImageView image;
    private TextView status;
    private Button apply;
    private Bitmap bitmap;
    private float unit;
    private int px(int value){return Math.round(value*unit);}
    private void style(Button button){button.setTextColor(ManagementUi.TEXT);button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(14));button.setBackgroundDrawable(ControlStyle.action(unit,12,0xff27313b,0xff526777));}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);unit=getResources().getDisplayMetrics().widthPixels/960f;getWindow().getDecorView().setSystemUiVisibility(5894);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff11161d);setContentView(root);
        FrameLayout media=new FrameLayout(this);FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(-1,-1);mp.bottomMargin=px(110);root.addView(media,mp);
        image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);media.addView(image,new FrameLayout.LayoutParams(-1,-1));video=new VideoWallpaperPlayer(this,media);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.VERTICAL);actions.setPadding(px(24),px(8),px(24),px(8));actions.setBackgroundColor(0xff11161d);root.addView(actions,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        status=new TextView(this);status.setTextColor(0xffeff0f4);status.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(14));status.setText("正在检查并导入壁纸…");actions.addView(status);
        LinearLayout buttons=new LinearLayout(this);actions.addView(buttons);Button cancel=new Button(this);cancel.setText("返回文件传输");style(cancel);cancel.setOnClickListener(v->finish());buttons.addView(cancel,new LinearLayout.LayoutParams(0,px(42),1));
        apply=new Button(this);apply.setText("设为壁纸");style(apply);apply.setEnabled(false);apply.setOnClickListener(v->apply());buttons.addView(apply,new LinearLayout.LayoutParams(0,px(42),1));cancel.requestFocus();
        String id=getIntent().getStringExtra("file");
        worker.execute(()->{
            try{
                FileTransferStore receipts=new FileTransferStore(new File(getFilesDir(),"received-files"));FileTransferStore.Entry receipt=receipts.entry(id);WallpaperStore store=WallpaperStore.get(this);
                String remembered=getSharedPreferences("file-transfer-wallpapers",0).getString(id,"");WallpaperCatalog.Entry result=store.snapshot().find(remembered);
                if(result==null){result=importWallpaper(receipts.file(id),receipt.name);if(!closed)getSharedPreferences("file-transfer-wallpapers",0).edit().putString(id,result.id).apply();}
                final WallpaperCatalog.Entry ready=result;Bitmap photo=ready.video()?null:WallpaperImages.decode(this,store,ready,1920,1080);
                runOnUiThread(()->{if(closed){if(photo!=null)photo.recycle();return;}entry=ready;status.setText("确认预览后设为壁纸");
                    if(photo!=null){bitmap=photo;image.setImageBitmap(photo);apply.setEnabled(true);}
                    else {video.setSound(store.snapshot().sound);video.play(ready,new VideoWallpaperPlayer.Listener(){public void ready(){if(!closed){apply.setEnabled(true);status.setText("确认预览后设为动态壁纸");}}public void failed(VideoWallpaperPlayer.Failure reason){if(!closed){apply.setEnabled(false);status.setText(reason.message(true));}}public void stopped(){apply.setEnabled(false);}});}
                });
            }catch(IOException|RuntimeException|OutOfMemoryError error){runOnUiThread(()->{if(!closed)status.setText("文件无法用作壁纸，请检查格式、规格或壁纸库剩余空间");});}
        });
    }
    private WallpaperCatalog.Entry importWallpaper(File source,String name)throws IOException{
        boolean movie=FileTransferPolicy.video(name);if(!movie&&!FileTransferPolicy.image(name))throw new IOException("文件类型不支持");
        try(InputStream input=new FileInputStream(source)){return WallpaperImport.accept(this,input,source.length(),name,movie,()->!closed,"source=file_transfer");}
    }
    private void apply(){
        if(entry==null||closed)return;apply.setEnabled(false);worker.execute(()->{try{WallpaperStore.get(this).select(entry.id);runOnUiThread(()->{if(!closed){startActivity(HomeController.launchIntent(this).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();}});}catch(IOException error){runOnUiThread(()->{if(!closed){status.setText("壁纸保存失败，请重试");apply.setEnabled(true);}});}});
    }
    @Override protected void onResume(){super.onResume();if(video!=null){video.setSound(WallpaperStore.get(this).snapshot().sound);video.setPaused(false);}}
    @Override protected void onPause(){if(video!=null)video.setPaused(true);super.onPause();}
    @Override protected void onDestroy(){closed=true;video.destroy();image.setImageDrawable(null);if(bitmap!=null)bitmap.recycle();worker.shutdown();super.onDestroy();}
}
