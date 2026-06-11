[CmdletBinding()]
param(
    [string]$ApkPath = "app/app/build/outputs/apk/full/release/app-full-release.apk",
    [string]$OutputDir = "artifacts/release-provenance"
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptRoot "..")

function Resolve-RepoPath {
    param([Parameter(Mandatory = $true)][string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return Resolve-Path -LiteralPath $Path
    }

    return Resolve-Path -LiteralPath (Join-Path $repoRoot $Path)
}

function Read-RepoFile {
    param([Parameter(Mandatory = $true)][string]$Path)
    return Get-Content -Raw -LiteralPath (Join-Path $repoRoot $Path)
}

function Get-GradleSetting {
    param(
        [Parameter(Mandatory = $true)][string]$Text,
        [Parameter(Mandatory = $true)][string]$Regex,
        [Parameter(Mandatory = $true)][string]$Name
    )

    $match = [regex]::Match($Text, $Regex)
    if (-not $match.Success) {
        return "unknown"
    }

    return $match.Groups[1].Value
}

function Get-GradleDependencyVersion {
    param(
        [Parameter(Mandatory = $true)][string]$Text,
        [Parameter(Mandatory = $true)][string]$Coordinate
    )

    $pattern = [regex]::Escape($Coordinate) + ':([^")]+)'
    $match = [regex]::Match($Text, $pattern)
    if (-not $match.Success) {
        return "unknown"
    }

    return $match.Groups[1].Value
}

function Find-ApkSigner {
    $candidateRoots = @()

    if ($env:ANDROID_HOME) {
        $candidateRoots += $env:ANDROID_HOME
    }
    if ($env:ANDROID_SDK_ROOT -and $env:ANDROID_SDK_ROOT -ne $env:ANDROID_HOME) {
        $candidateRoots += $env:ANDROID_SDK_ROOT
    }

    $localSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path -LiteralPath $localSdk) {
        $candidateRoots += $localSdk
    }

    foreach ($root in $candidateRoots | Select-Object -Unique) {
        $buildTools = Join-Path $root "build-tools"
        if (-not (Test-Path -LiteralPath $buildTools)) {
            continue
        }

        $apkSigner = Get-ChildItem -LiteralPath $buildTools -Recurse -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -in @("apksigner", "apksigner.bat", "apksigner.exe") } |
            Sort-Object FullName -Descending |
            Select-Object -First 1

        if ($apkSigner) {
            return $apkSigner.FullName
        }
    }

    return $null
}

function Find-JavaHome {
    $candidates = @()

    if ($env:JAVA_HOME) {
        $candidates += $env:JAVA_HOME
    }

    $androidStudioJbr = Join-Path $env:ProgramFiles "Android\Android Studio\jbr"
    if (Test-Path -LiteralPath $androidStudioJbr) {
        $candidates += $androidStudioJbr
    }

    foreach ($candidate in $candidates | Select-Object -Unique) {
        $javaExe = Join-Path $candidate "bin\java.exe"
        $javaUnix = Join-Path $candidate "bin/java"
        if ((Test-Path -LiteralPath $javaExe) -or (Test-Path -LiteralPath $javaUnix)) {
            return $candidate
        }
    }

    return $null
}

$apk = Resolve-RepoPath $ApkPath
$outputRoot = if ([System.IO.Path]::IsPathRooted($OutputDir)) {
    $OutputDir
} else {
    Join-Path $repoRoot $OutputDir
}

New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null

$appBuild = Read-RepoFile "app/app/build.gradle.kts"
$rootBuild = Read-RepoFile "app/build.gradle.kts"
$wrapperProperties = Read-RepoFile "app/gradle/wrapper/gradle-wrapper.properties"

$versionName = Get-GradleSetting $appBuild 'versionName\s*=\s*"([^"]+)"' "versionName"
$versionCode = Get-GradleSetting $appBuild 'versionCode\s*=\s*(\d+)' "versionCode"
$compileSdk = Get-GradleSetting $appBuild 'compileSdk\s*=\s*(\d+)' "compileSdk"
$minSdk = Get-GradleSetting $appBuild 'minSdk\s*=\s*(\d+)' "minSdk"
$agpVersion = Get-GradleSetting $rootBuild 'com\.android\.application"\)\s+version\s+"([^"]+)"' "AGP version"
$kotlinVersion = Get-GradleSetting $rootBuild 'org\.jetbrains\.kotlin\.(?:android|plugin\.compose)"\)\s+version\s+"([^"]+)"' "Kotlin version"
$gradleVersion = Get-GradleSetting $wrapperProperties 'gradle-([0-9][^-]+)-bin\.zip' "Gradle version"
$cronetEmbeddedVersion = Get-GradleDependencyVersion $appBuild 'org.chromium.net:cronet-embedded'

$commit = (& git -C $repoRoot rev-parse HEAD).Trim()
$status = (& git -C $repoRoot status --short).Trim()
$dirtyState = if ([string]::IsNullOrWhiteSpace($status)) { "clean" } else { "dirty" }

$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash.ToLowerInvariant()
$apkName = Split-Path -Leaf $apk

$apkSigner = Find-ApkSigner
$javaHome = Find-JavaHome
$signerFingerprint = "unavailable"
$apkSignerStatus = "apksigner not found"

if ($apkSigner) {
    if ($javaHome) {
        $env:JAVA_HOME = $javaHome
        $env:Path = (Join-Path $javaHome "bin") + [System.IO.Path]::PathSeparator + $env:Path
    }

    $apkSignerOutput = & $apkSigner verify --verbose --print-certs $apk 2>&1
    $apkSignerStatus = ($apkSignerOutput -join "`n").Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "apksigner verification failed for $apkName.`n$apkSignerStatus"
    }

    $fingerprintMatch = [regex]::Match($apkSignerStatus, 'Signer #1 certificate SHA-256 digest:\s*([A-Fa-f0-9:]+)')
    if ($fingerprintMatch.Success) {
        $signerFingerprint = $fingerprintMatch.Groups[1].Value.ToLowerInvariant()
    }
}

$checksumsPath = Join-Path $outputRoot "checksums.txt"
$provenancePath = Join-Path $outputRoot "release-provenance.md"

"$hash  $apkName" | Set-Content -Encoding UTF8 -LiteralPath $checksumsPath

$provenance = @(
    "# HostShield Release Provenance",
    "",
    "| Field | Value |",
    "|-------|-------|",
    "| Version | $versionName |",
    "| Version code | $versionCode |",
    "| Git commit | $commit |",
    "| Git status | $dirtyState |",
    "| APK path | $apk |",
    "| APK SHA-256 | $hash |",
    "| Signing cert SHA-256 | $signerFingerprint |",
    "| Gradle | $gradleVersion |",
    "| Android Gradle Plugin | $agpVersion |",
    "| Kotlin | $kotlinVersion |",
    "| Cronet embedded | $cronetEmbeddedVersion |",
    "| Java home | $(if ($javaHome) { $javaHome } else { 'unknown' }) |",
    "| compileSdk | $compileSdk |",
    "| minSdk | $minSdk |",
    "",
    "## Verification",
    "",
    '- `checksums.txt` contains the APK SHA-256 line for release notes or GitHub release assets.',
    '- Signing certificate fingerprint comes from `apksigner verify --verbose --print-certs` when Android build tools are available.',
    '- Git status is recorded so release notes can distinguish clean tagged releases from local smoke-test artifacts.'
)

$provenance | Set-Content -Encoding UTF8 -LiteralPath $provenancePath

Write-Host "Wrote $provenancePath"
Write-Host "Wrote $checksumsPath"
