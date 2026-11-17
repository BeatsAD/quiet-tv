package home.quiet.tv;

import android.animation.*;
import android.graphics.Rect;
import android.os.Build;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.ScrollView;
import java.util.ArrayList;

/** Small content-only motion. Navigation and focus never wait for an animation callback. */
final class UiMotion {
    private UiMotion(){}
    private static final TimeInterpolator FALLBACK=new DecelerateInterpolator(2);
    static TimeInterpolator curve(){return Build.VERSION.SDK_INT>=21?Api21.CURVE:FALLBACK;}
    static boolean enabled(View view){return !SettingsFocusMotion.reduced()&&view.getWindowToken()!=null&&view.isShown();}
    static void settle(View view){
        view.animate().cancel();view.setAlpha(1f);view.setTranslationX(0f);view.setTranslationY(0f);view.setScaleX(1f);view.setScaleY(1f);
    }
    static void panel(View view,float offset){
        settle(view);
        if(!enabled(view))return;
        // Keep content readable from its first frame; the backdrop and header do not move.
        view.setTranslationX(offset);
        view.animate().translationX(0f)
            .setDuration(view.getResources().getInteger(R.integer.quiet_motion_panel_ms))
            .setInterpolator(curve()).start();
    }
    static void scale(View view,float target,long duration){
        view.animate().cancel();
        if(!enabled(view)||!view.hasWindowFocus()){view.setScaleX(target);view.setScaleY(target);return;}
        view.animate().scaleX(target).scaleY(target).setDuration(duration).setInterpolator(curve()).start();
    }
    static void sheet(View view){
        if(view.getAlpha()>=1f)return;
        if(!enabled(view)){settle(view);return;}
        view.animate().cancel();if(view.getAlpha()==0f){view.setScaleX(.985f);view.setScaleY(.985f);}
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(curve()).start();
    }
    static void reveal(View view,float offset){
        if(!enabled(view)){settle(view);return;}
        view.animate().cancel();view.setTranslationY(offset);
        view.animate().alpha(1f).translationY(0f).setDuration(160).setInterpolator(curve()).start();
    }
    /** Measure the final layout once; only visible, disjoint sibling groups move afterwards. */
    static androidx.core.view.OneShotPreDrawListener reflow(View branch,Runnable complete){
        if(!enabled(branch))return null;
        ArrayList<View> siblings=new ArrayList<>();ArrayList<Float> tops=new ArrayList<>();Rect visible=new Rect();View node=branch;
        for(int depth=0;depth<4&&node.getParent() instanceof ViewGroup&&siblings.size()<18;depth++){
            ViewGroup parent=(ViewGroup)node.getParent();if(parent instanceof ScrollView)break;
            for(int i=0;i<parent.getChildCount()&&siblings.size()<18;i++){View child=parent.getChildAt(i);if(child!=node&&child.getVisibility()==View.VISIBLE&&child.getGlobalVisibleRect(visible)){siblings.add(child);tops.add(child.getTop()+child.getTranslationY());}}
            node=parent;
        }
        return androidx.core.view.OneShotPreDrawListener.add(branch,()->{
            complete.run();
            if(!enabled(branch))return;
            for(int i=0;i<siblings.size();i++){View child=siblings.get(i);if(child.getParent()==null||!child.isShown())continue;float offset=tops.get(i)-child.getTop();if(Math.abs(offset)<.5f)continue;child.animate().cancel();child.setTranslationY(offset);child.animate().translationY(0).setDuration(180).setInterpolator(curve()).start();}
        });
    }
    static Exit exit(View view,float distance,Runnable finish){
        if(!enabled(view)||view.getAlpha()<=0){finish.run();return null;}
        return new Exit(view,distance,finish);
    }
    static Exit collapse(View view,Runnable finish){
        if(Build.VERSION.SDK_INT<18||!enabled(view)||view.getAlpha()<=0||!(view.getRootView() instanceof ViewGroup))return null;
        ViewGroup host=(ViewGroup)view.getRootView();Rect clip=new Rect();if(!view.getLocalVisibleRect(clip))return null;Rect bounds=new Rect();view.getDrawingRect(bounds);host.offsetDescendantRectToMyCoords(view,bounds);
        view.clearFocus();view.setEnabled(false);if(view instanceof ViewGroup)((ViewGroup)view).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        view.animate().cancel();view.setTranslationX(0);view.setTranslationY(0);Api18.add(host,view);view.layout(bounds.left,bounds.top,bounds.right,bounds.bottom);Api18.clip(view,clip);
        // Overlay children aren't ordinary focus/navigation children; eligibility was checked before moving.
        return new Exit(view,-6*view.getResources().getDisplayMetrics().widthPixels/960f,()->{Api18.remove(host,view);finish.run();});
    }
    static final class Exit implements View.OnAttachStateChangeListener {
        private final ValueAnimator animation;
        private final Runnable complete;
        private final View view;
        private boolean done;
        Exit(View view,float distance,Runnable complete){
            this.complete=complete;this.view=view;view.addOnAttachStateChangeListener(this);view.animate().cancel();float alpha=view.getAlpha(),top=view.getTranslationY();animation=ValueAnimator.ofFloat(0,1);animation.setDuration(120);animation.setInterpolator(curve());
            animation.addUpdateListener(a->{float f=(float)a.getAnimatedValue();view.setAlpha(alpha*(1-f));view.setTranslationY(top+distance*f);});
            animation.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){finish();}});animation.start();
        }
        void finish(){if(done)return;done=true;view.removeOnAttachStateChangeListener(this);animation.cancel();complete.run();}
        @Override public void onViewAttachedToWindow(View view){}
        @Override public void onViewDetachedFromWindow(View view){finish();}
    }
    @android.annotation.TargetApi(18) private static final class Api18 {static void add(ViewGroup host,View view){host.getOverlay().add(view);}static void remove(ViewGroup host,View view){host.getOverlay().remove(view);}static void clip(View view,Rect clip){view.setClipBounds(clip);}}
    @android.annotation.TargetApi(21) private static final class Api21 {static final TimeInterpolator CURVE=new android.view.animation.PathInterpolator(.2f,.8f,.2f,1);}
}
