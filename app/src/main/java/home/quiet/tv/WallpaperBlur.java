package home.quiet.tv;

import android.graphics.*;
import android.os.*;
import android.view.View;
import android.widget.*;
import java.util.concurrent.*;

/** Filters only the wallpaper host; bitmap output keeps two bounded page caches. */
final class WallpaperBlur {
    interface Result {void ready(Bitmap image);}
    interface Snapshot {void capture(int width,int height,Result result);}
    private final FrameLayout host;
    private final Snapshot source;
    private final ImageView overlay,secondOverlay;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Runnable capture=this::capture;
    private int amount,generation,firstAmount,secondAmount,sourceVersion;
    private float progress;
    private boolean active,scheduled,busy,dirty,destroyed,nativeVideo,suspended;
    private long capturedAt=-1000;
    WallpaperBlur(FrameLayout host,Snapshot source){
        this.host=host;this.source=source;overlay=image();secondOverlay=image();
        host.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot){++generation;if(!bitmapOutput())Api31.apply(host,amount);invalidate();}});
    }
    private ImageView image(){
        ImageView image=new ImageView(host.getContext());image.setScaleType(ImageView.ScaleType.FIT_XY);image.setVisibility(View.GONE);image.setFocusable(false);androidx.core.view.ViewCompat.setImportantForAccessibility(image,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);host.addView(image,new FrameLayout.LayoutParams(-1,-1));return image;
    }
    private boolean bitmapOutput(){return Build.VERSION.SDK_INT<31||nativeVideo;}
    void nativeVideo(boolean value){nativeVideo(value,0);}
    void nativeVideo(boolean value,int version){
        if(destroyed||nativeVideo==value&&sourceVersion==version)return;
        nativeVideo=value;sourceVersion=version;++generation;
        if(Build.VERSION.SDK_INT>=31)Api31.apply(host,nativeVideo?0:amount);
        if(!nativeVideo)clear();else renderBitmap();
        invalidate();
    }
    void amount(int value){blend(value,value,0);}
    void blend(int first,int second,float fraction){
        first=Math.max(0,Math.min(100,first));second=Math.max(0,Math.min(100,second));fraction=Math.max(0,Math.min(1,fraction));
        boolean strength=first!=firstAmount||second!=secondAmount,moved=progress!=fraction;
        int next=Math.round(first+(second-first)*fraction);
        if(destroyed||!strength&&!moved&&next==amount)return;
        firstAmount=first;secondAmount=second;progress=fraction;amount=next;
        if(bitmapOutput()){
            if(strength){++generation;}
            if(moved&&nativeVideo)cancelCapture();
            renderBitmap();if(first==0&&second==0){cancelCapture();clear();}else if(strength||nativeVideo)invalidate();
            return;
        }
        ++generation;
        Api31.apply(host,amount);
    }
    private void renderBitmap(){
        boolean same=firstAmount==secondAmount;
        float first=firstAmount==0?0:same?1:secondAmount>0?(progress<1?1:0):1-progress;
        overlay.setAlpha(first);overlay.setVisibility(first>0&&overlay.getDrawable()!=null?View.VISIBLE:View.GONE);
        secondOverlay.setAlpha(progress);secondOverlay.setVisibility(!same&&secondAmount>0&&progress>0&&secondOverlay.getDrawable()!=null?View.VISIBLE:View.GONE);
    }
    // ImageView.setAlpha(int) changes drawable opacity on a 0-255 scale; use View's float overload.
    private void clear(){overlay.setVisibility(View.GONE);overlay.setImageDrawable(null);overlay.setAlpha(1f);secondOverlay.setVisibility(View.GONE);secondOverlay.setImageDrawable(null);}
    void sourceChanged(){++generation;clear();invalidate();}
    String diagnostics(){return "blur="+amount+" blurVisible="+(overlay.getVisibility()==View.VISIBLE)+" blurAlpha="+overlay.getAlpha()+" secondBlurVisible="+(secondOverlay.getVisibility()==View.VISIBLE)+" secondBlurAlpha="+secondOverlay.getAlpha()+" blurBusy="+busy;}
    private void cancelCapture(){main.removeCallbacks(capture);scheduled=false;}
    // Input and page motion must keep refreshing the visible video cache.
    void interaction(){if(nativeVideo){cancelCapture();invalidate();}}
    void suspended(boolean value){if(suspended==value)return;suspended=value;++generation;cancelCapture();if(!value)invalidate();}
    void active(boolean value){active=value;if(!value){++generation;cancelCapture();}else invalidate();}
    private boolean canCapture(){return !destroyed&&active&&!suspended&&bitmapOutput()&&(firstAmount>0||secondAmount>0);}
    void invalidate(){
        if(!canCapture())return;
        dirty=true;if(scheduled||busy)return;scheduled=true;
        int interval=nativeVideo?(amount>0?100:1000):66;
        long delay=Math.max(0,capturedAt+interval-SystemClock.uptimeMillis());
        main.postDelayed(capture,delay);
    }
    private void capture(){
        scheduled=false;if(!canCapture()||host.getWidth()==0||host.getHeight()==0)return;
        final int version=generation,width=Math.min(320,host.getWidth()),height=Math.max(1,Math.round(width*host.getHeight()/(float)host.getWidth()));
        final int first=firstAmount,second=secondAmount;final boolean nativeFrame=nativeVideo;
        busy=true;dirty=false;capturedAt=SystemClock.uptimeMillis();
        try{source.capture(width,height,image->filter(image,version,width,height,first,second,nativeFrame));}
        catch(RuntimeException|OutOfMemoryError e){DiagnosticLog.error("wallpaper.blur.capture_failed",e);completed();}
    }
    private void completed(){busy=false;if(dirty||nativeVideo&&Build.VERSION.SDK_INT>=24)invalidate();}
    private boolean valid(int version,boolean nativeFrame){return canCapture()&&version==generation&&nativeFrame==nativeVideo;}
    private static void blur(Bitmap image,int width,int height,int strength){
        int[] pixels=new int[width*height];image.getPixels(pixels,0,width,0,0,width,height);DockBackdropDrawable.blurPixels(pixels,width,height,Math.max(1,Math.round(strength*width/2000f)));image.setPixels(pixels,0,width,0,0,width,height);
    }
    private void filter(Bitmap image,int version,int width,int height,int first,int second,boolean nativeFrame){
        if(image==null){completed();return;}
        if(!valid(version,nativeFrame)){image.recycle();completed();return;}
        worker.execute(()->{
            Bitmap a=first>0?image:null,b=null;
            try{
                b=second==0?null:first==0?image:second==first?a:image.copy(Bitmap.Config.ARGB_8888,true);
                if(a!=null)blur(a,width,height,first);if(b!=null&&b!=a)blur(b,width,height,second);
            }catch(RuntimeException|OutOfMemoryError e){if(a!=null&&a!=image)a.recycle();if(b!=null&&b!=a&&b!=image)b.recycle();image.recycle();a=null;b=null;DiagnosticLog.error("wallpaper.blur.filter_failed",e);}
            Bitmap ready=a,other=b;main.post(()->{
                if(ready!=null||other!=null){
                    if(valid(version,nativeFrame)){
                        overlay.setImageBitmap(ready);secondOverlay.setImageBitmap(other==ready?null:other);
                        renderBitmap();
                    }else{if(ready!=null)ready.recycle();if(other!=null&&other!=ready)other.recycle();}
                }
                completed();
            });
        });
    }
    void destroy(){destroyed=true;active(false);worker.shutdownNow();clear();if(Build.VERSION.SDK_INT>=31)Api31.apply(host,0);}
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
