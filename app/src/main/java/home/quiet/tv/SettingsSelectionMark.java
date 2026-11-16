package home.quiet.tv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ReplacementSpan;

/** Shared selection artwork for centered text options and larger option surfaces. */
final class SettingsSelectionMark extends Drawable {
    private final QuickControlIcon icon=new QuickControlIcon("check");
    private final float unit;
    private final boolean lightSurface,top,selectedInk;
    private boolean selected,dark,enabled;
    private int alpha=255;
    SettingsSelectionMark(float unit,boolean lightSurface,boolean top){this(unit,lightSurface,top,false);}
    SettingsSelectionMark(float unit,boolean lightSurface,boolean top,boolean selectedInk){this.unit=unit;this.lightSurface=lightSurface;this.top=top;this.selectedInk=selectedInk;}
    static CharSequence label(String text,boolean selected,float unit){
        if(!selected)return text;
        SpannableString label=new SpannableString(text+"\ufffc");
        QuickControlIcon mark=new QuickControlIcon("check");int size=Math.round(10.56f*unit),gap=Math.round(4.8f*unit);mark.setBounds(0,0,size,size);
        label.setSpan(new ReplacementSpan(){
            @Override public int getSize(Paint paint,CharSequence text,int start,int end,Paint.FontMetricsInt metrics){return gap+size;}
            @Override public void draw(Canvas canvas,CharSequence text,int start,int end,float x,int top,int baseline,int bottom,Paint paint){
                Paint.FontMetricsInt metrics=paint.getFontMetricsInt();mark.color(paint.getColor());int save=canvas.save();
                canvas.translate(x+gap,baseline+(metrics.ascent+metrics.descent-size)/2f);mark.draw(canvas);canvas.restoreToCount(save);
            }
        },text.length(),label.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return label;
    }
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] states){
        boolean focus=false,window=false;selected=false;enabled=false;
        for(int state:states){if(state==android.R.attr.state_enabled)enabled=true;if(state==android.R.attr.state_selected)selected=true;if(state==android.R.attr.state_focused)focus=true;if(state==android.R.attr.state_window_focused)window=true;}
        dark=focus&&window;updateColor();invalidateSelf();return true;
    }
    private void updateColor(){int base=!enabled?UiPalette.FAINT:dark||selectedInk&&selected?UiPalette.INK:UiPalette.TEXT;icon.color((base&0x00ffffff)|((android.graphics.Color.alpha(base)*alpha/255)<<24));}
    @Override protected void onBoundsChange(Rect bounds){
        float size=(top?10.56f:16)*unit,right=bounds.right-(top?8.64f:9.6f)*unit,y=top?bounds.top+4.8f*unit:bounds.exactCenterY()-size/2;
        icon.setBounds(Math.round(right-size),Math.round(y),Math.round(right),Math.round(y+size));
    }
    @Override public void draw(android.graphics.Canvas canvas){if(selected)icon.draw(canvas);}
    @Override public void setAlpha(int value){alpha=value;updateColor();invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){icon.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
