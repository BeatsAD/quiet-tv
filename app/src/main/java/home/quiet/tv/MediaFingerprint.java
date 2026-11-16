package home.quiet.tv;

import java.io.*;
import java.security.*;

/** Fingerprint the bytes already read by a transfer, without changing its ownership or reads. */
final class MediaFingerprint extends FilterInputStream {
    private MessageDigest digest;
    private String hex;
    long bytes;
    MediaFingerprint(InputStream input){super(input);try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException|RuntimeException unavailable){}}
    @Override public int read()throws IOException{int value=in.read();if(value>=0){bytes++;try{if(digest!=null)digest.update((byte)value);}catch(RuntimeException unavailable){digest=null;}}return value;}
    @Override public int read(byte[] buffer,int offset,int length)throws IOException{int count=in.read(buffer,offset,length);if(count>0){bytes+=count;try{if(digest!=null)digest.update(buffer,offset,count);}catch(RuntimeException unavailable){digest=null;}}return count;}
    String contentHash(){if(hex==null)try{hex=digest==null?"unknown":UpdateTransfer.hex(digest.digest());}catch(RuntimeException unavailable){hex="unknown";}return hex;}
    String anonymous(){String hash=contentHash();try{return "unknown".equals(hash)?hash:DiagnosticLog.anonymous("media",hash);}catch(RuntimeException unavailable){return "unknown";}}
}
