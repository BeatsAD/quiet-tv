package home.quiet.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Path;
import android.view.View;
import android.widget.ImageView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.Collections;

/** QR artwork and its quiet zone share one rounded square, even in a tall container. */
final class QrCodeView extends ImageView {
    static final int SIDE=136;
    private final SurfaceDrawable surface;
    private final Path clip=new Path();
    QrCodeView(Context context,float unit){
        super(context);setScaleType(ScaleType.FIT_CENTER);
        surface=new SurfaceDrawable(unit);surface.setColor(Color.WHITE);surface.setCornerRadius(SurfaceCorners.CONTROL*unit);
        if(android.os.Build.VERSION.SDK_INT<18)setLayerType(View.LAYER_TYPE_SOFTWARE,null);
    }
    static Bitmap encode(String address)throws WriterException{
        BitMatrix matrix=new QRCodeWriter().encode(address,BarcodeFormat.QR_CODE,0,0,
                Collections.singletonMap(EncodeHintType.MARGIN,2));
        int size=matrix.getWidth()*8;
        int[] pixels=new int[size*size];
        for(int y=0;y<size;y++)for(int x=0;x<size;x++)
            pixels[y*size+x]=matrix.get(x/8,y/8)?Color.BLACK:Color.WHITE;
        return Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);
    }
    @Override public void setImageBitmap(Bitmap bitmap){
        super.setImageBitmap(bitmap);
        if(getDrawable() instanceof BitmapDrawable)((BitmapDrawable)getDrawable()).setFilterBitmap(false);
    }
    @Override public void draw(Canvas canvas){
        int side=Math.min(getWidth(),getHeight()),left=(getWidth()-side)/2,top=(getHeight()-side)/2;
        surface.setBounds(left,top,left+side,top+side);surface.path(clip);
        int save=canvas.save();canvas.clipPath(clip);surface.draw(canvas);super.draw(canvas);canvas.restoreToCount(save);
    }
}
