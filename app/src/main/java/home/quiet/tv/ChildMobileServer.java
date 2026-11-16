package home.quiet.tv;

import android.content.*;
import android.os.*;
import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;
import org.bouncycastle.util.encoders.Base64;
import org.json.*;

/** Native child-mode HTTPS adapter. QR web settings continue using their existing protocol. */
final class ChildMobileServer extends NanoHTTPD {
    interface Approval {void request(String fingerprint,Runnable allow,Runnable deny);}
    private static ChildMobileServer instance;
    private final Context context;
    private final SharedPreferences preferences;
    final ChildMobileIdentity identity;
    final String host;
    private final LinkedHashMap<String,Binding> bindings=new LinkedHashMap<>();
    private final Set<ClientHandler> clients=new HashSet<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean stopped;
    private String offerId="";
    private byte[] offerSecret;
    private long offerExpires;
    private volatile Approval approval;
    private volatile Pending pending;
    private static final class Pending {final String fingerprint,ticket=UUID.randomUUID().toString();final byte[] credential=new byte[32];final long expires=SystemClock.elapsedRealtime()+60_000;String binding="",failure="";boolean decided;Pending(String fingerprint){this.fingerprint=fingerprint;new SecureRandom().nextBytes(credential);}}
    private final class Binding {
        final String id,hash,fingerprint;
        final PhoneSettingsChildren children;
        final PhoneSettingsState protocol;
        Binding(String id,String hash,String fingerprint){this.id=id;this.hash=hash;this.fingerprint=fingerprint;children=new PhoneSettingsChildren(context,()->PhoneSettingsSession.get(context).configurationChanged());protocol=new PhoneSettingsState(new PhoneSettingsState.Source(){
            public String[] domains(){return new String[]{"child","quiz","childUsage","parent"};}
            public Object lock(String domain){return domain.equals("quiz")?ChildQuizStore.LOCK:ChildModeStore.LOCK;}
            public JSONObject read(String domain)throws IOException{try{switch(domain){case "child":return children.configuration();case "quiz":return children.quiz();case "childUsage":return children.usage();case "parent":return children.parent.state();default:throw new IllegalArgumentException();}}catch(JSONException e){throw new IOException(e);}}
            public JSONObject metadata()throws IOException{try{return new JSONObject().put("childApps",children.apps()).put("subjects",new JSONArray(Arrays.asList(ChildQuizBank.SUBJECTS))).put("grades",new JSONArray(Arrays.asList(ChildQuizBank.GRADES)));}catch(JSONException e){throw new IOException(e);}}
            public PhoneSettingsState.Result write(String domain,String field,Object value,String expected,PhoneSettingsServer.Active active)throws IOException{if(!domain.equals("child")&&!domain.equals("quiz"))throw new PhoneSettingsServer.BadInput();children.write(domain,field,value,active);return new PhoneSettingsState.Result(read(domain));}
        });}
        JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("hash",hash).put("fingerprint",fingerprint);}
    }
    static synchronized ChildMobileServer get(Context supplied)throws IOException{
        Context context=supplied.getApplicationContext();String host=WallpaperUploadPanel.lanAddress(context);if(host==null||host.isEmpty()||host.equals("127.0.0.1")||host.equals("0.0.0.0"))throw new IOException("请先连接局域网");if(instance!=null&&!instance.stopped&&instance.host.equals(host))return instance;if(instance!=null)instance.stop();ChildMobileServer created=new ChildMobileServer(context,host);created.start(15000,true);if(!created.preferences.edit().putBoolean("enabled",true).putInt("port",created.getListeningPort()).commit()){created.stop();throw new IOException("连接服务状态保存失败");}instance=created;return created;
    }
    static void restore(Context supplied){Context context=supplied.getApplicationContext();if(!context.getSharedPreferences("child-mobile",0).getBoolean("enabled",false))return;new Thread(()->{try{get(context);}catch(IOException e){DiagnosticLog.info("child.mobile.service","restore_unavailable");}},"child-mobile-restore").start();}
    private ChildMobileServer(Context context,String host)throws IOException{
        super(host,context.getSharedPreferences("child-mobile",0).getInt("port",0));this.context=context;this.host=host;preferences=context.getSharedPreferences("child-mobile",0);identity=new ChildMobileIdentity(context,host);makeSecure(identity.factory,new String[]{"TLSv1.2"});
        try{JSONArray saved=new JSONArray(preferences.getString("bindings","[]"));if(saved.length()>4)throw new IOException("手机绑定记录无效，原记录已保留");for(int i=0;i<saved.length();i++){JSONObject value=saved.getJSONObject(i);String id=value.getString("id"),hash=value.getString("hash"),fingerprint=value.getString("fingerprint");if(!id.matches("[a-f0-9-]{36}")||!hash.matches("[a-f0-9]{64}")||!fingerprint.matches("[a-f0-9]{64}"))throw new IOException("手机绑定记录无效，原记录已保留");bindings.put(id,new Binding(id,hash,fingerprint));}}catch(JSONException e){throw new IOException("手机绑定记录无法读取，原记录已保留",e);}
        setAsyncRunner(new AsyncRunner(){public void exec(ClientHandler handler){synchronized(clients){if(stopped||clients.size()>=4){handler.close();return;}clients.add(handler);}Thread worker=new Thread(handler,"child-mobile-https");worker.setDaemon(true);worker.start();}public void closed(ClientHandler handler){synchronized(clients){clients.remove(handler);}}public void closeAll(){synchronized(clients){for(ClientHandler handler:new ArrayList<>(clients))handler.close();clients.clear();}}});
    }
    synchronized JSONObject offer(Approval callback)throws IOException{
        clearOffer();offerId=UUID.randomUUID().toString();offerSecret=new byte[32];new SecureRandom().nextBytes(offerSecret);offerExpires=SystemClock.elapsedRealtime()+5*60_000;approval=callback;
        try{JSONObject result=new JSONObject(identity.peer.toString());return result.put("kind","quiettv-service-binding").put("protocol",1).put("audience","child").put("expiresAt",System.currentTimeMillis()+5*60_000).put("origin","https://"+(host.contains(":")?"["+host+"]":host)+":"+getListeningPort()).put("certificateSpkiSha256",identity.pin).put("tvId",identity.tvId).put("sessionId",offerId).put("oneTimeSecret",Base64.toBase64String(offerSecret));}catch(JSONException e){throw new IOException(e);}
    }
    synchronized void clearOffer(){if(offerSecret!=null)Arrays.fill(offerSecret,(byte)0);offerSecret=null;offerId="";approval=null;if(pending!=null)Arrays.fill(pending.credential,(byte)0);pending=null;}
    synchronized void clearOffer(String id){if(offerId.equals(id))clearOffer();}
    synchronized boolean pairingPending(){return pending!=null&&SystemClock.elapsedRealtime()<pending.expires;}
    synchronized JSONArray phones()throws IOException{try{JSONArray result=new JSONArray();for(Binding binding:bindings.values())result.put(new JSONObject().put("id",binding.id).put("fingerprint",binding.fingerprint.substring(0,8)));return result;}catch(JSONException e){throw new IOException(e);}}
    synchronized void revoke(String id)throws IOException{Binding removed=bindings.remove(id);if(removed==null)throw new IOException("绑定已不存在");try{saveBindings();}catch(IOException e){bindings.put(id,removed);throw e;}}
    private void saveBindings()throws IOException{try{JSONArray result=new JSONArray();for(Binding binding:bindings.values())result.put(binding.json());if(!preferences.edit().putString("bindings",result.toString()).commit())throw new IOException("手机授权保存失败");}catch(JSONException e){throw new IOException(e);}}
    synchronized void end()throws IOException{clearOffer();boolean saved=preferences.edit().putBoolean("enabled",false).commit();stop();if(!saved)throw new IOException("服务已停止，下次启动设置尚未保存");}
    @Override public void stop(){synchronized(this){stopped=true;clearOffer();}super.stop();}
    private synchronized boolean active(Binding binding){return !stopped&&bindings.get(binding.id)==binding;}
    private synchronized Binding bearer(String authorization)throws IOException{if(authorization==null||!authorization.startsWith("Bearer "))throw new PhoneSettingsServer.Rejected("手机连接未授权");byte[] raw=ChildMobileProof.decode(authorization.substring(7),64);if(raw.length!=32)throw new PhoneSettingsServer.Rejected("手机连接未授权");String hash=ChildMobileProof.digest(raw);Arrays.fill(raw,(byte)0);for(Binding binding:bindings.values())if(MessageDigest.isEqual(hash.getBytes(Charsets.US_ASCII),binding.hash.getBytes(Charsets.US_ASCII)))return binding;throw new PhoneSettingsServer.Rejected("手机授权已撤销，请重新连接");}
    private synchronized JSONObject pair(IHTTPSession session,String request,JSONObject body)throws IOException,JSONException{
        String auth=session.getHeaders().get("authorization");if(offerSecret==null||SystemClock.elapsedRealtime()>=offerExpires||auth==null||!auth.startsWith("Pairing ")||!MessageDigest.isEqual(offerSecret,ChildMobileProof.decode(auth.substring(8),64))||!offerId.equals(body.optString("sessionId")))throw new PhoneSettingsServer.Rejected("二维码已过期，请在电视刷新");
        if(session.getUri().endsWith("/status")){if(pending==null||!pending.ticket.equals(body.optString("ticket")))throw new PhoneSettingsServer.Rejected("授权票据无效");}
        else{
            String fingerprint=ChildMobileProof.verify(body,offerId,identity.tvId,identity.peer.getInt("uid"),request);
            if(pending!=null&&!pending.fingerprint.equals(fingerprint))throw new PhoneSettingsServer.Rejected("已有手机正在等待授权");if(pending==null){if(bindings.size()>=4)throw new PhoneSettingsServer.Rejected("已绑定四台手机，请先撤销不再使用的绑定");Pending item=new Pending(fingerprint);pending=item;Approval callback=approval;if(callback==null)throw new PhoneSettingsServer.Rejected("请在电视打开连接页");main.post(()->{if(approval==callback&&pending==item)callback.request(fingerprint,()->decide(item,true),()->decide(item,false));});}
        }
        Pending item=pending;if(item==null||SystemClock.elapsedRealtime()>=item.expires)throw new PhoneSettingsServer.Rejected("授权等待已过期，请刷新二维码");if(!item.failure.isEmpty())throw new PhoneSettingsServer.Rejected(item.failure);
        if(item.decided)return new JSONObject().put("result","BOUND").put("audience","child").put("bindingId",item.binding).put("grantedScopes",new JSONArray(Arrays.asList(ChildMobileProof.SCOPES))).put("credential",Base64.toBase64String(item.credential));return new JSONObject().put("result","PENDING").put("ticket",item.ticket);
    }
    private synchronized void decide(Pending item,boolean approved){if(pending!=item||SystemClock.elapsedRealtime()>=item.expires)return;if(!approved){item.failure="电视未批准此手机";return;}String id=UUID.randomUUID().toString();Binding binding=new Binding(id,ChildMobileProof.digest(item.credential),item.fingerprint);bindings.put(id,binding);try{saveBindings();item.binding=id;item.decided=true;}catch(IOException e){bindings.remove(id);item.failure=e.getMessage();}}
    @Override public Response serve(IHTTPSession session){String request=session.getHeaders().get("x-quiettv-request");try{
        if(request==null||!request.matches("[a-f0-9-]{36}"))throw new PhoneSettingsServer.BadInput();identity.requirePeer(session.getHeaders().get("x-quiettv-context"));String path=session.getUri();
        if(session.getMethod()==Method.POST&&(path.equals("/quiettv-mobile/v1/bindings")||path.equals("/quiettv-mobile/v1/bindings/status"))){JSONObject result=pair(session,request,body(session));return response(result.getString("result").equals("BOUND")?Response.Status.CREATED:Response.Status.ACCEPTED,result,request,null);}
        Binding binding=bearer(session.getHeaders().get("authorization"));PhoneSettingsServer.Active active=()->active(binding);if(!active.valid())throw new PhoneSettingsServer.Ended();JSONObject result;
        if(session.getMethod()==Method.GET&&path.equals("/quiettv-mobile/v1/capabilities"))result=new JSONObject().put("features",new JSONArray(Arrays.asList("child.v1"))).put("scopes",new JSONArray(Arrays.asList(ChildMobileProof.SCOPES)));
        else if(session.getMethod()==Method.GET&&path.equals("/quiettv-mobile/v1/child/state"))result=binding.protocol.bootstrap();
        else if(session.getMethod()==Method.GET&&path.startsWith("/quiettv-mobile/v1/child/rest/")){String slug=path.substring(path.lastIndexOf('/')+1);if(!Arrays.asList("cloud","forest","space").contains(slug))throw new PhoneSettingsServer.BadInput();byte[] bytes=PhoneSettingsPreview.get(context).resource("child-rest/"+slug);if(bytes.length>131072)throw new IOException("缩略图过大");result=new JSONObject().put("id",slug).put("contentBase64",Base64.toBase64String(bytes)).put("sha256",ChildMobileProof.digest(bytes));}
        else if(session.getMethod()==Method.POST&&path.equals("/quiettv-mobile/v1/child/parent"))result=binding.children.parent.execute(body(session),active);
        else if(session.getMethod()==Method.POST&&path.equals("/quiettv-mobile/v1/child/actions")){JSONObject value=body(session);if(!request.equals(value.optString("requestId")))throw new PhoneSettingsServer.BadInput();result=binding.protocol.apply(value,active);}
        else if(session.getMethod()==Method.GET&&path.startsWith("/quiettv-mobile/v1/child/requests/")){String id=path.substring(path.lastIndexOf('/')+1);if(!id.matches("[a-f0-9-]{36}"))throw new PhoneSettingsServer.BadInput();result=binding.protocol.request(id);if(result==null)return response(Response.Status.NOT_FOUND,new JSONObject().put("result","UNKNOWN"),request,binding);}
        else if(session.getMethod()==Method.DELETE&&path.equals("/quiettv-mobile/v1/bindings/"+binding.id)){revoke(binding.id);return response(Response.Status.OK,new JSONObject().put("revoked",true),request,binding);}
        else return response(Response.Status.NOT_FOUND,new JSONObject().put("message","此电视未提供该功能"),request,binding);
        if(!active.valid())throw new PhoneSettingsServer.Ended();return response(Response.Status.OK,result,request,binding);
    }catch(PhoneSettingsServer.Conflict e){return error(Response.Status.CONFLICT,e.getMessage(),request);}catch(PhoneSettingsServer.Ended e){return error(Response.Status.GONE,e.getMessage(),request);}catch(PhoneSettingsServer.Rejected e){return error(Response.Status.FORBIDDEN,e.getMessage(),request);}catch(PhoneSettingsServer.BadInput|JSONException|IllegalArgumentException e){return error(Response.Status.BAD_REQUEST,"请求内容无效",request);}catch(IOException e){return error(Response.Status.INTERNAL_ERROR,e.getMessage()==null?"操作结果待核对":e.getMessage(),request);}}
    private JSONObject body(IHTTPSession session)throws IOException{try{if(session.getHeaders().containsKey("transfer-encoding")||(session.getHeaders().get("content-type")==null||!session.getHeaders().get("content-type").startsWith("application/json")))throw new PhoneSettingsServer.BadInput();int length=Integer.parseInt(session.getHeaders().get("content-length"));if(length<1||length>65536)throw new PhoneSettingsServer.BadInput();byte[] bytes=new byte[length];new DataInputStream(session.getInputStream()).readFully(bytes);String text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();return new JSONObject(text);}catch(JSONException|NumberFormatException|NullPointerException e){throw new PhoneSettingsServer.BadInput();}}
    private Response error(Response.Status status,String message,String request){try{return response(status,new JSONObject().put("message",message),request,null);}catch(JSONException e){throw new IllegalStateException(e);}}
    private Response response(Response.Status status,JSONObject result,String request,Binding binding){try{JSONObject value=new JSONObject(result.toString());if(value.has("requestId"))value.put("operationRequestId",value.getString("requestId"));for(Iterator<String> keys=identity.peer.keys();keys.hasNext();){String key=keys.next();value.put(key,identity.peer.get(key));}value.put("protocol",1).put("tvId",identity.tvId).put("requestId",request==null?"":request);if(binding!=null)value.put("bindingId",binding.id);byte[] bytes=value.toString().getBytes(Charsets.UTF_8);if(bytes.length>262144)throw new IllegalStateException("儿童模式响应大小超限");Response response=newFixedLengthResponse(status,"application/json; charset=utf-8",new ByteArrayInputStream(bytes),bytes.length);response.addHeader("Cache-Control","no-store");response.addHeader("Connection","close");response.addHeader("X-Content-Type-Options","nosniff");return response;}catch(JSONException e){throw new IllegalStateException(e);}}
}
