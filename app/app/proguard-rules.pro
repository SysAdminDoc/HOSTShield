# HostShield ProGuard Rules
-keepattributes *Annotation*
-keep class com.hostshield.data.model.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
