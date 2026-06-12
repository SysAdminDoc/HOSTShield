[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptRoot "..")

function Read-RepoFile {
    param([Parameter(Mandatory = $true)][string]$Path)
    return Get-Content -Raw -LiteralPath (Join-Path $repoRoot $Path)
}

function Test-RepoFile {
    param([Parameter(Mandatory = $true)][string]$Path)
    return Test-Path -LiteralPath (Join-Path $repoRoot $Path)
}

$appBuild = Read-RepoFile "app/app/build.gradle.kts"
$rootBuild = Read-RepoFile "app/build.gradle.kts"
$releaseWorkflow = Read-RepoFile ".github/workflows/release.yml"
$releaseProvenance = Read-RepoFile "tools/release-provenance.ps1"
$osvPolicyScript = Read-RepoFile "tools/check-osv-report.ps1"
$versionNameMatch = [regex]::Match($appBuild, 'versionName\s*=\s*"([^"]+)"')
$versionCodeMatch = [regex]::Match($appBuild, 'versionCode\s*=\s*(\d+)')
$compileSdkMatch = [regex]::Match($appBuild, 'compileSdk\s*=\s*(\d+)')
$agpVersionMatch = [regex]::Match($rootBuild, 'com\.android\.application"\)\s+version\s+"([^"]+)"')
$kotlinVersionMatch = [regex]::Match($rootBuild, 'org\.jetbrains\.kotlin\.(?:android|plugin\.compose)"\)\s+version\s+"([^"]+)"')

if (-not $versionNameMatch.Success -or -not $versionCodeMatch.Success) {
    throw "Unable to parse versionName/versionCode from app/app/build.gradle.kts."
}

$versionName = $versionNameMatch.Groups[1].Value
$versionCode = $versionCodeMatch.Groups[1].Value
$compileSdk = if ($compileSdkMatch.Success) { $compileSdkMatch.Groups[1].Value } else { "unknown" }
$agpVersion = if ($agpVersionMatch.Success) { $agpVersionMatch.Groups[1].Value } else { "unknown" }
$kotlinVersion = if ($kotlinVersionMatch.Success) { $kotlinVersionMatch.Groups[1].Value } else { "unknown" }
$kotlinMajorMinor = if ($kotlinVersion -match '^(\d+\.\d+)') { $Matches[1] } else { $kotlinVersion }
$agpMajorMinor = if ($agpVersion -match '^(\d+\.\d+)') { $Matches[1] } else { $agpVersion }
$metadataChangelog = "app/metadata/en-US/changelogs/$versionCode.txt"

$docs = @{
    "README.md" = Read-RepoFile "README.md"
    "app/README.md" = Read-RepoFile "app/README.md"
    "app/CHANGELOG.md" = Read-RepoFile "app/CHANGELOG.md"
    "app/metadata/en-US/full_description.txt" = Read-RepoFile "app/metadata/en-US/full_description.txt"
    "app/metadata/en-US/short_description.txt" = Read-RepoFile "app/metadata/en-US/short_description.txt"
}

$failures = New-Object System.Collections.Generic.List[string]

function Test-DohBypassDomain {
    param([Parameter(Mandatory = $true)][string]$Value)

    $candidate = $Value.Trim().ToLowerInvariant()
    if ($candidate.Length -lt 4 -or $candidate.Length -gt 253) {
        return $false
    }
    if ($candidate.StartsWith("*.") -or $candidate.StartsWith(".") -or $candidate.EndsWith(".")) {
        return $false
    }
    return $candidate -match '^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])$'
}

