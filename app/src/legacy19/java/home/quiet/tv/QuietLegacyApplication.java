package home.quiet.tv;

import android.content.Context;

/** Install secondary dex before the shared startup logger on Android 4.x. */
public final class QuietLegacyApplication extends QuietApplication {
    @Override protected void attachBaseContext(Context context){super.attachBaseContext(context);androidx.multidex.MultiDex.install(this);}
}
