package home.quiet.tv;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Fixed grid with a shuffled, finite round of unseen wallpaper IDs. */
final class WallpaperWall {
    final int columns, rows;
    private final List<String> all;
    private final List<String> visible=new ArrayList<>();
    private final ArrayDeque<String> unseen=new ArrayDeque<>();
    private final Random random;

    static List<String> ids(WallpaperCatalog catalog){
        List<String> ids=new ArrayList<>();
        for(WallpaperCatalog.Entry entry:catalog.custom)
            if(entry.custom()&&!entry.video())ids.add(entry.id);
        return ids;
    }

    WallpaperWall(List<String> ids,Random random){
        this(ids,random,false);
    }

    WallpaperWall(List<String> ids,Random random,boolean posters){
        all=new ArrayList<>(ids);
        this.random=random;
        int count=ids.size();
        rows=posters?posterRows(count):rows(count);
        columns=posters?posterColumns(count):columns(count);
        refill();
        for(int i=0;i<columns*rows;i++)visible.add(unseen.removeFirst());
    }

    static int columns(int count){return count<=2?1:count<=6?2:count<=12?3:4;}
    static int rows(int count){return count==0?0:count<=3?1:count<=9?2:count<=16?3:4;}

    static int posterRows(int count){return count==0?0:count<=6?1:count<18?2:3;}
    static int posterColumns(int count){
        if(count<18)return count==0?0:Math.min(6,count/posterRows(count));
        // Three rows of eight 2:3 posters fill a 16:9 screen.
        return count>=24?8:Math.max(6,(count-1)/3);
    }

    String at(int cell){return visible.get(cell);}
    int size(){return visible.size();}
    boolean moves(){return all.size()>visible.size();}
    int cell(){return random.nextInt(visible.size());}
    String next(){
        if(!moves())return null;
        if(unseen.isEmpty())refill();
        return unseen.peekFirst();
    }
    void show(int cell){visible.set(cell,unseen.removeFirst());}
    void skip(){all.remove(unseen.removeFirst());}
    private void refill(){
        List<String> offscreen=new ArrayList<>(all);
        offscreen.removeAll(visible);
        Collections.shuffle(offscreen,random);
        unseen.addAll(offscreen);
    }
}
