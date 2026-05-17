# Dependency Refresh Report

Research date: 2026-05-17

## Metadata Sources

- Gradle current release metadata and wrapper output: https://services.gradle.org/versions/current
- Gradle 9.5.1 release notes: https://docs.gradle.org/9.5.1/release-notes.html
- Google Maven metadata for AGP and AndroidX artifacts: https://dl.google.com/dl/android/maven2/
- Maven Central metadata for Kotlin, KSP, Dagger, OkHttp, Vico, Lottie, ZXing, MaxMind, and test dependencies: https://repo.maven.apache.org/maven2/
- AGP built-in Kotlin migration guidance: https://developer.android.com/build/migrate-to-built-in-kotlin

## Applied Refresh

| Area | Previous | Applied | Decision |
|---|---:|---:|---|
| Gradle wrapper | 8.11.1 | 9.5.1 | Updated wrapper distribution and wrapper jar/scripts. |
| Android Gradle Plugin | 8.7.3 | 9.2.1 | Updated; removed `org.jetbrains.kotlin.android` because AGP 9 has built-in Kotlin support. |
| Kotlin Compose plugin | 2.1.0 | 2.3.21 | Updated; Kotlin Android plugin removed per AGP 9 guidance. |
| KSP plugin | 2.1.0-1.0.29 | 2.3.8 | Updated with AGP/Kotlin refresh. |
| compileSdk | 35 | 36 | Required by refreshed AndroidX/Vico metadata; targetSdk remains 35. |
| AndroidX Core | 1.15.0 | 1.18.0 | Updated. |
| Lifecycle | 2.8.7 | 2.10.0 | Updated runtime, compose, and ViewModel artifacts together. |
| Activity Compose | 1.9.3 | 1.13.0 | Updated. |
| WorkManager | 2.10.0 | 2.11.2 | Updated. |
| Compose BOM | 2024.12.01 | 2026.05.00 | Updated. |
| Navigation Compose | 2.8.5 | 2.9.8 | Updated to latest stable; alpha 2.10.x held. |
| Room | 2.6.1 | 2.8.4 | Updated runtime, KTX, compiler, and testing together. |
| Dagger/Hilt | 2.53.1 | 2.59.2 | Updated runtime and compiler together. |
| AndroidX Hilt | 1.2.0 | 1.3.0 | Updated navigation/work/compiler together. |
| OkHttp | 4.12.0 | 5.3.2 | Updated; compile warnings identify cleanup sites where `ResponseBody` is now non-null. |
| Cronet embedded | 143.7445.0 | 143.7445.0 | Already latest observed metadata version. |
| DataStore Preferences | 1.1.1 | 1.2.1 | Updated. |
| Tink Android | 1.21.0 | 1.21.0 | Already latest observed metadata version. |
| Browser | 1.8.0 | 1.10.0 | Updated. |
| Core SplashScreen | 1.0.1 | 1.2.0 | Updated. |
| Vico Compose M3 | 2.0.1 | 2.5.0 | Updated within 2.x stable. Vico 3.1.0 was tested and rejected for this batch because it moves chart packages/API. |
| Lottie Compose | 6.6.2 | 6.7.1 | Updated. |
| Glance | 1.1.1 | 1.1.1 | Held because only rc/beta newer metadata was observed. |
| ZXing Core | 3.5.3 | 3.5.4 | Updated. |
| MaxMind GeoIP2 | 4.2.1 | 5.1.0 | Updated; compile warnings identify deprecated accessor cleanup for a later pass. |
| Coroutines Test | 1.8.1 | 1.11.0 | Updated. |
| Truth | 1.4.2 | 1.4.5 | Updated. |
| AndroidX Test | JUnit 1.2.1 / Core 1.6.1 / Espresso 3.6.1 | JUnit 1.3.0 / Core 1.7.0 / Espresso 3.7.0 | Updated. |

## Compatibility Fixes

- Removed `org.jetbrains.kotlin.android` from root and app Gradle plugins because AGP 9 provides Kotlin support directly.
- Removed the old `kotlinOptions { jvmTarget = "17" }` block; Java 17 compatibility remains configured through `compileOptions`.
- Split the seven-flow `combine` call in `SettingsViewModel` into smaller typed flow combinations because Kotlin 2.3 rejects the previous intersection-type inference.
- Held Vico at 2.5.0 after Vico 3.1.0 produced package/API compile failures.

## Remaining Follow-Up

- Kotlin 2.3 reports future annotation target warnings for injected constructor parameters; a later cleanup should add explicit `@param:` targets or compiler flags.
- AndroidX Hilt moved `hiltViewModel` to `androidx.hilt.lifecycle.viewmodel.compose`; current imports still compile but are deprecated.
- OkHttp 5 makes several `ResponseBody` values non-null, leaving unnecessary safe-call warnings in network paths.
- MaxMind GeoIP2 5.1.0 marks several model accessors deprecated; `OfflineGeoIp` should move to the new accessor shape in a focused pass.
- The old `sourceSets.androidTest.assets.srcDir(...)` DSL is deprecated under AGP 9.

## Verification

- `.\app\gradlew.bat -p app --version`
- `.\app\gradlew.bat -p app --no-parallel :app:compileFullDebugKotlin`
- `.\app\gradlew.bat -p app --no-parallel :app:testFullDebugUnitTest`
- `.\app\gradlew.bat -p app --no-parallel :app:assembleFullDebug`
- `.\app\gradlew.bat -p app --no-parallel :app:assemblePlayDebug`
- `adb install -r app\app\build\outputs\apk\full\debug\app-full-debug.apk`
- `adb shell am start -W -n com.hostshield.debug/com.hostshield.MainActivity`
- `tools\check-release-docs.ps1`
- `git diff --check`
