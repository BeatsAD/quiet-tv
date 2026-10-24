package com.ccos.tv.homepage;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;

public class LaunchActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BootReceiver.keepEnabled(this);
        // Falls back to the stock Coocaa home if the launcher has been removed or disabled.
        if (!start(BootWatchService.LAUNCHER, "home.quiet.tv.HomeActivity")) {
            start("com.tianci.movieplatform", "com.coocaa.homepage.vast.HomePageActivity");
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BootWatchService.exitProcess();
    }

    private boolean start(String pkg, String cls) {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN)
                    .setClassName(pkg, cls)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
