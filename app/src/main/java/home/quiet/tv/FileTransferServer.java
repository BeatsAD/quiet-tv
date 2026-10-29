package home.quiet.tv;

import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.json.*;

/** One explicit LAN session; only files offered on the TV can be downloaded. */
final class FileTransferServer extends NanoHTTPD {
    interface Assets {InputStream open(String name)throws IOException;}
    interface Listener {void progress(long bytes,long expected);void received(FileTransferStore.Entry entry);default void started(){}default void finished(boolean success){}}
    private final String host,token,code;
    private final FileTransferStore store;
    private final Assets assets;
    private final Listener listener;
    private final Set<String> offered=Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<ClientHandler> clients=new HashSet<>();
    private final AtomicBoolean uploading=new AtomicBoolean();
    private final AtomicInteger uploadGeneration=new AtomicInteger();
    private volatile boolean closed;
    private final long started=System.nanoTime();
    private int failures;private long blocked;
    FileTransferServer(String host,FileTransferStore store,Assets assets,Listener listener){
        super(host,0);this.host=host;this.store=store;this.assets=assets;this.listener=listener;
        SecureRandom random=new SecureRandom();byte[] bytes=new byte[16];random.nextBytes(bytes);StringBuilder value=new StringBuilder();for(byte b:bytes)value.append(String.format(Locale.ROOT,"%02x",b&255));token=value.toString();
        code=String.format(Locale.ROOT,"%06d",random.nextInt(1000000));
        setAsyncRunner(new AsyncRunner(){
            public void exec(ClientHandler client){synchronized(clients){if(closed||clients.size()>=4){client.close();return;}clients.add(client);}Thread thread=new Thread(client,"file-transfer-http");thread.setDaemon(true);thread.start();}
            public void closed(ClientHandler client){synchronized(clients){clients.remove(client);}}
            public void closeAll(){synchronized(clients){for(ClientHandler client:new ArrayList<>(clients))client.close();clients.clear();}}
        });
    }
    void listen()throws IOException{start(15000,true);}
    String shortAddress(){return "http://"+host+":"+getListeningPort();}
    String address(){return shortAddress()+"/"+token+"/";}
    String code(){return code;}
    boolean expired(){return closed||(System.nanoTime()-started)/1000000>=FileTransferPolicy.SESSION_MS;}
    void offer(String id)throws IOException{store.entry(id);offered.add(id);}
    void cancelCurrent(){uploadGeneration.incrementAndGet();}
    void invalidate(){closed=true;cancelCurrent();}
    @Override public void stop(){invalidate();super.stop();}
    @Override protected boolean useGzipWhenAccepted(Response response){return false;}
    @Override public Response serve(IHTTPSession request){
        try{return headers(route(request));}
        catch(IOException|JSONException|IllegalArgumentException failure){return headers(error(Response.Status.BAD_REQUEST,failure instanceof FileNotFoundException?"文件不存在或已移除":"操作未完成，请检查文件、空间或连接后重试"));}
    }
    private Response route(IHTTPSession request)throws IOException,JSONException{
        if(expired())return error(Response.Status.GONE,"传输已结束，请在电视重新开启");
        String origin=request.getHeaders().get("origin");if(origin!=null&&!origin.equals(shortAddress()))return error(Response.Status.FORBIDDEN,"请求来源无效");
        String uri=request.getUri(),prefix="/"+token+"/";
        if("/connect".equals(uri)&&request.getMethod()==Method.POST)return connect(request);
        boolean authorized=uri.startsWith(prefix);String route=authorized?uri.substring(prefix.length()):uri.substring(1);
        if(request.getMethod()==Method.GET&&(route.isEmpty()||route.equals("app.js")||route.equals("styles.css")))return newChunkedResponse(Response.Status.OK,route.endsWith("js")?"text/javascript; charset=utf-8":route.endsWith("css")?"text/css; charset=utf-8":"text/html; charset=utf-8",assets.open(route.isEmpty()?"index.html":route));
        if(!authorized)return error(Response.Status.NOT_FOUND,"页面不存在");
        if(request.getMethod()==Method.GET&&route.equals("files")){
            JSONArray files=new JSONArray();List<String> ids;synchronized(offered){ids=new ArrayList<>(offered);}
            for(String id:ids)try{files.put(store.entry(id).json());}catch(IOException removed){offered.remove(id);}
            return json(new JSONObject().put("files",files));
        }
        if((request.getMethod()==Method.GET||request.getMethod()==Method.HEAD)&&route.startsWith("file/")){
            String id=route.substring(5);if(!offered.contains(id))return error(Response.Status.NOT_FOUND,"文件未加入下载列表");
            FileTransferStore.Entry entry=store.entry(id);InputStream input=request.getMethod()==Method.HEAD?new ByteArrayInputStream(new byte[0]):new FileInputStream(store.file(id));
            Response response=newFixedLengthResponse(Response.Status.OK,"application/octet-stream",input,entry.bytes);
            response.addHeader("Content-Disposition","attachment; filename=\"download\"; filename*=UTF-8''"+URLEncoder.encode(entry.name,"UTF-8").replace("+","%20"));return response;
        }
        if(request.getMethod()==Method.POST&&route.equals("upload")){
            if(request.getHeaders().containsKey("transfer-encoding"))return error(Response.Status.BAD_REQUEST,"需要明确文件长度");
            long length;try{length=Long.parseLong(request.getHeaders().get("content-length"));}catch(Exception e){return error(Response.Status.LENGTH_REQUIRED,"需要文件长度");}
            if(length<=0||length>FileTransferPolicy.MAX_FILE)return error(Response.Status.PAYLOAD_TOO_LARGE,"单文件最大 200 MB");
            String encoded=request.getHeaders().get("x-file-name");if(encoded==null||encoded.length()>2048)return error(Response.Status.BAD_REQUEST,"文件名无效");
            String name=FileTransferPolicy.name(URLDecoder.decode(encoded,"UTF-8"));
            if(!uploading.compareAndSet(false,true))return error(Response.Status.CONFLICT,"已有文件正在传输，请稍后再试");
            int generation=uploadGeneration.incrementAndGet();long deadline=System.nanoTime()+600000000000L;
            listener.started();boolean success=false;
            try{FileTransferStore.Entry entry=store.receive(request.getInputStream(),length,name,()->!closed&&generation==uploadGeneration.get()&&System.nanoTime()<deadline,listener::progress);
                listener.received(entry);success=true;return newFixedLengthResponse(Response.Status.CREATED,"application/json; charset=utf-8",entry.json().toString());
            }finally{uploading.set(false);listener.finished(success);}
        }
        return error(request.getMethod()==Method.GET?Response.Status.NOT_FOUND:Response.Status.METHOD_NOT_ALLOWED,"不支持此操作");
    }
    private synchronized Response connect(IHTTPSession request)throws IOException,JSONException{
        long now=System.nanoTime();if(now<blocked)return error(Response.Status.FORBIDDEN,"尝试过多，请稍后重试");
        if(!"6".equals(request.getHeaders().get("content-length"))||request.getHeaders().containsKey("transfer-encoding"))return error(Response.Status.BAD_REQUEST,"请输入六位连接码");
        byte[] input=new byte[6];new DataInputStream(request.getInputStream()).readFully(input);
        if(!code.equals(new String(input,Charsets.US_ASCII))){if(++failures>=5){blocked=now+30000000000L;failures=0;}return error(Response.Status.FORBIDDEN,"连接码不正确");}
        failures=0;return json(new JSONObject().put("path","/"+token+"/"));
    }
    private static Response json(JSONObject value){return newFixedLengthResponse(Response.Status.OK,"application/json; charset=utf-8",value.toString());}
    private static Response error(Response.Status status,String message){try{return newFixedLengthResponse(status,"application/json; charset=utf-8",new JSONObject().put("message",message).toString());}catch(JSONException impossible){throw new IllegalStateException(impossible);}}
    private static Response headers(Response response){response.addHeader("Cache-Control","no-store");response.addHeader("Connection","close");response.addHeader("Referrer-Policy","no-referrer");response.addHeader("X-Content-Type-Options","nosniff");response.addHeader("Content-Security-Policy","default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");return response;}
}
