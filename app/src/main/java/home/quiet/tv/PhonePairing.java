package home.quiet.tv;

import java.io.IOException;
import java.security.*;
import java.util.*;

/** Trusted browsers, independent of short-lived HTTP connections. Never placed in backups. */
final class PhonePairing {
    private static final java.util.regex.Pattern CREDENTIAL=java.util.regex.Pattern.compile("[a-f0-9]{64}");
    interface Storage {
        String get(String key);
        boolean put(String key,String value);
    }
    private static final SecureRandom RANDOM=new SecureRandom();
    private final Storage storage;private final Set<String> browsers=new LinkedHashSet<>();
    PhonePairing(Storage storage){
        this.storage=storage;
        for(String value:storage.get("browser").split("\\n"))if(value.matches("[a-f0-9]{64}"))browsers.add(value);
    }
    synchronized boolean paired(){return !browsers.isEmpty();}
    synchronized boolean accepts(String credential){
        if(credential==null||!CREDENTIAL.matcher(credential).matches())return false;
        byte[] digest=hash(credential).getBytes(Charsets.UTF_8);boolean accepted=false;
        for(String browser:browsers)accepted|=MessageDigest.isEqual(digest,browser.getBytes(Charsets.UTF_8));return accepted;
    }
    synchronized String pair()throws IOException {
        String credential=secret(),digest=hash(credential);
        StringBuilder saved=new StringBuilder();
        for(String browser:browsers)saved.append(browser).append('\n');
        save("browser",saved.append(digest).toString());browsers.add(digest);
        return credential;
    }
    synchronized void revoke()throws IOException {save("browser","");browsers.clear();}
    static String code(){return String.format(Locale.ROOT,"%08d",RANDOM.nextInt(100000000));}
    private void save(String key,String value)throws IOException {if(!storage.put(key,value))throw new IOException("手机配对信息保存失败，请重试");}
    private static String secret(){byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);StringBuilder value=new StringBuilder();for(byte b:bytes)value.append(String.format(Locale.ROOT,"%02x",b&255));return value.toString();}
    private static String hash(String text){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(Charsets.UTF_8));char[] value=new char[bytes.length*2];String digits="0123456789abcdef";for(int i=0;i<bytes.length;i++){value[i*2]=digits.charAt((bytes[i]&255)>>>4);value[i*2+1]=digits.charAt(bytes[i]&15);}return new String(value);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
