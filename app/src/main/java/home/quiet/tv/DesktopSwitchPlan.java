package home.quiet.tv;

import java.io.IOException;
import java.util.*;
import org.json.*;

/** Explicit two-way switching. The saved baseline survives process death; writes are never replayed. */
final class DesktopSwitchPlan {
    interface Port {
        JSONObject read() throws Exception;
        JSONObject load() throws Exception;
        void save(JSONObject record) throws Exception;
        void check() throws Exception;
        void enabled(boolean component,int value) throws Exception;
        void home(String component,boolean deferVerification) throws Exception;
        void pause() throws Exception;
        void resume(JSONObject baseline) throws Exception;
        void launch(boolean stock) throws Exception;
        default JSONObject runtime() throws Exception {return new JSONObject();}
        default JSONObject management() throws Exception {return new JSONObject();}
        default void management(JSONObject record) throws Exception {throw new IOException("管理记录不可用");}
        default void verify(String scene,String result) throws Exception {throw new IOException("此路线不支持记录验证结果");}
    }
    private final Port port;
    DesktopSwitchPlan(Port port){this.port=port;}
    private static boolean active(JSONObject record){return record!=null&&!"quiet".equals(record.optString("phase"));}
    private static void require(boolean value,String message)throws IOException{if(!value)throw new IOException(message);}
    private String revision(JSONObject env,JSONObject record)throws Exception{return PhoneSettingsState.revision(new JSONObject().put("environment",env).put("record",record).put("management",port.management()));}
    JSONObject state() throws Exception {
        JSONObject env=port.read(),record=port.load();boolean returning=active(record);String reason=env.optString("problem");
        if(reason.isEmpty()&&returning)try{validate(env,record.getJSONObject("baseline"));validateReturn(env,record);}catch(Exception failure){reason=failure.getMessage();}
        JSONArray actions=new JSONArray();
        if(returning){
            JSONObject baseline=record.getJSONObject("baseline");String problem=reason.isEmpty()?returnProblem(env,baseline,false):reason;
            actions.put(action("quiet","切回 Quiet TV",problem,"恢复切换前的 HOME 与接管配置，保留原桌面启用。"));
            if(!baseline.optBoolean("stockEnabled")&&!baseline.optBoolean("dependsOnStock"))actions.put(action("quiet-disable","切回并恢复原禁用状态",reason.isEmpty()?returnProblem(env,baseline,true):reason,"切回 Quiet TV 后，恢复本次切换前的原桌面包及组件状态。"));
        }else{
            String problem=reason.isEmpty()?stockProblem(env):reason;
            actions.put(action("stock",env.optBoolean("stockEnabled")?"切换到原桌面":"启用并切换到原桌面",problem,"暂停现有接管后使用原桌面；可从 Quiet TV 或手机设置切回。"));
        }
        JSONObject legacy=new JSONObject().put("revision",revision(env,record)).put("phase",returning?record.optString("phase"):"quiet").put("original",env.optString("original")).put("originalLabel",env.optString("label")).put("originalEnabled",env.optBoolean("stockEnabled")).put("originalStatus",env.optBoolean("stockEnabled")?"已启用":"已禁用")
            .put("mode",returning?("stock".equals(record.optString("phase"))?"原桌面":"切换待核对"):"Quiet TV").put("route",env.optString("routeLabel")).put("message",returning?record.optString("message"):reason).put("problem",reason).put("actions",actions);
        return DesktopManagementState.present(legacy,env,port.runtime(),record,port.management());
    }
    private static JSONObject action(String id,String title,String problem,String explanation)throws JSONException{return new JSONObject().put("id",id).put("title",title).put("enabled",problem.isEmpty()).put("reason",problem).put("explanation",explanation);}
    private static String stockProblem(JSONObject e){
        if(e.optBoolean("externalHelper"))return "当前有独立中转助手接管，请先在桌面管理中核对。";
        if(!e.optBoolean("homeKnown")&&!e.optBoolean("retainedHome"))return "系统 HOME 尚未确认，请先核对管理连接与默认桌面。";
        if(!e.optBoolean("packageEnabled")&&(!e.optBoolean("canEnable")||!e.optBoolean("canRestorePackage")))return "需要已授权的管理连接，且系统须支持恢复原包状态。";
        if(!e.optBoolean("componentEnabled")&&(!e.optBoolean("canEnable")||!e.optBoolean("canRestoreComponent")))return "需要管理连接以启用并恢复原桌面组件。";
        if(!e.optString("stockHome").equals(e.optString("home"))&&!e.optBoolean("canHome"))return "需要已授权的管理连接以切换并核对 HOME。";
        return "";
    }
    private static String returnProblem(JSONObject e,JSONObject b,boolean disable){
        if(!disable&&b.optBoolean("requiresStockDisabled"))return "此系统需恢复原禁用状态，HOME 才能返回 Quiet TV。";
        if(!e.optString("home").equals(b.optString("home"))&&!e.optBoolean("canHome"))return "需要已授权的管理连接以恢复切换前的 HOME。";
        if(disable&&((e.optInt("packageSetting")!=b.optInt("packageSetting")&&!e.optBoolean("canRestorePackage"))||(e.optInt("componentSetting")!=b.optInt("componentSetting")&&!e.optBoolean("canRestoreComponent"))))return "管理连接或恢复原禁用状态的命令不可用。";
        if(disable&&(b.optBoolean("dependsOnStock")||b.optString("home").equals(b.optString("original"))))return "原接管方案依赖原桌面，必须保留启用。";
        return "";
    }
    private static void validate(JSONObject e,JSONObject b)throws Exception {
        for(String key:new String[]{"original","guardKey","fingerprint","stockHome"})require(e.optString(key).equals(b.optString(key)),"原桌面记录或接管配置已变化，请核对后重试。");
        require(e.getLong("version")==b.getLong("version")&&e.getInt("user")==b.getInt("user"),"原桌面版本或当前用户已变化，请核对后重试。");
    }
    private static void validateReturn(JSONObject e,JSONObject r)throws Exception {
        JSONObject b=r.getJSONObject("baseline");if(r.optBoolean("paused")&&!r.optBoolean("resumeAttempted"))require("OFF".equals(e.getJSONObject("guard").optString("mode")),"接管开关已被其他操作修改，请先核对。");String home=e.optString("home");require((e.optBoolean("homeKnown")||e.optBoolean("retainedHome"))&&(home.equals(b.optString("home"))||home.equals(b.optString("stockHome"))),"当前 HOME 已被其他操作修改，保留现状，请核对。" );
        for(String key:new String[]{"packageSetting","componentSetting"})require(e.optInt(key,-1)==b.getInt(key)||e.optInt(key,-1)==1,"原桌面启用状态已被其他操作修改，保留现状，请核对。");
    }
    private void save(JSONObject r,String phase,String message)throws Exception{r.put("phase",phase).put("message",message);port.save(r);}
    private void enable(JSONObject r,boolean component,int wanted)throws Exception {
        String key=component?"componentSetting":"packageSetting";JSONObject e=port.read();if(e.getInt(key)==wanted)return;
        r.put("step",component?"恢复原桌面组件状态":"恢复原桌面包状态").put(component?"componentTouched":"packageTouched",true);port.save(r);port.check();port.enabled(component,wanted);
        e=port.read();validate(e,r.getJSONObject("baseline"));require(e.getInt(key)==wanted,"原桌面状态写入后未确认，请核对实际状态。");
    }
    private void home(JSONObject r,String wanted,boolean deferred)throws Exception {
        if(port.read().optString("home").equals(wanted))return;
        r.put("step","核对 HOME");port.save(r);port.check();port.home(wanted,deferred);if(!deferred)require(port.read().optString("home").equals(wanted),"系统 HOME 切换结果未确认，请核对实际状态。");
    }
    static boolean managementAction(String id){return Arrays.asList("stock","quiet","quiet-disable","guard-pause","guard-resume","original-enable","original-disable","quiet-then-disable-original").contains(id);}
    JSONObject manage(String id,String expected)throws Exception {
        require(managementAction(id),"管理操作无效");
        JSONObject view=state();require(expected.equals(view.optString("revision")),"桌面状态已变化，请查看最新状态后确认。");
        JSONObject selected=null;JSONArray options=view.getJSONArray("managementActions");
        for(int i=0;i<options.length();i++)if(id.equals(options.getJSONObject(i).optString("id")))selected=options.getJSONObject(i);
        require(selected!=null,"当前状态不支持此操作");require(selected.optBoolean("enabled"),selected.optString("reason"));
        if(Arrays.asList("stock","quiet","quiet-disable").contains(id))return run(id,expected);
        port.check();JSONObject m=port.management(),before=port.read();
        JSONObject pending=new JSONObject().put("action",id).put("phase","running").put("message","正在核对桌面配置");
        m.put("pending",pending);port.management(m);
        try{
            if(id.equals("guard-pause")){
                m.put("guardPause",before);port.management(m);port.check();port.pause();
                require("OFF".equals(port.read().getJSONObject("guard").optString("mode")),"守护暂停未确认");
                pending.put("message","守护已暂停，系统默认桌面保持当前设置。");
            }else if(id.equals("guard-resume")){
                JSONObject b=m.getJSONObject("guardPause");validate(before,b);
                require(before.optString("home").equals(b.optString("home")),"系统 HOME 已变化，请先核对。");
                require("OFF".equals(before.getJSONObject("guard").optString("mode")),"守护开关已变化，请重新核对。");
                pending.put("message","正在恢复原有守护配置");port.management(m);port.check();port.resume(b);port.launch(false);
                m.remove("guardPause");pending.put("message","已恢复原有守护设置，运行及实体效果请分别核对。");
            }else{
                if(id.equals("quiet-then-disable-original")){
                    // The return transaction retains its own exact baseline and must finish first.
                    run("quiet",state().getString("revision"));before=port.read();
                }
                JSONObject b=id.equals("original-enable")?originalBaseline(m,before):before;
                validate(before,b);
                if(!id.equals("original-enable")){
                    require(port.runtime().optBoolean("homeOwn"),"请先切回并核对 Quiet TV 桌面。");
                    require(DesktopManagementState.disableProblem(before).isEmpty(),DesktopManagementState.disableProblem(before));
                }
                JSONObject record=new JSONObject().put("baseline",b).put("action",id).put("step","准备修改原桌面状态");
                if(id.equals("original-enable"))record.put("restore",true);
                m.put("originalApp",record);port.management(m);
                if(id.equals("original-enable")){
                    setOriginal(m,record,false,b.optInt("packageSetting",1));setOriginal(m,record,true,b.optInt("componentSetting",1));
                    require(port.read().optBoolean("stockEnabled"),"原桌面启用结果未确认");
                    pending.put("message","原桌面已启用，主桌面与守护设置保留。");
                }else{
                    setOriginal(m,record,false,3);require(!port.read().optBoolean("stockEnabled"),"原桌面禁用未确认");
                    pending.put("message",id.equals("quiet-then-disable-original")?"已切回 Quiet TV 并禁用原桌面，请核对 HOME 行为。":"原桌面已禁用，可从更多管理恢复启用。");
                }
                record.put("completed",true);
            }
            pending.put("phase","completed");port.management(m);
        }catch(Exception failure){
            pending.put("phase","incomplete").put("message",(id.equals("quiet-then-disable-original")&&"quiet".equals(port.load().optString("phase"))?"已切回 Quiet TV，原桌面禁用未完成：":"操作未完成：")+failure.getMessage());
            port.management(m);throw failure;
        }
        return state();
    }
    private JSONObject originalBaseline(JSONObject m,JSONObject current)throws Exception {
        JSONObject record=m.optJSONObject("originalApp"),b=record==null?null:record.optJSONObject("baseline");
        if(b!=null){validate(current,b);return b;}
        JSONObject b2=new JSONObject(current.toString());
        if(!current.optBoolean("packageEnabled"))b2.put("packageSetting",1);
        if(!current.optBoolean("componentEnabled"))b2.put("componentSetting",1);
        return b2;
    }
    private void setOriginal(JSONObject m,JSONObject record,boolean component,int value)throws Exception {
        String key=component?"componentSetting":"packageSetting";if(port.read().optInt(key)==value)return;
        record.put("step",component?"核对原桌面组件状态":"核对原桌面包状态");port.management(m);port.check();port.enabled(component,value);
        JSONObject after=port.read();validate(after,record.getJSONObject("baseline"));require(after.optInt(key)==value,"原桌面状态写入后未确认。");
    }
    JSONObject verify(String scene,String result,String expected)throws Exception {
        require(Arrays.asList("home","reboot","coldBoot","wake").contains(scene)&&Arrays.asList("通过","失败","待验证").contains(result),"验证内容无效");
        JSONObject view=state();require(expected.equals(view.optString("revision")),"配置已变化，请重新核对验证项目。");
        require(view.optBoolean("canVerify"),"此路线尚不支持记录当前配置的验证结果。");port.check();port.verify(scene,result);return state();
    }

