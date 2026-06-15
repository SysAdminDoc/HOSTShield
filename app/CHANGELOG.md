# HostShield v6.9.13

**Release Date:** 2026-06-15
**Version Code:** 95

## Stricter Release Polish

### Fixed
- Custom redirect rules now validate IPv4 and IPv6 targets before saving, so
  incomplete redirect rules cannot be added from the dialog.
- Regex rule errors now use stable user-facing text instead of exposing raw
  parser exception messages.
- Source and DNS log failure banners now show actionable product copy while
  preserving full exception details in Logcat for diagnostics.
- Source failure rows now summarize common failure classes without displaying
  raw networking exception strings.

### UX and Accessibility
- Long rule hostnames, redirect targets, source labels/descriptions, DNS log
  app labels, packages, and detail values now truncate safely instead of
  crowding badges or actions.
- DNS log query-type filters now wrap on small screens.
- Rule/source add buttons, delete buttons, DNS log selection checkboxes, and
  the log-detail pin control now use larger touch targets.
- Rule and source dialogs now provide clearer validation feedback and keyboard
  actions.

### Verification
- `tools/check-release-docs.ps1`
- `:app:testFullDebugUnitTest`
- `:app:lintFullDebug`
- `:app:assembleFullDebug`
- `HOSTSHIELD_ALLOW_DEBUG_RELEASE_SIGNING=true :app:assembleFullRelease`

# HostShield v6.9.12

**Release Date:** 2026-06-15
**Version Code:** 94

## Second-Pass Release Polish

### Fixed
- WebDAV Sync now keeps the saved password when users save server settings with
  the password field left blank, and connection tests can reuse that saved
  password without displaying it.
- QR config sharing no longer overflows narrow cards when a QR code is
  generated, and import/export result messages now use the shared accessible
  status banner.
- Rule Tester now labels input fields, supports the keyboard search action,
  disables empty single-domain tests, and truncates long domains and match
  descriptions to protect result layout.
- Settings diagnostic, CSV, and PCAP export controls now have larger action
  targets, better small-screen wrapping, rounded KB display, and clearer
  user-facing failure messages.

### Accessibility
- Added IME padding to Settings, WebDAV Sync, QR Config Sharing, and Rule
  Tester flows.
- Added screen-reader result summaries to Rule Tester rows.

### Verification
- `tools/check-release-docs.ps1`
- `:app:compileFullDebugKotlin`
- `:app:testFullDebugUnitTest`
- `:app:lintFullDebug`
- `:app:assembleFullDebug`
- `HOSTSHIELD_ALLOW_DEBUG_RELEASE_SIGNING=true :app:assembleFullRelease`

# HostShield v6.9.11

**Release Date:** 2026-06-15
**Version Code:** 93

## DoH Provider Preference and Export UX

### Fixed
- DoH now treats the provider selected in Settings as the primary resolver even
  when another provider has lower observed latency. Latency still ranks failover
  candidates after the selected provider fails.
- Settings PCAP export now offers all, DNS-only, and firewall-only capture modes
  and keeps share/save/discard actions visible after generation.
- PCAP and diagnostic package share failures now surface as recoverable UI state
  instead of Logcat-only errors.
- Dismissing a generated diagnostic package now removes the temporary ZIP.

### Build
- Moved plugin and dependency versions to `gradle/libs.versions.toml`.

### Release
- Extended `tools/check-release-docs.ps1` to validate version-catalog versions,
  targetSdk, `systemExempted` foreground-service claims, fail-closed encrypted
  DNS code anchors, disabled embedded DoH3 posture, and removed offline
  GeoIP/MaxMind claims.

### Verification
- `:app:testFullDebugUnitTest`
- `:app:lintFullDebug`

# HostShield v6.9.10

**Release Date:** 2026-06-14
**Version Code:** 92

## Encrypted DNS Fixes (GitHub #1)

- Fixed DNS-over-HTTPS, which never connected for any provider due to incorrect
  certificate pins plus a response-read bug (`readByteArray` threw EOFException
  on every normal-sized response). DoH now works and is device-verified; pins
  target each provider's real intermediate + root CA so routine leaf-cert
  rotations no longer break them.
- Encrypted DNS now fails closed: when DoH/DoT/DoQ/WireGuard is enabled and the
  encrypted resolver fails, resolution fails (stale cache or SERVFAIL) instead of
  silently leaking to plaintext UDP against a public resolver.
