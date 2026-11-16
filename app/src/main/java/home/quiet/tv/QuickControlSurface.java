package home.quiet.tv;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Quick controls keep toggle state independent of the remote focus ring. */
final class QuickControlSurface extends Drawable {
    private final Paint paint=new Paint(3);
    private final float unit;private final int fill;
    private boolean on,focused,externalFocus;
    QuickControlSurface(float unit){this(unit,0x24ffffff);}
    QuickControlSurface(float unit,int fill){this.unit=unit;this.fill=fill;}
    void on(boolean value){on=value;invalidateSelf();}
    void focused(boolean value){externalFocus=true;focused=value;invalidateSelf();}
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] states){if(!externalFocus){boolean focus=false,window=false;for(int state:states){focus|=state==android.R.attr.state_focused;window|=state==android.R.attr.state_window_focused;}focused=focus&&window;}invalidateSelf();return true;}
    @Override public void draw(Canvas canvas){RectF r=new RectF(getBounds());float corner=12*unit;paint.setStyle(Paint.Style.FILL);paint.setColor(on?0xffe2edf8:fill);canvas.drawRoundRect(r,corner,corner,paint);if(focused){r.inset(-3*unit,-3*unit);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*unit);paint.setColor(Color.WHITE);canvas.drawRoundRect(r,corner+3*unit,corner+3*unit,paint);paint.setStyle(Paint.Style.FILL);}}
    @Override public void setAlpha(int alpha){}
    @Override public void setColorFilter(ColorFilter filter){}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
