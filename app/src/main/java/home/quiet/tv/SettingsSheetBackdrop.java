package home.quiet.tv;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;

/** One bounded settings snapshot supplies the sheet's frosted surface, without another player. */
final class SettingsSheetBackdrop extends Drawable implements GlassStyle.Listener {
    static final float DIM_AMOUNT=0x35/255f;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF area=new RectF();
    private final View panel;
    private Bitmap image;
    private Bitmap shaderImage;
    private BitmapShader imageShader;
    private final Matrix imageMatrix=new Matrix();
    private Shader shade;
    private float shadeBottom,cornerRadius=SurfaceCorners.PANEL;
    private boolean prepared,light,capsule,translucentFallback,coolTint;
    private Runnable firstFrameReady;
    private volatile int generation;
    private final GlassStyle glassStyle;
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
        this.glassStyle=GlassStyle.get(panel.getContext());glassStyle.observe(this);this.panel=panel;this.unit=unit;this.background=background;this.filter=filter;screenWidth=panel.getResources().getDisplayMetrics().widthPixels;screenHeight=panel.getResources().getDisplayMetrics().heightPixels;
        panel.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            @Override public void onViewAttachedToWindow(View view){if(followFocus)watchFocus();}
            @Override public void onViewDetachedFromWindow(View view){if(watchingFocus&&view.getViewTreeObserver().isAlive())view.getViewTreeObserver().removeOnPreDrawListener(focus);watchingFocus=false;windowFocused=false;release();}
        });
    }
    // Plain dialogs and AlertDialogs keep their layouts/listeners; observe their window through the panel.
    void followWindowFocus(){followFocus=true;if(panel.getWindowToken()!=null)watchFocus();}
    private void watchFocus(){if(watchingFocus)return;watchingFocus=true;windowFocused=false;if(image==null&&glassStyle.rendering())panel.setAlpha(0f);panel.getViewTreeObserver().addOnPreDrawListener(focus);panel.invalidate();}
    void release(){++generation;firstFrameReady=null;shaderImage=null;imageShader=null;paint.setShader(null);if(image!=null){image.recycle();image=null;}prepared=false;}
    @Override public void glassChanged(){
        Runnable pending=firstFrameReady;
        release();
        shade=null;
        if(!translucentFallback)panel.setAlpha(1f);
        if(panel.getWindowToken()!=null){
            if(pending!=null)prepareFirstFrame(pending);
            else if(glassStyle.rendering()&&panel.isShown()&&panel.hasWindowFocus()
                    &&(!translucentFallback||panel.getAlpha()>0f))prepare(true,null);
        }
        invalidateSelf();
    }
    void refresh(){prepare(true,null);}
    void light(){light=true;shade=null;invalidateSelf();}
    void coolTint(boolean enabled){if(coolTint!=enabled){coolTint=enabled;shade=null;invalidateSelf();}}
    SettingsSheetBackdrop cornerRadius(float radius){cornerRadius=radius;invalidateSelf();return this;}
    SettingsSheetBackdrop capsule(){capsule=true;return this;}
    SettingsSheetBackdrop translucentFallback(){translucentFallback=true;return this;}
    void prepare(){prepare(false,null);}
    void prepareFirstFrame(Runnable complete){
        firstFrameReady=complete;
        prepare(false,()->{firstFrameReady=null;complete.run();});
    }
    boolean ready(){return prepared||!glassStyle.rendering();}
    private Bitmap snapshot(){
        int width=320,height=Math.max(1,Math.round(width*screenHeight/screenWidth));
        Bitmap snapshot=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        try{
            Canvas canvas=new Canvas(snapshot);
            canvas.scale(width/screenWidth,height/screenHeight);
            background.draw(canvas,paint);
            return snapshot;
        }catch(RuntimeException|OutOfMemoryError error){
            snapshot.recycle();
            throw error;
        }
    }
    // Capture only after the dialog owns window focus: the source page's white
    // focus row must have disappeared before it becomes part of the glass.
    void prepareForFocusedWindow(Runnable ready){prepare(true,ready);}
    private void prepare(boolean refresh,Runnable complete){
        if(!glassStyle.rendering()){release();prepared=true;if(!translucentFallback)panel.setAlpha(1f);if(complete!=null)complete.run();return;}
        if(image!=null&&!refresh){if(complete!=null)complete.run();return;}
        Bitmap captured;
        try{captured=snapshot();}catch(RuntimeException|OutOfMemoryError error){prepared=true;if(complete!=null)complete.run();return;}
        Bitmap snapshot=captured;int version=++generation,styleRevision=glassStyle.revision();
        filter.execute(()->{
            if(version!=generation){snapshot.recycle();return;}
            Bitmap blurred=null;try{if(gaussian(snapshot,20f*snapshot.getWidth()/1280f,glassStyle,styleRevision))blurred=snapshot;else snapshot.recycle();}catch(RuntimeException|OutOfMemoryError error){snapshot.recycle();DiagnosticLog.info("management.sheet.filter_failed","keep stable surface");}
            Bitmap ready=blurred;
            main.post(()->{
                if(version!=generation||panel.getWindowToken()==null){if(ready!=null)ready.recycle();return;}
                boolean valid=glassStyle.valid(styleRevision);
                if(ready!=null){
                    if(valid){
                        Bitmap previous=image;shaderImage=null;imageShader=null;paint.setShader(null);image=ready;
                        if(previous!=null)previous.recycle();
                    }else ready.recycle();
                }
                if(!valid&&!translucentFallback)return;
                prepared=true;
                invalidateSelf();
                if(complete!=null)complete.run();
            });
        });
    }
    static void gaussian(Bitmap image,float sigma){gaussian(image,sigma,null,0);}
    static boolean gaussian(Bitmap image,float sigma,GlassStyle glass,int revision){
        if(glass!=null&&!glass.valid(revision))return false;
        int w=image.getWidth(),h=image.getHeight();int[] pixels=new int[w*h];image.getPixels(pixels,0,w,0,0,w,h);
        if(!DockBackdropDrawable.blurPixels(pixels,w,h,Math.max(1,Math.round(sigma-.5f)),glass,revision))return false;
        image.setPixels(pixels,0,w,0,0,w,h);return true;
    }
    @Override public void draw(Canvas canvas){
        if(!glassStyle.rendering()&&image!=null)release();
        area.set(getBounds());float radius=capsule?area.height()/2:(light?18:cornerRadius)*unit;paint.setShader(null);
        if(image!=null||!translucentFallback){
            paint.setColor(light?0xffcdd4e2:Color.BLACK);
            if(glassStyle.rendering()&&!capsule)paint.setShadowLayer(7.68f*unit,0,2.4f*unit,0x21000000);
            SurfaceCorners.draw(canvas,area,radius,radius,paint);
            paint.clearShadowLayer();
        }
        if(image!=null){if(shaderImage!=image){shaderImage=image;imageShader=new BitmapShader(image,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);}panel.getLocationOnScreen(position);imageMatrix.setScale(screenWidth/image.getWidth(),screenHeight/image.getHeight());imageMatrix.postTranslate(-position[0],-position[1]);imageShader.setLocalMatrix(imageMatrix);paint.setShader(imageShader);SurfaceCorners.draw(canvas,area,radius,radius,paint);paint.setShader(null);paint.setColor(light?0xf2d4dae8:0x35000000);SurfaceCorners.draw(canvas,area,radius,radius,paint);if(shade==null||shadeBottom!=area.bottom){
            shadeBottom=area.bottom;
            int top=light?0x0cffffff:coolTint?0x80213d59:0x1f000000;
            int bottom=light?0x15001058:coolTint?0x84101b2a:0x33000000;
            shade=new LinearGradient(0,0,0,area.bottom,top,bottom,Shader.TileMode.CLAMP);
        }paint.setShader(shade);}
        else paint.setColor(light?0xffcdd4e2:translucentFallback?0xcc202124:0xff202124);
        if(capsule){paint.setShader(null);paint.setColor(image==null?0xff454641:0x88545550);SurfaceCorners.draw(canvas,area,radius,radius,paint);return;}
        SurfaceCorners.draw(canvas,area,radius,radius,paint);paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.75f*unit);paint.setColor(light?0xe6ffffff:0x4dffffff);area.inset(.375f*unit,.375f*unit);SurfaceCorners.draw(canvas,area,radius-.375f*unit,radius-.375f*unit,paint);paint.setStyle(Paint.Style.FILL);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
