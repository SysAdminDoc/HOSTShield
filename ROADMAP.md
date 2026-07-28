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

## Audit Findings — 2026-07-22 (v6.9.59 deep audit)

The v6.9.59 pass fixed ~45 issues across correctness, security, UX, theming, and
accessibility (see CHANGELOG). The items below were found but deferred because
they need a device, a product decision, an external key, or an unreleased SDK.

### P2 — Needs device / live verification

- [ ] Backup schema v2 + encrypted-backup passphrase UI: the crypto backend
      exists but Settings has no passphrase prompt, and the schema omits several
      v6.x preferences on round-trip. (Pre-existing spec above.)

### P3 — Deferred correctness / coverage

- [ ] Hosts IPv4/IPv6 redirect-target prefs (`setIpv4Redirect`/`setIpv6Redirect`)
      still have no Settings control — they default to `0.0.0.0`/`::` (correct for
      blocking) and only matter for custom redirect responses. Add validated IP
      text fields if the custom-redirect use case is prioritized.

- [ ] Adblock `$denyallow` is still approximated as a global wildcard-allow (the
      cross-source exact-allow leak was removed in v6.9.59). Attach the exception
      to the owning rule for full AdGuard-correct scoping.
- [ ] `DomainAgeChecker` uses a small hard-coded multi-part-suffix table; adopt a
      full public-suffix list for complete ccSLD coverage.
- [ ] WireGuard key-entry UI is missing even in debug builds (keys can only
      arrive via QR/backup import), so the experimental transport can't be
      completed from Settings.

## Audit Findings — 2026-07-28

Baseline at audit time (v6.9.62, versionCode 144, commit 5b0703b): `testFullDebugUnitTest` + `lintFullDebug` BUILD SUCCESSFUL (lint: 0 errors, 11 warnings; 3 errors + 10 warnings pre-existing in lint-baseline.xml); `tools/check-release-docs.ps1` PASSES — note this contradicts the Roadmap_Blocked "Re-sign doh-bypass-list.json" item, which says the gate is red (see note added there).

### P2


- [ ] P3 — Backup does not round-trip the `app_dns_rules` table (per-app DNS rules)
  Category: correctness
  Where: `util/BackupRestoreUtil.kt` (`createBackup`/`restoreBackup`); `data/database/Daos.kt` AppDnsRuleDao
  Problem: profiles.wifi_ssids and the firewall context columns now round-trip and the firewall REPLACE clobber is fixed, but per-app DNS rules (`app_dns_rules`) are still neither exported nor restored, so app-scoped DNS allow/block rules are lost on restore.
  Fix: Add an `app_dns_rules` array to `createBackup` and a matching restore loop (inject AppDnsRuleDao), inside the existing restore transaction.
  Acceptance: Round-trip backup/restore preserves per-app DNS rules.
  Confidence: Verified
  Effort: M


### P3

- [ ] P3 — Home search, Logs multi-select, and add-dialogs lose user input on rotation (`remember` instead of `rememberSaveable`)
  Category: ux
  Where: `home/HomeScreen.kt:95-96` (searchQuery/searchExpanded); `logs/LogsScreen.kt:72-74` (multiSelectMode/selectedHostnames/showClearLogsDialog); `sources/SourcesScreen.kt:75-76,694-696` (AddSourceDialog fields); `lists/RulesScreen.kt:52-55,269-274`; `settings/ProtectionSettingsSection.kt:58-62`; `stats/StatsScreen.kt:471`
  Problem: MainActivity declares no `android:configChanges`, so rotation/fold recreates the activity. Typed search text, an in-progress multi-select of dozens of domains, half-filled Add Source/Add Rule dialogs (dialog vanishes), and evidence-export filters are all wiped because they use plain `remember`.
  Evidence: Grep shows no `rememberSaveable` in `ui/` except OnboardingScreen.
  Fix: Switch these to `rememberSaveable` (custom Saver for `Set<String>`/enums), or hoist search/selection into the ViewModels (LogsViewModel already holds its own search).
  Acceptance: Rotating mid-search/mid-selection/mid-dialog preserves input and the open dialog.
  Confidence: Verified
  Effort: M

