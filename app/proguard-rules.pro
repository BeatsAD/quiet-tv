# Offline license WebView uses no Javascript interface or reflective app bridge.

# API19's connection-local BCJSSE provider registers JCA implementations by class name.
# The condition keeps standard builds, which do not include BCJSSE, unaffected.
-if class org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-keep,allowoptimization class org.bouncycastle.jcajce.provider.** { *; }
-if class org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-keep,allowoptimization class org.bouncycastle.jce.provider.** { *; }
# Keep Java-version bridges separate: inlining JsseUtils_8 into PKIXUtil fails Dalvik verification.
-if class org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-keep class org.bouncycastle.jsse.provider.** { *; }
# Optional LDAP stores require JNDI, which Android does not ship. Wallpaper uses local roots.
-dontwarn javax.naming.**
