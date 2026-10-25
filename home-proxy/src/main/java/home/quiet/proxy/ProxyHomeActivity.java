package home.quiet.proxy;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public final class ProxyHomeActivity extends Activity {
    @Override public void onCreate(Bundle saved){super.onCreate(saved);forward();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);forward();}
    private void forward(){try{new ProxyState(this).home(this);}catch(RuntimeException failure){new ProxyState(this).original(this);}finally{finish();}}
}