$dohBypassManifest = "doh-bypass-list.json"
if (-not (Test-RepoFile $dohBypassManifest)) {
    $failures.Add("Missing remote DoH bypass manifest: $dohBypassManifest")
} else {
    $manifestRaw = Read-RepoFile $dohBypassManifest
    if ([Text.Encoding]::UTF8.GetByteCount($manifestRaw) -gt 50000) {
        $failures.Add("$dohBypassManifest exceeds DohBypassUpdater MAX_JSON_SIZE.")
    }

    $manifest = $null
    try {
        $manifest = $manifestRaw | ConvertFrom-Json -ErrorAction Stop
    } catch {
        $failures.Add("$dohBypassManifest is not valid JSON: $($_.Exception.Message)")
    }

    if ($null -ne $manifest) {
        [int]$manifestVersion = 0
        if (-not [int]::TryParse([string]$manifest.version, [ref]$manifestVersion) -or $manifestVersion -lt 1) {
            $failures.Add("$dohBypassManifest must contain integer version >= 1.")
        }

        $updatedDate = [DateTime]::MinValue
        if (-not [DateTime]::TryParseExact(
                [string]$manifest.updated,
                "yyyy-MM-dd",
                [Globalization.CultureInfo]::InvariantCulture,
                [Globalization.DateTimeStyles]::None,
                [ref]$updatedDate
            )) {
            $failures.Add("$dohBypassManifest must contain updated as yyyy-MM-dd.")
        }

        $domains = if ($null -ne $manifest.domains) { @($manifest.domains) } else { @() }
        $wildcards = if ($null -ne $manifest.wildcards) { @($manifest.wildcards) } else { @() }
        $entryCount = $domains.Count + $wildcards.Count
        if ($entryCount -eq 0) {
            $failures.Add("$dohBypassManifest must contain at least one domain or wildcard entry.")
        }
        if ($entryCount -gt 500) {
            $failures.Add("$dohBypassManifest has $entryCount entries; DohBypassUpdater caps remote lists at 500.")
        }

        $seenDomains = New-Object System.Collections.Generic.HashSet[string]
        foreach ($entry in $domains) {
            $value = ([string]$entry).Trim().ToLowerInvariant()
            if (-not (Test-DohBypassDomain $value)) {
                $failures.Add("$dohBypassManifest contains invalid domain entry: $entry")
            } elseif (-not $seenDomains.Add($value)) {
                $failures.Add("$dohBypassManifest contains duplicate domain entry: $value")
            }
        }

        $seenWildcards = New-Object System.Collections.Generic.HashSet[string]
        foreach ($entry in $wildcards) {
            $value = ([string]$entry).Trim().ToLowerInvariant()
            if (-not (Test-DohBypassDomain $value)) {
                $failures.Add("$dohBypassManifest contains invalid wildcard entry: $entry")
            } elseif (-not $seenWildcards.Add($value)) {
                $failures.Add("$dohBypassManifest contains duplicate wildcard entry: $value")
            }
        }
    }
}

$osvAllowlistPath = "tools/osv-allowlist.json"
if (-not (Test-RepoFile $osvAllowlistPath)) {
    $failures.Add("Missing OSV vulnerability allowlist: $osvAllowlistPath")
} else {
    try {
        $osvAllowlist = Read-RepoFile $osvAllowlistPath | ConvertFrom-Json -ErrorAction Stop
        if ([int]$osvAllowlist.version -ne 1) {
            $failures.Add("$osvAllowlistPath must contain version 1.")
        }
        if ($null -eq $osvAllowlist.ignored) {
            $failures.Add("$osvAllowlistPath must contain an ignored array.")
        }
    } catch {
        $failures.Add("$osvAllowlistPath is not valid JSON: $($_.Exception.Message)")
    }
}

$dohUpdaterPath = "app/app/src/main/java/com/hostshield/service/DohBypassUpdater.kt"
$dohUpdater = Read-RepoFile $dohUpdaterPath
$expectedDohManifestUrl = "https://raw.githubusercontent.com/SysAdminDoc/HostShield/main/$dohBypassManifest"
if ($dohUpdater -notmatch [regex]::Escape($expectedDohManifestUrl)) {
    $failures.Add("$dohUpdaterPath does not point at $expectedDohManifestUrl")
}

foreach ($doc in @("README.md", "app/README.md")) {
    if ($docs[$doc] -notmatch [regex]::Escape("version-$versionName")) {
        $failures.Add("$doc does not advertise version badge $versionName.")
    }
}

if (-not (Test-RepoFile $metadataChangelog)) {
    $failures.Add("Missing current metadata changelog: $metadataChangelog")
} else {
    $docs[$metadataChangelog] = Read-RepoFile $metadataChangelog
}

