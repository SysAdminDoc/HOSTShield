# HostShield

![Version](https://img.shields.io/badge/version-3.0.0-blue)
![License](https://img.shields.io/badge/license-GPL--3.0-green)
![Platform](https://img.shields.io/badge/platform-Android%207+-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?logo=kotlin&logoColor=white)
![Status](https://img.shields.io/badge/status-active-success)

> System-wide DNS-based ad/tracker/malware blocker for Android with per-app firewall, CNAME cloaking detection, DNS response caching, DoH with certificate pinning, and a professional dark-themed UI.

## Quick Start

1. Download the latest APK from [Releases](https://github.com/SysAdminDoc/HostShield/releases)
2. Install and launch — the onboarding wizard guides you through setup
3. Choose **VPN mode** (no root) or **Root mode** (better battery life)
4. Enable blocking — ads and trackers are filtered immediately

## Features

| Feature | Description |
|---------|-------------|
| **DNS Blocking** | Trie-based O(m) domain lookup with 200K+ domains from curated blocklists |
| **CNAME Cloaking Detection** | Inspects CNAME chains in DNS responses — catches first-party tracking that bypasses other blockers |
| **DNS Response Cache** | 2000-entry LRU cache with TTL-aware expiration — 60-70% cache hit rate reduces latency |
| **VPN Mode** | Local DNS filtering via Android VPN API — no root required, per-app stats |
| **Root Mode** | Direct `/etc/hosts` modification + iptables firewall — zero battery overhead |
| **Per-App Firewall** | Block Wi-Fi, mobile data, or VPN per-app with iptables (root) |
| **DoH (DNS-over-HTTPS)** | Cloudflare, Google, Quad9, NextDNS, AdGuard — with SHA-256 certificate pinning |
| **DoH Bypass Prevention** | Blocks 53+ known DoH provider domains + wildcard patterns to prevent apps bypassing DNS filtering |
| **DNS Trap** | Routes hardcoded DNS IPs (8.8.8.8, 1.1.1.1, etc.) through the VPN tunnel |
| **TCP DNS Handling** | Full TCP DNS support for responses >512 bytes |
| **IPv6 Support** | Full IPv6 DNS processing + UID attribution via `/proc/net/tcp6` |
| **Block Response Types** | NXDOMAIN (with SOA), Null IP (0.0.0.0/::), or REFUSED — configurable |
| **Blocking Profiles** | Switch between profile sets on schedule |
| **Live Query Stream** | Real-time DNS log feed with zero-latency SharedFlow |
| **7-Day Trend Charts** | Blocked vs. total queries line chart, hourly bar chart, daily history |
| **Per-Query Detail View** | Query type, response time, upstream server, CNAME chain, resolved IPs |
| **Diagnostic Export** | One-tap shareable report with device info, config, logs, network state |
| **AdAway Import** | Import hosts files, sources, and rules from AdAway backups |
| **Remote DoH Updates** | Supplementary DoH bypass domains fetched from GitHub without app updates |
| **Automation API** | Signature-protected broadcast intents for Tasker/MacroDroid |

## How It Works

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   App DNS    │────>│  HostShield VPN  │────>│  DNS Response   │
│   Query      │     │  Packet Engine   │     │  Cache (LRU)    │
└─────────────┘     └────────┬─────────┘     └────────┬────────┘
                             │                         │
                    ┌────────▼─────────┐      Cache    │ Miss
                    │  BlocklistHolder │      Hit ◄────┘
                    │  (Trie Lookup)   │               │
                    └────────┬─────────┘      ┌────────▼────────┐
                             │                │  Upstream DNS   │
                    Blocked? │                │  (UDP/DoH)      │
                 ┌───────────┼───────────┐    └────────┬────────┘
                 │           │           │             │
           ┌─────▼────┐  ┌──▼───┐  ┌────▼────┐  ┌────▼─────────┐
           │ NXDOMAIN  │  │ 0.0.0│  │ REFUSED │  │ CNAME Cloak  │
           │ + SOA     │  │ .0   │  │         │  │ Detection    │
           └──────────┘  └──────┘  └─────────┘  └──────────────┘
```

## Build

```bash
# Prerequisites: JDK 17, Android SDK 34

./gradlew assembleFullDebug     # Full flavor (root features)
./gradlew assemblePlayDebug     # Play Store flavor
./gradlew testFullDebugUnitTest # Run unit tests
```

## Configuration

### Blocklist Sources
Ships with curated defaults (Steven Black, OISD, HaGeZi, 1Hosts). Add custom URL sources via Settings → Sources in standard hosts file format.

### Automation API
Broadcast intents for Tasker/MacroDroid (requires signature permission or ADB grant):

```bash
adb shell am broadcast -a com.hostshield.action.ENABLE -n com.hostshield/.service.AutomationReceiver
adb shell am broadcast -a com.hostshield.action.DISABLE -n com.hostshield/.service.AutomationReceiver
adb shell am broadcast -a com.hostshield.action.STATUS -n com.hostshield/.service.AutomationReceiver
adb shell am broadcast -a com.hostshield.action.REFRESH_BLOCKLIST -n com.hostshield/.service.AutomationReceiver
```

## FAQ

**VPN mode vs Root mode?** Root mode: zero battery overhead, requires rooted device. VPN mode: works on any device, ~1-3% battery, persistent notification.

**Why does it use a VPN?** Entirely local — no traffic goes to a remote server. Standard technique used by NetGuard, RethinkDNS, Blokada.

**How is this different from AdAway?** CNAME cloaking detection, DNS response caching, DoH with cert pinning, per-app firewall, live query streaming, 7-day trend charts, and modern Material 3 dark UI.

## Project Structure

```
app/src/main/java/com/hostshield/
├── data/           # Room DB, DAOs, entities, preferences, repository
├── di/             # Hilt dependency injection modules
├── domain/         # BlocklistHolder (trie), HostsParser
├── service/        # VPN, root logger, iptables, DoH, DNS cache,
│                   # CNAME detector, packet builder, workers
├── ui/screens/     # Home, Logs, Stats, Settings, Firewall,
│                   # Onboarding, DNS Tools, Rules
└── util/           # Root utils, backup, import/export, diagnostics
```

## Contributing

Issues and PRs welcome. Run `./gradlew testFullDebugUnitTest` before submitting.

## License

GPL-3.0
