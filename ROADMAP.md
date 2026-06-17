# HostShield Roadmap

Last refreshed: 2026-06-16
Baseline: v6.9.26, versionCode 108

## Principles

- Local-first Android DNS firewall and blocker.
- No hosted account model and no remote telemetry.
- No-root VPN path remains first-class; root mode is a power-user accelerator.
- Fail closed for encrypted DNS and security-sensitive downgrade paths.
- Prefer auditable open-source dependencies, standards-track protocols, and local exportable diagnostics.
- Experimental protocol code must stay behind clear maturity gates until audited or replaced.

Research artifacts for this roadmap live in `.ai/research/2026-05-17/`.

Release governance note: the project is licensed under GPL-3.0.

## Autonomous Refresh - 2026-06-06

This refresh resumed the existing roadmap instead of replacing it. Local
inspection covered the repo root, `CLAUDE.md`, recent git history, release docs,
Android manifest/service lifecycle paths, security storage, automation intents,
top-flow UI tests, diagnostics, PCAP export, threat intel, CI, and prior
research docs. External research was refreshed against RethinkDNS, AdGuard for
Android, NetGuard, AdAway, PCAPdroid, Android VPN/foreground-service docs, and
Google Play package-visibility policy.

### Current High-Signal Findings

- **P1 automation connected coverage gap:** v6.6.2 normalized public
  automation docs, manifest actions, legacy aliases, and pause-extra handling,
  but direct receiver behavior still needs connected or instrumented coverage
  for permission denial, rate limiting, status replies, set-profile, set-DNS,
  and pause/resume effects. Evidence: `AutomationReceiver.kt`,
  `AutomationActionContractTest.kt`, `AndroidManifest.xml`.
- **P1 UI coverage advanced but is not complete:** `TopFlowComposeTest.kt`
  now covers onboarding with Private DNS warning, main app affordances, source
  add/remove, rule add/remove, log filtering, and parental PIN lockout. It does
  not yet verify actual VPN start/stop service state, backup export/import file
  behavior, diagnostic ZIP generation/share, Play-flavor visibility behavior,
  RTL/pseudolocale, or connected-device route/DNS behavior.
- **P1 connected/instrumented release evidence gap:** v6.6.3 makes GitHub
  release jobs run unit tests, release-doc checks, Cronet posture validation,
  provenance generation, full APK builds, and Play AAB builds, but connected
  device smoke evidence is still outside the release gate. Evidence:
  `.github/workflows/release.yml`, `TopFlowComposeTest.kt`.
- **P2 evidence-grade packet export opportunity:** `PcapExporter.kt` currently
  emits synthetic PCAP records from blocked DNS/connection logs. PCAPdroid's
  current public feature set highlights PCAPng, remote streaming, SNI/DNS/HTTP
  extraction, and TLS key export as user-facing diagnostics expectations.
  HostShield should keep privacy defaults conservative but can upgrade exports
  with PCAPng metadata and app/domain annotations. Source: E093.
- **P1 backup/export UX is behind backend capability:** Backup encryption and
  Argon2id/PBKDF2 compatibility are covered in crypto tests, but Settings calls
  `backupToUri(uri)` and `restoreFromUri(uri)` without a passphrase UI. Encrypted
  restore returns an `ENCRYPTED:` sentinel message for the UI to handle, but no
  prompt flow was found in `SettingsScreen.kt`. Evidence: `SettingsScreen.kt`,
  `SettingsViewModel.kt`, `BackupCryptoTest.kt`, `BackupRestoreUtil.kt`.
- **P1 backup schema is v1 and misses newer v6.x preferences:** Real backup
  creation writes `backup_version=1` and serializes core sources/rules/profiles,
  firewall rules, DoH, excluded apps, and basic firewall settings. It does not
  yet roundtrip newer preferences such as custom upstream DNS, DNS trap/block
  response, schedule, DoT/DoQ, WireGuard DNS, content filters, parental controls,
  WebDAV sync, pinned/search UI state, or theme/accessibility settings. The
  current `BackupRestoreUtilTest` builds synthetic JSON instead of exercising
  the real utility. Evidence: `BackupRestoreUtil.kt`, `AppPreferences.kt`,
  `BackupRestoreUtilTest.kt`.
- **P2 diagnostic and PCAP export need share/test seams:** Diagnostic export
  builds a ZIP and immediately starts Android's share sheet from application
  context, which is valuable manually but awkward to assert in Compose tests.
  PCAP export writes synthetic `.pcap` files into cache and reports only the
  filename/size, with no SAF destination, FileProvider share, retention cleanup,
  or user-visible privacy confirmation. Evidence: `DiagnosticExporter.kt`,
  `SettingsViewModel.kt`, `PcapExporter.kt`, `SettingsScreen.kt`.
- **P2 PCAPng should be a structured evidence format, not only a renamed PCAP:**
  The current exporter writes classic libpcap records with `LINKTYPE_RAW` and
  synthetic IPv4 DNS/TCP packets. PCAPng gives HostShield a standard Section
  Header, Interface Description, Enhanced Packet, Name Resolution, Interface
  Statistics, comments, and custom-option path for carrying app/domain context.
  The IETF draft also warns that custom copy semantics are not a privacy
  control, so sensitive metadata still needs explicit gating or encryption.
  Evidence: `PcapExporter.kt`, `PcapExporterTest.kt`; Sources: E096, E099.
- **P1 export destination UX should converge across diagnostics, PCAP, CSV, and
  backup:** HostShield already uses SAF `CreateDocument` for rules, CSV, and
  backup, and FileProvider for diagnostic ZIP sharing. PCAP export is the
  outlier: it generates a cache file without a user-selected destination or
  share URI. Android docs support both patterns: SAF for user-chosen persistent
  files and FileProvider content URIs for temporary sharing. Evidence:
  `SettingsScreen.kt`, `SettingsViewModel.kt`, `DiagnosticExporter.kt`,
  `file_paths.xml`, `AndroidManifest.xml`; Sources: E097, E098.
- **P1 threat-intel attribution data exists but lacks dedicated analytics UI:**
  `DnsLogEntry` now carries `decisionReason` ("threat_intel_domain"/"threat_intel_ip"),
  `decisionSource` (feed name), and `matchedValue`. Data is persisted in Room.
  Missing: DAO queries and StatsScreen panels for per-feed impact breakdown.
  Evidence: `DnsVpnService.kt`, `Entities.kt`, `Daos.kt`, `StatsScreen.kt`; Source: E104.
- **P2 existing source-health UX can be reused for threat intel:** HostShield's
  `SourcesScreen` already has health, last update, HTTP status, entry-count
  delta, and failure language for blocklists. Threat-intel feeds sit outside
  that model today, while PCAPdroid sets a clear competitive expectation with
  malware blacklist counts, last update, per-blacklist status, manual update,
  notification attribution, and whitelist actions. Evidence: `SourcesScreen.kt`,
  `SourceHealthWorker.kt`; Sources: E104, E105.

### New Implementation-Ready Backlog Items

| ID | Feature | Description | User Value | Business Value | Evidence | Effort | Impact | Priority | Confidence |
|---|---|---|---|---|---|---|---|---|---|
| HS-2026-06-P1-004 | Connected top-flow test completion | Extend `TopFlowComposeTest` from affordance smoke coverage into actual behavior checks for VPN toggle state, backup export/import, diagnostics ZIP contents, Play flavor app-visibility constraints, RTL/pseudolocale layout, and real source/rule persistence after process recreation. | Core workflows stay reliable across dependency updates. | Reduces regression risk in the most visible app flows. | `TopFlowComposeTest.kt`, `HostShieldTestTags.kt`, `MainActivity.kt`, `SettingsScreen.kt` | M | High | P1 | Medium |
| HS-2026-06-P2-005 | PCAPng diagnostic export | Add optional PCAPng export with interface metadata, app UID/package comments, DNS query annotations, connection-log annotations, and a clear privacy warning before sharing. Keep synthetic PCAP for lightweight compatibility. | Advanced users can diagnose breakage in Wireshark without losing app context. | Differentiates HostShield from basic DNS blockers while preserving local-first diagnostics. | `PcapExporter.kt`, `DiagnosticExporter.kt`; E093 | M | Medium | P2 | Medium |
| HS-2026-06-P1-008 | Backup schema v2 and encrypted backup UI | Add a Settings passphrase flow for encrypted export/import, replace the `ENCRYPTED:` sentinel with typed UI state, introduce backup schema v2 for v6.x preferences, and add real roundtrip tests through `BackupRestoreUtil`. | Users can safely move a full HostShield setup between devices without losing modern DNS/security settings. | Protects the app's local-first portability promise and reduces recovery/support failures. | `SettingsScreen.kt`, `SettingsViewModel.kt`, `BackupRestoreUtil.kt`, `AppPreferences.kt`, `BackupCryptoTest.kt` | M | High | P1 | High |
| HS-2026-06-P2-009 | Diagnostics and PCAP export UX/test seam | Make diagnostic ZIP generation return observable state before sharing, add SAF/share paths for PCAP exports, show privacy warnings for DNS/app metadata, and add tests for ZIP contents and empty/non-empty PCAP outcomes. | Users can retrieve useful diagnostics intentionally and understand what they are sharing. | Improves support evidence quality without adding telemetry. | `DiagnosticExporter.kt`, `PcapExporter.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`, `TopFlowComposeTest.kt` | M | Medium | P2 | High |
| HS-2026-06-P2-010 | PCAPng metadata export v1 | Add an opt-in `.pcapng` writer that emits Section Header, Interface Description, Enhanced Packet, optional Name Resolution, Interface Statistics, and HostShield metadata comments/custom options for app package, app label, rule verdict, source log row id, and redaction mode. Keep classic `.pcap` available as the default compatibility export until the PCAPng path is verified in Wireshark. | Power users get Wireshark-ready captures with enough local context to debug blocked DNS and firewall behavior. | Differentiates HostShield diagnostics without adding telemetry or raw packet capture scope. | `PcapExporter.kt`, `PcapExporterTest.kt`, `DiagnosticExporter.kt`; E096, E099 | M | Medium | P2 | Medium |
| HS-2026-06-P1-011 | Unified export destination controller | Normalize backup, rules, CSV, diagnostic ZIP, and PCAP exports around one ViewModel state model: pending generation, generated file/bytes, save-as via SAF, share via FileProvider, failure, and cleanup. Use explicit privacy-copy per export type. | Users stop seeing cache-only success messages and can always save or share generated evidence intentionally. | Reduces duplicated export logic and creates testable seams for connected UI coverage. | `SettingsScreen.kt`, `SettingsViewModel.kt`, `ProtectionSettingsSection.kt`, `file_paths.xml`, `AndroidManifest.xml`; E097, E098 | M | High | P1 | High |
| HS-2026-06-P2-015 | Threat-intel false-positive review workflow | From a threat-blocked log row, show feed, matched value, age, and user actions to allowlist domain, IP/CIDR, or app with clear scope. Include a local review queue for recent threat-intel blocks and export only summaries in diagnostics unless the user includes raw IOC details. | Users can recover quickly from a bad feed hit without disabling all malware protection. | Reduces churn from false positives while keeping local-first privacy boundaries. | `DnsVpnService.kt`, `LogsScreen.kt`, `RulesScreen.kt`, `DiagnosticExporter.kt`; E104, E105 | M | Medium | P2 | Medium |

