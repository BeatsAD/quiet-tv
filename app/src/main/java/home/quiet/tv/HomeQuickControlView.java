package home.quiet.tv;

import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import android.media.AudioManager;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.util.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/** Runtime controls use the HOME measurements, independently of the smaller editor preview. */
final class HomeQuickControlView extends LinearLayout {
    private static final int PHONE_CARD=0xfff1efeb;
    interface Value<T> {void accept(T value);}
    interface Host {
        QuickControlStore.Snapshot cachedShortcuts();
        void readShortcuts(Value<QuickControlStore.Snapshot> ready,Value<String> failed);
        void quickPanelFocused();
        boolean quickPanelFocusAllowed();
        void quickContentReady(View content);
        SettingsSheetBackdrop quickBackdrop(View panel);
    }
    private final android.app.Activity context;
    private final Host ui;
    private final float unit;
    private final boolean chinese;
    private final DesktopSettingsStore desktop;
    private final PhoneSettingsSession phone;
    private final Value<String> execute;
    private final ScrollView scroll;
    private final SettingsSheetBackdrop backdrop;
    private final QuickControlCards modules;
    private final LinearLayout phoneBody;
    private final Map<String,RangeSlider> rangeViews=new LinkedHashMap<>();
    private final Set<String> forcedRanges=new LinkedHashSet<>();
    private final Map<String,int[]> restoredRanges=new HashMap<>();
    private final Map<String,Integer> savingValues=new HashMap<>();
    private final AudioManager audio;
    private final Map<String,View> targets=new LinkedHashMap<>();
    private List<String> ids=Collections.emptyList();
    private List<String> grid=Collections.emptyList(),ranges=Collections.emptyList();
    private final TextView adjustHint;private int scrollLimit;
    private final android.os.Handler settingsMain=new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean destroyed,loading,rendered,contentReady,networkObserved,resumed;
    private String focus="",restoredFocus="",phoneRender="",qrAddress="";
    private int restoreScroll,memoCount=-1;private String pendingRange="";
    private Bitmap qr;
    private ImageView qrView;
    private final java.util.concurrent.ExecutorService qrWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final ViewTreeObserver.OnPreDrawListener revealOnPreDraw=this::reveal;
    private final Runnable sessionChanged=()->{renderPhone();refresh();};
    private final Runnable configurationChanged=this::configurationChanged;
    private void configurationChanged(){renderPhone();QuickControlStore.Snapshot value=ui.cachedShortcuts();if(value!=null)shortcuts(value);else reload();refresh();}
    private final SharedPreferences.OnSharedPreferenceChangeListener preferencesChanged=(preferences,key)->refresh();
    private final android.content.BroadcastReceiver networkChanged=new android.content.BroadcastReceiver(){@Override public void onReceive(android.content.Context context,android.content.Intent intent){refresh();}};
    private int px(float n){return Math.round(n*unit);}
    private int p(float n){return px(n*.75f);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(context);view.setOrientation(VERTICAL);view.setClipChildren(false);return view;}
    private LinearLayout row(){LinearLayout view=column();view.setOrientation(HORIZONTAL);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private TextView label(String value){TextView view=new TextView(context);view.setText(value);view.setIncludeFontPadding(false);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private Button actionButton(String value,Runnable action){Button b=new KeyFeedback.Button(context);b.setText(value);b.setAllCaps(false);b.setMinimumWidth(0);b.setMinWidth(0);b.setMinimumHeight(0);b.setMinHeight(0);b.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));if(android.os.Build.VERSION.SDK_INT>=21)b.setStateListAnimator(null);b.setOnClickListener(v->action.run());SettingsTouchClick.attach(b);return b;}
    private android.graphics.drawable.Drawable phoneSurface(){android.graphics.drawable.GradientDrawable bg=new SurfaceDrawable(unit);bg.setColor(PHONE_CARD);bg.setCornerRadius(p(12));return bg;}
    private void feedback(String message){OperationFeedback.get(context).show(OperationFeedback.Kind.ERROR,message);}
    HomeQuickControlView(android.app.Activity context,float unit,boolean chinese,Host ui,Value<String> execute){
        super(context);this.context=context;this.unit=unit;this.chinese=chinese;this.ui=ui;this.execute=execute;desktop=new DesktopSettingsStore(context);phone=PhoneSettingsSession.get(context);audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        setAlpha(0f);setOrientation(VERTICAL);setPadding(p(16),p(16),p(16),p(16));setClipChildren(false);setClipToPadding(false);setFocusable(true);setFocusableInTouchMode(true);setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        backdrop=ui.quickBackdrop(this);backdrop.prepare();setBackgroundDrawable(backdrop);
        LinearLayout heading=row();heading.setPadding(p(4),0,p(4),p(4));TextView title=text("快捷控制",24,33.6f);QuickControlPreviewView.weight(title,650);heading.addView(title,new LayoutParams(0,-2,1));
        addView(heading,new LayoutParams(-1,p(34)));
        scroll=new ScrollView(context){@Override protected void onMeasure(int w,int h){int available=Math.max(p(32),scrollLimit);super.onMeasure(w,MeasureSpec.makeMeasureSpec(Math.min(available,MeasureSpec.getSize(h)==0?available:MeasureSpec.getSize(h)),MeasureSpec.AT_MOST));}@Override protected void dispatchDraw(Canvas canvas){int save=canvas.save();canvas.clipRect(getScrollX(),getScrollY(),getScrollX()+getWidth(),getScrollY()+getHeight());super.dispatchDraw(canvas);canvas.restoreToCount(save);}};
        scroll.setVerticalScrollBarEnabled(false);scroll.setFillViewport(false);scroll.setClipChildren(false);scroll.setClipToPadding(false);scroll.setPadding(p(8),p(6),p(8),p(6));scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        modules=new QuickControlCards(context,unit*.75f);scroll.addView(modules,new ScrollView.LayoutParams(-1,-2));LayoutParams sp=new LayoutParams(-1,-2);sp.leftMargin=sp.rightMargin=-p(8);sp.topMargin=0;addView(scroll,sp);
        adjustHint=text("确定保存  返回取消",14,21);adjustHint.setTextColor(UiPalette.SECONDARY);LayoutParams hp=new LayoutParams(-1,p(21));hp.setMargins(p(8),p(4),p(8),0);addView(adjustHint,hp);adjustHint.setVisibility(GONE);
        phoneBody=column();phoneBody.setMinimumHeight(p(152));LayoutParams pp=new LayoutParams(-1,-2);pp.topMargin=p(4);addView(phoneBody,pp);
        getViewTreeObserver().addOnPreDrawListener(revealOnPreDraw);
    }
    private boolean reveal(){
        if(destroyed||!contentReady||!backdrop.ready()||getVisibility()!=VISIBLE)return true;
        getViewTreeObserver().removeOnPreDrawListener(revealOnPreDraw);
        UiMotion.reveal(this,-px(6));
        ui.quickContentReady(this);return true;
    }
    FrameLayout.LayoutParams position(){FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(p(384),-2,Gravity.RIGHT|Gravity.TOP);lp.rightMargin=p(48);lp.topMargin=px(72);lp.bottomMargin=px(12);return lp;}
    @Override protected void onMeasure(int w,int h){
        phoneBody.measure(MeasureSpec.makeMeasureSpec(Math.max(1,MeasureSpec.getSize(w)-p(32)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(p(300),MeasureSpec.AT_MOST));
        int available=MeasureSpec.getSize(h);if(available==0)available=getResources().getDisplayMetrics().heightPixels-px(72)-px(12);
        int previousLimit=scrollLimit;
        scrollLimit=available-getPaddingTop()-getPaddingBottom()-p(34+4)-phoneBody.getMeasuredHeight();
        // The sibling footer changes our limit without changing the ScrollView's parent specs.
        if(scrollLimit!=previousLimit)scroll.forceLayout();
        super.onMeasure(w,h);
    }
    private TextView text(String value,float size,float line){TextView t=label(value);QuickControlPreviewView.controlType(t,size,line,unit,UiPalette.TEXT);return t;}
    private void identify(View view,String id){view.setId(androidx.core.view.ViewCompat.generateViewId());view.setTag(id);targets.put(id,view);view.setFocusable(true);view.setFocusableInTouchMode(true);view.setSoundEffectsEnabled(false);view.setOnFocusChangeListener((v,has)->{if(has){focus=id;ui.quickPanelFocused();View frame=v instanceof RangeSlider?((RangeSlider)v).card:v;Rect r=new Rect();frame.getDrawingRect(r);r.inset(0,-p(4));frame.requestRectangleOnScreen(r,false);}else if(v instanceof RangeSlider)((RangeSlider)v).cancel();});}

    private Button button(String value,String id,Runnable action){Button b=actionButton(value,()->{focus=id;action.run();});identify(b,id);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,p(16));b.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));b.setIncludeFontPadding(true);b.setPadding(p(8),0,p(8),0);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setBackgroundDrawable(new SettingsFocusDrawable(unit*.75f,12,Color.TRANSPARENT));return b;}
    void reload(){reload(null);}
    void reload(String desired){
        if(desired!=null&&!desired.isEmpty())restoredFocus=desired;
        if(loading||destroyed)return;loading=true;QuickControlStore.Snapshot cached=ui.cachedShortcuts();if(cached!=null)post(()->{if(!destroyed)shortcuts(cached);});
        ui.readShortcuts(value->{loading=false;shortcuts(value);},error->{loading=false;rendered=false;cancelAdjustments();modules.clearCards();modules.addView(text(error,16,24));modules.addView(button("重新读取","reload",this::reload),new LayoutParams(-1,p(44)));focus("reload");contentReady=true;});
    }
    private void shortcuts(QuickControlStore.Snapshot value){QuickControlStore.Snapshot latest=ui.cachedShortcuts();if(latest!=null&&latest.revision!=value.revision)value=latest;boolean changed=!rendered||!ids.equals(value.ids);for(String id:forcedRanges)if(!rangeViews.containsKey(id))changed=true;ids=value.ids;if(changed)render();else if(!restoredFocus.isEmpty()){String next=restoredFocus;restoredFocus="";focus(next);}}
    private ImageView icon(String glyph){ImageView v=new ImageView(context);QuickControlIcon drawable=new QuickControlIcon(glyph);drawable.color(UiPalette.TEXT);v.setImageDrawable(drawable);v.setDuplicateParentStateEnabled(true);return v;}
    private void render(){
        cancelAdjustments();String remembered=restoredFocus.isEmpty()?focus:restoredFocus;restoredFocus="";int y=scroll.getScrollY();modules.clearCards();targets.keySet().removeAll(QuickControlStore.ALL);targets.remove(QuickControlStore.SETTINGS);rangeViews.clear();
        grid=QuickControlStore.grid(ids);
        for(int rowIndex=0;rowIndex<(grid.size()+2)/3;rowIndex++){
            LinearLayout row=row();row.setClipChildren(false);row.setClipToPadding(false);LayoutParams rp=new LayoutParams(-1,p(64));if(rowIndex>0)rp.topMargin=p(8);modules.addView(row,rp);
            for(int col=0;col<3;col++){
                int index=rowIndex*3+col;String id=index<grid.size()?grid.get(index):"";LayoutParams lp=new LayoutParams(0,-1,1);if(col>0)lp.leftMargin=p(8);
                if(id.isEmpty()){row.addView(new View(context),lp);continue;}
                LinearLayout card=new LinearLayout(context){
                    @Override public void setPressed(boolean pressed){
                        boolean changed=pressed!=isPressed();
                        super.setPressed(pressed);
                        if(!changed)return;
                        UiMotion.scale(this,pressed&&!SettingsFocusMotion.reduced()?.985f:1,pressed?80:120);
                    }
                };
                card.setOrientation(VERTICAL);card.setGravity(Gravity.CENTER);card.setPadding(p(4),p(5),p(4),p(5));card.setClipChildren(false);card.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);identify(card,id);card.setContentDescription(QuickControlStore.title(id,chinese));card.setClickable(true);
                ImageView icon=icon(QuickControlPreviewView.glyph(id));card.addView(icon,new LayoutParams(p(24),p(24)));TextView name=text(QuickControlStore.shortTitle(id,chinese),16,22);name.setGravity(Gravity.CENTER);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);LayoutParams np=new LayoutParams(-1,-2);np.topMargin=p(6);card.addView(name,np);
                row.addView(card,lp);modules.register(card,card,color->{((QuickControlIcon)icon.getDrawable()).color(color);name.setTextColor(color);});card.setOnClickListener(v->execute.accept(id));SettingsTouchClick.attach(card);
            }
        }
        ranges=QuickControlStore.ranges(ids);for(String id:forcedRanges)if(!ranges.contains(id))ranges.add(id);for(String id:ranges)addRange(id);
        for(Map.Entry<String,int[]> entry:restoredRanges.entrySet()){RangeSlider track=rangeViews.get(entry.getKey());if(track!=null){track.draft.begin(entry.getValue()[0]);track.preview(entry.getValue()[1]);track.setActivated(true);}}restoredRanges.clear();
        refresh();if(!pendingRange.isEmpty()){String next=pendingRange;pendingRange="";beginRange(next);remembered=next;}focus=remembered;focus(remembered);int requested=restoreScroll>0?restoreScroll:y;restoreScroll=0;scroll.post(()->scroll.scrollTo(0,requested));
        rendered=true;contentReady=true;
    }
    private void fillRow(LinearLayout row){while(row.getChildCount()<3){LayoutParams lp=new LayoutParams(0,1,1);lp.leftMargin=p(8);row.addView(new View(context),lp);}}
    private void addRange(String id){
        LinearLayout card=column();card.setClipChildren(false);card.setClipToPadding(false);card.setPadding(p(12),p(7.333f),p(12),p(7.333f));LayoutParams cp=new LayoutParams(-1,p(74.667f));cp.topMargin=p(8);modules.addView(card,cp);
        LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);TextView title=text(QuickControlStore.title(id,chinese),16,22);line.addView(title,new LayoutParams(0,-2,1));TextView level=text("",16,22);line.addView(level);card.addView(line,new LayoutParams(-1,p(20)));
        LinearLayout controls=row();controls.setGravity(Gravity.CENTER_VERTICAL);ImageView low=icon(QuickControlPreviewView.rangeGlyph(id,false)),high=icon(QuickControlPreviewView.rangeGlyph(id,true));controls.addView(low,new LayoutParams(p(24),p(24)));
        RangeSlider track=new RangeSlider(id,card,level);identify(track,id);track.setContentDescription(QuickControlStore.title(id,chinese)+"  左右调整  确定保存  上下保存并切换  返回取消");LayoutParams trackSize=new LayoutParams(0,p(24),1);trackSize.leftMargin=trackSize.rightMargin=p(10);controls.addView(track,trackSize);controls.addView(high,new LayoutParams(p(24),p(24)));LayoutParams rowSize=new LayoutParams(-1,p(24));rowSize.topMargin=p(16);card.addView(controls,rowSize);rangeViews.put(id,track);
        modules.register(track,card,color->{title.setTextColor(color);level.setTextColor(color);((QuickControlIcon)low.getDrawable()).color(color);((QuickControlIcon)high.getDrawable()).color(color);track.ink=color;track.invalidate();});
    }
    void memoCount(int count){memoCount=count;refresh();}
    void refresh(){
        if(destroyed)return;WallpaperCatalog wall=WallpaperStore.get(context).snapshot();
        for(String id:new String[]{"keySounds","mute"}){View target=targets.get(id);if(target!=null){boolean on="keySounds".equals(id)?desktop.keySounds():wall.audible();target.setContentDescription(QuickControlStore.title(id,chinese)+"  "+(on?"已开启":"已关闭"));modules.setOn(target,on);}}
        adjustHint.setVisibility(GONE);for(RangeSlider track:rangeViews.values())track.refresh();
    }
    void beginBrightness(){beginRange("brightness");}
    void beginVolume(){beginRange("volume");}
    void beginRange(String id){if(!QuickControlStore.range(id)||savingValues.containsKey(id))return;RangeSlider track=rangeViews.get(id);if(track==null){forcedRanges.add(id);pendingRange=id;if(!loading)reload();return;}track.begin();focus(id);}
    boolean cancelAdjustments(){boolean cancelled=false;for(RangeSlider track:rangeViews.values())cancelled=track.cancel()||cancelled;return cancelled;}
    private int volumeMaximum(){return audio==null?0:audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);}
    private int volumeValue(){return audio==null?0:audio.getStreamVolume(AudioManager.STREAM_MUSIC);}
    private boolean volumeAvailable(){return audio!=null&&volumeMaximum()>0&&(android.os.Build.VERSION.SDK_INT<21||!Api21.fixed(audio));}
    @android.annotation.TargetApi(21) private static final class Api21 {static boolean fixed(AudioManager audio){return audio.isVolumeFixed();}}
    private boolean writeVolume(int value){try{audio.setStreamVolume(AudioManager.STREAM_MUSIC,value,0);return true;}catch(RuntimeException unavailable){feedback("音量由系统控制，请使用电视音量键");return false;}}
    private final class RangeSlider extends View {
        final String id;final boolean sound;final SettingsRangeDraft draft;final LinearLayout card;final TextView level;private final Paint paint=new Paint(3);
        int ink=UiPalette.TEXT;
        RangeSlider(String id,LinearLayout card,TextView level){super(context);this.id=id;sound="volume".equals(id);this.card=card;this.level=level;draft=new SettingsRangeDraft(minimum(),maximum(),sound?1:5);setBackgroundColor(Color.TRANSPARENT);}
        int current(){return sound?volumeValue():QuickControlStore.rangeValue(id,desktop);}
        int minimum(){return QuickControlStore.rangeMinimum(id);}int maximum(){return sound?volumeMaximum():"dockScale".equals(id)?HomeActivity.maximumQuickDockScale(context):QuickControlStore.rangeMaximum(id);}
        boolean available(){return sound?volumeAvailable():!"dockMagnification".equals(id)||desktop.dockMagnify();}
        boolean begin(){if(!isEnabled()||savingValues.containsKey(id))return false;for(RangeSlider track:rangeViews.values())if(track!=this)track.cancel();draft.maximum(maximum());if(!draft.editing())draft.begin(current());setActivated(true);HomeQuickControlView.this.refresh();return true;}
        boolean cancel(){if(!draft.editing())return false;if(sound&&draft.value()!=draft.original())writeVolume(draft.original());draft.cancel();setActivated(false);if(!sound)HomeActivity.previewQuickAdjustment(id,null);refresh();return true;}
        boolean commit(){
            if(!draft.editing())return true;int candidate=draft.value(),original=draft.original();draft.commit();setActivated(false);if(sound){HomeQuickControlView.this.refresh();return true;}
            if(candidate==original){HomeActivity.previewQuickAdjustment(id,null);HomeQuickControlView.this.refresh();return true;}
            savingValues.put(id,candidate);qrWorker.execute(()->{String failure="";try{String key=QuickControlStore.rangeProperty(id);if("brightness".equals(id)||"homeBlur".equals(id))desktop.saveWallpaperEffect(false,key,candidate,original);else desktop.saveAppearance(key,candidate);}catch(IOException error){failure=error.getMessage();}String result=failure;settingsMain.post(()->{savingValues.remove(id);HomeActivity.previewQuickAdjustment(id,null);if(destroyed)return;if(result.isEmpty()){phone.configurationChanged();OperationFeedback.success(context,"设置已保存");}else feedback(result);HomeQuickControlView.this.refresh();});});return true;
        }
        void preview(int value){if(sound){int next=Math.max(0,Math.min(maximum(),value));if(!writeVolume(next))return;draft.preview(next);}else{draft.preview(value);HomeActivity.previewQuickAdjustment(id,draft.value());}HomeQuickControlView.this.refresh();}
        void refresh(){if(!available()&&draft.editing())cancel();setEnabled(available());draft.maximum(maximum());int value=savingValues.containsKey(id)?savingValues.get(id):draft.editing()?draft.value():current();value=Math.max(minimum(),Math.min(maximum(),value));level.setText(!isEnabled()?(sound?"系统控制":"未开启聚焦放大"):sound?Math.round(100f*value/Math.max(1,maximum()))+"%":value+"%");setContentDescription(QuickControlStore.title(id,chinese)+"  "+level.getText()+"  左右调整  确定保存  上下保存并切换  返回取消");invalidate();}
        @Override protected void onDraw(Canvas c){float left=p(7),right=getWidth()-p(7),mid=getHeight()/2f;int value=savingValues.containsKey(id)?savingValues.get(id):draft.editing()?draft.value():current();float x=left+(right-left)*(Math.max(minimum(),Math.min(maximum(),value))-minimum())/Math.max(1,maximum()-minimum());paint.setColor(QuickControlCards.trackTint(ink));RectF track=new RectF(left,mid-p(1.5f),right,mid+p(1.5f));c.drawRoundRect(track,p(2),p(2),paint);track.right=x;paint.setColor(ink);c.drawRoundRect(track,p(2),p(2),paint);paint.setShadowLayer(p(2),0,p(1),0x33000000);c.drawCircle(x,mid,p(7),paint);paint.clearShadowLayer();paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(p(.5f));paint.setColor(0x14000000);c.drawCircle(x,mid,p(7)-paint.getStrokeWidth()/2,paint);paint.setStyle(Paint.Style.FILL);}
        @Override public boolean onKeyDown(int key,KeyEvent e){if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER){if(e.getRepeatCount()==0){if(draft.editing())commit();else begin();}return true;}if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){if(!draft.editing()&&!begin())return true;int before=draft.value();draft.move(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1,e.getRepeatCount()>0?e.getEventTime()-e.getDownTime():0);int next=draft.value();draft.preview(before);preview(next);return true;}if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){if(!commit())return true;navigate(this,key==KeyEvent.KEYCODE_DPAD_UP?FOCUS_UP:FOCUS_DOWN);return true;}if(draft.editing()&&(key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_ESCAPE)){cancel();return true;}return super.onKeyDown(key,e);}
        @Override public boolean onKeyUp(int key,KeyEvent e){if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)return true;return super.onKeyUp(key,e);}
        @Override public boolean onTouchEvent(MotionEvent e){if(!isEnabled()||savingValues.containsKey(id))return false;if(e.getAction()==MotionEvent.ACTION_DOWN){requestFocusFromTouch();if(!begin())return false;getParent().requestDisallowInterceptTouchEvent(true);}if(e.getAction()==MotionEvent.ACTION_DOWN||e.getAction()==MotionEvent.ACTION_MOVE||e.getAction()==MotionEvent.ACTION_UP){int step=sound?1:5;preview(minimum()+Math.round((maximum()-minimum())*(e.getX()-p(7))/Math.max(1,getWidth()-p(14))/step)*step);if(e.getAction()==MotionEvent.ACTION_UP){commit();performClick();}return true;}if(e.getAction()==MotionEvent.ACTION_CANCEL){cancel();return true;}return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
    private void renderPhone(){
        if(resumed&&isShown()&&phone.state().equals("qr-expired"))phone.start();
        String state=phone.state();String signature=state+phone.address()+phone.failure()+phone.paired();if(signature.equals(phoneRender))return;phoneRender=signature;String previous=focus;
        // Keep a stable panel focus while removing the currently focused connection button.
        if(phoneBody.hasFocus()){int descendants=getDescendantFocusability();setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);requestFocus();setDescendantFocusability(descendants);}
        phoneBody.removeAllViews();qrView=null;targets.keySet().removeAll(Arrays.asList("phone-end","phone-restart"));
        boolean starting=state.equals("starting");
        if(starting||state.equals("waiting")||state.equals("connected")){
            LinearLayout connection=row();connection.setGravity(Gravity.CENTER_VERTICAL);connection.setPadding(p(8),p(8),p(8),p(8));connection.setBackgroundDrawable(phoneSurface());ImageView image=new QrCodeView(context,unit);image.setContentDescription("手机设置连接二维码");
            if(!starting)prepareQr(phone.address(),image);connection.addView(image,new LayoutParams(p(136),p(136)));
            LinearLayout copy=column();TextView title=text(phone.paired()?"手机设置桌面":"手机扫码设置桌面",18,26);title.setTextColor(0xff171717);QuickControlPreviewView.weight(title,600);copy.addView(title,new LayoutParams(-1,-2));
            TextView hint=text(starting?"正在生成二维码…":"扫码即可连接",16,24);hint.setTextColor(0xff484846);copy.addView(hint,new LayoutParams(-1,-2));
            if(state.equals("connected")){
                LinearLayout status=row();ImageView checked=icon("check-circle");((QuickControlIcon)checked.getDrawable()).color(0xff1d7c47);status.addView(checked,new LayoutParams(p(20),p(20)));
                TextView connected=text("手机已连接",16,24);connected.setTextColor(0xff1d7c47);QuickControlPreviewView.weight(connected,600);LayoutParams label=new LayoutParams(-2,-2);label.leftMargin=p(6);status.addView(connected,label);copy.addView(status,new LayoutParams(-1,-2));
            }
            if(phone.paired()){
                Button end=button("解除全部配对","phone-end",phone::revoke);end.setTextColor(0xff343432);end.setGravity(Gravity.CENTER);
                android.graphics.drawable.GradientDrawable normal=new SurfaceDrawable(unit);normal.setColor(0x05000000);normal.setCornerRadius(p(7));normal.setStroke(p(1),0xff92928e);
                android.graphics.drawable.GradientDrawable focused=new SurfaceDrawable(unit);focused.setColor(0xffdeded8);focused.setCornerRadius(p(7));focused.setStroke(p(1.5f),0xff343432);
                android.graphics.drawable.StateListDrawable background=new android.graphics.drawable.StateListDrawable();background.addState(new int[]{android.R.attr.state_focused},focused);background.addState(new int[0],normal);end.setBackgroundDrawable(background);
                LayoutParams action=new LayoutParams(p(112),p(30));action.topMargin=p(4);copy.addView(end,action);
            }
            LayoutParams cp=new LayoutParams(0,-2,1);cp.leftMargin=p(16);connection.addView(copy,cp);phoneBody.addView(connection);
        }else{String message=state.equals("error")?phone.failure():state.equals("ended")?"连接已结束":state.equals("network-changed")?"网络已变化，请重新连接":state.equals("qr-expired")?"二维码已过期":"连接已超时";phoneBody.addView(text(message,18,25.2f));phoneBody.addView(button(state.equals("qr-expired")?"刷新二维码":"重新连接","phone-restart",phone::restart),new LayoutParams(-1,p(44)));}
        requestLayout();if(previous.startsWith("phone-"))focus(previous);
    }
    private void prepareQr(String address,ImageView image){
        qrView=image;
        if(address.equals(qrAddress)){showQr();return;}
        qrAddress=address;if(qr!=null){qr.recycle();qr=null;}
        qrWorker.execute(()->{
            try{
                BitMatrix matrix=new QRCodeWriter().encode(address,BarcodeFormat.QR_CODE,0,0,Collections.singletonMap(EncodeHintType.MARGIN,2));int size=matrix.getWidth()*8;int[] pixels=new int[size*size];for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels[y*size+x]=matrix.get(x/8,y/8)?Color.BLACK:Color.WHITE;
                Bitmap ready=Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);
                context.runOnUiThread(()->{if(destroyed||!address.equals(qrAddress)){ready.recycle();return;}qr=ready;showQr();});
            }catch(WriterException error){context.runOnUiThread(()->{if(!destroyed&&address.equals(qrAddress))feedback("二维码生成失败，请打开局域网地址");});}
        });
    }
    private void showQr(){if(qrView!=null&&qr!=null){qrView.setImageBitmap(qr);((android.graphics.drawable.BitmapDrawable)qrView.getDrawable()).setFilterBitmap(false);}}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(phone!=null){if(visibility==VISIBLE&&resumed){phone.start();renderPhone();}else if(visibility!=VISIBLE)cancelAdjustments();}}
    String focusId(){return focus;}
    boolean firstRowFocused(){View current=findFocus();int index=current==null?-1:grid.indexOf(current.getTag());return index>=0&&index<3;}
    private View next(View current,int direction){
        String id=current.getTag() instanceof String?(String)current.getTag():"";int index=grid.indexOf(id);
        List<View> vertical=new ArrayList<>();for(String key:ranges){View v=targets.get(key);if(v!=null&&v.isEnabled())vertical.add(v);}for(String key:new String[]{"phone-end","phone-restart"}){View v=targets.get(key);if(v!=null&&v.isShown())vertical.add(v);}
        if(index>=0){int row=index/3,col=index%3;if(direction==FOCUS_LEFT||direction==FOCUS_RIGHT){for(int c=col+(direction==FOCUS_LEFT?-1:1);c>=0&&c<3&&row*3+c<grid.size();c+=direction==FOCUS_LEFT?-1:1){View v=targets.get(grid.get(row*3+c));if(v!=null&&v.isEnabled())return v;}}
            else{for(int r=row+(direction==FOCUS_UP?-1:1);r>=0&&r<(grid.size()+2)/3;r+=direction==FOCUS_UP?-1:1){View best=null;int distance=4;for(int c=0;c<3&&r*3+c<grid.size();c++){View v=targets.get(grid.get(r*3+c));if(v!=null&&v.isEnabled()&&Math.abs(col-c)<distance){best=v;distance=Math.abs(col-c);}}if(best!=null)return best;}if(direction==FOCUS_DOWN&&!vertical.isEmpty())return vertical.get(0);}
            return current;
        }
        int i=vertical.indexOf(current);
        if(direction==FOCUS_UP&&i==0){for(int k=grid.size()-1;k>=0;k--){View v=targets.get(grid.get(k));if(v!=null)return v;}}
        int n=i+(direction==FOCUS_UP?-1:direction==FOCUS_DOWN?1:0);return i>=0&&n>=0&&n<vertical.size()?vertical.get(n):current;
    }
    private void navigate(View current,int direction){View target=next(current,direction);if(target!=null&&target!=current)target.requestFocus();}
    @Override public View focusSearch(View focused,int direction){return next(focused,direction);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){int key=event.getKeyCode();if(key>=KeyEvent.KEYCODE_DPAD_UP&&key<=KeyEvent.KEYCODE_DPAD_RIGHT){View current=findFocus();if(current instanceof RangeSlider)return super.dispatchKeyEvent(event);if(current!=null){if(event.getAction()==KeyEvent.ACTION_DOWN)navigate(current,key==19?FOCUS_UP:key==20?FOCUS_DOWN:key==21?FOCUS_LEFT:FOCUS_RIGHT);return true;}}return super.dispatchKeyEvent(event);}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(!focused)cancelAdjustments();refresh();}
    void focus(String id){post(()->{if(destroyed||!ui.quickPanelFocusAllowed())return;View target=targets.get(id);if(target==null||!target.isEnabled()||!target.isShown()){target=null;for(String available:ids){View candidate=targets.get(available);if(candidate!=null&&candidate.isEnabled()&&candidate.isShown()){target=candidate;break;}}}if(target==null)target=targets.get("phone-end");if(target==null)target=targets.get("phone-restart");if(target!=null&&target.isEnabled()&&target.isShown())target.requestFocus();});}
    private android.content.IntentFilter networkFilter(){android.content.IntentFilter filter=new android.content.IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION);filter.addAction("android.media.VOLUME_CHANGED_ACTION");return filter;}
    void resume(){resumed=true;if(getVisibility()==VISIBLE)phone.start();phone.observe(sessionChanged);phone.observeConfiguration(configurationChanged);if(!networkObserved){androidx.core.content.ContextCompat.registerReceiver(context,networkChanged,networkFilter(),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);networkObserved=true;}context.getSharedPreferences("desktop-settings",0).registerOnSharedPreferenceChangeListener(preferencesChanged);configurationChanged.run();post(()->{if(resumed&&!destroyed)renderPhone();});}
    void pause(){resumed=false;if(!context.isChangingConfigurations())cancelAdjustments();if(networkObserved){context.unregisterReceiver(networkChanged);networkObserved=false;}phone.forget(sessionChanged);phone.forgetConfiguration(configurationChanged);context.getSharedPreferences("desktop-settings",0).unregisterOnSharedPreferenceChangeListener(preferencesChanged);}
    void save(Bundle state){state.putString("quick-focus",focus);state.putFloat("quick-scroll",scroll.getScrollY()/unit);state.putStringArrayList("quick-forced-ranges",new ArrayList<>(forcedRanges));for(RangeSlider track:rangeViews.values())if(track.draft.editing()){state.putInt("quick-"+track.id+"-base",track.draft.original());state.putInt("quick-"+track.id+"-value",track.draft.value());}}
    void restore(Bundle state){if(state==null)return;focus=state.getString("quick-focus","");restoredFocus=focus;restoreScroll=Math.round(state.getFloat("quick-scroll",0)*unit);ArrayList<String> forced=state.getStringArrayList("quick-forced-ranges");if(forced!=null)for(String id:forced)if(QuickControlStore.range(id))forcedRanges.add(id);if(state.getBoolean("quick-force-brightness"))forcedRanges.add("brightness");if(state.getBoolean("quick-force-volume"))forcedRanges.add("volume");for(String id:QuickControlStore.RANGES)if(state.containsKey("quick-"+id+"-base"))restoredRanges.put(id,new int[]{state.getInt("quick-"+id+"-base"),state.getInt("quick-"+id+"-value")});}
    void refreshBackdrop(){if(!destroyed)backdrop.refresh();}
    void destroy(){destroyed=true;animate().cancel();getViewTreeObserver().removeOnPreDrawListener(revealOnPreDraw);pause();qrWorker.shutdown();qrView=null;if(qr!=null){qr.recycle();qr=null;}}
}
