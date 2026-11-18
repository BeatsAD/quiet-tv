package home.quiet.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.LinearLayout;

import java.util.LinkedHashMap;
import java.util.Map;

/** Draws one moving focus surface between the card backgrounds and their content. */
final class QuickControlCards extends LinearLayout {
    private static final int OFF_FILL = 0x24ffffff;
    private static final int ON_FILL = 0xffe2edf8;
    private static final int ON_INK = 0xff24537d;
    private static final AccelerateDecelerateInterpolator EASE = new AccelerateDecelerateInterpolator();
    private final Map<View, Card> cards = new LinkedHashMap<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inkMask = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect scratch = new Rect();
    private final RectF plate = new RectF();
    private final RectF destination = new RectF();
    private final float corner;
    private View preview;
    private ValueAnimator animator;

    interface Ink { void color(int color); }

    static int trackTint(int ink) { return ink == UiPalette.INK ? 0x33000000 : 0x55ffffff; }

    private static final class Card {
        final View view;
        final Ink ink;
        final RectF bounds = new RectF();
        boolean on;
        int color = UiPalette.TEXT;

        Card(View view, Ink ink) {
            this.view = view;
            this.ink = ink;
        }
    }

    QuickControlCards(Context context, float unit) {
        super(context);
        corner = 12 * unit;
        inkMask.setColor(0xff191919);
        inkMask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        setOrientation(VERTICAL);
        setClipChildren(false);
    }

    void register(View focusView, View cardView, Ink ink) {
        cards.put(focusView, new Card(cardView, ink));
        // The container supplies the focus highlight, including for the range's inner view.
        if (android.os.Build.VERSION.SDK_INT >= 26) Api26.disableSystemHighlight(focusView);
    }

    void setOn(View focusView, boolean on) {
        Card card = cards.get(focusView);
        if (card != null && card.on != on) {
            card.on = on;
            invalidate();
        }
    }

    void preview(View view) {
        preview = view;
        invalidate();
    }

    void clearCards() {
        cards.clear();
        preview = null;
        clearPlate();
        removeAllViews();
    }

    @Override public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        invalidate();
    }

    @Override public void clearChildFocus(View child) {
        super.clearChildFocus(child);
        invalidate();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) clearPlate();
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        clearPlate();
        super.onDetachedFromWindow();
    }

    private void clearPlate() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        plate.setEmpty();
        destination.setEmpty();
    }

    private void movePlate(RectF bounds) {
        if (bounds.equals(destination)) return;
        if (animator != null) animator.cancel();
        destination.set(bounds);
        if (plate.isEmpty() || !hasWindowFocus() || !UiMotion.enabled(this)) {
            plate.set(bounds);
            return;
        }

        RectF start = new RectF(plate);
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(180);
        animator.setInterpolator(EASE);
        animator.addUpdateListener(animation -> {
            float f = (float) animation.getAnimatedValue();
            plate.set(
                    start.left + (destination.left - start.left) * f,
                    start.top + (destination.top - start.top) * f,
                    start.right + (destination.right - start.right) * f,
                    start.bottom + (destination.bottom - start.bottom) * f);
            invalidate();
        });
        animator.start();
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        // The plate uses content coordinates and scrolls and clips with the cards.
        for (Card card : cards.values()) {
            scratch.set(0, 0, card.view.getWidth(), card.view.getHeight());
            offsetDescendantRectToMyCoords(card.view, scratch);
            card.bounds.set(scratch);
            paint.setColor(card.on ? ON_FILL : OFF_FILL);
            canvas.drawRoundRect(card.bounds, corner, corner, paint);
        }

        Card selected = cards.get(preview != null ? preview : hasWindowFocus() ? findFocus() : null);
        if (selected == null || selected.bounds.isEmpty()) clearPlate();
        else movePlate(selected.bounds);
        if (!plate.isEmpty()) {
            paint.setColor(UiPalette.TEXT);
            canvas.drawRoundRect(plate, corner, corner, paint);
        }

        boolean sliding = !plate.equals(destination);
        for (Card card : cards.values()) {
            int color = card.on ? ON_INK : !sliding && card == selected ? UiPalette.INK : UiPalette.TEXT;
            if (card.color != color) {
                card.color = color;
                card.ink.color(color);
            }
        }
        if (sliding && getLocalVisibleRect(scratch)) {
            // Tint only the parts of glyphs and tracks that are over the moving white surface.
            int layer = canvas.saveLayer(scratch.left, scratch.top, scratch.right, scratch.bottom,
                    null, Canvas.ALL_SAVE_FLAG);
            super.dispatchDraw(canvas);
            canvas.drawRoundRect(plate, corner, corner, inkMask);
            canvas.restoreToCount(layer);
        } else {
            super.dispatchDraw(canvas);
        }
    }

    @android.annotation.TargetApi(26) private static final class Api26 {
        static void disableSystemHighlight(View view) { view.setDefaultFocusHighlightEnabled(false); }
    }
}
