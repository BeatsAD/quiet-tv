package home.quiet.tv;

import android.app.Dialog;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;

/** Draft editor for the three usage-time modes in one TV settings dialog. */
final class ChildTimeSheet {
    private static final String[] MODES = {"不限制", "单次限时", "循环限时"};
    private static final String[] DATES = {"周一至周五", "每天", "周六和周日", "自定义"};
    private static final int[] DATE_MASKS = {ChildModePolicy.WEEKDAYS, ChildModePolicy.EVERY_DAY, ChildModePolicy.WEEKEND};
    private static final int[] DURATIONS = {30, 60, 120};
    private static final String[] DAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final int CUSTOM = 3, MAX_MINUTES = 24 * 60;

    private final ManagementUi ui;
    private final ManagementUi.Sheet sheet;
    private final ChildModePolicy.Rule draft;
    private final Function<ChildModePolicy.Rule, String> saveRule;
    private final Consumer<ChildModePolicy.Rule> saved;
    private final Button[] modes = new Button[MODES.length], days = new Button[DAYS.length];
    private final Button close, cancel, save, finishDates;
    private final TextView status, connector, notice;
    private final LinearLayout values, dateEditor, timeEditor;
    private final TvOptionWheel date, duration, hours, minutes;
    private int customDays, customMinutes;

