package home.quiet.tv;

import android.content.Context;
import android.media.*;
import android.os.*;

/** Three original modal-synthesis wooden taps (34/64/50ms, mono PCM16 at 44.1kHz).
 * No external recordings. Never changes volume or requests audio focus. */
final class KeySounds {
    private static KeySounds instance;
    private final DesktopSettingsStore settings;
    private final AudioManager audio;
    private final SoundPool pool;
    private final int[] ids=new int[3];
    private final boolean[] loaded=new boolean[3];
    private final KeySoundPolicy policy=new KeySoundPolicy();
    private int playing;
    private String lastSkip="";
    private KeySounds(Context context){
        Context app=context.getApplicationContext();settings=new DesktopSettingsStore(app);audio=(AudioManager)app.getSystemService(Context.AUDIO_SERVICE);
        pool=Build.VERSION.SDK_INT>=21?Api21.create():new SoundPool(1,AudioManager.STREAM_MUSIC,0);
        pool.setOnLoadCompleteListener((p,id,status)->{for(int i=0;i<ids.length;i++)if(ids[i]==id){loaded[i]=status==0;DiagnosticLog.info("input.sound.loaded","sound="+i+" status="+status);}});
        int[] resources={R.raw.key_move,R.raw.key_confirm,R.raw.key_back};
        try{for(int i=0;i<ids.length;i++){ids[i]=pool.load(app,resources[i],1);if(ids[i]==0)throw new IllegalStateException("Sound resource unavailable");}}
        catch(RuntimeException failure){pool.release();throw failure;}
    }
    static void prepare(Context context){if(instance==null && HomeGuardState.unlocked(context))try{instance=new KeySounds(context);}catch(RuntimeException failure){DiagnosticLog.error("input.sound.unavailable",failure);}}
    static void play(Context context,int sound){if(instance!=null)instance.play(sound);}
    private void play(int sound){
        try{
            boolean audible=audio!=null&&audio.getStreamVolume(AudioManager.STREAM_MUSIC)>0;
            if(Build.VERSION.SDK_INT>=23&&audio!=null)audible&=!Api23.muted(audio);
            boolean enabled=settings.keySounds();
            if(!enabled||!audible||!loaded[sound]){skipped(!enabled?"disabled":!audible?"media_muted":"not_loaded");return;}
            if(!policy.allow(sound,SystemClock.uptimeMillis(),enabled,audible,loaded[sound]))return;
            if(playing!=0)pool.stop(playing);
            float volume=sound==KeySoundPolicy.MOVE?.28f:.38f;
            playing=pool.play(ids[sound],volume,volume,sound==KeySoundPolicy.MOVE?0:1,0,1);
            if(playing==0)skipped("stream_rejected");else lastSkip="";
        }catch(RuntimeException failure){DiagnosticLog.error("input.sound.unavailable",failure);}
    }
    private void skipped(String reason){if(!reason.equals(lastSkip)){lastSkip=reason;DiagnosticLog.info("input.sound.skipped","reason="+reason);}}
    static void stop(){if(instance!=null&&instance.playing!=0){instance.pool.stop(instance.playing);instance.playing=0;}}
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
