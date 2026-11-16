package home.quiet.tv;

import java.io.IOException;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import org.bouncycastle.util.encoders.Base64;
import org.json.*;

/** Exact binding purpose and proof; a child binding never grants general settings or ADB access. */
final class ChildMobileProof {
    static final String[] SCOPES={"child.read","child.trigger","child.write"};
    static String digest(byte[] value){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value);StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    static byte[] decode(String encoded,int maximum)throws IOException{try{if(encoded==null||encoded.length()>maximum*2)throw new IOException("授权数据过长");byte[] value=Base64.decode(encoded);if(value.length>maximum)throw new IOException("授权数据过长");return value;}catch(RuntimeException e){throw new IOException("授权数据格式无效",e);}}
    static String verify(JSONObject body,String session,String television,int uid,String request)throws IOException{
        try{
            if(!session.equals(body.getString("sessionId"))||!"child".equals(body.getString("audience")))throw new IOException("授权用途或会话不一致");JSONArray raw=body.getJSONArray("requestedScopes");TreeSet<String> scopes=new TreeSet<>();for(int i=0;i<raw.length();i++)scopes.add(raw.getString(i));if(raw.length()!=SCOPES.length||!scopes.equals(new TreeSet<>(Arrays.asList(SCOPES))))throw new IOException("儿童模式授权范围不一致");
            byte[] encoded=decode(body.getString("clientPublicKey"),2048);PublicKey key=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(encoded));if(!(key instanceof java.security.interfaces.ECPublicKey)||((java.security.interfaces.ECPublicKey)key).getParams().getCurve().getField().getFieldSize()!=256)throw new IOException("手机身份密钥类型无效");String fingerprint=digest(encoded);String challenge="quiettv-mobile-binding-v1\nchild\n"+session+"\n"+television+"\n"+uid+"\n"+fingerprint+"\nchild.read,child.trigger,child.write\n"+request;Signature signature=Signature.getInstance("SHA256withECDSA");signature.initVerify(key);signature.update(challenge.getBytes(Charsets.UTF_8));if(!signature.verify(decode(body.getString("clientProof"),1024)))throw new IOException("手机身份签名未通过");return fingerprint;
        }catch(JSONException|GeneralSecurityException e){throw new IOException("手机授权证明无效",e);}
    }
}