- [ ] P3 — SecureStore fails open on Keystore loss — parental PIN gate silently evaporates
  Category: security
  Where: `data/preferences/SecureStore.kt:38-46` (`getString` → `getOrDefault(default)`); `service/ParentalControlManager.kt:176-177,221`
  Problem: If the Keystore master key becomes unusable (OS/vendor keymaster fault), `getString` returns `""`. `isPinSet()` then returns false and `verifyPinDetailed` returns `NoPin`, so all PIN-gated parental actions become accessible with no PIN — fail-open on a control meant to restrict another user. `contains()` still returns true for the undecryptable entry (internally inconsistent).
  Evidence: The decrypt failure path is a blanket `runCatching{}.getOrDefault(default)`; no caller distinguishes "absent" from "present but undecryptable".
  Fix: Expose a tri-state read (absent/value/undecryptable); ParentalControlManager treats "undecryptable while parental_enabled=true" as locked (require PIN reset), not no-PIN.
  Acceptance: With a broken master key and parental controls enabled, PIN-gated screens show a lock/recovery state, not open access.
  Confidence: Verified (code path; trigger is environmental)
  Effort: M

- [ ] P3 — Custom upstream DNS field silently accepts invalid input; success/error state inferred by message-sniffing
  Category: ux
  Where: `settings/DnsSettingsSection.kt:147-171` + `SettingsViewModel.kt:670` (`setCustomUpstreamDns` persists any text); `DnsPreferences.kt:66-68` drops invalid at consumption; banner accent via `msg.contains("unreachable")` (SettingsScreen.kt)
  Problem: Invalid servers ("one.one.one.one", a typo'd IP) get a green check and no error, then silently fall back to defaults at consumption — unlike the LAN DNS port field which validates and surfaces an error. `DnsServerInputPolicy.parseServerList` already exists and could validate at input time.
  Evidence: Setter has no validation; SettingsScreen error styling uses substring matches.
  Fix: Run `DnsServerInputPolicy.parseServerList` on the field, show per-entry error/supportingText before save; replace message-sniffing with explicit success/error state.
  Acceptance: An invalid server shows inline error and disables the check button; banners no longer depend on substrings.
  Confidence: Verified
  Effort: M

- [ ] P3 — Block-alert notifications only run in root mode; documented burst/new-domain alerts are unimplemented
  Category: correctness
  Where: `service/BlockNotificationService.kt:20-26` (kdoc claims 3 alert types); started only from `RootDnsService.kt:76/82/95/104/128`
  Problem: The high-tracker-activity monitor is started exclusively by RootDnsService, so VPN and DNS-proxy users (the default/majority) never get block alerts though DnsLogDao is populated identically. The kdoc also documents "New tracking domain" and ">20 queries/sec burst" alerts that don't exist (only the 50-in-5-min check is implemented).
  Evidence: Grep for `blockNotificationService` shows only RootDnsService call sites; `checkForBursts` is the sole detector.
  Fix: Start/stop the monitor from DnsVpnService and DnsProxyService too (it's a transport-agnostic DAO poller); trim the kdoc to the implemented alert.
  Acceptance: With VPN protection on and a chatty tracker app, the "High tracker activity" notification fires.
  Confidence: Verified
  Effort: S

- [ ] P3 — DnsBenchmark accepts any UDP payload as a valid answer and cannot be cancelled from the UI
  Category: correctness
  Where: `util/DnsBenchmark.kt:131-152` (`measureDnsQuery`); `DnsBenchmarkScreen.kt` (no cancel affordance)
  Problem: `measureDnsQuery` never validates the response — transaction ID, source address, RCODE unchecked — so a SERVFAIL/REFUSED or stray datagram counts as a fast success, skewing resolver rankings users act on. The blocking loops ignore cancellation, so leaving the screen leaves up to 10 coroutines running ~27s each (bounded, not a leak). No stop button.
  Evidence: Only `socket.receive` success is measured; sockets are unconnected `DatagramSocket()`.
  Fix: `socket.connect(addr,53)` to filter sources, compare returned txn ID with sent, treat RCODE!=0 as failure; check `isActive` between queries and add a Cancel action.
  Acceptance: A resolver returning REFUSED ranks as unreachable; leaving the screen stops network activity promptly.
  Confidence: Verified
  Effort: M

- [ ] P3 — Downloaded source allowlists can neutralize REMOTE DoH-bypass guard entries (latent until the manifest re-signs)
  Category: security
  Where: `domain/BlocklistHolder.kt` `decideInternal()` — hardcoded DoH checks (710-725) precede allows, but remote-list entries live in `exactBlockSet`/wildcardBlock, evaluated AFTER `sourceExactAllowDomains` (735) and wildcard-allow (776-790); `BlocklistSourceCoordinator.rebuildBlocklistHolder` merges the remote cache into ordinary block sets (197-202)
  Problem: v6.9.59 stopped source allowlists from bypassing threat-intel, but the remote DoH-bypass list gets no such protection: a subscribed allowlist containing a remote-listed DoH endpoint returns "Source allowlist" allowed before the block is consulted, re-opening the bypass the list exists to close. Latent now (shipped manifest previously failed signature) but note the release-doc gate now PASSES — so the remote cache may become populated in production and this becomes live.
  Evidence: `mergeCachedInto` puts remote domains/wildcards into `allDomains`/`sourceWildcardBlocks` with only an origin label; no decision-path special-casing. `ThreatIntelBypassDecisionTest` covers threat intel only.
  Fix: Track remote DoH-bypass domains/wildcards as a dedicated snapshot field checked alongside the hardcoded sets (before allow evaluation), or exclude them from allow overrides the way threat intel is. User allows may still win.
  Acceptance: Remote cache contains `dns.newprovider.com`; a source allowlist with the same domain → `decide()` still returns blocked/`doh_bypass`; a user allow still wins.
  Confidence: Verified
  Effort: M

- [ ] P3 — `AdblockRuleParser.parseLine` does no domain-shape validation — URL/port/inline-wildcard patterns become inert junk rules
  Category: correctness
  Where: `domain/parser/AdblockRuleParser.kt` — `parseLine()` 280-285 (only checks empty/length ≤253); `DOMAIN_PATTERN` exists (line 462) but is only used by `parseAsHostsOrDomain`
  Problem: `||example.com/path`, `||example.com:8080^`, `||1.2.3.4^`, `||track*.example.com^` all produce `DnsRule`s with non-hostname `domain` values that land in the block/wildcard sets — inflating entry counts and the trie/bloom, and (for port/inline-wildcard) silently matching nothing, dropping the author's intent with no diagnostic (unlike the `$app`/`$removeparam` "skipped" classes).
  Evidence: After the caret/`$` split, `domain` is lowercased but never matched against `DOMAIN_PATTERN`; no test for `||domain/path`, ports, or inline wildcards.
  Fix: Validate `cleanDomain` against `DOMAIN_PATTERN` (allowing the stripped `*.` prefix); emit an "unsupported_pattern" diagnostic for rejects so counts/warnings stay honest.
  Acceptance: `parseLine("||example.com:8080^")` and `parseLine("||ad*.example.com^")` return null with a bulk-parse diagnostic; counts exclude them.
  Confidence: Verified
  Effort: S

- [ ] P3 — `$important` block protection doesn't extend to subdomain-scoped exceptions in the same source
  Category: correctness
  Where: `domain/parser/HostsParser.kt` — `importantBlockDomains` 130-133, allow filter line 174
  Problem: The v6.9.60 fix compares `rule.domain in importantBlockDomains` by exact string. `||x.example^$important` + `@@||sub.x.example^` in the same source: the allow's domain isn't in the set, so it becomes a wildcard allow that wins (deeper allow beats shallower block), overriding the important block for `sub.x.example` — contrary to AdGuard priority. `$important` on `$dnstype` rules is also dropped (`DnsTypeRule` has no importance flag).
  Evidence: `HostsParserTest` "important block outranks…" covers only identical domains; `decideInternal` 776-790 confirms the deeper wildcard allow wins.
  Fix: When checking an allow rule, also test whether any `importantBlockDomains` entry is a suffix ancestor of the allow domain; add an `important` flag to `DnsTypeRule` and prefer important typed blocks over non-important typed allows.
  Acceptance: Same-source `||x.example^$important` + `@@||sub.x.example^` → `sub.x.example` remains blocked; an `@@…$important` sub-allow still wins.
  Confidence: Verified
  Effort: M

- [ ] P3 — Partial block-source failure silently drops that source's rules from the live snapshot (no per-source content cache)
  Category: reliability
  Where: `service/BlocklistSourceCoordinator.kt:79-103,144-163`
  Problem: If 1 of N block sources fails during a rebuild, the swap proceeds and that source's domains vanish until the next successful refresh — e.g. losing OISD Big (200K) while keeping a 400-entry list. The user is notified and health recorded, but blocking silently degrades. The code comment acknowledges the no-disk-cache constraint but only guards total failure.
  Evidence: Failed source contributes nothing; no per-source parsed-content cache exists (only the DoH-bypass list has `getCached`).
  Fix: Persist each source's last-good parsed output (compact domain list keyed by source id) and merge it for sources that fail, mirroring ThreatIntelManager's v6.9.60 last-good IOC carry-forward.
  Acceptance: Source A succeeds then fails on the next rebuild while B succeeds → snapshot still contains A's last-good domains and A is marked ERROR.
  Confidence: Verified
  Effort: M-L

- [ ] P3 — WebDavSync.buildUrl form-decodes remote paths (`+`→space) and never re-encodes — encoded/special-char filenames break
  Category: correctness
  Where: `util/WebDavSync.kt:339-349` (`buildUrl` runs `URLDecoder.decode(remotePath,"UTF-8")` then string-concats)
  Problem: `URLDecoder.decode` form-decodes (`+`→space) and the decoded path is concatenated into the request URL without re-encoding. A remote file `backup+1.json`, or any percent-encoded href (spaces, `#`, non-ASCII), requests a different resource or throws (swallowed → generic "failed"). This is on the path used with server PROPFIND hrefs.
  Evidence: Decode-once-then-concat at 341-348; `Request.Builder().url(String)` rejects spaces/fragments; real WebDAV hrefs are percent-encoded.
  Fix: Keep the traversal check on decoded segments but build the request from the original encoded path (or `HttpUrl.Builder.addPathSegment`).
  Acceptance: A remote file with `+`/space/UTF-8 downloads successfully; `..` (raw or encoded) still rejected.
  Confidence: Verified
  Effort: S

- [ ] P3 — `HostSource.lastModifiedOnline` is never persisted — If-Modified-Since is permanently dead; v6.9.54 changelog claim is inaccurate
  Category: maintainability
  Where: `data/repository/HostShieldRepository.kt` `updateSourceDownloadMeta` 72-85 (drops `dl.lastModified`); `BlocklistSourceCoordinator.persistSuccessfulDownload` 227-244 (doesn't pass it); read at `service/SourceDownloader.kt:72-74`
  Problem: No write path stores a downloaded `Last-Modified` (grep shows only clears), so the conditional-request branch can never send `If-Modified-Since`. Live rebuilds force-download anyway (nil runtime impact today), but the field, the header logic, and the CHANGELOG/CLAUDE.md v6.9.54 claim ("persists … Last-Modified values") are drifted, and any future consumer inherits a silently broken validator.
  Evidence: Grep of `lastModifiedOnline`: read at SourceDownloader.kt:72, defined at Entities.kt:39, cleared twice, never set from `DownloadResult.lastModified`.
  Fix: Persist `dl.lastModified` in `updateSourceDownloadMeta`, or delete the dead conditional branch + field and correct the changelog claim.
  Acceptance: A successful download stores the Last-Modified value (coordinator test), or the field/branch is removed and docs updated.
  Confidence: Verified
  Effort: S

- [ ] P3 — Secondary-screen `HostShieldBackHeader` titles mix Title Case and sentence case; overlap messages don't pluralize; crash-reports copy promises a missing export
  Category: ux
  Where: Title Case — CrashReporterScreen.kt:50 "Crash Reports", TlsFingerprintScreen.kt:53 "TLS Fingerprints", AppPrivacyScreen.kt:52 "App Privacy", ContentFilterScreen.kt:50 "Content Filtering", ConnectionLogScreen.kt:58 "Connection Log", AppExclusionsScreen.kt:60, DnsToolsScreen.kt:47, AppLogsScreen.kt:51; sentence case — "Hosts editor", "Rule tester", "DNS leak test", "Network stats", "Parental controls", "WebDAV sync"; also OverlapViewModel.kt:85/135 ("Only 2 source could be analyzed"), CrashReporterScreen.kt:75 ("until you export" with no export action)
  Problem: Half the secondary screens use Title Case and half sentence case in the same shared header — reads as unfinished after three polish passes. Overlap messages don't pluralize; crash-reports loading copy promises an export the screen lacks.
  Evidence: Direct title reads across the audited screens.
  Fix: Normalize to sentence case (matches the majority and the v6.9.26 component copy); pluralize the overlap messages; drop "export" from the crash-reports copy or add the action.
  Acceptance: All back-header titles follow one casing rule; overlap messages read grammatically for 1 vs N.
  Confidence: Verified
  Effort: S

- [ ] P3 — TLS fingerprint timeline composes up to 100 expandable cards in a non-lazy scroll column
  Category: perf
  Where: `TlsFingerprintScreen.kt:44-49` (Column + verticalScroll), 126 (`fingerprints.take(100).forEachIndexed`)
  Problem: Unlike the other list screens (LazyColumn), the timeline materializes all 100 GlassCards eagerly on every recomposition — the heaviest secondary screen to open with full history; the BY_APP tab composes every group eagerly too.
  Evidence: Whole screen is `Column(verticalScroll)`; no lazy container.
  Fix: Convert to LazyColumn with `items(key = timestamp+ja3)`, keeping header/summary as items.
  Acceptance: Opening with 100 captures shows no frame drops; scroll matches ConnectionLog.
  Confidence: Verified
  Effort: S

- [ ] P3 — No unit tests for the hosts-editor/diff, WebDAV, tile, or widget surfaces despite their failure-path bugs
  Category: testing
  Where: `app/app/src/test/java/com/hostshield` (no references to HostsEditor, HostsDiff, WebDavSyncViewModel, HostShieldTileService, StatsWidgetProvider)
  Problem: The 521-test suite covers engine/parsers/workers well, but the ViewModels where this audit found P1s (HostsEditor Result-swallowing, WebDAV dead upload, widget feed gaps) have zero coverage — which is why they survived prior passes. A thin fake-RootUtil VM test would have caught the Result-contract mismatch.
  Evidence: Grep of test/ + androidTest/ for those names returns nothing.
  Fix: Add JVM tests with fake RootUtil/WebDavSync: save-failure keeps isEdited + surfaces error; read-failure reaches the error state; WebDAV validation messages flagged as errors; widget updateWidget call-sites exercised via a seam.
  Acceptance: New tests fail against current HEAD on the HostsEditor save-failure case and pass after the P1 fix.
  Confidence: Verified
  Effort: M

### Docs / hygiene

- [ ] P3 — CLAUDE.md working notes are stale one release behind (v6.9.61 vs shipped v6.9.62)
  Category: docs
  Where: `CLAUDE.md:1-4` (Overview "Current local note: v6.9.61") and `:115-116` (Version History top entry v6.9.61)
  Problem: build.gradle.kts (6.9.62/144), README badge, CHANGELOG ([v6.9.62]), metadata/en-US/changelogs/144.txt, and the top commit all agree on 6.9.62, but CLAUDE.md's Overview and Version History have no v6.9.62 entry. CLAUDE.md is gitignored (not a shipped mismatch), but it's the canonical working-notes file the audit protocol relies on.
  Evidence: All release artifacts read 6.9.62; CLAUDE.md lines 1-4 and 115-116 read 6.9.61.
  Fix: Add the v6.9.62 entry (Spotify Ads fork + gallery URL re-audit + Room v20) to Version History and update the Overview line.
  Acceptance: CLAUDE.md Version History leads with v6.9.62 matching build.gradle/README/CHANGELOG.
  Confidence: Verified
  Effort: S

### Unaudited — needs a pass

- [ ] P3 — Instrumented (`androidTest`) suite not exercised in this audit
  Category: testing
  Where: `app/app/src/androidTest/**`
  Problem: This pass was code-trace + JVM-baseline only; the Compose UI / migration / automation-receiver instrumented tests were not run (no device/emulator) and their current pass/fail state is unverified. Not a defect claim — a coverage gap to close on a device pass. (Overlaps the logged Roadmap_Blocked "Run instrumented suite in CI" item.)
  Evidence: Baseline ran `testFullDebugUnitTest` + `lintFullDebug` only.
  Fix: On a connected device/emulator, run `connectedFullDebugAndroidTest` and log any failures as findings.
  Acceptance: androidTest results recorded; failures triaged.
  Confidence: Needs-repro
  Effort: M
