package home.quiet.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.webkit.*;
import java.io.*;

/** Offline prototype only. No JavaScript bridge, ADB controller, shell, or native setup continuation. */
public final class SetupDemoActivity extends Activity {
    private static final String ORIGIN="https://quiet-tv.invalid/";
    private WebView web;
    @SuppressLint("SetJavaScriptEnabled") // Packaged, offline prototype scripts only; no native bridge or network access.
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().getDecorView().setSystemUiVisibility(5894);
        WebView.setWebContentsDebuggingEnabled((getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0);
        web=new WebView(this);web.setTag("setup-demo");web.setBackgroundColor(0xff0d1014);setContentView(web);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setBlockNetworkLoads(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSupportMultipleWindows(false);
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();String path=uri.getPath();
                if(!"GET".equals(request.getMethod()) || !"https".equals(uri.getScheme()) || !"quiet-tv.invalid".equals(uri.getHost()) || path==null || !path.matches("/[A-Za-z0-9_./-]+") || path.contains(".."))return blocked();
                try{String type=MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(path));if(path.endsWith(".js"))type="text/javascript";return new WebResourceResponse(type==null?"application/octet-stream":type,"UTF-8",getAssets().open("setup-demo"+path));}catch(IOException e){return blocked();}
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){if((ORIGIN+"exit").equals(request.getUrl().toString()))finish();return true;}
            @Override public void onPageFinished(WebView view,String url){view.requestFocus();}
        });
        web.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener(){
            boolean keyboard;
            @Override public WindowInsets onApplyWindowInsets(View view,WindowInsets insets){boolean shown=insets.isVisible(WindowInsets.Type.ime());if(keyboard && !shown)web.evaluateJavascript("window.quietTVRemote?.finishEditing()",null);keyboard=shown;return insets;}
        });
        web.loadUrl(ORIGIN+"tv.html"+(getIntent().getBooleanExtra("restore",false)?"?restore=true":""));
    }
    private WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",java.util.Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        String direction=null;switch(event.getKeyCode()){
            case KeyEvent.KEYCODE_DPAD_UP:direction="ArrowUp";break;case KeyEvent.KEYCODE_DPAD_DOWN:direction="ArrowDown";break;
            case KeyEvent.KEYCODE_DPAD_LEFT:direction="ArrowLeft";break;case KeyEvent.KEYCODE_DPAD_RIGHT:direction="ArrowRight";break;
        }
        if(direction!=null){if(event.getAction()==KeyEvent.ACTION_DOWN)web.evaluateJavascript("window.quietTVRemote?.move('"+direction+"')",result->{if("\"done\"".equals(result))getSystemService(InputMethodManager.class).hideSoftInputFromWindow(web.getWindowToken(),0);});return true;}
        if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode()==KeyEvent.KEYCODE_ENTER || event.getKeyCode()==KeyEvent.KEYCODE_NUMPAD_ENTER){
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0)web.evaluateJavascript("window.quietTVRemote?.activate()",result->{InputMethodManager keyboard=getSystemService(InputMethodManager.class);if("\"edit\"".equals(result))keyboard.showSoftInput(web,InputMethodManager.SHOW_IMPLICIT);else keyboard.hideSoftInputFromWindow(web.getWindowToken(),0);});return true;
        }
        if(event.getKeyCode()==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0)web.evaluateJavascript("window.quietTVRemote?.tools()",null);return true;}
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed(){web.evaluateJavascript("window.quietTVRemote?.back()",result->{if("\"exit\"".equals(result))finish();});getSystemService(InputMethodManager.class).hideSoftInputFromWindow(web.getWindowToken(),0);}
    @Override protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}
