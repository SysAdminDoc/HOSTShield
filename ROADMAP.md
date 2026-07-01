# HostShield Roadmap

Last refreshed: 2026-06-17
Baseline: v6.9.45, versionCode 127

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
inspection covered the repo root, recent git history, release docs,
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
| HS-2026-06-P2-015 | Threat-intel false-positive review workflow | From a threat-blocked log row, show feed, matched value, age, and user actions to allowlist domain, IP/CIDR, or app with clear scope. Include a local review queue for recent threat-intel blocks and export only summaries in diagnostics unless the user includes raw IOC details. | Users can recover quickly from a bad feed hit without disabling all malware protection. | Reduces churn from false positives while keeping local-first privacy boundaries. | `DnsVpnService.kt`, `LogsScreen.kt`, `RulesScreen.kt`, `DiagnosticExporter.kt`; E104, E105 | M | Medium | P2 | Medium |

## Engineering Quality & Bug Backlog — 2026-06-13

Added from a code-quality review (architecture, CI, tests, hygiene) plus the
first user-reported GitHub issue. Bug #1 is treated as P0 — a DNS leak on a
privacy tool is the highest-severity class of defect.

### Reported Bugs

### Code Architecture & Maintainability


## Next - v6.7 / v7.0 Design

### Distribution



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
| 2026-06-06 | Cycle 1 | Repository comprehension | `ROADMAP.md`, `PROJECT_CONTEXT.md`, `README.md`, `docs/RESEARCH.md`, `git log -10`, `git status` | Roadmap baseline was older than current `main`; current baseline remains v6.5.9/versionCode 67 at HEAD `9d361b4`. | Updated refresh date/baseline and added autonomous refresh section. |
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

### P3 - Operational Maturity

- [ ] P3 -- Move from pseudolocale scaffolding to real i18n and LocaleConfig readiness
  Why: Debug pseudolocales and some string resources exist, but the app has no real locale configuration and still contains hardcoded user-facing English in Compose surfaces.
  Evidence: `app/app/build.gradle.kts`; `app/app/src/main/res/values/strings.xml`; `app/app/src/androidTest/java/com/hostshield/ui/LocaleLayoutScaffoldTest.kt`; https://developer.android.com/guide/topics/resources/app-languages
  Touches: `app/app/build.gradle.kts`, `app/app/src/main/res/values/strings.xml`, optional `resources.properties`, `ui/screens/*`, locale/pseudolocale connected tests.
  Acceptance: Top Home/Settings/DNS/QR/backup flows use string resources for user-facing copy, pseudolocale/RTL tests cover those flows, and `generateLocaleConfig` is either enabled with the first real locale or explicitly documented as deferred until translations ship.
  Complexity: M

## Research-Driven Additions — 2026-06-19

### P2

- [ ] P2 — Extract co-located ViewModels to separate files
  Why: 7+ screens (StatsScreen 1,314 LOC, LogsScreen 1,282 LOC, SettingsScreen 1,218 LOC, SourcesScreen 1,166 LOC, DnsToolsScreen 953 LOC, FirewallScreen 913 LOC, OnboardingScreen 845 LOC) embed `@HiltViewModel` classes in the same file as composables. This makes ViewModels harder to test independently and inflates file sizes.
  Evidence: Grep for `@HiltViewModel` shows 18 ViewModels, most co-located with their screen composable; `StatsScreen.kt` has `StatsViewModel` starting at line 173 within the same 1,314-line file.
  Touches: Extract each ViewModel to `<Screen>ViewModel.kt` alongside the screen file.
  Acceptance: Each extracted ViewModel is in its own file; existing tests still pass; no behavioral change.
  Complexity: S

- [ ] P2 — Add Bloom filter pre-check to BlocklistHolder
  Why: The hash set fast path handles exact matches in O(1), but negative lookups (allowed domains) still traverse the trie. A Bloom filter (~1MB for 200K domains, <0.1% false positive) can fast-reject queries not in any blocklist, skipping both hash set and trie. This matters for the 60-70% of queries that are allowed.
  Evidence: `BlocklistHolder.kt` `isBlocked()` checks hash set then walks trie; `DnsCache.kt` comments note 60-70% cache hit rate; Pi-hole and AdGuard Home both use pre-filter structures for fast rejection.
  Touches: `domain/BlocklistHolder.kt`.
  Acceptance: Bloom filter populated during `update()`/`updateAsync()`; `isBlocked()` checks Bloom filter before trie for non-cached domains; unit test verifies no false negatives and measures lookup speedup.
  Complexity: M

- [ ] P2 — Harden AdGuard `$app=` and `$client=` modifier handling
  Why: `AdblockRuleParser.kt` ignores `$app=` and `$client=` scope-limiting modifiers but still applies the base domain rule globally. This means an imported AdGuard rule like `||tracker.com^$app=com.example` blocks tracker.com for ALL apps, not just com.example. Silent global fallback is wrong for a security utility.
  Evidence: `AdblockRuleParser.kt` strips modifiers; RESEARCH.md "Verified" section confirms this; AdGuard DNS filtering syntax docs define `$app=` as Android package scope.
  Touches: `domain/parser/AdblockRuleParser.kt`, `domain/BlocklistHolder.kt`, `service/DnsVpnService.kt` (needs app UID context in block check), `AdblockRuleParserTest.kt`.
  Acceptance: Rules with `$app=` are either applied only to the specified package (preferred) or rejected with a diagnostic log entry explaining the unsupported modifier. Rules are never silently applied globally when a scope modifier is present.
  Complexity: L

## Audit Findings — 2026-06-17

