package home.quiet.tv;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/** Cache the focused silhouette at its layout size; only its opacity and destination animate. */
final class SettingsFocusShadow {
    static final float CLEARANCE = 10;
    private static final float MASK_SCALE = .5f;

    private final Paint shadowPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF destination = new RectF();
    private final int[] offset = new int[2];
    private Bitmap mask;
    private int width, height;
    private float corner, unit;

    void draw(Canvas canvas, Rect bounds, RectF shape, float radius, float unit, int alpha) {
        if (alpha == 0 || bounds.isEmpty() || shape.isEmpty()) return;
        if (mask == null || width != bounds.width() || height != bounds.height()
                || corner != radius || this.unit != unit) {
            prepare(bounds.width(), bounds.height(), radius, unit);
        }

        float sx = shape.width() / (width * MASK_SCALE);
        float sy = shape.height() / (height * MASK_SCALE);
        float left = shape.left + offset[0] * sx;
        float top = shape.top + offset[1] * sy + 2 * unit;
        destination.set(left, top, left + mask.getWidth() * sx, top + mask.getHeight() * sy);
        shadowPaint.setAlpha(alpha);
        canvas.drawBitmap(mask, null, destination, shadowPaint);
    }

    private void prepare(int width, int height, float corner, float unit) {
        this.width = width;
        this.height = height;
        this.corner = corner;
        this.unit = unit;

        Bitmap silhouette = Bitmap.createBitmap((int) Math.ceil(width * MASK_SCALE),
                (int) Math.ceil(height * MASK_SCALE), Bitmap.Config.ALPHA_8);
        Canvas software = new Canvas(silhouette);
        software.scale(MASK_SCALE, MASK_SCALE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        SurfaceCorners.draw(software, new RectF(0, 0, width, height), corner, corner, paint);
        paint.setMaskFilter(new BlurMaskFilter(3.5f * unit * MASK_SCALE, BlurMaskFilter.Blur.NORMAL));
        mask = silhouette.extractAlpha(paint, offset);
        silhouette.recycle();
    }
}
