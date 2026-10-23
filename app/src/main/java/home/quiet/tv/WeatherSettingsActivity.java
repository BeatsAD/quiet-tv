package home.quiet.tv;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.ComponentActivity;

/** Compatibility entry: weather now lives in the desktop settings shell. */
public final class WeatherSettingsActivity extends ComponentActivity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        startActivity(new Intent(this,DesktopSettingsActivity.class)
            .putExtra(DesktopSettingsActivity.SECTION,"weather")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
}
