package home.quiet.tv;

import java.security.*;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** PBKDF2-HMAC-SHA1 is available on API 19; credentials never leave private app data. */
final class ChildModeCredentials {
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final int ROUNDS=120_000;
    static boolean numeric(String value){return value!=null && value.matches("[0-9]{6}");}
    static String recovery(String pin){String code;do{code=String.format(java.util.Locale.ROOT,"%06d",RANDOM.nextInt(1_000_000));}while(code.equals(pin));return code;}
    static String prepare(String pin){
        if(!numeric(pin))throw new IllegalArgumentException("请输入 6 位数字");
        byte[] salt=new byte[16];RANDOM.nextBytes(salt);return hex(salt)+":"+hex(derive(pin,salt));
    }
    static boolean verify(String pin,String saved){
        if(!numeric(pin))return false;
        String[] values=saved.split(":",-1);
        if(values.length!=2 || values[0].length()!=32 || values[1].length()!=64)throw new IllegalStateException("家长密码数据无法读取，请使用恢复码重设");
        return MessageDigest.isEqual(unhex(values[1]),derive(pin,unhex(values[0])));
    }
    private static byte[] derive(String pin,byte[] salt){
        PBEKeySpec spec=new PBEKeySpec(pin.toCharArray(),salt,ROUNDS,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();}
        catch(GeneralSecurityException e){throw new IllegalStateException("当前系统无法验证家长密码",e);}
        finally{spec.clearPassword();}
    }
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
    private static byte[] unhex(String value){
        byte[] out=new byte[value.length()/2];try{for(int i=0;i<out.length;i++)out[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16);return out;}
        catch(NumberFormatException e){Arrays.fill(out,(byte)0);throw new IllegalStateException("家长密码数据无法读取，请使用恢复码重设");}
    }
}
