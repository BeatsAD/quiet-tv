package home.quiet.tv;

import static home.quiet.tv.FeiniuMovieWall.MEDIA;
import static home.quiet.tv.FeiniuMovieWall.PLEX;
import static home.quiet.tv.FeiniuMovieWall.SMB;

import android.content.Context;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import java.util.List;

final class FeiniuMovieWallPanel {
    private final ManagementUi ui;
    private final FeiniuMovieWall wall;

    FeiniuMovieWallPanel(ManagementUi ui) {
        this.ui = ui;
        wall = FeiniuMovieWall.get(ui);
    }

    void open(View origin) {
        if (wall.busy()) return;
        ManagementUi.Sheet sheet = ui.new Sheet(tr("电影海报来源", "Movie poster source"));
        Button close = sheet.settingsLayout(tr("关闭", "Close"), sheet.dialog::dismiss);
        sheet.settingsActions(false);
        sheet.contentSize(380, 365);
        sheet.dialog.setOnDismissListener(ignored -> restoreFocus(origin));
        LinearLayout group = ui.listGroup();
        sheet.body.addView(group);
        boolean plexActive = PLEX.equals(wall.mediaProvider());
        DesktopSettingsRow feiniu = choice(group, tr("媒体服务器", "Media server"),
                wall.feiniuConnected() && !wall.smbActive() && !plexActive, () -> media(sheet, MEDIA));
        DesktopSettingsRow plex = choice(group, "Plex",
                wall.feiniuConnected() && !wall.smbActive() && plexActive, () -> media(sheet, PLEX));
        DesktopSettingsRow smb = choice(group, tr("NAS 共享文件夹（SMB）", "NAS shared folder (SMB)"),
                wall.smbActive(), () -> login(sheet, sheet, SMB));
        linkDown(close, feiniu);
        linkDown(feiniu, plex);
        linkDown(plex, smb);
        sheet.show(wall.smbActive() ? smb : plexActive ? plex : feiniu);
    }

    private void media(ManagementUi.Sheet picker, String provider) {
        if (!wall.feiniuConnected() || !provider.equals(wall.mediaProvider())) {
            login(picker, picker, provider);
            return;
        }
        ManagementUi.Sheet sheet = ui.new Sheet(PLEX.equals(provider) ? "Plex" : tr("媒体服务器", "Media server"), picker);
        Button close = sheet.settingsLayout(tr("返回", "Back"), sheet.dialog::dismiss);
        sheet.contentSize(380, 365);
        List<FeiniuMediaClient.Item> libraries = wall.libraries();
        View focus = close;
        View previous = close;
        if (libraries.isEmpty()) {
            sheet.text(tr("该账号暂无可用媒体库", "This account has no available libraries"));
        } else {
            LinearLayout group = ui.listGroup();
            sheet.body.addView(group, new LinearLayout.LayoutParams(-1, -2));
            String selected = wall.feiniuLibrary();
            for (FeiniuMediaClient.Item item : libraries) {
                boolean current = item.id.equals(selected);
                DesktopSettingsRow row = choice(group, item.name, current, () -> {
                    sheet.dialog.dismiss();
                    picker.dialog.dismiss();
                    if (!wall.syncFeiniu(item.id, this::report)) ui.error(tr("当前无法同步，请重试", "Unable to sync. Try again."));
                });
                linkDown(previous, row);
                previous = row;
                if (current || focus == close) focus = row;
            }
        }
        Button connection = sheet.action(tr("连接设置", "Connection settings"), () -> login(picker, sheet, provider));
        linkDown(previous, connection);
        sheet.show(focus);
    }

    private DesktopSettingsRow choice(LinearLayout group, String title, boolean selected, Runnable action) {
        DesktopSettingsRow row = new DesktopSettingsRow(ui, title, "", false);
        row.displayStyle();
        row.hideIcon();
        row.setSelected(selected);
        if (selected) row.statusValue("", "check", UiPalette.SECONDARY, UiPalette.INK);
        row.setOnClickListener(view -> action.run());
        group.addView(row, new LinearLayout.LayoutParams(-1, ui.px(42)));
        return row;
    }

