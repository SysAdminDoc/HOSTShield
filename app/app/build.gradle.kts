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
        compileSdk = 37

    defaultConfig {
        applicationId = "com.hostshield"
        minSdk = 26
        targetSdk = 36
        versionCode = 140
        versionName = "6.9.58"

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
    androidResources {
        generateLocaleConfig = true
    }

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
            // QUERY_ALL_PACKAGES removed via manifest overlay; the play overlay
            // adds a <queries> MAIN-intent declaration so launchable apps remain
            // visible in firewall/exclusion lists. Some system apps without a
            // launcher may be missing — the known tradeoff for Play publication.
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.startup.runtime)
    val serializationBom = platform(libs.kotlinx.serialization.bom)
    implementation(serializationBom)

    // Compose BOM
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Networking (for downloading hosts sources and pinned DoH)
    implementation(libs.okhttp)

    // Root access via libsu
    implementation(libs.libsu.core)
    implementation(libs.libsu.service)
    implementation(libs.libsu.nio)

    // DataStore for preferences
    implementation(libs.androidx.datastore.preferences)

    // Tink is retained only for one-time migration from legacy AndroidX
    // EncryptedSharedPreferences keysets; new secret writes use Android Keystore
    // directly through SecureStore.
    implementation(libs.tink.android)
    // Lightweight Argon2id implementation for new PIN and backup KDF records.
    implementation(libs.bcprov)

    // Custom Tabs (captive portal login)
    implementation(libs.androidx.browser)

    // Splash screen
    implementation(libs.androidx.core.splashscreen)

    // v6.1: Vico chart library (Roadmap #26)
    implementation(libs.vico.compose.m3)

    // v6.1: Lottie animations (Roadmap #27)
    implementation(libs.lottie.compose)

    // v6.1: Jetpack Glance widgets (Roadmap #29)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // v6.2: QR code generation for config sharing (Roadmap #38)
    implementation(libs.zxing.core)

    // Debug
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    // BackupRestoreUtil unit tests use org.json.JSONObject directly; without
    // this the stubbed Android JSONObject throws `not mocked` and three tests
    // were dead before v6.5.
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(serializationBom)
    androidTestImplementation(libs.androidx.room.testing)
}
