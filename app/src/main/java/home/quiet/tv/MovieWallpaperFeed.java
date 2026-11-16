package home.quiet.tv;

import org.json.*;
import java.io.IOException;
import java.net.URL;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import org.bouncycastle.util.encoders.Base64;
import java.text.*;
import java.util.*;

/** Versioned static feed; original JPEG integrity is verified before local preparation. */
final class MovieWallpaperFeed {
    static final long MAX_BYTES=2*1024*1024L;
    final long revision,id,bytes,votes;
    final String contentDate,publishedAt,title,overview,summary,sourceUrl,path,hash;
    final int year;
    final double rating;
    byte[] envelope;
    private MovieWallpaperFeed(MovieWallpaperFeed source){
        revision=source.revision;id=source.id;bytes=source.bytes;votes=source.votes;contentDate=source.contentDate;publishedAt=source.publishedAt;title=source.title;overview=source.overview;summary=source.summary;sourceUrl=source.sourceUrl;path=source.path;hash=source.hash;year=source.year;rating=source.rating;
    }
    // Catalog snapshots contain validated metadata; signed download envelopes stay with the loader.
    MovieWallpaperFeed snapshot(){return new MovieWallpaperFeed(this);}
    private MovieWallpaperFeed(JSONObject json)throws JSONException,IOException{
        if(integer(json,"schema_version")!=1)throw new IOException("Unsupported cinema feed");
        revision=integer(json,"revision");if(revision<1||revision>9007199254740991L)throw new IOException("Invalid cinema revision");
        contentDate=text(json,"content_date",10);date(contentDate);
        if(!"Asia/Shanghai".equals(json.getString("timezone")))throw new IOException("Invalid cinema timezone");
        publishedAt=text(json,"published_at",40);
        if(!publishedAt.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,6})?(Z|[+-][0-9]{2}:[0-9]{2})"))throw new IOException("Invalid cinema timestamp");
        date(publishedAt.substring(0,10));
        SimpleDateFormat timestamp=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US);timestamp.setLenient(false);ParsePosition stamp=new ParsePosition(0);
        if(timestamp.parse(publishedAt.substring(0,19),stamp)==null||stamp.getIndex()!=19)throw new IOException("Invalid cinema timestamp");
        if(!publishedAt.endsWith("Z")&&(Integer.parseInt(publishedAt.substring(publishedAt.length()-5,publishedAt.length()-3))>23||Integer.parseInt(publishedAt.substring(publishedAt.length()-2))>59))throw new IOException("Invalid cinema timezone offset");
        JSONObject movie=json.getJSONObject("movie"),image=json.getJSONObject("image"),score=movie.getJSONObject("rating");
        if(!"tmdb".equals(movie.getString("provider"))||!"zh-CN".equals(movie.getString("text_locale"))||!"TMDB".equals(score.getString("provider")))throw new IOException("Invalid cinema provider");
        id=integer(movie,"id");long releaseYear=integer(movie,"year");if(id<1||releaseYear<1800||releaseYear>2200)throw new IOException("Invalid cinema identity");year=(int)releaseYear;
        title=text(movie,"title",100);overview=text(movie,"overview",4000);summary=text(movie,"summary",120);
        if(!han(title)||!han(overview)||!han(summary))throw new IOException("Cinema requires Chinese text");
        sourceUrl=movie.getString("source_url");if(!sourceUrl.equals("https://www.themoviedb.org/movie/"+id))throw new IOException("Invalid cinema attribution");
        Object value=score.get("value");if(!(value instanceof Number))throw new IOException("Invalid cinema rating");rating=((Number)value).doubleValue();votes=integer(score,"vote_count");
        if(Double.isNaN(rating)||Double.isInfinite(rating)||rating<0||rating>10||votes<0)throw new IOException("Invalid cinema rating");
        hash=text(image,"sha256",64);path=image.getString("path");bytes=integer(image,"bytes");
        if(!hash.matches("[a-f0-9]{64}")||!path.equals("images/"+id+"/"+hash+".jpg")||integer(image,"width")!=1920||integer(image,"height")!=1080||bytes<1||bytes>MAX_BYTES)throw new IOException("Invalid cinema image");
    }
    static MovieWallpaperFeed read(JSONObject json)throws IOException{
        try{return new MovieWallpaperFeed(json);}catch(JSONException error){throw new IOException("Invalid cinema feed",error);}catch(RuntimeException error){throw new IOException("Invalid cinema feed",error);}
    }
    static MovieWallpaperFeed verify(byte[] envelope,byte[] publicKey)throws IOException{
        if(envelope.length>256*1024)throw new IOException("Cinema manifest too large");
        try{
            JSONObject json=new JSONObject(new String(envelope,Charsets.UTF_8));
            if(!"quiettv-cinema-1".equals(json.getString("keyId")))throw new IOException("Unknown cinema signing key");
            byte[] payload=Base64.decode(json.getString("payload")),signature=Base64.decode(json.getString("signature"));
            Signature verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKey)));verifier.update(payload);
            if(!verifier.verify(signature))throw new IOException("Cinema signature verification failed");
            MovieWallpaperFeed feed=read(new JSONObject(new String(payload,Charsets.UTF_8)));feed.envelope=envelope.clone();return feed;
        }catch(JSONException|GeneralSecurityException|RuntimeException error){throw new IOException("Invalid signed cinema feed",error);}
    }
    private static long integer(JSONObject json,String key)throws JSONException,IOException{
        Object value=json.get(key);if(!(value instanceof Integer)&&!(value instanceof Long))throw new IOException("Invalid cinema integer");return ((Number)value).longValue();
    }
    private static String text(JSONObject json,String key,int max)throws JSONException,IOException{
        Object value=json.get(key);if(!(value instanceof String)||((String)value).trim().isEmpty()||((String)value).length()>max)throw new IOException("Invalid cinema text");return (String)value;
    }
    private static boolean han(String value){return java.util.regex.Pattern.compile("[\\u3400-\\u9fff]").matcher(value).find();}
    private static void date(String value)throws IOException{
        SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd",Locale.US);format.setLenient(false);ParsePosition position=new ParsePosition(0);
        if(!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")||format.parse(value,position)==null||position.getIndex()!=value.length())throw new IOException("Invalid cinema date");
    }
    String imageUrl(String manifest)throws IOException{
        URL base=new URL(manifest),result=new URL(base,path);
        if(!"https".equalsIgnoreCase(base.getProtocol())||base.getHost().isEmpty()||base.getUserInfo()!=null||base.getRef()!=null||!result.getHost().equals(base.getHost()))throw new IOException("Cinema requires HTTPS");
        return result.toString();
    }
    boolean sameContent(MovieWallpaperFeed other){
        return other!=null&&id==other.id&&year==other.year&&votes==other.votes&&rating==other.rating&&bytes==other.bytes&&contentDate.equals(other.contentDate)&&publishedAt.equals(other.publishedAt)&&title.equals(other.title)&&overview.equals(other.overview)&&summary.equals(other.summary)&&hash.equals(other.hash);
    }
    String details(){return year+"  TMDB "+String.format(Locale.US,"%.1f",rating)+"  "+votes+" 人评分\n"+contentDate+"\n"+overview;}
    JSONObject json()throws JSONException{
        return new JSONObject().put("schema_version",1).put("revision",revision).put("content_date",contentDate).put("timezone","Asia/Shanghai").put("published_at",publishedAt)
            .put("movie",new JSONObject().put("provider","tmdb").put("id",id).put("title",title).put("year",year).put("overview",overview).put("summary",summary).put("text_locale","zh-CN").put("rating",new JSONObject().put("provider","TMDB").put("value",rating).put("vote_count",votes)).put("source_url",sourceUrl))
            .put("image",new JSONObject().put("path",path).put("width",1920).put("height",1080).put("bytes",bytes).put("sha256",hash));
    }
}