- DNS provider and custom upstream changes now apply immediately while protection
  is running, without restarting the VPN.
- The context-aware screen-state receiver now registers as `RECEIVER_NOT_EXPORTED`.

# HostShield v6.9.9

**Release Date:** 2026-06-14
**Version Code:** 91

## Launcher Icon Refresh

- Replaced the app launcher icon with the new HostShield shield artwork across
  legacy and adaptive Android icon densities.
- Updated the adaptive icon background to a matching near-black so transparent
  corners render cleanly on Android launchers.
- Enabled the built-in AdAway Default and StevenBlack Unified host sources by
  default, with a one-time database migration so existing installs receive the
  same default without overriding later user changes.

## Verification

- `:app:compileFullDebugKotlin`
- `:app:connectedFullDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hostshield.data.database.HostShieldMigrationTest`
- `:app:assembleFullRelease`
- Installed v6.9.9 on device `R5CT139QJ5F`.

---

# HostShield v6.9.8

**Release Date:** 2026-06-14
**Version Code:** 90

## Non-Root Dashboard Cleanup

- Removed the bottom Home dashboard "Root not detected" banner. Root
  availability is still reflected in the Root mode control without taking
  persistent dashboard space in VPN/non-root mode.
- The Settings "View on GitHub" action now explicitly opens the HostShield
  repository page with a browser-capable URL intent and no longer crashes when
  the device has no browser handler.
- Simplified the Sources header so only the add-source button remains next to
  the title.

## Verification

- `:app:compileFullDebugKotlin`

---

# HostShield v6.9.7

**Release Date:** 2026-06-14
**Version Code:** 89

## Animated Protection Orb

- The active dashboard protection orb now has a clear rotating arc, trailing
  sweep, breathing halo, and accent-colored orbit particles while protection
  is running.

## Verification

- `:app:testFullDebugUnitTest --tests com.hostshield.ui.theme.ThemeContrastTest`

---

# HostShield v6.9.6

**Release Date:** 2026-06-14
**Version Code:** 88

## Live Activity and Accent Theme Fixes

- Live DNS activity rows now restyle to the blocked/red state when a matching
  enabled user block rule is added from the DNS log.
- Accent color selections now drive the active theme palette across the app
  while preserving semantic red/green status colors.
- Blocklist rebuilds now avoid duplicating exact domains into trie nodes,
  reducing memory pressure during app resume on devices with 256 MB heaps.

## Verification

- `:app:testFullDebugUnitTest --tests com.hostshield.ui.screens.home.HomeDnsLogUiTest --tests com.hostshield.ui.theme.ThemeContrastTest`
- `:app:testFullDebugUnitTest --tests com.hostshield.domain.BlocklistHolderTest`

---

# HostShield v6.9.5

**Release Date:** 2026-06-14
**Version Code:** 87

## First-Run UX Polish

- Notification permission is now requested only after the user chooses to
  activate protection, instead of covering the first onboarding screen.
- The onboarding DNS resolver picker now uses a compact accessible selector so
  Default, Cloudflare, Google, Quad9, and AdGuard are visible with the Continue
  action on tall phones.

## Verification

- `:app:compileFullReleaseKotlin`
- connected-device release install and screenshot pass on `com.hostshield`

---

# HostShield v6.9.4

**Release Date:** 2026-06-14
**Version Code:** 86

## Audit Hardening

- Release artifact builds now fail closed unless release signing credentials are
  configured. Local non-distribution release verification must opt in with
  `HOSTSHIELD_ALLOW_DEBUG_RELEASE_SIGNING=true`.
- SAF imports, backups, QR config, automation DNS, WebDAV, threat intel,
  GeoIP/RDAP/update checks, diagnostics, crash reports, and DEX scans now
  enforce tighter validation and explicit size bounds.
- DNS diagnostic targets are validated before process launch.
- Room enum converters now fall back defensively for unknown source categories
  and rule types.
- Secondary dismiss, delete, refresh, copy, and toggle controls now use larger
  touch targets across Home warnings, Settings, QR config, Firewall, Sources,
  Rules, Stats, Logs, TLS fingerprints, Crash Reports, Automation, and App
  Privacy.

## Verification

