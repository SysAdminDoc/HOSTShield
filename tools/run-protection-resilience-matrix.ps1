[CmdletBinding()]
param(
    [string]$OutputPath = "artifacts/release-provenance/protection-resilience-matrix.json",
    [string]$PackageName = "",
    [string]$AdbPath = "adb",
    [string]$Serial = "",
    [switch]$RequireDevice,
    [switch]$SkipConnectedTests,
    [switch]$AttemptVpnStartStop,
    [switch]$AttemptRecoveryObservation,
    [switch]$RequireRecoveryAdvisory,
    [ValidateRange(0, 3600)][int]$RecoveryObservationSeconds = 0,
    [ValidateRange(0, 37)][int]$ExpectedSdk = 0,
    [string]$UpdateApkPath = "",
    [string]$GradleTask = ":app:connectedFullDebugAndroidTest",
    [string]$InstrumentationClass = "com.hostshield.ui.ProtectionResilienceMatrixTest"
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptRoot "..")
$scenarioResults = New-Object System.Collections.Generic.List[object]
$deviceObservations = New-Object System.Collections.Generic.List[object]

if ($AttemptRecoveryObservation -and $RecoveryObservationSeconds -eq 0) {
    $RecoveryObservationSeconds = 125
}
if ($RequireRecoveryAdvisory -and $RecoveryObservationSeconds -eq 0) {
    $RecoveryObservationSeconds = 125
    $AttemptRecoveryObservation = $true
}

function Resolve-RepoOutputPath {
    param([Parameter(Mandatory = $true)][string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }
    return Join-Path $repoRoot $Path
}

function Resolve-RepoInputPath {
    param([Parameter(Mandatory = $true)][string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }
    return Join-Path $repoRoot $Path
}

function Read-RepoFile {
    param([Parameter(Mandatory = $true)][string]$Path)
    return Get-Content -Raw -LiteralPath (Join-Path $repoRoot $Path)
}

function Add-ScenarioResult {
    param(
        [Parameter(Mandatory = $true)][string]$Id,
        [Parameter(Mandatory = $true)][string]$Title,
        [Parameter(Mandatory = $true)][string]$Mode,
        [Parameter(Mandatory = $true)][string]$Status,
        [Parameter(Mandatory = $true)][string]$Details,
        [hashtable]$Evidence = @{},
        [bool]$Required = $true
    )

    $scenarioResults.Add([ordered]@{
        id = $Id
        title = $Title
        mode = $Mode
        status = $Status
        required = $Required
        details = $Details
        evidence = $Evidence
    }) | Out-Null
}

function Add-DeviceObservation {
    param(
        [Parameter(Mandatory = $true)][string]$Id,
        [Parameter(Mandatory = $true)][string]$Status,
        [Parameter(Mandatory = $true)][string]$Details,
        [hashtable]$Evidence = @{}
    )

    $deviceObservations.Add([ordered]@{
        id = $Id
        status = $Status
        details = $Details
        evidence = $Evidence
    }) | Out-Null
}

function Test-ContainsAll {
    param(
        [Parameter(Mandatory = $true)][string]$Text,
        [Parameter(Mandatory = $true)][string[]]$Patterns
    )

    foreach ($pattern in $Patterns) {
        if ($Text -notmatch [regex]::Escape($pattern)) {
            return $false
        }
    }
    return $true
}

function Find-Adb {
    $command = Get-Command $AdbPath -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $roots = @()
    if ($env:ANDROID_HOME) { $roots += $env:ANDROID_HOME }
    if ($env:ANDROID_SDK_ROOT) { $roots += $env:ANDROID_SDK_ROOT }
    $localSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path -LiteralPath $localSdk) { $roots += $localSdk }

    foreach ($root in $roots | Select-Object -Unique) {
        $candidate = Join-Path $root "platform-tools\adb.exe"
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }
    return $null
}

function Ensure-AndroidEnvironment {
    if (-not $env:JAVA_HOME) {
        $androidStudioJbr = Join-Path $env:ProgramFiles "Android\Android Studio\jbr"
        if (Test-Path -LiteralPath (Join-Path $androidStudioJbr "bin\java.exe")) {
            $env:JAVA_HOME = $androidStudioJbr
            $env:Path = (Join-Path $androidStudioJbr "bin") + [System.IO.Path]::PathSeparator + $env:Path
        }
    }

    if (-not $env:ANDROID_HOME) {
        $localSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
        if (Test-Path -LiteralPath $localSdk) {
            $env:ANDROID_HOME = $localSdk
        }
    }
}

function Invoke-External {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    $oldErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & $FilePath @Arguments 2>&1
        return [ordered]@{
            exit_code = $LASTEXITCODE
            output = ($output -join "`n").Trim()
        }
    } finally {
        $ErrorActionPreference = $oldErrorActionPreference
    }
}

