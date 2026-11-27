package home.quiet.tv;

import android.content.Context;
import android.provider.Settings;
import java.io.*;
import java.util.*;
import org.json.*;

/** Project only configuration into CAS; live time counters cannot invalidate a parent's edit. */
final class PhoneSettingsChildren {
    private final Context context;final PhoneSettingsParent parent;private final Runnable changed;
    PhoneSettingsChildren(Context context,Runnable changed){this.context=context;this.changed=changed;parent=new PhoneSettingsParent(context);}
    JSONObject configuration()throws IOException{
        ChildModeStore store=new ChildModeStore(context);try{JSONObject rules=new JSONObject(store.preferences.getString("rules","{}")),out=new JSONObject();for(Iterator<String> keys=rules.keys();keys.hasNext();){String pkg=keys.next();ChildModePolicy.Rule rule=ChildModeStore.read(rules.optJSONObject(pkg));out.put(pkg,settings(rule));}return new JSONObject().put("childEnabled",store.enabled()).put("restTheme",store.restTheme()).put("rules",out);}catch(JSONException|RuntimeException e){throw new IOException("儿童设置无法读取，原配置已保留",e);}
    }
    JSONObject usage()throws IOException{
        try{
            ChildModeStore store=new ChildModeStore(context);
            JSONObject rules=new JSONObject(store.preferences.getString("rules","{}")),out=new JSONObject();
            for(Iterator<String> keys=rules.keys();keys.hasNext();){
                String pkg=keys.next();
                ChildModePolicy.Rule rule=ChildModeStore.read(rules.optJSONObject(pkg));
                rule.newDay(ChildModeStore.today());
                out.put(pkg,new JSONObject().put("singleUsed",rule.singleUsed).put("dailyUsed",rule.dailyUsed)
                    .put("remaining",rule.limited()?rule.remaining():-1).put("scheduled",rule.scheduled()));
            }
            ChildTimerState timer=ChildUsageService.status(context);
            ChildQuizState quiz=ChildQuizStore.get(context).read();
            JSONObject session=quiz.session(),status=new JSONObject();
            if(session!=null){
                status.put("id",session.getLong("id")).put("requestId",session.optString("requestId"))
                    .put("state",session.optBoolean("passed")?"passed":session.optBoolean("visible")?"displayed":"accepted")
                    .put("package",session.optString("package"));
            }else{
                JSONArray rounds=quiz.data.getJSONArray("rounds");
                if(rounds.length()>0){
                    JSONObject last=rounds.getJSONObject(rounds.length()-1);
                    status.put("id",last.optLong("id")).put("requestId",last.optString("requestId"))
                        .put("state",last.optBoolean("skipped")?"parentSkipped":"passed");
                }
            }
            return new JSONObject().put("usage",out).put("timerState",timer.name()).put("timerLabel",timer.label)
                .put("timerMessage",timer.message).put("currentApp",ChildUsageService.currentApp(context))
                .put("quizStatus",status).put("rejectedQuizRequestId",ChildUsageService.rejectedQuizRequest());
        }catch(JSONException|RuntimeException e){throw new IOException("使用状态无法读取",e);}
    }
    JSONObject quiz()throws IOException{
        try{ChildQuizState state=ChildQuizStore.get(context).read();ChildQuizState.Settings settings=state.settings();return new JSONObject().put("quizInterval",settings.interval).put("quizGrade",settings.grade).put("quizTopic",settings.topic).put("quizSubjects",new JSONArray(Arrays.asList(settings.subjects))).put("quizTheme",settings.theme).put("quizSettings",settings.json()).put("quizAppSettings",state.data.optJSONObject("appSettings")==null?new JSONObject():new JSONObject(state.data.getJSONObject("appSettings").toString())).put("quizQuestions",new JSONArray(state.data.getJSONArray("customQuestions").toString())).put("selectedCustomIds",new JSONArray(state.data.getJSONArray("selectedCustomIds").toString())).put("quizApps",new JSONObject(state.data.getJSONObject("apps").toString()));}catch(JSONException e){throw new IOException(e);}
    }
    JSONArray apps()throws IOException{
        try{JSONArray values=new JSONArray();ChildModeStore store=new ChildModeStore(context);for(android.content.pm.ResolveInfo info:new HomeAppCatalog(context).launchable().values()){String pkg=info.activityInfo.packageName;if(store.eligible(pkg))values.put(new JSONObject().put("id",pkg).put("name",new AppNameStore(context).display(pkg,info.loadLabel(context.getPackageManager()).toString())).put("icon","api/resources/icon/"+java.net.URLEncoder.encode(pkg,"UTF-8")));}return values;}catch(JSONException e){throw new IOException(e);}
    }
    static JSONObject settings(ChildModePolicy.Rule rule)throws JSONException{return new JSONObject().put("enabled",rule.enabled).put("hidden",rule.hidden).put("locked",rule.locked).put("mode",rule.mode).put("minutes",rule.limit/ChildModePolicy.MINUTE).put("repeatDays",rule.repeatDays);}
    void write(String domain,String field,Object value,PhoneSettingsServer.Active active)throws IOException{
        if("permission".equals(field)&&Boolean.TRUE.equals(value)){if(!new SystemSettingsRouter(context).open(Settings.ACTION_ACCESSIBILITY_SETTINGS))throw new PhoneSettingsServer.Rejected("此电视无可用无障碍设置入口");return;}
        parent.requireChild(active);ChildModeStore store=new ChildModeStore(context);
        try{
            if(domain.equals("child")){
                if(field.equals("childEnabled")&&value instanceof Boolean){if(!store.setEnabled((Boolean)value))throw new IOException("儿童模式保存未确认");}
                else if(field.equals("restTheme")){if(!store.restTheme(PhoneSettingsConfiguration.integer(value,0,2)))throw new IOException("休息屏保保存未确认");}
                else if(field.equals("quizNow")&&value instanceof JSONObject){
                    JSONObject command=(JSONObject)value;
                    String request=command.getString("requestId"),expected=command.optString("package");
                    if(!request.matches("[A-Za-z0-9_-]{8,80}")||!active.childManagement().isEmpty()&&expected.isEmpty())throw new IllegalArgumentException();
                    parent.requireChild(active);
                    ChildUsageService.startWatchingNow(context,request,expected);
                }
                else if(field.equals("rule")&&value instanceof JSONObject){JSONObject edit=(JSONObject)value;if(edit.length()!=3||!(edit.opt("package") instanceof String)||!(edit.opt("field") instanceof String))throw new IllegalArgumentException();String pkg=edit.getString("package");if(!store.eligible(pkg))throw new PhoneSettingsServer.Rejected("此应用不在儿童模式可管理范围");ChildModePolicy.Rule next=store.rule(pkg).copy();Object setting=edit.get("value");switch(edit.getString("field")){
                    case "hidden":throw new PhoneSettingsServer.Rejected("请在应用管理中隐藏或恢复应用");case "enabled":if(!(setting instanceof Boolean))throw new IllegalArgumentException();if(!((Boolean)setting))ChildQuizStore.get(context).change(state->state.cancelPending(pkg));else ChildQuizStore.get(context).change(state->state.configure(pkg,state.settings(pkg)));next.enabled=(Boolean)setting;break;case "locked":if(!(setting instanceof Boolean))throw new IllegalArgumentException();next.locked=(Boolean)setting;break;
                    case "mode":next.mode=PhoneSettingsConfiguration.integer(setting,0,2);break;case "minutes":next.limit=PhoneSettingsConfiguration.integer(setting,1,1440)*ChildModePolicy.MINUTE;break;case "repeatDays":next.repeatDays=PhoneSettingsConfiguration.integer(setting,1,127);break;
                    case "settings":if(!(setting instanceof JSONObject))throw new IllegalArgumentException();JSONObject schedule=(JSONObject)setting;if(schedule.length()!=3)throw new IllegalArgumentException();next.mode=PhoneSettingsConfiguration.integer(schedule.get("mode"),0,2);next.limit=PhoneSettingsConfiguration.integer(schedule.get("minutes"),1,1440)*ChildModePolicy.MINUTE;next.repeatDays=PhoneSettingsConfiguration.integer(schedule.get("repeatDays"),1,127);break;
                    default:throw new IllegalArgumentException();}
                    next.validate();parent.requireChild(active);if(!store.saveSettings(pkg,next))throw new IOException("儿童规则保存未确认");
                }else if(field.equals("grant")&&value instanceof JSONObject){JSONObject grant=(JSONObject)value;if(grant.length()!=2||!(grant.opt("package") instanceof String)||!store.eligible(grant.optString("package")))throw new IllegalArgumentException();if(!store.grant(grant.getString("package"),PhoneSettingsConfiguration.integer(grant.opt("minutes"),1,1440)*ChildModePolicy.MINUTE))throw new IOException("追加时间保存未确认");ChildUsageService.changed();}
                else throw new IllegalArgumentException();
            }else if(domain.equals("quiz")){
                ChildQuizStore learning=ChildQuizStore.get(context);learning.change(state->{parent.requireChild(active);ChildQuizState.Settings old=state.settings();
                    try{switch(field){case "quizSettings":if(!(value instanceof JSONObject))throw new IllegalArgumentException();state.configure(new ChildQuizState.Settings((JSONObject)value));break;
                    case "quizAppSettings":if(!(value instanceof JSONObject))throw new IllegalArgumentException();JSONObject app=(JSONObject)value;String target=app.getString("package");if(app.length()!=2||!store.eligible(target))throw new IllegalArgumentException();state.configure(target,new ChildQuizState.Settings(app.getJSONObject("settings")));break;
                    case "question":if(!(value instanceof JSONObject))throw new IllegalArgumentException();state.putQuestion((JSONObject)value);break;
                    case "deleteQuestion":if(!(value instanceof JSONObject))throw new IllegalArgumentException();JSONObject deleted=(JSONObject)value;state.deleteQuestion(deleted.getString("id"),deleted.getLong("revision"));break;
                    case "selectedCustomIds":if(!(value instanceof JSONArray))throw new IllegalArgumentException();state.selectQuestions((JSONArray)value);break;
                    case "quizInterval":int interval=PhoneSettingsConfiguration.integer(value,10,20);if(interval!=10&&interval!=15&&interval!=20)throw new IllegalArgumentException();state.configure(new ChildQuizState.Settings(interval,old.grade,old.subjects));break;
                    case "quizGrade":if(!(value instanceof String))throw new IllegalArgumentException();state.configure(new ChildQuizState.Settings(old.interval,(String)value,old.subjects));break;
                    case "quizApp":if(!(value instanceof JSONObject))throw new IllegalArgumentException();JSONObject edit=(JSONObject)value;if(edit.length()!=2||!(edit.opt("package") instanceof String)||!(edit.opt("enabled") instanceof Boolean)||!store.eligible(edit.getString("package")))throw new IllegalArgumentException();state.enable(edit.getString("package"),edit.getBoolean("enabled"));break;
                    default:throw new IllegalArgumentException();}}catch(PhoneSettingsServer.Ended|PhoneSettingsServer.Rejected e){throw e;}catch(IOException e){throw new PhoneSettingsServer.Rejected(e.getMessage());}catch(JSONException e){throw new IllegalArgumentException(e);}
                });ChildUsageService.changed();
            }else throw new IllegalArgumentException();changed.run();
        }catch(JSONException e){throw new IllegalArgumentException();}
    }
}
