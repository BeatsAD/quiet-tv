package home.quiet.tv;

import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.SecureRandom;
import java.util.*;
import org.json.*;

/** LAN-only city selection. The phone never receives weather credentials or writes preferences. */
final class WeatherInputServer extends NanoHTTPD {
    interface Assets { InputStream open(String name)throws IOException; }
    interface Receiver { boolean accept(WeatherRegions.Region region)throws IOException; }
    private final String host,token,code;
    private final Assets assets;
    private final WeatherRegions regions;
    private final Receiver receiver;
    private final Set<ClientHandler> clients=new HashSet<>();
    private volatile boolean closed,draining;
    private volatile long touched=System.nanoTime();
    private boolean received;
    private int failures;
    private long blockedUntil;
    WeatherInputServer(String host,Assets assets,WeatherRegions regions,Receiver receiver){
        super(host,0);this.host=host;this.assets=assets;this.regions=regions;this.receiver=receiver;
        SecureRandom random=new SecureRandom();byte[] bytes=new byte[16];random.nextBytes(bytes);StringBuilder value=new StringBuilder();for(byte b:bytes)value.append(String.format(Locale.ROOT,"%02x",b&255));token=value.toString();code=String.format(Locale.ROOT,"%06d",random.nextInt(1000000));
        setAsyncRunner(new AsyncRunner(){
            @Override public void exec(ClientHandler client){synchronized(clients){if(closed||clients.size()>=4){client.close();return;}clients.add(client);}Thread thread=new Thread(client,"weather-input-http");thread.setDaemon(true);thread.start();}
            @Override public void closed(ClientHandler client){boolean finish;synchronized(clients){clients.remove(client);finish=draining&&clients.isEmpty();}if(finish)WeatherInputServer.super.stop();}
            @Override public void closeAll(){synchronized(clients){for(ClientHandler client:new ArrayList<>(clients))client.close();clients.clear();}}
        });
    }
    void listen()throws IOException{start(5000,true);}
    String shortAddress(){return "http://"+host+":"+getListeningPort();}
    String address(){return shortAddress()+"/"+token+"/";}
    String connectionCode(){return code;}
    boolean expired(){return (System.nanoTime()-touched)/1000000>10*60*1000;}
    void invalidate(){closed=true;}
    @Override public void stop(){closed=true;super.stop();}
    void finishAfterResponse(){boolean finish;synchronized(clients){closed=true;draining=true;finish=clients.isEmpty();}if(finish)super.stop();}
    @Override public Response serve(IHTTPSession session){
        if(closed||expired())return error(Response.Status.GONE,"会话已结束，请在电视重新打开 / Session ended");
        String origin=session.getHeaders().get("origin");
        if(origin!=null&&!origin.equals(shortAddress()))return error(Response.Status.FORBIDDEN,"无效的请求来源 / Invalid origin");
        if(!(host+":"+getListeningPort()).equals(session.getHeaders().get("host")))return error(Response.Status.FORBIDDEN,"Invalid host");
        String uri=session.getUri(),prefix="/"+token+"/";
        if(uri.equals("/connect")&&session.getMethod()==Method.POST)return connect(session);
        boolean authorized=uri.startsWith(prefix);String route=authorized?uri.substring(prefix.length()):uri.substring(1);
        if(session.getMethod()==Method.GET){
            if(authorized&&(route.equals("search")||route.equals("children"))){
                synchronized(this){if(received)return error(Response.Status.GONE,"已发送，请在电视确认 / Already sent");}
                try{
                    String query=session.getParms().get(route.equals("search")?"q":"parent");if(query==null)query="";
                    if(query.length()>60)return error(Response.Status.BAD_REQUEST,"查询文字过长 / Query too long");
                    List<WeatherRegions.Region> matches=route.equals("search")?regions.search(query,40):regions.children(query);
                    touched=System.nanoTime();return json(Response.Status.OK,new JSONObject().put("results",regions.items(matches)));
                }catch(JSONException e){return error(Response.Status.INTERNAL_ERROR,"地区列表不可用 / Regions unavailable");}
            }
            if(!authorized&&!uri.equals("/")&&!uri.equals("/app.js")&&!uri.equals("/styles.css")&&!uri.equals("/weather.css"))return error(Response.Status.NOT_FOUND,"Not found");
            String file,mime;
            switch(route){case "":file="weather-input/index.html";mime="text/html; charset=utf-8";break;case "app.js":file="weather-input/app.js";mime="application/javascript; charset=utf-8";break;case "styles.css":file="greeting-input/styles.css";mime="text/css; charset=utf-8";break;case "weather.css":file="weather-input/weather.css";mime="text/css; charset=utf-8";break;default:return error(Response.Status.NOT_FOUND,"Not found");}
            if(authorized)touched=System.nanoTime();
            try{return secure(newChunkedResponse(Response.Status.OK,mime,assets.open(file)));}catch(IOException e){return error(Response.Status.INTERNAL_ERROR,"页面不可用 / Page unavailable");}
        }
        if(!authorized)return error(Response.Status.NOT_FOUND,"Not found");
        if(session.getMethod()!=Method.POST||!route.equals("select"))return error(Response.Status.METHOD_NOT_ALLOWED,"Method not allowed");
        String type=session.getHeaders().get("content-type");if(type==null||!type.split(";",2)[0].trim().equalsIgnoreCase("application/json"))return error(Response.Status.BAD_REQUEST,"JSON required");
        try{
            JSONObject body=new JSONObject(read(session,128));Object selected=body.opt("code");
            WeatherRegions.Region region=selected instanceof String?regions.find((String)selected):null;
            if(body.length()!=1||region==null||region.level==0)return error(Response.Status.BAD_REQUEST,"请选择城市或区县 / Select a city or district");
            synchronized(this){
                if(closed||received||expired())return error(Response.Status.GONE,"会话已结束 / Session ended");
                if(!receiver.accept(region))return error(Response.Status.GONE,"会话已结束 / Session ended");
                received=true;touched=System.nanoTime();
            }
            return json(Response.Status.OK,new JSONObject().put("accepted",true));
        }catch(IOException|JSONException e){return error(Response.Status.BAD_REQUEST,"未能发送，请检查连接 / Unable to send");}
    }
    private synchronized Response connect(IHTTPSession session){
        long now=System.nanoTime();if(now<blockedUntil)return error(Response.Status.FORBIDDEN,"尝试过于频繁，请稍后重试 / Too many attempts");
        try{
            String submitted=read(session,6);
            if(!code.equals(submitted)){if(++failures>=5){blockedUntil=now+30000000000L;failures=0;}return error(Response.Status.FORBIDDEN,"连接码不正确 / Incorrect code");}
            if(closed||received)return error(Response.Status.GONE,"会话已结束 / Session ended");
            failures=0;touched=now;return json(Response.Status.OK,new JSONObject().put("path","/"+token+"/"));
        }catch(IOException|JSONException e){return error(Response.Status.BAD_REQUEST,"请输入 6 位连接码 / Enter the six-digit code");}
    }
    private String read(IHTTPSession session,int limit)throws IOException{
        if(session.getHeaders().containsKey("transfer-encoding"))throw new IOException("Unsupported transfer encoding");
        int size;try{size=Integer.parseInt(session.getHeaders().get("content-length"));}catch(NumberFormatException e){throw new IOException("Length required");}
        if(size<=0||size>limit)throw new IOException("Invalid body length");
        byte[] bytes=new byte[size];new DataInputStream(session.getInputStream()).readFully(bytes);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    private Response error(Response.Status status,String text){try{return json(status,new JSONObject().put("message",text));}catch(JSONException e){throw new IllegalStateException(e);}}
    private Response json(Response.Status status,JSONObject value){return secure(newFixedLengthResponse(status,"application/json; charset=utf-8",value.toString()));}
    private Response secure(Response response){
        response.addHeader("Connection","close");response.addHeader("Cache-Control","no-store");response.addHeader("Referrer-Policy","no-referrer");response.addHeader("X-Content-Type-Options","nosniff");
        response.addHeader("Content-Security-Policy","default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");return response;
    }
}
