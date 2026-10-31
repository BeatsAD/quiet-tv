package home.quiet.tv;

import android.content.Context;
import android.os.Build;
import java.io.*;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;

/** API14-25 update-only TLS 1.2 with platform roots and bundled DigiCert / ISRG roots. */
final class UpdateTls {
    static SSLSocketFactory create(Context context)throws IOException{
        try(InputStream digicert=context.getAssets().open("certificates/digicert-global-root-g2.pem");
            InputStream isrg=context.getAssets().open("certificates/isrg-root-x1.pem")){
            return socketFactory(digicert,isrg);
        }
    }
    static SSLSocketFactory socketFactory(InputStream... bundled)throws IOException{
        return socketFactory(Build.VERSION.SDK_INT,bundled);
    }
    static SSLSocketFactory socketFactory(int sdk,InputStream... bundled)throws IOException{
        try{
            TrustManagerFactory platform=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());platform.init((KeyStore)null);
            KeyStore roots=KeyStore.getInstance(KeyStore.getDefaultType());roots.load(null,null);int index=0;
            for(TrustManager manager:platform.getTrustManagers())if(manager instanceof X509TrustManager)
                for(X509Certificate certificate:((X509TrustManager)manager).getAcceptedIssuers())roots.setCertificateEntry("system-"+index++,certificate);
            for(InputStream input:bundled)roots.setCertificateEntry("bundled-"+index++,CertificateFactory.getInstance("X.509").generateCertificate(input));
            SSLSocketFactory factory=new Tls12SocketFactory(WallpaperTlsProvider.socketFactory(roots));
            return sdk<19?new UpdateTlsSocketFactory(factory):factory;
        }catch(GeneralSecurityException e){throw new IOException("Update TLS initialization failed",e);}
    }
}
