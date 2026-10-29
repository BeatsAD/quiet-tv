package home.quiet.tv;

import android.net.Uri;
import android.os.Handler;
import android.content.Context;
import android.media.MediaFormat;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer.DecoderInitializationException;
import androidx.media3.exoplayer.mediacodec.MediaCodecDecoderException;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import android.media.MediaCodec;
import android.os.Build;
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
    private final Handler worker;
    private final String context;
    private boolean detaching,detachFailed,releasing,releaseTimedOut;
    private String decoder="unknown";
    private boolean rendered;
    WallpaperPlayback(Context source,Handler worker,Surface surface,File file,WallpaperCatalog.Entry entry,String context,VideoWallpaperPlayer.PlaybackEvents events){
        this.worker=worker;this.context=context;
        boolean manualRotation=VideoGeometry.manualRotation(android.os.Build.VERSION.SDK_INT,entry.rotation);
        DefaultRenderersFactory renderers=new DefaultRenderersFactory(source){
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
        player=new ExoPlayer.Builder(source,renderers)
            .setLooper(worker.getLooper())
            .setLoadControl(new DefaultLoadControl.Builder().setTargetBufferBytes(12*1024*1024)
                .setBufferDurationsMs(5000,15000,500,1000).setPrioritizeTimeOverSizeThresholds(false).build()).build();
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,true).build());
        player.setVolume(0);player.setRepeatMode(Player.REPEAT_MODE_ONE);player.setVideoSurface(surface);
        player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
        player.addAnalyticsListener(new AnalyticsListener(){
            @Override public void onVideoInputFormatChanged(EventTime time,Format format,DecoderReuseEvaluation reuse){
                DiagnosticLog.info("wallpaper.video.input_format",context+" mime="+VideoDiagnostics.mime(format.sampleMimeType)+" codecs="+VideoDiagnostics.token(format.codecs)
                    +" size="+format.width+"x"+format.height+" fps="+format.frameRate+" averageBitrate="+format.averageBitrate+" peakBitrate="+format.peakBitrate);
            }
            @Override public void onVideoDecoderInitialized(EventTime time,String name,long initializedAt,long durationMs){
                decoder=VideoDiagnostics.token(name);DiagnosticLog.info("wallpaper.video.decoder",context+" backend=media3 codec="+decoder+" initMs="+durationMs);
            }
        });
        player.addListener(new Player.Listener(){
            @Override public void onVideoSizeChanged(VideoSize size){events.size(size.width,size.height,size.pixelWidthHeightRatio);}
            @Override public void onRenderedFirstFrame(){rendered=true;events.ready();}
            @Override public void onPlayerError(PlaybackException error){
                DiagnosticLog.error("wallpaper.video.player_error",context+" backend=media3 stage="+(detaching?"detach":releasing?"release":rendered?"playback":"prepare")+" codec="+decoder+" error="+VideoDiagnostics.token(error.getErrorCodeName())+" code="+error.errorCode
                    +" state="+player.getPlaybackState()+" positionMs="+player.getCurrentPosition()+" "+VideoDiagnostics.causes(error),error);
                logDecoderCauses(context,error);
                if(detaching){detachFailed=true;return;}
                if(releasing){releaseTimedOut=true;return;}
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
    private static void logDecoderCauses(String context,Throwable error){
        for(int n=0;error!=null&&n<6;n++,error=error.getCause()){
            StringBuilder details=new StringBuilder();
            if(error instanceof ExoTimeoutException)details.append(" timeoutOperation=").append(((ExoTimeoutException)error).timeoutOperation);
            if(error instanceof DecoderInitializationException){
                DecoderInitializationException e=(DecoderInitializationException)error;
                details.append(" decoderInit=true codec=").append(VideoDiagnostics.token(e.codecInfo==null?null:e.codecInfo.name))
                    .append(" mime=").append(VideoDiagnostics.mime(e.mimeType)).append(" secureRequired=").append(e.secureDecoderRequired)
                    .append(" diagnostic=").append(VideoDiagnostics.token(e.diagnosticInfo));
                DecoderInitializationException fallback=e.fallbackDecoderInitializationException;
                if(fallback!=null)details.append(" fallbackCodec=").append(VideoDiagnostics.token(fallback.codecInfo==null?null:fallback.codecInfo.name))
                    .append(" fallbackDiagnostic=").append(VideoDiagnostics.token(fallback.diagnosticInfo));
            }
            if(error instanceof MediaCodecDecoderException){
                MediaCodecDecoderException e=(MediaCodecDecoderException)error;
                details.append(" codec=").append(VideoDiagnostics.token(e.codecInfo==null?null:e.codecInfo.name))
                    .append(" codecError=").append(e.errorCode).append(" diagnostic=").append(VideoDiagnostics.token(e.diagnosticInfo));
            }
            if(error instanceof MediaCodec.CodecException){
                MediaCodec.CodecException e=(MediaCodec.CodecException)error;
                details.append(" diagnostic=").append(VideoDiagnostics.token(e.getDiagnosticInfo()))
                    .append(" recoverable=").append(e.isRecoverable()).append(" transient=").append(e.isTransient());
                if(Build.VERSION.SDK_INT>=23)details.append(" platformCode=").append(Api23.code(e));
            }
            if(details.length()>0)DiagnosticLog.info("wallpaper.video.codec_error",context+" cause="+n+details);
            if(error.getCause()==error)break;
        }
    }
    @android.annotation.TargetApi(23) private static final class Api23 {static int code(MediaCodec.CodecException e){return e.getErrorCode();}}
    void prepare()throws java.io.IOException{player.prepare();}
    void setSound(boolean enabled){
        player.setVolume(enabled?1f:0f);
        player.setAudioAttributes(AudioAttributes.DEFAULT,enabled);
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,!enabled).build());
    }
    void setPaused(boolean paused){player.setPlayWhenReady(!paused);}
    boolean detachOutput(){
        if(detachFailed)return false;
        detaching=true;
        try{player.clearVideoSurface();return !detachFailed;}
        catch(RuntimeException e){detachFailed=true;DiagnosticLog.error("wallpaper.video.detach_failed",context+" "+VideoDiagnostics.failureDetails(e),e);return false;}
        finally{detaching=false;}
    }
    void release(Runnable completed){
        releasing=true;Thread decoderThread=player.getPlaybackLooper().getThread();
        try{player.release();}
        catch(RuntimeException e){releaseTimedOut=true;DiagnosticLog.error("wallpaper.video.release_failed",context+" "+VideoDiagnostics.failureDetails(e),e);}
        if(!releaseTimedOut){completed.run();return;}
        // release() may return before the internal thread has released its renderer/codec.
        // Keep its Surface alive and hold subsequent decoder work behind this acknowledgement.
        DiagnosticLog.info("wallpaper.video.release_pending",context+" reason=awaiting_decoder_thread");
        PlaybackHandoff.awaitTermination(decoderThread,()->worker.post(()->{
            DiagnosticLog.info("wallpaper.video.release_complete",context);completed.run();
        }));
    }
}
