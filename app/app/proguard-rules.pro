# HostShield v1.6.0 - ProGuard / R8 Rules

# ── Room ──────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.hostshield.data.model.** { *; }
-keep class com.hostshield.data.database.** { *; }
-dontwarn androidx.room.paging.**

# ── Hilt / Dagger ─────────────────────────────────────────────
-keepattributes *Annotation*
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}
-keep class * extends androidx.lifecycle.ViewModel { *; }

# ── libsu (topjohnwu) ────────────────────────────────────────
-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**

# ── OkHttp ────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# ── DataStore ─────────────────────────────────────────────────
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }

# ── WorkManager ───────────────────────────────────────────────
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Kotlin / Coroutines ──────────────────────────────────────
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations

# ── BroadcastReceivers ───────────────────────────────────────
-keep class com.hostshield.service.BootReceiver { *; }
-keep class com.hostshield.service.NetworkChangeReceiver { *; }
-keep class com.hostshield.service.HostShieldWidgetProvider { *; }
-keep class com.hostshield.service.AutomationReceiver { *; }

# ── Services ──────────────────────────────────────────────────
-keep class com.hostshield.service.DnsVpnService { *; }
-keep class com.hostshield.service.RootDnsService { *; }
-keep class com.hostshield.service.HostShieldTileService { *; }

# ── Room TypeConverters ──────────────────────────────────────
-keep class com.hostshield.data.database.Converters { *; }

# ── Serialization (JSON backup/restore) ──────────────────────
-keepclassmembers class com.hostshield.data.model.** {
    public <init>(...);
    public ** get*();
    public void set*(***);
}

# ── Enums ─────────────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── General ───────────────────────────────────────────────────
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
