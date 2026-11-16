package home.quiet.tv;

import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** One explicit backup transfer. A capability exposes one export or one pending import. */
final class DesktopBackupServer extends NanoHTTPD {
    interface Assets {InputStream open(String name)throws IOException;}
    interface Importer {JSONObject read(InputStream input,long length,String name,DesktopBackupArchive.Active active)throws IOException;}
    interface Listener {void started(String name,long length);void progress(long bytes,long length);void finished(String error);}
    private final String host,token,code,name;
    private final File download;
    private final boolean media;
    private final long maximum;
    private final Assets assets;
    private final Importer importer;
    private final Listener listener;
    private final AtomicBoolean busy=new AtomicBoolean();
    private final Set<ClientHandler> clients=new HashSet<>();
    private volatile boolean closed;
    private volatile boolean imported;
    private volatile long touched=System.nanoTime();
    private int failures;private long blocked;
    DesktopBackupServer(String host,File download,String name,boolean media,long maximum,Assets assets,Importer importer,Listener listener){
        super(host,0);this.host=host;this.download=download;this.name=name;this.media=media;this.maximum=maximum;this.assets=assets;this.importer=importer;this.listener=listener;SecureRandom random=new SecureRandom();byte[] bytes=new byte[16];random.nextBytes(bytes);StringBuilder value=new StringBuilder();for(byte b:bytes)value.append(String.format(Locale.ROOT,"%02x",b&255));token=value.toString();code=String.format(Locale.ROOT,"%06d",random.nextInt(1000000));
        setAsyncRunner(new AsyncRunner(){public void exec(ClientHandler client){synchronized(clients){if(closed||clients.size()>=4){client.close();return;}clients.add(client);}Thread thread=new Thread(client,"backup-transfer-http");thread.setDaemon(true);thread.start();}public void closed(ClientHandler client){synchronized(clients){clients.remove(client);}}public void closeAll(){synchronized(clients){for(ClientHandler client:new ArrayList<>(clients))client.close();clients.clear();}}});
    }
    void listen()throws IOException{start(15000,true);}
    String shortAddress(){return "http://"+host+":"+getListeningPort();}
    String address(){return shortAddress()+"/"+token+"/";}
    String code(){return code;}
    boolean expired(){return closed||!busy.get()&&(System.nanoTime()-touched)/1_000_000>=FileTransferPolicy.SESSION_MS;}
    void invalidate(){closed=true;}
    @Override public void stop(){closed=true;super.stop();}
    @Override protected boolean useGzipWhenAccepted(Response response){return false;}
    @Override public Response serve(IHTTPSession session){try{return secure(route(session));}catch(IOException|JSONException|IllegalArgumentException failure){return secure(error(Response.Status.BAD_REQUEST,failure.getMessage()==null?"备份传输未完成，请重试":failure.getMessage()));}}
    private Response route(IHTTPSession session)throws IOException,JSONException{
        if(expired())return error(Response.Status.GONE,"连接已结束，请在电视重新开启");String origin=session.getHeaders().get("origin");if(origin!=null&&!origin.equals(shortAddress()))return error(Response.Status.FORBIDDEN,"请求来源无效");String uri=session.getUri(),prefix="/"+token+"/";boolean authorized=uri.startsWith(prefix);String route=authorized?uri.substring(prefix.length()):uri.substring(1);
        if(session.getMethod()==Method.POST&&uri.equals("/connect"))return connect(session);
        if(session.getMethod()==Method.GET&&(route.isEmpty()||route.equals("app.js")||route.equals("styles.css")))return newChunkedResponse(Response.Status.OK,route.endsWith("js")?"text/javascript; charset=utf-8":route.endsWith("css")?"text/css; charset=utf-8":"text/html; charset=utf-8",assets.open(route.isEmpty()?"index.html":route));
        if(!authorized)return error(Response.Status.NOT_FOUND,"页面不存在");touched=System.nanoTime();
        if(session.getMethod()==Method.GET&&route.equals("info")){JSONObject info=new JSONObject().put("mode",download==null?"import":"export").put("name",name).put("bytes",download==null?0:download.length()).put("media",media).put("maximum",maximum);return json(info);}
        if((session.getMethod()==Method.GET||session.getMethod()==Method.HEAD)&&route.equals("download")&&download!=null){DesktopBackupRecord.require(download.isFile()&&download.length()>0,"备份文件已不可用，请重新导出");InputStream stream=session.getMethod()==Method.HEAD?new ByteArrayInputStream(new byte[0]):new FilterInputStream(new FileInputStream(download)){@Override public int read(byte[] bytes,int offset,int count)throws IOException{if(closed)throw new IOException("连接已结束");touched=System.nanoTime();return super.read(bytes,offset,count);}@Override public int read()throws IOException{if(closed)throw new IOException("连接已结束");touched=System.nanoTime();return super.read();}};Response response=newFixedLengthResponse(Response.Status.OK,"application/octet-stream",stream,download.length());response.addHeader("Content-Disposition","attachment; filename=\"QuietTV_backup\"; filename*=UTF-8''"+URLEncoder.encode(name,"UTF-8").replace("+","%20"));return response;}
        if(session.getMethod()!=Method.POST||!route.equals("upload")||importer==null)return error(Response.Status.METHOD_NOT_ALLOWED,"不支持此操作");
        if(imported)return error(Response.Status.CONFLICT,"备份已接收，请在电视确认；如需再次传入，请重新开启连接");
        if(session.getHeaders().containsKey("transfer-encoding"))return error(Response.Status.BAD_REQUEST,"需要明确文件长度");long length;try{length=Long.parseLong(session.getHeaders().get("content-length"));}catch(Exception missing){return error(Response.Status.LENGTH_REQUIRED,"需要文件长度");}if(length<=0||length>maximum)return error(Response.Status.PAYLOAD_TOO_LARGE,"备份文件超过电视可用空间");String encoded=session.getHeaders().get("x-file-name");if(encoded==null||encoded.length()>2048)return error(Response.Status.BAD_REQUEST,"文件名无效");String name=FileTransferPolicy.name(URLDecoder.decode(encoded,"UTF-8"));if(!name.toLowerCase(Locale.ROOT).matches(".*\\.(json|zip)$"))return error(Response.Status.BAD_REQUEST,"请选择 JSON 或 ZIP 桌面备份");if(!busy.compareAndSet(false,true))return error(Response.Status.CONFLICT,"已有备份正在传输");listener.started(name,length);String failure="";
        try{InputStream stream=new FilterInputStream(session.getInputStream()){@Override public void close(){}long bytes;@Override public int read(byte[] buffer,int offset,int count)throws IOException{if(closed)throw new IOException("连接已结束");int read=super.read(buffer,offset,count);if(read>0){bytes+=read;touched=System.nanoTime();listener.progress(bytes,length);}return read;}@Override public int read()throws IOException{byte[] value=new byte[1];return read(value,0,1)<0?-1:value[0]&255;}};JSONObject received=importer.read(stream,length,name,()->!closed);DesktopBackupRecord.require(!closed,"连接已结束");imported=true;return newFixedLengthResponse(Response.Status.CREATED,"application/json; charset=utf-8",received.toString());}catch(IOException invalid){failure=invalid.getMessage();throw invalid;}finally{busy.set(false);touched=System.nanoTime();listener.finished(failure);}
    }
    private synchronized Response connect(IHTTPSession session)throws IOException,JSONException{
        long now=System.nanoTime();if(now<blocked)return error(Response.Status.FORBIDDEN,"尝试过多，请稍后重试");if(!"6".equals(session.getHeaders().get("content-length"))||session.getHeaders().containsKey("transfer-encoding"))return error(Response.Status.BAD_REQUEST,"请输入六位连接码");byte[] bytes=new byte[6];new DataInputStream(session.getInputStream()).readFully(bytes);if(!code.equals(new String(bytes,Charsets.US_ASCII))){if(++failures>=5){blocked=now+30_000_000_000L;failures=0;}return error(Response.Status.FORBIDDEN,"连接码不正确");}failures=0;touched=System.nanoTime();return json(new JSONObject().put("path","/"+token+"/"));
    }
    private static Response json(JSONObject value){return newFixedLengthResponse(Response.Status.OK,"application/json; charset=utf-8",value.toString());}
    private static Response error(Response.Status status,String message){try{return newFixedLengthResponse(status,"application/json; charset=utf-8",new JSONObject().put("message",message).toString());}catch(JSONException impossible){throw new IllegalStateException(impossible);}}
    private static Response secure(Response response){response.addHeader("Cache-Control","no-store");response.addHeader("Connection","close");response.addHeader("Referrer-Policy","no-referrer");response.addHeader("X-Content-Type-Options","nosniff");response.addHeader("Content-Security-Policy","default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");return response;}
}