- `tools/check-release-docs.ps1`
- `tools/check-cronet-posture.ps1`
- `:app:compileFullDebugKotlin`
- `:app:testFullDebugUnitTest`
- `:app:assembleFullRelease` with
  `HOSTSHIELD_ALLOW_DEBUG_RELEASE_SIGNING=true`
- `:app:assembleFullRelease` without signing credentials fails closed with the
  expected credential guidance

---

# HostShield v6.9.3

**Release Date:** 2026-06-13
**Version Code:** 85

## Threat-Intel Feed Health Dashboard

- Stats now shows URLhaus, Spamhaus DROP, Emerging Threats, and Disconnect
  Malware feed health with freshness, HTTP status, entry counts, byte counts,
  SHA-256 prefixes, failure counts, and manual refresh state.
- Diagnostic exports now include redacted threat-feed health summaries and ZIP
  manifest counts without raw IOC details.
- Added JVM coverage for feed-health status mapping.

## Verification

- `tools/check-release-docs.ps1`
- `:app:compileFullDebugKotlin`
- `:app:testFullDebugUnitTest --tests com.hostshield.ui.screens.stats.ThreatIntelFeedHealthUiTest`

---

# HostShield v6.9.2

**Release Date:** 2026-06-13
**Version Code:** 84

## Embedded Cronet DoH3 Disabled

- Removed the embedded Cronet dependency while no maintained non-vulnerable
  Cronet artifact is available.
- DoH3 remains disabled through an explicit resolver facade, preserving provider
  mapping and Hilt wiring while avoiding the vulnerable native client.
- Encrypted DNS continues through fail-closed pinned OkHttp DoH/DoT production
  paths.
- Cronet posture and release provenance tooling now record the no-bundled-Cronet
  state instead of requiring the unavailable upgraded artifact.

## Verification

- `tools/check-cronet-posture.ps1`
- `tools/check-release-docs.ps1`
- `:app:compileFullDebugKotlin`
- `:app:testFullDebugUnitTest --tests com.hostshield.service.Doh3ResolverTest`

---

# HostShield v6.9.1

**Release Date:** 2026-06-13
**Version Code:** 83

## Pseudolocale and RTL Layout

- Debug builds now generate Android pseudolocales for layout-safety checks.
- Reusable Compose surfaces have an RTL pseudo-expanded scaffold covering panel
  headers, segmented controls, status banners, and empty states.
- Touched Home, Settings, Protection, and QR configuration copy now flows
  through string resources.
- QR config export/import validation remains covered by focused JVM tests.

## Verification

- `:app:compileFullDebugKotlin`
- `:app:compileFullDebugAndroidTestKotlin`
- `:app:testFullDebugUnitTest --tests com.hostshield.util.QrConfigSharingTest`

---

# HostShield v6.7.1

**Release Date:** 2026-06-13
**Version Code:** 79

## Temporary Bypass Timer

- The Home screen pause action now opens a duration picker (5, 15, 30, or 60
  minutes) instead of immediately disabling protection.
- A visible countdown shows remaining pause time with a one-tap resume button.
- Protection auto-resumes via WorkManager when the timer expires, surviving
  process death and Doze.
- "Until manually resumed" option retains the previous indefinite-disable
  behavior.
- All manual re-enable paths (shield orb, VPN permission grant, root apply)
  cancel any active pause timer.

## Verification

- `:app:compileFullDebugKotlin`
- `:app:testFullDebugUnitTest`

---

# HostShield v6.6.8

**Release Date:** 2026-06-11
**Version Code:** 76

## QR Config

- QR config export now includes actual enabled rules and custom source URLs in
  addition to DNS settings.
- QR import now previews validated changes and applies rules, HTTPS-only
  sources, custom upstream DNS, and enabled DoH settings through the existing
  repository and preference paths.
- QR decoding now rejects pasted strings over 4 KB and compressed payloads that
  expand past 64 KB before parsing JSON.
- Added focused JVM tests for QR encode, decode, validation, and apply planning.

## Verification

- `:app:testFullDebugUnitTest --tests com.hostshield.util.QrConfigSharingTest`

---

# HostShield v6.6.7

**Release Date:** 2026-06-11
**Version Code:** 75

## Safe Search

- Safe Search DNS enforcement now covers Google country and region domains such
  as `google.co.uk` and `google.com.br`.
