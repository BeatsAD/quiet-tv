package home.quiet.tv;

import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Only operations implemented in the app can be selected by a bundled rule. */
final class BootAdRule {
    final String id, namespace, key, target, pkg, impact, versionName;
    final long version;

    private BootAdRule(String id, String namespace, String key, String target, String pkg,
                       long version, String versionName, String impact) {
        this.id = id;
        this.namespace = namespace;
        this.key = key;
        this.target = target;
        this.pkg = pkg;
        this.version = version;
        this.versionName = versionName;
        this.impact = impact;
    }

    static BootAdRule match(JSONObject catalog, String brand, String model, String fingerprint,
                            String hardware, String display, String incremental) throws JSONException {
        if (catalog.getInt("schema") != 1) throw new JSONException("Unsupported boot ad rules");
        if (brand.isEmpty() || model.isEmpty() || fingerprint.isEmpty()) return null;
        JSONArray devices = catalog.getJSONArray("devices");
        BootAdRule matched = null, upstream = null;
        for (int i = 0; i < devices.length(); i++) {
            JSONObject device = devices.getJSONObject(i);
            if (!brand.equalsIgnoreCase(device.optString("brand")) || !model.equals(device.optString("model")))
                continue;
            JSONObject rule = device.getJSONObject("bootAd");
            if ("upstream".equals(rule.optString("verification"))) {
                if (!matchesUpstreamFirmware(device, fingerprint, hardware, display, incremental)) continue;
                if (upstream != null) throw new JSONException("Conflicting upstream boot ad rules");
                upstream = parse(rule);
            } else if (fingerprint.equals(device.optString("fingerprint"))) {
                if (matched != null) throw new JSONException("Conflicting boot ad rules");
                matched = parse(rule);
            }
        }
        return matched == null ? upstream : matched;
    }

    private static boolean matchesUpstreamFirmware(JSONObject device, String fingerprint, String hardware,
                                                   String display, String incremental) {
        if (device.has("fingerprint")) return fingerprint.equals(device.optString("fingerprint"));
        if (hardware.isEmpty() || !hardware.equals(device.optString("device"))) return false;
        String firmware = device.optString("firmware");
        if (firmware.equals(display) || firmware.equals(incremental)) return true;
        // Use an upstream-confirmed incremental build only if the display ID is not a Xiaomi version.
        return !display.matches("[0-9]+(?:\\.[0-9]+)+")
                && !incremental.isEmpty() && incremental.equals(device.optString("incremental"));
    }

    private static BootAdRule parse(JSONObject rule) throws JSONException {
        String id = rule.getString("id"), pkg = rule.getString("package"), impact = rule.getString("impact");
        Object code = rule.opt("versionCode");
        boolean upstream = "upstream".equals(rule.optString("verification"));
        String versionName = upstream ? rule.getString("versionName") : "";
        if (!rule.getString("source").startsWith("https://") || rule.getString("evidence").isEmpty()
                || rule.getString("purpose").isEmpty() || impact.isEmpty()) {
            throw new JSONException("Incomplete boot ad rule");
        }
        if (upstream) {
            if (code != null || !versionName.matches("[0-9]+(?:\\.[0-9]+)+")
                    || !rule.getString("source").startsWith("https://github.com/"))
                throw new JSONException("Incomplete upstream boot ad rule");
        } else if (!Boolean.TRUE.equals(rule.opt("verified"))
                || (!(code instanceof Integer) && !(code instanceof Long)) || ((Number) code).longValue() < 0) {
            throw new JSONException("Incomplete boot ad rule");
        }
        long version = upstream ? -1 : ((Number) code).longValue();
        if (!upstream && "hisense-ad-block".equals(id) && "com.hisense.service.ad".equals(pkg))
            return new BootAdRule(id, "global", "com.hisense.service.ad_block", "1", pkg, version, versionName, impact);
        if ("xiaomi-boot-ad-switch".equals(id) && "com.mitv.tvhome".equals(pkg))
            return new BootAdRule(id, "system", "boot_ad_switch", "0", pkg, version, versionName, impact);
        throw new JSONException("Unsupported boot ad operation");
    }

    boolean fromUpstream() { return !versionName.isEmpty(); }

    boolean matchesPackageVersion(long actualCode, String actualName) {
        return fromUpstream() ? versionName.equals(actualName) : version == actualCode;
    }

    static final String ABSENT = "absent";

    static boolean validValue(String value) {
        return ABSENT.equals(value) || "0".equals(value) || "1".equals(value);
    }

    String readValue(String output) throws IOException {
        String value = ABSENT;
        String prefix = key + "=";
        for (String row : output.split("\\r?\\n")) {
            if (row.isEmpty()) continue;
            if (row.indexOf('=') <= 0) throw new IOException("无法核对系统广告设置");
            if (!row.startsWith(prefix)) continue;
            String actual = row.substring(prefix.length());
            if (!ABSENT.equals(value) || !("0".equals(actual) || "1".equals(actual)))
                throw new IOException("广告设置原值无法确认");
            value = actual;
        }
        return value;
    }

    String legacyQuery() {
        return "content query --user 0 --uri content://settings/" + namespace + "/" + key + " --projection value";
    }

    static String readLegacyValue(String output) throws IOException {
        if ("No result found.".equals(output)) return ABSENT;
        if ("Row: 0 value=0".equals(output)) return "0";
        if ("Row: 0 value=1".equals(output)) return "1";
        throw new IOException("广告设置原值无法确认");
    }

    String writeCommand(String value) {
        if (!validValue(value)) throw new IllegalArgumentException("Invalid boot ad value");
        String verb = ABSENT.equals(value) ? "delete" : "put";
        return "settings --user 0 " + verb + " " + namespace + " " + key
                + (ABSENT.equals(value) ? "" : " " + value);
    }
}
