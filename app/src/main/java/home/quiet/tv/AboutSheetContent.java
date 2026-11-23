package home.quiet.tv;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/** The three About tabs share one header and keep the remote focused while readings change. */
final class AboutSheetContent {
    private static final int DEVICE=0,RUNTIME=1,SOURCE=2;
    private static final String[] TITLES={"设备信息","运行状态","开源信息"};
    private static final String[] TAGS={"help-device","help-runtime","help-opensource"};
    private static final int BLUE=0xff9adaf7;
    private static final int DEVICE_VALUE_SIZE=18;
    private final AppManagementActivity ui;
    private final Button close;
    private final ExecutorService worker;
    private final Button[] tabs=new Button[TITLES.length];
    private final FrameLayout panel;
    private final AboutDeviceInfo device;
    private final Runnable refresh=this::readRuntime;
    private int tab=-1,generation;
    private boolean active;
    private LinearLayout body,runtimeRows;
    private ScrollView scroll;
    private TextView gpu,note;
    private Usage memory,storage;
    private View license;
    private ListView document;
    private AboutRuntime.Snapshot renderedRuntime;

    AboutSheetContent(AppManagementActivity ui,ManagementUi.Sheet sheet,Button close,
                      ExecutorService worker){
        this.ui=ui;this.close=close;this.worker=worker;
        device=new AboutDeviceInfo(ui);
        header(sheet);
        // Give the About tabs a bounded viewport with their own scroll targets.
        int contentIndex=sheet.box.indexOfChild(sheet.scroll);
        ViewGroup.LayoutParams contentSize=sheet.scroll.getLayoutParams();
        sheet.body.setPadding(sheet.scroll.getPaddingLeft(),sheet.scroll.getPaddingTop(),
                sheet.scroll.getPaddingRight(),sheet.scroll.getPaddingBottom());
        sheet.scroll.removeView(sheet.body);
        sheet.box.removeViewAt(contentIndex);
        sheet.box.addView(sheet.body,contentIndex,contentSize);
        LinearLayout layout=ui.row();
        sheet.body.addView(layout,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout navigation=ui.column();
        layout.addView(navigation,new LinearLayout.LayoutParams(px(180),-1));
        for(int i=0;i<tabs.length;i++){
            final int index=i;
            Button button=ui.button(TITLES[i],()->select(index));
            button.setId(ViewCompat.generateViewId());
            button.setTag(TAGS[i]);
            button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,DesktopSettingsRow.TITLE_SIZE*ui.unit);
            button.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
            button.setPadding(px(16),0,px(10),0);
            button.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));
            button.setOnFocusChangeListener((view,focused)->{if(focused)select(index);});
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,px(49));
            size.topMargin=px(i==0?6:8);
            navigation.addView(button,size);
            tabs[i]=button;
        }
        for(int i=0;i<tabs.length;i++){
            tabs[i].setNextFocusUpId(i==0?close.getId():tabs[i-1].getId());
            tabs[i].setNextFocusDownId(tabs[Math.min(tabs.length-1,i+1)].getId());
            tabs[i].setNextFocusLeftId(tabs[i].getId());
        }
        panel=new FrameLayout(ui);
        panel.setBackgroundDrawable(ui.surface(0x101f2830,0x100c0c0f,UiPalette.EDGE,15));
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,-1,1);
        size.leftMargin=px(19);
        layout.addView(panel,size);
    }

    private int px(float size){return ui.px(size);}

    private void header(ManagementUi.Sheet sheet){
        LinearLayout header=(LinearLayout)sheet.heading.getParent();
        header.removeView(sheet.heading);
        header.setGravity(Gravity.TOP);
        header.getLayoutParams().height=px(81);
        LinearLayout brand=ui.row();
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding(px(17),0,px(12),px(8));
        ImageView icon=new ImageView(ui);
        icon.setImageResource(R.drawable.launcher_icon);
        ViewCompat.setImportantForAccessibility(icon,ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);
        brand.addView(icon,new LinearLayout.LayoutParams(px(64),px(64)));

        LinearLayout labels=ui.column();
        LinearLayout.LayoutParams textSize=new LinearLayout.LayoutParams(0,-2,1);
        textSize.leftMargin=px(18);
        brand.addView(labels,textSize);
        TextView title=label("QuietTV "+BuildConfig.VERSION_NAME+" "+UpdatePanel.channel(BuildConfig.UPDATE_CHANNEL),
                25,true,UiPalette.TEXT);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        add(labels,title,0);
        add(labels,label(AboutDeviceInfo.INTRO,15,false,UiPalette.SECONDARY),4);
        header.addView(brand,0,new LinearLayout.LayoutParams(0,-1,1));
        close.getLayoutParams().width=px(61);
        close.getLayoutParams().height=px(30);
        ((LinearLayout.LayoutParams)close.getLayoutParams()).topMargin=px(3);
        close.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,13*ui.unit);
    }

    void start(int initial,boolean visible){
        active=visible;
        select(initial>=DEVICE&&initial<=SOURCE?initial:DEVICE);
    }
    int tab(){return tab;}
    String tabFocus(){return TAGS[Math.max(0,tab)];}
    void pause(){
        active=false;
        ++generation;
        panel.removeCallbacks(refresh);
    }
    void resume(){
        if(active)return;
        active=true;
        if(tab==DEVICE)readDevice();
        else if(tab==RUNTIME)readRuntime();
    }

    private void select(int index){
        if(tab==index)return;
        ++generation;
        panel.removeCallbacks(refresh);
        tab=index;
        panel.removeAllViews();
        renderedRuntime=null;
        license=null;
        scroll=null;
        body=ui.column();
        body.setPadding(px(24),px(14),px(22),px(18));
        panel.addView(index==SOURCE?createScroll(body,"about-content-scroll"):body,new FrameLayout.LayoutParams(-1,-1));

        for(int i=0;i<tabs.length;i++){
            Button button=tabs[i];
            button.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,12,i==tab?UiPalette.SELECTED:UiPalette.CONTROL,
                    false,UiPalette.EDGE));
            button.setNextFocusRightId(button.getId());
        }
        close.setNextFocusDownId(tabs[tab].getId());
        close.setNextFocusLeftId(tabs[tab].getId());
        if(tab==DEVICE)device();
        else if(tab==RUNTIME)runtime();
        else source();
    }

    private ScrollView createScroll(LinearLayout content,String tag){
        ScrollView viewport=ui.settingsScroll();
        viewport.setId(ViewCompat.generateViewId());
        viewport.setTag(tag);
        viewport.setVerticalScrollBarEnabled(false);
        viewport.setFocusable(false);
        viewport.addView(content,new ScrollView.LayoutParams(-1,-2));
        scroll=viewport;
        return viewport;
    }

    private TextView label(String value,float size,boolean bold,int color){
        TextView text=ui.label(value,12);
        text.setTextColor(color);
        text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size*ui.unit);
        text.setIncludeFontPadding(false);
        if(bold)text.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        return text;
    }

    private TextView sectionHeading(String title){
        TextView heading=ui.label(title,12);
        ManagementUi.styleSheetHeading(heading,ui.unit);
        return heading;
    }

    private void add(LinearLayout parent,View child,int gap){
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);
        size.topMargin=px(gap);
        parent.addView(child,size);
    }

    private void spacer(LinearLayout parent,int size){
        parent.addView(new View(ui),new LinearLayout.LayoutParams(px(size),1));
    }

    private LinearLayout gridRow(int gap){
        LinearLayout row=ui.row();
        add(body,row,gap);
        return row;
    }

    private LinearLayout half(LinearLayout row){
        if(row.getChildCount()>0)spacer(row,40);
        LinearLayout cell=ui.column();
        row.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
        return cell;
    }

    private TextView field(LinearLayout cell,String name,String value,float valueSize,boolean bold){
        add(cell,label(name,14,false,UiPalette.SECONDARY),0);
        TextView text=label(value,valueSize,bold,UiPalette.TEXT);
        add(cell,text,4);
        return text;
    }

    private void device(){
        body.setPadding(body.getPaddingLeft(),px(8),body.getPaddingRight(),px(8));
        LinearLayout identity=gridRow(0);
        identity.addView(label("电视型号",14,false,UiPalette.SECONDARY),new LinearLayout.LayoutParams(0,-2,1));
        TextView brand=label(device.brand,14,false,UiPalette.TEXT);
        brand.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        identity.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        add(body,label(device.model,25,false,UiPalette.TEXT),4);
        LinearLayout row=gridRow(6);
        field(half(row),"安卓版本",device.android,DEVICE_VALUE_SIZE,false);
        field(half(row),"芯片型号",device.chipset,DEVICE_VALUE_SIZE,false);
        row=gridRow(6);
        AboutDeviceInfo.Screen screen=AboutDeviceInfo.screen(ui);
        field(half(row),screen.label,screen.value,DEVICE_VALUE_SIZE,false);
        gpu=field(half(row),"GPU 型号","读取中…",DEVICE_VALUE_SIZE,false);
        LinearLayout firmware=ui.column();
        add(body,firmware,6);
        field(firmware,"固件版本",device.firmware,DEVICE_VALUE_SIZE,false);
        row=gridRow(8);
        memory=new Usage(half(row),"运行内存");
        storage=new Usage(half(row),"内部存储");
        readDevice();
    }

    private void readDevice(){
        if(!active)return;
        int request=++generation;
        worker.execute(()->{
            AboutDeviceInfo.Resources info=AboutDeviceInfo.resources(ui);
            ui.runOnUiThread(()->{
                if(!active||generation!=request)return;
                memory.bind(info.ram);
                storage.bind(info.internal);
            });
            String renderer=AboutDeviceInfo.gpu();
            ui.runOnUiThread(()->{
                if(active&&generation==request)gpu.setText(renderer);
            });
        });
    }

    private final class Usage {
        final TextView value,percent;
        final LinearLayout track;
        final Bar bar;
        Usage(LinearLayout cell,String title){
            value=field(cell,title,"读取中…",23,true);
            track=ui.row();
            track.setGravity(Gravity.CENTER_VERTICAL);
            add(cell,track,6);
            bar=new Bar();
            track.addView(bar,new LinearLayout.LayoutParams(0,px(9),1));
            spacer(track,11);
            percent=label("",14,false,UiPalette.TEXT);
            track.addView(percent);
            track.setVisibility(View.INVISIBLE);
        }
        void bind(AboutDeviceInfo.Capacity capacity){
            value.setText(capacity.compact());
            track.setVisibility(capacity.valid()?View.VISIBLE:View.INVISIBLE);
            if(capacity.valid()){
                bar.setPercent(capacity.percent());
                percent.setText(capacity.percent()+"%");
            }
        }
    }

    private void runtime(){
        LinearLayout title=gridRow(0);
        title.addView(sectionHeading("运行状态"),new LinearLayout.LayoutParams(0,-2,1));
        title.addView(label("自动刷新",14,false,UiPalette.SECONDARY));
        LinearLayout columns=gridRow(16);
        columns.addView(label("当前应用",14,false,UiPalette.SECONDARY),new LinearLayout.LayoutParams(0,-2,42));
        columns.addView(label("内存占用",14,false,UiPalette.SECONDARY),new LinearLayout.LayoutParams(0,-2,26));
        columns.addView(label("占整机内存",14,false,UiPalette.SECONDARY),new LinearLayout.LayoutParams(0,-2,32));
        runtimeRows=ui.column();
        ScrollView appsViewport=createScroll(runtimeRows,"about-runtime-app-scroll");
        LinearLayout.LayoutParams listSize=new LinearLayout.LayoutParams(-1,0,1);
        listSize.topMargin=px(8);
        body.addView(appsViewport,listSize);
        AppRow desktop=new AppRow();
        desktop.packageName=ui.getPackageName();
        runtimeRows.addView(desktop);
        desktop.name.setText("Quiet TV 桌面");
        desktop.usage.setText("读取中…");
        tabs[RUNTIME].setNextFocusRightId(desktop.getId());
        note=label("",13,false,UiPalette.SECONDARY);
        note.setVisibility(View.GONE);
        add(body,note,12);
        readRuntime();
    }

    private void readRuntime(){
        if(!active)return;
        int request=++generation;
        worker.execute(()->{
            AboutRuntime.Snapshot info=AboutRuntime.read(ui.getApplicationContext());
            ui.runOnUiThread(()->{
                if(!active||generation!=request)return;
                bindRuntime(info);
                panel.postDelayed(refresh,AboutRuntime.REFRESH_MS);
            });
        });
    }

    private void bindRuntime(AboutRuntime.Snapshot info){
        if(renderedRuntime==info)return;
        renderedRuntime=info;
        // Sampling can reorder the list. Keep the focused package even when its rank changes.
        View focused=runtimeRows.findFocus();
        int focusedIndex=runtimeRows.indexOfChild(focused);
        String focusedPackage=focusedIndex<0?null:((AppRow)focused).packageName;
        int count=1+info.apps.size();
        View restored=null;
        for(int i=0;i<count;i++){
            AppRow row=(AppRow)runtimeRows.getChildAt(i);
            if(row==null){row=new AppRow();runtimeRows.addView(row);}
            AboutRuntime.App app=i==0?info.desktop:info.apps.get(i-1);
            row.bind(app,info.memory);
            View previous=i==0?row:runtimeRows.getChildAt(i-1);
            row.setNextFocusUpId(previous.getId());
            previous.setNextFocusDownId(row.getId());
            row.setNextFocusDownId(row.getId());
            if(app.packageName.equals(focusedPackage))restored=row;
        }
        if(focusedIndex>=0){
            if(restored==null)restored=runtimeRows.getChildAt(Math.min(focusedIndex,count-1));
            if(restored!=focused)restored.requestFocus();
        }
        int extra=runtimeRows.getChildCount()-count;
        if(extra>0)runtimeRows.removeViews(count,extra);
        note.setText(info.note);
        note.setVisibility(info.note.isEmpty()?View.GONE:View.VISIBLE);
    }

    private final class AppRow extends LinearLayout {
        final TextView name,usage,percent;
        final Bar bar;
        String packageName;
        AppRow(){
            super(ui);
            setId(ViewCompat.generateViewId());
            setFocusable(true);
            setNextFocusUpId(getId());
            setNextFocusDownId(getId());
            setNextFocusLeftId(tabs[RUNTIME].getId());
            setNextFocusRightId(getId());
            StateListDrawable background=new StateListDrawable();
            background.addState(new int[]{android.R.attr.state_focused,android.R.attr.state_window_focused},
                    ui.surface(0xff365a71,0xff294456,BLUE,8));
            setBackgroundDrawable(background);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(px(35));
            name=label("",15,false,UiPalette.TEXT);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            addView(name,new LinearLayout.LayoutParams(0,-2,42));
            usage=label("",15,false,UiPalette.TEXT);
            addView(usage,new LinearLayout.LayoutParams(0,-2,26));
            LinearLayout share=ui.row();
            share.setGravity(Gravity.CENTER_VERTICAL);
            addView(share,new LinearLayout.LayoutParams(0,-2,32));
            bar=new Bar();
            share.addView(bar,new LinearLayout.LayoutParams(0,px(7),1));
            spacer(share,8);
            percent=label("",14,false,UiPalette.TEXT);
            percent.setGravity(Gravity.RIGHT);
            share.addView(percent,new LinearLayout.LayoutParams(px(44),-2));
        }

        void bind(AboutRuntime.App app,AboutDeviceInfo.Capacity capacity){
            packageName=app.packageName;
            name.setText(app.name);
            boolean known=app.bytes>0;
            boolean showShare=known&&capacity.valid();
            usage.setText(known?AboutDeviceInfo.capacity(app.bytes):AboutDeviceInfo.UNAVAILABLE);
            percent.setText(showShare?String.format(Locale.ROOT,"%.1f%%",app.percent(capacity.total)):"");
            bar.setVisibility(showShare?View.VISIBLE:View.INVISIBLE);
            if(showShare)bar.setPercent(app.percent(capacity.total));
        }
    }

    private void source(){
        add(body,sectionHeading("开源信息"),0);
        View address=sourceRow("开源地址",AboutDeviceInfo.PROJECT.substring(8),"help-project",this::openProject,13);
        license=sourceRow("许可证","MIT 许可证","help-license",this::showLicense,3);
        sourceRow("致谢",AboutDeviceInfo.THANKS,null,null,3);
        tabs[SOURCE].setNextFocusRightId(address.getId());
        address.setNextFocusDownId(license.getId());
        address.setNextFocusUpId(close.getId());
        license.setNextFocusUpId(address.getId());
        license.setNextFocusDownId(license.getId());
    }

    private View sourceRow(String title,String value,String tag,Runnable action,int gap){
        LinearLayout row=ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView caption=label(title,17,false,UiPalette.SECONDARY);
        TextView detail=label(value,17,false,UiPalette.TEXT);
        row.addView(caption,new LinearLayout.LayoutParams(px(133),-2));
        row.addView(detail,new LinearLayout.LayoutParams(0,-2,1));
        row.setMinimumHeight(px(43));
        add(body,row,gap);
        if(action!=null){
            row.setId(ViewCompat.generateViewId());
            row.setTag(tag);
            row.setFocusableInTouchMode(true);
            row.setNextFocusLeftId(tabs[SOURCE].getId());
            row.setNextFocusRightId(row.getId());
            row.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,8,0,false,0));
            caption.setTextColor(SettingsFocusDrawable.textColors(UiPalette.SECONDARY));
            detail.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));
            caption.setDuplicateParentStateEnabled(true);
            detail.setDuplicateParentStateEnabled(true);
            row.setOnClickListener(view->KeyFeedback.clicked(view,action));
        }
        return row;
    }

    private void openProject(){
        try{ui.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(AboutDeviceInfo.PROJECT)));}
        catch(ActivityNotFoundException unavailable){ui.feedback("当前电视没有可用的浏览器");}
    }

    boolean licenseOpen(){return document!=null;}
    void showLicense(){
        if(document!=null||tab!=SOURCE)return;
        ListView list=new ListView(ui);
        document=list;
        list.setId(ViewCompat.generateViewId());
        list.setTag("help-license-document");
        list.setContentDescription("开源许可证与第三方声明");
        list.setDivider(null);
        list.setBackgroundColor(0xff141418);
        list.setFocusableInTouchMode(true);
        list.setNextFocusUpId(close.getId());
        list.setOnKeyListener((view,key,event)->{
            if(key!=KeyEvent.KEYCODE_DPAD_UP||list.getSelectedItemPosition()>1||list.getFirstVisiblePosition()>1)return false;
            if(event.getAction()==KeyEvent.ACTION_DOWN)close.requestFocus();
            return true;
        });
        ImageView attribution=new ImageView(ui);
        attribution.setImageDrawable(new TmdbLogoDrawable());
        attribution.setContentDescription("TMDB");
        attribution.setScaleType(ImageView.ScaleType.FIT_START);
        attribution.setPadding(px(24),px(10),0,px(8));
        attribution.setLayoutParams(new android.widget.AbsListView.LayoutParams(px(104),px(35)));
        list.addHeaderView(attribution,null,false);

        scroll.setVisibility(View.GONE);
        for(Button button:tabs)button.setFocusable(false);
        panel.addView(list,new FrameLayout.LayoutParams(-1,-1));
        close.setText("返回");
        close.setContentDescription("返回");
        close.setNextFocusDownId(list.getId());
        close.setNextFocusLeftId(list.getId());
        list.requestFocusFromTouch();
        worker.execute(()->{
            String[] paragraphs;
            try{paragraphs=AboutLicenses.read(ui.getApplicationContext()).split("\\n{2,}");}
            catch(IOException unavailable){paragraphs=new String[]{"许可证暂不可用"};}
            final String[] lines=paragraphs;
            ui.runOnUiThread(()->{
                if(document!=list)return;
                list.setAdapter(new ArrayAdapter<String>(ui,android.R.layout.simple_list_item_1,lines){
                    @Override public View getView(int position,View recycled,ViewGroup parent){
                        TextView text=recycled instanceof TextView?(TextView)recycled:label("",14,false,UiPalette.TEXT);
                        text.setPadding(px(24),px(7),px(24),px(7));
                        text.setText(getItem(position));
                        return text;
                    }
                });
            });
        });
    }

    void backFromLicense(){
        disposeDocument();
        scroll.setVisibility(View.VISIBLE);
        for(Button button:tabs)button.setFocusableInTouchMode(true);
        close.setText("关闭");
        close.setContentDescription("关闭");
        close.setNextFocusDownId(tabs[SOURCE].getId());
        close.setNextFocusLeftId(tabs[SOURCE].getId());
        license.requestFocusFromTouch();
    }

    void detach(){
        pause();
        disposeDocument();
    }

    private void disposeDocument(){
        if(document==null)return;
        panel.removeView(document);
        document=null;
    }

    private final class Bar extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect=new RectF();
        private double percent;
        Bar(){super(ui);ViewCompat.setImportantForAccessibility(this,ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void setPercent(double value){percent=Math.max(0,Math.min(100,value));invalidate();}
        @Override protected void onDraw(Canvas canvas){
            float h=getHeight(),w=getWidth();
            rect.set(0,0,w,h);
            paint.setColor(0x55a3a8b2);
            canvas.drawRoundRect(rect,h/2,h/2,paint);
            if(percent>0){
                rect.right=(float)(w*percent/100d);
                paint.setColor(BLUE);
                canvas.drawRoundRect(rect,h/2,h/2,paint);
            }
        }
    }
}
