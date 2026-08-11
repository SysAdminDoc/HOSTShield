[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ReportPath,

    [string]$AllowlistPath = "tools/osv-allowlist.json",

    [ValidateSet("LOW", "MEDIUM", "HIGH", "CRITICAL")]
    [string]$MinimumSeverity = "HIGH"
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

function Get-JsonProperty {
    param(
        [Parameter(Mandatory = $true)]$Object,
        [Parameter(Mandatory = $true)][string[]]$Names
    )

    foreach ($name in $Names) {
        $property = $Object.PSObject.Properties[$name]
        if ($null -ne $property) {
            return $property.Value
        }
    }

    return $null
}

function Convert-ToArray {
    param($Value)

    if ($null -eq $Value) {
        return @()
    }
    if ($Value -is [System.Array]) {
        return $Value
    }
    return @($Value)
}

function Get-SeverityRank {
    param([string]$Severity)

    switch ($Severity.ToUpperInvariant()) {
        "CRITICAL" { return 4 }
        "HIGH" { return 3 }
        "MEDIUM" { return 2 }
        "MODERATE" { return 2 }
        "LOW" { return 1 }
        default { return 0 }
    }
}

function Get-SeverityFromScore {
    param([double]$Score)

    if ($Score -ge 9.0) {
        return "CRITICAL"
    }
    if ($Score -ge 7.0) {
        return "HIGH"
    }
    if ($Score -ge 4.0) {
        return "MEDIUM"
    }
    if ($Score -gt 0.0) {
        return "LOW"
    }
    return "UNKNOWN"
}

function Convert-CvssVectorToMap {
    param([Parameter(Mandatory = $true)][string]$Vector)

    $map = @{}
    foreach ($part in $Vector.Split("/")) {
        if ($part -match "^([A-Za-z]+):(.+)$") {
            $map[$Matches[1]] = $Matches[2]
        }
    }
    return $map
}

function Round-CvssUp {
    param([double]$Value)

    return [Math]::Ceiling(($Value * 10.0) - 0.000001) / 10.0
}

function Get-CvssV3BaseScore {
    param([Parameter(Mandatory = $true)][string]$Vector)

    $m = Convert-CvssVectorToMap $Vector
    foreach ($required in @("AV", "AC", "PR", "UI", "S", "C", "I", "A")) {
        if (-not $m.ContainsKey($required)) {
            return $null
        }
    }

    $av = @{ N = 0.85; A = 0.62; L = 0.55; P = 0.20 }[$m["AV"]]
    $ac = @{ L = 0.77; H = 0.44 }[$m["AC"]]
    $ui = @{ N = 0.85; R = 0.62 }[$m["UI"]]
    $cia = @{ H = 0.56; L = 0.22; N = 0.0 }
    $scopeChanged = $m["S"] -eq "C"
    $pr = if ($scopeChanged) {
        @{ N = 0.85; L = 0.68; H = 0.50 }[$m["PR"]]
    } else {
        @{ N = 0.85; L = 0.62; H = 0.27 }[$m["PR"]]
    }

    if ($null -eq $av -or $null -eq $ac -or $null -eq $pr -or $null -eq $ui) {
        return $null
    }

    $c = $cia[$m["C"]]
    $i = $cia[$m["I"]]
    $a = $cia[$m["A"]]
    if ($null -eq $c -or $null -eq $i -or $null -eq $a) {
        return $null
    }

    $impactSubScore = 1.0 - ((1.0 - $c) * (1.0 - $i) * (1.0 - $a))
    $impact = if ($scopeChanged) {
        (7.52 * ($impactSubScore - 0.029)) - (3.25 * [Math]::Pow(($impactSubScore - 0.02), 15.0))
    } else {
        6.42 * $impactSubScore
    }

    if ($impact -le 0.0) {
        return 0.0
    }

    $exploitability = 8.22 * $av * $ac * $pr * $ui
    $baseScore = if ($scopeChanged) {
        [Math]::Min(1.08 * ($impact + $exploitability), 10.0)
    } else {
        [Math]::Min($impact + $exploitability, 10.0)
    }

    return Round-CvssUp $baseScore
}

function Get-CvssV2BaseScore {
    param([Parameter(Mandatory = $true)][string]$Vector)

    $m = Convert-CvssVectorToMap $Vector
    foreach ($required in @("AV", "AC", "Au", "C", "I", "A")) {
        if (-not $m.ContainsKey($required)) {
            return $null
        }
    }

    $av = @{ L = 0.395; A = 0.646; N = 1.0 }[$m["AV"]]
    $ac = @{ H = 0.35; M = 0.61; L = 0.71 }[$m["AC"]]
    $au = @{ M = 0.45; S = 0.56; N = 0.704 }[$m["Au"]]
    $cia = @{ N = 0.0; P = 0.275; C = 0.660 }

    if ($null -eq $av -or $null -eq $ac -or $null -eq $au) {
        return $null
    }

    $c = $cia[$m["C"]]
    $i = $cia[$m["I"]]
    $a = $cia[$m["A"]]
    if ($null -eq $c -or $null -eq $i -or $null -eq $a) {
        return $null
    }

    $impact = 10.41 * (1.0 - ((1.0 - $c) * (1.0 - $i) * (1.0 - $a)))
    $exploitability = 20.0 * $av * $ac * $au
    $impactFactor = if ($impact -eq 0.0) { 0.0 } else { 1.176 }
    $baseScore = (((0.6 * $impact) + (0.4 * $exploitability) - 1.5) * $impactFactor)
    return [Math]::Round($baseScore, 1, [MidpointRounding]::AwayFromZero)
}

function Get-CvssScore {
    param([string]$ScoreText)

    if ([string]::IsNullOrWhiteSpace($ScoreText)) {
        return $null
    }

    $score = 0.0
    if ([double]::TryParse(
            $ScoreText,
            [Globalization.NumberStyles]::Float,
            [Globalization.CultureInfo]::InvariantCulture,
            [ref]$score
        )) {
        return $score
    }

    if ($ScoreText.StartsWith("CVSS:3.")) {
        return Get-CvssV3BaseScore $ScoreText
    }

    if ($ScoreText -match "AV:[NAL]/AC:[HML]/Au:[MSN]/C:[NPC]/I:[NPC]/A:[NPC]") {
        return Get-CvssV2BaseScore $ScoreText
    }

    return $null
}

function Get-VulnerabilitySeverity {
    param($Vulnerability)

    $candidates = New-Object System.Collections.Generic.List[string]
    $scoreCandidates = New-Object System.Collections.Generic.List[double]

    $databaseSpecific = Get-JsonProperty $Vulnerability @("database_specific")
    if ($null -ne $databaseSpecific) {
        $severity = Get-JsonProperty $databaseSpecific @("severity")
        if (-not [string]::IsNullOrWhiteSpace([string]$severity)) {
            $candidates.Add([string]$severity)
        }
    }

    $ecosystemSpecific = Get-JsonProperty $Vulnerability @("ecosystem_specific")
    if ($null -ne $ecosystemSpecific) {
        $severity = Get-JsonProperty $ecosystemSpecific @("severity")
        if (-not [string]::IsNullOrWhiteSpace([string]$severity)) {
            $candidates.Add([string]$severity)
        }
    }

    # Any severity vector we cannot score (CVSS 4.0 uses a macro-vector lookup this
    # script does not implement) must not silently rank as UNKNOWN and slip under
    # the minimum-severity filter. Track it and force the finding to be surfaced.
    $unscored = $false
    foreach ($entry in Convert-ToArray (Get-JsonProperty $Vulnerability @("severity"))) {
        $scoreText = [string](Get-JsonProperty $entry @("score"))
        $score = Get-CvssScore $scoreText
        if ($null -ne $score) {
            $scoreCandidates.Add([double]$score)
        } elseif (-not [string]::IsNullOrWhiteSpace($scoreText)) {
            $unscored = $true
        }
    }

    $bestRank = 0
    $bestSeverity = "UNKNOWN"
    foreach ($candidate in $candidates) {
        $rank = Get-SeverityRank $candidate
        if ($rank -gt $bestRank) {
            $bestRank = $rank
            $bestSeverity = $candidate.ToUpperInvariant()
            if ($bestSeverity -eq "MODERATE") {
                $bestSeverity = "MEDIUM"
            }
        }
    }

    foreach ($score in $scoreCandidates) {
        $severity = Get-SeverityFromScore $score
        $rank = Get-SeverityRank $severity
        if ($rank -gt $bestRank) {
            $bestRank = $rank
            $bestSeverity = $severity
        }
    }

    if ($unscored -and $bestRank -eq 0) {
        $bestSeverity = "UNSCORED"
    }

    return [pscustomobject]@{
        Name = $bestSeverity
        Rank = $bestRank
        Unscored = $unscored
    }
}

function Read-JsonFile {
    param([Parameter(Mandatory = $true)][string]$Path)

    $raw = Get-Content -Raw -LiteralPath $Path
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return $null
    }
    return $raw | ConvertFrom-Json -ErrorAction Stop
}

