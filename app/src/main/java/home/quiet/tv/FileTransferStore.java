package home.quiet.tv;

import androidx.core.util.AtomicFile;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Only app-owned receipts are writable. Display names never become filesystem paths. */
final class FileTransferStore {
    static final class Entry {
        final String id,name;final long bytes,created;
        Entry(String id,String name,long bytes,long created){this.id=id;this.name=name;this.bytes=bytes;this.created=created;}
        JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("name",name).put("bytes",bytes).put("created",created);}
    }
    private final File directory;
    private final AtomicBoolean receiving=new AtomicBoolean();
    FileTransferStore(File directory)throws IOException{
        this.directory=directory;if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("文件接收目录不可用");
    }
    private File path(String id,String suffix)throws IOException{if(!FileTransferPolicy.id(id))throw new IOException("文件标识无效");return new File(directory,id+suffix);}
    File file(String id)throws IOException{File file=path(id,".data");if(!file.isFile())throw new FileNotFoundException("文件不存在或已移除");return file;}
    Entry entry(String id)throws IOException{
        File metadata=path(id,".json");
        if(metadata.length()>4096)throw new IOException("文件记录无效");
        try{JSONObject json=new JSONObject(new String(new AtomicFile(metadata).readFully(),Charsets.UTF_8));File file=file(id);
            if(!id.equals(json.getString("id"))||file.length()!=json.getLong("bytes"))throw new IOException("文件记录不匹配");
            return new Entry(id,FileTransferPolicy.name(json.getString("name")),file.length(),json.getLong("created"));
        }catch(JSONException e){throw new IOException("文件记录无法读取",e);}
    }
    List<Entry> list(){
        ArrayList<Entry> entries=new ArrayList<>();File[] files=directory.listFiles();
        if(files!=null)for(File file:files)if(file.getName().endsWith(".json"))try{entries.add(entry(file.getName().replaceFirst("\\.json$","")));}catch(IOException ignored){/* Incomplete records are not successful transfers. */}
        Collections.sort(entries,(a,b)->a.created==b.created?0:a.created>b.created?-1:1);return entries;
    }
    long used(){long used=0;File[] files=directory.listFiles();if(files!=null)for(File file:files)used+=file.length();return used;}
    Entry receive(InputStream input,long expected,String name,FileTransferPolicy.Active active,FileTransferPolicy.Progress progress)throws IOException{
        name=FileTransferPolicy.name(name);
        if(!receiving.compareAndSet(false,true))throw new IOException("已有文件正在传输，请稍后再试");
        String id=UUID.randomUUID().toString();File incoming=path(id,".part");
        try{
            if(list().size()>=FileTransferPolicy.MAX_FILES)throw new IOException("接收区已满，请在电视逐个删除不需要的文件");
            long maximum=Math.min(FileTransferPolicy.MAX_FILE,Math.min(FileTransferPolicy.MAX_TOTAL-used()-4096,directory.getUsableSpace()-FileTransferPolicy.RESERVE));
            long bytes;try(FileOutputStream output=new FileOutputStream(incoming)){bytes=FileTransferPolicy.copy(input,output,expected,maximum,active,progress);output.getFD().sync();}
            if(!incoming.renameTo(path(id,".data")))throw new IOException("文件保存失败");
            Entry entry=new Entry(id,name,bytes,System.currentTimeMillis());AtomicFile metadata=new AtomicFile(path(id,".json"));FileOutputStream output=null;
            try{output=metadata.startWrite();output.write(entry.json().toString().getBytes(Charsets.UTF_8));metadata.finishWrite(output);}
            catch(IOException|JSONException failure){if(output!=null)metadata.failWrite(output);throw new IOException("文件记录保存失败",failure);}
            return entry;
        }finally{receiving.set(false);if(incoming.isFile()&&!incoming.delete())System.err.println("QuietTV: cancelled transfer temporary file retained");}
    }
    void remove(String id)throws IOException{
        File file=file(id);if(!file.delete())throw new IOException("文件删除失败");
        File metadata=path(id,".json");if(metadata.isFile()&&!metadata.delete())throw new IOException("文件已删除，记录清理失败");
    }
}
