package home.quiet.tv;

import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.security.SecureRandom;
import java.util.*;

/** Only a single immutable export is served, under a random path for a finite session. */
final class DiagnosticDownloadServer extends NanoHTTPD {
    static final long SESSION_MS=10L*60*1000;
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
    @Override public Response serve(IHTTPSession session){
        if(expired())return text(Response.Status.GONE,"链接已失效，请在电视上重新打开诊断日志。");
        String authority=host+":"+getListeningPort(),origin=session.getHeaders().get("origin");
        if(!authority.equals(session.getHeaders().get("host"))||(origin!=null&&!origin.equals("http://"+authority)))return text(Response.Status.FORBIDDEN,"无法访问此链接");
        String prefix="/"+token+"/",uri=session.getUri();
        if(!uri.startsWith(prefix))return text(Response.Status.NOT_FOUND,"链接无效");
        if(session.getMethod()!=Method.GET&&session.getMethod()!=Method.HEAD)return text(Response.Status.METHOD_NOT_ALLOWED,"仅支持下载");
        String route=uri.substring(prefix.length());
        try{
            if("download".equals(route)){
                if(!file.isFile()||file.length()>2L*DiagnosticJournal.SEGMENT_BYTES+8192)throw new IOException("Export unavailable");
                Response response=newFixedLengthResponse(Response.Status.OK,"text/plain; charset=utf-8",new FileInputStream(file),file.length());
                response.addHeader("Content-Disposition","attachment; filename=\"QuietTV-diagnostics.txt\"");
                if(session.getMethod()==Method.GET)DiagnosticLog.info("diagnostic.download","requested");
                return secure(response);
            }
            if(route.isEmpty()||"style.css".equals(route))return secure(newChunkedResponse(Response.Status.OK,route.isEmpty()?"text/html; charset=utf-8":"text/css; charset=utf-8",assets.open(route.isEmpty()?"index.html":"style.css")));
            return text(Response.Status.NOT_FOUND,"链接无效");
        }catch(IOException e){DiagnosticLog.error("diagnostic.download_failed",e);return text(Response.Status.INTERNAL_ERROR,"无法读取日志，请在电视上重试。");}
    }
    private Response text(Response.Status status,String text){return secure(newFixedLengthResponse(status,"text/plain; charset=utf-8",text));}
    private Response secure(Response response){
        response.addHeader("Cache-Control","no-store");response.addHeader("Connection","close");response.addHeader("Referrer-Policy","no-referrer");
        response.addHeader("X-Content-Type-Options","nosniff");response.addHeader("Content-Security-Policy","default-src 'none'; style-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");return response;
    }
}
