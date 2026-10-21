package home.quiet.tv;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.widget.TextView;

/** Framework UI calls whose visual fallback is sufficient on Android 4.4. */
final class UiCompat {
    private UiCompat(){}
    @SuppressWarnings("deprecation")
    static Drawable drawable(Context context,int resource){return Build.VERSION.SDK_INT>=21?context.getDrawable(resource):context.getResources().getDrawable(resource);}
    static void letterSpacing(TextView text,float value){if(Build.VERSION.SDK_INT>=21)text.setLetterSpacing(value);}
    static void clipOutline(View view){if(Build.VERSION.SDK_INT>=21)view.setClipToOutline(true);}
}
