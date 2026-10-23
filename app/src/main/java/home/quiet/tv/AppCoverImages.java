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
        if(options.outWidth!=720||options.outHeight!=406)return null;
        options.inJustDecodeBounds=false;options.inSampleSize=width<720?2:1;return BitmapFactory.decodeFile(file.getPath(),options);
    }
}
