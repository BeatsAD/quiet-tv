package home.quiet.tv;

import org.junit.Test;
import static org.junit.Assert.*;
import java.net.*;
import java.util.concurrent.*;

public class PairingTransportTest {
    @Test public void cancellationUnblocksAHandshakeWaitingForPeer() throws Exception {
        try(ServerSocket daemon=new ServerSocket(0,1,InetAddress.getLoopbackAddress());PairingTransport proxy=new PairingTransport("127.0.0.1",daemon.getLocalPort(),2000);Socket client=new Socket("127.0.0.1",proxy.port());Socket remote=daemon.accept()){
            client.setSoTimeout(1000);remote.setSoTimeout(1000);client.getOutputStream().write(42);assertEquals(42,remote.getInputStream().read());
            proxy.close();assertEquals(-1,client.getInputStream().read());assertEquals(-1,remote.getInputStream().read());
        }
    }
    @Test public void deadlineClosesSilentPeerWithoutRetry() throws Exception {
        try(ServerSocket daemon=new ServerSocket(0,1,InetAddress.getLoopbackAddress());PairingTransport proxy=new PairingTransport("127.0.0.1",daemon.getLocalPort(),250);Socket client=new Socket("127.0.0.1",proxy.port());Socket remote=daemon.accept()){
            client.setSoTimeout(1500);assertEquals(-1,client.getInputStream().read());
        }
    }
}
