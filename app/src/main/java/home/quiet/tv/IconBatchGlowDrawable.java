package home.quiet.tv;

import android.animation.ValueAnimator;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

/** A short beam follows the button's clockwise outline. */
final class IconBatchGlowDrawable extends Drawable {
    private static final long LAP_MS = 2400;
    interface Eligibility {boolean allowed();}
    private final float unit;
    private final Eligibility eligibility;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path outline = new Path();
    private final Path rim = new Path();
    private BorderBeamRenderer beam;
    private Shader glass, focusGlass;
    private ValueAnimator animator;
    private float travel;
    private int alpha = 255;

    IconBatchGlowDrawable(float unit,Eligibility eligibility) {
        this.unit = unit;
        this.eligibility = eligibility;
    }

    void syncAnimation() {
        if (!isVisible() || !enabled() || !hasState(android.R.attr.state_window_focused)
                || !eligibility.allowed()) {
            stop();
            return;
        }
        if (animator == null) {
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setInterpolator(new LinearInterpolator());
            animator.setDuration(LAP_MS);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.addUpdateListener(frame -> {
                travel = frame.getAnimatedFraction();
                invalidateSelf();
            });
        }
        if (!animator.isStarted()) animator.start();
    }

    void stop() {
        if (animator != null && animator.isStarted()) {
            animator.cancel();
            invalidateSelf();
        }
    }

    @Override public boolean isStateful() { return true; }

    @Override protected boolean onStateChange(int[] state) {
        syncAnimation();
        invalidateSelf();
        return true;
    }

    @Override public boolean setVisible(boolean visible, boolean restart) {
        boolean changed=super.setVisible(visible,restart);
        if(changed)syncAnimation();
        return changed;
    }

    private boolean focused() {
        return enabled() && hasState(android.R.attr.state_focused)
                && hasState(android.R.attr.state_window_focused);
    }

    private boolean enabled() { return hasState(android.R.attr.state_enabled); }

    private boolean hasState(int attribute) {
        for (int value : getState()) if (value == attribute) return true;
        return false;
    }

    @Override protected void onBoundsChange(Rect bounds) {
        RectF area = new RectF(bounds);
        float radius = 12f * unit;
        outline.reset();
        rim.reset();
        beam = null;
        if (bounds.isEmpty()) return;
        // Match the source shader's circular corner geometry.
        outline.addRoundRect(area, radius, radius, Path.Direction.CW);
        area.inset(.65f * unit, .65f * unit);
        float rimRadius = radius - .65f * unit;
        rim.addRoundRect(area, rimRadius, rimRadius, Path.Direction.CW);
        glass = new LinearGradient(0f, bounds.top, 0f, bounds.bottom,
                0xfff2f8fe, 0xffd4e9f9, Shader.TileMode.CLAMP);
        focusGlass = new LinearGradient(0f, bounds.top, 0f, bounds.bottom,
                0xfffdfeff, 0xffdfedfc, Shader.TileMode.CLAMP);
    }

    @Override public void draw(Canvas canvas) {
        boolean focused = focused();
        boolean enabled = enabled();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(enabled ? Color.WHITE : 0xff28283d);
        paint.setAlpha(alpha);
        paint.setShader(enabled ? (focused ? focusGlass : glass) : null);
        canvas.drawPath(outline, paint);

        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth((focused ? 1.15f : .5f) * unit);
        paint.setColor(focused ? 0xff82b5ff : 0xffb8d3e8);
        paint.setAlpha(alpha * (enabled ? 100 : 38) / 100);
        canvas.drawPath(rim, paint);

        if (animator != null && animator.isStarted() && !outline.isEmpty()) {
            if (beam == null) beam = new BorderBeamRenderer(getBounds(), unit, 12f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            paint.setAlpha(alpha);
            beam.draw(canvas, getBounds(), paint, travel);
        }
    }

    @Override public void setAlpha(int alpha) { this.alpha = alpha; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
