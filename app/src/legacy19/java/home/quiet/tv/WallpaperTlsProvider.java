package home.quiet.tv;

import java.security.*;
import javax.net.ssl.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;

/** Pure Java TLS and certificate validation, independent of the API19 crypto engine. */
final class WallpaperTlsProvider {
    static SSLSocketFactory socketFactory(KeyStore roots) throws GeneralSecurityException {
        Provider provider=new BouncyCastleJsseProvider(new BouncyCastleProvider());
        TrustManagerFactory trust=TrustManagerFactory.getInstance("PKIX",provider);trust.init(roots);
        SSLContext context=SSLContext.getInstance("TLS",provider);context.init(null,trust.getTrustManagers(),new SecureRandom());
        return context.getSocketFactory();
    }
}
