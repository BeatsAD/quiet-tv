package home.quiet.tv;

import androidx.core.util.AtomicFile;
import java.io.*;
import java.util.*;
import org.json.*;

/** Private note content is independent of desktop appearance and shortcut configuration. */
final class MemoStore {
    static final int MAX_TEXT=60;
    static final class Note {
        final String id,text;
        final boolean done;
        Note(String id,String text,boolean done){this.id=id;this.text=text;this.done=done;}
    }
    static final Object LOCK=new Object();private static final java.util.concurrent.atomic.AtomicLong changes=new java.util.concurrent.atomic.AtomicLong();
    static long changeEpoch(){return changes.get();}
    private final File directory,file;
    MemoStore(File directory){this.directory=directory;file=new File(directory,"notes.json");}
    List<Note> list()throws IOException{synchronized(LOCK){return read();}}
    static final class Changed extends IOException{Changed(){super("记事已在其他位置修改，请返回列表核对；本次未提交文字仍保留");}}
    private static void unchanged(List<Note> notes,String id,Note expected)throws IOException{if(expected==null)return;for(Note note:notes)if(note.id.equals(id)){if(note.text.equals(expected.text)&&note.done==expected.done)return;throw new Changed();}throw new Changed();}
    List<Note> save(String id,String text)throws IOException{return save(id,text,null);}
    List<Note> save(String id,String text,Note expected)throws IOException{
        synchronized(LOCK){
            validId(id);text=text==null?"":text.trim();validText(text);ArrayList<Note> notes=read();unchanged(notes,id,expected);boolean found=false;
            for(int i=0;i<notes.size();i++)if(notes.get(i).id.equals(id)){Note old=notes.get(i);notes.set(i,new Note(id,text,old.done));found=true;break;}
            if(!found)notes.add(new Note(id,text,false));write(notes);return notes;
        }
    }
    List<Note> toggle(String id)throws IOException{return toggle(id,null);}
    List<Note> toggle(String id,Note expected)throws IOException{
        synchronized(LOCK){ArrayList<Note> notes=read();unchanged(notes,id,expected);int index=index(notes,id);Note old=notes.get(index);notes.set(index,new Note(id,old.text,!old.done));write(notes);return notes;}
    }
    List<Note> remove(String id)throws IOException{return remove(id,null);}
    List<Note> remove(String id,Note expected)throws IOException{
        synchronized(LOCK){ArrayList<Note> notes=read();unchanged(notes,id,expected);notes.remove(index(notes,id));write(notes);return notes;}
    }
    private int index(List<Note> notes,String id)throws IOException{validId(id);for(int i=0;i<notes.size();i++)if(notes.get(i).id.equals(id))return i;throw new IOException("Note unavailable");}
    private static void validId(String id)throws IOException{try{if(id==null||!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException();}catch(IllegalArgumentException error){throw new IOException("Invalid note id",error);}}
    private static void validText(String text)throws IOException{if(text==null||text.trim().isEmpty()||text.length()>MAX_TEXT)throw new IOException("Invalid note text");}
    private ArrayList<Note> read()throws IOException{
        ArrayList<Note> notes=new ArrayList<>();AtomicFile atomic=new AtomicFile(file);
        if(!file.exists()&&!new File(file.getPath()+".bak").exists()){if(directory.exists()&&!directory.isDirectory())throw new IOException("Note storage unavailable");return notes;}
        try{
            JSONObject root=new JSONObject(new String(atomic.readFully(),Charsets.UTF_8));if(root.getInt("version")!=1)throw new IOException("Unsupported note format");
            JSONArray entries=root.getJSONArray("notes");Set<String> ids=new HashSet<>();
            for(int i=0;i<entries.length();i++){
                JSONObject entry=entries.getJSONObject(i);String id=entry.getString("id"),text=entry.getString("text");validId(id);validText(text);
                if(!ids.add(id)||!(entry.get("done") instanceof Boolean))throw new IOException("Invalid note record");notes.add(new Note(id,text,entry.getBoolean("done")));
            }return notes;
        }catch(JSONException error){throw new IOException("Cannot read notes",error);}
    }
    private void write(List<Note> notes)throws IOException{
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Note storage unavailable");
        byte[] bytes;
        try{JSONArray entries=new JSONArray();for(Note note:notes)entries.put(new JSONObject().put("id",note.id).put("text",note.text).put("done",note.done));bytes=new JSONObject().put("version",1).put("notes",entries).toString().getBytes(Charsets.UTF_8);}
        catch(JSONException error){throw new IOException("Cannot encode notes",error);}
        AtomicFile atomic=new AtomicFile(file);FileOutputStream output=null;
        try{output=atomic.startWrite();output.write(bytes);output.getFD().sync();atomic.finishWrite(output);output=null;if(!Arrays.equals(bytes,atomic.readFully()))throw new IOException("Note save incomplete");changes.incrementAndGet();DiagnosticLog.info("memos.write.result","result=SAVED count="+notes.size());}
        catch(IOException error){DiagnosticLog.info("memos.write.result","result=FAILED reason="+error.getClass().getSimpleName());if(output!=null)atomic.failWrite(output);throw error;}
    }
}
