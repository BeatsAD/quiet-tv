package home.quiet.tv;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Samples only wallpaper pixels, never the Dock's cards or its own surface. */
final class DockBackdropDrawable extends Drawable {
    private static final int CACHE_WIDTH=320;
    interface Background {void draw(Canvas canvas,Paint paint);}
    private final Background background;
    private final View host,root;
    private final float unit;
    private float radius;
    private boolean glass=true;
    private boolean capsule;
    private boolean personalized;
    private final boolean card;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Path clip=new Path();
    private final Path outline=new Path();
    private float outlineWidth;
    private final RectF rect=new RectF();
    private final int[] location=new int[2],origin=new int[2];
    private Shader tint,focusTint;
    private Bitmap capsuleSample;
    private Canvas capsuleCanvas;
    private BitmapShader capsuleShader;
    private final Matrix capsuleMatrix=new Matrix();
    private final Paint samplePaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private boolean focused,selected;

    DockBackdropDrawable(WallpaperController wallpapers,View host,View root,float unit){
        this(wallpapers::drawDockBackground,host,root,unit);
    }
    DockBackdropDrawable(Background background,View host,View root,float unit){
        this(background,host,root,unit,false);
    }
    DockBackdropDrawable(Background background,View host,View root,float unit,boolean card){
        this.background=background;this.host=host;this.root=root;this.unit=unit;this.card=card;radius=(card?SurfaceCorners.CARD:SurfaceCorners.PANEL)*unit;
    }
    DockBackdropDrawable(Background background,View host,View root,float unit,boolean capsule,boolean glass){
        this(background,host,root,unit,false);this.capsule=capsule;this.glass=glass;personalized=true;
    }
    void radiusPixels(float radius){if(this.radius==radius)return;this.radius=radius;onBoundsChange(getBounds());invalidateSelf();}
    @Override protected void onBoundsChange(Rect bounds){
        rect.set(bounds);if(capsule)radius=rect.height()/2;else if(card)radius=SurfaceCorners.artworkRadius(rect.width(),rect.height());SurfaceCorners.path(clip,rect,radius);
        updateOutline();
        tint=new LinearGradient(0,bounds.top,0,bounds.bottom,personalized?new int[]{0x2e000000,0x4d000000}:new int[]{0x30ffffff,0x20000000},null,Shader.TileMode.CLAMP);
        if(capsule)focusTint=SettingsFocusDrawable.focusFill(bounds.top,bounds.bottom);
        if(capsule&&bounds.width()>0&&bounds.height()>0){
            if(capsuleSample==null||capsuleSample.getWidth()!=bounds.width()||capsuleSample.getHeight()!=bounds.height()){
                capsuleSample=Bitmap.createBitmap(bounds.width(),bounds.height(),Bitmap.Config.ARGB_8888);
                capsuleCanvas=new Canvas(capsuleSample);capsuleShader=new BitmapShader(capsuleSample,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
            }
            capsuleMatrix.setTranslate(bounds.left,bounds.top);capsuleShader.setLocalMatrix(capsuleMatrix);
        }
    }
    boolean capsule(){return capsule;}
    private void updateOutline(){
        outlineWidth=card?unit:capsule?(focused?.75f*unit:Math.max(.5f,.5f*unit)):Math.max(1,.5f*unit);
        RectF edge=new RectF(rect);edge.inset(outlineWidth/2,outlineWidth/2);SurfaceCorners.path(outline,edge,radius-outlineWidth/2);
    }
    @Override public boolean isStateful(){return capsule;}
    @Override protected boolean onStateChange(int[] states){
        if(!capsule)return false;boolean enabled=false,window=false,active=false,chosen=false;
        for(int state:states){if(state==android.R.attr.state_enabled)enabled=true;if(state==android.R.attr.state_selected)chosen=true;if(state==android.R.attr.state_window_focused)window=true;if(state==android.R.attr.state_focused||state==android.R.attr.state_activated)active=true;}
        boolean next=enabled&&window&&active,selection=enabled&&chosen;if(next==focused&&selection==selected)return false;focused=next;selected=selection;updateOutline();invalidateSelf();return true;
    }
    @Override public void draw(Canvas canvas){
        if(capsule){drawCapsule(canvas);return;}
        paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(0x18000000);
        if(!card){canvas.save();canvas.translate(0,2*unit);canvas.drawPath(clip,paint);canvas.restore();}
        int save=canvas.save();canvas.clipPath(clip);
        host.getLocationOnScreen(location);root.getLocationOnScreen(origin);
        canvas.translate((origin[0]-location[0])/root.getScaleX(),(origin[1]-location[1])/root.getScaleY());
        if(glass&&!SettingsFocusMotion.reduced())background.draw(canvas,paint);else canvas.drawColor(0xe6000000);
        canvas.restoreToCount(save);
        paint.setAlpha(255);if(card)paint.setColor(0x1fffffff);else paint.setShader(tint);canvas.drawPath(clip,paint);paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(outlineWidth);paint.setColor(card?0x38ffffff:personalized?0x4dffffff:0x66ffffff);
        canvas.drawPath(outline,paint);paint.setStyle(Paint.Style.FILL);
    }
    private void drawCapsule(Canvas canvas){
        if(capsuleSample==null)return;
        // Composite the wallpaper and tint before applying the circular edge once.
        // Re-clipping each opaque background pass leaves a dark anti-alias fringe.
        capsuleCanvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
        int save=capsuleCanvas.save();capsuleCanvas.translate(-rect.left,-rect.top);
        int backgroundSave=capsuleCanvas.save();host.getLocationOnScreen(location);root.getLocationOnScreen(origin);
        capsuleCanvas.translate((origin[0]-location[0])/root.getScaleX(),(origin[1]-location[1])/root.getScaleY());
        samplePaint.setShader(null);samplePaint.setAlpha(255);
        if(glass&&!SettingsFocusMotion.reduced())background.draw(capsuleCanvas,samplePaint);else capsuleCanvas.drawColor(0xe6000000);
        capsuleCanvas.restoreToCount(backgroundSave);
        samplePaint.setAlpha(255);samplePaint.setShader(tint);capsuleCanvas.drawRect(rect,samplePaint);samplePaint.setShader(null);
        if(focused){samplePaint.setShader(focusTint);capsuleCanvas.drawRect(rect,samplePaint);samplePaint.setShader(null);}
        else if(selected){samplePaint.setColor(SettingsFocusDrawable.selectedFill());capsuleCanvas.drawRect(rect,samplePaint);}
        capsuleCanvas.restoreToCount(save);
        paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);paint.setShader(capsuleShader);canvas.drawPath(clip,paint);paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(outlineWidth);paint.setColor(focused?0xb8ffffff:selected?0x66ffffff:0x36ffffff);
        canvas.drawPath(outline,paint);paint.setStyle(Paint.Style.FILL);
    }
    @Override public void setAlpha(int alpha){}
    @Override public void setColorFilter(ColorFilter filter){}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}

    /** Bounded, screen-aligned cache prepared on the wallpaper decoder thread. */
    static Bitmap prepare(Bitmap source,int screenWidth,int screenHeight){
        int width=Math.min(CACHE_WIDTH,screenWidth),height=Math.max(1,Math.round(width*screenHeight/(float)screenWidth));
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        float scale=Math.max(width/(float)source.getWidth(),height/(float)source.getHeight());
        float w=source.getWidth()*scale,h=source.getHeight()*scale;
        new Canvas(bitmap).drawBitmap(source,null,new RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),new Paint(Paint.FILTER_BITMAP_FLAG));
        int[] pixels=new int[width*height];bitmap.getPixels(pixels,0,width,0,0,width,height);
        // Preserve the screen-space blur strength as the cache resolution changes.
        blurPixels(pixels,width,height,Math.max(1,Math.round(width*8f/480)));
        bitmap.setPixels(pixels,0,width,0,0,width,height);return bitmap;
    }

    static void blurPixels(int[] pixels,int width,int height,int radius){
        int[] scratch=new int[pixels.length];
        // Three box passes approximate a Gaussian; edge samples are clamped.
        for(int pass=0;pass<3;pass++){
            blurAxis(pixels,scratch,width,height,radius,true);
            blurAxis(scratch,pixels,width,height,radius,false);
        }
    }
    private static void blurAxis(int[] source,int[] target,int width,int height,int radius,boolean horizontal){
        int length=horizontal?width:height,lines=horizontal?height:width,step=horizontal?1:width,size=radius*2+1;
        for(int line=0;line<lines;line++){
            int base=horizontal?line*width:line,r=0,g=0,b=0;
            for(int k=-radius;k<=radius;k++){int c=source[base+Math.max(0,Math.min(length-1,k))*step];r+=(c>>16)&255;g+=(c>>8)&255;b+=c&255;}
            for(int x=0;x<length;x++){
                target[base+x*step]=0xff000000|((r/size)<<16)|((g/size)<<8)|(b/size);
                int before=source[base+Math.max(0,x-radius)*step],after=source[base+Math.min(length-1,x+radius+1)*step];
                r+=((after>>16)&255)-((before>>16)&255);g+=((after>>8)&255)-((before>>8)&255);b+=(after&255)-(before&255);
            }
        }
    }
}
