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

/** Local-only Media3 playback, with bounded buffering and no audio decoder. */
@SuppressLint("UnsafeOptInUsageError")
final class WallpaperEngine implements WallpaperPlayback.LocalPlayer {
    interface Diagnostics {void record(boolean error,String event,String detail);}
    private final Diagnostics log;
    private final ExoPlayer player;
    private final Handler worker;
    private final String context;
    private boolean releasing,releaseTimedOut;
    private String decoder="unknown";
    private boolean rendered;
    private int loops;
    private volatile boolean softwareAvailable;
    private volatile java.util.Set<String> softwareNames=java.util.Collections.emptySet();
    private boolean softwareDecoder;
    WallpaperEngine(Context source,Handler worker,Surface surface,Uri uri,int rotation,String context,boolean softwareOnly,VideoWallpaperPlayer.PlaybackEvents events,Diagnostics log){
        this.worker=worker;this.context=context;this.log=log;
        boolean manualRotation=VideoGeometry.manualRotation(android.os.Build.VERSION.SDK_INT,rotation);
        DefaultRenderersFactory renderers=new DefaultRenderersFactory(source){
            @Override protected void buildVideoRenderers(Context context,int extensions,MediaCodecSelector selector,boolean fallback,Handler handler,VideoRendererEventListener listener,long joiningMs,ArrayList<Renderer> output){
                output.add(new MediaCodecVideoRenderer(new MediaCodecVideoRenderer.Builder(context).setCodecAdapterFactory(getCodecAdapterFactory()).setMediaCodecSelector(selector).setEnableDecoderFallback(fallback).setEventHandler(handler).setEventListener(listener).setAllowedJoiningTimeMs(joiningMs)){
                    @Override protected java.util.List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> getDecoderInfos(MediaCodecSelector selector,Format format,boolean secure)throws androidx.media3.exoplayer.mediacodec.MediaCodecUtil.DecoderQueryException{
                        java.util.List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> codecs=localDecoders(super.getDecoderInfos(selector,format,secure),format,softwareOnly);
                        java.util.Set<String> names=new java.util.HashSet<>();
                        for(androidx.media3.exoplayer.mediacodec.MediaCodecInfo codec:codecs)if(codec.softwareOnly)names.add(codec.name);
                        softwareNames=names;softwareAvailable=!names.isEmpty();
                        events.softwareDecoderAvailable(!softwareOnly&&softwareAvailable&&!codecs.isEmpty()&&!codecs.get(0).softwareOnly);
                        return codecs;
                    }
                    @Override protected androidx.media3.exoplayer.mediacodec.MediaCodecAdapter.Configuration getMediaCodecConfiguration(androidx.media3.exoplayer.mediacodec.MediaCodecInfo codec,Format format,android.media.MediaCrypto crypto,float rate){
                        androidx.media3.exoplayer.mediacodec.MediaCodecAdapter.Configuration value=super.getMediaCodecConfiguration(codec,format,crypto,rate);
                        if(configureLocalVideo(value.mediaFormat,codec.name))info("wallpaper.video.codec_workaround",WallpaperEngine.this.context+" codec="+codec.name+" adaptive=false reason=sony_mtk_hevc_fixed_size");
                        return value;
                    }
                    @Override protected MediaFormat getMediaFormat(Format format,String mime,CodecMaxValues maximum,float rate,boolean workaround,int session){
                        MediaFormat value=super.getMediaFormat(format,mime,maximum,rate,workaround,session);
                        // API21-26 native rotation can change the buffer's aspect. Rotate once in the texture instead.
                        if(manualRotation)value.setInteger(MediaFormat.KEY_ROTATION,0);
                        return value;
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
                info("wallpaper.video.input_format",context+" mime="+VideoDiagnostics.mime(format.sampleMimeType)+" codecs="+VideoDiagnostics.token(format.codecs)
                    +" size="+format.width+"x"+format.height+" fps="+format.frameRate+" averageBitrate="+format.averageBitrate+" peakBitrate="+format.peakBitrate);
            }
            @Override public void onVideoDecoderInitialized(EventTime time,String name,long initializedAt,long durationMs){
                decoder=VideoDiagnostics.token(name);softwareDecoder=softwareNames.contains(name);
                events.softwareDecoderAvailable(!softwareOnly&&!softwareDecoder&&softwareAvailable);
                info("wallpaper.video.decoder",context+" backend=media3 codec="+decoder+" mode="+(softwareDecoder?"software":"hardware")+" initMs="+durationMs);
            }
            @Override public void onVideoDisabled(EventTime time,androidx.media3.exoplayer.DecoderCounters counters){
                info("wallpaper.video.counters",context+" rendered="+counters.renderedOutputBufferCount+" dropped="+counters.droppedBufferCount+" skipped="+counters.skippedOutputBufferCount+" evidence=decoder_counters_not_presentation");
            }
        });
        player.addListener(new Player.Listener(){
            @Override public void onPlaybackStateChanged(int state){info("wallpaper.video.playback",context+" state="+state+" playWhenReady="+player.getPlayWhenReady()+" isPlaying="+player.isPlaying()+" suppression="+player.getPlaybackSuppressionReason()+" positionMs="+player.getCurrentPosition());}
            @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition,Player.PositionInfo newPosition,int reason){
                if(reason==Player.DISCONTINUITY_REASON_AUTO_TRANSITION){
                    androidx.media3.exoplayer.DecoderCounters counters=player.getVideoDecoderCounters();
                    if(counters!=null)counters.ensureUpdated();
                    info("wallpaper.video.loop",context+" count="+(++loops)+" fromMs="+oldPosition.positionMs+" toMs="+newPosition.positionMs
                        +" rendered="+(counters==null?-1:counters.renderedOutputBufferCount)+" dropped="+(counters==null?-1:counters.droppedBufferCount)+" evidence=player_timeline_transition_and_decoder_counters");
                }
            }
            @Override public void onVideoSizeChanged(VideoSize size){events.size(size.width,size.height,size.pixelWidthHeightRatio);}
            @Override public void onRenderedFirstFrame(){rendered=true;events.ready();}
            @Override public void onPlayerError(PlaybackException error){
                error("wallpaper.video.player_error",context+" backend=media3 stage="+(releasing?"release":rendered?"playback":"prepare")+" codec="+decoder+" error="+VideoDiagnostics.token(error.getErrorCodeName())+" code="+error.errorCode
                    +" state="+player.getPlaybackState()+" positionMs="+player.getCurrentPosition()+" "+VideoDiagnostics.causes(error),error);
                logDecoderCauses(context,error);
                if(releasing){releaseTimedOut=true;return;}
                if(retrySoftware(error,softwareAvailable,softwareOnly||softwareDecoder)){
                    events.retrySoftware();return;
                }
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
        player.setMediaItem(MediaItem.fromUri(uri));
    }
    static java.util.List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> localDecoders(java.util.List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> codecs,Format format)throws androidx.media3.exoplayer.mediacodec.MediaCodecUtil.DecoderQueryException{
        return localDecoders(codecs,format,false);
    }
    static java.util.List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> localDecoders(java.util.List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> codecs,Format format,boolean softwareOnly)throws androidx.media3.exoplayer.mediacodec.MediaCodecUtil.DecoderQueryException{
        java.util.List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> hardware=new ArrayList<>(),software=new ArrayList<>(),unsupported=new ArrayList<>();
        for(androidx.media3.exoplayer.mediacodec.MediaCodecInfo codec:codecs){
            if(codec.softwareOnly)software.add(codec);
            else if(!softwareOnly){if(codec.isFormatSupported(format))hardware.add(codec);else unsupported.add(codec);}
        }
        hardware.addAll(software);hardware.addAll(unsupported);return hardware;
    }
    static boolean retrySoftware(PlaybackException error,boolean available,boolean alreadySoftware){
        if(!available||alreadySoftware||!(error instanceof ExoPlaybackException))return false;
        ExoPlaybackException renderer=(ExoPlaybackException)error;
        if(renderer.type!=ExoPlaybackException.TYPE_RENDERER||renderer.rendererFormat==null||renderer.rendererFormat.sampleMimeType==null||!renderer.rendererFormat.sampleMimeType.startsWith("video/"))return false;
        // Initialization already tries the available decoders. Only a video decoding failure
        // during playback needs one fresh software-only attempt in a retired codec process.
        return error.errorCode==PlaybackException.ERROR_CODE_DECODING_FAILED
            ||error.errorCode==PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES
            ||error.errorCode==PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED;
    }
    static boolean configureLocalVideo(MediaFormat value,String codec){
        if(Build.VERSION.SDK_INT!=31||!"Sony".equalsIgnoreCase(Build.MANUFACTURER)||!"OMX.MTK.VIDEO.DECODER.HEVC".equals(codec))return false;
        // This firmware's adaptive/seamless path fails on multi-reference HEVC streams.
        // Each wallpaper owns one fixed-size codec, so adaptive resolution is unnecessary.
        value.removeKey("max-width");value.removeKey("max-height");return true;
    }
    private void logDecoderCauses(String context,Throwable error){
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
                if(e instanceof MediaCodecVideoDecoderException)details.append(" surfaceValid=").append(((MediaCodecVideoDecoderException)e).isSurfaceValid);
            }
            if(error instanceof MediaCodec.CodecException){
                MediaCodec.CodecException e=(MediaCodec.CodecException)error;
                details.append(" diagnostic=").append(VideoDiagnostics.token(e.getDiagnosticInfo()))
                    .append(" recoverable=").append(e.isRecoverable()).append(" transient=").append(e.isTransient());
                if(Build.VERSION.SDK_INT>=23)details.append(" platformCode=").append(Api23.code(e));
            }
            if(details.length()>0)info("wallpaper.video.codec_error",context+" cause="+n+details);
            if(error.getCause()==error)break;
        }
    }
    @android.annotation.TargetApi(23) private static final class Api23 {static int code(MediaCodec.CodecException e){return e.getErrorCode();}}
    private void info(String event,String detail){log.record(false,event,detail);}
    private void error(String event,String detail,Throwable error){log.record(true,event,detail+" "+DiagnosticLog.trace(error));}
    @Override public void prepare()throws java.io.IOException{player.prepare();}
    @Override public void setSound(boolean enabled){
        player.setVolume(enabled?1f:0f);
        player.setAudioAttributes(AudioAttributes.DEFAULT,enabled);
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,!enabled).build());
    }
    @Override public void setPaused(boolean paused){player.setPlayWhenReady(!paused);}
    @Override public void release(Runnable completed){
        releasing=true;Thread decoderThread=player.getPlaybackLooper().getThread();
        try{player.release();}
        catch(RuntimeException e){releaseTimedOut=true;error("wallpaper.video.release_failed",context+" "+VideoDiagnostics.failureDetails(e),e);}
        if(!releaseTimedOut){completed.run();return;}
        // release() may return before the internal thread has released its renderer/codec.
        // Keep its Surface alive and hold subsequent decoder work behind this acknowledgement.
        info("wallpaper.video.release_pending",context+" reason=awaiting_decoder_thread");
        PlaybackHandoff.awaitTermination(decoderThread,()->worker.post(()->{
            info("wallpaper.video.release_complete",context);completed.run();
        }));
    }
}
