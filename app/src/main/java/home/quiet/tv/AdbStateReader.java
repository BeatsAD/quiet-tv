package home.quiet.tv;

import android.content.ContentResolver;
import android.provider.Settings;

/** Zero/missing settings can be masked for third-party apps. Never infer off on first read. */
final class AdbStateReader {
    static final String WIFI="adb_wifi_enabled";
    private boolean sawUsb,sawWifi,observedOff;
    private final ContentResolver resolver;
    AdbStateReader(ContentResolver resolver){this.resolver=resolver;}
    AdbState.Debug read(){return observe(value(Settings.Global.ADB_ENABLED),value(WIFI));}
    private int value(String key){
        try{return Settings.Global.getInt(resolver,key,-1);}catch(SecurityException e){return -1;}
    }
    AdbState.Debug observe(int usb,int wifi){
        // A positive signal is usable. A transition to zero is evidence only for a
        // transport previously observed enabled in this foreground observation window.
        if(usb==1 || wifi==1){sawUsb=usb==1;sawWifi=wifi==1;observedOff=false;return AdbState.Debug.ENABLED;}
        boolean off=(sawUsb || sawWifi) && (!sawUsb || usb==0) && (!sawWifi || wifi==0);
        if(off)observedOff=true;
        if(observedOff && (!sawUsb || usb==0) && (!sawWifi || wifi==0))return AdbState.Debug.DISABLED;
        return AdbState.Debug.UNKNOWN;
    }
    void reset(){sawUsb=false;sawWifi=false;observedOff=false;}
}
