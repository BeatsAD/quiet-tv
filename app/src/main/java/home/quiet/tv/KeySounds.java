package home.quiet.tv;

import android.content.Context;
import android.media.*;
import android.os.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Three original sound sets, each with move/confirm/back cues, mono PCM16 at 44.1kHz.
 * No external recordings. Never changes volume or requests audio focus. */
final class KeySounds {
    private static final long MAX_CUE_AGE_MS=250;
    private static final ExecutorService worker=Executors.newSingleThreadExecutor(task->new Thread(task,"quiet-key-sounds"));
    private static final AtomicBoolean movePending=new AtomicBoolean();
    private static final AtomicInteger stopGeneration=new AtomicInteger();
    private static volatile KeySounds instance;
    private final DesktopSettingsStore settings;
    private final AudioManager audio;
    private final SoundPool pool;
    private final int[] ids=new int[9];
    private final boolean[] loaded=new boolean[9];
    private final KeySoundPolicy policy=new KeySoundPolicy();
    private int playing;
    private String lastSkip="";
    private KeySounds(Context app,DesktopSettingsStore settings){
        this.settings=settings;audio=(AudioManager)app.getSystemService(Context.AUDIO_SERVICE);
        pool=Build.VERSION.SDK_INT>=21?Api21.create():new SoundPool(1,AudioManager.STREAM_MUSIC,0);
        pool.setOnLoadCompleteListener((p,id,status)->worker.execute(()->{
            for(int i=0;i<ids.length;i++)if(ids[i]==id){loaded[i]=status==0;DiagnosticLog.info("input.sound.loaded","sound="+i+" status="+status);}
        }));
        int[] resources={R.raw.key_move,R.raw.key_confirm,R.raw.key_back,
            R.raw.key_bell_move,R.raw.key_bell_confirm,R.raw.key_bell_back,
            R.raw.key_drop_move,R.raw.key_drop_confirm,R.raw.key_drop_back};
        try{for(int i=0;i<ids.length;i++){ids[i]=pool.load(app,resources[i],1);if(ids[i]==0)throw new IllegalStateException("Sound resource unavailable");}}
        catch(RuntimeException failure){pool.release();throw failure;}
    }
    private static KeySounds prepareOnWorker(Context app){
        if(instance!=null)return instance;
        long started=SystemClock.uptimeMillis();
        try{
            if(!HomeGuardState.unlocked(app))return null;
            DesktopSettingsStore settings=new DesktopSettingsStore(app);
            if(settings.keySounds())instance=new KeySounds(app,settings);
        }
        catch(RuntimeException failure){DiagnosticLog.error("input.sound.unavailable",failure);}
        finally{
            long elapsed=SystemClock.uptimeMillis()-started;
            if(elapsed>=100)DiagnosticLog.info("input.sound.prepare_slow","elapsedMs="+elapsed);
        }
        return instance;
    }
    static void prepare(Context context){
        if(instance!=null)return;
        Context app=context.getApplicationContext();
        worker.execute(()->prepareOnWorker(app));
    }
    static void play(Context context,int sound){
        boolean move=sound==KeySoundPolicy.MOVE;
        if(move&&!movePending.compareAndSet(false,true))return;
        Context app=context.getApplicationContext();
        int generation=stopGeneration.get();
        long requested=SystemClock.uptimeMillis();
        worker.execute(()->{
            try{
                if(generation!=stopGeneration.get()||SystemClock.uptimeMillis()-requested>MAX_CUE_AGE_MS)return;
                KeySounds ready=prepareOnWorker(app);
                if(ready!=null&&generation==stopGeneration.get())ready.play(sound,generation,requested);
            }finally{if(move)movePending.set(false);}
        });
    }
    private void play(int sound,int generation,long requested){
        long start=SystemClock.uptimeMillis(),playback=start;
        try{
            boolean enabled=settings.keySounds();
            if(!enabled){skipped("disabled");return;}
            int sample=settings.keySoundTheme().ordinal()*3+sound;
            if(!loaded[sample]){skipped("not_loaded");return;}
            boolean audible=audio!=null&&audio.getStreamVolume(AudioManager.STREAM_MUSIC)>0;
            if(Build.VERSION.SDK_INT>=23&&audio!=null)audible&=!Api23.muted(audio);
            playback=SystemClock.uptimeMillis();
            if(generation!=stopGeneration.get()||playback-requested>MAX_CUE_AGE_MS)return;
            if(!audible){skipped("media_muted");return;}
            if(!policy.allow(sound,requested,enabled,audible,loaded[sample]))return;
            stopPlaying();
            float volume=sound==KeySoundPolicy.MOVE?.28f:.38f;
            playing=pool.play(ids[sample],volume,volume,sound==KeySoundPolicy.MOVE?0:1,0,1);
            if(playing==0)skipped("stream_rejected");else lastSkip="";
        }catch(RuntimeException failure){DiagnosticLog.error("input.sound.unavailable",failure);}
        finally{
            long elapsed=SystemClock.uptimeMillis()-start;
            if(elapsed>=100)DiagnosticLog.info("input.sound.play_slow","elapsedMs="+elapsed+" beforePlayMs="+(playback-start));
        }
    }
    private void skipped(String reason){if(!reason.equals(lastSkip)){lastSkip=reason;DiagnosticLog.info("input.sound.skipped","reason="+reason);}}
    private void stopPlaying(){if(playing!=0){pool.stop(playing);playing=0;}}
    static void stop(){
        stopGeneration.incrementAndGet();
        KeySounds current=instance;
        if(current!=null)worker.execute(()->{
            try{current.stopPlaying();}
            catch(RuntimeException failure){DiagnosticLog.error("input.sound.unavailable",failure);}
        });
    }
    @androidx.annotation.RequiresApi(21)
    private static final class Api21 {
        static SoundPool create(){return new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();}
    }
    @androidx.annotation.RequiresApi(23)
    private static final class Api23 {
        static boolean muted(AudioManager audio){return audio.isStreamMute(AudioManager.STREAM_MUSIC);}
    }
}
