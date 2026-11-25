# Keep line numbers for crash diagnostics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve Kotlin metadata & annotations
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }

# Keep app data models, Room entities, DAOs, WorkManager Workers & Glance AppWidgets
-keep class com.example.data.model.** { *; }
-keep class com.example.data.local.** { *; }
-keep class com.example.data.ai.** { *; }
-keep class com.example.worker.** { *; }
-keep class com.example.widget.** { *; }

# WorkManager ListenableWorker constructors
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Glance AppWidget & Receivers
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * extends androidx.glance.appwidget.action.ActionCallback { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Moshi / Retrofit / OkHttp
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.bouncycastle.**

# ML Kit Vision (Face Detection & On-Device OCR)
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Media3 Transformer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Avoid false warnings on optional runtime classes
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn javax.annotation.**