function Get-AdbDevices {
    param([Parameter(Mandatory = $true)][string]$AdbExe)

    $rawResult = Invoke-External -FilePath $AdbExe -Arguments @("devices")
    $raw = $rawResult.output -split "`n"
    $rows = @($raw | Where-Object { $_ -match "\tdevice$" })
    return @($rows | ForEach-Object { ($_ -split "\s+")[0] })
}

function Invoke-AdbShell {
    param(
        [Parameter(Mandatory = $true)][string]$AdbExe,
        [Parameter(Mandatory = $true)][string]$DeviceSerial,
        [Parameter(Mandatory = $true)][string]$Command
    )

    return Invoke-External -FilePath $AdbExe -Arguments @("-s", $DeviceSerial, "shell", $Command)
}

$manifest = Read-RepoFile "app/app/src/main/AndroidManifest.xml"
$starter = Read-RepoFile "app/app/src/main/java/com/hostshield/service/ProtectionServiceStarter.kt"
$diagnosticStore = Read-RepoFile "app/app/src/main/java/com/hostshield/util/DiagnosticEventStore.kt"
$dnsVpnService = Read-RepoFile "app/app/src/main/java/com/hostshield/service/DnsVpnService.kt"
$bootReceiver = Read-RepoFile "app/app/src/main/java/com/hostshield/service/BootReceiver.kt"
$privateSpaceDetector = Read-RepoFile "app/app/src/main/java/com/hostshield/util/PrivateSpaceDetector.kt"
$homeWarnings = Read-RepoFile "app/app/src/main/java/com/hostshield/ui/screens/home/HomeWarningsSection.kt"
$recoveryDetector = Read-RepoFile "app/app/src/main/java/com/hostshield/util/Android16VpnRecoveryDetector.kt"
$recoveryMonitor = Read-RepoFile "app/app/src/main/java/com/hostshield/service/VpnRecoveryMonitor.kt"
$connectedTest = Read-RepoFile "app/app/src/androidTest/java/com/hostshield/ui/ProtectionResilienceMatrixTest.kt"

$systemExemptedCount = [regex]::Matches($manifest, 'foregroundServiceType="systemExempted"').Count
Add-ScenarioResult `
    -Id "vpn-start-stop" `
    -Title "VPN start-stop foreground protection path" `
    -Mode "connected-or-manual" `
    -Status $(if (
        $systemExemptedCount -ge 3 -and
        (Test-ContainsAll $dnsVpnService @("DiagnosticEventType.VPN_START", "DiagnosticEventType.VPN_STOP")) -and
        (Test-ContainsAll $connectedTest @("vpn_start", "vpn_stop"))
    ) { "pass" } else { "fail" }) `
    -Details "Release matrix verifies systemExempted protection services and diagnostic VPN start/stop event wire names; run with -AttemptVpnStartStop on an already VPN-authorized device for live start/stop evidence." `
    -Evidence @{
        system_exempted_service_count = $systemExemptedCount
        live_attempt = [bool]$AttemptVpnStartStop
    }

Add-ScenarioResult `
    -Id "always-on-lockdown-advisory" `
    -Title "Always-on lockdown and Android 16 recovery advisory" `
    -Mode "connected-or-static" `
    -Status $(if (
        (Test-ContainsAll $dnsVpnService @("VpnRecoveryMonitor.advisory", "vpnRecoveryAdvisory")) -and
        (Test-ContainsAll $recoveryDetector @("ANDROID_16_SDK", "MIN_OBSERVATION_WINDOW_MS", "shouldShowRecoveryAdvisory")) -and
        (Test-ContainsAll $recoveryMonitor @("VPN_RECOVERY_SNAPSHOT", "elapsed_since_vpn_start_ms", "inbound_packet_count")) -and
        (Test-ContainsAll $homeWarnings @("warning_vpn_recovery_root", "warning_vpn_recovery_no_root")) -and
        (Test-ContainsAll $connectedTest @("VPN recovery advisory", "Restart device to recover the VPN stack"))
    ) { "pass" } else { "fail" }) `
    -Details "Static and connected UI checks cover the recovery banner; runtime mode captures the detector observation window and zero-inbound evidence when requested." `
    -Evidence @{
        recovery_observation_requested = [bool]($AttemptRecoveryObservation -or $RecoveryObservationSeconds -gt 0)
        expected_sdk = $ExpectedSdk
        update_apk_requested = [bool]$UpdateApkPath
    }

