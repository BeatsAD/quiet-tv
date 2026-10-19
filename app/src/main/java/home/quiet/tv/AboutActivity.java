package home.quiet.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

/** The reviewed single-screen About UI, packaged offline with installed-app metadata. */
public final class AboutActivity extends Activity {
    private static final String ORIGIN="https://quiet-tv.invalid/about/";
    private static final String PROJECT="https://github.com/BeatsAD/quiet-tv";
    private static final String UPSTREAM="https://github.com/Gathin23/tv-vibehack";
    private static final String EMAIL="mailto:wenbinailab@gmail.com";
    private WebView web;
    private String pageUrl;
    private boolean license, restoreLicense, pageReady;

    @SuppressLint("SetJavaScriptEnabled") // Only bundled scripts, no JavaScript-to-native bridge or network loads.
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        try {
            PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);
            pageUrl=Uri.parse(ORIGIN+"index.html").buildUpon()
                    .appendQueryParameter("name",getApplicationInfo().loadLabel(getPackageManager()).toString())
                    .appendQueryParameter("version",info.versionName)
                    .appendQueryParameter("build",Long.toString(info.getLongVersionCode()))
                    .appendQueryParameter("debug",Boolean.toString((getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0))
                    .build().toString();
        } catch (android.content.pm.PackageManager.NameNotFoundException e) { throw new IllegalStateException("Own package unavailable",e); }
        web=new WebView(this);web.setTag("about");web.setBackgroundColor(0xff020405);setContentView(web);
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setBlockNetworkLoads(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSupportMultipleWindows(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view,String url,Bitmap favicon){pageReady=false;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request) {
                Uri uri=request.getUrl();String path=uri.getPath();
                if(!"GET".equals(request.getMethod()) || !"https".equals(uri.getScheme()) || !"quiet-tv.invalid".equals(uri.getHost()) || path==null || !path.startsWith("/about/"))return blocked();
                String file=path.substring("/about/".length());
                if(!Arrays.asList("index.html","styles.css","app.js","contact.png","fonts/outfit.ttf","assets/source.svg","license.html").contains(file))return blocked();
                try {
                    String type=MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(path));
                    if(file.endsWith(".js"))type="text/javascript";
                    return new WebResourceResponse(type==null?"application/octet-stream":type,"UTF-8",getAssets().open("about/"+file));
                } catch (IOException e) { return blocked(); }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) {
                String url=request.getUrl().toString();
                if((ORIGIN+"exit").equals(url)){finish();return true;}
                if((ORIGIN+"license.html").equals(url)){license=true;return false;}
                if(url.startsWith(ORIGIN+"index.html")){returnToAbout();return true;}
                openLink(request.getUrl());return true;
            }
            @Override public void onPageFinished(WebView view,String url) {
                view.requestFocus();
                if(!license){
                    String focus=restoreLicense?"restoreLicense":"focusInitial";restoreLicense=false;
                    view.evaluateJavascript("window.quietTVAbout?."+focus+"()",value->{
                        pageReady=true;
                        view.evaluateJavascript("window.quietTVAbout.ready=true",null);
                    });
                }else pageReady=true;
            }
        });
        license=state!=null && state.getBoolean("license");
        web.loadUrl(license?ORIGIN+"license.html":pageUrl);
    }

    private WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));
    }

    private void openLink(Uri uri) {
        String url=uri.toString();
        if(!PROJECT.equals(url) && !UPSTREAM.equals(url) && !EMAIL.equals(url))return;
        try { startActivity(new Intent(EMAIL.equals(url)?Intent.ACTION_SENDTO:Intent.ACTION_VIEW,uri)); }
        catch (ActivityNotFoundException e) {
            Toast.makeText(this,EMAIL.equals(url)?"当前电视没有可用的邮件应用，可在手机上发送邮件":"当前电视没有可用的浏览器，请用手机扫描项目二维码",Toast.LENGTH_LONG).show();
        }
    }

    private void returnToAbout() { pageReady=false;license=false;restoreLicense=true;web.loadUrl(pageUrl); }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(event.getKeyCode()==KeyEvent.KEYCODE_ESCAPE){if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0)onBackPressed();return true;}
        if(license)return super.dispatchKeyEvent(event);
        String key=null;
        switch(event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:key="ArrowUp";break;
            case KeyEvent.KEYCODE_DPAD_DOWN:key="ArrowDown";break;
            case KeyEvent.KEYCODE_DPAD_LEFT:key="ArrowLeft";break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:key="ArrowRight";break;
        }
        if(key!=null){if(pageReady && event.getAction()==KeyEvent.ACTION_DOWN)web.evaluateJavascript("window.quietTVAbout.move('"+key+"')",null);return true;}
        if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode()==KeyEvent.KEYCODE_ENTER || event.getKeyCode()==KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if(pageReady && event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0)web.evaluateJavascript("window.quietTVAbout.activate()",null);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed() { if(license)returnToAbout();else finish(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putBoolean("license",license);super.onSaveInstanceState(state); }
    @Override protected void onPause(){web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){web.stopLoading();web.destroy();super.onDestroy();}
}
