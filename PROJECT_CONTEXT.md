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
- Database: Room schema version `15` in `HostShieldDatabase.kt`.
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
- `DohResolver` is fail-closed. It uses `DohPinManifest` for versioned provider SPKI pins, bounded DNS response reads, DoH3-first resolution through `Doh3Resolver`, and pinned OkHttp DoH fallback.
- `DnsStampParser` supports current DNS stamp property width, DNSCrypt provider public keys, DoQ, ODoH target/relay, and Anonymized DNSCrypt relay stamp types.
- `DnsCryptRoutePlanner` validates DNSCrypt resolver/relay role separation and builds relay prefixes, but full DNSCrypt query encryption/decryption is not exposed.
- DNSCrypt production work is gated by `docs/decisions/0001-dnscrypt-engine.md`. The accepted direction is an audited engine extraction behind a Kotlin facade, with full `dnscrypt-proxy`/`gomobile` only as a packaging and correctness spike; native Kotlin crypto is a fallback, not the first production path.
- `DoqResolver` and `WireGuardProxy` are explicitly experimental and simplified. Treat them as research-grade until replaced with audited engines.
- Settings, diagnostics, logs, and README now share explicit maturity labels for DoQ and WireGuard DNS. Production defaults remain pinned DoH/DoH3/DoT; DoQ and WireGuard DNS stay opt-in.
- `SecureStore` uses a local Android Keystore AES-GCM wrapper over `hostshield_secure_store_v2`. Tink is retained as a direct dependency only for one-time migration from legacy AndroidX EncryptedSharedPreferences keysets when the original Keystore master key is still present.
- Room migrations exist from v1 through v15. Public migration definitions now live in `Migrations.ALL`, including the older v1-v5 steps that used to be private inside `DatabaseModule`. Exported Room schema snapshots exist for versions 7, 8, 9, 12, 14, and 15; missing official exports are documented in `docs/database-migration-fixtures.md`.

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

- Public docs now track the v6.5.9 source truth for fail-closed DoH pinning, 405 tracker SDK signatures, ipapi.co GeoIP fallback, Kotlin 2.1, and Room v1-v15 migrations.
- `tools/check-release-docs.ps1` is the release-doc consistency gate for stale version/security phrases.
- `tools/release-provenance.ps1` generates release provenance under `artifacts/release-provenance/`, including APK SHA-256, signing cert fingerprint when `apksigner` is available, build commit, Gradle/AGP/Kotlin versions, and artifact path.
- License files conflict and require maintainer decision before publication: root `LICENSE` is MIT, while `app/LICENSE` is GPL-3.0. Docs surface this conflict instead of choosing a license implicitly.

## Diagnostics Notes

- `DiagnosticEventStore` is a local-only JSONL ring buffer at `filesDir/diagnostics/diagnostic-events.jsonl`, capped at 500 events.
- Current event types: `vpn_start`, `vpn_stop`, `tun_fd_invalid`, `private_dns_conflict`, `blocklist_swap`, `source_download_failed`, `cert_pin_failure`, `resolver_failover`, `doze_resume`, `root_command_failed`, and `backup_import_failed`.
- Event producers include VPN lifecycle/heartbeat/watchdog, DoH cert pin and failover paths, blocklist update workers, manual source apply, Private DNS detection, root shell/hosts failures, and backup restore failures.
- Settings diagnostic export now shares a ZIP package containing `hostshield-diagnostic.txt`, `diagnostic-events.jsonl`, and `manifest.json`; there is no automatic network upload.
- `DohResolver` now keeps an in-memory 24-hour provider health window. DNS Tools > Status shows per-resolver selected state, observed transport, latency, success rate, failovers, pin failures, and an EDE placeholder.
- DoH provider pins now live in `DohPinManifest` with manifest version, issued date, primary/backup labels, review dates, expiry dates, diagnostic summary lines, and pin-failure event fields. The OkHttp path remains fail-closed.
- Source download failures now persist `last_http_status`, `last_error`, and consecutive failure counts. Failed blocklist, allowlist, rule-sync, health-check, manual apply, VPN rebuild, and profile rebuild paths update source health; failed source updates post a local HostShield alert notification; Sources cards show last failure, HTTP status when available, and last successful update.
- `HostShieldMigrationTest` is an instrumented migration matrix. It creates frozen SQL fixtures for every supported start version 1 through 14, migrates to current schema version 15 with `Migrations.ALL`, validates against the Room schema export, and checks sentinel data survives.
- `ParserFuzzHarnessTest` is a deterministic JVM fuzz/property harness for malformed DNS bytes, generated DNS query roundtrips, DNS stamps, hosts/adblock imports, regex guard behavior, malformed backup payloads, and malformed source URLs.
- `BackupCryptoTest` covers AES-GCM roundtrip, wrong-passphrase authentication failure, short payload rejection, invalid header rejection, random salt/IV/ciphertext uniqueness, legacy plaintext detection, and encrypted-import prompt/failure behavior through `BackupRestoreUtil.decodeBackupBytes`.
- The curated blocklist gallery now includes 51 sources and a HaGeZi pack chooser for Light/Multi-Light, Normal, Pro, Pro++, Ultimate, TIF, TIF Mini, DynDNS, NRD, and Most Abused TLDs. Gallery items can carry tier and warning metadata, and `HostsParser.parseForBlocking` preserves adblock wildcard and `$denyallow=` semantics for source rebuild paths.
- Subscribed allowlists are first-class source-category inputs in the Home apply, VPN rebuild, profile schedule, and periodic hosts worker paths. `HostsParser.parseForAllowing` handles plain domains and adblock `@@||` exception files, stale HaGeZi allowlist URLs are repaired on seed, and Sources can run an allowlist impact analysis that shows neutralized counts and sample domains per enabled allowlist.
- Sources can preview enabled source updates before applying them. The preview downloads sources into a temporary `BlocklistHolder`, compares candidate exact/source-wildcard keys against the active in-memory filter, and lists recent DNS queries whose verdict would change.
- Primary Compose screens now have a shared accessibility semantics helper and a TalkBack pass over headings, icon-only actions, stateful switches/filters/tabs/radio choices, destructive controls, disabled actions, and loading/progress announcements.
- The first dynamic type pass replaced fixed onboarding button heights with minimum heights, let source/log dense rows wrap key text and metadata, made warning banner copy line-height tolerant, and loosened firewall column headers for larger font scales.
- `HostShieldTheme` now supports a persisted high-contrast AMOLED variant via `UiPreferences.highContrastAmoled`. The theme switches the shared Compose color tokens, Material color scheme, chart color sources, and widget colors to pure-black surfaces with brighter text and semantic warning/error/accent colors; `ThemeContrastTest` guards contrast ratios.

## Highest-Value Next Work

1. Continue security modernization: Argon2id migration path, AES-GCM backup nonce uniqueness guard, and controlled dependency refresh.
2. Reconcile the conflicting MIT/GPL license files before publishing any new public release.
3. Add top-flow Compose UI coverage for onboarding, VPN controls, source/rule flows, diagnostics export, and log filtering.

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
