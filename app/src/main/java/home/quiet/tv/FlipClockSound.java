package home.quiet.tv;

import android.content.Context;
import android.media.*;
import android.os.Build;

/** One original mechanical landing cue; independent of video sound and navigation cues. */
final class FlipClockSound {
    private final AudioManager audio;
    private final SoundPool pool;
    private final int sample;
    private boolean loaded,released;
    private int playing;
    FlipClockSound(Context context){
        Context app=context.getApplicationContext();audio=(AudioManager)app.getSystemService(Context.AUDIO_SERVICE);
        pool=Build.VERSION.SDK_INT>=21?Api21.create():new SoundPool(1,AudioManager.STREAM_MUSIC,0);
        pool.setOnLoadCompleteListener((p,id,status)->{if(!released)loaded=status==0;});
        try{sample=pool.load(app,R.raw.flip_clock,1);if(sample==0)throw new IllegalStateException("Flip sound unavailable");}
        catch(RuntimeException error){pool.release();throw error;}
    }
    void play(){
        if(released||!loaded||audio==null||audio.getStreamVolume(AudioManager.STREAM_MUSIC)==0||Build.VERSION.SDK_INT>=23&&Api23.muted(audio))return;
        try{stop();playing=pool.play(sample,.32f,.32f,1,0,1);}catch(RuntimeException error){DiagnosticLog.error("screensaver.flip_sound.unavailable",error);}
    }
    void stop(){if(!released&&playing!=0){pool.stop(playing);playing=0;}}
    void destroy(){if(released)return;stop();released=true;loaded=false;pool.release();}
    @androidx.annotation.RequiresApi(21) private static final class Api21 {static SoundPool create(){return new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();}}
    @androidx.annotation.RequiresApi(23) private static final class Api23 {static boolean muted(AudioManager audio){return audio.isStreamMute(AudioManager.STREAM_MUSIC);}}
}
