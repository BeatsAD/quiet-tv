package home.quiet.tv;

import android.content.*;
import android.content.pm.*;
import android.os.Build;
import org.json.*;
import java.util.*;

/** Exports only the recorded restoration recipe; the authorized computer executes shell steps. */
final class ComputerRestorePlan {
    private final Context context;
    ComputerRestorePlan(Context c){context=c;}
    private JSONObject record() throws Exception {
        JSONObject r=new SetupStore(context).get().optJSONObject("takeover");
        if(r==null || !Arrays.asList("applied","running","incomplete").contains(r.optString("state")))throw new IllegalStateException("没有待恢复的专项接管记录");
        String own=new ComponentName(context,HomeActivity.class).flattenToString();
        if(r.getInt("user")!=android.os.Process.myUid()/100000 || !Build.FINGERPRINT.equals(r.getString("fingerprint")) || !own.equals(r.getString("target")))throw new IllegalStateException("专项记录不属于当前设备、用户或目标");
        return r;
    }
    private void validate(JSONObject step,JSONObject record) throws Exception {
        String kind=step.getString("kind"),key=step.getString("key"),before=step.getString("before"),after=step.getString("after");
        SetupPresets rules=record.has("rules")?new SetupPresets(record.getJSONObject("rules").toString(),Build.BRAND,Build.MODEL,Build.FINGERPRINT):SetupPresets.legacy(context);
        if("package".equals(kind)){
            if(!Arrays.asList(HomeTakeoverPlan.GOOGLE,HomeTakeoverPlan.CLASSIC,HomeTakeoverPlan.SETUP).contains(key)
                    && !(rules.homeAllows("disableVerified") && rules.home().startsWith(key+"/") && rules.homeIdentityMatches(step.getLong("versionCode"),record.getInt("user"))))throw new IllegalStateException("原记录的包恢复资格不匹配");
            PackageInfo info=context.getPackageManager().getPackageInfo(key,HomeController.METADATA);
            if(info.applicationInfo==null || (info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0 || !AppAvailability.installed(context.getPackageManager(),info.applicationInfo)
                    || (Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode)!=step.getLong("versionCode") || !step.getString("version").equals(info.versionName==null?String.valueOf(Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode):info.versionName))throw new IllegalStateException("原桌面版本或安装状态已改变");
            if(!Arrays.asList("0","1").contains(before) || !"3".equals(after))throw new IllegalStateException("原启用状态无效");
        }else if("property".equals(kind)){
            if(record.getInt("user")!=0)throw new IllegalStateException("全局路由仅支持原记录的主用户");
            if(HomeTakeoverPlan.ADB_PORT.equals(key)){
                if(!rules.homeAllows("keepAdb") || !HomeTakeoverPlan.adbPortState(before) || !HomeTakeoverPlan.adbPort(after))throw new IllegalStateException("端口恢复资格无效");
            }else if(!HomeTakeoverPlan.property(key) || !(before.isEmpty() || HomeTakeoverPlan.packageName(before)) || !context.getPackageName().equals(after))throw new IllegalStateException("系统属性恢复步骤无效");
        }else if("home".equals(kind)){
            if(!record.getString("target").equals(key) || !key.equals(after) || !record.getString("original").equals(step.optString("restore",before))
                    || ComponentName.unflattenFromString(step.optString("restore",before))==null)throw new IllegalStateException("原 HOME 恢复记录无效");
        }else if("permission".equals(kind)){
            if(!rules.homeAllows("keepAdb") || !HomeTakeoverPlan.WRITE_SECURE.equals(key) || !"denied".equals(before) || !"granted".equals(after))throw new IllegalStateException("授权恢复记录无效");
        }else if("preference".equals(kind)){
            if(!rules.homeAllows("keepAdb") || !"keepAdb".equals(key) || !Arrays.asList("true","false").contains(before) || !"true".equals(after))throw new IllegalStateException("偏好恢复记录无效");
        }else throw new IllegalStateException("未知专项恢复步骤");
    }
    JSONObject plan() throws Exception {
        JSONObject record=record();JSONArray source=record.getJSONArray("steps"),steps=new JSONArray(),retained=new JSONArray();
        if(source.length()>16)throw new IllegalStateException("专项记录步骤过多");
        boolean preserveAdb=HomeGuardAdb.enabled(context);int homeCount=0;
        for(int i=0;i<source.length();i++){
            JSONObject step=source.getJSONObject(i);validate(step,record);
            String kind=step.getString("kind");if("home".equals(kind))homeCount++;
            if(!step.optBoolean("attempted") && !"home".equals(kind))continue;
            JSONObject copy=new JSONObject(step.toString()).put("index",i);
            if(preserveAdb && ("permission".equals(kind) || "preference".equals(kind) || HomeTakeoverPlan.ADB_PORT.equals(step.getString("key"))))retained.put(copy);
            else steps.put(copy);
        }
        if(homeCount!=1)throw new IllegalStateException("专项记录缺少唯一 HOME 恢复步骤");
        return new JSONObject().put("revision",DeviceInputProfile.digest(record.toString())).put("original",record.getString("original"))
            .put("steps",steps).put("retainedAdbSteps",retained).put("preserveAdb",preserveAdb);
    }
    void save(String text,String revision) throws Exception {
        if(text==null || text.length()>49152)throw new IllegalArgumentException("专项记录无效");
        SetupStore store=new SetupStore(context);JSONObject before=store.get().optJSONObject("takeover");
        if(!DeviceInputProfile.digest(before==null?"":before.toString()).equals(revision))throw new IllegalStateException("专项记录已变化");
        JSONObject next=new JSONObject(text);String own=new ComponentName(context,HomeActivity.class).flattenToString();
        if(next.getInt("user")!=android.os.Process.myUid()/100000 || !Build.FINGERPRINT.equals(next.getString("fingerprint")) || !own.equals(next.getString("target"))
                || !next.getString("original").equals(store.originalHome()) || !java.util.Arrays.asList("running","applied","incomplete").contains(next.getString("state")))throw new IllegalStateException("专项记录范围无效");
        JSONArray steps=next.getJSONArray("steps");if(steps.length()==0 || steps.length()>16)throw new IllegalStateException("专项记录步骤无效");
        int homes=0;for(int i=0;i<steps.length();i++){JSONObject step=steps.getJSONObject(i);validate(step,next);if("home".equals(step.getString("kind")))homes++;}
        if(homes!=1)throw new IllegalStateException("专项记录必须包含唯一 HOME");
        if(before!=null && java.util.Arrays.asList("running","applied","incomplete").contains(before.optString("state"))){
            JSONObject a=new JSONObject(before.toString()),b=new JSONObject(next.toString());
            for(JSONObject r:new JSONObject[]{a,b}){r.remove("state");JSONArray list=r.getJSONArray("steps");for(int i=0;i<list.length();i++){list.getJSONObject(i).remove("attempted");list.getJSONObject(i).remove("verified");}}
            if(!a.toString().equals(b.toString()))throw new IllegalStateException("不能覆盖已有恢复方案");
        }
        store.put("takeover",next);
    }
    private JSONObject checked(String expected) throws Exception {
        JSONObject plan=plan();if(expected==null || !expected.equals(plan.getString("revision")))throw new IllegalStateException("专项恢复记录已变化");return plan;
    }
    void preference(String expected,int index) throws Exception {
        JSONObject plan=checked(expected);JSONArray steps=plan.getJSONArray("steps");
        for(int i=0;i<steps.length();i++){JSONObject step=steps.getJSONObject(i);
            if(step.getInt("index")==index && "preference".equals(step.getString("kind"))){
                String actual=Boolean.toString(HomeGuardAdb.enabled(context));
                if(!actual.equals(step.getString("before")) && !actual.equals(step.getString("after")))throw new IllegalStateException("偏好被其他操作改变");
                if(!HomeGuardAdb.setEnabled(context,Boolean.parseBoolean(step.getString("before"))))throw new IllegalStateException("恢复偏好未保存");return;
            }
        }
        throw new IllegalStateException("没有对应的偏好恢复步骤");
    }
    void finish(String expected,String proofText) throws Exception {
        JSONObject plan=checked(expected),proof=new JSONObject(proofText);JSONArray steps=plan.getJSONArray("steps");
        for(int i=0;i<steps.length();i++){
            JSONObject step=steps.getJSONObject(i);String kind=step.getString("kind"),target="home".equals(kind)?step.optString("restore",step.getString("before")):step.getString("before");
            if(!target.equals(proof.getString(Integer.toString(step.getInt("index")))))throw new IllegalStateException("电脑恢复读回证据不完整");
            String actual=target;
            if("home".equals(kind)){HomeController.State state=new HomeController(context).inspect();actual=state.known?state.actual:"";}
            else if("package".equals(kind))actual=Integer.toString(context.getPackageManager().getApplicationEnabledSetting(step.getString("key")));
            else if("permission".equals(kind))actual=HomeGuardRecovery.writeAllowed(context)?"granted":"denied";
            else if("preference".equals(kind))actual=Boolean.toString(HomeGuardAdb.enabled(context));
            else{String property=CoocaaHomeRoutes.read(step.getString("key"));if(property!=null)actual=property;}
            if(!target.equals(actual))throw new IllegalStateException("恢复状态未匹配："+step.getString("key"));
        }
        JSONObject record=record();record.put("state","restored").put("restorationSource","computer_readback")
            .put("retainedAdbSteps",plan.getJSONArray("retainedAdbSteps")).put("restoredAt",System.currentTimeMillis());
        new SetupStore(context).put("takeover",record);new SetupStore(context).put("status","restored");
    }
}
