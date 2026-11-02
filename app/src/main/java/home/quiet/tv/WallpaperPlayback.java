package home.quiet.tv;

import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.os.*;
import android.net.Uri;
import android.view.Surface;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Playback replacement waits for codec release; normal devices use the isolated service. */
final class WallpaperPlayback {
    interface LocalPlayer {
        void prepare()throws IOException;
        void setSound(boolean enabled);
        void setPaused(boolean paused);
        void release(Runnable completed);
    }
    private static final AtomicInteger sessions=new AtomicInteger();
    private final int session=sessions.incrementAndGet();
    private final Context source;
    private final Handler worker;
    private final Surface surface;
    private final File file;
    private final WallpaperCatalog.Entry entry;
    private final String context;
    private final VideoWallpaperPlayer.PlaybackEvents events;
    private final Messenger replies;
    private Messenger remote;
    private IBinder binder;
    private ParcelFileDescriptor input;
    private long offset,length;
    private boolean bound,opened,released,terminal,sound,paused,linked,cancelled,softwareAvailable;
    private final boolean softwareOnly;
    private WallpaperPlayback fallback;
    private LocalPlayer local;
    private Runnable completion;
    private final IBinder.DeathRecipient death;
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder service){worker.post(()->connected(service));}
        @Override public void onServiceDisconnected(ComponentName name){worker.post(()->ended("service_disconnected"));}
    };
    WallpaperPlayback(Context source,Handler worker,Surface surface,File file,WallpaperCatalog.Entry entry,String context,VideoWallpaperPlayer.PlaybackEvents events){
        this(source,worker,surface,file,entry,context,events,false);
    }
    private WallpaperPlayback(Context source,Handler worker,Surface surface,File file,WallpaperCatalog.Entry entry,String context,VideoWallpaperPlayer.PlaybackEvents events,boolean softwareOnly){
        this.source=source;this.worker=worker;this.surface=surface;this.file=file;this.entry=entry;this.context=context;this.events=events;this.softwareOnly=softwareOnly;
        death=()->worker.post(()->ended("process_died"));
        replies=new Messenger(new Handler(worker.getLooper()){@Override public void handleMessage(Message message){receive(message);}});
    }
    void prepare()throws IOException{
        if(released||terminal)return;
        try{
            local=WallpaperService.localPlayer(source,worker,surface,uri(),entry.rotation,context,softwareOnly,new VideoWallpaperPlayer.PlaybackEvents(){
                @Override public void ready(){localEvent(WallpaperService.FRAME,0,null);}
                @Override public void softwareDecoderAvailable(boolean available){localEvent(WallpaperService.SOFTWARE_AVAILABLE,available?1:0,null);}
                @Override public void retrySoftware(){localEvent(WallpaperService.RETRY_SOFTWARE,0,null);}
                @Override public void failed(VideoWallpaperPlayer.Failure reason){localEvent(WallpaperService.FAILED,reason.ordinal(),null);}
                @Override public void size(int width,int height,float ratio){Bundle size=new Bundle();size.putInt("width",width);size.putInt("height",height);size.putFloat("pixelRatio",ratio);localEvent(WallpaperService.SIZE,0,size);}
            });
            if(local!=null){
                opened=true;
                DiagnosticLog.info("wallpaper.video.local",context+" phase=open session="+session+" reason=mitv_starwars_api22_firmware_1653");
                local.setSound(sound);local.setPaused(paused);local.prepare();return;
            }
            if(BuildConfig.LEGACY_19){
                if(file!=null){input=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);offset=0;length=file.length();}
                else try(AssetFileDescriptor asset=source.getAssets().openFd("wallpapers/"+entry.id+".mp4")){
                    input=ParcelFileDescriptor.dup(asset.getFileDescriptor());offset=asset.getStartOffset();length=asset.getLength();
                }
            }
            bound=source.bindService(new Intent(source,WallpaperService.class),connection,Context.BIND_AUTO_CREATE);
            if(!bound)throw new IOException("Playback service unavailable");
        }catch(IOException|RuntimeException e){closeInput();throw e;}
    }
    private Uri uri(){return file!=null?Uri.fromFile(file):Uri.parse("asset:///wallpapers/"+entry.id+".mp4");}
    private void localEvent(int what,int reason,Bundle data){
        // Codec discovery can call back on the renderer thread. Serialize with controls/release.
        worker.post(()->{
            Message message=Message.obtain(null,what,session,reason);if(data!=null)message.setData(data);
            try{receive(message);}finally{message.recycle();}
        });
    }
    private void connected(IBinder service){
        if(terminal||released)return;
        binder=service;remote=new Messenger(service);
        try{
            service.linkToDeath(death,0);linked=true;
            Bundle data=new Bundle();data.putParcelable("surface",surface);data.putParcelable("file",input);
            data.putString("uri",uri().toString());
            data.putBoolean("softwareOnly",softwareOnly);data.putInt("rotation",entry.rotation);data.putString("context",context);
            data.putLong("offset",offset);data.putLong("length",length);data.putBoolean("sound",sound);data.putBoolean("paused",paused);
            opened=true;send(WallpaperService.OPEN,data);
            DiagnosticLog.info("wallpaper.video.remote",context+" phase=bound session="+session);
        }catch(RemoteException e){remoteFailure("bind_failed");}
        finally{closeInput();}
    }
    private void receive(Message message){
        if(message.arg1!=session||terminal)return;
        if(message.what==WallpaperService.RELEASED){ended("release_complete");return;}
        if(message.what==WallpaperService.LOG){
            Bundle data=message.getData();String event=data.getString("event"),detail=data.getString("detail");
            if(data.getBoolean("error"))DiagnosticLog.error(event,detail,null);else DiagnosticLog.info(event,detail);return;
        }
        if(message.what==WallpaperService.NOTE){
            DiagnosticLog.info("wallpaper.video.remote",context+" session="+session+" phase="+VideoDiagnostics.token(message.getData().getString("phase")));return;
        }
        if(released)return;
        switch(message.what){
            case WallpaperService.SOFTWARE_AVAILABLE:softwareAvailable=message.arg2!=0;break;
            case WallpaperService.RETRY_SOFTWARE:
                if(!retrySoftware("video_decode_error"))events.failed(VideoWallpaperPlayer.Failure.DECODE);break;
            case WallpaperService.PREPARED:
                DiagnosticLog.info("wallpaper.video.prepared",context+" backend=platform evidence=prepared_callback_only");
                if(Build.VERSION.SDK_INT<17)events.ready();break;
            case WallpaperService.FRAME:events.ready();break;
            case WallpaperService.SIZE:
                if(!BuildConfig.LEGACY_19||entry.rotation==0)events.size(message.getData().getInt("width"),message.getData().getInt("height"),BuildConfig.LEGACY_19?entry.pixelRatio:message.getData().getFloat("pixelRatio",1f));break;
            case WallpaperService.FAILED:
                int reason=message.arg2;
                events.failed(reason>=0&&reason<VideoWallpaperPlayer.Failure.values().length?VideoWallpaperPlayer.Failure.values()[reason]:VideoWallpaperPlayer.Failure.DECODE);break;
        }
    }
    private void send(int command,Bundle data)throws RemoteException{
        Message message=Message.obtain(null,command,session,0);message.replyTo=replies;if(data!=null)message.setData(data);remote.send(message);
    }
    private void control(int command,boolean enabled){
        if(remote==null||released||terminal)return;
        Bundle data=new Bundle();data.putBoolean("enabled",enabled);
        try{send(command,data);}catch(RemoteException e){remoteFailure("control_failed");}
    }
    void setSound(boolean enabled){sound=enabled;if(fallback!=null)fallback.setSound(enabled);else if(local!=null){if(!released&&!terminal)local.setSound(enabled);}else control(WallpaperService.SOUND,enabled);}
    void setPaused(boolean value){paused=value;if(fallback!=null)fallback.setPaused(value);else if(local!=null){if(!released&&!terminal)local.setPaused(value);}else control(WallpaperService.PAUSE,value);}
    boolean retrySoftware(String reason){
        if(cancelled||released||terminal||softwareOnly||fallback!=null||!softwareAvailable)return false;
        DiagnosticLog.info("wallpaper.video.fallback",context+" from="+(BuildConfig.LEGACY_19?"platform":"hardware")+" to=software reason="+reason+" phase=retiring_player");
        events.retrying();
        retire(()->{
            if(cancelled)return;
            fallback=new WallpaperPlayback(source,worker,surface,file,entry,context,events,true);
            fallback.setSound(sound);fallback.setPaused(paused);
            DiagnosticLog.info("wallpaper.video.fallback",context+" from="+(BuildConfig.LEGACY_19?"platform":"hardware")+" to=software phase=starting_software");
            try{fallback.prepare();}
            catch(IOException|RuntimeException e){DiagnosticLog.error("wallpaper.video.fallback_failed",context+" "+VideoDiagnostics.failureDetails(e),e);events.failed(e instanceof IOException?VideoWallpaperPlayer.Failure.FILE:VideoWallpaperPlayer.Failure.DECODE);}
        });
        return true;
    }
    boolean detachOutput(){return false;}
    void release(Runnable completed){
        cancelled=true;
        if(fallback!=null){fallback.release(completed);return;}
        retire(completed);
    }
    private void retire(Runnable completed){
        boolean retiring=released;released=true;completion=completed;
        if(local!=null){
            if(terminal)ended("local_already_released");
            else if(!retiring){
                DiagnosticLog.info("wallpaper.video.local",context+" phase=release_requested session="+session);
                // No process-kill watchdog in HOME. A blocked release keeps the handoff closed.
                local.release(()->ended("local_release_complete"));
            }
            return;
        }
        if(terminal||!opened){ended("cancelled_before_open");return;}
        DiagnosticLog.info("wallpaper.video.remote",context+" phase=release_requested session="+session);
        try{send(WallpaperService.RELEASE,null);}catch(RemoteException e){remoteFailure("release_failed");}
    }
    private void remoteFailure(String phase){
        // A transport exception alone is not proof that the remote decoder freed its Surface.
        if(!opened||binder==null||!binder.isBinderAlive()){ended(phase);return;}
        DiagnosticLog.info("wallpaper.video.remote",context+" session="+session+" phase="+phase+" awaiting_release=true");
        // Unbinding asks the responsive service main Looper to retire even if this IPC send failed.
        if(bound){bound=false;source.unbindService(connection);}
        if(!released)events.failed(VideoWallpaperPlayer.Failure.DECODE);
    }
    private void ended(String phase){
        boolean first=!terminal;terminal=true;closeInput();
        if(binder!=null){if(linked)binder.unlinkToDeath(death,0);linked=false;binder=null;}
        remote=null;
        if(bound){bound=false;source.unbindService(connection);}
        if(first)DiagnosticLog.info(local!=null?"wallpaper.video.local":"wallpaper.video.remote",context+" session="+session+" phase="+phase);
        if(completion!=null){Runnable done=completion;completion=null;done.run();}
        else if(first&&!released)events.failed(VideoWallpaperPlayer.Failure.DECODE);
    }
    private void closeInput(){if(input!=null){try{input.close();}catch(IOException ignored){}input=null;}}
}
