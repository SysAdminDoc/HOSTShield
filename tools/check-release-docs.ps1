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
$versionCatalog = Read-RepoFile "app/gradle/libs.versions.toml"
$releaseProvenance = Read-RepoFile "tools/release-provenance.ps1"
$osvPolicyScript = Read-RepoFile "tools/check-osv-report.ps1"
$androidPageAlignmentScript = Read-RepoFile "tools/check-android-page-alignment.ps1"
$appManifest = Read-RepoFile "app/app/src/main/AndroidManifest.xml"
$fgsTypeHelper = Read-RepoFile "app/app/src/main/java/com/hostshield/service/ProtectionForegroundServiceTypes.kt"
$workManagerAudit = Read-RepoFile "docs/WORKMANAGER_AUDIT.md"
$dohResolver = Read-RepoFile "app/app/src/main/java/com/hostshield/service/DohResolver.kt"
$dnsVpnService = Read-RepoFile "app/app/src/main/java/com/hostshield/service/DnsVpnService.kt"
$doh3Resolver = Read-RepoFile "app/app/src/main/java/com/hostshield/service/Doh3Resolver.kt"
$geoIpLookup = Read-RepoFile "app/app/src/main/java/com/hostshield/util/GeoIpLookup.kt"
$versionNameMatch = [regex]::Match($appBuild, 'versionName\s*=\s*"([^"]+)"')
$versionCodeMatch = [regex]::Match($appBuild, 'versionCode\s*=\s*(\d+)')
$compileSdkMatch = [regex]::Match($appBuild, 'compileSdk\s*=\s*(\d+)')
$targetSdkMatch = [regex]::Match($appBuild, 'targetSdk\s*=\s*(\d+)')

