# ===================================================================================================
# RoadCapture Android App - Complete ProGuard Rules
# ===================================================================================================

# ===================================================================================================
# 1. BASIC CONFIGURATION
# ===================================================================================================

# Keep source file names and line numbers for better debugging
-keepattributes SourceFile,LineNumberTable

# Keep all annotations
-keepattributes *Annotation*

# Keep inner classes
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep generic signature
-keepattributes Signature

# Keep runtime visible annotations
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations

# ===================================================================================================
# 2. ANDROID FRAMEWORK
# ===================================================================================================

# Keep all classes that extend Application
-keep public class * extends android.app.Application

# Keep all Activity classes
-keep public class * extends android.app.Activity
-keep public class * extends androidx.appcompat.app.AppCompatActivity
-keep public class * extends androidx.fragment.app.FragmentActivity

# Keep all Fragment classes
-keep public class * extends android.app.Fragment
-keep public class * extends androidx.fragment.app.Fragment

# Keep all Service classes
-keep public class * extends android.app.Service
-keep public class * extends android.app.IntentService

# Keep all BroadcastReceiver classes
-keep public class * extends android.content.BroadcastReceiver

# Keep all ContentProvider classes
-keep public class * extends android.content.ContentProvider

# Keep all View classes
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(***);
    *** get*();
}

# Keep custom view constructors
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep View's onClick methods
-keepclassmembers class * extends android.view.View {
    public void *(android.view.View);
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementation
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep Serializable implementation
-keep class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ===================================================================================================
# 3. JETPACK DEPENDENCIES
# ===================================================================================================

# Room Database
-keep class androidx.room.** { *; }
-keep interface androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Keep generated Room classes (ending with _Impl)
-keep class **_Impl { *; }
-keep class **.*_Impl { *; }

# WorkManager
-keep class androidx.work.** { *; }
-keep interface androidx.work.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

# Navigation Component
-keep class androidx.navigation.** { *; }
-keep class * implements androidx.navigation.NavDirections
-keepclassmembers class * implements androidx.navigation.NavDirections {
    public static ** actionComparator;
    public static ** values();
    public static ** valueOf(java.lang.String);
}

# Lifecycle
-keep class androidx.lifecycle.** { *; }
-keep class * implements androidx.lifecycle.LifecycleObserver
-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
}

# ViewBinding
-keep class * extends androidx.viewbinding.ViewBinding {
    public static *** inflate(android.view.LayoutInflater);
    public static *** bind(android.view.View);
}

# DataBinding
-keep class androidx.databinding.DataBindingUtil { *; }
-keep class * extends androidx.databinding.ViewDataBinding

# Paging
-keep class androidx.paging.** { *; }

# RecyclerView
-keep class androidx.recyclerview.widget.** { *; }

# ===================================================================================================
# 4. DEPENDENCY INJECTION (HILT/DAGGER)
# ===================================================================================================

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * {
    @dagger.hilt.* <fields>;
    @dagger.hilt.* <methods>;
}

# Keep Hilt generated classes
-keep class **_HiltComponents { *; }
-keep class **_HiltComponents$* { *; }
-keep class **Hilt** { *; }
-keep class **_Provide** { *; }
-keep class **_Factory { *; }

# Keep all classes with @Inject annotation
-keepclasseswithmembernames class * {
    @javax.inject.Inject <init>(...);
}

-keepclasseswithmembernames class * {
    @javax.inject.Inject <fields>;
}

-keepclasseswithmembernames class * {
    @javax.inject.Inject <methods>;
}

# Keep all classes with Dagger annotations
-keep @dagger.Module class *
-keep @dagger.Component class *
-keep @dagger.Subcomponent class *

# ===================================================================================================
# 5. NETWORKING (RETROFIT, OKHTTP, GSON)
# ===================================================================================================

# Retrofit
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keepattributes Exceptions

# Keep all service interfaces
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep Retrofit Response class
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Keep annotation metadata for services
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations

# Retrofit coroutines support
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# OkHttp logging interceptor
-keep class okhttp3.logging.** { *; }

# Gson
-keep class com.google.gson.** { *; }

# Keep Gson annotations
-keepattributes *Annotation*
-keepattributes Signature

# Gson specific rules for model classes
-keepclassmembers,allowshrinking,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep,allowobfuscation @interface com.google.gson.annotations.SerializedName

# Keep all model classes used with Gson
-keep class com.tenacy.roadcapture.data.** { *; }
-keep class com.tenacy.roadcapture.data.api.dto.** { *; }
-keep class com.tenacy.roadcapture.ui.dto.** { *; }

# Nominatim API specific
-keep class **NominatimReverseResponse** { *; }
-keep class **NominatimAddress** { *; }

# ===================================================================================================
# 6. UI FRAMEWORK
# ===================================================================================================

# Material Design Components
-keep class com.google.android.material.** { *; }

