package home.quiet.tv;

import java.io.*;
import java.util.*;
import org.json.*;

/** Independent, bundled administrative data; never caches QWeather GeoAPI responses. */
final class WeatherRegions {
    private static WeatherRegions bundled;
    static synchronized WeatherRegions load(android.content.Context context){
        if(bundled==null)try(InputStream input=context.getAssets().open("weather/regions.json")){bundled=new WeatherRegions(input);}
        catch(IOException|JSONException e){throw new IllegalStateException("Invalid bundled weather regions",e);}
        return bundled;
    }
    static final class Region {
        final String code,name,province,city,parent;
        final int level;
        Region(String code,String name,String province,String city,String parent,int level){
            this.code=code;this.name=name;this.province=province;this.city=city;this.parent=parent;this.level=level;
        }
        String area(){return province+(city.isEmpty()||city.equals(province)||city.equals(name)?"":"  "+city);}
        String label(){return name+(area().isEmpty()||area().equals(name)?"":"  "+area());}
    }
    private final List<Region> all=new ArrayList<>();
    private final Map<String,Region> byCode=new LinkedHashMap<>();
    WeatherRegions(InputStream input)throws IOException,JSONException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[4096];int count;
        while((count=input.read(bytes))!=-1)out.write(bytes,0,count);
        JSONArray provinces=new JSONArray(out.toString("UTF-8"));
        for(int p=0;p<provinces.length();p++){
            JSONObject province=provinces.getJSONObject(p);String provinceName=province.getString("name"),provinceCode=province.getString("code")+"0000";
            add(new Region(provinceCode,provinceName,provinceName,"","",0));
            JSONArray cities=province.getJSONArray("children");
            for(int c=0;c<cities.length();c++){
                JSONObject city=cities.getJSONObject(c);String cityName=city.getString("name"),cityCode=city.getString("code")+"00";
                // Municipalities and province-administered counties have a synthetic middle level.
                if(cityName.equals("市辖区")||cityName.equals("县"))cityName=provinceName;
                add(new Region(cityCode,cityName,provinceName,cityName,provinceCode,1));
                JSONArray districts=city.optJSONArray("children");if(districts==null)continue;
                for(int d=0;d<districts.length();d++){
                    JSONObject district=districts.getJSONObject(d);String code=district.getString("code");
                    if(code.length()==6)add(new Region(code,district.getString("name"),provinceName,cityName,cityCode,2));
                }
            }
        }
    }
    private void add(Region region){all.add(region);byCode.put(region.code,region);}
    Region find(String code){return byCode.get(code);}
    List<Region> children(String parent){List<Region> result=new ArrayList<>();for(Region region:all)if(region.parent.equals(parent))result.add(region);return result;}
    List<Region> search(String query,int limit){
        String trimmed=query.trim();if(trimmed.isEmpty()||trimmed.length()>60)return Collections.emptyList();
        String[] words=fold(trimmed).split("\\s+");List<Region> result=new ArrayList<>();
        for(Region region:all){
            if(region.level==0)continue;
            String path=fold(region.province+region.city+region.name)+" "+region.code;boolean match=true;
            for(String word:words)if(!path.contains(word)){match=false;break;}
            if(match)result.add(region);
        }
        Collections.sort(result,(a,b)->Integer.compare(score(a,trimmed),score(b,trimmed)));
        return new ArrayList<>(result.subList(0,Math.min(limit,result.size())));
    }
    private static int score(Region region,String query){return fold(region.name).equals(fold(query))?0:region.name.contains(query)?1:2;}
    static String fold(String value){return value.toLowerCase(Locale.ROOT).replace("省","").replace("市","").replace("区","").replace("县","").replace("　"," ").trim();}
    static String administrativeName(String value){return value.replaceFirst("(特别行政区|维吾尔自治区|壮族自治区|回族自治区|自治区|省|市|区|县)$","");}
    JSONObject item(Region region)throws JSONException{
        Region city=region.level==2?find(region.parent):region;
        return new JSONObject().put("code",region.code).put("name",region.name).put("area",region.area()).put("level",region.level)
            .put("provinceCode",region.level==0?region.code:city.parent).put("cityCode",region.level==0?"":city.code);
    }
    JSONArray items(List<Region> regions)throws JSONException{JSONArray result=new JSONArray();for(Region region:regions)result.put(item(region));return result;}
}
