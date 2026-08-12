[CmdletBinding()]
param(
    [switch]$SkipRemoteUrlLiveness
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Net.Http

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

function Get-JsonRemoteUrls {
    param(
        [Parameter(Mandatory = $true)][string]$Json,
        [Parameter(Mandatory = $true)][string]$Path
    )

    try {
        $convertFromJsonParams = @{ ErrorAction = "Stop" }
        if ((Get-Command ConvertFrom-Json).Parameters.ContainsKey("DateKind")) {
            $convertFromJsonParams.DateKind = "String"
        }
        $catalog = $Json | ConvertFrom-Json @convertFromJsonParams
    } catch {
        $failures.Add("$Path is not valid JSON: $($_.Exception.Message)")
        return @()
    }

    $urls = [System.Collections.Generic.List[string]]::new()
    foreach ($category in @($catalog)) {
        foreach ($entry in @($category.lists)) {
            $url = ([string]$entry.url).Trim()
            if ([string]::IsNullOrWhiteSpace($url)) {
                $failures.Add("$Path contains a list entry without a URL.")
            } elseif ($url -notmatch '^https://') {
                $failures.Add("$Path contains a non-HTTPS remote URL: $url")
            } else {
                $urls.Add($url)
            }
        }
    }
    return $urls.ToArray()
}

function Get-KotlinRemoteUrls {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Path
    )

    $urls = [System.Collections.Generic.List[string]]::new()
    $patterns = @(
        '(?m)^\s*(?:(?:private|internal|public|protected)\s+)*(?:const\s+)?val\s+\w*(?:URL|Url)\w*\s*=\s*"(?<url>https?://[^"]+)"',
        '(?m)^\s*url\s*=\s*"(?<url>https?://[^"]+)"'
    )
    foreach ($pattern in $patterns) {
        foreach ($match in [regex]::Matches($Source, $pattern)) {
            $url = $match.Groups["url"].Value.Trim()
            if (-not $url.EndsWith('/')) {
                $urls.Add($url)
            }
        }
    }
    if ($urls.Count -eq 0) {
        $failures.Add("$Path did not expose any active HTTPS source URL declarations.")
    }
    return $urls.ToArray()
}

$appBuild = Read-RepoFile "app/app/build.gradle.kts"
$rootBuild = Read-RepoFile "app/build.gradle.kts"
$versionCatalog = Read-RepoFile "app/gradle/libs.versions.toml"
$releaseProvenance = Read-RepoFile "tools/release-provenance.ps1"
$osvPolicyScript = Read-RepoFile "tools/check-osv-report.ps1"
$androidPageAlignmentScript = Read-RepoFile "tools/check-android-page-alignment.ps1"
$protectionMatrixScript = Read-RepoFile "tools/run-protection-resilience-matrix.ps1"
$appManifest = Read-RepoFile "app/app/src/main/AndroidManifest.xml"
$fgsTypeHelper = Read-RepoFile "app/app/src/main/java/com/hostshield/service/ProtectionForegroundServiceTypes.kt"
$workManagerAudit = Read-RepoFile "docs/WORKMANAGER_AUDIT.md"
$dohResolver = Read-RepoFile "app/app/src/main/java/com/hostshield/service/DohResolver.kt"
$dnsVpnService = Read-RepoFile "app/app/src/main/java/com/hostshield/service/DnsVpnService.kt"
$doh3Resolver = Read-RepoFile "app/app/src/main/java/com/hostshield/service/Doh3Resolver.kt"
$dnsSettingsSection = Read-RepoFile "app/app/src/main/java/com/hostshield/ui/screens/settings/DnsSettingsSection.kt"
$protectionSettingsSection = Read-RepoFile "app/app/src/main/java/com/hostshield/ui/screens/settings/ProtectionSettingsSection.kt"
$mainActivity = Read-RepoFile "app/app/src/main/java/com/hostshield/MainActivity.kt"
$adaptiveNavigationScaffold = Read-RepoFile "app/app/src/main/java/com/hostshield/ui/navigation/AdaptiveNavigationScaffold.kt"
$localeLayoutScaffoldTest = Read-RepoFile "app/app/src/androidTest/java/com/hostshield/ui/LocaleLayoutScaffoldTest.kt"
$resourcesProperties = Read-RepoFile "app/app/src/main/res/resources.properties"
$stringsXml = Read-RepoFile "app/app/src/main/res/values/strings.xml"
$localDnsServerService = Read-RepoFile "app/app/src/main/java/com/hostshield/service/LocalDnsServerService.kt"
$experimentalDisclosure = Read-RepoFile "app/app/src/main/java/com/hostshield/util/ExperimentalEngineDisclosure.kt"
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

