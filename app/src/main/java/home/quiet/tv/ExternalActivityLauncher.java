package home.quiet.tv;

import android.content.Context;
import android.content.Intent;

/** One external dispatch; no retries, fallback routes, URI data or extras in diagnostics. */
final class ExternalActivityLauncher {
    static boolean open(Context context,String entry,Intent intent){
        return dispatch("entry="+entry+" "+DiagnosticLog.intent(intent),()->context.startActivity(intent));
    }
    static boolean dispatch(String detail,Runnable launch){
        DiagnosticLog.info("external_activity.request",detail);
        try{launch.run();}
        catch(RuntimeException failure){DiagnosticLog.error("external_activity.failed",detail,failure);return false;}
        DiagnosticLog.info("external_activity.dispatched",detail+" result=dispatched_unverified");
        return true;
    }
}
