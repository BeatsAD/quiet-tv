package home.quiet.tv;

import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.widget.TextView;
import android.app.AlertDialog;

/** Shared remote feedback. Only choices use selected state; actions never latch. */
final class ControlStyle extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect=new RectF();
    private final float unit,radius,verticalInset;
    private final int color,border;
    private final boolean choice,list;
    private int alpha=255;
    private ControlStyle(float unit,float radius,int color,int border,boolean choice,boolean list){this(unit,radius,color,border,choice,list,3);}
    private ControlStyle(float unit,float radius,int color,int border,boolean choice,boolean list,float verticalInset){this.unit=unit;this.radius=radius*unit;this.verticalInset=verticalInset*unit;this.color=color;this.border=border;this.choice=choice;this.list=list;}
    static Drawable action(float unit,float radius,int color,int border){return new ControlStyle(unit,radius,color,border,false,false);}
    static Drawable action(float unit,float radius,int color,int border,float verticalInset){return new ControlStyle(unit,radius,color,border,false,false,verticalInset);}
    static Drawable listRow(float unit,float radius){return new ControlStyle(unit,radius,0,0,false,true);}
    static void choice(TextView view,float unit,float radius,int color,int border){
        view.setBackgroundDrawable(new ControlStyle(unit,radius,color,border,true,false));
        view.setTextColor(SettingsFocusDrawable.textColors(UiPalette.SECONDARY,UiPalette.INK));
    }
    static void dialog(AlertDialog dialog,float unit){
        KeyFeedback.observeAlert(dialog);
        android.graphics.drawable.GradientDrawable panel=new SurfaceDrawable(unit);
        panel.setColor(0xe6000000);panel.setCornerRadius(12*unit);panel.setStroke(Math.max(1,Math.round(unit)),UiPalette.EDGE);
        dialog.getWindow().setBackgroundDrawable(panel);
        alertText(dialog.getWindow().getDecorView());
        for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){
            TextView button=dialog.getButton(which);
            if(button!=null){button.setBackgroundDrawable(new SettingsFocusDrawable(unit,24,UiPalette.CONTROL,false,UiPalette.EDGE));button.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));}
        }
        if(dialog.getListView()!=null){dialog.getListView().setDivider(null);dialog.getListView().setSelector(listRow(unit,12));}
    }
    private static void alertText(android.view.View view){
        if(view instanceof TextView){int normal=view.getId()==android.R.id.message?UiPalette.SECONDARY:UiPalette.TEXT;((TextView)view).setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled},{}},new int[]{UiPalette.FAINT,normal}));}
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)alertText(group.getChildAt(i));}
    }
    private boolean state(int attr){for(int value:getState())if(value==attr)return true;return false;}
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] state){invalidateSelf();return true;}
    @Override public void draw(Canvas canvas){
        rect.set(getBounds());float radius=SurfaceCorners.radius(rect,this.radius,unit);
        boolean enabled=state(android.R.attr.state_enabled);
        // HOME supplies activated from its existing directional-navigation model.
        boolean focused=enabled&&(state(android.R.attr.state_focused)||state(android.R.attr.state_activated))&&state(android.R.attr.state_window_focused);
        boolean selected=choice&&state(android.R.attr.state_selected),pressed=enabled&&state(android.R.attr.state_pressed);
        if(list){
            rect.set(getBounds());float stroke=Math.max(1,unit);rect.inset(stroke/2,stroke/2);
            paint.setStyle(Paint.Style.FILL);ink(pressed?0x20ffffff:focused?0x14ffffff:state(android.R.attr.state_selected)?UiPalette.CONTROL:Color.TRANSPARENT);
            SurfaceCorners.draw(canvas,rect,Math.max(0,radius-stroke/2),Math.max(0,radius-stroke/2),paint);
            if(focused){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);ink(UiPalette.TEXT);SurfaceCorners.draw(canvas,rect,Math.max(0,radius-stroke/2),Math.max(0,radius-stroke/2),paint);}
            return;
        }
        int fill=choice?(focused?UiPalette.TEXT:selected?UiPalette.SELECTED:color):selected?UiPalette.TEXT:focused&&Color.alpha(color)==0?0x1affffff:color;
        if(pressed)fill=Color.alpha(fill)==0?0xff000000:Color.argb(Math.round(Color.alpha(fill)*.8f),Color.red(fill),Color.green(fill),Color.blue(fill));
        rect.set(getBounds());paint.setStyle(Paint.Style.FILL);
        if(focused){ink(0xff000000);SurfaceCorners.draw(canvas,rect,radius,radius,paint);}
        rect.inset(3*unit,verticalInset);float inner=Math.max(0,radius-3*unit);
        ink(fill);SurfaceCorners.draw(canvas,rect,inner,inner,paint);
        if(border!=0&&!selected){float stroke=Math.max(1,unit);if(verticalInset==0){rect.inset(0,stroke/2);inner=Math.max(0,inner-stroke/2);}paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);ink(border);SurfaceCorners.draw(canvas,rect,inner,inner,paint);}
        if(focused){rect.set(getBounds());float stroke=Math.max(1,unit);rect.inset(stroke/2,stroke/2);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);ink(UiPalette.TEXT);SurfaceCorners.draw(canvas,rect,Math.max(0,radius-stroke/2),Math.max(0,radius-stroke/2),paint);}
    }
    private void ink(int color){paint.setColor(color);paint.setAlpha(Color.alpha(color)*alpha/255);}
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
