package home.quiet.tv;

import android.annotation.TargetApi;
import android.media.*;
import android.os.Build;
import java.io.*;
import org.json.*;

/** Bounded local-video inspection. No network, transcoding or full-video buffering. */
final class WallpaperVideo {
    static final class Info {
        int width,height,rotation;long durationMs;float fps,pixelRatio=1f;String mime;
    }
    static String capabilities() {
        try{return new JSONObject().put("purpose","wallpaper").put("imageMaxBytes",WallpaperUploadServer.MAX_UPLOAD)
            .put("videoMaxBytes",0).put("videoFormats",new JSONArray(java.util.Arrays.asList("mp4","mov","mkv","webm","m4v","3gp","3g2","ts","m2ts","mts","mpg","mpeg","vob","flv")))
            .put("hevc",Build.VERSION.SDK_INT>=21&&Api16.legacyDecoder("video/hevc")).toString();}
        catch(JSONException e){throw new IllegalStateException(e);}
    }
    static Info inspect(File file)throws IOException {return inspect(file,"");}
    static Info inspect(File file,String context)throws IOException {
        String details=context+" bytes="+file.length()+" container="+VideoDiagnostics.container(file);
        try{
            if(file.length()==0)throw rejected("file","file_size_invalid");
            return WallpaperVideoProbe.inspect(file,context);
        }catch(IOException|RuntimeException e){
            DiagnosticLog.error("wallpaper.video.inspect_failed",details+" "+VideoDiagnostics.failureDetails(e),e);throw e;
        }
    }
    static float frameRate(float reported,float sampled) {
        if(reported>0&&!Float.isInfinite(reported))return reported;
        return sampled>0&&!Float.isInfinite(sampled)?sampled:0;
    }
    static VideoDiagnostics.Failure rejected(String stage,String reason){
        String message;
        switch(reason){
            case "file_size_invalid":message="视频文件为空，请重新选择 / Video file is empty; choose another file";break;
            case "no_video_track":message="文件中未找到视频轨道，请选择包含画面的视频 / No video track found; choose a file containing video";break;
            case "encrypted_sample":case "encrypted_track":case "drm_metadata":message="暂不支持加密视频，请使用未加密的视频 / Encrypted video is not supported; use unencrypted video";break;
            default:message="无法解析视频容器或编码信息，请尝试其他视频；详情见诊断日志 / Unable to parse video container or codec data; try another video; see diagnostics";
        }
        return VideoDiagnostics.failure(stage,reason,message);
    }
    @TargetApi(16) static final class Api16 {
    static Info inspect(File file,String context) throws IOException {
        MediaExtractor extractor=new MediaExtractor();MediaMetadataRetriever metadata=new MediaMetadataRetriever();
        String stage="extractor_open";
        try{
            extractor.setDataSource(file.getPath());
            stage="tracks";MediaFormat format=null;int track=-1,count=extractor.getTrackCount();
            DiagnosticLog.info("wallpaper.video.tracks",context+" count="+count);
            for(int n=0;n<count;n++){
                MediaFormat candidate=extractor.getTrackFormat(n);String mime=candidate.getString(MediaFormat.KEY_MIME);
                if(n<8)DiagnosticLog.info("wallpaper.video.track",context+" index="+n+" mime="+VideoDiagnostics.mime(mime));
                if(track==-1&&mime!=null&&mime.startsWith("video/")){format=candidate;track=n;}
            }
            if(format==null)throw rejected(stage,"no_video_track");
            stage="metadata";Info info=new Info();info.mime=format.getString(MediaFormat.KEY_MIME);
            info.width=format.containsKey(MediaFormat.KEY_WIDTH)?Math.max(0,format.getInteger(MediaFormat.KEY_WIDTH)):0;
            info.height=format.containsKey(MediaFormat.KEY_HEIGHT)?Math.max(0,format.getInteger(MediaFormat.KEY_HEIGHT)):0;
            if(format.containsKey("sar-width")&&format.containsKey("sar-height")){int sw=format.getInteger("sar-width"),sh=format.getInteger("sar-height");if(sw>0&&sh>0)info.pixelRatio=sw/(float)sh;}
            else info.pixelRatio=VideoGeometry.pixelRatio(file,1f);
            info.durationMs=format.containsKey(MediaFormat.KEY_DURATION)?Math.max(0,format.getLong(MediaFormat.KEY_DURATION)/1000):0;
            if(format.containsKey("rotation-degrees"))info.rotation=format.getInteger("rotation-degrees");
            try{
                metadata.setDataSource(file.getPath());
                String duration=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if(duration!=null)info.durationMs=Math.max(0,Long.parseLong(duration));
                String rotation=Build.VERSION.SDK_INT>=17?metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION):null;
                if(rotation!=null)info.rotation=Integer.parseInt(rotation);
                else if(Build.VERSION.SDK_INT<17)info.rotation=LegacyVideoProbe.read(file).rotation;
            }catch(IOException|RuntimeException e){DiagnosticLog.info("wallpaper.video.metadata_unavailable",context+" "+VideoDiagnostics.causes(e));}
            DiagnosticLog.info("wallpaper.video.format",context+" mime="+VideoDiagnostics.mime(info.mime)+" size="+info.width+"x"+info.height+" rotation="+info.rotation+" durationMs="+info.durationMs
                +" profile="+integer(format,"profile")+" level="+integer(format,"level")+" bitrate="+integer(format,"bitrate")+" colorTransfer="+integer(format,"color-transfer"));
            stage="samples";extractor.selectTrack(track);
            if(Build.VERSION.SDK_INT>=18&&extractor.getPsshInfo()!=null&&!extractor.getPsshInfo().isEmpty())throw rejected(stage,"drm_metadata");
            // Timestamp scan is bounded; preserves the format's declared rate when available.
            long first=Long.MAX_VALUE,last=-1;int frames=0;
            while(frames<121&&extractor.getSampleTime()>=0){if((extractor.getSampleFlags()&MediaExtractor.SAMPLE_FLAG_ENCRYPTED)!=0)throw rejected(stage,"encrypted_sample");long time=extractor.getSampleTime();first=Math.min(first,time);last=Math.max(last,time);frames++;if(!extractor.advance())break;}
            float sampled=frames>1&&last>first?(frames-1)*1000000f/(last-first):0;
            if(format.containsKey(MediaFormat.KEY_FRAME_RATE))info.fps=((Number)number(format,MediaFormat.KEY_FRAME_RATE)).floatValue();
            if(info.fps<=0)info.fps=sampled;
            DiagnosticLog.info("wallpaper.video.samples",context+" count="+frames+" fps="+info.fps+" sampledFps="+sampled);
            info.fps=frameRate(info.fps,sampled);
            if(format.containsKey("crypto-key"))throw rejected(stage,"encrypted_track");
            DiagnosticLog.info("wallpaper.video.inspect",context+" mime="+VideoDiagnostics.mime(info.mime)+" size="+info.width+"x"+info.height+" fps="+info.fps+" sampledFps="+sampled+" rotation="+info.rotation+" durationMs="+info.durationMs+" bytes="+file.length());
            DiagnosticLog.info("wallpaper.video.admission",context+" decision=defer_to_playback");
            return info;
        }catch(VideoDiagnostics.Failure e){throw e;}
        catch(IOException|RuntimeException e){
            VideoDiagnostics.Failure failure=rejected(stage,e instanceof IOException?"platform_io":"platform_metadata_error");failure.initCause(e);throw failure;
        }finally{extractor.release();try{metadata.release();}catch(IOException ignored){}}
    }
    private static String integer(MediaFormat format,String key){try{return format.containsKey(key)?Integer.toString(format.getInteger(key)):"unknown";}catch(RuntimeException ignored){return "unknown";}}
    private static Number number(MediaFormat format,String key){try{return format.getInteger(key);}catch(ClassCastException e){return format.getFloat(key);}}
    private static boolean legacyDecoder(String mime){
        for(int n=0;n<MediaCodecList.getCodecCount();n++){MediaCodecInfo codec=MediaCodecList.getCodecInfoAt(n);if(!codec.isEncoder())for(String type:codec.getSupportedTypes())if(mime.equals(type))return true;}return false;
    }
    }
}
