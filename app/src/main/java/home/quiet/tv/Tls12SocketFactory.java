package home.quiet.tv;

import java.io.IOException;
import java.net.InetAddress;
import javax.net.ssl.*;

/** Enable TLS 1.2 on Android 4.4 without changing trust or hostname verification. */
final class Tls12SocketFactory extends SSLSocketFactory{
    private final SSLSocketFactory delegate;
    Tls12SocketFactory(SSLSocketFactory delegate){this.delegate=delegate;}
    Tls12SocketFactory()throws IOException{try{SSLContext context=SSLContext.getInstance("TLS");context.init(null,null,null);delegate=context.getSocketFactory();}catch(java.security.GeneralSecurityException e){throw new IOException(e);}}
    private java.net.Socket configure(java.net.Socket socket){if(socket instanceof SSLSocket)((SSLSocket)socket).setEnabledProtocols(new String[]{"TLSv1.2"});return socket;}
    @Override public String[] getDefaultCipherSuites(){return delegate.getDefaultCipherSuites();}
    @Override public String[] getSupportedCipherSuites(){return delegate.getSupportedCipherSuites();}
    @Override public java.net.Socket createSocket()throws IOException{return configure(delegate.createSocket());}
    @Override public java.net.Socket createSocket(java.net.Socket s,String h,int p,boolean close)throws IOException{return configure(delegate.createSocket(s,h,p,close));}
    @Override public java.net.Socket createSocket(String h,int p)throws IOException{return configure(delegate.createSocket(h,p));}
    @Override public java.net.Socket createSocket(String h,int p,InetAddress local,int port)throws IOException{return configure(delegate.createSocket(h,p,local,port));}
    @Override public java.net.Socket createSocket(InetAddress h,int p)throws IOException{return configure(delegate.createSocket(h,p));}
    @Override public java.net.Socket createSocket(InetAddress h,int p,InetAddress local,int port)throws IOException{return configure(delegate.createSocket(h,p,local,port));}
}
