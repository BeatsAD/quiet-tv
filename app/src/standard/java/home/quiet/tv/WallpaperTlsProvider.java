package home.quiet.tv;

import java.security.*;
import javax.net.ssl.*;

/** Platform TLS with caller-supplied roots; no global provider changes. */
final class WallpaperTlsProvider {
    static SSLSocketFactory socketFactory(KeyStore roots) throws GeneralSecurityException {
        TrustManagerFactory trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());trust.init(roots);
        SSLContext context=SSLContext.getInstance("TLS");context.init(null,trust.getTrustManagers(),null);
        return context.getSocketFactory();
    }
}
