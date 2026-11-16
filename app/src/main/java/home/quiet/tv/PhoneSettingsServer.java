package home.quiet.tv;

import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.SecureRandom;
import java.util.*;
import org.json.*;

/** Scanning connects the first browser; its cookie remains exclusive until the session ends. */
final class PhoneSettingsServer extends NanoHTTPD {
    static final long IDLE_MS=10L*60*1000;
    interface Assets {InputStream open(String name)throws IOException;}
    interface Active {boolean valid();}
    static final class Download{final InputStream input;final long bytes;final String name,mime;Download(InputStream input,long bytes,String name,String mime){this.input=input;this.bytes=bytes;this.name=name;this.mime=mime;}}
    interface Backend {
        JSONObject snapshot()throws IOException;
        JSONObject change(String field,Object value,String revision,Active active)throws IOException;
        byte[] thumbnail(String id)throws IOException;
        default PhoneSettingsState protocol(){return null;}
        default byte[] resource(String route)throws IOException{throw new BadInput();}
        default void delivered(String id){}
        default Download previewVideo(String id)throws IOException{throw new BadInput();}
        default byte[] previewImage(String id)throws IOException{throw new BadInput();}
        default Download download(String route)throws IOException{throw new BadInput();}
        default JSONObject places(String query)throws IOException{throw new BadInput();}
        default JSONObject parent(JSONObject value,Active active)throws IOException{throw new BadInput();}
        default JSONObject task(String id)throws IOException{throw new BadInput();}
        default JSONObject upload(String id,InputStream input,long length,Active active)throws IOException{throw new BadInput();}
    }
    static final class Conflict extends IOException {Conflict(){super("电视设置已变化，请重新读取后再保存");}}
    static final class BadInput extends IOException {BadInput(){super("Invalid request");}}
    static final class Ended extends IOException {Ended(){super("连接已结束，请重新扫码");}}
    static final class Rejected extends IOException {Rejected(String message){super(message);}}
    private final String diagnosticSession=UUID.randomUUID().toString();
    private void diagnosticState(){DiagnosticLog.info("phone.session.state","session="+diagnosticSession+" state="+(Arrays.asList("waiting","connected","qr-expired","expired","ended","network-changed").contains(state)?state:"other"));}
    private final String host,path;private final Assets assets;private final Backend backend;private final Runnable changed;
    private final java.util.concurrent.atomic.AtomicBoolean transferring=new java.util.concurrent.atomic.AtomicBoolean();
    private final Set<ClientHandler> clients=new HashSet<>();private final SecureRandom random=new SecureRandom();
    private volatile boolean closed;private String state="waiting",client="";private final long created=System.nanoTime();private long touched=created;
    PhoneSettingsServer(String host,int port,Assets assets,Backend backend,Runnable changed){
        super(host,port);this.host=host;this.assets=assets;this.backend=backend;this.changed=changed;path="/"+secret()+"/";
        diagnosticState();
        setAsyncRunner(new AsyncRunner(){public void exec(ClientHandler handler){synchronized(clients){if(closed||clients.size()>=4){handler.close();return;}clients.add(handler);}Thread thread=new Thread(handler,"phone-settings-http");thread.setDaemon(true);thread.start();}public void closed(ClientHandler handler){synchronized(clients){clients.remove(handler);}}public void closeAll(){synchronized(clients){for(ClientHandler handler:new ArrayList<>(clients))handler.close();clients.clear();}}});
    }
    private String secret(){byte[] bytes=new byte[16];random.nextBytes(bytes);StringBuilder value=new StringBuilder();for(byte b:bytes)value.append(String.format(Locale.ROOT,"%02x",b&255));return value.toString();}
    void listen()throws IOException {start(15000,true);}
    String shortAddress(){return "http://"+host+":"+getListeningPort();}
    String address(){return shortAddress()+path;}
    synchronized String state(){expire();return state;}
    private void expire(){long now=System.nanoTime();if(("waiting".equals(state)||"connected".equals(state))&&(closed||(now-("connected".equals(state)?touched:created))/1000000>IDLE_MS)){state="waiting".equals(state)?"qr-expired":"expired";diagnosticState();client="";changed.run();}}
    synchronized void end(){end("ended");}
    synchronized void end(String reason){state=reason;diagnosticState();client="";changed.run();}
    @Override public void stop(){synchronized(this){closed=true;if("waiting".equals(state)||"connected".equals(state))end();}super.stop();}
    private synchronized boolean authorized(String browser){expire();return !closed&&"connected".equals(state)&&!client.isEmpty()&&client.equals(browser);}
    private String browser(IHTTPSession session){String cookie=session.getHeaders().get("cookie");if(cookie!=null)for(String item:cookie.split(";")){String value=item.trim();if(value.startsWith("quiet_phone="))return value.substring(12);}return "";}
    @Override public Response serve(IHTTPSession session){
        if(closed)return error(Response.Status.GONE,"连接已结束，请重新扫码");
        String origin=session.getHeaders().get("origin");if(origin!=null&&!origin.equals(shortAddress()))return error(Response.Status.FORBIDDEN,"请求来源无效");
        String uri=session.getUri();boolean token=uri.startsWith(path);String route=token?uri.substring(path.length()):uri.equals("/")?"":uri.substring(1);
        if(session.getMethod()==Method.GET&&"/".equals(uri)){Response response=secure(newFixedLengthResponse(Response.Status.FOUND,"text/plain; charset=utf-8",""));response.addHeader("Location",path);return response;}
        boolean publicAsset=route.matches("assets/[A-Za-z0-9_-]+\\.(png|webp|svg)");
        if(session.getMethod()==Method.GET&&(route.isEmpty()||route.equals("app.js")||route.equals("styles.css")||token&&publicAsset)){
            if(!token&&!uri.equals("/")&&!uri.equals("/app.js")&&!uri.equals("/styles.css"))return error(Response.Status.NOT_FOUND,"Not found");
            String claim="";if(token&&route.isEmpty()){synchronized(this){expire();if("waiting".equals(state)){client=secret();state="connected";diagnosticState();touched=System.nanoTime();claim=client;changed.run();}}}
            try{String name=route.isEmpty()?"index.html":route,mime=route.isEmpty()?"text/html; charset=utf-8":route.endsWith(".js")?"application/javascript; charset=utf-8":route.endsWith(".png")?"image/png":route.endsWith(".webp")?"image/webp":route.endsWith(".svg")?"image/svg+xml":"text/css; charset=utf-8";Response response=secure(newChunkedResponse(Response.Status.OK,mime,assets.open(name)));if(!claim.isEmpty())response.addHeader("Set-Cookie","quiet_phone="+claim+"; HttpOnly; SameSite=Strict; Path=/");return response;}catch(IOException failure){return error(Response.Status.INTERNAL_ERROR,"设置页面暂不可用");}
        }
        if(!token)return error(Response.Status.NOT_FOUND,"Not found");
        String browser=browser(session);
        if(session.getMethod()==Method.GET&&route.equals("status")){synchronized(this){expire();if(!client.equals(browser)||client.isEmpty())return error(!client.isEmpty()?Response.Status.CONFLICT:Response.Status.GONE,!client.isEmpty()?"已有手机正在连接，请在电视结束后重试":"连接已拒绝或结束，请重新扫码");return json(Response.Status.OK,object("state",state));}}
        if(!authorized(browser))return error(Response.Status.GONE,"连接已结束或由其他手机使用，请重新扫码连接");
        try{
            if(session.getMethod()==Method.GET&&route.startsWith("api/preview-image/")){
                String id=java.net.URLDecoder.decode(route.substring("api/preview-image/".length()),"UTF-8");byte[] image=backend.previewImage(id);if(!authorized(browser))throw new Ended();return secure(newFixedLengthResponse(Response.Status.OK,"image/jpeg",new ByteArrayInputStream(image),image.length));
            }
            if((session.getMethod()==Method.GET||session.getMethod()==Method.HEAD)&&route.startsWith("api/preview-video/")){
                Download file=backend.previewVideo(route.substring("api/preview-video/".length()));
                return secure(PhoneSettingsVideo.response(file,session.getHeaders().get("range"),session.getMethod()==Method.HEAD,()->authorized(browser)));
            }
            if(session.getMethod()==Method.GET&&route.startsWith("api/downloads/")){
                if(!transferring.compareAndSet(false,true))throw new Rejected("已有文件正在传输，请稍后再试");Download file;try{file=backend.download(route.substring(14));if(!authorized(browser)){file.input.close();throw new Ended();}}catch(IOException|RuntimeException e){transferring.set(false);throw e;}
                InputStream checked=new FilterInputStream(file.input){private void valid()throws IOException{if(!authorized(browser))throw new Ended();touch();}public int read()throws IOException{valid();return in.read();}public int read(byte[] b,int off,int count)throws IOException{valid();return in.read(b,off,count);}public void close()throws IOException{try{super.close();}finally{transferring.set(false);}}};
                Response result=file.bytes<0?newChunkedResponse(Response.Status.OK,file.mime,checked):newFixedLengthResponse(Response.Status.OK,file.mime,checked,file.bytes);result.addHeader("Content-Disposition","attachment; filename*=UTF-8''"+java.net.URLEncoder.encode(file.name,"UTF-8").replace("+","%20"));return secure(result);
            }
            if(session.getMethod()==Method.GET&&route.equals("api/places")){JSONObject result=backend.places(session.getParms().get("q"));if(!authorized(browser))throw new Ended();return json(Response.Status.OK,result);}
            if(session.getMethod()==Method.POST&&route.equals("api/parent")){JSONObject result=backend.parent(read(session,4096),()->authorized(browser));if(!authorized(browser))throw new Ended();touch();return json(Response.Status.OK,result);}
            if(session.getMethod()==Method.GET&&route.startsWith("api/tasks/")){
                String id=route.substring("api/tasks/".length());if(!id.matches("[a-f0-9-]{36}"))throw new BadInput();JSONObject task=backend.task(id);if(!authorized(browser))throw new Ended();return json(Response.Status.OK,task);
            }
            if(session.getMethod()==Method.POST&&route.startsWith("api/uploads/")){
                String id=route.substring("api/uploads/".length());if(!id.matches("[a-f0-9-]{36}")||session.getHeaders().containsKey("transfer-encoding"))throw new BadInput();
                long length;try{length=Long.parseLong(session.getHeaders().get("content-length"));}catch(NumberFormatException e){throw new BadInput();}if(length<1)throw new BadInput();
                if(!transferring.compareAndSet(false,true))throw new Rejected("已有文件正在传输，请稍后再试");JSONObject result;try{result=backend.upload(id,session.getInputStream(),length,()->{if(!authorized(browser))return false;touch();return true;});}finally{transferring.set(false);}if(!authorized(browser))throw new Ended();touch();return json(Response.Status.OK,result);
            }
            if(session.getMethod()==Method.GET&&route.startsWith("api/resources/")){byte[] image=backend.resource(route.substring("api/resources/".length()));if(!authorized(browser))throw new Ended();return secure(newFixedLengthResponse(Response.Status.OK,route.startsWith("api/resources/quiz-art/")?"image/svg+xml":"image/png",new ByteArrayInputStream(image),image.length));}
            PhoneSettingsState protocol=backend.protocol();
            if(protocol!=null&&route.startsWith("api/")){
                JSONObject result;
                if(session.getMethod()==Method.GET&&route.equals("api/bootstrap")){touch();result=protocol.bootstrap();}
                else if(session.getMethod()==Method.GET&&route.equals("api/state")){
                    touch();result=protocol.changes(session.getParms().get("since"));
                    if(result==null){if(!authorized(browser))throw new Ended();return secure(newFixedLengthResponse(Response.Status.NO_CONTENT,"application/json",""));}
                }else if(session.getMethod()==Method.GET&&route.startsWith("api/requests/")){
                    String id=route.substring("api/requests/".length());if(!id.matches("[A-Za-z0-9_-]{8,80}"))throw new BadInput();
                    touch();result=protocol.request(id);if(result==null)return error(Response.Status.NOT_FOUND,"请求结果已不可查询，请核对当前设置");
                }else if(session.getMethod()==Method.POST&&route.equals("api/settings")){
                    result=protocol.apply(read(session,65536),()->authorized(browser));touch();
                }else return error(Response.Status.NOT_FOUND,"Not found");
                if(!authorized(browser))throw new Ended();JSONObject operation=result.optJSONObject("operation");Response.Status status=operation!=null&&operation.has("taskId")?Response.Status.ACCEPTED:Response.Status.OK;
                if(operation!=null&&operation.has("afterReceipt")){String id=operation.optString("afterReceipt");if(!id.matches("[a-f0-9-]{36}"))throw new BadInput();byte[] bytes=result.toString().getBytes(Charsets.UTF_8);return secure(new Response(status,"application/json; charset=utf-8",new ByteArrayInputStream(bytes),bytes.length){@Override protected void send(OutputStream output){final boolean[] failed={false};OutputStream tracked=new FilterOutputStream(output){public void write(int value)throws IOException{try{out.write(value);}catch(IOException e){failed[0]=true;throw e;}}public void write(byte[] values,int off,int count)throws IOException{try{out.write(values,off,count);}catch(IOException e){failed[0]=true;throw e;}}public void flush()throws IOException{try{out.flush();}catch(IOException e){failed[0]=true;throw e;}}};super.send(tracked);if(!failed[0]&&authorized(browser))backend.delivered(id);}});}
                return json(status,result);
            }
            if(session.getMethod()==Method.GET&&route.equals("settings")){JSONObject data=backend.snapshot();if(!authorized(browser))throw new Ended();return json(Response.Status.OK,data);}
            if(session.getMethod()==Method.GET&&route.startsWith("wallpaper/")){String id=java.net.URLDecoder.decode(route.substring(10),"UTF-8");byte[] image=backend.thumbnail(id);if(!authorized(browser))throw new Ended();return secure(newFixedLengthResponse(Response.Status.OK,"image/jpeg",new ByteArrayInputStream(image),image.length));}
            if(session.getMethod()!=Method.POST||!route.equals("setting"))return error(Response.Status.METHOD_NOT_ALLOWED,"Method not allowed");
            JSONObject data=read(session);Object field=data.opt("field"),revision=data.opt("revision");if(data.length()!=3||!(field instanceof String)||!(revision instanceof String)||!data.has("value"))return error(Response.Status.BAD_REQUEST,"设置内容无效");
            if(!Arrays.asList("keySounds","brightness","wallpaper","greeting").contains(field))return error(Response.Status.BAD_REQUEST,"此设置不可修改");
            JSONObject result=backend.change((String)field,data.get("value"),(String)revision,()->authorized(browser));touch();return json(Response.Status.OK,result);
        }catch(Conflict conflict){return error(Response.Status.CONFLICT,conflict.getMessage());}catch(Ended ended){return error(Response.Status.GONE,ended.getMessage());}catch(Rejected rejected){return error(Response.Status.BAD_REQUEST,rejected.getMessage());}catch(BadInput|JSONException|IllegalArgumentException error){return error(Response.Status.BAD_REQUEST,"设置内容无效");}catch(IOException error){return error(Response.Status.INTERNAL_ERROR,"保存未确认，输入已保留");}
    }
    private synchronized void touch(){touched=System.nanoTime();}
    private JSONObject read(IHTTPSession session)throws IOException {return read(session,8192);}
    private JSONObject read(IHTTPSession session,int limit)throws IOException {
        String type=session.getHeaders().get("content-type");if(type==null||!type.toLowerCase(Locale.ROOT).split(";",2)[0].trim().equals("application/json")||session.getHeaders().containsKey("transfer-encoding"))throw new BadInput();
        int count;try{count=Integer.parseInt(session.getHeaders().get("content-length"));}catch(NumberFormatException error){throw new BadInput();}if(count<1||count>limit)throw new BadInput();byte[] bytes=new byte[count];try{new DataInputStream(session.getInputStream()).readFully(bytes);String text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();try{return new JSONObject(text);}catch(JSONException error){throw new BadInput();}}catch(EOFException|CharacterCodingException error){throw new BadInput();}
    }
    private static JSONObject object(String key,Object value){try{return new JSONObject().put(key,value);}catch(JSONException error){throw new IllegalStateException(error);}}
    private Response error(Response.Status status,String message){return json(status,object("message",message));}
    private Response json(Response.Status status,JSONObject value){return secure(newFixedLengthResponse(status,"application/json; charset=utf-8",value.toString()));}
    private Response secure(Response response){response.addHeader("Connection","close");response.addHeader("Cache-Control","no-store");response.addHeader("Referrer-Policy","no-referrer");response.addHeader("X-Content-Type-Options","nosniff");response.addHeader("Content-Security-Policy","default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' blob:; media-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");return response;}
}
