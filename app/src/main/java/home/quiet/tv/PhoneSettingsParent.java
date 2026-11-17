package home.quiet.tv;

import android.content.Context;
import android.os.SystemClock;
import java.io.*;
import org.json.*;

/** Short-lived authority is private to this connected browser generation, never a stored web PIN. */
final class PhoneSettingsParent {
    static final class Grant {final String stamp;final long expires;Grant(String stamp,long now){this.stamp=stamp;expires=now+5*60_000L;}boolean valid(String current,long now){return now<expires&&!stamp.isEmpty()&&stamp.equals(current);}}
    private final Context context;private volatile Grant grant;private volatile PhoneSettingsServer.Active connection;
    PhoneSettingsParent(Context context){this.context=context;}
    void end(){connection=null;grant=null;}
    boolean authorized(){ChildModeStore store=new ChildModeStore(context);Grant current=grant;PhoneSettingsServer.Active connected=connection;return connected!=null&&connected.valid()&&current!=null&&current.valid(store.credential(),SystemClock.elapsedRealtime());}
    void require(PhoneSettingsServer.Active active)throws IOException{if(!active.valid())throw new PhoneSettingsServer.Ended();if(!authorized())throw new PhoneSettingsServer.Rejected("请先完成家长验证");}
    JSONObject state()throws JSONException{ChildModeStore store=new ChildModeStore(context);return new JSONObject().put("configured",store.configured()).put("authorized",authorized()).put("retrySeconds",Math.max(0,(store.retryAfter()-SystemClock.elapsedRealtime()+999)/1000));}
    JSONObject execute(JSONObject value,PhoneSettingsServer.Active active)throws IOException{
        DiagnosticOperation trace=new DiagnosticOperation("child.parent","source=phone");
        try{
            if(value.length()!=3||!(value.opt("action") instanceof String)||!(value.opt("pin") instanceof String)||!(value.opt("newPin") instanceof String))throw new PhoneSettingsServer.BadInput();String action=value.getString("action"),pin=value.getString("pin"),newPin=value.getString("newPin");
            ChildModeStore store=new ChildModeStore(context);if(!active.valid())throw new PhoneSettingsServer.Ended();if("recovery".equals(action)){synchronized(ChildModeStore.LOCK){require(active);trace.finish("COMPLETED","recovery_requested");return new JSONObject().put("recovery",store.recovery());}}
            if(!java.util.Arrays.asList("setup","verify","change","recover").contains(action))throw new PhoneSettingsServer.BadInput();trace.stage(action.toUpperCase(java.util.Locale.ROOT));String before=store.credential();
            if(store.retryAfter()>SystemClock.elapsedRealtime())throw new PhoneSettingsServer.Rejected("输入次数过多，请稍后重试");
            boolean valid;
            if(action.equals("setup")){if(store.configured())throw new PhoneSettingsServer.Rejected("家长密码已设置，请验证当前密码");valid=true;}
            else if(action.equals("change")){require(active);valid=true;}
            else if(action.equals("recover"))valid=store.verifyRecovery(pin);
            else{if(!store.configured())throw new PhoneSettingsServer.Rejected("请先设置家长密码");valid=ChildModeCredentials.verify(pin,before);}
            String prepared=null;if(valid&&!action.equals("verify")){if(!ChildModeCredentials.numeric(newPin))throw new PhoneSettingsServer.Rejected("请输入 6 位数字的新密码");prepared=ChildModeCredentials.prepare(newPin);}
            synchronized(ChildModeStore.LOCK){
                if(!active.valid())throw new PhoneSettingsServer.Ended();if(!before.equals(store.credential()))throw new PhoneSettingsServer.Rejected("家长凭据已变化，请重新验证");
                if(!valid){trace.finish("REJECTED","credential_mismatch");store.failed();throw new PhoneSettingsServer.Rejected(action.equals("recover")?"恢复码不正确":"密码不正确，请重试");}
                if(action.equals("change"))require(active);if(prepared!=null&&!store.saveCredential(prepared,newPin))throw new IOException("家长密码保存未确认，请验证新密码后核对");store.verified();if(prepared!=null)ChildUsageService.changed();connection=active;grant=new Grant(store.credential(),SystemClock.elapsedRealtime());
                trace.finish("COMPLETED","authorized");JSONObject result=state();if(action.equals("setup"))result.put("recovery",store.recovery());return result;
            }
        }catch(IOException e){trace.finish("UNKNOWN",e.getClass().getSimpleName());throw e;}catch(JSONException e){trace.finish("REJECTED","invalid_request");throw new PhoneSettingsServer.BadInput();}catch(IllegalStateException e){trace.finish("FAILED","credential_unavailable");throw new PhoneSettingsServer.Rejected("家长凭据无法读取，请使用恢复码重设");}
    }
}
