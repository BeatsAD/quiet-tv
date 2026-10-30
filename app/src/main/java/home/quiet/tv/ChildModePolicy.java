package home.quiet.tv;

import java.text.*;
import java.util.*;

/** Local foreground-use budgets. Locking never changes the saved time rule. */
final class ChildModePolicy {
    static final int NONE=0,SINGLE=1,DAILY=2;
    static final int EVERY_DAY=127,WEEKDAYS=31,WEEKEND=96;
    static final long MINUTE=60_000L,DAY=24*60*MINUTE;
    static final class Rule {
        boolean hidden,locked;
        int mode,repeatDays=EVERY_DAY;
        long limit=60*MINUTE,singleUsed,dailyUsed,singleExtra,dailyExtra,dailyExtraUsed;
        String day="",currentDay="";
        Rule copy(){Rule r=new Rule();r.hidden=hidden;r.locked=locked;r.mode=mode;r.repeatDays=repeatDays;r.limit=limit;r.singleUsed=singleUsed;r.dailyUsed=dailyUsed;r.singleExtra=singleExtra;r.dailyExtra=dailyExtra;r.dailyExtraUsed=dailyExtraUsed;r.day=day;r.currentDay=currentDay;return r;}
        boolean limited(){return mode==SINGLE || mode==DAILY;}
        void validate(){if(mode<NONE || mode>DAILY || repeatDays<1 || repeatDays>EVERY_DAY || limit<MINUTE || limit>DAY || singleUsed<0 || dailyUsed<0 || singleExtra<0 || singleExtra>DAY || dailyExtra<0 || dailyExtra>DAY || dailyExtraUsed<0 || dailyExtraUsed>dailyExtra || dailyExtraUsed>dailyUsed)throw new IllegalArgumentException("儿童模式规则无效");}
        void newDay(String today){
            currentDay=today;
            // Clock rollback must not grant a fresh budget for an already visited date.
            if(day.isEmpty() || today.compareTo(day)>0){day=today;dailyUsed=0;dailyExtra=0;dailyExtraUsed=0;}
        }
        boolean scheduled(){return mode!=DAILY || repeatDays==EVERY_DAY || (repeatDays & dayBit(currentDay.isEmpty()?day:currentDay))!=0;}
        private long baseRemaining(){return scheduled()?Math.max(0,limit-(dailyUsed-dailyExtraUsed)):0;}
        long remaining(){if(!limited())return Long.MAX_VALUE;return mode==SINGLE?Math.max(0,limit+singleExtra-singleUsed):baseRemaining()+dailyExtra-dailyExtraUsed;}
        String repeatLabel(){
            if(repeatDays==EVERY_DAY)return "每天";if(repeatDays==WEEKDAYS)return "周一至周五";if(repeatDays==WEEKEND)return "周六和周日";
            StringBuilder value=new StringBuilder();String[] names={"周一","周二","周三","周四","周五","周六","周日"};
            for(int i=0;i<7;i++)if((repeatDays & (1<<i))!=0){if(value.length()>0)value.append("、");value.append(names[i]);}return value.toString();
        }
        String nextDate(){
            Calendar date=date(currentDay.isEmpty()?day:currentDay);SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);
            for(int i=0;i<7;i++){date.add(Calendar.DAY_OF_MONTH,1);if((repeatDays & bit(date))!=0)return format.format(date.getTime());}throw new IllegalStateException("未选择重复日期");
        }
        void consume(long elapsed,String today,long sinceMidnight){
            if(elapsed<0)throw new IllegalArgumentException("计时间隔无效");
            boolean changed=!day.isEmpty() && today.compareTo(day)>0;newDay(today);
            if(!limited())return;
            long amount=Math.min(elapsed,remaining());
            if(mode==DAILY && changed)amount=Math.min(amount,Math.max(0,sinceMidnight));
            if(mode==SINGLE)singleUsed+=amount;else{dailyExtraUsed+=Math.max(0,amount-baseRemaining());dailyUsed+=amount;}
        }
        void grant(long amount){
            if(!limited() || amount<MINUTE || amount>DAY)throw new IllegalArgumentException("追加时间需为 1 分钟至 24 小时");
            if(mode==SINGLE){if(singleExtra+amount>DAY)throw new IllegalArgumentException("本次追加时间已达上限");singleExtra+=amount;}
            else{if(dailyExtra+amount>DAY)throw new IllegalArgumentException("今日追加时间已达上限");dailyExtra+=amount;}
        }
    }
    private static Calendar date(String value){
        try{SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);format.setLenient(false);Calendar date=Calendar.getInstance();date.setTime(format.parse(value));return date;}
        catch(ParseException invalid){throw new IllegalArgumentException("使用日期无效",invalid);}
    }
    private static int bit(Calendar date){return 1<<((date.get(Calendar.DAY_OF_WEEK)+5)%7);}
    static int dayBit(String value){return bit(date(value));}
    static final class TriplePress {
        private int count;
        private long last;
        boolean press(long now){if(count==0 || now<last || now-last>2000)count=0;last=now;return ++count>=3;}
        void clear(){count=0;last=0;}
    }
}
