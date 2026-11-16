package home.quiet.tv;

import org.json.*;
import java.util.regex.*;

/** Evidence counts, never inferred root causes or a claim that missing events mean success. */
final class DiagnosticFindings {
    private static final Pattern RESULT=Pattern.compile("(?:^| )result=(COMPLETED|ACCEPTED|SAVED|FAILED|UNKNOWN|REJECTED|CANCELLED|ROLLED_BACK)(?: |$)");
    static JSONObject summarize(JSONArray events)throws JSONException{
        JSONObject counts=new JSONObject();
        for(int i=0;i<events.length();i++){JSONObject event=events.getJSONObject(i);String name=event.optString("event");if(!name.endsWith(".result"))continue;Matcher result=RESULT.matcher(event.optString("detail"));if(!result.find())continue;
            String channel=name.substring(0,name.indexOf('.'));JSONObject values=counts.optJSONObject(channel);if(values==null){values=new JSONObject();counts.put(channel,values);}String key=result.group(1);values.put(key,values.optInt(key)+1);
        }
        return new JSONObject().put("resultCounts",counts).put("scope","Exported recent events only; counts exclude omissions. No result is unknown, never success. Callbacks do not verify the physical screen.");
    }
    private DiagnosticFindings(){}
}
