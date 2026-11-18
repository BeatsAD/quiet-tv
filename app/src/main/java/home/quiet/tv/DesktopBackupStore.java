package home.quiet.tv;

import android.content.Context;
import androidx.core.util.AtomicFile;
import java.io.*;
import java.util.*;
import org.json.*;

/** Local restore points share immutable media. A journal protects the existing independent stores. */
final class DesktopBackupStore {
    static final long RESERVE=32L*1024*1024;
    static final class Entry {
        final DesktopBackupRecord record;
        final JSONObject sources;
        Entry(DesktopBackupRecord record,JSONObject sources){this.record=record;this.sources=sources;}
    }
    private final Context context;
    final DesktopBackupSnapshot snapshots;
    private final File directory;
    private final AtomicFile indexFile,journal;
    private JSONObject index;
    private String indexSignature;
    private final List<Entry> entries=new ArrayList<>();
    DesktopBackupStore(Context context)throws IOException{
        this.context=context.getApplicationContext();snapshots=new DesktopBackupSnapshot(this.context);directory=new File(context.getFilesDir(),"desktop-backups");indexFile=new AtomicFile(new File(directory,"index.json"));journal=new AtomicFile(new File(directory,"restore.json"));recover();load();
    }
    private void ensure()throws IOException{if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("备份目录不可用，已有记录已保留");}
    private void load()throws IOException{
        try{
            File base=indexFile.getBaseFile();byte[] saved=!base.exists()&&!new File(base+".bak").exists()?null:indexFile.readFully();indexSignature=saved==null?null:DesktopBackupRecord.hash(saved);index=saved==null?new JSONObject().put("version",1).put("automatic",true).put("software","").put("records",new JSONArray()):new JSONObject(new String(saved,Charsets.UTF_8));
            DesktopBackupRecord.only(index,DesktopBackupRecord.names("version automatic software records"));DesktopBackupRecord.require(index.getInt("version")==1&&index.get("automatic") instanceof Boolean,"备份记录格式不支持，原记录已保留");index.getString("software");entries.clear();JSONArray records=index.getJSONArray("records");Set<String> ids=new HashSet<>();
            for(int i=0;i<records.length();i++){JSONObject value=records.getJSONObject(i);DesktopBackupRecord.only(value,DesktopBackupRecord.names("record sources"));DesktopBackupRecord record=new DesktopBackupRecord(value.getJSONObject("record"));DesktopBackupRecord.require(ids.add(record.id),"备份记录重复");JSONObject sources=value.getJSONObject("sources");validateSources(record,sources);entries.add(new Entry(record,sources));}
        }catch(JSONException failure){throw new IOException("备份记录无法读取，已有记录未覆盖",failure);}
    }
    private void validateSources(DesktopBackupRecord record,JSONObject sources)throws IOException,JSONException{
        DesktopBackupRecord.require(sources.length()==record.assets.length(),"备份素材引用不完整");for(int i=0;i<record.assets.length();i++){JSONObject asset=record.assets.getJSONObject(i);String source=sources.getString(asset.getString("path"));DesktopBackupRecord.require(DesktopBackupRecord.sourcePath(source)&&(!source.startsWith("desktop-backups/")||source.equals("desktop-backups/assets/"+asset.getString("sha256"))),"备份素材引用无效");}
    }
    private void checkIndex()throws IOException{File base=indexFile.getBaseFile();String current=!base.exists()&&!new File(base+".bak").exists()?null:DesktopBackupRecord.hash(indexFile.readFully());DesktopBackupRecord.require(indexSignature==null?current==null:indexSignature.equals(current),"备份记录已变化或无法读取，请重新读取；原记录未覆盖");}
    synchronized List<Entry> entries()throws IOException{checkIndex();return new ArrayList<>(entries);}
    synchronized Entry find(String id)throws IOException{checkIndex();for(Entry entry:entries)if(entry.record.id.equals(id))return entry;throw new IOException("备份记录已不可用，请重新读取");}
    synchronized void remove(String id)throws IOException{
        find(id);DesktopBackupRecord.require(!recoveryPending(),"桌面恢复尚未完成，请核对恢复结果后再删除备份");
        try{JSONArray records=new JSONArray();for(Entry entry:entries)if(!entry.record.id.equals(id))records.put(new JSONObject().put("record",entry.record.json).put("sources",entry.sources));JSONObject next=new JSONObject(index.toString());next.put("records",records);save(next);}
        catch(JSONException failure){throw new IOException("备份记录未删除，请重新读取",failure);}
        // Only remove the selected restore point. Live, shared and imported media files stay intact.
    }
    synchronized boolean automatic(){return index.optBoolean("automatic",true);}
    synchronized String software(){return index.optString("software");}
    synchronized void software(String version)throws IOException{try{JSONObject next=new JSONObject(index.toString());next.put("software",version);save(next);}catch(JSONException failure){throw new IOException("升级保护标记未保存",failure);}}
    synchronized void automatic(boolean value)throws IOException{try{JSONObject next=new JSONObject(index.toString());next.put("automatic",value);save(next);}catch(JSONException failure){throw new IOException("自动备份设置未保存",failure);}}
    private void save(JSONObject next)throws IOException{checkIndex();ensure();write(indexFile,next);index=next;load();}
    synchronized Entry capture(String kind)throws IOException{
        checkIndex();Entry entry=snapshot(kind);if(kind.equals("auto")&&!entries.isEmpty()&&entries.get(0).record.fingerprint.equals(entry.record.fingerprint))return entries.get(0);
        return add(entry);
    }
    private Entry snapshot(String kind)throws IOException{
        DesktopBackupRecord record=snapshots.capture(kind);
        try{JSONObject sources=new JSONObject();for(int i=0;i<record.assets.length();i++){String path=record.assets.getJSONObject(i).getString("path");sources.put(path,path);}return new Entry(record,sources);}catch(JSONException failure){throw new IOException("备份素材引用无法保存",failure);}
    }
    synchronized Entry add(Entry entry)throws IOException{
        checkIndex();for(Entry old:entries)if(old.record.id.equals(entry.record.id)){DesktopBackupRecord.require(old.record.fingerprint.equals(entry.record.fingerprint),"备份标识冲突，原记录已保留");return old;}
        if(entry.record.kind.equals("imported"))verify(entry);
        try{
            validateSources(entry.record,entry.sources);JSONArray records=new JSONArray();records.put(new JSONObject().put("record",entry.record.json).put("sources",entry.sources));int automatic=rotating(entry.record.kind)?1:0;
            for(Entry old:entries){if(rotating(old.record.kind)&&++automatic>3)continue;records.put(new JSONObject().put("record",old.record.json).put("sources",old.sources));}
            JSONObject next=new JSONObject(index.toString());next.put("records",records);save(next);DiagnosticLog.info("backup.saved","backup="+DiagnosticLog.anonymous("backup",entry.record.id)+" assets="+entry.record.assets.length());return find(entry.record.id);
        }catch(JSONException failure){throw new IOException("备份记录未保存，已有记录仍保留",failure);}
    }
    synchronized String draft(Entry entry)throws IOException{
        try{validateSources(entry.record,entry.sources);File folder=new File(directory,"drafts");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("导入草稿目录不可用");write(new AtomicFile(new File(folder,entry.record.id+".json")),new JSONObject().put("record",entry.record.json).put("sources",entry.sources));return entry.record.id;}catch(JSONException failure){throw new IOException("导入草稿未保存",failure);}
    }
    synchronized Entry draft(String id)throws IOException{
        DesktopBackupRecord.require(FileTransferPolicy.id(id),"导入草稿标识无效");File file=new File(new File(directory,"drafts"),id+".json");try{JSONObject value=new JSONObject(new String(new AtomicFile(file).readFully(),Charsets.UTF_8));DesktopBackupRecord record=new DesktopBackupRecord(value.getJSONObject("record"));JSONObject sources=value.getJSONObject("sources");validateSources(record,sources);DesktopBackupRecord.require(record.id.equals(id),"导入草稿记录不匹配");return new Entry(record,sources);}catch(JSONException failure){throw new IOException("导入草稿无法读取，已保存备份保留",failure);}
    }
    private static boolean rotating(String kind){return kind.equals("auto")||kind.equals("before")||kind.equals("upgrade");}
    synchronized Set<String> retainedPaths()throws IOException{
        checkIndex();Set<String> paths=new HashSet<>();try{for(Entry entry:entries)for(Iterator<String> keys=entry.sources.keys();keys.hasNext();){String source=entry.sources.getString(keys.next());paths.add(DesktopBackupSnapshot.file(context,source).getCanonicalPath());}}catch(JSONException failure){throw new IOException("备份素材引用无法读取",failure);}return paths;
    }
    File source(Entry entry,JSONObject asset)throws IOException,JSONException{
        String path=asset.getString("path");File target=DesktopBackupSnapshot.file(context,path);if(matches(target,asset,false))return target;String source=entry.sources.getString(path);File file=DesktopBackupSnapshot.file(context,source);DesktopBackupRecord.require(matches(file,asset,true),"备份素材缺失或已损坏："+target.getName());return file;
    }
    private boolean matches(File file,JSONObject asset,boolean verify)throws IOException,JSONException{return file.isFile()&&file.length()==asset.getLong("bytes")&&snapshots.hash(file,verify).equals(asset.getString("sha256"));}
    synchronized void verify(Entry entry)throws IOException{
        try{new DesktopBackupRecord(entry.record.json);for(int i=0;i<entry.record.assets.length();i++){JSONObject asset=entry.record.assets.getJSONObject(i);File source=source(entry,asset);DesktopBackupRecord.require(matches(source,asset,true),"备份素材校验失败");snapshots.media(source,asset.getString("path"),asset.getString("sha256"));}}catch(JSONException failure){throw new IOException("备份素材清单无法读取",failure);}
    }
    private void install(Entry entry,boolean rollback)throws IOException{
        try{for(int i=0;i<entry.record.assets.length();i++){JSONObject asset=entry.record.assets.getJSONObject(i);File target=DesktopBackupSnapshot.file(context,asset.getString("path"));
            // A before-restore point references files that this operation never overwrites.
            if(rollback){DesktopBackupRecord.require(target.isFile()&&target.length()==asset.getLong("bytes"),"原配置素材缺失，恢复尚未完成");continue;}
            File source=source(entry,asset);if(target.exists()){DesktopBackupRecord.require(matches(target,asset,true),"素材名称冲突，当前文件已保留");continue;}File parent=target.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("无法恢复素材目录");DesktopBackupRecord.require(parent.getUsableSpace()>asset.getLong("bytes")+RESERVE,"恢复素材所需空间不足");copy(source,target,asset);
        }}catch(JSONException failure){throw new IOException("恢复素材信息无效",failure);}
    }
    private void copy(File source,File target,JSONObject asset)throws IOException,JSONException{
        File pending=new File(target.getParentFile(),target.getName()+".restore-"+UUID.randomUUID());long bytes=0;java.security.MessageDigest digest=DesktopBackupRecord.digest();
        try(InputStream input=new FileInputStream(source);FileOutputStream output=new FileOutputStream(pending)){byte[] buffer=new byte[64*1024];int read;while((read=input.read(buffer))!=-1){bytes+=read;DesktopBackupRecord.require(bytes<=asset.getLong("bytes"),"素材大小已变化");output.write(buffer,0,read);digest.update(buffer,0,read);}output.getFD().sync();}
        DesktopBackupRecord.require(bytes==asset.getLong("bytes")&&DesktopBackupRecord.hex(digest.digest()).equals(asset.getString("sha256")),"恢复素材校验失败");DesktopBackupRecord.require(!target.exists()&&pending.renameTo(target),"恢复素材未保存");
    }
    synchronized void restore(String id)throws IOException{
        DiagnosticOperation trace=new DiagnosticOperation("backup.restore","target="+DiagnosticLog.anonymous("backup",id));
        try{trace.stage("VERIFY");
        Entry target=find(id);verify(target);
        // With automatic backups off, the journal alone holds the rollback snapshot.
        Entry before=automatic()?capture("before"):snapshot("before");trace.stage("PROTECT_BEFORE");try{write(journal,new JSONObject().put("version",1).put("phase","applying").put("before",before.record.id).put("target",target.record.id).put("original",new JSONObject().put("record",before.record.json).put("sources",before.sources)));}
        catch(JSONException failure){throw new IOException("恢复任务无法保存，当前桌面未改变",failure);}
        try{trace.stage("APPLY");install(target,false);snapshots.apply(target.record.config);finishJournal("complete",before.record.id,target.record.id);trace.finish("COMPLETED","journal_confirmed");}
        catch(IOException failure){trace.stage("ROLLBACK");try{install(before,true);snapshots.apply(before.record.config);finishJournal("rolled-back",before.record.id,target.record.id);trace.finish("ROLLED_BACK",failure.getClass().getSimpleName());}catch(IOException rollback){failure.addSuppressed(rollback);throw new IOException("恢复尚未完成，请重试恢复原配置",failure);}throw new IOException("恢复未完成，已保留原桌面配置",failure);}
        }catch(IOException|RuntimeException failure){trace.finish("UNKNOWN",failure.getClass().getSimpleName());throw failure;}
    }
    synchronized boolean recoveryPending()throws IOException{
        File base=journal.getBaseFile();if(!base.exists()&&!new File(base+".bak").exists())return false;try{JSONObject task=new JSONObject(new String(journal.readFully(),Charsets.UTF_8));DesktopBackupRecord.require(task.getInt("version")==1,"恢复任务格式无效");String phase=task.getString("phase");DesktopBackupRecord.require(Arrays.asList("applying","complete","rolled-back").contains(phase),"恢复任务状态无效");return phase.equals("applying");}catch(JSONException failure){throw new IOException("恢复任务无法读取，原记录已保留",failure);}
    }
    synchronized void recover()throws IOException{
        if(!recoveryPending())return;DiagnosticOperation trace=new DiagnosticOperation("backup.recovery","source=persisted_journal");try{JSONObject task=new JSONObject(new String(journal.readFully(),Charsets.UTF_8)),original=task.getJSONObject("original");DesktopBackupRecord record=new DesktopBackupRecord(original.getJSONObject("record"));JSONObject sources=original.getJSONObject("sources");validateSources(record,sources);DesktopBackupRecord.require(record.id.equals(task.getString("before")),"恢复原状态信息不匹配");Entry before=new Entry(record,sources);install(before,true);snapshots.apply(before.record.config);finishJournal("rolled-back",before.record.id,task.getString("target"));trace.finish("ROLLED_BACK","recovered_after_interruption");}catch(IOException|RuntimeException failure){trace.finish("UNKNOWN",failure.getClass().getSimpleName());throw failure;}catch(JSONException failure){trace.finish("UNKNOWN","journal_invalid");throw new IOException("原桌面配置尚未恢复，请重试",failure);}
    }
    private void finishJournal(String phase,String before,String target)throws IOException{try{write(journal,new JSONObject().put("version",1).put("phase",phase).put("before",before).put("target",target));}catch(JSONException failure){throw new IOException("恢复任务完成状态未保存",failure);}}
    static void write(AtomicFile file,JSONObject value)throws IOException{
        FileOutputStream output=null;byte[] bytes=value.toString().getBytes(Charsets.UTF_8);try{output=file.startWrite();output.write(bytes);output.getFD().sync();file.finishWrite(output);output=null;DesktopBackupRecord.require(Arrays.equals(bytes,file.readFully()),"备份写入未完成");}catch(IOException failure){if(output!=null)file.failWrite(output);throw failure;}
    }
}