$requiredPatterns = @{
    "README.md" = @(
        "fail-closed",
        "405 tracker SDK signatures",
        "ipapi.co",
        "Kotlin $kotlinMajorMinor",
        "AGP $agpMajorMinor",
        "com.hostshield.ACTION_ENABLE",
        "com.hostshield.ACTION_SET_PROFILE",
        "duration_minutes",
        "v1-v15"
    )
    "app/README.md" = @(
        "fail-closed",
        "405 tracker SDK signatures",
        "ipapi.co",
        "Android SDK $compileSdk",
        "com.hostshield.ACTION_ENABLE",
        "duration_minutes",
        "v$versionName"
    )
    "app/CHANGELOG.md" = @(
        "v$versionName"
    )
    "app/metadata/en-US/full_description.txt" = @(
        "fail-closed certificate pinning",
        "405 signatures",
        "ipapi.co",
        "Kotlin $kotlinMajorMinor",
        "Android SDK $compileSdk",
        "Android Gradle Plugin $agpMajorMinor",
        "Experimental DoQ and WireGuard",
        "QUERY_ALL_PACKAGES",
        "Play flavor artifacts remove that permission",
        "license-publication conflict"
    )
    "app/metadata/en-US/short_description.txt" = @(
        "Local-first DNS firewall"
    )
}

if ($docs.ContainsKey($metadataChangelog)) {
    $requiredPatterns[$metadataChangelog] = @(
        "v$versionName"
    )
}

foreach ($doc in $requiredPatterns.Keys) {
    foreach ($pattern in $requiredPatterns[$doc]) {
        if ($docs[$doc] -notmatch [regex]::Escape($pattern)) {
            $failures.Add("$doc is missing required release-doc phrase: $pattern")
        }
    }
}

$releaseGatePatterns = @{
    "app/build.gradle.kts" = @{
        Text = $rootBuild
        Patterns = @(
            "org.cyclonedx.bom",
            "3.2.4",
            "hostshield-bom.cdx.json",
            "componentVersion"
        )
    }
    ".github/workflows/release.yml" = @{
        Text = $releaseWorkflow
        Patterns = @(
            "cyclonedxBom",
            "google/osv-scanner-action/osv-scanner-action@v2.3.8",
            "check-osv-report.ps1",
            "hostshield-bom.cdx.json",
            "osv-results.json",
            "release-provenance.ps1",
            "upload-artifact"
        )
    }
    "tools/release-provenance.ps1" = @{
        Text = $releaseProvenance
        Patterns = @(
            "SbomPath",
            "OsvReportPath",
            "OsvAllowlistPath",
            "hostshield-bom.cdx.json",
            "osv-results.json",
            "OSV policy fails release CI"
        )
    }
    "tools/check-osv-report.ps1" = @{
        Text = $osvPolicyScript
        Patterns = @(
            "MinimumSeverity",
            "HIGH",
            "CRITICAL",
            "allowlist",
            "expires"
        )
    }
}

foreach ($file in $releaseGatePatterns.Keys) {
    foreach ($pattern in $releaseGatePatterns[$file].Patterns) {
        if ($releaseGatePatterns[$file].Text -notmatch [regex]::Escape($pattern)) {
            $failures.Add("$file is missing required release-gate phrase: $pattern")
        }
    }
}

$forbiddenPatterns = @(
    "unpinned fallback",
    "~60 tracker",
    "ip-api.com",
    "Android 7+",
    "Kotlin-1.9",
    "Kotlin 2.0",
    "Kotlin 2.1",
    "Android SDK 35",
    "v1-v12",
    "com.hostshield.action.ENABLE",
    "com.hostshield.action.DISABLE",
    "com.hostshield.action.STATUS",
    "com.hostshield.action.REFRESH_BLOCKLIST",
    "--ei pause_minutes"
)

foreach ($doc in @("README.md", "app/README.md", "app/metadata/en-US/full_description.txt", "app/metadata/en-US/short_description.txt", $metadataChangelog)) {
    if (-not $docs.ContainsKey($doc)) {
        continue
    }
    foreach ($pattern in $forbiddenPatterns) {
        if ($docs[$doc] -match [regex]::Escape($pattern)) {
            $failures.Add("$doc still contains stale release-doc phrase: $pattern")
        }
    }
}

if ($failures.Count -gt 0) {
    Write-Error ("Release documentation consistency check failed:`n - " + ($failures -join "`n - "))
    exit 1
}

Write-Host "Release documentation is consistent for HostShield v$versionName (versionCode $versionCode)."
