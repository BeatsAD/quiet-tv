package home.quiet.tv;

import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.net.URLDecoder;
import home.quiet.tv.Charsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** A session-only server: explicit assets, bounded connections, one streamed image at a time. */
final class WallpaperUploadServer extends NanoHTTPD {
    static final long MAX_UPLOAD=60L*1024*1024;
    static final long IDLE_MS=10L*60*1000;
    interface Assets { InputStream open(String name) throws IOException; }
    interface Active { boolean valid(); }
    interface Importer { String accept(InputStream input,long length,String name,Active active) throws IOException;
        default String acceptVideo(InputStream input,long length,String name,Active active) throws IOException {throw new IOException("此入口只支持图片 / Images only");}
        default String acceptVideo(InputStream input,long length,String name,Active active,long upload) throws IOException {return acceptVideo(input,length,name,active);} }
    private static final AtomicBoolean IMPORTING=new AtomicBoolean();
    private final Assets assets;
    private final Importer importer;
    private final long maximum;
    private final boolean videos;
    private final String information;
    private final String host,token,connectionCode;
    private int failedConnections;
    private long blockedUntil;
    private volatile boolean closed,draining;
    private final Set<ClientHandler> clients=new HashSet<>();
    private volatile long touched=System.nanoTime();

    WallpaperUploadServer(String host,int port,Assets assets,Importer importer) {
        this(host,port,assets,importer,MAX_UPLOAD,null);
    }
    WallpaperUploadServer(String host,int port,Assets assets,Importer importer,long maximum,String information) {
        this(host,port,assets,importer,maximum,information,false);
    }
    WallpaperUploadServer(String host,int port,Assets assets,Importer importer,long maximum,String information,boolean videos) {
        super(host,port);this.videos=videos;this.maximum=maximum;this.information=information;this.host=host;this.assets=assets;this.importer=importer;
        SecureRandom randomSource=new SecureRandom();byte[] random=new byte[16];randomSource.nextBytes(random);
        StringBuilder text=new StringBuilder();for(byte b:random)text.append(String.format(Locale.ROOT,"%02x",b&255));token=text.toString();
        connectionCode=String.format(Locale.ROOT,"%06d",randomSource.nextInt(1000000));
        setAsyncRunner(new AsyncRunner(){
            @Override public void exec(ClientHandler client) {
                synchronized(clients){if(closed || clients.size()>=4){client.close();return;}clients.add(client);}
                Thread thread=new Thread(client,"wallpaper-http");thread.setDaemon(true);thread.start();
            }
            @Override public void closed(ClientHandler client){boolean finish;synchronized(clients){clients.remove(client);finish=draining&&clients.isEmpty();}if(finish)WallpaperUploadServer.super.stop();}
            @Override public void closeAll(){synchronized(clients){for(ClientHandler client:new ArrayList<>(clients))client.close();clients.clear();}}
        });
    }
    void listen() throws IOException { start(15000,true); }
    String shortAddress(){return "http://"+host+":"+getListeningPort();}
    String address(){return shortAddress()+"/"+token+"/";}
    String connectionCode(){return connectionCode;}
    boolean expired(){return (System.nanoTime()-touched)/1000000>IDLE_MS;}
    void invalidate(){closed=true;}
    /** For a one-shot recipient: reject new work while allowing the success response to drain. */
    void finishAfterResponse(){boolean finish;synchronized(clients){closed=true;draining=true;finish=clients.isEmpty();}if(finish)super.stop();}
    @Override public void stop(){invalidate();super.stop();}
    @Override public Response serve(IHTTPSession session) {
        String prefix="/"+token+"/",uri=session.getUri();
        if(closed || expired())return error(Response.Status.GONE,"上传会话已结束，请在电视重新打开 / Session expired");
        boolean authorized=uri.startsWith(prefix);
        boolean publicAsset="/".equals(uri)||"/app.js".equals(uri)||"/styles.css".equals(uri);
        boolean connecting="/connect".equals(uri)&&session.getMethod()==Method.POST;
        if(!authorized&&!publicAsset&&!connecting)return error(Response.Status.NOT_FOUND,"未找到页面 / Not found");
        String route=authorized?uri.substring(prefix.length()):uri.substring(1);
        String origin=session.getHeaders().get("origin");
        if(origin!=null && !origin.equals(shortAddress()))return error(Response.Status.FORBIDDEN,"无效的上传来源 / Invalid origin");
        if(connecting)return connect(session);
        if(authorized)touched=System.nanoTime();
        if(session.getMethod()==Method.GET) {
            if(authorized&&"info".equals(route)&&information!=null)return secure(newFixedLengthResponse(Response.Status.OK,"application/json; charset=utf-8",information));
            String name,mime;
            switch(route){case"":name="index.html";mime="text/html; charset=utf-8";break;case"app.js":name="app.js";mime="application/javascript; charset=utf-8";break;case"styles.css":name="styles.css";mime="text/css; charset=utf-8";break;default:return error(Response.Status.NOT_FOUND,"Not found");}
            try {return secure(newChunkedResponse(Response.Status.OK,mime,assets.open(name)));}
            catch(IOException e){return error(Response.Status.INTERNAL_ERROR,"上传页面不可用 / Upload page unavailable");}
        }
        if(!authorized||session.getMethod()!=Method.POST || !"upload".equals(route))return error(Response.Status.METHOD_NOT_ALLOWED,"Method not allowed");
        String kind=session.getHeaders().get("x-wallpaper-kind");
        if(kind==null)kind="image";
        if(!"image".equals(kind)&&!(videos&&"video".equals(kind)))return error(Response.Status.BAD_REQUEST,"此入口不支持这种文件 / Unsupported media type");
        boolean video="video".equals(kind);long upload=video?VideoDiagnostics.nextUpload():0;long limit=video?WallpaperVideo.MAX_UPLOAD:maximum;
        long length;
        try { length=Long.parseLong(session.getHeaders().get("content-length")); }
        catch(NumberFormatException e){return uploadError(video,upload,Response.Status.LENGTH_REQUIRED,"missing_length","需要文件长度 / Content-Length required");}
        if(length<=0 || length>limit)return uploadError(video,upload,Response.Status.PAYLOAD_TOO_LARGE,"file_size_limit","文件不能超过 "+(limit/(1024*1024))+" MB / Maximum file size: "+(limit/(1024*1024))+" MB");
        if(session.getHeaders().containsKey("transfer-encoding"))return uploadError(video,upload,Response.Status.BAD_REQUEST,"transfer_encoding_unsupported","Unsupported transfer encoding");
        if(!IMPORTING.compareAndSet(false,true))return uploadError(video,upload,Response.Status.SERVICE_UNAVAILABLE,"import_busy","正在处理另一个文件，请稍后重试 / Another upload is in progress");
        try {
            String name=session.getHeaders().get("x-wallpaper-name");
            if(name==null || name.length()>1024)return uploadError(video,upload,Response.Status.BAD_REQUEST,"invalid_name_header","无效的文件名称 / Invalid file name");
            name=URLDecoder.decode(name,"UTF-8");
            long deadline=System.nanoTime()+(video?600000000000L:60000000000L);
            Active active=()->!closed && System.nanoTime()<deadline;
            String result=video?importer.acceptVideo(session.getInputStream(),length,name,active,upload):importer.accept(session.getInputStream(),length,name,active);
            return secure(newFixedLengthResponse(Response.Status.CREATED,"application/json; charset=utf-8",result));
        } catch(IOException|IllegalArgumentException e){
            if(video)DiagnosticLog.error("wallpaper.upload.response_failed","upload="+upload+" "+VideoDiagnostics.failureDetails(e),e);
            return error(Response.Status.BAD_REQUEST,e.getMessage()==null?"上传失败，请重试 / Upload failed":e.getMessage());}
        finally {IMPORTING.set(false);touched=System.nanoTime();}
    }
    private Response uploadError(boolean video,long upload,Response.Status status,String reason,String message){
        if(video)DiagnosticLog.info("wallpaper.upload.rejected","upload="+upload+" stage=request reason="+reason+" http="+status.getRequestStatus());
        return error(status,message);
    }
    private synchronized Response connect(IHTTPSession session) {
        long now=System.nanoTime();
        if(now<blockedUntil)return error(Response.Status.FORBIDDEN,"尝试过于频繁，请稍等 30 秒后重试 / Too many attempts. Wait 30 seconds.");
        if(!"6".equals(session.getHeaders().get("content-length"))||session.getHeaders().containsKey("transfer-encoding"))return error(Response.Status.BAD_REQUEST,"请输入 6 位连接码 / Enter the six-digit code");
        byte[] code=new byte[6];
        try {new DataInputStream(session.getInputStream()).readFully(code);}
        catch(IOException e){return error(Response.Status.BAD_REQUEST,"连接码传输未完成，请重试 / Incomplete connection code");}
        if(!connectionCode.equals(new String(code,Charsets.US_ASCII))){
            if(++failedConnections>=5){blockedUntil=now+30000000000L;failedConnections=0;return error(Response.Status.FORBIDDEN,"尝试过于频繁，请稍等 30 秒后重试 / Too many attempts. Wait 30 seconds.");}
            return error(Response.Status.FORBIDDEN,"连接码不正确，请查看电视后重试 / Incorrect code. Check your TV.");
        }
        failedConnections=0;touched=System.nanoTime();
        return secure(newFixedLengthResponse(Response.Status.OK,"application/json; charset=utf-8","{\"path\":\"/"+token+"/\"}"));
    }
    private Response error(Response.Status status,String message) {
        try{return secure(newFixedLengthResponse(status,"application/json; charset=utf-8",new JSONObject().put("message",message).toString()));}
        catch(JSONException e){throw new IllegalStateException(e);}
    }
    private Response secure(Response response) {
        response.addHeader("Connection","close");response.addHeader("Cache-Control","no-store");response.addHeader("Referrer-Policy","no-referrer");
        response.addHeader("X-Content-Type-Options","nosniff");
        response.addHeader("Content-Security-Policy","default-src 'none'; script-src 'self'; style-src 'self'; img-src blob: data:; "+(videos?"media-src blob:; ":"")+"connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");
        return response;
    }
}
