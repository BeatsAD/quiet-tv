package home.quiet.tv;

import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.os.*;
import android.view.Surface;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Android 4.x codec calls live outside the HOME process, including synchronous release(). */
final class WallpaperPlayback {
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
    private boolean bound,opened,released,terminal,sound,paused,linked;
    private Runnable completion;
    private final IBinder.DeathRecipient death;
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder service){worker.post(()->connected(service));}
        @Override public void onServiceDisconnected(ComponentName name){worker.post(()->ended("service_disconnected"));}
    };
    WallpaperPlayback(Context source,Handler worker,Surface surface,File file,WallpaperCatalog.Entry entry,String context,VideoWallpaperPlayer.PlaybackEvents events){
        this.source=source;this.worker=worker;this.surface=surface;this.file=file;this.entry=entry;this.context=context;this.events=events;
        death=()->worker.post(()->ended("process_died"));
        replies=new Messenger(new Handler(worker.getLooper()){@Override public void handleMessage(Message message){receive(message);}});
    }
    void prepare()throws IOException{
        try{
            if(file!=null){input=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);offset=0;length=file.length();}
            else try(AssetFileDescriptor asset=source.getAssets().openFd("wallpapers/"+entry.id+".mp4")){
                input=ParcelFileDescriptor.dup(asset.getFileDescriptor());offset=asset.getStartOffset();length=asset.getLength();
            }
            bound=source.bindService(new Intent(source,LegacyWallpaperService.class),connection,Context.BIND_AUTO_CREATE);
            if(!bound)throw new IOException("Playback service unavailable");
        }catch(IOException|RuntimeException e){closeInput();throw e;}
    }
    private void connected(IBinder service){
        if(terminal||released)return;
        binder=service;remote=new Messenger(service);
        try{
            service.linkToDeath(death,0);linked=true;
            Bundle data=new Bundle();data.putParcelable("surface",surface);data.putParcelable("file",input);
            data.putLong("offset",offset);data.putLong("length",length);data.putBoolean("sound",sound);data.putBoolean("paused",paused);
            opened=true;send(LegacyWallpaperService.OPEN,data);
            DiagnosticLog.info("wallpaper.video.remote",context+" phase=bound session="+session);
        }catch(RemoteException e){remoteFailure("bind_failed");}
        finally{closeInput();}
    }
    private void receive(Message message){
        if(message.arg1!=session||terminal)return;
        if(message.what==LegacyWallpaperService.RELEASED){ended("release_complete");return;}
        if(message.what==LegacyWallpaperService.NOTE){
            DiagnosticLog.info("wallpaper.video.remote",context+" session="+session+" phase="+VideoDiagnostics.token(message.getData().getString("phase")));return;
        }
        if(released)return;
        switch(message.what){
            case LegacyWallpaperService.PREPARED:
                DiagnosticLog.info("wallpaper.video.prepared",context+" backend=platform evidence=prepared_callback_only");
                if(Build.VERSION.SDK_INT<17)events.ready();break;
            case LegacyWallpaperService.FRAME:events.ready();break;
            case LegacyWallpaperService.SIZE:
                if(entry.rotation==0)events.size(message.getData().getInt("width"),message.getData().getInt("height"),entry.pixelRatio);break;
            case LegacyWallpaperService.FAILED:
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
    void setSound(boolean enabled){sound=enabled;control(LegacyWallpaperService.SOUND,enabled);}
    void setPaused(boolean value){paused=value;control(LegacyWallpaperService.PAUSE,value);}
    boolean detachOutput(){return false;}
    void release(Runnable completed){
        released=true;completion=completed;
        if(terminal||!opened){ended("cancelled_before_open");return;}
        DiagnosticLog.info("wallpaper.video.remote",context+" phase=release_requested session="+session);
        try{send(LegacyWallpaperService.RELEASE,null);}catch(RemoteException e){remoteFailure("release_failed");}
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
        if(first)DiagnosticLog.info("wallpaper.video.remote",context+" session="+session+" phase="+phase);
        if(completion!=null){Runnable done=completion;completion=null;done.run();}
        else if(first&&!released)events.failed(VideoWallpaperPlayer.Failure.DECODE);
    }
    private void closeInput(){if(input!=null){try{input.close();}catch(IOException ignored){}input=null;}}
}
