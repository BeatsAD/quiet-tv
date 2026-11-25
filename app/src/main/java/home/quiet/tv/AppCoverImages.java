package home.quiet.tv;

import android.graphics.*;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;

final class AppCoverImages {
    private AppCoverImages(){}
    static void checkContainer(File input,String mime) throws IOException {
        if(!"image/webp".equals(mime)&&!"image/png".equals(mime))return;
        try(RandomAccessFile file=new RandomAccessFile(input,"r")){
            long position="image/png".equals(mime)?8:12;
            while(position+8<=file.length()){
                file.seek(position);long size;int type;
                if("image/png".equals(mime)){size=file.readInt()&0xffffffffL;type=file.readInt();}
                else{type=file.readInt();size=Integer.reverseBytes(file.readInt())&0xffffffffL;}
                if(type==0x6163544c||type==0x414e494d||type==0x414e4d46)throw new IOException("请选择静态图片 / Animated images are not supported");
                long next=position+8+size+("image/png".equals(mime)?4:(size&1));
                if(next<=position||next>file.length())throw new IOException("图片文件不完整 / Invalid image container");position=next;
            }
        }
    }
    static void prepare(File input,File output) throws IOException {
        if(input.length()==0||input.length()>AppCoverStore.MAX_UPLOAD)throw new IOException("单张封面不能超过 2 MB / Maximum cover size: 2 MB");
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(input.getPath(),options);
        int w=options.outWidth,h=options.outHeight;
        if(w<=0||h<=0||(long)w*h>40000000L||w>16384||h>16384
                ||!("image/jpeg".equals(options.outMimeType)||"image/png".equals(options.outMimeType)||"image/webp".equals(options.outMimeType)))
            throw new IOException("请选择有效的 JPG、PNG 或静态 WebP 图片 / Invalid image");
        checkContainer(input,options.outMimeType);
        ExifInterface exif=new ExifInterface(input);int rotation=exif.getRotationDegrees();boolean swap=rotation==90||rotation==270;
        double ratio=swap?203d/360:360d/203;
        int cw=Math.min(w,(int)Math.floor(h*ratio)),ch=Math.min(h,(int)Math.floor(w/ratio));
        if((swap?ch:cw)<360||(swap?cw:ch)<203)throw new IOException("封面至少需要 360 × 203 像素 / Minimum cover size: 360 × 203");
        Rect crop=new Rect((w-cw)/2,(h-ch)/2,(w+cw)/2,(h+ch)/2);
        options.inJustDecodeBounds=false;options.inSampleSize=1;
        while(cw/(options.inSampleSize*2)>=(swap?406:720)&&ch/(options.inSampleSize*2)>=(swap?720:406))options.inSampleSize*=2;
        BitmapRegionDecoder decoder=BitmapRegionDecoder.newInstance(input.getPath(),false);Bitmap source=null,oriented=null,result=null;
        if(decoder==null)throw new IOException("无法读取封面 / Cannot decode cover");
        try{
            source=decoder.decodeRegion(crop,options);if(source==null)throw new IOException("无法读取封面 / Cannot decode cover");
            Matrix matrix=new Matrix();if(exif.isFlipped())matrix.postScale(-1,1);matrix.postRotate(rotation);
            oriented=Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),matrix,true);
            result=Bitmap.createBitmap(720,406,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(result);canvas.drawColor(AppArtwork.NEUTRAL);
            canvas.drawBitmap(oriented,null,new Rect(0,0,720,406),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
            try(FileOutputStream out=new FileOutputStream(output)){if(!result.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Unable to encode cover");out.getFD().sync();}
        }finally{decoder.recycle();if(result!=null)result.recycle();if(oriented!=null&&oriented!=source)oriented.recycle();if(source!=null)source.recycle();}
    }
    static Bitmap load(File file,int width){
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),options);
        if(!(options.outWidth==720&&(options.outHeight==406||options.outHeight==405)||options.outWidth==512&&options.outHeight==512))return null;
        options.inJustDecodeBounds=false;
        options.inSampleSize=coverSampleSize(options.outWidth,width,android.os.Build.VERSION.SDK_INT);
        return BitmapFactory.decodeFile(file.getPath(),options);
    }
    /** Prepares an owned cover for the TV card without rewriting the saved artwork. */
    static Bitmap desktopBacking(Bitmap image,String form){
        if(!image.hasAlpha()||"transparent".equals(form))return image;
        int width=image.getWidth(),height=image.getHeight(),middleX=width/2,middleY=height/2;
        if(!opaque(image,middleX,middleY))return image;

        Rect backing=new Rect(0,0,width,height);
        while(!opaque(image,backing.left,middleY))backing.left++;
        while(!opaque(image,backing.right-1,middleY))backing.right--;
        while(!opaque(image,middleX,backing.top))backing.top++;
        while(!opaque(image,middleX,backing.bottom-1))backing.bottom--;

        // Uploads lack a form tag. A filled upload must reach an edge of the fitted canvas.
        if(form.isEmpty()&&backing.width()<width*.98f&&backing.height()<height*.98f)return image;
        int w=backing.width(),h=backing.height();
        for(int y=1;y<=3;y++)for(int x=1;x<=3;x++){
            if(!opaque(image,backing.left+w*x/4,backing.top+h*y/4))return image;
        }

        int[] pixels=new int[w*h];
        image.getPixels(pixels,0,w,backing.left,backing.top,w,h);
        if(!opaque(image,backing.left,backing.top)||!opaque(image,backing.right-1,backing.top)
                ||!opaque(image,backing.left,backing.bottom-1)||!opaque(image,backing.right-1,backing.bottom-1)){
            LibraryCover.extendBackground(pixels,w,h);
        }
        // The old silhouette is already baked into PNG alpha. Fill the remaining edge pixels
        // before the shared card path supplies the new antialiased edge.
        int edge=pixels[w/2]|0xff000000;
        for(int i=0;i<pixels.length;i++){
            if((pixels[i]>>>24)!=255)pixels[i]=androidx.core.graphics.ColorUtils.compositeColors(pixels[i],edge);
        }
        Bitmap result=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
        result.setHasAlpha(false);
        image.recycle();
        return result;
    }
    private static boolean opaque(Bitmap image,int x,int y){return Color.alpha(image.getPixel(x,y))>=250;}