- A and AAAA queries now receive query-type-correct safe endpoint responses,
  including IPv4-mapped safe IPv6 where appropriate.
- Safe endpoints are resolved through their canonical hostnames with a local
  cache and bundled fallback addresses.
- Services without safe IPv6 answers return NODATA for AAAA instead of an
  invalid A response.
- HTTPS, SVCB, and other metadata queries return NODATA so clients do not learn
  alternate unsafe endpoints during enforcement.

## Verification

- `:app:testFullDebugUnitTest`
- `tools/check-release-docs.ps1`

---

# HostShield v6.6.6

**Release Date:** 2026-06-11
**Version Code:** 74

## Security

- LAN DNS server mode now rejects public, non-local clients by default while
  allowing private, loopback, link-local, and IPv6 ULA clients.
- Added per-client query throttling to reduce open-resolver and amplification
  abuse risk on hostile networks.
- Oversized UDP upstream responses now return a truncated DNS response with
  TC=1 so clients can retry over TCP instead of receiving oversized datagrams.
- Local DNS blocking now uses the shared hardened DNS packet builder for
  blocked responses.
- Local DNS startup loads configured custom upstream, DoT, and DoH preferences
  before falling back to plaintext UDP.

## Verification

- `:app:testFullDebugUnitTest`
- `tools/check-release-docs.ps1`

---

# HostShield v6.6.5

**Release Date:** 2026-06-11
**Version Code:** 73

## Sync

- Periodic threat-intel refreshes now honor the Wi-Fi-only sync preference
  instead of always running on any connected network.
- Periodic source-health checks now use the same Wi-Fi-only network constraint
  as blocklist updates.
- Changing the Wi-Fi-only preference reschedules existing WorkManager
  registrations with updated constraints.
- Manual refresh actions remain user-initiated and can still run on any
  connected network.
- Android test schema assets now use the current AGP 9 source-set directory API,
  removing the Gradle configuration deprecation warning from the build.

## Verification

- `:app:testFullDebugUnitTest`
- `tools/check-release-docs.ps1`

---

# HostShield v6.6.4

**Release Date:** 2026-06-11
**Version Code:** 72

## Threat Intel

- Hardened compromised-IP feed parsing so whitespace-separated Emerging
  Threats-style feed bodies emit one `/32` CIDR per IP and preserve explicit
  CIDR prefixes.
- Rejected invalid IPv4 tokens, invalid prefixes, and overly broad prefixes
  before cache persistence.
- De-duplicated repeated feed tokens while preserving first-seen order.
- Made partial feed refreshes report degraded status to WorkManager while still
  persisting successfully parsed threat data.

## Verification

- `:app:testFullDebugUnitTest`
- `tools/check-release-docs.ps1`

---

# HostShield v6.6.3

**Release Date:** 2026-06-11
**Version Code:** 71

## Release

- Updated the GitHub release workflow to run release-doc checks, Cronet posture
  validation, `testFullDebugUnitTest`, full release APK build, Play release AAB
  build, provenance generation, and release artifact upload before publishing.
- Added a Play flavor manifest overlay that removes `QUERY_ALL_PACKAGES` for
  Play AAB artifacts.
- Updated release provenance to discover Linux `apksigner` in GitHub Actions
  and Windows `apksigner.bat` locally.
- Refreshed current store metadata and added a versionCode 71 metadata
  changelog.
- Expanded release-doc checks to validate current metadata, toolchain versions,
  automation examples, Play/full flavor visibility claims, experimental
  protocol labels, and the license-publication warning.

## Maintainability

- Replaced deprecated Hilt Compose `hiltViewModel` imports with the current
  lifecycle package.
- Added explicit `@param:ApplicationContext` targets for constructor-injected
  context qualifiers so Kotlin 2.3 no longer warns about future annotation
  target changes.
- Removed redundant non-null assertions and the obsolete DNS stamp parser
  fallback branch flagged by the current Kotlin compiler.
- Switched the connection-log settings icon to the auto-mirrored Material icon.

## Verification

- `:app:testFullDebugUnitTest`
- `:app:assembleFullRelease`
- `:app:bundlePlayRelease`
- `tools/check-release-docs.ps1`
- `tools/check-cronet-posture.ps1`
- `tools/release-provenance.ps1`

---

