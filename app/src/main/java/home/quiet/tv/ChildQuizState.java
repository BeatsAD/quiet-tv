package home.quiet.tv;

import java.io.*;
import java.text.*;
import java.util.*;
import org.json.*;

/** One persisted learning gate shared by the TV and both authenticated phone surfaces. */
final class ChildQuizState {
    static final long MINUTE=60_000L,MAX_ROUND=5*MINUTE;
    static final class Settings {
        final int interval;
        final String grade,topic,theme;
        final String[] subjects;
        Settings(int interval,String grade,String topic)throws IOException{this(interval,grade,topic,new String[]{"数学"},"auto");}
        Settings(int interval,String grade,String[] subjects)throws IOException{this(interval,grade,ChildQuizBank.ALL,subjects,"auto");}
        Settings(int interval,String grade,String topic,String[] subjects,String theme)throws IOException{
            if(interval!=10&&interval!=15&&interval!=20||!Arrays.asList(ChildQuizBank.GRADES).contains(grade)||subjects.length<1||subjects.length>ChildQuizBank.SUBJECTS.length||new HashSet<>(Arrays.asList(subjects)).size()!=subjects.length||!Arrays.asList(ChildQuizBank.SUBJECTS).containsAll(Arrays.asList(subjects)))throw new IOException("答题设置无效");
            // Accept old documents, but retired overrides cannot restrict subjects or artwork.
            this.interval=interval;this.grade=grade;this.topic=ChildQuizBank.ALL;this.subjects=subjects.clone();this.theme="auto";
        }
        Settings(JSONObject value)throws JSONException,IOException{this(value.getInt("interval"),value.getString("grade"),strings(value.optJSONArray("subjects"),new String[]{"数学"}));}
        JSONObject json()throws JSONException{return new JSONObject().put("interval",interval).put("grade",grade).put("topic",topic).put("subjects",new JSONArray(Arrays.asList(subjects))).put("theme",theme).put("base",1).put("addOnWrong",false).put("review",false);}
        boolean customOnly(){return subjects.length==1&&ChildQuizBank.CUSTOM.equals(subjects[0]);}
    }
    final JSONObject data;
    final ChildQuizBank bank;
    ChildQuizState(ChildQuizBank bank,String day)throws IOException{
        this.bank=bank;
        try{data=new JSONObject().put("version",2).put("bankVersion",bank.version).put("day",validDay(day)).put("settings",new Settings(15,"二年级",new String[]{"数学"}).json()).put("apps",new JSONObject()).put("elapsed",0).put("sequence",0).put("reviews",new JSONObject()).put("attempts",new JSONArray()).put("rounds",new JSONArray()).put("customQuestions",new JSONArray()).put("selectedCustomIds",new JSONArray());}
        catch(JSONException impossible){throw new IOException("学习记录初始化失败",impossible);}
    }
    ChildQuizState(ChildQuizBank bank,JSONObject saved)throws IOException{
        this.bank=bank;data=saved;
        try{
            int version=data.getInt("version");if(version!=1&&version!=2)throw new IOException("学习记录格式不支持，原记录已保留");
            if(version==1){data.put("version",2).put("customQuestions",new JSONArray()).put("selectedCustomIds",new JSONArray());data.put("settings",settings().json());}
            JSONObject overrides=data.optJSONObject("appSettings"),elapsed=data.optJSONObject("appElapsed");if(data.has("appSettings")&&overrides==null||data.has("appElapsed")&&elapsed==null)throw new IOException("应用答题设置无效");if(overrides!=null)for(Iterator<String> keys=overrides.keys();keys.hasNext();)new Settings(overrides.getJSONObject(keys.next()));if(elapsed!=null)for(Iterator<String> keys=elapsed.keys();keys.hasNext();)if(elapsed.getLong(keys.next())<0)throw new IOException("应用答题计时无效");
            settings();validDay(data.getString("day"));if(data.getLong("elapsed")<0||data.getLong("sequence")<0)throw new IOException("学习进度无效");
            JSONObject apps=data.getJSONObject("apps");for(Iterator<String> ids=apps.keys();ids.hasNext();)apps.getBoolean(ids.next());data.getJSONObject("reviews");data.getJSONArray("attempts");data.getJSONArray("rounds");
            JSONArray custom=data.getJSONArray("customQuestions");if(custom.length()>100)throw new IOException("自定义题目数量超出范围");Set<String> ids=new HashSet<>();for(int i=0;i<custom.length();i++){ChildQuizBank.Question q=new ChildQuizBank.Question(custom.getJSONObject(i));if(!ChildQuizBank.CUSTOM.equals(q.subject)||!q.id.startsWith("custom-")||!ids.add(q.id)||custom.getJSONObject(i).getLong("revision")<1)throw new IOException("自定义题目记录无效");}
            Set<String> selected=new HashSet<>();JSONArray chosen=data.getJSONArray("selectedCustomIds");for(int i=0;i<chosen.length();i++)if(!ids.contains(chosen.getString(i))||!selected.add(chosen.getString(i)))throw new IOException("自定义选题记录无效");
            JSONObject session=session();if(session!=null){
                String status=session.getString("status");if(!Arrays.asList("question","feedback","complete","limit").contains(status)||session.getLong("spent")<0||session.getLong("spent")>MAX_ROUND||session.getLong("id")<1||session.getLong("id")>data.getLong("sequence")||session.getInt("correct")<0||session.getInt("errors")<0)throw new IOException("待答记录无效");new Settings(session.getJSONObject("settings"));
                if(!session.has("question")){JSONArray seen=session.getJSONArray("seen");if(seen.length()==0)throw new IOException("待答记录不完整");session.put("question",bank.get(seen.getString(0)).document);session.put("passed",session.optInt("correct")>0);if(!session.optBoolean("passed")){session.put("status","question").put("spent",0).remove("feedback");}}
                ChildQuizBank.Question question=new ChildQuizBank.Question(session.getJSONObject("question"));if(session.has("feedback")&&!question.id.equals(session.getJSONObject("feedback").getString("questionId")))throw new IOException("待答题目标识不一致");
                if(session.optBoolean("passed")&&session.getInt("correct")<1)throw new IOException("答题通过记录无效");
            }
        }catch(JSONException failure){throw new IOException("学习记录无法读取，原记录已保留",failure);}
    }
    static String[] strings(JSONArray values,String[] fallback)throws JSONException{if(values==null)return fallback.clone();String[] result=new String[values.length()];for(int i=0;i<result.length;i++)result[i]=values.getString(i);return result;}
    Settings settings()throws IOException{try{return new Settings(data.getJSONObject("settings"));}catch(JSONException failure){throw new IOException("答题设置无法读取",failure);}}
    Settings settings(String pkg)throws IOException{JSONObject values=data.optJSONObject("appSettings");try{return values!=null&&values.has(pkg)?new Settings(values.getJSONObject(pkg)):settings();}catch(JSONException e){throw new IOException("应用答题设置无法读取",e);}}
    private boolean scoped(String pkg){JSONObject values=data.optJSONObject("appSettings");return values!=null&&values.has(pkg);}
    private long elapsed(String pkg){JSONObject values=data.optJSONObject("appElapsed");return scoped(pkg)?values==null?0:values.optLong(pkg):data.optLong("elapsed");}
    private void elapsed(String pkg,long value)throws JSONException{if(!scoped(pkg)){data.put("elapsed",value);return;}JSONObject values=data.optJSONObject("appElapsed");if(values==null){values=new JSONObject();data.put("appElapsed",values);}values.put(pkg,value);}
    long untilDue(String pkg)throws IOException{return Math.max(0,settings(pkg).interval*MINUTE-elapsed(pkg));}
    void configure(String pkg,Settings value)throws JSONException,IOException{if(pkg.isEmpty()){configure(value);return;}if(pool(value).isEmpty())throw new IOException("请先选择至少一道可用题目");long watched=elapsed(pkg);JSONObject values=data.optJSONObject("appSettings");if(values==null){values=new JSONObject();data.put("appSettings",values);}values.put(pkg,value.json());elapsed(pkg,watched);}
    void cancelPending(String pkg)throws JSONException,IOException{if(session()!=null&&pending(pkg))finish(session().getLong("id"),true,day());}
    JSONObject session(){return data.optJSONObject("session");}
    boolean enabled(String pkg){return data.optJSONObject("apps").optBoolean(pkg,false);}
    boolean pending(String pkg){JSONObject round=session();return round!=null&&(round.optString("package").isEmpty()||round.optString("package").equals(pkg));}
    long untilDue()throws IOException{return Math.max(0,settings().interval*MINUTE-data.optLong("elapsed"));}
    void configure(Settings value)throws JSONException,IOException{if(pool(value).isEmpty())throw new IOException("请先选择至少一道可用题目");data.put("settings",value.json());}
    void enable(String pkg,boolean enabled)throws JSONException,IOException{if(enabled&&pool(settings(pkg)).isEmpty())throw new IOException("请先选择至少一道可用题目");data.getJSONObject("apps").put(pkg,enabled);}
    private String day(){return data.optString("day");}
    private void atDay(String value)throws IOException,JSONException{validDay(value);if(value.compareTo(day())>0)data.put("day",value);}
    List<ChildQuizBank.Question> pool(Settings settings)throws IOException,JSONException{
        List<ChildQuizBank.Question> result=bank.pool(settings.grade,ChildQuizBank.ALL,settings.subjects);if(Arrays.asList(settings.subjects).contains(ChildQuizBank.CUSTOM)){Set<String> selected=new HashSet<>(Arrays.asList(strings(data.getJSONArray("selectedCustomIds"),new String[0])));JSONArray custom=data.getJSONArray("customQuestions");for(int i=0;i<custom.length();i++){JSONObject item=custom.getJSONObject(i);if(selected.contains(item.getString("id")))result.add(new ChildQuizBank.Question(item));}}return result;
    }
    JSONObject custom(String id)throws IOException,JSONException{JSONArray questions=data.getJSONArray("customQuestions");for(int i=0;i<questions.length();i++)if(questions.getJSONObject(i).getString("id").equals(id))return questions.getJSONObject(i);throw new IOException("题目已不存在，请刷新列表");}
    void putQuestion(JSONObject edit)throws IOException,JSONException{
        String id=edit.optString("id");boolean fresh=id.isEmpty();if(fresh)id="custom-"+UUID.randomUUID().toString();else if(!id.startsWith("custom-"))throw new IOException("题目标识无效");
        long revision=fresh?0:custom(id).getLong("revision");if(!fresh&&edit.getLong("revision")!=revision)throw new IOException("题目已被修改，请刷新后重试");
        JSONObject next=new JSONObject().put("id",id).put("subject",ChildQuizBank.CUSTOM).put("prompt",edit.getString("prompt").trim()).put("choices",edit.getJSONArray("choices")).put("answer",edit.getInt("answer")).put("revision",revision+1);new ChildQuizBank.Question(next);
        JSONArray before=data.getJSONArray("customQuestions"),after=new JSONArray();if(fresh&&before.length()>=100)throw new IOException("最多保存 100 道自定义题目");for(int i=0;i<before.length();i++)after.put(before.getJSONObject(i).getString("id").equals(id)?next:before.getJSONObject(i));if(fresh)after.put(next);data.put("customQuestions",after);
    }
    void selectQuestions(JSONArray selected)throws IOException,JSONException{
        Set<String> unique=new HashSet<>();for(int i=0;i<selected.length();i++){String id=selected.getString(i);custom(id);if(!unique.add(id))throw new IOException("选题重复");}JSONArray previous=data.getJSONArray("selectedCustomIds");data.put("selectedCustomIds",selected);try{validateCustomPools();}catch(IOException e){data.put("selectedCustomIds",previous);throw e;}
    }
    void deleteQuestion(String id,long revision)throws IOException,JSONException{
        JSONObject current=custom(id);if(current.getLong("revision")!=revision)throw new IOException("题目已被修改，请刷新后重试");JSONArray previous=data.getJSONArray("customQuestions"),chosen=data.getJSONArray("selectedCustomIds"),after=new JSONArray(),selected=new JSONArray();for(int i=0;i<previous.length();i++)if(!previous.getJSONObject(i).getString("id").equals(id))after.put(previous.getJSONObject(i));for(int i=0;i<chosen.length();i++)if(!chosen.getString(i).equals(id))selected.put(chosen.getString(i));data.put("customQuestions",after).put("selectedCustomIds",selected);try{validateCustomPools();}catch(IOException e){data.put("customQuestions",previous).put("selectedCustomIds",chosen);throw e;}
    }
    private void validateCustomPools()throws IOException,JSONException{
        if(pool(settings()).isEmpty())throw new IOException("请至少保留一道可用题目，或先选择其他科目");JSONObject overrides=data.optJSONObject("appSettings");if(overrides!=null)for(Iterator<String> keys=overrides.keys();keys.hasNext();){String pkg=keys.next();if(pool(settings(pkg)).isEmpty())throw new IOException("仍有应用使用这些题目，请先修改该应用的答题设置");}
    }
    void watch(String pkg,long elapsed,String today,boolean mayStart)throws IOException,JSONException{if(elapsed<0)throw new IOException("观看计时无效");atDay(today);if(!enabled(pkg)||session()!=null)return;elapsed(pkg,elapsed(pkg)+elapsed);if(mayStart&&untilDue(pkg)==0)begin(pkg,"");}
    void startNow(String pkg,String request)throws IOException,JSONException{if(pkg.isEmpty()||request.isEmpty())throw new IOException("立即答题目标无效");JSONArray rounds=data.getJSONArray("rounds");for(int i=0;i<rounds.length();i++)if(request.equals(rounds.getJSONObject(i).optString("requestId")))return;if(session()!=null){if(!pending(pkg))throw new IOException("已有另一应用的待答题目");return;}begin(pkg,request);}
    void withdrawUnshown(String request){
        JSONObject pending=session();
        if(pending!=null&&request.equals(pending.optString("requestId"))&&!pending.optBoolean("visible"))
            data.remove("session");
    }
    private void begin(String pkg,String request)throws IOException,JSONException{
        Settings settings=settings(pkg);List<ChildQuizBank.Question> pool=pool(settings);long sequence=data.getLong("sequence");if(pool.isEmpty())throw new IOException("当前出题范围没有可用题目");List<String> available=new ArrayList<>();for(String subject:settings.subjects)for(ChildQuizBank.Question candidate:pool)if(subject.equals(candidate.subject)){available.add(subject);break;}String subject=available.get((int)(sequence%available.size()));List<ChildQuizBank.Question> subjectPool=new ArrayList<>();for(ChildQuizBank.Question candidate:pool)if(subject.equals(candidate.subject))subjectPool.add(candidate);ChildQuizBank.Question question=subjectPool.get((int)((sequence/available.size())%subjectPool.size()));
        data.put("sequence",sequence+1).put("session",new JSONObject().put("id",sequence+1).put("status","question").put("package",pkg).put("requestId",request).put("question",new JSONObject(question.document.toString())).put("passed",false).put("visible",false).put("bankVersion",bank.version).put("settings",settings.json()).put("queue",new JSONArray().put(new JSONObject().put("id",question.id).put("review",false))).put("seen",new JSONArray().put(question.id)).put("failed",new JSONArray()).put("correct",0).put("errors",0).put("spent",0));
    }
    ChildQuizBank.Question question()throws IOException{try{JSONObject session=session();return session==null?null:new ChildQuizBank.Question(session.getJSONObject("question"));}catch(JSONException e){throw new IOException("本轮题目无法读取",e);}}
    void shown(String pkg)throws JSONException{if(pending(pkg))session().put("visible",true);}
    void hidden(String pkg)throws JSONException{if(pending(pkg))session().put("visible",false);}
    void answer(long round,String questionId,int choice,String today)throws IOException,JSONException{
        atDay(today);JSONObject session=session();ChildQuizBank.Question q=question();if(session==null||session.getLong("id")!=round||!"question".equals(session.getString("status"))||q==null||!q.id.equals(questionId)||!q.accepts(choice))throw new IOException("题目已变化，请重新选择");boolean correct=choice==q.answer;
        session.put("passed",correct).put("correct",session.getInt("correct")+(correct?1:0)).put("errors",session.getInt("errors")+(correct?0:1)).put("status","feedback").put("feedback",new JSONObject().put("questionId",q.id).put("choice",choice).put("correct",correct));if(correct)session.put("queue",new JSONArray());
        append("attempts",new JSONObject().put("questionId",q.id).put("bankVersion",bank.version).put("correct",correct).put("day",day()).put("round",round),300);
    }
    void next(long round)throws IOException,JSONException{JSONObject session=session();if(session==null||session.getLong("id")!=round||!"feedback".equals(session.getString("status")))throw new IOException("答题状态已变化");session.put("status",session.optBoolean("passed")?"complete":"question").remove("feedback");}
    void spend(long elapsed)throws IOException,JSONException{if(elapsed<0)throw new IOException("答题计时无效");JSONObject session=session();if(session==null||!"question".equals(session.optString("status")))return;long spent=Math.min(MAX_ROUND,session.getLong("spent")+elapsed);session.put("spent",spent);if(spent>=MAX_ROUND)session.put("status","limit").put("reason","请家长帮忙后再继续");}
    void finish(long round,boolean skipped,String today)throws IOException,JSONException{
        atDay(today);JSONObject session=session();if(session==null||session.getLong("id")!=round||!skipped&&!session.optBoolean("passed"))throw new IOException("答对后才能继续观看");append("rounds",new JSONObject().put("id",round).put("requestId",session.optString("requestId")).put("day",day()).put("count",session.getJSONArray("seen").length()).put("errors",session.getInt("errors")).put("skipped",skipped).put("passed",!skipped),60);String pkg=session.optString("package");data.remove("session");elapsed(pkg,0);
    }
    private void append(String field,JSONObject item,int limit)throws JSONException{JSONArray before=data.getJSONArray(field),after=new JSONArray();for(int i=Math.max(0,before.length()-limit+1);i<before.length();i++)after.put(before.get(i));after.put(item);data.put(field,after);}
    static String validDay(String value)throws IOException{try{if(value==null||!value.matches("\\d{4}-\\d{2}-\\d{2}"))throw new ParseException("date",0);SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);format.setLenient(false);format.parse(value);return value;}catch(ParseException failure){throw new IOException("学习日期无效",failure);}}
}