    ChildTimeSheet(ManagementUi ui, String appName, ChildModePolicy.Rule initial,
                   Function<ChildModePolicy.Rule, String> saveRule, Consumer<ChildModePolicy.Rule> saved) {
        this.ui = ui;
        this.draft = initial;
        this.saveRule = saveRule;
        this.saved = saved;
        sheet = ui.new Sheet("使用时长");
        close = sheet.settingsLayout("关闭", sheet.dialog::dismiss);
        sheet.fixedSize(448, 286);
        sheet.box.setTag("child-time-sheet");
        sheet.scroll.setFocusable(false);
        LinearLayout.LayoutParams viewport = (LinearLayout.LayoutParams) sheet.scroll.getLayoutParams();
        int side = ui.px(18.75f);
        viewport.setMargins(-side, 0, -side, 0);
        sheet.scroll.setLayoutParams(viewport);
        sheet.scroll.setPadding(side, 0, side, 0);
        sheet.scroll.setFillViewport(true);

        TextView app = ui.muted(appName, 14);
        app.setSingleLine();
        app.setEllipsize(TextUtils.TruncateAt.END);
        sheet.box.addView(app, sheet.box.indexOfChild(sheet.scroll), new LinearLayout.LayoutParams(-1, ui.px(22)));
        LinearLayout tabs = ui.row();
        tabs.setPadding(ui.px(2), ui.px(2), ui.px(2), ui.px(2));
        tabs.setClipToPadding(false);
        tabs.setBackgroundDrawable(ui.surface(0x36ffffff, 0x28ffffff, 0, 999));
        LinearLayout.LayoutParams tabsSize = new LinearLayout.LayoutParams(-1, ui.px(36));
        tabsSize.topMargin = ui.px(6);
        sheet.box.addView(tabs, sheet.box.indexOfChild(sheet.scroll), tabsSize);
        for (int i = 0; i < modes.length; i++) {
            final int mode = i;
            Button tab = pill(MODES[i], () -> selectMode(mode));
            tab.setTag("child-mode-" + i);
            tab.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit, 999, 0));
            tab.setOnFocusChangeListener((v, focused) -> {
                if (focused && draft.mode != mode) selectMode(mode);
            });
            tabs.addView(tab, new LinearLayout.LayoutParams(0, -1, 1));
            modes[i] = tab;
        }

        FrameLayout content = new FrameLayout(ui);
        sheet.body.setGravity(Gravity.CENTER_VERTICAL);
        sheet.body.addView(content, new LinearLayout.LayoutParams(-1, ui.px(105)));
        status = ui.center("不限使用时长", 16);
        content.addView(status, new FrameLayout.LayoutParams(-1, -1));
        values = ui.row();
        values.setGravity(Gravity.CENTER);
        content.addView(values, new FrameLayout.LayoutParams(-1, -1));
        date = wheel("重复日期", "child-repeat-wheel", this::chooseDate, this::stepDate);
        values.addView(date, width(128));
        connector = ui.center("可用", 15);
        values.addView(connector, new LinearLayout.LayoutParams(-2, -1));
        connector.setPadding(ui.px(10), 0, ui.px(10), 0);

        FrameLayout length = new FrameLayout(ui);
        values.addView(length, width(172));
        duration = wheel("时长", "child-duration-wheel", this::chooseDuration, this::stepDuration);
        length.addView(duration, new FrameLayout.LayoutParams(ui.px(128), -1, Gravity.CENTER));
        timeEditor = ui.row();
        timeEditor.setGravity(Gravity.CENTER);
        length.addView(timeEditor, new FrameLayout.LayoutParams(-1, -1));
        hours = wheel("小时", "child-hours", () -> minutesFocus(), this::stepHour);
        minutes = wheel("分钟", "child-minutes", this::finishTime, this::stepMinute);
        timeEditor.addView(hours, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams minuteSize = new LinearLayout.LayoutParams(0, -1, 1);
        minuteSize.leftMargin = ui.px(6);
        timeEditor.addView(minutes, minuteSize);

        dateEditor = ui.column();
        dateEditor.setGravity(Gravity.CENTER);
        content.addView(dateEditor, new FrameLayout.LayoutParams(-1, -1));
        dateEditor.addView(ui.center("重复日期", 15), new LinearLayout.LayoutParams(-1, ui.px(24)));
        LinearLayout weekdays = ui.row();
        LinearLayout.LayoutParams weekdaysSize = new LinearLayout.LayoutParams(-1, ui.px(34));
        // Leave room inside the content frame for the outer buttons' focus scale.
        weekdaysSize.setMargins(ui.px(2), 0, ui.px(2), 0);
        dateEditor.addView(weekdays, weekdaysSize);
        for (int i = 0; i < days.length; i++) {
            final int day = i;
            days[i] = pill(DAYS[i], () -> {
                customDays ^= 1 << day;
                renderDays();
            });
            days[i].setTag("child-repeat-day-" + i);
            LinearLayout.LayoutParams daySize = new LinearLayout.LayoutParams(0, -1, 1);
            if (i > 0) daySize.leftMargin = ui.px(4);
            weekdays.addView(days[i], daySize);
        }
        finishDates = pill("完成", this::finishDates);
        dateEditor.addView(finishDates, new LinearLayout.LayoutParams(ui.px(64), ui.px(30)));

        notice = ui.muted("", 12);
        notice.setMaxLines(2);
        notice.setVisibility(View.GONE);
        sheet.box.addView(notice, sheet.box.indexOfChild(sheet.actions), new LinearLayout.LayoutParams(-1, -2));
        cancel = sheet.action("取消", sheet.dialog::dismiss);
        save = sheet.action("保存", this::save);
        cancel.setTag("child-time-cancel");
        save.setTag("child-time-save");
        for (Button action : new Button[]{cancel, save}) {
            action.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, ui.px(14));
            action.setMinWidth(ui.px(52));
        }

        BackNavigation.install(sheet.dialog, this::back);
        sheet.dialog.setOnKeyListener((dialog, key, event) -> KeyFeedback.backKey(ui, event, true, this::back));
        setDateIndex();
        setDurationIndex();
        showMode();
    }

    Dialog dialog() { return sheet.dialog; }

    void show() {
        sheet.show(modes[draft.mode]);
    }

    private LinearLayout.LayoutParams width(int width) {
        return new LinearLayout.LayoutParams(ui.px(width), -1);
    }

    private Button pill(String name, Runnable action) {
        Button button = ui.button(name, action);
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, ui.px(14));
        button.setSingleLine();
        button.setPadding(0, 0, 0, 0);
        button.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit, 999, 0x09ffffff));
        return button;
    }

    private TvOptionWheel wheel(String label, String tag, Runnable choose, Consumer<Integer> change) {
        TvOptionWheel wheel = new TvOptionWheel(ui, label, choose, change);
        wheel.setTag(tag);
        return wheel;
    }

    private void selectMode(int mode) {
        draft.mode = mode;
        setDateIndex();
        setDurationIndex();
        showMode();
    }

    private void showMode() {
        for (int i = 0; i < modes.length; i++) {
            modes[i].setSelected(draft.mode == i);
            modes[i].setTypeface(ui.navigationTypeface(draft.mode == i));
        }
        status.setVisibility(draft.limited() ? View.GONE : View.VISIBLE);
        values.setVisibility(draft.limited() ? View.VISIBLE : View.GONE);
        date.setVisibility(draft.mode == ChildModePolicy.DAILY ? View.VISIBLE : View.GONE);
        connector.setText(draft.mode == ChildModePolicy.DAILY ? "可用" : "本次可用");
        duration.setVisibility(View.VISIBLE);
        dateEditor.setVisibility(View.GONE);
        timeEditor.setVisibility(View.GONE);
        notice.setVisibility(View.GONE);
        routeFocus();
    }

    private void routeFocus() {
        View mode = modes[draft.mode];
        View firstValue = draft.mode == ChildModePolicy.DAILY ? date : duration;
        for (int i = 0; i < modes.length; i++) {
            arrows(modes[i], close, draft.limited() ? firstValue : save,
                    modes[(i + modes.length - 1) % modes.length], modes[(i + 1) % modes.length]);
        }
        arrows(close, close, mode, mode, close);
        horizontal(date, mode, duration);
        horizontal(duration, draft.mode == ChildModePolicy.DAILY ? date : mode, save);
        horizontal(hours, draft.mode == ChildModePolicy.DAILY ? date : mode, minutes);
        horizontal(minutes, hours, hours);
        for (int i = 0; i < days.length; i++) {
            arrows(days[i], days[i], finishDates, days[Math.max(0, i - 1)], days[Math.min(days.length - 1, i + 1)]);
        }
        arrows(finishDates, days[0], finishDates, finishDates, finishDates);
        arrows(cancel, draft.limited() ? firstValue : mode, cancel, cancel, save);
        arrows(save, draft.limited() ? duration : mode, save, cancel, save);
    }

    private void arrows(View button, View up, View down, View left, View right) {
        button.setOnKeyListener((v, key, event) -> {
            if (key < KeyEvent.KEYCODE_DPAD_UP || key > KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                View target = key == KeyEvent.KEYCODE_DPAD_UP ? up : key == KeyEvent.KEYCODE_DPAD_DOWN ? down
                        : key == KeyEvent.KEYCODE_DPAD_LEFT ? left : right;
                target.requestFocusFromTouch();
            }
            return true;
        });
    }

    private void horizontal(TvOptionWheel wheel, View left, View right) {
        wheel.setHorizontalNavigation(() -> {
            if (wheel == hours) leaveTime();
            left.requestFocusFromTouch();
        }, () -> right.requestFocusFromTouch());
    }

    private void setDateIndex() {
        int index = CUSTOM;
        for (int i = 0; i < DATE_MASKS.length; i++) if (DATE_MASKS[i] == draft.repeatDays) index = i;
        date.setOptions(0, CUSTOM, index, i -> DATES[i], true);
    }

    private void setDurationIndex() {
        int index = CUSTOM;
        for (int i = 0; i < DURATIONS.length; i++) if (DURATIONS[i] == totalMinutes()) index = i;
        duration.setOptions(0, CUSTOM, index, this::durationLabel, true);
    }

    private String durationLabel(int index) {
        if (index != CUSTOM) return ChildModePanel.duration(DURATIONS[index] * ChildModePolicy.MINUTE);
        int value = totalMinutes();
        for (int preset : DURATIONS) if (value == preset) return "自定义";
        return ChildModePanel.duration(draft.limit);
    }

    private int totalMinutes() {
        return (int) (draft.limit / ChildModePolicy.MINUTE);
    }

    private void stepDate(int index) {
        notice.setVisibility(View.GONE);
        if (index != CUSTOM) draft.repeatDays = DATE_MASKS[index];
    }

    private void stepDuration(int index) {
        notice.setVisibility(View.GONE);
        if (index != CUSTOM) draft.limit = DURATIONS[index] * ChildModePolicy.MINUTE;
    }

    private void chooseDate() {
        if (date.value() != CUSTOM) {
            duration.requestFocusFromTouch();
            return;
        }
        customDays = draft.repeatDays;
        renderDays();
        dateEditor.setVisibility(View.VISIBLE);
        days[0].requestFocusFromTouch();
        values.setVisibility(View.GONE);
    }

    private void renderDays() {
        for (int i = 0; i < days.length; i++) {
            boolean checked = (customDays & (1 << i)) != 0;
            days[i].setSelected(checked);
            days[i].setText(SettingsSelectionMark.label(DAYS[i], checked, ui.unit));
            days[i].setContentDescription(DAYS[i] + (checked ? "  已选" : "  未选"));
        }
        notice.setVisibility(View.GONE);
    }

    private void finishDates() {
        if (customDays == 0) {
            error("请至少选择一天");
            days[0].requestFocusFromTouch();
            return;
        }
        draft.repeatDays = customDays;
        leaveDates();
    }

    private void leaveDates() {
        setDateIndex();
        values.setVisibility(View.VISIBLE);
        date.requestFocusFromTouch();
        showMode();
    }

    private void chooseDuration() {
        if (duration.value() != CUSTOM) {
            save.requestFocusFromTouch();
            return;
        }
        customMinutes = totalMinutes();
        hours.setOptions(0, 24, customMinutes / 60, i -> i + " 小时", false);
        configureMinutes();
        timeEditor.setVisibility(View.VISIBLE);
        hours.requestFocusFromTouch();
        duration.setVisibility(View.GONE);
    }

    private void minutesFocus() {
        minutes.requestFocusFromTouch();
    }

    private void configureMinutes() {
        int hour = customMinutes / 60;
        minutes.setOptions(hour == 0 ? 1 : 0, hour == 24 ? 0 : 59,
                customMinutes % 60, i -> i + " 分钟", true);
    }

    private void stepHour(int value) {
        customMinutes = Math.max(1, Math.min(MAX_MINUTES, value * 60 + customMinutes % 60));
        configureMinutes();
    }

    private void stepMinute(int value) {
        customMinutes = customMinutes / 60 * 60 + value;
    }

    private void finishTime() {
        draft.limit = customMinutes * ChildModePolicy.MINUTE;
        leaveTime();
    }

    private void leaveTime() {
        setDurationIndex();
        duration.setVisibility(View.VISIBLE);
        duration.requestFocusFromTouch();
        showMode();
    }

    private void back() {
        if (dateEditor.isShown()) leaveDates();
        else if (timeEditor.isShown()) leaveTime();
        else if (date.hasFocus() || duration.hasFocus()) modes[draft.mode].requestFocusFromTouch();
        else sheet.dialog.dismiss();
    }

    private void save() {
        String problem = saveRule.apply(draft);
        if (problem != null) {
            error(problem);
            return;
        }
        sheet.dialog.dismiss();
        saved.accept(draft);
    }

    private void error(String message) {
        notice.setText(message);
        notice.setVisibility(View.VISIBLE);
    }
}