# HostShield v6.6.2

**Release Date:** 2026-06-11
**Version Code:** 70

## Automation

- Updated README automation examples to use canonical
  `com.hostshield.ACTION_*` action strings and the `duration_minutes` pause
  extra.
- Added `AutomationActionContract` to normalize canonical and legacy lowercase
  action aliases and to preserve `pause_minutes` compatibility.
- Exposed `ACTION_SET_PROFILE`, `ACTION_SET_DNS`, `ACTION_PAUSE`, and legacy
  action aliases in the manifest.
- Added JVM tests for automation action normalization and pause duration
  clamping/resume semantics.
- Expanded `tools/check-release-docs.ps1` to reject stale lowercase automation
  command examples.

## Verification

- `:app:testFullDebugUnitTest`
- `tools/check-release-docs.ps1`

---

# HostShield v6.6.1

**Release Date:** 2026-06-11
**Version Code:** 69

## Dependencies

- Updated OkHttp from 5.3.2 to 5.4.0 for source downloads, DoH fallback, WebDAV,
  update checks, and threat/source refresh HTTP calls.

## Verification

- `:app:compileFullDebugKotlin`
- `:app:testFullDebugUnitTest`
- `tools/check-release-docs.ps1`

---

# HostShield v6.6.0

**Release Date:** 2026-06-11
**Version Code:** 68

## Reliability

- Moved VPN, root DNS, and DNS proxy protection services to Android
  `systemExempted` foreground-service declarations and runtime service types.
- Added timeout callbacks for all long-running protection services so Android
  15/16 service timeouts are logged to the local diagnostic export before a
  controlled shutdown path runs.
- Routed boot restore, schedule resume, pause/resume, automation, Quick
  Settings, service helper, and Home resume/apply starts through a shared
  foreground-service starter that records `foreground_service_start_failed`
  diagnostics when Android denies a background start.

## Release

- Added `tools/check-cronet-posture.ps1` to verify the declared
  `org.chromium.net:cronet-embedded` dependency matches Google Maven's latest
  embedded release.
- Added the Cronet embedded version to generated release provenance and made
  local `apksigner` verification select Android Studio JBR when `JAVA_HOME`
  points at a stale path.

## Verification

- `:app:compileFullDebugKotlin`
- `:app:testFullDebugUnitTest`
- `tools/check-release-docs.ps1`
- `tools/check-cronet-posture.ps1`

---

# HostShield v6.5.9

**Release Date:** 2026-05-14
**Version Code:** 67

## DNS

- Corrected `sdns://` parsing to the current DNS stamp format with 8-byte
  little-endian properties while preserving compatibility with legacy
  one-byte HostShield-encoded stamps.
- DNSCrypt resolver stamps now retain and validate the required 32-byte
  provider public key instead of silently discarding it.
- Added Anonymized DNSCrypt relay stamp support (`0x81`) and a route planner
  that validates resolver/relay roles, rejects resolver-as-relay privacy
  collapses, and builds the relay prefix that targets the resolver while the
  network destination remains the relay.
- Added DoQ and ODoH stamp protocol identifiers in the shared parser so the
  next DNS protocol work can reuse the same spec-correct stamp layer.

## Verification

- `:app:testFullDebugUnitTest --tests com.hostshield.util.DnsStampParserTest --tests com.hostshield.service.DnsCryptRoutePlannerTest`

## Remaining DNSCrypt transport work

- Full DNSCrypt v2 query encryption/decryption is not wired yet. The parser
  and anonymized route layer are now correct, but production transport still
  needs an audited Android-compatible crypto/engine choice before exposing a
  user-facing DNSCrypt toggle.

---

# HostShield v6.5.8

**Release Date:** 2026-05-14
**Version Code:** 66

## DNS

- Added `Doh3Resolver`, an embedded-Cronet DNS-over-HTTPS/3 transport with QUIC
  enabled, provider QUIC hints, bounded DNS response reads, and public-key pins
  matching the existing DoH provider pin set.
- The existing DoH setting now tries DoH3 first and accepts the result only
  when Cronet reports an HTTP/3/QUIC negotiated protocol; HTTP/2 and failures
  fall back to the existing pinned OkHttp DoH resolver.
- Query logs now label successful HTTP/3 upstreams as `DoH3:<provider>` while
  preserving ordinary `DoH:<provider>` labels for fallback traffic.
