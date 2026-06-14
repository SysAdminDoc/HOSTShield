// HostShield Android application module
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val releaseSigningEnvNames = listOf("KEYSTORE_FILE", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
fun releaseEnv(name: String): String? =
    providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }

val releaseKeystoreFile = releaseEnv("KEYSTORE_FILE")?.let { file(it) }
val releaseSigningIssue = when {
    releaseSigningEnvNames.any { releaseEnv(it) == null } -> {
        val missing = releaseSigningEnvNames.filter { releaseEnv(it) == null }.joinToString(", ")
        "missing release signing environment variable(s): $missing"
    }
    releaseKeystoreFile?.exists() != true -> "KEYSTORE_FILE does not exist: ${releaseKeystoreFile?.path ?: "<unset>"}"
    else -> null
}
val allowDebugReleaseSigning = releaseEnv("HOSTSHIELD_ALLOW_DEBUG_RELEASE_SIGNING") == "true"
val releaseArtifactTaskRegex = Regex("""^(assemble|bundle|package|sign|validateSigning).*Release$""")

fun releaseSigningFailureMessage(): String =
    "Release signing is not configured ($releaseSigningIssue). " +
        "Set KEYSTORE_FILE, STORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD. " +
        "For local non-distribution release verification only, set HOSTSHIELD_ALLOW_DEBUG_RELEASE_SIGNING=true."

android {
    namespace = "com.hostshield"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hostshield"
        minSdk = 26
        targetSdk = 36
        versionCode = 92
        versionName = "6.9.10"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
            arg("room.generateKotlin", "true")
        }
    }

    signingConfigs {
        create("release") {
            if (releaseSigningIssue == null && releaseKeystoreFile != null) {
                storeFile = releaseKeystoreFile
                storePassword = releaseEnv("STORE_PASSWORD")
                keyAlias = releaseEnv("KEY_ALIAS")
                keyPassword = releaseEnv("KEY_PASSWORD")
            } else if (allowDebugReleaseSigning) {
                // Explicit local-only fallback for non-distribution verification.
                val debugKs = file("${System.getProperty("user.home")}/.android/debug.keystore")
                if (debugKs.exists()) {
                    storeFile = debugKs
                    storePassword = "android"
                    keyAlias = "androiddebugkey"
                    keyPassword = "android"
                } else {
                    throw GradleException(
                        "HOSTSHIELD_ALLOW_DEBUG_RELEASE_SIGNING=true was set, but the Android debug keystore was not found."
                    )
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            isPseudoLocalesEnabled = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Fail the build on lint errors so CI gates regressions. Existing issues
        // are captured in lint-baseline.xml so only NEW problems break the build.
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
        // Security checks we never want to slip silently (a TLS/DNS app must
        // not regress certificate or hostname verification).
        fatal += listOf("TrustAllX509TrustManager", "BadHostnameVerifier", "UnsafeImplicitIntentLaunch")
        htmlReport = true
        xmlReport = true
        sarifReport = true
        textReport = true
    }

    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

    // Run unit tests against real org.json / android.util.Base64 (etc.) rather
    // than the JVM-mocked stubs that throw `not mocked` at runtime. Pre-v6.5
    // unit tests for BackupRestoreUtil were dead because of this; flipping the
    // flag makes them executable on a normal JVM without Robolectric.
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    // ── Product Flavors ─────────────────────────────────────
    // "full": GitHub/F-Droid release — QUERY_ALL_PACKAGES for complete app listing
    // "play": Play Store — uses <queries> intent filters (Google rejects QUERY_ALL_PACKAGES)
    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
            // QUERY_ALL_PACKAGES in main manifest covers this flavor.
            // All system apps visible in firewall and per-app screens.
        }
        create("play") {
            dimension = "distribution"
            applicationIdSuffix = ".play"
            // QUERY_ALL_PACKAGES removed via manifest overlay.
            // Limited to <queries> declarations — user-installed apps still visible,
            // some system apps may be missing from firewall/exclusion lists.
            // This is a known tradeoff required for Play Store publication.
        }
    }
}

tasks.configureEach {
    val isReleaseArtifactTask = name.matches(releaseArtifactTaskRegex)
    if (isReleaseArtifactTask) {
        doFirst {
            if (releaseSigningIssue != null && !allowDebugReleaseSigning) {
                throw GradleException(releaseSigningFailureMessage())
            }
        }
    }
}

gradle.taskGraph.whenReady {
    if (
        allTasks.any { releaseArtifactTaskRegex.matches(it.name) } &&
        releaseSigningIssue != null &&
        !allowDebugReleaseSigning
    ) {
        throw GradleException(releaseSigningFailureMessage())
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("androidx.startup:startup-runtime:1.2.0")
    val serializationBom = platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.8.1")
    implementation(serializationBom)

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2026.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // Room Database
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-android-compiler:2.59.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")
    implementation("androidx.hilt:hilt-work:1.3.0")
    ksp("androidx.hilt:hilt-compiler:1.3.0")

    // Networking (for downloading hosts sources and pinned DoH)
    implementation("com.squareup.okhttp3:okhttp:5.4.0")

    // Root access via libsu
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    implementation("com.github.topjohnwu.libsu:service:6.0.0")
    implementation("com.github.topjohnwu.libsu:nio:6.0.0")

    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // Tink is retained only for one-time migration from legacy AndroidX
    // EncryptedSharedPreferences keysets; new secret writes use Android Keystore
    // directly through SecureStore.
    implementation("com.google.crypto.tink:tink-android:1.21.0")
    // Lightweight Argon2id implementation for new PIN and backup KDF records.
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")

    // Custom Tabs (captive portal login)
    implementation("androidx.browser:browser:1.10.0")

    // Splash screen
    implementation("androidx.core:core-splashscreen:1.2.0")

    // v6.1: Vico chart library (Roadmap #26)
    implementation("com.patrykandpatrick.vico:compose-m3:2.5.0")

    // v6.1: Lottie animations (Roadmap #27)
    implementation("com.airbnb.android:lottie-compose:6.7.1")

    // v6.1: Jetpack Glance widgets (Roadmap #29)
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // v6.2: QR code generation for config sharing (Roadmap #38)
    implementation("com.google.zxing:core:3.5.4")

    // v5.0: MaxMind GeoIP2 for offline GeoIP lookups (replaces ip-api.com rate-limited API)
    // Bundled GeoLite2-Country.mmdb (~6MB) + GeoLite2-ASN.mmdb (~8MB)
    implementation("com.maxmind.geoip2:geoip2:5.1.0")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("com.google.truth:truth:1.4.5")
    testImplementation("androidx.room:room-testing:2.8.4")
    // BackupRestoreUtil unit tests use org.json.JSONObject directly; without
    // this the stubbed Android JSONObject throws `not mocked` and three tests
    // were dead before v6.5.
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation(serializationBom)
    androidTestImplementation("androidx.room:room-testing:2.8.4")
}
