package home.quiet.tv;

import android.content.*;
import android.os.*;
import java.io.*;
import org.json.*;

/** Listener-backed snapshot of the production updater; no parallel update feed or installer. */
final class PhoneSettingsUpdates {
    private final Context context;private final Handler main=new Handler(Looper.getMainLooper());private volatile JSONObject snapshot=new JSONObject();private volatile boolean closed;private UpdateRepository repository;
    private final UpdateRepository.Listener listener=this::changed;
    PhoneSettingsUpdates(Context context){this.context=context;main.post(()->{if(closed)return;repository=UpdateRepository.get(context);repository.add(listener);});}
    private void changed(){
        try{UpdateRepository value=repository;JSONObject next=new JSONObject().put("updateLoaded",true).put("updateChecking",value.checking).put("updateDownloading",value.downloading).put("updateVerifying",value.verifying).put("updateReady",value.ready).put("updateReceived",value.received).put("updateTotal",value.total).put("updateCheckError",value.checkError).put("updateDownloadError",value.downloadError).put("updateUnavailable",value.unavailableReason).put("updateInstallState",value.snapshot().install.wire).put("updateInstallError",value.installError).put("updateInstallBusy",value.installBusy);
            if(value.available!=null){UpdateManifest.Artifact item=value.available;next.put("updateAvailable",new JSONObject().put("version",item.version()).put("build",item.code).put("bytes",item.bytes).put("sha256",item.sha256).put("features",new JSONArray(item.release.features)).put("fixes",new JSONArray(item.release.fixes)));}snapshot=next;
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
    JSONObject state()throws IOException{try{return new JSONObject(snapshot.toString());}catch(JSONException e){throw new IOException(e);}}
    JSONObject command(String field,Object value,PhoneSettingsServer.Active active)throws IOException{return new PhoneSettingsActions(context).onMain(()->{
        if(repository==null)throw new PhoneSettingsServer.Rejected("更新模块尚未就绪，请稍后重试");String message;
        if(field.equals("check")&&Boolean.TRUE.equals(value)){repository.check(true);message="检查更新请求已受理";}
        else if(field.equals("cancelCheck")&&Boolean.TRUE.equals(value)){repository.cancelCheck();message="已取消检查";}
        else if(field.equals("cancelDownload")&&Boolean.TRUE.equals(value)){repository.cancelDownload();message="已取消下载";}
        else if(field.equals("reconcile")&&Boolean.TRUE.equals(value)){repository.reconcile();message="已核对实际安装状态";}
        else if((field.equals("download")||field.equals("install"))&&value instanceof String){if(repository.available==null||!value.equals(repository.available.sha256))throw new PhoneSettingsServer.Rejected("更新目标已变化，请重新检查");if(field.equals("download")){repository.download();message="下载请求已受理";}else{if(!repository.ready)throw new PhoneSettingsServer.Rejected("请先完成安装包下载和校验");repository.install();context.startActivity(new Intent(context,AppManagementActivity.class).putExtra("management-page",SettingsPages.HELP).putExtra("phone-update-entry",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));message="已请求在电视继续安装，请完成系统确认并核对版本";}}
        else throw new IllegalArgumentException();changed();return new JSONObject().put("message",message);
    },active);}
    void close(){closed=true;main.post(()->{if(repository!=null)repository.remove(listener);});}
}
