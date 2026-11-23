package home.quiet.tv;

import android.content.Context;
import java.io.*;
import java.util.ArrayDeque;

/** Same bounded evidence package for TV and phone. Call only from an export worker. */
final class DiagnosticExports {
    static final String HISTORY_ONLY="诊断日志记录已关闭或在导出时发生变更。此包仅包含已有记录。";
    static final String FILE_NAME="QuietTV-diagnostics.zip",MIME="application/zip";
    private static final ArrayDeque<File> retained=new ArrayDeque<>();
    static File create(Context context,String window,boolean bluetooth)throws Exception{
        int epoch=DiagnosticLog.recordingEpoch();
        String snapshot=HISTORY_ONLY;
        if(DiagnosticLog.recording(epoch)){
            if(bluetooth)BluetoothSystemCapture.prepareExport(context);
            if(DiagnosticLog.recording(epoch)){
                String device=DiagnosticDeviceSnapshot.collect(context,DiagnosticDeviceSnapshot.adb(context),window)
                    +DiagnosticFeatureSnapshot.collect(context)+"\n桌面配置："+new DesktopRoutes(context,false).snapshot();
                if(DiagnosticLog.recording(epoch))snapshot=device;
            }
        }
        return retain(DiagnosticLog.bundle(context,snapshot,epoch));
    }
    static File inputs(Context context)throws Exception{return retain(InputSourceObservation.export(context));}
    private static synchronized File retain(File file){
        // Each result is immutable. Retain only the four most recent exports in this process.
        if(retained.size()==4){File previous=retained.removeFirst();if(!previous.delete()&&previous.exists())DiagnosticLog.info("diagnostic.cache_retained","reason=delete_failed");}
        retained.addLast(file);return file;
    }
    private DiagnosticExports(){}
}
