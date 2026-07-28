# Changelog

All notable changes to HostShield will be documented in this file. Detailed
release notes per version live in [`app/CHANGELOG.md`](app/CHANGELOG.md).

## [Unreleased]

### Fixed
- Blocklist preservation now keys off block sources specifically: a lone
  allowlist source downloading successfully while every block source fails no
  longer swaps in an empty blocklist. The live snapshot is preserved.
- The hosts editor now honors the write result — a failed root write shows the
  error banner and keeps the Save action instead of falsely reporting "saved",
  and a failed read shows a retry surface instead of a blank editor that could
  overwrite the real hosts file on save.
- The hosts file view no longer counts the stock `::1 localhost` line as a
  blocked entry; only `0.0.0.0`/`::` sinkhole lines are counted.
- Root hosts mode no longer disables the OS captive-portal ("Sign in to Wi-Fi")
  probe device-wide. The unrelated, never-restored `captive_portal_mode` write
  was removed from the DNS-cache flush.
- WebDAV sync now has a working "Upload backup now" action that stores a backup
  in the canonical `/HostShield/backups` directory; remote paths with `+` or
  percent-encoding are no longer corrupted. Error banners on the WebDAV and
  hosts-editor screens are driven by explicit state, not message text matching.
- The home stats widget now updates with today's blocked/queries counts.
- "Today" and rolling stat windows on Home and Stats reset at midnight instead
  of staying anchored to the day the screen was first opened.
- The home "Domains Blocked" tile now follows the live source count down when a
  source is disabled instead of sticking at the previous maximum.

## [v6.9.62] - 2026-07-28

### Added
- Forked the Spotify Ads hosts list into HostShield with its upstream MIT
  notice, then added five Spotify/Podscribe hosts confirmed through live device
  blocking. The HostShield-maintained list contains 84 unique hosts.

### Changed
- Spotify Ads now downloads from the HostShield repository. Room migration v20
  moves existing installations to the hosted URL, preserves the source toggle,
  de-duplicates a manually added hosted source, and clears stale download
  metadata.
- Re-audited every built-in and gallery source. The 45 distinct retained
  gallery endpoints all return parseable blocklist content.

### Fixed
- Repaired primary URLs for 1Hosts Lite, NextDNS CNAME Cloaking, Perflyst Smart
  TV, WindowsSpyBlocker, Stamparm Blackbook, NoCoin, and HaGeZi Gambling.
- Replaced invalid Facebook and TikTok gallery URLs with HostShield-hosted
  lists, and removed the broken or retired 1Hosts Pro, DuckDuckGo Tracker
  Radar, HaGeZi NRD7, Sinfool Pornhosts, and ZeroDot1 CoinBlocker entries.

### Distribution
- The v6.9.62 GitHub APK continues the v6.9.61 Android debug-certificate
  lineage and installs in place over v6.9.61.

## [v6.9.61] - 2026-07-28

### Added
- Added Spotify Ads as an optional built-in ADS source. It is disabled by
  default because the upstream list includes Spotify playback and update hosts.
- Database migration v19 adds the source to existing installations without
  duplicating a matching custom URL.

### Fixed
- Source health validation now reads a bounded response sample instead of
  rejecting every valid list larger than the old 5 MiB validation cap. This
  fixes false HTTP-200 `DEAD` states for GoodbyeAds and OISD Big.
- Disabled sources no longer contribute stale ERROR/DEAD badges, filters,
  unhealthy totals, repository alerts, or failure details. Disabling a source
  clears its health failure, and migration v19 repairs errors left by older
  builds.
- WorkManager constraint cancellations now propagate normally instead of being
  logged and diagnosed as blocklist-update failures.
- The AdGuard CNAME-cloak updater follows the current original-tracker list
  under the upstream `data/` path instead of requesting the retired 404 URL.

### Distribution
- The v6.9.61 GitHub APK starts a new certificate lineage. Android requires an
  APK certificate even when no production signing key is used, so this release
  uses the standard Android debug certificate. Export a HostShield backup if
  needed, uninstall v6.9.16 or earlier, then install v6.9.61. The uninstall
  clears the previous app data.

## [v6.9.60] - 2026-07-22

### Added
- Blocking profiles now apply their per-profile `source_ids`: activating a
  profile narrows the active blocklist to its configured block sources
  (allowlists always apply); deactivation rebuilds to all enabled sources.
- Settings controls for blocklist auto-update + interval, Wi-Fi-only updates,
  the ongoing protection notification, and DNS-log retention (previously
  collected in state with working setters but no UI).
- PCAP connection-log export emits IPv6 packets — v6 destinations were dropped.

### Fixed
- Threat-intel partial refresh carries forward a failed feed's last-good
  domains/CIDRs instead of dropping them from enforcement for the outage window.
- `RootDnsLogger` attribution maps (`hostnameUidMap`/`pendingUidLookup`) are
  bounded via periodic eviction, and its async iptables teardown is serialized
  against a subsequent start so a quick toggle can't delete the fresh NAT
  redirect.
- `$important` block rules now outrank a plain `@@` exception in the same source
  (an `@@...$important` allow still wins).

### Security
- The exported launcher `SHORTCUT_TOGGLE` action is honored only when the launch
  is system-delivered or comes from the app/a system launcher — a third-party
  app can no longer toggle protection.

## [v6.9.59] - 2026-07-22

### Fixed (deep engineering + security + UX audit)
- **Per-app firewall never applied**: `IptablesManager` split the apply script so
  the embedded chain-clear (delete) step ran after the chains were created, so
  every rule failed with "no chain by that name". The script now runs as one
  ordered clear->create->populate->hook job.
- **DNS proxy plaintext leak**: with DoH/DoT enabled, a transient encrypted
  resolver failure fell through to plaintext UDP against public resolvers. Proxy
  mode now returns SERVFAIL (fail-closed), and its receive buffers grew 512->4096
  bytes so EDNS answers are no longer truncated.
- **IPv6 DNS broken**: `wrapResponseV6` emitted a zero UDP checksum, which
  receivers must discard — the mandatory checksum is now computed.
- **Offline refresh emptied the blocklist**: `BlocklistSourceCoordinator` now
  preserves the live snapshot when every enabled source fails to download, and
  persists source metadata via targeted column updates that no longer clobber
  concurrent user edits.
- **Threat-intel bypass**: re-blocking is skipped only for user-originated allow
  decisions, so a downloaded source allowlist can no longer whitelist a malware
  domain past URLhaus/Spamhaus. TCP-DNS RST blocking now computes a valid,
  in-window ACK.
- **Live config reload**: content-filter, safe-search, threat-intel, and
  parental toggles apply without restarting protection; LAN DNS rebinds when its
  port or client policy changes; the threat-intel IOC cache is written
  atomically.
- **Backup/restore fidelity**: wildcard (`*.`) and regex rules survive a
  round-trip, sources/profiles de-duplicate, restored redirect IPs are
  validated, and the hosts `.bak` backup is no longer clobbered on re-apply.
- **Privacy scoring**: the permission dimension was dead (masking with
  `PackageManager.PERMISSION_GRANTED`, which is 0) — now uses
  `REQUESTED_PERMISSION_GRANTED`.
