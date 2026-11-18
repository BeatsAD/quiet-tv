package home.quiet.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;
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
    private final Handler main=new Handler(Looper.getMainLooper());private byte[] key;
    private final Future<IconLibraryCatalog> initialization;
    final LruCache<String,Bitmap> thumbnails=new LruCache<String,Bitmap>(4*1024*1024){@Override protected int sizeOf(String key,Bitmap value){return value.getRowBytes()*value.getHeight();}};
    private final IconLibraryTransfer transfer;private volatile IconLibraryCatalog catalog;private IconLibraryCatalog combined;private byte[] envelope;private boolean refreshing,manualRequested;private long checkedAt;
    private volatile SSLSocketFactory tls;
    private IconLibraryRepository(Context context){
        this.context=context;images=new IconLibraryCache(new File(context.getCacheDir(),"icon-library-v1"));index=new AtomicFile(new File(context.getFilesDir(),"icon-library-index.json"));
        transfer=new IconLibraryTransfer(url->{HttpsURLConnection connection=(HttpsURLConnection)url.openConnection();if(Build.VERSION.SDK_INT<=25){if(tls==null)tls=UpdateTls.create(context);connection.setSSLSocketFactory(tls);}return connection;});
        // Entry/lifecycle callbacks only obtain the repository; disk and signature work stays off UI.
        initialization=worker.submit(this::initialize);
    }
    private IconLibraryCatalog initialize()throws IOException{
        IconLibraryCatalog online=null;byte[] bytes=null;
        try{
            key=asset("icon-library/public-key.der",4096);bytes=asset("icon-library/online.json",IconLibraryCatalog.MAX_BYTES);
            online=IconLibraryCatalog.verify(bytes,key,0);
            try(InputStream input=index.openRead()){byte[] saved=read(input,IconLibraryCatalog.MAX_BYTES);if(!Arrays.equals(saved,bytes)){online=IconLibraryCatalog.verify(saved,key,online.revision);bytes=saved;}}catch(IOException ignored){}
        }catch(IOException ignored){}
        IconLibraryCatalog local=IconLibraryCatalog.readLocal(context);
        synchronized(this){catalog=online;envelope=bytes;combined=IconLibraryCatalog.merge(local,online);}
        return local;
    }
    IconLibraryCatalog readCatalog()throws IOException{
        try{initialization.get();}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new InterruptedIOException("图标库读取已中断");}
        catch(ExecutionException failure){throw new IOException("图标素材无法读取",failure.getCause());}
        synchronized(this){return combined;}
    }
    private byte[] asset(String path,int limit)throws IOException{try(InputStream input=context.getAssets().open(path)){return read(input,limit);}}
    static byte[] read(InputStream input,int limit)throws IOException{
        ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
        while((count=input.read(buffer))!=-1){if(output.size()+count>limit)throw new IOException("图标资源过大");output.write(buffer,0,count);}return output.toByteArray();
    }
    IconLibraryCatalog catalog(){return catalog;}
    void observe(Runnable listener){listeners.add(listener);refresh();}
    void forget(Runnable listener){listeners.remove(listener);}
    interface RefreshResult {void complete(boolean changed,String error);}
    void refresh(){requestRefresh(false,null);}
    void refresh(RefreshResult result){requestRefresh(true,result);}
    private synchronized void requestRefresh(boolean force,RefreshResult result){
        if(force){if(manualRequested){main.post(()->result.complete(false,"图标库正在刷新"));return;}manualRequested=true;}
        else{if(refreshing||manualRequested||checkedAt!=0&&SystemClock.elapsedRealtime()-checkedAt<15*60*1000)return;refreshing=true;checkedAt=SystemClock.elapsedRealtime();}
        worker.execute(()->refreshDirectory(force,result));
    }
    private void refreshDirectory(boolean force,RefreshResult result){
        if(key==null||catalog==null){synchronized(this){refreshing=false;if(force)manualRequested=false;}if(result!=null)main.post(()->result.complete(false,"图标清单不可用，请重试"));return;}
        synchronized(this){refreshing=true;if(force)checkedAt=SystemClock.elapsedRealtime();}
        DiagnosticOperation trace=new DiagnosticOperation("icons.refresh",force?"source=manual":"source=online");boolean changed=false;String error="";
        try{trace.stage("FETCH_AND_VERIFY");
            final IconLibraryCatalog previous=catalog();final IconLibraryCatalog[] verified={null};IconLibraryTransfer.Request request=new IconLibraryTransfer.Request();
            byte[] bytes=transfer.fetch("latest.signed.json",IconLibraryCatalog.MAX_BYTES,value->verified[0]=Arrays.equals(value,envelope)?previous:IconLibraryCatalog.verify(value,key,previous.revision),request,force);
            IconLibraryCatalog next=verified[0];IconLibraryCatalog local=initialization.get();
            synchronized(this){
                if(!Arrays.equals(bytes,envelope)){
                    if(next.revision==catalog.revision)throw new IOException("同版图标清单内容发生变化");
                    FileOutputStream stream=null;try{stream=index.startWrite();stream.write(bytes);index.finishWrite(stream);}catch(IOException e){if(stream!=null)index.failWrite(stream);throw e;}
                    catalog=next;envelope=bytes;combined=IconLibraryCatalog.merge(local,next);thumbnails.evictAll();changed=true;
                }
                trace.finish("COMPLETED",(changed?"committed revision=":"unchanged revision=")+catalog.revision);
            }
        }catch(InterruptedException failure){Thread.currentThread().interrupt();error="图标库刷新已中断";trace.finish("CANCELLED","interrupted");}
        catch(IOException|ExecutionException|RuntimeException failure){error="图标库刷新失败，请稍后重试";trace.finish("FAILED","cache_retained type="+failure.getClass().getSimpleName());}
        finally{synchronized(this){refreshing=false;if(force)manualRequested=false;}}
        boolean updated=changed;String problem=error;main.post(()->{if(updated){for(Runnable listener:listeners)listener.run();PhoneSettingsSession.get(context).configurationChanged();}if(result!=null)result.complete(updated,problem);});
    }
    File artwork(IconLibraryCatalog.Asset asset,IconLibraryTransfer.Request request)throws IOException{
        request.check();File file=images.find(asset);if(file!=null){if(DiagnosticProbe.allow("icons.cache",30000))DiagnosticLog.info("icons.cache","hit=true");return file;}
        byte[] bytes=transfer.fetch(asset.path,asset.bytes,asset::verify,request);request.check();return images.put(asset,bytes);
    }
}
