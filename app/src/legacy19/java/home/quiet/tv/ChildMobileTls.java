package home.quiet.tv;
import java.security.*;
import javax.net.ssl.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;

/** API14–19 must provide TLS1.2 without relying on the firmware SSL engine. */
final class ChildMobileTls {
    static Provider cryptoProvider(){return new BouncyCastleProvider();}
    static SSLServerSocketFactory factory(KeyStore keys,char[] password)throws GeneralSecurityException{
        Provider provider=new BouncyCastleJsseProvider(new BouncyCastleProvider());
        KeyManagerFactory managers=KeyManagerFactory.getInstance("X.509",provider);managers.init(keys,password);
        SSLContext tls=SSLContext.getInstance("TLS",provider);tls.init(managers.getKeyManagers(),null,new SecureRandom());return tls.getServerSocketFactory();
    }
}
