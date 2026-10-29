package home.quiet.tv;

import android.content.*;
import android.net.*;
import android.os.*;
import java.util.concurrent.*;

/** Owns foreground requests; generation checks prevent old cities replacing newer selections. */
final class WeatherController {
    interface Listener{void changed(WeatherData data,String message,boolean loading);}
    private final Context context;
    private final boolean chinese;
    private final Listener listener;
    private final WeatherStore store;
    private final WeatherClient client;
    private final WeatherLocationResolver locations;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Runnable refresh=()->refresh(false);
    private final Runnable ageTick=new Runnable(){public void run(){if(active){render();main.postDelayed(this,60000);}}};
    private final BroadcastReceiver network=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(connected())refresh(false);}};
    private WeatherData data;
    private Future<?> request;
    private boolean active,loading,rateLimited,screensaverWeather;
    private int generation;
    private long nextAttempt;
    private static long lastAttempt;
    private String key="",error="";
    WeatherController(Context context,boolean chinese,Listener listener){
        this.context=context;this.chinese=chinese;this.listener=listener;store=new WeatherStore(context);locations=new WeatherLocationResolver(context);client=new WeatherClient(context);
    }
    private String tr(String zh,String en){return chinese?zh:en;}
    void resume(){
        if(active)return;active=true;
        String selected=store.key(chinese);if(!selected.equals(key)){key=selected;if(!rateLimited)nextAttempt=0;error="";}
        data=store.cached(chinese); // Settings may have refreshed the same city while Home was paused.
        context.registerReceiver(network,new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        render();refresh(false);main.postDelayed(ageTick,60000);
    }
    void pause(){
        if(!active)return;active=false;context.unregisterReceiver(network);main.removeCallbacks(refresh);main.removeCallbacks(ageTick);
        cancel();
    }
    void destroy(){pause();worker.shutdownNow();}
    private boolean needed(){return store.enabled()||screensaverWeather;}
    void screensaverWeather(boolean enabled){
        if(screensaverWeather==enabled)return;screensaverWeather=enabled;
        if(needed()){render();refresh(false);}else{main.removeCallbacks(refresh);cancel();render();}
    }
    String errorMessage(){return error;}
    void settingsChanged(){cancel();key=store.key(chinese);data=store.cached(chinese);if(!rateLimited)nextAttempt=0;error="";render();refresh(true);}
    void refresh(boolean force){
        main.removeCallbacks(refresh);
        if(!active||!needed()||loading){render();return;}
        long elapsed=SystemClock.elapsedRealtime(),now=System.currentTimeMillis();
        if(elapsed<nextAttempt&&(!force||rateLimited)){schedule(nextAttempt-elapsed);render();return;}
        if(!force&&data!=null&&data.usable(now)&&now-data.fetchedAt>=0&&now-data.fetchedAt<WeatherData.REFRESH_MS){schedule(WeatherData.REFRESH_MS-(now-data.fetchedAt));render();return;}
        if(!connected()){DiagnosticLog.info("weather.refresh.skipped","reason=offline");error=tr("天气暂不可用","Weather unavailable");render();return;}
        // A remote cannot create a burst by repeatedly pressing Refresh.
        if(lastAttempt>0&&elapsed-lastAttempt<10000){schedule(10000-(elapsed-lastAttempt));return;}
        lastAttempt=elapsed;loading=true;error="";render();final int version=++generation;final String selected=key;
        if(store.automatic())locations.resolve(location->{
            if(!active||version!=generation)return;
            fetch(version,selected,"",location);
        });else fetch(version,selected,store.adcode(),null);
    }
    private void fetch(int version,String selected,String adcode,android.location.Location location){
        request=worker.submit(()->{
            try{
                WeatherRegions.Region place=store.automatic()?null:store.selected();
                if(!store.automatic()&&place==null)throw new WeatherClient.ApiException(404,0);
                if(place!=null&&!place.located())try{place=client.resolve(place);}catch(java.io.IOException|org.json.JSONException e){if(Thread.currentThread().isInterrupted())return;}
                final WeatherRegions.Region resolved=place;
                WeatherData result=location==null?client.weather(place,store.provider(),store.meteoFallback(),adcode.isEmpty()?"ip":"manual",chinese)
                    :client.locatedWeather(location.getLatitude(),location.getLongitude(),store.provider(),store.meteoFallback(),chinese);
                main.post(()->{if(!accept(version,selected))return;loading=false;data=result;
                    if(resolved!=null&&resolved.located()){store.select(resolved);key=store.key(chinese);}
                    store.save(chinese,result);nextAttempt=0;rateLimited=false;error="";render();schedule(WeatherData.REFRESH_MS);});
            }catch(java.io.IOException|org.json.JSONException e){
                DiagnosticLog.error("weather.refresh.failed",e);
                main.post(()->{
                    if(!accept(version,selected))return;loading=false;
                    long delay=5*60*1000L;
                    error=tr("天气暂不可用","Weather unavailable");
                    if(e instanceof WeatherClient.ApiException){WeatherClient.ApiException api=(WeatherClient.ApiException)e;
                        if(api.status==404)error=tr("此来源暂不支持该地点，请更换地点或天气来源","This source cannot resolve the place. Choose another place or source.");
                        if(api.status==503)error=tr("所选天气源未配置，请切换来源","The selected source is not configured. Choose another source.");
                        if(api.status==429){rateLimited=true;delay=Math.max(WeatherData.REFRESH_MS,api.retryAfterMs);error=tr("天气更新受限，请稍后再试","Weather refresh limited. Try later.");}
                    }
                    nextAttempt=SystemClock.elapsedRealtime()+delay;render();schedule(delay);
                });
            }
        });
    }
    private boolean accept(int version,String selected){return active&&version==generation&&selected.equals(store.key(chinese));}
    private void schedule(long delay){main.removeCallbacks(refresh);if(active&&needed())main.postDelayed(refresh,Math.max(1000,delay));}
    private void cancel(){++generation;locations.cancel();if(request!=null)request.cancel(true);client.cancel();loading=false;}
    private boolean connected(){ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);NetworkInfo info=manager==null?null:manager.getActiveNetworkInfo();return info!=null&&info.isConnected();}
    private void render(){
        if(!needed()){listener.changed(null,"",false);return;}
        WeatherData visible=data!=null&&data.usable(System.currentTimeMillis())?data:null;
        // Failed refreshes are explicit even while a recent cached value is retained.
        String message=visible!=null?(!error.isEmpty()||visible.stale(System.currentTimeMillis())?tr("未更新","Not updated"):""):error;
        if(visible==null&&message.isEmpty()&&!loading)message=tr("天气暂不可用","Weather unavailable");
        listener.changed(visible,message,loading);
    }
}
