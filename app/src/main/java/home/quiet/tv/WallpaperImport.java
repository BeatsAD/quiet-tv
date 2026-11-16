package home.quiet.tv;

import android.content.Context;
import java.io.*;
import java.util.Locale;
import java.util.UUID;
import android.os.Bundle;

/** The same bounded import and admission path for selected files and LAN uploads. */
final class WallpaperImport {
    static boolean video(String name,String mime){return mime!=null&&mime.startsWith("video/")||name.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mov|mkv|webm|m4v|3gp|3g2|ts|m2ts|mts|mpg|mpeg|vob|flv)$");}
    static boolean media(String name){return name.toLowerCase(Locale.ROOT).matches(".*\\.(jpg|jpeg|png|webp|mp4|mov|mkv|webm|m4v|3gp|3g2|ts|m2ts|mts|mpg|mpeg|vob|flv)$");}
    static long copy(InputStream input,OutputStream output,long length,long maximum,WallpaperUploadServer.Active active)throws IOException{
        if(length>maximum)throw new IOException("文件超过可用存储空间或大小上限 / File exceeds available storage or size limit");
        byte[] buffer=new byte[16384];long total=0;
        while(length<0||total<length){
            if(!active.valid())throw new IOException("导入已取消 / Import cancelled");
            int count=input.read(buffer,0,(int)Math.min(buffer.length,length<0?buffer.length:length-total));
            if(count<0)break;
            total+=count;if(total>maximum)throw new IOException("文件超过可用存储空间或大小上限 / File exceeds available storage or size limit");output.write(buffer,0,count);
        }
        if(total==0||length>=0&&total!=length)throw new IOException("文件读取不完整 / Incomplete file");
        return total;
    }
    static final class Prepared {
        final WallpaperStore store;final File file;final String id,name,fingerprint,transfer;final int width,height;final WallpaperVideo.Info video;
        Prepared(WallpaperStore store,File file,String id,String name,int width,int height,WallpaperVideo.Info video){this(store,file,id,name,width,height,video,"unknown","unknown");}
        Prepared(WallpaperStore store,File file,String id,String name,int width,int height,WallpaperVideo.Info video,String fingerprint,String transfer){this.fingerprint=fingerprint;this.transfer=transfer;this.store=store;this.file=file;this.id=id;this.name=name;this.width=width;this.height=height;this.video=video;}
        File existing(){if(file.isFile())return file;File destination=store.preparedDestination(id,video!=null);return destination.isFile()?destination:null;}
        WallpaperCatalog.Entry save(String name,WallpaperUploadServer.Active active)throws IOException{WallpaperCatalog.Entry saved=store.snapshot().find(id);if(saved!=null)return saved;File ready=existing();if(ready==null)throw new IOException("素材草稿无法读取 / Media draft unavailable");WallpaperCatalog.Entry entry=store.commitPrepared(id,ready,name,width,height,video,active,fingerprint,transfer);DiagnosticLog.info("media.evidence.saved","wallpaper="+entry.id+" transfer="+transfer+" fingerprint="+fingerprint+" contentStage=source_bytes actualFileContent=not_rehashed");return entry;}
        void discard(){if(store.snapshot().find(id)!=null)return;File ready=existing();if(ready!=null&&!ready.delete())android.util.Log.w("QuietTV","Import draft retained");}
        void saveState(Bundle state){state.putString("draft-fingerprint",fingerprint);state.putString("draft-transfer",transfer);state.putString("draft-file",file.getName());state.putString("draft-id",id);state.putString("draft-name",name);state.putInt("draft-width",width);state.putInt("draft-height",height);if(video!=null){state.putString("draft-mime",video.mime);state.putLong("draft-duration",video.durationMs);state.putFloat("draft-fps",video.fps);state.putInt("draft-rotation",video.rotation);state.putFloat("draft-pixel-ratio",video.pixelRatio);}}
        static Prepared restore(Context context,Bundle state)throws IOException{
            String file=state.getString("draft-file",""),id=state.getString("draft-id","");if(!file.matches("upload-[a-f0-9-]{36}\\.tmp")||!id.matches("user-[a-f0-9-]{36}"))throw new IOException("素材草稿无法读取");
            WallpaperVideo.Info video=null;int width=state.getInt("draft-width"),height=state.getInt("draft-height");if(width<1||height<1)throw new IOException("素材草稿无法读取");if(state.containsKey("draft-mime")){video=new WallpaperVideo.Info();video.width=width;video.height=height;video.mime=state.getString("draft-mime");video.durationMs=state.getLong("draft-duration");video.fps=state.getFloat("draft-fps");video.rotation=state.getInt("draft-rotation");video.pixelRatio=state.getFloat("draft-pixel-ratio",1);}
            Prepared prepared=new Prepared(WallpaperStore.get(context),new File(new File(context.getFilesDir(),"wallpapers"),file),id,state.getString("draft-name",""),width,height,video,state.getString("draft-fingerprint","unknown"),state.getString("draft-transfer","unknown"));if(prepared.store.snapshot().find(id)==null&&prepared.existing()==null)throw new IOException("素材草稿已不可用，请重新选择文件");return prepared;
        }
    }
    static WallpaperCatalog.Entry accept(Context context,InputStream input,long length,String name,boolean video,WallpaperUploadServer.Active active,String diagnostic)throws IOException{
        Prepared prepared=prepare(context,input,length,name,video,active,diagnostic);try{return prepared.save(name,active);}finally{prepared.discard();}
    }
    static Prepared prepare(Context context,InputStream input,long length,String name,boolean video,WallpaperUploadServer.Active active,String diagnostic)throws IOException{
        WallpaperStore store=WallpaperStore.get(context);
        if(video)store.checkVideoCapacity(Math.max(1,length));else store.checkCapacity();
        File incoming=store.videoIncoming();
        String transfer=java.util.UUID.randomUUID().toString();MediaFingerprint evidence=new MediaFingerprint(input);
        File prepared=null,retained=null;
        try{
            long bytes;
            try(FileOutputStream output=new FileOutputStream(incoming)){bytes=copy(evidence,output,length,video?store.videoCapacity():WallpaperUploadServer.MAX_UPLOAD,active);output.getFD().sync();}
            String fingerprint=evidence.anonymous();diagnostic+=" transfer="+transfer+" fingerprint="+fingerprint;
            DiagnosticLog.info("wallpaper.import.received",diagnostic+" kind="+(video?"video":"image")+" bytes="+bytes);
            if(!active.valid())throw new IOException("导入已取消 / Import cancelled");
            if(video){
                WallpaperVideo.Info info=WallpaperVideo.inspect(incoming,diagnostic);
                retained=incoming;return new Prepared(store,incoming,"user-"+UUID.randomUUID(),name,info.width,info.height,info,fingerprint,transfer);
            }
            prepared=store.videoIncoming();
            int[] size=WallpaperImages.prepareImport(incoming,prepared);
            if(!active.valid())throw new IOException("导入已取消 / Import cancelled");
            boolean original=!prepared.isFile();
            retained=original?incoming:prepared;
            DiagnosticLog.info("wallpaper.import.image",diagnostic+" size="+size[0]+"x"+size[1]+" bytes="+retained.length()+" originalPreserved="+original);
            return new Prepared(store,retained,"user-"+UUID.randomUUID(),name,size[0],size[1],null,fingerprint,transfer);
        }finally{
            if(prepared!=null&&!prepared.equals(retained)&&prepared.isFile()&&!prepared.delete())android.util.Log.w("QuietTV","Prepared import temporary file retained");
            if(!incoming.equals(retained)&&incoming.isFile()&&!incoming.delete())android.util.Log.w("QuietTV","Import temporary file retained");
        }
    }
}
