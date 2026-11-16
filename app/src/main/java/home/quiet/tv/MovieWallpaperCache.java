package home.quiet.tv;

import androidx.core.util.AtomicFile;
import java.io.*;
import java.security.*;
import java.text.*;
import java.util.*;

/** Four bounded slots keep today and three prepared dates separate from the displayed wallpaper. */
final class MovieWallpaperCache {
    static final int FUTURE_DAYS=3;
    private final File directory;
    private final byte[] publicKey;
    static final class Entry {
        final MovieWallpaperFeed movie;
        final File image;
        Entry(MovieWallpaperFeed movie,File image){this.movie=movie;this.image=image;}
    }
    MovieWallpaperCache(File directory,byte[] publicKey){this.directory=directory;this.publicKey=publicKey.clone();}
    static String date(String day,int offset)throws IOException{
        SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd",Locale.US);format.setTimeZone(TimeZone.getTimeZone("UTC"));format.setLenient(false);
        ParsePosition position=new ParsePosition(0);Date value=format.parse(day,position);
        if(!day.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")||value==null||position.getIndex()!=day.length())throw new IOException("Invalid cinema date");
        Calendar calendar=Calendar.getInstance(TimeZone.getTimeZone("UTC"));calendar.setTime(value);calendar.add(Calendar.DAY_OF_YEAR,offset);return format.format(calendar.getTime());
    }
    private int slot(String day)throws IOException{
        date(day,0);SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd",Locale.US);format.setTimeZone(TimeZone.getTimeZone("UTC"));
        try{long days=format.parse(day).getTime()/86400000L;return (int)((days%4+4)%4);}catch(ParseException failure){throw new IOException(failure);}
    }
    private File file(String day,String suffix)throws IOException{return new File(directory,"slot-"+slot(day)+suffix);}
    synchronized Entry read(String day){Entry entry=readSlot(day);return entry!=null&&day.equals(entry.movie.contentDate)?entry:null;}
    private Entry readSlot(String day){
        try{
            MovieWallpaperFeed movie=MovieWallpaperFeed.verify(new AtomicFile(file(day,".signed.json")).readFully(),publicKey);
            if(slot(day)!=slot(movie.contentDate))return null;
            File image=file(day,".jpg");
            try(InputStream input=new AtomicFile(image).openRead()){verifyImage(input,image.length(),movie);}
            return new Entry(movie,image);
        }catch(IOException|RuntimeException failure){return null;}
    }
    synchronized void save(MovieWallpaperFeed movie,File original,DailyWallpaperLoader.Check check)throws IOException{
        if(movie.envelope==null)throw new IOException("Cinema cache requires a signed feed");
        MovieWallpaperFeed verified=MovieWallpaperFeed.verify(movie.envelope,publicKey);
        if(verified.revision!=movie.revision||!verified.sameContent(movie))throw new IOException("Cinema metadata changed");
        Entry previous=readSlot(movie.contentDate);
        if(previous!=null){
            if(previous.movie.contentDate.compareTo(movie.contentDate)>0||previous.movie.contentDate.equals(movie.contentDate)&&(movie.revision<previous.movie.revision||movie.revision==previous.movie.revision&&!movie.sameContent(previous.movie)))throw new IOException("Cinema preparation moved backwards");
            if(previous.movie.contentDate.equals(movie.contentDate)&&movie.revision==previous.movie.revision)return;
        }
        try(InputStream input=new FileInputStream(original)){verifyImage(input,original.length(),movie);}
        check.run();if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cinema cache storage unavailable");
        AtomicFile image=new AtomicFile(file(movie.contentDate,".jpg"));FileOutputStream output=null;
        try{
            output=image.startWrite();try(InputStream input=new FileInputStream(original)){byte[] bytes=new byte[8192];int count;while((count=input.read(bytes))!=-1){check.run();output.write(bytes,0,count);}}
            check.run();image.finishWrite(output);output=null;
        }finally{if(output!=null)image.failWrite(output);}
        AtomicFile metadata=new AtomicFile(file(movie.contentDate,".signed.json"));
        try{output=metadata.startWrite();output.write(movie.envelope);check.run();metadata.finishWrite(output);output=null;}
        finally{if(output!=null)metadata.failWrite(output);}
    }
    private static void verifyImage(InputStream input,long bytes,MovieWallpaperFeed movie)throws IOException{
        if(bytes!=movie.bytes)throw new IOException("Cinema cache image size changed");
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);
            StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format(Locale.US,"%02x",value&255));
            if(!movie.hash.equals(hash.toString()))throw new IOException("Cinema cache image checksum changed");
        }catch(NoSuchAlgorithmException failure){throw new IOException(failure);}
    }
}