- **Workers/preferences**: automation double-`finish()` crash, missing
  DNS_PROXY dispatch in scheduled blocking, pause/resume re-enabling disabled
  protection, a maintenance channel downgrading alerts, all-time burst alerts,
  disabled-source health downloads, auto-backup interval reset, non-atomic
  pin/unpin, and eager Keystore reads on the caller's thread all fixed.
- **Parser**: unsupported adblock modifiers are skipped (not globalized),
  multi-hostname hosts lines keep every host, `$denyallow`/`$badfilter` scoping
  corrected, bloom second-hash forced odd.
- **UI/UX/a11y**: launcher toggle drives the real service; bottom system-bar
  inset restored on sub-screens; light/dynamic-theme contrast on destructive
  buttons, checkmarks, and unselected chips; scrollable log detail sheet;
  guarded reputation links; reduced-motion support; localized Glance widget with
  a correct block-rate bar; Play-flavor `<queries>` so app lists aren't empty.
- Util: ASCII-only QR hostnames, non-draft update selection, ccSLD-aware domain
  age, IPv4 octet range checks in PCAP, salted evidence-export redaction with
  cache-dir hygiene, chunked dex scanning to avoid OOM.

### Added
- Added LocaleConfig readiness guardrails: top Home/Settings/DNS/QR copy now
  routes through string resources, the default locale is declared through
  `resources.properties`, and release docs state that non-English locales are
  deferred until complete translations are available.
- Added a Material 3 adaptive navigation shell that switches top-level
  navigation from bottom bar to rail on non-compact large-screen surfaces, with
  connected Compose coverage for Android 16 foldable, tablet, Chromebook, and
  compact fallback breakpoints.
- Productized the LAN DNS server behind an explicit default-off Settings gate,
  foreground-service notification, private-client default, API 37 local-network
  permission readiness, boot restore, and release-doc guardrails.
- Added a checked local tracker attribution dataset with per-row provenance so
  Stats can resolve common tracker domains to owner/category offline and
  deterministically.
- Added a local protection-resilience matrix runner and connected smoke test for
  VPN recovery, Private Space/work-profile, battery, and diagnostic-event
  release evidence.
- Added bounded evidence JSONL export for DNS and firewall records with schema
  metadata, redaction controls, filters, chunk metadata, and share/save/discard
  destination handling.
- Scoped AdGuard `$app=`, `$client=`, and `$ctag=` source rules now emit parse
  diagnostics when skipped so unsupported scoped rules are never silently
  globalized.
- DNS logs now expose a counted threat-review queue, and diagnostic ZIPs include
  feed/type false-positive summaries without raw IOC details by default.

### Changed
- Refreshed the compatible build/test dependency batch: Gradle 9.6.1,
  AndroidX Core 1.19, KSP 2.3.9, Hilt 2.60, AndroidX Hilt 1.4,
  Compose BOM 2026.06.01, serialization 1.11, Vico 3.2.3, JSON 20260522,
  and current test libraries; Vico chart adapters now use the 3.x Compose API
  and the lint baseline no longer hides fixed dependency warnings.
- Prepared the build for API 37 by moving compileSdk to 37 and adopting
  AndroidX Lifecycle 2.11 while keeping targetSdk 36 behavior unchanged.
- Blocklist lookups now use a snapshot-local Bloom pre-check to fast-reject
  cold negative domains before trie traversal while preserving regex, DNS-type,
  allowlist, and `www.` fallback decisions.
- Extracted secondary-screen ViewModels into dedicated files so screen
  composables no longer own Hilt ViewModel classes.
- DNS stamp parsing now exposes explicit capability diagnostics so DNSCrypt,
  DoQ, ODoH, and relay stamps cannot be mistaken for production-active
  resolver imports.
- Blocklist rebuilds from VPN startup, periodic updates, profile schedules, and
  Home apply now share one coordinator for source downloads, allow/user merge
  rules, DoH bypass entries, holder swaps, and source metadata.

### Security
- Remote DoH bypass list updates now require a matching canonical payload hash
  and release-key signature before the app stores any fetched policy entries.
- The release documentation gate now verifies the signed DoH bypass manifest so
  tampered, unsigned, or stale-format remote policy cannot ship unnoticed.
- User allow rules and paused protection now bypass threat-intel re-blocking for
  domain and resolved-IP checks, so false-positive recovery does not require
  disabling malware feeds.

## [v6.9.58] - 2026-07-05

### Added
- Logs, Sources, Apps, and Firewall dense lists now expose persisted saved filters, filtered empty states, and accessible top/middle/end jump controls for large local datasets.
- Added JVM coverage for saved dense-list filter save/apply/clear behavior and large-font Compose coverage for the shared dense-list controls.

## [v6.9.57] - 2026-07-01

### Fixed
- AdGuard `$dnstype=` block and allow rules now persist through source rebuilds
  as qtype-aware policy instead of being silently discarded.
- VPN, DNS proxy, root DNS, and local DNS decisions now pass numeric DNS query
  types into blocklist evaluation, enforcing A, AAAA, and negated type rules
  without globalizing scoped rules.

## [v6.9.56] - 2026-07-01

### Fixed
- Release builds now document DoQ and WireGuard DNS as debug-only experimental
  engines that are hidden in release Settings and forced off in production
  routing.
- Release documentation checks now verify the Settings and VPN debug gates and
  reject release-effective claims for experimental DNS transports.

## [v6.9.55] - 2026-07-01

### Changed
- Removed current Local DNS Server / "Portable Pi-hole" feature claims from the
  public README until the feature has a production Settings, lifecycle,
  permission, and status path.
- Release documentation checks now fail if current release docs reintroduce the
  unwired Local DNS Server claim.

## [v6.9.54] - 2026-06-30

### Fixed
- Periodic, scheduled-profile, and VPN startup blocklist rebuilds now use one
  forced full-snapshot source coordinator so conditional 304 cache validation
  cannot produce an incomplete in-memory blocklist after process start.
- Successful block and allowlist downloads now persist source counts, ETags,
  Last-Modified values, byte sizes, last-success timestamps, health state, and
  entry deltas consistently across rebuild paths.

## [v6.9.53] - 2026-06-27

### Added
- DNS cache misses now use an in-flight single-flight coordinator keyed by
  domain, query type, and active upstream route, so concurrent identical
  queries share one resolver call while each caller receives a response patched
  with its own DNS transaction ID.

### Fixed
- Serve-stale and prefetch background refreshes now refresh DNS cache state
  without sending duplicate DNS responses back to the original app packet.
- Release documentation checks now enforce the local-only release process and
  reject checked-in GitHub Actions workflows instead of requiring stale workflow
  text.
- Gradle/Kotlin build heap settings now match the current release compile
  footprint so local release artifact builds do not crash the daemon.

## [v6.9.52] - 2026-06-20

### Fixed
- Dynamic color (Material You) now derives custom palette tokens from the
  wallpaper-based color scheme, preventing visual conflicts between Material
  surfaces and custom-token UI when system colors are enabled.
- TCP DNS data packets with unparseable DNS payloads (EDNS, zone transfers,
  fragmented) are now dropped silently instead of receiving RST, fixing
  breakage of legitimate TCP DNS for allowed domains. SYN packets still
  receive RST to reject blocked connections immediately.
- DoT resolver now tries all configured providers on failure instead of
  giving up after the primary, matching DoH's multi-provider failover.

## [v6.9.51] - 2026-06-20

