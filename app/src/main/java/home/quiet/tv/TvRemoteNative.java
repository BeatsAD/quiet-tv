package home.quiet.tv;

/** Loaded only after the signed ARM64 component has been installed. */
final class TvRemoteNative {
    static native String call(String input);
}
