# HostShield Roadmap

Last refreshed: 2026-05-17
Baseline: v6.5.9, versionCode 67, commit `9494e0b`

## Principles

- Local-first Android DNS firewall and blocker.
- No hosted account model and no remote telemetry.
- No-root VPN path remains first-class; root mode is a power-user accelerator.
- Fail closed for encrypted DNS and security-sensitive downgrade paths.
- Prefer auditable open-source dependencies, standards-track protocols, and local exportable diagnostics.
- Experimental protocol code must stay behind clear maturity gates until audited or replaced.

Research artifacts for this roadmap live in `.ai/research/2026-05-17/`.

Release governance note: licensing files need maintainer reconciliation before
publication. The root `LICENSE` is MIT, while `app/LICENSE` is GPL-3.0; public
docs now surface the conflict instead of asserting one license as canonical.

## Now - v6.6

### Documentation And Release Truth

- [x] **Repair public docs drift.** Update README and related docs so security claims match current source truth: DoH is fail-closed with no unpinned fallback, tracker SDK scanner has 405 signatures, online GeoIP fallback uses ipapi.co over HTTPS, and v6.5.9 is the active baseline. Add a small release-doc consistency check that greps version strings and known security phrases before release. Evidence: `README.md`, `DohResolver.kt`, `TrackerSignatureDb.kt`, `GeoIpLookup.kt`, `CHANGELOG.md`. Sources: L003, L004, L015, L027, L028.
- [x] **Normalize stale source-header comments without changing behavior.** Several file headers still say v1.6.0/v2.x/v3.x and the root `app/build.gradle.kts` comment says v1.6.0. Replace versioned header comments with stable component descriptions so future agents do not treat comments as version truth. Sources: L008, L009, L013, L018, L019.
- [x] **Add release provenance metadata.** Generate APK SHA-256, signing cert fingerprint, build commit, Gradle/AGP/Kotlin versions, and artifact path into release notes and an optional `checksums.txt`. Sources: L011, E073, E074.

### Diagnostics And Reliability

- [x] **Structured local event log.** Add a ring-buffered local event store for `vpn_start`, `vpn_stop`, `tun_fd_invalid`, `private_dns_conflict`, `blocklist_swap`, `source_download_failed`, `cert_pin_failure`, `resolver_failover`, `doze_resume`, `root_command_failed`, and `backup_import_failed`. Export as a diagnostics ZIP from Settings. No network upload. Sources: L013, L015, L018, L019, E003, E004.
- [x] **Per-resolver health card.** Use existing DoH/DoH3 latency/failure signals to show 24-hour latency, success rate, fallback count, pin failures, EDE count once implemented, and selected provider/transport. Sources: L015, L016, E020, E021.
- [x] **Failed-source feedback.** Surface failed blocklist/allowlist downloads in the notification channel and as a Sources screen badge with last failure, HTTP status, and last successful update. Sources: L029, L014, E015, E026.

### Protocol Safety

- [x] **DNSCrypt engine decision record.** Before a user-facing DNSCrypt toggle, write a short design record comparing: native Android/Kotlin implementation, Go `dnscrypt-proxy` component via gomobile, and audited library extraction. The decision must cover provider public key validation, client public key generation, XSalsa20/Poly1305 or supported primitive equivalence, Anonymized DNSCrypt relay wrapping, replay/nonces, timeout/failover, and test corpus. Sources: L020, L021, E037, E038, E039, E040, E041.
- [x] **Gate experimental DoQ and WireGuard modes.** Add explicit UI/state labels and diagnostics that say current DoQ and WireGuard implementations are experimental simplified engines. Keep production defaults on pinned DoH/DoH3/DoT until audited engines land. Sources: L018, L019, E044, E045.
- [x] **DoH pin rotation manifest.** Move provider pin metadata into a versioned local manifest with primary/backup pins, expiry/review date, and diagnostics for pin failures. Keep fail-closed behavior. Sources: L015, E042, E054.

### Test Hardening

- [x] **Room migration golden test matrix.** Create frozen DB fixtures for every supported version path and test v1->v14 upgrades without destructive fallback. Fill missing schema exports or document why older versions cannot be reconstructed. Sources: L024, L025, L026, E010.
- [x] **DNS and import parser fuzz harness.** Add fuzz/property tests for DNS packet parsing/building, DNS stamps, hosts/adblock syntax, regex guards, backup import, and malformed source updates. Sources: L014, L020, L023, L031, E042-E051.
- [x] **Backup crypto regression tests.** Cover wrong-passphrase behavior, short payload rejection, random IV uniqueness sampling, legacy plaintext auto-detection, and encrypted import failure messaging. Sources: L023, E052, E053.

