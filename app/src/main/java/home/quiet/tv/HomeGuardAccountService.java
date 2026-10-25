package home.quiet.tv;

import android.accounts.*;
import android.app.Service;
import android.content.*;
import android.os.*;

/** A local-only account type. It has no credentials, login UI or remote tokens. */
public final class HomeGuardAccountService extends Service {
    private AbstractAccountAuthenticator authenticator;
    @Override public void onCreate(){super.onCreate();authenticator=new AbstractAccountAuthenticator(this){
        private Bundle unsupported(){Bundle result=new Bundle();result.putInt(AccountManager.KEY_ERROR_CODE,AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION);result.putString(AccountManager.KEY_ERROR_MESSAGE,"此账号仅用于本机桌面恢复");return result;}
        @Override public Bundle editProperties(AccountAuthenticatorResponse r,String type){return unsupported();}
        @Override public Bundle addAccount(AccountAuthenticatorResponse r,String type,String token,String[] features,Bundle options){return unsupported();}
        @Override public Bundle confirmCredentials(AccountAuthenticatorResponse r,Account account,Bundle options){return unsupported();}
        @Override public Bundle getAuthToken(AccountAuthenticatorResponse r,Account account,String token,Bundle options){return unsupported();}
        @Override public String getAuthTokenLabel(String token){return null;}
        @Override public Bundle updateCredentials(AccountAuthenticatorResponse r,Account account,String token,Bundle options){return unsupported();}
        @Override public Bundle hasFeatures(AccountAuthenticatorResponse r,Account account,String[] features){Bundle result=new Bundle();result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT,false);return result;}
    };}
    @Override public IBinder onBind(Intent intent){return authenticator.getIBinder();}
}
