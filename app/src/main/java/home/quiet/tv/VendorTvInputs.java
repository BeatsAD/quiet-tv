package home.quiet.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Inputs actually enumerated by vendor firmware when the Android TV framework has none. */
final class VendorTvInputs {
    private static final String SKY_LIST="com.skyworth.tv.provider",SKY_SDK="com.skyworth.tv.sdk.provider";
    private static final ComponentName SKY_PLAYER=new ComponentName("com.skyworth.tv","com.skyworth.tv.activity.SkyTVMainActivity");
    private static final ComponentName KONKA_PLAYER=new ComponentName("com.konka.tvsettings","com.konka.tvsettings.RootActivity");
    static final class Source {
        final String label;
        final Intent intent;
        final Bundle parameters;
        Source(String label,Intent intent,Bundle parameters){this.label=label;this.intent=intent;this.parameters=parameters;}
    }
    private final Context context;
    VendorTvInputs(Context context){this.context=context;}

    List<Source> load(){
        List<Source> sources=skyworth(false);
        if(sources.isEmpty())sources=skyworth(true);
        if(sources.isEmpty())sources=konka();
        return sources;
    }
    private boolean provider(String authority,boolean write){
        ProviderInfo info=context.getPackageManager().resolveContentProvider(authority,0);
        return info!=null && TvInputController.enabledSystem(context,info) && info.exported
            && permitted(info.readPermission) && (!write||permitted(info.writePermission));
    }
    private boolean permitted(String permission){return permission==null || context.getPackageManager().checkPermission(permission,context.getPackageName())==PackageManager.PERMISSION_GRANTED;}
    private List<Source> skyworth(boolean sdk){
        ArrayList<Source> sources=new ArrayList<>();
        try{
            String authority=sdk?SKY_SDK:SKY_LIST;
            if(!provider(authority,false) || (sdk?!TvInputController.canOpen(context,SKY_PLAYER):!provider(SKY_SDK,true)))return sources;
            String[] columns=sdk?new String[]{"sourceName","sourceIndex","displayName","signalState"}:new String[]{"sourceName","sourceIndex","displayName"};
            try(Cursor cursor=context.getContentResolver().query(Uri.parse("content://"+authority+"/system/source_list"),columns,null,null,null)){
                if(cursor==null)return sources;
                int name=cursor.getColumnIndexOrThrow("sourceName"),index=cursor.getColumnIndexOrThrow("sourceIndex"),display=cursor.getColumnIndexOrThrow("displayName");
                HashSet<String> seen=new HashSet<>();
                while(cursor.moveToNext()){
                    String value=cursor.getString(name);if(value==null || value.isEmpty())continue;
                    int ordinal=cursor.getInt(index);if(!seen.add(value+":"+ordinal))continue;
                    String label=cursor.getString(display);if(label==null || label.isEmpty())label=value;
                    Bundle data=new Bundle();data.putString("name",value);data.putInt("index",ordinal);
                    if(sdk){
                        data.putString("displayname",label);data.putInt("signal",cursor.getInt(cursor.getColumnIndexOrThrow("signalState")));
                        sources.add(new Source(label,new Intent().setComponent(SKY_PLAYER).putExtra("source",data),null));
                    }else sources.add(new Source(label,null,data));
                }
            }
        }catch(RuntimeException failure){sources.clear();DiagnosticLog.error("input.vendor.enumeration.failed",failure);}
        return sources;
    }
    private List<Source> konka(){
        ArrayList<Source> sources=new ArrayList<>();
        if(!TvInputController.canOpen(context,KONKA_PLAYER))return sources;
        try{
            Class<?> managerClass=Class.forName("com.konka.android.tv.KKCommonManager");
            Object manager=managerClass.getMethod("getInstance",Context.class).invoke(null,context.getApplicationContext());
            Object reported=managerClass.getMethod("getTVInputSourceList").invoke(manager);
            if(!(reported instanceof List))return sources;
            Class<?> sourceClass=Class.forName("com.konka.android.tv.KKCommonManager$EN_KK_INPUT_SOURCE");
            java.lang.reflect.Method labelMethod=null;
            try{labelMethod=managerClass.getMethod("getInputSourceName",sourceClass);}catch(NoSuchMethodException ignored){}
            HashSet<String> seen=new HashSet<>();
            for(Object item:(List<?>)reported){
                if(!(item instanceof Enum) || !sourceClass.isInstance(item))continue;
                Enum<?> source=(Enum<?>)item;if(!seen.add(source.name()))continue;
                String label=source.name();
                if(labelMethod!=null){Object text=labelMethod.invoke(manager,source);if(text instanceof String && !((String)text).isEmpty())label=(String)text;}
                sources.add(new Source(label,new Intent("com.konka.tvsettings.intent.action.RootActivity").setComponent(KONKA_PLAYER).putExtra("source",source.ordinal()),null));
            }
        }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){sources.clear();DiagnosticLog.error("input.konka.enumeration.failed",failure);}
        return sources;
    }
    boolean open(Source source){
        try{
            if(source.intent!=null){
                if(!TvInputController.canOpen(context,source.intent.getComponent()))return false;
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