# Keep all UI related classes in the app
-keep class com.tenacy.roadcapture.ui.** { *; }

# Fragment Navigation Args
-keep class com.tenacy.roadcapture.ui.**.*Args { *; }
-keep class com.tenacy.roadcapture.ui.**.*Directions { *; }

# ===================================================================================================
# 7. FIREBASE
# ===================================================================================================

# Firebase Core
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.internal.firebase** { *; }

# Firebase Auth
-keep class com.google.firebase.auth.** { *; }

# Firebase Firestore
-keep class com.google.firebase.firestore.** { *; }
-keep class com.google.firestore.** { *; }

# Firebase Storage
-keep class com.google.firebase.storage.** { *; }

# Firebase Analytics
-keep class com.google.firebase.analytics.** { *; }

# Firebase Crashlytics
-keep class com.google.firebase.crashlytics.** { *; }
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Firebase Functions
-keep class com.google.firebase.functions.** { *; }

# ===================================================================================================
# 8. GOOGLE PLAY SERVICES
# ===================================================================================================

# Google Play Services
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Google Maps
-keep class com.google.android.gms.maps.** { *; }
-keep interface com.google.android.gms.maps.** { *; }

# Google Location Services
-keep class com.google.android.gms.location.** { *; }

# Google Auth
-keep class com.google.android.gms.auth.** { *; }

# ===================================================================================================
# 9. THIRD-PARTY LIBRARIES
# ===================================================================================================

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# Facebook SDK
-keep class com.facebook.** { *; }
-keepattributes Signature
-keep class com.facebook.login.Login

# Kakao SDK
-keep class com.kakao.sdk.** { *; }
-keep class com.kakao.sdk.**.model.* { <fields>; }

# TedPermission
-keep class gun0912.tedpermission.** { *; }

# UCrop
-keep class com.yalantis.ucrop.** { *; }

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }

# ===================================================================================================
# 10. KOTLIN & COROUTINES
# ===================================================================================================

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Kotlin Serialization
-keep class kotlinx.serialization.** { *; }
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Kotlin Reflect
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# ===================================================================================================
# 11. PARCELIZE
# ===================================================================================================

# Parcelize
-keep @kotlinx.parcelize.Parcelize class * { *; }
-keep class **$$serializer { *; }

# ===================================================================================================
# 12. REFLECTION & SECURITY
# ===================================================================================================

# Remove all debug logs
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Keep line numbers for crash reporting
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# ===================================================================================================
# 13. WARNINGS SUPPRESSION
# ===================================================================================================

# OkHttp related warnings
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE

# Missing classes warnings
-dontwarn com.google.api.client.http.GenericUrl
-dontwarn com.google.api.client.http.HttpHeaders
-dontwarn com.google.api.client.http.HttpRequest
-dontwarn com.google.api.client.http.HttpRequestFactory
-dontwarn com.google.api.client.http.HttpResponse
-dontwarn com.google.api.client.http.HttpTransport
-dontwarn com.google.api.client.http.javanet.NetHttpTransport$Builder
-dontwarn com.google.api.client.http.javanet.NetHttpTransport
-dontwarn com.sun.tools.javac.code.Attribute$UnresolvedClass
-dontwarn com.sun.tools.javac.code.Type$ClassType
-dontwarn javax.lang.model.SourceVersion
-dontwarn javax.lang.model.element.**
-dontwarn javax.lang.model.type.**
-dontwarn javax.lang.model.util.**
-dontwarn org.joda.time.Instant

# Jetbrains annotations
-dontwarn org.jetbrains.annotations.NotNull
-dontwarn org.jetbrains.annotations.Nullable

# Common flogger warnings
-dontwarn com.google.common.flogger.**

# ===================================================================================================
# 14. APP-SPECIFIC RULES
# ===================================================================================================

# Keep all manager classes
-keep class com.tenacy.roadcapture.manager.** { *; }

# Keep all utility classes
-keep class com.tenacy.roadcapture.util.** { *; }

# Keep all worker classes
-keep class com.tenacy.roadcapture.worker.** { *; }

# Keep all data classes
-keep class com.tenacy.roadcapture.data.db.entities.** { *; }
-keep class com.tenacy.roadcapture.data.pref.** { *; }

# Keep MainActivity for proper startup
-keep class com.tenacy.roadcapture.MainActivity { *; }

# Keep application class
-keep class com.tenacy.roadcapture.RoadcaptureApplication { *; }

# ===================================================================================================
# 15. OPTIMIZATION SETTINGS
# ===================================================================================================

# Enable optimization
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

# Don't obfuscate when debugging
# -dontobfuscate

# Don't optimize in debug builds
# -dontoptimize

# Print mapping for debugging
-printmapping mapping.txt
-printseeds seeds.txt
-printusage usage.txt
-printconfiguration configuration.txt

# ===================================================================================================
# END OF PROGUARD RULES
# ===================================================================================================