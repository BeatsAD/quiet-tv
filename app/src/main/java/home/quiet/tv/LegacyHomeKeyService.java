package home.quiet.tv;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.KeyguardManager;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;

/** User-enabled HOME filtering for Hisense Hi3751/API19. No window takeover or background polling. */
public final class LegacyHomeKeyService extends AccessibilityService {
    private static volatile LegacyHomeKeyService instance;
    private final LegacyHomeKeyPolicy keys=new LegacyHomeKeyPolicy();
    private volatile boolean filtering;

    static boolean supported(){
        return BuildConfig.DECLARES_HOME && LegacyHomeKeyPolicy.supported(BuildConfig.LEGACY_19,Build.VERSION.SDK_INT,
                Build.MANUFACTURER,Build.BRAND,Build.MODEL,Build.DEVICE,Build.HARDWARE);
    }
    static void prepare(Context context){
        ComponentName service=new ComponentName(context,LegacyHomeKeyService.class);
        PackageManager pm=context.getPackageManager();
        int current=pm.getComponentEnabledSetting(service);
        if(!supported() && current!=PackageManager.COMPONENT_ENABLED_STATE_ENABLED)return;
        if(supported() && current!=PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)return;
        try{
            // Make the entry visible only on the reported platform. This does not grant accessibility.
            pm.setComponentEnabledSetting(service,supported()?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,PackageManager.DONT_KILL_APP);
        }catch(SecurityException failure){DiagnosticLog.error("legacy_home_key.component",failure);}
    }
    static boolean authorized(Context context){
        if(Settings.Secure.getInt(context.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0)!=1)return false;
        String enabled=Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName own=new ComponentName(context,LegacyHomeKeyService.class);
        if(enabled!=null)for(String entry:enabled.split(":"))if(own.equals(ComponentName.unflattenFromString(entry)))return true;
        return false;
    }
    static boolean connected(){return instance!=null;}
    static boolean filtering(){LegacyHomeKeyService service=instance;return service!=null && service.filtering;}
    static boolean defaultHome(Context context){
        ResolveInfo resolved=context.getPackageManager().resolveActivity(HomeController.homeIntent(),PackageManager.MATCH_DEFAULT_ONLY);
        return resolved!=null && resolved.activityInfo!=null && context.getPackageName().equals(resolved.activityInfo.packageName)
                && HomeActivity.class.getName().equals(resolved.activityInfo.name);
    }
    private boolean eligible(){
        if(!supported() || !filtering || !defaultHome(this))return false;
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        KeyguardManager keyguard=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        return power!=null && power.isScreenOn() && (keyguard==null || !UiCompat.keyguardLocked(keyguard));
    }
    @Override protected void onServiceConnected(){
        super.onServiceConnected();if(Build.VERSION.SDK_INT<19)return;instance=this;
        AccessibilityServiceInfo info=getServiceInfo();
        if(info!=null){
            int flag=AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            filtering=supported() && (info.getCapabilities()&AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS)!=0;
            info.flags=filtering?info.flags|flag:info.flags&~flag;setServiceInfo(info);
        }
        DiagnosticLog.info("legacy_home_key.service","connected=true filterRequested="+filtering+" physicalHomeVerified=false");
    }
    @Override protected boolean onKeyEvent(KeyEvent event){
        if(event.getKeyCode()!=KeyEvent.KEYCODE_HOME)return false;
        boolean available=eligible();
        if(event.getAction()==KeyEvent.ACTION_DOWN){
            boolean consumed=keys.down(event.getDownTime(),event.getRepeatCount(),available);
            if(event.getRepeatCount()==0)DiagnosticLog.info("legacy_home_key.down","scanCode="+event.getScanCode()+" downTime="+event.getDownTime()+" consumed="+consumed);
            return consumed;
        }
        if(event.getAction()!=KeyEvent.ACTION_UP)return false;
        int action=keys.up(event.getDownTime(),event.getEventTime(),event.isCanceled() || !available,
                ViewConfiguration.getLongPressTimeout(),ViewConfiguration.getDoubleTapTimeout());
        DiagnosticLog.info("legacy_home_key.up","downTime="+event.getDownTime()+" action="+action+" eligible="+available);
        if(action==LegacyHomeKeyPolicy.PASS)return false;
        if(action==HomeKeyPolicy.HOME){
            Intent home=new Intent(this,HomeActivity.class).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            ExternalActivityLauncher.open(this,"legacy_home_key.home",home);
        }else if(action==HomeKeyPolicy.RECENTS){
            boolean accepted=(Build.VERSION.SDK_INT>=19 && performGlobalAction(GLOBAL_ACTION_RECENTS));
            DiagnosticLog.info("legacy_home_key.recents","accepted="+accepted);
        }
        return true;
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){/* No window or screen-content processing. */}
    @Override public void onInterrupt(){keys.interrupt();DiagnosticLog.info("legacy_home_key.service","interrupted=true");}
    @Override public boolean onUnbind(Intent intent){keys.interrupt();filtering=false;if(instance==this)instance=null;return super.onUnbind(intent);}
    @Override public void onDestroy(){keys.interrupt();filtering=false;if(instance==this)instance=null;super.onDestroy();}
}
