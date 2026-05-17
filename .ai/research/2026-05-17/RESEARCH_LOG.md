# Research Log

Research date: 2026-05-17

## Local Reconnaissance

Commands and checks:

- Listed repo root, `.github`, docs, Android app module, tests, and blocklists.
- Read `AGENTS.md`, repo `CLAUDE.md`, global Claude instructions, and Android stack memory.
- Ran `rtk git log -10` fallback through available shell path and confirmed recent commits.
- Inspected `app/app/build.gradle.kts`, root `app/build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `AndroidManifest.xml`, and release workflow.
- Counted Kotlin files and package distribution.
- Counted static blocklist entries and curated blocklist categories.
- Inspected central source files: `DnsVpnService`, `BlocklistHolder`, `DohResolver`, `DnsStampParser`, `DnsCryptRoutePlanner`, `DoqResolver`, `WireGuardProxy`, `SecureStore`, `BackupCrypto`, `TrackerSignatureDb`, `GeoIpLookup`, database and migration files.
- Searched for `TODO`, `FIXME`, `HACK`, and `XXX`; no matches found.
- Searched for version drift and user-facing hardcoded strings.

## External Research Passes

### Pass 1 - Direct Competitors

Searched and inspected:

- RethinkDNS / Rethink app
- AdGuard for Android
- NetGuard
- AdAway
- DNS66
- personalDNSfilter
- InviZible Pro
- Nebulo
- Intra
- Pi-hole
- AdGuard Home
- NextDNS and OISD/Hagezi list ecosystem

Live GitHub API metadata was collected for stars, forks, open issues, default branches, pushed dates, updated dates, licenses, and release tags where available.

### Pass 2 - Protocol And Standards

Standards covered:

- DoH: RFC 8484.
- DoT: RFC 7858.
- DoQ: RFC 9250.
- DoH3: RFC 9484.
- ODoH: RFC 9230.
- Extended DNS Errors: RFC 8914.
- DNS over TCP fallback: RFC 7766.
- Serve-stale: RFC 8767.
- DNS failure caching: RFC 9520.
- SVCB/HTTPS: RFC 9460.
- DNSCrypt stamps and Anonymized DNSCrypt through DNSCrypt proxy docs and vendored stamp parser.

### Pass 3 - Android Platform And Build

Reviewed:

- `VpnService` and `VpnService.Builder`.
- Foreground service type requirements.
- Doze/App Standby.
- Package visibility and `QUERY_ALL_PACKAGES` policy implications.
- App Bundle and build variant docs.
- Compose BOM, Compose accessibility, Compose testing, Material 3 Expressive.
- Room migration docs.
- AndroidX Security Crypto reference.
- AGP, Kotlin, Gradle, Hilt, WorkManager, OkHttp, libsu, Cronet, Vico, Lottie, ZXing, MaxMind.

### Pass 4 - Security, Datasets, Distribution

Reviewed:

- OWASP Password Storage Cheat Sheet.
- NIST SP 800-38D for AES-GCM nonce uniqueness.
- GitHub repositories and changelogs for DNSCrypt proxy, OkHttp, AdGuard Home, libsu.
- Hagezi DNS blocklists, OISD, StevenBlack, FilterLists, AdGuard Hostlists Registry.
- Exodus Privacy, DuckDuckGo Tracker Radar, URLhaus, Spamhaus DROP, Emerging Threats.
- Reproducible Builds, IzzyOnDroid, Obtainium.

## Failed Or Thin Searches

- Initial raw DNSCrypt path `dnscrypt-proxy/dnscrypt-proxy/stamps.go` returned 404. The current source is vendored at `vendor/github.com/jedisct1/go-dnsstamps/dnsstamps.go`.
- No repo TODO/FIXME/HACK/XXX markers were present, so backlog extraction relied on docs, source/code comments, dependency state, and external deltas.
- GitHub issues can be noisy as source evidence. Issue references are used as signal only where they map to local architecture or competitor feature pressure.
- Android 16 always-on VPN bug evidence remains partly community/platform-report based. The roadmap keeps this as already mitigated detection/advisory work rather than promising OS-level repair.

## Saturation Test

Research reached saturation when independent source classes converged on the same priorities:

- Competitors: RethinkDNS, AdGuard, NetGuard, AdAway, personalDNSfilter, Pi-hole, AdGuard Home, DNSCrypt proxy.
- Standards: ODoH, DNSCrypt, EDE, SVCB/HTTPS, DoQ/DoH3 all point to protocol correctness before UI toggles.
- Local source: `DoqResolver` and `WireGuardProxy` self-identify as experimental; `DnsCryptRoutePlanner` is only route groundwork; tests are mostly JVM utility tests.
- Docs: README/roadmap drift is concrete and repeatable.
- Dependencies: AndroidX Security Crypto alpha, OkHttp 4.x, AGP/Kotlin/Compose cadence, and release-distribution metadata all create maintenance backlog.

Additional searches would likely add examples, not change the top roadmap tiers.

## Implementation Follow-Up - DNSCrypt Decision Record

- Re-checked DNSCrypt project sources while completing the roadmap item:
  `dnscrypt-proxy`, DNS stamp references, DNSCrypt protocol draft,
  Anonymized DNSCrypt notes, Go Mobile `gomobile bind`, and libsodium
  ChaCha20/XChaCha20 docs.
- The GitHub wiki URLs listed as E040/E041 currently render as "Create new
  page" in GitHub's HTML view; the DNSCrypt website, protocol draft, and
  source repository were sufficient to support the decision record.
