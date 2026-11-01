package home.quiet.tv;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Handler;
import android.view.Surface;
import com.google.android.exoplayer2.*;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.mediacodec.*;
import com.google.android.exoplayer2.video.VideoSize;
import java.io.*;
import java.util.*;

/** API16+ adapter: platform software codecs, with ExoPlayer providing audio sync and looping. */
@TargetApi(16)
final class LegacySoftwareEngine implements WallpaperService.SoftwarePlayer {
    private final ExoPlayer player;
    private final Handler worker;
    private final String context;
    private final WallpaperService.SoftwareDiagnostics log;
    private boolean releasing,releaseTimedOut;
    private int loops;
    LegacySoftwareEngine(Context source,Handler worker,Surface surface,Uri uri,String context,VideoWallpaperPlayer.PlaybackEvents events,WallpaperService.SoftwareDiagnostics log){
        this.worker=worker;this.context=context;this.log=log;
        DefaultRenderersFactory renderers=new DefaultRenderersFactory(source).setEnableDecoderFallback(true)
            .setMediaCodecSelector((mime,secure,tunneling)->softwareCodecs(MediaCodecSelector.DEFAULT.getDecoderInfos(mime,secure,tunneling),mime));
        player=new ExoPlayer.Builder(source,renderers).setLooper(worker.getLooper()).setLoadControl(new DefaultLoadControl.Builder()
            .setTargetBufferBytes(12*1024*1024).setBufferDurationsMs(5000,15000,500,1000).setPrioritizeTimeOverSizeThresholds(false).build()).build();
        player.setRepeatMode(Player.REPEAT_MODE_ONE);player.setVideoSurface(surface);player.setAudioAttributes(AudioAttributes.DEFAULT,false);
        player.addAnalyticsListener(new com.google.android.exoplayer2.analytics.AnalyticsListener(){
            @Override public void onVideoDecoderInitialized(EventTime time,String name,long at,long duration){
                log.record(false,"wallpaper.video.decoder",context+" backend=legacy_exoplayer codec="+VideoDiagnostics.token(name)+" mode=software initMs="+duration);
            }
        });
        player.addListener(new Player.Listener(){
            @Override public void onRenderedFirstFrame(){events.ready();}
            @Override public void onVideoSizeChanged(VideoSize size){events.size(size.width,size.height,size.pixelWidthHeightRatio);}
            @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition,Player.PositionInfo newPosition,int reason){
                if(reason==Player.DISCONTINUITY_REASON_AUTO_TRANSITION)log.record(false,"wallpaper.video.loop",context+" backend=legacy_exoplayer count="+(++loops)+" fromMs="+oldPosition.positionMs+" toMs="+newPosition.positionMs+" evidence=player_timeline_transition");
            }
            @Override public void onPlayerError(PlaybackException error){
                log.record(true,"wallpaper.video.player_error",context+" backend=legacy_exoplayer error="+VideoDiagnostics.token(error.getErrorCodeName())+" code="+error.errorCode+" "+VideoDiagnostics.causes(error));
                if(releasing){releaseTimedOut=true;return;}
                VideoWallpaperPlayer.Failure reason=error.errorCode==PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND||error.errorCode==PlaybackException.ERROR_CODE_IO_NO_PERMISSION?VideoWallpaperPlayer.Failure.FILE:
                    error.errorCode==PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED||error.errorCode==PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES?VideoWallpaperPlayer.Failure.UNSUPPORTED:VideoWallpaperPlayer.Failure.DECODE;
                events.failed(reason);
            }
        });
        player.setMediaItem(MediaItem.fromUri(uri));
    }
    static List<MediaCodecInfo> softwareCodecs(List<MediaCodecInfo> codecs,String mime){
        if(!mime.startsWith("video/"))return codecs;
        List<MediaCodecInfo> result=new ArrayList<>();for(MediaCodecInfo codec:codecs)if(codec.softwareOnly)result.add(codec);return result;
    }
    static boolean available(FileDescriptor input,long offset,long length)throws IOException{
        MediaExtractor extractor=new MediaExtractor();
        try{
            extractor.setDataSource(input,offset,length);
            for(int index=0;index<extractor.getTrackCount();index++){
                String mime=extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME);
                if(mime!=null&&mime.startsWith("video/")&&!softwareCodecs(MediaCodecSelector.DEFAULT.getDecoderInfos(mime,false,false),mime).isEmpty())return true;
            }
            return false;
        }catch(MediaCodecUtil.DecoderQueryException e){throw new IOException("Software decoder query failed",e);}
        finally{extractor.release();}
    }
    @Override public void prepare(){player.prepare();}
    @Override public void setSound(boolean sound){player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,!sound).build());}
    @Override public void setVolume(float value){player.setVolume(value);}
    @Override public void setPaused(boolean paused){player.setPlayWhenReady(!paused);}
    @Override public void release(Runnable completed){
        releasing=true;Thread decoder=player.getPlaybackLooper().getThread();
        try{player.release();}catch(RuntimeException error){releaseTimedOut=true;log.record(true,"wallpaper.video.release_failed",context+" backend=legacy_exoplayer "+VideoDiagnostics.failureDetails(error));}
        if(!releaseTimedOut){completed.run();return;}
        PlaybackHandoff.awaitTermination(decoder,()->worker.post(completed));
    }
}
