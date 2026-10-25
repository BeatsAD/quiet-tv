package home.quiet.tv;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import home.quiet.proxy.ProxyContract;

/** A second pause gate. The helper never launches HomeActivity directly. */
public final class HomeProxyEntryActivity extends Activity {
    @Override public void onCreate(Bundle saved){super.onCreate(saved);forward();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);forward();}
    private void forward(){HomeProxy proxy=new HomeProxy(this);
        try{if(!HomeGuardState.canShow(this)){DiagnosticLog.info("home_proxy.entry","deferred_screen_or_lock");proxy.defer(getIntent());}
            else if(proxy.accept(getIntent())){DiagnosticLog.info("home_proxy.entry","accepted");startActivity(HomeController.launchIntent(this)
                .putExtra(ProxyContract.REQUEST,getIntent().getStringExtra(ProxyContract.REQUEST))
                .putExtra(ProxyContract.EPOCH,getIntent().getLongExtra(ProxyContract.EPOCH,-1)));}
            else{DiagnosticLog.info("home_proxy.entry","rejected_configuration");proxy.pause();proxy.original(this);}}
        catch(ActivityNotFoundException|SecurityException failure){proxy.pause();proxy.original(this);}
        finally{finish();}
    }
}
