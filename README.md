# HostShield

![Version](https://img.shields.io/badge/version-6.9.25-blue)
![License](https://img.shields.io/badge/license-GPL--3.0-blue)
![Platform](https://img.shields.io/badge/platform-Android%208+-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Status](https://img.shields.io/badge/status-active-success)

> System-wide DNS-based ad/tracker/malware blocker for Android with per-app firewall, CNAME cloaking detection, serve-stale DNS caching, fail-closed DoH certificate pinning, rate-limited GeoIP enrichment, and a professional AMOLED dark UI with an optional high-contrast AMOLED mode.

---

## Screenshots

<p align="center">
  <img src="assets/screenshots/dashboard.png" alt="HostShield dashboard showing active protection" width="180">
  <img src="assets/screenshots/sources.png" alt="HostShield sources list with default sources enabled" width="180">
  <img src="assets/screenshots/rules.png" alt="HostShield rules screen" width="180">
  <img src="assets/screenshots/stats.png" alt="HostShield statistics screen" width="180">
  <img src="assets/screenshots/settings.png" alt="HostShield settings screen" width="180">
</p>

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
| **DNS-over-QUIC (DoQ)** | Experimental simplified engine, not a full QUIC/TLS 1.3 stack. Falls back to DoT; production defaults remain pinned DoH/DoT |
| **DNS-over-WireGuard** | Experimental DNS-only simplified engine, not a full WireGuard tunnel. Production defaults remain pinned DoH/DoT |
| **Certificate Pinning** | Fail-closed SHA-256 pin validation per provider from a versioned local manifest with primary/backup pins and review/expiry diagnostics |
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
| **Tracker SDK Scanner** | Exodus-style APK dex scanning for 405 tracker SDK signatures. Room-cached, 7-day TTL, invalidated on app version change |
| **App Privacy Report** | A-F grade per app based on tracker SDK count, permissions, and DNS behavior |
| **Privacy Score** | 0-100 protection rating based on current configuration (blocklists, DoH, firewall) |
| **Suspicious TLD Detection** | Flags queries to high-abuse TLDs (.tk, .xyz, .onion, etc.) |
| **Domain Age Check** | Flags newly registered domains via RDAP lookup |
| **Domain Reputation** | One-tap VirusTotal, URLhaus, and Whois lookup from log detail |

### GeoIP & Network Intelligence

| Feature | Description |
|---------|-------------|
| **GeoIP Lookup** | ipapi.co over HTTPS for city-level detail, client-side rate-limited below the free-tier cap with exponential backoff |
| **Country Flags** | Emoji flag display next to resolved IPs in DNS logs |
| **ASN Lookup** | ISP/organization identification for every connection |

### Blocklist Management

| Feature | Description |
|---------|-------------|
| **Curated Gallery** | 50+ categorized blocklists with tier, size, and breakage warnings for aggressive packs |
| **Enabled Defaults** | AdAway Default and StevenBlack Unified are enabled on fresh installs and upgraded existing installs |
| **Source Categories** | ADS, TRACKERS, MALWARE, ADULT, SOCIAL, CRYPTO, ALLOWLIST, CUSTOM |
| **Allowlist Sources** | Subscribed allowlists (Anudeep, HaGeZi) override blocklist entries and can show neutralized-domain examples |
| **Overlap Analysis** | Identify redundant domains across enabled sources to optimize subscriptions |
| **Source Health Check** | Batch reachability test + staleness detection. Push notification for DEAD sources |
| **Source Impact Preview** | Preview enabled source updates before applying them, including added/removed entry counts and recent DNS queries whose verdict would change |
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
| **High-Contrast AMOLED Mode** | Optional pure-black palette with brighter text, semantic colors, chart colors, warning states, and widget surfaces |
| **6 Accent Colors** | Teal, Blue, Purple, Green, Pink, Peach |
| **31+ Screens** | Home, Sources, Rules, Stats, Settings, Logs, Apps, AppPrivacy, AppLogs, Firewall, ConnectionLog, DnsTools, NetworkStats, OverlapAnalysis, DnsLeakTest, RuleTest, HostsEditor, HostsDiff, AppExclusions, Onboarding, BlocklistGallery, AutomationAudit, ContentFilter, ParentalControls, DnsBenchmark, WebDavSync, CrashReports, QrConfig, TlsFingerprints |
| **Home Dashboard** | Shield status, live query rate, cache hit rate, latency sparkline, top queried apps, category toggles, search history chips |
| **Widgets** | Toggle widget + stats widget (blocked count, queries, block rate) |
| **Quick Settings Tile** | VPN toggle from Quick Settings panel |
| **App Shortcuts** | Long-press launcher: Toggle, Refresh Lists, Open Logs |
| **Deep Links** | `hostshield://logs`, `hostshield://stats`, etc. |
| **Onboarding Wizard** | Private DNS conflict detection, VPN permission, battery optimization |
| **TalkBack Semantics** | Primary screens expose headings, stateful toggles/filters, disabled action states, destructive labels, and progress announcements |
| **Dynamic Type Safety** | Dense onboarding, settings, source, log, firewall, and warning surfaces avoid fixed-height clipping under larger system font scales |

### Content Filtering & Parental Controls

| Feature | Description |
|---------|-------------|
| **15 Content Categories** | Gaming, Streaming, Social Media, News, Shopping, Dating, Gambling, Adult, VPN/Proxy, Malware, and more — toggleable per category |
| **Parental Controls** | 3 age profiles (Child, Teen, Adult) with automatic category blocking per profile |
| **PIN Lock** | Argon2id PIN lock protects parental control settings from bypass and forces legacy SHA-256 hashes through an upgrade prompt |
| **Local DNS Server** | "Portable Pi-hole" mode on port 5353 — private-network clients can use the phone as a DNS filter |
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

```powershell
# Prerequisites: JDK 17+, Android SDK 36
cd C:\Users\--\repos\HostShield

# Full flavor — GitHub/F-Droid release (root features, QUERY_ALL_PACKAGES)
.\app\gradlew.bat -p app :app:assembleFullRelease    # Signed release
.\app\gradlew.bat -p app :app:assembleFullDebug      # Debug build

# Play Store flavor (limited app visibility, no QUERY_ALL_PACKAGES)
.\app\gradlew.bat -p app :app:assemblePlayDebug

# Tests
.\app\gradlew.bat -p app :app:testFullDebugUnitTest

# Release doc/provenance checks
powershell -ExecutionPolicy Bypass -File .\tools\check-release-docs.ps1
powershell -ExecutionPolicy Bypass -File .\tools\check-cronet-posture.ps1
powershell -ExecutionPolicy Bypass -File .\tools\release-provenance.ps1
```

**Signing**: Release artifacts require `KEYSTORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. For local non-distribution release verification only, set `HOSTSHIELD_ALLOW_DEBUG_RELEASE_SIGNING=true` to use the Android debug keystore.

**CI/CD**: `.github/workflows/release.yml` triggers on tag push (`v*`) — builds, signs, and uploads APK to GitHub Releases.

---

## Configuration

### Blocklist Sources

Ships with curated defaults. AdAway Default and StevenBlack Unified are enabled out of the box; OISD, HaGeZi, 1Hosts, and other sources remain available from the gallery for stricter filtering. Add custom URL sources via Settings > Sources in hosts, domains-only, or DNS adblock syntax.

**Source categories**: ADS, TRACKERS, MALWARE, ADULT, SOCIAL, CRYPTO, ALLOWLIST, CUSTOM. Allowlist sources override block entries during updates, including adblock `@@||` exception lists.

### Upstream DNS

Default: system DNS. Configure custom upstream DNS servers (comma-separated) in Settings. DoH providers: Cloudflare, Google, Quad9, NextDNS, AdGuard, Mullvad, CleanBrowsing.

### Block Response Type

Choose how blocked domains are handled:
- **NXDOMAIN** (default) — domain doesn't exist, includes SOA for negative caching
- **Null IP** — returns 0.0.0.0 (A) or :: (AAAA), connection fails immediately
- **REFUSED** — DNS server refuses the query

### Automation API

Broadcast intents for Tasker/MacroDroid, shell, and same-signature companion apps. Canonical actions use `com.hostshield.ACTION_*`; older lowercase `com.hostshield.action.*` aliases are accepted for compatibility.

```bash
# Enable/disable protection
adb shell am broadcast -a com.hostshield.ACTION_ENABLE  -n com.hostshield/.service.AutomationReceiver
adb shell am broadcast -a com.hostshield.ACTION_DISABLE -n com.hostshield/.service.AutomationReceiver

# Query current status
adb shell am broadcast -a com.hostshield.ACTION_STATUS  -n com.hostshield/.service.AutomationReceiver

# Force blocklist refresh
adb shell am broadcast -a com.hostshield.ACTION_REFRESH_BLOCKLIST -n com.hostshield/.service.AutomationReceiver

# Apply profile or custom DNS
adb shell am broadcast -a com.hostshield.ACTION_SET_PROFILE --es profile_name Work -n com.hostshield/.service.AutomationReceiver
adb shell am broadcast -a com.hostshield.ACTION_SET_DNS --es dns_servers "9.9.9.9,149.112.112.112" -n com.hostshield/.service.AutomationReceiver

# Pause/resume (5 minutes)
adb shell am broadcast -a com.hostshield.ACTION_PAUSE --ei duration_minutes 5 -n com.hostshield/.service.AutomationReceiver
adb shell am broadcast -a com.hostshield.ACTION_PAUSE --ei duration_minutes 0 -n com.hostshield/.service.AutomationReceiver
```

Public actions are protected by HostShield's signature-level automation permission, rate-limited (5s cooldown per action per caller), and logged to the automation audit log.

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 2.3 |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt (Dagger) |
| Database | Room (11 entities, explicit v1-v15 migrations) |
| Preferences | DataStore |
| Async | Coroutines + Flow, ViewModels + StateFlow |
| Networking | OkHttp 5 (source downloads, pinned DoH resolver) |
| Root | libsu (topjohnwu) |
| GeoIP | Bounded ipapi.co lookup with in-memory cache and exponential backoff |
| Build | Gradle KTS, version catalog, AGP 9.2, KSP, Android SDK 36 compile / targetSdk 36, minSdk 26 |

---

## Project Structure

```
app/src/main/java/com/hostshield/
├── data/
│   ├── database/      # Room DB, DAOs, converters, migrations (v1-v15)
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
│   ├── DoqResolver.kt        # Experimental simplified DoQ path
│   ├── WireGuardProxy.kt     # Experimental simplified DNS-over-WireGuard path
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
│   └── theme/         # Material 3 theme, high-contrast AMOLED palette, accent colors
└── util/
    ├── GeoIpLookup.kt         # bounded ipapi.co GeoIP lookups
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
Root mode: zero battery overhead, requires rooted device with Magisk, KernelSU, or APatch. VPN mode: works on any device, ~1-3% battery, persistent notification. Both use the same blocking engine.

**Which root frameworks are supported?**
Magisk, KernelSU, and APatch are supported when HostShield has root permission. Magisk 26+ firewall commands use libsu's mount-master shell. KernelSU and APatch use the default `su` shell; hosts-file editing needs an active systemless hosts module such as bindhosts because those frameworks do not ship built-in systemless hosts. HostShield detects Magisk `hosts`, bindhosts, and the KernelSU systemless-hosts module paths under `/data/adb/modules/` when present. Without one, root DNS and firewall rules can still work through iptables, but direct hosts-file edits may fail on read-only `/system`.

**Why does it use a VPN?**
Entirely local — no traffic goes to a remote server. The VPN tunnel intercepts DNS queries on the device and filters them locally. Standard technique used by NetGuard, RethinkDNS, Blokada, and DNS66.

**How is this different from AdAway?**
CNAME cloaking detection (including SVCB/HTTPS records), serve-stale DNS cache (RFC 8767), fail-closed DoH with certificate pinning and selected-provider preference, per-app iptables firewall, live query streaming, 7-day trend charts, query anomaly detection, rate-limited GeoIP lookup, tracker SDK scanning, DNS leak test, automation API, and a modern Material 3 Compose UI.

**How is this different from RethinkDNS?**
HostShield focuses on local DNS blocking with a curated gallery of 50+ blocklists. It has a dual-mode architecture (VPN + root) while RethinkDNS is VPN-only. HostShield includes an iptables-based per-app firewall for rooted devices, tracker SDK scanning, and hosts file diffing.

**Does it work with other VPNs?**
In VPN mode: no — Android only allows one VPN at a time. In root mode: yes — iptables rules work alongside any VPN.

**What happens on Android 15/16 if protection is killed or denied at boot?**
Protection services use Android's `systemExempted` foreground-service type for VPN/root/proxy filtering, and each service records timeout events before shutting down cleanly. If Android denies a boot or background restart, HostShield records `foreground_service_start_failed` in the local diagnostic export and the Home screen asks the user to reopen the app and enable protection again.

**Does it send data to any server?**
No. All DNS filtering happens locally on-device. The only network requests are: downloading blocklist sources (user-configured URLs), encrypted DNS queries to the user-selected provider, optional rate-limited GeoIP lookup through ipapi.co, and optional remote DoH bypass / CNAME cloak list updates from GitHub.

**What about the Android 16 VPN update bug?**
Android 16 has a confirmed system-level bug where VPN apps become unusable after a background app update while always-on VPN is active. The device's network stack enters a corrupted state where all connections time out even though the VPN tunnel appears connected. This affects all VPN-based apps (Mullvad, Proton, Ivanti, TunnelBear, and others have confirmed it). Google has not issued a fix. HostShield detects this condition automatically (since v6.5.2) and shows a recovery banner on the Home screen. **Workaround**: reboot the device, or uninstall and reinstall the app. On rooted devices, HostShield offers a one-tap device restart from the recovery banner. To prevent the issue, disable always-on VPN before updating HostShield, then re-enable it after the update completes.

**What about Work Profiles and Private Spaces?**
Android Work Profiles managed by a DPC (Device Policy Controller) can override VPN settings. Known limitations: (1) The DPC may force its own always-on VPN, preventing HostShield's VPN from starting. Use root mode instead. (2) Some DPCs set a lockdown VPN that bypasses user VPN tunnels entirely, causing DNS queries from work apps to leak to Google DNS or the DPC's resolver. (3) Work Profile DNS resolution may route through a separate network stack that HostShield cannot intercept in VPN mode. Android 15's Private Spaces create an isolated user profile. HostShield detects Private Spaces (since v6.5.1) and shows a warning banner when VPN-based DNS filtering may not cover apps inside the private space. **Workaround**: use root mode for full coverage across all profiles, or enable always-on VPN with lockdown in Android settings to force all profiles through HostShield's tunnel.

**What about battery life?**
VPN mode: ~1-3% battery/day (all traffic routed through local TUN interface). Root mode: ~0% additional battery (iptables operates at kernel level). The DNS cache (60-70% hit rate) and serve-stale reduce upstream queries significantly.

---

## Version History

| Version | Highlights |
|---------|-----------|
| **6.9.25** | Backup schema v2 search history, empty-catch logging, unused resource cleanup, DoH3 dead-code annotation. |
| **6.9.24** | HomeViewModel deduplication (shared blocklist build, combined pref observers) and lint baseline burndown (14 fixes). |
| **6.9.23** | Security/correctness audit: LocalDnsServer fail-closed, BlocklistHolder most-specific-wins trie, WebDavSync path-traversal rejection, Pi-hole ReDoS guard, CI SHA pinning, release lint gate, manifest alias cleanup, LogsScreen ViewModel extraction. |
| **6.9.22** | Threat-intel blocked log details now include review actions for a global domain allow or an app-scoped DNS allow rule for the affected app/domain pair. |
| **6.9.21** | Stats now shows local threat-intel impact by feed with 24h/7d block counts, last matched time, top affected domains/apps, and a compact 7-day feed trend. |
| **6.9.20** | Main app navigation now applies explicit system-bar insets so top-level and sub-screen content stays clear of Android 15+ status and navigation bars while edge-to-edge remains enabled. |
| **6.9.19** | Theme color tokens now use a per-composition palette instead of global mutable state, preventing high-contrast/accent variants from racing when multiple themed surfaces coexist. |
| **6.9.18** | DNS log temporary-allow timers now use WorkManager, so process death or Doze cannot leave a temporarily allowed domain unblocked after the selected window expires. |
| **6.9.17** | Parental controls now detect legacy unsalted SHA-256 PIN hashes at app launch, force the user through the PIN upgrade gate, and rewrite the hash to the current KDF after successful verification. |
| **6.9.16** | Deep engineering audit: fixed blocklist data loss on all-304 periodic refresh, missing regex rules in VPN rebuild, per-app DNS rule cache torn-state, DNS cache TTL overcapping, hourly chart UTC mismatch, threat-intel domain swap race, GeoIP cache unbounded growth, VPN restart coroutine leaks, stability metric loss on DB failure, EDE JSON injection. Added legacy backup rules for API 26-30, restricted FileProvider paths, cleaned ProGuard dead rules. |
| **6.9.15** | Additional release-state polish: Blocklist Gallery now has explicit loading, unavailable, empty, success, and add-failure states using shared status surfaces; gallery add failures are logged and shown as errors instead of green success messages; Home warning dismiss/restart actions, automation copy, TLS/crash clear actions, parental message dismiss, and app privacy expand controls have larger touch targets; app privacy and content filter rows truncate long text safely. |
| **6.9.14** | Public-release polish for shared status surfaces, Home protection errors, DNS tools, hosts utilities, firewall controls, and the blocklist gallery: user-facing failures no longer expose raw exception text, repeated firewall controls and gallery add actions use larger touch targets, shared status/empty actions reserve larger hit areas, and gallery cards handle long labels/descriptions safely. |
| **6.9.13** | Stricter release polish for Rules, Sources, and DNS Logs: redirect rules now validate IPv4/IPv6 targets, regex errors no longer expose parser text, source/log failures use user-facing copy with detailed logs preserved, long rule/source/log text truncates safely, DNS log query-type filters wrap on narrow screens, and key icon/selection controls have larger touch targets. |
| **6.9.12** | Second-pass release polish for Settings-adjacent flows: WebDAV now preserves saved passwords when the password field is left blank, QR sharing fits narrow screens and uses accessible status banners, rule testing has keyboard submit behavior and long-domain handling, and diagnostic/PCAP/CSV export states use clearer touch targets and user-facing failure copy. |
| **6.9.11** | DoH now honors the user-selected provider as the primary resolver while using latency only for failover ordering. Gradle versions moved to `libs.versions.toml`, Settings PCAP export exposes all/DNS/firewall modes with visible share failure feedback, and release-doc checks now catch stale FGS, DoH3, GeoIP, and version metadata claims. |
| **6.9.9** | New launcher icon assets across Android icon densities, README screenshots, and AdAway Default plus StevenBlack Unified enabled by default with an upgrade migration for existing installs. |
| **6.9.8** | Removed the non-root "Root not detected" Home banner, fixed Settings > View on GitHub so it opens the repository in a browser, and simplified the Sources header to keep only the add-source action. |
| **6.9.7** | Animated the active protection orb with a rotating arc, trailing sweep, and breathing halo so the dashboard visibly communicates active protection. |
| **6.9.6** | Manual blocks now turn matching live DNS activity rows red, accent color preferences apply to the active app theme, and blocklist rebuilds use less memory. |
| **6.9.5** | First-run notification permission is now deferred until protection activation instead of covering onboarding, and the DNS resolver step uses a compact accessible selector so all resolver choices are visible on tall phones. |
| **6.9.4** | Release signing now fails closed unless real signing credentials are configured or an explicit local debug-signing override is set. Imports, backups, QR config, diagnostics, threat feeds, WebDAV, GeoIP, update checks, and DEX scans now use bounded input and stricter validation. Secondary controls have larger accessible touch targets. |
| **6.9.3** | Threat-intel feed health dashboard added to Stats with per-feed freshness, HTTP status, entry counts, SHA-256 prefixes, failure state, and manual refresh. Diagnostic exports now include redacted threat-feed health summaries. |
| **6.9.2** | Embedded Cronet removed and DoH3 disabled while no maintained non-vulnerable Cronet artifact is available. Pinned OkHttp DoH/DoT remains the production encrypted DNS path, and release posture/provenance scripts now record the no-bundled-Cronet state. |
| **6.9.1** | Debug pseudolocales enabled, RTL/pseudo-expanded Compose layout scaffold added, Home/Settings/QR strings moved through resources, and QR config import/export validation kept covered by JVM tests. |
| **6.9.0** | AdGuard `$dnsrewrite` import support. NXDOMAIN/REFUSED/null-IP rewrites imported as block rules. A/AAAA IP rewrites parsed as redirect rules. 3-part form (`NOERROR;A;1.2.3.4`) supported. Unsupported CNAME rewrites counted in diagnostics. |
| **6.8.0** | Threat-intel feed health tracking. Per-feed HTTP status, SHA-256, entry counts, staleness detection, and consecutive failure tracking with full cache persistence. Compile-warning deprecation cleanup: removed dead NetworkChangeReceiver, migrated clipboard API, updated MaxMind accessors. ZXing/Room 3.0/Accrescent evaluations documented. |
| **6.7.2** | Historical local-first GeoIP path for log detail enrichment. Current builds use the bounded ipapi.co lookup path documented above. |
| **6.7.1** | Temporary bypass timer. Home screen pause now offers 5/15/30/60-minute durations with visible countdown and auto-resume via WorkManager. |
| **6.7.0** | TargetSdk 36 (Android 16). QR config import hardened with bounded decompression, Base64 padding tolerance, and separated import planning. |
| **6.6.9** | License unification and hardening. Unified project license on GPL-3.0, resolving the root MIT / app GPL-3.0 conflict that blocked F-Droid/IzzyOnDroid publication. |
| **6.6.8** | QR config import completion. QR transfer now exports real rules and custom sources, previews import deltas, applies validated rules/sources/DNS settings, rejects oversized compressed payloads, and accepts only HTTPS source URLs before insertion. |
| **6.6.7** | Safe Search correctness hardening. DNS-level enforcement now handles Google country domains, answers A and AAAA queries with safe endpoints or NODATA as appropriate, suppresses HTTPS/SVCB metadata answers, and caches resolved canonical safe-search endpoints with bundled fallbacks. |
| **6.6.6** | Local DNS server abuse hardening. LAN DNS mode now rejects public clients by default, applies per-client query throttling, emits TC=1 for oversized UDP answers, reuses the shared DNS packet builder, and loads configured DoT/DoH/custom upstream preferences before plaintext fallback. |
| **6.6.5** | Wi-Fi-only sync hardening. Periodic threat-intel and source-health refresh workers now use the same Wi-Fi-only network constraint as blocklist updates, and changing the setting refreshes existing WorkManager registrations instead of leaving stale constraints behind. |
| **6.6.4** | Threat-intel parser hardening. Whitespace-separated compromised-IP feeds now parse into CIDRs correctly, invalid/broad tokens are rejected, duplicate feed tokens are de-duplicated, partial feed refreshes report degraded status while preserving successfully parsed data, and the release-doc gate now allows scoped per-version store changelogs while keeping durable metadata checks strict. |
| **6.6.3** | Release gate consolidation. GitHub releases now run release-doc checks, Cronet posture validation, full unit tests, full APK build, Play AAB build, provenance/checksum generation, and artifact upload before publishing. |
| **6.6.2** | Automation API contract repair. Public docs now use canonical `com.hostshield.ACTION_*` actions and `duration_minutes`, the manifest exposes every supported automation action, lowercase aliases remain compatible, and unit tests lock the action/extra normalization contract. |
| **6.6.1** | Dependency hardening. Updated OkHttp to 5.4.0 for the current stable networking stack, including the 256 KiB HTTP/2 response-header cap and current Okio baseline. |
| **6.6.0** | Android 15/16 foreground-service resilience. Protection services now declare `systemExempted`, runtime foreground promotion uses the matching service type, service timeout callbacks record local diagnostics, and all protection restart surfaces record controlled start-failure events instead of crashing. |
| **6.5.9** | DNSCrypt stamp and relay-route foundation. `sdns://` parsing now uses spec-width properties, preserves 32-byte DNSCrypt provider keys, parses Anonymized DNSCrypt relay stamps, and validates resolver-to-relay routes before building relay target prefixes. |
| **6.5.8** | DoH3 resolver transport. Existing DoH now tries embedded-Cronet HTTP/3/QUIC first, accepts only actual `h3`/QUIC negotiation, labels successful query-log upstreams as `DoH3`, and falls back to the existing pinned OkHttp DoH path when HTTP/3 is unavailable. |
| **6.5.7** | VPN route canonicalization. `VpnService.Builder.addRoute` paths now mask IPv4/IPv6 host bits before route insertion, preserving host routes while preventing Android 11+ validation failures for user-provided network prefixes. |
| **6.5.6** | Magisk 26+ mount-master hardening. Root firewall command paths now detect Magisk 26+ and prefer libsu's mount-master shell for `iptables`, `ip6tables`, and route-localnet sysctl work, with focused version-gate coverage. |
| **6.5.5** | TCP DNS fallback verification. Added shared RFC 7766 truncation handling, wired IPv6 UDP forwarding through the same `TC=1` TCP retry path as IPv4, and covered path-MTU-sized truncated responses with a 200 ms retry-start regression. |
| **6.5.4** | Hot-reload blocklist hardening. Production rebuild paths now use `BlocklistHolder.updateAsync()` so trie construction happens off the caller thread before the single snapshot swap, with concurrent-reader regression coverage. |
| **6.5.3** | Doze/App Standby resilience pass. Moved protection foreground services to explicit foreground-service type declarations, documented every WorkManager job, kept blocklist refresh expedited for immediate runs, and added a 60-second VPN heartbeat with structured kill/fd-failure events. |
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

This project is licensed under the [GNU General Public License v3.0](LICENSE).
