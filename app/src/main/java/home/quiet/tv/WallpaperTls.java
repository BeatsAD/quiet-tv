package home.quiet.tv;

import java.io.*;
import java.net.SocketTimeoutException;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;

/** Connection-local platform roots plus Bing's public root; never changes system providers. */
final class WallpaperTls {
    static SSLSocketFactory socketFactory(InputStream root) throws IOException {
        try{
            TrustManagerFactory platform=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());platform.init((KeyStore)null);
            KeyStore roots=KeyStore.getInstance(KeyStore.getDefaultType());roots.load(null,null);int index=0;
            for(TrustManager manager:platform.getTrustManagers())if(manager instanceof X509TrustManager)
                for(X509Certificate certificate:((X509TrustManager)manager).getAcceptedIssuers())roots.setCertificateEntry("system-"+index++,certificate);
            roots.setCertificateEntry("digicert-global-root-g2",CertificateFactory.getInstance("X.509").generateCertificate(root));
            return new Tls12SocketFactory(WallpaperTlsProvider.socketFactory(roots));
        }catch(GeneralSecurityException e){throw new IOException("Wallpaper certificate initialization failed",e);}
    }
    static String failureCode(Throwable error){
        String result="network";boolean certificate=false;
        for(int n=0;error!=null&&n<8;n++,error=error.getCause()){
            if(error instanceof CertificateExpiredException)return "certificate_expired";
            if(error instanceof CertificateNotYetValidException)return "certificate_not_yet_valid";
            if(error instanceof CertificateException||error instanceof CertPathValidatorException)certificate=true;
            if(error instanceof SSLPeerUnverifiedException)return "peer_verification";
            if(error instanceof SSLHandshakeException||"org.bouncycastle.tls.TlsFatalAlert".equals(error.getClass().getName()))result="tls_handshake";
            else if(error instanceof SSLProtocolException)result="tls_protocol";
            else if(error instanceof SSLException&&"network".equals(result))result="tls";
            else if(error instanceof SocketTimeoutException)result="timeout";
        }
        return certificate?"certificate":result;
    }
}
