# HostShield Project Context

Last refreshed: 2026-05-17
Repo state used: `main` after roadmap research refresh; source baseline remains tag `v6.5.9`

## Identity

HostShield is a local-first Android DNS firewall, ad/tracker blocker, and privacy diagnostics app. The product boundary is Android device protection first: no hosted resolver account, no remote telemetry, and no third-party crash/reporting SDK. It should keep a no-root VPN path as the default and use root features only as power-user accelerators.

Primary evidence:

- `README.md` - public product description and feature surface.
- `CLAUDE.md` - current working notes through v6.5.9.
- `app/app/build.gradle.kts` - Android app config, dependencies, flavors, signing.
- `app/app/src/main/AndroidManifest.xml` - permissions, services, receivers, provider exposure.
- `ROADMAP.md` - current source-backed planning.
- `.ai/research/2026-05-17/` - research run evidence and matrices.

## Current Baseline

- Version: `6.5.9`, versionCode `67` in `app/app/build.gradle.kts`.
- Database: Room schema version `14` in `HostShieldDatabase.kt`.
- Branch: `main`, clean before this research pass.
- Tags observed: latest `v6.5.9`; earlier release tags include `v6.5.1`, `v6.5.0`, `v6.4.0`, `v6.3.0`, `v6.2.0`.
- Source size: 169 Kotlin files, 151 main Kotlin files, 18 JVM test files, about 40.8K main Kotlin lines and 2.4K test lines.
- App flavor model: `full` flavor keeps `QUERY_ALL_PACKAGES`; `play` flavor uses an applicationId suffix and is intended for Play Store constraints.

## Architecture

Core packages:

- `service/` - VPN packet loop, DNS resolvers, root DNS/firewall, workers, proxy mode, caching, connection tracking.
- `domain/` - blocklist holder and parsers.
- `data/` - Room entities, DAOs, repositories, DataStore facade/managers, secure store.
- `ui/` - Compose screens, navigation, widgets, theme, reusable components.
- `util/` - import/export, backup crypto, tracker scanning, GeoIP, DNS stamps, diagnostics, root utilities.

Important implementation facts:

- `DnsVpnService` is the central VPN service. It routes DNS through a TUN interface, traps common public DNS endpoints, blocks known DoH bypass endpoints, supports IPv4/IPv6 packet classification, and dispatches encrypted upstream DNS in priority order.
- `BlocklistHolder` uses an exact-match hash set, reversed-label trie, wildcard allow/block handling, capped regex rules, and an LRU filter-decision cache. Production rebuilds use async snapshot replacement.
- `DohResolver` is fail-closed. It uses certificate pinning, bounded DNS response reads, DoH3-first resolution through `Doh3Resolver`, and pinned OkHttp DoH fallback.
- `DnsStampParser` supports current DNS stamp property width, DNSCrypt provider public keys, DoQ, ODoH target/relay, and Anonymized DNSCrypt relay stamp types.
- `DnsCryptRoutePlanner` validates DNSCrypt resolver/relay role separation and builds relay prefixes, but full DNSCrypt query encryption/decryption is not exposed.
- `DoqResolver` and `WireGuardProxy` are explicitly experimental and simplified. Treat them as research-grade until replaced with audited engines.
- `SecureStore` still uses AndroidX `EncryptedSharedPreferences` / `MasterKeys`; dependency review flags this as a migration candidate because AndroidX Security Crypto remains alpha and the API is aging.
- Room migrations exist from v1 through v14, but exported schema snapshots only exist for versions 7, 8, 9, 12, and 14. Migration-path tests need golden databases for every supported transition.

## Product Principles

- Local-first and no telemetry.
- Fail closed for encrypted DNS when pinning or provider policy fails.
- No-root by default; root mode should improve coverage, not be required for the primary experience.
- Prefer auditable open-source dependencies and standards-track protocols.
- Keep the AMOLED dark Material 3 identity, with accessibility improvements before visual novelty.
- Do not add cloud account features unless they are self-hosted or LAN/local-first with end-to-end encryption and clear export paths.

