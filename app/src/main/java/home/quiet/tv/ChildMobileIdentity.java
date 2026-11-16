package home.quiet.tv;

import android.content.*;
import android.content.pm.*;
import android.os.Build;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import javax.net.ssl.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.encoders.Base64;
import org.json.*;

/** Private TLS key and QR pin, independent of APK signing and the parent's PIN. */
final class ChildMobileIdentity {
    final JSONObject peer;
    final String tvId,pin;
    final SSLServerSocketFactory factory;
    ChildMobileIdentity(Context context,String host)throws IOException{
        try{
            SharedPreferences preferences=context.getSharedPreferences("child-mobile",0);String id=preferences.getString("tvId","");if(id.isEmpty()){id=UUID.randomUUID().toString();if(!preferences.edit().putString("tvId",id).commit())throw new IOException("电视身份保存失败");}tvId=id;
            String password=preferences.getString("tlsPassword","");if(password.isEmpty()){byte[] random=new byte[32];new SecureRandom().nextBytes(random);password=Base64.toBase64String(random);if(!preferences.edit().putString("tlsPassword",password).commit())throw new IOException("连接密钥保存失败");}
            Provider provider=ChildMobileTls.cryptoProvider();KeyStore keys=provider==null?KeyStore.getInstance("PKCS12"):KeyStore.getInstance("PKCS12",provider);char[] pass=password.toCharArray();androidx.core.util.AtomicFile file=new androidx.core.util.AtomicFile(new File(context.getFilesDir(),"child-mobile-tls.p12"));
            if(file.getBaseFile().exists()){try(InputStream stream=new ByteArrayInputStream(file.readFully())){keys.load(stream,pass);}}else keys.load(null,pass);
            X509Certificate certificate=(X509Certificate)keys.getCertificate("server");boolean matching=certificate!=null&&host.equals(preferences.getString("tlsHost",""));if(matching)try{certificate.checkValidity();}catch(java.security.cert.CertificateException expired){matching=false;}
            if(!matching){KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);KeyPair pair=generator.generateKeyPair();certificate=createCertificate(host,pair,provider);keys.setKeyEntry("server",pair.getPrivate(),pass,new java.security.cert.Certificate[]{certificate});FileOutputStream output=null;try{output=file.startWrite();keys.store(output,pass);file.finishWrite(output);output=null;}finally{if(output!=null)file.failWrite(output);}if(!preferences.edit().putString("tlsHost",host).commit())throw new IOException("连接证书保存失败");}
            pin=ChildMobileProof.digest(certificate.getPublicKey().getEncoded());factory=ChildMobileTls.factory(keys,pass);Arrays.fill(pass,'\0');
            PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),Build.VERSION.SDK_INT>=28?0x08000000:PackageManager.GET_SIGNATURES);android.content.pm.Signature[] signatures=Build.VERSION.SDK_INT>=28?Api28.signatures(info):info.signatures;ArrayList<String> hashes=new ArrayList<>();for(android.content.pm.Signature signature:signatures)hashes.add(ChildMobileProof.digest(signature.toByteArray()));Collections.sort(hashes);peer=new JSONObject().put("packageName",context.getPackageName()).put("uid",android.os.Process.myUid()).put("versionCode",info.versionCode).put("fingerprintSha256",ChildMobileProof.digest(Build.FINGERPRINT.getBytes(Charsets.UTF_8))).put("signerSha256",new JSONArray(hashes));
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("手机助手安全连接准备失败",e);}
    }
    static X509Certificate createCertificate(String host,KeyPair pair,Provider provider)throws Exception{
        long now=System.currentTimeMillis();X500Name name=new X500Name("CN=QuietTV Child Mode");
        JcaX509v3CertificateBuilder builder=new JcaX509v3CertificateBuilder(name,new BigInteger(128,new SecureRandom()),new Date(now-86400000L),new Date(now+365L*86400000L),name,pair.getPublic());
        builder.addExtension(Extension.subjectAlternativeName,false,new GeneralNames(new GeneralName(GeneralName.iPAddress,host)));
        JcaContentSignerBuilder signer=new JcaContentSignerBuilder("SHA256withRSA");JcaX509CertificateConverter converter=new JcaX509CertificateConverter();
        if(provider!=null){signer.setProvider(provider);converter.setProvider(provider);}
        return converter.getCertificate(builder.build(signer.build(pair.getPrivate())));
    }
    void requirePeer(String encoded)throws IOException{try{JSONObject value=new JSONObject(new String(ChildMobileProof.decode(encoded,8192),Charsets.UTF_8));for(String key:new String[]{"packageName","fingerprintSha256"})if(!peer.getString(key).equals(value.getString(key)))throw new IOException("当前电视身份不一致");if(peer.getLong("uid")!=value.getLong("uid")||peer.getLong("versionCode")!=value.getLong("versionCode"))throw new IOException("当前应用或安卓用户不一致");TreeSet<String> expected=new TreeSet<>(),received=new TreeSet<>();JSONArray a=peer.getJSONArray("signerSha256"),b=value.getJSONArray("signerSha256");for(int i=0;i<a.length();i++)expected.add(a.getString(i));for(int i=0;i<b.length();i++)received.add(b.getString(i));if(!expected.equals(received)||b.length()!=received.size())throw new IOException("当前应用签名不一致");}catch(JSONException|NullPointerException e){throw new IOException("请重新核对电视身份",e);}}
    @android.annotation.TargetApi(28) private static final class Api28 {static android.content.pm.Signature[] signatures(PackageInfo info){return info.signingInfo.getApkContentsSigners();}}
}
