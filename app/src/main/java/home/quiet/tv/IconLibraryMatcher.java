package home.quiet.tv;

import android.content.Context;
import java.io.*;
import java.text.Normalizer;
import java.util.*;
import org.json.*;

/** Application identities are exact matches; spelling and edition variants need review. */
final class IconLibraryMatcher {
    private static final String HAN_PAIRS="愛爱視视頻频遊游戲戏體体網网雲云聽听電电腦脑龍龙騰腾訊讯優优藝艺樂乐飛飞馬马貝贝場场當当雞鸡華华寶宝盤盘設设備备應应資资傳传圖图標标庫库倉仓歸归蟻蚁螞蚂殼壳紅红級级尋寻嗶哔鬥斗魚鱼擬拟劇剧別别嚕噜國国際际極极簡简潔洁";
    private static final String[] SUFFIXES={"电视版","tv版","安卓版","客户端","极速版","国际版","最新版","纯净版","版本","androidtv","international","edition","version","stable","beta","lite","next","版","tv","hd"};
    final int revision;
    private final Map<String,Set<String>> names=new LinkedHashMap<>(),packages=new HashMap<>(),relaxed=new HashMap<>(),aliases=new LinkedHashMap<>();
    private final Map<String,Boolean> automatic=new HashMap<>();
    IconLibraryMatcher(JSONObject data,IconLibraryCatalog catalog)throws IOException,JSONException{
        if(data.getInt("version")!=1||data.getInt("revision")<1)throw new IOException("图标匹配规则版本无效");revision=data.getInt("revision");
        JSONArray list=data.getJSONArray("items");
        for(int i=0;i<list.length();i++){JSONObject row=list.getJSONObject(i);String id=row.getString("id");if(!id.matches("[a-z][a-z0-9-]{0,47}")||automatic.containsKey(id)||!(row.get("autoMatch") instanceof Boolean))throw new IOException("图标匹配身份无效");automatic.put(id,row.getBoolean("autoMatch"));
            JSONArray labels=row.getJSONArray("aliases"),ids=row.getJSONArray("packages");for(int n=0;n<labels.length();n++){String name=labels.getString(n);if(name.length()>100||normalize(name).isEmpty())throw new IOException("图标别名无效");name(name,id);}for(int n=0;n<ids.length();n++){String pkg=ids.getString(n);if(!AppCoverStore.packageName(pkg))throw new IOException("图标匹配包名无效");add(packages,pkg,id);}}
        for(IconLibraryCatalog.Item item:catalog.items)if(automatic.containsKey(item.id)){name(item.zh,item.id);name(item.en,item.id);}
    }
    void requireCoverage(IconLibraryCatalog catalog)throws IOException{
        Set<String> themes=new HashSet<>();for(IconLibraryCatalog.Item item:catalog.items)themes.add(item.id);
        if(!automatic.keySet().equals(themes))throw new IOException("在线匹配规则与图库主题不一致");
    }
    private static void add(Map<String,Set<String>> index,String key,String id){Set<String> values=index.get(key);if(values==null){values=new LinkedHashSet<>();index.put(key,values);}values.add(id);}
    private void name(String value,String id){String key=normalize(value),simple=loose(key);add(names,key,id);if(!simple.isEmpty()){add(relaxed,simple,id);add(aliases,id,simple);}}
    static String normalize(String name){
        String value=Normalizer.normalize(name==null?"":name,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Z}]+","");
        StringBuilder result=new StringBuilder(value.length());for(int i=0;i<value.length();i++){char c=value.charAt(i);int at=HAN_PAIRS.indexOf(c);result.append(at>=0&&at%2==0?HAN_PAIRS.charAt(at+1):c);}return result.toString();
    }
    private static String loose(String name){return name.replaceAll("[^\\p{L}\\p{N}]","");}
    // Bundled additions supplement identity coverage, never the online autoMatch policy.
    private void supplement(IconLibraryMatcher bundled){
        for(Map.Entry<String,Set<String>> row:bundled.names.entrySet())for(String id:row.getValue())if(automatic.containsKey(id))name(row.getKey(),id);
        for(Map.Entry<String,Set<String>> row:bundled.packages.entrySet())for(String id:row.getValue())if(automatic.containsKey(id))add(packages,row.getKey(),id);
    }
    static IconLibraryMatcher read(Context context,IconLibraryCatalog catalog)throws IOException{
        try(InputStream input=context.getAssets().open("icon-library/matches.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=input.read(b))!=-1){if(out.size()+n>256*1024)throw new IOException("图标匹配规则过大");out.write(b,0,n);}
            IconLibraryMatcher bundled=new IconLibraryMatcher(new JSONObject(out.toString("UTF-8")),catalog);if(catalog.matches==null)return bundled;
            IconLibraryMatcher current=new IconLibraryMatcher(catalog.matches,catalog);current.supplement(bundled);return current;
        }catch(JSONException invalid){throw new IOException("图标匹配规则无法读取",invalid);}
    }
    static final class Match {
        final List<String> ids;final boolean automatic;final String reason;
        Match(Set<String> ids,boolean automatic,String reason){this.ids=ids==null?Collections.emptyList():new ArrayList<>(ids);this.automatic=automatic;this.reason=reason;}
    }
    private Match exact(Set<String> ids,String reason){boolean ready=ids.size()==1&&Boolean.TRUE.equals(automatic.get(ids.iterator().next()));return new Match(ids,ready,reason);}
    Match match(String pkg,String label,String confirmed){
        Match result=match(pkg,label);
        // An applied theme resolves review items, but never overrides a new exact identity.
        if(!result.automatic&&automatic.containsKey(confirmed)&&(result.ids.isEmpty()||result.ids.contains(confirmed)))return new Match(Collections.singleton(confirmed),true,"confirmed");
        return result;
    }
    Match match(String pkg,String label){
        Set<String> found=packages.get(pkg);if(found!=null)return exact(found,"package");
        String key=normalize(label);found=names.get(key);if(found!=null)return exact(found,"name");
        if(key.length()>100)return new Match(null,false,"unmatched");
        String simple=loose(key);found=relaxed.get(simple);if(found!=null)return new Match(found,false,"format");
        Set<String> variants=variants(key),candidates=new LinkedHashSet<>();
        for(String variant:variants){Set<String> ids=relaxed.get(variant);if(ids!=null)candidates.addAll(ids);}
        if(!candidates.isEmpty())return new Match(candidates,false,"suffix");
        for(Map.Entry<String,Set<String>> row:aliases.entrySet())for(String first:row.getValue())for(String second:row.getValue())if(han(first)!=han(second))for(String variant:variants)if(variant.equals(first+second))candidates.add(row.getKey());
        if(!candidates.isEmpty())return new Match(candidates,false,"bilingual");
        final Map<String,Integer> scores=new HashMap<>();
        for(Map.Entry<String,Set<String>> row:aliases.entrySet())for(String alias:row.getValue())for(String variant:variants){
            int size=Math.min(alias.length(),variant.length());if(han(alias)!=han(variant)||size<(han(alias)?3:5))continue;
            int limit=size>=9?2:1;if(Math.abs(alias.length()-variant.length())>limit)continue;int distance=distance(alias,variant);
            if(distance>limit)continue;int score=1000-distance*1000/Math.max(alias.length(),variant.length());Integer old=scores.get(row.getKey());if(old==null||score>old)scores.put(row.getKey(),score);
        }
        List<String> ordered=new ArrayList<>(scores.keySet());Collections.sort(ordered,(a,b)->{int n=scores.get(b).compareTo(scores.get(a));return n==0?a.compareTo(b):n;});
        candidates.clear();for(String id:ordered){candidates.add(id);if(candidates.size()==3)break;}
        return new Match(candidates,false,candidates.isEmpty()?"unmatched":"similar");
    }
    private static boolean han(String value){for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c>='\u3400'&&c<='\u9fff')return true;}return false;}
    private static Set<String> variants(String key){
        Set<String> values=new LinkedHashSet<>();values.add(loose(key));String version=key.replaceFirst("(?:v[0-9]+(?:\\.[0-9]+){0,3}|[（(\\[]?[0-9]+(?:\\.[0-9]+){1,3}[）)\\]]?)$","");if(version.length()>=2)values.add(loose(version));
        for(int depth=0;depth<2;depth++)for(String value:new ArrayList<>(values))for(String suffix:SUFFIXES)if(value.endsWith(suffix)&&value.length()>suffix.length()+1)values.add(value.substring(0,value.length()-suffix.length()));
        return values;
    }
    private static int distance(String a,String b){
        int[] older=new int[b.length()+1],previous=new int[b.length()+1];for(int j=0;j<=b.length();j++)previous[j]=j;
        for(int i=1;i<=a.length();i++){int[] current=new int[b.length()+1];current[0]=i;for(int j=1;j<=b.length();j++){current[j]=Math.min(Math.min(previous[j]+1,current[j-1]+1),previous[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));if(i>1&&j>1&&a.charAt(i-1)==b.charAt(j-2)&&a.charAt(i-2)==b.charAt(j-1))current[j]=Math.min(current[j],older[j-2]+1);}older=previous;previous=current;}return previous[b.length()];
    }
}