### Blocklist And Dataset Quality

- [x] **Hagezi pack chooser.** Add curated Hagezi Light, Multi-Light, Normal, Pro, Pro++, Ultimate, TIF, TIF-Mini, DynDNS, NRD, and Most-Abused-TLDs packs with clear breakage warnings and expected size. Sources: L029, E026.
- [x] **Subscribed allowlist packs.** Add first-class subscribed allowlists that override block entries according to current allowlist precedence. Include Hagezi/Anudeep-style unbreak lists and show which blocked domains they neutralize. Sources: L014, L029, E024, E025, E026.
- [x] **Source impact preview.** Before applying source updates, show added/removed domain counts and recently queried domains that would change verdict. Sources: L014, L029, E020, E026.

### Accessibility

- [x] **TalkBack semantics pass.** Audit all primary screens and icon-only controls for useful `contentDescription`, `stateDescription`, headings, disabled states, destructive action labels, and progress announcements. Sources: L003, L031, E011.
- [x] **Dynamic type pass.** Replace fixed-size text in dense surfaces where it can clip under font scaling. Prioritize onboarding, settings, source cards, log rows, and warning banners. Sources: L031, E011.
- [x] **High-contrast dark theme.** Add a high-contrast AMOLED variant and verify it across dashboard, lists, dialogs, charts, widgets, and warning/error states. Sources: L031, E011, E014.

## Next - v6.7 / v7.0 Design

### Security And Dependency Modernization

- [x] **Replace AndroidX Security Crypto alpha dependency.** Design and implement migration from `EncryptedSharedPreferences` / `MasterKeys` to a maintained local secret-store path using Android Keystore and an auditable encryption wrapper. Must migrate existing secrets without data loss. Sources: L022, E009.
- [ ] **Argon2id migration path.** Add Argon2id for new PIN/backup KDF material if Android-compatible dependency choice is acceptable; keep backward verification for PBKDF2 records. Sources: L022, L023, E052.
- [ ] **AES-GCM nonce uniqueness guard.** Add backup-level nonce/key tracking or export-time assertion so repeated IV under a key is impossible within the app's generated backup stream. Sources: L023, E053.
- [ ] **Dependency refresh wave.** Evaluate AGP, Kotlin, Compose BOM, Room, WorkManager, Hilt, OkHttp 5, Cronet, Vico, Lottie, Glance, ZXing, and MaxMind upgrades in one controlled branch with unit tests and a debug install smoke. Sources: L008, E008-E014, E054-E066.

### UI Test Coverage

- [ ] **Top-flow Compose UI tests.** Cover first-launch onboarding, VPN start/stop affordance, source add/remove, rule add/remove, PIN set/verify/lockout, backup export/import, Private DNS conflict warning, query-log filtering, scheduled profile, and diagnostics export. Sources: L031, E012.
- [ ] **Pseudolocale and RTL scaffolding.** Move user-facing strings into resources and add pseudolocale layout checks. Translation comes later; the first goal is layout safety. Sources: L003, `strings.xml`, E011.

### Distribution

- [ ] **Obtainium install docs.** Add a tested Obtainium configuration for GitHub release APK installs. Sources: E075, L011.
- [ ] **Play flavor AAB lane.** Add CI path for signed Play AAB builds without `QUERY_ALL_PACKAGES`, separate from full APK release. Sources: L008, L011, E005, E006.
- [ ] **IzzyOnDroid/reproducible build readiness.** Generate metadata, license/dependency inventory, reproducibility notes, and release checksums. Sources: E073, E074.

### Privacy Analytics

- [ ] **Exodus signature refresh pipeline.** Mirror and version tracker signatures from Exodus-compatible sources into a local cache with a frozen mini-corpus test. Sources: L027, E067, E068.
- [ ] **DuckDuckGo Tracker Radar ingestion.** Add optional local tracker-domain dataset refresh for network-based tracker classification. Sources: E069, L027.
- [ ] **Threat intel freshness dashboard.** Show feed age, last update result, entry count, SHA-256, and impact since last refresh for URLhaus, Spamhaus, Emerging Threats, and malware feeds. Sources: E070, E071, E072.