### Security
- `DnsVpnService`: added missing `@Volatile` to `useWireGuard`,
  `dnsTrapEnabled`, and `blockResponseType` fields that are written on IO and
  read from the packet loop. `useWireGuard` without volatile could cause a
  DNS leak to plaintext if the JVM cached a stale value.
- `DotResolver`: SPKI pin verification now fails closed when no pins are
  configured for a provider, matching the DoH pinning behavior.

### Fixed
- Light mode was completely broken across all 30 screens — each used
  hardcoded `Color.Black` (#000000) instead of the theme-aware palette
  `Black` which maps to light gray in light mode.
- High-contrast AMOLED toggle is now hidden in light mode since it only
  affects dark palette variants.

## [v6.9.50] - 2026-06-20

### Added
- Tracker company attribution view showing top tracking companies by blocked
  query count with percentage breakdown in Stats.
- Top allowed domains (7d) card in Stats alongside existing top blocked.
- LocaleConfig generation (`generateLocaleConfig`) for Android per-app language
  preferences readiness.
- VPN coexistence guide for Tailscale, Mullvad, and WireGuard in README FAQ.

## [v6.9.49] - 2026-06-20

### Added
- Light theme option with Dark/Light/System mode selector in Settings.
- Per-app DNS query and block count attribution (24h/7d) in Stats.
- Robolectric test infrastructure for Android-dependent unit tests.
- Turbine + MockK ViewModel Flow tests for Rules, Sources, Logs, and Firewall
  ViewModels (24 assertions).

### Changed
- Extracted ViewModels from 6 large screen files (Stats, Logs, Sources,
  Firewall, DnsTools, Rules) into dedicated ViewModel files for testability.

## [v6.9.48] - 2026-06-19

### Added
- Dynamic Color / Material You theme support on Android 12+ (opt-in in
  Settings > Appearance as "System colors").
- DNS latency percentile tracking (p50/p95/p99) in the Stats DNS Latency card.
- Log cleanup notification showing purged entry count and retention setting.
- Curated blocklist source metadata: license, homepage, and review date for
  all 51 sources with catalog test validation.

### Security
- Defense-in-depth DoH/DoT certificate pin-sets in `network_security_config.xml`
  alongside existing OkHttp-level pins.

### Changed
- Enabled predictive back gesture support (`enableOnBackInvokedCallback`) for
  Android 14+ system back animations.

## [v6.9.47] - 2026-06-19

### Security
- WebDAV sync now requires HTTPS URLs consistently in the UI and service layer,
  rejects embedded credentials, and fails closed for malformed server URLs.

### Fixed
- DNS benchmark, DNS leak testing, rule testing, overlap analysis, QR import,
  and firewall root actions now clear running states reliably and show calm,
  actionable failure messages instead of failing silently.
- Import paths now normalize and reject malformed allowlist, AdAway redirect,
  and Pi-hole exact-domain entries before they can pollute local rule state.

## [v6.9.46] - 2026-06-19

### Changed
- Extended premium UI polish across diagnostics, firewall, parental controls,
  QR configuration, WebDAV sync, hosts tools, blocklist gallery, and app
  activity with shared headers, calmer state feedback, and clearer empty
  guidance.

## [v6.9.45] - 2026-06-19

### Changed
- Refined secondary Android UI surfaces with shared back headers, segmented
  controls, responsive empty/loading states, and smoother app-exclusion loading.

## [v6.9.44] - 2026-06-18

### Changed
- Launcher icon resources now live in the unqualified adaptive-icon directory
  and include a monochrome layer for Android themed icons.

## [v6.9.43] - 2026-06-18

### Changed
- Widget secondary labels now use at least 11sp text for better launcher
  readability and accessibility.

## [v6.9.42] - 2026-06-18

### Fixed
- Firewalled-app count labels now use Android plural resources, so single-app
  and multi-app summaries read correctly.

## [v6.9.41] - 2026-06-18

### Security
- Hardened the WebRTC leak-test WebView by disabling file/content access,
  blocking navigation, bounding bridge payloads, parsing JSON safely, and
  removing the JavaScript bridge after each probe.

## [v6.9.40] - 2026-06-18

### Fixed
- Widget metadata now keeps Android 12+ target-cell sizing in `xml-v31`
  resources while the base widget definitions stay valid for API 26-30.

## [v6.9.39] - 2026-06-18

### Fixed
- Moved the API 27-only light-navigation-bar theme attribute out of the base
  API 26 resource set, removing the last baseline-suppressed `NewApi` finding.

## [v6.9.38] - 2026-06-18

### Fixed
- Older Android releases now skip API 29-only VPN status and Quick Settings
  subtitle calls instead of relying on exception handling around unavailable
  platform methods.

## [v6.9.37] - 2026-06-18

### Changed
- Updated the lint-reported stable Compose BOM and Tink refreshes while
  keeping Lifecycle pinned to the current compile SDK line.

## [v6.9.36] - 2026-06-18

### Changed
- Home stats and source-management labels now use the existing Android string
  resources instead of hardcoded Compose text, keeping the UI localization
  surface complete and lint-cleaner.

## [v6.9.35] - 2026-06-18

### Fixed
- Dropped-query warnings now use Android plural resources, so single-query and
  multi-query buffer-overflow messages are grammatically correct and lint-safe.

## [v6.9.34] - 2026-06-18

### Fixed
- Room enum converter fallbacks now emit bounded warning logs when corrupted
  or future enum values are read, preserving compatibility while surfacing
  database drift early.

### Tests
- Added converter fallback coverage for blank, lowercase, and long corrupted
  enum values.

## [v6.9.33] - 2026-06-18

### Performance
- Added Room schema version 18 with a covering `dns_logs(app_package, hostname)`
  index so per-app domain aggregation remains responsive on large DNS logs.

### Tests
- Extended the Android migration test fixture to assert the new index exists
  after every supported historical migration path.

## [v6.9.32] - 2026-06-18

### Security
- DNS cache hits, stale-cache responses, and encrypted fail-closed stale
  fallbacks now run the same post-forward CNAME and threat-intel IP checks as
  live upstream responses.

### Changed
- Threat-intel false-positive recovery now has a dedicated Logs review filter
  and clearer domain/app scope labels; app-scoped threat allows suppress
  threat-intel checks only for that allowed app/domain pair.

## [v6.9.31] - 2026-06-18

### Changed
- Settings exports now share one destination artifact model for generated
  content and cached files, including correct filenames, MIME types, privacy
  copy, Save As support for diagnostic ZIPs, and tested file/content streaming.

## [v6.9.30] - 2026-06-17

### Security
- Local DNS server abuse controls now enforce a global per-window query budget
  alongside the existing per-client cap, preventing spoofed source rotation
  from bypassing LAN DNS throttling.

## [v6.9.29] - 2026-06-17

### Security
- User-supplied blocklist regex matching now runs through a per-rule execution
  deadline and disables patterns that time out or overflow the regex stack.

### Fixed
- Blocklist decision-cache entries are tied to the snapshot that produced them,
  preventing stale cached decisions from surviving concurrent blocklist swaps.

## [v6.9.26] - 2026-06-16

### Changed
- Added shared premium Compose primitives for screen headers, square icon
  actions, selected filter controls, and compact inline actions.
- Updated Home search/history, DNS Logs, Sources, Rules, and Settings to use
  consistent spacing, 44-48dp touch targets, selected states, and row/action
  treatment.

### Fixed
- DNS log expanded actions now wrap on compact screens instead of crowding
  package/domain text.
- Sources now exposes the curated gallery from the header even when sources
  already exist, and source rows include category glyphs plus section counts.
- Settings provider selectors and section headers now match the rest of the
  product surface.
- Source failure rows now avoid raw HTTP/download fragments, and experimental
  DNS descriptions no longer clip on compact Settings rows.
- Release-doc validation now checks the pinned OSV scanner action SHA plus
  version comment instead of the old mutable tag.

## [v6.9.25] - 2026-06-16

### Added
- Search history now persists in backup schema v2 export/import.
- Connected backup/restore roundtrip test exercising real utility paths.

### Fixed
- Added diagnostic logging to ~15 silent empty catch blocks in DNS
  forwarding prefetch, stability flush, app resolution, DoH bypass
  refresh, CNAME cloak refresh, network stats, and dumpsys polling.
- Removed 5 unused string resources and 2 unused Glance widget XML files.
- Widget `baselineAligned=false` for layout performance.
- Annotated dead DoH3 branch with suppression for dead-code clarity.

## [v6.9.24] - 2026-06-16

### Changed
- HomeViewModel: collapsed 9 preference observer coroutines into a single
  `combine()` flow and extracted shared `downloadAndBuildBlocklist()` from
  three near-duplicate blocklist-building methods (~160 LOC reduction).
- Lint baseline burned down: AutoboxingStateCreation, UseKtx, TypographyEllipsis,
  UnnecessaryArrayInit, and ObsoleteSdkInt warnings fixed (14 fixes, baseline
  reduced from 72 to 58 warnings).

## [v6.9.23] - 2026-06-16

### Fixed
- LocalDnsServer now fails closed when encrypted DNS (DoH/DoT) is configured
  but fails, returning SERVFAIL instead of silently falling back to plaintext
  UDP — matching the VPN mode fail-closed policy.
- BlocklistHolder trie walk no longer breaks early on wildcardAllow, so a
  more-specific wildcardBlock at a deeper level correctly overrides a shallower
  wildcardAllow (most-specific-wins semantics).
- WebDavSync rejects path traversal segments (`..`/`.`) in server-supplied
  `<d:href>` values and in `buildUrl`, preventing a malicious WebDAV server
  from requesting arbitrary paths.
- Pi-hole CSV regex import now validates imported patterns against the same
  length cap and nested-quantifier rejection used at blocklist compile time,
  preventing ReDoS from pathological imported regexes.
- Removed duplicate lowercase automation action aliases from the manifest,
  halving the exported receiver attack surface.

### Changed
- CI and release workflow actions pinned to SHA digests instead of mutable tags.
- Release workflow now runs `lintFullDebug` before building artifacts.
- DNS log deduplication, filtering, and sorting moved from LogsScreen composable
  `remember{}` to ViewModel `StateFlow`, eliminating main-thread recomposition
  cost for up to 2000 log entries.

## [v6.9.22] - 2026-06-16

### Added
- Threat-intel blocked log details now include review actions to create a
  global domain allow rule or, when app attribution is available, an app-scoped
  DNS allow rule that recovers only the affected app/domain pair.

## [v6.9.21] - 2026-06-16

### Added
- Stats now includes local threat-intel impact analytics with per-feed 24-hour
  and 7-day block counts, last matched time, top affected domains/apps, and a
  compact 7-day feed trend.

## [v6.9.20] - 2026-06-16

### Fixed
- The main app scaffold now applies explicit system-bar window insets, keeping
  top-level and sub-screen content out from under Android 15+ status and
  navigation bars while preserving edge-to-edge rendering.

## [v6.9.19] - 2026-06-16

### Fixed
- Theme palette tokens now resolve through a per-composition palette instead
  of mutating top-level global state, so high-contrast and accent variants can
  coexist safely in previews, widgets, and nested themed surfaces.

## [v6.9.18] - 2026-06-16

### Fixed
- DNS log temporary-allow actions now schedule their re-block through
  WorkManager instead of a ViewModel coroutine delay, so process death and Doze
  no longer turn a temporary allow into a persistent bypass.

## [v6.9.17] - 2026-06-16

### Security
- Legacy unsalted SHA-256 parental PIN hashes are now detected on app launch,
  force the parental-controls PIN upgrade flow, and are rewritten to the
  current KDF after successful PIN verification.

## [v6.9.16] - 2026-06-15

### Fixed
- Periodic blocklist refresh no longer silently empties the blocklist when all
  sources return HTTP 304 Not Modified; the rebuild is skipped entirely when
  nothing changed upstream.
- VPN service blocklist rebuild now includes user regex rules, matching the
  periodic worker behavior. Previously regex block/allow rules were silently
  ignored until the next background worker run.
- Per-app DNS rule engine now uses atomic reference swap instead of
  clear-then-putAll, eliminating a brief window where all per-app rules were
  dropped during cache reload.
- EDE (Extended DNS Error) extra-text JSON is now properly escaped so block
  reasons containing quotes or backslashes produce valid JSON.
- VPN stability metrics (dropped queries, fd errors, rebuild count) are now
  restored to their counters when the database write fails instead of being
  silently lost.
- VPN restart on network change now cancels the recovery monitor and DNS config
  observer coroutines before restarting, preventing accumulated leaked
  coroutines on repeated network transitions.
- DNS cache now honors upstream TTLs above 300 seconds instead of silently
  capping all records to 5 minutes; the 300-second default is only used as a
  fallback when no TTL could be parsed from the response.
- Hourly blocked/total/latency chart queries now use SQLite `localtime` instead
  of raw UTC arithmetic so hour labels match the user's timezone.
- Threat-intel domain map now uses atomic reference swap instead of
  clear-then-putAll, eliminating a brief window during refresh where all
  domain-based threat-intel blocking was bypassed.
- GeoIP lookup cache now evicts entries when the cache exceeds 4096 IPs instead
  of growing without bound for the lifetime of the VPN service.

### Security
- Added legacy `fullBackupContent` rules for API 26-30 devices so cloud backup
  exclusions (blocklists, DNS logs, credentials) apply on Android 11 and below,
  not just Android 12+.
- Restricted FileProvider paths from entire `cache`/`files`/`external` trees to
  only the subdirectories actually used (diagnostics, pcap, crashes, backups).

### Build
- Removed dead ProGuard keep rule for non-existent `NetworkChangeReceiver`.
- Removed overly broad Compose R8 keep rules that prevented tree-shaking;
  Compose ships its own consumer ProGuard rules.

## [v6.9.15] - 2026-06-15

### Fixed
- Blocklist Gallery now handles loading, unavailable, and empty states instead
  of rendering a silent blank list while curated sources load or fail.
- Blocklist Gallery add failures now log the underlying error and show a clear
  error state instead of being indistinguishable from success messaging.

### UX
- Blocklist Gallery success and failure messages now use the shared status
  banner surface for consistent dark-mode, accessibility, and dismissal
  behavior.
- Home warning dismiss controls, the VPN recovery restart action, automation
  command copy, TLS/crash clear actions, parental message dismissal, and app
  privacy expand controls now use larger touch targets.
- App privacy and content filter rows now truncate long labels/descriptions
  safely around badges, switches, and action buttons.

## [v6.9.14] - 2026-06-15

### Fixed
- Home protection, DNS tools, DNS leak test, hosts editor/diff, firewall sync,
  backup/import/export, and update-check failures now use stable recovery copy
  instead of raw exception text.
- DNS tools lookup, batch lookup, ping, and traceroute failures now preserve
  details in Logcat while showing concise user-facing result text.
- Hosts editor/diff and firewall sync failures now preserve diagnostic details
  in Logcat and keep visible messages actionable.

### UX
- Shared status-banner actions and empty-state actions now reserve larger hit
  targets.
- Repeated firewall Wi-Fi, mobile, screen-off, background, and metered controls
  now use larger touch targets in dense app rows.
- Blocklist gallery add buttons now use larger touch targets, and gallery card
  labels/descriptions truncate safely on small screens.

## [v6.9.13] - 2026-06-15

### Fixed
- Redirect custom rules now require a valid IPv4 or IPv6 target before they can
  be saved, matching backup/restore validation.
- Regex rule validation now shows stable product copy instead of exposing raw
  parser exception text.
- Source and DNS log error banners now use user-facing recovery copy while
  preserving detailed exception data in logs.
- Source failure rows now summarize network, timeout, DNS, certificate, and
  empty-source failures without surfacing raw exception strings.

### UX
- Rules, Sources, and DNS Logs now truncate long hostnames, source labels,
  descriptions, app labels, and detail values where they would otherwise crowd
  badges or action buttons.
- DNS log query-type filters wrap on narrow screens instead of overflowing.
- Rule/source add buttons, delete buttons, log selection checkboxes, and the
  log-detail pin action use larger touch targets.
- Rule and source dialogs now provide clearer keyboard actions and validation
  feedback.

## [v6.9.12] - 2026-06-15

### Fixed
- WebDAV credential saving no longer clears an existing saved password when the
  password field is left blank; connection tests also reuse the saved password
  without exposing it in the UI.
- QR configuration sharing now fits generated codes inside narrow cards and
  reports import/export results through the shared accessible status banner.
- Rule testing now supports keyboard search submission, disables empty test
  actions, and truncates long domains/match details instead of crowding result
  badges.
- PCAP, diagnostic, and CSV export states now use larger action targets, rounded
  file-size display, clearer error coloring, and user-facing failure messages.

### Accessibility
- Added IME padding to Settings, WebDAV, QR sharing, and rule testing flows.
- Added result-row semantics for rule-test outcomes and improved text-field
  labels/keyboard options in form-heavy polish screens.

## [v6.9.11] - 2026-06-15

### Fixed
- DoH provider selection now honors the user-selected provider as the primary
  resolver even after latency data exists; measured latency is used only to rank
  failover providers after the selected resolver fails.
- Settings PCAP export now exposes all, DNS-only, and firewall-only export modes
  with share/save/discard actions and visible failure feedback instead of
  Logcat-only share errors.
- Diagnostic export dismissal now deletes the generated temporary ZIP.

### Build
- Moved Gradle plugin and dependency versions into `app/gradle/libs.versions.toml`.

### Docs
- Release documentation checks now validate the version catalog, targetSdk,
  `systemExempted` foreground-service state, fail-closed encrypted-DNS anchors,
  disabled embedded DoH3 posture, and removed offline GeoIP claims.
- Updated current docs and metadata to match the live bounded ipapi.co GeoIP
  implementation.

## [v6.9.10] - 2026-06-14

### Fixed
- **DNS-over-HTTPS now actually works (GitHub #1 root cause).** Two stacked bugs
  made DoH fail for *every* provider, so the app silently fell back to plaintext —
  which is why enabling DoH still showed a plaintext resolver on leak tests:
  1. **Stale/incorrect certificate pins.** `DohPinManifest` pinned the wrong CAs
     entirely (e.g. DigiCert G2 for Cloudflare, which actually uses SSL.com), so
     every provider failed pinning with `SSLPeerUnverifiedException`. Pins now
     target each provider's real **intermediate + root CA** (verified against the
     live chains), which also survive the ~90-day leaf-cert rotation that would
     otherwise re-break pinning. Manifest bumped to v2.
  2. **Broken response read.** `readBoundedBody` used `readByteArray(cap+1)`, which
     reads *exactly* that many bytes and threw `EOFException` on every normal
     (~100-byte) DoH response. Replaced with a bounded `request()`/`readByteArray()`
     that reads *at most* the cap without throwing on short streams.
  Verified on-device (Galaxy S22 Ultra, Android 16): queries now resolve via
  `DoH:CLOUDFLARE` instead of plaintext `8.8.8.8`.
- **Encrypted DNS no longer leaks to plaintext (GitHub #1).** When DoH, DoT,
  DoQ, or WireGuard is enabled, a resolver failure now fails closed — serving a
  stale cached answer if available, otherwise returning SERVFAIL — instead of
  silently falling back to plaintext UDP against the hardcoded public upstream
  (8.8.8.8/1.1.1.1). Previously an enabled-but-failing DoH provider (e.g. Quad9)
  would leak queries in the clear to Google, which is what `dnscheck.tools`
  reported. DoQ/DoT/WireGuard still chain to DoH when it is enabled (remaining
  encrypted); only the terminal plaintext fallback was removed.
- **DNS provider and upstream changes now apply without restarting protection
  (GitHub #1).** `DnsVpnService` observes the DoH/DoT/DoQ enable flags,
  providers, and custom upstream servers and reloads them live (flushing the DNS
  cache), so changing the resolver in Settings takes effect immediately instead
  of appearing stuck on whatever was selected when protection started.
- **Screen-state receiver now registers as `RECEIVER_NOT_EXPORTED`.** The
  context-aware firewall's screen on/off receiver was registered without an
  export flag; it is now explicitly not-exported (via `ContextCompat`),
  satisfying the Android 13+ requirement and closing an unnecessary IPC surface.

### Internal
- Added Android lint as a build gate (`lint {}` with `lint-baseline.xml` so only
  new issues fail) and a new `ci.yml` workflow that runs unit tests + lint on
  every push to `main` and every pull request — previously CI ran only on
  release tags.
- Pinned compact number formatting in the Stats screen and Glance widget to
  `Locale.US` so query/percentage displays don't break under locales with
  comma decimal separators.

## [v6.9.9] - 2026-06-14

### UI
- Replaced the launcher icon assets with the new HostShield shield artwork
  across legacy and adaptive Android icon densities.
- Enabled the built-in AdAway Default and StevenBlack Unified host sources by
  default, including a one-time upgrade migration for existing installs.

## [v6.9.8] - 2026-06-14

### UI
- Removed the bottom Home dashboard "Root not detected" banner so non-root DNS
  filtering keeps the dashboard space focused on active protection details.
- Made the Settings "View on GitHub" action explicitly open the HostShield
  repository page without crashing on devices that do not have a browser.
- Simplified the Sources header to show only the add-source button and removed
  the extra header shortcuts.

## [v6.9.7] - 2026-06-14

### UI
- Added a clear rotating arc, trailing sweep, and breathing halo to the active
  protection orb so the dashboard visibly communicates that protection is running.

## [v6.9.6] - 2026-06-14

### UI
- Live DNS activity rows now turn red when their hostname is manually blocked
  from the DNS log, even if the original query was allowed.
- Accent color preferences now apply to the active app theme instead of only
  updating the selected swatch in Settings.

### Reliability
- Reduced blocklist rebuild memory use by keeping exact domains in the hash-set
  fast path only, avoiding duplicate trie allocation on low-memory devices.

## [v6.9.5] - 2026-06-14

### UX
- Deferred Android notification permission until the user activates protection,
  so fresh installs can read onboarding before the system dialog appears.
- Tightened the DNS resolver onboarding selector so all resolver options remain
  visible with the fixed Continue action on tall phones.

## [v6.9.4] - 2026-06-14

### Security
- Release artifact tasks now fail closed when signing credentials are missing;
  local debug-keystore signing requires `HOSTSHIELD_ALLOW_DEBUG_RELEASE_SIGNING=true`.
- Hardened import, backup restore, QR config, automation DNS, threat-intel,
  WebDAV, GeoIP, update-check, crash-report, and DEX-scan input paths with
  size bounds and stricter validation.

### Reliability
- Room enum converters now tolerate unknown persisted category/rule values.
- Diagnostic command collection now has output caps and timeouts.

### Accessibility
- Expanded compact dismiss, delete, refresh, copy, and toggle controls across
  secondary screens to larger touch targets.

## [v6.9.3] - 2026-06-13

### Security
- Added a threat-intel feed health dashboard to Stats with per-feed freshness,
  HTTP status, entry counts, byte counts, SHA-256 prefixes, failure state, and
  manual refresh.
- Added redacted threat-feed health summaries to diagnostic exports.

## [v6.9.2] - 2026-06-13

### Security
- Removed the embedded Cronet dependency and disabled the DoH3 transport while
  no maintained non-vulnerable Cronet artifact is available.
- Kept encrypted DNS on the fail-closed pinned OkHttp DoH/DoT production paths.

### Release
- Updated Cronet posture and release provenance tooling to treat the
  no-bundled-Cronet state as the passing security posture.

## [v6.9.1] - 2026-06-13

### UI
- Enabled Android debug pseudolocales and added an RTL/pseudo-expanded Compose
  scaffold for reusable HostShield surfaces.
- Moved touched Home, Settings, Protection, and QR configuration copy through
  string resources to start the localization-safe layout path.

### QR Config
- Kept QR config export/import validation covered by focused JVM tests while
  preserving preview-and-apply behavior for rules, HTTPS sources, custom DNS,
  and enabled DoH settings.

## [v6.7.0] - 2026-06-13

### Platform
- Bumped targetSdk from 35 to 36 (Android 16). The app already handles
  edge-to-edge enforcement, systemExempted foreground-service types,
  inexact alarms, and WorkManager constraints correctly. No behavioral
  regressions expected.
- Android 16 behavior-change checklist: edge-to-edge opt-out removal
  (already using enableEdgeToEdge + systemBarsPadding), FGS tightening
  (already using systemExempted with runtime type gating), JobScheduler
  quota (using WorkManager with proper backoff), exact alarm restrictions
  (only inexact alarms used).

## [v6.6.9] - 2026-06-13

### Security
- Bounded VPN write-channel capacity to 512 packets with explicit overflow
  counting. Prevents unbounded memory growth under DNS flood or stalled TUN
  writer. Dropped responses are tracked in the existing diagnostics counter.
- Added PrivacyLog utility that gates sensitive log output behind
  BuildConfig.DEBUG. Release builds no longer emit raw hostnames, IP
  addresses, CNAME chains, DNS server addresses, package names, or rule
  decisions via android.util.Log. ~65 sensitive calls across 18 files
  replaced.
- Pinned Gradle wrapper distribution SHA-256 checksum and added
  gradle/actions/wrapper-validation to the release CI pipeline.

### Legal
- Unified project license on GPL-3.0. Removed the conflicting root MIT LICENSE
  and duplicate `app/LICENSE`, updated README badges and license sections.

## [v6.6.8] - 2026-06-11

### Security
- Added bounded streaming reads for blocklist sources, remote rule sync, and
  remote DoH-bypass manifests so oversized responses fail before full
  materialization.
- Added CycloneDX SBOM generation, OSV vulnerability scanning, and release
  provenance hashes for SBOM/report artifacts, with HIGH/CRITICAL findings
  blocked unless reviewed in an expiring allowlist.
- Added the hosted remote DoH-bypass manifest, release validation for its JSON
  schema, and cache-only merging of fetched entries into blocklist rebuilds.
- Rejected `http://` custom source URLs consistently in the Add Source flow and
  downloader validation so Android cleartext policy failures are deterministic.
- Added DNS-log decision provenance for blocklist, allowlist, user-rule,
  DoH-bypass, Safe Search, content/parental, threat-intel, and CNAME-cloak
  verdicts, including a v16 migration for old logs.

### Release
- Added an Android 16 KB page-size release gate that verifies APK zip alignment
  and Play AAB `PAGE_ALIGNMENT_16K` bundle config, then records the evidence in
  release provenance.

### Root
- Added KernelSU and APatch root-framework detection for diagnostics, expanded
  systemless hosts-module path detection, and documented bindhosts/systemless
  hosts requirements for root hosts editing.

### Automation
- Added connected receiver coverage for status replies, SET_DNS, SET_PROFILE,
  legacy PAUSE 0 resume, rate limiting, audit rows, and manifest permission
  enforcement.
- Requested HostShield's own signature automation permission so in-app status
  result receivers can observe protected automation result broadcasts.

### QR Config
- Completed QR config import with preview-and-apply persistence for rules,
  HTTPS-only sources, custom upstream DNS, and enabled DoH settings.
- Added QR decode safety caps for pasted payload size and decompressed JSON
  size, plus unit coverage for encode/decode/import planning.

## [v6.6.7] - 2026-06-11

### Safe Search
- Made DNS-level Safe Search enforcement query-type-aware for A, AAAA, HTTPS,
  SVCB, and other query types.
- Added Google country/region domain coverage and canonical safe-endpoint
  resolution with bundled fallbacks.

## [v6.6.6] - 2026-06-11

### Security
- Hardened the LAN Local DNS server against open-resolver abuse by rejecting
  public clients by default, adding per-client throttling, truncating oversized
  UDP answers with TC=1, and loading configured custom/DoT/DoH upstreams before
  plaintext fallback.

## [v6.6.5] - 2026-06-11

### Sync
- Made periodic threat-intel and source-health refresh workers honor the
  existing Wi-Fi-only sync preference.
- Rescheduled existing periodic WorkManager registrations when the Wi-Fi-only
  setting changes so stale constraints are replaced.

## [v6.6.4] - 2026-06-11

### Threat Intel
- Hardened compromised-IP feed parsing for whitespace-separated IPv4/CIDR
  tokens, invalid token rejection, duplicate suppression, and degraded partial
  refresh reporting.

## [v6.6.3] - 2026-06-11

### Release
- Consolidated GitHub release gates so releases run documentation checks,
  Cronet dependency posture validation, full debug unit tests, full release APK
  builds, Play release AAB builds, provenance generation, and checksum artifact
  upload before publishing.
- Added a Play flavor manifest overlay that removes `QUERY_ALL_PACKAGES` from
  Play release artifacts.
- Made release provenance locate `apksigner` on Linux CI as well as local
  Windows workstations.
- Refreshed current store metadata and expanded release-doc checks so metadata,
  toolchain, flavor-visibility, automation, protocol-maturity, and license
  warning drift fails before release.

### Maintainability
- Replaced deprecated Hilt Compose `hiltViewModel` imports, explicitized
  constructor-injected `@ApplicationContext` targets, and removed small Kotlin
  compiler warning sites found during the release build audit.

## [v6.6.2] - 2026-06-11

### Automation
- Repaired the public automation API contract by documenting canonical
  `com.hostshield.ACTION_*` actions and `duration_minutes` pause extras.
- Added code-level normalization for legacy lowercase action aliases and the
  old `pause_minutes` extra.
- Exposed all supported automation actions in the manifest and added unit
  coverage for action/extra normalization.
- Expanded release-doc checks so stale automation examples fail before release.

## [v6.6.1] - 2026-06-11

### Dependencies
- Updated OkHttp from 5.3.2 to 5.4.0, matching the current Maven Central
  release and Square changelog baseline.

## [v6.6.0] - 2026-06-11

### Reliability
- Moved long-running VPN/root/proxy protection services to Android
  `systemExempted` foreground-service declarations and runtime service types.
- Added foreground-service timeout callbacks for VPN, root DNS, and DNS proxy
  services with structured local diagnostic events.
- Routed boot restore, schedule resume, automation, Quick Settings, service
  helper, and Home resume/apply starts through a shared protected start helper
  that records Android background-start denials instead of crashing callers.

### Release
- Added a Cronet embedded posture gate that compares the declared
  `org.chromium.net:cronet-embedded` version against Google Maven metadata and
  fails when a newer embedded AAR is available.
- Included the Cronet embedded version in generated release provenance and made
  local `apksigner` verification select a valid Android Studio JBR when the
  shell has a stale `JAVA_HOME`.

## [v6.5.9] - 2026-05-14

### DNS
- Corrected DNS stamp parsing to the current 8-byte property format and kept
  legacy one-byte HostShield stamp compatibility.
- Preserved DNSCrypt resolver provider public keys, added `0x81` Anonymized
  DNSCrypt relay stamp parsing, and introduced a route planner that sends
  anonymized traffic to the relay while prefixing the resolver target.
- Added focused JVM coverage for resolver stamp keys, relay stamps, relay
  target prefixes, IPv4-mapped/IPv6 target encoding, and invalid privacy
  collapses.

## [v6.5.8] - 2026-05-14

### DNS
- Added a Cronet-backed DoH3 transport that sends DNS-over-HTTPS requests over
  real HTTP/3/QUIC when the selected provider negotiates `h3`/QUIC.
- Kept DoH reliable by falling back to the existing pinned OkHttp DoH transport
  whenever Cronet negotiates HTTP/2, redirects, times out, or fails.
- Added DoH3 provider mapping, bounded response handling, QUIC hints, public
  key pinning, provider latency EMA, and query-log transport labels.

## [v6.5.7] - 2026-05-14

### Reliability
- Added strict VPN route canonicalization before every `VpnService.Builder`
  route insertion so host bits are masked before Android validates routes.
- Replaced direct DNS trap and virtual DNS `addRoute` calls with a shared
  canonical route helper, covering IPv4, IPv6, host routes, and future
  network-prefix route additions.
- Added focused JVM coverage for IPv4/IPv6 host-bit masking, non-byte-aligned
  prefixes, and invalid route input rejection.

## [v6.5.6] - 2026-05-14

### Reliability
- Added a shared root-shell runner that detects Magisk 26+ and prefers libsu's
  mount-master shell for firewall commands that touch `iptables`, `ip6tables`,
  or route-localnet sysctls.
- Routed the network firewall and root DNS redirect firewall through the shared
  runner so Magisk mount-namespace isolation no longer makes rule application
  silently target the wrong namespace.
- Added focused Magisk version parsing coverage for the mount-master decision.

## [v6.5.5] - 2026-05-14

### Reliability
- Added shared TCP DNS fallback policy coverage for UDP responses with the
  `TC=1` truncation bit, including an IPv6 path-MTU-sized regression that
  verifies TCP retry starts within 200 ms.
- Reused the shared fallback path for IPv4 primary, IPv4 secondary, and IPv6
  UDP forwarding so truncated IPv6 DNS answers are retried over TCP instead of
  being forwarded incomplete.

## [v6.5.4] - 2026-05-14

### Reliability
- Added `BlocklistHolder.updateAsync()` so production blocklist rebuilds build
  the replacement trie on `Dispatchers.Default` before the existing atomic
  snapshot swap.
- Moved Home, VPN service, profile schedule, and hosts refresh rebuild paths to
  the async blocklist update path.
- Added concurrent-reader regression coverage around repeated async blocklist
  swaps.

## [v6.5.3] - 2026-05-14

### Reliability
- Moved HostShield's long-running protection foreground services from
  `specialUse` to `dataSync` foreground-service type declarations.
- Added a 60-second VPN tunnel heartbeat that asserts the TUN fd is still valid
  and restarts the VPN if the fd dies while the service is marked running.
- Shortened the VPN watchdog to 60 seconds and logs structured JSON events when
  the watchdog observes an OS kill or the heartbeat finds an invalid tunnel fd.
- Documented every WorkManager job and confirmed that immediate blocklist
  refresh uses expedited WorkManager with `RUN_AS_NON_EXPEDITED_WORK_REQUEST`.

## [v6.5.2] - 2026-05-14

### Reliability
- Added Android 16 always-on VPN recovery detection for the post-update
  lockdown corruption pattern where the VPN is established but receives no
  tunnel traffic despite a validated physical network.
- Added a Home advisory banner that tells the user to restart the device and,
  on rooted devices, exposes a one-tap restart action.
- Added focused detector coverage so the advisory only appears for Android 16+
  always-on lockdown sessions with a valid TUN fd, validated physical network,
  two-minute observation window, and zero inbound packets.

## [v6.5.1] - 2026-05-13

Premium product-polish pass focused on first-run trust, visual cohesion, action
discoverability, and on-device layout correctness.

### UX / UI
- Refined the global Compose shape system and removed oversized rounded surfaces
  from the checked user-facing flows for a sharper, more intentional visual
  language.
- Reworked onboarding footer layout so page indicators no longer collide with
  primary actions, converted the feature overview into a compact two-column
  summary, and kept DNS resolver choice scrollable with a fixed Continue action.
- Improved Sources and Rules action placement by moving add/gallery/paste
  actions into compact header controls instead of floating over list content.
- Added clearer loading, empty, error, disabled, selection, and accessibility
  semantics across onboarding, home, sources, rules, stats, and settings.
- Added consistent destructive-action confirmations for clearing logs,
  fingerprints, crash reports, and deleting custom rules or sources.
- Added a deliberate empty state for Connection Log's top blocked apps view so
  the secondary monitoring surface no longer appears blank with no data.
- Improved accessible names for prominent icon-only controls, warning banners,
  status dismiss actions, DNS save affordances, and accent color swatches.

### Reliability
- Fixed debug/release side-by-side installation by deriving the automation
  permission from the package id instead of hard-coding the release id.
- Fixed WebDAV connection testing so a failed PROPFIND is not reported as a
  successful empty remote directory.

### Verification
- Built and installed the full debug APK on a connected Samsung SM-S938B.
- Smoked onboarding, dashboard, sources, rules, stats, and settings on-device.

## [v6.5.0] - 2026-05-13

Engineering hardening pass — focused on real correctness, security, and reliability
issues found in a deep audit of the v6.4 codebase.

### Security
- **Parental PIN: fail-closed + brute-force lockout.** `verifyPin` previously
  returned `true` when no PIN was set — every PIN-gated action could be bypassed
  by clearing the stored hash. Now returns false unless `isPinSet()` is also
  true, with a new `verifyPinDetailed` returning `Success / Wrong / LockedOut /
  NoPin`. After 5 wrong attempts the caller is locked out for 30 s → 60 s →
  120 s → 300 s (exponential).
- **Backup encryption strengthened.** PBKDF2 iteration count raised from
  `100_000` → `600_000` (OWASP 2023 baseline) on both `EncryptedBackup` and
  `BackupCrypto`. Fixed off-by-one in `decrypt()` that rejected legal
  minimum-size ciphertexts and silently mis-classified ones too small to contain
  a GCM tag. Key bytes zeroed after `SecretKeySpec` is built.
- **SecureStore.verifyPin constant-time on raw bytes.** Previously compared
  Base64 strings; now decodes salt and expected hash, validates length, wraps
  decode in try/catch, and uses `MessageDigest.isEqual` on the byte arrays.
- **RootUtil hostname injection guard.** `appendHostEntry` / `removeHostEntry`
  now validate the hostname against RFC 1123, validate the redirect IP, switch
  `echo` for `printf`, and use token-aware line filtering on remove so entries
  with comments / tabs / multi-host lines are correctly removed.
- **Device-transfer no longer leaks encrypted prefs.** `data_extraction_rules.xml`
  now excludes `hostshield_secure_prefs` from `<device-transfer>` — the source
  device's hardware-backed master key cannot unwrap on the destination, so
  copying the ciphertext only locks the user out.
- **Widget receiver no longer launchable by third-party apps.** Removed
  `com.hostshield.WIDGET_TOGGLE` from the exported `HostShieldWidgetProvider`
  intent-filter. The widget's own PendingIntent targets the receiver by
  component, so toggling still works.
- **WireGuard input validation.** `buildDnsUdpPacket` no longer crashes on
  IPv6 / hostname inputs; `parseEndpoint` correctly handles `[v6]:port` form.
  WireGuard remains marked EXPERIMENTAL until a full Noise_IKpsk2 binding lands.

### Correctness & reliability
- **`ACTION_PAUSE > 10 s now works.** `AutomationReceiver` previously used
  `delay(N * 60_000L)` inside `goAsync()`, which Android killed after ~10 s.
  Replaced with `PauseResumeWorker` (new) — WorkManager survives Doze, so
  user-requested pauses of any documented length (up to 24 h) resume reliably.
- **TCP DNS fallback on TC=1 (RFC 7766 §6.2).** When the upstream UDP response
  has the TC (truncated) bit set, the VPN forwarder now retries the same query
  over TCP and substitutes the response. Large DNSSEC RRSIG / TXT records that
  exceeded the 1500-byte UDP buffer used to be silently truncated.
- **`HostsUpdateWorker` per-source error surfacing.** Failed block / allowlist
  / sync-URL downloads now log a warning with the URL and the error, plus a
  summary line at the end of the run. Previously `.onSuccess { }` silently
  swallowed 404s / DNS errors / SSL failures.
- **`HostsUpdateWorker.runNow` is now expedited.** Marked
  `RUN_AS_NON_EXPEDITED_WORK_REQUEST` and enqueued under a unique name so
  back-to-back invocations don't pile up.
- **`BlocklistHolder` atomic snapshot swap.** All read-side state (trie root,
  exact-match set, regex rules, IP blocks, count) is now packed into a single
  immutable `Snapshot`. Readers see either fully-old or fully-new state, never
  a torn view that previously could mis-classify a domain mid-update.
- **`BlocklistHolder` decision LRU is now actually LRU.** Replaced
  `ConcurrentHashMap` + random eviction with a synchronized
  `LinkedHashMap(accessOrder=true)` and `removeEldestEntry` — fixes TOCTOU on
  the size check + random eviction order (was not actually evicting the LRU
  entries).
- **`BlocklistHolder.removeDomain` no longer drops the count for never-added
  domains.** Counter UI used to drift over time.
- **`DohResolver` size cap + cert-pin diagnostics.** Responses are now read with
  an explicit 65 535-byte cap (RFC 8484 maximum) so a malicious or misconfigured
  endpoint can't OOM the VPN process. `SSLPeerUnverifiedException` is logged
  loudly so cert rotation failures are debuggable from `logcat` rather than
  silently failing closed. `failoverOrder` rotation (previously dead code) is
  now wired up — providers move to the end after 3 consecutive failures.
- **`DotResolver` response cap raised from 4 096 to 65 535.** RFC 7858 allows
  the full 16-bit length-prefix range; the old cap silently dropped legitimate
  large DNSSEC responses.
- **`DnsCache` honours RFC 2308 MINIMUM=0.** SOA-derived "do not cache" now
  actually skips the negative cache instead of falling back to a 60 s TTL.
  Positive-cache parse failures return TTL=0 (don't cache) instead of pinning
  the response at 5 minutes. RR scan cap raised from 20 → 100 for large CDN /
  CNAME chains. Eviction now sorts a single snapshot to avoid concurrent-map
  inconsistency.
- **`VpnService.Builder.addAddress` defensive wrap.** OEM-restricted builds that
  reject the IPv6 ULA address now degrade to IPv4-only instead of failing
  startup entirely.

### UX & polish
- **Onboarding DNS choice is now persisted.** The DNS picker on page 3 hoists
  its selection up to `OnboardingScreen` and threads it through `onComplete`;
  the chosen upstream is written to `customUpstreamDns` before the protection
  mode starts. Previously the choice was dropped on screen exit.
- **Onboarding state survives rotation.** `page` / `selectedMethod` /
  `selectedDns` switched from `remember` to `rememberSaveable`.
- **PushPin icon now shows pinned vs unpinned state visually.** Previously both
  branches drew `Icons.Filled.PushPin`; only the tint differed. Unpinned now
  draws `Icons.Outlined.PushPin`, and the content description updates.
- **Sources: `Add source` dialog validates URLs + includes category picker.**
  Previously any non-blank string was accepted, then crashed downstream; the
  unused `category` state had no UI so every user-added source was filed as
  ADS. New URL validation (`http://`/`https://` only), inline error text, and
  a FlowRow of category chips.
- **Settings update-check is throttled.** `autoCheckForUpdate` previously fired
  on every Settings open; now throttled to once per process per 24 h.

### Docs
- `CHANGELOG.md` repaired (was literal `## [v6.3.0] - %Y->-` placeholder).
- README version badge corrected (was `6.2.0` while build was `6.4.0`).

## [v6.4.0] - 2026-03-27

See [`app/CHANGELOG.md`](app/CHANGELOG.md) for the full v6.4.0 release notes.

## [v6.3.0]

- Security hardening audit, preferences refactor, DB indices, error handling.

## [v6.2.0]

- Major release — encrypted DNS, content filtering, parental controls, threat
  intel, 7 new screens.

## [v5.0.0]

- Core engine upgrades — serve-stale DNS, hash set fast path, offline GeoIP.

## [v4.6.0]

- Latency sparkline, source stats, search history, query type chart.
