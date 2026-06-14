# Changelog

All notable changes to HostShield will be documented in this file. Detailed
release notes per version live in [`app/CHANGELOG.md`](app/CHANGELOG.md).

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
