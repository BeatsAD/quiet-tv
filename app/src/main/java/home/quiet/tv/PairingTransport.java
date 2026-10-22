package home.quiet.tv;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/** A closeable loopback relay for pairing or an already connected local ADB socket. */
final class PairingTransport implements Closeable {
    private final ServerSocket listener;
    private final Socket upstream;
    private volatile Socket client;
    private volatile boolean closed;
    private final ExecutorService relay=Executors.newFixedThreadPool(2);
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    PairingTransport(String host,int port,int timeoutMillis) throws IOException {
        this(new Socket(),host,port,timeoutMillis);
    }
    // libadb creates its own blocking socket. Reuse the bounded connection without probing adbd twice.
    PairingTransport(Socket connected) throws IOException {this(connected,null,0,0);}
    private PairingTransport(Socket socket,String host,int port,int timeoutMillis) throws IOException {
        upstream=socket;
        try{listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));listener.setSoTimeout(timeoutMillis);}
        catch(IOException failure){upstream.close();throw failure;}
        if(timeoutMillis>0)timer.schedule(this::close,timeoutMillis,TimeUnit.MILLISECONDS);
        relay.execute(()->{try{
            Socket accepted=listener.accept();client=accepted;if(closed){accepted.close();return;}
            if(host!=null)upstream.connect(new InetSocketAddress(host,port),Math.min(2000,timeoutMillis));
            upstream.setSoTimeout(timeoutMillis);accepted.setSoTimeout(timeoutMillis);
            relay.execute(()->copy(upstream,accepted));copy(accepted,upstream);
        }catch(IOException|RejectedExecutionException ignored){close();}});
    }
    int port(){return listener.getLocalPort();}
    private void copy(Socket source,Socket destination){try{byte[] buffer=new byte[8192];int size;InputStream in=source.getInputStream();OutputStream out=destination.getOutputStream();while(!closed && (size=in.read(buffer))!=-1){out.write(buffer,0,size);out.flush();}}catch(IOException ignored){}finally{close();}}
    @Override public void close(){closed=true;try{listener.close();}catch(IOException ignored){}try{upstream.close();}catch(IOException ignored){}Socket socket=client;if(socket!=null)try{socket.close();}catch(IOException ignored){}relay.shutdownNow();timer.shutdownNow();}
}
