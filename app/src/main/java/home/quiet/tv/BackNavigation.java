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
}
