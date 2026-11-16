package home.quiet.tv;

import android.content.Context;
import android.os.*;
import androidx.core.util.AtomicFile;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/** A small signed directory is bundled; only requested artwork is downloaded. */
final class IconLibraryRepository {
    private static final Map<String,IconLibraryRepository> instances=new HashMap<>();
    static synchronized IconLibraryRepository get(Context context){
        Context app=context.getApplicationContext();if(app==null)app=context;String id=app.getFilesDir().getAbsolutePath();
        IconLibraryRepository result=instances.get(id);if(result==null){result=new IconLibraryRepository(app);instances.put(id,result);}return result;
    }
    private final Context context;private final IconLibraryCache images;private final AtomicFile index;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Set<Runnable> listeners=new CopyOnWriteArraySet<>();
    private final Handler main=new Handler(Looper.getMainLooper());private final byte[] key;
    private final IconLibraryTransfer transfer;private IconLibraryCatalog catalog;private byte[] envelope;private boolean refreshing;private long checkedAt;
    private volatile SSLSocketFactory tls;
    private IconLibraryRepository(Context context){
        this.context=context;images=new IconLibraryCache(new File(context.getCacheDir(),"icon-library-v1"));index=new AtomicFile(new File(context.getFilesDir(),"icon-library-index.json"));
        byte[] publicKey=null;
        try{
            publicKey=asset("icon-library/public-key.der",4096);byte[] bundled=asset("icon-library/online.json",IconLibraryCatalog.MAX_BYTES);
            catalog=IconLibraryCatalog.verify(bundled,publicKey,0);envelope=bundled;
            try(InputStream input=index.openRead()){byte[] saved=read(input,IconLibraryCatalog.MAX_BYTES);IconLibraryCatalog cached=IconLibraryCatalog.verify(saved,publicKey,catalog.revision);catalog=cached;envelope=saved;}catch(IOException ignored){}
        }catch(IOException ignored){}
        key=publicKey;
        transfer=new IconLibraryTransfer(url->{HttpsURLConnection connection=(HttpsURLConnection)url.openConnection();if(Build.VERSION.SDK_INT<=25){if(tls==null)tls=UpdateTls.create(context);connection.setSSLSocketFactory(tls);}return connection;});
    }
    private byte[] asset(String path,int limit)throws IOException{try(InputStream input=context.getAssets().open(path)){return read(input,limit);}}
    static byte[] read(InputStream input,int limit)throws IOException{
        ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
        while((count=input.read(buffer))!=-1){if(output.size()+count>limit)throw new IOException("图标资源过大");output.write(buffer,0,count);}return output.toByteArray();
    }
    synchronized IconLibraryCatalog catalog(){return catalog;}
    void observe(Runnable listener){listeners.add(listener);refresh();}
    void forget(Runnable listener){listeners.remove(listener);}
    synchronized void refresh(){
        if(key==null||catalog==null||refreshing||checkedAt!=0&&SystemClock.elapsedRealtime()-checkedAt<15*60*1000)return;
        refreshing=true;checkedAt=SystemClock.elapsedRealtime();
        worker.execute(()->{
            DiagnosticOperation trace=new DiagnosticOperation("icons.refresh","source=online");
            try{trace.stage("FETCH_AND_VERIFY");
                final IconLibraryCatalog previous=catalog();IconLibraryTransfer.Request request=new IconLibraryTransfer.Request();
                byte[] bytes=transfer.fetch("latest.signed.json",IconLibraryCatalog.MAX_BYTES,value->IconLibraryCatalog.verify(value,key,previous.revision),request);
                IconLibraryCatalog next=IconLibraryCatalog.verify(bytes,key,previous.revision);
                synchronized(this){
                    if(Arrays.equals(bytes,envelope)){trace.finish("COMPLETED","unchanged revision="+catalog.revision);return;}
                    if(next.revision==catalog.revision)throw new IOException("同版图标清单内容发生变化");
                    FileOutputStream stream=null;try{stream=index.startWrite();stream.write(bytes);index.finishWrite(stream);}catch(IOException e){if(stream!=null)index.failWrite(stream);throw e;}
                    catalog=next;envelope=bytes;trace.finish("COMPLETED","committed revision="+next.revision);
                }
                main.post(()->{for(Runnable listener:listeners)listener.run();PhoneSettingsSession.get(context).configurationChanged();});
            }catch(IOException|RuntimeException failure){trace.finish("FAILED","cache_retained type="+failure.getClass().getSimpleName());/* Keep the last verified directory. */}
            finally{synchronized(this){refreshing=false;}}
        });
    }
    File artwork(IconLibraryCatalog.Asset asset,IconLibraryTransfer.Request request)throws IOException{
        request.check();File file=images.find(asset);if(file!=null){if(DiagnosticProbe.allow("icons.cache",30000))DiagnosticLog.info("icons.cache","hit=true");return file;}
        byte[] bytes=transfer.fetch(asset.path,asset.bytes,asset::verify,request);request.check();return images.put(asset,bytes);
    }
}
