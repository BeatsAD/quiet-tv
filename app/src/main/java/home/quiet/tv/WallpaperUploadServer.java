package home.quiet.tv;

import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.net.URLDecoder;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** A session-only server: explicit assets, bounded connections, one streamed image at a time. */
final class WallpaperUploadServer extends NanoHTTPD {
    static final long MAX_UPLOAD=20L*1024*1024;
    static final long IDLE_MS=10L*60*1000;
    interface Assets { InputStream open(String name) throws IOException; }
    interface Active { boolean valid(); }
    interface Importer { String accept(InputStream input,long length,String name,Active active) throws IOException; }
    private static final AtomicBoolean IMPORTING=new AtomicBoolean();
    private final Assets assets;
    private final Importer importer;
    private final String host,token;
    private volatile boolean closed;
    private volatile long touched=System.nanoTime();

    WallpaperUploadServer(String host,int port,Assets assets,Importer importer) {
        super(host,port);this.host=host;this.assets=assets;this.importer=importer;
        byte[] random=new byte[16];new SecureRandom().nextBytes(random);
        StringBuilder text=new StringBuilder();for(byte b:random)text.append(String.format(Locale.ROOT,"%02x",b&255));token=text.toString();
        setAsyncRunner(new AsyncRunner(){
            private final Set<ClientHandler> clients=new HashSet<>();
            @Override public synchronized void exec(ClientHandler client) {
                if(closed || clients.size()>=4){client.close();return;}clients.add(client);
                Thread thread=new Thread(client,"wallpaper-http");thread.setDaemon(true);thread.start();
            }
            @Override public synchronized void closed(ClientHandler client){clients.remove(client);}
            @Override public synchronized void closeAll(){for(ClientHandler client:clients)client.close();clients.clear();}
        });
    }
    void listen() throws IOException { start(15000,true); }
    String address(){return "http://"+host+":"+getListeningPort()+"/"+token+"/";}
    boolean expired(){return (System.nanoTime()-touched)/1000000>IDLE_MS;}
    void invalidate(){closed=true;}
    @Override public void stop(){invalidate();super.stop();}
    @Override public Response serve(IHTTPSession session) {
        String prefix="/"+token+"/",uri=session.getUri();
        if(closed || expired())return error(Response.Status.GONE,"上传会话已结束，请在电视重新打开 / Session expired");
        if(!uri.startsWith(prefix))return error(Response.Status.NOT_FOUND,"未找到页面 / Not found");
        String route=uri.substring(prefix.length());
        String origin=session.getHeaders().get("origin");
        if(origin!=null && !origin.equals("http://"+host+":"+getListeningPort()))return error(Response.Status.FORBIDDEN,"无效的上传来源 / Invalid origin");
        touched=System.nanoTime();
        if(session.getMethod()==Method.GET) {
            String name,mime;
            switch(route){case"":name="index.html";mime="text/html; charset=utf-8";break;case"app.js":name="app.js";mime="application/javascript; charset=utf-8";break;case"styles.css":name="styles.css";mime="text/css; charset=utf-8";break;default:return error(Response.Status.NOT_FOUND,"Not found");}
            try {return secure(newChunkedResponse(Response.Status.OK,mime,assets.open(name)));}
            catch(IOException e){return error(Response.Status.INTERNAL_ERROR,"上传页面不可用 / Upload page unavailable");}
        }
        if(session.getMethod()!=Method.POST || !"upload".equals(route))return error(Response.Status.METHOD_NOT_ALLOWED,"Method not allowed");
        long length;
        try { length=Long.parseLong(session.getHeaders().get("content-length")); }
        catch(NumberFormatException e){return error(Response.Status.LENGTH_REQUIRED,"需要文件长度 / Content-Length required");}
        if(length<=0 || length>MAX_UPLOAD)return error(Response.Status.PAYLOAD_TOO_LARGE,"单张图片不能超过 20 MB / Maximum image size: 20 MB");
        if(session.getHeaders().containsKey("transfer-encoding"))return error(Response.Status.BAD_REQUEST,"Unsupported transfer encoding");
        if(!IMPORTING.compareAndSet(false,true))return error(Response.Status.SERVICE_UNAVAILABLE,"正在处理另一张图片，请稍后重试 / Another upload is in progress");
        try {
            String name=session.getHeaders().get("x-wallpaper-name");
            if(name==null || name.length()>1024)return error(Response.Status.BAD_REQUEST,"无效的图片名称 / Invalid image name");
            name=URLDecoder.decode(name,"UTF-8");
            long deadline=System.nanoTime()+60000000000L;
            String result=importer.accept(session.getInputStream(),length,name,()->!closed && System.nanoTime()<deadline);
            return secure(newFixedLengthResponse(Response.Status.CREATED,"application/json; charset=utf-8",result));
        } catch(IOException|IllegalArgumentException e){return error(Response.Status.BAD_REQUEST,e.getMessage()==null?"上传失败，请重试 / Upload failed":e.getMessage());}
        finally {IMPORTING.set(false);touched=System.nanoTime();}
    }
    private Response error(Response.Status status,String message) {
        try{return secure(newFixedLengthResponse(status,"application/json; charset=utf-8",new JSONObject().put("message",message).toString()));}
        catch(JSONException e){throw new IllegalStateException(e);}
    }
    private Response secure(Response response) {
        response.addHeader("Connection","close");response.addHeader("Cache-Control","no-store");response.addHeader("Referrer-Policy","no-referrer");
        response.addHeader("X-Content-Type-Options","nosniff");
        response.addHeader("Content-Security-Policy","default-src 'none'; script-src 'self'; style-src 'self'; img-src blob: data:; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");
        return response;
    }
}
