package home.quiet.tv;

import java.io.*;
import java.net.*;
import java.nio.channels.SocketChannel;
import javax.net.ssl.*;

/** Android 4.0-4.3 forces non-platform sockets to SSLv3 after the factory returns. */
final class UpdateTlsSocketFactory extends SSLSocketFactory {
    private final SSLSocketFactory delegate;
    UpdateTlsSocketFactory(SSLSocketFactory delegate){this.delegate=delegate;}
    private Socket protect(Socket socket){return new ProtectedSocket((SSLSocket)socket);}
    @Override public String[] getDefaultCipherSuites(){return delegate.getDefaultCipherSuites();}
    @Override public String[] getSupportedCipherSuites(){return delegate.getSupportedCipherSuites();}
    @Override public Socket createSocket()throws IOException{return protect(delegate.createSocket());}
    @Override public Socket createSocket(Socket socket,String host,int port,boolean close)throws IOException{return protect(delegate.createSocket(socket,host,port,close));}
    @Override public Socket createSocket(String host,int port)throws IOException{return protect(delegate.createSocket(host,port));}
    @Override public Socket createSocket(String host,int port,InetAddress local,int localPort)throws IOException{return protect(delegate.createSocket(host,port,local,localPort));}
    @Override public Socket createSocket(InetAddress host,int port)throws IOException{return protect(delegate.createSocket(host,port));}
    @Override public Socket createSocket(InetAddress host,int port,InetAddress local,int localPort)throws IOException{return protect(delegate.createSocket(host,port,local,localPort));}

