package home.quiet.tv;

import android.media.MediaPlayer;
import java.io.*;
import java.util.concurrent.*;

/** API14/15 media inspection. Bounded container reads plus the device's own player. */
final class LegacyVideoProbe {
    private static final int MAX_ELEMENTS=8192;
    static final class Metadata {
        int width,height,rotation;long timescale,samples,ticks;float fps;String mime="";
    }
    static WallpaperVideo.Info inspect(File file)throws IOException {return inspect(file,"");}
    static WallpaperVideo.Info inspect(File file,String context)throws IOException {
        Metadata metadata;
        try{metadata=read(file);}catch(IOException e){DiagnosticLog.error("wallpaper.video.legacy_probe_failed",context+" stage=container reason=invalid_or_unsupported_container "+VideoDiagnostics.causes(e),e);throw e;}
        if(metadata.width>0&&metadata.height>0&&(Math.max(metadata.width,metadata.height)>1920||Math.min(metadata.width,metadata.height)>1080))throw VideoDiagnostics.failure("legacy_metadata","legacy_resolution_limit","此系统请使用不超过 1080p 的视频 / Use video up to 1080p on this system");
        MediaPlayer player=new MediaPlayer();CountDownLatch ready=new CountDownLatch(1);boolean[] failed={false};
        try {
            player.setOnPreparedListener(p->ready.countDown());
            player.setOnErrorListener((p,what,extra)->{DiagnosticLog.info("wallpaper.video.legacy_probe_failed",context+" stage=prepare reason="+VideoDiagnostics.platformReason(what,extra)+" what="+what+" extra="+extra);failed[0]=true;ready.countDown();return true;});
            player.setDataSource(file.getPath());player.setVolume(0,0);player.prepareAsync();
            if(!ready.await(12,TimeUnit.SECONDS))throw VideoDiagnostics.failure("legacy_prepare","inspection_timeout","视频检查超时，请尝试其他视频 / Video inspection timed out");
            if(failed[0])throw VideoDiagnostics.failure("legacy_prepare","platform_player_error","电视无法解码此视频 / TV cannot decode this video");
            WallpaperVideo.Info info=new WallpaperVideo.Info();info.width=player.getVideoWidth();info.height=player.getVideoHeight();info.durationMs=player.getDuration();
            if(info.width<=0)info.width=metadata.width;if(info.height<=0)info.height=metadata.height;
            if(info.width<=0||info.height<=0||info.durationMs<=0)throw VideoDiagnostics.failure("legacy_metadata","invalid_dimensions_or_duration","视频尺寸或时长无效 / Invalid video dimensions or duration");
            if(Math.max(info.width,info.height)>1920||Math.min(info.width,info.height)>1080)throw VideoDiagnostics.failure("legacy_metadata","legacy_resolution_limit","此系统请使用不超过 1080p 的视频 / Use video up to 1080p on this system");
            if(metadata.fps>60.1f)throw VideoDiagnostics.failure("frame_rate","invalid_or_excessive_frame_rate","请使用不超过 60 FPS 的视频 / Use video up to 60 FPS");
            info.mime=metadata.mime.isEmpty()?"video/unknown":metadata.mime;info.rotation=metadata.rotation;info.fps=metadata.fps;info.pixelRatio=VideoGeometry.pixelRatio(file,1f);
            DiagnosticLog.info("wallpaper.video.inspect",context+" backend=api14_platform mime="+VideoDiagnostics.mime(info.mime)+" size="+info.width+"x"+info.height+" fps="+info.fps+" rotation="+info.rotation);
            return info;
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("Video inspection cancelled");}
        catch(RuntimeException e){DiagnosticLog.error("wallpaper.video.legacy_probe_failed",context+" stage=platform reason=platform_error "+VideoDiagnostics.causes(e),e);throw new IOException("视频无法解析 / Unable to read video",e);}
        finally{player.setOnPreparedListener(null);player.setOnErrorListener(null);player.release();}
    }
    static Metadata read(File file)throws IOException {
        try(RandomAccessFile input=new RandomAccessFile(file,"r")){
            if(input.length()<12)throw invalid();int signature=input.readInt();Metadata result=new Metadata();
            if(signature==0x1a45dfa3)ebml(input,0,input.length(),0,new int[]{0},result,false);
            else mp4(input,0,input.length(),0,new int[]{0},result);
            if(result.samples>0&&result.timescale>0&&result.ticks>0)result.fps=(float)((double)result.samples*result.timescale/result.ticks);
            if(Float.isNaN(result.fps)||Float.isInfinite(result.fps)||result.fps<0)throw invalid();return result;
        }
    }
    private static IOException invalid(){return new IOException("视频容器无效或不受支持 / Invalid or unsupported video container");}
    private static void mp4(RandomAccessFile in,long start,long end,int depth,int[] count,Metadata result)throws IOException {
        if(depth>10)throw invalid();long at=start;
        while(at+8<=end){
            if(++count[0]>MAX_ELEMENTS)throw invalid();in.seek(at);long size=in.readInt()&0xffffffffL;int type=in.readInt(),header=8;
            if(size==1){size=in.readLong();header=16;}if(size==0)size=end-at;if(size<header||size>end-at)throw invalid();
            long data=at+header,next=at+size;
            if(type==0x7472616b){Metadata track=new Metadata();mp4(in,data,next,depth+1,count,track);if(!track.mime.isEmpty()){if(!result.mime.isEmpty())throw invalid();copy(track,result);}}
            else if(type==0x6d6f6f76||type==0x6d646961||type==0x6d696e66||type==0x7374626c)mp4(in,data,next,depth+1,count,result);
            else if(type==0x6d646864){in.seek(data);int version=in.readUnsignedByte();long offset=version==1?20:12;if(data+offset+4>next)throw invalid();in.seek(data+offset);result.timescale=in.readInt()&0xffffffffL;}
            else if(type==0x73747473){if(next-data<8)throw invalid();in.seek(data+4);long entries=in.readInt()&0xffffffffL;if(entries>MAX_ELEMENTS||entries>(next-data-8)/8)throw invalid();for(int n=0;n<entries;n++){long samples=in.readInt()&0xffffffffL,ticks=in.readInt()&0xffffffffL;result.samples+=samples;result.ticks+=samples*ticks;}}
            else if(type==0x746b6864){in.seek(data);int version=in.readUnsignedByte();long matrix=data+(version==1?52:40);if(matrix+20>next)throw invalid();in.seek(matrix);int a=in.readInt(),b=in.readInt();in.skipBytes(4);int c=in.readInt(),d=in.readInt();if(a==0&&d==0)result.rotation=b>0&&c<0?90:b<0&&c>0?270:0;else if(a<0&&d<0)result.rotation=180;}
            else if(type==0x73747364){if(next-data<8)throw invalid();in.seek(data+4);long entries=in.readInt()&0xffffffffL;if(entries>32)throw invalid();long entry=data+8;
                for(int n=0;n<entries;n++){in.seek(entry);long length=in.readInt()&0xffffffffL;int codec=in.readInt();if(length<8||length>next-entry)throw invalid();
                    if(codec==0x656e6376)throw invalid();
                    if(codec==0x61766331||codec==0x61766333||codec==0x6d703476||codec==0x73323633){if(length<86)throw invalid();result.mime=codec==0x6d703476?"video/mp4v-es":codec==0x73323633?"video/3gpp":"video/avc";in.seek(entry+32);result.width=in.readUnsignedShort();result.height=in.readUnsignedShort();mp4(in,entry+86,entry+length,depth+1,count,result);}
                    entry+=length;
                }
            }else if(type==0x61766343){if(next-data<8)throw invalid();in.seek(data);if(in.readUnsignedByte()!=1)throw invalid();int profile=in.readUnsignedByte();if(profile!=66&&profile!=77&&profile!=88&&profile!=100)throw invalid();in.seek(data+5);int sps=in.readUnsignedByte()&31;if(sps<1)throw invalid();int length=in.readUnsignedShort();if(length<4||length>next-in.getFilePointer()||length>4096)throw invalid();byte[] bytes=new byte[length];in.readFully(bytes);WallpaperVideo.Bits bits=new WallpaperVideo.Bits(bytes,1);int p=bits.read(8);bits.read(8);bits.read(8);bits.ue();if(p==100&&(bits.ue()>1||bits.ue()!=0||bits.ue()!=0))throw invalid();}
            at=next;
        }
    }
    private static void copy(Metadata from,Metadata to){to.width=from.width;to.height=from.height;to.rotation=from.rotation;to.timescale=from.timescale;to.samples=from.samples;to.ticks=from.ticks;to.fps=from.fps;to.mime=from.mime;}
    private static long vint(RandomAccessFile in,boolean id)throws IOException {
        int first=in.readUnsignedByte(),mask=128,n=1;while(n<=8&&(first&mask)==0){mask>>=1;n++;}if(n>8||(id&&n>4))throw invalid();long value=id?first:first&(mask-1);for(int i=1;i<n;i++)value=(value<<8)|in.readUnsignedByte();return !id&&value==((1L<<(7*n))-1)?-1:value;
    }
    private static long integer(RandomAccessFile in,int size)throws IOException {if(size<1||size>8)throw invalid();long value=0;for(int n=0;n<size;n++)value=(value<<8)|in.readUnsignedByte();return value;}
    private static void ebml(RandomAccessFile in,long start,long end,int depth,int[] count,Metadata result,boolean video)throws IOException {
        if(depth>10)throw invalid();long at=start;
        while(at<end){if(++count[0]>MAX_ELEMENTS)throw invalid();in.seek(at);long id=vint(in,true),size=vint(in,false),data=in.getFilePointer();if(size<0)size=end-data;if(size>end-data)throw invalid();long next=data+size;
            if(id==0x18538067L||id==0x1654ae6bL||id==0xe0L||id==0x55b0L)ebml(in,data,next,depth+1,count,result,video||id==0xe0L);
            else if(id==0xaeL){Metadata track=new Metadata();ebml(in,data,next,depth+1,count,track,false);if(track.width>0&&track.height>0){if(!result.mime.isEmpty())throw invalid();copy(track,result);}}
            else if(id==0xb0L&&video)result.width=(int)integer(in,(int)size);
            else if(id==0xbaL&&video)result.height=(int)integer(in,(int)size);
            else if(id==0x86L){if(size>128)throw invalid();byte[] b=new byte[(int)size];in.readFully(b);String codec=new String(b,Charsets.US_ASCII);result.mime=codec.equals("V_VP8")?"video/x-vnd.on2.vp8":codec.equals("V_MPEG4/ISO/AVC")?"video/avc":codec.equals("V_MPEG4/ISO/ASP")?"video/mp4v-es":"";}
            else if(id==0x23e383L){long duration=integer(in,(int)size);if(duration>0)result.fps=1000000000f/duration;}
            else if(id==0x2383e3L&&video){if(size==4)result.fps=in.readFloat();else if(size==8)result.fps=(float)in.readDouble();else throw invalid();}
            else if(id==0x55baL&&video){long transfer=integer(in,(int)size);if(transfer==16||transfer==18)throw invalid();}
            else if(id==0x6d80L)throw invalid();
            at=next;
        }
    }
    private LegacyVideoProbe(){}
}
