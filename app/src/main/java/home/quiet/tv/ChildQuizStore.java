package home.quiet.tv;

import android.content.Context;
import androidx.core.util.AtomicFile;
import java.io.*;
import java.util.*;
import org.json.*;

/** Learning data has its own atomic file and is excluded from desktop appearance backups. */
final class ChildQuizStore {
    interface Change {void apply(ChildQuizState state)throws IOException,JSONException;}
    static final Object LOCK=new Object();
    private static ChildQuizBank bundled;
    private final File directory,file;
    final ChildQuizBank bank;
    static ChildQuizStore get(Context context)throws IOException{synchronized(LOCK){if(bundled==null)try(InputStream input=context.getAssets().open("child-quiz/questions.json");InputStream subjects=context.getAssets().open("child-quiz/subjects.json")){bundled=new ChildQuizBank(input,subjects);}return new ChildQuizStore(new File(context.getFilesDir(),"child-learning"),bundled);}}
    ChildQuizStore(File directory,ChildQuizBank bank){this.directory=directory;this.file=new File(directory,"learning.json");this.bank=bank;}
    ChildQuizState read()throws IOException{synchronized(LOCK){AtomicFile atomic=new AtomicFile(file);if(!file.exists()&&!new File(file.getPath()+".bak").exists()){if(directory.exists()&&!directory.isDirectory())throw new IOException("学习记录目录不可用");return new ChildQuizState(bank,ChildModeStore.today());}try{return new ChildQuizState(bank,new JSONObject(new String(atomic.readFully(),Charsets.UTF_8)));}catch(JSONException failure){throw new IOException("学习记录无法读取，原记录已保留",failure);}}}
    ChildQuizState change(Change operation)throws IOException{synchronized(LOCK){ChildQuizState state=read();String before=state.data.toString();try{operation.apply(state);}catch(JSONException failure){throw new IOException("学习记录保存失败，原记录已保留",failure);}if(before.equals(state.data.toString()))return state;if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("学习记录目录不可用");byte[] bytes=state.data.toString().getBytes(Charsets.UTF_8);AtomicFile atomic=new AtomicFile(file);FileOutputStream output=null;try{output=atomic.startWrite();output.write(bytes);output.getFD().sync();atomic.finishWrite(output);output=null;if(!Arrays.equals(bytes,atomic.readFully()))throw new IOException("学习记录保存未完成");if(DiagnosticProbe.allow("child.quiz.commit",2000))DiagnosticLog.info("child.quiz.commit","persisted=true sessionActive="+(state.session()!=null));}catch(IOException failure){if(output!=null)atomic.failWrite(output);throw failure;}return state;}}
}