- [ ] P2 — Lifecycle 2.11 requires API 37 compile-SDK readiness
  Why: AndroidX Lifecycle 2.11.0 fails AAR metadata on the current compileSdk 36 line; adopting it safely should be bundled with the API 37 readiness audit instead of bypassing the guard.
  Where: `app/gradle/libs.versions.toml`, `app/app/build.gradle.kts`

- [ ] P2 — Plan the remaining toolchain/dependency refresh as a compatibility batch
  Why: Remaining lint/dependency baseline entries cover target/compile SDK, KSP, core-ktx, Kotlin, serialization, Vico, and JSON; several likely need coordinated Android 17/API 37 readiness and visual/chart regression checks.
  Where: `app/app/build.gradle.kts`, `app/gradle/libs.versions.toml`, chart and serialization call sites.

## Research-Driven Additions - 2026-06-28

### P1

- [ ] P1 - Make release-build experimental DNS controls truthful
  Why: Settings exposes DoQ and WireGuard DNS toggles, but `DnsVpnService` forces both transports off outside `BuildConfig.DEBUG`, so release users can enable preferences that do not affect production DNS routing.
  Evidence: `app/app/src/main/java/com/hostshield/ui/screens/settings/DnsSettingsSection.kt`; `app/app/src/main/java/com/hostshield/service/DnsVpnService.kt`; `app/app/src/main/java/com/hostshield/util/ExperimentalEngineDisclosure.kt`.
  Touches: `DnsSettingsSection.kt`, `SettingsViewModel.kt`, `ExperimentalEngineDisclosure.kt`, README/app metadata, release-doc checks, ViewModel/UI tests.
  Acceptance: Release builds either hide/disable DoQ and WireGuard DNS controls with explicit debug-only copy or route them through a real audited engine; release-doc checks fail if docs imply release-effective experimental transports while production code disables them.
  Complexity: S

- [ ] P1 - Preserve AdGuard `$dnstype=` rules as query-type-aware policy
  Why: The parser recognizes `$dnstype=` but source rebuilds drop those rules before they reach `BlocklistHolder`, losing common DNS-filter semantics that AdGuard and modern adblock lists support.
  Evidence: `app/app/src/main/java/com/hostshield/domain/parser/AdblockRuleParser.kt`; `app/app/src/main/java/com/hostshield/domain/parser/HostsParser.kt`; `app/app/src/main/java/com/hostshield/service/DnsVpnService.kt`; https://adguard-dns.io/kb/general/dns-filtering-syntax/.
  Touches: `AdblockRuleParser.kt`, `HostsParser.kt`, `BlocklistHolder.kt`, DNS decision call sites with qtype context, parser/blocklist tests.
  Acceptance: `$dnstype=A`, `$dnstype=AAAA`, and negated forms are either enforced by qtype in VPN/proxy/root decisions or surfaced as counted unsupported-rule diagnostics; they are never silently discarded.
  Complexity: L

### P2

- [ ] P2 - Generate tracker-owner attribution from an audited local dataset
  Why: HostShield now shows company attribution, but the local tracker owner map is small and hand-maintained while TrackerControl and DuckDuckGo Tracker Radar demonstrate richer generated attribution without requiring telemetry.
  Evidence: `app/app/src/main/java/com/hostshield/util/NetworkTrackerDb.kt`; `app/app/src/main/java/com/hostshield/ui/screens/stats/StatsViewModel.kt`; https://github.com/TrackerControl/tracker-control-android; https://github.com/duckduckgo/tracker-radar.
  Touches: tracker dataset generation tooling, `NetworkTrackerDb.kt`, Stats attribution UI/tests, license/provenance docs.
  Acceptance: A reproducible local build step or checked generated asset maps common tracker domains to owner/category with provenance, Stats uses it for top tracker companies, and tests verify deterministic lookup plus no network dependency.
  Complexity: M

- [ ] P2 - Split blocklist rebuild logic into one tested coordinator
  Why: Source merge/build logic is duplicated across the worker, VPN service, Home apply flow, profile schedules, and source previews, which allowed `forceDownload` and metadata behavior to diverge.
  Evidence: `HostsUpdateWorker.kt`; `DnsVpnService.kt`; `HomeViewModel.kt`; `ProfileScheduleWorker.kt`; `SourcesViewModel.kt`.
  Touches: new domain/service rebuild coordinator, affected call sites, unit tests with fake downloader/repository.
  Acceptance: All rebuild entry points call one coordinator for source downloads, allowlist subtraction, DoH bypass merging, wildcard origins, diagnostics, and metadata updates; existing behavior remains covered by tests.
  Complexity: L

## Research-Driven Additions

### P2

- [ ] P2 — Add an Android 16 large-screen adaptive layout gate
  Why: HostShield targets API 36 and has many dense Compose screens, but no `WindowSizeClass`, Material 3 adaptive scaffold, navigation-rail, split-pane, or sw600 test path; Android 16 ignores orientation/resizability/aspect-ratio restrictions on 600dp+ displays.
  Evidence: `app/app/src/main/AndroidManifest.xml`; `app/app/src/main/java/com/hostshield/ui/screens/**`; `app/app/src/androidTest/java/com/hostshield/ui/LocaleLayoutScaffoldTest.kt`; https://developer.android.com/about/versions/16/behavior-changes-16; https://developer.android.com/docs/quality-guidelines/adaptive-app-quality.
  Touches: `ui/navigation/Navigation.kt`, top-level scaffold, Home/Logs/Stats/Sources/Settings dense screens, `LocaleLayoutScaffoldTest.kt`, screenshot/readme assets if tablet screenshots are added.
  Acceptance: Top flows render without clipping or unreachable primary actions at 841x701dp, 1024x640dp, 1280x800dp, and 1600x900dp; navigation uses an adaptive rail/pane where appropriate; tests cover portrait, landscape, split-screen width, and large font scale; no feature claims are added for tablets/foldables until the gate passes.
  Complexity: L
