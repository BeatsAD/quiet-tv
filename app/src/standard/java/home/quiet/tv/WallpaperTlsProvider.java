package home.quiet.tv;

import java.security.*;
import javax.net.ssl.*;

/** The standard build keeps its platform TLS; the API19 connection path is unreachable. */
final class WallpaperTlsProvider {
    static SSLSocketFactory socketFactory(KeyStore roots) throws GeneralSecurityException {
        TrustManagerFactory trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());trust.init(roots);
        SSLContext context=SSLContext.getInstance("TLS");context.init(null,trust.getTrustManagers(),null);
        return context.getSocketFactory();
    }
}
