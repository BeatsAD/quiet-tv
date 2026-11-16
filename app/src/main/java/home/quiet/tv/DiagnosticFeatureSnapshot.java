package home.quiet.tv;

import android.content.Context;
import java.io.*;
import org.json.*;

/** Allowlisted current state only; never serialize preferences, notes, rules or backup configs. */
final class DiagnosticFeatureSnapshot {
    interface Read {String get()throws Exception;}
    static String collect(Context context){
        StringBuilder text=new StringBuilder("\n[2.0功能快照  仅导出时状态]\n");
        section(text,"桌面",()->{DesktopSettingsStore s=new DesktopSettingsStore(context);return "layout="+code(s.layout())+" shape="+code(s.iconShape())+" size="+code(s.iconSize())+" seconds="+s.showSeconds()+" lunar="+s.showLunarDate();});
        section(text,"天气",()->{WeatherStore s=new WeatherStore(context);WeatherData data=s.cached(true);return "enabled="+s.enabled()+" automatic="+s.automatic()+" provider="+code(s.provider())+" cachePresent="+(data!=null)+" cacheAgeMs="+(data==null?-1:Math.max(0,System.currentTimeMillis()-data.fetchedAt));});
        section(text,"儿童模式",()->{ChildModeStore s=new ChildModeStore(context);return "enabled="+s.enabled()+" configured="+s.configured()+" connected="+ChildUsageService.connected()+" faultRecorded="+!s.fault().isEmpty();});
        section(text,"图标库",()->{IconLibraryCatalog catalog=IconLibraryRepository.get(context).catalog();return "catalogRevision="+(catalog==null?-1:catalog.revision)+" coverRevision="+AppCoverStore.get(context).revision();});
        section(text,"备份恢复",()->{
            File file=new File(context.getFilesDir(),"desktop-backups/restore.json");if(!file.isFile())return "journalPresent=false";
            if(file.length()>65536)return "journalPresent=true state=unavailable";
            byte[] data=new androidx.core.util.AtomicFile(file).readFully();if(data.length>65536)return "journalPresent=true state=unavailable";
            JSONObject journal=new JSONObject(new String(data,Charsets.UTF_8));return "journalPresent=true phase="+code(journal.optString("phase"));
        });
        return text.append("范围：不包含用户正文、位置、凭据、备份原配置或素材。历史问题须结合操作事件和故障窗口。\n").toString();
    }
    private static String code(String value){return java.util.Arrays.asList("classic","gallery","rectangle","square","circle","small","medium","large","auto","qweather","uapi","openweather","openmeteo","applying","complete","rolled-back").contains(value)?value:"unknown";}
    private static void section(StringBuilder text,String label,Read read){text.append(label).append(": ");try{text.append(read.get());}catch(Exception e){text.append("state=unavailable type=").append(e.getClass().getSimpleName());}text.append('\n');}
    private DiagnosticFeatureSnapshot(){}
}
