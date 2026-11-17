package home.quiet.tv;

import java.io.*;
import org.json.*;

/** Shared metadata and aspect-fit geometry for committed and draft library artwork. */
final class LibraryCover {
    private LibraryCover(){}
    static boolean library(JSONObject entry){return entry!=null&&"library".equals(entry.optString("source"));}
    static boolean upload(JSONObject entry){return entry!=null&&"upload".equals(entry.optString("source"));}
    static boolean generated(JSONObject entry){return entry!=null&&"generated".equals(entry.optString("source"));}
    static boolean contain(JSONObject entry){return (library(entry)||upload(entry)||generated(entry))&&"contain".equals(entry.optString("fit"));}
    static JSONObject uploadMetadata(String shape)throws JSONException{
        return new JSONObject().put("source","upload").put("shape",shape).put("fit","contain").put("width","rectangle".equals(shape)?720:512).put("height","rectangle".equals(shape)?405:512);
    }
    static JSONObject metadata(IconLibraryCatalog.Item item,String shape,String form,int version)throws JSONException{
        return new JSONObject().put("source","library").put("theme",item.id).put("style",item.style).put("shape",shape).put("form",form).put("catalogVersion",version).put("fit","contain").put("width","rectangle".equals(shape)?720:512).put("height","rectangle".equals(shape)?405:512);
    }
    static JSONObject frameMetadata(String style,String shape,String form,String sourceKey)throws JSONException{
        return new JSONObject().put("source","generated").put("style",style).put("shape",shape).put("form",form).put("frameVersion",IconLibraryFrame.VERSION).put("originalKey",sourceKey).put("fit","contain").put("width","rectangle".equals(shape)?720:512).put("height","rectangle".equals(shape)?405:512);
    }
    static void validate(JSONObject entry,boolean allowLibrary)throws IOException,JSONException{
        validate(entry,allowLibrary?2:1);
    }
    static void validate(JSONObject entry,int version)throws IOException,JSONException{
        DesktopBackupRecord.only(entry,DesktopBackupRecord.names(library(entry)?"file width height source theme style shape form catalogVersion fit":generated(entry)?"file width height source style shape form frameVersion originalKey fit":upload(entry)?"file width height source shape fit":"file width height"));
        for(String field:new String[]{"width","height"})DesktopBackupRecord.require(entry.get(field) instanceof Integer||entry.get(field) instanceof Long,"封面尺寸类型无效");
        DesktopBackupRecord.require(entry.getString("file").matches("cover-[a-f0-9-]{36}\\.png"),"封面文件名无效");
        if(!library(entry)&&!upload(entry)&&!generated(entry)){DesktopBackupRecord.require(entry.getInt("width")==720&&entry.getInt("height")==406,"旧封面尺寸无效");return;}
        String shape=entry.getString("shape");
        DesktopBackupRecord.require(IconLibraryCatalog.contains(IconLibraryCatalog.SHAPES,shape)&&"contain".equals(entry.getString("fit")),"图标形状无效");
        if(upload(entry))DesktopBackupRecord.require(version>=3,"此备份版本不支持上传图标");
        else if(generated(entry)){
            DesktopBackupRecord.require(version>=6,"此备份版本不支持自动生成图标");
            DesktopBackupRecord.require(entry.get("frameVersion") instanceof Integer&&entry.getInt("frameVersion")>=1&&entry.getString("originalKey").matches("[a-f0-9]{64}")&&IconLibraryCatalog.styleId(entry.getString("style"))&&IconLibraryCatalog.contains(IconLibraryCatalog.forms(shape),entry.getString("form")),"生成图标信息无效");
        }else{
            DesktopBackupRecord.require(entry.get("catalogVersion") instanceof Integer||entry.get("catalogVersion") instanceof Long,"图库素材版本类型无效");
            DesktopBackupRecord.require(version>=2&&entry.getString("theme").matches("[a-z][a-z0-9-]{0,47}")&&IconLibraryCatalog.styleId(entry.getString("style"))&&IconLibraryCatalog.contains(IconLibraryCatalog.FORMS,entry.getString("form"))&&entry.getInt("catalogVersion")>=1,"图库封面信息无效");
        }
        DesktopBackupRecord.require(entry.getInt("width")==("rectangle".equals(shape)?720:512)&&entry.getInt("height")==("rectangle".equals(shape)?405:512),"图库封面尺寸无效");
    }
    static boolean same(JSONObject a,JSONObject b){
        if(generated(a)&&generated(b)){for(String name:new String[]{"source","style","shape","form","frameVersion","originalKey","fit"})if(!a.optString(name).equals(b.optString(name)))return false;return true;}
        if(!library(a)||!library(b))return false;
        for(String name:new String[]{"source","theme","style","shape","form","catalogVersion","fit"})if(!a.optString(name).equals(b.optString(name)))return false;return true;
    }
    static boolean pngSize(File file,int width,int height)throws IOException{
        try(DataInputStream input=new DataInputStream(new FileInputStream(file))){return input.readLong()==0x89504e470d0a1a0aL&&input.readInt()==13&&input.readInt()==0x49484452&&input.readInt()==width&&input.readInt()==height;}
        catch(EOFException invalid){return false;}
    }
    static float[] fit(float sourceWidth,float sourceHeight,float width,float height){
        if(sourceWidth<=0||sourceHeight<=0||width<=0||height<=0)return new float[]{0,0,0,0};
        float scale=Math.min(width/sourceWidth,height/sourceHeight),w=sourceWidth*scale,h=sourceHeight*scale;return new float[]{(width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2};
    }
    // Extend a composed icon's background past its old rounded top/bottom edges.
    // Keeping the full artwork width preserves wide wordmarks when reshaping it.
    static void extendBackground(int[] pixels,int width,int height){
        int[] tops=new int[width],bottoms=new int[width],colors=new int[width*2];int inset=height/32,radius=width/32;
        for(int x=0;x<width;x++){
            int top=0,bottom=height-1;while(top<height&&(pixels[top*width+x]>>>24)<250)top++;if(top==height){tops[x]=-1;continue;}
            while(bottom>top&&(pixels[bottom*width+x]>>>24)<250)bottom--;
            tops[x]=Math.min(bottom,top+inset);bottoms[x]=Math.max(tops[x],bottom-inset);
            colors[x]=pixels[tops[x]*width+x]|0xff000000;colors[width+x]=pixels[bottoms[x]*width+x]|0xff000000;
        }
        for(int x=0;x<width;x++)if(tops[x]>=0){
            int first=edgeColor(colors,x,width,radius),last=edgeColor(colors,width+x,width,radius);
            for(int y=0;y<=tops[x];y++)pixels[y*width+x]=first;for(int y=bottoms[x];y<height;y++)pixels[y*width+x]=last;
        }
    }
    private static int edgeColor(int[] colors,int x,int width,int radius){
        int start=x/width*width,r=0,g=0,b=0,count=0;
        for(int i=Math.max(start,x-radius);i<=Math.min(start+width-1,x+radius);i++){int color=colors[i];if(color==0)continue;r+=(color>>16)&255;g+=(color>>8)&255;b+=color&255;count++;}
        return count==0?0:0xff000000|r/count<<16|g/count<<8|b/count;
    }
}
