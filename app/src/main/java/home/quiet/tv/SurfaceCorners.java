package home.quiet.tv;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.widget.*;

/** One macOS-inspired continuous curve, with fixed control/card/panel size tiers. */
final class SurfaceCorners {
    static final float CONTROL=6, CARD=12, PANEL=20;
    private static final ThreadLocal<Path> DRAW_PATH=new ThreadLocal<Path>() {
        @Override protected Path initialValue(){return new Path();}
    };
    private SurfaceCorners(){}
    static float radius(RectF bounds,float requested,float unit){
        float half=Math.min(bounds.width(),bounds.height())/2;
        if(requested<=0)return 0;
        // Explicit capsules and circles keep their semicircular ends.
        if(requested>=half*.9f)return half;
        float logical=requested/unit;
        return (logical<=8?CONTROL:logical<=16?CARD:PANEL)*unit;
    }
    static float artworkRadius(float width,float height){return Math.min(width,height)*.22f;}
    static void path(Path path,RectF b,float radius){
        path.reset();if(b.isEmpty())return;
        float half=Math.min(b.width(),b.height())/2;
        if(radius>=half){path.addRoundRect(b,half,half,Path.Direction.CW);return;}
        if(radius<=0){path.addRect(b,Path.Direction.CW);return;}
        // Normalized from macOS SwiftUI's public .continuous path. Three cubics
        // spread the turn across each corner; short sides also shorten the edge handles.
        float extent=1.528665f*radius,l=b.left,t=b.top,r=b.right,d=b.bottom;
        float ex=Math.min(extent,b.width()/2),ey=Math.min(extent,b.height()/2);
        float ax=1.08849f*radius-.24304622f*(extent-ex),ay=1.08849f*radius-.24304622f*(extent-ey);
        float bx=.868407f*radius-.09156464f*(extent-ex),by=.868407f*radius-.09156464f*(extent-ey);
        float c=.631494f*radius,h=.0749114f*radius,u=.372824f*radius,v=.16906f*radius;
        path.moveTo(l+ex,t);path.lineTo(r-ex,t);
        path.cubicTo(r-ax,t,r-bx,t,r-c,t+h);
        path.cubicTo(r-u,t+v,r-v,t+u,r-h,t+c);
        path.cubicTo(r,t+by,r,t+ay,r,t+ey);path.lineTo(r,d-ey);
        path.cubicTo(r,d-ay,r,d-by,r-h,d-c);
        path.cubicTo(r-v,d-u,r-u,d-v,r-c,d-h);
        path.cubicTo(r-bx,d,r-ax,d,r-ex,d);path.lineTo(l+ex,d);
        path.cubicTo(l+ax,d,l+bx,d,l+c,d-h);
        path.cubicTo(l+u,d-v,l+v,d-u,l+h,d-c);
        path.cubicTo(l,d-by,l,d-ay,l,d-ey);path.lineTo(l,t+ey);
        path.cubicTo(l,t+ay,l,t+by,l+h,t+c);
        path.cubicTo(l+v,t+u,l+u,t+v,l+c,t+h);
        path.cubicTo(l+bx,t,l+ax,t,l+ex,t);path.close();
    }
    static void draw(Canvas canvas,RectF bounds,float rx,float ry,Paint paint){
        Path path=DRAW_PATH.get();path(path,bounds,Math.min(rx,ry));canvas.drawPath(path,paint);
    }
    static int clip(Canvas canvas,View view,Path path){
        if(!(view.getBackground() instanceof SurfaceDrawable))return 0;
        SurfaceDrawable surface=(SurfaceDrawable)view.getBackground();
        // View updates background bounds lazily, after this explicit child clip on its first draw.
        surface.setBounds(0,0,view.getWidth(),view.getHeight());surface.path(path);
        int save=canvas.save();canvas.clipPath(path);return save;
    }
    static class Image extends ImageView {
        private final Path clip=new Path();
        Image(Context context){super(context);}
        @Override public void draw(Canvas canvas){int save=clip(canvas,this,clip);super.draw(canvas);if(save!=0)canvas.restoreToCount(save);}
    }
    static class Frame extends FrameLayout {
        private final Path clip=new Path();
        Frame(Context context){super(context);}
        @Override public void draw(Canvas canvas){int save=clip(canvas,this,clip);super.draw(canvas);if(save!=0)canvas.restoreToCount(save);}
    }
    static class Column extends LinearLayout {
        private final Path clip=new Path();
        Column(Context context){super(context);}
        @Override public void draw(Canvas canvas){int save=clip(canvas,this,clip);super.draw(canvas);if(save!=0)canvas.restoreToCount(save);}
    }
}
