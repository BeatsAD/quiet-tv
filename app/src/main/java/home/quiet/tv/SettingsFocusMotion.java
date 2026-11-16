package home.quiet.tv;

import android.animation.ValueAnimator;
import android.os.Build;
import android.view.*;
import android.view.animation.DecelerateInterpolator;

/** Focus motion follows settings-focus.css; committed selection never scales a control. */
final class SettingsFocusMotion {
    private SettingsFocusMotion(){}
    static boolean reduced(){return BuildConfig.LEGACY_19||Build.VERSION.SDK_INT>=26&&!Api26.enabled();}
    static void attach(View root){root.getViewTreeObserver().addOnGlobalFocusChangeListener((before,after)->{update(before,false);update(after,true);});}
    static void update(View view,boolean focused){
        if(view==null)return;
        if(view.getBackground() instanceof DockBackdropDrawable){if(((DockBackdropDrawable)view.getBackground()).capsule()){boolean raised=focused&&!reduced();if(Build.VERSION.SDK_INT>=21)Api21.headerShadow(view,raised);scale(view,raised?1.12f:1,focused);}return;}
        if(!(view.getBackground() instanceof SettingsFocusDrawable))return;
        SettingsFocusDrawable background=(SettingsFocusDrawable)view.getBackground();
        if(background.row()&&view instanceof ViewGroup){row((ViewGroup)view,focused);return;}
        float scale=focused&&!reduced()?(view.getTag() instanceof String&&((String)view.getTag()).startsWith("management-tab-")?1.06f:1.02f):1;
        scale(view,scale,focused);
    }
    static void row(ViewGroup row,boolean focused){for(int i=0;i<row.getChildCount();i++)scale(row.getChildAt(i),focused&&!reduced()?1.02f:1,focused);}
    private static void scale(View view,float scale,boolean focused){
        view.animate().cancel();
        if(!focused||reduced()){view.setScaleX(scale);view.setScaleY(scale);return;}
        view.animate().scaleX(scale).scaleY(scale).setDuration(160).setInterpolator(Build.VERSION.SDK_INT>=21?Api21.curve():new DecelerateInterpolator(2)).start();
    }
    @android.annotation.TargetApi(21) private static final class Api21 {
        private static final ViewOutlineProvider HEADER_OUTLINE=new ViewOutlineProvider(){@Override public void getOutline(View view,android.graphics.Outline outline){outline.setOval(0,0,view.getWidth(),view.getHeight());}};
        static void headerShadow(View view,boolean raised){view.setOutlineProvider(HEADER_OUTLINE);view.setElevation(raised?view.getLayoutParams().height*.22f:0);}
        static android.animation.TimeInterpolator curve(){return new android.view.animation.PathInterpolator(.2f,.8f,.2f,1);}
    }
    @android.annotation.TargetApi(26) private static final class Api26 {static boolean enabled(){return ValueAnimator.areAnimatorsEnabled();}}
}
