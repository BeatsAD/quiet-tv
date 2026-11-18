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
    private final Storage storage;private String name,link="";private final Set<String> browsers=new LinkedHashSet<>();
    PhonePairing(Storage storage){this.storage=storage;name=storage.get("hostname");for(String value:storage.get("browser").split("\\n")){if(value.matches("[a-f0-9]{64}"))browsers.add(value);else if(value.matches("link=[a-f0-9]{64}"))link=value.substring(5);}}
    synchronized String hostname()throws IOException {
        if(name.isEmpty()){String next="quiettv-"+secret().substring(0,12);save("hostname",next);name=next;}
        return name+".local";
    }
    synchronized boolean paired(){return !browsers.isEmpty();}
    synchronized boolean accepts(String credential){
        if(credential==null||!CREDENTIAL.matcher(credential).matches())return false;
        byte[] digest=hash(credential).getBytes(Charsets.UTF_8);boolean accepted=false;
        for(String browser:browsers)accepted|=MessageDigest.isEqual(digest,browser.getBytes(Charsets.UTF_8));return accepted;
    }
    synchronized String connectionKey()throws IOException {if(link.isEmpty()){String next=secret();saveState(next,browsers);link=next;}return link;}
    synchronized boolean acceptsConnectionKey(String key){return key!=null&&key.matches("[a-f0-9]{64}")&&!link.isEmpty()&&MessageDigest.isEqual(key.getBytes(Charsets.UTF_8),link.getBytes(Charsets.UTF_8));}
    synchronized String pair()throws IOException {
        String credential=secret(),next=hash(credential);Set<String> updated=new LinkedHashSet<>(browsers);updated.add(next);
        saveState(link,updated);browsers.add(next);return credential;
    }
    synchronized void revoke()throws IOException {String next=secret();saveState(next,Collections.emptySet());link=next;browsers.clear();}
    private void saveState(String key,Set<String> values)throws IOException {StringBuilder saved=new StringBuilder();if(!key.isEmpty())saved.append("link=").append(key);for(String browser:values){if(saved.length()>0)saved.append('\n');saved.append(browser);}save("browser",saved.toString());}
    private void save(String key,String value)throws IOException {if(!storage.put(key,value))throw new IOException("手机配对信息保存失败，请重试");}
    static String secret(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);StringBuilder value=new StringBuilder();for(byte b:bytes)value.append(String.format(Locale.ROOT,"%02x",b&255));return value.toString();}
    private static String hash(String text){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(Charsets.UTF_8));char[] value=new char[bytes.length*2];String digits="0123456789abcdef";for(int i=0;i<bytes.length;i++){value[i*2]=digits.charAt((bytes[i]&255)>>>4);value[i*2+1]=digits.charAt(bytes[i]&15);}return new String(value);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
