[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptRoot "..")

function Read-RepoFile {
    param([Parameter(Mandatory = $true)][string]$Path)
    return Get-Content -Raw -LiteralPath (Join-Path $repoRoot $Path)
}

$appBuild = Read-RepoFile "app/app/build.gradle.kts"
$versionNameMatch = [regex]::Match($appBuild, 'versionName\s*=\s*"([^"]+)"')
$versionCodeMatch = [regex]::Match($appBuild, 'versionCode\s*=\s*(\d+)')

if (-not $versionNameMatch.Success -or -not $versionCodeMatch.Success) {
    throw "Unable to parse versionName/versionCode from app/app/build.gradle.kts."
}

$versionName = $versionNameMatch.Groups[1].Value
$versionCode = $versionCodeMatch.Groups[1].Value

$docs = @{
    "README.md" = Read-RepoFile "README.md"
    "app/README.md" = Read-RepoFile "app/README.md"
    "app/CHANGELOG.md" = Read-RepoFile "app/CHANGELOG.md"
}

$failures = New-Object System.Collections.Generic.List[string]

foreach ($doc in @("README.md", "app/README.md")) {
    if ($docs[$doc] -notmatch [regex]::Escape("version-$versionName")) {
        $failures.Add("$doc does not advertise version badge $versionName.")
    }
}

$requiredPatterns = @{
    "README.md" = @(
        "fail-closed",
        "405 tracker SDK signatures",
        "ipapi.co",
        "Kotlin 2.1",
        "v1-v15"
    )
    "app/README.md" = @(
        "fail-closed",
        "405 tracker SDK signatures",
        "ipapi.co",
        "Android SDK 35",
        "v$versionName"
    )
    "app/CHANGELOG.md" = @(
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

$forbiddenPatterns = @(
    "unpinned fallback",
    "~60 tracker",
    "ip-api.com",
    "Android 7+",
    "Kotlin-1.9",
    "Kotlin 2.0",
    "v1-v12"
)

foreach ($doc in @("README.md", "app/README.md")) {
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
