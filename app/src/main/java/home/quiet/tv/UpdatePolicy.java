package home.quiet.tv;

/** Pure timing rules shared by the scheduler and transfer tests. */
final class UpdatePolicy {
    static final long DAY=24*60*60*1000L,FAILURE_DELAY=30*60*1000L;
    static boolean due(long now,long success,long failure){
        return (success==0||now<success||now-success>=DAY)&&(failure==0||now<failure||now-failure>=FAILURE_DELAY);
    }
    static boolean nearlyDone(long downloaded,long total){return total>0&&(downloaded>=total-total/20||total-downloaded<=512*1024);}
    static final class Speed {
        private long started,bytes;private int slow;
        Speed(long now,long downloaded){started=now;bytes=downloaded;}
        boolean switchSource(long now,long downloaded,long total){
            long elapsed=now-started;if(elapsed<5000)return false;
            slow=(downloaded-bytes)*1000/Math.max(1,elapsed)<100*1024?slow+1:0;started=now;bytes=downloaded;
            return slow>=3&&!nearlyDone(downloaded,total);
        }
    }
}
