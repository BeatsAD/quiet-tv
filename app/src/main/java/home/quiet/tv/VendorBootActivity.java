package home.quiet.tv;

import android.app.Activity;
import android.os.Bundle;

/** Enabled by foreground setup on an eligible TV. No grants or external targets. */
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
