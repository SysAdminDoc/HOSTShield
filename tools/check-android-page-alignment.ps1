[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [string]$AabPath = "",
    [string]$OutputPath = "artifacts/release-provenance/android-page-alignment.txt",
    [string]$ZipalignPath = "",
    [string]$BundletoolPath = ""
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptRoot "..")).Path

$bundletoolVersion = "1.18.3"
$bundletoolSha256 = "A099CFA1543F55593BC2ED16A70A7C67FE54B1747BB7301F37FDFD6D91028E29"
$bundletoolUrl = "https://github.com/google/bundletool/releases/download/$bundletoolVersion/bundletool-all-$bundletoolVersion.jar"

$reportLines = New-Object System.Collections.Generic.List[string]

function Resolve-RepoInputPath {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [bool]$Required = $true
    )

    $candidate = if ([System.IO.Path]::IsPathRooted($Path)) {
        $Path
    } else {
        Join-Path $repoRoot $Path
    }

    if ($Required -and -not (Test-Path -LiteralPath $candidate)) {
        throw "Path not found: $candidate"
    }

    if (Test-Path -LiteralPath $candidate) {
        return (Resolve-Path -LiteralPath $candidate).Path
    }

    return $candidate
}

function Find-Zipalign {
    if (-not [string]::IsNullOrWhiteSpace($ZipalignPath)) {
        return Resolve-RepoInputPath $ZipalignPath
    }

    $pathCommand = Get-Command zipalign -ErrorAction SilentlyContinue
    if ($pathCommand) {
        return $pathCommand.Source
    }

    $roots = New-Object System.Collections.Generic.List[string]
    foreach ($candidate in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path -LiteralPath $candidate)) {
            $roots.Add($candidate)
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        $localSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
        if (Test-Path -LiteralPath $localSdk) {
            $roots.Add($localSdk)
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($env:HOME)) {
        $homeSdk = Join-Path $env:HOME "Android/Sdk"
        if (Test-Path -LiteralPath $homeSdk) {
            $roots.Add($homeSdk)
        }
    }

    if (Test-Path -LiteralPath "/usr/local/lib/android/sdk") {
        $roots.Add("/usr/local/lib/android/sdk")
    }

    $candidates = foreach ($root in ($roots | Select-Object -Unique)) {
        $buildTools = Join-Path $root "build-tools"
        if (-not (Test-Path -LiteralPath $buildTools)) {
            continue
        }

        Get-ChildItem -LiteralPath $buildTools -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -in @("zipalign", "zipalign.exe", "zipalign.bat") } |
            ForEach-Object {
                $versionText = Split-Path -Leaf (Split-Path -Parent $_.FullName)
                $version = try { [Version]$versionText } catch { [Version]"0.0" }
                [pscustomobject]@{
                    Path = $_.FullName
                    Version = $version
                }
            }
    }

    $selected = $candidates | Sort-Object Version, Path -Descending | Select-Object -First 1
    if ($selected) {
        return $selected.Path
    }

    throw "Unable to find zipalign. Install Android build-tools or pass -ZipalignPath."
}

function Find-Java {
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCommand) {
        return $javaCommand.Source
    }

    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        foreach ($name in @("java.exe", "java")) {
            $candidate = Join-Path $env:JAVA_HOME "bin/$name"
            if (Test-Path -LiteralPath $candidate) {
                return (Resolve-Path -LiteralPath $candidate).Path
            }
        }
    }

    throw "Unable to find java. Install JDK 17 or ensure java is on PATH."
}

function New-BundletoolDescriptor {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Kind
    )

    return [pscustomobject]@{
        Path = $Path
        Kind = $Kind
    }
}

function Test-BundletoolHash {
    param([Parameter(Mandatory = $true)][string]$Path)

    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash
    if ($actual -ne $bundletoolSha256) {
        throw "Downloaded bundletool hash mismatch for $Path. Expected $bundletoolSha256 but got $actual."
    }
}