function Get-VersionCatalogVersion {
    param([Parameter(Mandatory = $true)][string]$Name)

    $match = [regex]::Match($versionCatalog, "(?m)^\s*$([regex]::Escape($Name))\s*=\s*`"([^`"]+)`"")
    if ($match.Success) {
        return $match.Groups[1].Value
    }
    return "unknown"
}

if (-not $versionNameMatch.Success -or -not $versionCodeMatch.Success) {
    throw "Unable to parse versionName/versionCode from app/app/build.gradle.kts."
}

$versionName = $versionNameMatch.Groups[1].Value
$versionCode = $versionCodeMatch.Groups[1].Value
$compileSdk = if ($compileSdkMatch.Success) { $compileSdkMatch.Groups[1].Value } else { "unknown" }
$targetSdk = if ($targetSdkMatch.Success) { $targetSdkMatch.Groups[1].Value } else { "unknown" }
$agpVersion = Get-VersionCatalogVersion "agp"
$kotlinVersion = Get-VersionCatalogVersion "kotlin"
$cyclonedxVersion = Get-VersionCatalogVersion "cyclonedx"
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

$workflowsDir = Join-Path $repoRoot ".github/workflows"
if (Test-Path -LiteralPath $workflowsDir) {
    $workflowFiles = @(Get-ChildItem -LiteralPath $workflowsDir -File -ErrorAction SilentlyContinue)
    if ($workflowFiles.Count -gt 0) {
        $failures.Add("GitHub Actions workflows are not allowed; remove files under .github/workflows.")
    }
}

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

$dohPinManifest = Read-RepoFile "app/app/src/main/java/com/hostshield/service/DohPinManifest.kt"

$pinReviewDates = [regex]::Matches($dohPinManifest, 'reviewAfter\s*=\s*"(\d{4}-\d{2}-\d{2})"')
$pinExpiryDates = [regex]::Matches($dohPinManifest, 'expiresAfter\s*=\s*"(\d{4}-\d{2}-\d{2})"')
$today = [DateTime]::UtcNow.Date

foreach ($match in $pinReviewDates) {
    $reviewDate = [DateTime]::ParseExact($match.Groups[1].Value, "yyyy-MM-dd", [Globalization.CultureInfo]::InvariantCulture)
    if ($today -gt $reviewDate) {
        $failures.Add("DohPinManifest has a reviewAfter date ($($match.Groups[1].Value)) that is past due. Certificate pins need rotation review.")
    }
}

foreach ($match in $pinExpiryDates) {
    $expiryDate = [DateTime]::ParseExact($match.Groups[1].Value, "yyyy-MM-dd", [Globalization.CultureInfo]::InvariantCulture)
    if ($today -gt $expiryDate) {
        $failures.Add("DohPinManifest has an expiresAfter date ($($match.Groups[1].Value)) that is expired. Certificate pins MUST be rotated before release.")
    }
}

$pinManifestVersion = [regex]::Match($dohPinManifest, 'const\s+val\s+VERSION\s*=\s*(\d+)')
if (-not $pinManifestVersion.Success -or [int]$pinManifestVersion.Groups[1].Value -lt 1) {
    $failures.Add("DohPinManifest.VERSION must be a positive integer.")
}

$providerCount = [regex]::Matches($dohPinManifest, 'ProviderPins\(').Count
if ($providerCount -lt 3) {
    $failures.Add("DohPinManifest must cover at least 3 DoH providers (found $providerCount).")
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
        "v1-v15",
        "without GitHub Actions workflows"
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
            "alias(libs.plugins.cyclonedx)",
            "hostshield-bom.cdx.json",
            "componentVersion"
        )
    }
    "app/gradle/libs.versions.toml" = @{
        Text = $versionCatalog
        Patterns = @(
            "agp = `"$agpVersion`"",
            "kotlin = `"$kotlinVersion`"",
            "cyclonedx = `"$cyclonedxVersion`"",
            "android-application",
            "kotlin-compose",
            "cyclonedx"
        )
    }
    "tools/release-provenance.ps1" = @{
        Text = $releaseProvenance
        Patterns = @(
            "SbomPath",
            "OsvReportPath",
            "OsvAllowlistPath",
            "PageAlignmentReportPath",
            "hostshield-bom.cdx.json",
            "osv-results.json",
            "android-page-alignment.txt",
            "16 KB",
            "OSV policy fails local release validation"
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
    "tools/check-android-page-alignment.ps1" = @{
        Text = $androidPageAlignmentScript
        Patterns = @(
            "zipalign",
            "-P",
            "16",
            "bundletool",
            "PAGE_ALIGNMENT_16K",
            "bundletool dump config"
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

$systemExemptedServices = [regex]::Matches($appManifest, 'foregroundServiceType="systemExempted"').Count
if ($systemExemptedServices -lt 3) {
    $failures.Add("AndroidManifest.xml must keep VPN/root/proxy protection services on systemExempted foreground-service type.")
}
if ($appManifest -match 'foregroundServiceType="dataSync"') {
    $failures.Add("AndroidManifest.xml still contains stale dataSync foreground-service type.")
}
if ($fgsTypeHelper -notmatch 'FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED' -or $fgsTypeHelper -match 'FOREGROUND_SERVICE_TYPE_DATA_SYNC') {
    $failures.Add("ProtectionForegroundServiceTypes.kt must use SYSTEM_EXEMPTED runtime type, not DATA_SYNC.")
}
if ($workManagerAudit -match '\bdataSync\b' -or $workManagerAudit -match 'FOREGROUND_SERVICE_TYPE_DATA_SYNC') {
    $failures.Add("docs/WORKMANAGER_AUDIT.md still contains stale dataSync foreground-service claims.")
}
foreach ($doc in @("README.md", "app/README.md", "app/metadata/en-US/full_description.txt")) {
    if ($docs[$doc] -notmatch [regex]::Escape("targetSdk $targetSdk")) {
        $failures.Add("$doc is missing targetSdk $targetSdk platform claim.")
    }
}

if ($dohResolver -notmatch 'certificatePinner' -or $dnsVpnService -notmatch 'failClosedEncrypted') {
    $failures.Add("Encrypted-DNS fail-closed code claims are not backed by DohResolver pinning plus DnsVpnService.failClosedEncrypted.")
}
if ($docs["README.md"] -notmatch 'fail-closed' -or $docs["app/README.md"] -notmatch 'fail-closed') {
    $failures.Add("README docs must continue to state the encrypted-DNS fail-closed posture.")
}

if ($doh3Resolver -notmatch 'EMBEDDED_CRONET_ENABLED\s*=\s*false') {
    $failures.Add("Doh3Resolver must remain explicitly disabled while embedded Cronet is unavailable.")
}
if ($appBuild -match 'cronet' -or $versionCatalog -match 'cronet') {
    $failures.Add("Build files still reference Cronet despite the disabled embedded DoH3 posture.")
}

$assetRoot = Join-Path $repoRoot "app/app/src/main/assets"
$geoIpAssets = @()
if (Test-Path -LiteralPath $assetRoot) {
    $geoIpAssets = Get-ChildItem -LiteralPath $assetRoot -Recurse -File |
        Where-Object { $_.Name -match 'GeoLite|GeoIP|\.mmdb$' }
}
if ($geoIpAssets.Count -gt 0) {
    $failures.Add("Offline GeoIP assets are present but the current GeoIpLookup path is ipapi-only: $($geoIpAssets.Name -join ', ')")
}
if ($appBuild -match 'geoip2|MaxMind|GeoLite' -or $versionCatalog -match 'geoip2|MaxMind|GeoLite') {
    $failures.Add("Build files still reference removed offline GeoIP dependencies.")
}
if ($geoIpLookup -notmatch 'https://ipapi.co/' -or $geoIpLookup -match 'MaxMind|GeoLite|OfflineGeoIp') {
    $failures.Add("GeoIpLookup.kt must reflect the current bounded ipapi.co implementation and not stale offline GeoIP paths.")
}
if ($docs["README.md"] -match 'OfflineGeoIp\.kt' -or
    $docs["app/metadata/en-US/full_description.txt"] -match 'offline GeoIP|MaxMind|GeoLite') {
    $failures.Add("Current release docs still claim removed offline GeoIP / MaxMind support.")
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

$currentLocalDnsClaimPatterns = @(
    "Portable Pi-hole",
    "private-network clients can use the phone as a DNS filter",
    "LocalDnsServer.kt     # LAN DNS server on port 5353"
)

foreach ($doc in @("README.md", "app/README.md", "app/metadata/en-US/full_description.txt", "app/metadata/en-US/short_description.txt")) {
    foreach ($pattern in $currentLocalDnsClaimPatterns) {
        if ($docs[$doc] -match [regex]::Escape($pattern)) {
            $failures.Add("$doc contains an unwired Local DNS Server release-doc claim: $pattern")
        }
    }
}

$rootDocForbiddenPhrases = @(
    "offline GeoIP",
    "MaxMind",
    "GeoLite",
    "OfflineGeoIp",
    "unpinned fallback",
    "dataSync foreground"
)

$rootDocFiles = @("LOGO_PROMPTS.md")
foreach ($rootDoc in $rootDocFiles) {
    if (Test-RepoFile $rootDoc) {
        $rootDocContent = Read-RepoFile $rootDoc
        foreach ($phrase in $rootDocForbiddenPhrases) {
            if ($rootDocContent -match [regex]::Escape($phrase)) {
                $failures.Add("$rootDoc still contains stale product claim: $phrase")
            }
        }
    }
}

if ($failures.Count -gt 0) {
    Write-Error ("Release documentation consistency check failed:`n - " + ($failures -join "`n - "))
    exit 1
}

Write-Host "Release documentation is consistent for HostShield v$versionName (versionCode $versionCode)."
