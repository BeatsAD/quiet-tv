package home.quiet.tv;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

/** GradientDrawable-compatible surface; fill, stroke and child clipping share one curve. */
final class SurfaceDrawable extends GradientDrawable {
    private final float unit;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final RectF area=new RectF();
    private final Orientation orientation;
    private int[] colors;
    private Shader gradient;
    private int fill,edge,opacity=255;
    private float radius,stroke;
    private PathEffect dash;
    SurfaceDrawable(float unit){this(unit,Orientation.TOP_BOTTOM,null);}
    SurfaceDrawable(float unit,Orientation orientation,int[] colors){this.unit=unit;this.orientation=orientation;this.colors=colors==null?null:colors.clone();}
    @Override public void setColor(int color){super.setColor(color);fill=color;colors=null;gradient=null;invalidateSelf();}
    @Override public void setCornerRadius(float value){super.setCornerRadius(value);radius=value;invalidateSelf();}
    @Override public void setStroke(int width,int color){setStroke(width,color,0,0);}
    @Override public void setStroke(int width,int color,float dashWidth,float dashGap){super.setStroke(width,color,dashWidth,dashGap);stroke=width;edge=color;dash=dashWidth>0&&dashGap>0?new DashPathEffect(new float[]{dashWidth,dashGap},0):null;invalidateSelf();}
    @Override protected void onBoundsChange(Rect bounds){super.onBoundsChange(bounds);gradient=null;}
    void path(Path target){area.set(getBounds());SurfaceCorners.path(target,area,SurfaceCorners.radius(area,radius,unit));}
    @Override public void draw(Canvas canvas){
        area.set(getBounds());float corner=SurfaceCorners.radius(area,radius,unit);
        if(colors!=null&&gradient==null){
            boolean horizontal=orientation==Orientation.LEFT_RIGHT||orientation==Orientation.TL_BR;
            gradient=new LinearGradient(area.left,area.top,horizontal?area.right:area.left,orientation==Orientation.LEFT_RIGHT?area.top:area.bottom,colors,null,Shader.TileMode.CLAMP);
        }
        paint.setStyle(Paint.Style.FILL);paint.setPathEffect(null);paint.setShader(gradient);paint.setColor(fill);paint.setAlpha(gradient==null?Color.alpha(fill)*opacity/255:opacity);SurfaceCorners.path(path,area,corner);canvas.drawPath(path,paint);
        if(stroke>0&&Color.alpha(edge)>0){area.inset(stroke/2,stroke/2);paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setColor(edge);paint.setAlpha(Color.alpha(edge)*opacity/255);paint.setPathEffect(dash);SurfaceCorners.path(path,area,Math.max(0,corner-stroke/2));canvas.drawPath(path,paint);paint.setPathEffect(null);}
    }
    @Override public void setAlpha(int alpha){opacity=alpha;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    @Override public Drawable.ConstantState getConstantState(){return null;}
}
