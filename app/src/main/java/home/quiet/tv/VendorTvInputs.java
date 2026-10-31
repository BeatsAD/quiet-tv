package home.quiet.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.media.tv.TvInputInfo;
import android.net.Uri;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Inputs actually enumerated by vendor firmware, including types missing from the framework. */
@android.annotation.TargetApi(21)
final class VendorTvInputs {
    private static final String SKY_LIST="com.skyworth.tv.provider",SKY_SDK="com.skyworth.tv.sdk.provider";
    private static final ComponentName SKY_PLAYER=new ComponentName("com.skyworth.tv","com.skyworth.tv.activity.SkyTVMainActivity");
    private static final ComponentName KONKA_PLAYER=new ComponentName("com.konka.tvsettings","com.konka.tvsettings.RootActivity");
    static final class Source {
        String label;
        final String key,owner;
        final boolean hdmi;
        final int type;
        final Intent intent;
        final Bundle parameters;
        String mapping="";
        Source(String label,Intent intent,Bundle parameters,String key,String owner,int type){this.label=label;this.intent=intent;this.parameters=parameters;this.key=key;this.owner=owner;this.type=type;hdmi=type==TvInputInfo.TYPE_HDMI;}
    }
    static final class Discovery {
        final List<Source> sources=new ArrayList<>();
        boolean restricted,disabled,failed,empty,sdkUnavailable;
        String problem(boolean chinese){
            if(restricted)return chinese?"厂商输入服务存在，但系统限制访问。":"The vendor input service is present, but access is restricted.";
            if(disabled)return chinese?"厂商输入组件已停用。":"The vendor input component is disabled.";
            if(failed)return chinese?"读取厂商输入服务时发生异常。":"Reading the vendor input service failed.";
            if(sdkUnavailable)return chinese?"原厂播放器存在，但未提供可用的端口枚举接口。":"The native player has no accessible source enumeration interface.";
            if(empty)return chinese?"厂商输入服务未返回端口。":"The vendor input service returned no ports.";
            return "";
        }
    }
    private Discovery discovery=new Discovery();
    private final Context context;
    VendorTvInputs(Context context){this.context=context;}

    // Use the firmware's source identifier, never its editable display label.
    static int sourceType(String name){
        String value=name.toUpperCase(java.util.Locale.ROOT);
        if(value.contains("HDMI"))return TvInputInfo.TYPE_HDMI;
        if(value.equals("DP")||value.contains("DISPLAYPORT"))return TvInputInfo.TYPE_DISPLAY_PORT;
        if(value.equals("VOD")||value.contains("DTV")||value.contains("ATV")||value.contains("DVB")||value.contains("DTMB"))return TvInputInfo.TYPE_TUNER;
        if(value.startsWith("AV")||value.contains("_AV"))return TvInputInfo.TYPE_COMPOSITE;
        if(value.contains("YPBPR")||value.contains("YUV"))return TvInputInfo.TYPE_COMPONENT;
        if(value.contains("SVIDEO"))return TvInputInfo.TYPE_SVIDEO;
        if(value.contains("VGA"))return TvInputInfo.TYPE_VGA;
        return -1;
    }

