package home.quiet.tv;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import androidx.core.content.pm.PackageInfoCompat;
import android.os.Build;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

/** Shared TV and phone settings. Only the management worker reads or writes through ADB. */
final class BootAdSettings {
    static final String GUIDE = "https://beatsad.github.io/quiet-tv/guides/boot-ads.md";
    static final String AI_REQUEST = "请通过我的电脑只读排查这台电视的开机广告，核对机型、固件、广告出现阶段、相关开关和服务状态，给出适用的处理办法。请先阅读 " + GUIDE;
    private static final String RECORD = "bootAdSetting";
    private static BootAdSettings instance;

    static synchronized BootAdSettings get(Context context) {
        if (instance == null) instance = new BootAdSettings(context.getApplicationContext());
        return instance;
    }

    private final Context context;
    private final SetupStore store;
    private final ManagementAdb management;
    private final BootAdRule rule;
    private final boolean invalidRules;
    private volatile State last;
    private volatile boolean pendingReadback;
    private long mutationRevision;

    private BootAdSettings(Context context) {
        this.context = context;
        store = new SetupStore(context);
        management = ManagementAdb.get(context);
        BootAdRule selected = null;
        boolean invalid = false;
        // Load only the bundled ad rules. Computer-managed HOME rules keep their own precedence.
        try (InputStream in = context.getAssets().open("boot-ad-rules.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int size;
            while ((size = in.read(buffer)) != -1) out.write(buffer, 0, size);
            selected = BootAdRule.match(new JSONObject(out.toString("UTF-8")), Build.BRAND, Build.MODEL,
                    Build.FINGERPRINT, Build.DEVICE, Build.DISPLAY, Build.VERSION.INCREMENTAL);
        } catch (IOException | JSONException failure) {
            invalid = true;
            DiagnosticLog.error("boot_ad.rules", failure);
        }
        rule = selected;
        invalidRules = invalid;
    }

    static final class State {
        final String status, revision, detail;
        final boolean needsAuthorization;
        final int generation;
        State(String status, int generation, String revision, String detail, boolean needsAuthorization) {
            this.status = status;
            this.generation = generation;
            this.revision = revision;
            this.detail = detail;
            this.needsAuthorization = needsAuthorization;
        }
        boolean canApply() { return "available".equals(status); }
        boolean canRestore() { return "applied".equals(status); }
        JSONObject json(String impact) throws JSONException {
            return new JSONObject().put("status", status).put("revision", revision)
                    .put("detail", detail).put("impact", impact).put("needsAuthorization", needsAuthorization)
                    .put("guide", GUIDE).put("aiRequest", AI_REQUEST);
        }
    }

    private State unavailable(String status, String detail) {
        return new State(status, management.adb.generation(), "", detail, false);
    }

    State state() {
        if (invalidRules) return unavailable("review", "广告规则无法读取");
        if (rule == null || Build.VERSION.SDK_INT < 21 || android.os.Process.myUid() / 100000 != 0)
            return unavailable("unsupported", "");
        State snapshot = last;
        if (!management.adb.connected()) {
            return new State(pendingReadback ? "review" : "authorize", management.adb.generation(), "",
                    pendingReadback && snapshot != null ? snapshot.detail : "", true);
        }
        return snapshot != null && snapshot.generation == management.adb.generation()
                ? snapshot : unavailable("check", "");
    }

    JSONObject snapshot() throws JSONException {
        return state().json(impact()).put("canCheck", canCheck()).put("upstream", fromUpstream());
    }

    boolean canCheck() {
        return rule != null && Build.VERSION.SDK_INT >= 21 && android.os.Process.myUid() / 100000 == 0
                && management.adb.connected();
    }

    void refresh() {
        if (!canCheck()) return;
        try {
            last = inspect().state;
            pendingReadback = false;
        } catch (UnsupportedPackage mismatch) {
            last = unavailable("unsupported", "");
            pendingReadback = false;
        } catch (Exception failure) {
            last = unavailable("review", "无法读取当前广告设置，请重新检测。");
        }
    }

    private final class Inspection {
        final String value;
        final JSONObject record;
        final State state;
        final long version;

        Inspection(String value, JSONObject record, int generation, long version) throws IOException, JSONException {
            this.value = value;
            this.record = record;
            this.version = version;
            String status = resolvedStatus(rule, version, value, record);
            String detail = "review".equals(status) ? "广告设置或相关应用已变化，请核对原记录。" : "";
            String revision = PhoneSettingsState.revision(new JSONObject().put("generation", generation)
                    .put("rule", rule.id).put("version", version).put("value", value).put("record", record)
                    .put("mutation", mutationRevision));
            state = new State(status, generation, revision, detail, false);
        }
    }

    static String resolvedStatus(BootAdRule rule, long version, String value, JSONObject record) {
        if (record == null) return rule.target.equals(value) ? "configured" : "available";
        String before = record.optString("before");
        if (!rule.id.equals(record.optString("rule")) || version != record.optLong("version", -1)
                || !rule.target.equals(record.optString("target")) || !BootAdRule.validValue(before)
                || before.equals(rule.target)) return "review";
        if (rule.target.equals(value)) return "applied";
        return before.equals(value) ? "available" : "review";
    }

    private Inspection inspect() throws Exception {
        AdbController adb = management.adb;
        adb.validateUser();
        if (adb.user != 0 || rule == null) throw new IOException("当前设备或用户未适配");
        int generation = adb.generation();
        PackageInfo info = systemPackage(rule.pkg);
        AppPackageState pkg = AppPackageState.parse(adb.shell("dumpsys package " + rule.pkg), adb.user);
        if (!pkg.installed || pkg.version != PackageInfoCompat.getLongVersionCode(info)
                || !rule.matchesPackageVersion(pkg.version, info.versionName)) throw new UnsupportedPackage();
        String value = readValue(adb);
        if (adb.generation() != generation) throw new IOException("ADB 连接已变化");
        JSONObject local = store.get();
        JSONObject stored = local.isNull(RECORD) ? null : local.getJSONObject(RECORD);
        Inspection current = new Inspection(value, stored, generation, pkg.version);
        // Once the original value is read back, retire the baseline so a later external
        // change to the target value cannot be attributed to Quiet TV.
        if (stored != null && current.state.canApply()) {
            store.put(RECORD, JSONObject.NULL);
            current = new Inspection(value, null, generation, pkg.version);
        }
        return current;
    }

    private String readValue(AdbController adb) throws Exception {
        if (Build.VERSION.SDK_INT < 23) return BootAdRule.readLegacyValue(adb.shell(rule.legacyQuery()));
        return rule.readValue(adb.shell("settings --user 0 list " + rule.namespace));
    }

    private PackageInfo systemPackage(String pkg) throws UnsupportedPackage {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(pkg, 0);
            if (info.applicationInfo != null && (info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                return info;
        } catch (PackageManager.NameNotFoundException missing) {
            throw new UnsupportedPackage();
        }
        throw new UnsupportedPackage();
    }

    boolean fromUpstream() { return rule != null && rule.fromUpstream(); }

    String impact() { return rule == null ? "" : rule.impact; }

    void change(boolean restore, String revision, PhoneSettingsServer.Active active) throws Exception {
        if (!canCheck()) throw new IOException("请先核对机型和本机 ADB 连接。");
        AppOperations.begin();
        boolean attempted = false, saved = false;
        try {
            Inspection before = inspect();
            last = before.state;
            if (!before.state.revision.equals(revision) || !(restore ? before.state.canRestore() : before.state.canApply()))
                throw new IOException("广告设置状态已变化，请重新检测后确认。");
            AdbController adb = management.adb;
            String target = restore ? before.record.getString("before") : rule.target;
            if (!active.valid()) throw new InterruptedIOException("操作已取消或连接已结束");
            if (!restore) {
                store.put(RECORD, new JSONObject().put("id", UUID.randomUUID().toString())
                        .put("rule", rule.id).put("version", before.version).put("target", rule.target)
                        .put("before", before.value));
                saved = true;
            }
            adb.validateUser();
            if (adb.generation() != before.state.generation || !active.valid())
                throw new InterruptedIOException("操作已取消或连接已结束");
            PackageInfo info = systemPackage(rule.pkg);
            if (before.version != PackageInfoCompat.getLongVersionCode(info)
                    || !rule.matchesPackageVersion(before.version, info.versionName)) throw new UnsupportedPackage();
            if (!before.value.equals(readValue(adb)))
                throw new IOException("广告设置已变化，请重新检测。");
            if (!active.valid()) throw new InterruptedIOException("操作已取消或连接已结束");
            attempted = true;
            mutationRevision++;
            adb.shell(rule.writeCommand(target));
            Inspection after = inspect();
            if (after.state.generation != before.state.generation || after.version != before.version
                    || !target.equals(after.value))
                throw new IOException("广告设置读回未确认，请重新检测。");
            last = after.state;
            pendingReadback = false;
        } catch (UnsupportedPackage mismatch) {
            last = unavailable("unsupported", "");
            pendingReadback = false;
            if (saved && !attempted) store.put(RECORD, JSONObject.NULL);
            if (attempted) throw new PhoneSettingsTasks.Problem("操作结果待核对，相关广告应用版本已变化。", true);
            throw mismatch;
        } catch (Exception failure) {
            if (attempted) {
                last = unavailable("review", "操作结果待核对，请重新检测实际设置。");
                pendingReadback = true;
                throw new PhoneSettingsTasks.Problem(last.detail, true);
            }
            if (saved) store.put(RECORD, JSONObject.NULL);
            if (!(failure instanceof InterruptedIOException) && (last == null || last.revision.equals(revision)))
                last = unavailable("review", "无法确认当前广告设置，请重新检测。");
            throw failure;
        } finally {
            AppOperations.end();
        }
    }

    private static final class UnsupportedPackage extends IOException {
        UnsupportedPackage() { super("相关广告应用版本未适配"); }
    }
}
