package home.quiet.tv;

import android.content.*;
import android.content.pm.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Jamdeo SourceManager protocol, observed on H55E9A / Android 8.
 * Aerial 587a2849, HisenseSources.kt (MIT). No port-number template or activity-owned binding. */
final class HisenseInputs {
    static final ComponentName SOURCE=new ComponentName("com.jamdeo.tv.service","com.jamdeo.tv.service.SourceService");
    static final ComponentName TRANSITION=new ComponentName("com.hisense.tv.tiletransition","com.hisense.tv.tiletransition.TileTransitionService");
    static final String ACTION="com.jamdeo.tv.policy.SILO_SWITCH_EVENT",EXTRA="silo_input_src_id";
    private static Connection connection;
    static boolean callable(Context context){
        for(ComponentName component:new ComponentName[]{SOURCE,TRANSITION})try{
            ServiceInfo info=context.getPackageManager().getServiceInfo(component,0);
            if(!InputComponents.enabledSystem(context,info)||!info.exported||info.permission!=null&&context.getPackageManager().checkPermission(info.permission,context.getPackageName())!=PackageManager.PERMISSION_GRANTED)return false;
        }catch(PackageManager.NameNotFoundException|RuntimeException unavailable){return false;}
        return true;
    }
    static String stamp(Context context) throws Exception {
        return VendorTvInputs.packageStamp(context,SOURCE.getPackageName())+"\n"+VendorTvInputs.packageStamp(context,TRANSITION.getPackageName());
    }
    static List<VendorTvInputs.Source> load(Context context) throws Exception {
        if(!callable(context))return Collections.emptyList();
        String scope=Build.FINGERPRINT+"\n"+stamp(context);Connection current;
        synchronized(HisenseInputs.class){
            if(connection==null||!scope.equals(connection.scope)||connection.ready.getCount()==0&&connection.manager==null)connection=new Connection(context.getApplicationContext(),scope);
            current=connection;
        }
        boolean worker=Looper.myLooper()!=Looper.getMainLooper();
        try{
            if(worker&&!current.ready.await(2,TimeUnit.SECONDS))return Collections.emptyList();
            Object manager=current.manager;if(manager==null)return Collections.emptyList();
            Class<?> type=manager.getClass();java.lang.reflect.Method connected=type.getMethod("isConnected"),inputs=type.getMethod("getInputSources"),name=type.getMethod("getInputSourceName",int.class);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);List<?> ports=null;
            do{
                if(Boolean.TRUE.equals(connected.invoke(manager))){Object value=inputs.invoke(manager);if(value instanceof List)ports=(List<?>)value;}
                if(ports!=null&&!ports.isEmpty())break;
                if(!worker||System.nanoTime()>=deadline)return Collections.emptyList();
                Thread.sleep(50);
            }while(true);
            ArrayList<VendorTvInputs.Source> result=new ArrayList<>();Set<Integer> seen=new HashSet<>();Pattern hdmi=Pattern.compile("HDMI\\s*([1-9][0-9]*)",Pattern.CASE_INSENSITIVE);
            for(Object item:ports){
                if(!(item instanceof Integer)||(Integer)item<=0||!seen.add((Integer)item))continue;
                Object value=name.invoke(manager,item);if(!(value instanceof String))continue;
                Matcher match=hdmi.matcher(((String)value).trim());if(!match.matches())continue;
                int id=(Integer)item;Intent intent=new Intent(ACTION).setPackage(TRANSITION.getPackageName()).putExtra(EXTRA,id);
                result.add(new VendorTvInputs.Source("HDMI "+match.group(1),intent,null,"hisense:"+id,TRANSITION.getPackageName(),VendorTvInputs.TYPE_HDMI));
            }
            return result;
        }catch(InterruptedException cancelled){Thread.currentThread().interrupt();return Collections.emptyList();}
    }
    static boolean open(Context context,VendorTvInputs.Source source){
        Intent intent=source.intent;
        if(!callable(context)||intent==null||!ACTION.equals(intent.getAction())||!TRANSITION.getPackageName().equals(intent.getPackage())||!source.key.equals("hisense:"+intent.getIntExtra(EXTRA,0))||intent.getIntExtra(EXTRA,0)<=0)return false;
        // The transition service registers its receiver dynamically; an absent manifest receiver
        // does not make this protocol unavailable. Dispatch is not a physical-switch receipt.
        context.sendBroadcast(new Intent(intent));return true;
    }
    private static final class Connection {
        final String scope;final CountDownLatch ready=new CountDownLatch(1);volatile Object manager;
        Connection(Context context,String scope){
            this.scope=scope;
            new Handler(Looper.getMainLooper()).post(()->{
                try{
                    Class<?> type=Class.forName("com.jamdeo.tv.TvManager");Object tv=type.getMethod("getInstance").invoke(null);
                    manager=type.getMethod("getSourceManager",Context.class).invoke(tv,context);
                }catch(Exception|LinkageError failure){DiagnosticLog.error("input.hisense.connect_failed",failure);}
                finally{ready.countDown();}
            });
        }
    }
}
