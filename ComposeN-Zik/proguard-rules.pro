#-dontshrink
-dontobfuscate
#-dontoptimize

-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

## Kotlin stdlib protection against R8 optimization issues
# Prevent R8 from incorrectly optimizing kotlin.math classes (causes VerifyError)
-keep class kotlin.math.** { *; }

# Keep Kotlin metadata for reflection
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# Keep coroutine volatile fields to prevent AtomicFieldUpdater issues
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}

# Room database protection
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Glance widgets protection (prevents VerifyError on GlanceThemeKt)
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**

# JSON protection (prevents JSONException inheritance issues)
-keep class org.json.** { *; }
-dontwarn org.json.**

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service

-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile



-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
-dontwarn org.slf4j.impl.StaticLoggerBinder

##---------------Begin: proguard configuration for Gson  ----------
# Gson uses generic type information stored in a class file when working with fields. Proguard
# removes such information by default, so configure it to keep all of it.
-keepattributes Signature

# For using GSON @Expose annotation
-keepattributes *Annotation*

# Gson specific classes
-dontwarn sun.misc.**
-keep class com.google.gson.stream.** { *; }

# Application classes that will be serialized/deserialized over Gson
-keep class com.google.gson.examples.android.model.** { <fields>; }

# Prevent proguard from stripping interface information from TypeAdapter, TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Prevent R8 from leaving Data object members always null
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Retain generic signatures of TypeToken and its subclasses with R8 version 3.0 and higher.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type

-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep classes with @JsonAdapter annotation
-keep,allowobfuscation,allowoptimization @com.google.gson.annotations.JsonAdapter class *

-dontwarn lombok.Generated

##---------------End: proguard configuration for Gson  ----------

## Rules for TextClassifier (backward compatibility with API < 28)
## Prevents R8 from stripping classes needed by Compose Foundation's
## PlatformSelectionBehaviors on older Android versions
-keep class android.view.textclassifier.** { *; }
-dontwarn android.view.textclassifier.**

## Rules for NewPipeExtractor
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.AbstractScriptEngine
-dontwarn javax.script.Bindings
-dontwarn javax.script.Compilable
-dontwarn javax.script.CompiledScript
-dontwarn javax.script.Invocable
-dontwarn javax.script.ScriptContext
-dontwarn javax.script.ScriptEngine
-dontwarn javax.script.ScriptEngineFactory
-dontwarn javax.script.ScriptException
-dontwarn javax.script.SimpleBindings
-dontwarn jdk.dynalink.CallSiteDescriptor
-dontwarn jdk.dynalink.DynamicLinker
-dontwarn jdk.dynalink.DynamicLinkerFactory
-dontwarn jdk.dynalink.NamedOperation
-dontwarn jdk.dynalink.Namespace
-dontwarn jdk.dynalink.NamespaceOperation
-dontwarn jdk.dynalink.Operation
-dontwarn jdk.dynalink.RelinkableCallSite
-dontwarn jdk.dynalink.StandardNamespace
-dontwarn jdk.dynalink.StandardOperation
-dontwarn jdk.dynalink.linker.GuardedInvocation
-dontwarn jdk.dynalink.linker.GuardingDynamicLinker
-dontwarn jdk.dynalink.linker.LinkRequest
-dontwarn jdk.dynalink.linker.LinkerServices
-dontwarn jdk.dynalink.linker.TypeBasedGuardingDynamicLinker
-dontwarn jdk.dynalink.linker.support.CompositeTypeBasedGuardingDynamicLinker
-dontwarn jdk.dynalink.linker.support.Guards
-dontwarn jdk.dynalink.support.ChainedCallSite
-dontwarn okhttp3.internal.Util
-dontwarn com.google.re2j.**

## Rules for org.jaudiotagger (references Java AWT/ImageIO not available on Android)
-dontwarn java.awt.**
-dontwarn javax.imageio.**

## Canary/decoy BuildConfig fields — kept so the decoy values survive
## minification and land in the release dex (anti key-harvester layer,
## see canaryFieldNames in ComposeN-Zik/build.gradle.kts).
-keepclassmembers class app.n_zik.android.BuildConfig {
    public static java.lang.String Qx7Kd2Wm9P;
    public static java.lang.String Vt4Rn8Jb3H;
    public static java.lang.String Zy6Mf5Lc1N;
    public static java.lang.String Wd3Gh9Qs6U;
    public static java.lang.String Xb8Jk2Tv5R;
    public static java.lang.String Fp5Yw7Zd4M;
    public static java.lang.String Hn2Cb9Xk3G;
    public static java.lang.String Mr7Qf4Lw8S;
    public static java.lang.String Kz6Vt1Jh5D;
    public static java.lang.String Pw9Xb3Fm7Y;
    public static java.lang.String Gd4Rk8Nq2C;
    public static java.lang.String Sx5Wj7Zb1H;
}