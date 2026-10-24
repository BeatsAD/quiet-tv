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
    static final long MAX_UPLOAD=30L*1024*1024;
    static final class Info {
        int width,height,rotation;long durationMs;float fps;String mime;
    }
    static String capabilities() {
        try{return new JSONObject().put("purpose","wallpaper").put("imageMaxBytes",WallpaperUploadServer.MAX_UPLOAD)
            .put("videoMaxBytes",MAX_UPLOAD).put("videoMaxWidth",Build.VERSION.SDK_INT>=21?3840:1920)
            .put("videoMaxHeight",Build.VERSION.SDK_INT>=21?2160:1080).put("videoMaxFps",30)
            .put("hevc",Build.VERSION.SDK_INT>=21&&Api21.hasHevc()).toString();}
        catch(JSONException e){throw new IllegalStateException(e);}
    }
    static Info inspect(File file) throws IOException {
        try(RandomAccessFile input=new RandomAccessFile(file,"r")){
            if(input.length()<12||input.length()>MAX_UPLOAD)throw invalid();
            input.readInt();if(input.readInt()!=0x66747970)throw invalid(); // MP4 ftyp, never an image renamed .mp4.
        }
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
            if(!"video/avc".equals(info.mime)&&!(Build.VERSION.SDK_INT>=21&&"video/hevc".equals(info.mime)))throw new IOException("请转换为 H.264 MP4 视频 / Convert to H.264 MP4");
            info.width=format.getInteger(MediaFormat.KEY_WIDTH);info.height=format.getInteger(MediaFormat.KEY_HEIGHT);
            info.durationMs=Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            String rotation=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);info.rotation=rotation==null?0:Integer.parseInt(rotation);
            if(info.width<=0||info.height<=0||info.width>3840||info.height>2160||info.durationMs<=0||info.rotation%90!=0)throw invalid();
            if(Build.VERSION.SDK_INT<21&&(info.width>1920||info.height>1080))throw new IOException("此系统请使用不超过 1080p 的 H.264 视频 / Use H.264 up to 1080p on this system");
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
            if((Float.isNaN(info.fps)||Float.isInfinite(info.fps))||info.fps<=0||info.fps>30.1f||sampled>30.5f)throw new IOException("请使用不超过 30 FPS 的视频 / Use video up to 30 FPS");
            if(format.containsKey("crypto-key"))throw invalid();
            if(Build.VERSION.SDK_INT>=21){if(!Api21.supports(info))throw new IOException("电视解码器不支持此规格，请使用较低规格的 H.264 视频 / Decoder does not support this video; use a lower H.264 resolution");}
            else if(!legacyDecoder(info.mime))throw invalid();
            return info;
        }catch(IOException e){if(e.getMessage()!=null&&e.getMessage().contains(" / "))throw e;throw invalid();}catch(RuntimeException e){throw invalid();}
        finally{extractor.release();try{metadata.release();}catch(IOException ignored){}}
    }
    private static Number number(MediaFormat format,String key){try{return format.getInteger(key);}catch(ClassCastException e){return format.getFloat(key);}}
    private static IOException invalid(){return new IOException("无效或不支持的 MP4 视频 / Invalid or unsupported MP4 video");}
    private static boolean legacyDecoder(String mime){
        for(int n=0;n<MediaCodecList.getCodecCount();n++){MediaCodecInfo codec=MediaCodecList.getCodecInfoAt(n);if(!codec.isEncoder())for(String type:codec.getSupportedTypes())if(mime.equals(type))return true;}return false;
    }
    private static void checkProfile(MediaFormat format,String mime) throws IOException {
        ByteBuffer data=format.getByteBuffer("csd-0");if(data==null)throw invalid();
        byte[] raw=new byte[data.remaining()];data.duplicate().get(raw);
        for(int i=0;i+4<raw.length;i++){
            int start=0;if(raw[i]==0&&raw[i+1]==0&&raw[i+2]==1)start=i+3;
            else if(i+5<raw.length&&raw[i]==0&&raw[i+1]==0&&raw[i+2]==0&&raw[i+3]==1)start=i+4;
            if(start==0)continue;
            if("video/avc".equals(mime)&&(raw[start]&31)==7){
                Bits bits=new Bits(raw,start+1);int profile=bits.read(8);bits.read(8);bits.read(8);bits.ue();
                if(profile!=66&&profile!=77&&profile!=88&&profile!=100)throw invalid();
                if(profile==100){int chroma=bits.ue();if(chroma>1)throw invalid();if(bits.ue()!=0||bits.ue()!=0)throw invalid();}return;
            }
            if("video/hevc".equals(mime)&&((raw[start]>>1)&63)==33){
                Bits bits=new Bits(raw,start+2);bits.read(8);bits.read(2);bits.read(1);if(bits.read(5)!=1)throw new IOException("请使用 HEVC Main 8bit 或 H.264 视频 / Use HEVC Main 8bit or H.264");return;
            }
        }
        throw invalid();
    }
    private static final class Bits {
        final byte[] bytes;int bit;
        Bits(byte[] raw,int start){ByteArrayOutputStream output=new ByteArrayOutputStream();int zeros=0;for(int i=start;i<raw.length;i++){int v=raw[i]&255;if(zeros>=2&&v==3){zeros=0;continue;}output.write(v);zeros=v==0?zeros+1:0;}bytes=output.toByteArray();}
        int read(int count) throws IOException {if(count<0||bit+count>bytes.length*8)throw invalid();int value=0;while(count-->0){value=(value<<1)|((bytes[bit/8]>>(7-bit%8))&1);bit++;}return value;}
        int ue() throws IOException {int zeros=0;while(read(1)==0){if(++zeros>20)throw invalid();}return (1<<zeros)-1+read(zeros);}
    }
    static void poster(File video,File output,Info info) {
        if(Build.VERSION.SDK_INT<27&&(info.width>1920||info.height>1080))return;
        MediaMetadataRetriever retriever=new MediaMetadataRetriever();Bitmap frame=null,small=null;
        try{
            retriever.setDataSource(video.getPath());int width=info.rotation%180==0?info.width:info.height,height=info.rotation%180==0?info.height:info.width;
            float scale=Math.min(1f,640f/Math.max(width,height));int w=Math.max(1,Math.round(width*scale)),h=Math.max(1,Math.round(height*scale));
            frame=Build.VERSION.SDK_INT>=27?Api27.frame(retriever,w,h):retriever.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if(frame==null)return;small=Bitmap.createScaledBitmap(frame,w,h,true);
            try(FileOutputStream stream=new FileOutputStream(output)){if(!small.compress(Bitmap.CompressFormat.JPEG,85,stream))throw new IOException();stream.getFD().sync();}
        }catch(IOException|RuntimeException|OutOfMemoryError ignored){if(output.isFile()&&!output.delete())android.util.Log.w("QuietTV","Incomplete video poster retained");}
        finally{if(small!=null&&small!=frame)small.recycle();if(frame!=null)frame.recycle();try{retriever.release();}catch(IOException ignored){}}
    }
    @TargetApi(21) private static final class Api21 {
        static boolean hasHevc(){return legacyDecoder("video/hevc");}
        static boolean supports(Info info){
            for(MediaCodecInfo codec:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()){
                if(codec.isEncoder())continue;
                if((info.width>1920||info.height>1080)&&((Build.VERSION.SDK_INT>=29&&!Api29.hardware(codec))||codec.getName().startsWith("OMX.google.")||codec.getName().startsWith("c2.android.")))continue;
                try{MediaCodecInfo.VideoCapabilities video=codec.getCapabilitiesForType(info.mime).getVideoCapabilities();
                    if(video!=null&&video.areSizeAndRateSupported(info.width,info.height,info.fps))return true;
                }catch(IllegalArgumentException ignored){}
            }return false;
        }
    }
    @TargetApi(27) private static final class Api27 {static Bitmap frame(MediaMetadataRetriever r,int w,int h){return r.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,w,h);}}
    @TargetApi(29) private static final class Api29 {static boolean hardware(MediaCodecInfo codec){return codec.isHardwareAccelerated();}}
}