# Derive the documented Room migration range from the tracked schema JSONs rather
# than a hand-written constant. A literal here previously required README to keep
# claiming a stale range long after the database moved on, so correcting the docs
# would have failed this gate.
$schemaRoot = Join-Path $repoRoot "app/app/schemas"
$schemaVersions = @()
if (Test-Path -LiteralPath $schemaRoot) {
    $schemaVersions = @(
        Get-ChildItem -LiteralPath $schemaRoot -Recurse -Filter "*.json" -ErrorAction SilentlyContinue |
            ForEach-Object { [int]([System.IO.Path]::GetFileNameWithoutExtension($_.Name)) } |
            Sort-Object
    )
}
if ($schemaVersions.Count -eq 0) {
    throw "Unable to determine the Room schema version from app/app/schemas."
}
$maxSchemaVersion = $schemaVersions[-1]
$migrationRange = "v1-v$maxSchemaVersion"

$docs = @{
    "README.md" = Read-RepoFile "README.md"
    "CHANGELOG.md" = Read-RepoFile "CHANGELOG.md"
    "app/metadata/en-US/full_description.txt" = Read-RepoFile "app/metadata/en-US/full_description.txt"
    "app/metadata/en-US/short_description.txt" = Read-RepoFile "app/metadata/en-US/short_description.txt"
}

$failures = New-Object System.Collections.Generic.List[string]

# Keep the historical app-directory paths discoverable without maintaining a
# second copy of either document. Only the root documents are release-gated.
$canonicalDocPointers = @{
    "app/README.md" = "../README.md"
    "app/CHANGELOG.md" = "../CHANGELOG.md"
}
foreach ($pointer in $canonicalDocPointers.GetEnumerator()) {
    if (-not (Test-RepoFile $pointer.Key)) {
        $failures.Add("Missing canonical document pointer: $($pointer.Key)")
    } elseif ((Read-RepoFile $pointer.Key) -notmatch [regex]::Escape($pointer.Value)) {
        $failures.Add("$($pointer.Key) must point to $($pointer.Value), not duplicate release content.")
    }
}

$remoteUrlSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
$curatedBlocklistsPath = "app/app/src/main/assets/curated_blocklists.json"
if (-not (Test-RepoFile $curatedBlocklistsPath)) {
    $failures.Add("Missing curated blocklist catalog: $curatedBlocklistsPath")
} else {
    foreach ($url in @(Get-JsonRemoteUrls (Read-RepoFile $curatedBlocklistsPath) $curatedBlocklistsPath)) {
        $remoteUrlSet.Add($url) | Out-Null
    }
}

$remoteSourceKotlinPaths = @(
    "app/app/src/main/java/com/hostshield/data/repository/SourceRepository.kt",
    "app/app/src/main/java/com/hostshield/service/ThreatIntelManager.kt",
    "app/app/src/main/java/com/hostshield/service/CnameCloakUpdater.kt",
    "app/app/src/main/java/com/hostshield/service/DohBypassUpdater.kt"
)
foreach ($sourcePath in $remoteSourceKotlinPaths) {
    if (-not (Test-RepoFile $sourcePath)) {
        $failures.Add("Missing Kotlin remote source declaration file: $sourcePath")
        continue
    }
    foreach ($url in @(Get-KotlinRemoteUrls (Read-RepoFile $sourcePath) $sourcePath)) {
        if ($url -notmatch '^https://') {
            $failures.Add("$sourcePath contains a non-HTTPS remote URL: $url")
        } else {
            $remoteUrlSet.Add($url) | Out-Null
        }
    }
}

