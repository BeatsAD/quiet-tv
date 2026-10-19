package home.quiet.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
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
import java.util.Collections;

/** Native About screen; the web engine is used only for the separate offline license document. */
public final class AboutActivity extends Activity {
    private static final String ORIGIN="https://quiet-tv.invalid/about/";
    private static final String UPSTREAM="https://github.com/Gathin23/tv-vibehack";
    private AboutView scene;
    private FrameLayout root;
    private WebView web;
    private boolean license, licenseReady;
    private long navigation;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        try {
            PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);
            scene=new AboutView(this,getApplicationInfo().loadLabel(getPackageManager()),info.versionName,
                    (getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0,this::finish,this::openLicense,this::openUpstream);
        } catch(android.content.pm.PackageManager.NameNotFoundException e){throw new IllegalStateException("Own package unavailable",e);}
        root=new FrameLayout(this);root.setBackgroundColor(0xff020405);
        root.addView(scene,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
        overridePendingTransition(0,0);
        scene.getViewTreeObserver().addOnDrawListener(new android.view.ViewTreeObserver.OnDrawListener(){
            private boolean drawn;
            @Override public void onDraw(){if(!drawn){drawn=true;scene.post(()->{if(!isFinishing()){reportFullyDrawn();Log.i("QuietTVAbout","Native About first frame complete");}});}}
        });
        if(state!=null && state.getBoolean("license"))openLicense();
    }

    private void openLicense() {
        license=true;licenseReady=false;++navigation;
        if(web==null){
            web=new WebView(this);web.setTag("about-license");web.setBackgroundColor(0xff0b121c);
            web.setVisibility(View.INVISIBLE);root.addView(web,new FrameLayout.LayoutParams(-1,-1));
            WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(false);settings.setDomStorageEnabled(false);
            settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setBlockNetworkLoads(true);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSupportMultipleWindows(false);
            web.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest resource){
                    if(!"GET".equals(resource.getMethod()) || !(ORIGIN+"license.html").equals(resource.getUrl().toString()))return blocked();
                    try{return new WebResourceResponse("text/html","UTF-8",getAssets().open("about/license.html"));}
                    catch(IOException e){return blocked();}
                }
                @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest resource){
                    if((ORIGIN+"index.html").equals(resource.getUrl().toString()))returnToAbout();return true;
                }
                @Override public void onPageFinished(WebView view,String url){
                    if(!(ORIGIN+"license.html").equals(url))return;
                    long completed=navigation;
                    view.postVisualStateCallback(completed,new WebView.VisualStateCallback(){
                        @Override public void onComplete(long id){
                            if(!license || id!=navigation || isFinishing() || isDestroyed())return;
                            // Attached INVISIBLE WebView becomes visible only when the complete DOM can be drawn.
                            view.setVisibility(View.VISIBLE);scene.setVisibility(View.INVISIBLE);
                            view.requestFocus();licenseReady=true;
                            Log.i("QuietTVAbout","Offline license visual frame ready");
                        }
                    });
                }
            });
        }
        web.setVisibility(View.INVISIBLE);web.loadUrl(ORIGIN+"license.html");
    }
    private WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
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
        if(code==KeyEvent.KEYCODE_ESCAPE){if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0)onBackPressed();return true;}
        if(license){
            if(!licenseReady && code!=KeyEvent.KEYCODE_BACK)return true;
            return super.dispatchKeyEvent(event);
        }
        if(code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN||code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT){
            if(event.getAction()==KeyEvent.ACTION_DOWN)scene.move(code);return true;
        }
        if(code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_NUMPAD_ENTER){
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0)scene.activate();return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void finish(){super.finish();overridePendingTransition(0,0);}
    @Override public void onBackPressed(){if(license)returnToAbout();else finish();}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("license",license);super.onSaveInstanceState(state);}
    @Override protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){if(web!=null){web.stopLoading();root.removeView(web);web.destroy();}super.onDestroy();}
}
