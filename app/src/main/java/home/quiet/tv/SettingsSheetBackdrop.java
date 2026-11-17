package home.quiet.tv;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;

/** One bounded settings snapshot supplies the sheet's frosted surface, without another player. */
final class SettingsSheetBackdrop extends Drawable {
    static final float DIM_AMOUNT=0x35/255f;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF area=new RectF();
    private final View panel;
    private Bitmap image;
    private Bitmap shaderImage;
    private BitmapShader imageShader;
    private final Matrix imageMatrix=new Matrix();
    private Shader shade;
    private float shadeBottom;
    private boolean prepared;
    private int generation;
    private final DockBackdropDrawable.Background background;
    private final java.util.concurrent.Executor filter;
    private final float unit,screenWidth,screenHeight;
    private final int[] position=new int[2];
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean followFocus,watchingFocus,windowFocused;
    private final ViewTreeObserver.OnPreDrawListener focus=this::windowFrame;
    // Legacy controls may not invalidate on window focus; keep the first capture pending until it arrives.
    private boolean windowFrame(){
        boolean focused=panel.hasWindowFocus();
        if(focused&&!windowFocused){windowFocused=true;prepareForFocusedWindow(()->{if(panel.hasWindowFocus())panel.setAlpha(1f);});}
        else if(!focused){windowFocused=false;if(image==null)panel.getRootView().postInvalidate();}
        return true;
    }
    SettingsSheetBackdrop(ManagementUi ui,View panel){
        this(panel,ui.unit,(canvas,paint)->ui.drawSheetBackdrop(canvas),ui::filterSheetBackdrop);
        panel.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){@Override public void onViewAttachedToWindow(View view){ui.watchBackdrop(SettingsSheetBackdrop.this,true);}@Override public void onViewDetachedFromWindow(View view){ui.watchBackdrop(SettingsSheetBackdrop.this,false);}});
    }
    SettingsSheetBackdrop(View panel,float unit,DockBackdropDrawable.Background background,java.util.concurrent.Executor filter){
        this.panel=panel;this.unit=unit;this.background=background;this.filter=filter;screenWidth=panel.getResources().getDisplayMetrics().widthPixels;screenHeight=panel.getResources().getDisplayMetrics().heightPixels;
        panel.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            @Override public void onViewAttachedToWindow(View view){if(followFocus)watchFocus();}
            @Override public void onViewDetachedFromWindow(View view){if(watchingFocus&&view.getViewTreeObserver().isAlive())view.getViewTreeObserver().removeOnPreDrawListener(focus);watchingFocus=false;windowFocused=false;release();}
        });
    }
    // Plain dialogs and AlertDialogs keep their layouts/listeners; observe their window through the panel.
    void followWindowFocus(){followFocus=true;if(panel.getWindowToken()!=null)watchFocus();}
    private void watchFocus(){if(watchingFocus)return;watchingFocus=true;windowFocused=false;if(image==null)panel.setAlpha(0f);panel.getViewTreeObserver().addOnPreDrawListener(focus);panel.invalidate();}
    void release(){++generation;shaderImage=null;imageShader=null;paint.setShader(null);if(image!=null){image.recycle();image=null;}prepared=false;}
    void refresh(){prepare(true,null);}
    void prepare(){prepare(false,null);}
    boolean ready(){return prepared||SettingsFocusMotion.reduced();}
    private Bitmap snapshot(){
        int width=320,height=Math.max(1,Math.round(width*screenHeight/screenWidth));
        Bitmap snapshot=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(snapshot);canvas.scale(width/screenWidth,height/screenHeight);background.draw(canvas,paint);
        return snapshot;
    }
    // Capture only after the dialog owns window focus: the source page's white
    // focus row must have disappeared before it becomes part of the glass.
    void prepareForFocusedWindow(Runnable ready){prepare(true,ready);}
    private void prepare(boolean refresh,Runnable complete){
        if(image!=null&&!refresh||SettingsFocusMotion.reduced()){if(complete!=null)complete.run();return;}
        Bitmap captured;
        try{captured=snapshot();}catch(RuntimeException|OutOfMemoryError error){prepared=true;if(complete!=null)complete.run();return;}
        Bitmap snapshot=captured;int version=++generation;
        // Keep the last filtered image visible until its replacement is ready.
        filter.execute(()->{
            Bitmap blurred=null;try{gaussian(snapshot,20f*snapshot.getWidth()/1280f);blurred=snapshot;}catch(RuntimeException|OutOfMemoryError error){snapshot.recycle();DiagnosticLog.info("management.sheet.filter_failed","keep stable surface");}
            Bitmap ready=blurred;main.post(()->{if(version!=generation||panel.getWindowToken()==null){if(ready!=null)ready.recycle();return;}if(ready!=null){Bitmap previous=image;shaderImage=null;imageShader=null;paint.setShader(null);image=ready;if(previous!=null)previous.recycle();}prepared=true;invalidateSelf();if(complete!=null)complete.run();});
        });
    }
    static void gaussian(Bitmap image,float sigma){
        int w=image.getWidth(),h=image.getHeight();int[] pixels=new int[w*h];image.getPixels(pixels,0,w,0,0,w,h);
        // Reuse the existing three-box Gaussian approximation, linear in the pixel count.
        DockBackdropDrawable.blurPixels(pixels,w,h,Math.max(1,Math.round(sigma-.5f)));
        image.setPixels(pixels,0,w,0,0,w,h);
    }
    @Override public void draw(Canvas canvas){
        area.set(getBounds());float radius=SurfaceCorners.PANEL*unit;paint.setShader(null);paint.setColor(Color.BLACK);paint.setShadowLayer(7.68f*unit,0,2.4f*unit,0x21000000);SurfaceCorners.draw(canvas,area,radius,radius,paint);paint.clearShadowLayer();
        if(image!=null){if(shaderImage!=image){shaderImage=image;imageShader=new BitmapShader(image,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);}panel.getLocationOnScreen(position);imageMatrix.setScale(screenWidth/image.getWidth(),screenHeight/image.getHeight());imageMatrix.postTranslate(-position[0],-position[1]);imageShader.setLocalMatrix(imageMatrix);paint.setShader(imageShader);SurfaceCorners.draw(canvas,area,radius,radius,paint);paint.setShader(null);paint.setColor(0x35000000);SurfaceCorners.draw(canvas,area,radius,radius,paint);if(shade==null||shadeBottom!=area.bottom){shadeBottom=area.bottom;shade=new LinearGradient(0,0,0,area.bottom,0x1f000000,0x33000000,Shader.TileMode.CLAMP);}paint.setShader(shade);}
        else paint.setColor(0xe6000000);
        SurfaceCorners.draw(canvas,area,radius,radius,paint);paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.75f*unit);paint.setColor(0x4dffffff);area.inset(.375f*unit,.375f*unit);SurfaceCorners.draw(canvas,area,radius-.375f*unit,radius-.375f*unit,paint);paint.setStyle(Paint.Style.FILL);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
