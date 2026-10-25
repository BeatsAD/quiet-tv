package home.quiet.tv;

import java.io.*;
import java.net.*;
import java.security.cert.*;
import java.util.*;
import javax.net.ssl.*;

/** Bounded diagnostics only: never include exception messages, URLs or request data. */
final class NetworkDiagnostics {
    static String failureDetails(Throwable error){
        String kind="io",tls="",certificate="";StringBuilder causes=new StringBuilder();
        for(int n=0;error!=null&&n<8;n++,error=error.getCause()){
            if(causes.length()>0)causes.append('>');String type=error.getClass().getSimpleName();causes.append(type.substring(0,Math.min(64,type.length())));
            if(error instanceof CertificateExpiredException)certificate="certificate_expired";
            else if(error instanceof CertificateNotYetValidException)certificate="certificate_not_yet_valid";
            else if((certificate.isEmpty()||"certificate".equals(certificate))&&(error instanceof CertPathValidatorException||error instanceof CertPathBuilderException))certificate="certificate_path";
            else if(certificate.isEmpty()&&error instanceof CertificateException)certificate="certificate";
            if(error instanceof UnknownHostException)kind="dns";
            else if(error instanceof SocketTimeoutException)kind="timeout";
            else if(error instanceof ConnectException)kind="connect";
            else if(error instanceof InterruptedIOException)kind="interrupted_io";
            else if("org.json.JSONException".equals(error.getClass().getName()))kind="payload";
            if(error instanceof SSLPeerUnverifiedException)certificate="peer_verification";
            if(tls.isEmpty()&&(error instanceof SSLHandshakeException||"org.bouncycastle.tls.TlsFatalAlert".equals(error.getClass().getName())))tls="tls_handshake";
            else if(tls.isEmpty()&&error instanceof SSLProtocolException)tls="tls_protocol";
            // Map known protocol tokens to fixed codes; the original text is never retained.
            if(error instanceof SSLException||"org.bouncycastle.tls.TlsFatalAlert".equals(error.getClass().getName())){
                String message=error.getMessage();if(message!=null){
                    String lower=message.toLowerCase(Locale.ROOT);
                    if(lower.contains("protocol_version")||lower.contains("alert protocol version")||lower.contains("unsupported protocol"))tls="tls_version";
                    else if(lower.contains("no shared cipher")||lower.contains("no cipher suites in common"))tls="tls_cipher";
                    else if(lower.contains("unsupported signature algorithm")||lower.contains("no suitable signature"))tls="tls_signature";
                }
            }
        }
        return "kind="+(!certificate.isEmpty()?certificate:!tls.isEmpty()?tls:kind)+" causes="+causes;
    }
    static String tlsDetails(SSLSocketFactory factory) throws IOException {
        try(SSLSocket socket=(SSLSocket)factory.createSocket()){
            return "enabled="+Arrays.toString(socket.getEnabledProtocols())+" supported="+Arrays.toString(socket.getSupportedProtocols())
                +" ciphers="+socket.getEnabledCipherSuites().length;
        }
    }
}