## Engineering Quality & Bug Backlog — 2026-06-13

Added from a code-quality review (architecture, CI, tests, hygiene) plus the
first user-reported GitHub issue. Bug #1 is treated as P0 — a DNS leak on a
privacy tool is the highest-severity class of defect.

### Reported Bugs

### Code Architecture & Maintainability

- [ ] **P1 — Decompose `DnsVpnService.kt` (~2,462 LOC god-class, ~22 responsibilities, no direct tests).** Extract `DnsQueryProcessor`, a `DnsForwarder` strategy family, `DnsLogManager`, `BlocklistManager`, `VpnRecoveryMonitor`, and `VpnNotificationController` so each becomes unit-testable. Highest maintainability risk in the repo.
- [ ] **P2 — Move StatsScreen inline business logic to ViewModel.** `StatsScreen` computes latency/percentage/health-threshold logic inline (344-349, 414-418, 460-464). Lift to derived `StateFlow` in the ViewModel.

### CI, Static Analysis & Tests

- [ ] **P2 — Add detekt/ktlint on top of Android lint.** Android lint + `ci.yml` (unit tests + `lintFullDebug` on push/PR) shipped in v6.9.10. detekt/ktlint still pending — gated on a detekt release that supports Kotlin 2.3.x (detekt 1.23.x tops out well below this toolchain).
- [ ] **P3 — Run instrumented (`androidTest`) suite in CI.** Migration, Compose-flow, and automation-receiver tests exist but never run on a CI emulator, so the Room migration safety net isn't exercised per release.

## Next - v6.7 / v7.0 Design

### UI Test Coverage

- [ ] **Connected top-flow Compose UI tests.** Extend the current smoke tests to verify real VPN start/stop state, backup export/import file behavior, encrypted-backup passphrase handling, diagnostic ZIP generation, PCAP export state, Play-flavor app visibility constraints, process-recreation persistence, and RTL/pseudolocale layout safety. Sources: L031, L033, L034, E011, E012.

### Android Platform Resilience

- [ ] **Foreground-service connected validation matrix.** Run a small instrumented or manual adb matrix for each start caller: BootReceiver restore, BlockingScheduleWorker, PauseResumeWorker, AutomationReceiver, HostShieldTileService, RootDnsService.start(), DnsProxyService.start(), and DnsVpnService ACTION_START. Each row should assert caller context, Android version, service type, expected exception/recovery behavior, prefs side effects, and diagnostic event output, including forced `FGS_INTRODUCE_TIME_LIMITS` coverage on Android 15+. Sources: L030, E085, E086.
- [ ] **Android 16 VPN recovery validation pass.** The app already has Android 16 always-on VPN recovery detection; refresh it against current public Android 16 VPN bug reports and add a connected-device script that validates the advisory after app update / zero-inbound-packet conditions where reproducible. Sources: L030, E087, E094.

### Documentation, Automation, And Release Gates

### Backup, Diagnostics, And Export

- [ ] **PCAPng metadata export v1.** Add an opt-in `.pcapng` export path with
  Section Header, Interface Description, Enhanced Packet, optional Name
  Resolution, Interface Statistics, and HostShield comments/custom options. Gate
  app labels, package names, DNS hostnames, connection destinations, and TLS
  secret material behind separate privacy toggles. Sources: L032, L035, E096,
  E099, E100.
- [ ] **Unified export destination controller.** Use one ViewModel/export state
  model for backup, rules, CSV, diagnostic ZIP, and PCAP output: generated,
  pending save-as, pending share, saved, shared, failed, and cleanup. Back it
  with SAF for persistent save-as and FileProvider for temporary sharing.
  Sources: L031, L034, L035, E097, E098.

### Distribution

- [ ] **Obtainium install docs.** Add a tested Obtainium configuration for GitHub release APK installs. Sources: E075, L011.
- [ ] **Play distribution declaration notes.** Document Play Console declaration notes for the full flavor, explain the play flavor's package-visibility fallback behavior, and verify the Play AAB artifact against those notes before submission. Sources: L008, L011, E005, E006, E088.
- [ ] **IzzyOnDroid/reproducible build readiness.** Generate fresh metadata, current changelogs, license/dependency inventory, reproducibility notes, and release checksums. Current `app/metadata/en-US` copy is stale and should be treated as a blocker for non-GitHub publication. Sources: E073, E074.

### Privacy Analytics

- [ ] **Exodus signature refresh pipeline.** Mirror and version tracker signatures from Exodus-compatible sources into a local cache with a frozen mini-corpus test. Sources: L027, E067, E068.
- [ ] **DuckDuckGo Tracker Radar ingestion.** Add optional local tracker-domain dataset refresh for network-based tracker classification. Sources: E069, L027.
- [ ] **Threat-intel false-positive review.** Let users jump from a blocked
  log row to a scoped allowlist action by domain, IP/CIDR, or app while keeping
  raw IOC details out of diagnostics unless explicitly included. Sources: L036,
  E104.

## Later - v7.x

### Architecture

- [ ] **Firestack/tun2socks feasibility spike.** Prototype whether a Go/gomobile TUN layer improves throughput, UID attribution, userspace firewall rules, WireGuard routing, and split DNS enough to justify migration complexity. Sources: E017, E018, E015.
- [ ] **No-root userspace per-app firewall.** If the TUN spike succeeds, implement UID-keyed block/allow rules in VPN mode using Android-supported attribution where available and graceful fallback where not. Sources: E001, E030.
- [ ] **Connection-state-aware rules.** Add block-on-screen-off, metered/unmetered, background-only, until-unlock, and network profile rules across root and VPN paths. Sources: E015, E030.

### Advanced DNS

- [ ] **DNSCrypt engine spike.** Implement the first hidden `DnsCryptEngine` facade and package-size/performance spike using an audited upstream extraction or `dnscrypt-proxy`/gomobile prototype, without exposing a normal settings toggle. Acceptance: certificate validation, X25519/XChaCha20-Poly1305 primitives, anonymized relay wrapping, fail-closed resolver health, and corpus tests are all present before UI exposure. Sources: L020, L021, E037, E038, E039, E040, E041, E095.
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
- Android 15 `dataSync` foreground-service timeout behavior for long-running
  local VPN/root/proxy protection services.

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
- L022 `SecureStore.kt`, `PasswordKdf.kt`
- L023 `BackupCrypto.kt`, `BackupNonceLedger.kt`, `EncryptedBackup.kt`
- L024-L026 Room database and migrations
- L027 `TrackerSignatureDb.kt`
- L028 `GeoIpLookup.kt`
- L029 curated blocklists
- L030 `AndroidManifest.xml`, `BootReceiver.kt`, `DnsVpnService.kt`,
  `RootDnsService.kt`, `DnsProxyService.kt`, `AutomationReceiver.kt`
- L031 `TopFlowComposeTest.kt`, `HostShieldTestTags.kt`, primary Compose screens
- L032 `PcapExporter.kt`, `DiagnosticExporter.kt`, `ThreatIntelManager.kt`
- L033 `.github/workflows/release.yml`, `tools/check-release-docs.ps1`,
  `tools/release-provenance.ps1`, `app/metadata/en-US/*`, `app/README.md`
- L034 `SettingsScreen.kt`, `SettingsViewModel.kt`, `BackupRestoreUtil.kt`,
  `BackupRestoreUtilTest.kt`, `BackupCryptoTest.kt`, `AppPreferences.kt`
- L035 `ProtectionSettingsSection.kt`, `DiagnosticEventStore.kt`,
  `PcapExporterTest.kt`, `file_paths.xml`, `AndroidManifest.xml` FileProvider
  declaration
