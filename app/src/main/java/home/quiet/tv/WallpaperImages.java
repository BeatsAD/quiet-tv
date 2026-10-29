package home.quiet.tv;

import android.content.Context;
import android.graphics.*;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;

final class WallpaperImages {
    private WallpaperImages() {}
    static InputStream open(Context context,WallpaperStore store,WallpaperCatalog.Entry entry) throws IOException {
        if(entry.video()){
            if(!entry.custom())return context.getAssets().open("wallpapers/"+entry.id+".jpg");
            File poster=store.poster(entry.id);
            return new FileInputStream(poster);
        }
        if(WallpaperCatalog.DAILY.equals(entry.id)){
            WallpaperCatalog state=store.snapshot();WallpaperCatalog.DailyImage image=state.dailyImages.get(state.dailySource);
            if(store.cached(image))return new FileInputStream(store.dailyFile(image.source,image.slot));
            WallpaperCatalog.Entry fallback=state.find(state.lastLocal);
            if(fallback==null||WallpaperCatalog.DEFAULT.equals(fallback.id))throw new IOException("Daily wallpaper cache unavailable");
            return open(context,store,fallback);
        }
        return entry.custom()?new FileInputStream(store.file(entry.id)):context.getAssets().open("wallpapers/"+entry.id+".jpg");
    }
    static Bitmap decode(Context context,WallpaperStore store,WallpaperCatalog.Entry entry,int width,int height) throws IOException {
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        try(InputStream input=open(context,store,entry)){BitmapFactory.decodeStream(input,null,options);}
        if(options.outWidth<=0 || options.outHeight<=0) throw new IOException("Invalid wallpaper image");
        int sample=1;
        while(options.outWidth/(sample*2)>=width && options.outHeight/(sample*2)>=height) sample*=2;
        options.inJustDecodeBounds=false;options.inSampleSize=sample;
        try(InputStream input=open(context,store,entry)) {
            Bitmap bitmap=BitmapFactory.decodeStream(input,null,options);
            if(bitmap==null) throw new IOException("Invalid wallpaper image"); return bitmap;
        }
    }
    static int importLimit(Context context){
        android.util.DisplayMetrics screen=context.getResources().getDisplayMetrics();
        android.app.ActivityManager memory=(android.app.ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        return importLimit(screen.widthPixels,screen.heightPixels,memory==null || UiCompat.lowRam(memory),memory==null?0:memory.getMemoryClass());
    }
    static int importLimit(int width,int height,boolean lowRam,int memoryMb){
        int maximum=lowRam || memoryMb<256?1920:3840;
        return Math.max(1,Math.min(maximum,Math.max(width,height)));
    }
    static int[] prepare(File input,File output) throws IOException {return prepare(input,output,1920);}
    static int[] prepare(File input,File output,int maximum) throws IOException {
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(input.getPath(),options);
        if(options.outWidth<=0 || options.outHeight<=0 || (long)options.outWidth*options.outHeight>40000000L
                || options.outWidth>16384 || options.outHeight>16384
                || !("image/jpeg".equals(options.outMimeType)||"image/png".equals(options.outMimeType)||"image/webp".equals(options.outMimeType)))
            throw new IOException("请选择有效的 JPG、PNG 或静态 WebP 图片（不超过 4000 万像素） / Invalid image");
        // Reject animated WebP containers, including on systems whose decoder only reads frame one.
        if("image/webp".equals(options.outMimeType)) try(RandomAccessFile file=new RandomAccessFile(input,"r")) {
            file.seek(12);long position=12;
            while(position+8<=file.length()) {
                int type=file.readInt();long size=(Integer.reverseBytes(file.readInt()) & 0xffffffffL);
                if(type==0x414e494d || type==0x414e4d46) throw new IOException("动态图片暂不支持，请使用 MP4 视频 / Use MP4 for animated wallpapers");
                position+=8+size+(size&1);if(position>file.length()) break;file.seek(position);
            }
        }
        options.inJustDecodeBounds=false;options.inSampleSize=1;
        if(maximum<1 || maximum>3840)throw new IllegalArgumentException("Invalid image target size");
        while(Math.max(options.outWidth,options.outHeight)/(options.inSampleSize*2)>=maximum) options.inSampleSize*=2;
        Bitmap bitmap=BitmapFactory.decodeFile(input.getPath(),options);
        if(bitmap==null) throw new IOException("无法读取图片 / Cannot decode image");
        try {
            ExifInterface exif=new ExifInterface(input); Matrix matrix=new Matrix();
            if(exif.isFlipped()) matrix.postScale(-1,1);
            matrix.postRotate(exif.getRotationDegrees());
            float scale=Math.min(1f,maximum/(float)Math.max(bitmap.getWidth(),bitmap.getHeight()));matrix.postScale(scale,scale);
            Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
            if(rotated!=bitmap){bitmap.recycle();bitmap=rotated;}
            if(bitmap.hasAlpha()){Bitmap opaque=Bitmap.createBitmap(bitmap.getWidth(),bitmap.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(opaque);canvas.drawColor(Color.BLACK);canvas.drawBitmap(bitmap,0,0,null);bitmap.recycle();bitmap=opaque;}
            try(FileOutputStream stream=new FileOutputStream(output)) {
                if(!bitmap.compress(Bitmap.CompressFormat.JPEG,90,stream)) throw new IOException("Unable to encode image");stream.getFD().sync();
            }
            return new int[]{bitmap.getWidth(),bitmap.getHeight()};
        } finally { bitmap.recycle(); }
    }
}