function Find-OrDownload-Bundletool {
    if (-not [string]::IsNullOrWhiteSpace($BundletoolPath)) {
        $resolved = Resolve-RepoInputPath $BundletoolPath
        $kind = if ($resolved.EndsWith(".jar", [StringComparison]::OrdinalIgnoreCase)) { "Jar" } else { "Command" }
        return New-BundletoolDescriptor -Path $resolved -Kind $kind
    }

    if (-not [string]::IsNullOrWhiteSpace($env:BUNDLETOOL_PATH)) {
        $resolved = Resolve-RepoInputPath $env:BUNDLETOOL_PATH
        $kind = if ($resolved.EndsWith(".jar", [StringComparison]::OrdinalIgnoreCase)) { "Jar" } else { "Command" }
        return New-BundletoolDescriptor -Path $resolved -Kind $kind
    }

    $command = Get-Command bundletool -ErrorAction SilentlyContinue
    if ($command) {
        return New-BundletoolDescriptor -Path $command.Source -Kind "Command"
    }

    $cacheRoot = if (-not [string]::IsNullOrWhiteSpace($env:RUNNER_TEMP)) {
        $env:RUNNER_TEMP
    } elseif (-not [string]::IsNullOrWhiteSpace($env:TEMP)) {
        $env:TEMP
    } else {
        Join-Path $repoRoot "artifacts"
    }

    New-Item -ItemType Directory -Force -Path $cacheRoot | Out-Null
    $downloaded = Join-Path $cacheRoot "bundletool-all-$bundletoolVersion.jar"

    if (Test-Path -LiteralPath $downloaded) {
        Test-BundletoolHash $downloaded
        return New-BundletoolDescriptor -Path (Resolve-Path -LiteralPath $downloaded).Path -Kind "Jar"
    }

    Invoke-WebRequest -Uri $bundletoolUrl -OutFile $downloaded
    Test-BundletoolHash $downloaded
    return New-BundletoolDescriptor -Path (Resolve-Path -LiteralPath $downloaded).Path -Kind "Jar"
}

function Invoke-CheckedProcess {
    param(
        [Parameter(Mandatory = $true)][string]$Executable,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$FailureMessage
    )

    $output = & $Executable @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    $text = ($output | ForEach-Object { $_.ToString() }) -join "`n"

    if ($exitCode -ne 0) {
        throw "$FailureMessage`n$text"
    }

    return $text.Trim()
}

$apk = Resolve-RepoInputPath $ApkPath
$aab = if ([string]::IsNullOrWhiteSpace($AabPath)) { "" } else { Resolve-RepoInputPath $AabPath }
$output = Resolve-RepoInputPath $OutputPath $false
$outputDir = Split-Path -Parent $output
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

try {
    $zipalign = Find-Zipalign
    $reportLines.Add("# Android 16 KB page alignment")
    $reportLines.Add("")
    $reportLines.Add("APK: $apk")
    $reportLines.Add("AAB: $(if ([string]::IsNullOrWhiteSpace($aab)) { 'not checked' } else { $aab })")
    $reportLines.Add("zipalign: $zipalign")
    $reportLines.Add("")

    $zipalignArgs = @("-v", "-c", "-P", "16", "4", $apk)
    $zipalignOutput = Invoke-CheckedProcess -Executable $zipalign -Arguments $zipalignArgs -FailureMessage "zipalign 16 KB verification failed for $apk."
    if ($zipalignOutput -notmatch "Verification successful") {
        throw "zipalign did not report successful verification for $apk."
    }

    $reportLines.Add("## APK zipalign -P 16")
    $reportLines.Add("")
    $reportLines.Add($zipalignOutput)
    $reportLines.Add("")

    if (-not [string]::IsNullOrWhiteSpace($aab)) {
        $bundletool = Find-OrDownload-Bundletool
        $java = Find-Java
        $reportLines.Add("bundletool: $($bundletool.Path)")
        $reportLines.Add("Command: bundletool dump config --bundle=$aab")
        $reportLines.Add("")

        $bundletoolOutput = if ($bundletool.Kind -eq "Jar") {
            Invoke-CheckedProcess -Executable $java -Arguments @("-jar", $bundletool.Path, "dump", "config", "--bundle=$aab") -FailureMessage "bundletool config dump failed for $aab."
        } else {
            Invoke-CheckedProcess -Executable $bundletool.Path -Arguments @("dump", "config", "--bundle=$aab") -FailureMessage "bundletool config dump failed for $aab."
        }

        if ($bundletoolOutput -notmatch "PAGE_ALIGNMENT_16K") {
            throw "AAB does not request PAGE_ALIGNMENT_16K: $aab"
        }

        $alignmentLines = $bundletoolOutput -split "`r?`n" | Where-Object { $_ -match "alignment|PAGE_ALIGNMENT" }
        $reportLines.Add("## AAB bundletool alignment")
        $reportLines.Add("")
        $reportLines.Add(($alignmentLines -join "`n").Trim())
        $reportLines.Add("")
    }

    $reportLines.Add("Result: PASS")
    $reportLines | Set-Content -Encoding UTF8 -LiteralPath $output
    Write-Host "Android 16 KB page alignment verified."
    Write-Host "Wrote $output"
} catch {
    $reportLines.Add("Result: FAIL")
    $reportLines.Add($_.Exception.Message)
    $reportLines | Set-Content -Encoding UTF8 -LiteralPath $output
    throw
}
