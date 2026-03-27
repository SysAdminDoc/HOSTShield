# HostShield

## Overview
Modern, AMOLED-dark hosts-based ad blocker app for Android. Inspired by AdAway. v6.2.0. All 52 roadmap items in `docs/RESEARCH.md` are DONE.

## Tech Stack
- Kotlin, Jetpack Compose, Material 3
- Hilt for dependency injection
- Room + DataStore for persistence
- Coroutines + Flow for async, ViewModels + StateFlow for UI state
- OkHttp for source downloads + DoH resolver, libsu for root access

## Key Architecture
- **AdblockRuleParser** - v5.0: Parses adblock-syntax (`||domain^`, `@@`, `$important`, `$badfilter`, `$dnstype`, `$denyallow`). Auto-detected by HostsParser when >20% of lines start with `||`. Returns DnsRule objects with 4-level priority system matching AdGuard urlfilter spec. Mixed-format files (hosts + adblock + domains-only) handled seamlessly.
- **BlocklistHolder** - v5.0: Hash set fast path (O(1) exact match before trie, ~2x faster for 90% of lookups) + filter decision LRU cache (8K entries, invalidated on update). Trie-based O(m) domain lookup, 200K+ domains, volatile root for thread safety. Regex rules capped at 500 chars with nested quantifier rejection (ReDoS prevention).
- **DnsVpnService** - Local VPN DNS interception, TUN interface, dual-stack (IPv4+IPv6), DNS trap, DoH/DoT/DoQ/WireGuard encrypted DNS forwarding, TCP DNS RST for both IPv4 and IPv6. Bounded log buffer (LinkedBlockingQueue 5000) with overflow detection. Context-aware firewall checks (screen off/background/metered). VPN stability tracking (uptime, rebuilds, fd errors, dropped queries). Publishes cache stats + dropped count via companion object for UI. v6.2: `forwardEncrypted()` dispatch: WireGuard > DoQ > DoT > DoH > UDP with automatic fallback chain. TLS fingerprinting (JA3/JA4) for both IPv4 and IPv6 non-DNS TCP packets.
- **DohResolver** - RFC 8484 POST+GET, certificate pinning, smart latency-based failover (EMA per provider, auto-selects fastest), unpinned fallback as last resort.
- **DotResolver** - v6.0: RFC 7858 DNS-over-TLS, TLSv1.3, SNI + hostname verification, 4 providers (Cloudflare, Google, Quad9, AdGuard). v6.2: Wired into forwardEncrypted() dispatch chain and DnsProxyService/LocalDnsServer.
- **DoqResolver** - v6.2: RFC 9250 DNS-over-QUIC. QUIC Initial packets with DNS in STREAM frame on stream 0. Variable-length integer codec. 3 providers (AdGuard, NextDNS, Mullvad). Falls back to DoT. Wired into forwardEncrypted() dispatch.
- **WireGuardProxy** - v6.2: Noise_IKpsk2 handshake, AES-256-GCM transport. DNS-over-WireGuard tunnel. Wired into forwardEncrypted() dispatch (highest priority).
- **RootDnsLogger** - Root-mode DNS proxy on 127.0.0.1:5454, iptables NAT redirect, UID attribution.
- **IptablesManager** - AFWall+-style per-app firewall, 20+ interface patterns, BLACKLIST/WHITELIST modes.
- **HostsUpdateWorker** - Periodic blocklist refresh + DoH bypass list update + allowlist subtraction. Uses shared singleton OkHttpClient (injected via Hilt).
- **TrackerSignatureDb** - v5.2: Exodus-style APK dex scanning for 405 tracker SDK signatures (8 categories: Advertising, Analytics, Crash, Profiling, Social, Location, Fingerprinting, Identification). Results cached in Room DB (tracker_scan_cache table), invalidated on app version change. 7-day cache TTL. Progress callback for UI.
- **CaptivePortalHandler** - v5.2: Detects captive portals via NetworkCallback (NET_CAPABILITY_CAPTIVE_PORTAL), auto-pauses VPN for 3 minutes, shows login notification, auto-resumes on NET_CAPABILITY_VALIDATED. 5s debounce.
- **ThreatIntelManager** - v5.2: Daily-updated threat intelligence feeds (abuse.ch URLhaus, Spamhaus DROP/EDROP, Emerging Threats, Disconnect Malware). IPv4 radix trie for O(1) CIDR lookup, ConcurrentHashMap for domain lookup. Persisted to JSON disk cache. ThreatIntelWorker runs daily via WorkManager. v6.0: Wired into all 6 forwarding methods (processIpv4Dns, processIpv6Dns, forwardUdp, forwardUdpFallback, forwardDoH, forwardUdpV6) for domain + IP blocking.
- **NetworkTrackerDb** - v6.0: Network-based tracker detection. 200+ tracker domains mapped to owner+category (Disconnect + DuckDuckGo Tracker Radar). Suffix-matching lookup. Auto-enriches every DNS log entry in logAsyncRich(). 8 categories: Advertising, Analytics, Fingerprinting, Social, Location, Identification. Enables per-app tracker analytics.
- **GeoIpLookup** - ip-api.com with in-memory cache, rate limiting (40 req/min window), exponential backoff on 429.
- **ContextState** - Screen on/off receiver + metered network detection + foreground app tracking. Drives context-aware firewall rules.
- **PrivateDnsDetector** - Detects Strict/Automatic Private DNS that bypasses VPN filtering. Shows in onboarding + persistent Home re-check on resume.
- **AutomationReceiver** - Broadcast intent API with rate limiting (5s per action per caller), full audit logging to Room DB. Audit log viewable in Settings > Tools.
- **ImportExportUtil** - Supports HostShield JSON, AdAway, Blokada, NextDNS, Pi-hole, plain hosts. Firewall rule JSON export/import added in v4.1.0.