    static int coverSampleSize(int sourceWidth,int requestedWidth,int sdk){
        // A 512px square otherwise decodes at full size on low-memory Android 4.x devices.
        int width=Math.max(1,requestedWidth),target=sdk<=19?Math.min(256,width):width,sample=1;
        while(sourceWidth/(sample*2)>=target)sample*=2;
        return sample;
    }
    static void prepareIcon(File input,File output,String shape)throws IOException{
        if(!IconLibraryCatalog.contains(IconLibraryCatalog.SHAPES,shape))throw new IOException("图标形状无效 / Invalid icon shape");
        if(input.length()==0||input.length()>AppCoverStore.MAX_UPLOAD)throw new IOException("图片不能超过 2 MB / Maximum image size: 2 MB");
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(input.getPath(),options);
        int w=options.outWidth,h=options.outHeight;
        if(w<=0||h<=0||w>16384||h>16384||(long)w*h>40000000L||!("image/png".equals(options.outMimeType)||"image/jpeg".equals(options.outMimeType)||"image/webp".equals(options.outMimeType)))throw new IOException("请选择有效的 JPG、PNG 或静态 WebP / Invalid image");
        checkContainer(input,options.outMimeType);ExifInterface exif=new ExifInterface(input);int rotation=exif.getRotationDegrees();boolean swap=rotation==90||rotation==270;
        int width="rectangle".equals(shape)?720:512,height="rectangle".equals(shape)?405:512,minWidth="rectangle".equals(shape)?360:256,minHeight="rectangle".equals(shape)?203:256;
        if((swap?h:w)<minWidth||(swap?w:h)<minHeight)throw new IOException("图片尺寸过小 / Image dimensions are too small");
        options.inJustDecodeBounds=false;options.inSampleSize=1;options.inPreferredConfig=Bitmap.Config.ARGB_8888;options.inScaled=false;
        while(w/(options.inSampleSize*2)>=width&&h/(options.inSampleSize*2)>=height)options.inSampleSize*=2;
        Bitmap source=null,oriented=null,result=null;
        try{
            source=BitmapFactory.decodeFile(input.getPath(),options);if(source==null)throw new IOException("无法读取图标 / Cannot decode icon");
            Matrix matrix=new Matrix();if(exif.isFlipped())matrix.postScale(-1,1);matrix.postRotate(rotation);oriented=Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),matrix,true);
            result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(result);
            if("circle".equals(shape)){Path circle=new Path();circle.addCircle(width/2f,height/2f,width/2f,Path.Direction.CW);canvas.clipPath(circle);}
            float[] fit=LibraryCover.fit(oriented.getWidth(),oriented.getHeight(),width,height);canvas.drawBitmap(oriented,null,new RectF(fit[0],fit[1],fit[2],fit[3]),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
            try(FileOutputStream out=new FileOutputStream(output)){if(!result.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("无法保存图标 / Cannot encode icon");out.getFD().sync();}
            if(output.length()==0||output.length()>AppCoverStore.MAX_UPLOAD)throw new IOException("处理后的图片超过 2 MB / Processed image exceeds 2 MB");
        }finally{if(result!=null)result.recycle();if(oriented!=null&&oriented!=source)oriented.recycle();if(source!=null)source.recycle();}
    }
}
