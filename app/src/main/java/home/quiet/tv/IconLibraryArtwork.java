package home.quiet.tv;

import android.content.Context;
import android.graphics.*;
import java.io.*;

/** Composes supplied artwork; it does not invent the deferred icon designs. */
final class IconLibraryArtwork {
    private IconLibraryArtwork(){}
    static Bitmap render(Context context,IconLibraryCatalog.Item item,String shape,String form,int width,boolean chinese)throws IOException{
        return render(context,item,shape,form,width,chinese,false,new IconLibraryTransfer.Request());
    }
    static Bitmap render(Context context,IconLibraryCatalog.Item item,String shape,String form,int width,boolean chinese,boolean thumbnail,IconLibraryTransfer.Request request)throws IOException{
        if(item.online()){
            if(width<1||width>("rectangle".equals(shape)?720:512))throw new IOException("图标尺寸无效");
            IconLibraryCatalog.Variant variant=item.variant(shape,form);IconLibraryCatalog.Asset asset=thumbnail?variant.thumbnail:variant.image;
            File source=IconLibraryRepository.get(context).artwork(asset,request);request.check();
            BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
            Bitmap image=BitmapFactory.decodeFile(source.getAbsolutePath(),options);
            if(image==null)throw new IOException("图标素材无法读取");
            if(image.getWidth()!=asset.width||image.getHeight()!=asset.height){image.recycle();throw new IOException("图标尺寸不匹配");}
            int target=Math.min(width,asset.width),height="rectangle".equals(shape)?target*9/16:target;
            if(!variant.shape.equals(shape))try{return reshape(image,shape,form,target);}finally{image.recycle();}
            if(image.getWidth()==target&&image.getHeight()==height)return image;
            try{return Bitmap.createScaledBitmap(image,target,height,true);}finally{image.recycle();}
        }
        if(!IconLibraryCatalog.contains(IconLibraryCatalog.SHAPES,shape)||!IconLibraryCatalog.contains(IconLibraryCatalog.forms(shape),form)||width<1||width>("rectangle".equals(shape)?720:512))throw new IOException("图标选项无效");
        int height="rectangle".equals(shape)?width*9/16:width;Bitmap foreground=null,background=null,result=null;
        try{
            foreground=decode(context,item.foreground,Math.max(1,Math.round(Math.min(width,height)*.72f)));
            if(!"transparent".equals(form)&&!item.background.isEmpty())background=decode(context,item.background,width);
            result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(result);Paint paint=new Paint("pixel".equals(item.style)?Paint.ANTI_ALIAS_FLAG:3);float side=Math.min(width,height),inset=side*.14f;
            Path clip=new Path();if("circle".equals(shape))clip.addCircle(width/2f,height/2f,side/2,Path.Direction.CW);else SurfaceCorners.path(clip,new RectF(0,0,width,height),SurfaceCorners.artworkRadius(width,height));
            if(background!=null){float scale=Math.max(width/(float)background.getWidth(),height/(float)background.getHeight()),w=background.getWidth()*scale,h=background.getHeight()*scale;canvas.drawBitmap(background,null,new RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),paint);}
            boolean text="text".equals(form);RectF area;
            if(text)area=new RectF(inset,inset,width*.48f,height-inset);
            else area=new RectF(inset,inset,width-inset,height-inset);
            float[] fit=LibraryCover.fit(foreground.getWidth(),foreground.getHeight(),area.width(),area.height());canvas.drawBitmap(foreground,null,new RectF(area.left+fit[0],area.top+fit[1],area.left+fit[2],area.top+fit[3]),paint);
            if(text){String label=item.label(chinese);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(side*.15f);paint.setColor(Color.WHITE);float limit=width*.43f;
                label=android.text.TextUtils.ellipsize(label,new android.text.TextPaint(paint),limit,android.text.TextUtils.TruncateAt.END).toString();Paint.FontMetrics metrics=paint.getFontMetrics();
                paint.setTextAlign(Paint.Align.LEFT);canvas.drawText(label,width*.52f,height/2f-(metrics.ascent+metrics.descent)/2,paint);}
            Bitmap mask=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);try{Paint edge=new Paint(Paint.ANTI_ALIAS_FLAG);edge.setColor(Color.WHITE);new Canvas(mask).drawPath(clip,edge);paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));canvas.drawBitmap(mask,0,0,paint);paint.setXfermode(null);}finally{mask.recycle();}
            Bitmap ready=result;result=null;return ready;
        }finally{if(foreground!=null)foreground.recycle();if(background!=null)background.recycle();if(result!=null)result.recycle();}
    }
    static void prepare(Context context,IconLibraryCatalog.Item item,String shape,String form,File output,boolean chinese)throws IOException{
        prepare(context,item,shape,form,output,chinese,new IconLibraryTransfer.Request());
    }
    static void prepare(Context context,IconLibraryCatalog.Item item,String shape,String form,File output,boolean chinese,IconLibraryTransfer.Request request)throws IOException{
        request.check();
        if(item.online()&&item.variant(shape,form).shape.equals(shape)){
            IconLibraryCatalog.Asset asset=item.variant(shape,form).image;
            File source=IconLibraryRepository.get(context).artwork(asset,request);
            try(InputStream input=new FileInputStream(source);FileOutputStream stream=new FileOutputStream(output)){
                byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){request.check();stream.write(buffer,0,count);}stream.getFD().sync();
            }
            if(output.length()!=asset.bytes||!IconLibraryCache.hash(output).equals(asset.sha256))throw new IOException("图标保存校验失败");
        }else{
            Bitmap image=render(context,item,shape,form,"rectangle".equals(shape)?720:512,chinese,false,request);try{write(image,output);}finally{image.recycle();}
        }
    }
    private static Bitmap reshape(Bitmap source,String shape,String form,int width){
        Bitmap extended=null;
        try{
            if(!"transparent".equals(form)){int w=source.getWidth(),h=source.getHeight();int[] pixels=new int[w*h];source.getPixels(pixels,0,w,0,0,w,h);LibraryCover.extendBackground(pixels,w,h);extended=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);}
            BitmapShader shader=new BitmapShader(extended==null?source:extended,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
            float scale=Math.min(width/(float)source.getWidth(),width/(float)source.getHeight());Matrix matrix=new Matrix();matrix.setScale(scale,scale);matrix.postTranslate((width-source.getWidth()*scale)/2,(width-source.getHeight()*scale)/2);shader.setLocalMatrix(matrix);
            Bitmap result=Bitmap.createBitmap(width,width,Bitmap.Config.ARGB_8888);Paint paint=new Paint(3);paint.setShader(shader);float radius="circle".equals(shape)?width/2f:SurfaceCorners.artworkRadius(width,width);SurfaceCorners.draw(new Canvas(result),new RectF(0,0,width,width),radius,radius,paint);return result;
        }finally{if(extended!=null)extended.recycle();}
    }
    private static Bitmap decode(Context context,String path,int width)throws IOException{
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;try(InputStream input=context.getAssets().open(path)){BitmapFactory.decodeStream(input,null,options);}
        if(options.outWidth<=0||options.outHeight<=0||options.outWidth>720||options.outHeight>720||!"image/png".equals(options.outMimeType))throw new IOException("图标素材无法读取");
        options.inSampleSize=1;while(options.outWidth/(options.inSampleSize*2)>=width&&options.outHeight/(options.inSampleSize*2)>=width)options.inSampleSize*=2;options.inJustDecodeBounds=false;
        options.inScaled=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        try(InputStream input=context.getAssets().open(path)){Bitmap image=BitmapFactory.decodeStream(input,null,options);if(image==null)throw new IOException("图标素材无法读取");return image;}
    }
    static void write(Bitmap image,File output)throws IOException{
        try(FileOutputStream stream=new FileOutputStream(output)){if(!image.compress(Bitmap.CompressFormat.PNG,100,stream))throw new IOException("图标保存失败");stream.getFD().sync();}
        if(output.length()==0||output.length()>1024*1024)throw new IOException("图标大小超出限制");
    }
}
