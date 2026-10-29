package home.quiet.tv;

import android.annotation.TargetApi;
import android.media.*;
import android.os.Build;
import java.io.*;
import java.nio.ByteBuffer;
import org.json.*;

/** Bounded local-video inspection. No network, transcoding or full-video buffering. */
final class WallpaperVideo {
    static final long MAX_UPLOAD=200L*1024*1024;
    static final class Info {
        int width,height,rotation;long durationMs;float fps,pixelRatio=1f;String mime;
    }
    static String capabilities() {
        try{return new JSONObject().put("purpose","wallpaper").put("imageMaxBytes",WallpaperUploadServer.MAX_UPLOAD)
            .put("videoMaxBytes",MAX_UPLOAD).put("videoMaxWidth",Build.VERSION.SDK_INT>=21?3840:1920)
            .put("videoMaxHeight",Build.VERSION.SDK_INT>=21?2160:1080).put("videoMaxFps",60).put("videoFormats",new JSONArray(java.util.Arrays.asList("mp4","mov","mkv","webm")))
            .put("hevc",Build.VERSION.SDK_INT>=21&&Api16.legacyDecoder("video/hevc")).toString();}
        catch(JSONException e){throw new IllegalStateException(e);}
    }
    static Info inspect(File file)throws IOException {return inspect(file,"");}
    static Info inspect(File file,String context)throws IOException {
        String details=context+" bytes="+file.length()+" container="+VideoDiagnostics.container(file);
        try{
            if(file.length()<12||file.length()>MAX_UPLOAD)throw rejected("file","file_size_invalid");
            return WallpaperVideoProbe.inspect(file,context);
        }catch(IOException|RuntimeException e){
            DiagnosticLog.error("wallpaper.video.inspect_failed",details+" "+VideoDiagnostics.failureDetails(e),e);throw e;
        }
    }
    private static IOException invalid(){return rejected("codec_data","invalid_codec_data");}
    private static VideoDiagnostics.Failure rejected(String stage,String reason){return VideoDiagnostics.failure(stage,reason,"视频无法解析，请使用 MP4、MOV、MKV 或 WebM / Unable to read video; use MP4, MOV, MKV or WebM");}
    @TargetApi(16) static final class Api16 {
    static Info inspect(File file,String context) throws IOException {
        MediaExtractor extractor=new MediaExtractor();MediaMetadataRetriever metadata=new MediaMetadataRetriever();
        String stage="extractor_open";
        try{
            extractor.setDataSource(file.getPath());stage="metadata_open";metadata.setDataSource(file.getPath());
            stage="tracks";MediaFormat format=null;int track=-1,count=extractor.getTrackCount();
            DiagnosticLog.info("wallpaper.video.tracks",context+" count="+count);
            for(int n=0;n<count;n++){
                MediaFormat candidate=extractor.getTrackFormat(n);String mime=candidate.getString(MediaFormat.KEY_MIME);
                if(n<8)DiagnosticLog.info("wallpaper.video.track",context+" index="+n+" mime="+VideoDiagnostics.mime(mime));
                if(mime!=null&&mime.startsWith("video/")){if(track!=-1)throw rejected(stage,"multiple_video_tracks");format=candidate;track=n;}
            }
            if(format==null)throw rejected(stage,"no_video_track");
            stage="metadata";Info info=new Info();info.mime=format.getString(MediaFormat.KEY_MIME);
            info.width=format.getInteger(MediaFormat.KEY_WIDTH);info.height=format.getInteger(MediaFormat.KEY_HEIGHT);
            if(format.containsKey("sar-width")&&format.containsKey("sar-height")){int sw=format.getInteger("sar-width"),sh=format.getInteger("sar-height");if(sw>0&&sh>0)info.pixelRatio=sw/(float)sh;}
            else info.pixelRatio=VideoGeometry.pixelRatio(file,1f);
            info.durationMs=Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            String rotation=Build.VERSION.SDK_INT>=17?metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION):null;info.rotation=rotation==null?(Build.VERSION.SDK_INT<17?LegacyVideoProbe.read(file).rotation:0):Integer.parseInt(rotation);
            DiagnosticLog.info("wallpaper.video.format",context+" mime="+VideoDiagnostics.mime(info.mime)+" size="+info.width+"x"+info.height+" rotation="+info.rotation+" durationMs="+info.durationMs
                +" profile="+integer(format,"profile")+" level="+integer(format,"level")+" bitrate="+integer(format,"bitrate")+" colorTransfer="+integer(format,"color-transfer"));
            if(info.width<=0||info.height<=0||info.durationMs<=0)throw rejected(stage,"invalid_dimensions_or_duration");
            if(Math.max(info.width,info.height)>3840||Math.min(info.width,info.height)>2160)throw rejected(stage,"resolution_limit");
            if(info.rotation%90!=0)throw rejected(stage,"invalid_rotation");
            if(Build.VERSION.SDK_INT<21&&(Math.max(info.width,info.height)>1920||Math.min(info.width,info.height)>1080))throw VideoDiagnostics.failure(stage,"legacy_resolution_limit","此系统请使用不超过 1080p 的 H.264 视频 / Use H.264 up to 1080p on this system");
            if(format.containsKey("color-transfer")){int transfer=format.getInteger("color-transfer");if(transfer==6||transfer==7)throw VideoDiagnostics.failure("color","hdr_unsupported","请使用 SDR 视频，暂不支持 HDR / Use SDR video; HDR is not supported");}
            stage="profile";checkProfile(format,info.mime);
            stage="samples";extractor.selectTrack(track);
            if(Build.VERSION.SDK_INT>=18&&extractor.getPsshInfo()!=null&&!extractor.getPsshInfo().isEmpty())throw rejected(stage,"drm_metadata");
            // Timestamp scan is bounded; preserves the format's declared rate when available.
            long first=Long.MAX_VALUE,last=-1;int frames=0;
            while(frames<121&&extractor.getSampleTime()>=0){if((extractor.getSampleFlags()&MediaExtractor.SAMPLE_FLAG_ENCRYPTED)!=0)throw rejected(stage,"encrypted_sample");long time=extractor.getSampleTime();first=Math.min(first,time);last=Math.max(last,time);frames++;if(!extractor.advance())break;}
            float sampled=frames>1&&last>first?(frames-1)*1000000f/(last-first):0;
            if(format.containsKey(MediaFormat.KEY_FRAME_RATE))info.fps=((Number)number(format,MediaFormat.KEY_FRAME_RATE)).floatValue();
            if(info.fps<=0)info.fps=sampled;
            DiagnosticLog.info("wallpaper.video.samples",context+" count="+frames+" fps="+info.fps+" sampledFps="+sampled);
            if((Float.isNaN(info.fps)||Float.isInfinite(info.fps))||info.fps<=0||info.fps>60.1f||sampled>60.5f)throw VideoDiagnostics.failure("frame_rate","invalid_or_excessive_frame_rate","请使用不超过 60 FPS 的视频 / Use video up to 60 FPS");
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
    static void checkProfile(MediaFormat format,String mime) throws IOException {
        // Container support is separate from decoder support. Keep the SDR/8-bit policy,
        // but do not reject VP8/VP9/AV1 merely because they have no AVC/HEVC SPS.
        if(format.containsKey("profile")){
            int profile=format.getInteger("profile");
            if(("video/x-vnd.on2.vp9".equals(mime)&&profile!=1&&profile!=2)||("video/av01".equals(mime)&&profile!=1))
                throw VideoDiagnostics.failure("profile","unsupported_profile_or_bit_depth","请使用 SDR 8bit 视频 / Use SDR 8-bit video");
        }
        if(!"video/avc".equals(mime)&&!"video/hevc".equals(mime))return;
        ByteBuffer data=format.getByteBuffer("csd-0");if(data==null)return;
        byte[] raw=new byte[data.remaining()];data.duplicate().get(raw);
        for(int i=0;i+4<raw.length;i++){
            int start=0;if(raw[i]==0&&raw[i+1]==0&&raw[i+2]==1)start=i+3;
            else if(i+5<raw.length&&raw[i]==0&&raw[i+1]==0&&raw[i+2]==0&&raw[i+3]==1)start=i+4;
            if(start==0)continue;
            if("video/avc".equals(mime)&&(raw[start]&31)==7){
                Bits bits=new Bits(raw,start+1);int profile=bits.read(8);bits.read(8);bits.read(8);bits.ue();
                if(profile!=66&&profile!=77&&profile!=88&&profile!=100)throw VideoDiagnostics.failure("profile","unsupported_profile_or_bit_depth","请使用 SDR 8bit 视频 / Use SDR 8-bit video");
                if(profile==100){int chroma=bits.ue();if(chroma>1)throw rejected("profile","unsupported_chroma");if(bits.ue()!=0||bits.ue()!=0)throw rejected("profile","unsupported_bit_depth");}return;
            }
            if("video/hevc".equals(mime)&&((raw[start]>>1)&63)==33){
                Bits bits=new Bits(raw,start+2);bits.read(8);bits.read(2);bits.read(1);if(bits.read(5)!=1)throw VideoDiagnostics.failure("profile","unsupported_hevc_profile","请使用 HEVC Main 8bit 或 H.264 视频 / Use HEVC Main 8bit or H.264");return;
            }
        }
        // Some extractors expose codec configuration without Annex-B start codes.
        // Actual preview remains authoritative for decoder compatibility.
    }
    }
    static final class Bits {
        final byte[] bytes;int bit;
        Bits(byte[] raw,int start){ByteArrayOutputStream output=new ByteArrayOutputStream();int zeros=0;for(int i=start;i<raw.length;i++){int v=raw[i]&255;if(zeros>=2&&v==3){zeros=0;continue;}output.write(v);zeros=v==0?zeros+1:0;}bytes=output.toByteArray();}
        int read(int count) throws IOException {if(count<0||bit+count>bytes.length*8)throw invalid();int value=0;while(count-->0){value=(value<<1)|((bytes[bit/8]>>(7-bit%8))&1);bit++;}return value;}
        int ue() throws IOException {int zeros=0;while(read(1)==0){if(++zeros>20)throw invalid();}return (1<<zeros)-1+read(zeros);}
    }
}
