package home.quiet.tv;

import androidx.core.util.AtomicFile;
import java.io.*;
import org.json.*;

/** One bounded Java crash record, independent of the normal event queue. */
final class DiagnosticCrashRecord {
    private static final int MAX_BYTES=32*1024;
    private final File file;

    DiagnosticCrashRecord(File file){this.file=file;}

    static JSONObject details(Thread thread,Throwable error,DiagnosticLog.UpdateCheck check)throws JSONException{
        String name=thread.getName();
        String role=name.equals("main")||name.equals("quiet-update")?name:"other";
        JSONObject record=new JSONObject().put("thread",role).put("threadId",thread.getId())
            .put("exception",error.getClass().getName());
        if(check!=null)record.put("updateCheck",check.snapshot());
        JSONArray causes=new JSONArray();
        for(int index=0;error!=null&&index<3;index++,error=error.getCause()){
            StackTraceElement[] stack=error.getStackTrace();
            JSONArray frames=new JSONArray();
            int count=Math.min(stack.length,24);
            for(int i=0;i<count;i++){
                String frame=DiagnosticJournal.clean(stack[i].toString());
                frames.put(frame.length()>128?frame.substring(0,125)+"...":frame);
            }
            causes.put(new JSONObject().put("type",error.getClass().getName())
                .put("frames",frames).put("omittedFrames",stack.length-count));
        }
        return record.put("causes",causes).put("causesTruncated",error!=null);
    }

    synchronized void write(JSONObject record)throws IOException{
        byte[] bytes=record.toString().getBytes(Charsets.UTF_8);
        if(bytes.length>MAX_BYTES)throw new IOException("Crash record exceeds limit");
        AtomicFile target=new AtomicFile(file);
        FileOutputStream output=target.startWrite();
        try{
            output.write(bytes);
            target.finishWrite(output);
        }catch(IOException|RuntimeException failure){
            target.failWrite(output);
            throw failure;
        }
    }

    JSONObject read(){
        // Read only the committed file. AtomicFile.openRead may remove an in-flight .new file.
        try(InputStream input=new FileInputStream(file)){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            byte[] chunk=new byte[1024];int length;
            while((length=input.read(chunk))!=-1){
                if(bytes.size()+length>MAX_BYTES)return null;
                bytes.write(chunk,0,length);
            }
            return new JSONObject(bytes.toString("UTF-8"));
        }catch(IOException|JSONException|RuntimeException unavailable){return null;}
    }
}
