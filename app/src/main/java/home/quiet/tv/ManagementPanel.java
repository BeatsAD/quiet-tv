package home.quiet.tv;

import android.content.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** A retained content panel. The Activity owns navigation, dialogs and the one ADB session. */
abstract class ManagementPanel extends ContextWrapper {
    static final int TEXT=ManagementUi.TEXT,MUTED=ManagementUi.MUTED;
    final AppManagementActivity activity;
    final float unit;
    final LinearLayout root;
    final FrameLayout stage;
    LinearLayout pageRoot,managementInfo;
    boolean showing;
    ManagementPanel(AppManagementActivity activity){super(activity);this.activity=activity;unit=activity.unit;root=activity.root;stage=activity.stage;}
    public void onCreate(Bundle state){}
    protected void onResume(){showing=true;}
    protected void onPause(){showing=false;}
    protected void onStop(){}
    protected void onDestroy(){}
    protected void onSaveInstanceState(Bundle state){}
    public boolean dispatchKeyEvent(KeyEvent event){return false;}
    public void handleBack(){finish();}
    void focusManagementContent(){}
    void managementConnected(){}
    void activityResult(int request,int result,Intent data){}
    void permissionResult(int request,int[] results){}
    void leaveManagement(Runnable action){action.run();}
    void managementMore(){activity.defaultManagementMore();}
    void managementMoreOptions(LinearLayout menu){activity.defaultManagementMoreOptions(menu);}
    void managementMoreOption(LinearLayout menu,String title,Runnable action){activity.managementMoreOption(menu,title,action);}
    void connectionPanel(){activity.defaultConnectionPanel();}
    boolean useSettingListStyle(){return true;}
    boolean isDestroyed(){return UiCompat.destroyed(activity);}
    boolean isFinishing(){return activity.isFinishing();}
    void finish(){activity.finish();}
    void runOnUiThread(Runnable action){activity.runOnUiThread(action);}
    View getCurrentFocus(){return activity.getCurrentFocus();}
    Window getWindow(){return activity.getWindow();}
    int px(float value){return activity.px(value);}
    LinearLayout row(){return activity.row();}
    LinearLayout column(){return activity.column();}
    LinearLayout managementContent(){
        pageRoot=column();managementInfo=row();LinearLayout.LayoutParams info=new LinearLayout.LayoutParams(-1,px(26));info.topMargin=px(8);pageRoot.addView(managementInfo,info);
        LinearLayout body=column();LinearLayout.LayoutParams content=new LinearLayout.LayoutParams(-1,0,1);content.topMargin=px(6);pageRoot.addView(body,content);
        activity.attachPanel(pageRoot);return body;
    }
    Button managementTab(){return activity.managementTab();}
    boolean managementTabFocused(){return !showing||activity.managementTabFocused();}
    void styleManagementTab(Button button,boolean compact){activity.styleManagementTab(button,compact);}
    TextView managementHint(String text){return activity.managementHint(text);}
    TextView label(String text,int size){return activity.label(text,size);}
    TextView muted(String text,int size){return activity.muted(text,size);}
    TextView center(String text,int size){return activity.center(text,size);}
    Button button(String text,Runnable action){return activity.button(text,action);}
    EditText input(String text,String hint){return activity.input(text,hint);}
    ImageView icon(Drawable drawable,int size){return activity.icon(drawable,size);}
    GradientDrawable surface(int top,int bottom,int stroke,float radius){return activity.surface(top,bottom,stroke,radius);}
    Drawable focusSurface(float radius){return activity.focusSurface(radius);}
    Drawable listSurface(){return activity.listSurface();}
    void focusable(View view,float radius){activity.focusable(view,radius);}
    void gap(LinearLayout parent,int height){activity.gap(parent,height);}
    void message(String text){if(showing)activity.message(text);}
    void feedback(String text){if(showing)activity.feedback(text);}
    final class Sheet {
        final ManagementUi.Sheet delegate;
        final android.app.Dialog dialog;
        final LinearLayout body;
        Sheet(String title){delegate=activity.new Sheet(title);dialog=delegate.dialog;body=delegate.body;}
        void text(String text){delegate.text(text);}
        Button action(String text,Runnable action){return delegate.action(text,action);}
        Button option(String text,Runnable action){return delegate.option(text,action);}
        Button option(String text,int icon,Runnable action){return delegate.option(text,icon,action);}
        void show(View focus){delegate.show(focus);}
    }
}