- L036 `ThreatIntelManager.kt`, `ThreatIntelWorker.kt`,
  `SourceHealthWorker.kt`, `DnsVpnService.kt`, `Entities.kt`, `Daos.kt`,
  `StatsScreen.kt`, `HomeStatsSection.kt`, `SourcesScreen.kt`, and
  threat-intel log/UI call sites

Primary external evidence:

- E001-E014 Android platform, Compose, Room, Material, and build docs.
- E015-E036 direct competitors and adjacent DNS/firewall projects.
- E037-E041 DNSCrypt implementation references.
- E042-E051 DNS standards.
- E052-E066 and E083 security and dependency references.
- E067-E075 datasets and distribution references.
- E076-E078 hardened Android and VPN coexistence references.
- E085 Android Developers: Android 15 `dataSync` foreground-service timeout
  behavior, https://developer.android.com/about/versions/15/behavior-changes-15
- E086 Android Developers: foreground service type guidance and boot-start
  restrictions, https://developer.android.com/develop/background-work/services/fgs/service-types
- E087 Android Developers: VPN, always-on VPN, per-app VPN, and bypass behavior,
  https://developer.android.com/develop/connectivity/vpn
- E088 Google Play policy: `QUERY_ALL_PACKAGES` package visibility permission,
  https://support.google.com/googleplay/android-developer/answer/10158779
- E089 RethinkDNS Android app and docs, https://github.com/celzero/rethink-app
  and https://docs.rethinkdns.com/firewall/
- E090 AdGuard for Android feature/UX notes, https://adguard.com/en/blog/adguard-v4-0-for-android-old-vs-new.html
  and https://adguard.com/en/blog/in-depth-review-adguard-for-android.html
- E091 NetGuard README/FAQ, https://github.com/M66B/NetGuard
- E092 AdAway README, https://github.com/AdAway/AdAway
- E093 PCAPdroid README/docs, https://github.com/emanuele-f/PCAPdroid
- E094 Android 16 VPN market-signal reports, including
  https://www.techradar.com/vpn/vpn-privacy-security/problems-with-your-android-vpn-warns-its-googles-fault
- E095 DNSCrypt upstream/protocol references, https://github.com/DNSCrypt/dnscrypt-proxy
  and https://dnscrypt.info/protocol
- E096 IETF OPSAWG pcapng draft, including Section Header, Interface
  Description, Enhanced Packet, Name Resolution, Interface Statistics, comments,
  and custom extension guidance,
  https://datatracker.ietf.org/doc/draft-ietf-opsawg-pcapng/
- E097 AndroidX FileProvider docs for content-URI sharing and temporary grants,
  https://developer.android.com/reference/androidx/core/content/FileProvider
- E098 Android Storage Access Framework docs for user-chosen document creation,
  https://developer.android.com/training/data-storage/shared/documents-files
- E099 PCAPdroid user guide dump modes and PCAPng feature notes,
  https://emanuele-f.github.io/PCAPdroid/dump_modes and
  https://emanuele-f.github.io/PCAPdroid/paid_features
- E100 Wireshark TLS/decryption-secrets references for PCAPng DSB expectations,
  https://wiki.wireshark.org/TLS and https://wiki.wireshark.org/DecryptionBlock
- E101 URLhaus Community API and download behavior, including feed freshness and
  auth-key expectations, https://urlhaus.abuse.ch/api/
- E102 Spamhaus DROP FAQ, including DROP/eDROP merge, attribution, and refresh
  cadence expectations, https://www.spamhaus.org/faqs/do-not-route-or-peer-drop/
- E103 Emerging Threats compromised-IP feed current output shape,
  https://rules.emergingthreats.net/blockrules/compromised-ips.txt
- E104 PCAPdroid paid malware-detection/status UX and blacklist update model,
  https://emanuele-f.github.io/PCAPdroid/paid_features
- E105 HaGeZi DNS blocklists and Threat Intelligence Feed variants,
  https://github.com/hagezi/dns-blocklists

## Detailed Feature Spec Addendum - 2026-06-06

**Current Command Matrix To Verify:**

| Action | Current code support | Manifest filter | README status | Required extras | Test expectation |
|---|---|---|---|---|---|
| `com.hostshield.ACTION_ENABLE` | Yes | Yes | Documented | None | Starts configured method or records controlled failure. |
| `com.hostshield.ACTION_DISABLE` | Yes | Yes | Documented | None | Stops configured method and sets `isEnabled=false`. |
| `com.hostshield.ACTION_TOGGLE` | Yes | Yes | Not documented | None | Toggles based on current `prefs.isEnabled`. |
| `com.hostshield.ACTION_APPLY_FIREWALL` | Yes | Yes | Not documented | None | Applies iptables rules and audits result. |
| `com.hostshield.ACTION_CLEAR_FIREWALL` | Yes | Yes | Not documented | None | Clears iptables rules and audits result. |
| `com.hostshield.ACTION_STATUS` | Yes | Yes | Documented | None | Sends `com.hostshield.STATUS_RESULT` with enabled/method/firewall/version. |
| `com.hostshield.ACTION_REFRESH_BLOCKLIST` | Yes | Yes | Documented | None | Queues one-time `HostsUpdateWorker`. |
| `com.hostshield.ACTION_SET_PROFILE` | Yes | Yes | Documented | `profile_name` | Activates matching profile or audits missing/not found. |
| `com.hostshield.ACTION_SET_DNS` | Yes | Yes | Documented | `dns_servers` | Persists custom upstream DNS or audits missing extra. |
| `com.hostshield.ACTION_PAUSE` | Yes | Yes | Documented with legacy alias compatibility | `duration_minutes` | Pauses immediately; `duration_minutes=0` resumes and cancels worker. |

### Feature: Backup, Diagnostics, And PCAP Export Flow

**Problem:** Settings exposes backup, restore, diagnostic ZIP, and PCAP export
affordances, but several flows stop at display or cache-file behavior. The
encrypted backup backend is available, yet the visible backup UI currently
launches plaintext export/import calls with no passphrase prompt.

**Proposed Solution:** Treat backup and diagnostics as first-class recovery
workflows. Add typed UI state, SAF/share integration, schema-v2 roundtrip
coverage, and privacy-confirmed diagnostic exports.

**User Stories:**

- As a privacy-conscious user, I want to encrypt a backup before saving it to
  cloud storage or another device.
- As a restoring user, I want an encrypted backup to prompt for a passphrase
  and recover all modern DNS/firewall/security settings.
- As a support user, I want diagnostic and PCAP exports to be retrievable,
  intentionally shared, and clear about included data.

**UX Requirements:**

- Backup export asks whether to save plaintext or encrypted, with passphrase
  confirmation for encrypted output.
- Encrypted restore shows a passphrase dialog, retry/error state, and no raw
  sentinel prefixes in user-facing text.
- Diagnostic and PCAP exports show pending/success/error state and a share/save
  action after generation.
- Privacy warnings explicitly mention DNS hostnames, app labels/packages, local
  config, and connection destinations when included.

**Technical Requirements:**

- Replace `backupMessage = "ENCRYPTED:..."` with typed UI state in
  `SettingsUiState`.
- Introduce backup schema v2 and migration/compat handling for v1 plaintext and
  encrypted backups.
- Roundtrip preference coverage through real `BackupRestoreUtil` tests rather
  than synthetic JSON-only assertions.
- Add a fakeable `DiagnosticExporter` or generation result wrapper so Compose
  and ViewModel tests can assert ZIP state without launching Android shares.
- Let PCAP export target a SAF URI or produce a FileProvider share URI, and add
  cache retention cleanup for generated files.

**Acceptance Criteria:**

- [ ] Encrypted backup can be created from Settings and restored after process
  recreation.
- [ ] Wrong passphrase shows a retryable error and records a diagnostic event.
- [ ] Schema v2 restore preserves custom upstream DNS, DoT/DoQ, WireGuard DNS,
  schedule, DNS trap/block response, content filter, parental, firewall, and
  theme/accessibility preferences.
- [ ] Diagnostic ZIP tests verify expected entries and manifest version fields.
**Dependencies:** Existing `BackupCrypto`, `BackupNonceLedger`,
`DiagnosticExporter`, `PcapExporter`, SAF launchers, and `DiagnosticEventStore`.

**Risks:** Exporting WebDAV credentials or other secrets must remain opt-in and
encrypted-only; diagnostics must avoid surprising disclosure of browsing
history beyond the explicit local logs the user chooses to share.

### Feature: Privacy-Preserving PCAPng Export And Unified Destination UX

**Problem:** Current PCAP export produces classic `.pcap` cache files and shows
only a filename. That limits user retrieval, testability, and Wireshark context.
Adding PCAPng without a privacy model would also risk leaking app labels,
packages, DNS names, connection destinations, or TLS secrets through comments,
custom options, and Decryption Secrets Blocks.

**Proposed Solution:** Keep classic PCAP as the lightweight compatibility path
and add opt-in PCAPng as a richer evidence mode. Route all generated exports
through a common destination controller that lets the user save through SAF,
share through FileProvider, or discard generated artifacts after review.

**User Stories:**

- As an advanced user, I want a PCAPng export with HostShield context so I can
  open it in Wireshark and understand which local rule or app produced a packet.
- As a privacy-sensitive user, I want to choose whether app names, package
  names, hostnames, IPs, and secrets are included before sharing an export.
- As a tester, I want export generation to expose observable state so connected
  UI tests can verify the artifact without depending on Android's chooser.

**UX Requirements:**

- PCAP export starts with a privacy dialog before generation: classic PCAP,
  PCAPng with redacted metadata, or PCAPng with full local metadata.
- Generated exports show actions for "Save", "Share", and "Discard" plus size,
  record count, time range, and included metadata summary.
