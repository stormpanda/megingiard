# MuntashirAkon ADB and Crypto libraries (libadb-android, spake2-android)
# Preserves internal classes, methods, and fields needed for reflection-based
# cache clearing (SslUtils.sslContext), JNI bindings, and TLS handshake.
-keep class io.github.muntashirakon.** { *; }
-keepclassmembers class io.github.muntashirakon.** { *; }
-dontwarn io.github.muntashirakon.**

# sun-security-android: X.509 certificate and extension generation
# Uses dynamic reflection and string-based class lookups (e.g. OIDMap.getClass).
-keep class android.sun.security.** { *; }
-keepclassmembers class android.sun.security.** { *; }
-dontwarn android.sun.security.**
