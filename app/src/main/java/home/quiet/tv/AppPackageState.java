package home.quiet.tv;

import java.io.IOException;
import java.util.regex.*;

/** Exact current-user state; an incomplete dump never means an app was removed. */
final class AppPackageState {
    final boolean installed;
    final int enabled;
    final long version;
    private AppPackageState(boolean installed,int enabled,long version){this.installed=installed;this.enabled=enabled;this.version=version;}
    static AppPackageState parse(String dump,int user) throws IOException {
        // dumpsys may append an older system-partition copy after the active package.
        int hidden=dump.indexOf("\nHidden system packages:");if(hidden>=0)dump=dump.substring(0,hidden);
        Matcher rows=Pattern.compile("(?m)^\\s*User "+user+":([^\\r\\n]*\\binstalled=[^\\r\\n]*)$").matcher(dump);
        if(!rows.find())throw new IOException("无法核对当前用户的包状态");String row=rows.group(1);
        if(rows.find())throw new IOException("包状态不唯一");
        Matcher installed=Pattern.compile("\\binstalled=(true|false)\\b").matcher(row),enabled=Pattern.compile("\\benabled=([0-4])\\b").matcher(row);
        if(!installed.find() || !enabled.find() || row.matches(".*\\b(?:hidden|suspended)=true\\b.*"))throw new IOException("包状态不完整、隐藏或挂起");
        Matcher versions=Pattern.compile("(?m)^\\s*versionCode=([0-9]+)\\b").matcher(dump);
        if(!versions.find())throw new IOException("应用版本未确认");long version;
        try{version=Long.parseLong(versions.group(1));}catch(NumberFormatException invalid){throw new IOException("应用版本无效",invalid);}
        if(versions.find())throw new IOException("应用版本不唯一");
        return new AppPackageState(Boolean.parseBoolean(installed.group(1)),Integer.parseInt(enabled.group(1)),version);
    }
}
