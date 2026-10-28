package home.quiet.tv;

import android.content.Context;
import android.os.Build;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Aerial #285: explicit shell repair only; never hide an initially visible vendor package. */
final class HisenseGuardRefresh {
    static final String PACKAGE="com.hisense.vidaaassistant";
    static final String FINGERPRINT="Hisense/VIDAA_TV/MSD6A828:5.1.1/LVY48C/01.02.40.01:user/release-keys";
    interface Check { void run() throws Exception; }
    interface Journal { void save(String phase) throws Exception; }
    static boolean matches(String brand,String manufacturer,String model,String device,String fingerprint,int sdk,int user,boolean legacy){
        return !legacy && sdk==22 && user==0 && "Hisense".equalsIgnoreCase(brand) && "Hisense".equalsIgnoreCase(manufacturer)
                && "VIDAA_TV".equals(model) && "MSD6A828".equals(device) && FINGERPRINT.equals(fingerprint);
    }
    static boolean supported(){return matches(Build.BRAND,Build.MANUFACTURER,Build.MODEL,Build.DEVICE,Build.FINGERPRINT,
            Build.VERSION.SDK_INT,android.os.Process.myUid()/100000,BuildConfig.LEGACY_19);}
    static boolean needsShell(Context c){
        if(!supported())return false;
        if(!new HomeGuardState(c,false).serviceEnabled())return true;
        try{return pending(new SetupStore(c).get().optJSONObject("hisenseGuardRefresh"));}
        catch(Exception unreadable){return true;}
    }
    private static boolean pending(org.json.JSONObject previous){
        return previous!=null && "unhide_pending".equals(previous.optString("phase")) && PACKAGE.equals(previous.optString("package"))
                && previous.optBoolean("hiddenBefore") && previous.optInt("user",-1)==0;
    }
    static boolean hidden(String dump) throws IOException {
        Matcher rows=Pattern.compile("(?m)^\\s*User 0:([^\\r\\n]+)").matcher(dump);
        if(!rows.find())throw new IOException("上游刷新包的当前用户状态无法确认，未修改厂商包");
        String row=rows.group(1);
        if(rows.find() || !Pattern.compile("\\binstalled=true\\b").matcher(row).find()
                || !Pattern.compile("\\benabled=[01]\\b").matcher(row).find())throw new IOException("上游刷新包未安装或已禁用，未修改厂商包");
        Matcher state=Pattern.compile("\\bhidden=(true|false)\\b").matcher(row);
        if(!state.find())throw new IOException("上游刷新包的隐藏状态无法确认，未修改厂商包");
        return Boolean.parseBoolean(state.group(1));
    }
}