    JSONObject run(String id,String expected)throws Exception {
        require(Arrays.asList("stock","quiet","quiet-disable").contains(id),"切换操作无效");
        JSONObject view=state();require(view.getString("revision").equals(expected),"桌面状态已变化，请查看最新状态后确认。");
        JSONObject selected=null;JSONArray actions=view.getJSONArray("actions");for(int i=0;i<actions.length();i++)if(id.equals(actions.getJSONObject(i).getString("id")))selected=actions.getJSONObject(i);
        require(selected!=null,"当前状态不支持此操作");require(selected.getBoolean("enabled"),selected.optString("reason"));port.check();
        JSONObject management=port.management();if(management.optJSONObject("pending")!=null){management.remove("pending");port.management(management);}
        JSONObject record=port.load(),env=port.read();
        if("stock".equals(id)){record=new JSONObject().put("baseline",env).put("packageTouched",false).put("componentTouched",false);save(record,"switching","正在切换到原桌面");}
        else save(record,"returning","正在恢复 Quiet TV");
        final JSONObject r=record;
        try{
            JSONObject baseline=r.getJSONObject("baseline");validate(port.read(),baseline);
            if("stock".equals(id)){
                if(!env.getBoolean("packageEnabled"))enable(r,false,1);
                if(!env.getBoolean("componentEnabled"))enable(r,true,1);
                r.put("step","暂停接管");port.save(r);port.check();port.pause();r.put("paused",true);port.save(r);
                require(port.read().optBoolean("stockEnabled"),"原桌面尚未就绪");home(r,baseline.getString("stockHome"),false);
                r.put("step","显示原桌面");port.save(r);port.check();port.launch(true);save(r,"stock","已核对原桌面启用及 HOME 状态，并请求显示原桌面；请在电视确认画面。");
            }else{
                validateReturn(port.read(),r);home(r,baseline.getString("home"),"quiet-disable".equals(id)&&baseline.optBoolean("requiresStockDisabled"));
                if("quiet-disable".equals(id)){
                    require(!baseline.optBoolean("dependsOnStock")&&!baseline.getString("home").equals(baseline.getString("original")),"中转接管必须保留原桌面");
                    if(r.optBoolean("componentTouched"))enable(r,true,baseline.getInt("componentSetting"));
                    if(r.optBoolean("packageTouched"))enable(r,false,baseline.getInt("packageSetting"));
                }
                require(port.read().optString("home").equals(baseline.getString("home")),"HOME 恢复结果未确认，请核对实际状态。");
                port.check();r.put("step","恢复接管").put("resumeAttempted",true);port.save(r);port.resume(baseline);port.launch(false);save(r,"quiet","已恢复切换前配置并请求显示 Quiet TV；请核对 HOME 和实际画面。");
            }
        }catch(Exception failure){try{save(r,"incomplete",(r.optString("step").isEmpty()?"":r.optString("step")+"：")+(failure.getMessage()==null?"切换结果待核对":failure.getMessage()));}catch(Exception saveFailure){failure=new IOException("切换结果待核对，记录保存未确认："+failure.getMessage(),saveFailure);}throw failure;}
        return state();
    }
}
