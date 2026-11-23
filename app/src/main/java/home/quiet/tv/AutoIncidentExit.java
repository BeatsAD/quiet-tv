package home.quiet.tv;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Only match the last QuietTV main process by pid and start time; no raw system traces are exported. */
final class AutoIncidentExit {
    static void capture(Context app,File directory,DiagnosticSession session,String packageName)throws IOException,JSONException{
        if(Build.VERSION.SDK_INT<30)return;
        File anchor=new File(directory,"last-process.json");
        JSONObject previous=null;
        try{previous=AutoIncidentStore.read(anchor);}catch(IOException|JSONException noAnchor){}
        AutoIncidentStore.write(anchor,new JSONObject().put("pid",android.os.Process.myPid()).put("atMs",session.startedAt)
            .put("build",BuildConfig.VERSION_CODE).put("version",BuildConfig.VERSION_NAME)
            .put("sourceSha256",session.sourceSha256()).put("session",session.id)
            .put("sdk",Build.VERSION.SDK_INT).put("model",AutoIncidentStore.bounded(Build.MODEL,100))
            .put("firmware",AutoIncidentStore.bounded(Build.DISPLAY,160)));
        if(previous!=null)Api30.capture(app,previous,session.startedAt,packageName);
    }

    @androidx.annotation.RequiresApi(30)
    private static final class Api30 {
        static void capture(Context app,JSONObject previous,long nextStarted,String packageName)throws JSONException,IOException{
            ActivityManager manager=(ActivityManager)app.getSystemService(Context.ACTIVITY_SERVICE);
            int pid=previous.optInt("pid");
            if(manager==null||pid<=0||previous.optLong("atMs")<=0||previous.optInt("build")<=0)return;
            for(ApplicationExitInfo info:manager.getHistoricalProcessExitReasons(packageName,pid,5)){
                long at=info.getTimestamp();
                if(info.getPid()!=pid||!packageName.equals(info.getProcessName())||at<previous.optLong("atMs")||at>nextStarted)continue;
                int code=info.getReason();
                String reason;
                switch(code){
                    case ApplicationExitInfo.REASON_ANR:reason="anr";break;
                    case ApplicationExitInfo.REASON_CRASH:reason="java_crash";break;
                    case ApplicationExitInfo.REASON_CRASH_NATIVE:reason="native_crash";break;
                    case ApplicationExitInfo.REASON_LOW_MEMORY:reason="low_memory";break;
                    default:return;
                }
                String id=UUID.nameUUIDFromBytes((previous.optString("session")+":"+at+":"+pid).getBytes(Charsets.UTF_8)).toString();
                JSONObject report=new JSONObject().put("schemaVersion",1).put("id",id).put("atMs",at)
                    .put("build",previous.optInt("build")).put("version",previous.optString("version")).put("package",packageName)
                    .put("sourceSha256",previous.optString("sourceSha256")).put("sdk",previous.optInt("sdk"))
                    .put("model",previous.optString("model")).put("firmware",previous.optString("firmware"))
                    .put("session",previous.optString("session")).put("operationId","process-exit")
                    .put("kind","process_exit").put("reason",reason).put("events",new JSONArray())
                    .put("state","sealed").put("count",1)
                    .put("exit",new JSONObject().put("reason",reason).put("androidReason",code).put("pssKiB",info.getPss())
                        .put("rssKiB",info.getRss()).put("match","previous_session_pid_and_time")
                        .put("systemTrace","not_collected"))
                    .put("evidence",new JSONObject().put("systemExit","observed").put("operationTimeline","missing")
                        .put("mainThreadStack","missing").put("physicalDisplay","unsupported").put("externalAppInternals","unsupported"));
                AutoIncidentStore.save(report);
                return;
            }
        }
    }

    private AutoIncidentExit(){}
}
