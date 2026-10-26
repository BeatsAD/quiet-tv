package home.quiet.tv;

import java.util.regex.Pattern;

/** Named OEM operations only; numeric IDs are not portable between firmwares. */
final class HomeGuardAppOps {
    static final String[] AUTO_START={"APP_AUTO_START","AUTO_START","APP_ASSOC_START"};
    static boolean tcl(String manufacturer,String brand){
        return Pattern.compile("(?i)\\b(?:tcl|ffalcon|iffalcon)\\b").matcher(manufacturer+" "+brand).find();
    }
    static boolean unknown(String output){
        return output!=null && Pattern.compile("(?i)unknown (?:operation|op)|invalid operation").matcher(output).find();
    }
    static boolean allowed(String output,String operation){
        return Pattern.compile("(?m)^\\s*"+Pattern.quote(operation)+":\\s*allow(?:[;\\s]|$)").matcher(output).find();
    }
}
