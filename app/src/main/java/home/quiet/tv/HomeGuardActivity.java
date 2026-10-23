package home.quiet.tv;

import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import java.util.List;

/** Explicit remote-controlled opt-in; opening system settings never enables handover by itself. */
public final class HomeGuardActivity extends ManagementUi {
    private HomeGuardState state;
    @Override public void onCreate(Bundle saved){super.onCreate(saved);state=new HomeGuardState(this);}
    @Override protected void onResume(){super.onResume();render();}
    private void render(){
        if(state.enabled() && (!state.supported() || !state.validSelection()))state.pause("原主页或适用条件已变化，守护已暂停");
        while(root.getChildCount()>1)root.removeViewAt(1);
        ((TextView)header.getChildAt(1)).setText("桌面守护（实验）");
        gap(root,18);root.addView(label(state.summary(),17));gap(root,9);
        root.addView(muted("识别原主页后返回 Quiet TV。系统默认 HOME 不因此改变。",12));
        root.addView(muted("请先停用独立中转助手；HOME、重启和待机唤醒需分别验证。",12));gap(root,14);
        ScrollView scroll=new ScrollView(this);LinearLayout homes=column();scroll.addView(homes);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        List<HomeGuardState.Candidate> candidates=state.candidates();Button first=null;
        if(state.supported()){
            homes.addView(muted("选择本机原主页",12));gap(homes,8);
            for(HomeGuardState.Candidate home:candidates){
                boolean selected=home.component.flattenToString().equals(state.selected());
                Button option=button((selected?"已选择  ":"")+home.label+"  "+home.component.getShortClassName(),()->{state.select(home.component);render();});option.setSelected(selected);
                option.setSingleLine(true);option.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(42));p.bottomMargin=px(6);homes.addView(option,p);if(first==null)first=option;
            }
            if(candidates.isEmpty())homes.addView(muted("未检测到可用的原主页，暂不能启用。",13));
        }
        gap(root,12);LinearLayout actions=row();root.addView(actions,new LinearLayout.LayoutParams(-1,px(40)));
        Button settings=button("开启无障碍",()->{
            try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}
            catch(ActivityNotFoundException|SecurityException failure){message("电视未开放无障碍设置入口，暂不能启用桌面守护（实验）。");}
        });actions.addView(settings,new LinearLayout.LayoutParams(-2,-1));settings.setEnabled(state.supported());
        Button enable=button("启用守护",()->{String problem=state.enable();if(!problem.isEmpty())message(problem);else render();});marginAdd(actions,enable,-2,-1,10);enable.setEnabled(state.supported() && !candidates.isEmpty());
        Button pause=button("暂停守护",()->{state.pause("已暂停");render();});marginAdd(actions,pause,-2,-1,10);
        Button refresh=button("重新检测",this::render);marginAdd(actions,refresh,-2,-1,10);
        gap(root,10);Button original=button("暂停并打开原桌面",()->{
            state.pause("已暂停");render();
            if(!state.validSelection()){message("原主页不可用，请重新选择。");return;}
            ComponentName home=ComponentName.unflattenFromString(state.selected());
            try{startActivity(new Intent(Intent.ACTION_MAIN).setComponent(home));}
            catch(ActivityNotFoundException|SecurityException failure){message("原桌面无法打开，守护保持暂停。");}
        });root.addView(original,new LinearLayout.LayoutParams(-2,px(38)));original.setEnabled(!state.selected().isEmpty());
        (state.enabled()?pause:first==null?refresh:first).requestFocus();
    }
}
