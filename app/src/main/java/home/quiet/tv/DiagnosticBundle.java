package home.quiet.tv;

import java.io.*;
import java.security.*;
import java.util.zip.*;
import org.json.*;

/** Fixed export files only; no device files, media, databases or arbitrary cache paths. */
final class DiagnosticBundle {
    static final long MAX_BYTES=16L*1024*1024;
    static File create(File text,File structured)throws IOException,JSONException{return create(text,structured,new File(text.getParentFile(),"diagnostic-export.zip"));}
    static File create(File text,File structured,File result)throws IOException,JSONException{
        JSONArray entries=new JSONArray();long total=0;
        try(ZipOutputStream output=new ZipOutputStream(new FileOutputStream(result))){
            File[] files={text,structured};String[] names={"diagnostics.txt","diagnostics.json"};
            for(int i=0;i<files.length;i++){
                File file=files[i];if(!file.isFile()||(total+=file.length())>MAX_BYTES)throw new IOException("Diagnostic bundle exceeds limit");
                MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
                output.putNextEntry(new ZipEntry(names[i]));long bytes=0;
                try(InputStream input=new FileInputStream(file)){byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1){if((bytes+=n)>MAX_BYTES)throw new IOException("Diagnostic file exceeds limit");output.write(buffer,0,n);digest.update(buffer,0,n);}}
                output.closeEntry();entries.put(new JSONObject().put("name",names[i]).put("bytes",bytes).put("sha256",UpdateTransfer.hex(digest.digest())));
            }
            JSONObject manifest=new JSONObject().put("schemaVersion",1).put("files",entries).put("scope","Current snapshot and bounded history are separate. JSON retains at most 1000 recent entries; see omissions and incident coverage.");
            output.putNextEntry(new ZipEntry("manifest.json"));output.write(manifest.toString().getBytes(Charsets.UTF_8));output.closeEntry();
        }
        if(result.length()>MAX_BYTES)throw new IOException("Diagnostic bundle exceeds limit");return result;
    }
    private DiagnosticBundle(){}
}
