package home.quiet.tv;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** Caches the focused card's blurred silhouette for drawing on any Canvas. */
final class AppCardShadow {
    private static final float MASK_SCALE = .5f;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF shadowBounds = new RectF();
    private final Mask cardMask = new Mask();
    private final Mask coverMask = new Mask();

    AppCardShadow() {
        paint.setColor(0xb5000000);
    }

    void draw(Canvas canvas, RectF card, float radius, Bitmap cover, RectF coverBounds, float unit) {
        if (card.isEmpty()) return;
        Mask mask = cover == null ? cardMask : coverMask;
        RectF silhouette = cover == null ? card : coverBounds;
        if (!mask.matches(silhouette, radius, cover, unit)) {
            mask.update(silhouette, radius, cover, unit);
        }
        float left = silhouette.left + mask.offset[0] / MASK_SCALE;
        float top = silhouette.top + mask.offset[1] / MASK_SCALE + 5 * unit;
        shadowBounds.set(left, top, left + mask.bitmap.getWidth() / MASK_SCALE,
                top + mask.bitmap.getHeight() / MASK_SCALE);
        canvas.drawBitmap(mask.bitmap, null, shadowBounds, paint);
    }

    private static final class Mask {
        private final RectF silhouetteBounds = new RectF();
        private final int[] offset = new int[2];
        private Bitmap bitmap;
        private Bitmap cover;
        private float radius;
        private float unit;

        boolean matches(RectF silhouette, float radius, Bitmap cover, float unit) {
            return bitmap != null && this.cover == cover && this.radius == radius
                    && this.unit == unit && silhouetteBounds.equals(silhouette);
        }

        void update(RectF silhouette, float radius, Bitmap cover, float unit) {
            silhouetteBounds.set(silhouette);
            this.radius = radius;
            this.cover = cover;
            this.unit = unit;

            int width = Math.max(1, (int) Math.ceil(silhouette.width() * MASK_SCALE));
            int height = Math.max(1, (int) Math.ceil(silhouette.height() * MASK_SCALE));
            Bitmap alpha = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
            Canvas maskCanvas = new Canvas(alpha);
            maskCanvas.scale(MASK_SCALE, MASK_SCALE);
            RectF bounds = new RectF(0, 0, silhouette.width(), silhouette.height());
            Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            // Contained covers can have their own shape and transparent padding.
            if (cover == null) SurfaceCorners.draw(maskCanvas, bounds, radius, radius, maskPaint);
            else maskCanvas.drawBitmap(cover, null, bounds, maskPaint);

            // Blur on a bitmap so the shadow works on older hardware-accelerated Canvas versions.
            maskPaint.setMaskFilter(new BlurMaskFilter(7.5f * unit * MASK_SCALE, BlurMaskFilter.Blur.NORMAL));
            bitmap = alpha.extractAlpha(maskPaint, offset);
            alpha.recycle();
        }
    }
}
