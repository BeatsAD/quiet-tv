package home.quiet.tv;

import android.annotation.SuppressLint;
import androidx.media3.common.*;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.*;
import java.io.*;
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
                Track video=output.video();
                if(result==Extractor.RESULT_END_OF_INPUT||output.ended&&video!=null&&video.frames>=121)break;
                if(result==Extractor.RESULT_SEEK){
                    if(seek.position<0||seek.position>source.length())throw rejected("container","invalid_seek");
                    source.seek(seek.position);input=input(source);
                }
            }
            Track video=output.video();if(video==null)throw rejected("tracks","no_video_track");
            Format format=video.format;WallpaperVideo.Info info=new WallpaperVideo.Info();
            info.mime=format.sampleMimeType;info.width=Math.max(0,format.width);info.height=Math.max(0,format.height);info.rotation=format.rotationDegrees;
            info.pixelRatio=format.pixelWidthHeightRatio;info.durationMs=output.durationUs==C.TIME_UNSET?0:output.durationUs/1000;
            float sampled=video.frames>1&&video.last>video.first?(video.frames-1)*1000000f/(video.last-video.first):0;
            info.fps=WallpaperVideo.frameRate(format.frameRate,sampled);
            DiagnosticLog.info("wallpaper.video.inspect",context+" backend=media3_extractor mime="+VideoDiagnostics.mime(info.mime)+" size="+info.width+"x"+info.height
                +" fps="+info.fps+" sampledFps="+sampled+" rotation="+info.rotation+" durationMs="+info.durationMs+" bytes="+file.length());
            if(format.drmInitData!=null||format.cryptoType!=C.CRYPTO_TYPE_NONE)throw rejected("tracks","encrypted_track");
            DiagnosticLog.info("wallpaper.video.admission",context+" decision=defer_to_playback parser=media3");
            return info;
        }catch(VideoDiagnostics.Failure e){throw e;}
        catch(IOException|RuntimeException e){VideoDiagnostics.Failure failure=rejected("container","container_parse_failed");failure.initCause(e);throw failure;}
        finally{if(selected!=null)selected.release();}
    }
    private static DefaultExtractorInput input(RandomAccessFile source)throws IOException{return new DefaultExtractorInput(source::read,source.getFilePointer(),source.length());}
    private static VideoDiagnostics.Failure rejected(String stage,String reason){return WallpaperVideo.rejected(stage,reason);}
    private static final class Track implements TrackOutput {
        Format format;int frames;long first=Long.MAX_VALUE,last=Long.MIN_VALUE;final Output output;final int type;final byte[] scratch=new byte[8192];
        Track(Output output,int type){this.output=output;this.type=type;}
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
        @Override public TrackOutput track(int id,int type){Track track=tracks.get(id);if(track==null){track=new Track(this,type);tracks.put(id,track);}return track;}
        @Override public void endTracks(){ended=true;}
        @Override public void seekMap(SeekMap map){durationUs=map.getDurationUs();}
        int videoCount(){int count=0;for(Track track:tracks.values())if(track.type==C.TRACK_TYPE_VIDEO)count++;return count;}
        Track video(){for(Track track:tracks.values())if(track.format!=null&&track.format.sampleMimeType!=null&&track.format.sampleMimeType.startsWith("video/"))return track;return null;}
    }
    private WallpaperVideoProbe(){}
}
