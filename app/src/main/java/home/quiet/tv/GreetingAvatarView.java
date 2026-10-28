package home.quiet.tv;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.io.File;
import java.util.concurrent.*;

/** Shader circles also work on API19. Immutable filenames avoid decoding on clock ticks. */
final class GreetingAvatarView extends View {
    private static final ExecutorService IMAGES=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"greeting-avatar");t.setDaemon(true);return t;});
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private Bitmap bitmap;
    private File source;
    private String key="";
    private int generation;
    private boolean failed,enabled;
    Runnable changed;
    GreetingAvatarView(Context context){super(context);setFocusable(false);androidx.core.view.ViewCompat.setImportantForAccessibility(this,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void show(File file,boolean show){
        source=file;enabled=show;String next=file==null?"":file.getPath();
        if(!key.equals(next)){
            key=next;failed=false;int attempt=++generation;clear();
            if(file!=null){int size=Math.min(256,Math.max(128,Math.max(getWidth(),getHeight())));IMAGES.execute(()->{
                Bitmap image;try{image=GreetingAvatarStore.load(file,size);}catch(RuntimeException|OutOfMemoryError e){image=null;}
                Bitmap ready=image;post(()->{
                    if(attempt!=generation){if(ready!=null)ready.recycle();return;}
                    bitmap=ready;failed=ready==null;updateShader();setVisibility(enabled&&!failed?VISIBLE:GONE);invalidate();if(changed!=null)changed.run();
                });
            });}
        }
        setVisibility(show&&file!=null&&!failed?VISIBLE:GONE);
    }
    private void clear(){paint.setShader(null);if(bitmap!=null){bitmap.recycle();bitmap=null;}invalidate();}
    private void updateShader(){
        if(bitmap==null)return;BitmapShader shader=new BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);Matrix matrix=new Matrix();
        float scale=Math.max(getWidth()/(float)bitmap.getWidth(),getHeight()/(float)bitmap.getHeight());matrix.setScale(scale,scale);
        matrix.postTranslate((getWidth()-bitmap.getWidth()*scale)/2,(getHeight()-bitmap.getHeight()*scale)/2);shader.setLocalMatrix(matrix);paint.setShader(shader);
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){updateShader();}
    @Override protected void onDraw(Canvas canvas){if(bitmap!=null)canvas.drawCircle(getWidth()/2f,getHeight()/2f,Math.min(getWidth(),getHeight())/2f,paint);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();show(source,enabled);}
    @Override protected void onDetachedFromWindow(){++generation;key="";clear();super.onDetachedFromWindow();}
}
