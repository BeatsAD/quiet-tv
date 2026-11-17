package home.quiet.tv;

import android.view.*;
import android.widget.*;
import home.quiet.components.ComponentRepository;
import org.json.JSONObject;

/** Uses the existing native settings rows and sheets; actions are always explicit. */
final class OptionalComponentsUi {
    static void missing(AppManagementActivity ui){show(ui,"weather");}
    static String weatherNotice(ManagementUi ui){return ui.tr("实时天气会占用更多内存和图形性能。\n仅建议高性能电视开启，老旧或低配置电视建议使用静态壁纸。\n最低要求：安卓 5.0 及以上，支持 OpenGL ES 3.0。系统版本达标不代表运行流畅。","Live weather uses more memory and graphics processing.\nRecommended only for high-performance TVs. Use a static wallpaper on older or lower-spec TVs.\nRequires Android 5.0 or later and OpenGL ES 3.0. Meeting the OS requirement does not guarantee smooth performance.");}
    static String label(JSONObject s){
        if(s.optString("state").equals("removing"))return "正在卸载";if(s.optString("state").equals("remove_failed"))return "卸载未完成";
        if(!s.optBoolean("supported"))return s.optString("reason");
        if(s.optBoolean("updatePending"))return "重启应用后生效";
        switch(s.optString("state")){case "checking":return "读取组件信息";case "downloading":return "正在下载";case "verifying":return "正在校验";case "ready":return "已下载";case "failed":return "下载未完成";case "cancelled":return "已取消";default:return s.optString("message").isEmpty()?"未下载":"下载未完成";}
    }
    static String detail(ManagementUi ui,String id,JSONObject s){
        if(s.optString("state").equals("removing")||s.optString("state").equals("remove_failed"))return s.optString("message");
        if(!s.optBoolean("supported")||s.optBoolean("updatePending"))return "";
        String status=s.optString("state"),message=s.optString("message"),error=message.toLowerCase(java.util.Locale.ROOT);
        if(java.util.Arrays.asList("checking","downloading","verifying").contains(status))return message.contains("切换")||message.contains("重试")||message.contains("继续")?message:"";
        if(error.contains("timeout")||error.contains("timed out")||error.contains("failed to connect"))return ui.tr("网络连接超时，请稍后重试","The connection timed out. Please try again.");
        if(error.contains("unable to resolve")||error.contains("unknownhost"))return ui.tr("无法连接下载服务，请检查网络","Cannot reach the download service. Check your connection.");
        if(error.contains("ssl")||error.contains("certificate")||error.contains("trust anchor"))return ui.tr("无法建立安全连接，请检查电视时间和网络","Cannot establish a secure connection. Check the TV clock and network.");
        if(!message.isEmpty()&&!status.equals("cancelled"))return message.matches("(?s).*[\\u4e00-\\u9fff].*")?message:ui.tr("下载失败，请检查网络后重试","Download failed. Check your connection and try again.");
        return id.equals("weather")?ui.tr("下载完成后，选择“随天气变化”","After downloading, select Follow weather."):ui.tr("组件就绪后继续登录与绑定","Sign in and bind after downloading.");
    }
    static ManagementUi.Sheet show(AppManagementActivity ui,String id){
        android.view.View origin=ui.getCurrentFocus();ComponentRepository repository=ComponentRepository.get(ui);String title=id.equals("weather")?ui.tr("动态天气壁纸组件","Dynamic weather wallpaper component"):"Tailscale 组网";
        ManagementUi.Sheet sheet=ui.new Sheet(title);Button close=sheet.settingsLayout(ui.tr("关闭","Close"),sheet.dialog::dismiss);sheet.settingsActions(true);
        sheet.body.setPadding(0,ui.px(14.4f),0,0);
        TextView state=ui.label("",16),message=ui.muted("",13);state.setTag("component-state");message.setTag("component-message");message.setLineSpacing(ui.px(4),1);sheet.body.addView(state);ui.gap(sheet.body,8);sheet.body.addView(message);
        if(id.equals("weather")){ui.gap(sheet.body,12);TextView notice=ui.muted(weatherNotice(ui),13);notice.setTag("component-requirements");notice.setLineSpacing(ui.px(4),1);sheet.body.addView(notice);}
        ProgressBar progress=new ProgressBar(ui,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setTag("component-progress");LinearLayout.LayoutParams progressSize=new LinearLayout.LayoutParams(-1,ui.px(5));progressSize.topMargin=ui.px(14);sheet.body.addView(progress,progressSize);
        Button use=sheet.action(id.equals("weather")?ui.tr("卸载","Uninstall"):ui.tr("继续组网设置","Continue remote setup"),()->{if(id.equals("weather")){confirmUninstall(ui);return;}sheet.dialog.dismiss();if(android.os.Build.VERSION.SDK_INT>=26)TvRemoteController.show(ui);});
        Button action=sheet.action("",()->{try{JSONObject s=repository.state(id);String status=s.optString("state");if(java.util.Arrays.asList("checking","downloading","verifying").contains(status))repository.cancel(id);else repository.start(id);}catch(java.io.IOException e){ui.message(e.getMessage());}});
        sheet.actions.getLayoutParams().height=ui.px(37);
        for(Button button:new Button[]{use,action}){button.setId(androidx.core.view.ViewCompat.generateViewId());button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,13.824f*ui.unit);LinearLayout.LayoutParams size=(LinearLayout.LayoutParams)button.getLayoutParams();size.width=0;size.weight=1;size.leftMargin=button==use?0:ui.px(10.24f);button.setLayoutParams(size);button.setNextFocusUpId(close.getId());button.setNextFocusDownId(button.getId());}
        use.setTag(id.equals("weather")?"component-uninstall":"component-return");action.setTag("component-action");action.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,999,UiPalette.SELECTED,false,UiPalette.EDGE));
        use.setNextFocusLeftId(use.getId());use.setNextFocusRightId(action.getId());action.setNextFocusLeftId(use.getId());action.setNextFocusRightId(action.getId());close.setNextFocusDownId(action.getId());
        ComponentRepository.Listener listener=()->{
            JSONObject s=repository.state(id);boolean busy=java.util.Arrays.asList("checking","downloading","verifying").contains(s.optString("state"));long total=s.optLong("totalBytes"),done=s.optLong("downloadedBytes");
            state.setText(label(s)+(busy&&total>0?"  "+android.text.format.Formatter.formatShortFileSize(ui,done)+" / "+android.text.format.Formatter.formatShortFileSize(ui,total):""));
            OperationFeedback.status(state,busy||s.optString("state").equals("removing")?OperationFeedback.Kind.PROGRESS:s.optBoolean("ready")?OperationFeedback.Kind.SUCCESS:s.optString("state").equals("failed")||s.optString("state").equals("remove_failed")?OperationFeedback.Kind.ERROR:OperationFeedback.Kind.INFO);message.setText(detail(ui,id,s));message.setVisibility(message.length()==0?View.GONE:View.VISIBLE);
            boolean removing=s.optString("state").equals("removing");progress.setVisibility(busy||removing?android.view.View.VISIBLE:android.view.View.GONE);progress.setIndeterminate(removing||total==0);if(total>0)progress.setProgress((int)(100*done/total));
            boolean retry=s.optString("state").equals("failed")||s.optString("state").equals("cancelled")||s.optString("state").equals("missing")&&!s.optString("message").isEmpty();
            action.setEnabled(s.optBoolean("supported")&&!removing&&!s.optString("state").equals("remove_failed"));action.setText(busy?ui.tr("取消下载","Cancel download"):s.optBoolean("ready")?ui.tr("检查组件更新","Check component update"):retry?ui.tr("重试下载","Retry download"):id.equals("weather")?ui.tr("下载天气资源","Download weather resources"):ui.tr("下载组网组件","Download remote component"));
            use.setEnabled(id.equals("weather")?s.optBoolean("canUninstall")&&!busy&&!removing:s.optBoolean("ready")&&!s.optBoolean("updatePending"));
            use.setNextFocusRightId(action.isEnabled()?action.getId():close.getId());action.setNextFocusLeftId(use.isEnabled()?use.getId():action.getId());close.setNextFocusDownId(action.isEnabled()?action.getId():use.isEnabled()?use.getId():close.getId());
            if(use.hasFocus()&&!use.isEnabled())(action.isEnabled()?action:close).requestFocus();
        };
        sheet.dialog.setOnDismissListener(d->{repository.remove(listener);if(!ui.isFinishing()&&!UiCompat.destroyed(ui)){android.view.View returned=origin!=null&&origin.isShown()?origin:id.equals("weather")?ui.stage.findViewWithTag("appearance-wall-mode"):null;if(returned!=null&&returned.isShown())returned.requestFocusFromTouch();}});repository.add(listener);sheet.show(action.isEnabled()?action:use.isEnabled()?use:close);return sheet;
    }
    private static void confirmUninstall(AppManagementActivity ui){
        ManagementUi.Sheet confirm=ui.new Sheet(ui.tr("卸载天气资源？","Uninstall weather resources?"));
        confirm.settingsLayout(ui.tr("关闭","Close"),confirm.dialog::dismiss);confirm.settingsActions(true);
        confirm.body.addView(ui.label(ui.tr("移除已下载的动态天气资源。正在使用时将切回静态壁纸，天气设置保留。","Remove downloaded weather resources. An active weather wallpaper switches to a static background. Weather settings are retained."),16));
        Button cancel=confirm.action(ui.tr("取消","Cancel"),confirm.dialog::dismiss);
        confirm.action(ui.tr("卸载","Uninstall"),()->{try{WeatherComponentActions.uninstall(ui);confirm.dialog.dismiss();}catch(java.io.IOException error){ui.message(error.getMessage());}});
        confirm.show(cancel);
    }

}
