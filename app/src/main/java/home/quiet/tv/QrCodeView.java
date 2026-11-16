package home.quiet.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.view.View;
import android.widget.ImageView;

/** QR artwork and its quiet zone share one rounded square, even in a tall container. */
final class QrCodeView extends ImageView {
    private final SurfaceDrawable surface;
    private final Path clip=new Path();
    QrCodeView(Context context,float unit){
        super(context);setScaleType(ScaleType.FIT_CENTER);
        surface=new SurfaceDrawable(unit);surface.setColor(Color.WHITE);surface.setCornerRadius(SurfaceCorners.CONTROL*unit);
        if(android.os.Build.VERSION.SDK_INT<18)setLayerType(View.LAYER_TYPE_SOFTWARE,null);
    }
    @Override public void draw(Canvas canvas){
        int side=Math.min(getWidth(),getHeight()),left=(getWidth()-side)/2,top=(getHeight()-side)/2;
        surface.setBounds(left,top,left+side,top+side);surface.path(clip);
        int save=canvas.save();canvas.clipPath(clip);surface.draw(canvas);super.draw(canvas);canvas.restoreToCount(save);
    }
}
