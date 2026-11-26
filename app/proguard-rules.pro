# Offline license WebView uses no Javascript interface or reflective app bridge.

# Keep the API16 software adapter behind a verifier-safe boundary on API14/15.
-keep,allowobfuscation class home.quiet.tv.WallpaperService$Api16 { *; }
-keep,allowobfuscation interface home.quiet.tv.WallpaperService$SoftwarePlayer { *; }

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

# SMB uses NTLM and simple event handlers. Kerberos and expression filters are optional JVM paths.
-dontwarn org.ietf.jgss.**
-dontwarn javax.el.**
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep @interface net.engio.mbassy.listener.Handler
-keepclassmembers class com.hierynomus.smbj.** {
    @net.engio.mbassy.listener.Handler <methods>;
}
