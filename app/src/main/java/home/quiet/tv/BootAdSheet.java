package home.quiet.tv;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import java.io.InterruptedIOException;

/** The settings dialog only sends a confirmed action and the state it was confirmed against. */
final class BootAdSheet {
    private final AppManagementActivity ui;
    private final BootAdSettings settings;
    private final ManagementAdb management;
    private final ManagementAdb.Listener connectionChanged = this::connectionChanged;
    private final Runnable configurationChanged = this::configurationChanged;
    private volatile ManagementUi.Sheet sheet;
    private View origin;
    private Button close;
    private boolean working, guide;
    private Boolean restore;
    private String revision, message = "";
    private String rendered;

    BootAdSheet(AppManagementActivity ui) {
        this.ui = ui;
        settings = BootAdSettings.get(ui);
        management = ManagementAdb.get(ui);
    }

    void show() {
        if (sheet != null) return;
        origin = ui.getCurrentFocus();
        guide = false;
        restore = null;
        message = "";
        rendered = null;
        sheet = ui.new Sheet(title());
        close = sheet.settingsLayout(ui.tr("关闭", "Close"), this::back);
        sheet.dialog.setOnDismissListener(dialog -> {
            sheet = null;
            management.removeListener(connectionChanged);
            PhoneSettingsSession.get(ui).forgetConfiguration(configurationChanged);
            if (origin != null && origin.isShown()) origin.requestFocusFromTouch();
        });
        management.addListener(connectionChanged);
        PhoneSettingsSession.get(ui).observeConfiguration(configurationChanged);
        if (!working && settings.canCheck()) refresh();
        else render();
    }

    private String title() { return ui.tr("开机广告", "Startup ads"); }

    static String label(ManagementUi ui, String status) {
        switch (status) {
            case "available": return ui.tr("可关闭", "Available");
            case "applied": return ui.tr("已应用", "Applied");
            case "configured": return ui.tr("电视已设为屏蔽广告", "Already configured");
            case "authorize": return ui.tr("需要授权", "Authorization required");
            case "unsupported": return ui.tr("暂不支持", "Unavailable");
            default: return ui.tr("状态待核对", "Check required");
        }
    }

    private void connectionChanged() {
        if (sheet == null || working || guide || restore != null) return;
        if ("check".equals(settings.state().status)) refresh();
        else render();
    }

    private void configurationChanged() {
        if (sheet != null && !working && !guide && restore == null) render();
    }

    private void refresh() { run(() -> settings.refresh(), false); }

    private void confirm(boolean restoring) {
        restore = restoring;
        revision = settings.state().revision;
        message = "";
        render();
    }

    private void execute() {
        boolean restoring = restore;
        String confirmed = revision;
        restore = null;
        ManagementUi.Sheet current = sheet;
        run(() -> settings.change(restoring, confirmed, () -> sheet == current && !ui.isFinishing()), restoring);
    }

    private interface Operation { void run() throws Exception; }

    private void run(Operation operation, boolean restored) {
        if (working) return;
        working = true;
        message = "";
        ManagementUi.Sheet current = sheet;
        render();
        management.worker.execute(() -> {
            String result = "";
            try {
                operation.run();
                if (restored) result = ui.tr("已恢复此次修改前的开机广告设置。", "Restored the previous startup ad setting.");
            } catch (InterruptedIOException cancelled) {
                result = ui.tr("操作已取消，请重新检测。", "Operation cancelled. Check the current setting.");
            } catch (Exception failure) {
                result = failure instanceof java.io.IOException && failure.getMessage() != null
                        ? failure.getMessage() : ui.tr("操作未完成，请重新检测实际设置。", "Operation incomplete. Check the current setting.");
            }
            String resultText = result;
            ui.runOnUiThread(() -> {
                working = false;
                if (sheet != null) {
                    if (sheet == current) message = resultText;
                    render();
                }
                PhoneSettingsSession.get(ui).configurationChanged();
            });
        });
    }

