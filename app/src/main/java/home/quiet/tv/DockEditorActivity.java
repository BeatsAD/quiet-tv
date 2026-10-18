package home.quiet.tv;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class DockEditorActivity extends ManagementUi {
    private DockStore store;
    private ArrayList<String> packages;
    private ArrayList<String> moving;
    private int movingIndex;
    private LinearLayout content;
    private final ArrayList<Button> slots=new ArrayList<>();
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new DockStore(this);packages=store.load();
        header.addView(label("Dock 管理",26),new LinearLayout.LayoutParams(0,-1,1));
        header.addView(button("应用列表",()->finish()));
        content=column();root.addView(content);render(-1);
    }
    private String name(String pkg){try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,PackageManager.MATCH_DISABLED_COMPONENTS)).toString();}catch(PackageManager.NameNotFoundException e){return pkg;}}
    private void render(int focus){
        content.removeAllViews();slots.clear();
        content.addView(label(moving==null?"最多 4 个常用应用 · 确定编辑位置 · 无需 ADB":"移动模式：左右调整 · 确定保存 · 返回取消",15));
        LinearLayout row=row();content.addView(row,new LinearLayout.LayoutParams(-1,px(150)));
        List<String> values=moving==null?packages:moving;
        for(int i=0;i<4;i++){
            final int slot=i;
            String title=(i+1)+"\n"+(i<values.size()?name(values.get(i)):"添加常用应用");
            Button b=button(title,()->edit(slot));slots.add(b);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,px(122),1);p.setMargins(px(4),px(16),px(4),0);row.addView(b,p);
        }
        content.addView(label("移除仅改变快捷入口；禁用应用保留位置，重新启用后恢复。",14));
        content.addView(label("所有更改保存在电视本地，更新安装保留设置。",14));
        slots.get(focus<0?0:focus).requestFocus();
    }
    private void edit(int index){
        if(moving!=null)return;
        if(index>=packages.size()){choose(-1);return;}
        new AlertDialog.Builder(this).setTitle(name(packages.get(index))).setItems(new String[]{"替换应用","移除快捷入口","左右排序"},(d,which)->{
            if(which==0)choose(index);
            if(which==1){packages.remove(index);store.save(packages);render(Math.min(index,3));}
            if(which==2){moving=new ArrayList<>(packages);movingIndex=index;render(index);}
        }).show();
    }
    private void choose(int replace){
        PackageManager pm=getPackageManager();LinkedHashMap<String,ResolveInfo> choices=new LinkedHashMap<>();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER})
            for(ResolveInfo info:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0))
                if(info.activityInfo.applicationInfo.enabled && !info.activityInfo.packageName.equals(getPackageName()) && !packages.contains(info.activityInfo.packageName))choices.putIfAbsent(info.activityInfo.packageName,info);
        ArrayList<ResolveInfo> apps=new ArrayList<>(choices.values());apps.sort((a,b)->a.loadLabel(pm).toString().compareTo(b.loadLabel(pm).toString()));
        if(apps.isEmpty()){message("没有可添加的已启用应用");return;}
        String[] names=new String[apps.size()];for(int i=0;i<apps.size();i++)names[i]=apps.get(i).loadLabel(pm)+"\n"+apps.get(i).activityInfo.packageName;
        new AlertDialog.Builder(this).setTitle(replace<0?"添加到 Dock 末尾":"替换第 "+(replace+1)+" 个位置").setItems(names,(d,which)->{
            String pkg=apps.get(which).activityInfo.packageName;
            if(replace<0){if(packages.size()==4){message("Dock 已满，请选择一个位置替换");return;}packages.add(pkg);}else packages.set(replace,pkg);
            store.save(packages);render(replace<0?packages.size()-1:replace);
        }).setNegativeButton("取消",null).show();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(moving==null)return super.dispatchKeyEvent(event);
        int key=event.getKeyCode();
        if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP){moving=null;render(movingIndex);}return true;}
        if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER || key==KeyEvent.KEYCODE_NUMPAD_ENTER){
            if(event.getAction()==KeyEvent.ACTION_UP){packages=moving;moving=null;store.save(packages);render(movingIndex);}return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT){
            if(event.getAction()==KeyEvent.ACTION_DOWN){int target=Math.max(0,Math.min(moving.size()-1,movingIndex+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1)));Collections.swap(moving,movingIndex,target);movingIndex=target;render(target);}return true;
        }
        return key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN || super.dispatchKeyEvent(event);
    }
}
