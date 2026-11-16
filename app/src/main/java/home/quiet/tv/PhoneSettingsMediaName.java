package home.quiet.tv;

/** Display labels never replace the original filename used for format detection. */
final class PhoneSettingsMediaName {
    private PhoneSettingsMediaName(){}
    static String read(Object value)throws PhoneSettingsServer.BadInput {
        if(!(value instanceof String))throw new PhoneSettingsServer.BadInput();
        String raw=(String)value,name=raw.trim();
        if(name.isEmpty()||name.length()>48)throw new PhoneSettingsServer.BadInput();
        for(int i=0;i<raw.length();i++){
            char c=raw.charAt(i);if(Character.isISOControl(c)||c=='/'||c=='\\')throw new PhoneSettingsServer.BadInput();
            if(Character.isHighSurrogate(c)){if(++i>=raw.length()||!Character.isLowSurrogate(raw.charAt(i)))throw new PhoneSettingsServer.BadInput();}
            else if(Character.isLowSurrogate(c))throw new PhoneSettingsServer.BadInput();
        }
        return name;
    }
}
