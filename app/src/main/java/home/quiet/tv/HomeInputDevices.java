package home.quiet.tv;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Handler;
import android.view.InputDevice;
import java.util.Locale;

/** Input topology affects key filtering only; it never changes accessibility grants. API 21+. */
final class HomeInputDevices implements InputManager.InputDeviceListener {
    private final InputManager manager;
    private final Runnable changed;
    private boolean listening;
    boolean known,gamepad,mouse;
    HomeInputDevices(Context context,Runnable changed){manager=(InputManager)context.getSystemService(Context.INPUT_SERVICE);this.changed=changed;}
    void start(Handler handler){
        if(manager==null)return;
        try{if(!listening){manager.registerInputDeviceListener(this,handler);listening=true;}refresh();}
        catch(RuntimeException unavailable){known=false;changed.run();}
    }
    void stop(){if(listening){manager.unregisterInputDeviceListener(this);listening=false;}known=false;}
    static boolean physical(String name,boolean virtual){return !virtual && (name==null || !name.toLowerCase(Locale.ROOT).contains("virtual"));}
    static boolean gamepad(String name,boolean virtual,int sources){return physical(name,virtual) && (sources&InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD;}
    static boolean mouse(String name,boolean virtual,int sources,int sdk,boolean external){
        // Before API 29 external identity is unavailable; conservatively pass keys for a physical mouse.
        return physical(name,virtual) && (sources&InputDevice.SOURCE_MOUSE)==InputDevice.SOURCE_MOUSE && (sdk<29 || external);
    }
    static boolean safe(boolean known,boolean xiaomi,boolean mouse,boolean gamepad,boolean homeForeground){
        return known && !(xiaomi && mouse) && !(gamepad && !homeForeground);
    }
    private void refresh(){
        boolean pad=false,pointer=false;
        try{
            for(int id:manager.getInputDeviceIds()){
                InputDevice d=manager.getInputDevice(id);if(d==null)continue;
                pad|=gamepad(d.getName(),d.isVirtual(),d.getSources());
                pointer|=mouse(d.getName(),d.isVirtual(),d.getSources(),Build.VERSION.SDK_INT,Build.VERSION.SDK_INT>=29 && d.isExternal());
            }
            gamepad=pad;mouse=pointer;known=true;
        }catch(RuntimeException unavailable){known=false;}
        changed.run();
    }
    public void onInputDeviceAdded(int id){refresh();}
    public void onInputDeviceRemoved(int id){refresh();}
    public void onInputDeviceChanged(int id){refresh();}
}
