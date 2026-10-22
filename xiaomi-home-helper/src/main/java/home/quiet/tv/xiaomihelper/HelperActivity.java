package home.quiet.tv.xiaomihelper;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import java.util.List;

/** All setup uses the remote; enabling this helper never changes the stock launcher or HOME preference. */
public final class HelperActivity extends Activity {
    private HelperState state;
    @Override public void onCreate(Bundle saved){super.onCreate(saved);state=new HelperState(this);}
    @Override public void onResume(){super.onResume();render();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void message(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    private void render(){
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(32),dp(20),dp(32),dp(20));scroll.addView(root);
        TextView title=new TextView(this);title.setText("Quiet TV 小米中转助手");title.setTextSize(26);root.addView(title);
        TextView status=new TextView(this);status.setTextSize(17);
        status.setText("实验适配  MiTV4-ANSM0  Android 6.0.1\n当前设备："+Build.MODEL+"  API "+Build.VERSION.SDK_INT
            +"\n中转："+(state.enabled()?"已启用":"已暂停")+"  无障碍："+(state.serviceEnabled()?"已开启":"未开启")
            +"\n系统默认 HOME 不会因此改变。可能短暂显示原桌面，真实 HOME、重启和唤醒仍需验收。");
        root.addView(status);
        List<ComponentName> homes=state.stockHomes();
        if(!state.supported()){
            TextView unsupported=new TextView(this);unsupported.setText("当前设备不属于此实验适配范围，中转不可启用。");root.addView(unsupported);
        }
        TextView prompt=new TextView(this);prompt.setText("选择本机检测到的小米原主页。仅匹配这个主页，不拦截设置或信号源。");root.addView(prompt);
        RadioGroup group=new RadioGroup(this);root.addView(group);
        for(ComponentName home:homes){
            RadioButton button=new RadioButton(this);button.setId(View.generateViewId());button.setText(home.flattenToShortString());group.addView(button);
            button.setChecked(home.flattenToString().equals(state.selected()));
            button.setOnClickListener(v->{state.pause();state.preferences.edit().putString("home",home.flattenToString()).apply();status.setText("原主页已选择，中转已暂停。开启无障碍后点击启用中转。");});
        }
        if(homes.isEmpty()){TextView empty=new TextView(this);empty.setText("未检测到可用的小米原主页，不能启用中转。");root.addView(empty);}
        button(root,"打开无障碍设置",()->{
            try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}
            catch(ActivityNotFoundException|SecurityException e){message("电视未开放无障碍设置入口。中转暂不可用，请保留原桌面。");}
        });
        button(root,"启用中转",()->{
            if(!state.supported()){message("当前型号或 Android 版本不在此实验适配范围");return;}
            if(!state.validSelection()){message("请先选择本机检测到的小米原主页");return;}
            if(!state.quietAvailable()){message("请先安装并确认 Quiet TV 可以打开");return;}
            if(!state.serviceEnabled()){message("请先在无障碍设置中开启 Quiet TV 小米中转助手，再返回启用");return;}
            state.preferences.edit().putBoolean("enabled",true).apply();render();
        });
        button(root,"暂停中转",()->{state.pause();render();});
        button(root,"打开 Quiet TV",()->{if(!state.openQuiet())message("Quiet TV 未安装或无法打开");});
        setContentView(scroll);
    }
    private void button(LinearLayout root,String label,Runnable action){
        Button button=new Button(this);button.setText(label);button.setAllCaps(false);button.setOnClickListener(v->action.run());
        root.addView(button,new LinearLayout.LayoutParams(-1,dp(52)));
    }
}
