# HostShield Feature Research & Competitive Analysis

> Compiled 2026-03-26 | Sources: 30+ open-source projects analyzed

---

## Table of Contents

1. [DNS Blocking Engines](#1-dns-blocking-engines)
2. [Blocklist Management](#2-blocklist-management)
3. [Firewall & Network Control](#3-firewall--network-control)
4. [Network Monitoring & PCAP](#4-network-monitoring--pcap)
5. [GeoIP & Threat Intelligence](#5-geoip--threat-intelligence)
6. [Privacy Scoring & App Analysis](#6-privacy-scoring--app-analysis)
7. [DNS Security Tools](#7-dns-security-tools)
8. [UI/UX Patterns](#8-uiux-patterns)
9. [Widgets & Quick Settings](#9-widgets--quick-settings)
10. [Automation & Scheduling](#10-automation--scheduling)
11. [Backup, Export & Sync](#11-backup-export--sync)
12. [Logging & Diagnostics](#12-logging--diagnostics)
13. [Notifications](#13-notifications)
14. [New Feature Opportunities](#14-new-feature-opportunities)
15. [Battery & Performance Comparison](#15-battery--performance-comparison)
16. [Roadmap](#16-roadmap)

---

## 1. DNS Blocking Engines

### 1.1 VPN-Based DNS Interception (Competing Projects)

| Project | Stars | Language | Key Approach |
|---------|-------|----------|--------------|
| [RethinkDNS](https://github.com/celzero/rethink-app) | ~4,700 | Kotlin + Go | VPN + Go firestack (outline-go-tun2socks fork), compressed succinct radix-trie for 13.5M domains, split-tunnel DNS, supports DoH/DoT/DNSCrypt/ODoH |
| [AdGuard for Android](https://github.com/AdguardTeam/AdguardForAndroid) | ~4,000+ | Kotlin | VPN with adblock-syntax engine (urlfilter), HTTPS filtering, trie-based indexes via `internal/lookup` package |
| [Blokada 5/6](https://github.com/blokadaorg/blokada) | ~3,000+ | Kotlin | VPN-based, cloud relay option (Blokada Plus), network-aware profiles by Wi-Fi SSID |
| [NetGuard](https://github.com/M66B/NetGuard) | ~3,530 | Java + C | VPN sinkhole in native C layer, per-app Wi-Fi/mobile toggles, screen on/off rules |
| [InviZible Pro](https://github.com/Gedsh/InviZible) | ~2,000+ | Java | DNSCrypt + Tor + I2P + firewall, no VPN required on root |
| [personalDNSfilter](https://github.com/IngoZenz/personaldnsfilter) | ~1,500+ | Java | Lightweight, CNAME cloaking detection, can run as LAN DNS proxy on root |
| [Nebulo](https://github.com/Ch4t4r/Nebulo) | ~236 | Kotlin | Zero-dependency DNS implementation, DoH/DoT/DoQ, DNS stamp support, 4 blocklist formats |
| [DNS66](https://github.com/julian-klode/dns66) | ~2,200 | Java | DNS-only VPN routing (port 53 only, not all traffic) — minimal battery impact |

### 1.2 Improvements for HostShield's DNS Engine

**Trie / Data Structure Optimizations:**
- HostShield currently uses a trie-based O(m) lookup. Consider adding a **hash set for exact matches** (covers 90%+ of lookups) as a fast path before the trie — this is what AdGuard Home does internally.
- For wildcard matching, use a **reversed-label trie** (split domain by `.`, reverse labels, walk trie). This naturally handles `*.example.com` patterns.
- Consider a **Bloom filter pre-check** (~1MB for 200K domains, <0.1% false positive) to fast-reject queries not in any blocklist before trie traversal.
- RethinkDNS compiles ~17M domains into a **succinct radix-trie** (based on Steve Hanov's implementation) — 10-50x memory reduction for large blocklists. Worth evaluating for HostShield's trie.
- **Filter decision cache** (personalDNSfilter pattern): Cache both allowed AND blocked domain lookup results to avoid repeated trie traversals. HostShield's DnsCache caches DNS responses, but a separate filter-decision LRU would skip the trie entirely for hot domains.

**DNS-Only VPN Routing (Battery Optimization):**
- DNS66 and Blokada only route DNS traffic (port 53 UDP/TCP) through the VPN via `VpnService.Builder.addRoute()` targeting DNS IPs only, rather than all device traffic.
- This dramatically reduces battery drain (~0.5% vs ~1-3% for full-traffic VPN).
- HostShield currently routes all traffic. Consider adding a **"DNS-only mode"** for users who don't need the firewall — only capture port 53 and DoH/DoT bypass traffic.
- Trade-off: DNS-only mode cannot support per-app firewall, PCAP export, or network stats. Make it a user choice.

**DNS Packet Handling:**
- **Hybrid parser approach** (recommended): Kotlin-native parser for hot path, **dnsjava** (~1.1k stars) for complex operations (DNSSEC, TSIG, full record types), JNI/native C only for TUN read/write loop (NetGuard's `dns.c` pattern).
- **EDNS(0) support** (RFC 6891): OPT pseudo-record in all queries, buffer size 1232 bytes (DNS Flag Day 2020), EDNS Client Subnet opt-in (privacy trade-off for CDN optimization).
- **EDNS padding** (RFC 7830) for DoH/DoT queries to prevent traffic analysis.
- **DNSSEC validation** (opt-in via dnsjava): Set DO bit, validate signatures, display secure/insecure/bogus status in query log. Return SERVFAIL with Extended DNS Error (EDE, RFC 8914) code for bogus responses.
- **HTTPS/SVCB record parsing** (RFC 9460, TYPE 64/65): Critical for ECH detection and CNAME cloaking via SVCB redirects.

**DNS Caching Enhancements:**
- **Serve-stale** (RFC 8767): When upstream is unreachable, return expired cache entries with a short TTL (30s) instead of SERVFAIL. Critical for mobile where connectivity transitions (WiFi↔cellular) cause brief DNS failures. Used in production by Akamai since 2011, BIND 9.12+, Unbound.
- **Negative caching** (RFC 2308, RFC 9520): Cache NXDOMAIN/NODATA using SOA minimum TTL. Also cache SERVFAIL with 1-5s TTL (RFC 9520, published 2024) to prevent retry storms. Reduces upstream queries by 15-30%.
- **Prefetching** (Unbound algorithm): When cached entry's remaining TTL < 10% of original AND queryCount > threshold (~3), serve stale and dispatch background refresh. ~10% more upstream queries, but near-zero latency for popular domains.
- **Minimum TTL floor**: Enforce 60s minimum (configurable) to prevent rapid re-queries for low-TTL CDN domains.
- **Maximum TTL cap**: Cap at 86400s (1 day) configurable, per RFC 8767 suggestion of 7-day max. RethinkDNS and Nebulo both allow user-configurable cache lifetimes.
- **Two-tier cache architecture:**
  ```
  L1: In-memory LRU (~10K domains)
      +-- Positive cache (A, AAAA, CNAME, etc.)
      +-- Negative cache (NXDOMAIN, NODATA, SERVFAIL)
      +-- Filter decision cache (blocked/allowed results)
  L2: On-disk persistent cache (~100K domains, SQLite)
      +-- Survives app/device restart
      +-- Instant DNS after reboot
  Prefetch queue (background refresh at <10% TTL)
  ```

### 1.3 Root-Based DNS Blocking

| Project | Stars | Approach |
|---------|-------|----------|
| [AdAway](https://github.com/AdAway/AdAway) | ~8,900 | Direct `/etc/hosts` modification, systemless Magisk module, local web server for blocked responses |
| [AFWall+](https://github.com/ukanth/afwall) | ~3,320 | iptables per-app firewall, 6 network categories per app |
| [InviZible Pro](https://github.com/Gedsh/InviZible) | ~2,500 | Tri-mode: VPN / root iptables / proxy, refreshes iptables on connectivity change |

**Improvements for HostShield's Root Mode:**
- **Hosts file as fallback** (AdAway pattern): Even with iptables-based blocking, writing a hosts file provides belt-and-suspenders that survives service crashes.
- **Local web server for blocked responses** (AdAway): Return a proper HTTP empty response for blocked domains instead of connection timeout — prevents UI hangs in apps.
- **NFLOG improvements**: Use `nflog-group` parameter for cleaner capture vs raw iptables logging.
- **iptables/nftables backend detection**: Run `iptables -V` to detect `nf_tables` vs `legacy`. AFWall+ has NOT migrated to nftables yet. Use iptables compatibility layer but architect rule generation to be backend-agnostic.
- **iptables refresh on connectivity change** (InviZible Pro pattern): Refresh iptables rules on every connectivity change event — critical for root mode reliability when switching WiFi↔cellular.
- **Binary management**: Bundle binaries with SHA-256 verification on extraction. Support **KernelSU detection** alongside Magisk and legacy SuperSU. Use `libsu` (topjohnwu) for modern root shell management.
- **Systemless hosts**: Support Magisk systemless hosts module — avoids triggering SafetyNet/Play Integrity.
- **Proxy mode** (InviZible Pro, no VPN/no root): Works alongside other VPN apps. HostShield's dual-mode could become tri-mode.

### 1.4 Encrypted DNS Protocols

| Protocol | Status in HostShield | Improvement |
|----------|---------------------|-------------|
| **DoH (RFC 8484)** | Implemented (POST+GET, cert pinning, latency failover) | Add HTTP/3 (QUIC) transport, Oblivious DoH (ODoH) relay support |
| **DoT (RFC 7858)** | Blocking only | Add as upstream resolver option (port 853, TLS 1.3). Simpler than DoH, lower overhead, widely supported. Use Android's built-in TLS stack |
| **DoQ (RFC 9250)** | Not implemented | Faster than DoH, multiplexed. Use `cronet` (Chromium QUIC) or `kwik` (pure Java). Nebulo + AdGuard DnsLibs both implement |
| **DNSCrypt** | Not implemented | InviZible Pro proves viability; can wrap dnscrypt-proxy binary or implement natively |
| **ODoH** | Not implemented | RethinkDNS supports via relay — double-blind privacy (relay can't see query, resolver can't see client IP) |
| **DNS-over-Tor** | Not implemented | RethinkDNS routes DNS through Tor for maximum anonymity. Consider as optional mode |

**DNS Stamp Support (`sdns://`):**
- Parse `sdns://` stamps per [specification](https://dnscrypt.info/stamps-specifications/) for one-click DNS server configuration.
- Support stamp types: DNSCrypt, DoH, DoT, DoQ, Anonymized DNSCrypt, ODoH.
- Import from public resolver lists (dnscrypt.info/public-servers).
- QR code scanning for easy mobile configuration sharing.
- Pre-populate stamps for popular resolvers (Cloudflare, Google, Quad9, NextDNS, AdGuard DNS, Mullvad).
- **Implementation**: Stamps are base64url-encoded binary — write a Kotlin parser (~200 lines). No existing Android/Kotlin library; reference: [DNSCrypt stamp parser (Go)](https://github.com/DNSCrypt/dnscrypt-proxy/blob/master/dnscrypt-proxy/stamps.go).

**CNAME Cloaking Detection Enhancements:**
HostShield already has CNAME cloak detection. Enhance to match RethinkDNS (gold standard):
```
1. Intercept DNS response
2. If response contains CNAME records:
   a. Follow full CNAME chain (up to N=8 hops)
   b. Check EACH domain in chain against blocklists
   c. If ANY matches, block original query
3. If response contains HTTPS/SVCB records (TYPE 64/65):
   a. Extract TargetName from SVCB/HTTPS records
   b. Check TargetName against blocklists (SVCB-based cloaking)
4. Consume external CNAME cloak databases:
   - AdGuard cname-trackers: https://github.com/AdguardTeam/cname-trackers (auto-updated)
   - NextDNS cname-cloaking-blocklist: https://github.com/nextdns/cname-cloaking-blocklist
5. Cache CNAME chains to avoid repeated resolution overhead
```

**ECH (Encrypted Client Hello) Preparedness:**
- RFC 9849 (2025) encrypts SNI using keys in DNS SVCB/HTTPS records.
- Impact: DNS-level blocking becomes the **only** viable blocking layer when ECH is deployed.
- Parse HTTPS/SVCB records for ECHConfig, log ECH-enabled domains, block SVCB records containing ECHConfig for blocked domains.

---

## 2. Blocklist Management

### 2.1 Format Support (Current vs Needed)

| Format | HostShield | Industry Trend | Action |
|--------|-----------|----------------|--------|
| **hosts** (`0.0.0.0 domain`) | Supported | Declining (OISD deprecated Jan 2024) | Keep |
| **domains-only** | Supported | Common | Keep |
| **adblock-syntax** (`\|\|domain^`) | Not supported | **Primary format** — AdGuard, hagezi, OISD all use it | **HIGH PRIORITY: Add** |
| **wildcard** (`*.domain`) | Partial (trie) | Growing | Ensure full support |
| **regex** (`/pattern/`) | Supported (capped 500 chars) | Niche | Keep |
| **dnsmasq** (`local=/domain/`) | Not supported | Server-specific | Low priority — convert on import |
| **RPZ** (`domain CNAME .`) | Not supported | DNS server native | Low priority — convert on import |

**Key Insight**: The industry is moving to adblock-syntax. OISD deprecated hosts format entirely. HostShield must support `||domain^` syntax as a first-class citizen.

### 2.2 Adblock-Syntax Engine

Modeled on [AdGuard urlfilter](https://github.com/AdguardTeam/urlfilter) (Go reference implementation):

**Rule Priority System** (highest to lowest):
1. `$dnsrewrite` rules
2. Exception rules with `$important` (`@@||example.com^$important`)
3. Blocking rules with `$important`
4. Standard exception rules (`@@||example.com^`)
5. Standard blocking rules (`||example.com^`)
6. Host file rules

**Essential Modifiers to Support:**
- `$important` — elevates rule priority
- `$badfilter` — disables matching rules
- `$dnstype` — filter by DNS record type (A, AAAA, CNAME)
- `$dnsrewrite` — rewrite DNS responses
- `$denyallow` — block everything except specified domains

### 2.3 Blocklist Sources Landscape

| Project | Stars | Entries | Formats | Update Freq |
|---------|-------|---------|---------|-------------|
| [StevenBlack/hosts](https://github.com/StevenBlack/hosts) | ~29.9k | 86k+ | hosts | Automated, frequent |
| [hagezi/dns-blocklists](https://github.com/hagezi/dns-blocklists) | ~16k+ | Tiered (Light→Ultimate) | 7 formats (domains, hosts, ABP, wildcard, dnsmasq, RPZ, unbound) | Daily |
| [OISD](https://oisd.nl/) | N/A | Big/Small/NSFW | ABP, wildcard, RPZ, regex | Continuous |
| [Energized](https://github.com/EnergizedProtection/block) | ~2.6k | Multiple packs | hosts, domains, ABP | Daily |
| [AdGuard DNS Filter](https://github.com/AdguardTeam/AdGuardSDNSFilter) | ~1.5k+ | Composite | adblock-syntax | Frequent |

### 2.4 Gallery Improvements

Current HostShield gallery has 70+ curated lists. Enhance with:

**Metadata schema** (adopt AdGuard HostlistsRegistry model):
```json
{
  "filterKey": "unique-id",
  "name": "Filter Name",
  "purpose": ["ads", "privacy", "malware"],
  "trusted": true,
  "recommended": true,
  "healthScore": 95,
  "lastUpdated": "2026-03-25T10:00:00Z",
  "ruleCount": 85000,
  "uniqueRules": 12000,
  "updateFrequency": "daily"
}
```

**New gallery features:**
- Health metrics: staleness indicator, update frequency, false positive reports
- Category bundles: "Privacy Essential", "Family Safe", "Maximum Protection"
- Per-list health card: total rules, unique rules, overlap %, last updated
- "Optimize my lists" button suggesting removal of redundant subscriptions

### 2.5 Overlap Analysis Enhancements

Current HostShield has basic overlap analysis. Enhance with:

**Static analysis** (improved):
```
For each pair of lists (A, B):
  overlap(A,B) = |A ∩ B| / min(|A|, |B|)
  unique(A)    = |A - (B ∪ C ∪ ...)|

Output:
  - Overlap heatmap matrix
  - Unique contribution % per list
  - Redundancy score (lists adding <1% unique domains)
  - Suggested removals to minimize subscriptions
```

**Query-log attribution** (new):
- Tag each blocked query with the list(s) that matched
- Show "If you removed List X, these N queries would go unblocked"
- Per-list actual hit-rate contribution over time

### 2.6 Hosts Diff Improvements

Current HostShield has HostsDiffScreen. Enhance with:
- **Update impact preview**: Before applying, show new domains that would be blocked (cross-reference with query history)
- **Changelog generation**: Auto-generate human-readable changelog per update
- **Version rollback**: Store last N versions of each list for rollback
- **Notification**: "List X updated: +142 / -87 domains"

---

## 3. Firewall & Network Control

### 3.1 Competing Firewall Implementations

| Feature | HostShield | NetGuard | AFWall+ | RethinkDNS |
|---------|-----------|----------|---------|------------|
| Per-app Wi-Fi/Mobile | Yes (iptables) | Yes (VPN) | Yes (iptables) | Yes (VPN) |
| Screen on/off rules | Via ScreenStateReceiver | Best-in-class | No | Accessibility Service |
| Metered vs unmetered | No | Metered Wi-Fi only | No | **Yes (correct model)** |
| Domain-per-app | No | No | No | **Yes (unique)** |
| LAN toggle per app | No | Partial | **Yes (dedicated toggle)** | No |
| Category-based blocking | No | No | No | **Yes (Play Store cats)** |
| Country-based blocking | No | No | No | No (LostNet does) |
| Roaming rules | No | No | **Yes (separate column)** | No |

### 3.2 Firewall Improvements to Implement

**HIGH PRIORITY:**
- **Domain-per-app rules** (from RethinkDNS): Allow/deny specific domains per app. Intercept DNS in VPN layer, match app UID + domain.
- **Metered vs unmetered** (from RethinkDNS): Use `ConnectivityManager.isActiveNetworkMetered()` instead of Wi-Fi vs mobile — more correct since Wi-Fi can be metered.
- **Screen on/off improvements**: Already have ScreenStateReceiver. Add per-app "block when screen off" toggle (NetGuard's most popular feature).

**MEDIUM PRIORITY:**
- **LAN toggle per app** (from AFWall+): Detect RFC1918 destinations in VPN layer; separate allow/deny from internet rules.
- **Category-based blocking** (from RethinkDNS): Query `PackageManager` for Play Store category; group apps by Social/Games/Productivity.
- **Country-based blocking** (from LostNet): GeoIP lookup on destination IP; allow/deny per country per app.

**LOWER PRIORITY:**
- **Roaming rules** (from AFWall+): Detect roaming via `TelephonyManager`; apply stricter rules.
- **Shizuku mode** (from ShizuWall, ~1,290 stars): Alternative to VPN — avoids single-VPN limitation. Worth investigating.

---

## 4. Network Monitoring & PCAP

### 4.1 Network Stats Approaches

| Approach | Pros | Cons |
|----------|------|------|
| **NetworkStatsManager** (API 23+) | Official API, per-UID, survives reboot | 2-hour bucket minimum, not real-time |
| **VPN Packet Counting** | Real-time, per-connection | Requires active VPN |
| **/proc/net Parsing** | No permissions on older Android | Restricted Android 10+ |

**Recommended**: Use `NetworkStatsManager.queryDetailsForUid()` for historical per-app bandwidth + count bytes in VPN TUN read/write loop for real-time stats. Zero additional overhead.

**New features:**
- Data usage quotas + alerts (WorkManager job comparing usage to thresholds)
- Per-country data breakdown (GeoIP resolve destination IPs, aggregate bytes by country)
- Per-app bandwidth widget combining historical + real-time data

### 4.2 PCAP Export Improvements

Reference: [PCAPdroid](https://github.com/emanuele-f/PCAPdroid) (~3,850 stars)

| Feature | Current | Improvement |
|---------|---------|-------------|
| Format | PCAP | Add **PCAP-NG** (interface metadata, app UID annotations) |
| Protocol detection | None | Integrate [nDPI](https://github.com/ntop/nDPI) (~4,390 stars) via JNI — 300+ protocols |
| TLS fingerprinting | None | nDPI provides **JA3/JA4** fingerprints for free |
| Remote streaming | None | UDP sender to Wireshark on desktop |
| TLS metadata | None | Extract SNI + JA3/JA4 from nDPI, display in connection detail |
| Threat detection | None | Daily-updated IP+domain blacklists (abuse.ch, Spamhaus DROP) |

---

## 5. GeoIP & Threat Intelligence

### 5.1 GeoIP Improvements

Current HostShield uses ip-api.com (free tier, 45 req/min). **Major upgrade path:**

**Bundle MaxMind GeoLite2 databases:**
- `GeoLite2-Country.mmdb` (~6MB) + `GeoLite2-ASN.mmdb` (~8MB) in assets or download on first launch
- Library: `com.maxmind.geoip2:geoip2` ([GeoIP2-java](https://github.com/maxmind/GeoIP2-java), 856 stars)
- Thread-safe `DatabaseReader` with `CHMCache` (~2MB memory)
- Weekly background update via WorkManager (use [P3TERX/GeoLite.mmdb](https://github.com/P3TERX/GeoLite.mmdb) mirror)
- **Result**: Unlimited offline lookups, no rate limiting, no API latency

**Visualization:**
- Globe/map view showing active connections by geo-coordinates (WebView + Globe.GL or lightweight OpenGL)
- Country flags next to resolved IPs in logs
- Per-country connection counts on dashboard

### 5.2 Threat Intelligence

**Blocklist-based approach** (like PCAPdroid — real-time, not API-based):
- Download IP/domain blacklists daily:
  - abuse.ch URLhaus (malware URLs/IPs)
  - Spamhaus DROP (worst IP ranges)
  - Emerging Threats IPs
  - Disconnect Tracker List (used by Firefox)
  - DuckDuckGo Tracker Radar
- Store as radix trie (IPs) + hash set (domains) for O(1) lookups
- Check every connection in VPN loop

**On-demand deep checks:**
- Reserve AbuseIPDB API (1,000 free checks/day) for when user taps a connection
- Show abuse confidence score (0-100), total reports, ISP

---

## 6. Privacy Scoring & App Analysis

### 6.1 Competing Approaches

| Project | Stars | Method |
|---------|-------|--------|
| [Exodus Privacy / ETIP](https://github.com/Exodus-Privacy/etip) | 71 (DB), 956 (app) | DEX class name pattern matching against tracker signature database |
| [TrackerControl](https://github.com/TrackerControl/tracker-control-android) | ~2,420 | **Dual: static (Exodus signatures) + network (VPN-observed tracker domains)** |
| [ClassyShark3xodus](https://f-droid.org/packages/com.oF2pks.classyshark3xodus/) | F-Droid | Local APK scanning with Exodus signatures |

### 6.2 Improvements for HostShield

Current: TrackerSignatureDb scans ~60 SDK signatures with Room caching. Enhance with:

**Expanded tracker database:**
- Import full ETIP database (400+ tracker signatures vs current ~60)
- Add network signatures (tracker domains) from ETIP, not just code signatures
- Categorize trackers: Analytics, Advertising, Fingerprinting, Social, Crash Reporting

**Dual detection** (TrackerControl model):
1. **Static**: DEX class scanning (existing) — proves SDK is embedded
2. **Network**: Match VPN-observed domains per-app against Disconnect + DuckDuckGo Tracker Radar lists — proves actual data exfiltration

**Enhanced scoring formula:**
```
Privacy Score = 100 - (tracker_penalty + permission_penalty + network_penalty)

tracker_penalty (max -40):
  Analytics trackers:      -3 each
  Advertising trackers:    -5 each
  Fingerprinting trackers: -7 each

permission_penalty (max -30):
  CAMERA, MICROPHONE, LOCATION:   -5 each
  CONTACTS, CALL_LOG, SMS:        -4 each
  STORAGE, PHONE_STATE:           -2 each

network_penalty (max -30):
  Known tracker domains:       -3 per unique domain
  Unencrypted HTTP connections: -5 per unique host
  High-risk country connections: -2 per country
```

---

## 7. DNS Security Tools

### 7.1 DNS Leak Test Improvements

Current: Built-in DNS leak test. Enhance with:
- **Unique subdomain method**: Generate random subdomains (`<random>.test.hostshield.app`), check which resolver IP makes the query
- **Multiple test servers**: Test against 3+ endpoints to detect partial leaks
- **VPN-layer verification**: Inspect DNS traffic in VPN layer to detect queries bypassing tunnel

### 7.2 New: WebRTC Leak Test

- Load local HTML in WebView with `RTCPeerConnection` JavaScript
- Parse ICE candidates for IP addresses
- Compare against VPN-assigned IP — flag mismatches as leaks
- Reference: [IPCheck.ing/MyIP](https://github.com/jason5ng32/MyIP) (~9,990 stars)

### 7.3 New: IPv6 Leak Test

- Attempt IPv6 connections to known test servers
- If reachable and IP doesn't match VPN's IPv6 address, flag as leak
- Mitigation: Block IPv6 in TUN config or explicitly tunnel it

### 7.4 New: Captive Portal Handling

- Hook `ConnectivityManager.NetworkCallback` for `NET_CAPABILITY_CAPTIVE_PORTAL`
- Temporarily pause VPN/firewall, show notification, open Custom Tabs for login
- Re-enable after authentication
- Reference: [Captive Portal Controller](https://f-droid.org/packages/io.github.muntashirakon.captiveportalcontroller/) on F-Droid

---

## 8. UI/UX Patterns

### 8.1 Dashboard Improvements

**Competing dashboard patterns:**
| App | Layout | Key Metrics |
|-----|--------|-------------|
| Pi-hole | 4 stat cards + 24hr bar chart | Total queries, blocked, %, domains on list |
| AdGuard Home | Summary cards + time-series + upstream perf | Queries, blocked, malware, avg response time |
| RethinkDNS | Connection-centric + per-app drill-down | Active connections, per-app data, GeoIP |

**Recommended improvements:**
- **Lottie shield animation** for protection status (pulse when active, crack when disabled). LottieFiles has 13,000+ shield animations.
- **Vico charts** ([patrykandpatrick/vico](https://github.com/patrykandpatrick/vico)) — Compose-native, real-time animations, M3 integration. Best for 24hr query volume, block %, latency.
- **Semantic color tokens**: `surfaceBlocked` (red tint), `surfaceAllowed` (green tint), `surfaceWarning` (amber).
- **Real-time query feed**: `LazyColumn` with `animateItemPlacement()`, color-coded entries, auto-scroll with "jump to latest" FAB.
- **Material 3 dynamic theming**: `dynamicColorScheme()` from wallpaper (Android 12+), fallback to brand palette.

### 8.2 Onboarding Improvements

Recommended 6-screen progressive flow:
1. **Welcome** — Lottie shield animation + "Get Started"
2. **VPN Permission** — "Local VPN, no data leaves device" + visual diagram
3. **Battery Optimization** — Exempt from Doze with device-specific guide
4. **DNS Provider** — Simple: "Recommended (Cloudflare DoH)" / Advanced: Custom config
5. **Blocklist Selection** — Presets: "Standard", "Strict", "Family-Safe"
6. **Done** — Live demo of first blocked queries appearing

Key UX principles from RethinkDNS: Use plain language ("Allow"/"Block" not "whitelist"/"blacklist"), progressive feature discovery, contextual hints only when needed.

---

## 9. Widgets & Quick Settings

### 9.1 Jetpack Glance Widgets

Current: Toggle widget + stats widget. Enhance with:

**Widget 1: Toggle + Stats Combo (2x2)**
```
+---------------------------+
|  [Shield]  HostShield     |
|  Protected  [ON/OFF]      |
|  Blocked: 3,241 today     |
|  Queries: 12,847          |
+---------------------------+
```

**Widget 2: Stats Bar (4x1)**
```
+-----------------------------------------------------+
| Queries: 12.8K | Blocked: 3.2K | 25.2% | Latency: 23ms |
+-----------------------------------------------------+
```

**Widget 3: Mini Toggle (1x1)**

**Glance best practices:**
- Use `PreferencesDataStore` for state (Glance doesn't auto-redraw)
- Use color resource IDs, NOT `MaterialTheme` (incompatible with Glance)
- `SizeMode.Responsive` with breakpoints for widget placement sizes
- `actionRunCallback<ToggleProtectionAction>()` for toggle
- Android 16+: `AppWidgetEvent` API for tap/scroll tracking

### 9.2 Quick Settings Tile

Modeled on WireGuard's implementation:
- `TOGGLEABLE_TILE` metadata for accessibility
- States: Active (green shield), Inactive (grey), Unavailable (crossed-out)
- Subtitle: "3.2K blocked" or "Protected"
- `isSecure()` check before toggling — use `unlockAndRun()` if locked
- Handle VPN permission not yet granted → launch permission flow from tile

---

## 10. Automation & Scheduling

### 10.1 Tasker/Intent Integration

Current: AutomationReceiver with ENABLE/DISABLE/STATUS/REFRESH_BLOCKLIST. Expand with:

```kotlin
// New intents
"com.hostshield.action.TOGGLE_PROTECTION"
"com.hostshield.action.SET_PROFILE"       // extra: "profile_name"
"com.hostshield.action.SET_DNS"           // extra: "dns_url" or "sdns://" stamp
"com.hostshield.action.ENABLE_BLOCKLIST"  // extra: "blocklist_id"
"com.hostshield.action.DISABLE_BLOCKLIST" // extra: "blocklist_id"
"com.hostshield.action.PAUSE"            // extra: "minutes" (0 = resume)
```

### 10.2 Schedule Improvements

Current: BlockingScheduleWorker + ProfileScheduleWorker. Enhance with:

**Named schedule types:**
- Focus Mode — Block social media during work hours
- Sleep Mode — Block all except essential at night
- Family Mode — Safe search + adult blocking during school hours
- Custom — User-defined time windows with specific blocklists

**UI**: Weekly calendar grid with colored time blocks, each linked to a Profile.

### 10.3 New: Wi-Fi SSID-Based Profile Switching

```
"Home"   → SSID "MyHomeWifi" → Relaxed (Pi-hole handles rest)
"Office" → SSID "CorpWifi"   → Strict + no social media
"Public" → Any other Wi-Fi    → Maximum + force DoH
"Mobile" → Cellular           → Standard + data-saving
```
- `NetworkCallback` for connectivity changes
- Debounce 300ms to avoid rapid toggling during handoffs
- Note: Requires `ACCESS_FINE_LOCATION` on Android 10+

---

## 11. Backup, Export & Sync

### 11.1 Backup Improvements

Current: AutoBackupWorker with 5-backup rotation. Enhance with:

**Encrypted format** (`.hostshield-backup`):
- AES-256-GCM with user passphrase via PBKDF2
- Or Android Keystore-backed encryption for zero-passphrase local backups
- Include: dns_config, blocklists, custom_rules, profiles, schedules, app_rules, firewall_rules, settings

**Cloud sync options:**
| Method | Priority | Notes |
|--------|----------|-------|
| WebDAV (Nextcloud) | High | Self-hosted, privacy-friendly, OkHttp + WebDAV extensions |
| Google Drive / SAF | Medium | Zero setup for most users |

### 11.2 Config Sharing

- **QR code**: Encode config as QR (like `sdns://` stamps)
- **Deep link**: `hostshield://import?config=base64data`
- **Share intent**: Export via Android share sheet
- **Nearby Share**: For household device setup

---

## 12. Logging & Diagnostics

### 12.1 Query Log Improvements

Current: LogsScreen with search, filter, export. Enhance with:

- **Real-time streaming**: `LazyColumn` + `Flow<List<QueryLogEntry>>` with slide+fade animations
- **Color coding**: Allowed (green), Blocked (red), Cached (blue), CNAME-cloaked (purple)
- **Expanded detail**: DNSSEC status, JA3/JA4 fingerprint, GeoIP with country flag
- **Room + PagingSource** for historical logs with configurable retention (7/30/90 days)
- **"Jump to latest" FAB** when scrolled up (like terminal)
- **Pause button** to freeze stream for inspection

### 12.2 Diagnostic Report

Current: DiagnosticExporter. Enhance with:
- Battery usage (BatteryManager stats)
- Memory RSS
- VPN rebuild count + fd error count
- Sanitized last 100 queries
- Active blocklist rule counts
- Network state (metered, Wi-Fi SSID, carrier)

### 12.3 New: ACRA Crash Reporting

[ACRA](https://github.com/ACRA/acra) — open-source, self-hosted:
- No proprietary dependencies
- Offline queuing (sent on next launch)
- Backend: Acrarium (official) or custom HTTP endpoint
- Annotation-based config: `@AcraCore`, `@AcraHttpSender`

---

## 13. Notifications

### 13.1 Channel Architecture

```
hostshield_protection_status   — Foreground service (required, persistent)
hostshield_blocking_summary    — Periodic summaries (hourly/daily)
hostshield_new_app_alert       — New app network access detected
hostshield_schedule_change     — Profile auto-switched
hostshield_update_available    — App/blocklist updates
hostshield_diagnostic          — Errors, warnings, connectivity
hostshield_backup              — Backup completed/failed
```

Group with `NotificationChannelGroup`. Default non-essential to LOW importance.

### 13.2 VPN Notification Enhancement

- Show live stats: queries blocked today, current DNS latency
- Quick actions: "Pause 30min", "Switch Profile", "View Log"
- `BigTextStyle` for expandable stats
- Guide users to "Silent & Minimized" for the system VPN notification

---

## 14. New Feature Opportunities

### 14.1 High-Value New Features

| Feature | Inspiration | Impact |
|---------|-------------|--------|
| **Split tunneling** | RethinkDNS | Per-app VPN/DNS routing — exclude banking/work apps |
| **App-specific DNS rules** | RethinkDNS | Allow/deny domains per app (unique differentiator) |
| **Content filtering categories** | AdGuard Home | 12+ toggleable categories (Adult, Gambling, Social, Gaming, etc.) |
| **Safe Search enforcement** | AdGuard DNS | Force safe search on Google, Bing, YouTube, DuckDuckGo via DNS |
| **Parental controls** | NextDNS, AdGuard | Age-based profiles, time limits, PIN lock, activity reports |
| **DNS stamps (`sdns://`)** | DNSCrypt ecosystem | One-click DNS config, QR sharing |
| **WireGuard integration** | RethinkDNS | Proxy tunnels, per-app routing, multi-hop |
| **Local DNS server mode** | personalDNSfilter | Serve DNS to LAN devices — "portable Pi-hole" |
| **DNS benchmark** | LibreSpeed pattern | Measure latency to resolvers, auto-select fastest |
| **Connection tracker** | RethinkDNS | Real-time per-app connections with IP, port, protocol, bytes |

### 14.2 Content Filtering Categories

```
Ads & Trackers       [ON]  — Advertising, analytics
Malware & Phishing   [ON]  — Known malicious domains
Adult Content         [OFF] — Pornography, explicit
Gambling              [OFF] — Betting, casino
Social Media          [OFF] — Facebook, Instagram, TikTok
Gaming                [OFF] — Online games, stores
Streaming             [OFF] — Netflix, YouTube, Twitch
Dating                [OFF] — Dating apps/sites
Cryptocurrency        [OFF] — Mining, exchanges
Piracy                [OFF] — Torrent, illegal streaming
VPN & Proxy           [OFF] — VPN/proxy bypass services
New Domains           [OFF] — Registered < 30 days
```

### 14.3 Safe Search Enforcement

DNS-level enforcement (AdGuard's approach):
- Google: CNAME `www.google.com` → `forcesafesearch.google.com`
- Bing: CNAME `www.bing.com` → `strict.bing.com`
- YouTube: CNAME `www.youtube.com` → `restrict.youtube.com` (Moderate) or `restrictmoderate.youtube.com` (Strict)
- DuckDuckGo: CNAME `duckduckgo.com` → `safe.duckduckgo.com`

---

## 15. Battery & Performance Comparison

Understanding the performance cost of each blocking mode helps users choose the right one.

| Mode | Battery Impact | DNS Latency | Features Available | Root Required |
|------|---------------|-------------|-------------------|---------------|
| **VPN (full traffic)** | ~1-3%/day | +2-5ms (VPN overhead) | All (firewall, PCAP, stats, per-app) | No |
| **VPN (DNS-only)** | ~0.5%/day | +2-5ms | DNS blocking only, no firewall | No |
| **Root (iptables)** | ~0%/day | +0-1ms (local redirect) | DNS + firewall, no PCAP in VPN | Yes |
| **Proxy (future)** | ~0.5%/day | +1-3ms | DNS blocking, works with other VPNs | No |

**Testing strategy for new features:**
- DNS cache: Measure hit rate, serve-stale trigger rate, prefetch accuracy
- Adblock-syntax: Test against AdGuard's test suite (urlfilter has unit tests)
- Firewall rules: Verify per-app blocking with `adb shell dumpsys netstats`
- Battery: Use `adb shell dumpsys batterystats` before/after 24h runs
- Blocklist parsing: Benchmark against hagezi Pro++ (~380K rules) as stress test

---

## 16. Roadmap

### Phase 1: Core Engine Upgrades (v5.0)

| # | Feature | Effort | Status |
|---|---------|--------|--------|
| 1 | ~~**Adblock-syntax parsing** (`\|\|domain^`, `@@`, `$important`, `$dnsrewrite`)~~ | High | **DONE** — AdblockRuleParser with 4-level priority, auto-detect in HostsParser, HostsUpdateWorker integration |
| 2 | ~~**Serve-stale DNS cache** (RFC 8767)~~ | Low | **DONE** — CacheResult.isStale, getStale(), 3-day stale window |
| 3 | ~~**Negative caching** (NXDOMAIN/NODATA per RFC 2308, SERVFAIL per RFC 9520)~~ | Low | **DONE** — SOA-derived TTL, failureCache (5s TTL) |
| 4 | ~~**DNS cache prefetching** (Unbound algorithm, refresh at <10% TTL)~~ | Low | **DONE** — CacheResult.needsPrefetch, queryCount threshold |
| 5 | ~~**Hash set fast path** for exact domain matches before trie~~ | Low | **DONE** — exactBlockSet HashSet, O(1) before trie |
| 6 | ~~**Filter decision LRU cache** (blocked/allowed results)~~ | Low | **DONE** — decisionCache ConcurrentHashMap (8K max) |
| 7 | ~~**Two-tier cache** (L1 in-memory LRU + L2 persistent SQLite)~~ | Medium | **DONE** — DnsDiskCache SQLite (10K cap, WAL), warm on VPN start, persist every 60s |
| 8 | ~~**Bundle GeoLite2-Country + ASN** databases (replace ip-api.com)~~ | Medium | **DONE** — OfflineGeoIp + MaxMind dep + ProGuard |
| 9 | ~~**Consume AdGuard + NextDNS CNAME cloak databases**~~ | Low | **DONE** — CnameCloakUpdater + detector integration |
| 10 | ~~**HTTPS/SVCB record parsing** (RFC 9460)~~ | Medium | **DONE** — CnameCloakDetector.extractSvcbTargets() + DnsPacketBuilder constants |
| 11 | ~~**Max/min TTL caps** (configurable floor 60s, ceiling 86400s)~~ | Low | **DONE** — 60s floor, 24h ceiling, SOA for negative |

### Phase 2: Firewall & Network (v5.1)

| # | Feature | Effort | Impact |
|---|---------|--------|--------|
| 12 | ~~**Domain-per-app DNS rules**~~ | ~~High~~ | **DONE** v6.1 — AppDnsRuleEngine Hilt singleton. ConcurrentHashMap<packageName, List<CompiledRule>> for O(1) hot-path lookup. Wildcard support (*.facebook.com matches sub.facebook.com and bare domain). ALLOW rules take precedence over BLOCK. AppDnsRule Room entity + DAO, DB v12 migration. Wired into processIpv4Dns/processIpv6Dns before blocklist check |
| 13 | ~~**Metered vs unmetered network rules**~~ | Low | **ALREADY DONE** (v3.8.0) — FirewallRule.blockMetered + ContextState.isMetered |
| 14 | ~~**Per-app "block when screen off"** toggle~~ | Low | **ALREADY DONE** (v3.8.0) — FirewallRule.blockScreenOff + ContextState.isScreenOn |
| 15 | ~~**LAN toggle per app**~~ | Medium | **DONE** — FirewallRule.lanAllowed + LanDetector (RFC1918/RFC4193) + DB v10 |
| 16 | ~~**Category-based app blocking** (Social, Games, etc.)~~ | Medium | **DONE** — AppCategoryResolver using ApplicationInfo.category (API 26+) + heuristic fallback |
| 17 | ~~**Country-based blocking** with GeoIP~~ | Medium | **DONE** — FirewallRule.blockedCountries + OfflineGeoIp + DB v10 migration |
| 18 | ~~**DNS-only VPN mode** (port 53 only, ~0.5% battery)~~ | ~~Medium~~ | **DONE** v6.0 — `dns_only_mode` preference. Skips DNS trap IPs + DoH bypass IP routes in VPN builder (only virtual DNS routed). Skips per-app firewall + context-aware firewall in packet loop. Domain blocklist + threat intel still active |

### Phase 3: Privacy & Security (v5.2)

| # | Feature | Effort | Impact |
|---|---------|--------|--------|
| 19 | ~~**Expanded tracker DB** (400+ ETIP signatures)~~ | Medium | **DONE** — TrackerSignatureDb expanded from 60 to 405 signatures across 8 categories (Advertising, Analytics, Crash, Profiling, Social, Location, Fingerprinting, Identification) |
| 20 | ~~**Network-based tracker detection** (Disconnect + DuckDuckGo lists)~~ | ~~Medium~~ | **DONE** — NetworkTrackerDb with 200+ tracker domains across 8 categories, suffix-matching lookup in logAsyncRich(), new tracker_category/tracker_owner columns in dns_logs (migration v10→v11), 4 new DAO queries for tracker analytics |
| 21 | ~~**Enhanced privacy scoring** (tracker + permission + network)~~ | ~~Medium~~ | **DONE** v6.0 — Three-dimension weighted scoring: tracker penalty (40%, embedded SDKs + NetworkTrackerDb domains), permission penalty (25%, dangerous + high-risk Android permissions), network penalty (35%, block rate + suspicious TLDs + volume + tracker ratio). ScoreBreakdown in AppReport. Permission analysis via PackageManager |
| 22 | ~~**Threat intelligence feeds** (abuse.ch, Spamhaus DROP)~~ | Medium | **DONE** — ThreatIntelManager with IpRadixTrie (O(1) CIDR lookup) + ConcurrentHashMap domains. 5 feeds: URLhaus, Spamhaus DROP/EDROP, Emerging Threats, Disconnect Malware. ThreatIntelWorker daily refresh via WorkManager. Disk-persisted JSON cache |
| 23 | ~~**WebRTC leak test**~~ | Low | **DONE** — LeakTester.testWebRtcLeak() via WebView + RTCPeerConnection |
| 24 | ~~**IPv6 leak test**~~ | Low | **DONE** — LeakTester.testIpv6Leak() via IPv6 socket probe |
| 25 | ~~**Captive portal handling**~~ | Medium | **DONE** — CaptivePortalHandler: NetworkCallback for NET_CAPABILITY_CAPTIVE_PORTAL, auto-pauses VPN 3min, login notification via ALERT_CHANNEL_ID, auto-resumes on NET_CAPABILITY_VALIDATED |

### Phase 4: UI/UX Polish (v5.3)

| # | Feature | Effort | Impact |
|---|---------|--------|--------|
| 26 | ~~**Vico chart library** migration~~ | ~~Medium~~ | **DONE** v6.1 — Added vico:compose-m3:2.0.1 dependency. VicoCharts.kt with 5 reusable composables: HourlyBlockedChart (line), DailyTrendChart (stacked columns), QueryTypeDistribution (donut), LatencyHistogram (colored columns), TopDomainsChart (horizontal bars). Teal/Mauve/Green color scheme, dark-background compatible |
| 27 | ~~**Lottie shield animation**~~ | ~~Low~~ | **DONE** v6.1 — Added lottie-compose:6.6.2 dependency. shield_animation.json (512x512, 3s loop, shield with pulse/scan/particle effects). ShieldAnimation.kt with 3 composables: ShieldAnimation (looping Lottie), ShieldStatusIndicator (blocked count overlay), AnimatedShieldToggle (interactive with scale pulse) |
| 28 | ~~**Material 3 dynamic theming**~~ | ~~Medium~~ | **ALREADY DONE** — Full M3 implementation: darkColorScheme(), Teal/Mauve/Peach accent colors, all Compose components use M3. Dynamic theming ready via Compose BOM 2024.12.01 |
| 29 | ~~**Glance widget overhaul** (toggle+stats combo)~~ | ~~Medium~~ | **DONE** v6.1 — Added glance-appwidget:1.1.1 + glance-material3:1.1.1 dependencies. HostShieldGlanceWidget (toggle + stats combo, 3x2), HostShieldStatsGlanceWidget (compact stats, 2x2). GlanceTheme + Material 3 colors. XML metadata for both widgets. Replaces legacy RemoteViews approach |
| 30 | ~~**Quick Settings tile** improvements~~ | ~~Low~~ | **DONE** v6.0 — Tile subtitle shows "X blocked today" via DnsVpnService.currentBlockedCount companion property, updates on onStartListening() |
| 31 | ~~**Real-time log animations**~~ | ~~Low~~ | **DONE** v6.1 — AnimatedLogFeed composable with slide-in + fade entry animations. Pulsing status dots for blocked entries, highlight flash on new entries, staggered animation delays. LiveActivityIndicator (pulsing green dot). QueryRateSparkline (mini Canvas sparkline). Drop-in replacement for static LiveLogRow |
| 32 | ~~**Onboarding flow** refresh (6 screens, Lottie, progressive)~~ | ~~Medium~~ | **DONE** v6.1 — Expanded from 3-4 to 6 screens: Welcome, Method Selection, Features Overview (staggered animations, 6 feature cards), DNS Configuration (5 providers with RadioButton selection), Private DNS Warning (conditional), Ready. Progressive disclosure pattern with animated transitions |

### Phase 5: Automation & Ecosystem (v5.4)

| # | Feature | Effort | Impact |
|---|---------|--------|--------|
| 33 | ~~**Expanded Tasker intents** (SET_PROFILE, SET_DNS, PAUSE)~~ | ~~Low~~ | **DONE** v6.0 — ACTION_SET_PROFILE (by name, case-insensitive), ACTION_SET_DNS (comma-separated servers), ACTION_PAUSE (1-1440 min with auto-resume). Same security/rate-limiting/audit-logging as existing actions |
| 34 | ~~**Wi-Fi SSID-based profiles**~~ | ~~Medium~~ | **DONE** (pre-existing) — BlockingProfile.wifiSsids field + ProfileScheduleWorker matches current SSID via WifiManager. WiFi match takes priority over time-based scheduling. Worker runs every 15min via WorkManager |
| 35 | ~~**Named schedules** (Focus, Sleep, Family modes)~~ | ~~Medium~~ | **DONE** v6.0 — SchedulePresets with 5 built-in presets (Focus, Sleep, Family, Work, Kids). Each defines time range, days, source categories. applyPreset() creates BlockingProfile via ProfileDao |
| 36 | ~~**Encrypted backup format** (AES-256-GCM)~~ | ~~Medium~~ | **DONE** v6.0 — EncryptedBackup Hilt singleton. AES-256-GCM with PBKDF2WithHmacSHA256 (100K iterations). Binary format: HSBACKUP magic + version + salt(16) + IV(12) + ciphertext. isEncryptedBackup() detects format. javax.crypto only |
| 37 | ~~**WebDAV cloud sync**~~ | ~~Medium~~ | **DONE** v6.1 — WebDavSync Hilt singleton. OkHttp-based WebDAV client (PUT, GET, DELETE, PROPFIND, MKCOL). Basic auth. PROPFIND XML parsing. Higher-level syncBackup/fetchLatestBackup/listBackups methods. SyncResult sealed class for error handling |
| 38 | ~~**QR code config sharing**~~ | ~~Low~~ | **DONE** v6.1 — QrConfigSharing utility. GZIP + Base64 encoding with "HS:" scheme prefix. ShareableConfig data class for user rules, DNS settings, sources. Encode/decode methods for QR generation |
| 39 | ~~**ACRA crash reporting**~~ | ~~Low~~ | **DONE** v6.1 — CrashReporter Hilt singleton. Custom Thread.UncaughtExceptionHandler (no ACRA dependency). Collects stack trace, device info, memory stats. JSON files in crashes/ dir (max 20). getCrashReports/clearCrashReports API |

### Phase 6: Advanced Features (v6.0)

| # | Feature | Effort | Impact |
|---|---------|--------|--------|
| 40 | ~~**Content filtering categories** (12+ toggleable)~~ | ~~High~~ | **DONE** v6.1 — ContentFilterManager Hilt singleton. 12+ categories (Adult, Gambling, Social, Gaming, Streaming, Dating, VPN/Proxy, Malware, Crypto, News, Shopping). ConcurrentHashMap domain index with suffix matching. contentFilterCategories StringSet preference. Wired into processIpv4Dns/processIpv6Dns before blocklist check |
| 41 | ~~**Safe Search enforcement** (DNS-level)~~ | ~~Medium~~ | **DONE** v6.0 — SafeSearchEnforcer rewrites DNS for Google/Bing/DuckDuckGo/YouTube to safe-search IPs. Wired into processIpv4Dns/processIpv6Dns before blocklist check. safeSearchEnabled preference (default false) |
| 42 | ~~**DNS stamp support** (`sdns://`)~~ | ~~Medium~~ | **DONE** v6.0 — DnsStampParser Hilt singleton. Parses and encodes sdns:// URLs per DNSCrypt spec. Supports Plain DNS (0x00), DNSCrypt (0x01), DoH (0x02), DoT (0x03). LP-encoded fields, hash chains, DNSSEC/no-log/no-filter flags |
| 43 | ~~**Split tunneling** (per-app VPN routing)~~ | ~~High~~ | **ALREADY DONE** — excludedApps preference + VpnService.Builder.addDisallowedApplication() in DnsVpnService.startVpn(). Excluded apps bypass VPN entirely (no DNS filtering, no firewall). Self-exclusion also applied |
| 44 | ~~**DNS-over-TLS** as upstream option~~ | ~~Medium~~ | **DONE** v6.0 — DotResolver Hilt singleton. RFC 7858 (2-byte length prefix + wire format) over TLSv1.3. 4 providers (Cloudflare, Google, Quad9, AdGuard). SNI + hostname verification. Per-query TLS connections |
| 45 | ~~**DNS-over-QUIC**~~ | ~~High~~ | **DONE** v6.2 — DoqResolver Hilt singleton. RFC 9250 DNS over QUIC. Builds QUIC Initial packets with DNS query in STREAM frame (stream 0). QUIC variable-length integer codec. Frame parser (STREAM, ACK, CRYPTO, PADDING). 3 providers (AdGuard, Nextdns, Mullvad). Automatic fallback to DoT if QUIC handshake fails. 1200-byte PMTU padding. Short/long header response parsing |
| 46 | ~~**Connection tracker** (real-time per-app)~~ | ~~High~~ | **DONE** v6.1 — ConnectionTracker Hilt singleton. ConcurrentHashMap<packageName, records> with ring buffer (500/app, 5000 total). AppConnectionSummary aggregation. SharedFlow<ConnectionRecord> for live UI updates. recordConnection() called from packet loop |
| 47 | ~~**nDPI protocol detection + JA3/JA4**~~ | ~~High~~ | **DONE** v6.1 — TlsFingerprinter Hilt singleton. Parses TLS ClientHello from raw packet bytes. Computes JA3 (MD5) and JA4 (SHA-256 truncated) fingerprints. GREASE value filtering (RFC 8701). SNI extraction. Known fingerprint database (Chrome, Firefox, Safari, Python, curl, OkHttp, Java). ConcurrentHashMap identity cache |
| 48 | ~~**Parental controls** (age profiles, PIN lock)~~ | ~~High~~ | **DONE** v6.1 — ParentalControlManager Hilt singleton. 3 age profiles (Child, Teen, Adult) with escalating ContentCategory restrictions. SHA-256 PIN lock (4-digit). Wired into processIpv4Dns/processIpv6Dns after content filter. Preferences: parental_enabled, parental_pin_hash, parental_age_profile |
| 49 | ~~**Local DNS server mode**~~ | ~~Medium~~ | **DONE** v6.0 — LocalDnsServer Hilt singleton. UDP server on port 5353 (no root). Blocklist check → NXDOMAIN or forward to upstream. Concurrent query handling via coroutines. LAN devices point DNS to phone IP:5353 |
| 50 | ~~**DNS benchmark**~~ | ~~Low~~ | **DONE** v6.0 — DnsBenchmark Hilt singleton. Tests 10 DNS resolvers + custom servers via raw UDP DatagramSocket queries. Concurrent testing, 3s timeout, avg/min/max latency, success rate. Results sorted by avgLatencyMs |
| 51 | ~~**WireGuard proxy integration**~~ | ~~High~~ | **DONE** v6.2 — WireGuardProxy Hilt singleton. Noise_IKpsk2 handshake (Type 1 init, Type 2 response). Transport data (Type 4) with AES-256-GCM encryption. Curve25519 ephemeral key generation. HKDF key derivation. TAI64N timestamps. DNS-over-WireGuard: inner IP/UDP packet construction + extraction. WgConfig data class with private key, peer public key, PSK, endpoint, DNS server. NAT keepalive support |
| 52 | ~~**Proxy mode** (no VPN, no root — tri-mode)~~ | ~~Medium~~ | **DONE** v6.1 — DnsProxyService foreground service. Local DNS proxy on port 5353 using LocalDnsServer. No VPN or root required. BlocklistHolder.isBlocked() for filtering, upstream forwarding for allowed queries |

---

## Sources

### DNS & Blocking
- [RethinkDNS](https://github.com/celzero/rethink-app) — DNS/Firewall/VPN, Kotlin+Go, succinct radix-trie
- [AdGuard urlfilter](https://github.com/AdguardTeam/urlfilter) — Reference adblock-syntax engine
- [AdGuard DnsLibs](https://github.com/AdguardTeam/DnsLibs) — C++ DNS filtering/encryption (DoH, DoT, DoQ)
- [AdGuard CNAME Trackers](https://github.com/AdguardTeam/cname-trackers) — Auto-updated CNAME cloak database
- [NextDNS CNAME Blocklist](https://github.com/nextdns/cname-cloaking-blocklist) — Known CNAME cloaking destinations
- [AdGuard HostlistCompiler](https://github.com/AdguardTeam/HostlistCompiler) — Multi-source list compiler
- [AdGuard HostlistsRegistry](https://github.com/AdguardTeam/HostlistsRegistry) — Curated list metadata
- [serverless-dns/blocklists](https://github.com/serverless-dns/blocklists) — Compressed radix-trie
- [personalDNSfilter](https://github.com/IngoZenz/personaldnsfilter) — CNAME cloaking, LAN DNS, filter decision cache
- [InviZible Pro](https://github.com/Gedsh/InviZible) — DNSCrypt + Tor + I2P, tri-mode architecture
- [DNS66](https://github.com/julian-klode/dns66) — DNS-only VPN routing, minimal battery
- [Nebulo](https://github.com/Ch4t4r/Nebulo) — Zero-dependency DNS, DoQ, DNS stamps, 4 blocklist formats
- [AdAway](https://github.com/AdAway/AdAway) — Root hosts-based blocking, local web server for blocked responses
- [dnsjava](https://github.com/dnsjava/dnsjava) — Java DNS library with DNSSEC validation
- [MiniDNS](https://github.com/MiniDNS/minidns) — Lightweight Java DNS with DNSSEC+DANE

### Blocklists
- [StevenBlack/hosts](https://github.com/StevenBlack/hosts) — 29.9k stars, gold standard merged hosts
- [hagezi/dns-blocklists](https://github.com/hagezi/dns-blocklists) — 16k+ stars, 7 output formats
- [OISD](https://oisd.nl/) — ABP/wildcard format, deprecated hosts Jan 2024
- [Energized Protection](https://github.com/EnergizedProtection/block) — Multi-pack approach
- [FilterLists](https://github.com/collinbarrett/FilterLists) — Largest list directory
- [phani-kb/dns-toolkit](https://github.com/phani-kb/dns-toolkit) — Overlap C/U/X metrics

### Firewall & Network
- [NetGuard](https://github.com/M66B/NetGuard) — VPN sinkhole, screen on/off rules
- [AFWall+](https://github.com/ukanth/afwall) — iptables firewall, LAN toggle, roaming
- [PCAPdroid](https://github.com/emanuele-f/PCAPdroid) — PCAP-NG, nDPI, JA3/JA4
- [nDPI](https://github.com/ntop/nDPI) — Deep packet inspection, 300+ protocols
- [ShizuWall](https://github.com/AhmetCanArslan/ShizuWall) — Shizuku-based firewall

### Privacy & Threat Intel
- [TrackerControl](https://github.com/TrackerControl/tracker-control-android) — Dual static+network detection
- [Exodus Privacy / ETIP](https://github.com/Exodus-Privacy/etip) — Tracker signature database
- [GeoIP2-java](https://github.com/maxmind/GeoIP2-java) — MaxMind MMDB reader
- [IPCheck.ing/MyIP](https://github.com/jason5ng32/MyIP) — DNS/WebRTC/IPv6 leak testing
- [AbuseIPDB](https://www.abuseipdb.com/api.html) — IP reputation API

### UI/UX & Libraries
- [Vico](https://github.com/patrykandpatrick/vico) — Compose-native charts
- [Lottie Android](https://github.com/airbnb/lottie-android) — Animation rendering
- [ACRA](https://github.com/ACRA/acra) — Open-source crash reporting
- [Jetpack Glance](https://developer.android.com/develop/ui/compose/glance) — Widget framework
- [DNS Stamps Specification](https://dnscrypt.info/stamps-specifications/)

### Key RFCs Referenced
- [RFC 8767](https://www.rfc-editor.org/rfc/rfc8767.html) — Serving Stale Data to Improve DNS Resiliency
- [RFC 2308](https://datatracker.ietf.org/doc/html/rfc2308) — Negative Caching of DNS Queries
- [RFC 9520](https://datatracker.ietf.org/doc/rfc9520/) — Negative Caching of DNS Resolution Failures
- [RFC 9849](https://www.rfc-editor.org/rfc/rfc9849.html) — TLS Encrypted Client Hello (ECH)
- [RFC 9460](https://www.rfc-editor.org/rfc/rfc9460) — SVCB and HTTPS DNS Resource Records
- [RFC 9250](https://www.rfc-editor.org/rfc/rfc9250) — DNS over Dedicated QUIC Connections (DoQ)
- [RFC 8484](https://www.rfc-editor.org/rfc/rfc8484) — DNS Queries over HTTPS (DoH)
- [RFC 7858](https://www.rfc-editor.org/rfc/rfc7858) — DNS over Transport Layer Security (DoT)
- [RFC 6891](https://www.rfc-editor.org/rfc/rfc6891) — Extension Mechanisms for DNS (EDNS0)
- [RFC 7830](https://www.rfc-editor.org/rfc/rfc7830) — The EDNS(0) Padding Option
- [RFC 8914](https://www.rfc-editor.org/rfc/rfc8914) — Extended DNS Errors (EDE)
