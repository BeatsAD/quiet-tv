package home.quiet.tv;

import android.app.Activity;
import android.os.Bundle;

/** Disabled unless an exact, verified firmware profile is selected. No grants or external targets. */
public final class VendorBootActivity extends Activity {
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        DesktopRoutes routes=new DesktopRoutes(this);routes.reconcileVendor();
        boolean accepted=routes.acceptVendor(getIntent());
        DiagnosticLog.info("home_guard.vendor_entry","accepted="+accepted+" ready="+routes.vendorReady()+" problem="+routes.vendorProblem());
        if(accepted)HomeGuardRecovery.launchVendor(this);
        finish();
    }
}
