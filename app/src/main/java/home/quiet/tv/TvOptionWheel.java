package home.quiet.tv;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.Consumer;
import java.util.function.IntFunction;

/** Remote-operated vertical selector. Only the value strip moves. */
final class TvOptionWheel extends FrameLayout {
    private final LinearLayout strip;
    private final TextView[] rows = new TextView[5];
    private final String label;
    private final int step;
    private final Consumer<Integer> change;
    private Runnable left, right;
    private IntFunction<String> labels = i -> "";
    private int minimum, maximum, index;
    private boolean wrap;
    private float touchStart;

    TvOptionWheel(ManagementUi ui, String label, Runnable confirm, Consumer<Integer> change) {
        super(ui);
        this.label = label;
        this.change = change;
        step = ui.px(32);
        setFocusableInTouchMode(true);
        setSelected(true);
        setOnClickListener(view -> KeyFeedback.clicked(view,confirm));

        View band = new View(ui);
        band.setDuplicateParentStateEnabled(true);
        band.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit, 999, UiPalette.SELECTED));
        LayoutParams bandSize = new LayoutParams(-1, step, Gravity.CENTER);
        bandSize.leftMargin = bandSize.rightMargin = ui.px(2);
        addView(band, bandSize);

        FrameLayout viewport = new FrameLayout(ui);
        viewport.setClipChildren(true);
        addView(viewport, new LayoutParams(-1, step * 3, Gravity.CENTER));
        strip = ui.column();
        LayoutParams stripSize = new LayoutParams(-1, step * rows.length, Gravity.TOP);
        stripSize.topMargin = -step;
        viewport.addView(strip, stripSize);
        for (int i = 0; i < rows.length; i++) {
            TextView value = ui.center("", i == 2 ? 16 : 14);
            value.setSingleLine();
            value.setEllipsize(TextUtils.TruncateAt.END);
            androidx.core.view.ViewCompat.setImportantForAccessibility(value,
                    androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);
            rows[i] = value;
            strip.addView(value, new LinearLayout.LayoutParams(-1, step));
        }
    }

    void setOptions(int min, int max, int value, IntFunction<String> labels, boolean wrap) {
        this.minimum = min;
        this.maximum = max;
        this.index = value;
        this.labels = labels;
        this.wrap = wrap;
        strip.animate().cancel();
        strip.setTranslationY(0);
        render();
    }

    void setHorizontalNavigation(Runnable left, Runnable right) {
        this.left = left;
        this.right = right;
    }

    int value() { return index; }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (key < KeyEvent.KEYCODE_DPAD_UP || key > KeyEvent.KEYCODE_DPAD_RIGHT) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
                int before=index;
                move(key == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1);
                if(index!=before)KeyFeedback.moved(getContext());
            } else {
                Runnable navigation = key == KeyEvent.KEYCODE_DPAD_LEFT ? left : right;
                if (navigation != null) navigation.run();
            }
        }
        return true;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            touchStart = event.getY();
            requestFocusFromTouch();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float travel = event.getY() - touchStart;
            float offset = event.getY() - getHeight() / 2f;
            if (Math.abs(travel) > step / 3f) move(travel < 0 ? 1 : -1);
            else if (Math.abs(offset) > step / 2f) move(offset > 0 ? 1 : -1);
            else performClick();
            return true;
        }
        return true;
    }

    @Override protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect old) {
        super.onFocusChanged(focused, direction, old);
        if (rows[2] != null) render();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (rows[2] != null) render();
    }

    private void move(int direction) {
        int next = index + direction;
        if (wrap) next = wrapped(next);
        else next = Math.max(minimum, Math.min(maximum, next));
        if (next == index) return;
        index = next;
        change.accept(index);
        render();
        if (UiMotion.enabled(this)) {
            strip.animate().cancel();
            strip.setTranslationY(direction * step);
            strip.animate().translationY(0f).setInterpolator(UiMotion.curve()).setDuration(145).start();
        }
    }

    private void render() {
        for (int row = 0; row < rows.length; row++) {
            int value = index + row - 2;
            boolean visible = (wrap && minimum < maximum) || (value >= minimum && value <= maximum);
            if (visible && wrap) value = wrapped(value);
            rows[row].setText(visible ? labels.apply(value) : "");
            rows[row].setTextColor(row == 2
                    ? hasFocus() && hasWindowFocus() ? UiPalette.INK : UiPalette.TEXT
                    : UiPalette.SECONDARY);
            rows[row].setAlpha(row == 0 || row == 4 ? .35f : 1f);
        }
        setContentDescription(label + "  " + labels.apply(index));
    }

    private int wrapped(int value) {
        int count = maximum - minimum + 1;
        return minimum + ((value - minimum) % count + count) % count;
    }
}
