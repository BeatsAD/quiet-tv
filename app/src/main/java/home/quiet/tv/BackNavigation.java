package home.quiet.tv;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;

/** One lifecycle-bound action for both legacy remote Back and current system callbacks. */
final class BackNavigation {
    private BackNavigation(){}
    static void install(ComponentActivity activity,Runnable back){
        activity.getOnBackPressedDispatcher().addCallback(activity,new OnBackPressedCallback(true){
            @Override public void handleOnBackPressed(){back.run();if(!(activity instanceof HomeActivity))KeyFeedback.returned(activity);}
        });
    }
    static void install(android.app.Dialog dialog,Runnable back){
        if(android.os.Build.VERSION.SDK_INT>=33)dialog.setOnShowListener(ignored->Api33.install(dialog,back));
    }
    @android.annotation.TargetApi(33)
    private static final class Api33 {
        static void install(android.app.Dialog dialog,Runnable back){
            dialog.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->{back.run();KeyFeedback.returned(dialog.getContext());});
        }
    }
}
