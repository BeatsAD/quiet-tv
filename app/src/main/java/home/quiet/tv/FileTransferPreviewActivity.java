package home.quiet.tv;

import android.app.Activity;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Color;
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
    private void style(Button button){button.setTextColor(ManagementUi.TEXT);button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(14));button.setBackgroundDrawable(ControlStyle.action(unit,24,0xff24262c,0x22ffffff));}
    @Override public boolean dispatchKeyEvent(KeyEvent key){return KeyFeedback.dispatch(getWindow().getDecorView(),key,()->{boolean handled=super.dispatchKeyEvent(key);if(key.getKeyCode()==KeyEvent.KEYCODE_BACK&&isFinishing())KeyFeedback.back(this);return handled;});}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);unit=getResources().getDisplayMetrics().widthPixels/960f;getWindow().getDecorView().setSystemUiVisibility(5894);getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);getWindow().setDimAmount(.65f);
        FrameLayout root=new FrameLayout(this);setContentView(root);WallpaperUi ui=new WallpaperUi(this,unit);
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(px(24),px(22),px(24),px(22));card.setBackgroundDrawable(ui.surface(0xff202126,22,0x38ffffff,1));root.addView(card,new FrameLayout.LayoutParams(px(500),-2,Gravity.CENTER));
        TextView title=ui.text("预览壁纸",22);card.addView(title,new LinearLayout.LayoutParams(-1,px(36)));
        FrameLayout media=new FrameLayout(this);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,px(205));mp.topMargin=px(10);mp.bottomMargin=px(12);card.addView(media,mp);video=new VideoWallpaperPlayer(this,media,true);
        image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);media.addView(image,new FrameLayout.LayoutParams(-1,-1));
        status=ui.text("正在检查并导入壁纸…",13);status.setTextColor(ManagementUi.MUTED);card.addView(status,new LinearLayout.LayoutParams(-1,px(38)));
        LinearLayout buttons=new LinearLayout(this);buttons.setGravity(Gravity.CENTER);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,px(42));bp.topMargin=px(12);card.addView(buttons,bp);Button cancel=new KeyFeedback.Button(this);cancel.setText("返回文件传输");style(cancel);cancel.setOnClickListener(v->{finish();KeyFeedback.returned(this);});buttons.addView(cancel,new LinearLayout.LayoutParams(0,-1,1));
        apply=new KeyFeedback.Button(this);apply.setText("设为壁纸");style(apply);ControlStyle.choice(apply,unit,24,0xff24262c,0x22ffffff);apply.setSelected(true);apply.setEnabled(false);apply.setOnClickListener(v->apply());LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-1,1);ap.leftMargin=px(12);buttons.addView(apply,ap);cancel.requestFocus();
        String id=getIntent().getStringExtra("file");
        worker.execute(()->{
            try{
                FileTransferStore receipts=new FileTransferStore(new File(getFilesDir(),"received-files"));FileTransferStore.Entry receipt=receipts.entry(id);WallpaperStore store=WallpaperStore.get(this);
                String remembered=getSharedPreferences("file-transfer-wallpapers",0).getString(id,"");WallpaperCatalog.Entry result=store.snapshot().find(remembered);
                if(result==null){result=importWallpaper(receipts.file(id),receipt.name);if(!closed)getSharedPreferences("file-transfer-wallpapers",0).edit().putString(id,result.id).apply();}
                final WallpaperCatalog.Entry ready=result;Bitmap photo=ready.video()&&!store.poster(ready.id).isFile()?null:WallpaperImages.decode(this,store,ready,1280,720);
                runOnUiThread(()->{if(closed){if(photo!=null)photo.recycle();return;}entry=ready;status.setText("确认预览后设为壁纸");
                    if(photo!=null){bitmap=photo;image.setImageBitmap(photo);}
                    if(!ready.video())apply.setEnabled(true);
                    else {video.setSound(store.snapshot().sound);video.play(ready,new VideoWallpaperPlayer.Listener(){public void ready(){if(!closed){image.setAlpha(0f);apply.setEnabled(true);status.setText("确认预览后设为动态壁纸");}}public void failed(VideoWallpaperPlayer.Failure reason){if(!closed){image.setAlpha(1f);apply.setEnabled(false);status.setText(reason.message(true));}}public void stopped(){apply.setEnabled(false);}});}
                });
            }catch(IOException|RuntimeException|OutOfMemoryError error){runOnUiThread(()->{if(!closed)status.setText("文件无法用作壁纸，请检查格式、规格或壁纸库剩余空间");});}
        });
    }
    private WallpaperCatalog.Entry importWallpaper(File source,String name)throws IOException{
        boolean movie=FileTransferPolicy.video(name);if(!movie&&!FileTransferPolicy.image(name))throw new IOException("文件类型不支持");
        try(InputStream input=new FileInputStream(source)){return WallpaperImport.accept(this,input,source.length(),name,movie,()->!closed,"source=file_transfer");}
    }
    private void apply(){
        if(entry==null||closed)return;apply.setEnabled(false);worker.execute(()->{try{WallpaperStore.get(this).select(entry.id);runOnUiThread(()->{if(!closed){Toast.makeText(this,"已设为壁纸",Toast.LENGTH_SHORT).show();finish();}});}catch(IOException error){runOnUiThread(()->{if(!closed){status.setText("壁纸保存失败，请重试");apply.setEnabled(true);}});}});
    }
    @Override protected void onResume(){super.onResume();if(video!=null){video.setSound(WallpaperStore.get(this).snapshot().sound);video.setPaused(false);}}
    @Override protected void onPause(){if(video!=null)video.setPaused(true);super.onPause();}
    @Override protected void onDestroy(){closed=true;video.destroy();image.setImageDrawable(null);if(bitmap!=null)bitmap.recycle();worker.shutdown();super.onDestroy();}
}
