package home.quiet.tv;

import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import org.json.*;

/** A fixed entry with durable browser pairing and separately bounded active connections. */
final class PhoneSettingsServer extends NanoHTTPD {
    static final long IDLE_MS=10L*60*1000;
    private static final int PAIRING_ATTEMPT_LIMIT=5;
    private static final long PAIRING_RETRY_MS=30_000;
    interface Assets {InputStream open(String name)throws IOException;}
    interface Active {boolean valid();default String childManagement(){return "";}}
    interface BackendFactory {Backend create();}
    static final class Download{final InputStream input;final long bytes;final String name,mime;Download(InputStream input,long bytes,String name,String mime){this.input=input;this.bytes=bytes;this.name=name;this.mime=mime;}}
    interface Backend {
        JSONObject snapshot()throws IOException;
        JSONObject change(String field,Object value,String revision,Active active)throws IOException;
        byte[] thumbnail(String id)throws IOException;
        default PhoneSettingsState protocol(){return null;}
        default byte[] resource(String route)throws IOException{throw new BadInput();}
        default void delivered(String id){}
        default void connectionEnded(){}
        default Download previewFile(String id)throws IOException{throw new BadInput();}
        default Download previewVideo(String id)throws IOException{throw new BadInput();}
        default byte[] previewImage(String id)throws IOException{throw new BadInput();}
        default Download download(String route)throws IOException{throw new BadInput();}
        default JSONObject about(boolean runtime)throws IOException{throw new BadInput();}
        default JSONObject places(String query)throws IOException{throw new BadInput();}
        default JSONObject parent(JSONObject value,Active active)throws IOException{throw new BadInput();}
        default JSONObject connectMovieWall(JSONObject value,Active active)throws IOException{throw new BadInput();}
        default JSONObject task(String id)throws IOException{throw new BadInput();}
        default JSONObject upload(String id,InputStream input,long length,Active active)throws IOException{throw new BadInput();}
    }
    static final class Conflict extends IOException {Conflict(){super("电视设置已变化，请重新读取后再保存");}}
    static final class BadInput extends IOException {BadInput(){super("Invalid request");}}
    static final class Ended extends IOException {Ended(){super("连接已结束，请重新打开收藏的网址");}}
    static final class Rejected extends IOException {Rejected(String message){super(message);}}
    private final String diagnosticSession=UUID.randomUUID().toString();
    private void diagnosticState(){DiagnosticLog.info("phone.session.state","session="+diagnosticSession+" state="+(Arrays.asList("waiting","connected","qr-expired","expired","ended","network-changed").contains(state)?state:"other"));}
    private final String host;private final PhonePairing pairing;private final Assets assets;private final BackendFactory backends;private final Runnable changed;
    private final java.util.concurrent.atomic.AtomicBoolean transferring=new java.util.concurrent.atomic.AtomicBoolean();
    private final Set<ClientHandler> clients=new HashSet<>();
    private static final class BrowserConnection {
        final Backend backend;long touched=System.nanoTime();BrowserConnection(Backend backend){this.backend=backend;}
    }
    private final Map<String,BrowserConnection> connections=new HashMap<>();
    private String pairingCode="",state="waiting";
    private long codeCreated,blockedAt;
    private int failedPairings;
    private volatile boolean closed;
    PhoneSettingsServer(String host,int port,PhonePairing pairing,Assets assets,Backend backend,Runnable changed){
        this(host,port,pairing,assets,()->backend,changed);
    }
    PhoneSettingsServer(String host,int port,PhonePairing pairing,Assets assets,BackendFactory backends,Runnable changed){
        super(host,port);this.host=host;this.pairing=pairing;this.assets=assets;this.backends=backends;this.changed=changed;
        diagnosticState();
        setAsyncRunner(new AsyncRunner(){public void exec(ClientHandler handler){synchronized(clients){if(closed||clients.size()>=4){handler.close();return;}clients.add(handler);}Thread thread=new Thread(handler,"phone-settings-http");thread.setDaemon(true);thread.start();}public void closed(ClientHandler handler){synchronized(clients){clients.remove(handler);}}public void closeAll(){synchronized(clients){for(ClientHandler handler:new ArrayList<>(clients))handler.close();clients.clear();}}});
    }
    void listen()throws IOException {start(15000,true);}
    String address(){return origin()+"/";}
    private String origin(){return "http://"+host+":"+getListeningPort();}
    synchronized String pairingCode(){
        if(closed)return "";
        if(pairingCode.isEmpty()){
            pairingCode=PhonePairing.code();
            codeCreated=System.nanoTime();
        }
        return pairingCode;
    }
    synchronized String state(){
        expire();
        if(codeExpired()){pairingCode="";changed.run();}
        return state;
    }
    private boolean codeExpired(){return !pairingCode.isEmpty()&&(System.nanoTime()-codeCreated)/1000000>=IDLE_MS;}
    synchronized void reopen(){end("waiting");}
    synchronized void revokePairing()throws IOException {pairing.revoke();reopen();}
    private void expire(){
        long now=System.nanoTime();boolean expired=false;
        for(Iterator<BrowserConnection> values=connections.values().iterator();values.hasNext();){BrowserConnection value=values.next();if((now-value.touched)/1000000>IDLE_MS){values.remove();value.backend.connectionEnded();expired=true;}}
        if(expired){if(connections.isEmpty())state="expired";diagnosticState();changed.run();}
    }
    synchronized void end(){end("ended");}
    synchronized void end(String reason){for(BrowserConnection value:connections.values())value.backend.connectionEnded();connections.clear();pairingCode="";state=reason;diagnosticState();changed.run();}
    @Override public void stop(){synchronized(this){closed=true;if(!connections.isEmpty()||"waiting".equals(state))end();}super.stop();}
    private synchronized boolean authorized(String browser,BrowserConnection expected){expire();return !closed&&expected!=null&&connections.get(browser)==expected&&pairing.accepts(browser);}
    private String browser(IHTTPSession session){String cookie=session.getHeaders().get("cookie");if(cookie!=null)for(String item:cookie.split(";")){String value=item.trim();if(value.startsWith("quiet_phone="))return value.substring(12);}return "";}
    @Override public Response serve(IHTTPSession session){
        if(closed)return error(Response.Status.GONE,"连接已结束，请重新扫码");
        String requestOrigin=session.getHeaders().get("origin"),authority=host+":"+getListeningPort();
        if(!authority.equalsIgnoreCase(session.getHeaders().get("host"))||requestOrigin!=null&&!requestOrigin.equals(origin()))return error(Response.Status.FORBIDDEN,"请求来源无效");
        String uri=session.getUri(),browser=browser(session);
        if(session.getMethod()==Method.GET&&uri.startsWith("/pair/"))return landing();
        if(session.getMethod()==Method.POST&&uri.equals("/pair"))return pair(session,browser);
        String route=uri.equals("/")?"":uri.substring(1);
        boolean publicAsset=route.matches("assets/[A-Za-z0-9_-]+\\.(png|webp|svg|wav)");
        if((session.getMethod()==Method.GET||session.getMethod()==Method.HEAD)&&(route.isEmpty()||route.equals("app.js")||route.equals("styles.css")||route.equals("tmdb.svg")||publicAsset)){
            boolean trusted=session.getMethod()==Method.GET&&route.isEmpty()&&pairing.accepts(browser);
            if(trusted)synchronized(this){expire();if(pairing.accepts(browser))connect(browser);else trusted=false;}
            try{
                Response response=secure(route.isEmpty()?settingsPage():newChunkedResponse(Response.Status.OK,
                        route.endsWith(".js")?"application/javascript; charset=utf-8":route.endsWith(".png")?"image/png":route.endsWith(".webp")?"image/webp":route.endsWith(".svg")?"image/svg+xml":route.endsWith(".wav")?"audio/wav":"text/css; charset=utf-8",
                        assets.open(route)));
                if(trusted)cookie(response,browser);
                return response;
            }catch(IOException failure){return error(Response.Status.INTERNAL_ERROR,"设置页面暂不可用");}
        }
        if(session.getMethod()==Method.GET&&route.equals("status")){
            synchronized(this){
                expire();
                if(!pairing.accepts(browser))return json(Response.Status.OK,object("state","pairing"));
                connect(browser);return connected(browser);
            }
        }
        final BrowserConnection expected; synchronized(this){expected=connections.get(browser);}
        if(!authorized(browser,expected))return error(Response.Status.GONE,"连接已结束，请重新打开收藏的网址；若已解除配对，请重新扫码");
        final Backend backend=expected.backend;
        String managementToken=session.getHeaders().get("x-quiet-child-management");
        Active active=new Active(){
            public boolean valid(){return authorized(browser,expected);}
            public String childManagement(){return managementToken==null?"":managementToken;}
        };
        try{
            if(session.getMethod()==Method.GET&&route.startsWith("api/preview-image/")){
                String id=java.net.URLDecoder.decode(route.substring("api/preview-image/".length()),"UTF-8");byte[] image=backend.previewImage(id);if(!authorized(browser,expected))throw new Ended();return secure(newFixedLengthResponse(Response.Status.OK,"image/jpeg",new ByteArrayInputStream(image),image.length));
            }
            boolean filePreview=route.startsWith("api/preview-file/");
            if((session.getMethod()==Method.GET||session.getMethod()==Method.HEAD)&&(filePreview||route.startsWith("api/preview-video/"))){
                Download file=filePreview?backend.previewFile(route.substring("api/preview-file/".length()))
                    :backend.previewVideo(route.substring("api/preview-video/".length()));
                return secure(PhoneSettingsVideo.response(file,session.getHeaders().get("range"),session.getMethod()==Method.HEAD,()->authorized(browser,expected)));
            }
            if(session.getMethod()==Method.GET&&route.startsWith("api/downloads/")){
                if(!transferring.compareAndSet(false,true))throw new Rejected("已有文件正在传输，请稍后再试");Download file;try{file=backend.download(route.substring(14));if(!authorized(browser,expected)){file.input.close();throw new Ended();}}catch(IOException|RuntimeException e){transferring.set(false);throw e;}
                InputStream checked=new FilterInputStream(file.input){private void valid()throws IOException{if(!authorized(browser,expected))throw new Ended();touch(expected);}public int read()throws IOException{valid();return in.read();}public int read(byte[] b,int off,int count)throws IOException{valid();return in.read(b,off,count);}public void close()throws IOException{try{super.close();}finally{transferring.set(false);}}};
                Response result=file.bytes<0?newChunkedResponse(Response.Status.OK,file.mime,checked):newFixedLengthResponse(Response.Status.OK,file.mime,checked,file.bytes);result.addHeader("Content-Disposition","attachment; filename*=UTF-8''"+java.net.URLEncoder.encode(file.name,"UTF-8").replace("+","%20"));return secure(result);
            }
            if(session.getMethod()==Method.GET&&(route.equals("api/about/device")||route.equals("api/about/runtime"))){
                JSONObject result=backend.about(route.equals("api/about/runtime"));
                if(!authorized(browser,expected))throw new Ended();
                return json(Response.Status.OK,result);
            }
            if(session.getMethod()==Method.GET&&route.equals("api/places")){JSONObject result=backend.places(session.getParms().get("q"));if(!authorized(browser,expected))throw new Ended();return json(Response.Status.OK,result);}
            if(session.getMethod()==Method.POST&&route.equals("api/parent")){JSONObject result=backend.parent(read(session,4096),active);if(!authorized(browser,expected))throw new Ended();touch(expected);return json(Response.Status.OK,result);}
            if(session.getMethod()==Method.POST&&route.equals("api/movie-wall/connect")){
                JSONObject result=backend.connectMovieWall(read(session,8192),()->authorized(browser,expected));
                if(!authorized(browser,expected))throw new Ended();
                touch(expected);
                return json(Response.Status.ACCEPTED,result);
            }
            if(session.getMethod()==Method.GET&&route.startsWith("api/tasks/")){
                String id=route.substring("api/tasks/".length());if(!id.matches("[a-f0-9-]{36}"))throw new BadInput();JSONObject task=backend.task(id);if(!authorized(browser,expected))throw new Ended();return json(Response.Status.OK,task);
            }
            if(session.getMethod()==Method.POST&&route.startsWith("api/uploads/")){
                String id=route.substring("api/uploads/".length());if(!id.matches("[a-f0-9-]{36}")||session.getHeaders().containsKey("transfer-encoding"))throw new BadInput();
                long length;try{length=Long.parseLong(session.getHeaders().get("content-length"));}catch(NumberFormatException e){throw new BadInput();}if(length<1)throw new BadInput();
                if(!transferring.compareAndSet(false,true))throw new Rejected("已有文件正在传输，请稍后再试");JSONObject result;try{result=backend.upload(id,session.getInputStream(),length,()->{if(!authorized(browser,expected))return false;touch(expected);return true;});}finally{transferring.set(false);}if(!authorized(browser,expected))throw new Ended();touch(expected);return json(Response.Status.OK,result);
            }
            if(session.getMethod()==Method.GET&&route.startsWith("api/resources/")){byte[] image=backend.resource(route.substring("api/resources/".length()));if(!authorized(browser,expected))throw new Ended();return secure(newFixedLengthResponse(Response.Status.OK,route.startsWith("api/resources/quiz-art/")?"image/svg+xml":route.startsWith("api/resources/movie-wall/")?"image/jpeg":"image/png",new ByteArrayInputStream(image),image.length));}
            PhoneSettingsState protocol=backend.protocol();
            if(protocol!=null&&route.startsWith("api/")){
                JSONObject result;
                if(session.getMethod()==Method.GET&&route.equals("api/bootstrap")){touch(expected);result=protocol.bootstrap();}
                else if(session.getMethod()==Method.GET&&route.equals("api/state")){
                    touch(expected);result=protocol.changes(session.getParms().get("since"));
                    if(result==null){if(!authorized(browser,expected))throw new Ended();return secure(newFixedLengthResponse(Response.Status.NO_CONTENT,"application/json",""));}
                }else if(session.getMethod()==Method.GET&&route.startsWith("api/requests/")){
                    String id=route.substring("api/requests/".length());if(!id.matches("[A-Za-z0-9_-]{8,80}"))throw new BadInput();
                    touch(expected);result=protocol.request(id);if(result==null)return error(Response.Status.NOT_FOUND,"请求结果已不可查询，请核对当前设置");
                }else if(session.getMethod()==Method.POST&&route.equals("api/settings")){
                    result=protocol.apply(read(session,65536),active);touch(expected);
                }else return error(Response.Status.NOT_FOUND,"Not found");
                if(!authorized(browser,expected))throw new Ended();JSONObject operation=result.optJSONObject("operation");Response.Status status=operation!=null&&operation.has("taskId")?Response.Status.ACCEPTED:Response.Status.OK;
                if(operation!=null&&operation.has("afterReceipt")){String id=operation.optString("afterReceipt");if(!id.matches("[a-f0-9-]{36}"))throw new BadInput();byte[] bytes=result.toString().getBytes(Charsets.UTF_8);return secure(new Response(status,"application/json; charset=utf-8",new ByteArrayInputStream(bytes),bytes.length){@Override protected void send(OutputStream output){final boolean[] failed={false};OutputStream tracked=new FilterOutputStream(output){public void write(int value)throws IOException{try{out.write(value);}catch(IOException e){failed[0]=true;throw e;}}public void write(byte[] values,int off,int count)throws IOException{try{out.write(values,off,count);}catch(IOException e){failed[0]=true;throw e;}}public void flush()throws IOException{try{out.flush();}catch(IOException e){failed[0]=true;throw e;}}};super.send(tracked);if(!failed[0]&&authorized(browser,expected))backend.delivered(id);}});}
                return json(status,result);
            }
            if(session.getMethod()==Method.GET&&route.equals("settings")){JSONObject data=backend.snapshot();if(!authorized(browser,expected))throw new Ended();return json(Response.Status.OK,data);}
            if(session.getMethod()==Method.GET&&route.startsWith("wallpaper/")){String id=java.net.URLDecoder.decode(route.substring(10),"UTF-8");byte[] image=backend.thumbnail(id);if(!authorized(browser,expected))throw new Ended();return secure(newFixedLengthResponse(Response.Status.OK,"image/jpeg",new ByteArrayInputStream(image),image.length));}
            if(session.getMethod()!=Method.POST||!route.equals("setting"))return error(Response.Status.METHOD_NOT_ALLOWED,"Method not allowed");
            JSONObject data=read(session);Object field=data.opt("field"),revision=data.opt("revision");if(data.length()!=3||!(field instanceof String)||!(revision instanceof String)||!data.has("value"))return error(Response.Status.BAD_REQUEST,"设置内容无效");
            if(!Arrays.asList("keySounds","brightness","wallpaper","greeting").contains(field))return error(Response.Status.BAD_REQUEST,"此设置不可修改");
            JSONObject result=backend.change((String)field,data.get("value"),(String)revision,()->authorized(browser,expected));touch(expected);return json(Response.Status.OK,result);
        }catch(Conflict conflict){return error(Response.Status.CONFLICT,conflict.getMessage());}catch(Ended ended){return error(Response.Status.GONE,ended.getMessage());}catch(Rejected rejected){return error(Response.Status.BAD_REQUEST,rejected.getMessage());}catch(BadInput|JSONException|IllegalArgumentException error){return error(Response.Status.BAD_REQUEST,"设置内容无效");}catch(IOException error){return error(Response.Status.INTERNAL_ERROR,"保存未确认，输入已保留");}
    }
    private Response settingsPage()throws IOException {
        try(InputStream input=assets.open("index.html");ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] buffer=new byte[1024];
            int count;
            while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);
            // Share clients need full URLs. Use the same bound LAN origin as the QR code.
            String html=bytes.toString("UTF-8")
                    .replace("property=\"og:url\" content=\"/\"", "property=\"og:url\" content=\""+address()+"\"")
                    .replace("property=\"og:image\" content=\"./", "property=\"og:image\" content=\""+address());
            return newFixedLengthResponse(Response.Status.OK,"text/html; charset=utf-8",html);
        }
    }
    private Response landing(){Response response=secure(newFixedLengthResponse(Response.Status.FOUND,"text/plain; charset=utf-8",""));response.addHeader("Location","/");return response;}
    private Response pair(IHTTPSession session,String browser){
        try{
            JSONObject request=read(session,512);
            Object code=request.opt("code");
            if(request.length()!=1||!(code instanceof String)||!((String)code).matches("[0-9]{8}"))throw new BadInput();
            synchronized(this){
                if(closed)return error(Response.Status.GONE,"连接已结束，请重新打开收藏的网址");
                if(pairing.accepts(browser)){connect(browser);return connected(browser);}
                if(pairingCode.isEmpty()||codeExpired())return error(Response.Status.GONE,"请在电视打开扫码入口，查看当前配对码");
                if(failedPairings>=PAIRING_ATTEMPT_LIMIT){
                    if((System.nanoTime()-blockedAt)/1000000<PAIRING_RETRY_MS)
                        return error(Response.Status.FORBIDDEN,"尝试次数过多，请在 30 秒后重试");
                    failedPairings=0;
                }
                if(!pairingCode.equals(code)){
                    if(++failedPairings==PAIRING_ATTEMPT_LIMIT)blockedAt=System.nanoTime();
                    return error(Response.Status.FORBIDDEN,"配对码不正确，请核对电视上的配对码");
                }
                String credential=pairing.pair();
                failedPairings=0;connect(credential);return connected(credential);
            }
        }catch(BadInput invalid){return error(Response.Status.BAD_REQUEST,"请输入电视上的 8 位配对码");}
        catch(IOException failure){return error(Response.Status.INTERNAL_ERROR,"手机配对信息保存失败，请重试");}
    }
    private Response connected(String browser){
        Response response=json(Response.Status.OK,object("state","connected"));cookie(response,browser);return response;
    }
    private void cookie(Response response,String credential){response.addHeader("Set-Cookie","quiet_phone="+credential+"; HttpOnly; SameSite=Strict; Max-Age=31536000; Path=/");}
    private void connect(String browser){
        BrowserConnection value=connections.get(browser);
        if(value==null){value=new BrowserConnection(backends.create());connections.put(browser,value);state="connected";diagnosticState();changed.run();}
        value.touched=System.nanoTime();
    }
    private synchronized void touch(BrowserConnection expected){expected.touched=System.nanoTime();}
    private JSONObject read(IHTTPSession session)throws IOException {return read(session,8192);}
    private JSONObject read(IHTTPSession session,int limit)throws IOException {
        String type=session.getHeaders().get("content-type");if(type==null||!type.toLowerCase(Locale.ROOT).split(";",2)[0].trim().equals("application/json")||session.getHeaders().containsKey("transfer-encoding"))throw new BadInput();
        int count;try{count=Integer.parseInt(session.getHeaders().get("content-length"));}catch(NumberFormatException error){throw new BadInput();}if(count<1||count>limit)throw new BadInput();byte[] bytes=new byte[count];try{new DataInputStream(session.getInputStream()).readFully(bytes);String text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();try{return new JSONObject(text);}catch(JSONException error){throw new BadInput();}}catch(EOFException|CharacterCodingException error){throw new BadInput();}
    }
    private static JSONObject object(String key,Object value){try{return new JSONObject().put(key,value);}catch(JSONException error){throw new IllegalStateException(error);}}
    private Response error(Response.Status status,String message){return json(status,object("message",message));}
    private Response json(Response.Status status,JSONObject value){return secure(newFixedLengthResponse(status,"application/json; charset=utf-8",value.toString()));}
    private Response secure(Response response){response.addHeader("Connection","close");response.addHeader("Cache-Control","no-store");response.addHeader("Referrer-Policy","no-referrer");response.addHeader("X-Content-Type-Options","nosniff");response.addHeader("Content-Security-Policy","default-src 'none'; script-src 'self'; style-src 'self'; font-src data:; connect-src 'self'; img-src 'self' blob:; media-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");return response;}
}
