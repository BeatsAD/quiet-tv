package home.quiet.tv;

import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.content.Context;
import android.media.MediaFormat;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.*;
import java.util.ArrayList;
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
        boolean manualRotation=VideoGeometry.manualRotation(android.os.Build.VERSION.SDK_INT,entry.rotation);
        DefaultRenderersFactory renderers=new DefaultRenderersFactory(activity){
            @Override protected void buildVideoRenderers(Context context,int extensions,MediaCodecSelector selector,boolean fallback,Handler handler,VideoRendererEventListener listener,long joiningMs,ArrayList<Renderer> output){
                if(!manualRotation){super.buildVideoRenderers(context,extensions,selector,fallback,handler,listener,joiningMs,output);return;}
                output.add(new MediaCodecVideoRenderer(new MediaCodecVideoRenderer.Builder(context).setCodecAdapterFactory(getCodecAdapterFactory()).setMediaCodecSelector(selector).setEnableDecoderFallback(fallback).setEventHandler(handler).setEventListener(listener).setAllowedJoiningTimeMs(joiningMs)){
                    @Override protected MediaFormat getMediaFormat(Format format,String mime,CodecMaxValues maximum,float rate,boolean workaround,int session){
                        MediaFormat value=super.getMediaFormat(format,mime,maximum,rate,workaround,session);
                        // API21-26 native rotation can change the buffer's aspect. Rotate once in the texture instead.
                        value.setInteger(MediaFormat.KEY_ROTATION,0);return value;
                    }
                });
            }
        };
        renderers.setEnableDecoderFallback(true);
        player=new ExoPlayer.Builder(activity,renderers)
            .setLooper(worker.getLooper())
            .setLoadControl(new DefaultLoadControl.Builder().setTargetBufferBytes(12*1024*1024)
                .setBufferDurationsMs(5000,15000,500,1000).setPrioritizeTimeOverSizeThresholds(false).build()).build();
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,true).build());
        player.setVolume(0);player.setRepeatMode(Player.REPEAT_MODE_ONE);player.setVideoSurface(surface);
        player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
        player.addListener(new Player.Listener(){
            @Override public void onVideoSizeChanged(VideoSize size){events.size(size.width,size.height,size.pixelWidthHeightRatio);}
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
    void prepare()throws java.io.IOException{player.prepare();}
    void setPaused(boolean paused){player.setPlayWhenReady(!paused);}
    void release(){player.release();}
}