- Diagnostic support bundles and packet-evidence exports stay separate by
  default. A support ZIP may include a manifest reference to a PCAP artifact only
  when the user chooses to attach it.
- TLS or other decryption secrets are disabled by default and require a second
  explicit confirmation because Wireshark treats embedded secrets as a way to
  decrypt capture contents.

**Technical Requirements:**

- Add a `PcapngExporter` or expand `PcapExporter` with a clear interface:
  `exportClassicPcap`, `exportPcapngRedacted`, and `exportPcapngFullMetadata`.
- PCAPng v1 writer should emit Section Header Block, Interface Description
  Block, Enhanced Packet Blocks, optional Name Resolution Block, and optional
  Interface Statistics Block.
- Use `opt_comment` for human-readable redacted context and portable custom
  options only after deciding whether HostShield needs an IANA Private
  Enterprise Number for durable app-specific metadata.
- Do not write TLS/DoH/DoT/DoQ/WireGuard secret material until there is a
  separate audited Decryption Secrets Block design and UI confirmation.
- Add a shared export result model in `SettingsViewModel` with generated file,
  content URI, export type, privacy mode, record counts, failure reason, and
  cleanup callback.
- Use SAF `ACTION_CREATE_DOCUMENT`/Compose launchers for persistent save-as and
  FileProvider `content://` URIs with read grants for temporary sharing.
- Add cache cleanup policy for generated diagnostic and PCAP files older than a
  short retention window or discarded by the user.

**Acceptance Criteria:**

- [ ] Classic PCAP export still writes a valid libpcap file and remains
  available as a compatibility option.
- [ ] PCAPng export opens in Wireshark with a valid Section Header, Interface
  Description, and Enhanced Packet sequence.
- [ ] Redacted PCAPng contains no app labels, package names, DNS hostnames, raw
  connection destinations, or TLS/decryption secrets.
- [ ] Full-metadata PCAPng records clearly list which sensitive metadata classes
  were included before saving or sharing.
- [ ] Users can save exports to a chosen location through SAF or share them via
  a temporary FileProvider URI.
- [ ] Connected UI or ViewModel tests can verify generated export state without
  launching Android's chooser.

**Dependencies:** Existing `PcapExporter`, `DiagnosticExporter`,
`file_paths.xml`, `SettingsViewModel`, `ProtectionSettingsSection`, Room log
DAOs, and current FileProvider manifest entry.

**Risks:** PCAPng custom options may not render usefully in Wireshark without a
plugin or private option interpretation; metadata comments are visible to anyone
with the file and must be treated as sensitive. TLS/secret export would be a
separate security feature, not a default diagnostic convenience.

**Open Questions:** Whether HostShield should register an IANA PEN before
portable custom PCAPng options, whether metadata should be comments-only for v1,
and whether PCAPng should ever include decryption secrets for local encrypted
DNS troubleshooting.

### Feature: Threat Intelligence Feed Health And Impact Dashboard

**Problem:** Threat-intel blocking is active but mostly invisible. The engine
can block from domain and IP feeds, yet it cannot show per-feed freshness,
partial failures, parser drift, or which feed caused a user-visible block.

**Proposed Solution:** Add a persisted threat-intel feed-state model, harden
feed parsing and refresh semantics, and surface health plus impact in Stats,
Logs, and a Sources-like detail view. Keep all telemetry local and export only
summaries by default.

**User Stories:**

- As a security-conscious user, I want to know whether each malware feed is
  fresh, stale, degraded, or failed.
- As a user hit by a false positive, I want to see the exact feed and matched
  value so I can allowlist narrowly.
- As a maintainer, I want diagnostics that explain feed health without bundling
  the raw IOC database.

**UX Requirements:**

- Add a dashboard card with overall threat-intel state: healthy, degraded,
  stale, or failed; feeds updated X/Y; last successful update; domain and CIDR
  totals; and blocked impact over 24 hours / 7 days.
- Add a feed detail list using existing source-health language: feed name,
  type, entry count, last success, last failure, HTTP status, bytes, checksum,
  stale threshold, consecutive failures, and manual refresh action.
- Add log-row details for threat-intel blocks with block reason, feed name,
  match type, matched domain/IP/CIDR, and allowlist actions by domain, IP/CIDR,
  or app.
- Show partial refresh as degraded, not healthy, even when one feed succeeded.

**Technical Requirements:**

- Create a persisted `ThreatIntelFeedState` model or table with feed name, URL,
  type, update policy, last success, last failure, last HTTP status, byte count,
  SHA-256, parsed domain count, parsed CIDR count, and consecutive failures.
- Update `refreshFeedsAndPersist` so each feed returns a structured result.
  Preserve successful feeds, mark failed feeds individually, and expose an
  aggregate degraded state.
- Preserve the v6.6.4 IP-feed parser regression coverage for whitespace and CIDR
  tokens, and add malformed-token counters when feed-state persistence lands.
- Add DNS-log attribution fields or a separate attribution table for
  `blockReason`, `threatFeed`, `threatMatchType`, and `threatMatchedValue`.
- Extend DAOs with counts by feed, top apps/domains by feed, last matched time,
  and recent false-positive review queries.
- Include feed-health summary in diagnostic ZIP manifests while omitting raw IOC
  lists unless the user explicitly chooses an advanced export.

**Acceptance Criteria:**

- [ ] If one of five feeds fails, the worker result is observable as degraded
  and the UI names the failed feed plus its last HTTP/error state.
- [ ] URLhaus and Spamhaus refresh policies are documented in code or metadata,
  including Spamhaus DROP cadence and the eDROP merge.
- [ ] Threat-intel DNS log rows show feed and match type for both domain and IP
  matches.
**Dependencies:** `ThreatIntelManager`, `ThreatIntelWorker`, `DnsVpnService`,
Room migrations, `DnsLogDao`, `StatsScreen`, `LogsScreen`, `SourcesScreen`,
`DiagnosticExporter`, and current source-health UI patterns.

**Risks:** Upstream feed auth or rate-limit policies can change without app
updates; blocklist false positives can break domains or apps; storing raw IOCs
or matched destinations in diagnostics would create avoidable privacy exposure.

**Open Questions:** Whether feed state should live in Room or the existing JSON
cache, whether URLhaus should remain on the public hostfile path or migrate to
an authenticated API/download path, and whether Spamhaus eDROP should be removed
from defaults or retained as a compatibility alias after the 2024 DROP merge.

## Research Log