- Added focused JVM coverage for DoH-to-DoH3 provider mapping and negotiated
  protocol acceptance policy.

## Verification

- `:app:compileFullDebugKotlin`
- `:app:testFullDebugUnitTest --tests com.hostshield.service.Doh3ResolverTest`
- `:app:assembleFullDebug`
- Installed `app-full-debug.apk` on connected adb device and verified
  `com.hostshield.debug` launches as v6.5.8 / versionCode 66 with no crash-log
  hit.

---

# HostShield v6.5.7

**Release Date:** 2026-05-14
**Version Code:** 65

## Reliability

- Added `VpnRouteCanonicalizer` to normalize numeric VPN route addresses before
  passing them into `VpnService.Builder.addRoute`.
- Masked IPv4 and IPv6 host bits for network prefixes, including
  non-byte-aligned prefixes, so Android 11+ route validation does not reject
  otherwise valid user-provided routes.
- Routed virtual DNS, IPv6 virtual DNS, DNS trap, and DoH bypass route
  insertions through the shared canonical route helper.
- Added JVM coverage for IPv4, IPv6, host-route preservation, invalid prefix
  rejection, CIDR-suffix rejection, and non-numeric hostname rejection.

## Verification

- `:app:compileFullDebugKotlin`
- `:app:testFullDebugUnitTest --tests com.hostshield.service.VpnRouteCanonicalizerTest`
- `:app:assembleFullDebug`
- Installed `app-full-debug.apk` on connected adb device and verified
  `com.hostshield.debug` launches as v6.5.7 / versionCode 65 with no crash-log
  hit.

---

# HostShield v6.5.6

**Release Date:** 2026-05-14
**Version Code:** 64

## Reliability

- Added `RootShellRunner` to centralize root command execution and Magisk version
  detection.
- On Magisk 26+, firewall command paths now prefer libsu's mount-master shell
  for `iptables`, `ip6tables`, and route-localnet sysctl work.
- Routed both the network firewall (`IptablesManager`) and root DNS redirect
  firewall (`RootDnsLogger`) through the shared runner so rule changes apply in
  the intended mount namespace.
- Added JVM coverage for Magisk version parsing and the mount-master support
  gate.

## Verification

- `:app:compileFullDebugKotlin`
- `:app:testFullDebugUnitTest --tests com.hostshield.util.RootShellRunnerTest`
- `:app:assembleFullDebug`
- Installed `app-full-debug.apk` on connected adb device and verified
  `com.hostshield.debug` launches as v6.5.6 / versionCode 64 with no crash-log
  hit.

---

# HostShield v6.5.5

**Release Date:** 2026-05-14
**Version Code:** 63

## Reliability

- Added shared RFC 7766 TCP fallback handling for UDP DNS responses with the
  `TC=1` truncation bit.
- Wired IPv6 UDP forwarding through the same TCP retry path as IPv4 so
  path-MTU-truncated AAAA/DNSSEC-sized responses are retried over TCP instead
  of being returned incomplete.
- Added JVM regression coverage that verifies a path-MTU-sized truncated UDP
  response starts TCP retry within the 200 ms budget and preserves the UDP
  response only when TCP retry fails.

## Verification

- `:app:compileFullDebugKotlin`
- `:app:testFullDebugUnitTest --tests com.hostshield.service.DnsTcpFallbackTest`
- `:app:assembleFullDebug`
- Installed `app-full-debug.apk` on connected adb device and verified
  `com.hostshield.debug` launches as v6.5.5 / versionCode 63 with no crash-log
  hit.

---

# HostShield v6.5.4

**Release Date:** 2026-05-14
**Version Code:** 62

## Reliability

- Added `BlocklistHolder.updateAsync()` so production blocklist rebuilds happen
  off the caller thread before the existing single-reference snapshot swap.
- Moved Home apply/resume, VPN rebuild, profile schedule, and hosts refresh
  paths to the async blocklist update API.
- Added concurrent-reader regression coverage for repeated async blocklist
  swaps.

## Verification

- `:app:compileFullDebugKotlin`
- `:app:testFullDebugUnitTest --tests com.hostshield.domain.BlocklistHolderTest`

---

# HostShield v6.5.3

**Release Date:** 2026-05-14
**Version Code:** 61

