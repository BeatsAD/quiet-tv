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
        // Submitted VIDAA/API22 production identities keep firmware and context paired.
        // Do not turn this exception into general support for root or engineering images.
        return uid==0 && sdk==22 && "Hisense".equalsIgnoreCase(brand) && "Hisense".equalsIgnoreCase(manufacturer)
                && "VIDAA_TV".equals(model) && uid(identity)==0
                && "1".equals(secure.trim()) && "0".equals(debuggable.trim())
                && (fingerprint.startsWith("Hisense/VIDAA_TV/MSD6A828:5.1.1/LVY48C/")
                    && fingerprint.endsWith(":user/release-keys") && identity.matches("(?s).*\\bcontext=u:r:hshell:s0\\s*$")
                    || "Hisense/VIDAA_TV/Hi3751v551:5.1.1/LVY48C/03.02.41.01:user/release-keys".equals(fingerprint)
                    && identity.matches("(?s).*\\bcontext=u:r:hsc:s0\\s*$"));
    }
    private AdbIdentity(){}
    static boolean jmFactory(int uid,int sdk,String model,String platform,String jmModel,boolean systemRouter){
        return uid==0 && sdk==33 && "t950s_be311".equals(model) && "txhd2".equals(platform.trim())
                && "C201_PRO".equals(jmModel.trim()) && systemRouter;
    }
}
