package home.quiet.tv;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;

/** A bounded foreground network-location attempt; null means use IP-based city lookup. */
final class WeatherLocationResolver {
    interface Callback{void resolved(Location location);}
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LocationManager manager;
    private LocationListener listener;
    private Runnable timeout;
    WeatherLocationResolver(Context context){this.context=context;manager=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);}
    boolean permitted(){return Build.VERSION.SDK_INT<23||context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    void resolve(Callback callback){
        cancel();
        if(manager==null||!permitted()){callback.resolved(null);return;}
        try{
            if(!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){callback.resolved(null);return;}
            Location cached=manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if(fresh(cached)){callback.resolved(cached);return;}
            listener=new LocationListener(){
                @Override public void onLocationChanged(Location location){if(fresh(location)){cancel();callback.resolved(location);}}
                @Override public void onStatusChanged(String provider,int status,Bundle extras){}
                @Override public void onProviderEnabled(String provider){}
                @Override public void onProviderDisabled(String provider){cancel();callback.resolved(null);}
            };
            timeout=()->{cancel();callback.resolved(null);};main.postDelayed(timeout,5000);
            manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,0,0,listener,Looper.getMainLooper());
        }catch(SecurityException|IllegalArgumentException e){cancel();callback.resolved(null);}
    }
    private static boolean fresh(Location location){
        if(location==null)return false;long age=SystemClock.elapsedRealtimeNanos()-location.getElapsedRealtimeNanos();
        return age>=0&&age<30*60*1000000000L;
    }
    // A listener exists only after the permission check in resolve; revocation is caught here.
    @android.annotation.SuppressLint("MissingPermission")
    void cancel(){if(timeout!=null)main.removeCallbacks(timeout);timeout=null;if(listener!=null){try{manager.removeUpdates(listener);}catch(SecurityException ignored){}listener=null;}}
}
