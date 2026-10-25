package home.quiet.tv;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.Handler;
import android.view.Surface;
import java.io.*;

/** Android 4.4 backend. Media3 is absent from this profile's dependency graph. */
final class WallpaperPlayback {
    private final MediaPlayer player=new MediaPlayer();
    private final Activity activity;
    private final File file;
    private final WallpaperCatalog.Entry entry;
    WallpaperPlayback(Activity activity,Handler worker,Surface surface,File file,WallpaperCatalog.Entry entry,VideoWallpaperPlayer.PlaybackEvents events){
        this.activity=activity;this.file=file;this.entry=entry;
        player.setSurface(surface);player.setScreenOnWhilePlaying(false);player.setVolume(0,0);player.setLooping(true);
        player.setOnPreparedListener(p->{try{
            for(int n=0;n<p.getTrackInfo().length;n++)if(p.getTrackInfo()[n].getTrackType()==MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO)p.deselectTrack(n);
            p.start();events.ready();
        }catch(RuntimeException e){DiagnosticLog.error("wallpaper.video.start_failed",e);events.failed(VideoWallpaperPlayer.Failure.DECODE);}});
        player.setOnErrorListener((p,what,extra)->{
            DiagnosticLog.info("wallpaper.video.player_error","what="+what+" extra="+extra);
            VideoWallpaperPlayer.Failure reason=extra==MediaPlayer.MEDIA_ERROR_UNSUPPORTED?VideoWallpaperPlayer.Failure.UNSUPPORTED:
                extra==MediaPlayer.MEDIA_ERROR_IO?VideoWallpaperPlayer.Failure.FILE:
                extra==MediaPlayer.MEDIA_ERROR_TIMED_OUT?VideoWallpaperPlayer.Failure.TIMEOUT:VideoWallpaperPlayer.Failure.DECODE;
            events.failed(reason);return true;
        });
    }
    void prepare()throws IOException{
        if(file!=null){try(FileInputStream input=new FileInputStream(file)){player.setDataSource(input.getFD());}}
        else try(AssetFileDescriptor input=activity.getAssets().openFd("wallpapers/"+entry.id+".mp4")){player.setDataSource(input.getFileDescriptor(),input.getStartOffset(),input.getLength());}
        player.prepareAsync();
    }
    void release(){player.setOnPreparedListener(null);player.setOnErrorListener(null);player.release();}
}
