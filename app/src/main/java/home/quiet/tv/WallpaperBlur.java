package home.quiet.tv;

import android.graphics.*;
import android.os.*;
import android.view.View;
import android.widget.*;
import java.util.concurrent.*;

/** Filters only the wallpaper host. Older TVs process bounded snapshots off the UI thread. */
final class WallpaperBlur {
    interface Result {void ready(Bitmap image);}
    interface Snapshot {void capture(int width,int height,Result result);}
    private final FrameLayout host;
    private final Snapshot source;
    private final ImageView overlay;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Runnable capture=this::capture;
    private int amount,generation;
    private boolean active,scheduled,busy,dirty,destroyed,nativeVideo;
    private long capturedAt;
    WallpaperBlur(FrameLayout host,Snapshot source){
        this.host=host;this.source=source;
        overlay=new ImageView(host.getContext());overlay.setScaleType(ImageView.ScaleType.FIT_XY);overlay.setVisibility(View.GONE);overlay.setFocusable(false);androidx.core.view.ViewCompat.setImportantForAccessibility(overlay,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);host.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        host.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot){if(!bitmapOutput())Api31.apply(host,amount);invalidate();}});
    }
    private boolean bitmapOutput(){return Build.VERSION.SDK_INT<31||nativeVideo;}
    void nativeVideo(boolean value){
        if(destroyed||nativeVideo==value)return;
        nativeVideo=value;++generation;
        if(Build.VERSION.SDK_INT>=31){Api31.apply(host,nativeVideo?0:amount);if(!nativeVideo){overlay.setVisibility(View.GONE);overlay.setImageDrawable(null);}}
        invalidate();
    }
    void amount(int value){
        int next=Math.max(0,Math.min(100,value));if(destroyed||next==amount)return;amount=next;++generation;
        if(!bitmapOutput()){Api31.apply(host,amount);return;}
        if(amount==0){main.removeCallbacks(capture);scheduled=false;overlay.setVisibility(View.GONE);overlay.setImageDrawable(null);}
        else invalidate();
    }
    void active(boolean value){
        active=value;
        if(!value){++generation;main.removeCallbacks(capture);scheduled=false;}else invalidate();
    }
    void invalidate(){
        if(destroyed||!active||amount==0||!bitmapOutput())return;
        dirty=true;if(scheduled||busy)return;scheduled=true;
        main.postDelayed(capture,Math.max(0,(nativeVideo?100:66)-(SystemClock.uptimeMillis()-capturedAt)));
    }
    private void capture(){
        scheduled=false;if(destroyed||!active||amount==0||!bitmapOutput()||host.getWidth()==0||host.getHeight()==0)return;
        final int version=generation,width=Math.min(320,host.getWidth()),height=Math.max(1,Math.round(width*host.getHeight()/(float)host.getWidth()));
        busy=true;dirty=false;capturedAt=SystemClock.uptimeMillis();final int radius=Math.max(1,Math.round(amount*width/2000f));
        try{source.capture(width,height,image->filter(image,version,width,height,radius));}
        catch(RuntimeException|OutOfMemoryError e){DiagnosticLog.error("wallpaper.blur.capture_failed",e);completed();}
    }
    private void completed(){busy=false;if(dirty||nativeVideo&&Build.VERSION.SDK_INT>=24)invalidate();}
    private void filter(Bitmap image,int version,int width,int height,int radius){
        if(image==null){completed();return;}
        if(destroyed||!active||version!=generation||amount==0||!bitmapOutput()){image.recycle();completed();return;}
        worker.execute(()->{
            Bitmap result=image;
            try{int[] pixels=new int[width*height];image.getPixels(pixels,0,width,0,0,width,height);DockBackdropDrawable.blurPixels(pixels,width,height,radius);image.setPixels(pixels,0,width,0,0,width,height);}
            catch(RuntimeException|OutOfMemoryError e){image.recycle();result=null;DiagnosticLog.error("wallpaper.blur.filter_failed",e);}
            Bitmap ready=result;main.post(()->{
                if(ready!=null){if(!destroyed&&active&&version==generation&&amount>0&&bitmapOutput()){overlay.setImageBitmap(ready);overlay.setVisibility(View.VISIBLE);}else ready.recycle();}
                completed();
            });
        });
    }
    void destroy(){destroyed=true;active(false);worker.shutdownNow();overlay.setImageDrawable(null);if(Build.VERSION.SDK_INT>=31)Api31.apply(host,0);}
    /** Each layer occupies the viewport; View.draw alone does not apply its alpha. */
    static Bitmap snapshot(int width,int height,Bitmap video,View... layers){
        Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.drawColor(Color.BLACK);
        if(video!=null){float scale=Math.max(width/(float)video.getWidth(),height/(float)video.getHeight()),w=video.getWidth()*scale,h=video.getHeight()*scale;canvas.drawBitmap(video,null,new RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),new Paint(Paint.FILTER_BITMAP_FLAG));}
        if(layers.length==0||layers[0].getWidth()==0||layers[0].getHeight()==0)return image;
        canvas.scale(width/(float)layers[0].getWidth(),height/(float)layers[0].getHeight());
        for(View layer:layers){if(layer.getVisibility()!=View.VISIBLE||layer.getAlpha()==0)continue;int save=canvas.saveLayerAlpha(0,0,layer.getWidth(),layer.getHeight(),Math.round(layer.getAlpha()*255),Canvas.ALL_SAVE_FLAG);layer.draw(canvas);canvas.restoreToCount(save);}
        return image;
    }
    @androidx.annotation.RequiresApi(31) private static final class Api31 {
        static void apply(View view,int amount){float radius=amount*.48f*Math.max(1,view.getWidth())/960f;view.setRenderEffect(amount==0?null:RenderEffect.createBlurEffect(radius,radius,Shader.TileMode.CLAMP));}
    }
}
