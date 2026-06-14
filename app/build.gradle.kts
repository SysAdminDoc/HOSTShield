// HostShield Android build configuration
// Top-level build file

import org.cyclonedx.gradle.CyclonedxDirectTask
import org.cyclonedx.model.Component

plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("com.google.devtools.ksp") version "2.3.8" apply false
    id("com.google.dagger.hilt.android") version "2.59.2" apply false
    id("org.cyclonedx.bom") version "3.2.4"
}

fun hostShieldVersionName(): String {
    val appBuild = file("app/build.gradle.kts")
    val versionMatch = Regex("""versionName\s*=\s*"([^"]+)"""").find(appBuild.readText())
    return versionMatch?.groupValues?.get(1) ?: "unspecified"
}

group = "com.hostshield"
version = hostShieldVersionName()

subprojects {
    group = "com.hostshield"
    version = rootProject.version
}

tasks.cyclonedxBom {
    projectType = Component.Type.APPLICATION
    componentName = "HostShield"
    componentVersion = hostShieldVersionName()
    componentGroup = "com.hostshield"
    jsonOutput = file("build/reports/cyclonedx/hostshield-bom.cdx.json")
    xmlOutput.unsetConvention()
}

// The aggregate SBOM pulls each project's direct BOM, which by default sweeps in
// every configuration — including test-only deps (io.grpc:grpc-netty and its
// netty transitive graph), an old BouncyCastle, commons-lang3, httpclient — that
// never ship in the APK yet tripped the OSV release gate on build/test-tooling
// CVEs. Restrict each direct BOM to the release runtime classpath and drop the
// build environment so the SBOM and OSV scan describe only shipped dependencies.
allprojects {
    tasks.withType<CyclonedxDirectTask>().configureEach {
        includeConfigs.set(listOf("fullReleaseRuntimeClasspath"))
        includeBuildEnvironment.set(false)
    }
}
