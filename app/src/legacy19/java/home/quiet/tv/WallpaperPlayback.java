package home.quiet.tv;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.Handler;
import android.view.Surface;
import java.io.*;

/** Android 4.x backend. Media3 is absent from this profile's dependency graph. */
final class WallpaperPlayback {
    private final MediaPlayer player=new MediaPlayer();
    private final Context source;
    private final File file;
    private final WallpaperCatalog.Entry entry;
    private boolean prepared,paused;
    WallpaperPlayback(Context source,Handler worker,Surface surface,File file,WallpaperCatalog.Entry entry,String context,VideoWallpaperPlayer.PlaybackEvents events){
        this.source=source;this.file=file;this.entry=entry;
        player.setSurface(surface);player.setScreenOnWhilePlaying(false);player.setVolume(0,0);player.setLooping(true);
        player.setOnPreparedListener(p->{try{
            prepared=true;if(!paused)p.start();DiagnosticLog.info("wallpaper.video.prepared",context+" backend=platform evidence=prepared_callback_only");events.ready();
        }catch(RuntimeException e){DiagnosticLog.error("wallpaper.video.start_failed",context+" backend=platform stage=start "+VideoDiagnostics.failureDetails(e),e);events.failed(VideoWallpaperPlayer.Failure.DECODE);}});
        player.setOnErrorListener((p,what,extra)->{
            DiagnosticLog.info("wallpaper.video.player_error",context+" backend=platform stage="+(prepared?"playback":"prepare")+" reason="+VideoDiagnostics.platformReason(what,extra)+" what="+what+" extra="+extra);
            VideoWallpaperPlayer.Failure reason=extra==MediaPlayer.MEDIA_ERROR_UNSUPPORTED?VideoWallpaperPlayer.Failure.UNSUPPORTED:
                extra==MediaPlayer.MEDIA_ERROR_IO?VideoWallpaperPlayer.Failure.FILE:
                extra==MediaPlayer.MEDIA_ERROR_TIMED_OUT?VideoWallpaperPlayer.Failure.TIMEOUT:VideoWallpaperPlayer.Failure.DECODE;
            events.failed(reason);return true;
        });
    }
    void prepare()throws IOException{
        if(file!=null){try(FileInputStream input=new FileInputStream(file)){player.setDataSource(input.getFD());}}
        else try(AssetFileDescriptor input=source.getAssets().openFd("wallpapers/"+entry.id+".mp4")){player.setDataSource(input.getFileDescriptor(),input.getStartOffset(),input.getLength());}
        player.prepareAsync();
    }
    // TextureView's owned output remains alive until the platform confirms release.
    boolean detachOutput(){return false;}
    void release(Runnable completed){player.setOnPreparedListener(null);player.setOnErrorListener(null);player.release();completed.run();}
    void setPaused(boolean paused){this.paused=paused;if(prepared){if(paused)player.pause();else player.start();}}
}
