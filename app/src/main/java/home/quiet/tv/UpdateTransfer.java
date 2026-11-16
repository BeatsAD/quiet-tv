package home.quiet.tv;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** One source at a time; only verified Content-Range responses may append. */
final class UpdateTransfer {
    static final String[] SOURCES=home.quiet.components.DownloadSources.values();
    static final String PAGES="https://beatsad.github.io/quiet-tv/updates.json";
    static final String RAW="https://raw.githubusercontent.com/BeatsAD/quiet-tv/main/updates/updates.json";
    interface Connection {HttpURLConnection open(URL url)throws IOException;}
    interface Progress {void changed(long bytes,long total,boolean switching);}
    interface Clock {long now();}
    private static final ScheduledExecutorService WATCHDOG=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"QuietTV-update-timeout");t.setDaemon(true);return t;});
    private final Connection factory;private final Clock clock;private final boolean android4,independent;
    private volatile boolean cancelled,expired;private volatile HttpURLConnection active;
    UpdateTransfer(Connection factory){this(factory,false);}
    UpdateTransfer(Connection factory,boolean android4){this(factory,()->System.nanoTime()/1000000,android4);}
    UpdateTransfer(Connection factory,Clock clock){this(factory,clock,false);}
    UpdateTransfer(Connection factory,Clock clock,boolean android4){this(factory,clock,android4,false);}
    UpdateTransfer(Connection factory,boolean android4,boolean independent){this(factory,()->System.nanoTime()/1000000,android4,independent);}
    UpdateTransfer(Connection factory,Clock clock,boolean android4,boolean independent){this.factory=factory;this.clock=clock;this.android4=android4;this.independent=independent;}
    void cancel(){cancelled=true;HttpURLConnection request=active;if(request!=null)request.disconnect();}
    private void expire(){expired=true;HttpURLConnection request=active;if(request!=null)request.disconnect();}
    private void alive()throws InterruptedIOException{if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Update cancelled");}
    static List<Integer> sources(int ignoredPreference){
        // Preserve the agreed priority even when an older client remembered another source.
        List<Integer> result=new ArrayList<>();for(int n=0;n<SOURCES.length;n++)result.add(n);return result;
    }
    private static boolean mirrorHost(String host){
        return home.quiet.components.DownloadSources.mirror(host);
    }
    static String address(int source,String origin){return source<0?PAGES:SOURCES[source]+origin;}
    static String sourceName(int source){return source<0?"github_pages":source==SOURCES.length-1?"github_direct":SOURCES[source].substring(8,SOURCES[source].length()-1);}
    private static void failed(int source,String stage,IOException error){DiagnosticLog.info("update.source.failed","source="+sourceName(source)+" "+UpdateFailure.details(stage,error));}
    static boolean allowed(URL url){
        String host=url.getHost().toLowerCase(Locale.ROOT);
        if(!"https".equals(url.getProtocol())||url.getUserInfo()!=null||(url.getPort()!=-1&&url.getPort()!=443)||url.getRef()!=null)return false;
        if(Arrays.asList("github.com","raw.githubusercontent.com","release-assets.githubusercontent.com","objects.githubusercontent.com","beatsad.github.io").contains(host))return true;
        return mirrorHost(host);
    }
    static final class Response implements Closeable{
        final HttpURLConnection request;final InputStream input;final int status;
        Response(HttpURLConnection request,InputStream input,int status){this.request=request;this.input=input;this.status=status;}
        public void close()throws IOException{try{input.close();}finally{request.disconnect();}}
    }
    private Response connect(String address,long offset,long budget,boolean fresh,NetworkEvidence evidence)throws IOException{
        long end=clock.now()+Math.min(8000,budget);URL url=new URL(address);boolean mirrored=mirrorHost(url.getHost().toLowerCase(Locale.ROOT));
        for(int redirect=0;redirect<=4;redirect++){
            alive();if(!allowed(url))throw new IOException("Unapproved update endpoint");
            if(mirrored&&!mirrorHost(url.getHost().toLowerCase(Locale.ROOT)))throw new IOException("Mirror redirected to origin before fallback");
            int remaining=(int)Math.max(1,end-clock.now());if(clock.now()>=end)throw new SocketTimeoutException();
            HttpURLConnection request=factory.open(url);active=request;
            request.setConnectTimeout(remaining);request.setReadTimeout(remaining);request.setInstanceFollowRedirects(false);
            request.setRequestProperty("User-Agent","QuietTV-Update/1");request.setRequestProperty("Accept-Encoding","identity");
            if(fresh){request.setUseCaches(false);request.setRequestProperty("Cache-Control","no-cache, no-store");}if(offset>0)request.setRequestProperty("Range","bytes="+offset+"-");
            ScheduledFuture<?> timeout=WATCHDOG.schedule(request::disconnect,remaining,TimeUnit.MILLISECONDS);
            try{
                int status=request.getResponseCode();
                evidence.status(status);
                evidence.phase("headers");
                if(status==301||status==302||status==303||status==307||status==308){evidence.redirects++;String next=request.getHeaderField("Location");if(next==null)throw new IOException("Missing redirect");url=new URL(url,next);request.disconnect();continue;}
                if(status!=200&&status!=206&&status!=416)throw new UpdateFailure.HttpException(status);
                String encoding=request.getHeaderField("Content-Encoding");if(encoding!=null&&!encoding.equalsIgnoreCase("identity"))throw new IOException("Encoded update body");
                if(status==416)return new Response(request,new ByteArrayInputStream(new byte[0]),status);
                evidence.phase("first_byte");InputStream stream=request.getInputStream();int first=stream.read();alive();
                if(first<0||clock.now()>end){stream.close();throw new SocketTimeoutException("No update data");}
                request.setReadTimeout(8000);
                return new Response(request,new SequenceInputStream(new ByteArrayInputStream(new byte[]{(byte)first}),stream),status);
            }catch(IOException e){request.disconnect();throw e;}
            catch(RuntimeException e){request.disconnect();throw UpdateFailure.connectionFailure(e);}
            finally{timeout.cancel(false);}
        }
        throw new IOException("Too many update redirects");
    }
    interface ManifestConsumer {long verified(byte[] data,int source)throws IOException;}
    void manifest(int preferred,ManifestConsumer consumer)throws IOException{
        final String diagnosticRequest=NetworkEvidence.request();
        boolean diagnosticVerified=false;
        // A deadline stops further requests; only explicit cancellation discards a verified result.
        ScheduledFuture<?> deadline=WATCHDOG.schedule(this::expire,40000,TimeUnit.MILLISECONDS);
        try{
            long end=clock.now()+40000;List<Integer> order=sources(preferred);
            // Both GitHub-hosted fallbacks follow every mirror, with Raw last.
            order.add(order.size()-1,-1);
            IOException last=null;byte[] best=null;long revision=-1;int bestSource=-1,verified=0;boolean unpublished=false;
            String nonce="?quietCheck="+System.currentTimeMillis();
            for(int requestIndex=0;requestIndex<order.size()*(android4&&!independent?2:1);requestIndex++){
                int source=order.get(requestIndex%order.size());boolean modern=android4&&requestIndex<order.size();
                String feed=independent?"updates-independent.json":modern?"updates-v2.json":"updates.json";
                String origin=RAW.replace("updates.json",feed);
                String endpoint=source<0?PAGES.replace("updates.json",feed):address(source,origin);
                alive();if(expired||clock.now()>=end)break;String stage="connect";
                NetworkEvidence evidence=new NetworkEvidence(diagnosticRequest,modern?"manifest_v2":"manifest_legacy",sourceName(source));boolean complete=false;
                try(Response response=connect(endpoint+nonce,0,end-clock.now(),true,evidence)){
                    if(response.status!=200)throw new UpdateFailure.HttpException(response.status);
                    stage="read";evidence.phase(stage);
                    ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
                    while((count=response.input.read(buffer))!=-1){evidence.add(count);alive();if(expired||clock.now()>=end)throw new SocketTimeoutException();if(output.size()+count>UpdateManifest.MAX_BYTES)throw new IOException("Update manifest too large");output.write(buffer,0,count);}
                    stage="verify";evidence.phase(stage);byte[] bytes=output.toByteArray();long candidate;
                    try{candidate=consumer.verified(bytes,source);}catch(IOException e){throw new UpdateFailure.ManifestException(e);}verified++;
                    DiagnosticLog.info("update.source.verified","source="+sourceName(source)+" revision="+candidate);
                    complete=true;
                    evidence.verified=true;evidence.phase("close");
                    if(candidate>revision){best=bytes;revision=candidate;bestSource=source;}
                    // Compare two independently fetched signed manifests instead of accepting a stale first reply.
                    if(verified>=2)break;
                }catch(IOException e){complete=false;last=e;if(independent&&source==SOURCES.length-1&&e instanceof UpdateFailure.HttpException&&((UpdateFailure.HttpException)e).status==404)unpublished=true;failed(source,stage,e);evidence.finish("failed",e);}
                catch(RuntimeException e){complete=false;evidence.finish("failed",e);throw e;}
                finally{if(complete)evidence.finish("verified",null);}
            }
            alive();if(best!=null){consumer.verified(best,bestSource);diagnosticVerified=true;DiagnosticLog.info("network.request.result","request="+diagnosticRequest+" kind=manifest selectedSource="+sourceName(bestSource)+" outcome=verified revision="+revision);return;}
            if(unpublished)throw new UpdateFailure.UnpublishedException();
            if(expired||clock.now()>=end){SocketTimeoutException timeout=new SocketTimeoutException("Update check timed out");timeout.initCause(last);throw timeout;}
            throw new IOException("All update manifest sources failed",last);
        }finally{deadline.cancel(false);active=null;if(!diagnosticVerified)DiagnosticLog.info("network.request.result","request="+diagnosticRequest+" kind=manifest outcome=not_verified cancelled="+cancelled+" expired="+expired);}
    }
    int download(String origin,long total,String sha,File target,int preferred,Progress progress)throws IOException{
        final String diagnosticRequest=NetworkEvidence.request();
        boolean diagnosticVerified=false;
        try{
        IOException last=null;List<Integer> order=sources(preferred);
        for(int index=0;index<order.size();index++){
            alive();int source=order.get(index);long offset=target.isFile()?target.length():0;
            if(offset==total){
                try{verify(target,total,sha);diagnosticVerified=true;DiagnosticLog.info("network.request.result","request="+diagnosticRequest+" kind=apk outcome=verified_cached receivedBytes=0 apkSha256="+sha);return source;}
                catch(IntegrityException corrupt){try(RandomAccessFile file=new RandomAccessFile(target,"rw")){file.setLength(0);}offset=0;}
            }
            if(offset>total)try(RandomAccessFile file=new RandomAccessFile(target,"rw")){file.setLength(0);offset=0;}
            if(index>0)progress.changed(offset,total,true);
            NetworkEvidence evidence=new NetworkEvidence(diagnosticRequest,"apk",sourceName(source));
            try(Response response=connect(address(source,origin),offset,8000,false,evidence)){
                evidence.phase("headers");
                long expected;
                if(response.status==416){if(target.length()==total){evidence.phase("verify");verify(target,total,sha);diagnosticVerified=true;evidence.finish("verified_cached_range",null);return source;}throw new IOException("Invalid update range");}
                if(response.status==206){expected=rangeLength(response.request.getHeaderField("Content-Range"),offset,total);}
                else{offset=0;expected=total;}
                String type=response.request.getContentType();if(type!=null&&(type.contains("text/")||type.contains("html")))throw new IOException("Not an APK response");
                String length=response.request.getHeaderField("Content-Length");if(length!=null&&parseLength(length)!=expected)throw new IOException("Wrong update response length");
                UpdatePolicy.Speed speed=new UpdatePolicy.Speed(clock.now(),offset);long received=0;
                evidence.phase("read");
                try(RandomAccessFile file=new RandomAccessFile(target,"rw")){
                    if(offset==0)file.setLength(0);file.seek(offset);progress.changed(offset,total,false);
                    byte[] buffer=new byte[16384];int count;
                    while((count=response.input.read(buffer))!=-1){
                        evidence.add(count);alive();received+=count;if(received>expected)throw new IOException("APK exceeds signed size");file.write(buffer,0,count);offset+=count;
                        progress.changed(offset,total,false);
                        if(speed.switchSource(clock.now(),offset,total)&&index+1<order.size())throw new IOException("Slow update source");
                    }
                    file.getFD().sync();
                }
                if(received!=expected||target.length()!=total)throw new IOException("Incomplete APK");
            }catch(IOException e){evidence.finish("failed",e);alive();last=e;failed(source,"download",e);continue;}
            catch(RuntimeException e){evidence.finish("failed",e);throw e;}
            try{evidence.phase("verify");verify(target,total,sha);diagnosticVerified=true;evidence.finish("verified",null);DiagnosticLog.info("network.request.result","request="+diagnosticRequest+" kind=apk selectedSource="+sourceName(source)+" outcome=verified apkSha256="+sha);return source;}
            catch(IntegrityException corrupt){
                failed(source,"integrity",corrupt);
                evidence.finish("integrity_failed",corrupt);
                // Another mirror can retry the same signed artifact; never resume a corrupt complete file.
                if(index+1==order.size())throw corrupt;last=corrupt;
                try(RandomAccessFile file=new RandomAccessFile(target,"rw")){file.setLength(0);}
            }
            catch(IOException failure){evidence.finish("failed",failure);throw failure;}
            catch(RuntimeException failure){evidence.finish("failed",failure);throw failure;}
        }
        alive();throw new IOException("All APK sources failed",last);
        }finally{if(!diagnosticVerified)DiagnosticLog.info("network.request.result","request="+diagnosticRequest+" kind=apk outcome=not_verified cancelled="+cancelled);}
    }
    static long rangeLength(String header,long offset,long total)throws IOException{
        Matcher match=Pattern.compile("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matcher(header==null?"":header);
        if(!match.matches())throw new IOException("Missing Content-Range");
        long start=parseLength(match.group(1)),end=parseLength(match.group(2)),size=parseLength(match.group(3));
        if(start!=offset||end!=total-1||size!=total||end<start)throw new IOException("Mismatched Content-Range");return end-start+1;
    }
    private static long parseLength(String value)throws IOException{try{long n=Long.parseLong(value);if(n<0)throw new IOException("Invalid response length");return n;}catch(NumberFormatException e){throw new IOException("Invalid response length",e);}}
    static final class IntegrityException extends IOException{IntegrityException(){super("APK integrity verification failed");}}
    static void verify(File file,long bytes,String hash)throws IOException{
        if(file.length()!=bytes||!digest(file).equals(hash))throw new IntegrityException();
    }
    static String digest(File file)throws IOException{
        try(InputStream input=new FileInputStream(file)){MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[32768];int n;while((n=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();digest.update(buffer,0,n);}return hex(digest.digest());}
        catch(NoSuchAlgorithmException e){throw new IOException(e);}
    }
    static String hex(byte[] bytes){StringBuilder value=new StringBuilder();for(byte b:bytes)value.append(String.format(Locale.ROOT,"%02x",b&255));return value.toString();}
}