$reportFile = Resolve-RepoPath $ReportPath
$allowlistFile = Resolve-RepoPath $AllowlistPath
$report = Read-JsonFile $reportFile
$allowlist = Read-JsonFile $allowlistFile

if ($null -eq $report) {
    throw "OSV report is empty: $reportFile"
}
if ($null -eq $allowlist) {
    throw "OSV allowlist is empty: $allowlistFile"
}

$invalidAllowlistEntries = New-Object System.Collections.Generic.List[string]
$allowlistEntries = New-Object System.Collections.Generic.List[object]
$today = [DateTime]::UtcNow.Date

foreach ($entry in Convert-ToArray (Get-JsonProperty $allowlist @("ignored", "IgnoredVulns", "ignoredVulnerabilities"))) {
    $id = ([string](Get-JsonProperty $entry @("id"))).Trim()
    $reason = ([string](Get-JsonProperty $entry @("reason"))).Trim()
    $expiresRaw = ([string](Get-JsonProperty $entry @("expires", "ignoreUntil"))).Trim()
    $package = ([string](Get-JsonProperty $entry @("package", "name"))).Trim()
    $ecosystem = ([string](Get-JsonProperty $entry @("ecosystem"))).Trim()

    if ([string]::IsNullOrWhiteSpace($id)) {
        $invalidAllowlistEntries.Add("allowlist entry is missing id")
        continue
    }
    if ([string]::IsNullOrWhiteSpace($reason)) {
        $invalidAllowlistEntries.Add("$id is missing a review reason")
        continue
    }
    if ([string]::IsNullOrWhiteSpace($expiresRaw)) {
        $invalidAllowlistEntries.Add("$id is missing expires/ignoreUntil")
        continue
    }

    $expires = [DateTime]::MinValue
    if (-not [DateTime]::TryParseExact(
            $expiresRaw,
            "yyyy-MM-dd",
            [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::AssumeUniversal,
            [ref]$expires
        )) {
        $invalidAllowlistEntries.Add("$id has invalid expires date '$expiresRaw'; expected yyyy-MM-dd")
        continue
    }
    if ($expires.Date -lt $today) {
        $invalidAllowlistEntries.Add("$id expired on $expiresRaw")
        continue
    }

    $allowlistEntries.Add([pscustomobject]@{
        Id = $id.ToUpperInvariant()
        Package = $package
        Ecosystem = $ecosystem
        Expires = $expires.Date
        Reason = $reason
    })
}

if ($invalidAllowlistEntries.Count -gt 0) {
    Write-Error ("OSV allowlist validation failed:`n - " + ($invalidAllowlistEntries -join "`n - "))
    exit 1
}

$minimumRank = Get-SeverityRank $MinimumSeverity
$totalVulnerabilities = 0
$policyFindings = New-Object System.Collections.Generic.List[string]
$acknowledgedFindings = New-Object System.Collections.Generic.List[string]

foreach ($result in Convert-ToArray (Get-JsonProperty $report @("results"))) {
    $source = Get-JsonProperty (Get-JsonProperty $result @("source")) @("path")
    foreach ($packageResult in Convert-ToArray (Get-JsonProperty $result @("packages"))) {
        $packageObject = Get-JsonProperty $packageResult @("package")
        $packageName = [string](Get-JsonProperty $packageObject @("name"))
        $packageVersion = [string](Get-JsonProperty $packageObject @("version", "commit"))
        $ecosystem = [string](Get-JsonProperty $packageObject @("ecosystem"))

        foreach ($vulnerability in Convert-ToArray (Get-JsonProperty $packageResult @("vulnerabilities"))) {
            $totalVulnerabilities += 1
            $ids = New-Object System.Collections.Generic.HashSet[string]
            $primaryId = [string](Get-JsonProperty $vulnerability @("id"))
            if (-not [string]::IsNullOrWhiteSpace($primaryId)) {
                [void]$ids.Add($primaryId.ToUpperInvariant())
            }
            foreach ($alias in Convert-ToArray (Get-JsonProperty $vulnerability @("aliases"))) {
                if (-not [string]::IsNullOrWhiteSpace([string]$alias)) {
                    [void]$ids.Add(([string]$alias).ToUpperInvariant())
                }
            }

            $severity = Get-VulnerabilitySeverity $vulnerability
            # Fail closed: a vulnerability we could not score is reported (and must be
            # allowlisted deliberately) rather than skipped as sub-threshold.
            if ($severity.Rank -lt $minimumRank -and -not $severity.Unscored) {
                continue
            }

            $allowed = $false
            foreach ($allowlistEntry in $allowlistEntries) {
                if (-not $ids.Contains($allowlistEntry.Id)) {
                    continue
                }
                if ($allowlistEntry.Package -and $allowlistEntry.Package -ne $packageName) {
                    continue
                }
                if ($allowlistEntry.Ecosystem -and $allowlistEntry.Ecosystem -ne $ecosystem) {
                    continue
                }
                $allowed = $true
                break
            }

            $idList = ($ids | Sort-Object) -join ","
            $finding = "$($severity.Name) $idList in $ecosystem/$packageName@$packageVersion from $source"
            if ($allowed) {
                $acknowledgedFindings.Add($finding)
            } else {
                $policyFindings.Add($finding)
            }
        }
    }
}

if ($policyFindings.Count -gt 0) {
    Write-Error ("OSV policy failed. Unacknowledged $MinimumSeverity-or-higher vulnerabilities:`n - " + ($policyFindings -join "`n - "))
    exit 1
}

Write-Host "OSV report accepted: $totalVulnerabilities total vulnerabilities, $($acknowledgedFindings.Count) acknowledged $MinimumSeverity-or-higher findings, 0 unacknowledged $MinimumSeverity-or-higher findings."
