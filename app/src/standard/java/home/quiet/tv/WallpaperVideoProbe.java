package home.quiet.tv;

import android.annotation.SuppressLint;
import android.media.MediaFormat;
import androidx.media3.common.*;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.*;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import android.util.Pair;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/** Uses the same container parsers as playback; never opens a decoder during import. */
@SuppressLint("UnsafeOptInUsageError")
final class WallpaperVideoProbe {
    static WallpaperVideo.Info inspect(File file,String context)throws IOException {
        Extractor selected=null;
        try(RandomAccessFile source=new RandomAccessFile(file,"r")){
            DefaultExtractorInput input=input(source);
            for(Extractor candidate:new DefaultExtractorsFactory().createExtractors()){
                boolean matches;
                try{matches=candidate.sniff(input);}catch(EOFException e){matches=false;}
                finally{input.resetPeekPosition();}
                if(matches){selected=candidate;break;}candidate.release();
            }
            if(selected==null)throw rejected("container","unsupported_container");
            Output output=new Output();selected.init(output);PositionHolder seek=new PositionHolder();
            while(true){
                int result=selected.read(input,seek);
                if(output.encrypted)throw rejected("samples","encrypted_sample");
                if(output.ended&&output.videoCount()==0)throw rejected("tracks","no_video_track");
                if(output.videoCount()>1)throw rejected("tracks","multiple_video_tracks");
                Track video=output.video();
                if(result==Extractor.RESULT_END_OF_INPUT||output.ended&&output.durationUs!=C.TIME_UNSET&&video!=null&&video.frames>=121)break;
                if(result==Extractor.RESULT_SEEK){
                    if(seek.position<0||seek.position>source.length())throw rejected("container","invalid_seek");
                    source.seek(seek.position);input=input(source);
                }
            }
            Track video=output.video();if(video==null)throw rejected("tracks","no_video_track");
            Format format=video.format;WallpaperVideo.Info info=new WallpaperVideo.Info();
            info.mime=format.sampleMimeType;info.width=format.width;info.height=format.height;info.rotation=format.rotationDegrees;
            info.pixelRatio=format.pixelWidthHeightRatio;info.durationMs=output.durationUs==C.TIME_UNSET?0:output.durationUs/1000;
            float sampled=video.frames>1&&video.last>video.first?(video.frames-1)*1000000f/(video.last-video.first):0;
            info.fps=format.frameRate>0?format.frameRate:sampled;
            DiagnosticLog.info("wallpaper.video.inspect",context+" backend=media3_extractor mime="+VideoDiagnostics.mime(info.mime)+" size="+info.width+"x"+info.height
                +" fps="+info.fps+" sampledFps="+sampled+" rotation="+info.rotation+" durationMs="+info.durationMs+" bytes="+file.length());
            if(info.width<=0||info.height<=0||info.durationMs<=0)throw rejected("metadata","invalid_dimensions_or_duration");
            if(Math.max(info.width,info.height)>3840||Math.min(info.width,info.height)>2160)throw rejected("metadata","resolution_limit");
            if(info.rotation%90!=0)throw rejected("metadata","invalid_rotation");
            if((Float.isNaN(info.fps)||Float.isInfinite(info.fps))||info.fps<=0||info.fps>60.1f||sampled>60.5f)throw rejected("frame_rate","invalid_or_excessive_frame_rate");
            if(format.drmInitData!=null||format.cryptoType!=C.CRYPTO_TYPE_NONE)throw rejected("tracks","encrypted_track");
            if(format.colorInfo!=null){
                if(format.colorInfo.colorTransfer==C.COLOR_TRANSFER_ST2084||format.colorInfo.colorTransfer==C.COLOR_TRANSFER_HLG)throw rejected("color","hdr_unsupported");
                if(format.colorInfo.lumaBitdepth>8||format.colorInfo.chromaBitdepth>8)throw rejected("profile","unsupported_bit_depth");
            }
            MediaFormat profile=new MediaFormat();Pair<Integer,Integer> values=MediaCodecUtil.getCodecProfileAndLevel(format);
            if(values!=null)profile.setInteger("profile",values.first);
            if(!format.initializationData.isEmpty())profile.setByteBuffer("csd-0",ByteBuffer.wrap(format.initializationData.get(0)));
            WallpaperVideo.Api16.checkProfile(profile,info.mime);
            DiagnosticLog.info("wallpaper.video.admission",context+" decision=defer_to_playback parser=media3");
            return info;
        }catch(VideoDiagnostics.Failure e){throw e;}
        catch(IOException|RuntimeException e){VideoDiagnostics.Failure failure=rejected("container","container_parse_failed");failure.initCause(e);throw failure;}
        finally{if(selected!=null)selected.release();}
    }
    private static DefaultExtractorInput input(RandomAccessFile source)throws IOException{return new DefaultExtractorInput(source::read,source.getFilePointer(),source.length());}
    private static VideoDiagnostics.Failure rejected(String stage,String reason){return VideoDiagnostics.failure(stage,reason,"视频无法解析或规格不受支持，请查看诊断日志 / Video format unsupported; see diagnostics");}
    private static final class Track implements TrackOutput {
        Format format;int frames;long first=Long.MAX_VALUE,last=Long.MIN_VALUE;final Output output;final byte[] scratch=new byte[8192];
        Track(Output output){this.output=output;}
        @Override public void format(Format format){this.format=format;}
        @Override public int sampleData(DataReader input,int length,boolean allowEnd,int part)throws IOException{
            int read=input.read(scratch,0,Math.min(length,scratch.length));if(read==-1&&!allowEnd)throw new EOFException();return read;
        }
        @Override public void sampleData(ParsableByteArray data,int length,int part){data.skipBytes(length);}
        @Override public void sampleMetadata(long timeUs,int flags,int size,int offset,CryptoData crypto){
            if(crypto!=null||(flags&C.BUFFER_FLAG_ENCRYPTED)!=0)output.encrypted=true;
            if(format!=null&&format.sampleMimeType!=null&&format.sampleMimeType.startsWith("video/")){frames++;first=Math.min(first,timeUs);last=Math.max(last,timeUs);}
        }
    }
    private static final class Output implements ExtractorOutput {
        final Map<Integer,Track> tracks=new LinkedHashMap<>();boolean ended,encrypted;long durationUs=C.TIME_UNSET;
        @Override public TrackOutput track(int id,int type){Track track=tracks.get(id);if(track==null){track=new Track(this);tracks.put(id,track);}return track;}
        @Override public void endTracks(){ended=true;}
        @Override public void seekMap(SeekMap map){durationUs=map.getDurationUs();}
        int videoCount(){int count=0;for(Track track:tracks.values())if(track.format!=null&&track.format.sampleMimeType!=null&&track.format.sampleMimeType.startsWith("video/"))count++;return count;}
        Track video(){for(Track track:tracks.values())if(track.format!=null&&track.format.sampleMimeType!=null&&track.format.sampleMimeType.startsWith("video/"))return track;return null;}
    }
    private WallpaperVideoProbe(){}
}
