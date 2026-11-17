package home.quiet.tv;

import android.content.Context;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** A bounded JSON manifest, optionally followed by deduplicated, hash-checked ZIP assets. */
final class DesktopBackupArchive {
    interface Active {boolean valid();}
    interface Progress {void update(long bytes,long total);}
    private final Context context;
    private final DesktopBackupStore store;
    private final DesktopBackupSnapshot snapshots;
    DesktopBackupArchive(Context context,DesktopBackupStore store){this.context=context.getApplicationContext();this.store=store;snapshots=store==null?new DesktopBackupSnapshot(context):store.snapshots;}
    static long estimate(DesktopBackupRecord record,boolean media)throws IOException{
        long total=DesktopBackupRecord.media(record,media).json.toString().getBytes(Charsets.UTF_8).length+(media?1024:0);Set<String> seen=new HashSet<>();try{if(media)for(int i=0;i<record.assets.length();i++){JSONObject asset=record.assets.getJSONObject(i);if(seen.add(asset.getString("sha256")))total=plus(total,plus(asset.getLong("bytes"),512));}}catch(JSONException failure){throw new IOException("备份素材信息无法读取",failure);}return total;
    }
    private static long plus(long a,long b)throws IOException{DesktopBackupRecord.require(b>=0&&a<=Long.MAX_VALUE-b,"备份大小无效");return a+b;}
    private static void active(Active active)throws IOException{DesktopBackupRecord.require(active.valid(),"备份操作已取消");}
    void export(DesktopBackupStore.Entry entry,boolean media,OutputStream output,Active active)throws IOException{
        export(entry,media,output,active,(bytes,total)->{});
    }
    void export(DesktopBackupStore.Entry entry,boolean media,OutputStream output,Active active,Progress progress)throws IOException{
        new DesktopBackupRecord(entry.record.json);active(active);byte[] manifest=DesktopBackupRecord.media(entry.record,media).json.toString().getBytes(Charsets.UTF_8);DesktopBackupRecord.require(manifest.length<=DesktopBackupRecord.MAX_CONFIG_BYTES,"备份配置不能超过 2 MB");
        if(!media){try{for(int i=0;i<entry.record.assets.length();i++){active(active);JSONObject asset=entry.record.assets.getJSONObject(i);DesktopBackupRecord.require(snapshots.hash(source(entry,asset),true).equals(asset.getString("sha256")),"备份素材校验失败");}}catch(JSONException failure){throw new IOException("备份素材信息无法读取",failure);}active(active);output.write(manifest);output.flush();return;}
        // Buffer ZIP headers and payload together; FilterOutputStream's default bulk write writes one byte at a time.
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(output,64*1024){@Override public void close()throws IOException{flush();}})){long total=0,done=0;Set<String> seen=new HashSet<>();for(int i=0;i<entry.record.assets.length();i++){JSONObject asset=entry.record.assets.getJSONObject(i);if(seen.add(asset.getString("sha256")))total=plus(total,asset.getLong("bytes"));}progress.update(0,total);zip.setLevel(0);zip.putNextEntry(new ZipEntry("backup.json"));zip.write(manifest);zip.closeEntry();seen.clear();for(int i=0;i<entry.record.assets.length();i++){JSONObject asset=entry.record.assets.getJSONObject(i);String hash=asset.getString("sha256");if(!seen.add(hash))continue;active(active);zip.putNextEntry(new ZipEntry("assets/"+hash));try(InputStream input=new FileInputStream(source(entry,asset))){copy(input,zip,asset.getLong("bytes"),hash,active,progress,done,total);}done=plus(done,asset.getLong("bytes"));zip.closeEntry();}active(active);zip.finish();}
        catch(JSONException failure){throw new IOException("备份素材信息无法读取",failure);}
    }
    private File source(DesktopBackupStore.Entry entry,JSONObject asset)throws IOException,JSONException{
        if(store!=null)return store.source(entry,asset);File file=DesktopBackupSnapshot.file(context,entry.sources.getString(asset.getString("path")));DesktopBackupRecord.require(file.isFile()&&file.length()==asset.getLong("bytes")&&snapshots.hash(file,true).equals(asset.getString("sha256")),"备份素材缺失或已损坏");return file;
    }
    private void copy(InputStream input,OutputStream output,long expected,String hash,Active active)throws IOException{
        copy(input,output,expected,hash,active,(bytes,total)->{},0,expected);
    }
    private void copy(InputStream input,OutputStream output,long expected,String hash,Active active,Progress progress,long before,long all)throws IOException{
        long total=0;MessageDigest digest=DesktopBackupRecord.digest();byte[] buffer=new byte[64*1024];int read;while((read=input.read(buffer))!=-1){active(active);total=plus(total,read);DesktopBackupRecord.require(total<=expected,"素材大小不匹配");output.write(buffer,0,read);digest.update(buffer,0,read);progress.update(plus(before,total),all);}DesktopBackupRecord.require(total==expected&&DesktopBackupRecord.hex(digest.digest()).equals(hash),"素材校验失败，备份未完成");
    }
    private byte[] manifest(InputStream input,Active active)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int read;while((read=input.read(buffer))!=-1){active(active);DesktopBackupRecord.require(bytes.size()+read<=DesktopBackupRecord.MAX_CONFIG_BYTES,"备份配置不能超过 2 MB");bytes.write(buffer,0,read);}return bytes.toByteArray();
    }
    private DesktopBackupRecord record(byte[] bytes)throws IOException{try{return new DesktopBackupRecord(new JSONObject(new String(bytes,Charsets.UTF_8)));}catch(JSONException failure){throw new IOException("备份文件无法读取",failure);}}
    DesktopBackupStore.Entry read(InputStream input,long length,Active active)throws IOException{
        long maximum=context.getFilesDir().getUsableSpace()-DesktopBackupStore.RESERVE;DesktopBackupRecord.require(maximum>0&&length!=0&&(length<0||length<=maximum),"导入备份所需空间不足");
        try(Bounded source=new Bounded(input,length,maximum);BufferedInputStream buffered=new BufferedInputStream(source)){
            buffered.mark(4);int first=buffered.read(),second=buffered.read();buffered.reset();DesktopBackupRecord original;Map<String,String> supplied=new HashMap<>();
            if(first=='P'&&second=='K'){
                try(ZipInputStream zip=new ZipInputStream(buffered)){ZipEntry header=zip.getNextEntry();DesktopBackupRecord.require(header!=null&&!header.isDirectory()&&header.getName().equals("backup.json"),"备份压缩包缺少配置清单");original=record(manifest(zip,active));zip.closeEntry();Map<String,Long> expected=new LinkedHashMap<>();long needed=0;
                    for(int i=0;i<original.assets.length();i++){JSONObject asset=original.assets.getJSONObject(i);String hash=asset.getString("sha256");long bytes=asset.getLong("bytes");Long old=expected.put(hash,bytes);DesktopBackupRecord.require(old==null||old==bytes,"备份素材大小冲突");if(old==null){File pool=DesktopBackupSnapshot.file(context,"desktop-backups/assets/"+hash);if(!pool.isFile())needed=plus(needed,bytes);}}
                    DesktopBackupRecord.require(context.getFilesDir().getUsableSpace()>plus(needed,DesktopBackupStore.RESERVE),"导入素材所需空间不足");Set<String> received=new HashSet<>();ZipEntry entry;
                    while((entry=zip.getNextEntry())!=null){active(active);String name=entry.getName();if(entry.isDirectory()||!name.matches("assets/[a-f0-9]{64}"))throw new IOException("备份压缩包包含无效路径");String hash=name.substring(7);DesktopBackupRecord.require(expected.containsKey(hash)&&received.add(hash),"备份压缩包含重复或未引用素材");File pool=new File(context.getFilesDir(),"desktop-backups/assets").getCanonicalFile(),target=new File(pool,hash).getCanonicalFile();if(!target.getCanonicalPath().startsWith(pool.getCanonicalPath()+File.separator))throw new IOException("备份素材路径不可用");if(target.exists()){DesktopBackupRecord.require(target.isFile()&&target.length()==expected.get(hash)&&store.snapshots.hash(target,true).equals(hash),"本机备份素材已损坏，请检查存储");copy(zip,new OutputStream(){public void write(int value){}public void write(byte[] bytes,int off,int count){}},expected.get(hash),hash,active);}else{File parent=target.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("备份素材目录不可用");File pending=new File(parent,"incoming-"+UUID.randomUUID());try(FileOutputStream output=new FileOutputStream(pending)){copy(zip,output,expected.get(hash),hash,active);output.getFD().sync();}active(active);DesktopBackupRecord.require(!target.exists()&&pending.renameTo(target),"导入素材未保存");}supplied.put(hash,"desktop-backups/assets/"+hash);zip.closeEntry();}
                    DesktopBackupRecord.require(received.equals(expected.keySet()),"备份压缩包缺少素材");byte[] drain=new byte[8192];while(buffered.read(drain)!=-1)active(active);DesktopBackupRecord.require(length<0||source.total==length,"备份文件传输未完成");
                }
            }else{original=record(manifest(buffered,active));DesktopBackupRecord.require(length<0||source.total==length,"备份文件传输未完成");}
            boolean packed=first=='P'&&second=='K';DesktopBackupRecord.require(packed||!original.json.optBoolean("includesAssets"),"此配置声明包含素材，请选择原始 ZIP 备份");active(active);return prepare(DesktopBackupRecord.media(original,packed),supplied,active);
        }catch(JSONException failure){throw new IOException("备份素材清单无效",failure);}
    }
    private Map<String,List<String>> available()throws IOException,JSONException{
        Map<String,List<String>> result=new HashMap<>();JSONArray current=store.snapshots.collect(store.snapshots.read());for(int i=0;i<current.length();i++){JSONObject asset=current.getJSONObject(i);addSource(result,asset.getString("sha256"),asset.getString("path"));}for(DesktopBackupStore.Entry entry:store.entries())for(int i=0;i<entry.record.assets.length();i++){JSONObject asset=entry.record.assets.getJSONObject(i);addSource(result,asset.getString("sha256"),entry.sources.getString(asset.getString("path")));}return result;
    }
    private void addSource(Map<String,List<String>> values,String hash,String path){List<String> paths=values.get(hash);if(paths==null){paths=new ArrayList<>();values.put(hash,paths);}if(!paths.contains(path))paths.add(path);}
    private DesktopBackupStore.Entry prepare(DesktopBackupRecord original,Map<String,String> supplied,Active active)throws IOException,JSONException{
        JSONObject config=new JSONObject(original.config.toString());Map<String,List<String>> available=available();Map<String,String> rename=new HashMap<>(),sources=new HashMap<>();
        for(int i=0;i<original.assets.length();i++){active(active);JSONObject asset=original.assets.getJSONObject(i);String path=asset.getString("path"),hash=asset.getString("sha256");List<String> candidates=new ArrayList<>();if(supplied.containsKey(hash))candidates.add(supplied.get(hash));candidates.add(path);if(available.containsKey(hash))candidates.addAll(available.get(hash));String source=null;for(String candidate:candidates){File file=DesktopBackupSnapshot.file(context,candidate);if(file.isFile()&&file.length()==asset.getLong("bytes")&&store.snapshots.hash(file,true).equals(hash)){source=candidate;break;}}DesktopBackupRecord.require(source!=null,"本机缺少匹配素材，请导入包含素材的备份");store.snapshots.media(DesktopBackupSnapshot.file(context,source),path,hash);sources.put(path,source);
            File target=DesktopBackupSnapshot.file(context,path);if(target.exists()&&(!target.isFile()||target.length()!=asset.getLong("bytes")||!store.snapshots.hash(target,true).equals(hash))){String old,newName;if(path.startsWith("wallpapers/")){old=path.substring(11,path.endsWith("-poster.jpg")?path.length()-11:path.length()-4);newName="user-"+UUID.randomUUID();}else{old=target.getName();newName=(path.startsWith("app-covers/")?"cover-":"avatar-")+UUID.randomUUID()+".png";}if(!rename.containsKey(old))rename.put(old,newName);}
        }
        JSONObject walls=config.getJSONObject("wallpaper");for(String key:new String[]{"current","lastVideo","staticBeforeVideo","lastFixed"})if(rename.containsKey(walls.optString(key)))walls.put(key,rename.get(walls.getString(key)));JSONObject daily=walls.getJSONObject("daily");if(rename.containsKey(daily.optString("lastLocal")))daily.put("lastLocal",rename.get(daily.getString("lastLocal")));JSONArray custom=walls.getJSONArray("custom");for(int i=0;i<custom.length();i++){JSONObject value=custom.getJSONObject(i);if(rename.containsKey(value.getString("id")))value.put("id",rename.get(value.getString("id")));}JSONObject saver=config.getJSONObject("screensaver");if(rename.containsKey(saver.getString("mediaId")))saver.put("mediaId",rename.get(saver.getString("mediaId")));JSONObject desktop=config.getJSONObject("desktop");if(rename.containsKey(desktop.optString("greeting-avatar-file")))desktop.put("greeting-avatar-file",rename.get(desktop.getString("greeting-avatar-file")));JSONObject covers=config.getJSONObject("covers");for(Iterator<String> keys=covers.keys();keys.hasNext();){JSONObject cover=covers.getJSONObject(keys.next());if(rename.containsKey(cover.getString("file")))cover.put("file",rename.get(cover.getString("file")));}
        TreeMap<String,JSONObject> sorted=new TreeMap<>();JSONObject references=new JSONObject();for(int i=0;i<original.assets.length();i++){JSONObject asset=new JSONObject(original.assets.getJSONObject(i).toString());String old=asset.getString("path"),path=old;for(Map.Entry<String,String> change:rename.entrySet()){String name=change.getKey();if(path.equals("wallpapers/"+name+".jpg")||path.equals("wallpapers/"+name+".mp4")||path.equals("wallpapers/"+name+"-poster.jpg"))path=path.replace(name,change.getValue());else if(path.equals("greeting-avatars/"+name)||path.equals("app-covers/"+name))path=path.substring(0,path.lastIndexOf('/')+1)+change.getValue();}asset.put("path",path);sorted.put(path,asset);references.put(path,sources.get(old));}JSONArray assets=new JSONArray();for(JSONObject asset:sorted.values())assets.put(asset);return new DesktopBackupStore.Entry(DesktopBackupRecord.imported(original,config,assets),references);
    }
    private static final class Bounded extends FilterInputStream {
        final long expected,maximum;long total;
        Bounded(InputStream source,long expected,long maximum){super(source);this.expected=expected;this.maximum=maximum;}
        @Override public int read()throws IOException{byte[] value=new byte[1];return read(value,0,1)<0?-1:value[0]&255;}
        @Override public int read(byte[] bytes,int offset,int count)throws IOException{if(expected>=0&&total==expected)return -1;int read=super.read(bytes,offset,(int)Math.min(count,expected<0?count:expected-total));if(read<0){if(expected>=0&&total!=expected)throw new EOFException("备份文件传输未完成");return -1;}total=plus(total,read);DesktopBackupRecord.require(total<=maximum,"导入备份超过可用空间");return read;}
    }
}
