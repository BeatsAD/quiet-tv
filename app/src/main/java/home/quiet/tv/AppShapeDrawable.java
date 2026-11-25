package home.quiet.tv;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Center-cropped artwork and continuous corners work on every supported Android API. */
final class AppShapeDrawable extends Drawable {
    private final Bitmap image;private final boolean circle,contain,fit;private final Paint paint=new Paint(3);private final BitmapShader shader;private final RectF contained=new RectF();
    private final String previewShape;
    AppShapeDrawable(Bitmap image,boolean circle){this(image,circle,false);}
    AppShapeDrawable(Bitmap image,boolean circle,boolean contain){this(image,circle,contain,null);}
    AppShapeDrawable(Bitmap image,String shape,boolean contain){this(image,"circle".equals(shape),contain,shape);}
    static AppShapeDrawable desktop(Bitmap image,boolean circle,boolean fit){return new AppShapeDrawable(image,circle,fit&&image.hasAlpha(),null,fit);}
    private AppShapeDrawable(Bitmap image,boolean circle,boolean contain,String previewShape){this(image,circle,contain,previewShape,false);}
    private AppShapeDrawable(Bitmap image,boolean circle,boolean contain,String previewShape,boolean fit){
        this.image=image;this.circle=circle;this.contain=contain;this.previewShape=previewShape;this.fit=fit;
        shader=contain?null:new BitmapShader(image,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
    }
    @Override public int getIntrinsicWidth(){return previewShape==null?-1:"rectangle".equals(previewShape)?360:203;}
    @Override public int getIntrinsicHeight(){return previewShape==null?-1:203;}
    @Override protected void onBoundsChange(Rect b){if(contain){float[] fit=LibraryCover.fit(image.getWidth(),image.getHeight(),b.width(),b.height());contained.set(b.left+fit[0],b.top+fit[1],b.left+fit[2],b.top+fit[3]);return;}float scaleX=b.width()/(float)image.getWidth(),scaleY=b.height()/(float)image.getHeight();float scale=fit?Math.min(scaleX,scaleY):Math.max(scaleX,scaleY);Matrix matrix=new Matrix();matrix.setScale(scale,scale);matrix.postTranslate(b.left+(b.width()-image.getWidth()*scale)/2,b.top+(b.height()-image.getHeight()*scale)/2);shader.setLocalMatrix(matrix);}
    @Override public void draw(Canvas c){Rect b=getBounds();if(contain){paint.setShader(null);c.drawBitmap(image,null,contained,paint);return;}paint.setShader(shader);float radius=circle?Math.min(b.width(),b.height())/2f:SurfaceCorners.artworkRadius(b.width(),b.height());SurfaceCorners.draw(c,new RectF(b),radius,radius,paint);}
    @Override public void setAlpha(int value){paint.setAlpha(value);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter value){paint.setColorFilter(value);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
