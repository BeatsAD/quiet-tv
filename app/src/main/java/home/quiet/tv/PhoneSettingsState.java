package home.quiet.tv;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Versioned phone protocol over the TV's existing stores and mutation locks. */
final class PhoneSettingsState {
    interface Source {
        String[] domains();
        Object lock(String domain);
        default boolean requiresRevision(String domain,String field){return true;}
        JSONObject read(String domain)throws IOException;
        JSONObject metadata()throws IOException;
        Result write(String domain,String field,Object value,String expected,PhoneSettingsServer.Active active)throws IOException;
    }
    static final class Result {
        final JSONObject state,operation;
        Result(JSONObject state){this(state,null);}
        Result(JSONObject state,JSONObject operation){this.state=state;this.operation=operation;}
    }
    private final Source source;
    private final String generation=UUID.randomUUID().toString();
    private long sequence;
    private final LinkedHashMap<String,Attempt> attempts=new LinkedHashMap<>();
    private final LinkedHashMap<String,JSONObject> knownRevisions=new LinkedHashMap<>();
    private static final class Attempt {
        final String fingerprint;final long sequence;String state="unknown",domain="",committed="",message="";int errorStatus;JSONObject operation,committedState;
        Attempt(String fingerprint,long sequence){this.fingerprint=fingerprint;this.sequence=sequence;}
    }
    PhoneSettingsState(Source source){this.source=source;}
    synchronized JSONObject bootstrap()throws IOException {
        try{return snapshot().put("metadata",source.metadata());}catch(JSONException e){throw new IOException(e);}
    }
    synchronized JSONObject changes(String since)throws IOException {
        JSONObject snapshot=snapshot();if(snapshot.optString("revision").equals(since))return null;
        JSONObject previous=knownRevisions.get(since);
        if(previous!=null)try{
            JSONObject values=snapshot.getJSONObject("domains"),revisions=snapshot.getJSONObject("revisions");
            for(String name:source.domains())if(revisions.optString(name).equals(previous.optString(name)))values.remove(name);
            snapshot.put("complete",false);
        }catch(JSONException e){throw new IOException(e);}
        return snapshot;
    }
    private JSONObject snapshot()throws IOException {
        try{
            JSONObject domains=new JSONObject(),revisions=new JSONObject();
            for(String name:source.domains()){
                JSONObject value;synchronized(source.lock(name)){value=copy(source.read(name));}
                domains.put(name,value);revisions.put(name,revision(value));
            }
            String revision=revision(revisions);knownRevisions.put(revision,copy(revisions));
            if(knownRevisions.size()>64)knownRevisions.remove(knownRevisions.keySet().iterator().next());
            return new JSONObject().put("protocolVersion",2).put("generation",generation).put("lastSequence",sequence).put("complete",true)
                .put("domains",domains).put("revisions",revisions).put("revision",revision);
        }catch(JSONException e){throw new IOException(e);}
    }
    synchronized JSONObject apply(JSONObject request,PhoneSettingsServer.Active active)throws IOException {
        try{
            if(request.length()!=7)throw new IllegalArgumentException();
            for(String name:new String[]{"requestId","generation","domain","field","revision"})
                if(!(request.opt(name) instanceof String))throw new IllegalArgumentException();
            String id=request.getString("requestId"),domain=request.getString("domain"),field=request.getString("field");
            if(!id.matches("[A-Za-z0-9_-]{8,80}")||!request.has("value")||request.isNull("value"))throw new IllegalArgumentException();
            if(!generation.equals(request.getString("generation"))||!active.valid())throw new PhoneSettingsServer.Ended();
            Object raw=request.opt("sequence");if(!(raw instanceof Number))throw new IllegalArgumentException();
            long next=((Number)raw).longValue();
            if(next<1||next>9007199254740991L||((Number)raw).doubleValue()!=next)throw new IllegalArgumentException();
            String fingerprint=revision(request);Attempt previous=attempts.get(id);
            if(previous!=null){
                if(!previous.fingerprint.equals(fingerprint))throw new IllegalArgumentException();
                if(DiagnosticProbe.allow("phone.request.replay",2000))DiagnosticLog.info("phone.request.replay","request="+DiagnosticLog.anonymous("request",id)+" state="+previous.state);
                return receipt(id,previous.state);
            }
            // Never re-execute an old request, including after its receipt leaves the bounded cache.
            if(next<=sequence)throw new PhoneSettingsServer.Conflict();
            if(!Arrays.asList(source.domains()).contains(domain))throw new IllegalArgumentException();
            DiagnosticOperation trace=new DiagnosticOperation("phone.setting","request="+DiagnosticLog.anonymous("request",id)+" domain="+DiagnosticOperation.domain(domain));
            try{
            Attempt attempt=new Attempt(fingerprint,next);attempt.domain=domain;
            synchronized(source.lock(domain)){
                if(!active.valid())throw new PhoneSettingsServer.Ended();
                if(source.requiresRevision(domain,field)&&!revision(source.read(domain)).equals(request.getString("revision")))throw new PhoneSettingsServer.Conflict();
                sequence=next;attempts.put(id,attempt);
                if(attempts.size()>256)attempts.remove(attempts.keySet().iterator().next());
                String previousParent=DiagnosticOperation.enter(trace.id);
                try{trace.stage("WRITE");Result result=source.write(domain,field,request.get("value"),request.getString("revision"),active);attempt.committedState=copy(result.state);attempt.committed=revision(attempt.committedState);attempt.operation=result.operation==null?null:copy(result.operation);attempt.state="completed";}
                catch(PhoneSettingsServer.Conflict|PhoneSettingsServer.Ended|PhoneSettingsServer.Rejected e){attempt.state="rejected";attempt.errorStatus=e instanceof PhoneSettingsServer.Conflict?409:e instanceof PhoneSettingsServer.Ended?410:400;attempt.message=e.getMessage();throw e;}
                catch(IllegalArgumentException e){attempt.state="rejected";attempt.errorStatus=400;attempt.message="设置内容无效";throw e;}
                finally{DiagnosticOperation.restore(previousParent);}
            }
            if(!active.valid())throw new PhoneSettingsServer.Ended();
            trace.finish(attempt.operation==null?"SAVED":"ACCEPTED","state_returned task="+DiagnosticLog.anonymous("task",attempt.operation==null?"":attempt.operation.optString("taskId")));
            return receipt(id,attempt.state);
            }catch(PhoneSettingsServer.Conflict e){trace.finish("REJECTED","revision_conflict");throw e;}catch(PhoneSettingsServer.Ended e){trace.finish("UNKNOWN","connection_ended");throw e;}catch(PhoneSettingsServer.Rejected|IllegalArgumentException e){trace.finish("REJECTED","validation_or_policy");throw e;}catch(IOException|RuntimeException e){trace.finish("UNKNOWN",e.getClass().getSimpleName());throw e;}
        }catch(JSONException e){throw new IllegalArgumentException(e);}
    }
    synchronized JSONObject request(String id)throws IOException {
        Attempt attempt=attempts.get(id);return attempt==null?null:receipt(id,attempt.state);
    }
    private JSONObject receipt(String id,String state)throws IOException {
        // A receipt describes this operation only. Unrelated reads must not hide a committed write.
        try{Attempt attempt=attempts.get(id);return new JSONObject().put("protocolVersion",2).put("generation",generation).put("lastSequence",sequence).put("sequence",attempt.sequence)
            .put("requestId",id).put("requestStatus",state).put("committedDomain",attempt.domain).put("committedRevision",attempt.committed).put("committedState",attempt.committedState==null?null:copy(attempt.committedState))
            .put("operation",attempt.operation==null?null:copy(attempt.operation)).put("errorStatus",attempt.errorStatus).put("message",attempt.message);}catch(JSONException e){throw new IOException(e);}
    }
    static JSONObject copy(JSONObject object)throws IOException {
        try{return new JSONObject(object.toString());}catch(JSONException e){throw new IOException(e);}
    }
    static String revision(Object value)throws IOException {
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(canonical(value).getBytes(Charsets.UTF_8));
            StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
        }catch(java.security.NoSuchAlgorithmException|JSONException e){throw new IOException(e);}
    }
    private static String canonical(Object value)throws JSONException {
        if(value instanceof JSONObject){
            JSONObject object=(JSONObject)value;List<String> keys=new ArrayList<>();Iterator<String> iterator=object.keys();while(iterator.hasNext())keys.add(iterator.next());Collections.sort(keys);
            StringBuilder out=new StringBuilder("{");for(String key:keys){if(out.length()>1)out.append(',');out.append(JSONObject.quote(key)).append(':').append(canonical(object.get(key)));}return out.append('}').toString();
        }
        if(value instanceof JSONArray){JSONArray array=(JSONArray)value;StringBuilder out=new StringBuilder("[");for(int i=0;i<array.length();i++){if(i>0)out.append(',');out.append(canonical(array.get(i)));}return out.append(']').toString();}
        return value instanceof String?JSONObject.quote((String)value):String.valueOf(value);
    }
}
