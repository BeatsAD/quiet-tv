package home.quiet.tv;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
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
            .put("videoMaxHeight",Build.VERSION.SDK_INT>=21?2160:1080).put("videoMaxFps",60).put("videoFormats",new JSONArray(new String[]{"mp4","mov","mkv","webm"}))
            .put("hevc",Build.VERSION.SDK_INT>=21&&Api21.hasHevc()).toString();}
        catch(JSONException e){throw new IllegalStateException(e);}
    }
    static Info inspect(File file) throws IOException {
        if(file.length()<12||file.length()>MAX_UPLOAD)throw invalid();
        MediaExtractor extractor=new MediaExtractor();MediaMetadataRetriever metadata=new MediaMetadataRetriever();
        try{
            extractor.setDataSource(file.getPath());metadata.setDataSource(file.getPath());
            MediaFormat format=null;int track=-1;
            for(int n=0;n<extractor.getTrackCount();n++){
                MediaFormat candidate=extractor.getTrackFormat(n);String mime=candidate.getString(MediaFormat.KEY_MIME);
                if(mime!=null&&mime.startsWith("video/")){if(track!=-1)throw invalid();format=candidate;track=n;}
            }
            if(format==null)throw invalid();
            Info info=new Info();info.mime=format.getString(MediaFormat.KEY_MIME);
            info.width=format.getInteger(MediaFormat.KEY_WIDTH);info.height=format.getInteger(MediaFormat.KEY_HEIGHT);
            if(format.containsKey("sar-width")&&format.containsKey("sar-height")){int sw=format.getInteger("sar-width"),sh=format.getInteger("sar-height");if(sw>0&&sh>0)info.pixelRatio=sw/(float)sh;}
            else info.pixelRatio=VideoGeometry.pixelRatio(file,1f);
            info.durationMs=Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            String rotation=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);info.rotation=rotation==null?0:Integer.parseInt(rotation);
            if(info.width<=0||info.height<=0||Math.max(info.width,info.height)>3840||Math.min(info.width,info.height)>2160||info.durationMs<=0||info.rotation%90!=0)throw invalid();
            if(Build.VERSION.SDK_INT<21&&(Math.max(info.width,info.height)>1920||Math.min(info.width,info.height)>1080))throw new IOException("此系统请使用不超过 1080p 的 H.264 视频 / Use H.264 up to 1080p on this system");
            if(format.containsKey("color-transfer")){int transfer=format.getInteger("color-transfer");if(transfer==6||transfer==7)throw new IOException("请使用 SDR 视频，暂不支持 HDR / Use SDR video; HDR is not supported");}
            checkProfile(format,info.mime);
            extractor.selectTrack(track);
            if(extractor.getPsshInfo()!=null&&!extractor.getPsshInfo().isEmpty())throw invalid();
            // Timestamp scan is bounded; preserves the format's declared rate when available.
            long first=Long.MAX_VALUE,last=-1;int frames=0;
            while(frames<121&&extractor.getSampleTime()>=0){if((extractor.getSampleFlags()&MediaExtractor.SAMPLE_FLAG_ENCRYPTED)!=0)throw invalid();long time=extractor.getSampleTime();first=Math.min(first,time);last=Math.max(last,time);frames++;if(!extractor.advance())break;}
            float sampled=frames>1&&last>first?(frames-1)*1000000f/(last-first):0;
            if(format.containsKey(MediaFormat.KEY_FRAME_RATE))info.fps=((Number)number(format,MediaFormat.KEY_FRAME_RATE)).floatValue();
            if(info.fps<=0)info.fps=sampled;
            if((Float.isNaN(info.fps)||Float.isInfinite(info.fps))||info.fps<=0||info.fps>60.1f||sampled>60.5f)throw new IOException("请使用不超过 60 FPS 的视频 / Use video up to 60 FPS");
            if(format.containsKey("crypto-key"))throw invalid();
            DiagnosticLog.info("wallpaper.video.inspect","mime="+info.mime+" size="+info.width+"x"+info.height+" fps="+info.fps+" sampledFps="+sampled+" rotation="+info.rotation+" durationMs="+info.durationMs+" bytes="+file.length());
            // Vendor capability declarations are diagnostic only. The TV's actual preview
            // decides compatibility, and applying a wallpaper waits for its first frame.
            if(Build.VERSION.SDK_INT>=21)try{Api21.supports(info);}catch(IOException ignored){}
            DiagnosticLog.info("wallpaper.video.admission","decision=defer_to_playback");
            return info;
        }catch(IOException e){if(e.getMessage()!=null&&e.getMessage().contains(" / "))throw e;throw invalid();}catch(RuntimeException e){throw invalid();}
        finally{extractor.release();try{metadata.release();}catch(IOException ignored){}}
    }
    private static Number number(MediaFormat format,String key){try{return format.getInteger(key);}catch(ClassCastException e){return format.getFloat(key);}}
    private static IOException invalid(){return new IOException("视频无法解析，请使用 MP4、MOV、MKV 或 WebM / Unable to read video; use MP4, MOV, MKV or WebM");}
    private static boolean legacyDecoder(String mime){
        for(int n=0;n<MediaCodecList.getCodecCount();n++){MediaCodecInfo codec=MediaCodecList.getCodecInfoAt(n);if(!codec.isEncoder())for(String type:codec.getSupportedTypes())if(mime.equals(type))return true;}return false;
    }
    private static void checkProfile(MediaFormat format,String mime) throws IOException {
        // Container support is separate from decoder support. Keep the SDR/8-bit policy,
        // but do not reject VP8/VP9/AV1 merely because they have no AVC/HEVC SPS.
        if(format.containsKey("profile")){
            int profile=format.getInteger("profile");
            if(("video/x-vnd.on2.vp9".equals(mime)&&profile!=1&&profile!=2)||("video/av01".equals(mime)&&profile!=1))
                throw new IOException("请使用 SDR 8bit 视频 / Use SDR 8-bit video");
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
                if(profile!=66&&profile!=77&&profile!=88&&profile!=100)throw new IOException("请使用 SDR 8bit 视频 / Use SDR 8-bit video");
                if(profile==100){int chroma=bits.ue();if(chroma>1)throw invalid();if(bits.ue()!=0||bits.ue()!=0)throw invalid();}return;
            }
            if("video/hevc".equals(mime)&&((raw[start]>>1)&63)==33){
                Bits bits=new Bits(raw,start+2);bits.read(8);bits.read(2);bits.read(1);if(bits.read(5)!=1)throw new IOException("请使用 HEVC Main 8bit 或 H.264 视频 / Use HEVC Main 8bit or H.264");return;
            }
        }
        // Some extractors expose codec configuration without Annex-B start codes.
        // The decoder capability check and actual preview remain authoritative.
    }
    private static final class Bits {
        final byte[] bytes;int bit;
        Bits(byte[] raw,int start){ByteArrayOutputStream output=new ByteArrayOutputStream();int zeros=0;for(int i=start;i<raw.length;i++){int v=raw[i]&255;if(zeros>=2&&v==3){zeros=0;continue;}output.write(v);zeros=v==0?zeros+1:0;}bytes=output.toByteArray();}
        int read(int count) throws IOException {if(count<0||bit+count>bytes.length*8)throw invalid();int value=0;while(count-->0){value=(value<<1)|((bytes[bit/8]>>(7-bit%8))&1);bit++;}return value;}
        int ue() throws IOException {int zeros=0;while(read(1)==0){if(++zeros>20)throw invalid();}return (1<<zeros)-1+read(zeros);}
    }
    static void poster(File video,File output,Info info) {
        MediaMetadataRetriever retriever=new MediaMetadataRetriever();Bitmap frame=null,small=null;
        try{
            retriever.setDataSource(video.getPath());int width=info.rotation%180==0?info.width:info.height,height=info.rotation%180==0?info.height:info.width;
            float scale=Math.min(1f,640f/Math.max(width,height));int w=Math.max(1,Math.round(width*scale)),h=Math.max(1,Math.round(height*scale));
            frame=Build.VERSION.SDK_INT>=27?Api27.frame(retriever,w,h):retriever.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if(frame==null)frame=Build.VERSION.SDK_INT>=27?retriever.getScaledFrameAtTime(-1,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,w,h):retriever.getFrameAtTime(-1);
            if(frame==null)return;
            // The retriever may already rotate the frame. Preserve its actual aspect instead of stretching it.
            scale=Math.min(1f,640f/Math.max(frame.getWidth(),frame.getHeight()));
            w=Math.max(1,Math.round(frame.getWidth()*scale));h=Math.max(1,Math.round(frame.getHeight()*scale));
            if(info.pixelRatio!=1f){float aspect=VideoGeometry.aspect(info.width,info.height,info.pixelRatio,info.rotation);w=aspect>=1?640:Math.max(1,Math.round(640*aspect));h=aspect>=1?Math.max(1,Math.round(640/aspect)):640;}
            small=Bitmap.createScaledBitmap(frame,w,h,true);
            try(FileOutputStream stream=new FileOutputStream(output)){if(!small.compress(Bitmap.CompressFormat.JPEG,85,stream))throw new IOException();stream.getFD().sync();}
        }catch(IOException|RuntimeException|OutOfMemoryError ignored){if(output.isFile()&&!output.delete())android.util.Log.w("QuietTV","Incomplete video poster retained");}
        finally{if(small!=null&&small!=frame)small.recycle();if(frame!=null)frame.recycle();try{retriever.release();}catch(IOException ignored){}}
    }
    static void poster(File video,File output,WallpaperCatalog.Entry entry){
        Info info=new Info();info.width=entry.width;info.height=entry.height;info.rotation=entry.rotation;info.pixelRatio=entry.pixelRatio;poster(video,output,info);
    }
    @TargetApi(21) private static final class Api21 {
        static boolean hasHevc(){return legacyDecoder("video/hevc");}
        static boolean supports(Info info)throws IOException{
            boolean queryFailed=false;int matching=0,eligible=0;
            MediaCodecInfo[] codecs;
            try{codecs=new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos();}
            catch(RuntimeException failure){DiagnosticLog.error("wallpaper.video.codec_query_failed",failure);throw unavailable();}
            for(MediaCodecInfo codec:codecs){
                try{
                    if(codec.isEncoder())continue;
                    boolean matches=false;for(String type:codec.getSupportedTypes())if(info.mime.equalsIgnoreCase(type)){matches=true;break;}
                    if(!matches)continue;matching++;
                    if((Math.max(info.width,info.height)>1920||Math.min(info.width,info.height)>1080)&&((Build.VERSION.SDK_INT>=29&&!Api29.hardware(codec))||codec.getName().startsWith("OMX.google.")||codec.getName().startsWith("c2.android."))){
                        DiagnosticLog.info("wallpaper.video.codec_skipped","codec="+codec.getName()+" reason=software_above_1080p");continue;
                    }
                    eligible++;MediaCodecInfo.VideoCapabilities video=codec.getCapabilitiesForType(info.mime).getVideoCapabilities();
                    if(video==null){queryFailed=true;DiagnosticLog.info("wallpaper.video.codec_query_failed","codec="+codec.getName()+" reason=missing_video_capabilities");continue;}
                    boolean supported=fits(video,info.width,info.height,info.fps,codec.getName());
                    if(!supported&&Math.max(info.width,info.height)<=1920&&Math.min(info.width,info.height)<=1080)supported=fits(video,info.height,info.width,info.fps,codec.getName());
                    if(supported){DiagnosticLog.info("wallpaper.video.codec_check","result=supported codec="+codec.getName()+" evidence=declared_capabilities_only");return true;}
                }catch(RuntimeException failure){queryFailed=true;DiagnosticLog.error("wallpaper.video.codec_query_failed","codec="+codec.getName(),failure);}
            }
            DiagnosticLog.info("wallpaper.video.codec_check","result="+(queryFailed?"unknown":"unsupported")+" matching="+matching+" eligible="+eligible);
            if(queryFailed)throw unavailable();
            return false;
        }
        private static IOException unavailable(){return new IOException("无法确认电视解码能力，请导出诊断日志 / Unable to query TV decoder capabilities; export diagnostics");}
        private static boolean fits(MediaCodecInfo.VideoCapabilities video,int width,int height,float fps,String codec){
            // Match Media3 1.8.1: check aligned coded dimensions and floor the signaled rate.
            int wa=video.getWidthAlignment(),ha=video.getHeightAlignment();
            int alignedWidth=((width+wa-1)/wa)*wa,alignedHeight=((height+ha-1)/ha)*ha;
            double checkedFps=Math.floor(fps);
            boolean supported=fps<1?video.isSizeSupported(alignedWidth,alignedHeight):video.areSizeAndRateSupported(alignedWidth,alignedHeight,checkedFps);
            String detail="codec="+codec+" size="+width+"x"+height+" aligned="+alignedWidth+"x"+alignedHeight+" alignment="+wa+"x"+ha+" fps="+fps+" checkedFps="+checkedFps+" result="+supported;
            // Optional diagnostic ranges must not override a successful size/rate query.
            try{detail+=" widths="+video.getSupportedWidths()+" heights="+video.getSupportedHeights()+" frameRates="+video.getSupportedFrameRates();}
            catch(RuntimeException failure){DiagnosticLog.error("wallpaper.video.codec_details_failed","codec="+codec,failure);}
            DiagnosticLog.info("wallpaper.video.codec_size_rate",detail);
            return supported;
        }
    }
    @TargetApi(27) private static final class Api27 {static Bitmap frame(MediaMetadataRetriever r,int w,int h){return r.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,w,h);}}
    @TargetApi(29) private static final class Api29 {static boolean hardware(MediaCodecInfo codec){return codec.isHardwareAccelerated();}}
}
