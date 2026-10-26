package home.quiet.tv;

import android.app.Activity;
import android.content.*;
import android.os.Build;
import android.provider.Settings;
import org.json.JSONObject;

/** Explicit, permission-protected computer setup; never changes the system HOME or stock apps. */
public final class HomeGuardSetupReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        setResultCode(Activity.RESULT_CANCELED);
        if(intent==null || !(context.getPackageName()+".CONFIGURE_HOME_GUARD").equals(intent.getAction()))return;
        HomeGuardState state=new HomeGuardState(context,false);
        try{
            if(intent.getIntExtra("user",-1)!=android.os.Process.myUid()/100000)throw new IllegalStateException("目标用户不一致");
            if(intent.getBooleanExtra("inspect",false)){
                org.json.JSONArray candidates=new org.json.JSONArray();
                for(HomeGuardState.Candidate candidate:state.candidates())candidates.put(candidate.component.flattenToString());
                android.content.pm.ResolveInfo resolved=context.getPackageManager().resolveActivity(HomeController.homeIntent(),android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
                String resolvedHome=resolved==null || resolved.activityInfo==null?"":new ComponentName(resolved.activityInfo.packageName,resolved.activityInfo.name).flattenToString();
                setResultData(new JSONObject().put("schemaVersion",1).put("package",context.getPackageName())
                    .put("supported",state.supported()).put("selected",state.selected()).put("candidates",candidates).put("resolvedHome",resolvedHome)
                    .put("usageRequired",HomeGuardService.usageRequired(context)).put("accessibilityAllowed",state.serviceEnabled())
                    .put("accessibilityBound",HomeGuardService.connected()).put("physicalHomeVerified",false).toString());
                setResultCode(Activity.RESULT_OK);return;
            }
            if(!state.supported() || !HomeGuardState.unlocked(context))throw new IllegalStateException("系统档位不支持或尚未解锁");
            state=new HomeGuardState(context);
            if(intent.getBooleanExtra("pause",false)){
                state.pause("电脑暂停中转");setResultData(new DesktopRoutes(context).snapshot().toString());if(!state.preferences.getBoolean("proxyPausePending",false))setResultCode(Activity.RESULT_OK);return;
            }
            if(!HomeGuardRecovery.writeAllowed(context))throw new IllegalStateException("请先通过电脑授予写安全设置权限");
            if(!intent.getBooleanExtra("enable",false))throw new IllegalStateException("未明确请求启用守护");
            // Validate the exact on-device candidate before changing a paused configuration.
            String home=intent.getStringExtra("home");
            if(home!=null){
                ComponentName selected=ComponentName.unflattenFromString(home);boolean found=false;
                for(HomeGuardState.Candidate candidate:state.candidates())if(candidate.component.equals(selected)){found=true;break;}
                if(!found)throw new IllegalStateException("原主页不是本机有效候选");
                state.select(selected);
            }else state.configure();
            if(!state.validSelection())throw new IllegalStateException("请在电视选择原桌面，或指定本机检测到的 home 组件");
            if(state.helperActive())throw new IllegalStateException("请先暂停独立中转助手");
            state.enable();HomeGuardRecovery.configureGranted(context);
            if(intent.hasExtra("keep_adb") && !HomeGuardAdb.setEnabled(context,intent.getBooleanExtra("keep_adb",false)))throw new IllegalStateException("调试恢复选项未保存");
            String problem=HomeGuardRecovery.configurationProblem(context);
            JSONObject result=new JSONObject();
            result.put("configured",problem.isEmpty());result.put("enabled",state.enabled());
            result.put("physicalHomeVerified",false);
            result.put("accessibilityAllowed",state.serviceEnabled());result.put("accessibilityBound",HomeGuardService.connected());
            result.put("usageRequired",HomeGuardService.usageRequired(context));result.put("usageAllowed",HomeGuardService.usageAllowed(context));
            result.put("notificationSupported",HomeGuardRecovery.notificationSupported(context));
            result.put("syncRecoveryReady",HomeGuardSync.ready(context));
            result.put("notificationAllowed",HomeGuardRecovery.notificationAllowed(context));result.put("notificationBound",HomeGuardRevivalListener.connected());
            result.put("syncSupported",HomeGuardSync.supported(context));result.put("syncReady",HomeGuardSync.ready(context));result.put("syncStatus",HomeGuardSync.summary(context));result.put("keepAdb",HomeGuardAdb.enabled(context));
            result.put("overlayAllowed",Build.VERSION.SDK_INT<23 || Settings.canDrawOverlays(context));
            result.put("problem",problem);
            result.put("desktopRoutes",new DesktopRoutes(context).snapshot());setResultData(result.toString());
            if(problem.isEmpty())setResultCode(Activity.RESULT_OK);
            DiagnosticLog.info("home_guard.computer_setup",problem.isEmpty()?"权限已配置，接管待验证":"部分权限未配置");
        }catch(Exception failure){setResultData("配置未完成："+failure.getMessage());}
    }
}
