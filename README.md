# HostShield

![Version](https://img.shields.io/badge/version-6.5.2-blue)
![License](https://img.shields.io/badge/license-GPL--3.0-green)
![Platform](https://img.shields.io/badge/platform-Android%208+-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Status](https://img.shields.io/badge/status-active-success)

> System-wide DNS-based ad/tracker/malware blocker for Android with per-app firewall, CNAME cloaking detection, serve-stale DNS caching, DoH with certificate pinning, offline GeoIP, and a professional AMOLED dark UI.

---

## Quick Start

1. Download the latest APK from [Releases](https://github.com/SysAdminDoc/HostShield/releases)
2. Install and launch — the onboarding wizard guides you through setup
3. Choose **VPN mode** (no root) or **Root mode** (better battery life)
4. Enable blocking — ads and trackers are filtered immediately

---

## How It Works

```
                           ┌─────────────────────────────────────┐
                           │         HostShield Engine           │
┌─────────┐    DNS Query   │                                     │
│  App on  │──────────────>│  ┌───────────┐   ┌───────────────┐  │
│  Device  │               │  │ Blocklist │   │  DNS Cache    │  │
│          │<──────────────│  │ Holder    │   │  (LRU + Stale │  │
└─────────┘    Response    │  │           │   │  + Prefetch)  │  │
                           │  │ Hash Set  │   └───────┬───────┘  │
                           │  │ + Trie    │           │          │
                           │  │ + Regex   │    Miss   │  Hit     │
                           │  └─────┬─────┘     ┌─────▼──────┐   │
                           │        │           │  Upstream   │   │
                           │   Blocked?         │  DNS / DoH  │   │
                           │    ┌───┴───┐       └─────┬──────┘   │
                           │    │       │             │          │
                           │  ┌─▼──┐  ┌─▼──┐   ┌─────▼──────┐   │
                           │  │ NX │  │0.0.│   │CNAME Cloak │   │
                           │  │DOM │  │0.0 │   │ Detection  │   │
                           │  └────┘  └────┘   │+ SVCB/HTTPS│   │
                           │                   └────────────┘   │
                           └─────────────────────────────────────┘
```

**VPN Mode** (no root): Creates a local-only VPN tunnel. All DNS queries pass through HostShield's packet engine. No traffic leaves the device to any remote server.

**Root Mode**: Redirects DNS via iptables NAT rules to a local proxy on `127.0.0.1:5454`. Zero battery overhead. Per-app firewall via iptables.

---

## Features

### DNS Blocking Engine

| Feature | Description |
|---------|-------------|
| **Trie + Hash Set Lookup** | O(1) hash set fast path for exact matches (~90% of queries), O(m) reversed-label trie for wildcards. 200K+ domains. |
| **Filter Decision Cache** | LRU cache (8K entries) for `isBlocked()` results — skips trie entirely for hot domains |
| **CNAME Cloaking Detection** | Inspects full CNAME chains + SVCB/HTTPS records (TYPE 64/65). Checks against main blocklist + dedicated AdGuard/NextDNS CNAME cloak databases |
| **DNS Response Cache** | 2000-entry LRU with serve-stale (RFC 8767), negative caching (RFC 2308), SERVFAIL caching (RFC 9520), and Unbound-style prefetching |
| **Serve-Stale (RFC 8767)** | Returns expired cache entries during WiFi/cellular transitions. 3-day stale window, 30s stale TTL. Background refresh on stale serve |
| **Cache Prefetching** | When TTL < 10% remaining and domain queried 3+ times, serves from cache and refreshes in background. Near-zero latency for popular domains |
| **Configurable TTL** | 60s minimum floor, 24h maximum ceiling. SOA-derived TTL for NXDOMAIN negative caching |
| **Block Response Types** | NXDOMAIN (with SOA), Null IP (0.0.0.0/::), or REFUSED — configurable per preference |
| **Regex & Wildcard Rules** | Block/allow domains by regex pattern (capped at 500 chars, ReDoS-safe) or wildcard (`*.example.com`) |
| **DoH Bypass Prevention** | Blocks 65+ known DoH provider domains + wildcard patterns. Remote-updatable via GitHub-hosted JSON |

### Encrypted DNS

| Feature | Description |
|---------|-------------|
| **DNS-over-HTTPS (DoH)** | RFC 8484 POST+GET. Cloudflare, Google, Quad9, NextDNS, AdGuard, Mullvad, CleanBrowsing |
| **DNS-over-TLS (DoT)** | RFC 7858, TLSv1.3, SNI + hostname verification. Cloudflare, Google, Quad9, AdGuard |
| **DNS-over-QUIC (DoQ)** | RFC 9250, QUIC Initial framing. AdGuard, NextDNS, Mullvad. Falls back to DoT |
| **DNS-over-WireGuard** | Noise_IKpsk2 handshake, AES-256-GCM transport encryption. DNS-only WireGuard tunnel |
| **Certificate Pinning** | SHA-256 pin validation per provider, unpinned fallback as last resort |
| **Smart Latency Failover** | EMA-based latency tracking per provider, auto-selects fastest, falls back through all on failure |
| **DNS Trap** | Routes hardcoded DNS IPs (8.8.8.8, 1.1.1.1, etc.) through VPN tunnel to prevent bypass |
| **TCP DNS** | Full TCP DNS support for responses >512 bytes, IPv4 + IPv6 |
| **IPv6 Support** | Full dual-stack DNS processing + UID attribution via `/proc/net/tcp6` |

### Per-App Firewall (Root)

| Feature | Description |
|---------|-------------|
| **AFWall+-Style Rules** | Per-app Wi-Fi / mobile data / VPN blocking via iptables, 20+ interface patterns |
| **BLACKLIST / WHITELIST Modes** | Block selected apps or allow only selected apps |
| **Context-Aware Rules** | Screen on/off detection + metered network detection + foreground app tracking |
| **Connection Logging** | Per-connection log with interface labels (rmnet0=Mobile, wlan0=WiFi) |
| **Firewall Export/Import** | JSON export/import of firewall rules (UIDs resolved by package name) |

### Privacy & Tracking Analysis

| Feature | Description |
|---------|-------------|
| **Tracker SDK Scanner** | Exodus-style APK dex scanning for ~60 tracker SDK signatures. Room-cached, 7-day TTL, invalidated on app version change |
| **App Privacy Report** | A-F grade per app based on tracker SDK count, permissions, and DNS behavior |
| **Privacy Score** | 0-100 protection rating based on current configuration (blocklists, DoH, firewall) |
| **Suspicious TLD Detection** | Flags queries to high-abuse TLDs (.tk, .xyz, .onion, etc.) |
| **Domain Age Check** | Flags newly registered domains via RDAP lookup |
| **Domain Reputation** | One-tap VirusTotal, URLhaus, and Whois lookup from log detail |

### GeoIP & Network Intelligence

| Feature | Description |
|---------|-------------|
| **Offline GeoIP** | MaxMind GeoLite2 Country + ASN databases (~14MB). Unlimited, zero-latency, no rate limits |
| **Online GeoIP Fallback** | ip-api.com for city-level detail (rate-limited, 40 req/min with exponential backoff) |
| **Country Flags** | Emoji flag display next to resolved IPs in DNS logs |
| **ASN Lookup** | ISP/organization identification for every connection |

### Blocklist Management

| Feature | Description |
|---------|-------------|
| **Curated Gallery** | 70+ categorized blocklists (Ads, Trackers, Malware, Adult, Social, Crypto, Allowlist) |
| **Source Categories** | ADS, TRACKERS, MALWARE, ADULT, SOCIAL, CRYPTO, ALLOWLIST, CUSTOM |
| **Allowlist Sources** | Curated allowlists (Anudeep, HaGeZi) subtracted from blocklist during updates |
| **Overlap Analysis** | Identify redundant domains across enabled sources to optimize subscriptions |
| **Source Health Check** | Batch reachability test + staleness detection. Push notification for DEAD sources |
| **Hosts Diff** | Track new/removed domains between blocklist updates |
| **Remote Rule Sync** | Subscribe to remote rule lists that auto-sync during periodic updates |
| **CNAME Cloak Database** | Auto-updated from AdGuard cname-trackers + NextDNS cname-cloaking-blocklist |

### DNS Logs & Analytics

| Feature | Description |
|---------|-------------|
| **Live Query Stream** | Real-time DNS log feed via SharedFlow with search, filter, and export |
| **Per-Query Detail** | Query type, response time, upstream server, CNAME chain, resolved IPs, GeoIP |
| **7-Day Trend Charts** | Blocked vs. total queries line chart, hourly bar chart, daily history |
| **DNS Latency Chart** | Per-hour average and peak response time with sparkline on Home |
| **Query Type Distribution** | A/AAAA/CNAME/MX/TXT bar chart in Stats |
| **Per-App DNS Logs** | Drill-down per app with domains + timeline tabs |
| **Query Rate Monitor** | Real-time queries/min and blocks/min on dashboard with 3x anomaly detection |
| **Bulk Log Actions** | Multi-select domains to block/allow in batch |
| **Search History** | 10 recent searches persisted in DataStore, displayed as chips |
| **Stats CSV Export** | Export daily stats, top blocked domains, and top apps |

### Automation & Scheduling

| Feature | Description |
|---------|-------------|
| **Automation API** | Broadcast intents for Tasker/MacroDroid: ENABLE, DISABLE, STATUS, REFRESH_BLOCKLIST, PAUSE |
| **Rate-Limited API** | 5-second per-action cooldown with full audit logging to Room DB |
| **Scheduled Blocking** | Auto-enable/disable by time (bedtime mode, work hours) |
| **Blocking Profiles** | Switch between profile sets on schedule |
| **Network-Aware Profiles** | Auto-switch blocking profiles by WiFi SSID |

### UI & Experience

| Feature | Description |
|---------|-------------|
| **AMOLED Dark Theme** | Material 3 dark UI optimized for OLED displays |
| **6 Accent Colors** | Teal, Blue, Purple, Green, Pink, Peach |
| **31+ Screens** | Home, Sources, Rules, Stats, Settings, Logs, Apps, AppPrivacy, AppLogs, Firewall, ConnectionLog, DnsTools, NetworkStats, OverlapAnalysis, DnsLeakTest, RuleTest, HostsEditor, HostsDiff, AppExclusions, Onboarding, BlocklistGallery, AutomationAudit, ContentFilter, ParentalControls, DnsBenchmark, WebDavSync, CrashReports, QrConfig, TlsFingerprints |
| **Home Dashboard** | Shield status, live query rate, cache hit rate, latency sparkline, top queried apps, category toggles, search history chips |
| **Widgets** | Toggle widget + stats widget (blocked count, queries, block rate) |
| **Quick Settings Tile** | VPN toggle from Quick Settings panel |
| **App Shortcuts** | Long-press launcher: Toggle, Refresh Lists, Open Logs |
| **Deep Links** | `hostshield://logs`, `hostshield://stats`, etc. |
| **Onboarding Wizard** | Private DNS conflict detection, VPN permission, battery optimization |

### Content Filtering & Parental Controls

| Feature | Description |
|---------|-------------|
| **15 Content Categories** | Gaming, Streaming, Social Media, News, Shopping, Dating, Gambling, Adult, VPN/Proxy, Malware, and more — toggleable per category |
| **Parental Controls** | 3 age profiles (Child, Teen, Adult) with automatic category blocking per profile |
| **PIN Lock** | SHA-256 hashed PIN protects parental control settings from bypass |
| **Local DNS Server** | "Portable Pi-hole" mode on port 5353 — other LAN devices can use phone as DNS filter |
| **DNS Proxy Mode** | No-VPN, no-root DNS blocking via local proxy (tri-mode: VPN / Root / Proxy) |
| **Safe Search Enforcement** | DNS-level rewriting for Google, Bing, DuckDuckGo, YouTube |

### Import, Export & Backup

| Feature | Description |
|---------|-------------|
| **Multi-Format Import** | HostShield JSON, AdAway, Blokada, NextDNS, Pi-hole Teleporter, plain hosts |
| **Firewall Export** | JSON export/import of firewall rules |
| **Auto Backup** | Scheduled backup to app storage with 5-backup rotation |
| **Diagnostic Export** | One-tap shareable report: device info, config, logs, network state |
| **Clipboard Import** | Quick-paste domains to bulk-add as block rules |

---

## Build

```bash
# Prerequisites: JDK 17, Android SDK 35
cd app

# Full flavor — GitHub/F-Droid release (root features, QUERY_ALL_PACKAGES)
./gradlew assembleFullRelease    # Signed release
./gradlew assembleFullDebug      # Debug build

# Play Store flavor (limited app visibility, no QUERY_ALL_PACKAGES)
./gradlew assemblePlayDebug

# Tests
./gradlew testFullDebugUnitTest
```

**Signing**: Set env vars `KEYSTORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` or falls back to debug keystore.

**CI/CD**: `.github/workflows/release.yml` triggers on tag push (`v*`) — builds, signs, and uploads APK to GitHub Releases.

---

## Configuration

### Blocklist Sources

Ships with curated defaults (Steven Black, OISD, HaGeZi, 1Hosts). Add custom URL sources via Settings > Sources in standard hosts file format.

**Source categories**: ADS, TRACKERS, MALWARE, ADULT, SOCIAL, CRYPTO, ALLOWLIST, CUSTOM. Allowlist sources are subtracted from the blocklist during updates.

### Upstream DNS

Default: system DNS. Configure custom upstream DNS servers (comma-separated) in Settings. DoH providers: Cloudflare, Google, Quad9, NextDNS, AdGuard, Mullvad, CleanBrowsing.

### Block Response Type

Choose how blocked domains are handled:
- **NXDOMAIN** (default) — domain doesn't exist, includes SOA for negative caching
- **Null IP** — returns 0.0.0.0 (A) or :: (AAAA), connection fails immediately
- **REFUSED** — DNS server refuses the query

### Automation API

Broadcast intents for Tasker/MacroDroid (requires signature permission or ADB grant):

```bash
# Enable/disable protection
adb shell am broadcast -a com.hostshield.action.ENABLE  -n com.hostshield/.service.AutomationReceiver
adb shell am broadcast -a com.hostshield.action.DISABLE -n com.hostshield/.service.AutomationReceiver

# Query current status
adb shell am broadcast -a com.hostshield.action.STATUS  -n com.hostshield/.service.AutomationReceiver

# Force blocklist refresh
adb shell am broadcast -a com.hostshield.action.REFRESH_BLOCKLIST -n com.hostshield/.service.AutomationReceiver

# Pause/resume (5 minutes)
adb shell am broadcast -a com.hostshield.action.PAUSE --ei pause_minutes 5 -n com.hostshield/.service.AutomationReceiver
adb shell am broadcast -a com.hostshield.action.PAUSE --ei pause_minutes 0 -n com.hostshield/.service.AutomationReceiver
```

All actions are rate-limited (5s cooldown per action per caller) and logged to the automation audit log.

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt (Dagger) |
| Database | Room (11 tables, 12 migrations) |
| Preferences | DataStore |
| Async | Coroutines + Flow, ViewModels + StateFlow |
| Networking | OkHttp 4 (source downloads, DoH resolver) |
| Root | libsu (topjohnwu) |
| GeoIP | MaxMind GeoIP2 (GeoLite2-Country + ASN) |
| Build | Gradle KTS, KSP, Android SDK 35, minSdk 26 |

---

## Project Structure

```
app/src/main/java/com/hostshield/
├── data/
│   ├── database/      # Room DB, DAOs, converters, migrations (v1-v12)
│   ├── model/         # Entities (11 tables), enums
│   ├── preferences/   # DataStore preferences (AppPreferences)
│   ├── repository/    # HostShieldRepository
│   └── source/        # SourceDownloader
├── di/                # Hilt modules (DatabaseModule — DB + OkHttpClient singleton)
├── domain/
│   ├── BlocklistHolder.kt    # Trie + hash set + regex + wildcard engine
│   └── parser/
│       └── HostsParser.kt    # Hosts file parser with wildcard support
├── service/
│   ├── DnsVpnService.kt      # VPN packet loop (~2700 lines)
│   ├── DnsCache.kt           # LRU + serve-stale + prefetch + negative/failure cache
│   ├── DnsPacketBuilder.kt   # DNS wire format builder/parser
│   ├── DohResolver.kt        # DoH with smart latency failover
│   ├── DotResolver.kt        # DoT (RFC 7858, TLSv1.3, 4 providers)
│   ├── DoqResolver.kt        # DoQ (RFC 9250, QUIC Initial, 3 providers)
│   ├── WireGuardProxy.kt     # DNS-over-WireGuard (Noise_IKpsk2, AES-256-GCM)
│   ├── CnameCloakDetector.kt # CNAME + SVCB/HTTPS cloak detection
│   ├── CnameCloakUpdater.kt  # Remote CNAME cloak DB fetcher (AdGuard + NextDNS)
│   ├── DohBypassUpdater.kt   # Remote DoH bypass list fetcher
│   ├── RootDnsService.kt     # Root-mode DNS proxy
│   ├── RootDnsLogger.kt      # Root-mode DNS logging with UID attribution
│   ├── IptablesManager.kt    # Per-app firewall rule management
│   ├── LocalDnsServer.kt     # LAN DNS server on port 5353
│   ├── DnsProxyService.kt    # No-VPN proxy mode DNS blocking
│   ├── ContentFilterManager.kt # 15 content filter categories
│   ├── ParentalControlManager.kt # Age-profile parental controls + PIN
│   ├── AppDnsRuleEngine.kt   # Per-app domain DNS rules
│   ├── ConnectionTracker.kt  # Real-time per-app connection tracking
│   ├── ThreatIntelManager.kt # Threat intel feeds + radix trie IP lookup
│   ├── SafeSearchEnforcer.kt # DNS-level safe search rewriting
│   ├── NetworkStatsTracker.kt
│   ├── AutomationReceiver.kt # Broadcast intent API
│   ├── ScreenStateReceiver.kt # Context-aware firewall state
│   └── *Worker.kt            # HostsUpdate, AutoBackup, LogCleanup, etc.
├── ui/
│   ├── navigation/    # Compose navigation graph
│   ├── screens/       # 31+ screens (Home, Logs, Stats, Settings, Firewall, ...)
│   ├── components/    # Vico charts, Lottie animations, animated log feed
│   ├── widget/        # Glance widgets (toggle + stats)
│   └── theme/         # Material 3 theme + accent colors
└── util/
    ├── OfflineGeoIp.kt        # MaxMind GeoLite2 offline lookups
    ├── GeoIpLookup.kt         # ip-api.com online lookups (legacy)
    ├── TrackerSignatureDb.kt   # Exodus-style APK tracker scanner
    ├── TlsFingerprinter.kt    # JA3/JA4 TLS ClientHello fingerprinting
    ├── AppPrivacyScorer.kt     # Per-app A-F privacy grades
    ├── ImportExportUtil.kt     # Multi-format import/export
    ├── EncryptedBackup.kt      # AES-256-GCM encrypted backups
    ├── BackupRestoreUtil.kt    # Backup/restore to app storage
    ├── WebDavSync.kt           # WebDAV cloud sync
    ├── QrConfigSharing.kt      # QR code config sharing (GZIP+Base64)
    ├── CrashReporter.kt        # Custom crash reporting
    ├── DnsBenchmark.kt         # DNS resolver latency benchmark
    ├── DnsStampParser.kt       # sdns:// DNS stamp parser
    ├── DiagnosticExporter.kt   # One-tap diagnostic report
    ├── PcapExporter.kt         # PCAP packet capture export
    └── RootUtil.kt             # Root detection + binary management
```

---

## FAQ

**VPN mode vs Root mode?**
Root mode: zero battery overhead, requires rooted device with Magisk/KernelSU. VPN mode: works on any device, ~1-3% battery, persistent notification. Both use the same blocking engine.

**Why does it use a VPN?**
Entirely local — no traffic goes to a remote server. The VPN tunnel intercepts DNS queries on the device and filters them locally. Standard technique used by NetGuard, RethinkDNS, Blokada, and DNS66.

**How is this different from AdAway?**
CNAME cloaking detection (including SVCB/HTTPS records), serve-stale DNS cache (RFC 8767), DoH with certificate pinning and smart latency failover, per-app iptables firewall, live query streaming, 7-day trend charts, query anomaly detection, offline GeoIP, tracker SDK scanning, DNS leak test, automation API, and a modern Material 3 Compose UI.

**How is this different from RethinkDNS?**
HostShield focuses on hosts-based blocking with a curated gallery of 70+ blocklists. It has a dual-mode architecture (VPN + root) while RethinkDNS is VPN-only. HostShield includes an iptables-based per-app firewall for rooted devices, tracker SDK scanning, and hosts file diffing.

**Does it work with other VPNs?**
In VPN mode: no — Android only allows one VPN at a time. In root mode: yes — iptables rules work alongside any VPN.

**Does it send data to any server?**
No. All DNS filtering happens locally on-device. The only network requests are: downloading blocklist sources (user-configured URLs), DoH queries to the user-selected DNS provider, GeoIP database updates (MaxMind), and optional remote DoH bypass / CNAME cloak list updates from GitHub.

**What about battery life?**
VPN mode: ~1-3% battery/day (all traffic routed through local TUN interface). Root mode: ~0% additional battery (iptables operates at kernel level). The DNS cache (60-70% hit rate) and serve-stale reduce upstream queries significantly.

---

## Version History

| Version | Highlights |
|---------|-----------|
| **6.5.2** | Android 16 always-on VPN recovery advisory. Detects the always-on + lockdown + validated-network + zero-tunnel-ingress pattern, surfaces a Home recovery banner, and offers a rooted device restart action for the post-update VPN-stack corruption case. |
| **6.5.1** | Premium UX/UI polish pass. Refined Compose shape and typography consistency, improved first-run onboarding layout and copy, fixed page-indicator/CTA collisions, converted the feature overview to a compact grid, anchored DNS resolver actions, moved Sources/Rules actions into header controls, improved loading/empty/error/selection/accessibility states, fixed debug automation permission side-by-side install, and corrected WebDAV failed-listing handling. |
| **6.5.0** | Engineering hardening pass. Parental PIN fail-closed + brute-force lockout, PBKDF2 iterations raised to 600k, backup decrypt off-by-one fixed, RootUtil hostname injection guard, device-transfer no longer leaks encrypted prefs, widget receiver no longer launchable by other apps. ACTION_PAUSE > 10s now works via WorkManager (was killed by `goAsync()` timeout). TCP DNS fallback on TC=1 (RFC 7766). BlocklistHolder atomic snapshot + real LRU decision cache. DoH/DoT response size caps + cert-pin diagnostics. DnsCache RFC 2308 MINIMUM=0 honored, RR cap raised. Onboarding DNS choice now persisted. Sources URL validation + category picker. Settings update-check throttled. CHANGELOG / README version drift repaired. |
| **6.4.0** | Security hardening audit, architecture refactor, reliability improvements. See [`app/CHANGELOG.md`](app/CHANGELOG.md). |
| **6.3.0** | Preferences facade over 6 domain managers, BlocklistHolder unified trie walk, DB v14 composite indices, PBKDF2 PIN hashing, encrypted backups, DoH fail-closed, HTTPS-only sync URLs, SHA-256 integrity, RootUtil shell injection fixes. |
| **6.2.0** | DoQ resolver (RFC 9250), WireGuard DNS proxy, 7 new UI screens, ConnectionTracker + TlsFingerprinter wired in. **Release hardening audit**: fixed ~60 operator precedence bugs in DNS wire format parsing across 6 files, WireGuard encryption failure no longer leaks plaintext, OkHttp response leaks fixed in 8 files, shell command injection prevention in root mode, CoroutineScope lifecycle fix in LocalDnsServer, private IP range validation fix, Compose crash safety, ProGuard rules for all new classes. **52/52 roadmap items complete** |
| **6.1.0** | Per-app DNS rules, content filtering (15 categories), proxy mode, QR config sharing, parental controls, crash reporter, WebDAV sync, connection tracker, Vico charts, Lottie animations, Glance widgets |
| **6.0.0** | Threat intel integration, NetworkTrackerDb, Safe Search enforcement, DNS benchmark, local DNS server, DoT resolver, encrypted backups, DNS stamps, schedule presets |
| **5.0.0** | Serve-stale DNS (RFC 8767), SERVFAIL caching (RFC 9520), cache prefetching, hash set fast path (~2x), filter decision LRU cache, CNAME cloak databases (AdGuard+NextDNS), SVCB/HTTPS record parsing, offline GeoIP (MaxMind GeoLite2), configurable TTL caps |
| 4.6.0 | DNS latency sparkline, source summary stats, search history persistence |
| 4.5.0 | Query type distribution chart, per-app DNS log drill-down, permanent block/allow in log detail |
| 4.4.0 | Connection log interface labels, DNS cache management in Settings, expanded notification actions |
| 4.3.x | UI fixes (FlowRow wrapping), bug audit (rate limiting, atomic state) |
| 4.2.0 | DNS log data enrichment (CNAME chains, resolved IPs, latency), fd error tracking, IPv6 DoH |
| 4.1.0 | Custom upstream DNS, firewall export/import, automation audit log, query anomaly detection |
| 4.0.0 | Automation API, GeoIP rate limiting, shared OkHttpClient, tracker scanner Room caching, VPN stability metrics |
| 3.9.0 | Private DNS warning, smart DNS failover, GeoIP in logs, IPv6 TCP DNS |
| 3.8.0 | Curated blocklist gallery (70+), Exodus tracker detection, context-aware firewall, regex DoS protection |
| 3.7.0 | App privacy report, rule sync URLs, blocked domain trends |
| 3.0.0 | DNS cache, CNAME cloaking, trend charts, diagnostic export, CI/CD |
| 2.0.0 | DoH, DNS trap, iptables firewall, connection logging |

---

## Contributing

Issues and PRs welcome. Please run `./gradlew testFullDebugUnitTest` before submitting.

The `gradlew` script lives in the `app/` directory, not the repo root.

---

## License

[GPL-3.0](LICENSE)
