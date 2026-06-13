# HostShield

![Version](https://img.shields.io/badge/version-6.6.9-blue)
![License](https://img.shields.io/badge/license-needs%20reconciliation-yellow)
![Platform](https://img.shields.io/badge/platform-Android%208+-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)
![Status](https://img.shields.io/badge/status-active-success)

> System-wide DNS-based ad/tracker/malware blocker for Android with per-app firewall, CNAME cloaking detection, serve-stale DNS caching, fail-closed DoH certificate pinning, offline GeoIP, and a professional AMOLED dark UI with an optional high-contrast mode.

Current module baseline: v6.6.9, versionCode 77.

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
| **DNS Response Cache** | 2000-entry LRU cache with serve-stale, negative caching, SERVFAIL caching, and prefetch |
| **VPN Mode** | Local DNS filtering via Android VPN API — no root required, per-app stats |
| **Root Mode** | iptables DNS redirection and per-app firewall support for rooted devices |
| **Per-App Firewall** | Block Wi-Fi, mobile data, or VPN per-app with iptables (root) |
| **DoH (DNS-over-HTTPS)** | Cloudflare, Google, Quad9, NextDNS, AdGuard, Mullvad, CleanBrowsing — with fail-closed SHA-256 certificate pinning |
| **DoH Bypass Prevention** | Blocks 65+ known DoH provider domains + wildcard patterns to prevent apps bypassing DNS filtering |
| **DNS Trap** | Routes hardcoded DNS IPs (8.8.8.8, 1.1.1.1, etc.) through the VPN tunnel |
| **TCP DNS Handling** | Full TCP DNS support for responses >512 bytes |
| **IPv6 Support** | Full IPv6 DNS processing + UID attribution via `/proc/net/tcp6` |
| **Block Response Types** | NXDOMAIN (with SOA), Null IP (0.0.0.0/::), or REFUSED — configurable |
| **Blocking Profiles** | Switch between profile sets on schedule |
| **Live Query Stream** | Real-time DNS log feed with zero-latency SharedFlow |
| **7-Day Trend Charts** | Blocked vs. total queries line chart, hourly bar chart, daily history |
| **Per-Query Detail View** | Query type, response time, upstream server, CNAME chain, resolved IPs, GeoIP |
| **Tracker SDK Scanner** | Exodus-style APK dex scanning for 405 tracker SDK signatures |
| **Online GeoIP Fallback** | ipapi.co over HTTPS for city-level detail when offline GeoIP is not enough |
| **Diagnostic Export** | One-tap shareable report with device info, config, logs, network state |
| **AdAway Import** | Import hosts files, sources, and rules from AdAway backups |
| **Remote DoH Updates** | Supplementary DoH bypass domains fetched from GitHub without app updates |
| **Automation API** | Signature-protected broadcast intents for Tasker/MacroDroid |
| **High-Contrast AMOLED** | Settings toggle for pure-black surfaces, brighter text, stronger semantic colors, chart contrast, and high-contrast widgets |

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

```powershell
# Prerequisites: JDK 17+, Android SDK 36, AGP 9.2

./gradlew assembleFullDebug     # Full flavor (root features)
./gradlew assemblePlayDebug     # Play Store flavor
./gradlew testFullDebugUnitTest # Run unit tests

# From the repository root before release:
# powershell -ExecutionPolicy Bypass -File .\tools\check-release-docs.ps1
# powershell -ExecutionPolicy Bypass -File .\tools\check-cronet-posture.ps1
# powershell -ExecutionPolicy Bypass -File .\tools\release-provenance.ps1
```

## Configuration

### Blocklist Sources
Ships with curated defaults (Steven Black, OISD, HaGeZi, 1Hosts). Add custom URL sources via Settings → Sources in hosts, domains-only, or DNS adblock syntax.

### Automation API
Broadcast intents for Tasker/MacroDroid, shell, and same-signature companion apps. Canonical actions use `com.hostshield.ACTION_*`; older lowercase `com.hostshield.action.*` aliases are accepted for compatibility.

```bash
adb shell am broadcast -a com.hostshield.ACTION_ENABLE -n com.hostshield/.service.AutomationReceiver
adb shell am broadcast -a com.hostshield.ACTION_DISABLE -n com.hostshield/.service.AutomationReceiver
adb shell am broadcast -a com.hostshield.ACTION_STATUS -n com.hostshield/.service.AutomationReceiver
adb shell am broadcast -a com.hostshield.ACTION_REFRESH_BLOCKLIST -n com.hostshield/.service.AutomationReceiver
adb shell am broadcast -a com.hostshield.ACTION_SET_PROFILE --es profile_name Work -n com.hostshield/.service.AutomationReceiver
adb shell am broadcast -a com.hostshield.ACTION_SET_DNS --es dns_servers "9.9.9.9,149.112.112.112" -n com.hostshield/.service.AutomationReceiver
adb shell am broadcast -a com.hostshield.ACTION_PAUSE --ei duration_minutes 5 -n com.hostshield/.service.AutomationReceiver
adb shell am broadcast -a com.hostshield.ACTION_PAUSE --ei duration_minutes 0 -n com.hostshield/.service.AutomationReceiver
```

## FAQ

**VPN mode vs Root mode?** Root mode: zero battery overhead, requires rooted device. VPN mode: works on any device, ~1-3% battery, persistent notification.

**Why does it use a VPN?** Entirely local — no traffic goes to a remote server. Standard technique used by NetGuard, RethinkDNS, Blokada.

**Android 15/16 foreground-service behavior?** VPN/root/proxy protection uses `systemExempted` foreground services. Timeout and denied background-start events are written to the local diagnostic export as `foreground_service_timeout` or `foreground_service_start_failed`.

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

This project is licensed under the [GNU General Public License v3.0](../LICENSE).
