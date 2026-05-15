# =====================================================================
# Megingiard ProGuard / R8 keep rules
# =====================================================================
#
# General principles:
#  - kotlinx.serialization needs the @Serializable classes preserved
#    along with their synthetic Companion + $serializer members.
#  - Components declared in AndroidManifest.xml are referenced by name
#    from the framework and must stay un-obfuscated and un-shrunk.
#  - Reflection into android.os.ServiceManager (DirectMirrorSurfaceBridge)
#    targets a framework class — no keep rule needed for our own code.

# Stack trace readability for release crashes:
#  - LineNumberTable: preserves line numbers so `retrace` can pinpoint exact lines.
#  - SourceFile:      kept as an attribute so the format is valid; the value is
#                     replaced with the constant "SourceFile" by the next rule,
#                     which intentionally hides real source file names from
#                     reverse-engineering while keeping line numbers functional.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------
# Android components referenced by name in AndroidManifest.xml
# ---------------------------------------------------------------------
-keep class com.stormpanda.megingiard.MainActivity { *; }
-keep class com.stormpanda.megingiard.CaptureRequestActivity { *; }
-keep class com.stormpanda.megingiard.mirror.ScreenCaptureService { *; }

# ---------------------------------------------------------------------
# kotlinx.serialization
# ---------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep `Companion` object fields of serializable classes.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all @Serializable classes from our packages so polymorphic
# (sealed) hierarchies survive shrinking.
-keep,includedescriptorclasses class com.stormpanda.megingiard.**$$serializer { *; }
-keepclassmembers class com.stormpanda.megingiard.** {
    *** Companion;
}
-keepclasseswithmembers class com.stormpanda.megingiard.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.stormpanda.megingiard.** { *; }

# ---------------------------------------------------------------------
# Compose / lifecycle reflective lookups
# ---------------------------------------------------------------------
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# ---------------------------------------------------------------------
# Suppress noisy warnings
# ---------------------------------------------------------------------
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**

# Conscrypt references legacy SSL parameter classes that don't exist on
# modern Android. They're only used on pre-KitKat / hidden-API paths we
# never hit, but R8 still chokes without explicit dontwarn rules.
-dontwarn com.android.org.conscrypt.SSLParametersImpl
-dontwarn org.apache.harmony.xnet.provider.jsse.SSLParametersImpl