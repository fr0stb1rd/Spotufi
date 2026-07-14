# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations and generic signatures
-keepattributes *Annotation*,Signature,Exceptions

# Kotlin serialization
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.metrolist.**$$serializer { *; }
-keepclassmembers class com.metrolist.** {
    *** Companion;
}
-keepclasseswithmembers class com.metrolist.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Ktor
-keep class io.ktor.** { *; }
-keep class io.ktor.client.engine.** { *; }
-keep class io.ktor.serialization.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }

# ExoPlayer / Media3 — ships its own consumer rules; only keep what we call via reflection
-dontwarn androidx.media3.**

# Coil
-keep class coil3.** { *; }
-dontwarn coil3.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Core library desugaring
-dontwarn java8.**
-keep class java8.** { *; }

# Keep data classes for serialization
-keep class com.metrolist.music.data.entity.** { *; }
-keep class com.metrolist.music.models.** { *; }
-keep class com.metrolist.spotify.models.** { *; }
-keep class com.metrolist.innertube.models.** { *; }
-keep class io.github.sekademi.spotufi.data.entity.** { *; }

# WebView JavaScript interfaces (SpotifyWebPlayer bridge)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep custom Application class and entry points
-keep class io.github.sekademi.spotufi.MyApplication { *; }
-keep class io.github.sekademi.spotufi.MainActivity { *; }

# Timber
-dontwarn org.jetbrains.annotations.**

# R8 missing classes (transitive deps from NewPipeExtractor / Coil / OkHttp)
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn javax.script.ScriptEngineFactory
