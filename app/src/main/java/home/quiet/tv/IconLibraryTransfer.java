package home.quiet.tv;

import java.io.*;
import java.net.*;

/** Small bounded image requests; invalid mirror responses fall through to the next source. */
final class IconLibraryTransfer {
    interface Check {void verify(byte[] bytes)throws IOException;}
    interface Active {boolean valid();}
    static final class Request {
        private final Active authority;
        private volatile boolean cancelled;
        private volatile HttpURLConnection connection;
        Request(){this(null);}
        Request(Active authority){this.authority=authority;}
        void check()throws InterruptedIOException{if(cancelled||Thread.currentThread().isInterrupted()||authority!=null&&!authority.valid())throw new InterruptedIOException("图标下载已取消");}
        void cancel(){cancelled=true;HttpURLConnection value=connection;if(value!=null)value.disconnect();}
        void attach(HttpURLConnection value)throws IOException{connection=value;try{check();}catch(IOException e){release(value);throw e;}}
        void release(HttpURLConnection value){if(connection==value)connection=null;value.disconnect();}
    }
    private final UpdateTransfer.Connection factory;
    IconLibraryTransfer(UpdateTransfer.Connection factory){this.factory=factory;}
    byte[] fetch(String path,int limit,Check validator,Request active)throws IOException{
        IOException last=null;int source=0;
        for(String address:IconLibrarySources.addresses(path)){
            active.check();int selected=source++;String stage="network";
            try{
                byte[] bytes=read(address,limit,active);active.check();stage="verify";validator.verify(bytes);return bytes;
            }catch(InterruptedIOException failure){active.check();DiagnosticLog.info("icons.source.failed","source="+selected+" stage="+stage+" "+NetworkDiagnostics.failureDetails(failure));last=failure;}
            catch(IOException failure){active.check();DiagnosticLog.info("icons.source.failed","source="+selected+" stage="+stage+" "+NetworkDiagnostics.failureDetails(failure));last=failure;}
        }
        throw new IOException("图标下载失败，请重试；已应用图标不受影响",last);
    }
    private byte[] read(String address,int limit,Request active)throws IOException{
        URL url=new URL(address);boolean mirror=IconLibrarySources.mirrored(url);long deadline=System.nanoTime()+12_000_000_000L;
        for(int redirect=0;redirect<=4;redirect++){
            active.check();
            if(!UpdateTransfer.allowed(url)||mirror&&!IconLibrarySources.mirrored(url))throw new IOException("图标下载地址无效");
            HttpURLConnection connection=factory.open(url);active.attach(connection);
            try{
                connection.setConnectTimeout(2500);connection.setReadTimeout(3000);connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept-Encoding","identity");connection.setRequestProperty("User-Agent","QuietTV-Icons/1");
                int status=connection.getResponseCode();
                if(status==301||status==302||status==303||status==307||status==308){
                    String location=connection.getHeaderField("Location");if(location==null)throw new IOException("图标跳转无效");url=new URL(url,location);continue;
                }
                if(status!=200)throw new IOException("图标源暂不可用");
                String type=connection.getContentType();if(type!=null&&type.toLowerCase(java.util.Locale.ROOT).contains("text/html"))throw new IOException("图标源返回了网页");
                long size=connection.getContentLength();if(size>limit)throw new IOException("图标资源过大");
                try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[8192];int count;
                    while((count=input.read(buffer))!=-1){active.check();if(System.nanoTime()>deadline)throw new SocketTimeoutException("图标读取超时");if(output.size()+count>limit)throw new IOException("图标资源过大");output.write(buffer,0,count);}
                    if(output.size()==0)throw new IOException("图标资源为空");return output.toByteArray();
                }
            }finally{active.release(connection);}
        }
        throw new IOException("图标跳转过多");
    }
}
