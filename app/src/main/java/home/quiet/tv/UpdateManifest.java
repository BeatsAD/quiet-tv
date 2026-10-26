package home.quiet.tv;

import org.json.*;
import org.bouncycastle.util.encoders.Base64;
import java.io.IOException;
import java.net.URI;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** Signed public release data. No download decision is made from a filename. */
final class UpdateManifest {
    static final int MAX_BYTES=256*1024;
    static final String REPOSITORY="https://github.com/BeatsAD/quiet-tv/";
    final long revision;
    final List<Release> releases=new ArrayList<>();
    static final class Release {
        final String version,channel;
        final List<String> features,fixes;
        final List<Artifact> artifacts=new ArrayList<>();
        Release(JSONObject json)throws JSONException,IOException{
            version=json.getString("versionName");channel=json.getString("channel");versionParts(version);
            if(!Arrays.asList("test","stable","experimental").contains(channel))throw new IOException("Invalid update channel");
            features=strings(json.getJSONArray("features"));fixes=strings(json.getJSONArray("fixes"));
            JSONArray values=json.getJSONArray("artifacts");
            for(int n=0;n<values.length();n++)artifacts.add(new Artifact(this,values.getJSONObject(n)));
        }
    }
    static final class Artifact {
        final Release release;
        final String id,batch,pkg,compatibility,homeMode,sha256,url;
        final long code,bytes;
        final int minSdk;
        final List<String> abis,signers;
        Artifact(Release release,JSONObject json)throws JSONException,IOException{
            this.release=release;id=json.getString("artifactId");batch=json.getString("batchId");pkg=json.getString("packageName");
            compatibility=json.getString("compatibility");homeMode=json.getString("homeMode");sha256=json.getString("sha256");url=json.getString("url");
            code=json.getLong("versionCode");bytes=json.getLong("bytes");minSdk=json.getInt("minSdk");
            abis=strings(json.getJSONArray("abis"));signers=strings(json.getJSONArray("signerSha256"));
            if(id.isEmpty()||batch.isEmpty()||code<1||bytes<1||minSdk<19||!sha256.matches("[a-f0-9]{64}")||signers.isEmpty())throw new IOException("Invalid APK metadata");
            for(String signer:signers)if(!signer.matches("[a-f0-9]{64}"))throw new IOException("Invalid APK signer");
            if(!Arrays.asList("home.quiet.tv","com.coocaa.quiettv").contains(pkg)||!Arrays.asList("standard","legacy19").contains(compatibility)
                    ||!Arrays.asList("standard","relay").contains(homeMode))throw new IOException("Unknown APK profile");
            if(("legacy19".equals(compatibility)&&(minSdk!=19||!homeMode.equals("standard")||!abis.isEmpty()))
                    ||("standard".equals(compatibility)&&minSdk<21))throw new IOException("Inconsistent APK profile");
            for(String abi:abis)if(!Arrays.asList("armeabi-v7a","arm64-v8a","x86","x86_64").contains(abi))throw new IOException("Unknown ABI");
            if(!url.startsWith(REPOSITORY+"releases/download/"))throw new IOException("APK outside release repository");
            try{URI uri=new URI(url);if(!"github.com".equals(uri.getHost())||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getQuery()!=null||uri.getPort()!=-1||!uri.getPath().endsWith(".apk")||uri.getPath().contains("/../"))throw new IOException("Invalid APK URL");}
            catch(java.net.URISyntaxException e){throw new IOException("Invalid APK URL",e);}
        }
        String version(){return release.version;}
        boolean matches(Device device){
            return pkg.equals(device.pkg)&&compatibility.equals(device.compatibility)&&homeMode.equals(device.homeMode)&&minSdk<=device.sdk
                    &&(release.channel.equals(device.channel)||("test".equals(device.channel)&&"stable".equals(release.channel)))
                    &&new HashSet<>(signers).equals(new HashSet<>(device.signers))&&code>device.code&&compare(version(),device.version)>0
                    &&(abis.isEmpty()||!Collections.disjoint(abis,device.abis))
                    &&(abis.isEmpty()||abis.size()>1||(!device.abi.equals("universal")&&abis.contains(device.abi)));
        }
        int architectureRank(Device device){
            if(device.abi.equals("universal"))return abis.size()>1?0:1;
            if(abis.size()==1&&abis.contains(device.abi))return 0;
            return abis.size()>1||abis.isEmpty()?1:2;
        }
    }
    static final class Device {
        final String pkg,version,channel,compatibility,homeMode,abi;
        final long code;final int sdk;final List<String> abis,signers;
        Device(String pkg,String version,long code,String channel,String compatibility,String homeMode,String abi,int sdk,List<String> abis,List<String> signers){
            this.pkg=pkg;this.version=version;this.code=code;this.channel=channel;this.compatibility=compatibility;this.homeMode=homeMode;this.abi=abi;this.sdk=sdk;this.abis=abis;this.signers=signers;
        }
    }
    private UpdateManifest(JSONObject json)throws JSONException,IOException{
        if(json.getInt("schemaVersion")!=1)throw new IOException("Unknown update schema");
        revision=json.getLong("revision");if(revision<1)throw new IOException("Invalid update revision");
        JSONArray values=json.getJSONArray("releases");Set<String> ids=new HashSet<>();
        for(int n=0;n<values.length();n++){
            Release release=new Release(values.getJSONObject(n));releases.add(release);
            for(Artifact artifact:release.artifacts)if(!ids.add(artifact.id))throw new IOException("Duplicate update artifact");
        }
    }
    static UpdateManifest verify(byte[] envelope,byte[] publicKey,long minimumRevision)throws IOException{
        if(envelope.length>MAX_BYTES)throw new IOException("Update manifest too large");
        try{
            JSONObject json=new JSONObject(new String(envelope,java.nio.charset.StandardCharsets.UTF_8));
            if(!"quiettv-update-1".equals(json.getString("keyId")))throw new IOException("Unknown update signing key");
            byte[] payload=Base64.decode(json.getString("payload")),signed=Base64.decode(json.getString("signature"));
            Signature verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKey)));verifier.update(payload);
            if(!verifier.verify(signed))throw new IOException("Update signature verification failed");
            UpdateManifest result=new UpdateManifest(new JSONObject(new String(payload,java.nio.charset.StandardCharsets.UTF_8)));
            if(result.revision<minimumRevision)throw new IOException("Stale update manifest");return result;
        }catch(JSONException|GeneralSecurityException e){throw new IOException("Invalid signed update manifest",e);}
        catch(RuntimeException e){throw new IOException("Invalid signed update manifest",e);}
    }
    Artifact select(Device device){
        Artifact best=null;
        for(Release release:releases)for(Artifact value:release.artifacts)if(value.matches(device)){
            if(best==null||compare(value.version(),best.version())>0||(compare(value.version(),best.version())==0&&value.architectureRank(device)<best.architectureRank(device)))best=value;
        }
        return best;
    }
    String unavailableReason(Device device){
        List<Artifact> candidates=new ArrayList<>();
        for(Release release:releases)if(compare(release.version,device.version)>0)candidates.addAll(release.artifacts);
        if(candidates.isEmpty())return "";
        for(Iterator<Artifact> it=candidates.iterator();it.hasNext();){Artifact a=it.next();if(!a.pkg.equals(device.pkg))it.remove();}if(candidates.isEmpty())return "新版尚未提供当前包名的安装包";
        for(Iterator<Artifact> it=candidates.iterator();it.hasNext();){Artifact a=it.next();if(!a.compatibility.equals(device.compatibility)||!a.homeMode.equals(device.homeMode))it.remove();}if(candidates.isEmpty())return "新版尚未提供当前系统档位或桌面形态的安装包";
        for(Iterator<Artifact> it=candidates.iterator();it.hasNext();){Artifact a=it.next();if(!a.release.channel.equals(device.channel)&&!("test".equals(device.channel)&&"stable".equals(a.release.channel)))it.remove();}if(candidates.isEmpty())return "新版与当前安装包的更新渠道不同";
        for(Iterator<Artifact> it=candidates.iterator();it.hasNext();){Artifact a=it.next();if(!new HashSet<>(a.signers).equals(new HashSet<>(device.signers)))it.remove();}if(candidates.isEmpty())return "新版与当前安装包签名不同，无法直接覆盖更新";
        for(Iterator<Artifact> it=candidates.iterator();it.hasNext();){Artifact a=it.next();if(a.minSdk>device.sdk)it.remove();}if(candidates.isEmpty())return "新版暂不支持当前安卓版本";
        for(Iterator<Artifact> it=candidates.iterator();it.hasNext();){Artifact a=it.next();if(a.code<=device.code)it.remove();}if(candidates.isEmpty())return "新版构建号不高于当前安装包，无法覆盖更新";
        return "新版尚未提供适合当前架构的安装包";
    }
    private static List<String> strings(JSONArray values)throws JSONException,IOException{
        List<String> result=new ArrayList<>();for(int n=0;n<values.length();n++){String value=values.getString(n);if(value.trim().isEmpty()||value.length()>2000)throw new IOException("Invalid update text");result.add(value);}return Collections.unmodifiableList(result);
    }
    static long[] versionParts(String value)throws IOException{
        if(!value.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"))throw new IOException("Invalid release version");
        String[] fields=value.split("\\.");long[] parts=new long[3];try{for(int n=0;n<3;n++)parts[n]=Long.parseLong(fields[n]);}catch(NumberFormatException e){throw new IOException("Invalid release version",e);}return parts;
    }
    static int compare(String left,String right){try{long[] a=versionParts(left),b=versionParts(right);for(int n=0;n<3;n++){int c=Long.compare(a[n],b[n]);if(c!=0)return c;}return 0;}catch(IOException e){return -1;}}
}
