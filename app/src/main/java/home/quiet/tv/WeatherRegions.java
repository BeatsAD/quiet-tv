package home.quiet.tv;

import java.io.*;
import java.util.*;
import org.json.*;

/** Independent, bundled administrative data; never caches QWeather GeoAPI responses. */
final class WeatherRegions {
    private static WeatherRegions bundled;
    static synchronized WeatherRegions load(android.content.Context context){
        if(bundled==null)try(InputStream input=context.getAssets().open("weather/regions.json");InputStream places=context.getAssets().open("weather/places.json")){bundled=new WeatherRegions(input);bundled.addPlaces(places);}
        catch(IOException|JSONException e){throw new IllegalStateException("Invalid bundled weather regions",e);}
        return bundled;
    }
    static final class Region {
        final String code,name,province,city,parent;
        final int level;
        final double latitude,longitude;
        final String adcode,aliases;
        Region(String code,String name,String province,String city,String parent,int level){
            this(code,name,province,city,parent,level,Double.NaN,Double.NaN,code.matches("[0-9]{6}")?code:"","");
        }
        Region(String code,String name,String province,String city,String parent,int level,double latitude,double longitude,String adcode,String aliases){
            this.code=code;this.name=name;this.province=province;this.city=city;this.parent=parent;this.level=level;
            this.latitude=latitude;this.longitude=longitude;this.adcode=adcode;this.aliases=aliases;
        }
        boolean located(){return coordinates(latitude,longitude);}
        Region at(double lat,double lon){return new Region(code,name,province,city,parent,level,lat,lon,adcode,aliases);}
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
    static boolean coordinates(double lat,double lon){return !Double.isNaN(lat)&&!Double.isInfinite(lat)&&!Double.isNaN(lon)&&!Double.isInfinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180;}
    void addPlaces(InputStream input)throws IOException,JSONException{
        JSONArray places=new JSONArray(new String(read(input),"UTF-8"));
        for(int i=0;i<places.length();i++){
            JSONObject place=places.getJSONObject(i);Region city=find(place.getString("cityCode")),province=city==null?null:find(city.parent);
            if(city==null||province==null)throw new JSONException("Invalid weather place parent");
            double lat=place.getDouble("latitude"),lon=place.getDouble("longitude");if(!coordinates(lat,lon))throw new JSONException("Invalid place coordinates");
            Region region=new Region(place.getString("id"),place.getString("name"),province.name,city.name,city.code,2,lat,lon,"",place.getJSONArray("aliases").join(" ").replace("\"",""));add(region);
            JSONArray legacy=place.optJSONArray("legacyCodes");if(legacy!=null)for(int n=0;n<legacy.length();n++)byCode.put(legacy.getString(n),region);
        }
    }
    private static byte[] read(InputStream input)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1)out.write(buffer,0,n);return out.toByteArray();}
    static Region readPlace(JSONObject json)throws JSONException{
        String code=json.getString("code"),name=json.getString("name"),adcode=json.optString("adcode","");
        if(code.length()>100||name.trim().isEmpty()||name.length()>120||(!adcode.isEmpty()&&!adcode.matches("[0-9]{6}")))throw new JSONException("Invalid weather place");
        double lat=json.optDouble("latitude",Double.NaN),lon=json.optDouble("longitude",Double.NaN);
        if(json.has("latitude")||json.has("longitude")){if(!coordinates(lat,lon))throw new JSONException("Invalid place coordinates");}
        return new Region(code,name,json.optString("province",""),json.optString("city",""),json.optString("parent",""),json.optInt("level",2),lat,lon,adcode,"");
    }
    static JSONObject placeJson(Region region)throws JSONException{
        JSONObject json=new JSONObject().put("code",region.code).put("name",region.name).put("province",region.province).put("city",region.city).put("parent",region.parent).put("level",region.level).put("adcode",region.adcode);
        if(region.located())json.put("latitude",region.latitude).put("longitude",region.longitude);return json;
    }
    private void add(Region region){all.add(region);byCode.put(region.code,region);}
    Region find(String code){return byCode.get(code);}
    List<Region> children(String parent){List<Region> result=new ArrayList<>();for(Region region:all)if(region.parent.equals(parent))result.add(region);return result;}
    List<Region> search(String query,int limit){
        String trimmed=query.trim();if(trimmed.isEmpty()||trimmed.length()>60)return Collections.emptyList();
        String[] words=fold(trimmed).split("\\s+");List<Region> result=new ArrayList<>();
        for(Region region:all){
            if(region.level==0)continue;
            String path=fold(region.province+region.city+region.name+region.aliases)+" "+region.code;boolean match=true;
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
        String cityCode=city==null?"":city.code,provinceCode=city==null?"":city.parent;
        return new JSONObject().put("code",region.code).put("name",region.name).put("area",region.area()).put("level",region.level)
            .put("provinceCode",region.level==0?region.code:provinceCode).put("cityCode",region.level==0?"":cityCode);
    }
    JSONArray items(List<Region> regions)throws JSONException{JSONArray result=new JSONArray();for(Region region:regions)result.put(item(region));return result;}
}
