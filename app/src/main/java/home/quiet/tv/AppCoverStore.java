package home.quiet.tv;

import android.content.Context;
import androidx.core.util.AtomicFile;
import android.util.Log;
import java.io.*;
import home.quiet.tv.Charsets;
import java.util.UUID;
import java.util.Arrays;
import org.json.*;

/** One current cover per package, scoped by Android's private per-user storage. */
final class AppCoverStore {
    static final long MAX_UPLOAD=2L*1024*1024;
    private static AppCoverStore instance;
    private final File directory;
    private final AtomicFile indexFile;
    private JSONObject index;
    private boolean unreadable;
    static synchronized AppCoverStore get(Context context){
        if(instance==null)instance=new AppCoverStore(context.getApplicationContext());return instance;
    }
    private AppCoverStore(Context context){this(new File(context.getFilesDir(),"app-covers"));}
    AppCoverStore(File directory){
        this.directory=directory;
        indexFile=new AtomicFile(new File(directory,"index.json"));index=new JSONObject();
        if(indexFile.getBaseFile().exists()||new File(directory,"index.json.bak").exists())try{
            index=new JSONObject(new String(indexFile.readFully(),Charsets.UTF_8));
        }catch(IOException|JSONException e){unreadable=true;}
    }
    private void requireIndex() throws IOException {if(!unreadable)return;try{index=new JSONObject(new String(indexFile.readFully(),Charsets.UTF_8));unreadable=false;}catch(IOException|JSONException e){throw new IOException("无法读取封面记录，已保留原文件，请检查存储后重试",e);}}
    static boolean packageName(String pkg){return pkg!=null&&pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+");}
    private void ensureDirectory() throws IOException {
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法保存封面，请检查电视存储空间 / Cover storage unavailable");
    }
    File incoming() throws IOException {ensureDirectory();return File.createTempFile("incoming-",".tmp",directory);}
    File prepared() throws IOException {ensureDirectory();return new File(directory,"cover-"+UUID.randomUUID()+".png");}
    File candidate(String name){return name!=null&&name.matches("cover-[a-f0-9-]{36}\\.png")?new File(directory,name):null;}
    synchronized void discardCandidate(File file){if(file==null)return;try{requireIndex();}catch(IOException e){return;}java.util.Iterator<String> keys=index.keys();while(keys.hasNext())if(file.equals(image(index.optJSONObject(keys.next()))))return;discard(file);}
    private File image(JSONObject entry){
        String name=entry==null?"":entry.optString("file");
        return name.matches("cover-[a-f0-9-]{36}\\.png")?new File(directory,name):null;
    }
    synchronized File file(String pkg){File f=image(index.optJSONObject(pkg));return f!=null&&f.isFile()?f:null;}
    synchronized boolean custom(String pkg){return file(pkg)!=null;}
    synchronized String key(String pkg){File f=file(pkg);return f==null?"original":f.getName();}
    synchronized boolean library(String pkg){return file(pkg)!=null&&LibraryCover.library(index.optJSONObject(pkg));}
    synchronized boolean contain(String pkg){return file(pkg)!=null&&LibraryCover.contain(index.optJSONObject(pkg));}
    synchronized JSONObject entry(String pkg)throws IOException{requireIndex();try{JSONObject value=index.optJSONObject(pkg);return value==null?null:new JSONObject(value.toString());}catch(JSONException e){throw new IOException("封面记录无法读取",e);}}
    synchronized String artworkForm(String pkg){
        JSONObject entry=index.optJSONObject(pkg);
        return entry==null?"":entry.optString("form");
    }
    synchronized long revision(){return index.optLong("revision",0);}
    synchronized JSONObject backupSnapshot()throws IOException{
        requireIndex();try{JSONObject copy=new JSONObject(index.toString());copy.remove("revision");return copy;}catch(JSONException failure){throw new IOException("封面清单无法读取",failure);}
    }
    synchronized void restoreBackup(JSONObject values)throws IOException{
        try{if(revision()==Long.MAX_VALUE)throw new IOException("封面版本无法更新");JSONObject next=new JSONObject(values.toString());java.util.Iterator<String> keys=next.keys();while(keys.hasNext()){String pkg=keys.next();if(!packageName(pkg))throw new IOException("封面应用无效");LibraryCover.validate(next.getJSONObject(pkg),DesktopBackupRecord.VERSION);}next.put("revision",revision()+1);persist(next);unreadable=false;}catch(JSONException failure){throw new IOException("封面清单无法恢复",failure);}
    }
    private void persist(JSONObject next) throws IOException {
        ensureDirectory();FileOutputStream out=null;
        byte[] bytes=next.toString().getBytes(Charsets.UTF_8);
        try{out=indexFile.startWrite();out.write(bytes);out.getFD().sync();indexFile.finishWrite(out);out=null;if(!Arrays.equals(bytes,indexFile.readFully()))throw new IOException("封面保存未完成，请重试");index=next;DiagnosticLog.info("icons.commit.result","result=SAVED revision="+next.optLong("revision")+" parent="+DiagnosticOperation.current());DesktopBackups.changed();}
        catch(IOException e){if(out!=null)indexFile.failWrite(out);unreadable=true;DiagnosticLog.info("icons.commit.result","result=UNKNOWN reason="+e.getClass().getSimpleName()+" parent="+DiagnosticOperation.current());throw new IOException("封面保存失败，请检查存储空间后重试",e);}
    }
    synchronized void apply(String pkg,File ready) throws IOException {
        requireIndex();if(!packageName(pkg)||ready==null||!directory.getCanonicalFile().equals(ready.getCanonicalFile().getParentFile())
                ||!ready.getName().matches("cover-[a-f0-9-]{36}\\.png")||ready.length()==0||ready.length()>MAX_UPLOAD)
            throw new IOException("无效的应用封面 / Invalid cover");
        File old=image(index.optJSONObject(pkg));
        if(ready.equals(old))return;
        try{JSONObject next=new JSONObject(index.toString());next.put(pkg,new JSONObject().put("file",ready.getName()).put("width",720).put("height",406));
            next.put("revision",revision()+1);persist(next);
        }catch(JSONException e){throw new IOException("Unable to save cover settings",e);}
        if(old!=null&&!old.equals(ready))discard(old);
    }
    synchronized void restore(String pkg) throws IOException {
        requireIndex();if(!index.has(pkg))return;File old=image(index.optJSONObject(pkg));
        try{JSONObject next=new JSONObject(index.toString());next.remove(pkg);next.put("revision",revision()+1);persist(next);}
        catch(JSONException e){throw new IOException("Unable to restore cover",e);}
        discard(old);
    }
    synchronized boolean applyLibrary(String pkg,File ready,JSONObject metadata,String expected)throws IOException{
        if(!LibraryCover.library(metadata))throw new IOException("无效的图库封面");
        return applyArtwork(pkg,ready,metadata,expected);
    }
    synchronized void applyUpload(String pkg,File ready,String shape,String expected)throws IOException{
        try{applyArtwork(pkg,ready,LibraryCover.uploadMetadata(shape),expected);}
        catch(JSONException failure){throw new IOException("上传图标记录无效",failure);}
    }
    private boolean applyArtwork(String pkg,File ready,JSONObject metadata,String expected)throws IOException{
        requireIndex();if(!key(pkg).equals(expected))throw new IOException("当前封面已变化，请查看最新预览后重试");
        try{
            JSONObject entry=new JSONObject(metadata.toString()).put("file",ready==null?"":ready.getName());LibraryCover.validate(entry,DesktopBackupRecord.VERSION);
            if(!LibraryCover.contain(entry)||!packageName(pkg)||ready==null||!directory.getCanonicalFile().equals(ready.getCanonicalFile().getParentFile())||ready.length()==0||ready.length()>MAX_UPLOAD||!LibraryCover.pngSize(ready,entry.getInt("width"),entry.getInt("height")))throw new IOException("无效的应用图标");
            if(LibraryCover.same(index.optJSONObject(pkg),entry))return false;
            if(revision()==Long.MAX_VALUE)throw new IOException("封面版本无法更新");File old=image(index.optJSONObject(pkg));JSONObject next=new JSONObject(index.toString());next.put(pkg,entry);next.put("revision",revision()+1);persist(next);if(old!=null&&!old.equals(ready))discard(old);return true;
        }catch(JSONException e){throw new IOException("图库封面记录无效",e);}
    }
    static final class ArtworkChange {
        final String pkg,expected;final File file;final JSONObject metadata;
        ArtworkChange(String pkg,File file,JSONObject metadata,String expected){this.pkg=pkg;this.file=file;this.metadata=metadata;this.expected=expected;}
    }
    synchronized int applyLibraryBatch(java.util.List<ArtworkChange> changes)throws IOException{
        requireIndex();if(changes.isEmpty())return 0;java.util.Set<String> seen=new java.util.HashSet<>();
        try{JSONObject next=new JSONObject(index.toString());int applied=0;
            for(ArtworkChange change:changes){
                if(!seen.add(change.pkg)||!packageName(change.pkg)||!key(change.pkg).equals(change.expected))throw new IOException("当前图标已变化，请重新匹配");
                File file=change.file;JSONObject entry=new JSONObject(change.metadata.toString()).put("file",file==null?"":file.getName());LibraryCover.validate(entry,DesktopBackupRecord.VERSION);
                if((!LibraryCover.library(entry)&&!LibraryCover.generated(entry))||file==null||!directory.getCanonicalFile().equals(file.getCanonicalFile().getParentFile())||file.length()==0||file.length()>MAX_UPLOAD||!LibraryCover.pngSize(file,entry.getInt("width"),entry.getInt("height")))throw new IOException("批量图标文件无效");
                if(!LibraryCover.same(index.optJSONObject(change.pkg),entry)){next.put(change.pkg,entry);applied++;}
            }
            if(applied==0)return 0;if(revision()==Long.MAX_VALUE)throw new IOException("封面版本无法更新");next.put("revision",revision()+1);persist(next);return applied;
        }catch(JSONException invalid){throw new IOException("批量图标记录无效",invalid);}
    }
    static void discard(File file){if(file!=null&&file.exists()&&!DesktopBackups.retains(file)&&!file.delete())Log.w("QuietTV","Cover file retained");}
}