## Source Categories
- ADS, TRACKERS, MALWARE, ADULT, SOCIAL, CRYPTO, **ALLOWLIST**, CUSTOM
- ALLOWLIST sources subtracted from blocklist during updates (not added to block trie)

## Key Files
- `app/app/src/main/java/com/hostshield/service/DnsVpnService.kt` - VPN packet loop (~2700 lines)
- `app/app/src/main/java/com/hostshield/domain/BlocklistHolder.kt` - Trie + hash set + regex + wildcard engine
- `app/app/src/main/java/com/hostshield/domain/parser/AdblockRuleParser.kt` - v5.0: Adblock-syntax parser (||, @@, $important, $badfilter, $dnstype, $denyallow)
- `app/app/src/main/java/com/hostshield/service/DohResolver.kt` - DoH with smart latency failover
- `app/app/src/main/java/com/hostshield/service/DnsCache.kt` - v5.0: LRU DNS cache with serve-stale (RFC 8767), negative caching (RFC 2308), SERVFAIL caching (RFC 9520), prefetching (Unbound algorithm), configurable TTL caps (60s-24h), L2 disk cache warm/export
- `app/app/src/main/java/com/hostshield/service/DnsDiskCache.kt` - v5.0: Persistent SQLite DNS cache (L2), survives reboots, 10K entry cap, WAL mode
- `app/app/src/main/java/com/hostshield/service/CnameCloakUpdater.kt` - v5.0: Fetches AdGuard + NextDNS CNAME cloak databases
- `app/app/src/main/java/com/hostshield/service/DnsPacketBuilder.kt` - v5.0: DNS wire format builder/parser + SVCB/HTTPS type constants + queryTypeLabel()
- `app/app/src/main/java/com/hostshield/util/TrackerSignatureDb.kt` - APK tracker SDK scanner (405 signatures, Room-cached)
- `app/app/src/main/java/com/hostshield/util/NetworkTrackerDb.kt` - v6.0: Network-based tracker domain DB (200+ domains, suffix matching)
- `app/app/src/main/java/com/hostshield/service/CaptivePortalHandler.kt` - v5.2: Captive portal detection + VPN auto-pause/resume
- `app/app/src/main/java/com/hostshield/service/ThreatIntelManager.kt` - v5.2: Threat intel feeds + radix trie IP lookup + domain blacklist
- `app/app/src/main/java/com/hostshield/service/ThreatIntelWorker.kt` - v5.2: Daily WorkManager job for threat intel refresh
- `app/app/src/main/java/com/hostshield/util/GeoIpLookup.kt` - GeoIP/ASN lookup via ip-api.com (rate limited, legacy — use OfflineGeoIp for new code)
- `app/app/src/main/java/com/hostshield/util/OfflineGeoIp.kt` - v5.0: MaxMind GeoLite2 offline lookups (Country+ASN), unlimited, zero-latency
- `app/app/src/main/java/com/hostshield/util/AppCategoryResolver.kt` - v5.1: App category resolution (Play Store categories + heuristic fallback)
- `app/app/src/main/java/com/hostshield/util/LanDetector.kt` - v5.1: RFC1918/RFC4193 private IP detection for LAN toggle
- `app/app/src/main/java/com/hostshield/util/LeakTester.kt` - v5.2: WebRTC leak test (WebView + RTCPeerConnection) + IPv6 leak test
- `app/app/src/main/java/com/hostshield/util/AppPrivacyScorer.kt` - Per-app A-F privacy grades
- `app/app/src/main/java/com/hostshield/util/ImportExportUtil.kt` - Multi-format import/export + firewall rules
- `app/app/src/main/java/com/hostshield/service/AutomationReceiver.kt` - Rate-limited automation API (v6.0: +SET_PROFILE, SET_DNS, PAUSE)
- `app/app/src/main/java/com/hostshield/service/SafeSearchEnforcer.kt` - v6.0: DNS-level safe search rewriting (Google, Bing, DDG, YouTube)
- `app/app/src/main/java/com/hostshield/util/DnsBenchmark.kt` - v6.0: DNS resolver latency benchmark (UDP, concurrent)
- `app/app/src/main/java/com/hostshield/service/LocalDnsServer.kt` - v6.0: Local DNS server on port 5353 ("portable Pi-hole"). v6.2: Supports DoH/DoT encrypted upstream forwarding
- `app/app/src/main/java/com/hostshield/service/DotResolver.kt` - v6.0: DNS-over-TLS resolver (RFC 7858, TLSv1.3, 4 providers)
- `app/app/src/main/java/com/hostshield/util/EncryptedBackup.kt` - v6.0: AES-256-GCM encrypted backups (PBKDF2, HSBACKUP format)
- `app/app/src/main/java/com/hostshield/util/DnsStampParser.kt` - v6.0: sdns:// DNS stamp parser/encoder (DNSCrypt spec)
- `app/app/src/main/java/com/hostshield/util/SchedulePresets.kt` - v6.0: Named schedule presets (Focus, Sleep, Family, Work, Kids)
- `app/app/src/main/java/com/hostshield/service/AppDnsRuleEngine.kt` - v6.1: Per-app domain DNS rules (wildcard + exact match, allow > block)
- `app/app/src/main/java/com/hostshield/service/ContentFilterManager.kt` - v6.1: 12+ content filter categories with suffix matching
- `app/app/src/main/java/com/hostshield/service/DnsProxyService.kt` - v6.1: No-VPN proxy mode DNS blocking (port 5353). v6.2: Supports DoH/DoT encrypted upstream forwarding
- `app/app/src/main/java/com/hostshield/util/QrConfigSharing.kt` - v6.1: QR code config sharing (GZIP+Base64)
- `app/app/src/main/java/com/hostshield/service/ParentalControlManager.kt` - v6.1: Age-profile parental controls with PIN lock
- `app/app/src/main/java/com/hostshield/util/TlsFingerprinter.kt` - v6.1: JA3/JA4 TLS ClientHello fingerprinting
- `app/app/src/main/java/com/hostshield/util/CrashReporter.kt` - v6.1: Custom crash reporting (UncaughtExceptionHandler, JSON files)
- `app/app/src/main/java/com/hostshield/util/WebDavSync.kt` - v6.1: WebDAV cloud sync (OkHttp, PROPFIND, basic auth)
- `app/app/src/main/java/com/hostshield/service/ConnectionTracker.kt` - v6.1: Real-time per-app connection tracking (ring buffer, SharedFlow)
- `app/app/src/main/java/com/hostshield/ui/components/VicoCharts.kt` - v6.1: 5 Vico chart composables (line, column, donut, histogram, horizontal bar)
- `app/app/src/main/java/com/hostshield/ui/components/ShieldAnimation.kt` - v6.1: Lottie shield animation composables
- `app/app/src/main/java/com/hostshield/ui/components/AnimatedLogFeed.kt` - v6.1: Animated live DNS log feed with entry/exit animations
- `app/app/src/main/java/com/hostshield/ui/widget/HostShieldGlanceWidget.kt` - v6.1: Jetpack Glance widget (toggle + stats)
- `app/app/src/main/java/com/hostshield/service/ScreenStateReceiver.kt` - ContextState for firewall
- `app/app/src/main/java/com/hostshield/ui/screens/sources/BlocklistGalleryScreen.kt` - Curated gallery (70+)
- `app/app/src/main/java/com/hostshield/ui/screens/settings/AutomationAuditScreen.kt` - Audit log viewer
- `app/app/src/main/java/com/hostshield/data/model/Entities.kt` - Room entities (11 tables)
- `app/app/src/main/java/com/hostshield/data/database/Migrations.kt` - DB migrations (v5-v12)
- `app/app/src/main/java/com/hostshield/di/DatabaseModule.kt` - Hilt DI (DB + singleton OkHttpClient)
- `app/app/src/main/assets/curated_blocklists.json` - 70+ categorized blocklist definitions
- `app/app/src/main/java/com/hostshield/ui/screens/settings/ContentFilterScreen.kt` - v6.2: Content filter category toggles (15 categories)
- `app/app/src/main/java/com/hostshield/ui/screens/settings/ParentalControlScreen.kt` - v6.2: Age profile selector, PIN lock, restricted category display
- `app/app/src/main/java/com/hostshield/ui/screens/settings/DnsBenchmarkScreen.kt` - v6.2: DNS resolver latency benchmark UI
- `app/app/src/main/java/com/hostshield/ui/screens/settings/WebDavSyncScreen.kt` - v6.2: WebDAV server config, test, file listing
- `app/app/src/main/java/com/hostshield/ui/screens/settings/CrashReporterScreen.kt` - v6.2: Crash report viewer with expandable stack traces
- `app/app/src/main/java/com/hostshield/ui/screens/settings/QrConfigScreen.kt` - v6.2: QR code config export/import with ZXing rendering
- `app/app/src/main/java/com/hostshield/ui/screens/settings/TlsFingerprintScreen.kt` - v6.2: JA3/JA4 fingerprint viewer (timeline + by-app views)
- `app/app/src/main/java/com/hostshield/service/DoqResolver.kt` - v6.2: DNS-over-QUIC (RFC 9250) with QUIC Initial framing and DoT fallback
- `app/app/src/main/java/com/hostshield/service/WireGuardProxy.kt` - v6.2: WireGuard DNS proxy with Noise handshake and transport encryption