## Reliability

- Moved `DnsVpnService`, `RootDnsService`, and `DnsProxyService` to Android
  `dataSync` foreground-service declarations and runtime service types.
- Added a 60-second VPN tunnel heartbeat that checks the TUN fd while protection
  is running and restarts the VPN if the fd becomes invalid.
- Shortened the watchdog alarm to 60 seconds and changed kill/fd-failure logs to
  structured JSON events for local diagnostics.
- Added `docs/WORKMANAGER_AUDIT.md` covering every WorkManager job, the lack of
  direct JobScheduler usage, and the expedited immediate blocklist refresh path.

## Verification

- `:app:compileFullDebugKotlin`
- `:app:testFullDebugUnitTest --tests com.hostshield.util.Android16VpnRecoveryDetectorTest`

---

# HostShield v6.5.2

**Release Date:** 2026-05-14
**Version Code:** 60

## Reliability

- Added Android 16 always-on VPN recovery detection for the known post-update
  lockdown corruption pattern where the VPN is established but receives no TUN
  ingress despite a validated physical network.
- Added a Home recovery advisory that instructs the user to restart the device
  and exposes a rooted one-tap restart action when root is available.
- Added focused JVM coverage for the detector gates so the banner is limited to
  Android 16+ always-on lockdown sessions after a two-minute zero-packet window.

## Verification

- `:app:compileFullDebugKotlin`
- `:app:testFullDebugUnitTest --tests com.hostshield.util.Android16VpnRecoveryDetectorTest`

---

# HostShield v6.5.1

**Release Date:** 2026-05-13
**Version Code:** 59

## Premium polish

- Refined the Compose shape system, typography consistency, selected states, and icon treatment across the main user-facing surfaces.
- Reworked onboarding into a more resilient first-run flow: compact feature overview grid, non-overlapping page indicators, fixed bottom actions, clearer DNS and VPN copy, and stronger accessibility semantics.
- Moved Sources and Rules actions into compact header controls so add/gallery/paste actions no longer cover source rows, rule empty states, or bottom navigation.
- Added calmer loading, empty, warning, error, disabled, and selection states across home, sources, rules, stats, settings, and onboarding.
- Added consistent confirmation dialogs for destructive actions: clearing DNS logs, connection logs, TLS fingerprints, crash reports, and deleting custom rules or sources.
- Added an intentional empty state for Connection Log's top blocked apps tab.
- Improved accessible names for warning banners, icon-only actions, DNS save controls, status dismiss buttons, and accent color swatches.

## Reliability

- Debug and release builds can now install side by side because the automation permission derives from the package id.
- WebDAV remote listing now distinguishes failed connections from successful empty directories.

## Verification

- `:app:compileFullDebugKotlin`
- `:app:installFullDebug`
- Connected-device smoke pass on Samsung SM-S938B covering onboarding, dashboard, sources, rules, stats, and settings.

---

# HostShield v6.4.0

**Release Date:** 2026-03-27
**Version Code:** 57

## Security

- **Cleartext traffic disabled globally** — all network requests now require HTTPS; GeoIP switched from HTTP ip-api.com to HTTPS ipapi.co
- **Unpinned DoH fallback removed** — DoH resolver now fails closed instead of downgrading to unpinned client when all certificate pins fail
- **Secrets encrypted at rest** — WireGuard keys, PSK, and WebDAV credentials migrated from plaintext DataStore to EncryptedSharedPreferences (AES256-GCM); automatic migration on first launch
- **Parental PIN hardened** — upgraded from SHA-256 to PBKDF2-HMAC-SHA256 (210K iterations) with automatic legacy hash upgrade; PIN comparison now uses constant-time `MessageDigest.isEqual()`
- **AES-256-GCM backup encryption** — optional passphrase-based encryption for backup export/import via new `BackupCrypto` utility
- **Shell injection mitigated** — RootUtil now quotes all shell variables and replaced `sed` with Kotlin-side filtering to prevent delimiter injection
- **Manifest hardened** — `allowBackup="false"`, `dataExtractionRules` added, BootReceiver restricted with `RECEIVE_BOOT_COMPLETED` permission
- **Automation rate limit fixed** — inverted check-then-act logic in AutomationReceiver corrected; rate limiting now properly rejects rapid-fire intents
- **WireGuard nonce randomized** — nonce counter now initialized from `SecureRandom` instead of 0 to prevent nonce reuse on session recreate
- **Sync URL hardening** — custom rule sync URLs now require HTTPS, enforce 10MB size limit, and track SHA-256 content hashes
- **Parental controls PIN gate** — disabling parental controls now requires PIN verification via dialog
- **Threat intel IP validation** — `ipToInt()` now validates each octet is 0-255, preventing overflow in trie lookups

