package home.quiet.tv;

import java.util.*;

/** Bounded admission only. Formatting, redaction and encoding belong to the writer thread. */
final class DiagnosticEventBuffer {
    static final int CAPACITY=32,NORMAL_LIMIT=24,REPEAT_KEYS=16;
    static final long REPEAT_MS=1000;
    static final class Entry {
        final long at;final String level,event,detail;final boolean important;
        Entry(long at,String level,String event,String detail,boolean important){this.at=at;this.level=level;this.event=event;this.detail=detail;this.important=important;}
        String line(){return at+" "+level+" "+event+" "+detail;}
    }
    private static final class Repeat {
        String level,detail;long at;int suppressed;
        Repeat(String level,String detail,long at){this.level=level;this.detail=detail;this.at=at;}
    }
    private final ArrayDeque<Entry> entries=new ArrayDeque<>();
    private final LinkedHashMap<String,Repeat> repeats=new LinkedHashMap<>();
    private int dropped,droppedTotal,suppressedTotal,evictedSuppressed;
    private static String bounded(String value,int limit){return value==null?"":value.length()>limit?value.substring(0,limit):value;}
    private static boolean repeatable(String level,String event){
        if(event.startsWith("process.uncaught") || event.endsWith(".begin") || event.endsWith(".result"))return false;
        return "ERROR".equals(level)
                || "home.action.focus".equals(event) || "home_guard.status".equals(event) || "home_guard.settle".equals(event);
    }
    synchronized boolean offer(String level,String event,String detail,long wall,long elapsed){
        event=bounded(event,96);detail=bounded(detail,1000);
        Repeat previous=repeatable(level,event)?repeats.get(event):null;
        if(previous!=null && previous.level.equals(level) && previous.detail.equals(detail)
                && elapsed>=previous.at && elapsed-previous.at<REPEAT_MS){previous.suppressed++;suppressedTotal++;return false;}
        boolean important="ERROR".equals(level) || event.endsWith(".begin") || event.endsWith(".result") || "process.start".equals(event);
        if(!important && entries.size()>=NORMAL_LIMIT){dropped++;droppedTotal++;return false;}
        if(entries.size()>=CAPACITY){
            boolean removed=false;
            for(Iterator<Entry> iterator=entries.iterator();iterator.hasNext();)if(!iterator.next().important){iterator.remove();removed=true;break;}
            dropped++;droppedTotal++;
            if(!removed)return false;
        }
        entries.addLast(new Entry(wall,level,event,detail,important));
        if(repeatable(level,event)){
            if(previous==null){
                if(repeats.size()>=REPEAT_KEYS){Iterator<Repeat> iterator=repeats.values().iterator();evictedSuppressed+=iterator.next().suppressed;iterator.remove();}
                repeats.put(event,new Repeat(level,detail,elapsed));
            }else{previous.level=level;previous.detail=detail;previous.at=elapsed;}
        }
        return true;
    }
    // Only the serialized writer calls take; summaries are built there, never on the caller thread.
    synchronized List<Entry> take(int limit,long wall){
        List<Entry> batch=new ArrayList<>();
        for(int i=0;i<limit && !entries.isEmpty();i++)batch.add(entries.removeFirst());
        if(!entries.isEmpty())return batch;
        if(dropped>0){batch.add(new Entry(wall,"WARN","log.queue_dropped","count="+dropped,true));dropped=0;}
        if(evictedSuppressed>0){batch.add(new Entry(wall,"INFO","log.repeat_suppressed","event=evicted_events count="+evictedSuppressed,false));evictedSuppressed=0;}
        for(Map.Entry<String,Repeat> entry:repeats.entrySet())if(entry.getValue().suppressed>0){
            batch.add(new Entry(wall,"INFO","log.repeat_suppressed","event="+entry.getKey()+" count="+entry.getValue().suppressed,false));entry.getValue().suppressed=0;
        }
        return batch;
    }
    synchronized boolean hasEntries(){return !entries.isEmpty();}
    synchronized void discard(){dropped+=entries.size();droppedTotal+=entries.size();entries.clear();}
    synchronized int droppedTotal(){return droppedTotal;}
    synchronized int suppressedTotal(){return suppressedTotal;}
}
