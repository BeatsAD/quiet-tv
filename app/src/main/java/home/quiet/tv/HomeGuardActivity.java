package home.quiet.tv;

import android.os.Bundle;
import android.view.View;
import android.widget.*;

/** Keeps existing direct callers working; the settings Tab uses the same panel. */
public final class HomeGuardActivity extends ManagementUi {
    private HomeGuardPanel panel;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);((TextView)header.getChildAt(1)).setText("桌面守护（实验）");
        View back=header.getChildAt(0);back.setId(View.generateViewId());
        panel=new HomeGuardPanel(this,back);panel.restoreState(saved);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,0,1);p.topMargin=px(18);root.addView(panel.view,p);
        if(saved==null)root.post(()->panel.firstRow().requestFocus());
    }
    @Override protected void onResume(){super.onResume();panel.resume();}
    @Override protected void onPause(){panel.pause();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle saved){super.onSaveInstanceState(saved);panel.saveState(saved);}
}
