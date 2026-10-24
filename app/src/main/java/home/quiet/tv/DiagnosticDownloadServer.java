package home.quiet.tv;

import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** Only a single immutable export is served, under a random path for a finite session. */
final class DiagnosticDownloadServer extends NanoHTTPD {
    static final long SESSION_MS=10L*60*1000;
    static final long MAX_EXPORT_BYTES=2L*DiagnosticJournal.SEGMENT_BYTES+32768;
    static final String FILE_NAME="QuietTV-diagnostics.txt";
    interface Assets {InputStream open(String name) throws IOException;}
    private final File file;
    private final Assets assets;
    private final String host,token;
    private final long started=System.nanoTime(),lifetime;
    private volatile boolean closed;
    DiagnosticDownloadServer(String host,File file,Assets assets){this(host,file,assets,SESSION_MS);}
    DiagnosticDownloadServer(String host,File file,Assets assets,long lifetime){
        super(host,0);this.host=host;this.file=file;this.assets=assets;this.lifetime=lifetime;
        byte[] bytes=new byte[16];new SecureRandom().nextBytes(bytes);StringBuilder value=new StringBuilder();
        for(byte b:bytes)value.append(String.format(Locale.ROOT,"%02x",b&255));token=value.toString();
        setAsyncRunner(new AsyncRunner(){
            private final Set<ClientHandler> clients=new HashSet<>();
            public synchronized void exec(ClientHandler client){if(closed||clients.size()>=2){client.close();return;}clients.add(client);Thread t=new Thread(client,"diagnostic-http");t.setDaemon(true);t.start();}
            public synchronized void closed(ClientHandler client){clients.remove(client);}
            public synchronized void closeAll(){for(ClientHandler client:clients)client.close();clients.clear();}
        });
    }
    void listen() throws IOException {start(10000,true);}
    String address(){return "http://"+host+":"+getListeningPort()+"/"+token+"/";}
    boolean expired(){return closed||(System.nanoTime()-started)/1000000>=lifetime;}
    void invalidate(){closed=true;}
    @Override public void stop(){invalidate();super.stop();}
    // NanoHTTPD 2.3.1 otherwise compresses text and removes its known length.
    @Override protected boolean useGzipWhenAccepted(Response response){return false;}
    @Override public Response serve(IHTTPSession session){
        Response response=route(session);
        if(session.getMethod()==Method.HEAD){
            InputStream data=response.getData();if(data!=null)try{data.close();}catch(IOException ignored){}
            // Keep a non-null empty stream so 2.3.1 preserves the original contentLength.
            response.setData(new ByteArrayInputStream(new byte[0]));
        }
        return response;
    }
    private Response route(IHTTPSession session){
        if(expired())return text(Response.Status.GONE,"链接已失效，请在电视上重新打开诊断日志。");
        String authority=host+":"+getListeningPort(),origin=session.getHeaders().get("origin");
        if(!authority.equals(session.getHeaders().get("host"))||(origin!=null&&!origin.equals("http://"+authority)))return text(Response.Status.FORBIDDEN,"无法访问此链接");
        String prefix="/"+token+"/",uri=session.getUri();
        if(!uri.startsWith(prefix))return text(Response.Status.NOT_FOUND,"链接无效");
        if(session.getMethod()!=Method.GET&&session.getMethod()!=Method.HEAD)return text(Response.Status.METHOD_NOT_ALLOWED,"仅支持下载");
        String route=uri.substring(prefix.length());
        try{
            if("download".equals(route)){
                if(!file.isFile()||file.length()>MAX_EXPORT_BYTES)throw new IOException("Export unavailable");
                Response response=newFixedLengthResponse(Response.Status.OK,"text/plain; charset=utf-8",new FileInputStream(file),file.length());
                response.addHeader("Content-Disposition","attachment; filename=\""+FILE_NAME+"\"");
                if(session.getMethod()==Method.GET)DiagnosticLog.info("diagnostic.download","requested");
                return secure(response);
            }
            if(route.isEmpty()||"style.css".equals(route)){
                byte[] bytes;
                try(InputStream input=assets.open(route.isEmpty()?"index.html":"style.css");ByteArrayOutputStream output=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);bytes=output.toByteArray();
                }
                if(route.isEmpty()){
                    if(!file.isFile()||file.length()>MAX_EXPORT_BYTES)throw new IOException("Export unavailable");
                    String html=new String(bytes,StandardCharsets.UTF_8).replace("__FILE_NAME__",FILE_NAME)
                        .replace("__FILE_SIZE__",fileSize(file.length()))
                        .replace("__GENERATED_AT__",new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.ROOT).format(new Date(file.lastModified())));
                    bytes=html.getBytes(StandardCharsets.UTF_8);
                }
                return fixed(Response.Status.OK,route.isEmpty()?"text/html; charset=utf-8":"text/css; charset=utf-8",bytes);
            }
            return text(Response.Status.NOT_FOUND,"链接无效");
        }catch(IOException e){DiagnosticLog.error("diagnostic.download_failed",e);return text(Response.Status.INTERNAL_ERROR,"无法读取日志，请在电视上重试。");}
    }
    static String fileSize(long bytes){return bytes<1024?bytes+" 字节":String.format(Locale.ROOT,"%.1f %s（%d 字节）",bytes/(bytes<1048576?1024.0:1048576.0),bytes<1048576?"KB":"MB",bytes);}
    private Response text(Response.Status status,String text){return fixed(status,"text/plain; charset=utf-8",text.getBytes(StandardCharsets.UTF_8));}
    private Response fixed(Response.Status status,String mime,byte[] bytes){
        Response response=newFixedLengthResponse(status,mime,new ByteArrayInputStream(bytes),bytes.length);
        return secure(response);
    }
    private Response secure(Response response){
        response.addHeader("Cache-Control","no-store");response.addHeader("Connection","close");response.addHeader("Referrer-Policy","no-referrer");
        response.addHeader("X-Content-Type-Options","nosniff");response.addHeader("Content-Security-Policy","default-src 'none'; style-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");return response;
    }
}
