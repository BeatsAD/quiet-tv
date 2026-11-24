package home.quiet.tv;

import androidx.core.util.AtomicFile;
import java.io.*;
import org.json.*;

/** Reads the last manual incident saved by an older build. */
final class DiagnosticIncident {
    static final int PART_BYTES=512*1024;
    static final long AFTER_MS=30000;
    private final File directory;
    private JSONObject meta=new JSONObject();
    DiagnosticIncident(File directory)throws IOException{
        this.directory=directory;
        AtomicFile file=new AtomicFile(new File(directory,"incident.json"));
        if(file.getBaseFile().isFile())try(InputStream input=file.openRead()){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int n;
            while((n=input.read(buffer))!=-1){if(bytes.size()+n>16384)throw new IOException("Incident metadata exceeds limit");bytes.write(buffer,0,n);}
            meta=new JSONObject(bytes.toString("UTF-8"));
            if(meta.optBoolean("recording"))meta.put("recording",false).put("endReason","process_interrupted").put("processGap",true);
        }catch(JSONException invalid){throw new IOException("Incident metadata unreadable",invalid);}
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
}