if ($SkipRemoteUrlLiveness) {
    Write-Warning "Skipping GET-based remote URL liveness checks by request (-SkipRemoteUrlLiveness)."
} else {
    $httpClient = [System.Net.Http.HttpClient]::new()
    $httpClient.Timeout = [TimeSpan]::FromSeconds(30)
    $remoteUrlFailures = 0
    try {
        foreach ($url in @($remoteUrlSet | Sort-Object)) {
            $request = $null
            $response = $null
            try {
                $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Get, $url)
                $request.Headers.UserAgent.ParseAdd("HostShield-release-check/1.0")
                $response = $httpClient.SendAsync(
                    $request,
                    [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead
                ).GetAwaiter().GetResult()
                $statusCode = [int]$response.StatusCode
                if ($statusCode -ne 200) {
                    $remoteUrlFailures++
                    $failures.Add("Remote source URL returned HTTP ${statusCode}: $url")
                }
            } catch {
                $remoteUrlFailures++
                $failures.Add("Remote source URL GET failed: $url ($($_.Exception.Message))")
            } finally {
                if ($null -ne $response) {
                    $response.Dispose()
                }
                if ($null -ne $request) {
                    $request.Dispose()
                }
            }
        }
    } finally {
        $httpClient.Dispose()
    }
    Write-Host "Checked $($remoteUrlSet.Count) remote source URLs with HTTP GET; $remoteUrlFailures failed."
}

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

function ConvertTo-CanonicalIp {
    param([Parameter(Mandatory = $true)][string]$Value)

    $parsed = [System.Net.IPAddress]::Parse($Value.Trim())
    $bytes = $parsed.GetAddressBytes()
    if ($bytes.Length -eq 4) {
        return $parsed.ToString()
    }
    if ($bytes.Length -ne 16) {
        throw "Unsupported IP address family."
    }
    $groups = for ($index = 0; $index -lt 16; $index += 2) {
        '{0:x}' -f (($bytes[$index] -shl 8) -bor $bytes[$index + 1])
    }
    return $groups -join ":"
}

function Get-Sha256Hex {
    param([Parameter(Mandatory = $true)][string]$Text)

    $hash = [Security.Cryptography.SHA256]::Create().ComputeHash([Text.Encoding]::UTF8.GetBytes($Text))
    return [BitConverter]::ToString($hash).Replace("-", "").ToLowerInvariant()
}

