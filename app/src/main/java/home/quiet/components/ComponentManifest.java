package home.quiet.components;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import org.json.*;
import org.bouncycastle.util.encoders.Base64;

/** Signed, finite component catalog. APK updates keep their existing protocol. */
public final class ComponentManifest {
    public static final int MAX_BYTES=262144, HOST_API=1;
    public static final String KEY_ID="quiettv-components-1";
    public final long revision;
    public final List<Artifact> artifacts=new ArrayList<>();
    public static final class Entry {
        public final String name,sha256;public final long bytes;
        Entry(JSONObject j)throws JSONException,IOException{
            name=j.getString("name");sha256=j.getString("sha256");bytes=j.getLong("bytes");
            if(!name.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,95}")||name.contains("..")||!sha256.matches("[0-9a-f]{64}")||bytes<1||bytes>64L*1024*1024)throw new IOException("组件文件清单无效");
        }
    }
    public static final class Artifact {
        public final String id,target,version,abi,sha256,url;public final int minSdk;public final long bytes,expandedBytes;
        public final Map<String,Entry> files=new LinkedHashMap<>();
        public final JSONObject json;
        Artifact(JSONObject j)throws JSONException,IOException{
            json=new JSONObject(j.toString());id=j.getString("id");target=j.getString("target");version=j.getString("version");abi=j.getString("abi");minSdk=j.getInt("minSdk");bytes=j.getLong("bytes");expandedBytes=j.getLong("expandedBytes");sha256=j.getString("sha256");url=j.getString("url");
            if(!Arrays.asList("weather","tailscale").contains(id)||!Arrays.asList("tv","phone").contains(target)||!version.matches("[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}")||j.getInt("hostApi")!=HOST_API||!sha256.matches("[0-9a-f]{64}")||bytes<1||bytes>64L*1024*1024||expandedBytes<1||expandedBytes>128L*1024*1024)throw new IOException("组件版本或大小不兼容");
            if(id.equals("weather")&&(!target.equals("tv")||!abi.equals("universal")||minSdk!=21)||id.equals("tailscale")&&(!abi.equals("arm64-v8a")||minSdk!=26))throw new IOException("组件系统或架构不兼容");
            validateUrl(url);JSONArray entries=j.getJSONArray("files");long total=0;
            if(entries.length()<1||entries.length()>64)throw new IOException("组件文件数量无效");
            for(int n=0;n<entries.length();n++){Entry e=new Entry(entries.getJSONObject(n));if(files.put(e.name,e)!=null)throw new IOException("组件文件重复");total+=e.bytes;}
            if(total!=expandedBytes||id.equals("tailscale")&&(files.size()!=1||!files.containsKey("libquietremote.so")))throw new IOException("组件展开大小不匹配");
        }
    }
    private ComponentManifest(JSONObject j)throws JSONException,IOException{
        if(j.getInt("schema")!=1||(revision=j.getLong("revision"))<1)throw new IOException("组件清单版本无效");
        JSONArray all=j.getJSONArray("artifacts");Set<String> seen=new HashSet<>();if(all.length()<1||all.length()>16)throw new IOException("组件清单数量无效");
        for(int n=0;n<all.length();n++){Artifact a=new Artifact(all.getJSONObject(n));if(!seen.add(a.target+"/"+a.id))throw new IOException("组件清单重复");artifacts.add(a);}
    }
    public Artifact select(String target,String id){for(Artifact a:artifacts)if(a.target.equals(target)&&a.id.equals(id))return a;return null;}
    public static ComponentManifest verify(byte[] envelope,byte[] key,long minimumRevision)throws IOException{
        try{
            if(envelope.length>MAX_BYTES)throw new IOException("组件清单过大");JSONObject j=new JSONObject(new String(envelope,Charset.forName("UTF-8")));
            if(!KEY_ID.equals(j.getString("keyId")))throw new IOException("组件签名密钥不匹配");
            byte[] payload=Base64.decode(j.getString("payload")),signature=Base64.decode(j.getString("signature"));
            Signature verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(key)));verifier.update(payload);
            if(!verifier.verify(signature))throw new IOException("组件签名验证失败");ComponentManifest result=new ComponentManifest(new JSONObject(new String(payload,Charset.forName("UTF-8"))));
            if(result.revision<minimumRevision)throw new IOException("组件清单版本已过期");return result;
        }catch(JSONException|GeneralSecurityException|IllegalArgumentException e){throw new IOException("组件清单无法验证",e);}
    }
    public static void validateUrl(String address)throws IOException{
        try{URI u=new URI(address);boolean repository=u.getPath()!=null&&(u.getPath().startsWith("/BeatsAD/quiet-tv-components/releases/download/")||u.getPath().startsWith("/BeatsAD/quiet-tv/releases/download/"));if(!"https".equals(u.getScheme())||!"github.com".equals(u.getHost())||u.getPort()!=-1||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||!repository||u.getPath().contains(".."))throw new IOException("组件下载地址无效");}catch(java.net.URISyntaxException e){throw new IOException("组件下载地址无效",e);}
    }
}
