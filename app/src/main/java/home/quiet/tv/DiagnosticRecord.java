package home.quiet.tv;

import java.util.regex.*;
import org.json.*;

/** Parse only the known journal envelope; legacy events retain unknown historical identity. */
final class DiagnosticRecord {
    private static final Pattern LINE=Pattern.compile("^(\\d{1,16}) (INFO|WARN|ERROR) ([A-Za-z0-9_.-]+) ?(.*)$");
    static JSONObject parse(String line)throws JSONException{
        Matcher match=LINE.matcher(line);if(!match.matches())return null;
        JSONObject value=new JSONObject().put("timestampMs",Long.parseLong(match.group(1))).put("level",match.group(2)).put("event",match.group(3)).put("detail",match.group(4));
        String session=field(match.group(4),"diagSession","[a-f0-9]{16}");
        value.put("legacyUnscoped",session.isEmpty()).put("processSession",session.isEmpty()?JSONObject.NULL:session);
        for(String key:new String[]{"diagBoot","diagBuild","diagSource","diagElapsed","diagPid","originRole","originSession","originBuild","originSource","originPid","originWallMs","originElapsedMs"}){
            String found=field(match.group(4),key,"[A-Za-z0-9_-]+");value.put(key,found.isEmpty()?JSONObject.NULL:found);
        }
        return value;
    }
    private static String field(String detail,String name,String expression){Matcher match=Pattern.compile("(?:^| )"+name+"=("+expression+")(?: |$)").matcher(detail);return match.find()?match.group(1):"";}
    private DiagnosticRecord(){}
}
