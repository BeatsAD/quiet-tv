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
    private LocationManager manager;
    private LocationListener listener;
    private Runnable timeout;
    WeatherLocationResolver(Context context){this.context=context;}
    boolean permitted(){return Build.VERSION.SDK_INT<23||context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    // Permission and vendor service failures both mean the caller should use IP lookup.
    @android.annotation.SuppressLint("MissingPermission")
    void resolve(Callback callback){
        cancel();
        Location cached=null;boolean available=false;
        try{
            if(permitted())manager=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
            if(manager!=null&&manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){
                cached=manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);available=true;
            }
        }catch(RuntimeException ignored){
            // Some stripped Android 4.4 firmwares return a manager with a null service binder.
        }
        if(!available||fresh(cached)){cancel();callback.resolved(available?cached:null);return;}
        listener=new LocationListener(){
            @Override public void onLocationChanged(Location location){if(fresh(location))complete(this,callback,location);}
            @Override public void onStatusChanged(String provider,int status,Bundle extras){}
            @Override public void onProviderEnabled(String provider){}
            @Override public void onProviderDisabled(String provider){complete(this,callback,null);}
        };
        LocationListener attempt=listener;
        timeout=()->complete(attempt,callback,null);main.postDelayed(timeout,5000);
        boolean failed=false;
        try{manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,0,0,attempt,Looper.getMainLooper());}
        catch(RuntimeException ignored){failed=true;}
        if(failed)complete(attempt,callback,null);
    }
    private void complete(LocationListener attempt,Callback callback,Location location){
        if(listener!=attempt)return;
        cancel();callback.resolved(location);
    }
    private static boolean fresh(Location location){
        if(location==null)return false;long age=SystemClock.elapsedRealtimeNanos()-location.getElapsedRealtimeNanos();
        return age>=0&&age<30*60*1000000000L;
    }
    // Clear local state before vendor IPC so failed removal and late callbacks cannot finish twice.
    @android.annotation.SuppressLint("MissingPermission")
    void cancel(){
        if(timeout!=null)main.removeCallbacks(timeout);timeout=null;
        LocationListener previous=listener;LocationManager service=manager;listener=null;manager=null;
        if(previous!=null&&service!=null)try{service.removeUpdates(previous);}catch(RuntimeException ignored){}
    }
}
