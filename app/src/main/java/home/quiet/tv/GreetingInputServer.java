package home.quiet.tv;

import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.SecureRandom;
import java.util.*;
import org.json.*;

/** A short-lived, single-submission text session. Never writes desktop preferences. */
final class GreetingInputServer extends NanoHTTPD {
    static final int MAX_BODY=8192;
    static final long IDLE_MS=10L*60*1000;
    interface Assets { InputStream open(String name) throws IOException; }
    interface Receiver { boolean accept(String text) throws IOException; }
    private final Assets assets;
    private final Receiver receiver;
    private final String host,token,code,draft;
    private final Set<ClientHandler> clients=new HashSet<>();
    private volatile boolean closed,draining;
    private volatile long touched=System.nanoTime();
    private boolean received;
    private int failures;
    private long blockedUntil;

    GreetingInputServer(String host,int port,String draft,Assets assets,Receiver receiver){
        super(host,port);this.host=host;this.draft=draft;this.assets=assets;this.receiver=receiver;
        SecureRandom random=new SecureRandom();byte[] bytes=new byte[16];random.nextBytes(bytes);
        StringBuilder path=new StringBuilder();for(byte b:bytes)path.append(String.format(Locale.ROOT,"%02x",b&255));token=path.toString();code=String.format(Locale.ROOT,"%06d",random.nextInt(1000000));
        setAsyncRunner(new AsyncRunner(){
            @Override public void exec(ClientHandler client){synchronized(clients){if(closed||clients.size()>=4){client.close();return;}clients.add(client);}Thread thread=new Thread(client,"greeting-http");thread.setDaemon(true);thread.start();}
            @Override public void closed(ClientHandler client){boolean finish;synchronized(clients){clients.remove(client);finish=draining&&clients.isEmpty();}if(finish)GreetingInputServer.super.stop();}
            @Override public void closeAll(){synchronized(clients){for(ClientHandler client:new ArrayList<>(clients))client.close();clients.clear();}}
        });
    }
    void listen() throws IOException {start(15000,true);}
    String shortAddress(){return "http://"+host+":"+getListeningPort();}
    String address(){return shortAddress()+"/"+token+"/";}
    String connectionCode(){return code;}
    boolean expired(){return (System.nanoTime()-touched)/1000000>IDLE_MS;}
    void invalidate(){closed=true;}
    /** Reject further requests now, but let the accepted response reach the phone. */
    void finishAfterResponse(){boolean finish;synchronized(clients){closed=true;draining=true;finish=clients.isEmpty();}if(finish)super.stop();}
    @Override public void stop(){closed=true;super.stop();}
    @Override public Response serve(IHTTPSession session){
        if(closed||expired())return error(Response.Status.GONE,"会话已结束，请在电视重新打开 / Session ended");
        String uri=session.getUri(),prefix="/"+token+"/";
        String origin=session.getHeaders().get("origin");
        if(origin!=null&&!origin.equals(shortAddress()))return error(Response.Status.FORBIDDEN,"无效的请求来源 / Invalid origin");
        if("/connect".equals(uri)&&session.getMethod()==Method.POST)return connect(session);
        boolean authorized=uri.startsWith(prefix);
        String route=authorized?uri.substring(prefix.length()):uri.substring(1);
        if(session.getMethod()==Method.GET){
            if("draft".equals(route)&&authorized){synchronized(this){if(received)return error(Response.Status.GONE,"文字已接收，请在电视保存 / Text already received");}touched=System.nanoTime();return json(Response.Status.OK,"text",draft);}
            String mime;
            switch(route){case "":route="index.html";mime="text/html; charset=utf-8";break;case "app.js":mime="application/javascript; charset=utf-8";break;case "styles.css":mime="text/css; charset=utf-8";break;default:return error(Response.Status.NOT_FOUND,"Not found");}
            if(!authorized&&!uri.equals("/")&&!uri.equals("/app.js")&&!uri.equals("/styles.css"))return error(Response.Status.NOT_FOUND,"Not found");
            if(authorized)touched=System.nanoTime();
            try{return secure(newChunkedResponse(Response.Status.OK,mime,assets.open(route)));}catch(IOException e){return error(Response.Status.INTERNAL_ERROR,"输入页面不可用 / Input page unavailable");}
        }
        if(!authorized)return error(Response.Status.NOT_FOUND,"Not found");
        if(session.getMethod()!=Method.POST||!"text".equals(route))return error(Response.Status.METHOD_NOT_ALLOWED,"Method not allowed");
        String type=session.getHeaders().get("content-type");
        if(type==null||!type.toLowerCase(Locale.ROOT).split(";",2)[0].trim().equals("application/json"))return error(Response.Status.BAD_REQUEST,"需要 JSON 文字 / JSON required");
        try{
            String body=read(session,MAX_BODY);
            JSONObject object=new JSONObject(body);Object value=object.opt("text");
            if(object.length()!=1||!(value instanceof String))return error(Response.Status.BAD_REQUEST,"无效的文字 / Invalid text");
            String text=((String)value).replace('\r',' ').replace('\n',' ').replace('\t',' ');
            for(int i=0;i<text.length();i++){
                char c=text.charAt(i);if(c<32||c==127)return error(Response.Status.BAD_REQUEST,"文字包含不支持的控制字符 / Unsupported control character");
                if(Character.isHighSurrogate(c)){if(i+1>=text.length()||!Character.isLowSurrogate(text.charAt(++i)))return error(Response.Status.BAD_REQUEST,"无效的文字编码 / Invalid text encoding");}
                else if(Character.isLowSurrogate(c))return error(Response.Status.BAD_REQUEST,"无效的文字编码 / Invalid text encoding");
            }
            text=text.trim();
            synchronized(this){
                if(closed||expired())return error(Response.Status.GONE,"会话已结束 / Session ended");
                if(received)return error(Response.Status.CONFLICT,"文字已接收，请在电视保存 / Text already received");
                if(!receiver.accept(text))return error(Response.Status.GONE,"会话已结束 / Session ended");
                received=true;touched=System.nanoTime();
            }
            return json(Response.Status.OK,"accepted",true);
        }catch(BodyTooLarge e){return error(Response.Status.PAYLOAD_TOO_LARGE,"文字过长，请精简后重试 / Text is too long");}
        catch(IOException|JSONException e){return error(Response.Status.BAD_REQUEST,"文字未能接收，请检查连接后重试 / Unable to receive text");}
    }
    private synchronized Response connect(IHTTPSession session){
        long now=System.nanoTime();if(now<blockedUntil)return error(Response.Status.FORBIDDEN,"尝试过于频繁，请稍后重试 / Too many attempts");
        try{
            String submitted=read(session,6);
            if(submitted.length()!=6)return error(Response.Status.BAD_REQUEST,"请输入 6 位连接码 / Enter the six-digit code");
            if(!code.equals(submitted)){if(++failures>=5){blockedUntil=now+30000000000L;failures=0;}return error(Response.Status.FORBIDDEN,"连接码不正确 / Incorrect connection code");}
            if(closed||received)return error(Response.Status.GONE,"会话已结束 / Session ended");
            failures=0;touched=now;return json(Response.Status.OK,"path","/"+token+"/");
        }catch(IOException e){return error(Response.Status.BAD_REQUEST,"请输入 6 位连接码 / Enter the six-digit code");}
    }
    private String read(IHTTPSession session,int limit)throws IOException{
        if(session.getHeaders().containsKey("transfer-encoding"))throw new IOException("Unsupported transfer encoding");
        int length;try{length=Integer.parseInt(session.getHeaders().get("content-length"));}catch(NumberFormatException e){throw new IOException("Length required");}
        if(length>limit)throw new BodyTooLarge();if(length<=0)throw new IOException("Empty body");
        byte[] bytes=new byte[length];new DataInputStream(session.getInputStream()).readFully(bytes);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    private static final class BodyTooLarge extends IOException {}
    private Response json(Response.Status status,String key,Object value){try{return secure(newFixedLengthResponse(status,"application/json; charset=utf-8",new JSONObject().put(key,value).toString()));}catch(JSONException e){throw new IllegalStateException(e);}}
    private Response error(Response.Status status,String message){return json(status,"message",message);}
    private Response secure(Response response){
        response.addHeader("Connection","close");response.addHeader("Cache-Control","no-store");response.addHeader("Referrer-Policy","no-referrer");response.addHeader("X-Content-Type-Options","nosniff");
        response.addHeader("Content-Security-Policy","default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");return response;
    }
}
