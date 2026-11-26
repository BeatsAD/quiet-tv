package home.quiet.tv;

import androidx.core.util.AtomicFile;
import java.io.*;
import java.util.*;
import org.json.*;

/** Shortcut membership is independent of the tools' own content and desktop preferences. */
final class QuickControlStore {
    static final String SETTINGS="desktopSettings";
    static final String SCREENSAVER_SOUND="saverSound";
    static final List<String> ALL=Collections.unmodifiableList(Arrays.asList("keySounds","brightness","network","source","mute",SCREENSAVER_SOUND,"original","transfer","memoShortcut","wallpaper","screensaver","boot","bluetooth","volume","child","apps","clock","weather","systemSettings","homeBlur","dockScale","dockMagnification",SETTINGS));
    static final List<String> DEFAULT=Collections.unmodifiableList(Arrays.asList(SETTINGS,"network","bluetooth","keySounds","source","wallpaper","screensaver","mute","transfer","brightness","volume"));
    static final int MAX_BUTTONS=8, RANGE_LIMIT=2;
    static final List<String> RANGES=Collections.unmodifiableList(Arrays.asList("brightness","volume","homeBlur","dockScale","dockMagnification"));
    // Legacy files and backups did not store the required entry's position.
    static List<String> normalize(List<String> ids){List<String> result=new ArrayList<>(ids);if(!result.contains(SETTINGS))result.add(0,SETTINGS);return result;}
    static int buttonCount(List<String> ids){int n=0;for(String id:ids)if(!range(id)&&!SETTINGS.equals(id))n++;return n;}
    static List<String> buttons(List<String> ids){List<String> result=new ArrayList<>();for(String id:normalize(ids))if(!range(id))result.add(id);return result;}
    static List<String> allRanges(List<String> ids){List<String> result=new ArrayList<>();for(String id:ids)if(range(id))result.add(id);return result;}
    static int rangeSlotsRemaining(List<String> ids){return Math.max(0,RANGE_LIMIT-allRanges(ids).size());}
    static List<String> ranges(List<String> ids){return ranges(ids,"");}
    static List<String> ranges(List<String> ids,String temporary){
        List<String> result=allRanges(ids);
        if(result.size()>RANGE_LIMIT)result=new ArrayList<>(result.subList(0,RANGE_LIMIT));
        if(range(temporary)&&!result.contains(temporary)){
            if(result.size()==RANGE_LIMIT)result.set(RANGE_LIMIT-1,temporary);else result.add(temporary);
        }
        return result;
    }
    static List<String> overflow(List<String> ids){List<String> result=buttons(ids);result.remove(SETTINGS);return result.subList(Math.min(MAX_BUTTONS,result.size()),result.size());}
    static List<String> grid(List<String> ids){List<String> result=new ArrayList<>();int count=0;for(String id:buttons(ids))if(SETTINGS.equals(id)||count++<MAX_BUTTONS)result.add(id);return result;}
    static boolean canAdd(List<String> ids,String id,String replace){return !SETTINGS.equals(id)&&!SETTINGS.equals(replace)&&!ids.contains(id)&&(range(id)?allRanges(ids).size()-(range(replace)&&ids.contains(replace)?1:0)<RANGE_LIMIT:buttonCount(ids)-(replace!=null&&!replace.isEmpty()&&!range(replace)?1:0)<MAX_BUTTONS);}
    static void validateLimit(List<String> before,List<String> ids)throws IOException{
        validate(ids);
        if(!ids.contains(SETTINGS))throw new IOException("桌面设置必须保留，可调整位置");
        if(buttonCount(ids)>MAX_BUTTONS)throw new IOException("普通快捷按钮最多 8 个，请先选定保留内容");
        List<String> ranges=allRanges(ids);
        // Old data can still be saved unchanged, reordered, or reduced. It cannot gain an item while over capacity.
        if(ranges.size()>RANGE_LIMIT&&!allRanges(before).containsAll(ranges))throw new IOException("快捷调节最多 2 个，请先移除多余调节项");
    }
    static final class Snapshot {
        final List<String> ids;final long revision;
        Snapshot(List<String> ids,long revision){this.ids=Collections.unmodifiableList(new ArrayList<>(ids));this.revision=revision;}
    }
    static final class Changed extends IOException {Changed(){super("快捷功能已在其他位置修改，请重新读取后再编辑");}}
    static final Object LOCK=new Object();
    private static final class Cached {
        final File file;final Snapshot snapshot;
        Cached(File file,Snapshot snapshot){this.file=file;this.snapshot=snapshot;}
    }
    private static volatile Cached cached;
    private final File directory,file;
    QuickControlStore(File directory){this.directory=directory;file=new File(directory,"shortcuts.json");}
    Snapshot cached(){Cached value=cached;return value!=null&&file.equals(value.file)?value.snapshot:null;}
    private Snapshot remember(Snapshot value){cached=new Cached(file,value);return value;}
    Snapshot read()throws IOException{synchronized(LOCK){try{return remember(load());}catch(IOException error){Cached value=cached;if(value!=null&&file.equals(value.file))cached=null;throw error;}}}
    Snapshot save(long expected,List<String> ids)throws IOException{return update(expected,ids,false);}
    Snapshot restore(long expected,List<String> ids)throws IOException{return update(expected,ids,true);}
    private Snapshot update(long expected,List<String> ids,boolean structuralOnly)throws IOException{synchronized(LOCK){
        validate(ids);Snapshot current=remember(load());
        List<String> normalized=normalize(ids);
        if(!current.ids.equals(normalized)&&current.revision!=expected)throw new Changed();
        if(!structuralOnly)validateLimit(current.ids,ids);
        ids=normalized;if(current.ids.equals(ids))return current;
        if(current.revision==Long.MAX_VALUE)throw new IOException("快捷功能版本无法继续保存");Snapshot next=new Snapshot(ids,current.revision+1);write(next);return remember(next);
    }}
    private static void validate(List<String> ids)throws IOException {if(ids==null||ids.size()>ALL.size()||new HashSet<>(ids).size()!=ids.size()||!ALL.containsAll(ids))throw new IOException("快捷功能配置无效");}
    private Snapshot load()throws IOException{
        AtomicFile atomic=new AtomicFile(file);if(!file.exists()&&!new File(file+".bak").exists()){if(directory.exists()&&!directory.isDirectory())throw new IOException("快捷功能存储不可用");return new Snapshot(DEFAULT,0);}
        try{JSONObject data=new JSONObject(new String(atomic.readFully(),Charsets.UTF_8));if(data.getInt("version")!=1)throw new IOException("不支持的快捷功能格式");long revision=data.getLong("revision");if(revision<0)throw new IOException("快捷功能版本无效");JSONArray array=data.getJSONArray("items");List<String> ids=new ArrayList<>();for(int i=0;i<array.length();i++)ids.add(array.getString(i));validate(ids);return new Snapshot(normalize(ids),revision);}
        catch(JSONException error){throw new IOException("无法读取快捷功能，原文件已保留",error);}
    }
    private void write(Snapshot value)throws IOException{
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("快捷功能存储不可用");byte[] bytes;
        try{bytes=new JSONObject().put("version",1).put("revision",value.revision).put("items",new JSONArray(value.ids)).toString().getBytes(Charsets.UTF_8);}catch(JSONException error){throw new IOException("无法编码快捷功能",error);}
        AtomicFile atomic=new AtomicFile(file);FileOutputStream output=null;
        try{output=atomic.startWrite();output.write(bytes);output.getFD().sync();atomic.finishWrite(output);output=null;if(!Arrays.equals(bytes,atomic.readFully()))throw new IOException("快捷功能保存未完成");DesktopBackups.changed();}
        catch(IOException error){if(output!=null)atomic.failWrite(output);throw error;}
    }
    static String title(String id,boolean chinese){if(SETTINGS.equals(id))return chinese?"桌面设置":"Desktop settings";int index=ALL.indexOf(id);if(index<0)return "";return (chinese?ZH:EN)[index];}
    static String shortTitle(String id,boolean chinese){return SCREENSAVER_SOUND.equals(id)&&!chinese?"Saver sound":title(id,chinese);}
    static boolean range(String id){return RANGES.contains(id);}
    static int rangeMinimum(String id){return "brightness".equals(id)?20:"dockScale".equals(id)?70:"dockMagnification".equals(id)?100:0;}
    static int rangeMaximum(String id){return "dockScale".equals(id)?130:"dockMagnification".equals(id)?160:100;}
    static int rangeValue(String id,DesktopSettingsStore desktop){switch(id){case "brightness":return desktop.homeWallpaperBrightness();case "homeBlur":return desktop.homeWallpaperBlur();case "dockScale":return desktop.appearance("dock-scale");case "dockMagnification":return desktop.appearance("dock-magnification");default:throw new IllegalArgumentException("Unknown desktop adjustment");}}
    static String rangeProperty(String id){switch(id){case "brightness":return "brightness";case "homeBlur":return "blur";case "dockScale":return "dock-scale";case "dockMagnification":return "dock-magnification";default:throw new IllegalArgumentException("Unknown desktop adjustment");}}
    private static final String[] ZH={"按键音","背景明暗","网络设置","信号源","壁纸声音","屏保声音","原桌面","文件传输","备忘录","更换壁纸","立即屏保","开机直达","蓝牙","音量","儿童模式","应用管理","时钟","天气","系统设置","首页背景模糊度","Dock 栏大小","Dock 聚焦放大幅度"};
    private static final String[] EN={"Key sounds","Background brightness","Network","Inputs","Wallpaper sound","Screensaver sound","Original launcher","File transfer","My notes","Change background","Screensaver","Startup destination","Bluetooth","Volume","Child mode","Manage apps","Clock","Weather","System settings","Home background blur","Dock size","Dock magnification"};
}
