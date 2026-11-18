package home.quiet.tv;

import android.content.Context;
import java.io.*;
import java.util.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import org.bouncycastle.util.encoders.Base64;
import org.json.*;

/** Local layered artwork and signed, precomposed online artwork share one finite index. */
final class IconLibraryCatalog {
    static final int MAX_BYTES=2*1024*1024;
    static final String KEY_ID="quiettv-icons-1";
    static final String[] STYLES={"apple","google","flat","skeuo","soft","3d","pixel"};
    static final String[] SHAPES={"rectangle","circle","square"};
    static final String[] FORMS={"text","icon","transparent"};
    private static final String[] ICON_FORMS={"icon","transparent"};
    static String[] forms(String shape){return "rectangle".equals(shape)?FORMS:ICON_FORMS;}
    static boolean styleId(String value){return value!=null&&value.matches("[a-z0-9][a-z0-9-]{0,47}");}
    static final class Style {
        final String id,zh,en;
        Style(String id,String zh,String en)throws IOException{if(!styleId(id)||zh.isEmpty()||zh.length()>40||en.isEmpty()||en.length()>80)throw new IOException("图标风格定义无效");this.id=id;this.zh=zh;this.en=en;}
        String label(boolean chinese){return chinese?zh:en;}
    }
    static String legacyStyleName(String id,boolean chinese){String[] names=chinese?new String[]{"苹果风格","谷歌风格","扁平化","拟物化","轻拟物","3D 风格","像素风格"}:new String[]{"Apple","Google","Flat","Skeuomorphic","Soft","3D","Pixel"};int index=Arrays.asList(STYLES).indexOf(id);return index<0?id:names[index];}
    private static List<Style> styles(List<Item> items,JSONArray definitions)throws JSONException,IOException{
        LinkedHashSet<String> used=new LinkedHashSet<>();for(Item item:items)used.add(item.style);ArrayList<Style> result=new ArrayList<>();Set<String> declared=new HashSet<>();
        if(definitions!=null){if(definitions.length()>64)throw new IOException("图标风格数量过多");for(int i=0;i<definitions.length();i++){JSONObject value=definitions.getJSONObject(i);Style style=new Style(value.getString("id"),value.getString("name"),value.getString("nameEn"));if(!declared.add(style.id))throw new IOException("图标风格重复");if(used.contains(style.id))result.add(style);}if(!declared.containsAll(used))throw new IOException("图标缺少风格定义");}
        else for(String id:used)result.add(new Style(id,legacyStyleName(id,true),legacyStyleName(id,false)));
        return Collections.unmodifiableList(result);
    }
    static final class Asset {
        final String path,sha256;final int width,height,bytes;
        Asset(JSONObject value,int width,int height,boolean thumbnail)throws JSONException,IOException{
            path=value.getString("path");sha256=value.getString("sha256");this.width=value.getInt("width");this.height=value.getInt("height");bytes=value.getInt("bytes");
            if(!IconLibrarySources.path(path)||path.equals("latest.signed.json")||!path.contains(thumbnail?"/thumbnails/":"/icons/")||!sha256.matches("[a-f0-9]{64}")||this.width!=width||this.height!=height||bytes<33||bytes>(thumbnail?256*1024:1024*1024))throw new IOException("在线图标清单无效");
        }
        void verify(byte[] data)throws IOException{
            if(data.length!=bytes||!IconLibraryCache.hash(data).equals(sha256))throw new IOException("图标校验失败");
            try(DataInputStream input=new DataInputStream(new ByteArrayInputStream(data))){
                if(input.readLong()!=0x89504e470d0a1a0aL||input.readInt()!=13||input.readInt()!=0x49484452||input.readInt()!=width||input.readInt()!=height||input.readUnsignedByte()!=8||input.readUnsignedByte()!=6||input.readUnsignedByte()!=0||input.readUnsignedByte()!=0||input.readUnsignedByte()!=0)throw new IOException("图标格式不匹配");
            }
        }
    }
    static final class Variant {
        final String shape,form;final Asset image,thumbnail;
        Variant(JSONObject value)throws JSONException,IOException{
            shape=value.getString("shape");form=value.getString("form");
            if(!contains(SHAPES,shape)||!contains(forms(shape),form))throw new IOException("在线图标选项无效");
            boolean rectangle=shape.equals("rectangle");int width=rectangle?720:512,height=rectangle?405:512;
            image=new Asset(value.getJSONObject("image"),width,height,false);thumbnail=new Asset(value.getJSONObject("thumbnail"),rectangle?320:256,rectangle?180:256,true);
        }
    }
    static final class Item {
        final String id,zh,en,style,foreground,background;
        final Map<String,Variant> variants=new LinkedHashMap<>();
        Item(JSONObject value)throws JSONException,IOException{
            id=value.getString("id");zh=value.getString("name");en=value.getString("nameEn");style=value.getString("style");
            foreground=value.optString("foreground");background=value.optString("background");
            if(!id.matches("[a-z][a-z0-9-]{0,47}")||zh.isEmpty()||zh.length()>40||en.isEmpty()||en.length()>80||!styleId(style))throw new IOException("图标素材清单无效");
            if(value.has("variants")){
                JSONArray all=value.getJSONArray("variants");if(all.length()<1||all.length()>7||!foreground.isEmpty()||!background.isEmpty())throw new IOException("在线图标组合无效");
                for(int n=0;n<all.length();n++){Variant variant=new Variant(all.getJSONObject(n));if(variants.put(variant.shape+":"+variant.form,variant)!=null)throw new IOException("图标组合重复");}
            }else if(!asset(foreground)||!background.isEmpty()&&!asset(background))throw new IOException("图标素材路径无效");
            if(value.getString("source").trim().isEmpty())throw new IOException("图标素材缺少来源记录");
        }
        String label(boolean chinese){return chinese?zh:en;}
        boolean online(){return !variants.isEmpty();}
        boolean supports(String shape,String form){return contains(SHAPES,shape)&&contains(forms(shape),form)&&(!online()||variants.containsKey(shape+":"+form)||!"rectangle".equals(shape)&&variants.containsKey("rectangle:"+form));}
        Variant variant(String shape,String form)throws IOException{Variant result=variants.get(shape+":"+form);if(result==null&&supports(shape,form))result=variants.get("rectangle:"+form);if(result==null)throw new IOException("此形状或形式暂无素材");return result;}
    }
    final int version,revision;
    final List<Item> items;
    final List<Style> styles;
    final JSONObject matches;
    IconLibraryCatalog(JSONObject value)throws JSONException,IOException{
        version=value.getInt("version");revision=value.getInt("revision");if((version!=1&&version!=2)||revision<1)throw new IOException("图标素材版本不支持");
        ArrayList<Item> result=new ArrayList<>();Set<String> seen=new HashSet<>();JSONArray list=value.getJSONArray("items");
        if(list.length()>1024)throw new IOException("图标素材数量过多");
        for(int i=0;i<list.length();i++){Item item=new Item(list.getJSONObject(i));if(!seen.add(item.style+":"+item.id))throw new IOException("图标素材重复");result.add(item);}items=Collections.unmodifiableList(result);styles=styles(items,value.has("styles")?value.getJSONArray("styles"):null);
        matches=value.has("matches")?value.getJSONObject("matches"):null;
        if(matches!=null){if(version!=2)throw new IOException("本地图标目录不支持在线匹配规则");new IconLibraryMatcher(matches,this).requireCoverage(this);}
    }
    static IconLibraryCatalog read(Context context)throws IOException{
        return IconLibraryRepository.get(context).readCatalog();
    }
    static IconLibraryCatalog merge(IconLibraryCatalog local,IconLibraryCatalog online){
        if(online==null)return local;
        LinkedHashMap<String,Item> merged=new LinkedHashMap<>();for(Item item:online.items)merged.put(item.style+":"+item.id,item);for(Item item:local.items)merged.put(item.style+":"+item.id,item);
        LinkedHashMap<String,Style> styles=new LinkedHashMap<>();for(Style style:online.styles)styles.put(style.id,style);for(Style style:local.styles)if(!styles.containsKey(style.id))styles.put(style.id,style);
        return new IconLibraryCatalog(Math.max(local.revision,online.revision),new ArrayList<>(merged.values()),online.matches,new ArrayList<>(styles.values()));
    }
    private IconLibraryCatalog(int revision,List<Item> items,JSONObject matches,List<Style> styles){this.version=2;this.revision=revision;this.items=Collections.unmodifiableList(items);this.matches=matches;this.styles=Collections.unmodifiableList(styles);}
    String[] styleIds(){String[] result=new String[styles.size()];for(int i=0;i<result.length;i++)result[i]=styles.get(i).id;return result;}
    String styleName(String id,boolean chinese){for(Style style:styles)if(style.id.equals(id))return style.label(chinese);return legacyStyleName(id,chinese);}
    static IconLibraryCatalog readLocal(Context context)throws IOException{
        try(InputStream input=context.getAssets().open("icon-library/catalog.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1){if(out.size()+n>256*1024)throw new IOException("图标素材清单过大");out.write(buffer,0,n);}return new IconLibraryCatalog(new JSONObject(out.toString("UTF-8")));
        }catch(JSONException invalid){throw new IOException("图标素材清单无法读取",invalid);}
    }
    static IconLibraryCatalog verify(byte[] envelope,byte[] key,int minimumRevision)throws IOException{
        try{
            if(envelope.length>MAX_BYTES)throw new IOException("图标清单过大");
            JSONObject json=new JSONObject(new String(envelope,"UTF-8"));if(!KEY_ID.equals(json.getString("keyId")))throw new IOException("图标签名密钥无效");
            byte[] payload=Base64.decode(json.getString("payload")),signature=Base64.decode(json.getString("signature"));
            Signature verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(key)));verifier.update(payload);
            if(!verifier.verify(signature))throw new IOException("图标清单签名验证失败");
            IconLibraryCatalog result=new IconLibraryCatalog(new JSONObject(new String(payload,"UTF-8")));
            if(result.version!=2||result.revision<minimumRevision)throw new IOException("图标清单版本已过期");return result;
        }catch(JSONException|GeneralSecurityException|IllegalArgumentException e){throw new IOException("图标清单无法验证",e);}
    }
    List<Item> style(String style){ArrayList<Item> result=new ArrayList<>();for(Item item:items)if(item.style.equals(style))result.add(item);return result;}
    List<Item> style(String style,String shape,String form){ArrayList<Item> result=new ArrayList<>();for(Item item:style(style))if(item.supports(shape,form))result.add(item);return result;}
    static boolean contains(String[] options,String value){return Arrays.asList(options).contains(value);}
    static boolean asset(String value){return value!=null&&value.matches("icon-library/[a-z0-9-]+(?:/[a-z0-9-]+)*\\.png");}
}
