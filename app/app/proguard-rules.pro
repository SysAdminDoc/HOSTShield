# HostShield v3.8.0 - ProGuard / R8 Rules

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
-keep class com.hostshield.service.DnsProxyService { *; }
-keep class com.hostshield.service.HostShieldTileService { *; }
-keep class com.hostshield.service.DnsPacketBuilder { *; }
-keep class com.hostshield.service.DnsCache { *; }
-keep class com.hostshield.service.DnsCache$CacheKey { *; }
-keep class com.hostshield.service.DnsCache$CacheStats { *; }
-keep class com.hostshield.service.CnameCloakDetector { *; }
-keep class com.hostshield.service.CnameCloakDetector$CnameResult { *; }
-keep class com.hostshield.service.DohBypassUpdater { *; }
-keep class com.hostshield.service.DohResolver { *; }
-keep class com.hostshield.service.LocalDnsServer { *; }
-keep class com.hostshield.service.DotResolver { *; }
-keep class com.hostshield.service.DotResolver$** { *; }
-keep class com.hostshield.service.IptablesManager { *; }
-keep class com.hostshield.service.IptablesBinaryManager { *; }
-keep class com.hostshield.util.DiagnosticExporter { *; }
-keep class com.hostshield.util.TrackerSignatureDb { *; }
-keep class com.hostshield.util.GeoIpLookup { *; }
-keep class com.hostshield.util.GeoIpLookup$** { *; }
-keep class com.hostshield.util.TrackerSignatureDb$** { *; }
-keep class com.hostshield.util.NetworkTrackerDb { *; }
-keep class com.hostshield.util.NetworkTrackerDb$** { *; }
-keep class com.hostshield.service.ContextState { *; }

# ── v5.0: New classes ──────────────────────────────────────
-keep class com.hostshield.service.CnameCloakUpdater { *; }
-keep class com.hostshield.service.DnsCache$CacheResult { *; }
-keep class com.hostshield.service.DnsDiskCache { *; }
-keep class com.hostshield.service.DnsDiskCache$** { *; }
-keep class com.hostshield.util.OfflineGeoIp { *; }
-keep class com.hostshield.util.OfflineGeoIp$** { *; }
-keep class com.hostshield.util.DnsBenchmark { *; }
-keep class com.hostshield.util.DnsBenchmark$** { *; }

# ── v5.2: Captive portal + Threat Intel ──────────────────
-keep class com.hostshield.service.CaptivePortalHandler { *; }
-keep class com.hostshield.service.ThreatIntelManager { *; }
-keep class com.hostshield.service.ThreatIntelManager$** { *; }
-keep class com.hostshield.service.ThreatIntelWorker { *; }
-keep class com.hostshield.service.SafeSearchEnforcer { *; }

# ── v6.3: Google Tink / EncryptedSharedPreferences ────────
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
-keep class com.hostshield.data.preferences.SecureStore { *; }

# ── v5.0: MaxMind GeoIP2 ──────────────────────────────────
-dontwarn com.maxmind.**
-keep class com.maxmind.** { *; }
-dontwarn com.fasterxml.jackson.**
-keep class com.fasterxml.jackson.** { *; }

# ── StatsWidget (v3.8.0) ────────────────────────────────────
-keep class com.hostshield.service.StatsWidgetProvider { *; }

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

# ── v5.x: Encrypted Backup (Roadmap #36) ────────────────────
-keep class com.hostshield.util.EncryptedBackup { *; }

# ── v1.7.0: Named Schedule Presets (Roadmap #35) ────────────
-keep class com.hostshield.util.SchedulePresets { *; }
-keep class com.hostshield.util.SchedulePresets$** { *; }
-keep class com.hostshield.util.SchedulePreset { *; }

# ── v5.x: DNS Stamp Parser (Roadmap #42) ─────────────────────
-keep class com.hostshield.util.DnsStampParser { *; }
-keep class com.hostshield.util.DnsStampParser$** { *; }

# ── v5.x: QR Config Sharing (Roadmap #38) ────────────────────
-keep class com.hostshield.util.QrConfigSharing { *; }
-keep class com.hostshield.util.ShareableConfig { *; }
-keep class com.hostshield.util.RuleEntry { *; }

# ── v6.1: Domain-per-app DNS rules (Roadmap #12) ─────────────
-keep class com.hostshield.service.AppDnsRuleEngine { *; }
-keep class com.hostshield.service.AppDnsRuleEngine$** { *; }
-keep class com.hostshield.data.model.AppDnsRule { *; }

# ── v6.1: Content Filter Categories (Roadmap #40) ────────────
-keep class com.hostshield.service.ContentFilterManager { *; }
-keep class com.hostshield.service.ContentFilterManager$** { *; }
-keep enum com.hostshield.service.ContentCategory { *; }

# ── v6.1: Parental Controls (Roadmap #48) ──────────────────
-keep class com.hostshield.service.ParentalControlManager { *; }
-keep class com.hostshield.service.ParentalControlManager$** { *; }

# ── v6.1: TLS Fingerprinting (Roadmap #47) ─────────────────
-keep class com.hostshield.util.TlsFingerprinter { *; }
-keep class com.hostshield.util.TlsFingerprinter$** { *; }

# ── v6.1: Crash Reporter (Roadmap #39) ─────────────────────
-keep class com.hostshield.util.CrashReporter { *; }
-keep class com.hostshield.util.CrashReporter$** { *; }

# ── v6.1: Connection Tracker (Roadmap #46) ─────────────────
-keep class com.hostshield.service.ConnectionTracker { *; }
-keep class com.hostshield.service.ConnectionTracker$** { *; }

# ── v6.1: WebDAV Cloud Sync (Roadmap #37) ──────────────────
-keep class com.hostshield.util.WebDavSync { *; }
-keep class com.hostshield.util.WebDavSync$** { *; }

# ── v6.2: DoQ + WireGuard (Roadmap #45, #51) ────────────────
-keep class com.hostshield.service.DoqResolver { *; }
-keep class com.hostshield.service.DoqResolver$** { *; }
-keep class com.hostshield.service.WireGuardProxy { *; }
-keep class com.hostshield.service.WireGuardProxy$** { *; }

# ── v6.2: NflogReader + RootDnsLogger ───────────────────────
-keep class com.hostshield.service.NflogReader { *; }
-keep class com.hostshield.service.NflogReader$** { *; }
-keep class com.hostshield.service.RootDnsLogger { *; }
-keep class com.hostshield.service.RootDnsLogger$** { *; }
-keep class com.hostshield.service.BlockNotificationService { *; }
-keep class com.hostshield.service.BlockingScheduleWorker { *; }
