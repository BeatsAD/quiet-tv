package home.quiet.tv;

import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Lifecycle adapter for the existing weather editor and its permission callbacks. */
final class WeatherSettingsPanel extends ManagementPanel {
    private DesktopWeatherPanel weather;
    private int scrollY;
    private String focusTag;
    WeatherSettingsPanel(AppManagementActivity activity){super(activity);}
    @Override public void onCreate(Bundle state){
        pageRoot=column();pageRoot.setPadding(0,px(18),0,0);weather=new DesktopWeatherPanel(activity);pageRoot.addView(weather.view,new LinearLayout.LayoutParams(-1,-1));activity.attachPanel(pageRoot);
        activity.wireRows(weather.rows(),managementTab());
        if(state!=null){scrollY=state.getInt("scroll");focusTag=state.getString("focus");}
    }
    private void remember(){View focus=getCurrentFocus();if(focus!=null&&pageRoot.hasFocus()&&focus.getTag() instanceof String)focusTag=(String)focus.getTag();scrollY=weather.view.getScrollY();}
    @Override protected void onResume(){super.onResume();weather.resume();weather.view.post(()->{if(showing)weather.view.scrollTo(0,scrollY);});}
    @Override protected void onPause(){remember();weather.pause();super.onPause();}
    @Override protected void onDestroy(){weather.destroy();super.onDestroy();}
    @Override void permissionResult(int request,int[] results){weather.permissionResult(request);}
    @Override void focusManagementContent(){View target=focusTag==null?null:pageRoot.findViewWithTag(focusTag);if(target==null||!target.isShown()||!target.isEnabled())target=weather.firstRow();target.requestFocus();}
    @Override protected void onSaveInstanceState(Bundle state){remember();state.putString("focus",focusTag);state.putInt("scroll",scrollY);}
    void settingsChanged(){weather.settingsChanged();}
}
