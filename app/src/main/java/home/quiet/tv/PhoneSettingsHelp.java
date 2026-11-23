package home.quiet.tv;

import android.content.Context;
import android.os.Build;
import java.io.*;
import java.util.*;
import org.json.*;

final class PhoneSettingsHelp {
    private final Context context;private final PhoneSettingsTasks tasks;private final String scope;private final Map<String,File> downloads=new LinkedHashMap<>();
    PhoneSettingsHelp(Context context,PhoneSettingsTasks tasks,String scope){this.context=context;this.tasks=tasks;this.scope=scope;}
    JSONObject metadata()throws IOException{
        try{JSONObject help=new JSONObject();for(int i=0;i<HelpToolsSheet.TITLES.length;i++)help.put(HelpToolsSheet.TITLES[i],HelpToolsSheet.CONTENT[i]);AboutDeviceInfo device=new AboutDeviceInfo(context);AboutDeviceInfo.Resources resources=AboutDeviceInfo.resources(context);
            return new JSONObject().put("help",help).put("license",AboutLicenses.read(context)).put("project",AboutDeviceInfo.PROJECT).put("thanks",AboutDeviceInfo.THANKS).put("setupGuide","https://beatsad.github.io/quiet-tv/").put("releaseNotes",releaseNotes())
                .put("deviceInfo",new JSONObject().put("model",device.model).put("brand",device.brand).put("android",device.android).put("cpu",device.chipset).put("abis",device.abis).put("firmware",device.firmware).put("compatibility",device.compatibility).put("memory",resources.memory).put("storage",resources.storage));
        }catch(JSONException e){throw new IOException(e);}
    }
    JSONObject about(boolean runtime)throws IOException{
        try{
            if(!runtime){
                AboutDeviceInfo.Resources info=AboutDeviceInfo.resources(context);
                AboutDeviceInfo.Screen display=AboutDeviceInfo.screen(context);
                return new JSONObject().put("memory",capacity(info.ram)).put("storage",capacity(info.internal))
                        .put("screenLabel",display.label).put("screen",display.value).put("gpu",AboutDeviceInfo.gpu());
            }
            AboutRuntime.Snapshot snapshot=AboutRuntime.read(context);
            JSONArray apps=new JSONArray();
            apps.put(app(snapshot.desktop));
            for(AboutRuntime.App item:snapshot.apps)apps.put(app(item));
            return new JSONObject().put("memory",capacity(snapshot.memory)).put("apps",apps).put("note",snapshot.note);
        }catch(JSONException error){throw new IOException(error);}
    }
    private static JSONObject capacity(AboutDeviceInfo.Capacity value)throws JSONException{
        return value.valid()?new JSONObject().put("total",value.total).put("used",value.used()):null;
    }
    private static JSONObject app(AboutRuntime.App app)throws JSONException{
        return new JSONObject().put("name",app.name).put("bytes",app.bytes);
    }
    private String releaseNotes()throws IOException{
        try(InputStream input=context.getAssets().open("phone-settings/release-notes.txt");ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);return "QuietTV "+BuildConfig.VERSION_NAME+"  构建 "+BuildConfig.VERSION_CODE+"\n\n"+output.toString("UTF-8").trim();}
    }
    JSONObject diagnosticState()throws JSONException{
        return new JSONObject().put("format",DiagnosticSession.SCHEMA).put("bundle",true)
            .put("recording",DiagnosticLog.recording()).put("recordingActive",DiagnosticProbe.active())
            .put("bluetoothPermissionRequired",Build.VERSION.SDK_INT>=31&&!BluetoothDiagnostics.permitted(context))
            .put("inputEvidence",InputSourceObservation.hasEvidence(context)).put("inputRecording",InputSourceObservation.busy());
    }
    JSONObject mark(PhoneSettingsServer.Active active)throws IOException{
        requireRecording();
        PhoneSettingsTasks.Job job=tasks.begin(scope+"/diagnostics/mark","录制 30 秒","mark",active);
        tasks.start(job,work->{work.check();DiagnosticLog.markIncident();work.stage("录制已开始，请在 30 秒内复现问题",100);return new JSONObject().put("marked",true);});
        return tasks.receipt(job);
    }
    private void requireRecording()throws IOException{
        if(!DiagnosticLog.recording())throw new PhoneSettingsServer.Rejected("请先开启诊断日志记录");
    }
    JSONObject export(String kind,PhoneSettingsServer.Active active)throws IOException{
        boolean bluetooth=kind.equals("exportBluetooth"),inputs=kind.equals("exportInputs");
        if(bluetooth)requireRecording();
        if(inputs){
            if(InputSourceObservation.busy())throw new PhoneSettingsServer.Rejected("请在信号源记录结束后下载");
            if(!InputSourceObservation.hasEvidence(context))throw new PhoneSettingsServer.Rejected("暂无信号源配对或测试记录");
        }
        String title=inputs?"信号源诊断包":bluetooth?"蓝牙诊断":"诊断日志";
        PhoneSettingsTasks.Job job=tasks.begin(scope+"/diagnostics/"+kind,title,kind,active);
        tasks.start(job,work->{
            work.check();
            work.stage("正在生成"+title,-1);
            String id=UUID.randomUUID().toString();
            File file=inputs?DiagnosticExports.inputs(context):DiagnosticExports.create(context,"未采集：手机导出没有诊断窗口；首页最近绘制单独列出",bluetooth);
            work.check();
            synchronized(downloads){
                if(downloads.size()==4)downloads.remove(downloads.keySet().iterator().next());
                downloads.put(id,file);
            }
            work.stage(title+"已生成",100);
            return new JSONObject().put("download","api/downloads/diagnostics/"+id).put("bytes",file.length());
        });
        return tasks.receipt(job);
    }
    PhoneSettingsServer.Download download(String id)throws IOException{File file;synchronized(downloads){file=downloads.get(id);}if(file==null||!file.isFile())throw new PhoneSettingsServer.BadInput();return new PhoneSettingsServer.Download(new FileInputStream(file),file.length(),file.getName().startsWith("QuietTV-inputs-")?"QuietTV-inputs.zip":DiagnosticExports.FILE_NAME,DiagnosticExports.MIME);}
}
