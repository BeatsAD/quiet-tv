package home.quiet.tv;

/** UI paint only. Wallpapers, app artwork, avatars and illustrations keep their source colors. */
final class UiPalette {
    static final int TEXT=0xffffffff,SECONDARY=0xb3ffffff,FAINT=0x73ffffff;
    static final int INK=0xe6000000,INK_SECONDARY=0x99000000,INK_FAINT=0x73000000;
    static final int CONTROL=0x0dffffff,SELECTED=0x29ffffff,EDGE=0x1fffffff,EDGE_STRONG=0x4dffffff;
    static final int STATUS_SUCCESS=0xff30d158,STATUS_PROGRESS=0xff0a84ff,STATUS_WARNING=0xffffd166,STATUS_ERROR=0xffff9a9f;
    static final int SWITCH_ON=0xff34c759;
    static final int SLIDER_PROGRESS=0xff0a84ff,SLIDER_PROGRESS_FOCUSED=0xff007aff;
    private UiPalette(){}
}
