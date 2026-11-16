package home.quiet.tv;

import android.content.Context;
import java.io.*;
import java.util.*;
import org.json.*;

final class PhoneSettingsHelp {
    private final Context context;private final PhoneSettingsTasks tasks;private final String scope;private final Map<String,File> downloads=new HashMap<>();
    PhoneSettingsHelp(Context context,PhoneSettingsTasks tasks,String scope){this.context=context;this.tasks=tasks;this.scope=scope;}
    JSONObject metadata()throws IOException{
        try{JSONObject help=new JSONObject();for(int i=0;i<HelpToolsSheet.TITLES.length;i++)help.put(HelpToolsSheet.TITLES[i],HelpToolsSheet.CONTENT[i]);AboutDeviceInfo device=new AboutDeviceInfo(context);AboutDeviceInfo.Resources resources=AboutDeviceInfo.resources(context);
            return new JSONObject().put("help",help).put("license",license()).put("project","https://github.com/BeatsAD/quiet-tv").put("setupGuide","https://beatsad.github.io/quiet-tv/").put("releaseNotes",releaseNotes())
                .put("deviceInfo",new JSONObject().put("model",device.model).put("brand",device.brand).put("android",device.android).put("cpu",device.cpu).put("abis",device.abis).put("firmware",device.firmware).put("compatibility",device.compatibility).put("memory",resources.memory).put("storage",resources.storage));
        }catch(JSONException e){throw new IOException(e);}
    }
    private String releaseNotes()throws IOException{
        try(InputStream input=context.getAssets().open("phone-settings/release-notes.txt");ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);return "QuietTV "+BuildConfig.VERSION_NAME+"  构建 "+BuildConfig.VERSION_CODE+"\n\n"+output.toString("UTF-8").trim();}
    }
    @SuppressWarnings("deprecation") private String license()throws IOException{try(InputStream input=context.getAssets().open("about/license.html");ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);String html=output.toString("UTF-8").replaceAll("(?is)<head>.*?</head>","");return android.text.Html.fromHtml(html).toString().trim();}}
    JSONObject mark(PhoneSettingsServer.Active active)throws IOException{
        PhoneSettingsTasks.Job job=tasks.begin(scope+"/diagnostics/mark","标记问题","mark",active);tasks.start(job,work->{work.check();DiagnosticLog.markIncident();work.stage("已标记，继续记录30秒；随后重新生成诊断包",100);return new JSONObject().put("marked",true);});return tasks.receipt(job);
    }
    JSONObject export(PhoneSettingsServer.Active active)throws IOException{
        PhoneSettingsTasks.Job job=tasks.begin(scope+"/diagnostics","导出电视诊断","export",active);
        tasks.start(job,work->{work.check();work.stage("正在生成诊断包",-1);String id=UUID.randomUUID().toString();
            File immutable=DiagnosticExports.create(context,DiagnosticDeviceSnapshot.adb(context),"未采集：手机导出没有诊断窗口；首页最近绘制单独列出");work.check();
            synchronized(downloads){if(downloads.size()==4)downloads.remove(downloads.keySet().iterator().next());downloads.put(id,immutable);}
            work.stage("诊断包已生成，可下载到手机",100);return new JSONObject().put("download","api/downloads/diagnostics/"+id).put("bytes",immutable.length());});return tasks.receipt(job);
    }
    PhoneSettingsServer.Download download(String id)throws IOException{File file;synchronized(downloads){file=downloads.get(id);}if(file==null||!file.isFile())throw new PhoneSettingsServer.BadInput();return new PhoneSettingsServer.Download(new FileInputStream(file),file.length(),DiagnosticExports.FILE_NAME,DiagnosticExports.MIME);}
}