| Date | Cycle | Research Area | Sources / Files Reviewed | Key Findings | Roadmap Changes |
|---|---|---|---|---|---|
| 2026-06-06 | Cycle 1 | Repository comprehension | `CLAUDE.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `README.md`, `docs/RESEARCH.md`, `git log -10`, `git status` | Roadmap baseline was older than current `main`; worktree has untracked `AGENTS.md` left untouched; current baseline remains v6.5.9/versionCode 67 at HEAD `9d361b4`. | Updated refresh date/baseline and added autonomous refresh section. |
| 2026-06-06 | Cycle 2 | Current feature inventory | `TopFlowComposeTest.kt`, `SecureStore.kt`, `PasswordKdf.kt`, `AutomationReceiver.kt`, `AndroidManifest.xml`, `.github/workflows/release.yml` | UI smoke tests now exist; Argon2id and secure-store migration are current; automation docs drift from code; release workflow does not run local gates. | Split UI-test item into completed smoke coverage and remaining connected coverage; added automation and CI backlog items. |
| 2026-06-06 | Cycle 3 | Platform resilience | Android Developers FGS docs, Android VPN docs, `BootReceiver.kt`, service declarations | `dataSync` services have Android 15 timeout/boot restrictions; HostShield uses `dataSync` for long-running protection services. | Added P0 Android 15/16 service resilience item and detailed spec. |
| 2026-06-06 | Cycle 4 | Competitive landscape | RethinkDNS, AdGuard Android, NetGuard, AdAway, PCAPdroid, Play policy | Competitors set expectations around split DNS/firewall, app/global firewall rules, screen/roaming conditions, root+VPN hosts blocking, PCAPng/TLS diagnostics, and package-visibility justification. | Added source IDs, PCAPng export item, Play AAB/package-visibility notes, and future research leads. |
| 2026-06-06 | Cycle 5 | Android foreground-service start surface | `RootDnsService.kt`, `DnsProxyService.kt`, `BlockingScheduleWorker.kt`, `PauseResumeWorker.kt`, `HostShieldTileService.kt`, `rg startForegroundService` | Protection starts come from boot, WorkManager schedule/resume, automation, tile, direct helpers, and service restarts; no inspected service exposes `onTimeout()` handling. | Expanded P0 item with start-surface matrix requirement and updated continuation state. |
| 2026-06-06 | Cycle 6 | Automation API contract | `AutomationReceiver.kt`, `AutomationAuditScreen.kt`, `Daos.kt`, `Entities.kt`, `README.md`, `app/README.md`, `AndroidManifest.xml`, `rg AutomationReceiver` | Receiver supports 10 actions; docs cover only 5 and use stale lowercase strings; pause docs use `pause_minutes` while receiver expects `duration_minutes`; no direct receiver behavior tests found. | Added command matrix and automation helper backlog item. |
| 2026-06-06 | Cycle 7 | Release and distribution readiness | `.github/workflows/release.yml`, `tools/release-provenance.ps1`, `tools/check-release-docs.ps1`, `app/README.md`, `app/metadata/en-US/*` | CI only builds/uploads full APK; provenance Kotlin regex misses current compose-plugin version; metadata/changelogs are stale v1.x-era copy; release-doc check misses metadata, badges, and automation example drift. | Added store metadata release-truth gate and expanded release CI/doc-check items. |
| 2026-06-06 | Cycle 8 | Connected UI, backup, diagnostics, and export gaps | `TopFlowComposeTest.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt`, `BackupRestoreUtil.kt`, `BackupRestoreUtilTest.kt`, `BackupCryptoTest.kt`, `DiagnosticExporter.kt`, `PcapExporter.kt`, `AppPreferences.kt` | Connected UI tests cover source/rule/log/parental behavior but backup and diagnostics are affordance-only; encrypted backup backend has no Settings passphrase flow; backup schema v1 misses newer preferences; diagnostic sharing and PCAP cache output need observable save/share paths. | Added backup schema v2, encrypted-backup UI, diagnostic ZIP seam, and PCAP share/save backlog/spec items. |
| 2026-06-06 | Cycle 9 | PCAPng, diagnostic export, and privacy-preserving destination design | `PcapExporter.kt`, `PcapExporterTest.kt`, `ProtectionSettingsSection.kt`, `SettingsViewModel.kt`, `DiagnosticExporter.kt`, `DiagnosticEventStore.kt`, `file_paths.xml`, Android FileProvider/SAF docs, IETF pcapng draft, PCAPdroid docs, Wireshark TLS/DSB notes | PCAPng can carry useful HostShield context through standard blocks, comments, and custom options, but custom-copy behavior is not privacy protection; PCAP export is the only major Settings export still cache-only; FileProvider and SAF already provide the right destination patterns. | Added PCAPng metadata export v1, unified export destination controller, source IDs E096-E100, and a full PCAPng/export destination spec. |
| 2026-06-06 | Cycle 10 | Threat-intel freshness dashboard and feed health UX | `ThreatIntelManager.kt`, `ThreatIntelWorker.kt`, `DnsVpnService.kt`, `Entities.kt`, `Daos.kt`, `StatsScreen.kt`, `HomeStatsSection.kt`, `SourcesScreen.kt`, `SourceHealthWorker.kt`, URLhaus docs, Spamhaus DROP FAQ, Emerging Threats feed, PCAPdroid malware UX, HaGeZi blocklists | Threat intel stores only aggregate freshness and counts; partial refresh can appear healthy; DNS logs lose feed/match attribution; the current Emerging Threats feed shape is whitespace-separated and likely missed by the line-only parser; PCAPdroid and HostShield's own source-health UI define a better status model. | Added feed-health dashboard, match attribution analytics, parser/refresh hardening, false-positive review backlog items, source IDs E101-E105, and a full threat-intel dashboard spec. |

## Research Queries To Run Later

- Android 15 VpnService foreground service `dataSync` timeout real device behavior
- Android 16 VPN update bug Issue Tracker HostShield mitigation zero inbound TUN
- HostShield AdAway Magisk KernelSU APatch systemless hosts compatibility
- RethinkDNS split DNS domain rules app attribution Android 12 native DNS logs
- PCAPng Android app UID annotation Wireshark custom comments best practice
- Google Play VPN content filtering QUERY_ALL_PACKAGES acceptable use declaration
- F-Droid IzzyOnDroid reproducible Android app Gradle 9 AGP 9 requirements
- DNSCrypt Android gomobile AAR size dnscrypt-proxy extraction examples
- Compose pseudolocale instrumentation Android Gradle Managed Devices
- Hagezi OISD false positive user complaints Android DNS blocker allowlist UX
- Android SAF encrypted backup binary MIME type and passphrase UX patterns
- Android FileProvider PCAP share cache retention privacy best practice
- IANA Private Enterprise Number Android app pcapng custom option precedent
- Wireshark pcapng custom option display plugin practical examples
- Android Wireshark PCAPng Name Resolution Block app attribution examples
- PCAPdroid pcapng decryption secrets user complaints privacy review
- ThreatIntelManager Emerging Threats whitespace-separated feed parser Android test
- URLhaus hostfile auth-key migration public downloads HostShield
- Spamhaus EDROP merged into DROP mobile app refresh policy
- Threat intel false positive whitelist UX DNS firewall Android
- Android malware DNS blocker feed freshness dashboard URLhaus Spamhaus UX

## Open Questions

- Should HostShield keep `dataSync`, return to `specialUse`, or use another
  foreground-service policy shape for always-on local protection services?
- How should a future automation command-helper surface explain canonical
  actions versus legacy lowercase aliases without encouraging new alias usage?
- Is Play distribution a near-term goal, or should full APK/F-Droid/Obtainium
  remain the primary distribution lane?
- How much packet detail should PCAPng export include by default without
  surprising privacy-sensitive users?
- Should WebDAV credentials and other secrets be excluded from backup v2 unless
  the backup is encrypted?
- Should PCAP/PCAPng defaults include app labels/packages, or should that
  metadata require a separate privacy confirmation?
- Should HostShield register an IANA Private Enterprise Number before writing
  portable custom PCAPng options, or keep PCAPng v1 comments-only?
- Should TLS/decryption-secret export stay prohibited until DNSCrypt/DoH/DoT
  troubleshooting has a separate audited consent model?
- Should Spamhaus eDROP remain in the default feed list now that Spamhaus merged
  eDROP data into DROP in 2024?
- Should threat-intel feed health live in Room for queryability or remain in the
  existing JSON/cache path for smaller migration risk?
- Should URLhaus use an authenticated API/download path, the current public
  hostfile URL, or a curated threat-intel pack instead?

## Next Research Cycles

1. Cycle 11: DNSCrypt engine spike planning with dependency/licensing review.
2. Cycle 12: Play flavor/package visibility policy and manifest review.
3. Cycle 13: Competitor UX teardown of firewall and resolver health flows.
4. Cycle 14: Accessibility, RTL, pseudolocale, and dynamic type evidence pass.
5. Cycle 15: Android 15 foreground-service connected-device matrix execution
   plan.
6. Cycle 16: Automation receiver compatibility-alias and generated-help design.
7. Cycle 17: Export-state implementation decomposition and test fixture design.
8. Cycle 18: Threat-intel feed-state migration and parser-test decomposition.

## Continuation State

### Last Completed Cycle

Cycle 10: Threat-intel freshness dashboard and feed health UX.

### Current Focus

Preparing the next autonomous pass around DNSCrypt engine spike planning,
dependency/licensing review, and implementation-test corpus design.

### Important Findings So Far

- `ROADMAP.md` now reflects HEAD `9d361b4` and current date.
- `tools/check-release-docs.ps1` passed locally for v6.5.9/versionCode 67.
- `TopFlowComposeTest.kt` adds meaningful smoke coverage but not full connected
  behavior coverage.
- Android 15 `dataSync` service limits are the highest-risk newly identified
  platform issue because HostShield targets SDK 35 and declares all protection
  services as `dataSync`.
- Foreground-service starts occur from boot, schedule, pause/resume,
  automation, Quick Settings tile, and direct helper paths; the next platform
  pass should convert this into an executable matrix.
- Automation docs are stale relative to `AutomationReceiver.kt`; receiver
  behavior needs direct tests and a generated command/help surface.
- Release/distribution metadata is stale outside the current release-doc gate,
  and CI does not yet enforce local release checks or provenance.
- Backup crypto primitives are well covered, but Settings currently exposes no
  encrypted-backup passphrase flow and restore uses a string sentinel for an
  encrypted-backup prompt.
- Backup schema v1 omits multiple modern v6.x settings exposed through
  `AppPreferences`, including custom DNS, DoT/DoQ, WireGuard DNS, schedule,
  content/parental controls, WebDAV, and theme/accessibility state.
- Diagnostic ZIP generation and PCAP export need observable save/share state so
  tests and users can retrieve generated artifacts intentionally.
- PCAPng should be opt-in and metadata-aware, not a simple extension rename.
  Use standard blocks first, treat comments/custom options as sensitive, and
  keep TLS/decryption secrets behind a separate future consent model.
- Android SAF and FileProvider are already available patterns in the app; PCAP
  export should join the same save/share/discard destination model used by
  backup/rules/CSV/diagnostic flows.
- Threat-intel refresh state is aggregate-only today; the worker treats partial
  success as success and does not persist per-feed HTTP status, bytes, checksum,
  parse counts, last success/failure, or consecutive failures.
- DNS log rows lose threat-intel attribution even though `DnsVpnService` has
  feed name and match type at decision time.
- The current Emerging Threats feed output is whitespace-separated; v6.6.4 added
  parser coverage for that shape, but malformed-token counters are still pending
  feed-state persistence.
- PCAPdroid's malware-detection status UX and HostShield's existing
  `SourcesScreen` health model provide a practical blueprint for threat-intel
  freshness, impact, manual refresh, and false-positive review.

### Next Best Actions

1. Inspect `DnsStampParser.kt`, `DnsCryptRoutePlanner.kt`, DNSCrypt-related
   tests, and current build dependencies for real versus placeholder engine
   behavior.
2. Research Android-compatible DNSCrypt implementation options, including
   upstream `dnscrypt-proxy`/gomobile, libsodium-compatible primitives, AAR size,
   licensing, and maintenance burden.
3. Convert DNSCrypt findings into a dependency/licensing decision record,
   hidden-engine spike plan, and corpus-test requirements, then refresh the
   continuation state again.

### Unprocessed Leads

- NetGuard FAQ compatibility notes around always-on VPN, work profiles, and OEM
  VPN bugs.
- RethinkDNS split DNS/app attribution implementation details in firestack.
- PCAPdroid PCAPng metadata and remote-streaming docs for diagnostics export.
- DNSCrypt gomobile extraction feasibility and AAR size.
- Whether legacy lowercase automation aliases should remain indefinitely or be
  removed after a documented compatibility window.
- Whether store metadata should live under fastlane-compatible ownership or be
  generated from README/release notes to prevent drift.
- Whether encrypted backup export should use `.json`, `.hsbackup`, or another
  extension/MIME type to avoid implying readable plaintext.
- Whether diagnostics should offer separate "support bundle" and "packet
  evidence" exports so PCAP data is never bundled accidentally.
- Whether PCAPng metadata should use comments-only for v1 or wait for a
  registered PEN-backed custom option schema.
- Whether classic PCAP should remain the default forever because it is easier to
  inspect and less metadata-rich.

### Files Still To Inspect

- `app/app/src/main/java/com/hostshield/service/DnsVpnService.kt` focused
  chunks around `startForeground`, `onDestroy`, watchdog, and restart paths
- `app/app/src/main/java/com/hostshield/service/CaptivePortalHandler.kt`
- `app/app/src/main/java/com/hostshield/ui/screens/logs/LogsScreen.kt`
- `app/app/src/main/java/com/hostshield/ui/screens/settings/ProtectionSettingsSection.kt`
- `app/app/src/main/java/com/hostshield/util/DiagnosticEventStore.kt`
- `app/app/src/test/java/com/hostshield/util/PcapExporterTest.kt`
- `app/app/src/main/java/com/hostshield/util/DnsStampParser.kt`
- `app/app/src/main/java/com/hostshield/service/DnsCryptRoutePlanner.kt`
- DNSCrypt unit tests, fixtures, and dependency declarations

### Searches Still To Run

- `rg -n "startForegroundService|onTimeout|ForegroundServiceStartNotAllowedException|ACTION_SET_PROFILE|duration_minutes|pause_minutes" app/app/src`
- `rg -n "androidTest|connectedAndroidTest|managedDevices|testOptions|pseudolocale|locale" app`
- `rg -n "generateDiagnosticReport|generateAndShare|exportPcap|PcapExporter|FileProvider|CreateDocument" app/app/src`
- `rg -n "ThreatIntel|threat intel|URLhaus|Spamhaus|Emerging|feed|lastUpdate|malware" app/app/src`
- `rg -n "DnsCrypt|DnsStamp|dnscrypt|XSalsa|XChaCha|libsodium|gomobile|stamp" app/app/src app/app/build.gradle.kts`
- Web: `Android 15 VpnService dataSync foreground service timeout`
- Web: `dnscrypt-proxy gomobile Android AAR DNSCrypt client`

## Research-Driven Additions -- 2026-06-09

### P1 -- High

### P2 -- Medium

- [ ] P2 -- Remove Tink dependency after v7.0
  Why: Legacy EncryptedSharedPreferences migration has shipped since v6.4.0 (6 versions). Tink is only used in `SecureStore.LegacyEncryptedPreferencesReader`. The migration sets `KEY_LEGACY_MIGRATION_DONE=true` and never calls Tink again. Safe to remove after v7.0 if no `legacy_secure_migration` diagnostic events are observed in crash reports.
  Touches: `app/app/build.gradle.kts` (remove tink-android), `SecureStore.kt` (remove LegacyEncryptedPreferencesReader), `proguard-rules.pro` (remove Tink dontwarn)
  Complexity: S

- [ ] P2 -- Add network-aware SSID-based profile switching
  Why: Blokada and RethinkDNS offer automatic profile switching by Wi-Fi SSID (e.g., aggressive blocking at coffee shops, relaxed at home). HostShield has profiles but no automatic SSID-based profile selection.
  Evidence: `IptablesManager.kt` (NetworkCallback), `ProfileRepository.kt`, Blokada network-aware profiles feature.
  Touches: `IptablesManager.kt` or new NetworkCallback, `ProfileRepository.kt`, `SettingsScreen.kt` or new profile-network mapping UI
  Acceptance: Users can associate a blocking profile with a Wi-Fi SSID. When connecting to that SSID, the profile activates automatically. Manual override is always available.
  Complexity: M

- [ ] P2 -- Android 16 Pixel fast-network-switching compatibility
  Why: InviZible Pro v7.4.0 added an option to control fast network switching and disabled it by default for Pixel devices on Android 16, suggesting a platform-specific VPN stability issue. HostShield's VPN reconnect logic should be verified.
  Evidence: InviZible Pro v7.4.0 changelog; `DnsVpnService.kt` network callback handling (NetworkChangeReceiver removed — dead on minSdk 26).
  Touches: `DnsVpnService.kt`, possible new setting
  Acceptance: Verified that rapid Wi-Fi/cellular transitions on Pixel Android 16 do not cause VPN tunnel corruption or DNS resolution failure. Documented workaround if needed.
  Complexity: S

## Research-Driven Additions -- 2026-06-09 (Pass 2)

### P1 -- High

### P2 -- Medium

- [ ] P2 -- DDR-based encrypted-DNS upgrade advisory (RFC 9462)
  Why: No Android DNS blocker auto-discovers the local resolver's encrypted endpoint while Apple, Windows, Quad9, and Cloudflare ship DDR today -- a leapfrog feature that stays consistent with fail-closed pinning by only one-tap-applying known pinned providers and labeling everything else opportunistic.
  Evidence: RFC 9462 (`_dns.resolver.arpa` SVCB), https://datatracker.ietf.org/doc/rfc9462/; NextDNS DDR feature-request thread; existing DoH pin manifest in `DohResolver.kt`.
  Touches: new `DdrProbe` util, `DnsToolsScreen` or Home advisory card, `DohResolver`/`DotResolver` provider config
  Acceptance: on network change an opt-in probe queries `_dns.resolver.arpa` SVCB via the system resolver, surfaces discovered encrypted endpoints with a verification status, and one tap applies a matching pinned provider; no automatic switch to unpinned endpoints.
  Complexity: M

- [ ] P2 -- NAT64/DNS64 awareness for custom encrypted upstreams (needs live validation)
  Why: on IPv6-only carrier networks, forwarding DNS to a non-DNS64 upstream (pinned DoH) breaks IPv4-only destinations because no AAAA synthesis occurs; detecting Pref64 via ipv4only.arpa and synthesizing AAAA preserves connectivity.
  Evidence: RFC 7050 / RFC 8880; `rg "ipv4only|Pref64|DNS64"` over `app/app/src/main` finds no synthesis logic (only a dns64.dns.google bypass-list entry in `BlocklistHolder.kt`).
  Touches: `DnsVpnService.kt` forward paths, new `Dns64Synthesizer` util, `DiagnosticExporter` network section
  Acceptance: Pref64 is discovered on NAT64 networks (or an emulated fixture), AAAA synthesis applies to A-only answers when an encrypted upstream is active, unit tests cover fixture prefixes, and diagnostics record detection; live-device validation gates default-on.
  Complexity: L

### P3 -- Low

- [ ] P3 -- Material 3 Expressive motion/shape adoption pass
  Why: Compose Material3 1.4+ ships the new MotionScheme and expressive shape/progress components; HostShield's BOM 2026.05.00 already includes the APIs, and a scoped motion/shape refresh keeps the premium UI current without a redesign.
  Evidence: https://m3.material.io/blog/m3-expressive-motion-theming; `app/app/build.gradle.kts` composeBom 2026.05.00; v6.5.1 polish-pass baseline.
  Touches: `ui/theme/*`, shared components (cards, progress indicators, chart containers)
  Acceptance: MotionScheme applied app-wide, updated progress/shape tokens on Home/Stats/Sources, no layout regressions under font scaling, before/after screenshots reviewed.
  Complexity: M

## Research-Driven Additions

### P1

### P2

## Research-Driven Additions — 2026-06-13

### P1

### P2

- [ ] P2 — Newly Registered Domain (NRD) blocking
  Why: NextDNS's most differentiated security feature, paywalled at $1.99/mo. Domains registered within the last 30 days are disproportionately used for phishing, malware C2, and spam. No open-source Android DNS blocker implements this. HostShield can offer it locally at zero recurring cost.
  Evidence: NextDNS NRD feature; https://nextdns.io/; Quad9 threat intelligence approach; URLhaus feed analysis showing high NRD prevalence in malware domains.
  Touches: New `NrdChecker` util, `BlocklistHolder.kt` decision path, `DnsVpnService.kt` block-reason attribution, NRD feed/data source integration, Settings toggle, `DnsLogEntry` block-reason field.
  Acceptance: Domains younger than a configurable threshold (default 30 days) are blocked when NRD protection is enabled; block reason is attributed as "newly registered" in logs; a curated NRD feed or WHOIS-based age check provides the data; false-positive rate is documented.
  Complexity: L

### P3

- [ ] P3 — Typosquatting and IDN homograph detection
  Why: NextDNS detects lookalike domains (e.g., `paypai.com`, Cyrillic `а` substituted for Latin `a`). This catches phishing domains that bypass blocklists because they are newly created or not yet reported. Local-first detection using Levenshtein distance against a curated brand list requires no cloud service.
  Evidence: NextDNS typosquatting/homograph protection feature; IDN homograph attack Wikipedia documentation; Unicode confusable characters database (unicode.org/reports/tr39/).
  Touches: New `HomographDetector` util, `BlocklistHolder.kt` or `DnsVpnService.kt` decision path, curated brand/domain list, `DnsLogEntry` block-reason attribution.
  Acceptance: Known confusable substitutions of popular domains (configurable brand list) are detected and blocked or warned; IDN domains with mixed-script characters are flagged; detection runs locally with no network calls; false-positive rate on legitimate IDN domains is documented.
  Complexity: L

- [ ] P3 — DGA (Domain Generation Algorithm) heuristic detection
  Why: NextDNS detects algorithmically-generated domains used by botnets. HostShield's threat intel is feed-based and cannot catch zero-day DGA domains not yet in any feed. A lightweight entropy/n-gram classifier running locally can flag high-entropy random-looking domains for review or blocking.
  Evidence: NextDNS DGA detection feature; academic literature on DGA detection via character entropy and n-gram frequency; Quad9 threat intelligence approach.
  Touches: New `DgaClassifier` util, `BlocklistHolder.kt` or `DnsVpnService.kt` decision path, Settings toggle (off by default), `DnsLogEntry` block-reason attribution, false-positive review integration with threat-intel allowlisting.
  Acceptance: A lightweight classifier flags domains exceeding an entropy/n-gram threshold; flagged domains are blocked (strict mode) or logged-only (monitor mode); classifier does not require network access; known CDN/cloud random-subdomain patterns are allowlisted; false-positive rate on legitimate domains is < 0.1%.
  Complexity: L

- [ ] P3 — Tracker company attribution view
  Why: TrackerControl and NextDNS show "tracking companies" (Google, Facebook, Amazon, etc.) as a first-class concept, not just blocked domains. This turns raw block data into a privacy story users can understand and act on. HostShield's tracker scanner (405 SDK signatures) already has company metadata but does not surface it in DNS log analytics.
  Evidence: TrackerControl company-level statistics UI; NextDNS "Tracker Insights" percentage-of-traffic visualization; DuckDuckGo Tracker Radar dataset (company attribution).
  Touches: `TrackerSignatureDb.kt`, `StatsScreen.kt`, `LogsScreen.kt`, new tracker-company aggregation in DAOs or ViewModel.
  Acceptance: Stats or a dedicated privacy view shows top tracking companies by blocked query count with percentage breakdown; tapping a company shows its domains and affected apps; data is derived from existing tracker signatures and DNS logs with no network calls.
  Complexity: M

- [ ] P3 — Query analytics dashboard with block-rate trends and drill-down
  Why: NextDNS, Control D, and Cloudflare Gateway all paywall analytics dashboards (top blocked/allowed domains, block rate over time, per-app breakdown, category distribution). HostShield's StatsScreen has 7-day trend charts but no drill-down into top domains, per-app breakdowns, or category-level analytics. Offering this locally differentiates against cloud services that charge $2-7/mo for equivalent visibility.
  Evidence: NextDNS analytics dashboard; Control D audit trail; Cloudflare Gateway analytics; existing `StatsScreen.kt` and `Daos.kt` query capabilities.
  Touches: `StatsScreen.kt`, `DnsLogDao`, new DAO queries for top-N blocked/allowed domains, per-app block counts, category distribution, block-rate-over-time aggregation.
  Acceptance: StatsScreen shows top 10 blocked domains, top 10 allowed domains, per-app block counts, block rate percentage over 24h/7d, and category distribution; drill-down from any stat opens filtered log view; all computation is local with no network calls.
  Complexity: M

## Research-Driven Additions — 2026-06-14

These items are net-new vs. the existing roadmap. The 2026-06-14 research pass
confirmed the prior RESEARCH.md top opportunities (one-tap allowlist, EDE
emission, YouTube Restricted Mode, log retention, temporary-bypass UX, Cronet
CVE, targetSdk 36) are all shipped or moot, so none are re-added. DDR, NAT64,
DNSCrypt spike, ODoH, EDE parse/display, SVCB-ECH awareness, DNSSEC, resolver
tests, the `DnsVpnService` decomposition, and the analytics/tracker items above
already exist and are deliberately excluded.

### P1

- [ ] P1 — Android 17 (API 37) Certificate Transparency readiness for pinned encrypted DNS
  Why: Targeting API 37 enables Certificate Transparency by default for system-trust TLS. Because the encrypted-DNS stack is fail-closed (no plaintext fallback), a pinned DoH/DoT provider whose cert is not CT-logged would fail CT verification and produce a total DNS outage — the highest-severity defect class for this app (same impact as the GitHub #1 leak). Must be audited before any targetSdk 37 bump.
  Evidence: https://developer.android.com/privacy-and-security/certificate-transparency-policy ; `app/app/src/main/res/xml/network_security_config.xml` (system trust anchors, no per-domain config); `DohResolver.kt`/`DotResolver.kt` CertificatePinner usage; https://developer.android.com/about/versions/17/behavior-changes-17
  Touches: `network_security_config.xml` (add `<domainEncryption>`/CT opt-out per provider as needed), `DohResolver.kt`, `DotResolver.kt`, `DohPinManifest.kt`, a provider-cert CT-status audit doc/test.
  Acceptance: Every default and user-selectable DoH/DoT provider cert is verified CT-logged, or a per-domain CT opt-out is declared; an instrumented or documented check confirms encrypted DNS still resolves on an API-37 build; no fail-closed outage on CT enforcement.
  Complexity: M

### P2



- [ ] P2 — Android 17 ACCESS_LOCAL_NETWORK readiness audit
  Why: Android 17 (API 37) enforces the new `ACCESS_LOCAL_NETWORK` permission for LAN access by default. The core resolver path is exempt (DNS to/from a local DNS server on port 53), but the LAN-exposed Local DNS server on non-:53 ports and any LAN probing would be blocked without the permission. Needs an audit before targeting API 37.
  Evidence: https://developer.android.com/privacy-and-security/local-network-permission (port-53 DNS exemption); `LocalDnsServer.kt` (LAN bind + abuse controls), captive-portal/connectivity probes, any mDNS/NSD usage.
  Touches: `AndroidManifest.xml` (declare `ACCESS_LOCAL_NETWORK` if needed), `LocalDnsServer.kt`, `CaptivePortalHandler.kt`, runtime-permission request flow, docs.
  Acceptance: An audit enumerates every LAN-touching call site and whether it falls under the :53 exemption; the LAN DNS server (non-:53) and any LAN probe declare/request the permission and degrade gracefully when denied on API 37; core port-53 resolution verified unaffected.
  Complexity: M

### P3


- [ ] P3 — Android 17 ECH opt-in for the DoH/DoT transport
  Why: Distinct from the existing "SVCB/HTTPS/ECH awareness" item (which parses the `ech` SVCB param for *resolved domains*). Android 17 adds platform Encrypted Client Hello for the app's *own* TLS connections via `<domainEncryption>` in network security config, hiding the SNI of HostShield's DoH/DoT resolver connections — anti-censorship hardening consistent with fail-closed pinning.
  Evidence: https://developer.android.com/about/versions/17/behavior-changes-17 (ECH via `<domainEncryption>`); `res/xml/network_security_config.xml`; `DohResolver.kt`/`DotResolver.kt`.
  Touches: `network_security_config.xml` (`<domainEncryption>` opportunistic/enabled per resolver host), settings toggle, docs; gated on minSdk/targetSdk reaching API 37.
  Acceptance: On API-37 builds, DoH/DoT resolver connections negotiate ECH when the resolver supports it (opportunistic by default), with a user toggle; no regression when the resolver lacks ECH; fail-closed pinning preserved.
  Complexity: M

- [ ] P3 — Battery-friendly mode (doze-aware resolver tuning)
  Why: Battery drain is the recurring real-world complaint against always-on VPN-mode DNS firewalls (RethinkDNS users report ~16% drain with DNS+firewall). HostShield already has Doze/App-Standby resilience (v6.5.3) but no user-facing battery-optimization mode; leading on a measurable low-power path is a differentiator.
  Evidence: https://www.saashub.com/compare-rethinkdns-vs-netguard (battery-drain reports); existing `DnsVpnService.kt` watchdog (60s heartbeat), `DohResolver` provider racing/EMA, `DnsCache` serve-stale.
  Touches: `DnsVpnService.kt` (watchdog cadence under battery saver), `DohResolver.kt` (cap concurrent provider probing), `DnsCache.kt` (extend serve-stale window), a setting + PowerManager battery-saver observer.
  Acceptance: A battery-saver-aware mode reduces watchdog frequency, caps DoH provider racing to the fastest known provider, and widens serve-stale on low battery; measured drain reduction documented; resolution correctness and fail-closed behavior unchanged.
  Complexity: M

## Research-Driven Additions — 2026-06-14 (Pass 2)

Net-new vs. the existing roadmap and vs. the 2026-06-14 (Pass 1) additions above.
This pass re-verified every Pass-1 code claim against the v6.9.10 source. Items
confirmed already-shipped and therefore NOT added: per-app VPN exclusion
(`AppExclusionsScreen.kt`, `addDisallowedApplication`), QR config share/import
(`QrConfigImporter.kt`), custom-URL blocklist subscriptions (`SourceRepository`/
`SourceDownloader`), fastest-upstream auto-pick (`DohResolver.getFastestProvider`).
The Android 15+ `dataSync` 6h foreground-service cap is moot — all protection
services declare FGS type `systemExempted`, not `dataSync`.

### P2

- [ ] P2 — Android Developer Verification readiness (Sept 2026 deadline)
  Why: Google's mandatory developer verification for sideload-installable apps on certified Android devices begins September 2026 (Brazil, Indonesia, Singapore, Thailand first, then global). HostShield's primary distribution lanes are GitHub release APK, F-Droid/IzzyOnDroid, and Obtainium — all sideload paths affected. Without a verified developer identity + registered package/signing-key, the APK may stop installing on certified devices post-rollout. Distinct from the existing Play/Obtainium/IzzyOnDroid items, which do not address verification identity.
  Evidence: https://blog.accrescent.app/posts/android-developer-verification/ ; https://accrescent.app/docs/guide/publish/requirements.html ; existing distribution items in this ROADMAP (Obtainium/Play/IzzyOnDroid).
  Touches: release/signing process docs (`tools/release-provenance.ps1`, `app/metadata/en-US/*`), README distribution section, GitHub release workflow notes; no app-code change.
  Acceptance: A documented plan exists for registering the HostShield package + signing-key fingerprint under Google's developer-verification program (or an explicit decision to remain unverified with the device-compatibility consequences stated); the release docs name the responsible identity and key-custody path; verified before the global rollout reaches the project's user base.
  Complexity: S

### P3


## Research-Driven Additions

### P1



## Audit Findings — 2026-06-15

### P1

### P2


### P3

- [ ] P3 — ~60+ hardcoded English strings in Home, Stats, Warnings, DNS Settings, Sources dialogs
  Why: Breaks localization. Existing pattern uses `stringResource()` in other files.
  Where: `HomeComponents.kt`, `HomeStatsSection.kt`, `HomeWarningsSection.kt`, `DnsSettingsSection.kt`, `SettingsScreen.kt`, `SourcesScreen.kt`

## Research-Driven Additions

### P0 - Critical Security and Data Safety

### P1 - Reliability and Hardening

- [ ] P1 -- Add APK signing lineage and key-rotation drill
  Why: The existing developer-verification item registers the current package/signing key, but it does not prove update survivability after release-key loss, compromise, or planned rotation across GitHub/F-Droid/Obtainium installs.
  Evidence: `app/app/build.gradle.kts` signing configuration; `hostshield-release.keystore`; https://source.android.com/docs/security/features/apksigning/v3 ; https://source.android.com/docs/security/features/apksigning/v3-1
  Touches: release signing docs, `tools/release-provenance.ps1`, `.github/workflows/release.yml`, release verification notes, private key-custody procedure.
  Acceptance: A documented rotation drill creates an APK signing lineage, signs test APKs with old/new keys, verifies install/update behavior on API 28/33/37, records current and rotated fingerprints, and states the recovery path if the production key is lost or compromised.
  Complexity: M

### P2 - Evidence Quality and Observability


### P3 - Operational Maturity

- [ ] P3 -- Ship a startup and protection-enable Baseline Profile
  Why: HostShield is a Compose-heavy VPN utility where first launch, onboarding, and enabling protection are latency-sensitive, but no baseline profile module or plugin is configured.
  Evidence: `app/app/build.gradle.kts`; `app/app/src/main/java/com/hostshield/MainActivity.kt`; `app/app/src/main/java/com/hostshield/ui/screens/onboarding/OnboardingScreen.kt`; https://developer.android.com/topic/performance/baselineprofiles/overview ; https://developer.android.com/develop/ui/compose/performance/baseline-profiles
  Touches: Gradle plugin/dependencies, new baseline profile or macrobenchmark module, `MainActivity.kt`, onboarding/Home/Settings critical flows, CI release verification.
  Acceptance: Release builds include a generated Baseline Profile for cold start, onboarding, Home load, and protection-enable paths; CI can regenerate or verify the profile; startup metrics before/after are recorded.
  Complexity: M

- [ ] P3 -- Move from pseudolocale scaffolding to real i18n and LocaleConfig readiness
  Why: Debug pseudolocales and some string resources exist, but the app has no real locale configuration and still contains hardcoded user-facing English in Compose surfaces.
  Evidence: `app/app/build.gradle.kts`; `app/app/src/main/res/values/strings.xml`; `app/app/src/androidTest/java/com/hostshield/ui/LocaleLayoutScaffoldTest.kt`; https://developer.android.com/guide/topics/resources/app-languages
  Touches: `app/app/build.gradle.kts`, `app/app/src/main/res/values/strings.xml`, optional `resources.properties`, `ui/screens/*`, locale/pseudolocale connected tests.
  Acceptance: Top Home/Settings/DNS/QR/backup flows use string resources for user-facing copy, pseudolocale/RTL tests cover those flows, and `generateLocaleConfig` is either enabled with the first real locale or explicitly documented as deferred until translations ship.
  Complexity: M

## Research-Driven Additions

### P1

- [ ] P1 — Respect scoped AdGuard DNS rules instead of globalizing them
  Why: Scoped `$app=`, `$client=`, and `$ctag=` DNS rules are safety boundaries; applying their base domain globally can create false positives and makes imported custom rules less trustworthy.
  Evidence: `app/app/src/main/java/com/hostshield/domain/parser/AdblockRuleParser.kt`; https://adguard-dns.io/kb/general/dns-filtering-syntax/ ; https://github.com/AdguardTeam/Adguardhome/wiki/Clients
  Touches: `AdblockRuleParser.kt`, `HostsParser.kt`, DNS rule matching in `BlocklistHolder.kt`/`DnsVpnService.kt`, parser tests, custom-source import validation.
  Acceptance: Scoped rules are either honored against Android package/client metadata or safely skipped with a parser warning; no `$app=`, `$client=`, or `$ctag=` rule can block globally unless explicitly unscoped; unit tests cover positive, negated, malformed, and mixed scoped-rule imports.
  Complexity: M

- [ ] P1 — Verify release attestations and publish user verification commands
  Why: Release CI now generates APK/AAB/SBOM attestations, but users and maintainers still lack a release-blocking verification step and copy-ready provenance commands.
  Evidence: `.github/workflows/release.yml`; `tools/release-provenance.ps1`; https://docs.github.com/en/actions/concepts/security/artifact-attestations ; https://cli.github.com/manual/gh_attestation_verify
  Touches: `.github/workflows/release.yml`, `tools/release-provenance.ps1`, `tools/check-release-docs.ps1`, `README.md`, `app/README.md`.
  Acceptance: Release CI verifies APK, AAB, and SBOM attestations with repo/workflow identity constraints; release provenance includes exact `gh attestation verify` commands for `SysAdminDoc/HostShield`; docs check fails if those commands drift.
  Complexity: S

- [ ] P1 — Gate DoH/DoT pin manifest freshness and live-chain drift
  Why: `DohPinManifest` records review/expiry dates and DoT reuses those pins, but CI does not fail before a review-due/expired pin or provider-chain rotation can cause encrypted-DNS outage.
  Evidence: `app/app/src/main/java/com/hostshield/service/DohPinManifest.kt`; `app/app/src/main/java/com/hostshield/service/DotResolver.kt`; OkHttp `CertificatePinner` behavior.
  Touches: `DohPinManifest.kt`, new pin-audit test or script, `tools/check-release-docs.ps1`, release workflow, diagnostics.
  Acceptance: A deterministic test fails on review-due or expired pins; an optional live-chain audit validates each configured provider has at least one pinned SPKI in the verified chain; diagnostics and release provenance report pin freshness.
  Complexity: M

### P2

- [ ] P2 — Add curated blocklist source provenance and license metadata gate
  Why: The curated gallery is a trust surface, but entries lack homepage, license, maintainer, issue-report URL, last-reviewed date, mirror policy, and format/license compatibility checks.
  Evidence: `app/app/src/main/assets/curated_blocklists.json`; `app/app/src/main/java/com/hostshield/data/repository/SourceRepository.kt`; https://github.com/AdguardTeam/HostlistsRegistry ; https://github.com/StevenBlack/hosts
  Touches: `curated_blocklists.json`, `SourceRepository.kt`, `BlocklistGalleryScreen.kt`, `CuratedBlocklistsCatalogTest.kt`, release-doc checks.
  Acceptance: Every curated source has license/provenance/review metadata; tests reject duplicate URLs, non-HTTPS URLs, missing issue/homepage/license fields, stale review dates, and unsupported formats; gallery rows surface license/provenance details without adding clutter.
  Complexity: M

- [ ] P2 — Add automated accessibility regression gates for key Compose flows
  Why: HostShield has accessibility modifiers and connected smoke flows, but no gate catches missing labels, broken semantics, live-region regressions, or undersized touch targets before release.
  Evidence: `app/app/src/main/java/com/hostshield/ui/accessibility/AccessibilityModifiers.kt`; `app/app/src/androidTest/java/com/hostshield/ui/TopFlowComposeTest.kt`; https://developer.android.com/develop/ui/compose/accessibility/testing ; https://developer.android.com/training/testing/espresso/accessibility-checking
  Touches: `TopFlowComposeTest.kt`, `HostShieldTestTags.kt`, shared Compose components, Home/Sources/Rules/Logs/Settings/DNS Tools/Parental screens.
  Acceptance: Connected tests assert labels/content descriptions, switch/tab state descriptions, live regions, focusable actions, and minimum 48dp targets on the top flows; suppressions are narrow and documented if a framework limitation blocks a check.
  Complexity: M

- [ ] P2 — Expand release truth checks to root evidence docs
  Why: Root docs still carry product/security claims outside the current release-doc gate, including stale generated logo prompt copy that mentions removed offline GeoIP support.
  Evidence: `LOGO_PROMPTS.md`; `CLAUDE.md`; `tools/check-release-docs.ps1`; `RESEARCH.md`
  Touches: `tools/check-release-docs.ps1`, `LOGO_PROMPTS.md`, `CLAUDE.md`, release-doc validation patterns.
  Acceptance: Release-doc validation reads all root product-claim docs or explicitly excludes generated/developer-only docs; stale phrases for removed features, experimental transports, SDK versions, and resolver posture fail the check.
  Complexity: S
