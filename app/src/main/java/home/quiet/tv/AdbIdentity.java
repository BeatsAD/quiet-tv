package home.quiet.tv;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Identity compatibility is separate from local-device, user and command-capability checks. */
final class AdbIdentity {
    static int uid(String output){
        String value=output.trim();
        if(value.matches("[0-9]{1,6}"))return Integer.parseInt(value);
        Matcher match=Pattern.compile("^uid=([0-9]{1,6})\\([^\\r\\n)]*\\)(?:[ \\t]+[^\\r\\n]*)?$").matcher(value);
        return match.matches()?Integer.parseInt(match.group(1)):-1;
    }
    static boolean accepted(int uid,String brand,String manufacturer,String model,String fingerprint,int sdk,
                            String identity,String secure,String debuggable){
        if(uid==2000)return true;
        // The submitted VIDAA/API22 trace identifies this production firmware's hshell daemon.
        // Do not turn this exception into general support for root or engineering images.
        return uid==0 && sdk==22 && "Hisense".equalsIgnoreCase(brand) && "Hisense".equalsIgnoreCase(manufacturer)
                && "VIDAA_TV".equals(model) && fingerprint.startsWith("Hisense/VIDAA_TV/MSD6A828:5.1.1/LVY48C/")
                && fingerprint.endsWith(":user/release-keys") && uid(identity)==0
                && identity.matches("(?s).*\\bcontext=u:r:hshell:s0\\s*$")
                && "1".equals(secure.trim()) && "0".equals(debuggable.trim());
    }
    private AdbIdentity(){}
}
