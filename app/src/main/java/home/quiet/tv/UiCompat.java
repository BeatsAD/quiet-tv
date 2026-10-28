package home.quiet.tv;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.widget.TextView;

/** Framework UI calls whose visual fallback is sufficient on Android 4.x. */
final class UiCompat {
    private UiCompat(){}
    static boolean lowRam(android.app.ActivityManager memory){return Build.VERSION.SDK_INT>=19 && memory.isLowRamDevice();}
    static boolean keyguardLocked(android.app.KeyguardManager keyguard){return Build.VERSION.SDK_INT>=16?keyguard.isKeyguardLocked():keyguard.inKeyguardRestrictedInputMode();}
    static boolean destroyed(android.app.Activity activity){return Build.VERSION.SDK_INT>=17 && activity.isDestroyed();}
    @SuppressWarnings("deprecation")
    static Drawable drawable(Context context,int resource){return Build.VERSION.SDK_INT>=21?context.getDrawable(resource):context.getResources().getDrawable(resource);}
    static void letterSpacing(TextView text,float value){if(Build.VERSION.SDK_INT>=21)text.setLetterSpacing(value);}
    static void clipOutline(View view){if(Build.VERSION.SDK_INT>=21)view.setClipToOutline(true);}
    static void announce(View view,String text){
        if(Build.VERSION.SDK_INT>=16)view.announceForAccessibility(text);
        else {android.view.accessibility.AccessibilityEvent event=android.view.accessibility.AccessibilityEvent.obtain(android.view.accessibility.AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);event.getText().add(text);event.setPackageName(view.getContext().getPackageName());event.setClassName(view.getClass().getName());view.sendAccessibilityEventUnchecked(event);}
    }
    static Runnable onDraw(View view,Runnable draw){
        if(Build.VERSION.SDK_INT>=16)return Api16.onDraw(view,draw);
        android.view.ViewTreeObserver tree=view.getViewTreeObserver();
        android.view.ViewTreeObserver.OnPreDrawListener listener=()->{draw.run();return true;};
        tree.addOnPreDrawListener(listener);
        return ()->{if(tree.isAlive())tree.removeOnPreDrawListener(listener);};
    }
    @android.annotation.TargetApi(16) private static final class Api16 {
    static Runnable onDraw(View view,Runnable draw){
            android.view.ViewTreeObserver tree=view.getViewTreeObserver();
            android.view.ViewTreeObserver.OnDrawListener listener=draw::run;tree.addOnDrawListener(listener);
            return ()->{if(tree.isAlive())tree.removeOnDrawListener(listener);};
        }
    }
}