    private void login(ManagementUi.Sheet picker, ManagementUi.Sheet underlay, String provider) {
        boolean sharedFolder = SMB.equals(provider), plex = PLEX.equals(provider);
        boolean savedMedia = provider.equals(wall.mediaProvider());
        ManagementUi.Sheet sheet = ui.new Sheet(sharedFolder ? tr("NAS 共享文件夹（SMB）", "NAS shared folder (SMB)")
                : plex ? tr("连接 Plex", "Connect to Plex") : tr("连接媒体服务器", "Connect to media server"), underlay);
        sheet.confirmationLayout(sheet.dialog::dismiss);
        sheet.contentSize(380, 365);
        sheet.dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        EditText address = ui.input(sharedFolder ? wall.smbAddress() : savedMedia ? wall.address() : "", sharedFolder
                ? "smb://NAS/Movies" : tr("服务器地址", "Server address"));
        if (sharedFolder) address.setContentDescription(tr("SMB 地址", "SMB address"));
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText username = plex ? null : ui.input(sharedFolder ? wall.smbName() : savedMedia ? wall.name() : "", sharedFolder
                ? tr("账号（访客留空）", "Username (blank for guest)") : tr("账号", "Username"));
        EditText password = ui.input("", plex ? tr("访问令牌", "Access token") : sharedFolder ? tr("SMB 密码", "SMB password")
                : tr("密码（未设置时留空）", "Password (if set)"));
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setSaveEnabled(false);
        EditText[] fields = plex ? new EditText[]{address, password} : new EditText[]{address, username, password};
        for (EditText field : fields) {
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
            layout.bottomMargin = ui.px(8);
            sheet.body.addView(field, layout);
            field.setOnKeyListener((view, key, event) -> {
                if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
                    int nextId = key == KeyEvent.KEYCODE_DPAD_UP ? view.getNextFocusUpId() : view.getNextFocusDownId();
                    if (nextId == View.NO_ID) return false;
                    View next = sheet.box.findViewById(nextId);
                    if (event.getAction() == KeyEvent.ACTION_DOWN && next != null) next.requestFocusFromTouch();
                    return true;
                }
                if (!KeyFeedback.confirmKey(key)) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    InputMethodManager keyboard = (InputMethodManager) ui.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (keyboard.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)) KeyFeedback.confirmOnPress(ui);
                }
                return true;
            });
        }
        TextView status = ui.muted("", 12);
        status.setVisibility(View.GONE);
        ViewCompat.setAccessibilityLiveRegion(status, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);
        sheet.body.addView(status);

        Button cancel = sheet.action(tr("取消", "Cancel"), sheet.dialog::dismiss);
        Button connect = sheet.action(sharedFolder ? tr("同步海报", "Sync posters") : tr("连接", "Connect"), () -> {});
        connect.setOnClickListener(view -> {
            FeiniuMovieWall.Result complete = () -> {
                if (!sheet.dialog.isShowing() || ui.isFinishing() || UiCompat.destroyed(ui)) return;
                String result = wall.status();
                if (sharedFolder ? "synced".equals(result) : "connected".equals(result) || "empty-libraries".equals(result)) {
                    sheet.dialog.dismiss();
                    if (sharedFolder) {
                        picker.dialog.dismiss();
                        ui.success(tr("已同步 ", "Synced ") + wall.ids().size() + tr(" 张海报", " posters"));
                    } else {
                        if (underlay != picker) underlay.dialog.dismiss();
                        media(picker, provider);
                    }
                } else {
                    sheet.box.setEnabled(true);
                    sheet.dialog.setCancelable(true);
                    for (EditText field : fields) field.setEnabled(true);
                    cancel.setEnabled(true);
                    connect.setEnabled(true);
                    showStatus(status, OperationFeedback.Kind.ERROR, message(result));
                    ("smb-folder".equals(result) ? address : password).requestFocusFromTouch();
                }
            };
            String target = address.getText().toString(), user = plex ? "" : username.getText().toString();
            String secret = password.getText().toString();
            boolean started = sharedFolder ? wall.syncSmb(target, user, secret, complete)
                    : wall.connect(provider, target, user, secret, complete);
            if (started) {
                password.setText("");
                sheet.box.setEnabled(false);
                sheet.dialog.setCancelable(false);
                for (EditText field : fields) field.setEnabled(false);
                cancel.setEnabled(false);
                connect.setEnabled(false);
                showStatus(status, OperationFeedback.Kind.PROGRESS, sharedFolder
                        ? tr("正在同步海报…", "Syncing posters…") : tr("正在连接…", "Connecting…"));
            } else {
                showStatus(status, OperationFeedback.Kind.ERROR, sharedFolder || wall.busy()
                        ? tr("当前无法同步，请稍后重试", "Sync unavailable. Try again shortly.")
                        : plex ? tr("请输入 Plex 访问令牌", "Enter your Plex access token")
                        : tr("请输入媒体账号", "Enter your media account username"));
            }
        });
        for (int i = 1; i < fields.length; i++) linkDown(fields[i - 1], fields[i]);
        linkDown(password, cancel);
        if (connect.getId() == View.NO_ID) connect.setId(ViewCompat.generateViewId());
        connect.setNextFocusUpId(password.getId());
        cancel.setNextFocusRightId(connect.getId());
        connect.setNextFocusLeftId(cancel.getId());
        sheet.show(address);
    }

    private void linkDown(View upper, View lower) {
        if (upper.getId() == View.NO_ID) upper.setId(ViewCompat.generateViewId());
        if (lower.getId() == View.NO_ID) lower.setId(ViewCompat.generateViewId());
        upper.setNextFocusDownId(lower.getId());
        lower.setNextFocusUpId(upper.getId());
    }

    private void restoreFocus(View origin) {
        if (origin.isShown()) origin.requestFocusFromTouch();
    }

    private void showStatus(TextView status, OperationFeedback.Kind kind, String text) {
        status.setText(text);
        OperationFeedback.status(status, kind);
        status.setVisibility(View.VISIBLE);
    }

    String summary() {
        String status = wall.status();
        if (!status.isEmpty() && !"connected".equals(status) && !"synced".equals(status)) return message(status);
        if (!wall.connected()) return tr("选择来源", "Choose a source");
        if (wall.ids().isEmpty()) return tr("选择媒体库", "Choose a library");
        return wall.ids().size() + tr(" 张海报", " posters");
    }

    private void report() {
        String status = wall.status();
        if (!"synced".equals(status)) ui.error(message(status));
    }

    private String message(String status) {
        switch (status) {
            case "working": return tr("正在连接或同步", "Connecting or syncing");
            case "smb-auth": return tr("无法读取共享文件夹，请检查账号和权限", "Cannot read the share. Check the account and permissions.");
            case "smb-folder": return tr("请检查共享地址，例如 smb://NAS/Movies", "Check the share address, for example smb://NAS/Movies.");
            case "smb-empty": return tr("共享目录里没有可用海报，请检查 poster.jpg 等图片", "No posters found. Check for files such as poster.jpg.");
            case "smb-failed": return tr("读取共享失败，请检查网络与 SMB 服务", "Cannot read the share. Check the network and SMB service.");
            case "unauthorized": return tr("认证失败，请检查账号和密码", "Authentication failed. Check your username and password.");
            case "plex-auth": return tr("Plex 授权失败，请检查访问令牌", "Check your Plex access token.");
            case "empty-libraries": return tr("该账号暂无可用媒体库", "This account has no available libraries");
            case "empty-movies": return tr("此媒体库暂无电影", "This library has no movies");
            case "empty-posters": return tr("此媒体库暂无可用海报", "This library has no available posters");
            default: return tr("连接或同步失败，请检查地址和网络", "Connection or sync failed. Check the address and network.");
        }
    }

    private String tr(String zh, String en) { return ui.tr(zh, en); }
}
