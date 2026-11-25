package home.quiet.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import java.io.IOException;
import org.json.JSONObject;

/** Local setup uses the current system HOME and permissions confirmed in system Settings. */
final class DesktopGuardSetup {
    private final Context context;

    DesktopGuardSetup(Context context) {
        this.context = context;
    }

    private HomeGuardState.Candidate candidate(HomeGuardState guard, HomeController.State home) {
        if (!guard.supported() || !HomeGuardState.unlocked(context) || !home.known || home.isOwn(context)
                || !home.ownUsable || guard.helperActive() || guard.preferences.getBoolean("configured", false)
                || "OFF".equals(guard.preferences.getString("mode", "AUTO"))
                || guard.preferences.getBoolean("proxyPausePending", false)) return null;
        for (HomeGuardState.Candidate candidate : guard.candidates()) {
            if (candidate.component.flattenToString().equals(home.actual)) return candidate;
        }
        return null;
    }

    JSONObject status(HomeGuardState guard, HomeController.State home) throws Exception {
        HomeGuardState.Candidate original = candidate(guard, home);
        if (original == null) return null;
        String recorded = new SetupStore(context).originalHome();
        if (!recorded.isEmpty() && !original.component.equals(ComponentName.unflattenFromString(recorded))) return null;
        boolean notification = HomeGuardRecovery.notificationSupported(context);
        return accessibility(guard).put("original", home.actual).put("label", original.label)
                .put("preferGuard", HomeGuardPolicy.xiaomi(Build.MANUFACTURER, Build.BRAND)
                        && "com.mitv.tvhome".equals(original.component.getPackageName()))
                .put("notificationSupported", notification)
                .put("notificationAllowed", notification && HomeGuardRecovery.notificationAllowed(context))
                .put("notificationSettings", notification && settings("notification") != null);
    }

    private JSONObject accessibility(HomeGuardState guard) throws Exception {
        return new JSONObject().put("accessibilityAllowed", guard.serviceEnabled())
                .put("accessibilityBound", HomeGuardService.connected())
                .put("accessibilitySettings", settings("accessibility") != null);
    }

    JSONObject repairStatus(HomeGuardState guard) throws Exception {
        JSONObject status = accessibility(guard);
        boolean retry = status.optBoolean("accessibilityAllowed")
                && (status.optBoolean("accessibilityBound") || HomeGuardRecovery.writeAllowed(context)
                    || HomeGuardUsageService.eligible(context));
        return retry || status.optBoolean("accessibilitySettings") ? status.put("canRetry", retry) : null;
    }

    Intent settings(String permission) {
        if ("accessibility".equals(permission)) return available(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        if (!"notification".equals(permission)) return null;
        if (Build.VERSION.SDK_INT >= 30) {
            Intent detail = available(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            new ComponentName(context, HomeGuardRevivalListener.class).flattenToString()));
            if (detail != null) return detail;
        }
        return available(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
    }

    static String permissionPrompt(Context context, String permission) {
        int label = "accessibility".equals(permission) ? R.string.home_guard_name : R.string.home_guard_revival_name;
        return "请在电视系统设置中开启「" + context.getString(label) + "」。";
    }

    private Intent available(Intent intent) {
        ComponentName component = intent.resolveActivity(context.getPackageManager());
        return component != null && TvInputController.canOpen(context, component) ? intent : null;
    }

    void enable(String expected) throws Exception {
        HomeGuardState guard = new HomeGuardState(context);
        HomeController.State home = new HomeController(context).inspect();
        JSONObject current = status(guard, home);
        if (current == null || !expected.equals(home.actual))
            throw new IOException("桌面状态已变化，请重新核对原桌面。");
        if (!current.getBoolean("accessibilityAllowed") || !current.getBoolean("accessibilityBound"))
            throw new IOException("请先在电视开启无障碍并确认桌面守护已连接。");

        new SetupStore(context).baseline(home.actual, home.actual);
        guard.select(ComponentName.unflattenFromString(home.actual));
        if (!home.actual.equals(guard.selected())) throw new IOException("原桌面记录未保存。");
        String problem = guard.enable();
        if (!problem.isEmpty() || !guard.observationReady())
            throw new IOException(problem.isEmpty() ? "守护已配置，等待无障碍连接。" : problem);
    }
}
