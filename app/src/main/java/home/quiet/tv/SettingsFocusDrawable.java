package home.quiet.tv;

import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.animation.ValueAnimator;
import android.os.Build;
import android.view.animation.PathInterpolator;

/** Settings-only lifted surface. HOME keeps its existing artwork and feedback. */
final class SettingsFocusDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF area=new RectF();
    private final float unit,radius;
    private final int fill,border;
    private final boolean row,primary;
    private boolean focused,frosted,focusOutline;
    private float lift;
    private ValueAnimator animator;
    private int alpha=255;
    SettingsFocusDrawable(float unit,float radius,int fill){this(unit,radius,fill,false);}
    SettingsFocusDrawable(float unit,float radius,int fill,boolean row){this(unit,radius,fill,row,0);}
    SettingsFocusDrawable(float unit,float radius,int fill,boolean row,int border){this(unit,radius,fill,row,border,false);}
    SettingsFocusDrawable(float unit,float radius,int fill,boolean row,int border,boolean primary){this.unit=unit;this.radius=radius*unit;this.fill=fill;this.row=row;this.border=border;this.primary=primary;}
    boolean row(){return row;}
    SettingsFocusDrawable outlineOnFocus(){focusOutline=true;invalidateSelf();return this;}
    SettingsFocusDrawable frosted(){frosted=true;invalidateSelf();return this;}
    static Shader focusFill(float top,float bottom){return new LinearGradient(0,top,0,bottom,UiPalette.TEXT,UiPalette.TEXT,Shader.TileMode.CLAMP);}
    static Shader frostedFill(float top,float bottom){return new LinearGradient(0,top,0,bottom,0xadffffff,0x99ffffff,Shader.TileMode.CLAMP);}
    static int selectedFill(){return UiPalette.SELECTED;}
    static ColorStateList textColors(int normal){
        int ink=(Color.alpha(normal)==Color.alpha(UiPalette.SECONDARY)||Color.alpha(normal)==Color.alpha(UiPalette.INK_SECONDARY))?UiPalette.INK_SECONDARY:Color.alpha(normal)==Color.alpha(UiPalette.FAINT)?UiPalette.INK_FAINT:UiPalette.INK;
        return textColors(normal,ink);
    }
    static ColorStateList textColors(int normal,int ink){
        return new ColorStateList(new int[][]{{-android.R.attr.state_enabled},{android.R.attr.state_focused,android.R.attr.state_window_focused},{android.R.attr.state_activated,android.R.attr.state_window_focused},{android.R.attr.state_selected},{}},new int[]{Color.red(normal)==0?UiPalette.INK_FAINT:UiPalette.FAINT,ink,ink,Color.red(normal)==0?normal:UiPalette.TEXT,normal});
    }
    private boolean state(int attr){for(int value:getState())if(value==attr)return true;return false;}
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] state){
        boolean next=state(android.R.attr.state_enabled)&&(state(android.R.attr.state_focused)||state(android.R.attr.state_activated))&&state(android.R.attr.state_window_focused);
        if(next!=focused){focused=next;if(animator!=null)animator.cancel();lift=0;
            if(next&&!SettingsFocusMotion.reduced()){
                animator=ValueAnimator.ofFloat(0,1);animator.setDuration(160);if(Build.VERSION.SDK_INT>=21)animator.setInterpolator(Api21.curve());
                animator.addUpdateListener(a->{lift=(float)a.getAnimatedValue();invalidateSelf();});animator.start();
            }
        }invalidateSelf();return true;
    }
    @Override public void draw(Canvas canvas){
        area.set(getBounds());
        float corner=SurfaceCorners.radius(area,radius,unit);
        if(row){if(focused&&!SettingsFocusMotion.reduced())area.inset(3*unit-(3*unit+Math.min(area.width()*.01f,3*unit))*lift,3*unit-(3*unit+area.height()*.01f)*lift);else area.inset(3*unit,3*unit);}
        paint.setAlpha(alpha);
        // One opaque fill avoids the black antialias fringe of a separate shadow caster.
        if(focused){paint.setShader(frosted?frostedFill(area.top,area.bottom):null);if(!frosted){paint.setColor(UiPalette.TEXT);paint.setAlpha(alpha);if(!SettingsFocusMotion.reduced())paint.setShadowLayer(11.52f*unit,0,6.72f*unit,Color.argb(Math.round(85*lift),0,0,0));}}
        else if(primary){paint.setShader(null);paint.setColor(UiPalette.SELECTED);paint.setAlpha(Color.alpha(UiPalette.SELECTED)*alpha/255);}
        else{paint.setShader(null);paint.setColor(state(android.R.attr.state_selected)?selectedFill():fill);paint.setAlpha(Color.alpha(paint.getColor())*alpha/255);}
        if(row&&!focused&&fill!=0)canvas.drawRect(getBounds(),paint);else SurfaceCorners.draw(canvas,area,corner,corner,paint);paint.clearShadowLayer();paint.setShader(null);
        if(border!=0&&!focused||focused&&(frosted||focusOutline)){int edge=focused?0xb8ffffff:border;float stroke=.75f*unit;area.inset(stroke/2,stroke/2);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setColor(edge);paint.setAlpha(Color.alpha(edge)*alpha/255);SurfaceCorners.draw(canvas,area,Math.max(0,corner-stroke/2),Math.max(0,corner-stroke/2),paint);paint.setStyle(Paint.Style.FILL);}
    }
    @Override public void jumpToCurrentState(){if(animator!=null)animator.cancel();lift=focused?1:0;invalidateSelf();}
    @android.annotation.TargetApi(21) private static final class Api21 {static android.animation.TimeInterpolator curve(){return new PathInterpolator(.2f,.8f,.2f,1);}}
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
