package home.quiet.tv;

import android.app.KeyguardManager;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;
import java.io.*;
import java.util.concurrent.*;
import org.json.*;

/** A real foreground learning gate; completing questions never changes viewing allowances. */
public final class ChildQuizActivity extends ManagementUi implements ChildQuizView.Actions {
    private String pkg="",label="",rendered="",failure="";
    private ChildQuizStore learning;
    private ChildQuizState state;
    private ChildModeStore child;
    private ChildQuizView view;
    private boolean active,parent,verified,verifying,leaving;
    private long last,pending;
    private int authGeneration;
    private final Handler clock=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private android.media.AudioManager audio;
    private final android.media.AudioManager.OnAudioFocusChangeListener audioFocus=change->{};
    private final Runnable tick=new Runnable(){public void run(){if(!active||leaving)return;if(restrictions())return;if(!parent&&interactive()){long now=SystemClock.elapsedRealtime();if(last>0)pending+=Math.max(0,now-last);last=now;if(pending>=1000)flush();}else last=0;clock.postDelayed(this,1000);}};
    private final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){last=0;flush();}};
    static Intent intent(Context context,String pkg){return new Intent(context,ChildQuizActivity.class).putExtra("package",pkg);}
    @Override boolean loadWallpaper(){return false;}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);root.setVisibility(View.GONE);child=new ChildModeStore(this);pkg=getIntent().getStringExtra("package");if(pkg==null||!child.eligible(pkg)){finish();return;}try{label=new AppNameStore(this).display(pkg,getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString());}catch(android.content.pm.PackageManager.NameNotFoundException missing){finish();return;}view=new ChildQuizView(this,this);stage.addView(view,new FrameLayout.LayoutParams(-1,-1));androidx.core.content.ContextCompat.registerReceiver(this,screen,new IntentFilter(Intent.ACTION_SCREEN_OFF),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);parent=saved!=null&&saved.getBoolean("quiz-parent");retry();}
    @Override protected void onResume(){super.onResume();active=true;ChildUsageService.blockVisible(pkg);if(learning!=null)try{state=learning.change(value->value.shown(pkg));}catch(IOException e){failure=e.getMessage();}if(view!=null)pausePlayback();if(parent)show();last=SystemClock.elapsedRealtime();clock.removeCallbacks(tick);clock.post(tick);}
    @Override protected void onPause(){active=false;++authGeneration;verified=verifying=false;if(last>0&&!parent&&interactive())pending+=Math.max(0,SystemClock.elapsedRealtime()-last);last=0;flush();if(learning!=null)try{state=learning.change(value->value.hidden(pkg));}catch(IOException e){failure=e.getMessage();}clock.removeCallbacks(tick);if(audio!=null)audio.abandonAudioFocus(audioFocus);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){flush();super.onSaveInstanceState(out);out.putBoolean("quiz-parent",parent);}
    @Override protected void onDestroy(){++authGeneration;clock.removeCallbacksAndMessages(null);if(view!=null)unregisterReceiver(screen);worker.shutdown();super.onDestroy();}
    private boolean interactive(){PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);KeyguardManager lock=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);return power!=null&&(Build.VERSION.SDK_INT>=20?power.isInteractive():power.isScreenOn())&&(lock==null||!UiCompat.keyguardLocked(lock));}
    private void pausePlayback(){audio=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);if(audio==null)return;try{int result=audio.requestAudioFocus(audioFocus,android.media.AudioManager.STREAM_MUSIC,android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);long now=SystemClock.uptimeMillis();for(int action:new int[]{KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP}){KeyEvent key=new KeyEvent(now,now,action,KeyEvent.KEYCODE_MEDIA_PAUSE,0);if(Build.VERSION.SDK_INT>=19)audio.dispatchMediaKeyEvent(key);else sendOrderedBroadcast(new Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg).putExtra(Intent.EXTRA_KEY_EVENT,key),null);}DiagnosticLog.info("child.quiz.playback","pause_requested audio_focus="+result);}catch(RuntimeException unavailable){DiagnosticLog.info("child.quiz.playback","pause_unavailable");}}
    private boolean restrictions(){if(child==null||pkg.isEmpty())return false;if(!child.enabled()){launch();return true;}try{if(!child.appEnabled(pkg,true)){launch();return true;}ChildModePolicy.Rule rule=child.effectiveRule(pkg);if(rule.hidden||rule.locked||rule.remaining()<=0){leaving=true;ChildUsageService.cancel();startActivity(ChildBlockActivity.intent(this,pkg));finish();return true;}}catch(RuntimeException unavailable){failure="儿童规则无法读取，请返回桌面检查";show();return true;}return false;}
    private long round()throws JSONException{return state.session().getLong("id");}
    private void change(ChildQuizStore.Change action){if(learning==null||state==null||verifying||leaving)return;try{state=learning.change(action);failure="";show();}catch(IOException unavailable){failure=unavailable.getMessage();show();}}
    private void flush(){if(pending<=0||learning==null||state==null||!failure.isEmpty())return;long elapsed=pending;try{state=learning.change(value->value.spend(elapsed));pending=0;show();}catch(IOException unavailable){failure="学习记录保存失败，请重试后继续";show();}}
    private void show(){if(view==null||leaving)return;try{if(parent){view.parent(label,verified,verifying,failure);return;}if(state==null){view.problem(label,failure.isEmpty()?"学习记录暂不可用":failure);return;}JSONObject session=state.session();if(session==null){view.problem(label,"当前没有待答题目，请返回桌面");return;}String key=session.getLong("id")+":"+session.getString("status")+":"+(state.question()==null?"":state.question().id);if(!key.equals(rendered)){rendered=key;view.render(label,state);}if(!failure.isEmpty())view.failure(failure);else view.clearFailure();}catch(IOException|JSONException invalid){failure=invalid.getMessage();view.problem(label,failure);}}
    @Override public void retry(){try{boolean initial=state==null;learning=ChildQuizStore.get(this);state=learning.read();failure="";if(initial)rendered="";if(pending>0)flush();show();}catch(IOException unavailable){failure=unavailable.getMessage();state=null;show();}}
    @Override public void answer(int choice){flush();if(!failure.isEmpty()||state==null||state.session()==null)return;try{long round=round();String question=state.question().id;change(value->value.answer(round,question,choice,ChildModeStore.today()));}catch(JSONException|IOException invalid){failure=invalid.getMessage();show();}}
    @Override public void next(){flush();if(!failure.isEmpty()||state==null||state.session()==null)return;try{long round=round();change(value->value.next(round));}catch(JSONException invalid){failure=invalid.getMessage();show();}}
    @Override public void resume(){flush();if(!failure.isEmpty()||state==null||state.session()==null||restrictions())return;try{long round=round();state=learning.change(value->value.finish(round,false,ChildModeStore.today()));launch();}catch(IOException|JSONException unavailable){failure=unavailable.getMessage();show();}}
    @Override public void exit(){if(leaving)return;flush();leaving=true;ChildUsageService.cancel();startActivity(new Intent(this,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();}
    @Override public void parent(){if(state==null||state.session()==null)return;flush();last=0;parent=true;verified=false;failure="";show();}
    @Override public void back(){++authGeneration;parent=verified=verifying=false;failure="";rendered="";last=SystemClock.elapsedRealtime();show();}
    @Override public void verify(String pin){if(verifying||!parent||!active)return;if(!child.configured()){failure="请先返回儿童模式设置家长密码";show();return;}if(!ChildModeCredentials.numeric(pin)){failure="请输入 6 位数字";show();return;}if(child.retryAfter()>SystemClock.elapsedRealtime()){failure="输入次数过多，请稍后重试";show();return;}int version=++authGeneration;verifying=true;failure="";show();worker.execute(()->{boolean valid=false;try{valid=ChildModeCredentials.verify(pin,child.credential());}catch(RuntimeException unavailable){}boolean result=valid;clock.post(()->{if(!active||leaving||version!=authGeneration||!parent)return;verifying=false;if(result){child.verified();verified=true;failure="";}else{child.failed();failure="密码不正确，请重试";}show();});});}
    @Override public void skip(){if(!verified||!parent||state==null||state.session()==null||restrictions())return;try{long round=round();state=learning.change(value->value.finish(round,true,ChildModeStore.today()));launch();}catch(IOException|JSONException unavailable){failure=unavailable.getMessage();show();}}
    private void launch(){if(leaving)return;try{if(ChildBlockActivity.gate(this,pkg)){leaving=true;finish();return;}ResolveInfo target=new HomeAppCatalog(this).find(pkg);if(target==null)throw new IOException("应用暂不可用，请返回桌面");leaving=true;startActivity(new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(pkg,target.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));finish();}catch(IOException|RuntimeException unavailable){leaving=false;failure=unavailable.getMessage();show();}}
    @Override public void handleBack(){if(parent)back();else exit();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        return KeyFeedback.dispatch(getWindow().getDecorView(),event,()->{
            int key=event.getKeyCode();
            if(KeyFeedback.direction(key)&&view!=null){
                if(event.getAction()==KeyEvent.ACTION_DOWN&&view.direction(key))return true;
                if(event.getAction()==KeyEvent.ACTION_UP)return true;
            }
            return super.dispatchKeyEvent(event);
        });
    }
}
