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
    private boolean prepared,paused,sound,focus,released;
    private final android.media.AudioManager audio;
    private final android.media.AudioManager.OnAudioFocusChangeListener audioFocus;
    private void volume(){if(!released)player.setVolume(sound&&focus&&!paused?1:0,sound&&focus&&!paused?1:0);}
    private void requestFocus(){if(sound&&!paused&&!focus)focus=audio!=null&&audio.requestAudioFocus(audioFocus,android.media.AudioManager.STREAM_MUSIC,android.media.AudioManager.AUDIOFOCUS_GAIN)==android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;volume();}
    void setSound(boolean enabled){sound=enabled;if(enabled)requestFocus();else{if(audio!=null)audio.abandonAudioFocus(audioFocus);focus=false;volume();}}
    WallpaperPlayback(Context source,Handler worker,Surface surface,File file,WallpaperCatalog.Entry entry,String context,VideoWallpaperPlayer.PlaybackEvents events){
        this.source=source;this.file=file;this.entry=entry;audioFocus=change->worker.post(()->{if(released)return;focus=change==android.media.AudioManager.AUDIOFOCUS_GAIN;volume();});audio=(android.media.AudioManager)source.getSystemService(android.content.Context.AUDIO_SERVICE);
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
    // TextureView output stays alive until platform release is acknowledged.
    boolean detachOutput(){return false;}
    void release(Runnable completed){released=true;if(audio!=null)audio.abandonAudioFocus(audioFocus);player.setOnPreparedListener(null);player.setOnErrorListener(null);player.release();completed.run();}
    void setPaused(boolean paused){this.paused=paused;if(paused){if(audio!=null)audio.abandonAudioFocus(audioFocus);focus=false;volume();}else requestFocus();if(prepared){if(paused)player.pause();else player.start();}}
}
