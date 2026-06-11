[CmdletBinding()]
param(
    [string]$MetadataUrl = "https://dl.google.com/dl/android/maven2/org/chromium/net/cronet-embedded/maven-metadata.xml"
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptRoot "..")
$appBuildPath = Join-Path $repoRoot "app/app/build.gradle.kts"
$appBuild = Get-Content -Raw -LiteralPath $appBuildPath

$declaredMatch = [regex]::Match(
    $appBuild,
    'org\.chromium\.net:cronet-embedded:([^")]+)'
)

if (-not $declaredMatch.Success) {
    throw "Unable to find org.chromium.net:cronet-embedded dependency in app/app/build.gradle.kts."
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