## Architecture

- **Preferences refactored** — monolithic `AppPreferences` (369 lines) split into 6 domain managers (BlockingPreferences, DnsPreferences, FirewallPreferences, SecurityPreferences, UiPreferences, SyncPreferences) with backward-compatible facade
- **Repository refactored** — monolithic `HostShieldRepository` split into 4 domain repositories (SourceRepository, RuleRepository, DnsLogRepository, ProfileRepository) with facade
- **DnsVpnService decomposed** — extracted PacketClassifier, TcpRstBuilder, DnsPacketParser, AppResolver as stateless utility classes; extracted `postForwardChecks()` eliminating ~240 lines of duplication across 7 forwarding methods
- **HomeScreen split** — 1654-line god-composable broken into HomeSearchSection, HomeWarningsSection, HomeStatsSection, and HomeComponents (now 572 lines)
- **SettingsScreen split** — 1012-line composable broken into DnsSettingsSection and ProtectionSettingsSection (now 833 lines)
- **SettingsViewModel consolidated** — 29 individual preference collectors reduced to 8 using `combine()` groups
- **BlocklistHolder optimized** — replaced redundant dual trie traversal with unified single-pass check; added `@Synchronized` to `update()` preventing race conditions between workers

## Reliability

- **Iptables rollback** — firewall rule application now uses two-phase execution with automatic `clearRules()` rollback on partial failure
- **Worker error handling** — all 7 WorkManager workers now have proper `Result.retry()` with attempt limits (5 max), exponential backoff policies, and `Log.e()` error reporting; previously 3 workers silently returned success on failure
- **AutoBackupWorker activated** — was dead code (never scheduled); now runs weekly via HostShieldApp.onCreate()
- **Socket leak fixed** — `forwardUdp()` DatagramSocket now properly closed in `finally` block
- **runBlocking ANR guard** — `stopVpn()` flush wrapped in `withTimeoutOrNull(3000L)` to prevent ANR on service shutdown
- **LogsScreen error handling** — all 8 coroutine launches wrapped with try-catch and error state exposed via StateFlow
- **Loading/error states** — LogsScreen, FirewallScreen, and SourcesScreen now show CircularProgressIndicator during load and dismissible error banners on failure
- **@Transaction atomicity** — profile activation and DNS log batch deletion now wrapped in Room `@Transaction` to prevent inconsistent state
- **BootReceiver structured scope** — replaced unstructured `CoroutineScope(Dispatchers.IO)` with properly scoped coroutine that cancels on completion
- **BlockNotificationService scope leak** — CoroutineScope now cancelled in `stop()` and recreated in `start()`

## Performance

- **Database indices added** — new indices on `host_sources(category)`, `host_sources(enabled)`, and `user_rules(enabled, type)` via Room migration v13→v14; eliminates full table scans on DAO queries
- **DAO singleton scoping** — all 11 DAO providers in DatabaseModule now annotated with `@Singleton`, preventing duplicate instances per injection site
- **Firewall stale rule cleanup** — `syncInstalledApps()` now removes database entries for uninstalled apps, preventing UID reuse conflicts

## Testing

- **DnsCacheTest deterministic** — replaced 5 `Thread.sleep()` calls with injectable `FakeClock`, eliminating timing-dependent flakiness on CI
- **New test suites** — PacketClassifierTest (16 tests), DnsPacketParserTest (21 tests) for extracted utility classes

## Build

- **Compose ProGuard rules** — added keep rules for `androidx.compose.runtime`, `androidx.compose.ui`, and `@Composable` annotations to prevent R8 stripping
- **DoT response bounds** — added 4096-byte upper limit on DoT response length to prevent OOM allocation
- **DoQ/WireGuard experimental warnings** — runtime log warnings added to both resolvers clarifying limitations
- **Keystore gitignore** — added `*.jks` entry to prevent accidental keystore commits
