package home.quiet.tv;

import android.content.Context;
import org.json.JSONObject;

/** A read-only presentation of existing routes and user scene evidence, never another saved state. */
final class DesktopTakeoverStatus {
    final String code,title,next;
    private DesktopTakeoverStatus(String code,String title,String next){this.code=code;this.title=title;this.next=next;}
    static DesktopTakeoverStatus read(Context context){
        JSONObject snapshot=DesktopSwitch.get(context).state(),guard=snapshot.optJSONObject("guard"),desktop=snapshot.optJSONObject("mainDesktop");
        if(guard==null)return repair("无法核对当前配置，请检查桌面管理。");
        String state=guard.optString("state"),code=state.equals("paused")?"paused":state.equals("unconfigured")?"unconfigured":state.equals("review")||state.equals("authorization")||state.equals("disconnected")?"repair":"pending";
        return new DesktopTakeoverStatus(code,snapshot.optString("summary","状态待核对"),desktop==null?guard.optString("reason"):desktop.optString("hint"));
    }
    private static DesktopTakeoverStatus repair(String next){return new DesktopTakeoverStatus("repair","需要修复",next);}
    private static DesktopTakeoverStatus limited(){return new DesktopTakeoverStatus("limited","此机型能力受限","请通过电脑确认此固件支持的桌面接管方式。");}
    JSONObject snapshot() throws org.json.JSONException {return new JSONObject().put("code",code).put("title",title).put("next",next).put("evidence","route_and_user_scenes");}
}