    private void render() {
        BootAdSettings.State state = settings.state();
        String key = state.status + "|" + state.revision + "|" + state.detail + "|" + state.needsAuthorization + "|" + guide + "|" + restore + "|" + working + "|" + message;
        if (key.equals(rendered)) return;
        rendered = key;
        sheet.body.removeAllViews();
        sheet.actions.removeAllViews();
        sheet.settingsActions(restore != null);
        close.setText(guide || restore != null ? ui.tr("返回", "Back") : ui.tr("关闭", "Close"));
        close.setEnabled(!working);
        sheet.heading.setText(title());
        View focus = close;
        if (guide) {
            sheet.heading.setText(ui.tr("电脑 AI 排查", "Diagnose with AI on a computer"));
            guideContent(ui, sheet);
        } else if (restore != null) {
            sheet.heading.setText(restore ? ui.tr("恢复原设置？", "Restore the previous setting?")
                    : ui.tr("关闭开机广告？", "Turn off startup ads?"));
            if (restore) {
                sheet.text(ui.tr("将撤销 Quiet TV 对开机广告的修改，广告可能再次出现。",
                        "This reverses the Quiet TV change. Startup ads may appear again."));
            } else {
                if (settings.fromUpstream()) sheet.text(ui.tr("此方案来自开源项目的同款固件实测。", "This option is based on upstream testing on the matching firmware."));
                else if (!settings.impact().isEmpty()) sheet.text(settings.impact());
                sheet.text(ui.tr("关闭效果因电视机型和系统版本而异。下次开机时，请确认广告是否消失。",
                        "Results vary by TV model and system version. Check for ads the next time the TV starts."));
                sheet.text(ui.tr("如果仍有广告，可通过电脑 AI 排查原因。",
                        "If ads still appear, use AI on a computer to investigate."));
            }
            focus = sheet.action(ui.tr("取消", "Cancel"), this::back);
            sheet.action(restore ? ui.tr("确认恢复", "Restore") : ui.tr("确认关闭", "Turn off"), this::execute);
        } else if (working) {
            sheet.text(ui.tr("正在核对广告设置…", "Checking the startup ad setting…"));
        } else {
            if (!message.isEmpty()) sheet.text(message);
            sheet.text(label(ui, state.status));
            switch (state.status) {
                case "available":
                    focus = sheet.option(ui.tr("关闭开机广告", "Turn off startup ads"), () -> confirm(false));
                    break;
                case "applied":
                    sheet.text(ui.tr("下次开机时，请确认广告是否消失。", "Check for ads the next time the TV starts."));
                    focus = sheet.option(ui.tr("恢复原设置", "Restore the previous setting"), () -> confirm(true));
                    break;
                case "unsupported":
                    sheet.text(ui.tr("当前机型或系统版本暂不支持直接关闭开机广告。可通过电脑 AI 排查是否有适用的关闭方式。",
                            "Startup ads cannot be turned off directly on this TV model or system version. AI on a computer can check for other options."));
                    if (settings.canCheck()) focus = sheet.option(ui.tr("重新检测", "Check again"), this::refresh);
                    break;
                case "check":
                case "review":
                case "configured":
                    if (!state.detail.isEmpty()) sheet.text(state.detail);
                    if (settings.canCheck()) focus = sheet.option(ui.tr("重新检测", "Check again"), this::refresh);
                    break;
            }
            if (state.needsAuthorization) {
                focus = sheet.option(ui.tr("去授权", "Authorize"), () -> {
                    sheet.dialog.dismiss();
                    ui.adbTool();
                });
            }
            View help = sheet.option(ui.tr("电脑 AI 排查", "Diagnose with AI on a computer"), () -> {
                guide = true;
                render();
            });
            if (focus == close) focus = help;
        }
        sheet.show(focus);
    }

    static void guideContent(ManagementUi ui, ManagementUi.Sheet sheet) {
        sheet.text(ui.tr("扫码查看开机广告排查说明，将排查指令交给电脑上的 AI。", "Scan the code for instructions to give to AI on your computer."));
        QrCodeView qr = new QrCodeView(ui, ui.unit);
        qr.setContentDescription(ui.tr("电脑 AI 排查说明二维码", "QR code for computer diagnostics"));
        try {
            qr.setImageBitmap(QrCodeView.encode(BootAdSettings.GUIDE));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ui.px(150), ui.px(150));
            params.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            sheet.body.addView(qr, params);
        } catch (com.google.zxing.WriterException unavailable) {
            sheet.text(BootAdSettings.GUIDE);
        }
    }

    private void back() {
        if (working) return;
        if (!guide && restore == null) sheet.dialog.dismiss();
        else {
            guide = false;
            restore = null;
            render();
        }
    }

    void destroy() { if (sheet != null) sheet.dialog.dismiss(); }
}
