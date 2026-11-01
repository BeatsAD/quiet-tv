package home.quiet.tv;

import android.content.Context;
import java.io.*;
import java.util.Locale;

/** The same bounded import and admission path for selected files and LAN uploads. */
final class WallpaperImport {
    static boolean video(String name,String mime){return mime!=null&&mime.startsWith("video/")||name.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mov|mkv|webm)$");}
    static boolean media(String name){return name.toLowerCase(Locale.ROOT).matches(".*\\.(jpg|jpeg|png|webp|mp4|mov|mkv|webm)$");}
    static long copy(InputStream input,OutputStream output,long length,long maximum,WallpaperUploadServer.Active active)throws IOException{
        if(length>maximum)throw new IOException("文件超过大小限制 / File too large");
        byte[] buffer=new byte[16384];long total=0;
        while(length<0||total<length){
            if(!active.valid())throw new IOException("导入已取消 / Import cancelled");
            int count=input.read(buffer,0,(int)Math.min(buffer.length,length<0?buffer.length:length-total));
            if(count<0)break;
            total+=count;if(total>maximum)throw new IOException("文件超过大小限制 / File too large");output.write(buffer,0,count);
        }
        if(total==0||length>=0&&total!=length)throw new IOException("文件读取不完整 / Incomplete file");
        return total;
    }
    static WallpaperCatalog.Entry accept(Context context,InputStream input,long length,String name,boolean video,WallpaperUploadServer.Active active,String diagnostic)throws IOException{
        WallpaperStore store=WallpaperStore.get(context);
        if(video)store.checkVideoCapacity(Math.max(0,length));else store.checkCapacity();
        File incoming=store.videoIncoming();
        File prepared=null;
        try{
            long bytes;
            try(FileOutputStream output=new FileOutputStream(incoming)){bytes=copy(input,output,length,video?WallpaperVideo.MAX_UPLOAD:WallpaperUploadServer.MAX_UPLOAD,active);output.getFD().sync();}
            DiagnosticLog.info("wallpaper.import.received",diagnostic+" kind="+(video?"video":"image")+" bytes="+bytes);
            if(!active.valid())throw new IOException("导入已取消 / Import cancelled");
            if(video){
                WallpaperVideo.Info info=WallpaperVideo.inspect(incoming,diagnostic);
                return store.addVideo(incoming,name,info,active);
            }
            prepared=store.videoIncoming();
            int[] size=WallpaperImages.prepareImport(incoming,prepared);
            if(!active.valid())throw new IOException("导入已取消 / Import cancelled");
            boolean original=!prepared.isFile();
            WallpaperCatalog.Entry entry=store.add(original?incoming:prepared,name,size[0],size[1]);
            DiagnosticLog.info("wallpaper.import.image",diagnostic+" size="+size[0]+"x"+size[1]+" bytes="+entry.bytes+" originalPreserved="+original);
            return entry;
        }finally{
            if(prepared!=null&&prepared.isFile()&&!prepared.delete())android.util.Log.w("QuietTV","Prepared import temporary file retained");
            if(incoming.isFile()&&!incoming.delete())android.util.Log.w("QuietTV","Import temporary file retained");
        }
    }
}
