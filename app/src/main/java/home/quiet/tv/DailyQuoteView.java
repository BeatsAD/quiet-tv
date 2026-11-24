package home.quiet.tv;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

final class DailyQuoteView extends LinearLayout {
    private final TextView text;
    private final TextView credit;
    private DailyQuote.Entry shown;

    DailyQuoteView(Context context, float unit, boolean centered) {
        super(context);
        setOrientation(VERTICAL);
        setFocusable(false);
        setVisibility(GONE);
        text = label(16, 2, unit, UiPalette.TEXT);
        credit = label(11, 1, unit, 0xffe0e0e0);
        addView(text, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LayoutParams source = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        source.topMargin = Math.round(4 * unit);
        addView(credit, source);
        centered(centered);
    }

    private TextView label(int size, int lines, float unit, int color) {
        TextView label = new TextView(getContext());
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * unit);
        label.setTextColor(color);
        label.setIncludeFontPadding(false);
        label.setMaxLines(lines);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setShadowLayer(2.25f * unit, 0, unit, UiPalette.INK);
        return label;
    }

    void centered(boolean centered) {
        int gravity = centered ? Gravity.CENTER_HORIZONTAL : Gravity.LEFT;
        text.setGravity(gravity);
        credit.setGravity(gravity);
    }

    void show(DailyQuote.Entry quote) {
        if (quote == shown) return;
        shown = quote;
        text.setText(quote == null ? "" : quote.text);
        credit.setText(quote == null ? "" : quote.credit);
        setVisibility(quote == null ? GONE : VISIBLE);
    }
}
