package home.quiet.tv;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.content.res.ColorStateList;
import androidx.core.graphics.PathParser;

/** Pinned Lucide paths; source mapping and ISC/MIT notices are in assets/licenses/Lucide-NOTICE.txt. */
final class QuickControlIcon extends Drawable {
    private static final float STROKE_WIDTH=2f;
    private final String pathData;private final Path path;private final float viewport;private final boolean stroke;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private int color=UiPalette.TEXT;private int alpha=255;private Integer fixedColor;private ColorStateList tint;private boolean solidSun;
    // AndroidX 1.12 retains this public parser for the API14 profile with a library-only annotation.
    @android.annotation.SuppressLint("RestrictedApi")
    QuickControlIcon(String name){String data;boolean outlined=false;float size=256;switch(name){
        case "range-sun-low":size=24;outlined=true;data="M 16 12 A 4 4 0 1 0 8 12 A 4 4 0 1 0 16 12 Z M 12 4 h .01 M 20 12 h .01 M 12 20 h .01 M 4 12 h .01 M 17.657 6.343 h .01 M 17.657 17.657 h .01 M 6.343 17.657 h .01 M 6.343 6.343 h .01";break; // Lucide: sun-dim
        case "range-sun-high":case "sun":size=24;outlined=true;data="M 16 12 A 4 4 0 1 0 8 12 A 4 4 0 1 0 16 12 Z M 12 2 v 2 M 12 20 v 2 M 0 0 m 4.93 4.93 1.41 1.41 M 0 0 m 17.66 17.66 1.41 1.41 M 2 12 h 2 M 20 12 h 2 M 0 0 m 6.34 17.66 -1.41 1.41 M 0 0 m 19.07 4.93 -1.41 1.41";break; // Lucide: sun
        case "range-speaker-low":size=24;outlined=true;data="M 11 4.702 a .705 .705 0 0 0 -1.203 -.498 L 6.413 7.587 A 1.4 1.4 0 0 1 5.416 8 H 3 a 1 1 0 0 0 -1 1 v 6 a 1 1 0 0 0 1 1 h 2.416 a 1.4 1.4 0 0 1 .997 .413 l 3.383 3.384 A .705 .705 0 0 0 11 19.298 z M 16 9 a 5 5 0 0 1 0 6";break; // Lucide: volume-1
        case "range-speaker-high":case "speaker-high":size=24;outlined=true;data="M 11 4.702 a .705 .705 0 0 0 -1.203 -.498 L 6.413 7.587 A 1.4 1.4 0 0 1 5.416 8 H 3 a 1 1 0 0 0 -1 1 v 6 a 1 1 0 0 0 1 1 h 2.416 a 1.4 1.4 0 0 1 .997 .413 l 3.383 3.384 A .705 .705 0 0 0 11 19.298 z M 16 9 a 5 5 0 0 1 0 6 M 19.364 18.364 a 9 9 0 0 0 0 -12.728";break; // Lucide: volume-2
        case "speaker-off":size=24;outlined=true;data="M 11 4.702 a .7 .7 0 0 0 -1.203 -.498 L 6.413 7.587 A 1.4 1.4 0 0 1 5.416 8 H 3 a 1 1 0 0 0 -1 1 v 6 a 1 1 0 0 0 1 1 h 2.416 a 1.4 1.4 0 0 1 .997 .413 l 3.383 3.384 A .7 .7 0 0 0 11 19.298 z M 0 0 m 16.5 14.5 5 -5 M 0 0 m 16.5 9.5 5 5";break; // Lucide: volume-x
        case "image":case "left-wallpaper":size=24;outlined=true;data="M 5 3 H 19 A 2 2 0 0 1 21 5 V 19 A 2 2 0 0 1 19 21 H 5 A 2 2 0 0 1 3 19 V 5 A 2 2 0 0 1 5 3 Z M 11 9 A 2 2 0 1 0 7 9 A 2 2 0 1 0 11 9 Z M 0 0 m 21 15 -3.086 -3.086 a 2 2 0 0 0 -2.828 0 L 6 21";break; // Lucide: image
        case "monitor-play":size=24;outlined=true;data="M 15.033 9.44 a .647 .647 0 0 1 0 1.12 l -4.065 2.352 a .645 .645 0 0 1 -.968 -.56 V 7.648 a .645 .645 0 0 1 .967 -.56 z M 12 17 v 4 M 8 21 h 8 M 4 3 H 20 A 2 2 0 0 1 22 5 V 15 A 2 2 0 0 1 20 17 H 4 A 2 2 0 0 1 2 15 V 5 A 2 2 0 0 1 4 3 Z";break; // Lucide: monitor-play
        case "plugs":case "left-input":case "header-input":size=24;outlined=true;data="M 22 9 a 1 1 0 0 0 -1 -1 H 3 a 1 1 0 0 0 -1 1 v 4 a 1 1 0 0 0 1 1 h .5 a 2 2 0 0 1 1.6 .8 l .3 .4 A 2 2 0 0 0 7 16 h 10 a 2 2 0 0 0 1.6 -.8 l .3 -.4 a 2 2 0 0 1 1.6 -.8 h .5 a 1 1 0 0 0 1 -1 z M 8 12 h 8";break; // Lucide: hdmi-port
        case "wifi-high":case "left-network":case "header-wifi":size=24;outlined=true;data="M 12 20 h .01 M 2 8.82 a 15 15 0 0 1 20 0 M 5 12.859 a 10 10 0 0 1 14 0 M 8.5 16.429 a 5 5 0 0 1 7 0";break; // Lucide: wifi
        case "device-mobile":size=24;outlined=true;data="M 7 2 H 17 A 2 2 0 0 1 19 4 V 20 A 2 2 0 0 1 17 22 H 7 A 2 2 0 0 1 5 20 V 4 A 2 2 0 0 1 7 2 Z M 12 18 h .01";break; // Lucide: smartphone
        case "gear-six":case "header-settings":case "left-settings":size=24;outlined=true;data="M 9.671 4.136 a 2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1 -2.33 4.033 2.34 2.34 0 0 0 -3.319 1.915 2.34 2.34 0 0 1 -4.659 0 2.34 2.34 0 0 0 -3.32 -1.915 2.34 2.34 0 0 1 -2.33 -4.033 2.34 2.34 0 0 0 0 -3.831 A 2.34 2.34 0 0 1 6.35 6.051 a 2.34 2.34 0 0 0 3.319 -1.915 M 15 12 A 3 3 0 1 0 9 12 A 3 3 0 1 0 15 12 Z";break; // Lucide: settings
        case "caret-right":case "chevron-right":size=24;outlined=true;data="M 0 0 m 9 18 6 -6 -6 -6";break; // Lucide: chevron-right
        case "caret-down":case "chevron-down":size=24;outlined=true;data="M 0 0 m 6 9 6 6 6 -6";break; // Lucide: chevron-down
        case "x":size=24;outlined=true;data="M 18 6 6 18 M 0 0 m 6 6 12 12";break; // Lucide: x
        case "left-desktop":size=24;outlined=true;data="M 15 21 v -8 a 1 1 0 0 0 -1 -1 h -4 a 1 1 0 0 0 -1 1 v 8 M 3 10 a 2 2 0 0 1 .709 -1.528 l 7 -6 a 2 2 0 0 1 2.582 0 l 7 6 A 2 2 0 0 1 21 10 v 9 a 2 2 0 0 1 -2 2 H 5 a 2 2 0 0 1 -2 -2 z";break; // Lucide: house
        case "left-save":size=24;outlined=true;data="M 15.2 3 a 2 2 0 0 1 1.4 .6 l 3.8 3.8 a 2 2 0 0 1 .6 1.4 V 19 a 2 2 0 0 1 -2 2 H 5 a 2 2 0 0 1 -2 -2 V 5 a 2 2 0 0 1 2 -2 z M 17 21 v -7 a 1 1 0 0 0 -1 -1 H 8 a 1 1 0 0 0 -1 1 v 7 M 7 3 v 4 a 1 1 0 0 0 1 1 h 7";break; // Lucide: save
        case "header-wifi-off":size=24;outlined=true;data="M 12 20 h .01 M 8.5 16.429 a 5 5 0 0 1 7 0 M 5 12.859 a 10 10 0 0 1 5.17 -2.69 M 19 12.859 a 10 10 0 0 0 -2.007 -1.523 M 2 8.82 a 15 15 0 0 1 4.177 -2.643 M 22 8.82 a 15 15 0 0 0 -11.288 -3.764 M 0 0 m 2 2 20 20";break; // Lucide: wifi-off
        case "header-ethernet":size=24;outlined=true;data="M 10 8 v 1 M 14 8 v 1 M 18 8 v 1 M 19 17 a 2 2 0 0 0 -1.765 1.059 l -.47 .882 A 2 2 0 0 1 15 20 H 9 a 2 2 0 0 1 -1.765 -1.059 l -.47 -.882 A 2 2 0 0 0 5 17 H 4 a 2 2 0 0 1 -2 -2 V 6 a 2 2 0 0 1 2 -2 h 16 a 2 2 0 0 1 2 2 v 9 a 2 2 0 0 1 -2 2 z M 6 8 v 1";break; // Lucide: ethernet-port
        case "header-control":case "sliders":size=24;outlined=true;data="M 10 5 H 3 M 12 19 H 3 M 14 3 v 4 M 16 17 v 4 M 21 12 h -9 M 21 19 h -5 M 21 5 h -7 M 8 10 v 4 M 8 12 H 3";break; // Lucide: sliders-horizontal
        case "plus":size=24;outlined=true;data="M 5 12 h 14 M 12 5 v 14";break; // Lucide: plus
        case "bluetooth":size=24;outlined=true;data="M 0 0 m 7 7 10 10 -5 5 V 2 l 5 5 L 7 17";break; // Lucide: bluetooth
        case "folder-transfer":size=24;outlined=true;data="M 9 20 H 4 a 2 2 0 0 1 -2 -2 V 5 a 2 2 0 0 1 2 -2 h 3.9 a 2 2 0 0 1 1.69 .9 l .81 1.2 a 2 2 0 0 0 1.67 .9 H 20 a 2 2 0 0 1 2 2 v .5 M 12 10 v 4 h 4 M 0 0 m 12 14 1.535 -1.605 a 5 5 0 0 1 8 1.5 M 22 22 v -4 h -4 M 0 0 m 22 18 -1.535 1.605 a 5 5 0 0 1 -8 -1.5";break; // Lucide: folder-sync
        case "note":size=24;outlined=true;data="M 13.4 2 H 6 a 2 2 0 0 0 -2 2 v 16 a 2 2 0 0 0 2 2 h 12 a 2 2 0 0 0 2 -2 v -7.4 M 2 6 h 4 M 2 10 h 4 M 2 14 h 4 M 2 18 h 4 M 21.378 5.626 a 1 1 0 1 0 -3.004 -3.004 l -5.01 5.012 a 2 2 0 0 0 -.506 .854 l -.837 2.87 a .5 .5 0 0 0 .62 .62 l 2.87 -.837 a 2 2 0 0 0 .854 -.506 z";break; // Lucide: notebook-pen
        case "key-sound":size=24;outlined=true;data="M 10 8 h .01 M 12 12 h .01 M 14 8 h .01 M 16 12 h .01 M 18 8 h .01 M 6 8 h .01 M 7 16 h 10 M 8 12 h .01 M 4 4 H 20 A 2 2 0 0 1 22 6 V 18 A 2 2 0 0 1 20 20 H 4 A 2 2 0 0 1 2 18 V 6 A 2 2 0 0 1 4 4 Z";break; // Lucide: keyboard
        case "wallpaper-sound":size=24;outlined=true;data="M 2 10 v 3 M 6 6 v 11 M 10 3 v 18 M 14 8 v 7 M 18 5 v 13 M 22 10 v 3";break; // Lucide: audio-lines
        case "boot-direct":size=24;outlined=true;data="M 12 2 v 10 M 18.4 6.6 a 9 9 0 1 1 -12.77 .04";break; // Lucide: power
        case "desktop-layout":size=24;outlined=true;data="M 5 3 H 19 A 2 2 0 0 1 21 5 V 19 A 2 2 0 0 1 19 21 H 5 A 2 2 0 0 1 3 19 V 5 A 2 2 0 0 1 5 3 Z M 3 9 h 18 M 9 21 V 9";break; // Lucide: panels-top-left
        case "child":size=24;outlined=true;data="M 10 16 c .5 .3 1.2 .5 2 .5 s 1.5 -.2 2 -.5 M 15 12 h .01 M 19.38 6.813 A 9 9 0 0 1 20.8 10.2 a 2 2 0 0 1 0 3.6 9 9 0 0 1 -17.6 0 2 2 0 0 1 0 -3.6 A 9 9 0 0 1 12 3 c 2 0 3.5 1.1 3.5 2.5 s -.9 2.5 -2 2.5 c -.8 0 -1.5 -.4 -1.5 -1 M 9 12 h .01";break; // Lucide: baby
        case "apps":case "layout-grid":size=24;outlined=true;data="M 4 3 H 9 A 1 1 0 0 1 10 4 V 9 A 1 1 0 0 1 9 10 H 4 A 1 1 0 0 1 3 9 V 4 A 1 1 0 0 1 4 3 Z M 15 3 H 20 A 1 1 0 0 1 21 4 V 9 A 1 1 0 0 1 20 10 H 15 A 1 1 0 0 1 14 9 V 4 A 1 1 0 0 1 15 3 Z M 15 14 H 20 A 1 1 0 0 1 21 15 V 20 A 1 1 0 0 1 20 21 H 15 A 1 1 0 0 1 14 20 V 15 A 1 1 0 0 1 15 14 Z M 4 14 H 9 A 1 1 0 0 1 10 15 V 20 A 1 1 0 0 1 9 21 H 4 A 1 1 0 0 1 3 20 V 15 A 1 1 0 0 1 4 14 Z";break; // Lucide: layout-grid
        case "clock":size=24;outlined=true;data="M 22 12 A 10 10 0 1 0 2 12 A 10 10 0 1 0 22 12 Z M 12 6 v 6 l 4 2";break; // Lucide: clock
        case "weather":case "cloud-sun":size=24;outlined=true;data="M 12 2 v 2 M 0 0 m 4.93 4.93 1.41 1.41 M 20 12 h 2 M 0 0 m 19.07 4.93 -1.41 1.41 M 15.947 12.65 a 4 4 0 0 0 -5.925 -4.128 M 13 22 H 7 a 5 5 0 1 1 4.9 -6 H 13 a 3 3 0 0 1 0 6 Z";break; // Lucide: cloud-sun
        case "check":size=24;outlined=true;data="M 20 6 9 17 l -5 -5";break; // Lucide: check
        case "check-circle":case "circle-check":size=24;outlined=true;data="M 22 12 A 10 10 0 1 0 2 12 A 10 10 0 1 0 22 12 Z M 0 0 m 16 9 -5.5 5.5 L 8 12";break; // Lucide: circle-check
        case "arrow-left":size=24;outlined=true;data="M 0 0 m 12 19 -7 -7 7 -7 M 19 12 H 5";break; // Lucide: arrow-left
        case "chevron-up":size=24;outlined=true;data="M 0 0 m 18 15 -6 -6 -6 6";break; // Lucide: chevron-up
        case "move":size=24;outlined=true;data="M 12 2 v 20 M 0 0 m 15 19 -3 3 -3 -3 M 0 0 m 19 9 3 3 -3 3 M 2 12 h 20 M 0 0 m 5 9 -3 3 3 3 M 0 0 m 9 5 3 -3 3 3";break; // Lucide: move
        case "replace":size=24;outlined=true;data="M 14 4 a 1 1 0 0 1 1 -1 M 15 10 a 1 1 0 0 1 -1 -1 M 21 4 a 1 1 0 0 0 -1 -1 M 21 9 a 1 1 0 0 1 -1 1 M 0 0 m 3 7 3 3 3 -3 M 6 10 V 5 a 2 2 0 0 1 2 -2 h 2 M 4 14 H 9 A 1 1 0 0 1 10 15 V 20 A 1 1 0 0 1 9 21 H 4 A 1 1 0 0 1 3 20 V 15 A 1 1 0 0 1 4 14 Z";break; // Lucide: replace
        case "pin-off":size=24;outlined=true;data="M 12 17 v 5 M 15 9.34 V 7 a 1 1 0 0 1 1 -1 2 2 0 0 0 0 -4 H 7.89 M 0 0 m 2 2 20 20 M 9 9 v 1.76 a 2 2 0 0 1 -1.11 1.79 l -1.78 .9 A 2 2 0 0 0 5 15.24 V 16 a 1 1 0 0 0 1 1 h 11";break; // Lucide: pin-off
        case "pin":size=24;outlined=true;data="M 12 17 v 5 M 9 10.76 a 2 2 0 0 1 -1.11 1.79 l -1.78 .9 A 2 2 0 0 0 5 15.24 V 16 a 1 1 0 0 0 1 1 h 12 a 1 1 0 0 0 1 -1 v -.76 a 2 2 0 0 0 -1.11 -1.79 l -1.78 -.9 A 2 2 0 0 1 15 10.76 V 7 a 1 1 0 0 1 1 -1 2 2 0 0 0 0 -4 H 8 a 2 2 0 0 0 0 4 1 1 0 0 1 1 1 z";break; // Lucide: pin
        case "eye-off":size=24;outlined=true;data="M 10.733 5.076 a 10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1 -1.444 2.49 M 14.084 14.158 a 3 3 0 0 1 -4.242 -4.242 M 17.479 17.499 a 10.75 10.75 0 0 1 -15.417 -5.151 1 1 0 0 1 0 -.696 10.75 10.75 0 0 1 4.446 -5.143 M 0 0 m 2 2 20 20";break; // Lucide: eye-off
        case "trash":size=24;outlined=true;data="M 10 11 v 6 M 14 11 v 6 M 19 6 v 14 a 2 2 0 0 1 -2 2 H 7 a 2 2 0 0 1 -2 -2 V 6 M 3 6 h 18 M 8 6 V 4 a 2 2 0 0 1 2 -2 h 4 a 2 2 0 0 1 2 2 v 2";break; // Lucide: trash
        case "app-window":size=24;outlined=true;data="M 4 4 H 20 A 2 2 0 0 1 22 6 V 18 A 2 2 0 0 1 20 20 H 4 A 2 2 0 0 1 2 18 V 6 A 2 2 0 0 1 4 4 Z M 10 4 v 4 M 2 8 h 20 M 6 4 v 4";break; // Lucide: app-window
        case "upload":size=24;outlined=true;data="M 12 3 v 12 M 0 0 m 17 8 -5 -5 -5 5 M 21 15 v 4 a 2 2 0 0 1 -2 2 H 5 a 2 2 0 0 1 -2 -2 v -4";break; // Lucide: upload
        case "play":size=24;outlined=true;data="M 5 5 a 2 2 0 0 1 3.008 -1.728 l 11.997 6.998 a 2 2 0 0 1 .003 3.458 l -12 7 A 2 2 0 0 1 5 19 z";break; // Lucide: play
        case "grid-2x2-check":size=24;outlined=true;data="M 12 3 v 17 a 1 1 0 0 1 -1 1 H 5 a 2 2 0 0 1 -2 -2 V 5 a 2 2 0 0 1 2 -2 h 14 a 2 2 0 0 1 2 2 v 6 a 1 1 0 0 1 -1 1 H 3 M 0 0 m 16 19 2 2 4 -4";break; // Lucide: grid-2x2-check
        case "qr-code":size=24;outlined=true;data="M 4 3 H 7 A 1 1 0 0 1 8 4 V 7 A 1 1 0 0 1 7 8 H 4 A 1 1 0 0 1 3 7 V 4 A 1 1 0 0 1 4 3 Z M 17 3 H 20 A 1 1 0 0 1 21 4 V 7 A 1 1 0 0 1 20 8 H 17 A 1 1 0 0 1 16 7 V 4 A 1 1 0 0 1 17 3 Z M 4 16 H 7 A 1 1 0 0 1 8 17 V 20 A 1 1 0 0 1 7 21 H 4 A 1 1 0 0 1 3 20 V 17 A 1 1 0 0 1 4 16 Z M 21 16 h -3 a 2 2 0 0 0 -2 2 v 3 M 21 21 v .01 M 12 7 v 3 a 2 2 0 0 1 -2 2 H 7 M 3 12 h .01 M 12 3 h .01 M 12 16 v .01 M 16 12 h 1 M 21 12 v .01 M 12 21 v -1";break; // Lucide: qr-code
        case "lock-keyhole":size=24;outlined=true;data="M 13 16 A 1 1 0 1 0 11 16 A 1 1 0 1 0 13 16 Z M 5 10 H 19 A 2 2 0 0 1 21 12 V 20 A 2 2 0 0 1 19 22 H 5 A 2 2 0 0 1 3 20 V 12 A 2 2 0 0 1 5 10 Z M 7 10 V 7 a 5 5 0 0 1 10 0 v 3";break; // Lucide: lock-keyhole
        case "timer":size=24;outlined=true;data="M 10 2 L 14 2 M 12 14 L 15 11 M 20 14 A 8 8 0 1 0 4 14 A 8 8 0 1 0 20 14 Z";break; // Lucide: timer
        case "shield-check":size=24;outlined=true;data="M 20 13 c 0 5 -3.5 7.5 -7.66 8.95 a 1 1 0 0 1 -.67 -.01 C 7.5 20.5 4 18 4 13 V 6 a 1 1 0 0 1 1 -1 c 2 0 4.5 -1.2 6.24 -2.72 a 1.17 1.17 0 0 1 1.52 0 C 14.51 3.81 17 5 19 5 a 1 1 0 0 1 1 1 z M 0 0 m 9 12 2 2 4 -4";break; // Lucide: shield-check
        case "arrow-right":size=24;outlined=true;data="M 5 12 h 14 M 0 0 m 12 5 7 7 -7 7";break; // Lucide: arrow-right
        case "delete":size=24;outlined=true;data="M 10 5 a 2 2 0 0 0 -1.344 .519 l -6.328 5.74 a 1 1 0 0 0 0 1.481 l 6.328 5.741 A 2 2 0 0 0 10 19 h 10 a 2 2 0 0 0 2 -2 V 7 a 2 2 0 0 0 -2 -2 z M 0 0 m 12 9 6 6 M 0 0 m 18 9 -6 6";break; // Lucide: delete
        case "loader-circle":size=24;outlined=true;data="M 21 12 a 9 9 0 1 1 -6.219 -8.56";break; // Lucide: loader-circle
        case "circle-exclamation-point":size=24;outlined=true;data="M 22 12 A 10 10 0 1 0 2 12 A 10 10 0 1 0 22 12 Z M 12 8 L 12 12 M 12 16 L 12.01 16";break; // Lucide: circle-exclamation-point
        case "unplug":size=24;outlined=true;data="M 0 0 m 19 5 3 -3 M 0 0 m 2 22 3 -3 M 6.3 20.3 a 2.4 2.4 0 0 0 3.4 0 L 12 18 l -6 -6 -2.3 2.3 a 2.4 2.4 0 0 0 0 3.4 Z M 7.5 13.5 10 11 M 10.5 16.5 13 14 M 0 0 m 12 6 6 6 2.3 -2.3 a 2.4 2.4 0 0 0 0 -3.4 l -2.6 -2.6 a 2.4 2.4 0 0 0 -3.4 0 Z";break; // Lucide: unplug
        case "square":size=24;outlined=true;data="M 5 3 H 19 A 2 2 0 0 1 21 5 V 19 A 2 2 0 0 1 19 21 H 5 A 2 2 0 0 1 3 19 V 5 A 2 2 0 0 1 5 3 Z";break; // Lucide: square
        case "square-check":size=24;outlined=true;data="M 5 3 H 19 A 2 2 0 0 1 21 5 V 19 A 2 2 0 0 1 19 21 H 5 A 2 2 0 0 1 3 19 V 5 A 2 2 0 0 1 5 3 Z M 0 0 m 16 9 -5.5 5.5 L 8 12";break; // Lucide: square-check
        default:data="";
        }pathData=data;path=PathParser.createPathFromPathData(data);solidSun=false;viewport=size;stroke=outlined;
    }
    static org.json.JSONObject description(String name)throws org.json.JSONException{QuickControlIcon icon=new QuickControlIcon(name);return new org.json.JSONObject().put("path",icon.pathData).put("size",icon.viewport).put("outline",icon.stroke).put("strokeWidth",STROKE_WIDTH).put("solidSun",icon.solidSun);}
    void color(int value){fixedColor=value;color=value;invalidateSelf();}
    // DrawableCompat calls the drawable's tint API directly on newer Android versions.
    @Override public void setTintList(ColorStateList value){tint=value;invalidateSelf();}
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] states){boolean focus=false,window=false,enabled=false;for(int value:states){if(value==android.R.attr.state_enabled)enabled=true;if(value==android.R.attr.state_focused||value==android.R.attr.state_activated)focus=true;if(value==android.R.attr.state_window_focused)window=true;}color=fixedColor!=null?fixedColor:!enabled?UiPalette.FAINT:focus&&window?UiPalette.INK:UiPalette.TEXT;invalidateSelf();return true;}
    @Override public void draw(Canvas canvas){if(path==null)return;int save=canvas.save();Rect b=getBounds();float scale=Math.min(b.width()/viewport,b.height()/viewport);canvas.translate(b.left+(b.width()-viewport*scale)/2,b.top+(b.height()-viewport*scale)/2);canvas.scale(scale,scale);int ink=tint==null?color:tint.getColorForState(getState(),tint.getDefaultColor());paint.setColor(ink);paint.setAlpha(Color.alpha(ink)*alpha/255);paint.setStyle(stroke?Paint.Style.STROKE:Paint.Style.FILL);paint.setStrokeWidth(STROKE_WIDTH);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);canvas.drawPath(path,paint);if(solidSun){paint.setStyle(Paint.Style.FILL);canvas.drawCircle(12,12,4.4f,paint);}canvas.restoreToCount(save);}
    @Override public int getIntrinsicWidth(){return 24;}
    @Override public int getIntrinsicHeight(){return 24;}
    @Override public void setAlpha(int alpha){this.alpha=alpha;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