Add-ScenarioResult `
    -Id "boot-update-resume" `
    -Title "Boot and update resume restart path" `
    -Mode "static-with-manual-device-step" `
    -Status $(if (
        (Test-ContainsAll $manifest @("android.intent.action.BOOT_COMPLETED", "android.intent.action.QUICKBOOT_POWERON")) -and
        (Test-ContainsAll $bootReceiver @("ProtectionServiceStarter.startForegroundService", "RootDnsService.start", "DnsProxyService.start"))
    ) { "pass" } else { "fail" }) `
    -Details "Boot receiver restart paths are statically verified; actual device boot/update survival remains a manual observation recorded by this matrix artifact." `
    -Evidence @{}

Add-ScenarioResult `
    -Id "work-profile-private-space-warning" `
    -Title "Work profile and Private Space warning render" `
    -Mode "connected-ui" `
    -Status $(if (
        (Test-ContainsAll $privateSpaceDetector @("Private Space", "Apps in Private Space bypass VPN-based blocking entirely")) -and
        (Test-ContainsAll $connectedTest @("Private Space Detected", "Apps in Private Space bypass VPN-based blocking entirely."))
    ) { "pass" } else { "fail" }) `
    -Details "Connected Compose test renders the Private Space warning; adb records current user/profile list when a device is present." `
    -Evidence @{}

Add-ScenarioResult `
    -Id "battery-exemption-denial" `
    -Title "Battery exemption denial and fallback messaging" `
    -Mode "connected-ui" `
    -Status $(if (
        (Test-ContainsAll $homeWarnings @("warning_battery_a11y", "warning_battery_dismiss")) -and
        (Test-ContainsAll $connectedTest @("Battery Optimization", "Battery optimization may interrupt protection. Open battery exemption settings."))
    ) { "pass" } else { "fail" }) `
    -Details "Connected Compose test covers the battery warning action and dismissal; adb records deviceidle whitelist state when a device is present." `
    -Evidence @{}

Add-ScenarioResult `
    -Id "diagnostic-event-export" `
    -Title "Diagnostic event export for protection failures" `
    -Mode "connected-diagnostic" `
    -Status $(if (
        (Test-ContainsAll $starter @("FOREGROUND_SERVICE_START_FAILED", "recordBlocking")) -and
        (Test-ContainsAll $diagnosticStore @("foreground_service_start_failed", "foreground_service_timeout")) -and
        (Test-ContainsAll $connectedTest @("foreground_service_start_failed", "foreground_service_timeout"))
    ) { "pass" } else { "fail" }) `
    -Details "Foreground-service start failures and timeouts are serialized to diagnostic-events.jsonl; connected test writes and reads the same wire names." `
    -Evidence @{}

$adbExe = Find-Adb
$selectedDevice = $null
$deviceInfo = [ordered]@{
    present = $false
}

if (-not $adbExe) {
    Add-DeviceObservation -Id "adb" -Status $(if ($RequireDevice) { "fail" } else { "skipped" }) -Details "adb was not found in PATH or Android SDK platform-tools."
} else {
    $devices = Get-AdbDevices -AdbExe $adbExe
    if ($Serial) {
        if ($devices -contains $Serial) {
            $selectedDevice = $Serial
        } else {
            Add-DeviceObservation -Id "device-selection" -Status $(if ($RequireDevice) { "fail" } else { "skipped" }) -Details "Requested serial '$Serial' is not connected." -Evidence @{ connected_devices = $devices }
        }
    } elseif ($devices.Count -eq 1) {
        $selectedDevice = $devices[0]
    } elseif ($devices.Count -gt 1) {
        Add-DeviceObservation -Id "device-selection" -Status $(if ($RequireDevice) { "fail" } else { "skipped" }) -Details "Multiple adb devices are connected; rerun with -Serial." -Evidence @{ connected_devices = $devices }
    } else {
        Add-DeviceObservation -Id "device-selection" -Status $(if ($RequireDevice) { "fail" } else { "skipped" }) -Details "No adb device is connected." -Evidence @{}
    }
}

