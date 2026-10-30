package home.quiet.tv;

import android.content.Context;
import android.graphics.*;

/** Built-in entries share their artwork with the sorting preview. */
final class HomeEntryArtwork {
    static final int WALLPAPER_COLOR=0xff139d88,DESKTOP_COLOR=0xff205fdd,MANAGEMENT_COLOR=0xffe77821;
    static final float ADD_TOP=42f/203,ADD_BOTTOM=161f/203;

    static Bitmap addFavorite(String title){
        Bitmap bitmap=Bitmap.createBitmap(360,203,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF rect=new RectF(1.2f,42+1.2f,360-1.2f,161-1.2f);
        paint.setColor(0x50080d14);canvas.drawRoundRect(rect,33,33,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.4f);paint.setColor(0x50ffffff);
        canvas.drawRoundRect(rect,33,33,paint);paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(28);
        float width=paint.measureText(title);if(width>260){paint.setTextSize(28*260/width);width=paint.measureText(title);}
        float left=(360-30-20-width)/2,center=(42+161)/2f;
        paint.setColor(0xfff1f2f6);paint.setStrokeWidth(3);paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(left,center,left+30,center,paint);canvas.drawLine(left+15,center-15,left+15,center+15,paint);
        Paint.FontMetrics metrics=paint.getFontMetrics();canvas.drawText(title,left+50,center-(metrics.ascent+metrics.descent)/2,paint);
        return bitmap;
    }

    static Bitmap render(Context context,String title,boolean desktop){
        return render(context,title,desktop?HomeAppCatalog.DESKTOP_SETTINGS:HomeAppCatalog.MANAGEMENT);
    }

    static Bitmap render(Context context,String title,String entry){
        boolean wallpaper=HomeAppCatalog.WALLPAPER.equals(entry),desktop=HomeAppCatalog.DESKTOP_SETTINGS.equals(entry);
        Bitmap bitmap=Bitmap.createBitmap(360,203,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        int top=wallpaper?0xff18ac94:desktop?0xff1878ff:0xffff8b0d,bottom=wallpaper?0xff087665:desktop?0xff1045bd:0xffca4b12;
        paint.setShader(new LinearGradient(0,0,360,203,top,bottom,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,360,203,paint);paint.setShader(null);

        // Decode near the drawing size; do not retain full-resolution icon bitmaps.
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;
        Bitmap icon=BitmapFactory.decodeResource(context.getResources(),wallpaper?R.drawable.home_wallpaper_art:desktop?R.drawable.home_desktop_art:R.drawable.home_management_art,options);
        paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(34);paint.setColor(Color.WHITE);
        float width=paint.measureText(title);if(width>172){paint.setTextSize(34*172/width);width=paint.measureText(title);}
        float left=(360-144-12-width)/2f,size=desktop||wallpaper?144:132,inset=(144-size)/2f;
        canvas.drawBitmap(icon,null,new RectF(left+inset,(203-size)/2f,left+inset+size,(203+size)/2f),paint);icon.recycle();
        Paint.FontMetrics metrics=paint.getFontMetrics();
        canvas.drawText(title,left+156,(203-metrics.ascent-metrics.descent)/2f,paint);
        return bitmap;
    }
}
