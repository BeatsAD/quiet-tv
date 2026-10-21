package home.quiet.tv;

import java.io.IOException;
import java.security.KeyStore;
import java.util.concurrent.atomic.AtomicInteger;

/** Android 4.4 uses traditional TCP ADB; no TLS/SPAKE2 provider is packaged. */
final class WirelessPairing {
    static boolean available(){return false;}
    void start(int port,String code,KeyStore keys,AtomicInteger generation,int attempt,AdbController.Status status) throws IOException {
        throw new IOException("Android 4.4 兼容版仅支持传统 TCP ADB");
    }
    void close(){}
}
