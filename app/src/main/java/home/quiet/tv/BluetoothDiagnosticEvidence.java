package home.quiet.tv;

import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/** Export diagnostic fields, never arbitrary system messages, device names or pairing secrets. */
final class BluetoothDiagnosticEvidence {
    private static final Pattern ADDRESS=Pattern.compile("(?i)(?<![0-9a-f])(?:[0-9a-f]{2}:){5}[0-9a-f]{2}(?![0-9a-f])");
    private static final Pattern LOG=Pattern.compile("^\\s*(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+)\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEF])\\s+([^:]+):\\s*(.*)$");
    private static final Pattern FIELD=Pattern.compile("(?i)\\b(status|reason|state|newState|oldState|prevState|previousState|bondState|connectionState|enabled|connected|pid|uid|deviceId|sources?|keyCode|scanCode|action|errorCode|hci_status|new_state|prev_state|mState|mConnectionState|capabilities|flags)\\s*[:=]\\s*(true\\b|false\\b|0x[0-9a-f]{1,8}\\b|-?[0-9]{1,9}\\b)");
    private static final Pattern DEVICE=Pattern.compile("(?i)^\\s*Device\\s+(\\d{1,6})\\s*:");
    private static final Pattern QUOTED=Pattern.compile("\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'");
    private static final Pattern LABEL=Pattern.compile("(?i)\\b(?:name|alias|deviceName|remoteName|localName)\\s*[:=]\\s*[^,;\\r\\n]*");
    private static final Pattern SERVICE=Pattern.compile("(?:\\bid\\s*=|\\bcomponent\\s*[:=]|ComponentInfo\\{|mComponentName\\s*=)\\s*([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+){1,12}/[A-Za-z_.$][A-Za-z0-9_.$]{0,160})");
    private static final Pattern CLASS=Pattern.compile("\\b(?:com\\.android\\.(?:bluetooth|server)|android\\.bluetooth|com\\.xiaomi\\.mitv|com\\.mitv\\.tvhome|home\\.quiet\\.tv|java\\.lang)(?:\\.[A-Za-z_$][A-Za-z0-9_$]*){0,12}\\b");
    private static final String[] MARKERS={"BOND_NONE","BOND_BONDING","BOND_BONDED","STATE_DISCONNECTED","STATE_CONNECTING","STATE_CONNECTED","STATE_DISCONNECTING","STATE_TURNING_ON","STATE_TURNING_OFF","STATE_ON","STATE_OFF","DISCONNECTED","CONNECTING","CONNECTED","DISCONNECTING","ON","OFF","bondStateChangeCallback","createBond","removeBond","cancelBond","AUTH_FAILED","AUTH_FAILURE","AUTH_TIMEOUT","PIN_OR_KEY_MISSING","authentication failed","authentication timeout","connection timeout","FATAL EXCEPTION","ANR","Killing","Force stopping","HidHostService","HidHostStateMachine","BleRemoteService","InputReader","InputDispatcher","EventHub","Device added","Device removed"};
    private static final Set<String> TAGS=new HashSet<>(Arrays.asList("BluetoothManagerService","BluetoothAdapter","BluetoothDevice","BluetoothBondStateMachine","BondStateMachine","AdapterService","BluetoothHidHost","HidHostService","HidHostStateMachine","BleRemoteService","bt_btif","bt_btm","bt_hh","bt_btif_dm","bt_bta_dm","InputReader","InputDispatcher","EventHub","ActivityManager","ActivityTaskManager","PackageManager","AndroidRuntime","AccessibilityManagerService"));
    private static final Pattern[] MARKER_PATTERNS=markerPatterns();
    private static Pattern[] markerPatterns(){Pattern[] values=new Pattern[MARKERS.length];for(int i=0;i<values.length;i++)values[i]=Pattern.compile("(?i)(?<![a-z0-9_])"+Pattern.quote(MARKERS[i])+"(?![a-z0-9_])");return values;}
    static final class Result {
        final List<String> lines=new ArrayList<>();
        int relevant,unparsed,omitted;
    }
    static String device(String salt,String address){
        if(address==null||!ADDRESS.matcher(address).matches())return "unknown";
        try{
            byte[] hash=MessageDigest.getInstance("SHA-256").digest((salt+":"+address.toUpperCase(Locale.ROOT)).getBytes("UTF-8"));
            StringBuilder id=new StringBuilder("bt-");for(int i=0;i<6;i++)id.append(String.format(Locale.ROOT,"%02x",hash[i]&255));return id.toString();
        }catch(java.io.UnsupportedEncodingException|java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    static String bond(int value){return value==10?"NONE":value==11?"BONDING":value==12?"BONDED":"UNKNOWN("+value+")";}
    static String adapter(int value){return value==10?"OFF":value==11?"TURNING_ON":value==12?"ON":value==13?"TURNING_OFF":"UNKNOWN("+value+")";}
    static String connection(int value){return value==0?"DISCONNECTED":value==1?"CONNECTING":value==2?"CONNECTED":value==3?"DISCONNECTING":"UNKNOWN("+value+")";}
    static String queryFailure(String output){
        if(Pattern.compile("(?m)^\\s*(?:Permission Denial|Permission denied|logcat:.*[Pp]ermission denied)").matcher(output).find())return "permission";
        if(Pattern.compile("(?m)^\\s*(?:Can't find service|Unknown command)").matcher(output).find())return "unsupported";
        return "";
    }
    static Result parse(String source,String raw,String salt){
        Result result=new Result();boolean logcat="logcat".equals(source);
        for(String original:raw.split("\\r?\\n")){
            String line=original.length()>4096?original.substring(0,4096):original;
            String prefix="",body=line;
            if(logcat){
                Matcher match=LOG.matcher(line);if(!match.matches())continue;
                String tag=match.group(5).trim();body=match.group(6);
                boolean bluetooth=tag.toLowerCase(Locale.ROOT).matches(".*(?:bluetooth|bondstate|adapterservice|hidhost|bleremote).*|bt[_-].*");
                boolean input=tag.equals("InputReader")||tag.equals("InputDispatcher")||tag.equals("EventHub");
                boolean related=TAGS.contains(tag)&&body.toLowerCase(Locale.ROOT).matches(".*(?:bluetooth|hidhost|bleremote|com\\.mitv\\.tvhome|home\\.quiet\\.tv).*");
                if(!bluetooth&&!input&&!related)continue;
                prefix="time="+match.group(1)+" pid="+match.group(2)+" tid="+match.group(3)+" level="+match.group(4)+" tag="+(TAGS.contains(tag)?tag:bluetooth?"bluetooth_vendor":"input");
            }
            LinkedHashSet<String> fields=new LinkedHashSet<>();
            Matcher address=ADDRESS.matcher(body);while(address.find()&&fields.size()<8)fields.add("device="+device(salt,address.group()));
            body=LABEL.matcher(QUOTED.matcher(body).replaceAll(" ")).replaceAll(" ");
            Matcher number=FIELD.matcher(body);while(number.find()&&fields.size()<20)fields.add(number.group(1).toLowerCase(Locale.ROOT)+"="+number.group(2).toLowerCase(Locale.ROOT));
            Matcher input=DEVICE.matcher(body);if(input.find())fields.add("deviceId="+input.group(1));
            TreeMap<Integer,String> events=new TreeMap<>();
            for(int i=0;i<MARKERS.length;i++){Matcher event=MARKER_PATTERNS[i].matcher(body);while(event.find())events.put(event.start(),"event="+MARKERS[i].replace(' ','_'));}
            fields.addAll(events.values());
            Matcher name=CLASS.matcher(body);int classes=0;while(name.find()&&classes++<4)fields.add("component="+name.group());
            Matcher service=SERVICE.matcher(body);if(service.find())fields.add("service="+service.group(1));
            if(source.endsWith("_services")){
                Matcher record=Pattern.compile("ServiceRecord\\{[a-fA-F0-9]+\\s+u([0-9]+)\\s+([A-Za-z0-9_.]+/[A-Za-z0-9_.$]+)").matcher(body);
                if(record.find()){fields.add("user="+record.group(1));fields.add("service="+record.group(2));}
                Matcher process=Pattern.compile("ProcessRecord\\{[a-fA-F0-9]+\\s+([0-9]+):([A-Za-z0-9_.:]+)").matcher(body);
                if(process.find()){fields.add("pid="+process.group(1));fields.add("process="+process.group(2));}
                Matcher binding=Pattern.compile("\\b(isForeground|startRequested|hasBound|mBound|bindCount|connectionCount)\\s*[:=]\\s*(true\\b|false\\b|[0-9]+\\b)").matcher(body);
                while(binding.find()&&fields.size()<20)fields.add(binding.group(1)+"="+binding.group(2));
                if(fields.isEmpty()&&!body.trim().isEmpty())result.unparsed++;
            }
            if("stock_package".equals(source)){
                Matcher user=Pattern.compile("^\\s*User ([0-9]+):").matcher(body);if(user.find())fields.add("user="+user.group(1));
                Matcher installed=Pattern.compile("\\binstalled=(true|false)\\b").matcher(body);if(installed.find())fields.add("installed="+installed.group(1));
            }
            if(fields.isEmpty()){if(logcat)result.unparsed++;continue;}
            result.relevant++;
            if(result.lines.size()>=64){result.omitted++;if(logcat)result.lines.remove(0);else continue;}
            StringBuilder safe=new StringBuilder(prefix);for(String field:fields){if(safe.length()>850)break;if(safe.length()>0)safe.append(' ');safe.append(field);}
            result.lines.add(safe.toString());
        }
        return result;
    }
    private BluetoothDiagnosticEvidence(){}
}
