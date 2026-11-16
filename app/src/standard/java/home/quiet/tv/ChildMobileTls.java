package home.quiet.tv;
import java.security.*;
import javax.net.ssl.*;

final class ChildMobileTls {
    // API21+ already provides PKCS12, RSA signatures and X.509 certificates.
    static Provider cryptoProvider(){return null;}
    static SSLServerSocketFactory factory(KeyStore keys,char[] password)throws GeneralSecurityException{
        KeyManagerFactory managers=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());managers.init(keys,password);
        SSLContext tls=SSLContext.getInstance("TLS");tls.init(managers.getKeyManagers(),null,new SecureRandom());return tls.getServerSocketFactory();
    }
}
