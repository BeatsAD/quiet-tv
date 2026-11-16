package home.quiet.tv;

import android.content.Context;
import java.io.*;
import java.util.ArrayDeque;

/** Same bounded evidence package for TV and phone. Call only from an export worker. */
final class DiagnosticExports {
    static final String FILE_NAME="QuietTV-diagnostics.zip",MIME="application/zip";
    private static final ArrayDeque<File> retained=new ArrayDeque<>();
    static synchronized File create(Context context,String adb,String window)throws Exception{
        BluetoothSystemCapture.prepareExport(context);
        String snapshot=DiagnosticDeviceSnapshot.collect(context,adb,window)+DiagnosticFeatureSnapshot.collect(context);
        File file=DiagnosticLog.bundle(context,snapshot);
        // Each result is immutable. Retain only the four most recent exports in this process.
        if(retained.size()==4){File previous=retained.removeFirst();if(!previous.delete()&&previous.exists())DiagnosticLog.info("diagnostic.cache_retained","reason=delete_failed");}
        retained.addLast(file);return file;
    }
    private DiagnosticExports(){}
}
