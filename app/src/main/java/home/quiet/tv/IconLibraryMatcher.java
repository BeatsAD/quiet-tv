package home.quiet.tv;

import android.content.Context;
import java.io.*;
import java.text.Normalizer;
import java.util.*;
import org.json.*;

/** Deterministic application identity matching, independent of style or screen position. */
final class IconLibraryMatcher {
    final int revision;
    private final Map<String,Set<String>> names=new HashMap<>(),packages=new HashMap<>();
    private final Map<String,Boolean> automatic=new HashMap<>();
    IconLibraryMatcher(JSONObject data,IconLibraryCatalog catalog)throws IOException,JSONException{
        if(data.getInt("version")!=1||data.getInt("revision")<1)throw new IOException("图标匹配规则版本无效");revision=data.getInt("revision");
        JSONArray list=data.getJSONArray("items");
        for(int i=0;i<list.length();i++){JSONObject row=list.getJSONObject(i);String id=row.getString("id");if(!id.matches("[a-z][a-z0-9-]{0,47}")||automatic.containsKey(id)||!(row.get("autoMatch") instanceof Boolean))throw new IOException("图标匹配身份无效");automatic.put(id,row.getBoolean("autoMatch"));
            JSONArray aliases=row.getJSONArray("aliases"),ids=row.getJSONArray("packages");for(int n=0;n<aliases.length();n++){String name=aliases.getString(n);if(name.length()>100||normalize(name).isEmpty())throw new IOException("图标别名无效");add(names,normalize(name),id);}for(int n=0;n<ids.length();n++){String pkg=ids.getString(n);if(!AppCoverStore.packageName(pkg))throw new IOException("图标匹配包名无效");add(packages,pkg,id);}}
        for(IconLibraryCatalog.Item item:catalog.items)if(automatic.containsKey(item.id)){add(names,normalize(item.zh),item.id);add(names,normalize(item.en),item.id);}
    }
    void requireCoverage(IconLibraryCatalog catalog)throws IOException{
        Set<String> themes=new HashSet<>();for(IconLibraryCatalog.Item item:catalog.items)themes.add(item.id);
        if(!automatic.keySet().equals(themes))throw new IOException("在线匹配规则与图库主题不一致");
    }
    private static void add(Map<String,Set<String>> index,String key,String id){Set<String> values=index.get(key);if(values==null){values=new LinkedHashSet<>();index.put(key,values);}values.add(id);}
    static String normalize(String name){return Normalizer.normalize(name==null?"":name,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Z}]+","");}
    static IconLibraryMatcher read(Context context,IconLibraryCatalog catalog)throws IOException{
        if(catalog.matches!=null)try{return new IconLibraryMatcher(catalog.matches,catalog);}catch(JSONException invalid){throw new IOException("在线匹配规则无法读取",invalid);}
        try(InputStream input=context.getAssets().open("icon-library/matches.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=input.read(b))!=-1){if(out.size()+n>256*1024)throw new IOException("图标匹配规则过大");out.write(b,0,n);}return new IconLibraryMatcher(new JSONObject(out.toString("UTF-8")),catalog);}catch(JSONException invalid){throw new IOException("图标匹配规则无法读取",invalid);}
    }
    static final class Match {
        final List<String> ids;final boolean automatic;final String reason;
        Match(Set<String> ids,boolean automatic,String reason){this.ids=ids==null?Collections.emptyList():new ArrayList<>(ids);this.automatic=automatic;this.reason=reason;}
    }
    Match match(String pkg,String label){Set<String> found=packages.get(pkg);String reason="package";if(found==null){found=names.get(normalize(label));reason="name";}boolean ready=found!=null&&found.size()==1&&Boolean.TRUE.equals(automatic.get(found.iterator().next()));return new Match(found,ready,found==null?"unmatched":ready?reason:"pending");}
}
