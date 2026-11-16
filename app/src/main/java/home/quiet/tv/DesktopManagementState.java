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
        boolean incomplete=runtime.optBoolean("needsReview")||!"quiet".equals(phase)&&!"stock".equals(phase)
            ||pending!=null&&!"completed".equals(pending.optString("phase"));
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
        String state,label;
        if(incomplete||runtime.optBoolean("proxyPausePending")){state="review";label="状态待核对";}
        else if(paused){state="paused";label="守护已暂停";}
        else if(needed&&ready){state="running";label="守护中";}
        else if(needed&&!runtime.optBoolean("authorized")){state="authorization";label="等待授权";}
        else if(needed){state="disconnected";label="服务未连接";}
        else if(own){state="direct";label="系统直接启动";}
        else{state="unconfigured";label="尚未配置";}
        String desktopLabel="quiet".equals(desktop)?"Quiet TV":"original".equals(desktop)?e.optString("label","电视原桌面"):"other".equals(desktop)?runtime.optString("systemHomeLabel","其他桌面"):"待核对";
        if(desktopLabel.isEmpty())desktopLabel="电视原桌面";
        String hint="quiet".equals(desktop)?"按电视遥控器 HOME 键返回 Quiet TV":"original".equals(desktop)?"按电视遥控器 HOME 键进入电视原桌面":
            "other".equals(desktop)?"当前尚未配置 Quiet TV 桌面，可按需进入系统设置。":runtime.optString("problem");
        if(hint.isEmpty())hint=problem.isEmpty()?"请核对当前电视的桌面配置。":problem;
        JSONArray entries=new JSONArray().put(new JSONObject().put("id","quiet").put("label","Quiet TV").put("enabled",runtime.optBoolean("quietUsable",true))
            .put("detail","quiet".equals(desktop)?"当前主桌面":"已启用"));
        entries.put(new JSONObject().put("id","original").put("label",e.optString("label","电视原桌面"))
            .put("enabled",stock).put("dependency",e.optBoolean("dependsOnStock"))
            .put("detail",e.optString("original").isEmpty()?"记录待核对":"original".equals(desktop)?"当前主桌面":stock?e.optBoolean("dependsOnStock")?"已启用  守护依赖":"已启用":"已禁用"));
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
            actions.put(action("original-disable","禁用原桌面",reason,"仅禁用当前用户的原桌面，保留安装和数据，可从桌面管理恢复启用。","original"));
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
        return out.put("presentationVersion",1).put("mainDesktop",new JSONObject().put("id",desktop).put("label",desktopLabel).put("hint",hint)
                .put("systemHome",runtime.optString("systemHome")).put("source","system_and_route"))
            .put("guard",new JSONObject().put("state",state).put("label",label).put("needed",needed).put("reason",runtime.optString("problem")))
            .put("desktops",entries).put("managementActions",actions).put("primaryAction",primary)
            .put("summary",desktopLabel+"  "+label).put("mode",desktopLabel).put("verification",verification)
            .put("canVerify",runtime.optBoolean("canVerify")&&!incomplete).put("systemSelection",runtime.optBoolean("systemSelection"))
            .put("operation",pending==null?new JSONObject():pending).put("message",message);
    }
    private static boolean enabled(JSONArray actions,String id){for(int i=0;i<actions.length();i++){JSONObject action=actions.optJSONObject(i);if(action!=null&&id.equals(action.optString("id")))return action.optBoolean("enabled");}return false;}
    private DesktopManagementState(){}
}