    List<Source> load(){return inspect().sources;}
    Discovery inspect(){return inspect(new DeviceInputProfile(context).active());}
    Discovery inspect(org.json.JSONObject profile){
        discovery=new Discovery();List<Source> sources=skyworth(false);
        if(sources.isEmpty())sources=skyworth(true);
        if(sources.isEmpty())sources=konka();
        discovery.sources.addAll(sources);
        // Preserve working native protocols. Do not combine two vendor lists whose hardware
        // identities cannot be compared, or manufacture ports from the brand alone.
        if(discovery.sources.isEmpty())try{discovery.sources.addAll(XgimiInputs.load(context));}catch(Exception|LinkageError failure){adapterFailure("xgimi",failure);}
        if(discovery.sources.isEmpty())try{discovery.sources.addAll(HisenseInputs.load(context));}catch(Exception|LinkageError failure){adapterFailure("hisense",failure);}
        if(discovery.sources.isEmpty())try{discovery.sources.addAll(XiaomiInputs.load(context,profile));}catch(Exception|LinkageError failure){adapterFailure("xiaomi",failure);}
        DiagnosticLog.info("input.vendor.enumeration","count="+discovery.sources.size()+" restricted="+discovery.restricted+" disabled="+discovery.disabled+" failed="+discovery.failed+" sdkUnavailable="+discovery.sdkUnavailable+" empty="+discovery.empty);
        return discovery;
    }
    private void adapterFailure(String name,Throwable failure){
        if(failure instanceof ClassNotFoundException||failure instanceof NoSuchMethodException||failure instanceof LinkageError)discovery.sdkUnavailable=true;else discovery.failed=true;
        DiagnosticLog.error("input."+name+".enumeration_failed",failure);
    }
    static String packageStamp(Context context,String pkg) throws PackageManager.NameNotFoundException {
        android.content.pm.PackageInfo info=context.getPackageManager().getPackageInfo(pkg,0);
        return pkg+":"+info.versionCode+":"+info.lastUpdateTime;
    }
    static String scope(Context context,Source source) throws Exception {
        String provider="";
        if(source.key.startsWith(SKY_LIST+":")||source.key.startsWith(SKY_SDK+":")){
            ProviderInfo info=context.getPackageManager().resolveContentProvider(source.key.substring(0,source.key.indexOf(':')),0);
            if(info==null)throw new IllegalStateException("Input provider unavailable");provider=packageStamp(context,info.packageName);
        }else if(source.key.startsWith("hisense:"))provider=HisenseInputs.stamp(context);
        String value=android.os.Build.FINGERPRINT+"\n"+source.key+"\n"+provider+"\n"+packageStamp(context,source.owner);
        // Preserve saved scopes of the existing Skyworth and Konka protocols byte for byte.
        if(source.key.startsWith("xgimi:")||source.key.startsWith("hisense:")||source.key.startsWith("xiaomi:"))value+="\n"+source.intent.toUri(0)+"\n"+source.mapping;
        return DeviceInputProfile.digest(value);
    }
    private boolean provider(String authority,boolean write){
        ProviderInfo info=context.getPackageManager().resolveContentProvider(authority,PackageManager.GET_DISABLED_COMPONENTS);
        String reason=info==null?"missing":!TvInputController.enabledSystem(context,info)?"not_enabled_system":!info.exported?"not_exported":!permitted(info.readPermission)?"read_permission_denied":write&&!permitted(info.writePermission)?"write_permission_denied":"ready";
        if(info!=null&&(info.applicationInfo.flags&android.content.pm.ApplicationInfo.FLAG_SYSTEM)!=0){
            if(!TvInputController.enabledSystem(context,info))discovery.disabled=true;
            else if(!info.exported||!permitted(info.readPermission)||write&&!permitted(info.writePermission))discovery.restricted=true;
        }
        DiagnosticLog.info("input.vendor.provider","authority="+authority+" write="+write+" result="+reason);
        return "ready".equals(reason);
    }
    private boolean player(ComponentName name){
        try{
            android.content.pm.ActivityInfo info=context.getPackageManager().getActivityInfo(name,PackageManager.GET_DISABLED_COMPONENTS);
            if((info.applicationInfo.flags&android.content.pm.ApplicationInfo.FLAG_SYSTEM)==0)return false;
            if(!TvInputController.enabledSystem(context,info)){discovery.disabled=true;return false;}
            if(!info.exported||!permitted(info.permission)){discovery.restricted=true;return false;}
            return true;
        }catch(PackageManager.NameNotFoundException missing){return false;}
        catch(SecurityException restricted){discovery.restricted=true;return false;}
        catch(RuntimeException failure){discovery.failed=true;return false;}
    }
    private boolean permitted(String permission){return permission==null || context.getPackageManager().checkPermission(permission,context.getPackageName())==PackageManager.PERMISSION_GRANTED;}
    private List<Source> skyworth(boolean sdk){
        ArrayList<Source> sources=new ArrayList<>();
        try{
            String authority=sdk?SKY_SDK:SKY_LIST;
            if(!provider(authority,false) || (sdk?!player(SKY_PLAYER):!provider(SKY_SDK,true)))return sources;
            String[] columns=sdk?new String[]{"sourceName","sourceIndex","displayName","signalState"}:new String[]{"sourceName","sourceIndex","displayName"};
            try(Cursor cursor=context.getContentResolver().query(Uri.parse("content://"+authority+"/system/source_list"),columns,null,null,null)){
                if(cursor==null){discovery.failed=true;DiagnosticLog.info("input.vendor.query","provider="+authority+" result=null_cursor");return sources;}
                int name=cursor.getColumnIndexOrThrow("sourceName"),index=cursor.getColumnIndexOrThrow("sourceIndex"),display=cursor.getColumnIndexOrThrow("displayName");
                HashSet<String> seen=new HashSet<>();
                while(cursor.moveToNext()){
                    String value=cursor.getString(name);if(value==null || value.isEmpty())continue;
                    int ordinal=cursor.getInt(index);if(!seen.add(value+":"+ordinal))continue;
                    String label=cursor.getString(display);if(label==null || label.isEmpty())label=value;
                    Bundle data=new Bundle();data.putString("name",value);data.putInt("index",ordinal);
                    if(sdk){
                        data.putString("displayname",label);data.putInt("signal",cursor.getInt(cursor.getColumnIndexOrThrow("signalState")));
                        sources.add(new Source(label,new Intent().setComponent(SKY_PLAYER).putExtra("source",data),null,authority+":"+value,"com.skyworth.tv",sourceType(value)));
                    }else sources.add(new Source(label,null,data,authority+":"+value,"com.skyworth.tv",sourceType(value)));
                }
            }
            if(sources.isEmpty())discovery.empty=true;
        }catch(SecurityException failure){discovery.restricted=true;DiagnosticLog.error("input.vendor.enumeration.restricted",failure);}
        catch(RuntimeException failure){discovery.failed=true;DiagnosticLog.error("input.vendor.enumeration.failed",failure);}
        return sources;
    }
    private List<Source> konka(){
        ArrayList<Source> sources=new ArrayList<>();
        if(!player(KONKA_PLAYER)){DiagnosticLog.info("input.konka.enumeration","result=player_unavailable");return sources;}
        try{
            Class<?> managerClass=Class.forName("com.konka.android.tv.KKCommonManager");
            Object manager=managerClass.getMethod("getInstance",Context.class).invoke(null,context.getApplicationContext());
            Object reported=managerClass.getMethod("getTVInputSourceList").invoke(manager);
            if(!(reported instanceof List)){discovery.failed=true;return sources;}
            Class<?> sourceClass=Class.forName("com.konka.android.tv.KKCommonManager$EN_KK_INPUT_SOURCE");
            java.lang.reflect.Method labelMethod=null;
            try{labelMethod=managerClass.getMethod("getInputSourceName",sourceClass);}catch(NoSuchMethodException ignored){}
            HashSet<String> seen=new HashSet<>();
            for(Object item:(List<?>)reported){
                if(!(item instanceof Enum) || !sourceClass.isInstance(item))continue;
                Enum<?> source=(Enum<?>)item;if(!seen.add(source.name()))continue;
                String label=source.name();
                if(labelMethod!=null){Object text=labelMethod.invoke(manager,source);if(text instanceof String && !((String)text).isEmpty())label=(String)text;}
                sources.add(new Source(label,new Intent("com.konka.tvsettings.intent.action.RootActivity").setComponent(KONKA_PLAYER).putExtra("source",source.ordinal()),null,"konka:"+source.name(),"com.konka.tvsettings",sourceType(source.name())));
            }
            if(sources.isEmpty())discovery.empty=true;
        }catch(ClassNotFoundException|NoSuchMethodException|LinkageError unavailable){discovery.sdkUnavailable=true;DiagnosticLog.error("input.konka.sdk_unavailable",unavailable);}
        catch(Exception failure){
            Throwable cause=failure instanceof java.lang.reflect.InvocationTargetException?failure.getCause():failure;
            if(cause instanceof SecurityException)discovery.restricted=true;else discovery.failed=true;
            DiagnosticLog.error("input.konka.enumeration.failed",failure);
        }
        return sources;
    }
    boolean open(Source source){
        try{
            if(source.key.startsWith("xgimi:")||source.key.startsWith("hisense:")||source.key.startsWith("xiaomi:")){
                boolean current=false;
                for(Source candidate:new TvInputController(context).inspectSources().vendor.sources)
                    if(candidate.key.equals(source.key)&&candidate.mapping.equals(source.mapping)&&candidate.intent.toUri(0).equals(source.intent.toUri(0))){current=true;break;}
                if(!current)return false;
            }
            if(source.key.startsWith("hisense:"))return HisenseInputs.open(context,source);
            if(source.intent!=null){
                if(!TvInputController.canOpen(context,source.intent.getComponent())){DiagnosticLog.info("input.vendor.request",DiagnosticLog.intent(source.intent)+" result=not_callable");return false;}
                context.startActivity(new Intent(source.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                DiagnosticLog.info("input.vendor.dispatch",source.intent.getComponent().flattenToShortString());return true;
            }
            if(!provider(SKY_SDK,true))return false;
            Bundle result=context.getContentResolver().call(Uri.parse("content://"+SKY_SDK+"/method"),"METHOD_GOTO_SOURCE",null,new Bundle(source.parameters));
            boolean accepted=result!=null && result.getBoolean("ret");
            DiagnosticLog.info("input.vendor.request","skyworth accepted="+accepted);return accepted;
        }catch(RuntimeException failure){DiagnosticLog.error("input.vendor.dispatch.failed",failure);return false;}
    }
}
