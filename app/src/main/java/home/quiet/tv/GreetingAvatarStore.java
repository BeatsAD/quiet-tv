package home.quiet.tv;

import android.content.Context;
import android.graphics.*;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import java.util.UUID;

/** Immutable, private candidates. The desktop preference is the only active-image reference. */
final class GreetingAvatarStore {
    static final long MAX_UPLOAD=2L*1024*1024;
    private final File directory;
    GreetingAvatarStore(Context context){directory=new File(context.getFilesDir(),"greeting-avatars");}
    private void ensure() throws IOException {
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法保存头像，请检查电视存储空间 / Avatar storage unavailable");
    }
    File incoming() throws IOException {ensure();return File.createTempFile("incoming-",".tmp",directory);}
    File candidate() throws IOException {ensure();return new File(directory,"avatar-"+UUID.randomUUID()+".png");}
    File file(String name){return validName(name)?new File(directory,name):null;}
    static boolean validName(String name){return name!=null&&name.matches("avatar-[a-f0-9-]{36}\\.png");}
    File existing(String name){File file=file(name);return file!=null&&file.isFile()?file:null;}
    static void discard(File file){if(file!=null&&file.exists()&&!file.delete())DiagnosticLog.info("greeting.avatar.retained","file");}
    static void prepare(File input,File output) throws IOException {
        if(input.length()<=0||input.length()>MAX_UPLOAD)throw new IOException("头像不能超过 2 MB / Maximum avatar size: 2 MB");
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(input.getPath(),options);
        int w=options.outWidth,h=options.outHeight;
        if(w<=0||h<=0||(long)w*h>40000000L||w>16384||h>16384
                ||!("image/jpeg".equals(options.outMimeType)||"image/png".equals(options.outMimeType)||"image/webp".equals(options.outMimeType)))
            throw new IOException("请选择有效的 JPG、PNG 或静态 WebP 图片 / Invalid image");
        AppCoverImages.checkContainer(input,options.outMimeType);
        int edge=Math.min(w,h);Rect crop=new Rect((w-edge)/2,(h-edge)/2,(w+edge)/2,(h+edge)/2);
        options.inJustDecodeBounds=false;options.inSampleSize=1;
        while(edge/(options.inSampleSize*2)>=512)options.inSampleSize*=2;
        BitmapRegionDecoder decoder=BitmapRegionDecoder.newInstance(input.getPath(),false);Bitmap source=null,oriented=null,result=null;
        if(decoder==null)throw new IOException("无法读取头像 / Cannot decode avatar");
        try{
            source=decoder.decodeRegion(crop,options);if(source==null)throw new IOException("无法读取头像 / Cannot decode avatar");
            ExifInterface exif=new ExifInterface(input);Matrix matrix=new Matrix();if(exif.isFlipped())matrix.postScale(-1,1);matrix.postRotate(exif.getRotationDegrees());
            oriented=Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),matrix,true);
            result=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(result);
            canvas.drawBitmap(oriented,null,new Rect(0,0,512,512),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
            try(FileOutputStream out=new FileOutputStream(output)){if(!result.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Unable to encode avatar");out.getFD().sync();}
        }finally{decoder.recycle();if(result!=null)result.recycle();if(oriented!=null&&oriented!=source)oriented.recycle();if(source!=null)source.recycle();}
    }
    static Bitmap load(File file,int target){
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),options);
        if(options.outWidth!=512||options.outHeight!=512)return null;
        options.inJustDecodeBounds=false;options.inSampleSize=1;
        while(512/(options.inSampleSize*2)>=Math.max(32,target))options.inSampleSize*=2;
        return BitmapFactory.decodeFile(file.getPath(),options);
    }
}