## Later - v7.x

### Architecture

- [ ] **Firestack/tun2socks feasibility spike.** Prototype whether a Go/gomobile TUN layer improves throughput, UID attribution, userspace firewall rules, WireGuard routing, and split DNS enough to justify migration complexity. Sources: E017, E018, E015.
- [ ] **No-root userspace per-app firewall.** If the TUN spike succeeds, implement UID-keyed block/allow rules in VPN mode using Android-supported attribution where available and graceful fallback where not. Sources: E001, E030.
- [ ] **Connection-state-aware rules.** Add block-on-screen-off, metered/unmetered, background-only, until-unlock, and network profile rules across root and VPN paths. Sources: E015, E030.

### Advanced DNS

- [ ] **ODoH resolver path.** Build target/proxy support only after diagnostics and resolver health are in place. Sources: L020, E046.
- [ ] **Extended DNS Errors UI.** Parse and display EDE reason codes in query details and resolver health. Sources: E047.
- [ ] **SVCB/HTTPS/ECH awareness.** Expand existing SVCB parsing into policy and diagnostics for ECH-enabled domains. Sources: E051.
- [ ] **DNSSEC validation toggle.** Keep opt-in and visibly explain operational failure modes. Sources: E047, E051.
- [ ] **Smart/Split DNS routing.** Domain-keyed resolver routing after resolver health and diagnostics mature. Sources: E016.

### Local Ecosystem

- [ ] **Self-hosted encrypted sync server.** LAN/self-hosted sync for rules, allowlists, sources, and profiles with end-to-end encryption and manual export/import fallback. No hosted account dependency. Sources: E020, E037.
- [ ] **Desktop companion resolver CLI.** Small Windows/macOS/Linux companion for shared resolver/blocklist testing before any GUI. Sources: E037.
- [ ] **Read-only LAN household dashboard.** Optional local dashboard for parental/review mode, bound to localhost by default and LAN only by explicit user action. Sources: E020, E024.
- [ ] **Tailscale and GrapheneOS compatibility guides.** Document VPN coexistence and hardened Android behavior with explicit caveats. Sources: E076, E077, E078.

## Watchlist

- DELEG record standardization.
- Android 16+ VPN and foreground service policy changes.
- OkHttp 5 HTTP/3 maturity and Android behavior.
- Cronet embedded update cadence and binary size/security tradeoffs.
- DNSCrypt proxy stamp/protocol changes.
- Hagezi/OISD format and licensing changes.
- Play policy around `QUERY_ALL_PACKAGES`, VPN services, and content filtering.

## Not Planned

- Hosted cloud resolver account model. It conflicts with HostShield's local-first/no-telemetry identity.
- Browser extension form factor. Browser blockers already own that layer; HostShield's value is Android OS-level coverage.
- Third-party telemetry or crash SDK. Use local event log and manual export instead.
- Bundled general-purpose VPN service. HostShield can coexist with VPNs and route DNS, but should not become a VPN provider.
- Closed-source binary resolver/filter engines for production defaults.

## Source Index

Local source IDs and external source IDs are defined in `.ai/research/2026-05-17/SOURCE_REGISTER.md`.

Core local evidence:

- L003 `README.md`
- L004 `CHANGELOG.md`
- L008 `app/app/build.gradle.kts`
- L012 `AndroidManifest.xml`
- L013 `DnsVpnService.kt`
- L014 `BlocklistHolder.kt`
- L015 `DohResolver.kt`
- L018 `DoqResolver.kt`
- L019 `WireGuardProxy.kt`
- L020 `DnsStampParser.kt`
- L021 `DnsCryptRoutePlanner.kt`
- L022 `SecureStore.kt`
- L023 `BackupCrypto.kt`
- L024-L026 Room database and migrations
- L027 `TrackerSignatureDb.kt`
- L028 `GeoIpLookup.kt`
- L029 curated blocklists

Primary external evidence:

- E001-E014 Android platform, Compose, Room, Material, and build docs.
- E015-E036 direct competitors and adjacent DNS/firewall projects.
- E037-E041 DNSCrypt implementation references.
- E042-E051 DNS standards.
- E052-E066 security and dependency references.
- E067-E075 datasets and distribution references.
- E076-E078 hardened Android and VPN coexistence references.
