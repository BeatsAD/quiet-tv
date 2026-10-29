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
    private final float unit,radius;
    private final int color,border;
    private final boolean choice,list;
    private int alpha=255;
    private ControlStyle(float unit,float radius,int color,int border,boolean choice,boolean list){this.unit=unit;this.radius=radius*unit;this.color=color;this.border=border;this.choice=choice;this.list=list;}
    static Drawable action(float unit,float radius,int color,int border){return new ControlStyle(unit,radius,color,border,false,false);}
    static Drawable listRow(float unit,float radius){return new ControlStyle(unit,radius,0,0,false,true);}
    static void choice(TextView view,float unit,float radius,int color,int border){
        view.setBackgroundDrawable(new ControlStyle(unit,radius,color,border,true,false));
        view.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled,android.R.attr.state_selected},{-android.R.attr.state_enabled},{android.R.attr.state_selected},{android.R.attr.state_focused},{}},new int[]{0xff686d76,0xff777d88,0xff191d23,0xfff5f6f8,0xff9298a4}));
    }
    static void dialog(AlertDialog dialog,float unit){
        KeyFeedback.observeAlert(dialog);
        for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){
            TextView button=dialog.getButton(which);
            if(button!=null){button.setBackgroundDrawable(action(unit,24,0xff242a33,0x38ffffff));button.setTextColor(0xffeff0f4);}
        }
        if(dialog.getListView()!=null){dialog.getListView().setDivider(null);dialog.getListView().setSelector(listRow(unit,12));}
    }
    private boolean state(int attr){for(int value:getState())if(value==attr)return true;return false;}
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] state){invalidateSelf();return true;}
    @Override public void draw(Canvas canvas){
        boolean enabled=state(android.R.attr.state_enabled);
        // HOME supplies activated from its existing directional-navigation model.
        boolean focused=enabled&&(state(android.R.attr.state_focused)||state(android.R.attr.state_activated))&&state(android.R.attr.state_window_focused);
        boolean selected=choice&&state(android.R.attr.state_selected),pressed=enabled&&state(android.R.attr.state_pressed);
        if(list){
            rect.set(getBounds());float stroke=Math.max(1,unit);rect.inset(stroke/2,stroke/2);
            paint.setStyle(Paint.Style.FILL);ink(pressed?0x20ffffff:focused?0x14ffffff:state(android.R.attr.state_selected)?0x08ffffff:Color.TRANSPARENT);
            canvas.drawRoundRect(rect,Math.max(0,radius-stroke/2),Math.max(0,radius-stroke/2),paint);
            if(focused){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);ink(0xfff3f4f7);canvas.drawRoundRect(rect,Math.max(0,radius-stroke/2),Math.max(0,radius-stroke/2),paint);}
            return;
        }
        int fill=selected?0xffe6e8ec:focused&&Color.alpha(color)==0?0xff353a43:color;
        if(pressed)fill=Color.alpha(fill)==0?0xff262b33:Color.argb(Color.alpha(fill),Math.round(Color.red(fill)*.8f),Math.round(Color.green(fill)*.8f),Math.round(Color.blue(fill)*.8f));
        rect.set(getBounds());paint.setStyle(Paint.Style.FILL);
        if(focused){ink(0xff101116);canvas.drawRoundRect(rect,radius,radius,paint);}
        rect.inset(3*unit,3*unit);float inner=Math.max(0,radius-3*unit);
        ink(fill);canvas.drawRoundRect(rect,inner,inner,paint);
        if(border!=0&&!selected){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,unit));ink(border);canvas.drawRoundRect(rect,inner,inner,paint);}
        if(focused){rect.set(getBounds());float stroke=Math.max(1,unit);rect.inset(stroke/2,stroke/2);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);ink(0xfff3f4f7);canvas.drawRoundRect(rect,Math.max(0,radius-stroke/2),Math.max(0,radius-stroke/2),paint);}
    }
    private void ink(int color){paint.setColor(color);paint.setAlpha(Color.alpha(color)*alpha/255);}
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
