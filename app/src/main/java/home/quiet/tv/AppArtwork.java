package home.quiet.tv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Build;

/** One local artwork policy for the home, Dock editor and sorting preview. */
final class AppArtwork {
    static final int WIDTH=360,HEIGHT=203,NEUTRAL=0xff505966;

    static Bitmap load(Context context,ResolveInfo info){
        PackageManager pm=context.getPackageManager();Drawable banner=null;
        ActivityInfo activity=info.activityInfo;
        if(Build.VERSION.SDK_INT>=21){banner=resource(pm,activity.applicationInfo,activity.getBannerResource());if(banner==null)banner=activity.loadBanner(pm);}
        Drawable art=banner!=null?banner:resource(pm,activity.applicationInfo,activity.getIconResource());
        if(art==null)art=info.loadIcon(pm);
        return render(art!=null?art:UiCompat.drawable(context,R.drawable.ic_manage_apps),banner!=null,width(context));
    }

    static Bitmap load(Context context,String pkg,ApplicationInfo info){
        ResolveInfo entry=new HomeAppCatalog(context).launchable().get(pkg);
        if(entry!=null)return load(context,entry);
        Drawable icon=info==null?null:resource(context.getPackageManager(),info,info.icon);
        if(icon==null && info!=null)icon=info.loadIcon(context.getPackageManager());
        return render(icon==null?UiCompat.drawable(context,R.drawable.ic_manage_apps):icon,false,width(context));
    }

    private static Drawable resource(PackageManager pm,ApplicationInfo app,int id){
        if(id==0)return null;
        try{return pm.getResourcesForApplication(app).getDrawableForDensity(id,android.util.DisplayMetrics.DENSITY_XXXHIGH);}
        catch(PackageManager.NameNotFoundException|android.content.res.Resources.NotFoundException|SecurityException e){return null;}
    }
    private static int width(Context context){
        android.app.ActivityManager memory=(android.app.ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        if(memory==null || memory.isLowRamDevice() || memory.getMemoryClass()<256)return WIDTH;
        return Math.max(WIDTH,Math.min(720,Math.round(WIDTH*context.getResources().getDisplayMetrics().widthPixels/1920f)));
    }

    static Bitmap render(Drawable art,boolean banner){return render(art,banner,WIDTH);}
    private static Bitmap render(Drawable art,boolean banner,int outputWidth){
        float ratio=outputWidth/(float)WIDTH;int outputHeight=Math.round(HEIGHT*ratio);
        int limitWidth=banner?outputWidth:Math.round(108*ratio),limitHeight=banner?outputHeight:Math.round(108*ratio);
        int width=art==null?1:Math.max(1,art.getIntrinsicWidth()),height=art==null?1:Math.max(1,art.getIntrinsicHeight());
        float scale=Math.min(limitWidth/(float)width,limitHeight/(float)height);
        width=Math.max(1,Math.round(width*scale));height=Math.max(1,Math.round(height*scale));
        Bitmap source=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        if(art!=null){Rect bounds=new Rect(art.getBounds());try{art.setBounds(0,0,width,height);art.draw(new Canvas(source));}finally{art.setBounds(bounds);}}
        Bitmap result=Bitmap.createBitmap(outputWidth,outputHeight,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(result);
        canvas.drawColor(background(source,banner));
        canvas.drawBitmap(source,(outputWidth-width)/2f,(outputHeight-height)/2f,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
        source.recycle();return result;
    }

    private static int background(Bitmap source,boolean banner){
        // Alpha-weighted bins ignore transparent padding and isolated accent pixels.
        int[] weight=new int[4096];long[] red=new long[4096],green=new long[4096],blue=new long[4096];
        float[] hsv=new float[3];int total=0;
        for(int y=0;y<source.getHeight();y++)for(int x=0;x<source.getWidth();x++){
            if(banner && x>1 && y>1 && x<source.getWidth()-2 && y<source.getHeight()-2)continue;
            int color=source.getPixel(x,y),alpha=Color.alpha(color);if(alpha<128)continue;total+=alpha;
            if(!banner){Color.colorToHSV(color,hsv);if(hsv[1]<.16f || hsv[2]<.15f)continue;}
            int r=Color.red(color),g=Color.green(color),b=Color.blue(color),bin=(r>>4<<8)|(g>>4<<4)|(b>>4);
            weight[bin]+=alpha;red[bin]+=(long)r*alpha;green[bin]+=(long)g*alpha;blue[bin]+=(long)b*alpha;
        }
        int best=0;for(int i=1;i<weight.length;i++)if(weight[i]>weight[best])best=i;
        int count=weight[best];if(count==0 || !banner && count<total*.02f)return NEUTRAL;
        int color=Color.rgb((int)(red[best]/count),(int)(green[best]/count),(int)(blue[best]/count));
        if(banner)return color;
        Color.colorToHSV(color,hsv);hsv[1]=Math.min(hsv[1],.65f);hsv[2]=Math.max(.3f,Math.min(hsv[2],.6f));return Color.HSVToColor(hsv);
    }
}
