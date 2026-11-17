package home.quiet.tv;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import java.io.*;

/** Local frames retain the application's own artwork, independently of online identities. */
final class IconLibraryFrame {
    static final int VERSION=2;
    private static final String PREFIX="original:";
    private IconLibraryFrame(){}
    static String id(String pkg){return PREFIX+pkg;}
    static boolean original(String id){return id!=null&&id.startsWith(PREFIX)&&AppCoverStore.packageName(id.substring(PREFIX.length()));}
    static String packageName(String id){return original(id)?id.substring(PREFIX.length()):"";}
    static String sourceKey(Context context,String pkg,String label)throws IOException{
        try{return IconLibraryCache.hash((context.getPackageManager().getPackageInfo(pkg,0).lastUpdateTime+"\n"+label).getBytes(Charsets.UTF_8));}
        catch(PackageManager.NameNotFoundException failure){throw new IOException("应用已变化，请重新匹配",failure);}
    }
    static Bitmap render(Context context,String pkg,String label,String style,String shape,String form,int width)throws IOException{
        if(!IconLibraryCatalog.styleId(style)||!IconLibraryCatalog.contains(IconLibraryCatalog.forms(shape),form)||!IconLibraryCatalog.contains(IconLibraryCatalog.SHAPES,shape)||width<1||width>("rectangle".equals(shape)?720:512))throw new IOException("图标外框选项无效");
        Drawable drawable;try{drawable=AppArtwork.originalIcon(context,pkg);}catch(PackageManager.NameNotFoundException failure){throw new IOException("应用原图标无法读取",failure);}
        boolean pixel="pixel".equals(style),clear="transparent".equals(form),text="text".equals(form);int height="rectangle".equals(shape)?Math.max(1,width*9/16):width;
        int iw=Math.max(1,drawable.getIntrinsicWidth()),ih=Math.max(1,drawable.getIntrinsicHeight()),limit=pixel?40:256;float scale=Math.min(limit/(float)iw,limit/(float)ih);
        Bitmap icon=Bitmap.createBitmap(Math.max(1,Math.round(iw*scale)),Math.max(1,Math.round(ih*scale)),Bitmap.Config.ARGB_8888),result=null;
        try{
            Rect old=new Rect(drawable.getBounds());try{Canvas source=new Canvas(icon);source.scale(icon.getWidth()/(float)iw,icon.getHeight()/(float)ih);drawable.setBounds(0,0,iw,ih);drawable.draw(source);}finally{drawable.setBounds(old);}
            int fill=background(icon),ink=Color.red(fill)*299+Color.green(fill)*587+Color.blue(fill)*114>=150000?0xff172033:Color.WHITE;float side=Math.min(width,height),u=side/100f;
            result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(result);Paint paint=new Paint(pixel?0:Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            RectF frame=new RectF(3*u,3*u,width-3*u,height-3*u);float radius="circle".equals(shape)?side/2:side*("apple".equals(style)?.19f:"google".equals(style)?.23f:.13f);
            Path clip=path(frame,shape,radius);
            if(!clear){
                if(!pixel&&!"flat".equals(style)){paint.setColor(fill);paint.setShadowLayer(2*u,0,1.5f*u,0x65000000);canvas.drawPath(clip,paint);paint.clearShadowLayer();}
                paint.setColor(fill);canvas.drawPath(clip,paint);
            }
            canvas.save();canvas.clipPath(clip);
            if("skeuo".equals(style)){
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5*u);paint.setShader(new LinearGradient(0,frame.top,0,frame.bottom,mix(fill,Color.WHITE,.65f),mix(fill,Color.BLACK,.40f),Shader.TileMode.CLAMP));canvas.drawPath(clip,paint);paint.setShader(null);paint.setStyle(Paint.Style.FILL);
            }else if("soft".equals(style)){
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.5f*u);paint.setColor(0xaaffffff);canvas.save();canvas.translate(-u,-u);canvas.drawPath(clip,paint);canvas.restore();paint.setColor(0x550f233f);canvas.save();canvas.translate(u,u);canvas.drawPath(clip,paint);canvas.restore();paint.setStyle(Paint.Style.FILL);
            }else if("3d".equals(style)){
                paint.setColor(clear?0x80000000:mix(fill,Color.BLACK,.30f));canvas.drawRect(frame.left,frame.bottom-6*u,frame.right,frame.bottom,paint);paint.setColor(0x60ffffff);canvas.drawRect(frame.left,frame.top,frame.right,frame.top+2*u,paint);
            }else if("pixel".equals(style)){
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3*u);paint.setColor(mix(fill,Color.BLACK,.65f));canvas.drawRect(frame,paint);RectF inner=new RectF(frame);inner.inset(4*u,4*u);paint.setColor(mix(fill,Color.WHITE,.65f));paint.setStrokeWidth(2*u);canvas.drawRect(inner,paint);paint.setStyle(Paint.Style.FILL);
                for(int x=0;x<width;x+=Math.max(1,Math.round(8*u))){paint.setColor(mix(fill,Color.BLACK,.25f));canvas.drawRect(x,frame.bottom-5*u,x+3*u,frame.bottom-2*u,paint);}
            }
            if(!pixel){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth("flat".equals(style)?u:1.2f*u);paint.setColor(clear?mix(fill,Color.WHITE,.40f):"google".equals(style)?mix(fill,Color.BLACK,.10f):0x90ffffff);canvas.drawPath(clip,paint);paint.setStyle(Paint.Style.FILL);}
            RectF area=text?new RectF(15*u,20*u,width*.43f,height-20*u):new RectF(frame.centerX()-29*u,frame.centerY()-29*u,frame.centerX()+29*u,frame.centerY()+29*u);
            if("3d".equals(style))area.offset(0,-3*u);
            float[] fit=LibraryCover.fit(icon.getWidth(),icon.getHeight(),area.width(),area.height());paint.setColor(Color.WHITE);paint.setFilterBitmap(!pixel);canvas.drawBitmap(icon,null,new RectF(area.left+fit[0],area.top+fit[1],area.left+fit[2],area.top+fit[3]),paint);
            if(text){paint.setColor(ink);paint.setTypeface(Typeface.create(pixel?"monospace":"sans-serif-medium",Typeface.NORMAL));paint.setTextSize(11*u);float left=width*.49f,available=width-left-12*u;String value=TextUtils.ellipsize(label,new android.text.TextPaint(paint),available,TextUtils.TruncateAt.END).toString();Paint.FontMetrics font=paint.getFontMetrics();canvas.drawText(value,left,height/2f-(font.ascent+font.descent)/2,paint);}
            canvas.restore();Bitmap complete=result;result=null;return complete;
        }finally{icon.recycle();if(result!=null)result.recycle();}
    }
    static void prepare(Context context,String pkg,String label,String style,String shape,String form,File file,IconLibraryTransfer.Request request)throws IOException{
        request.check();Bitmap image=render(context,pkg,label,style,shape,form,"rectangle".equals(shape)?720:512);try{request.check();IconLibraryArtwork.write(image,file);}finally{image.recycle();}
    }
    static int background(Bitmap icon){
        int left=icon.getWidth(),top=icon.getHeight(),right=-1,bottom=-1;
        for(int y=0;y<icon.getHeight();y++)for(int x=0;x<icon.getWidth();x++)if(Color.alpha(icon.getPixel(x,y))>=128){left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);}
        if(right<left)return AppArtwork.NEUTRAL;
        int edge=Math.max(1,Math.min(right-left+1,bottom-top+1)/12);int[] weight=new int[4096];long[] red=new long[4096],green=new long[4096],blue=new long[4096];
        // Sample the visible perimeter, including white and black; the center is usually the logo.
        for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){
            if(x>=left+edge&&x<=right-edge&&y>=top+edge&&y<=bottom-edge)continue;
            int color=icon.getPixel(x,y),alpha=Color.alpha(color);if(alpha<128)continue;int r=Color.red(color),g=Color.green(color),b=Color.blue(color),bin=(r>>4<<8)|(g>>4<<4)|(b>>4);
            weight[bin]+=alpha;red[bin]+=(long)r*alpha;green[bin]+=(long)g*alpha;blue[bin]+=(long)b*alpha;
        }
        int best=0;for(int i=1;i<weight.length;i++)if(weight[i]>weight[best])best=i;int count=weight[best];
        return count==0?AppArtwork.NEUTRAL:Color.rgb(Math.round(red[best]/(float)count),Math.round(green[best]/(float)count),Math.round(blue[best]/(float)count));
    }
    private static Path path(RectF rect,String shape,float radius){Path path=new Path();if("circle".equals(shape))path.addCircle(rect.centerX(),rect.centerY(),Math.min(rect.width(),rect.height())/2,Path.Direction.CW);else path.addRoundRect(rect,radius,radius,Path.Direction.CW);return path;}
    private static int mix(int a,int b,float amount){return Color.rgb(Math.round(Color.red(a)*(1-amount)+Color.red(b)*amount),Math.round(Color.green(a)*(1-amount)+Color.green(b)*amount),Math.round(Color.blue(a)*(1-amount)+Color.blue(b)*amount));}
}
