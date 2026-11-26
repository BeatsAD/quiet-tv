package home.quiet.tv;

import org.json.*;

/** One read-only presentation for native settings and the bundled phone client. */
final class DesktopManagementState {
    static JSONObject action(String id,String title,String reason,String explanation,String scope)throws JSONException {
        return new JSONObject().put("id",id).put("title",title).put("enabled",reason.isEmpty())
            .put("reason",reason).put("explanation",explanation).put("scope",scope);
    }
    static String disableProblem(JSONObject e) {
        if(e.optBoolean("externalHelper"))return "当前由独立中转助手接管，请先核对原方案。";
        if(e.optBoolean("dependsOnStock"))return "当前接管依赖原桌面，需要保持启用。";
        if(!e.optBoolean("disableAllowed"))return "此机型尚未确认原桌面可禁用，请通过电脑核对适配。";
        if(!e.optBoolean("canDisable")||!e.optBoolean("canRestorePackage")||!e.optBoolean("canRestoreComponent"))
            return "需要支持禁用及精确恢复的电视管理连接。";
        return "";
    }
    static JSONObject present(JSONObject legacy,JSONObject e,JSONObject runtime,JSONObject record,JSONObject management)throws JSONException {
        JSONObject out=new JSONObject(legacy.toString());
        String phase=out.optString("phase","quiet"),problem=out.optString("problem");
        JSONObject pending=management.optJSONObject("pending");
        boolean operationIncomplete=!"quiet".equals(phase)&&!"stock".equals(phase)
            ||pending!=null&&!"completed".equals(pending.optString("phase"));
        boolean incomplete=runtime.optBoolean("needsReview")||operationIncomplete;
        boolean paused=runtime.optBoolean("paused"),needed=runtime.optBoolean("guardNeeded"),ready=runtime.optBoolean("guardReady");
        boolean own=runtime.optBoolean("homeOwn"),stock=e.optBoolean("stockEnabled");
        String desktop="unknown";
        if(!incomplete&&!runtime.optBoolean("proxyPausePending")){
            if(own)desktop="quiet";
            else if(ready&&runtime.optBoolean("routeValid"))desktop="quiet";
            else if(paused&&stock&&(e.optBoolean("homeKnown")&&e.optString("home").equals(e.optString("stockHome"))||e.optBoolean("retainedHome")))desktop="original";
            else if(!needed&&stock&&e.optBoolean("homeKnown")&&e.optString("home").equals(e.optString("original")))desktop="original";
            else if(!needed&&runtime.optBoolean("homeKnown"))desktop="other";
        }
        JSONObject repair=!operationIncomplete&&"quiet".equals(phase)&&!paused&&!runtime.optBoolean("proxyPausePending")
                &&!e.optString("original").isEmpty()?runtime.optJSONObject("guardRepair"):null;
        String state,label;
        if(incomplete||runtime.optBoolean("proxyPausePending")){
            state="review";
            label=operationIncomplete||runtime.optBoolean("proxyPausePending")?"结果未确认":repair!=null?"需要修复":"无法确认";
        }else if(paused){state="paused";label="已暂停";}
        else if(needed&&ready){state="running";label="运行中";}
        else if(needed&&!runtime.optBoolean("authorized")){state="authorization";label="待电视授权";}
        else if(needed){state="disconnected";label="未连接";}
        else if(own){state="direct";label="系统直接启动";}
        else{state="unconfigured";label="未设置";}
        String desktopLabel="quiet".equals(desktop)?"Quiet TV":"original".equals(desktop)?e.optString("label","电视原桌面"):"other".equals(desktop)?runtime.optString("systemHomeLabel","其他桌面"):"待核对";
        if(desktopLabel.isEmpty())desktopLabel="电视原桌面";
        String hint="quiet".equals(desktop)?"按电视遥控器 HOME 键返回 Quiet TV":"original".equals(desktop)?"按电视遥控器 HOME 键进入电视原桌面":
            "other".equals(desktop)?"当前尚未配置 Quiet TV 桌面，可按需进入系统设置。":runtime.optString("problem");
        if(hint.isEmpty())hint=problem.isEmpty()?"请核对当前电视的桌面配置。":problem;
        boolean quietUsable=runtime.optBoolean("quietUsable",true);
        boolean originalKnown=!e.optString("original").isEmpty();
        String originalName=originalKnown?e.optString("label"):"";
        String originalStatus=!originalKnown?"无法确认":stock?"已启用":"已禁用";
        JSONArray entries=new JSONArray().put(new JSONObject().put("id","quiet").put("label","Quiet TV").put("enabled",quietUsable)
            .put("detail",quietUsable?"已启用":"不可用"));
        entries.put(new JSONObject().put("id","original").put("label",originalName)
            .put("enabled",stock).put("dependency",e.optBoolean("dependsOnStock")).put("detail",originalStatus));
        JSONArray actions=new JSONArray(),old=out.optJSONArray("actions");
        String primary="";
        if(old!=null&&problem.isEmpty()){
            for(int i=0;i<old.length();i++){
                JSONObject a=new JSONObject(old.getJSONObject(i).toString());String id=a.optString("id");
                a.put("scope","return");if(incomplete&&id.equals("stock"))continue;
                if("quiet".equals(desktop)&&id.equals("stock")||"original".equals(desktop)&&id.equals("quiet")){a.put("scope","primary");primary=id;}
                actions.put(a);
            }
            if((primary.isEmpty()||!enabled(actions,primary))&&("original".equals(desktop)||incomplete))for(int i=0;i<actions.length();i++){
                JSONObject a=actions.getJSONObject(i);if((a.optString("id").equals("quiet-disable")||incomplete&&a.optString("id").equals("quiet"))&&a.optBoolean("enabled")){primary=a.optString("id");a.put("scope","primary");}
            }
        }
        String blocked=incomplete?"上次操作尚未核对，请先检查状态及恢复记录。":problem;
        if(needed&&!paused){
            String reason=!blocked.isEmpty()?blocked:runtime.optBoolean("externalHelper")?"请通过原助手管理当前接管。":"";
            actions.put(action("guard-pause","暂停守护",reason,"暂停自动接管，系统默认桌面与个性化设置保留。","guard"));
        }
        JSONObject guardPause=management.optJSONObject("guardPause");
        if(paused&&guardPause!=null&&!"stock".equals(phase)){
            String reason=runtime.optBoolean("externalHelper")?"请先暂停独立中转助手，避免同时接管。":problem;
            JSONObject a=action("guard-resume","恢复守护",reason,"恢复暂停前的守护方案，保持原有启动路线。","original".equals(desktop)?"primary":"guard");
            actions.put(a);if(primary.isEmpty()&&"original".equals(desktop)&&reason.isEmpty())primary="guard-resume";
        }
        String originalProblem=runtime.optBoolean("needsReview")?runtime.optString("problem","原有桌面配置需要核对。"):!problem.isEmpty()?problem:e.optString("original").isEmpty()?"没有可核对的原桌面记录。":"";
        if(!stock)actions.put(action("original-enable","启用原桌面",!originalProblem.isEmpty()?originalProblem:e.optBoolean("requiresStockDisabled")&&own?"此固件启用原桌面会影响 HOME，请使用启用并切换。":!e.optBoolean("canEnable")||!e.optBoolean("canRestorePackage")||!e.optBoolean("canRestoreComponent")?"需要支持精确恢复的电视管理连接。":"",
            "只启用已记录的电视原桌面，保持主桌面和守护设置。","original"));
        if(stock){
            String reason=!originalProblem.isEmpty()?originalProblem:"quiet".equals(desktop)&&e.optBoolean("dependsOnStock")?disableProblem(e):!own?"请先切回 Quiet TV 并核对桌面状态。":disableProblem(e);
            actions.put(action("original-disable","禁用原桌面",reason,"仅禁用当前用户的原桌面，保留安装和数据。","original"));
            JSONObject baseline=record==null?null:record.optJSONObject("baseline");
            if("original".equals(desktop)&&"stock".equals(phase)&&baseline!=null){
                reason=!originalProblem.isEmpty()?originalProblem:disableProblem(baseline);
                boolean canReturn=false;if(old!=null)for(int i=0;i<old.length();i++)if(old.getJSONObject(i).optString("id").equals("quiet")&&old.getJSONObject(i).optBoolean("enabled"))canReturn=true;
                if(reason.isEmpty()&&!canReturn)reason="切回 Quiet TV 的配置尚未就绪。";
                actions.put(action("quiet-then-disable-original","切回 Quiet TV 后禁用原桌面",reason,"先恢复 Quiet TV 并核对 HOME，再禁用原桌面；切换失败时不会禁用。","original"));
            }
        }
        JSONObject verification=runtime.optJSONObject("verification");if(verification==null)verification=new JSONObject();
        String message=pending!=null?pending.optString("message"):out.optString("message");
        // Direct system HOME is identifiable even when guard health needs separate review.
        String currentName=own?"Quiet TV":"unknown".equals(desktop)?"":desktopLabel;
        String currentStatus=currentName.isEmpty()?"无法确认":(own||"quiet".equals(desktop))&&!quietUsable?"不可用":"已启用";
        JSONObject settings=settings(actions,desktop,currentName,currentStatus,originalName,originalStatus,state,label,phase,runtime,problem);
        settings.put("guardRepair",repair);
        return out.put("presentationVersion",1).put("mainDesktop",new JSONObject().put("id",desktop).put("label",desktopLabel).put("hint",hint)
                .put("systemHome",runtime.optString("systemHome")).put("source","system_and_route"))
            .put("guard",new JSONObject().put("state",state).put("label",label).put("needed",needed).put("reason",runtime.optString("problem")))
            .put("desktops",entries).put("managementActions",actions).put("primaryAction",primary).put("settings",settings)
            .put("summary",desktopLabel+"  "+label).put("mode",desktopLabel).put("verification",verification)
            .put("canVerify",runtime.optBoolean("canVerify")&&!incomplete).put("systemSelection",runtime.optBoolean("systemSelection"))
            .put("operation",pending==null?new JSONObject():pending).put("message",message);
    }
    private static JSONObject settings(JSONArray actions,String desktop,String currentName,String currentStatus,
                                       String originalName,String originalStatus,String guardState,String guardLabel,
                                       String phase,JSONObject runtime,String problem)throws JSONException {
        boolean quietCurrent="quiet".equals(desktop),originalCurrent="original".equals(desktop);
        JSONObject toOriginal=quietCurrent?find(actions,"stock"):null;
        JSONObject toQuiet=!quietCurrent&&(originalCurrent||!"quiet".equals(phase))?find(actions,"quiet"):null;
        JSONObject restoreDisabled=find(actions,"quiet-disable");
        if(toQuiet!=null&&restoreDisabled!=null&&(restoreDisabled.optBoolean("enabled")||!toQuiet.optBoolean("enabled"))){
            toQuiet=restoreDisabled;
        }
        JSONObject guard=find(actions,"guard-resume");
        if(guard==null)guard=find(actions,"guard-pause");

        JSONArray rows=new JSONArray()
                .put(setting("current","当前桌面",currentStatus,toQuiet).put("name",currentName))
                .put(setting("original","电视原桌面",originalStatus,toOriginal).put("name",originalName))
                .put(setting("guard","桌面守护",guardLabel,guard));

        String recovery="",reason="";
        JSONObject target=quietCurrent?toOriginal:toQuiet;
        boolean setup="quiet".equals(phase)&&"unconfigured".equals(guardState)&&!runtime.optBoolean("configured");
        JSONObject localSetup=setup?runtime.optJSONObject("guardSetup"):null;
        if(setup&&runtime.optBoolean("systemSelection")&&(localSetup==null||!localSetup.optBoolean("preferGuard")))recovery="system";
        else if(target!=null&&!target.optBoolean("enabled")){
            reason=target.optString("reason");
            recovery=target.optBoolean("requiresManagement")&&!runtime.optBoolean("managementConnected")?"authorize":"computer";
        }else if("review".equals(guardState)||"authorization".equals(guardState)||"disconnected".equals(guardState)
                ||target==null&&!(originalCurrent&&guard!=null&&guard.optBoolean("enabled"))
                ||guard!=null&&!guard.optBoolean("enabled")){
            recovery="computer";
            reason=problem.isEmpty()?runtime.optString("problem"):problem;
            if(reason.isEmpty()&&guard!=null)reason=guard.optString("reason");
        }
        if(localSetup!=null)reason="";
        if(!setup&&"running".equals(guardState)&&!runtime.optString("problem").isEmpty()){
            recovery="computer";
            reason=runtime.optString("problem");
        }
        return new JSONObject().put("rows",rows).put("recovery",recovery).put("reason",reason).put("guardSetup",localSetup);
    }
    private static JSONObject setting(String id,String title,String value,JSONObject action)throws JSONException {
        JSONObject row=new JSONObject().put("id",id).put("title",title).put("value",value);
        if(action!=null)row.put("action",action);
        return row;
    }
    private static JSONObject find(JSONArray actions,String id) {
        for(int i=0;i<actions.length();i++){
            JSONObject action=actions.optJSONObject(i);
            if(action!=null&&id.equals(action.optString("id")))return action;
        }
        return null;
    }
    private static boolean enabled(JSONArray actions,String id){
        JSONObject action=find(actions,id);
        return action!=null&&action.optBoolean("enabled");
    }
    private DesktopManagementState(){}
}
