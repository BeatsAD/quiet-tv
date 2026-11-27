package home.quiet.tv;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.*;
import android.os.*;
import android.view.accessibility.AccessibilityEvent;
import org.json.JSONObject;

/** Observes package/window identity only; never reads screen contents or accessibility text. */
public final class ChildUsageService extends AccessibilityService {
    private static volatile ChildUsageService instance;
    private static final java.util.concurrent.CopyOnWriteArrayList<Runnable> listeners=new java.util.concurrent.CopyOnWriteArrayList<>();
    static void listen(Runnable listener){listeners.addIfAbsent(listener);}
    static void unlisten(Runnable listener){listeners.remove(listener);}
    private static final Handler notifications=new Handler(Looper.getMainLooper());
    static void changed(){if(Looper.myLooper()!=Looper.getMainLooper()){notifications.post(ChildUsageService::changed);return;}ChildUsageService service=instance;if(service!=null){service.handler.removeCallbacks(service.reconcile);service.handler.post(service.reconcile);}for(Runnable listener:listeners)listener.run();}
    private final Handler handler=new Handler(Looper.getMainLooper());
    private ChildModeStore store;
    private ChildQuizStore learning;
    private String target="",foreground="",blocking="",usageDay="";
    private volatile String observed="";
    private volatile String pendingRemoteQuiz="",rejectedQuizRequest="";
    private long last,pending,lastBlock;
    private boolean registered;
    private final Runnable checkBlock=()->{
        if(blocking.isEmpty())return;
        store.fault("系统未确认到时限制页，请检查儿童模式服务");blocking="";
    };
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(target.isEmpty() || !target.equals(foreground))return;
        if(!interactive()){last=0;flush();return;}
        long now=SystemClock.elapsedRealtime();pending+=Math.max(0,now-last);last=now;
        try{
            ChildModePolicy.Rule r=store.effectiveRule(target);
            if(r.hidden || r.locked){flush();block();return;}
            ChildQuizState quiz=quiz();if(!store.appEnabled(target,quiz.enabled(target)||quiz.session()!=null&&quiz.pending(target))){stopSession();return;}boolean questions=quiz.enabled(target)||quiz.session()!=null;
            if(!r.limited()&&!questions){stopSession();return;}
            if(pending>=r.remaining()){flush();block();return;}
            if((pending>=5000||questions&&pending>=quiz.untilDue(target))&&!flush())return;
            if(questions&&quiz().session()!=null){block(true);return;}
            handler.postDelayed(this,1000);
        }catch(java.io.IOException e){store.fault("学习数据无法读取，请检查儿童模式设置");block(true);}catch(RuntimeException e){store.fault("计时数据无法读取，请检查儿童模式设置");stopSession();}
    }};
    private final Runnable reconcile=()->{
        if(store==null)return;if(!store.enabled()){stopSession();return;}
        String pkg=observed;if(!store.eligible(pkg)||!interactive()||!store.fault().isEmpty())return;
        try{ChildModePolicy.Rule rule=store.effectiveRule(pkg);ChildQuizState state=quiz();if(!store.appEnabled(pkg,state.enabled(pkg)||state.session()!=null&&state.pending(pkg))){stopSession();return;}if(!rule.limited()&&!rule.hidden&&!rule.locked&&!state.enabled(pkg)&&state.session()==null){stopSession();return;}if(!pkg.equals(target)){pause();target=pkg;usageDay=ChildModeStore.today();}foreground=pkg;handler.removeCallbacks(tick);if(rule.hidden||rule.locked||rule.remaining()<=0){block();return;}if(state.session()!=null){block(true);return;}last=SystemClock.elapsedRealtime();handler.postDelayed(tick,1000);}catch(java.io.IOException|RuntimeException e){store.fault("儿童模式状态无法读取，请检查设置");}
    };
    static String currentApp(Context context){ChildUsageService service=instance;return service!=null&&new ChildModeStore(context).eligible(service.observed)?service.observed:"";}
    static String rejectedQuizRequest(){ChildUsageService service=instance;return service==null?"":service.rejectedQuizRequest;}
    static void startWatchingNow(Context context,String request,String expectedPackage)throws java.io.IOException{
        startNow(context,request,expectedPackage,!expectedPackage.isEmpty());
    }
    static void startNow(Context context,String request,String explicitPackage)throws java.io.IOException{
        startNow(context,request,explicitPackage,false);
    }
    private static void startNow(Context context,String request,String explicitPackage,boolean watching)throws java.io.IOException{
        ChildUsageService service=instance;
        ChildModeStore child=new ChildModeStore(context);
        if(!child.enabled())throw new PhoneSettingsServer.Rejected("请先开启儿童模式");
        if(service==null||!ready(context)||!service.interactive())throw new PhoneSettingsServer.Rejected("计时服务尚未就绪，或电视当前未亮屏");
        String pkg=explicitPackage.isEmpty()?currentApp(context):explicitPackage;
        if(watching&&!pkg.equals(service.observed))throw new PhoneSettingsServer.Rejected("观看应用已切换，请核对后重新发起");
        if(pkg.isEmpty()||!child.eligible(pkg))throw new PhoneSettingsServer.Rejected("当前没有可发起答题的观看应用");
        if(!child.appEnabled(pkg,true))throw new PhoneSettingsServer.Rejected("请先为此应用开启儿童模式");
        ChildModePolicy.Rule rule=child.effectiveRule(pkg);
        if(rule.hidden||rule.locked||rule.remaining()<=0)throw new PhoneSettingsServer.Rejected("此应用当前受使用限制，请先处理限制");
        ChildQuizStore learning=ChildQuizStore.get(context);
        learning.change(state->{
            if(watching&&state.session()!=null)throw new PhoneSettingsServer.Rejected("电视已有待答题目");
            try{state.startNow(pkg,request);}catch(java.io.IOException error){throw new PhoneSettingsServer.Rejected(error.getMessage());}
            if(watching)service.pendingRemoteQuiz=request;
        });
        service.handler.post(()->{
            try{
                if(watching){
                    if(request.equals(service.pendingRemoteQuiz))service.block(true,request);
                    return;
                }
                service.pause();service.target=pkg;service.usageDay=ChildModeStore.today();
                if(service.quiz().pending(pkg))service.block(true);
            }catch(java.io.IOException error){service.store.fault("答题请求已保存，显示结果待核对");}
        });
        changed();
    }
    private final BroadcastReceiver screen=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
        if(Intent.ACTION_SCREEN_OFF.equals(i.getAction()))last=0;
        pause();
        if(!Intent.ACTION_SCREEN_OFF.equals(i.getAction()) && !target.isEmpty() && target.equals(foreground) && interactive()){
            last=SystemClock.elapsedRealtime();handler.postDelayed(tick,1000);
        }
    }};
    @Override protected void onServiceConnected(){
        super.onServiceConnected();store=new ChildModeStore(this);instance=this;DiagnosticLog.info("child.timer.service","state=connected");
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);
        if(!registered){if(Build.VERSION.SDK_INT>=33)registerReceiver(screen,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(screen,filter);registered=true;}
        // Clear stale failure only after a fresh explicit service connection.
        store.fault("");
    }
    static Boolean allowed(Context context){
        try{
            String enabled=android.provider.Settings.Secure.getString(context.getContentResolver(),android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if(enabled==null)return android.provider.Settings.Secure.getInt(context.getContentResolver(),
                android.provider.Settings.Secure.ACCESSIBILITY_ENABLED,-1)==0?Boolean.FALSE:null;
            ComponentName own=new ComponentName(context,ChildUsageService.class);
            for(String item:enabled.split(":"))if(own.equals(ComponentName.unflattenFromString(item)))return true;
            return false;
        }catch(SecurityException unavailable){return null;}
    }
    static ChildTimerState status(Context context){return ChildTimerState.read(allowed(context),connected(),new ChildModeStore(context).fault());}
    static boolean connected(){return instance!=null;}
    static boolean ready(Context context){return connected() && new ChildModeStore(context).fault().isEmpty();}
    static boolean watch(String pkg){
        ChildUsageService service=instance;if(service==null || !service.store.enabled())return false;
        service.stopSession();service.target=pkg;service.foreground="";service.usageDay=ChildModeStore.today();return true;
    }
    static void cancel(){ChildUsageService service=instance;if(service!=null){if(Looper.myLooper()==Looper.getMainLooper())service.stopSession();else service.handler.post(service::stopSession);}}
    static void blockVisible(String pkg){ChildUsageService service=instance;if(service!=null && service.blocking.equals(pkg)){service.handler.removeCallbacks(service.checkBlock);service.blocking="";}}
    private boolean interactive(){
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);KeyguardManager keyguard=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        return power!=null && (Build.VERSION.SDK_INT>=20?power.isInteractive():power.isScreenOn()) && (keyguard==null || !UiCompat.keyguardLocked(keyguard));
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(store==null || event==null || event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName()==null)return;
        long age=SystemClock.uptimeMillis()-event.getEventTime();if(age<0 || age>3000)return;
        String pkg=event.getPackageName().toString();
        observed=pkg;if(!store.enabled()){stopSession();return;}
        if(target.isEmpty()||!pkg.equals(target)&&store.eligible(pkg)){pause();handler.removeCallbacks(reconcile);handler.post(reconcile);return;}
        if(pkg.equals(foreground))return;
        pause();foreground=pkg;
        if(!pkg.equals(target) || !interactive())return;
        try{ChildQuizState state=quiz();if(!store.appEnabled(target,state.enabled(target)||state.session()!=null&&state.pending(target))){stopSession();return;}ChildModePolicy.Rule rule=store.effectiveRule(target);if(rule.hidden||rule.locked||rule.remaining()<=0){block();return;}if(quiz().session()!=null){block(true);return;}}
        catch(java.io.IOException e){store.fault("学习数据无法读取");block(true);return;}catch(RuntimeException e){store.fault("计时数据无法读取");return;}
        last=SystemClock.elapsedRealtime();handler.postDelayed(tick,1000);
    }
    private boolean flush(){
        if(pending==0 || target.isEmpty())return true;
        long watched=pending;pending=0;String today=ChildModeStore.today();
        // A rule read may already have persisted midnight's reset. Split pending time independently.
        boolean nextDay=!usageDay.isEmpty()&&today.compareTo(usageDay)>0;usageDay=today;
        try{
            ChildModePolicy.Rule rule=store.effectiveRule(target);long qualifying=Math.min(watched,rule.remaining());long amount=ChildModePolicy.pendingUsage(rule.mode,qualifying,nextDay,ChildModeStore.sinceMidnight());
            if(rule.limited()&&!store.consume(target,amount)){store.fault("使用时长保存失败，请检查存储空间");handler.removeCallbacks(tick);return false;}
            if(store.enabled()&&!rule.hidden&&!rule.locked){ChildQuizState quiz=quiz();if(quiz.enabled(target)&&store.appEnabled(target,true)){boolean mayStart=store.effectiveRule(target).remaining()>0;learning.change(value->value.watch(target,qualifying,today,mayStart));}}
            return true;
        }catch(java.io.IOException|RuntimeException e){store.fault("学习或计时记录保存失败，请检查存储空间");handler.removeCallbacks(tick);return false;}
    }
    private void pause(){
        handler.removeCallbacks(tick);
        if(!target.isEmpty() && target.equals(foreground) && last>0 && interactive())pending+=Math.max(0,SystemClock.elapsedRealtime()-last);
        last=0;if(store!=null)flush();
    }
    private void stopSession(){pause();target="";foreground="";pending=0;blocking="";handler.removeCallbacks(checkBlock);}
    private ChildQuizState quiz()throws java.io.IOException{if(learning==null)learning=ChildQuizStore.get(this);return learning.read();}
    private void block(){block(false);}
    private void block(boolean quiz){block(quiz,"");}
    private void block(boolean quiz,String expectedRequest){
        try{
            JSONObject session=quiz?quiz().session():null;
            String request=session==null?"":session.optString("requestId");
            if(!expectedRequest.isEmpty()&&!expectedRequest.equals(request))return;
            handler.removeCallbacks(tick);
            String pkg=session==null?target:session.optString("package",target);
            boolean remote=!request.isEmpty()&&request.equals(pendingRemoteQuiz);
            if(remote && (!pkg.equals(observed)||!interactive())){
                learning.change(state->state.withdrawUnshown(request));
                rejectedQuizRequest=request;
                pendingRemoteQuiz="";
                changed();
                return;
            }
            long now=SystemClock.elapsedRealtime();
            if(!remote&&now-lastBlock<1500)return;
            lastBlock=now;
            if(remote&&!pkg.equals(target)){pause();target=pkg;usageDay=ChildModeStore.today();}
            if(!pkg.equals(blocking))DiagnosticLog.info("child.block.request","app="+DiagnosticLog.anonymous("app",pkg)+" kind="+(quiz?"quiz":"limit"));
            blocking=pkg;
            startActivity((quiz?ChildQuizActivity.intent(this,pkg):ChildBlockActivity.intent(this,pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
            if(remote)pendingRemoteQuiz="";
            DiagnosticLog.info("child.block.dispatched","kind="+(quiz?"quiz":"limit")+" visualVerified=false");
            handler.removeCallbacks(checkBlock);handler.postDelayed(checkBlock,2000);
        }catch(java.io.IOException e){store.fault("本轮题目无法读取");blocking="";}
        catch(SecurityException|android.content.ActivityNotFoundException e){store.fault("系统不允许显示到时限制页");blocking="";}
    }
    @Override public void onInterrupt(){DiagnosticLog.info("child.timer.service","state=interrupted");pause();foreground="";if(store!=null)store.fault("计时服务已中断，请重新启用");}
    @Override public void onDestroy(){DiagnosticLog.info("child.timer.service","state=destroyed");stopSession();handler.removeCallbacksAndMessages(null);if(registered)unregisterReceiver(screen);if(instance==this)instance=null;changed();super.onDestroy();}
    @Override public boolean onUnbind(Intent intent){DiagnosticLog.info("child.timer.service","state=unbound");stopSession();if(instance==this)instance=null;changed();return super.onUnbind(intent);}
}
