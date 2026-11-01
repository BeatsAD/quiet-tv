package home.quiet.tv;

import android.animation.ValueAnimator;
import android.os.Build;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Small content-only motion. Navigation and focus never wait for an animation callback. */
final class UiMotion {
    private UiMotion(){}
    static void settle(View view){
        view.animate().cancel();view.setAlpha(1f);view.setTranslationX(0f);
    }
    static void panel(View view,float offset){
        settle(view);
        if(Build.VERSION.SDK_INT>=26&&!Api26.enabled())return;
        // Keep content readable from its first frame; the backdrop and header do not move.
        view.setAlpha(.94f);view.setTranslationX(offset);
        view.animate().alpha(1f).translationX(0f)
            .setDuration(view.getResources().getInteger(R.integer.quiet_motion_panel_ms))
            .setInterpolator(new DecelerateInterpolator()).start();
    }
    @android.annotation.TargetApi(26) private static final class Api26 {
        static boolean enabled(){return ValueAnimator.areAnimatorsEnabled();}
    }
}