function Get-DohBypassCanonicalPayload {
    param(
        [Parameter(Mandatory = $true)][int]$Version,
        [Parameter(Mandatory = $true)][string]$Updated,
        [Parameter(Mandatory = $true)][string]$CreatedAt,
        [Parameter(Mandatory = $true)][string[]]$Domains,
        [Parameter(Mandatory = $true)][string[]]$Wildcards,
        [int]$Schema = 1,
        [string[]]$DnsTrapIpv4 = @(),
        [string[]]$DnsTrapIpv6 = @(),
        [string[]]$DohBypassIpv4 = @(),
        [string[]]$DohBypassIpv6 = @()
    )

    [string[]]$domainArray = @($Domains)
    [string[]]$wildcardArray = @($Wildcards)
    [Array]::Sort($domainArray, [StringComparer]::Ordinal)
    [Array]::Sort($wildcardArray, [StringComparer]::Ordinal)

    $sortedDomains = $domainArray -join ","
    $sortedWildcards = $wildcardArray -join ","
    $payload = "schema=$Schema`nversion=$Version`nupdated=$Updated`ncreated_at=$CreatedAt`ndomains=$sortedDomains`nwildcards=$sortedWildcards"
    if ($Schema -eq 2) {
        $ipSets = @(
            @($DnsTrapIpv4),
            @($DnsTrapIpv6),
            @($DohBypassIpv4),
            @($DohBypassIpv6)
        )
        $ipSetNames = @("dns_trap_ipv4", "dns_trap_ipv6", "doh_bypass_ipv4", "doh_bypass_ipv6")
        for ($index = 0; $index -lt $ipSets.Count; $index++) {
            [string[]]$sortedIps = @($ipSets[$index])
            [Array]::Sort($sortedIps, [StringComparer]::Ordinal)
            $payload += "`n$($ipSetNames[$index])=$($sortedIps -join ',')"
        }
    }
    return $payload
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
        $convertFromJsonParams = @{
            ErrorAction = "Stop"
        }
        # PowerShell 7.6 converts ISO-8601 strings to DateTime by default,
        # which changes the signed canonical payload when cast back to text.
        # Preserve JSON strings when the runtime exposes the DateKind switch;
        # older supported PowerShell versions already leave them as strings.
        if ((Get-Command ConvertFrom-Json).Parameters.ContainsKey("DateKind")) {
            $convertFromJsonParams.DateKind = "String"
        }
        $manifest = $manifestRaw | ConvertFrom-Json @convertFromJsonParams
    } catch {
        $failures.Add("$dohBypassManifest is not valid JSON: $($_.Exception.Message)")
    }

    if ($null -ne $manifest) {
        [int]$manifestSchema = 0
        if (-not [int]::TryParse([string]$manifest.schema, [ref]$manifestSchema) -or $manifestSchema -notin @(1, 2)) {
            $failures.Add("$dohBypassManifest must contain integer schema 1 or 2.")
        }

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

        $createdAt = ([string]$manifest.created_at).Trim()
        if ($createdAt -notmatch '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$') {
            $failures.Add("$dohBypassManifest must contain created_at as UTC yyyy-MM-ddTHH:mm:ssZ.")
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
        $normalizedDomains = New-Object System.Collections.Generic.List[string]
        foreach ($entry in $domains) {
            $value = ([string]$entry).Trim().ToLowerInvariant()
            if (-not (Test-DohBypassDomain $value)) {
                $failures.Add("$dohBypassManifest contains invalid domain entry: $entry")
            } elseif (-not $seenDomains.Add($value)) {
                $failures.Add("$dohBypassManifest contains duplicate domain entry: $value")
            } else {
                $normalizedDomains.Add($value)
            }
        }

        $seenWildcards = New-Object System.Collections.Generic.HashSet[string]
        $normalizedWildcards = New-Object System.Collections.Generic.List[string]
        foreach ($entry in $wildcards) {
            $value = ([string]$entry).Trim().ToLowerInvariant()
            if (-not (Test-DohBypassDomain $value)) {
                $failures.Add("$dohBypassManifest contains invalid wildcard entry: $entry")
            } elseif (-not $seenWildcards.Add($value)) {
                $failures.Add("$dohBypassManifest contains duplicate wildcard entry: $value")
            } else {
                $normalizedWildcards.Add($value)
            }
        }

        $normalizedIpSets = @{}
        if ($manifestSchema -eq 2) {
            $ipSetDefinitions = @{
                "dns_trap_ipv4" = $false
                "dns_trap_ipv6" = $true
                "doh_bypass_ipv4" = $false
                "doh_bypass_ipv6" = $true
            }
            $ipCount = 0
            foreach ($ipSetName in $ipSetDefinitions.Keys) {
                $rawIpSet = if ($null -ne $manifest.$ipSetName) { @($manifest.$ipSetName) } else { @() }
                $seenIps = New-Object System.Collections.Generic.HashSet[string]
                $normalizedIps = New-Object System.Collections.Generic.List[string]
                if ($rawIpSet.Count -eq 0) {
                    $failures.Add("$dohBypassManifest schema 2 requires a non-empty $ipSetName array.")
                }
                foreach ($rawIp in $rawIpSet) {
                    try {
                        $candidate = ([string]$rawIp).Trim()
                        $canonicalIp = ConvertTo-CanonicalIp $candidate
                        $isIpv6 = $canonicalIp.Contains(":")
                        if ($isIpv6 -ne [bool]$ipSetDefinitions[$ipSetName]) {
                            throw "wrong address family"
                        }
                        if (-not $seenIps.Add($canonicalIp)) {
                            throw "duplicate address"
                        }
                        $normalizedIps.Add($canonicalIp)
                        $ipCount++
                    } catch {
                        $failures.Add("$dohBypassManifest contains invalid $ipSetName entry '$rawIp': $($_.Exception.Message)")
                    }
                }
                $normalizedIpSets[$ipSetName] = $normalizedIps.ToArray()
            }
            if ($ipCount -gt 128) {
                $failures.Add("$dohBypassManifest has $ipCount IPs; the verifier caps schema 2 at 128.")
            }
        }

        $canonicalPayload = $null
        if ($manifestSchema -in @(1, 2)) {
            $canonicalParams = @{
                Version = $manifestVersion
                Updated = ([string]$manifest.updated).Trim()
                CreatedAt = $createdAt
                Domains = $normalizedDomains.ToArray()
                Wildcards = $normalizedWildcards.ToArray()
                Schema = $manifestSchema
            }
            if ($manifestSchema -eq 2) {
                $canonicalParams.DnsTrapIpv4 = $normalizedIpSets["dns_trap_ipv4"]
                $canonicalParams.DnsTrapIpv6 = $normalizedIpSets["dns_trap_ipv6"]
                $canonicalParams.DohBypassIpv4 = $normalizedIpSets["doh_bypass_ipv4"]
                $canonicalParams.DohBypassIpv6 = $normalizedIpSets["doh_bypass_ipv6"]
            }
            $canonicalPayload = Get-DohBypassCanonicalPayload @canonicalParams
        }
        if ($null -ne $canonicalPayload) {
            $payloadHash = Get-Sha256Hex $canonicalPayload
            if (([string]$manifest.payload_sha256).Trim().ToLowerInvariant() -ne $payloadHash) {
                $failures.Add("$dohBypassManifest payload_sha256 does not match canonical payload.")
            }
        }

        $signature = $manifest.signature
        if ($null -eq $signature) {
            $failures.Add("$dohBypassManifest must contain a signature object.")
        } else {
            if ($null -eq $canonicalPayload) {
                $failures.Add("$dohBypassManifest signature cannot be verified without a supported canonical schema.")
            } else {
                if ([string]$signature.algorithm -ne "SHA256withRSA") {
                    $failures.Add("$dohBypassManifest signature algorithm must be SHA256withRSA.")
                }
                if ([string]$signature.key_id -ne "hostshield-release-rsa-v1") {
                    $failures.Add("$dohBypassManifest signature key_id must be hostshield-release-rsa-v1.")
                }

                $dohVerifierPath = "app/app/src/main/java/com/hostshield/service/DohBypassManifestVerifier.kt"
                if (-not (Test-RepoFile $dohVerifierPath)) {
                    $failures.Add("Missing DoH bypass manifest verifier: $dohVerifierPath")
                } else {
                    $dohVerifier = Read-RepoFile $dohVerifierPath
                    $certMatch = [regex]::Match(
                        $dohVerifier,
                        'PINNED_CERTIFICATE_BASE64\s*=\s*"([^"]+)"'
                    )
                    if (-not $certMatch.Success) {
                        $failures.Add("$dohVerifierPath must contain PINNED_CERTIFICATE_BASE64.")
                    } else {
                        try {
                            $certBytes = [Convert]::FromBase64String($certMatch.Groups[1].Value)
                            $cert = [Security.Cryptography.X509Certificates.X509Certificate2]::new($certBytes)
                            $rsa = [Security.Cryptography.X509Certificates.RSACertificateExtensions]::GetRSAPublicKey($cert)
                            $signatureBytes = [Convert]::FromBase64String([string]$signature.value)
                            $payloadBytes = [Text.Encoding]::UTF8.GetBytes($canonicalPayload)
                            $isValid = $rsa.VerifyData(
                                $payloadBytes,
                                $signatureBytes,
                                [Security.Cryptography.HashAlgorithmName]::SHA256,
                                [Security.Cryptography.RSASignaturePadding]::Pkcs1
                            )
                            if (-not $isValid) {
                                $failures.Add("$dohBypassManifest signature verification failed.")
                            }
                        } catch {
                            $failures.Add("$dohBypassManifest signature could not be verified: $($_.Exception.Message)")
                        }
                    }
                }
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

foreach ($doc in @("README.md")) {
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
        "Android SDK $compileSdk",
        "com.hostshield.ACTION_ENABLE",
        "com.hostshield.ACTION_SET_PROFILE",
        "duration_minutes",
        $migrationRange,
        "run-protection-resilience-matrix.ps1",
        "without GitHub Actions workflows"
    )
    "CHANGELOG.md" = @(
        "v$versionName"
    )
    "app/metadata/en-US/full_description.txt" = @(
        "fail-closed certificate pinning",
        "405 signatures",
        "ipapi.co",
        "Kotlin $kotlinMajorMinor",
        "Android SDK $compileSdk",
        "Android Gradle Plugin $agpMajorMinor",
        "Debug-only DoQ and WireGuard DNS controls",
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
            "ProtectionMatrixPath",
            "protection-resilience-matrix.json",
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
    "tools/run-protection-resilience-matrix.ps1" = @{
        Text = $protectionMatrixScript
        Patterns = @(
            "ProtectionResilienceMatrixTest",
            "vpn-start-stop",
            "always-on-lockdown-advisory",
            "boot-update-resume",
            "work-profile-private-space-warning",
            "battery-exemption-denial",
            "diagnostic-event-export",
            "foreground_service_start_failed",
            "foreground_service_timeout",
            "always_on_vpn_app",
            "always_on_vpn_lockdown",
            "cmd user list",
            "dumpsys deviceidle whitelist",
            "AttemptVpnStartStop",
            "protection-resilience-matrix.json"
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

# Assert the audit actually enumerates every worker in the tree. Grepping the doc
# for one forbidden word let it silently fall behind by a worker for ~9 releases.
$serviceDir = Join-Path $repoRoot "app/app/src/main/java/com/hostshield/service"
if (Test-Path -LiteralPath $serviceDir) {
    $workerFiles = @(
        Get-ChildItem -LiteralPath $serviceDir -Filter "*Worker.kt" -File -ErrorAction SilentlyContinue |
            Where-Object { (Get-Content -Raw -LiteralPath $_.FullName) -match 'CoroutineWorker|:\s*Worker\(' }
    )
    foreach ($workerFile in $workerFiles) {
        $workerName = [System.IO.Path]::GetFileNameWithoutExtension($workerFile.Name)
        if ($workManagerAudit -notmatch [regex]::Escape($workerName)) {
            $failures.Add("docs/WORKMANAGER_AUDIT.md does not document worker $workerName.")
        }
    }
}
foreach ($doc in @("README.md", "app/metadata/en-US/full_description.txt")) {
    if ($docs[$doc] -notmatch [regex]::Escape("targetSdk $targetSdk")) {
        $failures.Add("$doc is missing targetSdk $targetSdk platform claim.")
    }
}

if ($dohResolver -notmatch 'certificatePinner' -or $dnsVpnService -notmatch 'failClosedEncrypted') {
    $failures.Add("Encrypted-DNS fail-closed code claims are not backed by DohResolver pinning plus DnsVpnService.failClosedEncrypted.")
}
if ($docs["README.md"] -notmatch 'fail-closed') {
    $failures.Add("README docs must continue to state the encrypted-DNS fail-closed posture.")
}

if ($doh3Resolver -notmatch 'EMBEDDED_CRONET_ENABLED\s*=\s*false') {
    $failures.Add("Doh3Resolver must remain explicitly disabled while embedded Cronet is unavailable.")
}
if ($appBuild -match 'cronet' -or $versionCatalog -match 'cronet') {
    $failures.Add("Build files still reference Cronet despite the disabled embedded DoH3 posture.")
}
if ($dnsSettingsSection -notmatch 'if \(BuildConfig\.DEBUG\)' -or
    $dnsSettingsSection -notmatch 'R\.string\.dns_over_quic_experimental' -or
    $dnsSettingsSection -notmatch 'R\.string\.dns_wireguard_experimental') {
    $failures.Add("DnsSettingsSection.kt must keep DoQ and WireGuard DNS controls behind the BuildConfig.DEBUG gate.")
}
$doqForcedOffGate = 'useDoQ = if (com.hostshield.BuildConfig.DEBUG) prefs.doqEnabled.first() else false'
$wireGuardForcedOffGate = 'useWireGuard = if (com.hostshield.BuildConfig.DEBUG) prefs.wireGuardEnabled.first() else false'
if ($dnsVpnService -notmatch [regex]::Escape($doqForcedOffGate)) {
    $failures.Add("DnsVpnService.kt must force DoQ off in release builds.")
}
if ($dnsVpnService -notmatch [regex]::Escape($wireGuardForcedOffGate)) {
    $failures.Add("DnsVpnService.kt must force WireGuard DNS off in release builds.")
}
if ($experimentalDisclosure -notmatch 'Release builds force DoQ, DoH3, and WireGuard DNS off') {
    $failures.Add("ExperimentalEngineDisclosure.kt must state the release forced-off policy.")
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
    "com.hostshield.action.ENABLE",
    "com.hostshield.action.DISABLE",
    "com.hostshield.action.STATUS",
    "com.hostshield.action.REFRESH_BLOCKLIST",
    "--ei pause_minutes"
)

foreach ($doc in @("README.md", "app/metadata/en-US/full_description.txt", "app/metadata/en-US/short_description.txt", $metadataChangelog)) {
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

$lanDnsGateImplemented = (
    $appManifest -match 'android\.permission\.ACCESS_LOCAL_NETWORK' -and
    $appManifest -match '\.service\.LocalDnsServerService' -and
    $localDnsServerService -match 'ACTION_START' -and
    $localDnsServerService -match 'startForeground' -and
    $protectionSettingsSection -match 'lan_dns_enable' -and
    $protectionSettingsSection -match 'lan_dns_allow_external'
)

foreach ($doc in @("README.md", "app/metadata/en-US/full_description.txt", "app/metadata/en-US/short_description.txt")) {
    foreach ($pattern in $currentLocalDnsClaimPatterns) {
        if (($docs[$doc] -match [regex]::Escape($pattern)) -and -not $lanDnsGateImplemented) {
            $failures.Add("$doc contains an unwired Local DNS Server release-doc claim: $pattern")
        }
    }
}

$lanDnsReleaseClaimPatterns = @(
    "LAN DNS Server",
    "LAN DNS server",
    "UDP DNS serving",
    "local-network permission"
)

foreach ($doc in @("README.md", "app/metadata/en-US/full_description.txt", "app/metadata/en-US/short_description.txt")) {
    foreach ($pattern in $lanDnsReleaseClaimPatterns) {
        if (($docs[$doc] -match [regex]::Escape($pattern)) -and -not $lanDnsGateImplemented) {
            $failures.Add("$doc claims LAN DNS support, but the manifest/service/Settings gate is incomplete: $pattern")
        }
    }
}

$adaptiveLargeScreenGateImplemented = (
    $versionCatalog -match 'material3-adaptive-navigation-suite' -and
    $appBuild -match 'androidx\.compose\.material3\.adaptive\.navigation\.suite' -and
    $mainActivity -match 'HostShieldAdaptiveNavigationScaffold' -and
    $adaptiveNavigationScaffold -match 'NavigationSuiteScaffold' -and
    $adaptiveNavigationScaffold -match 'NavigationSuiteType\.NavigationRail' -and
    $adaptiveNavigationScaffold -match '600\.dp' -and
    $adaptiveNavigationScaffold -match '480\.dp' -and
    $localeLayoutScaffoldTest -match '841\.dp to 701\.dp' -and
    $localeLayoutScaffoldTest -match '1024\.dp to 640\.dp' -and
    $localeLayoutScaffoldTest -match '1280\.dp to 800\.dp' -and
    $localeLayoutScaffoldTest -match '1600\.dp to 900\.dp' -and
    $localeLayoutScaffoldTest -match 'fontScale = 1\.3f'
)

$adaptiveLargeScreenClaimPatterns = @(
    "Adaptive Large Screens",
    "adaptive navigation",
    "navigation rail",
    "Android 16 large-screen"
)

foreach ($doc in @("README.md", "app/metadata/en-US/full_description.txt", "app/metadata/en-US/short_description.txt")) {
    foreach ($pattern in $adaptiveLargeScreenClaimPatterns) {
        if (($docs[$doc] -match [regex]::Escape($pattern)) -and -not $adaptiveLargeScreenGateImplemented) {
            $failures.Add("$doc claims adaptive large-screen navigation, but the scaffold/dependency/test gate is incomplete: $pattern")
        }
    }
}

$localeConfigReadyImplemented = (
    $appBuild -match 'generateLocaleConfig\s*=\s*true' -and
    $resourcesProperties -match '(?m)^unqualifiedResLocale=en-US\s*$' -and
    $stringsXml -match 'name="home_search_placeholder"' -and
    $stringsXml -match 'name="qr_export_subtitle"' -and
    $stringsXml -match 'name="dns_over_https"' -and
    $docs["README.md"] -match 'non-English per-app languages stay deferred until full translations are available'
)

$localeConfigClaimPatterns = @(
    "LocaleConfig Ready",
    "generateLocaleConfig",
    "per-app languages"
)

foreach ($doc in @("README.md", "app/metadata/en-US/full_description.txt", "app/metadata/en-US/short_description.txt")) {
    foreach ($pattern in $localeConfigClaimPatterns) {
        if (($docs[$doc] -match [regex]::Escape($pattern)) -and -not $localeConfigReadyImplemented) {
            $failures.Add("$doc claims LocaleConfig readiness, but resources.properties/string-resource/doc gate is incomplete: $pattern")
        }
    }
}

$releaseEffectiveExperimentalDnsClaims = @(
    "Falls back to DoT; production defaults remain pinned DoH/DoT",
    "Production defaults remain pinned DoH/DoT",
    "stay out of production defaults"
)

foreach ($doc in @("README.md", "app/metadata/en-US/full_description.txt", "app/metadata/en-US/short_description.txt")) {
    foreach ($pattern in $releaseEffectiveExperimentalDnsClaims) {
        if ($docs[$doc] -match [regex]::Escape($pattern)) {
            $failures.Add("$doc implies release-effective experimental DNS transports: $pattern")
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
