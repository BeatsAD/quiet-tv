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
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Path clip=new Path();
    private final RectF rect=new RectF();
    private final int[] location=new int[2],origin=new int[2];
    private Shader tint;

    DockBackdropDrawable(WallpaperController wallpapers,View host,View root,float unit){
        this(wallpapers::drawDockBackground,host,root,unit);
    }
    DockBackdropDrawable(Background background,View host,View root,float unit){
        this.background=background;this.host=host;this.root=root;this.unit=unit;
    }
    @Override protected void onBoundsChange(Rect bounds){
        rect.set(bounds);clip.reset();clip.addRoundRect(rect,22*unit,22*unit,Path.Direction.CW);
        tint=new LinearGradient(0,bounds.top,0,bounds.bottom,new int[]{0x30e6e8ec,0x202c3038},null,Shader.TileMode.CLAMP);
    }
    @Override public void draw(Canvas canvas){
        paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(0x18000000);
        canvas.save();canvas.translate(0,2*unit);canvas.drawRoundRect(rect,22*unit,22*unit,paint);canvas.restore();
        int save=canvas.save();canvas.clipPath(clip);
        host.getLocationOnScreen(location);root.getLocationOnScreen(origin);
        canvas.translate((origin[0]-location[0])/root.getScaleX(),(origin[1]-location[1])/root.getScaleY());
        background.draw(canvas,paint);
        canvas.restoreToCount(save);
        paint.setAlpha(255);paint.setShader(tint);canvas.drawRoundRect(rect,22*unit,22*unit,paint);paint.setShader(null);
        float stroke=Math.max(1,.5f*unit);rect.inset(stroke/2,stroke/2);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setColor(0x66ffffff);
        canvas.drawRoundRect(rect,22*unit-stroke/2,22*unit-stroke/2,paint);
        rect.inset(-stroke/2,-stroke/2);paint.setStyle(Paint.Style.FILL);
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
