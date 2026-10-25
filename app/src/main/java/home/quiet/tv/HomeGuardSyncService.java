package home.quiet.tv;

import android.accounts.Account;
import android.app.Service;
import android.content.*;
import android.os.*;

/** The framework calls this after settings changes. No provider data is queried or uploaded. */
public final class HomeGuardSyncService extends Service {
    private AbstractThreadedSyncAdapter adapter;
    @Override public void onCreate(){super.onCreate();adapter=new AbstractThreadedSyncAdapter(getApplicationContext(),false){
        @Override public void onPerformSync(Account account,Bundle extras,String authority,ContentProviderClient provider,SyncResult result){HomeGuardSync.perform(getContext(),account,authority);}
    };}
    @Override public IBinder onBind(Intent intent){return adapter.getSyncAdapterBinder();}
}
