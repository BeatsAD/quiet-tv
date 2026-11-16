package home.quiet.tv;

/** Stable preference values for the three built-in key-sound sets. */
enum KeySoundTheme {
    // Keep saved IDs when redesigning the two additional sound sets.
    WOOD("wood","木韵","Wood"),ELECTRONIC("bell","电子","Electronic"),MECHANICAL("drop","机械","Mechanical");
    final String id,zh,en;
    KeySoundTheme(String id,String zh,String en){this.id=id;this.zh=zh;this.en=en;}
    static KeySoundTheme from(String id){for(KeySoundTheme theme:values())if(theme.id.equals(id))return theme;return WOOD;}
    static boolean valid(Object id){for(KeySoundTheme theme:values())if(theme.id.equals(id))return true;return false;}
    static String[] ids(){KeySoundTheme[] themes=values();String[] ids=new String[themes.length];for(int i=0;i<themes.length;i++)ids[i]=themes[i].id;return ids;}
    static String[] titles(boolean chinese){KeySoundTheme[] themes=values();String[] titles=new String[themes.length];for(int i=0;i<themes.length;i++)titles[i]=chinese?themes[i].zh:themes[i].en;return titles;}
}
