[CmdletBinding()]
param(
    [string]$ApkPath = "app/app/build/outputs/apk/full/release/app-full-release.apk",
    [string]$OutputDir = "artifacts/release-provenance",
    [string]$SbomPath = "artifacts/release-provenance/hostshield-bom.cdx.json",
    [string]$OsvReportPath = "artifacts/release-provenance/osv-results.json",
    [string]$OsvAllowlistPath = "tools/osv-allowlist.json",
    [string]$PageAlignmentReportPath = "artifacts/release-provenance/android-page-alignment.txt",
    [string]$ProtectionMatrixPath = "artifacts/release-provenance/protection-resilience-matrix.json",
    # A provenance document that records "unavailable" for the SBOM and the OSV
    # scan looks complete but attests to nothing. Release runs must fail instead;
    # pass this switch for a deliberate local/no-evidence run.
    [switch]$AllowMissingEvidence
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

function Test-RepoPath {
    param([Parameter(Mandatory = $true)][string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return Test-Path -LiteralPath $Path
    }

    return Test-Path -LiteralPath (Join-Path $repoRoot $Path)
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
$versionsCatalog = Read-RepoFile "app/gradle/libs.versions.toml"
$wrapperProperties = Read-RepoFile "app/gradle/wrapper/gradle-wrapper.properties"

$versionName = Get-GradleSetting $appBuild 'versionName\s*=\s*"([^"]+)"' "versionName"
$versionCode = Get-GradleSetting $appBuild 'versionCode\s*=\s*(\d+)' "versionCode"
$compileSdk = Get-GradleSetting $appBuild 'compileSdk\s*=\s*(\d+)' "compileSdk"
$minSdk = Get-GradleSetting $appBuild 'minSdk\s*=\s*(\d+)' "minSdk"
$agpVersion = Get-GradleSetting $versionsCatalog '(?m)^agp\s*=\s*"([^"]+)"' "AGP version"
$kotlinVersion = Get-GradleSetting $versionsCatalog '(?m)^kotlin\s*=\s*"([^"]+)"' "Kotlin version"
$gradleVersion = Get-GradleSetting $wrapperProperties 'gradle-([0-9][^-]+)-bin\.zip' "Gradle version"
$cronetEmbeddedVersion = Get-GradleDependencyVersion $appBuild 'org.chromium.net:cronet-embedded'
if ($cronetEmbeddedVersion -eq "unknown") {
    $cronetEmbeddedVersion = "not bundled (embedded DoH3 disabled; pinned OkHttp DoH active)"
}

$commit = ((& git -C $repoRoot rev-parse HEAD) -join "`n").Trim()
$status = ((& git -C $repoRoot status --short) -join "`n").Trim()
$dirtyState = if ([string]::IsNullOrWhiteSpace($status)) { "clean" } else { "dirty" }

$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash.ToLowerInvariant()
$apkName = Split-Path -Leaf $apk
$sbom = if (Test-RepoPath $SbomPath) { Resolve-RepoPath $SbomPath } else { $null }
$osvReport = if (Test-RepoPath $OsvReportPath) { Resolve-RepoPath $OsvReportPath } else { $null }
$osvAllowlist = if (Test-RepoPath $OsvAllowlistPath) { Resolve-RepoPath $OsvAllowlistPath } else { $null }
$pageAlignmentReport = if (Test-RepoPath $PageAlignmentReportPath) { Resolve-RepoPath $PageAlignmentReportPath } else { $null }
$protectionMatrix = if (Test-RepoPath $ProtectionMatrixPath) { Resolve-RepoPath $ProtectionMatrixPath } else { $null }
$sbomHash = if ($sbom) { (Get-FileHash -Algorithm SHA256 -LiteralPath $sbom).Hash.ToLowerInvariant() } else { "unavailable" }
$osvReportHash = if ($osvReport) { (Get-FileHash -Algorithm SHA256 -LiteralPath $osvReport).Hash.ToLowerInvariant() } else { "unavailable" }
$osvAllowlistHash = if ($osvAllowlist) { (Get-FileHash -Algorithm SHA256 -LiteralPath $osvAllowlist).Hash.ToLowerInvariant() } else { "unavailable" }
$pageAlignmentReportHash = if ($pageAlignmentReport) { (Get-FileHash -Algorithm SHA256 -LiteralPath $pageAlignmentReport).Hash.ToLowerInvariant() } else { "unavailable" }
$protectionMatrixHash = if ($protectionMatrix) { (Get-FileHash -Algorithm SHA256 -LiteralPath $protectionMatrix).Hash.ToLowerInvariant() } else { "unavailable" }
$missingEvidence = New-Object System.Collections.Generic.List[string]
if (-not $sbom) { $missingEvidence.Add("SBOM ($SbomPath)") }
if (-not $osvReport) { $missingEvidence.Add("OSV report ($OsvReportPath)") }
if ($missingEvidence.Count -gt 0 -and -not $AllowMissingEvidence) {
    throw ("Release provenance is missing required evidence:`n - " + ($missingEvidence -join "`n - ") +
        "`nGenerate it, or re-run with -AllowMissingEvidence for a non-release run.")
}

$sbomName = if ($sbom) { Split-Path -Leaf $sbom } else { Split-Path -Leaf $SbomPath }
$osvReportName = if ($osvReport) { Split-Path -Leaf $osvReport } else { Split-Path -Leaf $OsvReportPath }
$pageAlignmentReportName = if ($pageAlignmentReport) { Split-Path -Leaf $pageAlignmentReport } else { Split-Path -Leaf $PageAlignmentReportPath }
$protectionMatrixName = if ($protectionMatrix) { Split-Path -Leaf $protectionMatrix } else { Split-Path -Leaf $ProtectionMatrixPath }

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

    $fingerprintMatch = [regex]::Match(
        $apkSignerStatus,
        '(?:Signer #1 certificate|V\d(?:\.\d+)? Signer: certificate) SHA-256 digest:\s*([A-Fa-f0-9:]+)'
    )
    if ($fingerprintMatch.Success) {
        $signerFingerprint = $fingerprintMatch.Groups[1].Value.ToLowerInvariant()
    }
}

$checksumsPath = Join-Path $outputRoot "checksums.txt"
$provenancePath = Join-Path $outputRoot "release-provenance.md"

$checksumLines = @("$hash  $apkName")
if ($sbom) {
    $checksumLines += "$sbomHash  $sbomName"
}
if ($osvReport) {
    $checksumLines += "$osvReportHash  $osvReportName"
}
if ($pageAlignmentReport) {
    $checksumLines += "$pageAlignmentReportHash  $pageAlignmentReportName"
}
if ($protectionMatrix) {
    $checksumLines += "$protectionMatrixHash  $protectionMatrixName"
}
$checksumLines | Set-Content -Encoding UTF8 -LiteralPath $checksumsPath

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
    "| CycloneDX SBOM path | $(if ($sbom) { $sbom } else { 'missing' }) |",
    "| CycloneDX SBOM SHA-256 | $sbomHash |",
    "| OSV report path | $(if ($osvReport) { $osvReport } else { 'missing' }) |",
    "| OSV report SHA-256 | $osvReportHash |",
    "| OSV allowlist path | $(if ($osvAllowlist) { $osvAllowlist } else { 'missing' }) |",
    "| OSV allowlist SHA-256 | $osvAllowlistHash |",
    "| Android 16 KB page alignment report path | $(if ($pageAlignmentReport) { $pageAlignmentReport } else { 'missing' }) |",
    "| Android 16 KB page alignment report SHA-256 | $pageAlignmentReportHash |",
    "| Protection resilience matrix path | $(if ($protectionMatrix) { $protectionMatrix } else { 'missing' }) |",
    "| Protection resilience matrix SHA-256 | $protectionMatrixHash |",
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
    '- CycloneDX SBOM and OSV JSON report hashes are recorded when local release checks generated them before the APK build.',
    '- OSV policy fails local release validation on unacknowledged HIGH or CRITICAL vulnerabilities; allowlist entries require a reason and expiry.',
    '- Android 16 KB page alignment is verified with `zipalign -P 16` for APKs and `PAGE_ALIGNMENT_16K` bundle config for AABs.',
    '- Protection resilience evidence is recorded by `tools/run-protection-resilience-matrix.ps1` when a connected or manual device pass is run.',
    '- Signing certificate fingerprint comes from `apksigner verify --verbose --print-certs` when Android build tools are available.',
    '- Git status is recorded so release notes can distinguish clean tagged releases from local smoke-test artifacts.',
    '',
    '## Local Release Assets',
    '',
    'HostShield releases are built and verified locally on the maintainer workstation before upload to GitHub Releases.',
    'Attach these generated files with the release asset set:',
    '',
    '- Signed release APK/AAB artifacts',
    '- `hostshield-bom.cdx.json`',
    '- `osv-results.json`',
    '- `android-page-alignment.txt`',
    '- `protection-resilience-matrix.json`',
    '- `checksums.txt`',
    '',
    'The checksums file records the SHA-256 digest for each local release artifact.'
)

$provenance | Set-Content -Encoding UTF8 -LiteralPath $provenancePath

Write-Host "Wrote $provenancePath"
Write-Host "Wrote $checksumsPath"
