package home.quiet.tv;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.*;

/** Retained version-update tab; the web engine is used only for the offline license document. */
final class VersionUpdatePanel extends ManagementPanel {
    private static final String ORIGIN="https://quiet-tv.invalid/about/";
    private static final String UPSTREAM="https://github.com/Gathin23/tv-vibehack";
    private AboutView scene;
    private UpdatePanel updates;
    private final UpdateRepository.Listener updateListener=()->{if(scene!=null)scene.updateAvailable(UpdateRepository.get(this).available!=null);if(updates!=null)updates.changed();};
    private FrameLayout content;
    private WebView web;
    private boolean license, licenseReady;
    private long navigation;
    private final ExecutorService deviceWorker=new ThreadPoolExecutor(0,1,5,TimeUnit.SECONDS,new LinkedBlockingQueue<>());
    private int deviceRead;

    VersionUpdatePanel(AppManagementActivity activity){super(activity);}
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);updates=new UpdatePanel(activity);
        try {
            PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);
            scene=new AboutView(this,getApplicationInfo().loadLabel(getPackageManager()),info.versionName,
                    UpdatePanel.channel(BuildConfig.UPDATE_CHANNEL),new AboutDeviceInfo(this),()->managementTab().requestFocus(),this::openLicense,this::openUpstream,updates::check,this::recovery);
        } catch(android.content.pm.PackageManager.NameNotFoundException e){throw new IllegalStateException("Own package unavailable",e);}
        pageRoot=column();content=new FrameLayout(this);
        content.addView(scene,new FrameLayout.LayoutParams(-1,-1));pageRoot.addView(content,new android.widget.LinearLayout.LayoutParams(-1,-1));activity.attachPanel(pageRoot);
        UiCompat.onDraw(scene,new Runnable(){
            private boolean drawn;
            @Override public void run(){if(!drawn){drawn=true;scene.post(()->{if(!isFinishing()){activity.reportFullyDrawn();Log.i("QuietTVAbout","Native About first frame complete");}});}}
        });
        if(state!=null && state.getBoolean("license"))openLicense();
    }

    private void recovery(){
        ChildModeStore store=new ChildModeStore(this);
        if(!store.configured()){message("请先在儿童模式设置家长密码");return;}
        ChildModeAuth.show(activity,()->{if(showing&&!license)scene.revealRecovery();});
    }
    private void openLicense() {
        scene.hideRecovery();
        license=true;licenseReady=false;++navigation;
        if(web==null){
            web=new WebView(this);web.setTag("about-license");web.setBackgroundColor(0xff0b121c);
            web.setVisibility(View.INVISIBLE);content.addView(web,new FrameLayout.LayoutParams(-1,-1));
            web.setNextFocusUpId(managementTab().getId());
            web.setOnKeyListener((view,key,event)->{if(key!=KeyEvent.KEYCODE_DPAD_UP || web.getScrollY()!=0)return false;if(event.getAction()==KeyEvent.ACTION_DOWN)managementTab().requestFocus();return true;});
            WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(false);settings.setDomStorageEnabled(false);
            settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setBlockNetworkLoads(true);
            if(android.os.Build.VERSION.SDK_INT>=21)settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSupportMultipleWindows(false);
            web.setWebViewClient(android.os.Build.VERSION.SDK_INT>=21?new Api21Client():new OfflineClient());
        }
        web.setVisibility(View.INVISIBLE);web.loadUrl(ORIGIN+"license.html");
    }
    private class OfflineClient extends WebViewClient {
        @Override public WebResourceResponse shouldInterceptRequest(WebView view,String url){return localResource(url);}
        @Override public boolean shouldOverrideUrlLoading(WebView view,String url){
            if((ORIGIN+"index.html").equals(url))returnToAbout();return true;
        }
        @Override public void onPageFinished(WebView view,String url){
            if(!(ORIGIN+"license.html").equals(url))return;
            long completed=navigation;
            if(android.os.Build.VERSION.SDK_INT>=23)Api23.whenDrawn(view,completed,()->showLicense(view,completed));
            // Android 4/5 have no visual-state callback; reveal the finished offline page on the next frame.
            else androidx.core.view.ViewCompat.postOnAnimation(view,()->showLicense(view,completed));
        }
    }
    @android.annotation.TargetApi(21)
    private final class Api21Client extends OfflineClient {
        @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest resource){
            return "GET".equals(resource.getMethod())?localResource(resource.getUrl().toString()):blocked();
        }
        @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest resource){
            if((ORIGIN+"index.html").equals(resource.getUrl().toString()))returnToAbout();return true;
        }
    }
    private void showLicense(WebView view,long completed){
        if(!license || completed!=navigation || isFinishing() || isDestroyed())return;
        boolean focus=showing && scene.hasFocus();
        view.setVisibility(View.VISIBLE);scene.setVisibility(View.INVISIBLE);
        if(focus)view.requestFocus();licenseReady=true;
        Log.i("QuietTVAbout","Offline license ready");
    }
    @android.annotation.TargetApi(23)
    private static final class Api23 {
        static void whenDrawn(WebView view,long completed,Runnable ready){
            view.postVisualStateCallback(completed,new WebView.VisualStateCallback(){
                @Override public void onComplete(long id){ready.run();}
            });
        }
    }
    private WebResourceResponse localResource(String url){
        if(!(ORIGIN+"license.html").equals(url))return blocked();
        try{return new WebResourceResponse("text/html","UTF-8",getAssets().open("about/license.html"));}
        catch(IOException e){return blocked();}
    }
    private WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
    private void openUpstream(){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(UPSTREAM)));}
        catch(ActivityNotFoundException e){Toast.makeText(this,"当前电视没有可用的浏览器",Toast.LENGTH_LONG).show();}
    }
    private void returnToAbout(){
        license=false;licenseReady=false;++navigation;web.stopLoading();web.setVisibility(View.INVISIBLE);
        scene.setVisibility(View.VISIBLE);scene.focusLicense();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int code=event.getKeyCode();
        if(managementTabFocused() || (!license && !scene.hasFocus()) || (license && !web.hasFocus()))return false;
        if(code==KeyEvent.KEYCODE_ESCAPE){if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0)handleBack();return true;}
        if(license){
            if(!licenseReady && code!=KeyEvent.KEYCODE_BACK)return true;
            return false;
        }
        if(code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN||code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT){
            if(event.getAction()==KeyEvent.ACTION_DOWN)scene.move(code);return true;
        }
        if(code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_NUMPAD_ENTER){
            // Activate on release, as HOME does, so finishing cannot send this key-up back to HOME.
            if(event.getAction()==KeyEvent.ACTION_DOWN)scene.press(true);
            if(event.getAction()==KeyEvent.ACTION_UP)scene.activate();return true;
        }
        return false;
    }
    @Override void focusManagementContent(){if(license)web.requestFocus();else scene.focusUpdate();}
    @Override public void handleBack(){if(license)returnToAbout();else if(!managementTabFocused())managementTab().requestFocus();else super.handleBack();}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("license",license);super.onSaveInstanceState(state);}
    private void refreshDevice(){
        int read=++deviceRead;
        scene.screen(AboutDeviceInfo.screen(getWindow().getWindowManager().getDefaultDisplay()));
        deviceWorker.execute(()->{
            AboutDeviceInfo.Resources info=AboutDeviceInfo.resources(getApplicationContext());
            runOnUiThread(()->{if(showing && read==deviceRead && !isDestroyed() && !isFinishing())scene.resources(info);});
        });
    }
    @Override protected void onPause(){ChildModeAuth.dismiss(activity);++deviceRead;if(scene!=null)scene.hideRecovery();if(web!=null)web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();refreshDevice();UpdateRepository.get(this).add(updateListener);UpdateRepository.get(this).reconcile();updateListener.changed();updates.resumed();if(web!=null)web.onResume();}
    @Override protected void onStop(){UpdateRepository.get(this).remove(updateListener);if(updates!=null)updates.suspend();super.onStop();}
    @Override protected void onDestroy(){++deviceRead;deviceWorker.shutdownNow();if(updates!=null)updates.suspend();if(web!=null){web.stopLoading();content.removeView(web);web.destroy();}super.onDestroy();}
}
