package home.quiet.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Non-interactive dot over the update tab, above the native focus plate. */
final class UpdateBadgeView extends View {
    private static final Paint DOT=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.widget.Button anchor;
    private final float unit;
    private final int[] button=new int[2],overlay=new int[2];
    private boolean available;

    UpdateBadgeView(Context context,android.widget.Button anchor,float unit){
        super(context);this.anchor=anchor;this.unit=unit;setFocusable(false);setClickable(false);
        androidx.core.view.ViewCompat.setImportantForAccessibility(this,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);
        anchor.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->invalidate());
        anchor.getViewTreeObserver().addOnGlobalFocusChangeListener((old,next)->{if(old==anchor||next==anchor)invalidate();});
        anchor.getViewTreeObserver().addOnScrollChangedListener(()->{if(available)invalidate();});
    }
    void available(boolean value,String description){
        if(available==value)return;
        available=value;
        anchor.setContentDescription(value?anchor.getText()+"  "+description:null);
        invalidate();
    }
    private static void dot(Canvas canvas,float x,float y,float radius,boolean lightSurface){
        DOT.setColor(lightSurface?0xffc9253f:0xffff5c6c);
        canvas.drawCircle(x,y,radius,DOT);
    }
    @Override protected void onDraw(Canvas canvas){
        if(!available||!anchor.isShown()||anchor.getWidth()==0)return;
        anchor.getLocationInWindow(button);getLocationInWindow(overlay);
        dot(canvas,button[0]-overlay[0]+anchor.getWidth()-anchor.getPaddingRight()*.68f+1.5f*unit,
                button[1]-overlay[1]+anchor.getHeight()*.35f,3.75f*unit,anchor.isFocused()&&anchor.hasWindowFocus());
    }
}
