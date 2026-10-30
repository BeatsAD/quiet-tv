package home.quiet.tv;

import android.animation.ValueAnimator;
import android.graphics.*;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

/** Brightness and reveal opacity belong to the wallpaper, never to foreground controls. */
final class WallpaperAppearance {
    private final FrameLayout host;
    private final View dim;
    private final Runnable changed;
    private int brightness=100;
    private float opacity=1;
    private ValueAnimator reveal;
    WallpaperAppearance(FrameLayout host,Runnable changed){
        this.host=host;this.changed=changed;dim=new View(host.getContext());dim.setBackgroundColor(Color.BLACK);dim.setVisibility(View.GONE);dim.setFocusable(false);androidx.core.view.ViewCompat.setImportantForAccessibility(dim,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);host.addView(dim,new FrameLayout.LayoutParams(-1,-1));
    }
    void brightness(int value){int next=Math.max(20,Math.min(100,value));if(next==brightness)return;brightness=next;apply();}
    // An ordinary overlay covers both native SurfaceView and composited wallpaper pixels.
    private void apply(){float alpha=1-brightness/100f*opacity;dim.setAlpha(alpha);dim.setVisibility(alpha==0?View.GONE:View.VISIBLE);changed.run();}
    void fadeIn(int millis){
        finish();if(millis<=0)return;
        opacity=0;apply();reveal=ValueAnimator.ofFloat(0,1);reveal.setDuration(millis);reveal.setInterpolator(new LinearInterpolator());
        reveal.addUpdateListener(animation->{opacity=(float)animation.getAnimatedValue();apply();});reveal.start();
    }
    void finish(){if(reveal!=null){reveal.cancel();reveal.removeAllUpdateListeners();reveal=null;}opacity=1;host.setAlpha(1);apply();}
    void drawShade(Canvas canvas){int alpha=255-Math.round(brightness*2.55f*opacity);if(alpha>0)canvas.drawColor(Color.argb(alpha,0,0,0));}
}
