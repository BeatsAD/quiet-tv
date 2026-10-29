package home.quiet.tv;

import android.content.Context;
import androidx.core.util.AtomicFile;
import java.io.*;
import org.json.*;

/** Publishes an immutable snapshot only after its atomic disk write succeeds. */
final class ScreensaverStore {
    private static ScreensaverStore instance;
    private final AtomicFile file;
    private ScreensaverSettings state;
    private ScreensaverStore(Context context){file=new AtomicFile(new File(context.getFilesDir(),"screensaver.json"));}
    static synchronized ScreensaverStore get(Context context){if(instance==null)instance=new ScreensaverStore(context.getApplicationContext());return instance;}
    synchronized void initialize(WallpaperCatalog catalog)throws IOException{
        if(state==null){
            if(file.getBaseFile().exists()||new File(file.getBaseFile().getPath()+".bak").exists()){
                try{state=ScreensaverSettings.read(new JSONObject(new String(file.readFully(),Charsets.UTF_8)));}
                catch(JSONException e){throw new IOException("Unable to read screensaver settings",e);}
            }else{save(new ScreensaverSettings(catalog.current,0,true,true));}
        }
        reconcile(catalog);
    }
    synchronized ScreensaverSettings snapshot(WallpaperCatalog catalog)throws IOException{initialize(catalog);return state;}
    synchronized ScreensaverSettings current(){return state;}
    synchronized void reconcile(WallpaperCatalog catalog)throws IOException{
        if(state!=null){ScreensaverSettings valid=state.validated(catalog);if(valid!=state)save(valid);}
    }
    synchronized void save(ScreensaverSettings next)throws IOException{
        FileOutputStream stream=null;
        try{
            byte[] expected=next.json().toString().getBytes(Charsets.UTF_8);
            stream=file.startWrite();stream.write(expected);stream.getFD().sync();file.finishWrite(stream);stream=null;
            // AtomicFile logs rename failures rather than throwing them. Verify before publishing.
            if(!java.util.Arrays.equals(expected,file.readFully()))throw new IOException("Screensaver settings were not committed");
            state=next;
        }
        catch(IOException|JSONException e){if(stream!=null)file.failWrite(stream);throw new IOException("Unable to save screensaver settings",e);}
    }
}
