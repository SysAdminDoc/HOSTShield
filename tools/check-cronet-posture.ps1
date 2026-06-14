[CmdletBinding()]
param(
    [string]$MetadataUrl = "https://dl.google.com/dl/android/maven2/org/chromium/net/cronet-embedded/maven-metadata.xml"
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptRoot "..")
$appBuildPath = Join-Path $repoRoot "app/app/build.gradle.kts"
$doh3ResolverPath = Join-Path $repoRoot "app/app/src/main/java/com/hostshield/service/Doh3Resolver.kt"
$appBuild = Get-Content -Raw -LiteralPath $appBuildPath
$doh3Resolver = Get-Content -Raw -LiteralPath $doh3ResolverPath

$declaredMatch = [regex]::Match(
    $appBuild,
    'org\.chromium\.net:cronet-embedded:([^")]+)'
)

if (-not $declaredMatch.Success) {
    if (
        $doh3Resolver -match 'EMBEDDED_CRONET_ENABLED\s*=\s*false' -and
        $doh3Resolver -notmatch 'org\.chromium\.net'
    ) {
        Write-Host "Cronet embedded posture OK: dependency is absent and embedded DoH3 transport is disabled."
        exit 0
    }

    throw "Unable to find org.chromium.net:cronet-embedded dependency and DoH3 resolver is not explicitly disabled."
}

$declaredVersion = $declaredMatch.Groups[1].Value

try {
    [xml]$metadata = (Invoke-WebRequest -UseBasicParsing -Uri $MetadataUrl).Content
} catch {
    throw "Unable to read Cronet metadata from $MetadataUrl. $($_.Exception.Message)"
}

$latest = [string]$metadata.metadata.versioning.latest
$release = [string]$metadata.metadata.versioning.release
$lastUpdated = [string]$metadata.metadata.versioning.lastUpdated
$versions = @($metadata.metadata.versioning.versions.version | ForEach-Object { [string]$_ })

if (-not $latest -or -not $release -or $versions.Count -eq 0) {
    throw "Cronet metadata from $MetadataUrl did not include latest/release/version entries."
}

if ($declaredVersion -notin $versions) {
    throw "Declared Cronet version $declaredVersion is not present in Google Maven metadata."
}

if ($declaredVersion -ne $latest -or $declaredVersion -ne $release) {
    throw "Cronet embedded is stale. Declared $declaredVersion, latest $latest, release $release."
}

Write-Host "Cronet embedded posture OK: declared $declaredVersion matches Google Maven latest/release $latest (metadata lastUpdated $lastUpdated)."
