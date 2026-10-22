package home.quiet.tv;

/** Redact common identifiers and credentials before diagnostic text reaches disk. */
final class DiagnosticText {
    private static final java.util.regex.Pattern MAC=java.util.regex.Pattern.compile("(?i)\\b(?:[0-9a-f]{2}:){5}[0-9a-f]{2}\\b");
    private static final java.util.regex.Pattern IP=java.util.regex.Pattern.compile("(?<![\\w.])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![\\w.])");
    private static final java.util.regex.Pattern BEARER=java.util.regex.Pattern.compile("(?i)(bearer\\s+)[a-z0-9._~+/=-]+");
    private static final java.util.regex.Pattern QUOTED_SECRET=java.util.regex.Pattern.compile("(?i)(\\b(?:password|passwd|access_token|token|secret|authorization|pairing.?code|android_id|serial(?:no|number)?|device_id|ssid)[\"']?\\s*[=:]\\s*)([\"'])(.*?)\\2");
    private static final java.util.regex.Pattern SECRET=java.util.regex.Pattern.compile("(?i)(\\b(?:password|passwd|access_token|token|secret|authorization|pairing.?code|android_id|serial(?:no|number)?|device_id|ssid)[\"']?\\s*[=:]\\s*[\"']?)[^\\s,;}\"']+");
    static String redact(String value){
        String text=MAC.matcher(value).replaceAll("[MAC]");text=IP.matcher(text).replaceAll("[IP]");text=BEARER.matcher(text).replaceAll("$1[REDACTED]");text=QUOTED_SECRET.matcher(text).replaceAll("$1$2[REDACTED]$2");return SECRET.matcher(text).replaceAll("$1[REDACTED]");
    }
}
