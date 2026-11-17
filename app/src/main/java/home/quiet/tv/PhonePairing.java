package home.quiet.tv;

import java.io.IOException;
import java.security.*;
import java.util.Locale;

/** One trusted browser, independent of short-lived HTTP connections. Never placed in backups. */
final class PhonePairing {
    interface Storage {
        String get(String key);
        boolean put(String key,String value);
    }
    private final Storage storage;private String name,browser;
    PhonePairing(Storage storage){this.storage=storage;name=storage.get("hostname");browser=storage.get("browser");}
    synchronized String hostname()throws IOException {
        if(name.isEmpty()){String next="quiettv-"+secret().substring(0,12);save("hostname",next);name=next;}
        return name+".local";
    }
    synchronized boolean paired(){return !browser.isEmpty();}
    synchronized boolean accepts(String credential){
        return credential!=null&&credential.matches("[a-f0-9]{64}")&&MessageDigest.isEqual(hash(credential).getBytes(Charsets.UTF_8),browser.getBytes(Charsets.UTF_8));
    }
    synchronized String pair()throws IOException {
        if(paired())throw new IOException("已有配对手机，请先在电视解除配对");
        String credential=secret();String next=hash(credential);save("browser",next);browser=next;return credential;
    }
    synchronized void revoke()throws IOException {save("browser","");browser="";}
    private void save(String key,String value)throws IOException {if(!storage.put(key,value))throw new IOException("手机配对信息保存失败，请重试");}
    static String secret(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);StringBuilder value=new StringBuilder();for(byte b:bytes)value.append(String.format(Locale.ROOT,"%02x",b&255));return value.toString();}
    private static String hash(String text){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(Charsets.UTF_8));StringBuilder value=new StringBuilder();for(byte b:bytes)value.append(String.format(Locale.ROOT,"%02x",b&255));return value.toString();}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
