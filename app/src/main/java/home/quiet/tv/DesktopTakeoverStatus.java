package home.quiet.tv;

import android.content.Context;
import org.json.JSONObject;

/** A read-only presentation of existing routes and user scene evidence, never another saved state. */
final class DesktopTakeoverStatus {
    final String code,title,next;
    private DesktopTakeoverStatus(String code,String title,String next){this.code=code;this.title=title;this.next=next;}
    static DesktopTakeoverStatus read(Context context){
        try{
            HomeGuardState guard=new HomeGuardState(context,false);
            if(!HomeGuardState.unlocked(context))return new DesktopTakeoverStatus("incomplete","等待系统解锁","解锁后重新检查桌面接管状态。");
            if(guard.preferences.getBoolean("proxyPausePending",false))return repair("暂停尚未确认，请通过电脑继续恢复。");
            if("OFF".equals(guard.preferences.getString("mode","AUTO")))return new DesktopTakeoverStatus("paused","已暂停","需要继续接管时，通过电脑恢复原有配置。");
            if(LegacyWindowHomeGuard.configured(context)){
                if(!LegacyWindowHomeGuard.enabled(context))return repair("窗口守护配置已变化，请通过电脑核对固件及原桌面版本。");
                if(!LegacyWindowHomeGuard.authorized(context) || !LegacyWindowHomeGuardService.connected())return repair("窗口守护无障碍尚未连接，请通过电脑检查。");
                return new DesktopTakeoverStatus("pending","窗口守护待验证","保留系统原桌面；实体 HOME、开机和待机唤醒须分别验证。");
            }
            if(LegacyAdbHomeGuard.configured(context)){
                if(!LegacyAdbHomeGuard.enabled(context))return repair("ADB 守护配置已变化，请通过电脑核对固件及原桌面。");
                return new DesktopTakeoverStatus("pending","本机 ADB 守护待验证",LegacyAdbHomeGuardService.status()+"；重启后须确认本机 ADB 端口仍可连接。");
            }
            JSONObject record=new SetupStore(context).get().optJSONObject("takeover");
            if(record!=null && !java.util.Arrays.asList("applied","restored","rolled-back").contains(record.optString("state")))
                return new DesktopTakeoverStatus("incomplete","配置尚未完成","请通过电脑继续原任务，核对恢复记录。");
            boolean own=new HomeController(context).inspect().isOwn(context);
            if(!guard.preferences.getBoolean("configured",false)){
                if(own)return new DesktopTakeoverStatus("pending","已选择默认桌面，待验证","请通过电脑记录实体 HOME、重启、断电开机和待机唤醒结果。");
                if(!guard.supported() && !BuildConfig.DECLARES_HOME)return limited();
                return new DesktopTakeoverStatus("unconfigured","尚未配置","可先选择系统默认桌面；没有此入口时，通过电脑配置。");
            }
            if(!guard.supported())return own?new DesktopTakeoverStatus("pending","已选择默认桌面，待验证","此系统的守护能力受限，请通过电脑核对实体 HOME 和开机结果。"):limited();
            DesktopRoutes routes=new DesktopRoutes(context,false);
            if(routes.proxyUpgradePending())return new DesktopTakeoverStatus("repair","配置已保留，接管待恢复",guard.preferences.getString("status","请通过电脑继续恢复原有 HOME 代理。"));
            String problem=routes.configurationProblem();
            if(!problem.isEmpty())return repair("请通过电脑检查并修复现有配置。");
            boolean passed=true;
            for(String scene:new String[]{"home","reboot","coldBoot","wake"}){
                String result=routes.test(scene);
                if("失败".equals(result))return repair("已有场景验证失败，请通过电脑修复并重新验证。");
                passed&="通过".equals(result) && guard.preferences.getLong("routeTestAt_"+scene,0)>0
                    && !guard.preferences.getString("routeTestEvidence_"+scene,"").isEmpty();
            }
            return passed?new DesktopTakeoverStatus("verified","已验证可用","实体 HOME、重启、断电开机和待机唤醒均有当前配置的确认记录。")
                :new DesktopTakeoverStatus("pending","配置已完成，开机待验证","请通过电脑补齐实体 HOME、重启、断电开机和待机唤醒验证。");
        }catch(Exception unavailable){return repair("无法核对当前配置，请通过电脑检查。");}
    }
    private static DesktopTakeoverStatus repair(String next){return new DesktopTakeoverStatus("repair","需要修复",next);}
    private static DesktopTakeoverStatus limited(){return new DesktopTakeoverStatus("limited","此机型能力受限","请通过电脑确认此固件支持的桌面接管方式。");}
    JSONObject snapshot() throws org.json.JSONException {return new JSONObject().put("code",code).put("title",title).put("next",next).put("evidence","route_and_user_scenes");}
}
