package home.quiet.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import java.io.IOException;
import java.util.Iterator;
import org.json.*;

/** Current-user display aliases only; package identities and original labels remain untouched. */
final class AppNameStore {
    static final String KEY="app-names";
    static final int MAX_LENGTH=20;
    private final SharedPreferences preferences;
    AppNameStore(Context context){this(context.getSharedPreferences("dock",0));}
    AppNameStore(SharedPreferences preferences){this.preferences=preferences;}
    String signature(){return preferences.getString(KEY,"{}");}
    String alias(String pkg){try{return new JSONObject(signature()).optString(pkg,"");}catch(JSONException e){throw new IllegalStateException("应用名称设置无法读取",e);}}
    String display(String pkg,String original){String value=alias(pkg);return value.isEmpty()?original:value;}
    static boolean allowed(Context context,AppRepository.App app){return AppCoverStore.packageName(app.pkg)&&app.installed&&app.metadataKnown&&!AppCoverPolicy.ownReadOnly(context,app.pkg);}
    static String original(Context context,String pkg)throws IOException{
        if(!AppCoverStore.packageName(pkg)||AppCoverPolicy.ownReadOnly(context,pkg))throw new PhoneSettingsServer.Rejected("此应用不支持重命名");
        try{ApplicationInfo info=context.getPackageManager().getApplicationInfo(pkg,0);if(!AppAvailability.installed(context.getPackageManager(),info))throw new PhoneSettingsServer.Rejected("应用已移除，请重新选择");return info.loadLabel(context.getPackageManager()).toString();}
        catch(PackageManager.NameNotFoundException e){throw new PhoneSettingsServer.Rejected("应用已移除，请重新选择");}
    }
    static String normalize(Object value)throws IOException{
        if(!(value instanceof String))throw new PhoneSettingsServer.Rejected("请输入 1–20 个字符的应用名称");
        String raw=(String)value;int start=0,end=raw.length();while(start<end&&space(raw.charAt(start)))start++;while(end>start&&space(raw.charAt(end-1)))end--;String name=raw.substring(start,end);
        if(name.isEmpty()||name.codePointCount(0,name.length())>MAX_LENGTH)throw new PhoneSettingsServer.Rejected("请输入 1–20 个字符的应用名称");
        for(int i=0;i<raw.length();i++){char c=raw.charAt(i);if(Character.isISOControl(c)||c==0x2028||c==0x2029)throw new PhoneSettingsServer.Rejected("应用名称不能包含换行或控制字符");if(Character.isHighSurrogate(c)){if(++i>=raw.length()||!Character.isLowSurrogate(raw.charAt(i)))throw new PhoneSettingsServer.Rejected("应用名称包含无效字符");}else if(Character.isLowSurrogate(c))throw new PhoneSettingsServer.Rejected("应用名称包含无效字符");}
        return name;
    }
    private static boolean space(char c){return c=='\ufeff'||Character.isWhitespace(c)||Character.isSpaceChar(c);}
    static void validate(String raw)throws IOException{
        try{JSONObject values=new JSONObject(raw);for(Iterator<String> keys=values.keys();keys.hasNext();){String pkg=keys.next();Object value=values.get(pkg);if(!AppCoverStore.packageName(pkg)||!normalize(value).equals(value))throw new IOException("备份应用名称无效");}}
        catch(JSONException e){throw new IOException("应用名称设置无效",e);}
    }
    boolean save(String pkg,String expected,String value,String original,PhoneSettingsServer.Active active)throws IOException{synchronized(DesktopSettingsStore.MUTATIONS){
        if(!AppCoverStore.packageName(pkg))throw new PhoneSettingsServer.BadInput();String name=value.equals(original)?original:normalize(value),before=signature();validate(before);
        try{JSONObject next=new JSONObject(before);if(!next.optString(pkg,"").equals(expected))throw new PhoneSettingsServer.Conflict();if(name.equals(original))next.remove(pkg);else next.put(pkg,name);
            if(!active.valid())throw new PhoneSettingsServer.Ended();String saved=next.toString();if(saved.equals(before))return false;
            if(!preferences.edit().putString(KEY,saved).commit()){preferences.edit().putString(KEY,before).commit();throw new IOException("名称保存失败，请检查存储空间后重试");}return true;
        }catch(JSONException e){throw new IOException("应用名称无法保存",e);}
    }}
}
