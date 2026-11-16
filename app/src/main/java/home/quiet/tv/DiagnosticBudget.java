package home.quiet.tv;

import java.util.*;
import org.json.*;

/** Admission for extra probes only. Counts include records intentionally sampled out. */
final class DiagnosticBudget {
    private static final class Count {long bucket,last=Long.MIN_VALUE/2,accepted,suppressed;int used;}
    private final Map<String,Count> counts=new LinkedHashMap<>();
    synchronized boolean take(String key,long now,long interval,int limit){
        Count count=counts.get(key);if(count==null){if(counts.size()>=64)key="other";count=counts.get(key);if(count==null){count=new Count();count.bucket=now;counts.put(key,count);}}
        if(now-count.bucket>=30000||now<count.bucket){count.bucket=now;count.used=0;}
        if(now-count.last<interval||count.used>=limit){count.suppressed++;return false;}
        count.last=now;count.used++;count.accepted++;return true;
    }
    synchronized void beginWindow(long now){for(Count count:counts.values()){count.bucket=now;count.used=0;count.last=Long.MIN_VALUE/2;}}
    synchronized JSONObject json()throws JSONException{
        JSONObject result=new JSONObject();for(Map.Entry<String,Count> item:counts.entrySet())result.put(item.getKey(),new JSONObject().put("accepted",item.getValue().accepted).put("sampledOut",item.getValue().suppressed));return result;
    }
}
