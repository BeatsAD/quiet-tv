package home.quiet.tv;

import android.app.Activity;
import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;
import java.io.*;

/** One foreground-only decoder. The caller owns poster visibility and committed selection. */
final class VideoWallpaperPlayer implements SurfaceHolder.Callback {
    interface Listener {void ready();void failed();void stopped();}
    private final Activity activity;
    private final FrameLayout host,frame;
    private final SurfaceView surface;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final BroadcastReceiver screen=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){screenOff=Intent.ACTION_SCREEN_OFF.equals(i.getAction());if(screenOff)release();else start();}};
    private MediaPlayer player;
    private WallpaperCatalog.Entry entry;
    private Listener listener;
    private boolean enabled,screenOff,rendered;
    private int generation,bufferWidth,bufferHeight;
    private final Runnable timeout=()->fail(player);
    VideoWallpaperPlayer(Activity activity,FrameLayout host){
        this.activity=activity;this.host=host;frame=new FrameLayout(activity);frame.setClipChildren(true);frame.setVisibility(View.GONE);host.addView(frame,new FrameLayout.LayoutParams(-1,-1));
        surface=new SurfaceView(activity);surface.setKeepScreenOn(false);surface.setFocusable(false);surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);frame.addView(surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));surface.getHolder().addCallback(this);
        host.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->layout());
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);activity.registerReceiver(screen,filter);
    }
    void play(WallpaperCatalog.Entry entry,Listener listener){
        release();this.entry=entry;this.listener=listener;enabled=true;
        PowerManager power=(PowerManager)activity.getSystemService(Context.POWER_SERVICE);screenOff=power==null||!(Build.VERSION.SDK_INT>=20?power.isInteractive():power.isScreenOn());
        frame.setVisibility(View.VISIBLE);layout();start();
    }
    boolean ready(){return rendered&&player!=null;}
    void stop(){enabled=false;release();frame.setVisibility(View.GONE);entry=null;listener=null;}
    void destroy(){stop();surface.getHolder().removeCallback(this);activity.unregisterReceiver(screen);main.removeCallbacksAndMessages(null);}
    private void layout(){
        if(entry==null||host.getWidth()==0||host.getHeight()==0)return;
        int w=entry.rotation%180==0?entry.width:entry.height,h=entry.rotation%180==0?entry.height:entry.width;
        float scale=Math.max(host.getWidth()/(float)w,host.getHeight()/(float)h);
        int width=Math.round(w*scale),height=Math.round(h*scale);
        FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)surface.getLayoutParams();
        // A layout callback can run even with unchanged bounds; do not request another pass.
        if(params.width!=width||params.height!=height){params.width=width;params.height=height;surface.setLayoutParams(params);}
        // The decoder's buffers retain source resolution, including on TVs with a 1080p UI.
        if(bufferWidth!=w||bufferHeight!=h){bufferWidth=w;bufferHeight=h;surface.getHolder().setFixedSize(w,h);}
    }
    private void start(){
        if(!enabled||screenOff||player!=null||entry==null||!surface.getHolder().getSurface().isValid())return;
        final int request=++generation;MediaPlayer created=new MediaPlayer();player=created;rendered=false;
        try{
            created.setDisplay(surface.getHolder());created.setScreenOnWhilePlaying(false);created.setVolume(0,0);created.setLooping(true);
            if(entry.custom()){
                try(FileInputStream input=new FileInputStream(WallpaperStore.get(activity).file(entry.id))){created.setDataSource(input.getFD());}
            }else{
                try(AssetFileDescriptor input=activity.getAssets().openFd("wallpapers/"+entry.id+".mp4")){created.setDataSource(input.getFileDescriptor(),input.getStartOffset(),input.getLength());}
            }
            created.setOnPreparedListener(p->{if(player==p&&request==generation)p.start();});
            created.setOnInfoListener((p,what,extra)->{if(player==p&&request==generation&&!rendered&&what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START){rendered=true;main.removeCallbacks(timeout);if(listener!=null)listener.ready();}return false;});
            created.setOnErrorListener((p,what,extra)->{if(request==generation)fail(p);return true;});
            main.postDelayed(timeout,8000);created.prepareAsync();
        }catch(IOException|RuntimeException e){fail(created);}
    }
    private void fail(MediaPlayer failed){if(failed==null||failed!=player)return;Listener report=listener;enabled=false;release();frame.setVisibility(View.GONE);if(report!=null)report.failed();}
    private void release(){if(listener!=null)listener.stopped();++generation;main.removeCallbacks(timeout);rendered=false;MediaPlayer old=player;player=null;if(old!=null){old.setOnPreparedListener(null);old.setOnInfoListener(null);old.setOnErrorListener(null);old.release();}}
    @Override public void surfaceCreated(SurfaceHolder holder){start();}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder holder){release();}
}