## Screens (31+)
Home, Sources, Rules, Stats, Settings, Logs, Apps, AppPrivacy, AppLogs, Firewall (DNS/Network/Context tabs), ConnectionLog, DnsTools, NetworkStats, OverlapAnalysis, DnsLeakTest, RuleTest, HostsEditor, HostsDiff, AppExclusions, Onboarding (with Private DNS warning), BlocklistGallery, AutomationAudit, ContentFilter, ParentalControls, DnsBenchmark, WebDavSync, CrashReports, QrConfig, TlsFingerprints

## Build
```bash
cd app
./gradlew assembleFullRelease  # Signed release (needs env vars or debug keystore fallback)
./gradlew assembleFullDebug    # Full flavor (root features)
./gradlew assemblePlayDebug    # Play Store flavor
```
- JDK: Android Studio bundled JBR at `/c/Program Files/Android/Android Studio/jbr`
- SDK: `$LOCALAPPDATA/Android/Sdk` (set in `app/local.properties`)
- Signing: env vars `KEYSTORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (falls back to debug keystore)
- Release keystore: `~/repos/HostShield/hostshield-release.keystore` (alias: hostshield, pass: hostshield123)

## CI/CD
- `.github/workflows/release.yml` — triggers on tag push (`v*`) or `workflow_dispatch`
- `ubuntu-latest` runner, `working-directory: app`, `./gradlew assembleFullRelease`
- Decodes `KEYSTORE_BASE64` secret at build time, signs APK
- Renames to `HostShield-v{VERSION}-full-release.apk`, uploads to GitHub release
- Secrets configured: `KEYSTORE_BASE64`, `KEY_ALIAS`, `KEY_PASSWORD`, `STORE_PASSWORD`

## Version History
- v6.2.0: Bug fixes + UI completion audit + **release hardening audit**. **Bugs fixed**: TCP DNS hostname extraction operator precedence (affected both IPv4/IPv6 TCP DNS blocking), missing ContentCategory enum values (VPN_PROXY, MALWARE, SOCIAL) that caused compile errors in ParentalControlManager. **Release audit fixes (v6.2.0 hardening)**: Operator precedence bugs (`and 0xFF shl 8` → `(and 0xFF) shl 8`) fixed across 6 files — DnsCache.kt (12), CnameCloakDetector.kt (13), DnsPacketBuilder.kt (1), RootDnsLogger.kt (1), DnsVpnService.kt (~30), DoqResolver.kt fallback mapping. WireGuardProxy security fix: `encryptTransport()` returned plaintext on crypto failure → now returns null. OkHttp response leaks fixed in 8 files (DohResolver, DotResolver, SourceDownloader, DomainAgeChecker, GeoIpLookup, DohBypassUpdater, UpdateChecker) via `response.use{}` or try-finally. LocalDnsServer `scope.cancel()` killing @Singleton permanently → recreate scope in stop(). OfflineGeoIp `isPrivateIp()` overly broad 172.x check fixed (was matching 172.2.x.x non-private). LogsScreen `selectedEntry!!` crash → safe local val capture. RootUtil shell command injection → single-quote escaping. HomeViewModel/StatsViewModel `while(true)` → `while(isActive)`. DnsVpnService `excludedApps`/`blockedApps` missing `@Volatile`. LeakTester null safety on `socket.localAddress`. ContentFilterManager duplicate twitch.tv removed. ProGuard rules added for DoqResolver, WireGuardProxy, NflogReader, RootDnsLogger, BlockNotificationService, BlockingScheduleWorker. **New integrations**: ConnectionTracker wired into logAsyncRich, TlsFingerprinter wired into packet loop for JA3/JA4, category logging for content filter/parental blocks. **New screens (7)**: ContentFilter, ParentalControls, DnsBenchmark, WebDavSync, CrashReports, QrConfig, TlsFingerprints. DoQ resolver (RFC 9250). WireGuard DNS proxy. **52/52 roadmap items complete**
- v6.1.0: Domain-per-app DNS rules (AppDnsRuleEngine — wildcard + exact match, allow > block precedence, DB v12), content filtering categories (ContentFilterManager — 12+ toggleable categories with suffix matching), proxy mode (DnsProxyService — no-VPN no-root tri-mode blocking), QR config sharing (QrConfigSharing — GZIP+Base64 encode/decode), parental controls (ParentalControlManager — 3 age profiles + PIN lock), crash reporter, WebDAV cloud sync, connection tracker
- v6.0.0: Major feature release — 15+ new features. Threat intel packet loop integration, NetworkTrackerDb (200+ domains), DB v11, enhanced privacy scoring, DNS-only VPN, QS tile live stats, expanded Tasker intents, Safe Search enforcement, DNS benchmark, local DNS server, DNS-over-TLS, encrypted backups (AES-256-GCM), DNS stamp parser (sdns://), named schedule presets (5 modes)
- v5.2.0: Privacy & Security (Phase 3 partial) — expanded tracker DB (405 ETIP signatures, 8 categories), threat intelligence feeds (URLhaus, Spamhaus DROP, Emerging Threats, Disconnect — radix trie IP + domain lookup), captive portal handling (NetworkCallback + auto-pause/resume VPN)
- v5.0.0: Core engine upgrades (Phase 1 complete 11/11) — adblock-syntax parser, two-tier DNS cache (L1+L2), serve-stale (RFC 8767), negative/SERVFAIL caching, prefetching, hash set fast path, filter decision cache, CNAME cloak DBs, SVCB/HTTPS parsing, offline GeoIP (MaxMind), TTL caps. Phase 2 partial: country-based blocking, LAN toggle, app category resolver. Phase 3 partial: WebRTC + IPv6 leak tests
- v4.6.0: DNS latency sparkline on Home (live response time mini-graph), source summary stats on Sources screen (total domains, size, unhealthy count), search history persistence (DataStore, 10 recent, chip display), search history chips on Home
- v4.5.0: Query type distribution chart in Stats (A/AAAA/CNAME/MX bar chart), per-app DNS log drill-down (AppLogsScreen with domains + timeline tabs), permanent block/allow buttons in log detail sheet, log cleanup worker improved (6h interval, battery-not-low constraint)
- v4.4.0: Connection log interface labels (rmnet0=Mobile, wlan0=WiFi, etc), DNS cache management in Settings (clear cache button + live stats), expanded notification (Pause 5m / Pause 30m / Stop), top querying apps mini-card on Home dashboard
- v4.3.2: UI fixes — FlowRow wrapping for category chips (was smushed in single Row), larger text fields (search bar, custom DNS), FlowRow for DoH provider selector and feature pills, removed fixed heights that clipped text
- v4.3.1: Bug audit — fix AutomationReceiver rate limiting (static companion state), GeoIpLookup atomic CAS window reset, SourceHealthWorker ensures alert channel exists, pauseResumeJob @Volatile, baselineRates synchronized list
- v4.3.0: Notification pause/resume action (5-min pause from notification), CNAME CLOAK badge in log detail sheet, pretty upstream server labels (DoH:Cloudflare), source health DEAD notifications (push alert), alerts notification channel, pause state bypasses blocking
- v4.2.0: Fix DNS log data starvation (CNAME chains, resolved IPs, latency, upstream server now written to DB), CNAME-blocked domains now logged, fd error tracking + auto-restart on TUN error, IPv6 DoH support (honours useDoH flag), IPv6 DNS cache lookup, DohBypassUpdater uses shared OkHttpClient, app context threaded through all forward methods
- v4.1.0: Custom upstream DNS UI in Settings, firewall rule export/import (JSON), automation audit log viewer screen, query rate anomaly detection (3x baseline warning on Home), dropped queries banner, cache hit rate on Home
- v4.0.0: Automation API rate limiting + audit logging, GeoIP rate limit backoff, shared OkHttpClient pooling, tracker scanner Room caching, VPN stability metrics (uptime/rebuilds/errors/drops), DNS cache stats in Stats screen, VPN Health card, log buffer overflow detection, DB v9
- v3.9.0: Private DNS conflict warning in onboarding, smart DNS latency-based failover, GeoIP/ASN/country in DNS log details, automation broadcast security fix, IPv6 TCP DNS support
- v3.8.0: Curated blocklist gallery (70+ lists), Exodus tracker SDK detection, context-aware firewall, regex DoS protection, bounded log buffer, LRU cache fix
- v3.7.0: App privacy report, rule sync URLs, blocked domain trends, per-app scoring, category toggles
- v3.6.0: Live query rate, hosts editor, Pi-hole import, deep links, notification actions
- v3.5.0: Rule tester, temporary allow, domain age check, stats widget, search history
- v3.4.0: Privacy score, scheduled blocking, query type filter, suspicious TLD, batch health
- v3.3.0: DNS leak test, clipboard import, accent colors, auto backup, IP blocking, pinning
- v3.2.0: App shortcuts, widget, bulk log, latency chart, network profiles, regex, reputation
- v3.1.0: DoH scheduling, multi-DNS, auto update, allowlists, overlap analysis, CSV export
- v3.0.0: DNS cache, CNAME cloaking, trend charts, diagnostic export, CI/CD
- v2.1.0: Automation API, iptables firewall, connection logging
- v2.0.0: DoH, DNS trap, batch domain test, network stats

## Research & Roadmap
- Full competitive analysis: `docs/RESEARCH.md` (30+ open-source projects, 52-item roadmap across 6 phases)
- **All 52 roadmap items COMPLETE** as of v6.2.0. Phases: v5.0 (core engine), v5.1-5.2 (privacy/security), v6.0 (network intel + encrypted DNS), v6.1 (content filtering + parental + UI), v6.2 (DoQ + WireGuard + bug fixes + release hardening)

## Key Competitor References
- RethinkDNS (4.7k stars): Domain-per-app rules, metered/unmetered, Go firestack, succinct radix-trie (17M domains)
- AdGuard urlfilter: Reference adblock-syntax engine with 6-level priority system, CNAME tracker database
- AdAway (8.9k stars): Hosts-file-as-fallback pattern, local web server for blocked responses, systemless Magisk
- PCAPdroid (3.8k stars): nDPI integration for JA3/JA4 fingerprinting, PCAP-NG streaming
- NetGuard (3.5k stars): Screen on/off rules (most popular feature), native C sinkhole, DNS-only VPN routing
- InviZible Pro (2.5k stars): Tri-mode (VPN/root/proxy), DNSCrypt+Tor+I2P, iptables refresh on connectivity change
- TrackerControl (2.4k stars): Dual static+network tracker detection model
- hagezi (16k stars): 7-format blocklist output, tiered blocking (Light→Ultimate)
- DNS66 (2.2k stars): DNS-only VPN routing (port 53 only) — minimal battery (~0.5%/day)

## Gotchas
- v5.0: DnsCache.get() now returns CacheResult (not ByteArray?) — callers must handle .response, .isStale, .needsPrefetch. Use getSimple() for backward-compat ByteArray? return.
- v5.0: DnsCache.getStale() exists for serve-stale path — call when upstream fails to get expired-but-valid entry
- v5.0: DnsCache.CacheStats has new fields: failureSize, staleHits, prefetchTriggers — update UI consumers
- v5.0: BlocklistHolder.decisionCache is auto-cleared on every update() call — no manual invalidation needed
- v5.0: BlocklistHolder.exactBlockSet is built alongside trie in update() — O(1) for exact matches, trie still needed for wildcards
- v5.0: CnameCloakDetector now checks HTTPS/SVCB records (TYPE 64/65) for SVCB-based cloaking
- v5.0: CnameCloakUpdater must be injected via Hilt and called from HostsUpdateWorker alongside DohBypassUpdater
- v5.0: CnameCloakDetector.cnameCloakDomains is @Volatile set — call updateCloakDatabase() or loadCached() on startup
- v5.0: OfflineGeoIp requires GeoLite2 MMDB files in app assets or internal storage. Falls back gracefully if missing — isReady() returns false
- v5.0: OfflineGeoIp.initialize() copies from assets on first run. Call once in HostShieldApp.onCreate() via appScope
- v5.0: GeoIpLookup (ip-api.com) is legacy — use OfflineGeoIp for new features. GeoIpLookup retained for city-level detail (GeoLite2-City is 70MB, not bundled)
- v5.0: DnsPacketBuilder.queryTypeLabel() maps qtype ints to human labels (A, AAAA, SVCB, HTTPS, etc.)
- v5.0: MaxMind GeoIP2 dependency added to build.gradle.kts (`com.maxmind.geoip2:geoip2:4.2.1`)
- v5.0: AdblockRuleParser auto-detected by HostsParser.isAdblockFormat() — checks first 100 lines, >20% `||` prefix triggers adblock mode
- v5.0: HostsParser.parse() returns only block domains (ParsedHost) for backward compat. Use parseAdblock() for full DnsRule details (allow, $important, etc.)
- v5.0: HostsUpdateWorker extracts @@|| allow rules from adblock-syntax sources and subtracts them alongside ALLOWLIST sources
- v5.0: AdblockRuleParser treats `||domain^` as implicit wildcard (blocks domain + all subdomains), matching AdGuard behavior
- v5.0: $dnsrewrite rules are skipped (logged, not implemented). $all, $popup, $domain= browser modifiers cause rule to be skipped
- v5.0: DnsDiskCache is a separate SQLite DB (`dns_cache.db`), NOT Room. Ephemeral data — safe to delete without affecting user data
- v5.0: DnsDiskCache.loadAll() called in startVpn() AFTER rebuildBlocklist(). Persisted every ~60s from log flusher via exportForDisk()
- v5.0: DnsCache.warmFromDisk() does NOT overwrite existing in-memory entries — fresh L1 entries take priority over L2
- v5.0: clearCacheCallback now clears both L1 (DnsCache) and L2 (DnsDiskCache) when user taps "Clear Cache" in Settings
- Room stores enums as strings — adding new enum values doesn't need migration
- HostsUpdateWorker has DohBypassUpdater injected — runs on every periodic cycle regardless of blocking state. v5.0: also inject CnameCloakUpdater
- OverlapAnalysisScreen downloads sources with `forceDownload=true` to bypass ETag caching
- Regex rules limited to 500 chars with nested quantifier rejection (ReDoS prevention)
- Log buffer is LinkedBlockingQueue(5000) — uses `offer()` instead of `add()`, tracks dropped count
- DB version is 12. Migration chain: v1-v5 inline in DatabaseModule, v5-v12 in Migrations.kt. v11→v12 adds app_dns_rules table, v10→v11 adds tracker columns to dns_logs, v9→v10 adds blocked_countries to firewall_rules, v8→v9 added tracker_scan_cache, automation_audit_log, vpn_stability tables
- v5.1: FirewallRule.blockedCountries is comma-separated ISO codes (e.g., "CN,RU,IR"). Empty string = no country blocking
- v5.1: getContextAwareRules() query now includes blocked_countries != '' condition
- TrackerSignatureDb reads raw dex bytes from APK — scans cached in Room, invalidated on app version change
- ContextState.register() must be called in startVpn(), unregister() in stopVpn()
- Context-aware firewall requires Usage Stats permission for foreground app detection
- GeoIpLookup uses ip-api.com (free tier, 45 req/min) — rate limiting + exponential backoff on 429
- DohResolver smart DNS prefers fastest measured provider, falls back through all on failure
- Automation API has per-action rate limiting (5s cooldown) and audit log with 30-day retention
- IPv6 TCP RST uses separate checksum function (computeTcpChecksumV6) with IPv6 pseudo-header
- Private DNS warning shows in onboarding AND re-checks on Home screen resume
- `gradlew` lives in `app/` not repo root — CI workflow uses `working-directory: app`
- Release keystore is gitignored (*.keystore in .gitignore)
- Shared OkHttpClient is provided as Hilt singleton in DatabaseModule — inject instead of creating new instances
- Firewall export UIDs are device-specific — importer must resolve UIDs from package names
- Query anomaly baseline needs 10 samples (~50s) before detection activates
- DnsVpnService.currentCacheStats and currentDroppedQueries are companion @Volatile fields polled by UI
- forwardUdp/forwardDoH/forwardUdpV6 all log rich data (CNAME, IPs, latency) — processIpv4Dns/v6 only log basic for blocked/cache hits
- processIpv6Dns now has DoH + cache lookup (was plaintext-only before v4.2.0)
- forwardDoH is dual-mode: `wrapV6=true` wraps response as IPv6 packet instead of IPv4
- packetLoop auto-restarts VPN on unexpected exit (fd error) while `isRunning=true`
- ACTION_PAUSE with `pause_minutes` extra; 0 = resume immediately
- isPaused flag checked in isDomainBlocked — all queries allowed while paused
- SourceHealthWorker tracks newly-DEAD sources and posts notification via ALERT_CHANNEL_ID
- ALERT_CHANNEL_ID created alongside VPN channel in createNotificationChannel()
- v5.2: CaptivePortalHandler uses ALERT_CHANNEL_ID for login notification (NOTIFICATION_ID=42). Registered in startVpn() gated by captivePortalHandling pref (default true). Unregistered in stopVpn()
- v5.2: CaptivePortalHandler pauses VPN for 3 minutes (not indefinitely) — if portal login takes longer, user re-enters normally
- v5.2: CaptivePortalHandler has two NetworkCallbacks: one for CAPTIVE_PORTAL detection, one for VALIDATED to auto-resume
- v5.2: ThreatIntelManager persists feeds to JSON file (threat_intel_cache.json in app internal storage). loadCached() warms from disk on VPN start
- v5.2: ThreatIntelManager.IpRadixTrie stores CIDR prefixes as binary trie — O(32) worst case for IPv4. Not used for IPv6 CIDRs currently
- v5.2: ThreatIntelWorker scheduled in HostShieldApp.onCreate() — runs every 24h with network constraint
- v5.2: TrackerSignatureDb expanded to 405 signatures across 8 categories. Two new categories: "Fingerprinting", "Identification". cachedToScanResult() resolves by name from master list — adding new trackers won't break existing cache entries (they just won't show new ones until re-scan)
- v5.2: androidx.browser:browser:1.8.0 dependency added for Custom Tabs (captive portal login)
- v6.0: NetworkTrackerDb uses suffix matching (parent domain traversal) — a query for "pixel.ad.doubleclick.net" matches "doubleclick.net". Built-in database is loaded in init{}, no disk I/O
- v6.0: Every DNS log entry is auto-enriched with tracker_category and tracker_owner via logAsyncRich(). No explicit caller changes needed — lookup happens inside logAsyncRich
- v6.0: DB migration v10→v11 adds tracker_category and tracker_owner TEXT columns to dns_logs (default '')
- v6.0: ThreatIntelManager.refreshFeeds() now delegates to refreshFeedsAndPersist() — the old saveToDisk() with empty CIDR bug is eliminated
- v6.0: ThreatIntelManager is wired into processIpv4Dns/processIpv6Dns (domain check before DNS resolution) and all 4 forwarding methods (IP check after resolution). Gated by threatIntelEnabled preference
- v6.0: ThreatIntelManager.downloadFeed() uses response.use{} to prevent OkHttp socket leaks
- v6.0: AppPrivacyScorer now requires @ApplicationContext for permission analysis. ScoreBreakdown added to AppReport (breaking change for any code constructing AppReport directly, but only generated internally). Removed old trackerPatterns list — now uses NetworkTrackerDb for live domain matching
- v6.0: AppPrivacyScorer permission analysis uses PackageManager.GET_PERMISSIONS + requestedPermissionsFlags to check *granted* dangerous permissions only (not just declared)
- v6.0: DNS-only mode (dnsOnlyMode pref, default false) skips DNS trap + DoH bypass IP routes in VPN builder AND skips per-app/context-aware firewall checks in processIpv4Dns/processIpv6Dns. Domain blocklist + threat intel + CNAME cloak detection still active. VPN restart required to change mode (routes are set at establish() time)
- v6.0: SafeSearchEnforcer intercepts DNS queries BEFORE blocklist check in processIpv4Dns/processIpv6Dns. Returns A record with safe IP (300s TTL). Only handles A queries — AAAA queries for safe-search domains will resolve normally (search engines handle AAAA→A fallback)
- v6.0: AutomationReceiver.ACTION_PAUSE uses coroutine delay — if the process is killed during pause, the resume won't fire. Acceptable tradeoff vs WorkManager complexity for a user-initiated temporary pause
- v6.0: DnsBenchmark uses raw DatagramSocket for UDP DNS queries — must be called from a context where sockets are allowed (not inside VPN protect() scope). Runs on Dispatchers.IO
- v6.0: HostShieldTileService reads DnsVpnService.currentBlockedCount (companion @Volatile) for subtitle. Updated every 100 blocks in updateNotification(). Reset on VPN restart
- v6.0: LocalDnsServer runs on port 5353 (not 53) to avoid requiring root. LAN devices need to specify port. Uses BlocklistHolder.isBlocked() for filtering — shares the same blocklist as VPN mode. Server socket runs on Dispatchers.IO with coroutine-per-query concurrency
- v6.0: DotResolver creates a fresh TLS connection per query (no pooling). This is intentional — DoT is a fallback/user-choice upstream, not the primary path. For high-throughput, DoH with OkHttp connection pooling is preferred. TLSv1.3 with SNI + hostname verification via default HostnameVerifier
- v6.0: EncryptedBackup uses PBKDF2WithHmacSHA256 with 100K iterations — slow on purpose for brute-force resistance. isEncryptedBackup() checks first 8 bytes for "HSBACKUP" magic. Wrong password throws AEADBadTagException (GCM auth failure)
- v6.0: DnsStampParser uses android.util.Base64 with URL_SAFE|NO_PADDING|NO_WRAP flags. LP-encoded fields use high-bit continuation flag in length byte for hash chains per DNSCrypt spec
- v6.0: SchedulePresets.applyPreset() takes sourceIds as String (comma-separated) — caller must resolve category names to actual source IDs from the HostSource table
- v6.1: AppDnsRuleEngine.checkDomain() is called from VPN packet loop hot path — must be O(1). ConcurrentHashMap<packageName, List<CompiledRule>> ensures thread safety without locks. ALLOW rules take precedence over BLOCK (whitelist > blacklist). Returns null when no rule matches — caller falls back to blocklist
- v6.1: AppDnsRuleEngine.loadRules() must be called in startVpn() to warm the cache. reloadForApp() is cheaper than full reload for single-app rule edits
- v6.1: ContentFilterManager is checked AFTER per-app rules but BEFORE isDomainBlocked() in the packet loop. Order: firewall → safe search → per-app rules → content filter → blocklist → threat intel
- v6.1: ContentFilterManager.isBlocked() takes a Set<ContentCategory> parsed from the StringSet preference. Preference stores category enum names as strings
- v6.1: DB migration v11→v12 creates app_dns_rules table with UNIQUE(package_name, domain) index
- v6.1: DnsProxyService runs on port 5353 — same as LocalDnsServer. They share the pattern but DnsProxyService is a foreground Service for Android lifecycle management
- v6.1: QrConfigSharing uses "HS:" prefix for scheme detection. GZIP compressed before Base64 to keep QR codes scannable
- v6.1: ParentalControlManager.shouldBlock() delegates to ContentFilterManager with profile-specific category sets. Child blocks 9 categories, Teen blocks 6, Adult blocks none. PIN is stored as SHA-256 hash — no plaintext persistence
- v6.1: Parental control check order in packet loop: content filter (user-chosen categories) → parental controls (age-profile categories) → blocklist. Both use ContentFilterManager.isBlocked() but with different category sets
- v6.1: ParentalControlManager.loadState() must be called in startVpn(). The enabled/profile fields are @Volatile for hot-path reads
- v6.1: Vico charts use CartesianChartModelProducer with runTransaction + lineSeries/columnSeries. Dark theme compatible — set transparent chart backgrounds. Vico 2.0 API (not 1.x)
- v6.1: Lottie shield_animation.json is in res/raw/. ShieldAnimation uses LottieCompositionSpec.RawRes. Iterations = IterateForever for continuous loop
- v6.1: Glance widgets use GlanceAppWidget + GlanceAppWidgetReceiver pattern. Widget state via SharedPreferences ("hostshield_widget_prefs"). Must register receivers in AndroidManifest
- v6.1: AnimatedLogFeed is a drop-in replacement for the static LiveLogRow loop in HomeScreen. Uses key(entry.id) for stable identity during animations
- v6.1: Onboarding expanded to 6 pages. FeaturesOverviewPage uses staggered LaunchedEffect delays for sequential card animations. DnsConfigPage selection state is local (doesn't persist yet — wire to AppPreferences in UI integration)
- v6.1: blockedCount/allowedCount in DnsVpnService are now AtomicInteger — use .get(), .set(), .incrementAndGet() instead of direct Int operations
- v6.1: build.gradle.kts now includes vico:compose-m3:2.0.1, lottie-compose:6.6.2, glance-appwidget:1.1.1, glance-material3:1.1.1
- v6.2: TCP DNS hostname extraction had operator precedence bug — `and 0xFF shl 8` evaluated as `and (0xFF shl 8)` = `and 0xFF00`, always producing dnsLen=0. Fixed to `(x and 0xFF) shl 8`. Both IPv4 and IPv6 TCP DNS handlers affected
- v6.2: ContentCategory enum now has 15 entries (added VPN_PROXY, MALWARE, SOCIAL). ParentalControlManager references all 15
- v6.2: ConnectionTracker.recordConnection() is called from logAsyncRich() in DnsVpnService — every DNS query (blocked and allowed) populates the ring buffer
- v6.2: TlsFingerprinter is wired via tryTlsFingerprint() in the packet loop — runs on non-DNS TCP packets, extracts JA3/JA4 from TLS ClientHello
- v6.2: Content filter and parental control blocks now use logAsyncRich() with trackerCategory="ContentFilter:{category}" or "Parental:{category}" for category-level logging
- v6.2: WebDAV preferences (webdavUrl, webdavUsername, webdavPassword) added to AppPreferences DataStore
- v6.2: 5 new navigation routes: CONTENT_FILTER, PARENTAL_CONTROLS, DNS_BENCHMARK, WEBDAV_SYNC, CRASH_REPORTS
- v6.2: SettingsScreen has new "Protection" section for content filtering and parental controls
- v6.2: TlsFingerprinter now has persistent ring buffer (500 entries) with record()/getHistory()/getByApp()/clearHistory() API. DnsVpnService calls record() after fingerprinting
- v6.2: QrConfigScreen uses ZXing (com.google.zxing:core:3.5.3) for QR bitmap rendering — no camera/scanner (string paste import only)
- v6.2: DoqResolver sends DNS in QUIC Initial STREAM frame on stream 0. Falls back to DoT automatically. Not a full QUIC implementation — requires DoQ-mode servers (AdGuard, Nextdns, Mullvad)
- v6.2: WireGuardProxy uses simplified Noise_IKpsk2 handshake with AES-256-GCM transport. DNS-only tunneling (no general traffic routing). WgConfig data class holds all tunnel parameters
- v6.2: Navigation has 9 total new SubScreen routes across v6.2: CONTENT_FILTER, PARENTAL_CONTROLS, DNS_BENCHMARK, WEBDAV_SYNC, CRASH_REPORTS, QR_CONFIG, TLS_FINGERPRINTS
- v6.2: forwardEncrypted() unified dispatch: WireGuard > DoQ > DoT > DoH > UDP. All 12 forwarding call sites (IPv4+IPv6) route through it. Each encrypted forwarder has its own fallback chain (e.g., DoQ → DoH → UDP)
- v6.2: DotResolver is now wired as a first-class upstream option with dotEnabled/dotProvider preferences, UI toggle + 4-provider selector, and forwardDoT() in DnsVpnService
- v6.2: IPv6 TLS fingerprinting (tryTlsFingerprintV6) — previously only IPv4 non-DNS TCP got JA3/JA4 analysis
- v6.2: DnsProxyService + LocalDnsServer now support DoH/DoT encrypted upstream forwarding (previously plaintext UDP only)
- v6.2 AUDIT: Kotlin operator precedence — `x.toInt() and 0xFF shl 8` means `x.toInt() and (0xFF shl 8)` = `and 0xFF00`. Must be `(x.toInt() and 0xFF) shl 8`. This bug class existed in all DNS wire format parsing. Fixed across DnsCache, CnameCloakDetector, DnsPacketBuilder, RootDnsLogger, DnsVpnService
- v6.2 AUDIT: WireGuardProxy.encryptTransport() returns ByteArray? (nullable). Call sites must handle null (encryption failure) — do NOT fall back to plaintext
- v6.2 AUDIT: OkHttp responses MUST be closed — use `response.use{}` or try-finally with `response.close()`. Unclosed responses leak sockets
- v6.2 AUDIT: LocalDnsServer is @Singleton — scope.cancel() in stop() kills the scope permanently. Must recreate: `scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` after cancel
- v6.2 AUDIT: OfflineGeoIp.isPrivateIp() parses second octet for 172.16-31.x.x range. Do NOT use startsWith("172.2") which matches 172.2.x.x (not private)
- v6.2 AUDIT: RootUtil shell commands must escape single quotes in hostnames: `hostname.replace("'", "'\\''")`. Unescaped hostnames allow command injection
- v6.2 AUDIT: Compose mutable state `!!` in lambdas can race to null. Capture to local val first: `val entry = selectedEntry ?: return`
- v6.2 AUDIT: Coroutine loops inside viewModelScope.launch must use `while(isActive)` not `while(true)` for proper cancellation cleanup
