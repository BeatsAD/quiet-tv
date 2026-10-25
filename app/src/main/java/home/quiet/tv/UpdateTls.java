package home.quiet.tv;

import android.content.Context;
import java.io.*;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;

/** Old-system TLS 1.2 with platform roots and the bundled public DigiCert / ISRG roots. */
final class UpdateTls {
    static SSLSocketFactory create(Context context)throws IOException{
        try{
            TrustManagerFactory platform=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());platform.init((KeyStore)null);
            KeyStore roots=KeyStore.getInstance(KeyStore.getDefaultType());roots.load(null,null);int index=0;
            for(TrustManager manager:platform.getTrustManagers())if(manager instanceof X509TrustManager)
                for(X509Certificate certificate:((X509TrustManager)manager).getAcceptedIssuers())roots.setCertificateEntry("system-"+index++,certificate);
            for(String name:new String[]{"digicert-global-root-g2","isrg-root-x1"})try(InputStream input=context.getAssets().open("certificates/"+name+".pem")){
                roots.setCertificateEntry(name,CertificateFactory.getInstance("X.509").generateCertificate(input));
            }
            return new Tls12SocketFactory(WallpaperTlsProvider.socketFactory(roots));
        }catch(GeneralSecurityException e){throw new IOException("Update TLS initialization failed",e);}
    }
}
