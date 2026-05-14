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
