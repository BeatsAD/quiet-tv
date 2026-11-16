package home.quiet.tv;

import java.io.*;
import java.util.*;
import org.json.*;

/** Versioned, bundled questions. Every published question is checked independently of its answer key. */
final class ChildQuizBank {
    static final String ALL="全部知识点",CUSTOM="自定义出题";
    static final String[] GRADES={"一年级","二年级","三年级","四年级","五年级","六年级"};
    static final String[] SUBJECTS={"语文","数学","英语","科学","文化常识","益智问答",CUSTOM};
    static final String[][] TOPICS={{"20以内加减法"},{"100以内加减法","乘法口诀"},{"乘法口诀","两位数乘法"},{"四则运算"},{"小数与分数"},{"比例与几何"}};
    static final class Question {
        final String id,subject,grade,topic,prompt,explanation;
        final int answer;
        final int[] choices;
        final String[] labels;
        final JSONObject document;
        Question(JSONObject item)throws JSONException,IOException{
            document=new JSONObject(item.toString());id=item.getString("id");subject=item.optString("subject","数学");grade=item.optString("grade");topic=item.optString("topic",ALL);prompt=item.getString("prompt");explanation=item.optString("explanation");answer=item.getInt("answer");
            if(!id.matches("[a-z0-9-]{1,80}")||!Arrays.asList(SUBJECTS).contains(subject)||(!CUSTOM.equals(subject)&&!Arrays.asList(GRADES).contains(grade)))throw new IOException("题目分类或标识无效");
            text(prompt,160,"题目");JSONArray values=item.getJSONArray("choices");if(values.length()!=4)throw new IOException("请填写四个单选项");choices=new int[4];labels=new String[4];Set<String> unique=new HashSet<>();
            boolean arithmetic=item.has("operation");
            for(int i=0;i<4;i++){choices[i]=arithmetic?values.getInt(i):i;labels[i]=arithmetic?String.valueOf(choices[i]):values.getString(i);text(labels[i],64,"选项");if(!unique.add(labels[i].trim()))throw new IOException("题目选项不能重复");}
            if(!arithmetic){if(answer<0||answer>3)throw new IOException("请选择一个正确答案");return;}
            int a=item.getInt("a"),b=item.getInt("b");String operation=item.getString("operation");int calculated;
            if("+".equals(operation))calculated=a+b;else if("−".equals(operation))calculated=a-b;else if("×".equals(operation))calculated=a*b;else throw new IOException("题库运算符无效");
            if(a<0||b<0||answer!=calculated||!prompt.equals(a+" "+operation+" "+b+" = ?")||!validTopic(grade,topic))throw new IOException("题库题干或答案校验失败");
            if("20以内加减法".equals(topic)&&("×".equals(operation)||a>20||b>20||answer<0||answer>20)||"100以内加减法".equals(topic)&&("×".equals(operation)||a>100||b>100||answer<0||answer>100)||"乘法口诀".equals(topic)&&(!"×".equals(operation)||a<1||a>9||b<1||b>9)||"两位数乘法".equals(topic)&&(!"×".equals(operation)||a<10||a>99||b<10||b>99))throw new IOException("题库知识点范围校验失败");
            String checked="×".equals(operation)?a+" × "+b+" 表示 "+b+" 个 "+a+" 相加，结果是 "+answer+"。":"−".equals(operation)?"从 "+a+" 里面减去 "+b+"，还剩 "+answer+"。":"把 "+a+" 和 "+b+" 合起来，一共是 "+answer+"。";
            if(!checked.equals(explanation))throw new IOException("题库解析校验失败");
            int correct=0;for(int value:choices)if(value==answer)correct++;if(correct!=1)throw new IOException("题库单选答案无效");
        }
        boolean accepts(int choice){for(int value:choices)if(value==choice)return true;return false;}
        String label(int choice){for(int i=0;i<choices.length;i++)if(choices[i]==choice)return labels[i];return "";}
    }
    static void text(String value,int maximum,String name)throws IOException{if(value.trim().isEmpty()||value.length()>maximum)throw new IOException(name+"需为 1–"+maximum+" 个字");for(int i=0;i<value.length();i++)if(Character.isISOControl(value.charAt(i))&&value.charAt(i)!='\n')throw new IOException(name+"包含无效字符");}
    final String version;
    private final LinkedHashMap<String,Question> questions=new LinkedHashMap<>();
    ChildQuizBank(InputStream input)throws IOException{
        try{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))!=-1)out.write(buffer,0,count);JSONObject root=new JSONObject(out.toString("UTF-8"));if(root.getInt("format")!=1)throw new IOException("题库格式不支持");version=root.getString("version");JSONArray entries=root.getJSONArray("questions");Set<String> prompts=new HashSet<>();
            for(int i=0;i<entries.length();i++){Question q=new Question(entries.getJSONObject(i));if(questions.put(q.id,q)!=null||!prompts.add(q.grade+"/"+q.topic+"/"+q.prompt))throw new IOException("题库题目标识或题干重复");}
            for(int grade=0;grade<3;grade++)for(String topic:TOPICS[grade])if(pool(GRADES[grade],topic).size()<5)throw new IOException("知识点题量不足");
        }catch(JSONException failure){throw new IOException("题库无法读取",failure);}
    }
    ChildQuizBank(InputStream legacy,InputStream subjects)throws IOException{this(legacy);try{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;while((count=subjects.read(buffer))!=-1)out.write(buffer,0,count);JSONObject root=new JSONObject(out.toString("UTF-8"));if(root.getInt("format")!=2)throw new IOException("多科题库格式无效");JSONArray entries=root.getJSONArray("questions");for(int i=0;i<entries.length();i++){Question question=new Question(entries.getJSONObject(i));if(CUSTOM.equals(question.subject)||questions.put(question.id,question)!=null)throw new IOException("多科题目标识重复");}for(String grade:GRADES)for(String subject:SUBJECTS)if(!CUSTOM.equals(subject)&&pool(grade,ALL,new String[]{subject}).size()<5)throw new IOException(subject+grade+"题量不足");}catch(JSONException e){throw new IOException("多科题库无法读取",e);}}
    static boolean validTopic(String grade,String topic){if(ALL.equals(topic))return Arrays.asList(GRADES).contains(grade);for(int i=0;i<GRADES.length;i++)if(GRADES[i].equals(grade))return Arrays.asList(TOPICS[i]).contains(topic);return false;}
    Question get(String id)throws IOException{Question value=questions.get(id);if(value==null)throw new IOException("学习记录中的题目暂不可用，原记录已保留");return value;}
    List<Question> pool(String grade,String topic){List<Question> result=new ArrayList<>();for(Question value:questions.values())if(value.grade.equals(grade)&&value.topic.equals(topic))result.add(value);return result;}
    List<Question> pool(String grade,String topic,String[] subjects){List<Question> result=new ArrayList<>();for(Question value:questions.values())if(value.grade.equals(grade)&&Arrays.asList(subjects).contains(value.subject)&&(ALL.equals(topic)||value.topic.equals(topic)))result.add(value);return result;}
    String[] topics(String grade,String[] subjects){LinkedHashSet<String> result=new LinkedHashSet<>();result.add(ALL);for(Question value:questions.values())if(value.grade.equals(grade)&&Arrays.asList(subjects).contains(value.subject))result.add(value.topic);return result.toArray(new String[0]);}
}
