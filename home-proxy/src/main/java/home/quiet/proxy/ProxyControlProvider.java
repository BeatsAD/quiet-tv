package home.quiet.proxy;

import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;

public final class ProxyControlProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public Bundle call(String method,String arg,Bundle data){
        Context c=getContext();c.enforceCallingPermission(BuildConfig.TARGET_PACKAGE+".permission.HOME_PROXY","Signed HOME controller required");
        if(c.getPackageManager().checkSignatures(Binder.getCallingUid(),android.os.Process.myUid())!=PackageManager.SIGNATURE_MATCH)throw new SecurityException("Signature mismatch");
        synchronized(ProxyState.LOCK){
            ProxyState state=new ProxyState(c);
            if(ProxyContract.STATUS.equals(method))return state.status();
            if(data==null)throw new IllegalArgumentException("Missing configuration");
            long epoch=data.getLong(ProxyContract.EPOCH,-1),saved=state.prefs.getLong(ProxyContract.EPOCH,0);
            if(ProxyContract.ACK.equals(method) || ProxyContract.DEFER.equals(method)){
                String token=data.getString(ProxyContract.TOKEN,"");
                boolean visible=ProxyContract.ACK.equals(method);
                if(epoch!=saved || token.isEmpty() || !token.equals(state.prefs.getString("pending","")) || visible && !state.valid()
                        || !ProxyContract.TARGET.equals(state.prefs.getString("mode","")))throw new IllegalStateException("Stale visibility callback");
                SharedPreferences.Editor edit=state.prefs.edit().remove("pending");if(visible)edit.putLong("visibleAt",System.currentTimeMillis());
                if(!edit.commit())throw new IllegalStateException("Ack not saved");
            }else{
                if(!ProxyPolicy.command(method,epoch,saved))throw new IllegalStateException("Stale control command");
                if(ProxyContract.PREPARE.equals(method)){
                    String original=data.getString(ProxyContract.ORIGINAL,"");
                    if(!state.usable(original,true) || !state.usable(state.entry(),false))throw new IllegalStateException("Recovery or target unavailable");
                    if(!state.prefs.edit().putString(ProxyContract.ORIGINAL,original).putString("firmware",Build.FINGERPRINT)
                            .putLong("targetVersion",state.targetVersion()).putLong(ProxyContract.EPOCH,epoch).putString("mode",ProxyContract.STOCK)
                            .remove("pending").putString("problem","").putLong("visibleAt",0).commit())throw new IllegalStateException("Prepare not saved");
                }else if(ProxyContract.PAUSE.equals(method)){
                    if(!state.prefs.edit().putLong(ProxyContract.EPOCH,epoch).putString("mode",ProxyContract.STOCK).remove("pending").commit())throw new IllegalStateException("Pause not saved");
                }else if(ProxyContract.ENABLE.equals(method)){
                    if(!state.valid())throw new IllegalStateException("Recovery or target changed");
                    if(!state.prefs.edit().putString("mode",ProxyContract.TARGET).putString("problem","").commit())throw new IllegalStateException("Enable not saved");
                }else throw new IllegalArgumentException("Unknown control command");
            }
            return state.status();
        }
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new UnsupportedOperationException();}
    @Override public String getType(Uri u){return null;}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
