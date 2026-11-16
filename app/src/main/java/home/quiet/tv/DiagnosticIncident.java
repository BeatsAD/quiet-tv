package home.quiet.tv;

import androidx.core.util.AtomicFile;
import java.io.*;
import java.util.*;
import org.json.*;

/** One explicit user marker, with two fixed 512 KiB files. All calls use the diagnostic writer. */
final class DiagnosticIncident {
    static final int PART_BYTES=512*1024;
    static final long AFTER_MS=30000;
    private final File directory;
    private JSONObject meta=new JSONObject();
    DiagnosticIncident(File directory)throws IOException{
        this.directory=directory;if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Incident storage unavailable");
        AtomicFile file=new AtomicFile(new File(directory,"incident.json"));
        if(file.getBaseFile().isFile())try(InputStream input=file.openRead()){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int n;
            while((n=input.read(buffer))!=-1){if(bytes.size()+n>16384)throw new IOException("Incident metadata exceeds limit");bytes.write(buffer,0,n);}
            meta=new JSONObject(bytes.toString("UTF-8"));
            if(meta.optBoolean("recording")){meta.put("recording",false).put("endReason","process_interrupted").put("processGap",true);persist();}
        }catch(JSONException invalid){throw new IOException("Incident metadata unreadable",invalid);}
    }
    String mark(DiagnosticJournal.Tail before,long wall,long elapsed,JSONObject session)throws IOException,JSONException{
        if(meta.optBoolean("recording")&&elapsed<meta.optLong("deadlineElapsed"))return meta.getString("id");
        String id="incident-"+UUID.randomUUID().toString().substring(0,12);
        // Explicitly marking a new problem replaces the previous bounded diagnostic window only.
        try(OutputStream output=new FileOutputStream(new File(directory,"before.txt"))){output.write(("incident="+id+" phase=before\n").getBytes(Charsets.UTF_8));for(String line:before.lines)output.write((line+"\n").getBytes(Charsets.UTF_8));}
        byte[] afterHeader=("incident="+id+" phase=after\n").getBytes(Charsets.UTF_8);
        try(OutputStream output=new FileOutputStream(new File(directory,"after.txt"))){output.write(afterHeader);}
        meta=new JSONObject().put("id",id).put("source","user").put("markedAtMs",wall).put("markedElapsed",elapsed).put("deadlineElapsed",elapsed+AFTER_MS)
            .put("session",session).put("recording",true).put("processGap",false).put("beforeLines",before.lines.size()).put("beforeOmitted",before.omitted)
            .put("postBytes",afterHeader.length).put("postOmitted",0).put("endReason","recording");persist();return id;
    }
    void append(List<DiagnosticEventBuffer.Entry> entries,List<byte[]> encoded)throws IOException,JSONException{
        if(!meta.optBoolean("recording"))return;
        if(!matches("after.txt"))throw new IOException("Incident part identity mismatch");
        int bytes=meta.optInt("postBytes"),omitted=meta.optInt("postOmitted");
        try(OutputStream output=new BufferedOutputStream(new FileOutputStream(new File(directory,"after.txt"),true))){
            for(int i=0;i<entries.size();i++){
                long elapsed=entries.get(i).elapsed;
                if(elapsed<meta.optLong("markedElapsed")||elapsed>meta.optLong("deadlineElapsed"))continue;
                byte[] line=encoded.get(i);if(bytes+line.length>PART_BYTES){omitted++;continue;}
                output.write(line);bytes+=line.length;
            }
        }
        meta.put("postBytes",bytes).put("postOmitted",omitted);persist();
    }
    void finish(long elapsed)throws IOException,JSONException{
        if(meta.optBoolean("recording")&&elapsed>=meta.optLong("deadlineElapsed")){meta.put("recording",false).put("endReason","time_limit");persist();}
    }
    JSONObject snapshot()throws JSONException{return new JSONObject(meta.toString()).put("beforeMatches",matches("before.txt")).put("afterMatches",matches("after.txt"))
        .put("beforeFileBytes",new File(directory,"before.txt").length()).put("afterFileBytes",new File(directory,"after.txt").length());}
    private boolean matches(String name){
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(new File(directory,name)),Charsets.UTF_8))){String first=reader.readLine();return first!=null&&first.equals("incident="+meta.optString("id")+" phase="+(name.startsWith("before")?"before":"after"));}
        catch(IOException unavailable){return false;}
    }
    void appendTo(OutputStream output)throws IOException{
        if(!meta.has("id"))return;
        output.write(("\n[用户标记的故障窗口]\n"+meta+"\n范围：仅最近一次标记，之前最多512KiB，之后30秒最多512KiB；进程中断不续录。标记时刻不是自动识别的故障起点。\n").getBytes(Charsets.UTF_8));
        for(String name:new String[]{"before.txt","after.txt"}){
            if(!matches(name)){output.write((name+" status=identity_unavailable\n").getBytes(Charsets.UTF_8));continue;}
            File file=new File(directory,name);if(!file.isFile())continue;if(file.length()>PART_BYTES)throw new IOException("Incident part exceeds limit");
            output.write(("\n["+name+"]\n").getBytes(Charsets.UTF_8));
            try(InputStream input=new FileInputStream(file)){byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);}
        }
    }
    private void persist()throws IOException{
        AtomicFile file=new AtomicFile(new File(directory,"incident.json"));FileOutputStream output=file.startWrite();
        try{output.write(meta.toString().getBytes(Charsets.UTF_8));file.finishWrite(output);}catch(IOException|RuntimeException failure){file.failWrite(output);throw failure;}
    }
}
