package home.quiet.tv;

import android.content.Context;
import android.content.pm.ProviderInfo;
import java.io.IOException;
import org.json.*;

/** Own-package edits to recognized TCL schemas. A provider readback is not proof that its daemon uses the row. */
final class TclHomePolicy {
    static final String[] MODULES={"TGuardMemoryManager","BackgroundProcess","StandbyProcess","AppBootPolicy"};
    private static final String[] AUTHORITIES={"com.tcl.config.ConfigProvider","com.tcl.providers.config"};
    static String merge(String module,String encoded,String pkg) throws Exception {
        if(!HomeTakeoverPlan.packageName(pkg))throw new IOException("应用包名无效");
        JSONArray rows=new JSONArray(encoded);
        if("AppBootPolicy".equals(module)){
            if(rows.length()!=1)throw new IOException("不支持的自启动策略结构");
            JSONObject root=rows.getJSONObject(0);String white="serviceStartForegroundWhiteList",black="serviceStartForegroundBlackList";
            if(root.has(white)==root.has(black))throw new IOException("自启动策略模式不明确");
            String before=rows.toString(),key=root.has(white)?white:black;
            JSONObject foreground=root.getJSONObject(key);foreground.put("packageName",change(foreground.getJSONArray("packageName"),pkg,key.equals(white)));
            if(root.has("procWhiteList")){JSONObject process=root.getJSONObject("procWhiteList");process.put("packageName",change(process.getJSONArray("packageName"),pkg,true));}
            if(!before.equals(rows.toString())){
                Object version=root.opt("version"),sub=root.opt("subVersion");
                if(!(version instanceof Number) || !(sub instanceof Number))throw new IOException("缺少自启动策略版本");
                long major=((Number)version).longValue(),minor=((Number)sub).longValue();
                if(major<0 || major>=Integer.MAX_VALUE || minor<0 || minor>511 || ((Number)version).doubleValue()!=major || ((Number)sub).doubleValue()!=minor)throw new IOException("自启动策略版本不受支持");
                root.put("version",minor==511?major+1:major).put("subVersion",minor==511?0:minor+1);
            }
        }else{
            String positive="TGuardMemoryManager".equals(module)?"KillWhiteList":"BackgroundProcess".equals(module)?"backgroundProcessIgnoreList":"";
            String[] negatives="BackgroundProcess".equals(module)?new String[]{"backgroundProcessBlackList","backgroundProcesKillList"}
                    :"StandbyProcess".equals(module)?new String[]{"standbyProcessBlackList","standbyWakeLockBlackList"}:new String[0];
            if(positive.isEmpty() && negatives.length==0)throw new IOException("未知后台策略");
            java.util.HashSet<String> names=new java.util.HashSet<>();boolean recognized=false;
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);String name=row.getString("name");
                if(!names.add(name))throw new IOException("后台策略字段重复");
                boolean add=!positive.isEmpty() && positive.equals(name),remove=java.util.Arrays.asList(negatives).contains(name);
                if(add || remove){row.put("value",change(row.getJSONArray("value"),pkg,add));recognized=true;}
            }
            if(!recognized || "TGuardMemoryManager".equals(module) && !names.contains(positive))throw new IOException("未识别后台策略字段");
        }
        return rows.toString();
    }
    private static JSONArray change(JSONArray source,String own,boolean include) throws Exception {
        JSONArray result=new JSONArray();boolean found=false;
        for(int i=0;i<source.length();i++){
            Object item=source.get(i);if(!(item instanceof String))throw new IOException("后台名单结构不受支持");
            if(own.equals(item)){found=true;if(include)result.put(item);}else result.put(item);
        }
        if(include && !found)result.put(own);return result;
    }
    static String content(String output) throws Exception {
        if(output.split("Row:",-1).length!=2)throw new IOException("后台策略查询不是唯一记录");
        int start=output.indexOf("config_content=");if(start<0)throw new IOException("缺少后台策略内容");
        String encoded=output.substring(start+15).trim();new JSONArray(encoded);return encoded;
    }
    private static String project(String output) throws IOException {
        java.util.regex.Matcher match=java.util.regex.Pattern.compile("\\bproject_id=([0-9]+), config_content=").matcher(output);
        if(!match.find())throw new IOException("后台策略项目不明确");return match.group(1);
    }
    private static String quote(String value){return "'"+value.replace("'","'\\''")+"'";}
    static void configure(Context c,AdbController adb,SetupOperations.Ticket ticket) throws Exception {
        HomeGuardState guard=new HomeGuardState(c);String selected=guard.selected();
        if(adb.user!=0){guard.preferences.edit().putString("tclPolicyStatus","非主用户，未修改全局后台策略").apply();return;}
        SetupStore store=new SetupStore(c);JSONObject records=store.get().optJSONObject("tclPolicies");if(records==null)records=new JSONObject();
        int verified=0,unavailable=0;
        for(String module:MODULES){
            boolean matched=false;
            for(String authority:AUTHORITIES){
                ticket.check();adb.validateUser();if(!guard.ready() || !selected.equals(guard.selected()))throw new IOException("守护已暂停或目标已改变");
                ProviderInfo provider=c.getPackageManager().resolveContentProvider(authority,0);
                if(provider==null || !"com.tcl.providers.config".equals(provider.packageName)
                        || (provider.applicationInfo.flags&android.content.pm.ApplicationInfo.FLAG_SYSTEM)==0)continue;
                String uri="content://"+authority+"/"+module,query="content query --user 0 --uri "+uri+" --projection project_id:config_content";
                String raw,before,after,id;
                try{raw=adb.shell(query);}catch(AdbController.CommandFailure unsupported){continue;}
                try{before=content(raw);id=project(raw);after=merge(module,before,c.getPackageName());}
                catch(JSONException|IOException unsupported){continue;}
                matched=true;String key=authority+"/"+module+"/"+id;
                JSONObject record=records.optJSONObject(key);
                if(record==null)record=new JSONObject().put("before",before).put("user",adb.user).put("firmware",android.os.Build.FINGERPRINT);
                record.put("requested",after).put("readback",false);records.put(key,record);store.put("tclPolicies",records);
                if(!new JSONArray(before).toString().equals(after)){
                    ticket.check();adb.validateUser();if(!guard.ready() || !selected.equals(guard.selected()))throw new IOException("守护已暂停或目标已改变");
                    // Compare-and-set preserves a policy changed concurrently by the OEM daemon.
                    String where="project_id="+id+" AND config_content='"+before.replace("'","''")+"'";
                    try{adb.shell("content update --user 0 --uri "+uri+" --bind "+quote("config_content:s:"+after)+" --where "+quote(where));}
                    catch(AdbController.CommandFailure refused){break;}
                }
                String readback;
                try{readback=adb.shell(query);}catch(AdbController.CommandFailure refused){break;}
                if(id.equals(project(readback)) && after.equals(new JSONArray(content(readback)).toString())){
                    record.put("readback",true);store.put("tclPolicies",records);verified++;
                }
                break;
            }
            if(!matched)unavailable++;
        }
        guard.preferences.edit().putString("tclPolicyStatus","后台策略读回 "+verified+"/4  不适用 "+unavailable+"  固件实际生效待验证").apply();
    }
    private TclHomePolicy(){}
}
