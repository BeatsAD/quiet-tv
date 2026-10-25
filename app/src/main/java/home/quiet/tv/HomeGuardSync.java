package home.quiet.tv;

import android.accounts.*;
import android.app.Service;
import android.content.*;
import android.os.*;

/** Local system-sync binding revives the guard when notification access cannot bind. No data sync. */
public final class HomeGuardSync extends Service {
    private static final String AUTHORITY="settings";
    private static Account account(Context c){return new Account("Quiet TV 桌面恢复",c.getPackageName()+".guard");}
    static boolean ready(Context c){
        if(!new HomeGuardState(c).ready() || !HomeGuardRecovery.writeAllowed(c))return false;
        try{return ContentResolver.getMasterSyncAutomatically() && ContentResolver.getIsSyncable(account(c),AUTHORITY)>0
                && ContentResolver.getSyncAutomatically(account(c),AUTHORITY);}
        catch(SecurityException|IllegalArgumentException failure){return false;}
    }
    static void ensure(Context c){
        if(!new HomeGuardState(c).ready() || !HomeGuardRecovery.writeAllowed(c) || ready(c))return;
        try{
            Account own=account(c);
            if(ContentResolver.getIsSyncable(own,AUTHORITY)<=0){
                AccountManager.get(c).addAccountExplicitly(own,null,null);
                ContentResolver.setIsSyncable(own,AUTHORITY,1);
            }
            if(!ContentResolver.getSyncAutomatically(own,AUTHORITY))ContentResolver.setSyncAutomatically(own,AUTHORITY,true);
            // Respect the system's master switch; do not enable syncing for any other account.
            DiagnosticLog.info("home_guard.sync",ready(c)?"configured":"master_sync_unavailable");
        }catch(SecurityException|IllegalArgumentException failure){DiagnosticLog.info("home_guard.sync","configuration_unavailable");}
    }
    static void pause(Context c){
        if(BuildConfig.LEGACY_19 || !HomeGuardState.unlocked(c))return;
        try{if(ContentResolver.getSyncAutomatically(account(c),AUTHORITY))ContentResolver.setSyncAutomatically(account(c),AUTHORITY,false);}
        catch(SecurityException|IllegalArgumentException ignored){}
    }
    private AbstractThreadedSyncAdapter adapter;
    @Override public IBinder onBind(Intent intent){
        if(adapter==null)adapter=new AbstractThreadedSyncAdapter(getApplicationContext(),false){
            @Override public void onPerformSync(Account own,Bundle extras,String authority,ContentProviderClient provider,SyncResult result){
                Context c=getContext();
                if(!account(c).equals(own) || !AUTHORITY.equals(authority) || !new HomeGuardState(c).ready())return;
                // Never read SettingsProvider or transfer settings/account data.
                new Handler(Looper.getMainLooper()).post(()->{
                    if(!new HomeGuardState(c).ready())return;
                    DiagnosticLog.info("home_guard.sync_callback","guardConnected="+HomeGuardService.connected());
                    if(!HomeGuardService.connected())HomeGuardRecovery.noteRevival(c);
                    HomeGuardRecovery.request(c,"系统同步恢复");
                });
            }
        };
        return adapter.getSyncAdapterBinder();
    }
    public static final class AccountService extends Service {
        private AbstractAccountAuthenticator authenticator;
        @Override public IBinder onBind(Intent intent){
            if(authenticator==null)authenticator=new AbstractAccountAuthenticator(this){
                @Override public Bundle editProperties(AccountAuthenticatorResponse r,String type){return null;}
                @Override public Bundle addAccount(AccountAuthenticatorResponse r,String type,String token,String[] features,Bundle options){return null;}
                @Override public Bundle confirmCredentials(AccountAuthenticatorResponse r,Account account,Bundle options){return null;}
                @Override public Bundle getAuthToken(AccountAuthenticatorResponse r,Account account,String type,Bundle options){return null;}
                @Override public String getAuthTokenLabel(String type){return null;}
                @Override public Bundle updateCredentials(AccountAuthenticatorResponse r,Account account,String type,Bundle options){return null;}
                @Override public Bundle hasFeatures(AccountAuthenticatorResponse r,Account account,String[] features){Bundle b=new Bundle();b.putBoolean(AccountManager.KEY_BOOLEAN_RESULT,false);return b;}
            };
            return authenticator.getIBinder();
        }
    }
}
