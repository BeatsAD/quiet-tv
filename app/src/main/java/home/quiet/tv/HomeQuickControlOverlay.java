package home.quiet.tv;

import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

/** The existing quick panel and HOME header share one window and one set of buttons. */
final class HomeQuickControlOverlay implements HomeQuickControlView.Host {
    private final HomeActivity home;
    private final FrameLayout root,layer;
    private final ViewGroup desktop;
    private final HomeHeaderView header;
    private final HomeQuickControlView controls;
    private final DockBackdropDrawable.Background background;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final int desktopFocus,volumeStream;
    private final long opened=SystemClock.uptimeMillis();
    private Runnable contentDraw;
    private String returnFocus="",restoredHeader="";
    private boolean closed,contentReported,started;

    HomeQuickControlOverlay(HomeActivity home,FrameLayout root,ViewGroup desktop,HomeHeaderView header,float unit,boolean chinese,DockBackdropDrawable.Background background,Bundle saved){
        this.home=home;this.root=root;this.desktop=desktop;this.header=header;this.background=background;
        desktopFocus=desktop.getDescendantFocusability();volumeStream=home.getVolumeControlStream();
        layer=new FrameLayout(home){
            private boolean headerGesture;
            @Override public boolean dispatchTouchEvent(MotionEvent event){
                if(closed)return false;
                int[] location=new int[2];header.buttons.getLocationOnScreen(location);
                if(event.getActionMasked()==MotionEvent.ACTION_DOWN){Rect bounds=new Rect();headerGesture=header.buttons.getGlobalVisibleRect(bounds)&&bounds.contains((int)event.getRawX(),(int)event.getRawY());}
                if(!headerGesture)return super.dispatchTouchEvent(event);
                MotionEvent copy=MotionEvent.obtain(event);int[] origin=new int[2];getLocationOnScreen(origin);copy.offsetLocation(origin[0]-location[0],origin[1]-location[1]);
                header.buttons.dispatchTouchEvent(copy);copy.recycle();return true;
            }
            // Only consume the empty desktop; the panel and original header handle clicks.
            @android.annotation.SuppressLint("ClickableViewAccessibility")
            @Override public boolean onTouchEvent(MotionEvent event){return true;}
        };
        layer.setClipChildren(false);layer.setClipToPadding(false);
        controls=new HomeQuickControlView(home,unit,chinese,this,this::execute);
        layer.addView(controls,controls.position());controls.restore(saved);
        if(saved!=null){returnFocus=saved.getString("inline-return-focus","");restoredHeader=saved.getString("inline-header-focus","");}
        desktop.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        for(TextView pill:header.pills){
            pill.setFocusable(true);pill.setFocusableInTouchMode(true);pill.setLongClickable(false);SettingsTouchClick.attach(pill);
            pill.setOnFocusChangeListener((v,on)->{if(on)syncHeader();});
        }
        home.setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        root.addView(layer,new FrameLayout.LayoutParams(-1,-1));
    }
    void resume(){
        String focused=started?headerFocus():restoredHeader;restoredHeader="";if(!returnFocus.isEmpty())focused="";
        int index=header.buttonIds.indexOf(focused);controls.setVisibility(index>=0&&!"control".equals(focused)?View.INVISIBLE:View.VISIBLE);
        if(index>=0)header.pills.get(index).requestFocus();else controls.requestFocus();
        controls.resume();controls.reload();if(!returnFocus.isEmpty()){controls.focus(returnFocus);returnFocus="";}started=true;syncHeader();
    }
    void pause(){controls.pause();}
    void refreshBackdrop(){if(!closed)controls.refreshBackdrop();}
    void returningTo(String id,boolean hide){returnFocus=id;controls.cancelAdjustments();if(hide)controls.setVisibility(View.INVISIBLE);}
    private String headerFocus(){View focused=header.buttons.findFocus();return focused!=null&&focused.getTag() instanceof String?(String)focused.getTag():"";}
    void save(Bundle state){controls.save(state);state.putString("inline-return-focus",returnFocus);state.putString("inline-header-focus",headerFocus());}
    void syncHeader(){
        if(closed)return;
        for(int i=0;i<header.pills.size();i++){
            TextView pill=header.pills.get(i);boolean focused=pill.isFocused();
            pill.setSelected(i<header.buttonIds.size()&&"control".equals(header.buttonIds.get(i))&&controls.getVisibility()==View.VISIBLE);
            if(pill.isActivated()!=focused){pill.setActivated(focused);SettingsFocusMotion.update(pill,focused);}
        }
        header.controlVisibility();
    }
    boolean key(KeyEvent event){
        int key=event.getKeyCode();boolean confirm=key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER;
        if(key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_ESCAPE){if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled())back();return true;}
        boolean direction=key>=KeyEvent.KEYCODE_DPAD_UP&&key<=KeyEvent.KEYCODE_DPAD_RIGHT;
        if(header.buttons.hasFocus()&&(confirm||direction)){
            if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){
                int index=header.pills.indexOf(header.buttons.findFocus());if(index<0||index>=header.buttonIds.size())return true;
                if(confirm)home.activateQuickHeader(header.buttonIds.get(index));
                else if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){
                    int next=Math.max(0,Math.min(header.buttonIds.size()-1,index+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1)));
                    controls.setVisibility("control".equals(header.buttonIds.get(next))?View.VISIBLE:View.INVISIBLE);header.pills.get(next).requestFocus();syncHeader();
                }else if(key==KeyEvent.KEYCODE_DPAD_DOWN){if("control".equals(header.buttonIds.get(index))){controls.setVisibility(View.VISIBLE);controls.requestFocus();controls.focus(null);}else home.closeQuickControls(true);}
            }return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_UP&&event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0&&controls.firstRowFocused()){
            int index=header.buttonIds.indexOf("control");
            if(index>=0){header.pills.get(index).requestFocus();syncHeader();return true;}
        }
        if(confirm||direction){controls.dispatchKeyEvent(event);return true;}
        return false;
    }
    void back(){if(!controls.cancelAdjustments()){home.closeQuickControls(false);KeyFeedback.returned(home);}}
    private void execute(String id){if(QuickControlStore.range(id)){controls.beginRange(id);return;}home.executeQuickControl(id);}
    void destroy(){
        closed=true;if(contentDraw!=null)contentDraw.run();controls.destroy();controls.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);controls.setFocusable(false);controls.setFocusableInTouchMode(false);controls.clearFocus();
        for(TextView pill:header.pills){pill.setOnFocusChangeListener(null);pill.setOnTouchListener(null);pill.setFocusable(false);pill.setFocusableInTouchMode(false);pill.setLongClickable(true);pill.setSelected(false);pill.setActivated(false);SettingsFocusMotion.update(pill,false);}
        header.controlVisibility();desktop.setDescendantFocusability(desktopFocus);home.setVolumeControlStream(volumeStream);worker.shutdown();
        if(home.isFinishing()||UiCompat.destroyed(home))root.removeView(layer);else UiMotion.exit(controls,-6*home.getResources().getDisplayMetrics().widthPixels/960f,()->root.removeView(layer));
    }
    @Override public boolean quickPanelFocusAllowed(){return !closed&&!header.buttons.hasFocus()&&controls.isShown();}
    @Override public void quickPanelFocused(){syncHeader();}
    @Override public QuickControlStore.Snapshot cachedShortcuts(){return new QuickControlStore(new File(home.getFilesDir(),"quick-controls")).cached();}
    @Override public void readShortcuts(HomeQuickControlView.Value<QuickControlStore.Snapshot> ready,HomeQuickControlView.Value<String> failed){
        worker.execute(()->{try{QuickControlStore.Snapshot value=new QuickControlStore(new File(home.getFilesDir(),"quick-controls")).read();home.runOnUiThread(()->{if(!closed)ready.accept(value);});}catch(IOException error){home.runOnUiThread(()->{if(!closed)failed.accept(error.getMessage());});}});
    }
    @Override public SettingsSheetBackdrop quickBackdrop(View panel){return new SettingsSheetBackdrop(panel,home.getResources().getDisplayMetrics().widthPixels/960f,background,worker);}
    @Override public void quickContentReady(View content){
        if(contentReported||contentDraw!=null)return;
        contentDraw=UiCompat.onDraw(content,()->content.post(()->{if(closed||contentReported)return;if(contentDraw!=null){contentDraw.run();contentDraw=null;}contentReported=true;DiagnosticLog.info("page.content_ready","page=quick_controls inline=true createMs="+(SystemClock.uptimeMillis()-opened)+" evidence=controls_drawn");}));
    }
}
