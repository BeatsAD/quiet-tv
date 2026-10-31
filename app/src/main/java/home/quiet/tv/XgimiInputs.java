package home.quiet.tv;

import android.content.*;
import android.media.tv.TvInputInfo;
import android.os.Build;
import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Pre-Android-9 native player; ports come from product config, not MStar's generic enum.
 * Protocol: Aerial 587a2849, XgimiMstarSources.kt (MIT; THIRD_PARTY_NOTICES.md). */
@android.annotation.TargetApi(21)
final class XgimiInputs {
    static final ComponentName PLAYER=new ComponentName("com.xgimi.xhplayer","com.xgimi.xhplayer.InputActivity");
    static List<VendorTvInputs.Source> load(Context context) throws Exception {
        if(Build.VERSION.SDK_INT>=28||!TvInputController.canOpen(context,PLAYER))return Collections.emptyList();
        Class<?> type=Class.forName("com.xgimi.clients.GimiConfigManager");
        Object manager=type.getMethod("getInstance").invoke(null);
        Object config=type.getMethod("getXgimiConfig",String.class).invoke(manager,"global");
        return config instanceof String?parse((String)config):Collections.emptyList();
    }
    static List<VendorTvInputs.Source> parse(String raw) throws JSONException {
        ArrayList<VendorTvInputs.Source> result=new ArrayList<>();JSONArray signals=new JSONObject(raw).optJSONArray("signal");
        if(signals==null)return result;
        Pattern pattern=Pattern.compile("HDMI([1-9][0-9]*)?");Set<String> seen=new HashSet<>();
        for(int n=0;n<signals.length();n++){
            JSONObject item=signals.optJSONObject(n);if(item==null)continue;
            Matcher match=pattern.matcher(item.optString("sw").trim().toUpperCase(Locale.ROOT));if(!match.matches())continue;
            int port;try{port=match.group(1)==null?1:Integer.parseInt(match.group(1));}catch(NumberFormatException invalid){continue;}
            String name="HDMI"+port;if(!seen.add(name))continue;
            String label=item.optString("name").trim();if(label.isEmpty()||"HDMI".equalsIgnoreCase(label))label="HDMI "+port;
            Intent intent=new Intent("com.xgimi.action.hdmiPlayer").setComponent(PLAYER).putExtra("launcher_start_source",name);
            result.add(new VendorTvInputs.Source(label,intent,null,"xgimi:"+name,PLAYER.getPackageName(),TvInputInfo.TYPE_HDMI));
        }
        return result;
    }
}
