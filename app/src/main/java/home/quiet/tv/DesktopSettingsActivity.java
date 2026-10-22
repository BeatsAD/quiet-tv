package home.quiet.tv;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import org.json.JSONObject;

/** Independent home display controls; changes take effect on returning Home. */
public final class DesktopSettingsActivity extends ManagementUi {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        boolean chinese=true;
        try(java.io.InputStream input=getAssets().open("launcher.json");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            byte[] bytes=new byte[4096];int count;while((count=input.read(bytes))!=-1)out.write(bytes,0,count);
            chinese=!"en".equals(new JSONObject(out.toString("UTF-8")).optString("language","zh"));
        }catch(java.io.IOException|org.json.JSONException e){throw new IllegalStateException("Invalid bundled launcher configuration",e);}
        ((TextView)header.getChildAt(1)).setText(chinese?"桌面设置":"Desktop Settings");
        header.getChildAt(0).setContentDescription(chinese?"返回":"Back");
        DesktopSettingsStore store=new DesktopSettingsStore(this);
        gap(root,32);
        Switch time=option(chinese?"显示时间与日期":"Show time and date",store.showTime());
        time.setOnCheckedChangeListener((view,checked)->store.showTime(checked));
        gap(root,14);
        Switch buttons=option(chinese?"显示右侧按钮":"Show right-side buttons",store.showButtons());
        buttons.setOnCheckedChangeListener((view,checked)->store.showButtons(checked));
        if(!BuildConfig.LEGACY_19){
            gap(root,20);
            root.addView(button(chinese?"主页兼容（实验）":"Home compatibility (experimental)",
                ()->startActivity(new android.content.Intent(this,HomeGuardActivity.class))),new LinearLayout.LayoutParams(-2,px(42)));
        }
        gap(root,14);root.addView(button(chinese?"诊断日志":"Diagnostics",()->startActivity(new android.content.Intent(this,DiagnosticsActivity.class))),new LinearLayout.LayoutParams(-2,px(38)));
        time.requestFocus();
    }
    private Switch option(String title,boolean checked){
        Switch view=new Switch(this);view.setText(title);view.setTextColor(TEXT);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(18));view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(px(24),0,px(24),0);view.setSwitchPadding(px(24));view.setChecked(checked);
        focusable(view,14);root.addView(view,new LinearLayout.LayoutParams(-1,px(76)));return view;
    }
}