## Instruction And Memory Reconciliation

- `AGENTS.md` is ignored by Git and points to `CLAUDE.md`.
- `CLAUDE.md` is ignored by Git and current through `v6.5.9`.
- Shared Claude memory file `hostshield.md` is stale at `v6.3.0`; use it only as historical context.
- Codex memory only covers the `v6.5.1` signed release and adb reinstall path; it is useful for release work, not current architecture.
- Root `.gitignore` ignores `CLAUDE.md`, `*.keystore`, `*.apk`, app build outputs, and `/com/`.

## Release Truth Notes

- Public docs now track the v6.5.9 source truth for fail-closed DoH pinning, 405 tracker SDK signatures, ipapi.co GeoIP fallback, Kotlin 2.1, and Room v1-v14 migrations.
- `tools/check-release-docs.ps1` is the release-doc consistency gate for stale version/security phrases.
- `tools/release-provenance.ps1` generates release provenance under `artifacts/release-provenance/`, including APK SHA-256, signing cert fingerprint when `apksigner` is available, build commit, Gradle/AGP/Kotlin versions, and artifact path.
- License files conflict and require maintainer decision before publication: root `LICENSE` is MIT, while `app/LICENSE` is GPL-3.0. Docs surface this conflict instead of choosing a license implicitly.

## Diagnostics Notes

- `DiagnosticEventStore` is a local-only JSONL ring buffer at `filesDir/diagnostics/diagnostic-events.jsonl`, capped at 500 events.
- Current event types: `vpn_start`, `vpn_stop`, `tun_fd_invalid`, `private_dns_conflict`, `blocklist_swap`, `source_download_failed`, `cert_pin_failure`, `resolver_failover`, `doze_resume`, `root_command_failed`, and `backup_import_failed`.
- Event producers include VPN lifecycle/heartbeat/watchdog, DoH cert pin and failover paths, blocklist update workers, manual source apply, Private DNS detection, root shell/hosts failures, and backup restore failures.
- Settings diagnostic export now shares a ZIP package containing `hostshield-diagnostic.txt`, `diagnostic-events.jsonl`, and `manifest.json`; there is no automatic network upload.
- `DohResolver` now keeps an in-memory 24-hour provider health window. DNS Tools > Status shows per-resolver selected state, observed transport, latency, success rate, failovers, pin failures, and an EDE placeholder.

## Highest-Value Next Work

1. Surface failed-source feedback in notifications and Sources badges with last error/status context.
2. Complete DNSCrypt safely by choosing an audited Android-compatible crypto/engine path before exposing any user-facing toggle.
3. Add migration and parser fuzz coverage around Room, DNS packet parsing, stamps, blocklist parsing, and encrypted backup import.
4. Modernize dependency posture: AndroidX Security replacement, OkHttp 5 evaluation, Compose/AGP/Kotlin refresh, and release metadata/reproducibility.
5. Reconcile the conflicting MIT/GPL license files before publishing any new public release.

## Verification Commands

Use Windows PowerShell from the repo root:

```powershell
cd C:\Users\--\repos\HostShield\app
.\gradlew.bat :app:testFullDebugUnitTest
.\gradlew.bat :app:compileFullDebugKotlin
.\gradlew.bat :app:assembleFullRelease

cd C:\Users\--\repos\HostShield
powershell -ExecutionPolicy Bypass -File .\tools\check-release-docs.ps1
powershell -ExecutionPolicy Bypass -File .\tools\release-provenance.ps1
```

Release signing path from prior verified memory:

- `KEYSTORE_FILE=C:\Users\--\repos\HostShield\hostshield-release.keystore`
- `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` must be set.
- Release artifact path: `app/app/build/outputs/apk/full/release/app-full-release.apk`
