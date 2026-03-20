# HostShield

## Overview
Modern, AMOLED-dark hosts-based ad blocker app for Android. Inspired by AdAway. v4.3.1.

## Tech Stack
- Kotlin, Jetpack Compose, Material 3
- Hilt for dependency injection
- Room + DataStore for persistence
- Coroutines + Flow for async, ViewModels + StateFlow for UI state
- OkHttp for source downloads + DoH resolver, libsu for root access

## Key Architecture
- **BlocklistHolder** - Trie-based O(m) domain lookup, 200K+ domains, volatile root for thread safety. Regex rules capped at 500 chars with nested quantifier rejection (ReDoS prevention).
- **DnsVpnService** - Local VPN DNS interception, TUN interface, dual-stack (IPv4+IPv6), DNS trap, DoH/DoT blocking, TCP DNS RST for both IPv4 and IPv6. Bounded log buffer (LinkedBlockingQueue 5000) with overflow detection. Context-aware firewall checks (screen off/background/metered). VPN stability tracking (uptime, rebuilds, fd errors, dropped queries). Publishes cache stats + dropped count via companion object for UI.
- **DohResolver** - RFC 8484 POST+GET, certificate pinning, smart latency-based failover (EMA per provider, auto-selects fastest), unpinned fallback as last resort.
- **RootDnsLogger** - Root-mode DNS proxy on 127.0.0.1:5454, iptables NAT redirect, UID attribution.
- **IptablesManager** - AFWall+-style per-app firewall, 20+ interface patterns, BLACKLIST/WHITELIST modes.
- **HostsUpdateWorker** - Periodic blocklist refresh + DoH bypass list update + allowlist subtraction. Uses shared singleton OkHttpClient (injected via Hilt).
- **TrackerSignatureDb** - Exodus-style APK dex scanning for ~60 tracker SDK signatures. Results cached in Room DB (tracker_scan_cache table), invalidated on app version change. 7-day cache TTL. Progress callback for UI.
- **GeoIpLookup** - ip-api.com with in-memory cache, rate limiting (40 req/min window), exponential backoff on 429.
- **ContextState** - Screen on/off receiver + metered network detection + foreground app tracking. Drives context-aware firewall rules.
- **PrivateDnsDetector** - Detects Strict/Automatic Private DNS that bypasses VPN filtering. Shows in onboarding + persistent Home re-check on resume.
- **AutomationReceiver** - Broadcast intent API with rate limiting (5s per action per caller), full audit logging to Room DB. Audit log viewable in Settings > Tools.
- **ImportExportUtil** - Supports HostShield JSON, AdAway, Blokada, NextDNS, Pi-hole, plain hosts. Firewall rule JSON export/import added in v4.1.0.

## Source Categories
- ADS, TRACKERS, MALWARE, ADULT, SOCIAL, CRYPTO, **ALLOWLIST**, CUSTOM
- ALLOWLIST sources subtracted from blocklist during updates (not added to block trie)

## Key Files
- `app/app/src/main/java/com/hostshield/service/DnsVpnService.kt` - VPN packet loop (~1850 lines)
- `app/app/src/main/java/com/hostshield/domain/BlocklistHolder.kt` - Trie + regex + wildcard engine
- `app/app/src/main/java/com/hostshield/service/DohResolver.kt` - DoH with smart latency failover
- `app/app/src/main/java/com/hostshield/service/DnsCache.kt` - LRU DNS cache with TTL
- `app/app/src/main/java/com/hostshield/service/DnsPacketBuilder.kt` - DNS wire format builder/parser
- `app/app/src/main/java/com/hostshield/util/TrackerSignatureDb.kt` - APK tracker SDK scanner (Room-cached)
- `app/app/src/main/java/com/hostshield/util/GeoIpLookup.kt` - GeoIP/ASN lookup (rate limited)
- `app/app/src/main/java/com/hostshield/util/AppPrivacyScorer.kt` - Per-app A-F privacy grades
- `app/app/src/main/java/com/hostshield/util/ImportExportUtil.kt` - Multi-format import/export + firewall rules
- `app/app/src/main/java/com/hostshield/service/AutomationReceiver.kt` - Rate-limited automation API
- `app/app/src/main/java/com/hostshield/service/ScreenStateReceiver.kt` - ContextState for firewall
- `app/app/src/main/java/com/hostshield/ui/screens/sources/BlocklistGalleryScreen.kt` - Curated gallery (70+)
- `app/app/src/main/java/com/hostshield/ui/screens/settings/AutomationAuditScreen.kt` - Audit log viewer
- `app/app/src/main/java/com/hostshield/data/model/Entities.kt` - Room entities (10 tables)
- `app/app/src/main/java/com/hostshield/data/database/Migrations.kt` - DB migrations (v1-v9)
- `app/app/src/main/java/com/hostshield/di/DatabaseModule.kt` - Hilt DI (DB + singleton OkHttpClient)
- `app/app/src/main/assets/curated_blocklists.json` - 70+ categorized blocklist definitions

## Screens (23+)
Home, Sources, Rules, Stats, Settings, Logs, Apps, AppPrivacy, Firewall (DNS/Network/Context tabs), ConnectionLog, DnsTools, NetworkStats, OverlapAnalysis, DnsLeakTest, RuleTest, HostsEditor, HostsDiff, AppExclusions, Onboarding (with Private DNS warning), BlocklistGallery, AutomationAudit

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

## Gotchas
- Room stores enums as strings — adding new enum values doesn't need migration
- HostsUpdateWorker has DohBypassUpdater injected — runs on every periodic cycle regardless of blocking state
- OverlapAnalysisScreen downloads sources with `forceDownload=true` to bypass ETag caching
- Regex rules limited to 500 chars with nested quantifier rejection (ReDoS prevention)
- Log buffer is LinkedBlockingQueue(5000) — uses `offer()` instead of `add()`, tracks dropped count
- DB version is 9 (v8->v9 adds tracker_scan_cache, automation_audit_log, vpn_stability tables)
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
