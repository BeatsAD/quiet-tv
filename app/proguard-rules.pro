# The SPAKE2 entry point is checked by name before wireless pairing is offered.
# Keep JNI bindings supplied by spake2; Conscrypt supplies its own consumer rules.
-keep class io.github.muntashirakon.crypto.spake2.Spake2Context { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Offline license WebView uses no Javascript interface or reflective app bridge.
