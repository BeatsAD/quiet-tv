package home.quiet.tv;

import android.content.Context;
import android.graphics.*;
import android.os.Build;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;

final class WallpaperImages {
    private static final int IMPORT_LONG_EDGE=3840,IMPORT_SHORT_EDGE=2160;
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
            return open(context,store,state.find(state.dailyFallback()));
        }
        if(WallpaperCatalog.BUILTIN.equals(entry.id)){
            return context.getAssets().open(WallpaperCatalog.BUILTIN+".jpg");
        }
        return entry.custom()?new FileInputStream(store.file(entry.id)):context.getAssets().open("wallpapers/"+entry.id+".jpg");
    }
    static Bitmap decode(Context context,WallpaperStore store,WallpaperCatalog.Entry entry,int width,int height) throws IOException {
        if(WallpaperCatalog.DAILY.equals(entry.id)){
            WallpaperCatalog state=store.snapshot();
            if(!store.cached(state.dailyImages.get(state.dailySource))){
                entry=state.find(state.dailyFallback());
            }
        }
        if(entry.custom()&&!entry.video()){
            int memoryEdge=memoryEdge(context);
            return decodeOriginal(store.file(entry.id),width,height,screenLimit(context,memoryEdge),memoryEdge);
        }
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
    // Display limits depend on the UI and memory class; the saved import has a separate 4K cap.
    static int importLimit(Context context){return screenLimit(context,memoryEdge(context));}
    private static int screenLimit(Context context,int memoryEdge){
        android.util.DisplayMetrics screen=context.getResources().getDisplayMetrics();
        return Math.max(1,Math.min(memoryEdge,Math.max(screen.widthPixels,screen.heightPixels)));
    }
    private static int memoryEdge(Context context){
        android.app.ActivityManager memory=(android.app.ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        return memory==null||UiCompat.lowRam(memory)||memory.getMemoryClass()<256?1920:3840;
    }
    static int[] prepare(File input,File output) throws IOException {return prepare(input,output,1920);}
    static int[] prepare(File input,File output,int maximum) throws IOException {
        BitmapFactory.Options options=bounds(input);
        options.inJustDecodeBounds=false;options.inSampleSize=1;
        if(maximum<1 || maximum>3840)throw new IllegalArgumentException("Invalid image target size");
        while(Math.max(options.outWidth,options.outHeight)/(options.inSampleSize*2)>=maximum) options.inSampleSize*=2;
        Bitmap bitmap=BitmapFactory.decodeFile(input.getPath(),options);
        if(bitmap==null) throw new IOException("无法读取图片 / Cannot decode image");
        try {
            ExifInterface exif=new ExifInterface(input); Matrix matrix=orientation(exif);
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
    private static BitmapFactory.Options bounds(File input)throws IOException{
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
        return options;
    }
    private static void validateOriginal(File input,int longEdge)throws IOException{
        // Validate decodability without allocating a full-resolution bitmap or rewriting the file.
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
        while(longEdge/(options.inSampleSize*2)>=256)options.inSampleSize*=2;
        Bitmap check=BitmapFactory.decodeFile(input.getPath(),options);
        if(check==null)throw new IOException("无法读取图片 / Cannot decode image");
        check.recycle();
    }
    // Leave images within 4K byte-for-byte intact; only larger imports create an output file.
    static int[] prepareImport(File input,File output)throws IOException{
        BitmapFactory.Options bounds=bounds(input);
        int sourceWidth=bounds.outWidth,sourceHeight=bounds.outHeight;
        ExifInterface exif=new ExifInterface(input);
        boolean swap=exif.getRotationDegrees()%180!=0;
        int longEdge=Math.max(sourceWidth,sourceHeight),shortEdge=Math.min(sourceWidth,sourceHeight);
        if(longEdge<=IMPORT_LONG_EDGE&&shortEdge<=IMPORT_SHORT_EDGE){
            validateOriginal(input,longEdge);
            return swap?new int[]{sourceHeight,sourceWidth}:new int[]{sourceWidth,sourceHeight};
        }
        Rect crop=importCrop(sourceWidth,sourceHeight,swap);
        int cropWidth=swap?crop.height():crop.width(),cropHeight=swap?crop.width():crop.height();
        double scale=Math.min(1d,Math.min(IMPORT_LONG_EDGE/(double)cropWidth,IMPORT_SHORT_EDGE/(double)cropHeight));
        int width=Math.max(1,(int)Math.round(cropWidth*scale)),height=Math.max(1,(int)Math.round(cropHeight*scale));
        boolean cropped=crop.width()!=sourceWidth||crop.height()!=sourceHeight;
        Bitmap prepared=Build.VERSION.SDK_INT>=28&&!cropped?Api28.scaled(input,width,height,null):null;
        if(prepared==null)prepared=prepareSampled(input,sourceWidth,sourceHeight,crop,cropped,exif,swap,width,height);
        try{
            try(FileOutputStream stream=new FileOutputStream(output)){
                if(!prepared.compress(Bitmap.CompressFormat.PNG,100,stream))throw new IOException("Unable to encode image");stream.getFD().sync();
            }
            return new int[]{prepared.getWidth(),prepared.getHeight()};
        }finally{prepared.recycle();}
    }
    private static Rect importCrop(int width,int height,boolean swap){
        // Decode coordinates follow the encoded axes. A 90° EXIF rotation swaps them.
        int horizontal=swap?9:16,vertical=swap?16:9;
        int units=Math.min(width/horizontal,height/vertical);
        if(units==0)return new Rect(0,0,width,height);
        int left=(width-units*horizontal)/2,top=(height-units*vertical)/2;
        return new Rect(left,top,left+units*horizontal,top+units*vertical);
    }
    private static Bitmap prepareSampled(File input,int sourceWidth,int sourceHeight,Rect crop,boolean cropped,
                                         ExifInterface exif,boolean swap,int width,int height)throws IOException{
        BitmapFactory.Options options=new BitmapFactory.Options();
        int rawWidth=swap?height:width,rawHeight=swap?width:height;
        long budget=2L*width*height;
        Bitmap source=null;
        if(cropped){
            // With native bitmap allocation, keep a sample that covers the target whenever possible.
            long regionBudget=Build.VERSION.SDK_INT>=26?2*budget:budget;
            options.inSampleSize=importSample(crop.width(),crop.height(),rawWidth,rawHeight,regionBudget);
            source=decodeRegion(input,crop,options);
        }
        if(source==null){
            options.inSampleSize=importSample(sourceWidth,sourceHeight,rawWidth,rawHeight,budget);
            source=BitmapFactory.decodeFile(input.getPath(),options);
        }
        if(source==null)throw new IOException("无法读取图片 / Cannot decode image");
        Bitmap prepared=null;
        try{
            Rect area=importCrop(source.getWidth(),source.getHeight(),swap);
            Matrix matrix=orientation(exif);
            float scale=Math.min(1f,Math.min(rawWidth/(float)area.width(),rawHeight/(float)area.height()));
            matrix.postScale(scale,scale);
            prepared=Bitmap.createBitmap(source,area.left,area.top,area.width(),area.height(),matrix,true);
            return prepared;
        }finally{
            if(prepared!=source)source.recycle();
        }
    }
    private static int importSample(int width,int height,int targetWidth,int targetHeight,long budget){
        int sample=1;
        while(width/(sample*2)>=targetWidth&&height/(sample*2)>=targetHeight)sample*=2;
        while((long)((width+sample-1)/sample)*((height+sample-1)/sample)>budget)sample*=2;
        return sample;
    }
    private static Bitmap decodeRegion(File input,Rect crop,BitmapFactory.Options options){
        BitmapRegionDecoder decoder=null;
        try{
            decoder=BitmapRegionDecoder.newInstance(input.getPath(),false);
            return decoder.decodeRegion(crop,options);
        }catch(IOException|RuntimeException|OutOfMemoryError unavailable){return null;}
        finally{if(decoder!=null)decoder.recycle();}
    }
    private static Matrix orientation(ExifInterface exif){
        Matrix matrix=new Matrix();if(exif.isFlipped())matrix.postScale(-1,1);matrix.postRotate(exif.getRotationDegrees());return matrix;
    }
    static int displaySample(int width,int height,int targetWidth,int targetHeight,int memoryEdge){
        int sample=1;
        while(width/(sample*2)>=targetWidth&&height/(sample*2)>=targetHeight)sample*=2;
        long budget=displayBudget(targetWidth,targetHeight,memoryEdge);
        while((long)((width+sample-1)/sample)*((height+sample-1)/sample)>budget)sample*=2;
        return sample;
    }
    private static long displayBudget(int targetWidth,int targetHeight,int memoryEdge){
        long targetPixels=(long)targetWidth*targetHeight;
        long tierPixels=(long)memoryEdge*memoryEdge*9/16;
        // A power-of-two sample can need almost four decoded pixels per target pixel. Allow
        // that headroom within the device tier and preserve the existing large-target budget.
        return Math.max(2*targetPixels,Math.min(4*targetPixels,tierPixels));
    }
    private static Bitmap decodeOriginal(File input,int width,int height,int limit,int memoryEdge)throws IOException{
        BitmapFactory.Options options=bounds(input);
        ExifInterface exif=new ExifInterface(input);
        float fit=Math.min(1f,limit/(float)Math.max(1,Math.max(width,height)));
        int targetWidth=Math.max(1,Math.round(width*fit)),targetHeight=Math.max(1,Math.round(height*fit));
        boolean swap=exif.getRotationDegrees()%180!=0;
        int orientedWidth=swap?options.outHeight:options.outWidth,orientedHeight=swap?options.outWidth:options.outHeight;
        float sourceFit=Math.min(1f,Math.min(orientedWidth/(float)targetWidth,orientedHeight/(float)targetHeight));
        targetWidth=Math.max(1,Math.round(targetWidth*sourceFit));targetHeight=Math.max(1,Math.round(targetHeight*sourceFit));
        options.inJustDecodeBounds=false;
        options.inSampleSize=displaySample(orientedWidth,orientedHeight,targetWidth,targetHeight,memoryEdge);
        if(Build.VERSION.SDK_INT>=28&&(orientedWidth/options.inSampleSize<targetWidth||orientedHeight/options.inSampleSize<targetHeight)){
            double scale=Math.max(targetWidth/(double)orientedWidth,targetHeight/(double)orientedHeight);
            int scaledWidth=Math.max(targetWidth,(int)Math.round(orientedWidth*scale));
            int scaledHeight=Math.max(targetHeight,(int)Math.round(orientedHeight*scale));
            // ImageDecoder's crop is in scaled coordinates. Bound the full scaled image as well.
            if((long)scaledWidth*scaledHeight<=displayBudget(targetWidth,targetHeight,memoryEdge)){
                int left=(scaledWidth-targetWidth)/2,top=(scaledHeight-targetHeight)/2;
                Bitmap scaled=Api28.scaled(input,scaledWidth,scaledHeight,new Rect(left,top,left+targetWidth,top+targetHeight));
                if(scaled!=null)return scaled;
            }
        }
        Bitmap source=BitmapFactory.decodeFile(input.getPath(),options);
        if(source==null)throw new IOException("无法读取图片 / Cannot decode image");
        Bitmap display=null;
        try {
            Matrix matrix=orientation(exif);RectF rectangle=new RectF(0,0,source.getWidth(),source.getHeight());matrix.mapRect(rectangle);
            float sampledFit=Math.min(1f,Math.min(rectangle.width()/targetWidth,rectangle.height()/targetHeight));
            targetWidth=Math.max(1,Math.round(targetWidth*sampledFit));targetHeight=Math.max(1,Math.round(targetHeight*sampledFit));
            float scale=Math.max(targetWidth/rectangle.width(),targetHeight/rectangle.height());
            matrix.postTranslate(-rectangle.left,-rectangle.top);matrix.postScale(scale,scale);
            matrix.postTranslate((targetWidth-rectangle.width()*scale)/2,(targetHeight-rectangle.height()*scale)/2);
            display=Bitmap.createBitmap(targetWidth,targetHeight,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(display);canvas.drawColor(Color.BLACK);canvas.drawBitmap(source,matrix,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
            return display;
        }catch(RuntimeException|OutOfMemoryError failure){if(display!=null)display.recycle();throw failure;}
        finally{source.recycle();}
    }
    @androidx.annotation.RequiresApi(28)
    private static class Api28 {
        static Bitmap scaled(File input,int width,int height,Rect crop){
            try{
                return ImageDecoder.decodeBitmap(ImageDecoder.createSource(input),(decoder,info,source)->{
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    decoder.setTargetSize(width,height);
                    if(crop!=null){
                        decoder.setCrop(crop);
                        decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
                        decoder.setPostProcessor(canvas->{canvas.drawColor(Color.BLACK,PorterDuff.Mode.DST_OVER);return PixelFormat.OPAQUE;});
                    }
                });
            }catch(IOException|OutOfMemoryError unavailable){return null;}
        }
    }
}