if ($selectedDevice) {
    $deviceInfo.present = $true
    $deviceInfo.serial = $selectedDevice

    $sdk = Invoke-AdbShell $adbExe $selectedDevice "getprop ro.build.version.sdk"
    $manufacturer = Invoke-AdbShell $adbExe $selectedDevice "getprop ro.product.manufacturer"
    $model = Invoke-AdbShell $adbExe $selectedDevice "getprop ro.product.model"
    if ($UpdateApkPath) {
        $apk = Resolve-RepoInputPath $UpdateApkPath
        if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) {
            throw "Update APK was not found: $apk"
        }
        $update = Invoke-External -FilePath $adbExe -Arguments @(
            "-s",
            $selectedDevice,
            "install",
            "-r",
            "-d",
            "-g",
            $apk
        )
        Add-DeviceObservation `
            -Id "app-update" `
            -Status $(if ($update.exit_code -eq 0 -and $update.output -match "Success") { "pass" } else { "fail" }) `
            -Details "Installed the supplied APK over the existing package to exercise the post-update recovery observation path." `
            -Evidence @{
                apk = $apk
                exit_code = $update.exit_code
                output = $update.output
            }
    } else {
        Add-DeviceObservation -Id "app-update" -Status "skipped" -Details "No -UpdateApkPath was supplied; no package update was performed."
    }

    if (-not $PackageName) {
        $packageCandidates = @(
            "com.hostshield.debug",
            "com.hostshield.play.debug",
            "com.hostshield",
            "com.hostshield.play"
        )
        foreach ($candidate in $packageCandidates) {
            $candidatePath = Invoke-AdbShell $adbExe $selectedDevice "pm path $candidate"
            if ($candidatePath.exit_code -eq 0 -and $candidatePath.output -match [regex]::Escape($candidate)) {
                $PackageName = $candidate
                break
            }
        }
        if (-not $PackageName) {
            $PackageName = "com.hostshield.debug"
        }
    }

    $packagePath = Invoke-AdbShell $adbExe $selectedDevice "pm path $PackageName"
    $alwaysOn = Invoke-AdbShell $adbExe $selectedDevice "settings get secure always_on_vpn_app"
    $lockdown = Invoke-AdbShell $adbExe $selectedDevice "settings get secure always_on_vpn_lockdown"
    $users = Invoke-AdbShell $adbExe $selectedDevice "cmd user list"
    $batteryWhitelist = Invoke-AdbShell $adbExe $selectedDevice "dumpsys deviceidle whitelist"
    $vpnDump = Invoke-AdbShell $adbExe $selectedDevice "dumpsys vpn"
    $connectivityDump = Invoke-AdbShell $adbExe $selectedDevice "dumpsys connectivity"

    $deviceInfo.sdk = $sdk.output
    $deviceInfo.manufacturer = $manufacturer.output
    $deviceInfo.model = $model.output
    $deviceInfo.package_installed = ($packagePath.exit_code -eq 0 -and $packagePath.output -match [regex]::Escape($PackageName))

    if ($ExpectedSdk -gt 0) {
        $sdkMatches = $sdk.output.Trim() -eq [string]$ExpectedSdk
        Add-DeviceObservation `
            -Id "sdk-selection" `
            -Status $(if ($sdkMatches) { "pass" } else { "fail" }) `
            -Details "Compared the connected device API level with -ExpectedSdk." `
            -Evidence @{
                expected_sdk = $ExpectedSdk
                actual_sdk = $sdk.output
            }
    }

    Add-DeviceObservation -Id "always-on-lockdown-state" -Status "recorded" -Details "Captured secure VPN settings from the connected device." -Evidence @{
        always_on_vpn_app = $alwaysOn.output
        always_on_vpn_lockdown = $lockdown.output
        vpn_dump_contains_package = ($vpnDump.output -match [regex]::Escape($PackageName))
        vpn_dump_tail = ($vpnDump.output -split "`n" | Select-Object -Last 80) -join "`n"
        connectivity_dump_tail = ($connectivityDump.output -split "`n" | Select-Object -Last 40) -join "`n"
    }
    Add-DeviceObservation -Id "profile-state" -Status "recorded" -Details "Captured Android user/profile list for work-profile or Private Space review." -Evidence @{
        users = $users.output
    }
    Add-DeviceObservation -Id "battery-exemption-state" -Status "recorded" -Details "Captured deviceidle whitelist state for HostShield package review." -Evidence @{
        hostshield_listed = ($batteryWhitelist.output -match [regex]::Escape($PackageName))
        raw = $batteryWhitelist.output
    }

    if (-not $SkipConnectedTests) {
        Ensure-AndroidEnvironment
        $gradle = Join-Path $repoRoot "app\gradlew.bat"
        $gradleResult = Invoke-External -FilePath $gradle -Arguments @(
            "-p",
            (Join-Path $repoRoot "app"),
            $GradleTask,
            "-Pandroid.testInstrumentationRunnerArguments.class=$InstrumentationClass"
        )
        Add-DeviceObservation `
            -Id "connected-resilience-tests" `
            -Status $(if ($gradleResult.exit_code -eq 0) { "pass" } else { "fail" }) `
            -Details "Ran $GradleTask for $InstrumentationClass." `
            -Evidence @{
                exit_code = $gradleResult.exit_code
                output_tail = ($gradleResult.output -split "`n" | Select-Object -Last 80) -join "`n"
            }
    } else {
        Add-DeviceObservation -Id "connected-resilience-tests" -Status "skipped" -Details "Skipped by -SkipConnectedTests."
    }

    $recoveryObservationRequested = $AttemptRecoveryObservation -or $RecoveryObservationSeconds -gt 0
    if ($recoveryObservationRequested) {
        $sdkNumber = 0
        [int]::TryParse($sdk.output.Trim(), [ref]$sdkNumber) | Out-Null
        if ($sdkNumber -lt 36) {
            Add-DeviceObservation `
                -Id "vpn-recovery-observation" `
                -Status "fail" `
                -Details "The recovery detector requires Android 16/API 36 or newer." `
                -Evidence @{ actual_sdk = $sdk.output }
        } elseif (-not $deviceInfo.package_installed) {
            Add-DeviceObservation `
                -Id "vpn-recovery-observation" `
                -Status "fail" `
                -Details "The recovery observation requires the HostShield package to be installed." `
                -Evidence @{}
        } else {
            $start = Invoke-External -FilePath $adbExe -Arguments @(
                "-s", $selectedDevice, "shell", "am", "broadcast",
                "-a", "com.hostshield.ACTION_ENABLE", "-p", $PackageName
            )
            $deadline = [DateTime]::UtcNow.AddSeconds($RecoveryObservationSeconds)
            while ([DateTime]::UtcNow -lt $deadline) {
                $remaining = [int][Math]::Ceiling(($deadline - [DateTime]::UtcNow).TotalSeconds)
                Start-Sleep -Seconds ([Math]::Min(5, [Math]::Max(1, $remaining)))
            }

            $recoveryEvents = Invoke-AdbShell $adbExe $selectedDevice "run-as $PackageName cat files/diagnostics/diagnostic-events.jsonl"
            $recoveryLog = Invoke-External -FilePath $adbExe -Arguments @(
                "-s", $selectedDevice, "shell", "logcat", "-d", "-v", "threadtime", "-s", "HostShield:I"
            )
            $snapshotSeen = $recoveryEvents.output -match '"type"\s*:\s*"vpn_recovery_snapshot"' -or
                $recoveryLog.output -match "VPN recovery observation window reached"
            $advisorySeen = $recoveryLog.output -match "Android 16 VPN recovery advisory raised"
            $alwaysOnConfigured =
                $alwaysOn.output.Trim() -and
                $alwaysOn.output.Trim() -ne "null" -and
                $alwaysOn.output.Trim() -ne "0"
            $lockdownConfigured = $lockdown.output.Trim() -eq "1" -or
                $lockdown.output.Trim().ToLowerInvariant() -eq "true"
            $observationStatus = if ($advisorySeen) {
                "pass"
            } elseif ($snapshotSeen) {
                "pass"
            } elseif (-not $alwaysOnConfigured -or -not $lockdownConfigured) {
                "not_configured"
            } else {
                "not_observed"
            }
            if ($RequireRecoveryAdvisory -and -not $advisorySeen) {
                $observationStatus = "fail"
            }
            Add-DeviceObservation `
                -Id "vpn-recovery-observation" `
                -Status $observationStatus `
                -Details "Requested protection through the app's automation receiver and observed the Android 16 recovery window for $RecoveryObservationSeconds seconds; advisory evidence is required only with -RequireRecoveryAdvisory." `
                -Evidence @{
                    start_exit_code = $start.exit_code
                    start_output = $start.output
                    always_on_vpn_app = $alwaysOn.output
                    always_on_vpn_lockdown = $lockdown.output
                    recovery_snapshot_seen = $snapshotSeen
                    recovery_advisory_seen = $advisorySeen
                    diagnostic_events_exit_code = $recoveryEvents.exit_code
                    diagnostic_events_tail = ($recoveryEvents.output -split "`n" | Select-Object -Last 20) -join "`n"
                    recovery_log_tail = ($recoveryLog.output -split "`n" | Select-Object -Last 80) -join "`n"
                }
            $stopAfterObservation = Invoke-External -FilePath $adbExe -Arguments @(
                "-s", $selectedDevice, "shell", "am", "broadcast",
                "-a", "com.hostshield.ACTION_DISABLE", "-p", $PackageName
            )
        }
    } else {
        Add-DeviceObservation -Id "vpn-recovery-observation" -Status "manual_required" -Details "Recovery observation was not attempted. Supply -AttemptRecoveryObservation or -RecoveryObservationSeconds, optionally with -UpdateApkPath and -ExpectedSdk 36/37."
    }

    if ($AttemptVpnStartStop) {
        $start = Invoke-External -FilePath $adbExe -Arguments @("-s", $selectedDevice, "shell", "am", "broadcast", "-a", "com.hostshield.ACTION_ENABLE", "-p", $PackageName)
        Start-Sleep -Seconds 3
        $vpnDumpAfterStart = Invoke-AdbShell $adbExe $selectedDevice "dumpsys vpn"
        $stop = Invoke-External -FilePath $adbExe -Arguments @("-s", $selectedDevice, "shell", "am", "broadcast", "-a", "com.hostshield.ACTION_DISABLE", "-p", $PackageName)
        Add-DeviceObservation -Id "vpn-start-stop-live" -Status "recorded" -Details "Attempted live VPN service start and stop; requires prior user VPN consent to prove tunnel activation." -Evidence @{
            start_exit_code = $start.exit_code
            start_output = $start.output
            vpn_dump_after_start = $vpnDumpAfterStart.output
            stop_exit_code = $stop.exit_code
            stop_output = $stop.output
        }
    } else {
        Add-DeviceObservation -Id "vpn-start-stop-live" -Status "manual_required" -Details "Live VPN start/stop was not attempted. Rerun with -AttemptVpnStartStop on a test device with VPN consent already granted."
    }

    $events = Invoke-AdbShell $adbExe $selectedDevice "run-as $PackageName cat files/diagnostics/diagnostic-events.jsonl"
    Add-DeviceObservation -Id "diagnostic-events-jsonl" -Status $(if ($events.exit_code -eq 0) { "recorded" } else { "manual_required" }) -Details "Read diagnostic-events.jsonl through run-as when the installed build allows it." -Evidence @{
        exit_code = $events.exit_code
        contains_start_failure = ($events.output -match "foreground_service_start_failed")
        contains_timeout = ($events.output -match "foreground_service_timeout")
        contains_vpn_start = ($events.output -match "vpn_start")
        contains_vpn_stop = ($events.output -match "vpn_stop")
        contains_recovery_snapshot = ($events.output -match "vpn_recovery_snapshot")
        output_tail = ($events.output -split "`n" | Select-Object -Last 40) -join "`n"
    }
}

$output = Resolve-RepoOutputPath $OutputPath
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $output) | Out-Null

$report = [ordered]@{
    schema = 1
    project = "HostShield"
    generated_at_utc = [DateTime]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")
    git_commit = (& git -C $repoRoot rev-parse HEAD).Trim()
    package_name = $PackageName
    instrumentation_class = $InstrumentationClass
    device = $deviceInfo
    scenarios = $scenarioResults
    device_observations = $deviceObservations
}

$json = $report | ConvertTo-Json -Depth 12
Set-Content -LiteralPath $output -Encoding UTF8 -Value $json
Write-Host "Wrote protection resilience matrix: $output"

$requiredFailures = @($scenarioResults | Where-Object { $_.required -and $_.status -eq "fail" })
$deviceFailures = @($deviceObservations | Where-Object { $_.status -eq "fail" })
if ($requiredFailures.Count -gt 0 -or $deviceFailures.Count -gt 0) {
    exit 1
}
