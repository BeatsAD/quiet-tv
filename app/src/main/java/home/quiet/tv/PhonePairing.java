package home.quiet.tv;

import java.io.IOException;
import java.security.*;
import java.util.*;

/** Trusted browsers, independent of short-lived HTTP connections. Never placed in backups. */
final class PhonePairing {
    interface Storage {
        String get(String key);
        boolean put(String key,String value);
    }
    private final Storage storage;private String name;private final Set<String> browsers=new LinkedHashSet<>();
    PhonePairing(Storage storage){this.storage=storage;name=storage.get("hostname");for(String value:storage.get("browser").split("\\n"))if(value.matches("[a-f0-9]{64}"))browsers.add(value);}
    synchronized String hostname()throws IOException {
        if(name.isEmpty()){String next="quiettv-"+secret().substring(0,12);save("hostname",next);name=next;}
        return name+".local";
    }
    synchronized boolean paired(){return !browsers.isEmpty();}
    synchronized boolean accepts(String credential){
        if(credential==null||!credential.matches("[a-f0-9]{64}"))return false;
        byte[] digest=hash(credential).getBytes(Charsets.UTF_8);boolean accepted=false;
        for(String browser:browsers)accepted|=MessageDigest.isEqual(digest,browser.getBytes(Charsets.UTF_8));return accepted;
    }
    synchronized String pair()throws IOException {
        String credential=secret(),next=hash(credential);Set<String> updated=new LinkedHashSet<>(browsers);updated.add(next);
        StringBuilder saved=new StringBuilder();for(String browser:updated){if(saved.length()>0)saved.append('\n');saved.append(browser);}save("browser",saved.toString());browsers.add(next);return credential;
    }
    synchronized void revoke()throws IOException {save("browser","");browsers.clear();}
    private void save(String key,String value)throws IOException {if(!storage.put(key,value))throw new IOException("手机配对信息保存失败，请重试");}
    static String secret(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);StringBuilder value=new StringBuilder();for(byte b:bytes)value.append(String.format(Locale.ROOT,"%02x",b&255));return value.toString();}
    private static String hash(String text){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(Charsets.UTF_8));StringBuilder value=new StringBuilder();for(byte b:bytes)value.append(String.format(Locale.ROOT,"%02x",b&255));return value.toString();}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
