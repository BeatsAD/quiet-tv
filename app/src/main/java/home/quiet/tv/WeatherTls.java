package home.quiet.tv;

import java.io.*;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

/** Adds the bundled ISRG root to platform roots for this client's connections only. */
final class WeatherTls {
    static SSLSocketFactory socketFactory(InputStream root) throws IOException {
        try{
            TrustManagerFactory platform=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            platform.init((KeyStore)null);
            KeyStore roots=KeyStore.getInstance(KeyStore.getDefaultType());roots.load(null,null);
            int index=0;
            for(TrustManager manager:platform.getTrustManagers())if(manager instanceof X509TrustManager){
                for(X509Certificate certificate:((X509TrustManager)manager).getAcceptedIssuers())
                    roots.setCertificateEntry("system-"+index++,certificate);
            }
            roots.setCertificateEntry("isrg-root-x1",CertificateFactory.getInstance("X.509").generateCertificate(root));
            TrustManagerFactory combined=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            combined.init(roots);
            if(android.os.Build.VERSION.SDK_INT<16)return WallpaperTlsProvider.socketFactory(roots);
            SSLContext tls=SSLContext.getInstance("TLS");tls.init(null,combined.getTrustManagers(),null);
            return tls.getSocketFactory();
        }catch(GeneralSecurityException e){throw new IOException("Weather certificate initialization failed",e);}
    }
}
