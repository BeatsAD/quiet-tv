package home.quiet.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Renders the visible grid plus one predecoded replacement. All bitmap decoding is serial. */
final class WallpaperWallView extends View {
    interface Listener {void ready(boolean available);}
    private static final long FLIP_MS=650L;
    private static final long FLIP_INTERVAL_MS=5000L;
    private final WallpaperStore store;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService decoder=Executors.newSingleThreadExecutor();
    private final Random random=new Random();
    private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect rect=new Rect();
    private final Camera camera=new Camera();
    private final Matrix transform=new Matrix();
    private final Set<String> unreadable=new HashSet<>();
    private final Runnable advance=this::advance;
    private final Listener listener;
    private List<String> ids=Collections.emptyList();
    private WallpaperWall grid;
    private Bitmap[] tiles;
    private Bitmap replacement;
    private ValueAnimator flip;
    private Future<?> task;
    private int generation, cell=-1;
    private long nextFlip;
    private boolean running, loading, destroyed;

    WallpaperWallView(Context context,WallpaperStore store,Listener listener){
        super(context);
        this.store=store;
        this.listener=listener;
        setFocusable(false);
    }

    void show(List<String> images){
        if(!ids.equals(images)){
            unreadable.clear();
            ids=images;
            resetGrid();
        }else if(grid==null)resetGrid();
    }

    void active(boolean active){
        if(running==active)return;
        running=active;
        if(active){
            nextFlip=SystemClock.uptimeMillis()+FLIP_INTERVAL_MS;
            if(grid==null)resetGrid();
            else if(!loading)continueLoading();
        }else{
            main.removeCallbacks(advance);
            stopFlip();
        }
    }

    void clear(){
        active(false);
        ++generation;
        if(task!=null)task.cancel(false);
        grid=null;
        tiles=null;
        replacement=null;
        loading=false;
        unreadable.clear();
        ids=Collections.emptyList();
        invalidate();
    }

    void destroy(){clear();destroyed=true;decoder.shutdownNow();}

    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){if(running)resetGrid();else if(w!=oldW||h!=oldH)grid=null;}
    @Override protected void onDetachedFromWindow(){clear();super.onDetachedFromWindow();}

    private void resetGrid(){
        ++generation;
        if(task!=null)task.cancel(false);
        main.removeCallbacks(advance);
        stopFlip();
        loading=false;
        replacement=null;
        grid=null;
        tiles=null;
        invalidate();
        if(!running||destroyed||getWidth()==0||getHeight()==0)return;
        List<String> available=new ArrayList<>(ids);
        available.removeAll(unreadable);
        grid=new WallpaperWall(available,random);
        tiles=new Bitmap[grid.size()];
        if(tiles.length==0){listener.ready(false);return;}
        decode(grid.at(0),0);
    }

    private void continueLoading(){
        if(grid==null||!running||loading||flip!=null)return;
        for(int i=0;i<tiles.length;i++){
            if(tiles[i]==null){decode(grid.at(i),i);return;}
        }
        if(grid.moves()&&replacement==null)decode(grid.next(),-1);
        else schedule();
    }

    private void decode(String id,int slot){
        loading=true;
        final int request=generation;
        final int displayMax=getWidth()>1920?1920:getWidth();
        final int width=Math.max(1,(displayMax+grid.columns-1)/grid.columns);
        final int height=Math.max(1,(Math.round(getHeight()*(displayMax/(float)getWidth()))+grid.rows-1)/grid.rows);
        task=decoder.submit(()->{
            Bitmap bitmap=null;
            try{
                WallpaperCatalog.Entry entry=store.entry(id);
                if(entry!=null)bitmap=WallpaperImages.decode(getContext(),store,entry,width,height);
            }catch(IOException|RuntimeException|OutOfMemoryError error){
                DiagnosticLog.info("screensaver.wall.decode_failed",id);
            }
            Bitmap decoded=bitmap;
            main.post(()->{
                if(request!=generation||destroyed){if(decoded!=null)decoded.recycle();return;}
                loading=false;
                if(decoded==null){
                    unreadable.add(id);
                    if(slot<0){grid.skip();continueLoading();}
                    else resetGrid();
                    return;
                }
                if(slot<0)replacement=decoded;
                else{
                    tiles[slot]=decoded;
                    if(slot==tiles.length-1){
                        nextFlip=SystemClock.uptimeMillis()+FLIP_INTERVAL_MS;
                        listener.ready(true);
                    }
                    invalidate();
                }
                continueLoading();
            });
        });
    }

    private void schedule(){
        main.removeCallbacks(advance);
        if(running&&grid!=null&&replacement!=null&&flip==null)main.postAtTime(advance,nextFlip);
    }

    private void advance(){
        if(!running||replacement==null)return;
        cell=grid.cell();
        nextFlip=SystemClock.uptimeMillis()+FLIP_INTERVAL_MS;
        flip=ValueAnimator.ofFloat(0f,1f);
        flip.setDuration(FLIP_MS);
        flip.addUpdateListener(animation->{
            float progress=(Float)animation.getAnimatedValue();
            if(progress>=.5f&&replacement!=null)swap();
            invalidate();
            if(progress>=1f){flip=null;cell=-1;continueLoading();}
        });
        flip.start();
    }

    private void swap(){tiles[cell]=replacement;replacement=null;grid.show(cell);}

    private void stopFlip(){
        if(flip==null)return;
        flip.removeAllUpdateListeners();
        flip.cancel();
        flip=null;
        cell=-1;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas){
        if(grid==null||tiles.length==0||tiles[tiles.length-1]==null)return;
        canvas.drawColor(0xff08090b);
        int width=getWidth(),height=getHeight();
        int gap=Math.max(1,Math.round(width*2f/1920f));
        for(int i=0;i<tiles.length;i++){
            int x=i%grid.columns,y=i/grid.columns;
            rect.set(x*width/grid.columns+(x==0?0:gap/2),y*height/grid.rows+(y==0?0:gap/2),
                (x+1)*width/grid.columns-(x==grid.columns-1?0:gap-gap/2),
                (y+1)*height/grid.rows-(y==grid.rows-1?0:gap-gap/2));
            if(i!=cell||flip==null){canvas.drawBitmap(tiles[i],null,rect,paint);continue;}
            float progress=(Float)flip.getAnimatedValue();
            float angle=(progress<.5f?progress:progress-1f)*180f;
            float cx=rect.exactCenterX(),cy=rect.exactCenterY();
            canvas.save();
            canvas.clipRect(rect);
            camera.save();
            camera.setLocation(0,0,-Math.max(8f,rect.width()/50f));
            camera.rotateY(angle);
            camera.getMatrix(transform);
            camera.restore();
            transform.preTranslate(-cx,-cy);
            transform.postTranslate(cx,cy);
            canvas.concat(transform);
            canvas.drawBitmap(tiles[i],null,rect,paint);
            canvas.restore();
        }
    }
}
