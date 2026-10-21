package home.quiet.tv;

import android.content.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Two native screens. UI choices are snapshots; the worker rechecks every write. */
public final class SetupActivity extends ManagementUi {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Map<String,Boolean> choices=new LinkedHashMap<>();
    private final ArrayList<AppRepository.App> apps=new ArrayList<>();
    private AdbController adb;
    private AdbConnectionPanel connection;
    private AppRepository repository;
    private AppOperations operations;
    private HomeController home;
    private SetupStore store;
    private SetupOperations.Ticket ticket;
    private HomeController.State homeState;
    private AppRepository.App original;
    private String originalChoice="KEEP",notice="",progress="正在检查设备能力…";
    private boolean originalTouched,busy,active,recovery,roleCancelled,roleResultExpected,recordAvailable=true;
    private int screen;
    private LinearLayout page,steps;
    private SetupFocus focusOutline;
    private TextView originalDetail;
    private int renderedScreen=-1,renderGeneration;
    private boolean renderedRecovery;
    private String lastHome="",lastCard="",lastBottom="",lastMain="";
    private final ArrayList<View> mainControls=new ArrayList<>(),homeControls=new ArrayList<>(),cardControls=new ArrayList<>(),bottomControls=new ArrayList<>(),footerControls=new ArrayList<>();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);home=new HomeController(this);store=new SetupStore(this);repository=new AppRepository(this);operations=new AppOperations(this);
        adb=new AdbController(this,value->runOnUiThread(()->{if(active && !isDestroyed()){progress=value;if(busy)render();}}));connection=new AdbConnectionPanel(this,adb,worker);
        recovery=getIntent().getBooleanExtra("restore",false);
        setupSurface();
        try{JSONObject record=store.get(),saved=record.optJSONObject("choices");if(saved!=null)for(Iterator<String> it=saved.keys();it.hasNext();){String pkg=it.next();choices.put(pkg,saved.getBoolean(pkg));}
            originalTouched=record.optBoolean("originalTouched");originalChoice=record.optString("originalChoice","KEEP");
            JSONObject run=record.optJSONObject("run");if(run!=null && !"complete".equals(run.optString("state")))notice="上次设置未完成；已重新查询状态，不会自动重放操作。";
            JSONObject switching=record.optJSONObject("homeSwitch");
            if(switching!=null && !"complete".equals(switching.optString("state")) && !"rolled-back".equals(switching.optString("state")))notice="上次默认桌面切换未确认；请刷新实际状态，不会自动重试或禁用应用。";
        }catch(Exception e){recordAvailable=false;notice="设置记录无法读取；请先退出并检查记录，未执行任何操作。";}
    }
    @Override protected void onResume(){super.onResume();active=true;refresh();}
    @Override protected void onStop(){active=false;cancel();super.onStop();}
    @Override protected void onDestroy(){cancel();adb.close();worker.shutdownNow();super.onDestroy();}
    public void handleBack(){if(!busy && !recovery && screen==1){screen=0;render();}else exit(false);}
    private void cancel(){if(ticket!=null)ticket.cancel();connection.close();adb.disconnect();busy=false;}
    private boolean valid(SetupOperations.Ticket task){return active && task==ticket && task.active() && !isFinishing() && !isDestroyed();}
    private void ui(SetupOperations.Ticket task,Runnable action){runOnUiThread(()->{if(valid(task))action.run();});}
    private SetupOperations.Ticket begin(String text){if(ticket!=null)ticket.cancel();ticket=new SetupOperations.Ticket();busy=true;progress=text;render();return ticket;}
    private void refresh(){
        SetupOperations.Ticket task=begin("正在检查设备能力与应用状态…");
        worker.execute(()->{try{
            task.check();HomeController.State state=home.inspect(adb);List<AppRepository.App> loaded=repository.load(adb);task.check();
            ui(task,()->{homeState=state;apps.clear();apps.addAll(loaded);original=null;
                for(AppRepository.App app:apps)if(!state.original.isEmpty() && state.original.startsWith(app.pkg+"/"))original=app;
                screen=state.isOwn(this) && !roleCancelled?1:0;busy=false;
                for(AppRepository.App app:apps)if(canRecommend(app) && !choices.containsKey(app.pkg))choices.put(app.pkg,true);
                if(!originalTouched)originalChoice=canOriginal("DISABLE")?"DISABLE":"KEEP";
                if(!"KEEP".equals(originalChoice) && !canOriginal(originalChoice))originalChoice="KEEP";
                render();
            });
        }catch(Exception e){ui(task,()->{busy=false;homeState=null;screen=0;notice="状态未确认："+e.getMessage();render();});}});
    }
    private boolean canRecommend(AppRepository.App app){return (!adb.connected() || adb.capabilities.supports(AppOperations.Action.DISABLE)) && recordAvailable && app.installed && app.enabled && app.metadataKnown && home.presets.recommended(app.pkg) && operations.protection(app).isEmpty();}
    private boolean canOriginal(String action){
        if(!recordAvailable || homeState==null || !homeState.isOwn(this) || !homeState.ownUsable || original==null || !original.metadataKnown || !original.installed || !original.enabled)return false;
        if(adb.connected() && (!adb.capabilities.supports("DISABLE".equals(action)?AppOperations.Action.DISABLE:AppOperations.Action.UNINSTALL) || ("DISABLE".equals(action) && original.enabledSetting==0 && !adb.capabilities.defaultState)))return false;
        return home.presets.homeAllows("DISABLE".equals(action)?"disableVerified":"uninstallVerified") && (!"UNINSTALL".equals(action) || home.presets.homeAllows("restoreInstallVerified"));
    }
    private String originalReason(String action){
        if("KEEP".equals(action))return original==null?"保持原桌面当前状态":!original.installed?"保持原桌面当前未安装状态":!original.enabled?"保持原桌面当前已禁用状态":"保持原桌面当前状态";
        if(!recordAvailable)return "设置记录无法读取，暂不能执行";
        if(homeState==null || !homeState.isOwn(this))return "请先将 Quiet TV 设为实际默认桌面";
        if(!homeState.ownUsable)return "Quiet TV 桌面组件不可用";
        if(original==null || !original.metadataKnown)return "原桌面身份未确认";
        if("UNINSTALL".equals(action) && (!home.presets.homeAllows("uninstallVerified") || !home.presets.homeAllows("restoreInstallVerified")))return "当前设备尚未开放原桌面卸载";
        if(!original.installed)return "原桌面未安装，保持当前状态";
        if(!original.enabled)return "DISABLE".equals(action)?"原桌面已禁用，无需重复禁用":"原桌面已禁用，当前流程不执行卸载";
        if(!home.presets.homeAllows("disableVerified") && "DISABLE".equals(action))return "当前设备尚未开放原桌面禁用";
        return "UNINSTALL".equals(action)?"卸载将删除当前用户数据":"禁用保留安装和数据";
    }
    private String recommendReason(AppRepository.App app){
        if(!recordAvailable)return "设置记录无法读取，暂不能执行";
        if(!app.installed || !app.enabled)return app.state();
        if(!app.metadataKnown)return "应用信息未确认";
        String protection=operations.protection(app);return protection.isEmpty()?"当前设备未开放此操作":protection;
    }
    private void focused(View view){
        String tag=String.valueOf(view.getTag());
        if(tag.startsWith("home-")){lastHome=tag;if(originalDetail!=null)originalDetail.setText(originalReason(tag.substring(5)));}
        else if(tag.startsWith("app-"))lastCard=tag;
        else if(bottomControls.contains(view))lastBottom=tag;
        else if(mainControls.contains(view))lastMain=tag;
    }
    private void collectControls(View view){
        if(view.isFocusable() && view.isEnabled() && view.getTag() instanceof String){String tag=(String)view.getTag();
            if(tag.startsWith("home-"))homeControls.add(view);
            else if(tag.startsWith("app-"))cardControls.add(view);
            else if("inventory".equals(tag)||"skip".equals(tag)||(!busy&&!recovery&&screen==1&&"primary".equals(tag)))bottomControls.add(view);
            else if("back".equals(tag)||"temporary".equals(tag)||(!recovery&&"exit".equals(tag)))footerControls.add(view);
            else if(view instanceof Button)mainControls.add(view);
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)collectControls(group.getChildAt(i));}
    }
    private View remembered(ArrayList<View> group,String tag,int fallback){
        for(View view:group)if(tag.equals(view.getTag()))return view;
        return group.isEmpty()?null:group.get(Math.max(0,Math.min(group.size()-1,fallback)));
    }
    private View initialFocus(){
        if(!busy && !recovery && screen==1)return remembered(homeControls,"home-"+originalChoice,0);
        View primary=root.findViewWithTag("primary"),refresh=root.findViewWithTag("refresh");
        if(mainControls.contains(primary))return primary;if(mainControls.contains(refresh))return refresh;return remembered(mainControls,"",0);
    }
    private View destination(View source,int key){
        int index=homeControls.indexOf(source);boolean left=key==KeyEvent.KEYCODE_DPAD_LEFT,right=key==KeyEvent.KEYCODE_DPAD_RIGHT,up=key==KeyEvent.KEYCODE_DPAD_UP;
        if(index>=0){if(left||right)return remembered(homeControls,"",index+(left?-1:1));if(up)return source;return cardControls.isEmpty()?remembered(bottomControls,lastBottom,2):remembered(cardControls,lastCard,index==0?0:1);}
        index=cardControls.indexOf(source);
        if(index>=0){if(left||right){int next=index+(left?-1:1);return next>=0 && next<cardControls.size() && next/2==index/2?cardControls.get(next):source;}
            if(up)return index>=2?cardControls.get(index-2):remembered(homeControls,lastHome,0);
            return index+2<cardControls.size()?cardControls.get(index+2):remembered(bottomControls,"",index%2==0?0:2);}
        index=bottomControls.indexOf(source);
        if(index>=0){if(left||right)return remembered(bottomControls,"",index+(left?-1:1));if(up)return cardControls.isEmpty()?remembered(homeControls,lastHome,0):remembered(cardControls,lastCard,cardControls.size()-1);return remembered(footerControls,"",0);}
        index=mainControls.indexOf(source);
        if(index>=0){if(left||right)return source;if(up)return remembered(mainControls,"",index-1);return index+1<mainControls.size()?mainControls.get(index+1):remembered(footerControls,"",0);}
        index=footerControls.indexOf(source);
        if(index>=0){if(left||right)return remembered(footerControls,"",index+(left?-1:1));if(up)return !bottomControls.isEmpty()?remembered(bottomControls,lastBottom,2):remembered(mainControls,lastMain,0);return source;}
        return initialFocus();
    }
    // Public Activity override; AndroidX core marks its inherited bridge library-only.
    @android.annotation.SuppressLint("RestrictedApi")
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();
        if(key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT || key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN){
            if(event.getAction()==KeyEvent.ACTION_DOWN){View source=getCurrentFocus(),next=source==null?initialFocus():destination(source,key);if(next!=null)next.requestFocus();}return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override Button button(String text,Runnable action){Button b=super.button(text,action);b.setFocusableInTouchMode(true);b.setBackground(surface(0x10ffffff,0x06ffffff,0x22ffffff,24));return b;}
    @Override EditText input(String text,String hint){EditText edit=super.input(text,hint);edit.setBackground(surface(0x10ffffff,0x06ffffff,0x22ffffff,10));return edit;}
    private void persist(){try{JSONObject saved=new JSONObject();for(Map.Entry<String,Boolean> value:choices.entrySet())saved.put(value.getKey(),value.getValue());store.put("choices",saved);store.put("originalChoice",originalChoice);store.put("originalTouched",originalTouched);}catch(Exception e){notice="选择未保存："+e.getMessage();}}
    private Button action(LinearLayout parent,String text,String tag,Runnable run,boolean enabled){Button button=button(text,run);button.setTag(tag);button.setEnabled(enabled);button.setAlpha(enabled?1f:.42f);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(40));p.bottomMargin=px(10);parent.addView(button,p);return button;}
    // The setup has its own composition; the existing application manager keeps its surface unchanged.
    private void setupSurface(){
        stage.removeAllViews();stage.setBackgroundColor(0xff0d1014);
        WallpaperStore wallpaperStore=WallpaperStore.get(this);
        WallpaperCatalog wallpaperState=wallpaperStore.snapshot();
        WallpaperCatalog.Entry wallpaperEntry=wallpaperState.find(wallpaperState.current);
        if(wallpaperEntry!=null && !WallpaperCatalog.DEFAULT.equals(wallpaperEntry.id))try{
            Bitmap bitmap=WallpaperImages.decode(this,wallpaperStore,wallpaperEntry,480,270);
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageBitmap(bitmap);stage.addView(image,new FrameLayout.LayoutParams(-1,-1));
        }catch(java.io.IOException ignored){}
        View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xed080d15,0xe80a0e16,0xdd0e121a}));stage.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        root=column();root.setPadding(px(51),px(29),px(51),px(19));stage.addView(root,new FrameLayout.LayoutParams(-1,-1));
        header=row();header.addView(glyph(R.drawable.ic_manage_apps,17));TextView brand=label("Quiet TV",14);brand.setPadding(px(9),0,0,0);header.addView(brand);root.addView(header,new LinearLayout.LayoutParams(-1,px(27)));
        if(focusOutline==null)focusOutline=new SetupFocus(stage,unit,this::focused);
    }
    private ImageView glyph(int resource,int size){ImageView image=new ImageView(this);image.setImageResource(resource);image.setColorFilter(0xffc8cfd8);image.setLayoutParams(new LinearLayout.LayoutParams(px(size),px(size)));return image;}
    Button setupButton(String text,String tag,Runnable run,boolean primary){
        Button b=button(text,run);b.setTag(tag);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(12));
        if(primary){b.setBackground(surface(0xffe8ebee,0xffe0e4e9,0xfff4f5f7,24));b.setTextColor(0xff1e242c);}
        return b;
    }
    Sheet setupSheet(String title){
        Sheet sheet=new Sheet(title);sheet.box.setBackground(surface(0xff20252d,0xff171e27,0x30ffffff,17));sheet.heading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(20));
        FrameLayout frame=new FrameLayout(this);((android.view.ViewGroup)sheet.box.getParent()).removeView(sheet.box);frame.addView(sheet.box,new FrameLayout.LayoutParams(-1,-2));Button close=textButton("×","close-dialog",sheet.dialog::dismiss);close.setContentDescription("取消");FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(px(28),px(28),Gravity.TOP|Gravity.END);cp.setMargins(0,px(8),px(8),0);frame.addView(close,cp);sheet.dialog.setContentView(frame);new SetupFocus(frame,unit,null);return sheet;
    }
    @Override void message(String text){Sheet sheet=setupSheet("提示");sheet.text(text);Button close=sheet.action("确定",sheet.dialog::dismiss);sheet.show(close);}
    private void buttonIcon(Button button,int resource){Drawable icon=UiCompat.drawable(this,resource);icon.setColorFilter(0xff26303b,android.graphics.PorterDuff.Mode.SRC_IN);icon.setBounds(0,0,px(14),px(14));button.setCompoundDrawables(icon,null,null,null);button.setCompoundDrawablePadding(px(8));}
    private Button textButton(String text,String tag,Runnable run){Button b=setupButton(text,tag,run,false);b.setBackground(new ColorDrawable(Color.TRANSPARENT));b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(10));b.setTextColor(0xffa4acb7);b.setPadding(px(6),0,px(6),0);return b;}
    private void enable(View view,boolean enabled){view.setEnabled(enabled);view.setAlpha(enabled?1f:.4f);}
    private void render(){
        if(root==null)return;Object focus=renderedScreen==screen && renderedRecovery==recovery && getCurrentFocus()!=null?getCurrentFocus().getTag():null;
        renderedScreen=screen;renderedRecovery=recovery;int generation=++renderGeneration;
        mainControls.clear();homeControls.clear();cardControls.clear();bottomControls.clear();footerControls.clear();originalDetail=null;
        if(root.getChildCount()>1)root.removeViews(1,root.getChildCount()-1);
        page=column();page.setPadding(px(11),0,px(11),0);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,0,1);bp.topMargin=px(30);root.addView(page,bp);
        if(!notice.isEmpty()){TextView note=muted(notice,11);note.setMaxLines(2);page.addView(note,new LinearLayout.LayoutParams(-1,-2));gap(page,10);}
        if(busy){page.addView(center(progress,18),new LinearLayout.LayoutParams(-1,0,1));action(page,"停止后续操作并退出设置","stop",()->exit(false),true);}
        else if(recovery)renderRecovery(page);else if(screen==0)renderDefault(page);else renderApps(page);
        setupFooter();
        SetupFocus.passiveContainers(root);collectControls(root);
        root.post(()->{if(generation!=renderGeneration || isFinishing() || isDestroyed())return;View restored=focus==null?null:root.findViewWithTag(focus);if(restored==null || !restored.isEnabled() || !restored.isFocusable())restored=initialFocus();if(restored==null)restored=remembered(footerControls,"",0);if(restored!=null)restored.requestFocus();});
    }
    private void setupFooter(){
        FrameLayout foot=new FrameLayout(this);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,px(32));fp.topMargin=px(12);root.addView(foot,fp);
        Button back=textButton("‹  返回","back",this::handleBack);foot.addView(back,new FrameLayout.LayoutParams(px(65),-1,Gravity.START));
        if(!recovery){steps=row();steps.setGravity(Gravity.CENTER);steps.setTag("steps");
            for(int i=0;i<2;i++){TextView step=label((i==0?(screen==1?"✓":"❶"):"❷")+"  "+(i==0?"默认桌面":"预装应用"),9);step.setTextColor(i==screen?TEXT:0xff76808d);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-1);p.setMargins(px(8),0,px(8),0);steps.addView(step,p);}foot.addView(steps,new FrameLayout.LayoutParams(-2,-1,Gravity.CENTER));
        }
        if(!busy && screen==0 && !recovery){LinearLayout exits=row();exits.addView(textButton("暂不设置，临时使用","temporary",()->exit(true)),new LinearLayout.LayoutParams(-2,-1));exits.addView(textButton("退出设置","exit",()->exit(false)),new LinearLayout.LayoutParams(-2,-1));foot.addView(exits,new FrameLayout.LayoutParams(-2,-1,Gravity.END));}
    }
    private void renderDefault(LinearLayout body){
        boolean own=homeState!=null && homeState.isOwn(this),usable=recordAvailable && homeState!=null && homeState.ownUsable;
        LinearLayout split=row();split.setGravity(Gravity.CENTER_VERTICAL);body.addView(split,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout copy=column();copy.setGravity(Gravity.CENTER_VERTICAL);split.addView(copy,new LinearLayout.LayoutParams(0,-1,47));
        copy.addView(muted("01 / 默认桌面",10));gap(copy,20);TextView title=label(own?"默认桌面已设置":"设为默认桌面",38);title.setTag("setup-title");copy.addView(title,new LinearLayout.LayoutParams(-1,px(58)));gap(copy,18);copy.addView(muted("按 HOME 键返回 Quiet TV。",13));gap(copy,30);
        Button primary=setupButton(own?"管理预装应用":"设为默认桌面","primary",()->{if(own){screen=1;render();}else if(homeState!=null && homeState.roleAvailable)requestRole();else withConnection(this::setDefault);},true);enable(primary,usable);buttonIcon(primary,R.drawable.ic_setup_home);copy.addView(primary,new LinearLayout.LayoutParams(-2,px(40)));
        if(!own && homeState!=null && homeState.roleAvailable){gap(copy,10);copy.addView(textButton("通过 ADB 设置","adb",()->withConnection(this::setDefault)),new LinearLayout.LayoutParams(-2,px(30)));}
        if(!notice.isEmpty() || !usable){gap(copy,8);copy.addView(textButton("重新检测","refresh",()->{roleCancelled=false;refresh();}),new LinearLayout.LayoutParams(-2,px(30)));}
        View spacing=new View(this);split.addView(spacing,new LinearLayout.LayoutParams(0,1,5));
        LinearLayout preview=column();preview.setGravity(Gravity.CENTER_VERTICAL);split.addView(preview,new LinearLayout.LayoutParams(0,-1,48));
        preview.addView(new HomePreview(),new LinearLayout.LayoutParams(-1,px(226)));gap(preview,18);
        LinearLayout status=row();status.setPadding(px(14),px(13),px(14),px(13));status.setBackground(surface(0x10ffffff,0x05ffffff,0x22ffffff,11));status.addView(glyph(R.drawable.ic_manage_shield,17));
        TextView caption=muted(own?"Quiet TV 已是默认桌面":homeState==null?"默认桌面状态未确认":homeState.ownUsable?"原桌面保持当前状态":"Quiet TV 桌面组件不可用",11);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=px(12);status.addView(caption,cp);preview.addView(status,new LinearLayout.LayoutParams(-1,px(58)));
    }
    private boolean hasRecommendations(){for(AppRepository.App app:apps)if(home.presets.recommended(app.pkg))return true;return false;}
    private void renderApps(LinearLayout body){
        LinearLayout heading=row();LinearLayout titles=column();titles.addView(muted("02 / 预装应用",10));gap(titles,9);TextView title=label("管理预装应用",27);title.setTag("setup-title");titles.addView(title);heading.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        TextView count=muted((hasRecommendations()?"推荐预设":"暂无可用推荐")+" · 已选 "+selected().size()+" 项",10);count.setTag("selected-count");heading.addView(count);body.addView(heading,new LinearLayout.LayoutParams(-1,px(50)));gap(body,15);
        LinearLayout factory=row();factory.setTag("factory-choice");factory.setPadding(px(16),px(9),px(12),px(9));factory.setBackground(surface(0x14ffffff,0x08ffffff,0x26ffffff,10));factory.addView(glyph(R.drawable.ic_setup_home,20));
        LinearLayout info=column();info.addView(label("原桌面",12));gap(info,5);String hint=original==null?"身份未确认，保持当前状态":!original.installed?"已卸载":!original.enabled?"已禁用，保持当前状态":"UNINSTALL".equals(originalChoice)?"卸载将删除当前用户数据":"DISABLE".equals(originalChoice)?"禁用保留安装和数据":"保持当前状态";TextView detail=muted(hint,9);originalDetail=detail;detail.setTag("original-reason");detail.setSingleLine(true);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams infop=new LinearLayout.LayoutParams(0,-2,1);infop.leftMargin=px(12);factory.addView(info,infop);info.addView(detail);
        LinearLayout options=row();options.setTag("factory-options");options.setPadding(px(3),px(3),px(3),px(3));options.setBackground(surface(0x18000000,0x18000000,0x16ffffff,24));
        for(String value:new String[]{"KEEP","DISABLE","UNINSTALL"}){String text="KEEP".equals(value)?"保留":"DISABLE".equals(value)?"禁用":"卸载";Button option=setupButton(text,"home-"+value,()->{if(!"KEEP".equals(value) && !canOriginal(value)){feedback(originalReason(value));return;}originalTouched=true;originalChoice=value;persist();render();},value.equals(originalChoice));option.setSelected(value.equals(originalChoice));option.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(10));option.setPadding(px(12),0,px(12),0);option.setAlpha("KEEP".equals(value)||canOriginal(value)?1f:.4f);option.setContentDescription(text+" · "+originalReason(value));options.addView(option,new LinearLayout.LayoutParams(px(55),px(31)));}factory.addView(options);body.addView(factory,new LinearLayout.LayoutParams(-1,px(62)));gap(body,12);
        ScrollView scroll=new ScrollView(this);scroll.setClipToPadding(false);scroll.setPadding(px(3),px(3),px(3),px(3));LinearLayout grid=column();grid.setTag("preset-grid");scroll.addView(grid);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        ArrayList<AppRepository.App> shown=new ArrayList<>();for(AppRepository.App app:apps)if(home.presets.recommended(app.pkg))shown.add(app);
        if(shown.isEmpty()){LinearLayout empty=column();empty.setGravity(Gravity.CENTER);empty.addView(glyph(R.drawable.ic_manage_shield,28));gap(empty,14);empty.addView(muted("当前设备暂无已核实的推荐预设",13));gap(empty,9);empty.addView(muted("已识别的预装应用可在完整清单中查看。",10));grid.addView(empty,new LinearLayout.LayoutParams(-1,px(145)));}
        for(int i=0;i<shown.size();i+=2){LinearLayout pair=row();for(int j=i;j<Math.min(i+2,shown.size());j++){AppRepository.App app=shown.get(j);boolean allowed=canRecommend(app),checked=allowed && Boolean.TRUE.equals(choices.get(app.pkg));LinearLayout card=row();card.setTag("app-"+app.pkg);card.setPadding(px(14),px(6),px(14),px(6));focusable(card,10);card.setFocusableInTouchMode(true);StateListDrawable cardBackground=new StateListDrawable();cardBackground.addState(new int[]{android.R.attr.state_selected},surface(0x14ffffff,0x10ffffff,0x1effffff,10));cardBackground.addState(new int[]{},surface(0x10ffffff,0x06ffffff,0x22ffffff,10));card.setBackground(cardBackground);card.setAlpha(allowed?1f:.55f);card.setSelected(checked);card.setContentDescription(app.label+(allowed?(checked?" 已选择":" 未选择"):" "+app.state()));card.addView(icon(app.icon,30));LinearLayout text=column();TextView name=label(app.label,12);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);text.addView(name);if(!allowed){TextView state=muted(app.state(),9);text.addView(state);}LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=px(12);card.addView(text,tp);TextView check=center(checked?"✓":"",11);check.setTag("check-"+app.pkg);check.setTextColor(0xff26303b);check.setBackground(surface(checked?0xffe1e6ed:0x08ffffff,checked?0xffe1e6ed:0x08ffffff,checked?0xffe1e6ed:0x60ffffff,4));card.addView(check,new LinearLayout.LayoutParams(px(16),px(16)));card.setOnClickListener(v->{if(canRecommend(app)){choices.put(app.pkg,!Boolean.TRUE.equals(choices.get(app.pkg)));persist();render();}else feedback(app.label+" · "+recommendReason(app));});LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,px(51),1);if(j>i)cp.leftMargin=px(12);pair.addView(card,cp);}if(i+1>=shown.size()){View blank=new View(this);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,1,1);p.leftMargin=px(12);pair.addView(blank,p);}LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=px(8);grid.addView(pair,rp);}
        LinearLayout bottom=row();bottom.setPadding(0,px(10),0,0);LinearLayout help=column();help.addView(muted("禁用可恢复，卸载不保留数据。",9));help.addView(textButton("查看全部预装应用","inventory",this::showInventory),new LinearLayout.LayoutParams(-2,px(25)));bottom.addView(help,new LinearLayout.LayoutParams(0,-2,1));
        marginAdd(bottom,textButton("不处理，进入桌面","skip",()->finishSetup("skipped","已进入桌面")),-2,38,10);
        int selected=selected().size();String text=!"KEEP".equals(originalChoice)?("UNINSTALL".equals(originalChoice)?"卸载原桌面并完成":"禁用原桌面并完成"):selected>0?"禁用 "+selected+" 项并完成":"完成设置";Button apply=setupButton(text,"primary",this::confirm,true);buttonIcon(apply,R.drawable.ic_setup_arrow);marginAdd(bottom,apply,-2,38,14);body.addView(bottom,new LinearLayout.LayoutParams(-1,px(58)));
    }
    private void showInventory(){Sheet sheet=setupSheet("已识别的预装应用");for(AppRepository.App app:apps)if(app.system || home.presets.app(app.pkg)!=null){LinearLayout line=column();line.setPadding(px(8),px(7),px(8),px(7));focusable(line,8);line.setFocusableInTouchMode(true);line.setBackground(surface(0x10ffffff,0x06ffffff,0x22ffffff,8));line.addView(label(app.label,12));line.addView(muted(app.state()+" · "+app.pkg,9));sheet.body.addView(line,new LinearLayout.LayoutParams(-1,-2));}Button close=sheet.action("关闭",sheet.dialog::dismiss);sheet.show(close);}
    private final class HomePreview extends ImageView {
        HomePreview(){super(SetupActivity.this);setTag("home-preview");setBackground(surface(0xff202328,0xff111317,0x30ffffff,13));UiCompat.clipOutline(this);
            setContentDescription("Quiet TV 桌面示意图");
            setScaleType(ImageView.ScaleType.FIT_CENTER);
            setImageResource(R.drawable.setup_home_preview);
        }
    }
    private ArrayList<AppRepository.App> selected(){ArrayList<AppRepository.App> result=new ArrayList<>();for(AppRepository.App app:apps)if(Boolean.TRUE.equals(choices.get(app.pkg)) && canRecommend(app))result.add(app);return result;}
    private void requestRole(){if(busy)return;try{home.remember(homeState);roleCancelled=false;Intent request=home.request();roleResultExpected="android.app.role.action.REQUEST_ROLE".equals(request.getAction());startActivityForResult(request,40);}catch(Exception e){notice="系统入口不可用："+e.getMessage();render();}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==40){roleCancelled=roleResultExpected && result!=RESULT_OK;if(!roleCancelled)notice="";if(roleCancelled)notice="已取消默认桌面设置，可重试、临时使用或退出。";}}
    private void withConnection(Runnable next){if(busy)return;if(adb.connected())next.run();else connection.show(()->{if(active)next.run();},screen==1 && !recovery?("UNINSTALL".equals(originalChoice)?"连接并卸载原桌面":"连接并应用设置"):"连接并继续");}
    private void setDefault(){
        if(busy)return;
        SetupOperations.Ticket task=begin("正在设置并核对默认桌面…");worker.execute(()->{try{task.check();home.switchToOwn(adb);task.check();ui(task,()->{busy=false;roleCancelled=false;notice="";refresh();});}catch(Exception e){ui(task,()->{busy=false;notice="未完成默认桌面设置，请重新检测实际状态："+e.getMessage();render();});}});
    }
    private void confirm(){
        if(busy)return;final ArrayList<AppRepository.App> frozen=selected();final AppRepository.App old=original;final String choice=originalChoice;
        Runnable execute=()->{if(frozen.isEmpty() && "KEEP".equals(choice))runSelected(frozen,old,choice);else withConnection(()->runSelected(frozen,old,choice));};
        if("UNINSTALL".equals(choice)){Sheet sheet=setupSheet("卸载原桌面？");sheet.text("仅为当前用户卸载，数据将删除。恢复安装不恢复数据。\n"+(old==null?"":old.label));Button cancel=sheet.action("取消",sheet.dialog::dismiss);sheet.action("确认卸载",()->{sheet.dialog.dismiss();execute.run();});sheet.show(cancel);}else execute.run();
    }
    private void runSelected(ArrayList<AppRepository.App> frozen,AppRepository.App old,String choice){
        if(busy)return;
        SetupOperations.Ticket task=begin("正在核对所选操作…");worker.execute(()->{try{new SetupOperations(this,home.presets).execute(adb,frozen,old,choice,task,value->ui(task,()->{progress=value;render();}));ui(task,()->{busy=false;openHome("设置成功");});}catch(Exception e){ui(task,()->{busy=false;notice="部分设置未完成："+e.getMessage();roleCancelled=false;refresh();});}});
    }
    private void finishSetup(String status,String text){
        if(busy)return;SetupOperations.Ticket task=begin("正在核对默认桌面…");worker.execute(()->{try{task.check();if(!home.inspect(adb).isOwn(this))throw new java.io.IOException("默认桌面已改变或未确认");store.put("status",status);ui(task,()->{busy=false;openHome(text);});}catch(Exception e){ui(task,()->{busy=false;screen=0;notice=e.getMessage();render();});}});
    }
    private void renderRecovery(LinearLayout body){
        body.addView(label(original==null?"原桌面身份未确认":original.label+" · "+original.state(),18));gap(body,16);body.addView(muted("先恢复可用，再切回默认桌面；其他预装应用保持当前状态。恢复安装不会恢复已删除的数据。",13));gap(body,24);
        if(!adb.connected())action(body,"连接后核对恢复能力","primary",()->withConnection(this::refresh),true);
        else{
            boolean available=original!=null && original.metadataKnown && original.verified && (original.installed?(original.enabled||home.presets.homeAllows("disableVerified")):(original.recoverable&&home.presets.homeAllows("restoreInstallVerified")));
            String text=original==null?"无法确认原桌面":!original.installed?"恢复安装并切回":!original.enabled?"启用并切回":"切回原桌面";
            action(body,text,"primary",this::restore,available);if(!available)body.addView(muted("当前固件未验收恢复能力，或原桌面安装包／组件缺失。",12));
        }
        action(body,"重新检测","refresh",this::refresh,true);body.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));action(body,"退出设置","exit",()->exit(false),true);
    }
    private void restore(){if(busy)return;SetupOperations.Ticket task=begin("正在恢复原桌面…");worker.execute(()->{try{home.remember(home.inspect(adb));new SetupOperations(this).restore(adb,task,value->ui(task,()->{progress=value;render();}));ui(task,()->{busy=false;cancel();startActivity(HomeController.homeIntent());finish();});}catch(Exception e){ui(task,()->{busy=false;notice="恢复未完成："+e.getMessage();refresh();});}});}
    private void exit(boolean temporary){
        cancel();try{store.put("status",homeState==null || !homeState.known?"unknown":"deferred");}catch(Exception e){Toast.makeText(this,"设置记录未保存",Toast.LENGTH_LONG).show();}
        if(temporary)openHome("已进入 Quiet TV；默认桌面以系统实际状态为准");else{if(isTaskRoot())try{startActivity(HomeController.homeIntent());}catch(ActivityNotFoundException ignored){}finish();}
    }
    private void openHome(String text){cancel();try{startActivity(new Intent(this,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));Toast.makeText(this,text,Toast.LENGTH_LONG).show();finish();}catch(ActivityNotFoundException|SecurityException e){notice="Quiet TV 桌面组件不可用，请重新检测或退出设置。";render();}}
}
