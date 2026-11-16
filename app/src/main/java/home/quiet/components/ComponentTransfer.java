package home.quiet.components;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Transport changes sources; signed manifest and archive verification remain mandatory. */
final class ComponentTransfer {
    interface Connection {HttpURLConnection open(URL url)throws IOException;}
    interface Clock {long now();}
    interface Verifier {long verify(byte[] data)throws IOException;}
    interface Listener {
        default void source(String stage,String name,int attempt,boolean switching){}
        default void progress(long bytes,long total){}
        default void failed(String stage,String name,IOException error){}
    }
    private static final ScheduledExecutorService WATCHDOG=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"QuietTV-component-timeout");t.setDaemon(true);return t;});
    private final Connection factory;private final Clock clock;private final ComponentFiles.Active check;private final Listener listener;
    private volatile boolean cancelled;private volatile HttpURLConnection active;
    ComponentTransfer(ComponentFiles.Active check,Listener listener){this(url->(HttpURLConnection)url.openConnection(),()->System.nanoTime()/1000000,check,listener);}
    ComponentTransfer(Connection factory,Clock clock,ComponentFiles.Active check,Listener listener){this.factory=factory;this.clock=clock;this.check=check;this.listener=listener;}
    void cancel(){cancelled=true;HttpURLConnection request=active;if(request!=null)request.disconnect();}
    private void alive()throws IOException{if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("下载已取消");check.check();}
    private static String name(int source){String prefix=DownloadSources.values()[source];return prefix.isEmpty()?"github_direct":prefix.substring(8,prefix.length()-1);}
    private static boolean allowed(URL url){String host=url.getHost().toLowerCase(Locale.ROOT);return "https".equals(url.getProtocol())&&url.getUserInfo()==null&&(url.getPort()==-1||url.getPort()==443)&&url.getRef()==null&&(DownloadSources.mirror(host)||Arrays.asList("github.com","release-assets.githubusercontent.com","objects.githubusercontent.com").contains(host));}
    static final class HttpFailure extends IOException {final int status;HttpFailure(int status){super("HTTP "+status);this.status=status;}}
    private static final class VerificationFailure extends IOException {VerificationFailure(IOException cause){super("组件清单校验失败",cause);}}
    private static final class Response implements Closeable {
        final HttpURLConnection request;final InputStream input;final int status;
        Response(HttpURLConnection request,InputStream input,int status){this.request=request;this.input=input;this.status=status;}
        @Override public void close()throws IOException{try{input.close();}finally{request.disconnect();}}
    }
    private Response connect(String address,long offset,long budget,boolean fresh)throws IOException{
        URL url=new URL(address);boolean mirrored=DownloadSources.mirror(url.getHost().toLowerCase(Locale.ROOT));long end=clock.now()+Math.min(8000,budget);
        for(int redirect=0;redirect<5;redirect++){
            alive();if(!allowed(url)||mirrored&&!DownloadSources.mirror(url.getHost().toLowerCase(Locale.ROOT)))throw new IOException("组件下载重定向来源无效");
            if(clock.now()>=end)throw new SocketTimeoutException("组件连接超时");int remaining=(int)Math.max(1,end-clock.now());
            HttpURLConnection request=factory.open(url);active=request;alive();request.setConnectTimeout(remaining);request.setReadTimeout(remaining);request.setInstanceFollowRedirects(false);
            request.setRequestProperty("Accept-Encoding","identity");request.setRequestProperty("User-Agent","QuietTV-Component/1");
            if(fresh){request.setUseCaches(false);request.setRequestProperty("Cache-Control","no-cache");}if(offset>0)request.setRequestProperty("Range","bytes="+offset+"-");
            ScheduledFuture<?> timeout=WATCHDOG.schedule(request::disconnect,remaining,TimeUnit.MILLISECONDS);
            try{
                int status=request.getResponseCode();alive();
                if(status==301||status==302||status==303||status==307||status==308){String location=request.getHeaderField("Location");if(location==null)throw new IOException("组件下载重定向无效");url=new URL(url,location);request.disconnect();continue;}
                if(status!=200&&status!=206&&status!=416)throw new HttpFailure(status);
                String encoding=request.getHeaderField("Content-Encoding");if(encoding!=null&&!encoding.equalsIgnoreCase("identity"))throw new IOException("组件响应编码无效");
                if(status==416)return new Response(request,new ByteArrayInputStream(new byte[0]),status);
                InputStream input=request.getInputStream();int first=input.read();alive();if(first<0||clock.now()>=end){input.close();throw new SocketTimeoutException("组件响应超时");}
                request.setReadTimeout(8000);return new Response(request,new SequenceInputStream(new ByteArrayInputStream(new byte[]{(byte)first}),input),status);
            }catch(IOException e){request.disconnect();throw e;}
            catch(RuntimeException e){request.disconnect();throw new IOException("组件连接失败",e);}
            finally{timeout.cancel(false);}
        }
        throw new IOException("组件下载重定向过多");
    }
    byte[] manifest(String origin,int limit,Verifier verifier)throws IOException{
        long end=clock.now()+40000,revision=-1;IOException last=null,invalid=null;byte[] best=null;int accepted=0;String[] sources=DownloadSources.values();
        try{
            for(int attempt=0;attempt<sources.length*2&&clock.now()<end;attempt++){
                alive();int source=attempt%sources.length;String name=name(source);listener.source("manifest",name,attempt,attempt>0);
                try(Response response=connect(sources[source]+origin,0,end-clock.now(),true)){
                    if(response.status!=200)throw new HttpFailure(response.status);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
                    while((count=response.input.read(buffer))!=-1){alive();if(clock.now()>=end)throw new SocketTimeoutException("组件清单读取超时");if(out.size()+count>limit)throw new IOException("组件清单过大");out.write(buffer,0,count);}
                    byte[] bytes=out.toByteArray();long candidate;try{candidate=verifier.verify(bytes);}catch(IOException e){throw new VerificationFailure(e);}if(candidate>revision){revision=candidate;best=bytes;}if(++accepted>=2)return best;
                }catch(IOException e){alive();last=e;if(e instanceof VerificationFailure)invalid=e;listener.failed(e instanceof VerificationFailure?"verify_manifest":"manifest",name,e);}
            }
            alive();if(best!=null)return best;throw new IOException(invalid==null?"组件清单获取失败，请检查网络后重试":"组件清单校验失败，请稍后重试",invalid==null?last:invalid);
        }finally{active=null;}
    }
    void download(String origin,long total,String sha,File target)throws IOException{
        String[] sources=DownloadSources.values();IOException last=null;
        try{
            for(int attempt=0;attempt<sources.length*2;attempt++){
                alive();int source=attempt%sources.length;String name=name(source);long offset=target.isFile()?target.length():0;
                if(offset==total){if(sha.equals(ComponentFiles.sha256(target))){alive();listener.progress(total,total);return;}truncate(target);offset=0;}
                if(offset>total){truncate(target);offset=0;}
                listener.source("download",name,attempt,attempt>0);listener.progress(offset,total);
                try(Response response=connect(sources[source]+origin,offset,8000,false)){
                    if(response.status==416)throw new IOException("组件续传范围无效");long expected;
                    if(response.status==206)expected=rangeLength(response.request.getHeaderField("Content-Range"),offset,total);else{offset=0;expected=total;}
                    String type=response.request.getContentType();if(type!=null&&(type.contains("text/")||type.contains("html")))throw new IOException("组件响应不是资源包");
                    String length=response.request.getHeaderField("Content-Length");if(length!=null&&number(length)!=expected)throw new IOException("组件下载长度不匹配");long received=0;
                    try(RandomAccessFile file=new RandomAccessFile(target,"rw")){
                        if(offset==0)file.setLength(0);file.seek(offset);listener.progress(offset,total);byte[] buffer=new byte[16384];int count;
                        while((count=response.input.read(buffer))!=-1){alive();if(received+count>expected)throw new IOException("组件下载大小超限");file.write(buffer,0,count);received+=count;offset+=count;listener.progress(offset,total);}file.getFD().sync();
                    }
                    if(received!=expected||target.length()!=total)throw new IOException("组件下载未完成");
                }catch(IOException e){alive();last=e;listener.failed("download",name,e);continue;}
                if(sha.equals(ComponentFiles.sha256(target))){alive();return;}
                last=new IOException("组件文件校验失败");listener.failed("verify",name,last);truncate(target);
            }
            alive();throw new IOException("组件下载未完成，重试将继续下载",last);
        }finally{active=null;}
    }
    private static void truncate(File file)throws IOException{try(RandomAccessFile out=new RandomAccessFile(file,"rw")){out.setLength(0);}}
    static long rangeLength(String header,long offset,long total)throws IOException{
        Matcher m=Pattern.compile("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matcher(header==null?"":header);if(!m.matches())throw new IOException("组件续传范围无效");
        long start=number(m.group(1)),end=number(m.group(2)),size=number(m.group(3));if(start!=offset||end!=total-1||size!=total||end<start)throw new IOException("组件续传范围不匹配");return end-start+1;
    }
    private static long number(String value)throws IOException{try{long n=Long.parseLong(value);if(n<0)throw new IOException("组件响应长度无效");return n;}catch(NumberFormatException e){throw new IOException("组件响应长度无效",e);}}
}