    private static final class ProtectedSocket extends SSLSocket {
        private final SSLSocket delegate;
        ProtectedSocket(SSLSocket delegate){this.delegate=delegate;}
        @Override public void setEnabledProtocols(String[] protocols){
            // Keep the TLS 1.2 configuration supplied by UpdateTls, rather than enable SSLv3.
            if(protocols!=null&&protocols.length==1&&"SSLv3".equals(protocols[0]))return;
            delegate.setEnabledProtocols(protocols);
        }
        @Override public String[] getEnabledProtocols(){return delegate.getEnabledProtocols();}
        @Override public String[] getSupportedProtocols(){return delegate.getSupportedProtocols();}
        @Override public String[] getSupportedCipherSuites(){return delegate.getSupportedCipherSuites();}
        @Override public String[] getEnabledCipherSuites(){return delegate.getEnabledCipherSuites();}
        @Override public void setEnabledCipherSuites(String[] suites){delegate.setEnabledCipherSuites(suites);}
        @Override public SSLSession getSession(){return delegate.getSession();}
        @Override public void addHandshakeCompletedListener(HandshakeCompletedListener listener){delegate.addHandshakeCompletedListener(listener);}
        @Override public void removeHandshakeCompletedListener(HandshakeCompletedListener listener){delegate.removeHandshakeCompletedListener(listener);}
        @Override public void startHandshake()throws IOException{delegate.startHandshake();}
        @Override public void setUseClientMode(boolean mode){delegate.setUseClientMode(mode);}
        @Override public boolean getUseClientMode(){return delegate.getUseClientMode();}
        @Override public void setNeedClientAuth(boolean need){delegate.setNeedClientAuth(need);}
        @Override public boolean getNeedClientAuth(){return delegate.getNeedClientAuth();}
        @Override public void setWantClientAuth(boolean want){delegate.setWantClientAuth(want);}
        @Override public boolean getWantClientAuth(){return delegate.getWantClientAuth();}
        @Override public void setEnableSessionCreation(boolean enabled){delegate.setEnableSessionCreation(enabled);}
        @Override public boolean getEnableSessionCreation(){return delegate.getEnableSessionCreation();}
        @Override public SSLParameters getSSLParameters(){return delegate.getSSLParameters();}
        @Override public void setSSLParameters(SSLParameters parameters){delegate.setSSLParameters(parameters);}
        @Override public void connect(SocketAddress endpoint)throws IOException{delegate.connect(endpoint);}
        @Override public void connect(SocketAddress endpoint,int timeout)throws IOException{delegate.connect(endpoint,timeout);}
        @Override public void bind(SocketAddress address)throws IOException{delegate.bind(address);}
        @Override public InetAddress getInetAddress(){return delegate.getInetAddress();}
        @Override public InetAddress getLocalAddress(){return delegate.getLocalAddress();}
        @Override public int getPort(){return delegate.getPort();}
        @Override public int getLocalPort(){return delegate.getLocalPort();}
        @Override public SocketAddress getRemoteSocketAddress(){return delegate.getRemoteSocketAddress();}
        @Override public SocketAddress getLocalSocketAddress(){return delegate.getLocalSocketAddress();}
        @Override public SocketChannel getChannel(){return delegate.getChannel();}
        @Override public InputStream getInputStream()throws IOException{return delegate.getInputStream();}
        @Override public OutputStream getOutputStream()throws IOException{return delegate.getOutputStream();}
        @Override public void setTcpNoDelay(boolean enabled)throws SocketException{delegate.setTcpNoDelay(enabled);}
        @Override public boolean getTcpNoDelay()throws SocketException{return delegate.getTcpNoDelay();}
        @Override public void setSoLinger(boolean enabled,int linger)throws SocketException{delegate.setSoLinger(enabled,linger);}
        @Override public int getSoLinger()throws SocketException{return delegate.getSoLinger();}
        @Override public void sendUrgentData(int data)throws IOException{delegate.sendUrgentData(data);}
        @Override public void setOOBInline(boolean enabled)throws SocketException{delegate.setOOBInline(enabled);}
        @Override public boolean getOOBInline()throws SocketException{return delegate.getOOBInline();}
        @Override public void setSoTimeout(int timeout)throws SocketException{delegate.setSoTimeout(timeout);}
        @Override public int getSoTimeout()throws SocketException{return delegate.getSoTimeout();}
        @Override public void setSendBufferSize(int size)throws SocketException{delegate.setSendBufferSize(size);}
        @Override public int getSendBufferSize()throws SocketException{return delegate.getSendBufferSize();}
        @Override public void setReceiveBufferSize(int size)throws SocketException{delegate.setReceiveBufferSize(size);}
        @Override public int getReceiveBufferSize()throws SocketException{return delegate.getReceiveBufferSize();}
        @Override public void setKeepAlive(boolean enabled)throws SocketException{delegate.setKeepAlive(enabled);}
        @Override public boolean getKeepAlive()throws SocketException{return delegate.getKeepAlive();}
        @Override public void setTrafficClass(int value)throws SocketException{delegate.setTrafficClass(value);}
        @Override public int getTrafficClass()throws SocketException{return delegate.getTrafficClass();}
        @Override public void setReuseAddress(boolean enabled)throws SocketException{delegate.setReuseAddress(enabled);}
        @Override public boolean getReuseAddress()throws SocketException{return delegate.getReuseAddress();}
        @Override public void close()throws IOException{delegate.close();}
        @Override public void shutdownInput()throws IOException{delegate.shutdownInput();}
        @Override public void shutdownOutput()throws IOException{delegate.shutdownOutput();}
        @Override public boolean isConnected(){return delegate.isConnected();}
        @Override public boolean isBound(){return delegate.isBound();}
        @Override public boolean isClosed(){return delegate.isClosed();}
        @Override public boolean isInputShutdown(){return delegate.isInputShutdown();}
        @Override public boolean isOutputShutdown(){return delegate.isOutputShutdown();}
        @Override public void setPerformancePreferences(int connection,int latency,int bandwidth){delegate.setPerformancePreferences(connection,latency,bandwidth);}
        @Override public String toString(){return delegate.toString();}
    }
}
