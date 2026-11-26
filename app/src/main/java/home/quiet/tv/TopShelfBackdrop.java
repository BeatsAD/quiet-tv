package home.quiet.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

final class TopShelfBackdrop extends View {
    private final Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint shade = new Paint();
    private final Rect source = new Rect();
    private final RectF target = new RectF();
    private Bitmap image;

    TopShelfBackdrop(Context context) {
        super(context);
        androidx.core.view.ViewCompat.setImportantForAccessibility(this,
                androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void image(Bitmap next) {
        if (image == next) return;
        image = next;
        setVisibility(next == null ? GONE : VISIBLE);
        crop();
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        target.set(0, 0, w, h);
        shade.setShader(new LinearGradient(0, 0, 0, Math.max(1, h),
                new int[]{0x73000000, 0x20000000, 0xAB000000},
                new float[]{0, .36f, 1}, Shader.TileMode.CLAMP));
        crop();
    }

    private void crop() {
        if (image == null || target.isEmpty()) return;
        float scale = Math.max(target.width() / image.getWidth(), target.height() / image.getHeight());
        int width = Math.round(target.width() / scale);
        int height = Math.round(target.height() / scale);
        int left = (image.getWidth() - width) / 2;
        int top = (image.getHeight() - height) / 2;
        source.set(left, top, left + width, top + height);
    }

    @Override protected void onDraw(Canvas canvas) {
        if (image != null && !target.isEmpty()) {
            canvas.drawBitmap(image, source, target, imagePaint);
            canvas.drawRect(target, shade);
        }
    }
}
