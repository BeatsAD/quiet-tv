package home.quiet.proxy;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;

/** A visible exit when both launch targets are unavailable; never recursively sends ACTION_HOME. */
public final class RecoveryActivity extends Activity {
    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(48,48,48,48);
        TextView text=new TextView(this);text.setText("桌面中转已停止\n原桌面暂时无法打开，请在系统设置中恢复默认桌面。");text.setTextSize(22);body.addView(text);
        Button settings=new Button(this);settings.setText("打开系统设置");body.addView(settings);settings.setOnClickListener(v->{
            try{startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));}
            catch(ActivityNotFoundException|SecurityException failure){try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(ActivityNotFoundException|SecurityException ignored){text.setText("系统设置无法打开，请通过已授权的 ADB 恢复原桌面。");}}
        });setContentView(body);settings.requestFocus();
    }
}
