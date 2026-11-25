package home.quiet.tv;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;

import java.util.LinkedHashMap;
import java.util.Map;

/** Draws one moving focus surface between the card backgrounds and their content. */
final class QuickControlCards extends LinearLayout {
    private static final int OFF_FILL = 0x24ffffff;
    private static final LinearInterpolator LINEAR_TIME = new LinearInterpolator();
    private final Map<View, Card> cards = new LinkedHashMap<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inkMask = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect scratch = new Rect();
    private final RectF plate = new RectF();
    private final RectF destination = new RectF();
    private final float corner;
    private View preview;
    private ValueAnimator animator;
    private TabSlideCurve left, top, right, bottom;

    interface Ink { void update(int color, boolean focused, boolean on); }

    static int trackTint(int ink) { return ink == UiPalette.INK ? 0x33000000 : 0x55ffffff; }

    private static final class Card {
        final View view;
        final Ink ink;
        final RectF bounds = new RectF();
        boolean on;
        boolean focused;
        boolean appearanceDirty = true;
        int color = UiPalette.TEXT;

        Card(View view, Ink ink) {
            this.view = view;
            this.ink = ink;
        }
    }

    QuickControlCards(Context context, float unit) {
        super(context);
        corner = SurfaceCorners.CARD * unit;
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

    void cardLostFocus(View focusView) {
        // Moving to the HOME header can bypass clearChildFocus() on this subtree.
        if (cards.containsKey(focusView)) invalidate();
    }

    void setOn(View focusView, boolean on) {
        Card card = cards.get(focusView);
        if (card != null && card.on != on) {
            card.on = on;
            card.appearanceDirty = true;
            invalidate();
        }
    }

    void preview(View view) {
        preview = view;
        syncPlate();
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
        syncPlate();
        invalidate();
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        syncPlate();
    }

    @Override public void clearChildFocus(View child) {
        super.clearChildFocus(child);
        invalidate();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) clearPlate();
        syncPlate();
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        clearPlate();
        super.onDetachedFromWindow();
    }

    private void clearPlate() {
        stopSlide();
        plate.setEmpty();
        destination.setEmpty();
    }

    private void stopSlide() {
        if (animator == null) return;
        ValueAnimator previous = animator;
        animator = null;
        previous.cancel();
    }

    private Card selectedCard() {
        return cards.get(preview != null ? preview : hasWindowFocus() ? findFocus() : null);
    }

    private void readBounds(Card card) {
        scratch.set(0, 0, card.view.getWidth(), card.view.getHeight());
        offsetDescendantRectToMyCoords(card.view, scratch);
        card.bounds.set(scratch);
    }

    private void syncPlate() {
        Card selected = selectedCard();
        if (selected == null) {
            clearPlate();
            return;
        }
        readBounds(selected);
        if (selected.bounds.isEmpty()) clearPlate();
        else movePlate(selected.bounds);
    }

    private void movePlate(RectF bounds) {
        if (bounds.equals(destination)) return;
        destination.set(bounds);
        if (plate.isEmpty() || !hasWindowFocus() || !UiMotion.enabled(this)) {
            stopSlide();
            plate.set(bounds);
            return;
        }

        // Keep the position and velocity from the same frame when a direction key retargets the plate.
        boolean moving = animator != null;
        float fraction = moving ? animator.getAnimatedFraction() : 0;
        stopSlide();
        int duration = getResources().getInteger(R.integer.quiet_motion_panel_ms);
        left = edge(plate.left, bounds.left, moving ? left : null, fraction, duration);
        top = edge(plate.top, bounds.top, moving ? top : null, fraction, duration);
        right = edge(plate.right, bounds.right, moving ? right : null, fraction, duration);
        bottom = edge(plate.bottom, bounds.bottom, moving ? bottom : null, fraction, duration);
        ValueAnimator slide = ValueAnimator.ofFloat(0, 1);
        slide.setDuration(duration);
        slide.setInterpolator(LINEAR_TIME);
        slide.addUpdateListener(animation -> {
            float progress = animation.getAnimatedFraction();
            plate.set(left.position(progress), top.position(progress), right.position(progress), bottom.position(progress));
            invalidate();
        });
        slide.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (animator != animation) return;
                animator = null;
                plate.set(destination);
                invalidate();
            }
        });
        animator = slide;
        slide.start();
    }

    private static TabSlideCurve edge(float start, float end, TabSlideCurve previous, float fraction, int duration) {
        return previous == null ? new TabSlideCurve(start, end, duration)
                : new TabSlideCurve(start, end, previous.velocity(fraction), duration);
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        // The plate uses content coordinates and scrolls and clips with the cards.
        for (Card card : cards.values()) {
            readBounds(card);
            paint.setColor(card.on ? UiPalette.TEXT : OFF_FILL);
            SurfaceCorners.draw(canvas, card.bounds, corner, corner, paint);
        }

        Card selected = selectedCard();
        if (selected == null || selected.bounds.isEmpty()) clearPlate();
        if (!plate.isEmpty()) {
            paint.setColor(UiPalette.TEXT);
            SurfaceCorners.draw(canvas, plate, corner, corner, paint);
        }

        boolean sliding = !plate.equals(destination);
        for (Card card : cards.values()) {
            boolean focused = card == selected;
            int color = card.on || !sliding && focused ? UiPalette.INK : UiPalette.TEXT;
            if (card.appearanceDirty || card.color != color || card.focused != focused) {
                card.appearanceDirty = false;
                card.color = color;
                card.focused = focused;
                card.ink.update(color, focused, card.on);
            }
        }
        if (sliding && getLocalVisibleRect(scratch)) {
            // Tint only the parts of glyphs and tracks that are over the moving white surface.
            int layer = canvas.saveLayer(scratch.left, scratch.top, scratch.right, scratch.bottom,
                    null, Canvas.ALL_SAVE_FLAG);
            super.dispatchDraw(canvas);
            SurfaceCorners.draw(canvas, plate, corner, corner, inkMask);
            canvas.restoreToCount(layer);
        } else {
            super.dispatchDraw(canvas);
        }
    }

    @android.annotation.TargetApi(26) private static final class Api26 {
        static void disableSystemHighlight(View view) { view.setDefaultFocusHighlightEnabled(false); }
    }
}
