package home.quiet.tv;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** One source at a time; only verified Content-Range responses may append. */
final class UpdateTransfer {
    static final String[] SOURCES={"https://ghfast.top/","https://gh-proxy.com/","https://v4.gh-proxy.org/",""};
    static final String PAGES="https://beatsad.github.io/quiet-tv/updates.json";
    static final String RAW="https://raw.githubusercontent.com/BeatsAD/quiet-tv/main/updates/updates.json";
    interface Connection {HttpURLConnection open(URL url)throws IOException;}
    interface Progress {void changed(long bytes,long total,boolean switching);}
    interface Clock {long now();}
    private static final ScheduledExecutorService WATCHDOG=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"QuietTV-update-timeout");t.setDaemon(true);return t;});
    private final Connection factory;private final Clock clock;
    private volatile boolean cancelled;private volatile HttpURLConnection active;
    UpdateTransfer(Connection factory){this(factory,()->System.nanoTime()/1000000);}
    UpdateTransfer(Connection factory,Clock clock){this.factory=factory;this.clock=clock;}
    void cancel(){cancelled=true;HttpURLConnection request=active;if(request!=null)request.disconnect();}
    private void alive()throws InterruptedIOException{if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Update cancelled");}
    static List<Integer> sources(int ignoredPreference){
        // Preserve the agreed priority even when an older client remembered another source.
        List<Integer> result=new ArrayList<>();for(int n=0;n<SOURCES.length;n++)result.add(n);return result;
    }
    private static boolean mirrorHost(String host){
        for(String root:new String[]{"ghfast.top","gh-proxy.com","v4.gh-proxy.org"})if(host.equals(root)||host.endsWith("."+root))return true;return false;
    }
    static String address(int source,String origin){return source<0?PAGES:SOURCES[source]+origin;}
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
    private Response connect(String address,long offset,long budget,boolean fresh)throws IOException{
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
                if(status==301||status==302||status==303||status==307||status==308){String next=request.getHeaderField("Location");if(next==null)throw new IOException("Missing redirect");url=new URL(url,next);request.disconnect();continue;}
                if(status!=200&&status!=206&&status!=416)throw new IOException("Update HTTP "+status);
                String encoding=request.getHeaderField("Content-Encoding");if(encoding!=null&&!encoding.equalsIgnoreCase("identity"))throw new IOException("Encoded update body");
                if(status==416)return new Response(request,new ByteArrayInputStream(new byte[0]),status);
                InputStream stream=request.getInputStream();int first=stream.read();alive();
                if(first<0||clock.now()>end){stream.close();throw new SocketTimeoutException("No update data");}
                request.setReadTimeout(8000);
                return new Response(request,new SequenceInputStream(new ByteArrayInputStream(new byte[]{(byte)first}),stream),status);
            }catch(IOException|RuntimeException e){request.disconnect();throw e;}finally{timeout.cancel(false);}
        }
        throw new IOException("Too many update redirects");
    }
    interface ManifestConsumer {long verified(byte[] data,int source)throws IOException;}
    void manifest(int preferred,ManifestConsumer consumer)throws IOException{
        ScheduledFuture<?> deadline=WATCHDOG.schedule(this::cancel,40000,TimeUnit.MILLISECONDS);
        try{
            long end=clock.now()+40000;List<Integer> order=sources(preferred);
            // Both GitHub-hosted fallbacks follow every mirror, with Raw last.
            order.add(order.size()-1,-1);
            IOException last=null;byte[] best=null;long revision=-1;int bestSource=-1,verified=0;
            String nonce="?quietCheck="+System.currentTimeMillis();
            for(int source:order){
                alive();if(clock.now()>=end)break;
                try(Response response=connect(address(source,RAW)+nonce,0,end-clock.now(),true)){
                    if(response.status!=200)throw new IOException("Partial update manifest");
                    ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
                    while((count=response.input.read(buffer))!=-1){alive();if(clock.now()>=end)throw new SocketTimeoutException();if(output.size()+count>UpdateManifest.MAX_BYTES)throw new IOException("Update manifest too large");output.write(buffer,0,count);}
                    byte[] bytes=output.toByteArray();long candidate=consumer.verified(bytes,source);verified++;
                    if(candidate>revision){best=bytes;revision=candidate;bestSource=source;}
                    // Compare two independently fetched signed manifests instead of accepting a stale first reply.
                    if(verified>=2)break;
                }catch(IOException e){last=e;}
            }
            alive();if(best!=null){consumer.verified(best,bestSource);return;}
            throw new IOException("All update manifest sources failed",last);
        }finally{deadline.cancel(false);}
    }
    int download(String origin,long total,String sha,File target,int preferred,Progress progress)throws IOException{
        IOException last=null;List<Integer> order=sources(preferred);
        for(int index=0;index<order.size();index++){
            alive();int source=order.get(index);long offset=target.isFile()?target.length():0;
            if(offset==total){
                try{verify(target,total,sha);return source;}
                catch(IntegrityException corrupt){try(RandomAccessFile file=new RandomAccessFile(target,"rw")){file.setLength(0);}offset=0;}
            }
            if(offset>total)try(RandomAccessFile file=new RandomAccessFile(target,"rw")){file.setLength(0);offset=0;}
            if(index>0)progress.changed(offset,total,true);
            try(Response response=connect(address(source,origin),offset,8000,false)){
                long expected;
                if(response.status==416){if(target.length()==total){verify(target,total,sha);return source;}throw new IOException("Invalid update range");}
                if(response.status==206){expected=rangeLength(response.request.getHeaderField("Content-Range"),offset,total);}
                else{offset=0;expected=total;}
                String type=response.request.getContentType();if(type!=null&&(type.contains("text/")||type.contains("html")))throw new IOException("Not an APK response");
                String length=response.request.getHeaderField("Content-Length");if(length!=null&&parseLength(length)!=expected)throw new IOException("Wrong update response length");
                UpdatePolicy.Speed speed=new UpdatePolicy.Speed(clock.now(),offset);long received=0;
                try(RandomAccessFile file=new RandomAccessFile(target,"rw")){
                    if(offset==0)file.setLength(0);file.seek(offset);progress.changed(offset,total,false);
                    byte[] buffer=new byte[16384];int count;
                    while((count=response.input.read(buffer))!=-1){
                        alive();received+=count;if(received>expected)throw new IOException("APK exceeds signed size");file.write(buffer,0,count);offset+=count;
                        progress.changed(offset,total,false);
                        if(speed.switchSource(clock.now(),offset,total)&&index+1<order.size())throw new IOException("Slow update source");
                    }
                    file.getFD().sync();
                }
                if(received!=expected||target.length()!=total)throw new IOException("Incomplete APK");
            }catch(IOException e){alive();last=e;continue;}
            try{verify(target,total,sha);return source;}
            catch(IntegrityException corrupt){
                // Another mirror can retry the same signed artifact; never resume a corrupt complete file.
                if(index+1==order.size())throw corrupt;last=corrupt;
                try(RandomAccessFile file=new RandomAccessFile(target,"rw")){file.setLength(0);}
            }
        }
        alive();throw new IOException("All APK sources failed",last);
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
