package home.quiet.tv;

import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.view.Surface;
import androidx.media3.common.*;
import android.annotation.SuppressLint;
import androidx.media3.exoplayer.*;
import java.io.File;

/** Local-only Media3 playback, with bounded buffering and no audio decoder. */
@SuppressLint("UnsafeOptInUsageError")
final class WallpaperPlayback {
    private final ExoPlayer player;
    WallpaperPlayback(Activity activity,Handler worker,Surface surface,File file,WallpaperCatalog.Entry entry,VideoWallpaperPlayer.PlaybackEvents events){
        player=new ExoPlayer.Builder(activity,new DefaultRenderersFactory(activity).setEnableDecoderFallback(true))
            .setLooper(worker.getLooper())
            .setLoadControl(new DefaultLoadControl.Builder().setTargetBufferBytes(12*1024*1024)
                .setBufferDurationsMs(5000,15000,500,1000).setPrioritizeTimeOverSizeThresholds(false).build()).build();
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,true).build());
        player.setVolume(0);player.setRepeatMode(Player.REPEAT_MODE_ONE);player.setVideoSurface(surface);
        player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
        player.addListener(new Player.Listener(){
            @Override public void onRenderedFirstFrame(){events.ready();}
            @Override public void onPlayerError(PlaybackException error){
                DiagnosticLog.info("wallpaper.video.player_error",error.getErrorCodeName()+" code="+error.errorCode);
                VideoWallpaperPlayer.Failure reason;
                switch(error.errorCode){
                    case PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED:
                    case PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES:
                    case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
                    case PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED:reason=VideoWallpaperPlayer.Failure.UNSUPPORTED;break;
                    case PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND:
                    case PlaybackException.ERROR_CODE_IO_NO_PERMISSION:
                    case PlaybackException.ERROR_CODE_IO_UNSPECIFIED:reason=VideoWallpaperPlayer.Failure.FILE;break;
                    case PlaybackException.ERROR_CODE_TIMEOUT:reason=VideoWallpaperPlayer.Failure.TIMEOUT;break;
                    case PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED:
                    case PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED:reason=VideoWallpaperPlayer.Failure.OUTPUT;break;
                    default:reason=VideoWallpaperPlayer.Failure.DECODE;
                }
                events.failed(reason);
            }
        });
        player.setMediaItem(MediaItem.fromUri(file!=null?Uri.fromFile(file):Uri.parse("asset:///wallpapers/"+entry.id+".mp4")));
    }
    void prepare()throws java.io.IOException{player.prepare();player.play();}
    void release(){player.release();}
}
