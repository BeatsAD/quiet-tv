package home.quiet.tv;

import android.content.Context;
import android.graphics.*;
import android.util.AtomicFile;
import java.io.*;

/** One bounded, atomically saved cover and its prepared Dock background. */
final class WallpaperBackdropCache {
    static final int MAX_EDGE=960;
    private final AtomicFile file;
    static final class Picture {
        final Bitmap image,blur;
        Picture(Bitmap image,Bitmap blur){this.image=image;this.blur=blur;}
    }
    WallpaperBackdropCache(Context context){file=new AtomicFile(new File(context.getFilesDir(),"wallpaper-backdrop"));}
    static String key(WallpaperStore store,String id,int width,int height){return "1|"+store.imageKey(id)+"|"+width+"x"+height;}
    Picture read(String key){synchronized(WallpaperBackdropCache.class){
        try(DataInputStream input=new DataInputStream(file.openRead())){
            if(!key.equals(input.readUTF()))return null;
            Bitmap image=readBitmap(input,MAX_EDGE),blur=readBitmap(input,320);
            return new Picture(image,blur);
        }catch(FileNotFoundException ignored){return null;}
        catch(IOException|RuntimeException|OutOfMemoryError e){DiagnosticLog.info("wallpaper.backdrop.unavailable","cached cover unreadable");return null;}
    }}
    private static Bitmap readBitmap(DataInputStream input,int edge)throws IOException{
        int length=input.readInt();if(length<=0||length>4*1024*1024)throw new IOException("Invalid cover size");
        byte[] bytes=new byte[length];input.readFully(bytes);
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,length,bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outWidth>edge||bounds.outHeight>edge)throw new IOException("Invalid cover dimensions");
        Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,length);
        if(bitmap==null)throw new IOException("Invalid cover image");return bitmap;
    }
    static Bitmap cover(Bitmap source,int width,int height){
        float scale=Math.min(1f,MAX_EDGE/(float)Math.max(width,height));
        int w=Math.max(1,Math.round(width*scale)),h=Math.max(1,Math.round(height*scale));
        Bitmap image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.drawColor(Color.BLACK);
        float crop=Math.max(w/(float)source.getWidth(),h/(float)source.getHeight());
        float sw=source.getWidth()*crop,sh=source.getHeight()*crop;
        canvas.drawBitmap(source,null,new RectF((w-sw)/2,(h-sh)/2,(w+sw)/2,(h+sh)/2),new Paint(Paint.FILTER_BITMAP_FLAG));return image;
    }
    void write(String key,Bitmap image,Bitmap blur){synchronized(WallpaperBackdropCache.class){
        FileOutputStream stream=null;
        try{
            stream=file.startWrite();DataOutputStream output=new DataOutputStream(stream);output.writeUTF(key);
            writeBitmap(output,image,Bitmap.CompressFormat.JPEG);writeBitmap(output,blur,Bitmap.CompressFormat.PNG);
            output.flush();file.finishWrite(stream);
        }catch(IOException|RuntimeException|OutOfMemoryError e){if(stream!=null)file.failWrite(stream);DiagnosticLog.info("wallpaper.backdrop.save_failed","previous cover retained");}
    }}
    private static void writeBitmap(DataOutputStream output,Bitmap bitmap,Bitmap.CompressFormat format)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        if(!bitmap.compress(format,92,bytes))throw new IOException("Unable to encode cover");
        output.writeInt(bytes.size());bytes.writeTo(output);
    }
}
