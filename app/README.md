# HostShield

![Version](https://img.shields.io/badge/version-1.6.0-blue)
![License](https://img.shields.io/badge/license-GPL--3.0-green)
![Platform](https://img.shields.io/badge/platform-Android%207+-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?logo=kotlin&logoColor=white)
![Status](https://img.shields.io/badge/status-active-success)

> System-wide ad, tracker, and malware blocker for Android with root DNS proxy, iptables network firewall, and per-app traffic control.

![Screenshot](screenshot.png)

## Quick Start

```bash
git clone https://github.com/SysAdminDoc/HostShield.git
cd HostShield
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or download the latest APK from [Releases](https://github.com/SysAdminDoc/HostShield/releases).

## Features

### Blocking Modes

| Mode | Root Required | Method | Per-App Attribution |
|------|:---:|--------|:---:|
| VPN Mode | No | Local VPN intercepts DNS queries | No |
| Root Mode | Yes | DNS proxy on 127.0.0.1:5454 via iptables NAT | Yes |

### Network Firewall (Root)

| Feature | Description |
|---------|-------------|
| Per-App WiFi Control | Block/allow individual apps on WiFi |
| Per-App Mobile Data | Block/allow individual apps on cellular |
| Per-App VPN Control | Block/allow individual apps through VPN tunnels |
| NFLOG Connection Log | Real-time log of all blocked connections |
| Auto-Reapply | Rules re-applied on network changes and boot |
| Diagnostic Dump | Full iptables chain inspection |
| Script Export | Export rules as standalone shell script |
| Bulk Operations | Block all WiFi / block all mobile data in one tap |

### DNS Tools

| Tool | Description |
|------|-------------|
| DNS Lookup | Resolve domains with blocklist check and latency |
| Batch Test | Test multiple domains at once against blocklist |
| Ping | ICMP ping with timeout (15s) |
| Traceroute | Path tracing via `tracepath` (rootless) or `traceroute` (30s timeout) |
| DNS Cache | View and flush system DNS cache |
| DoH Config | DNS-over-HTTPS provider selection (Cloudflare, Google, Quad9, AdGuard, Mullvad) |
| Custom Upstream | Configure custom DNS server for root proxy |

### Privacy & Security

| Feature | Description |
|---------|-------------|
| DoH Bypass Prevention | Blocks 15+ known DoH providers at DNS level (Google, Cloudflare, Quad9, NextDNS, AdGuard, OpenDNS, CleanBrowsing) |
| DoT Bypass Prevention | Root: iptables REJECT on port 853; VPN: silent drop via TUN routing |
| DoQ/QUIC Bypass Prevention | Root: blocks UDP 443 to known DoH IPs; VPN: drops all non-DNS to trapped IPs |
| Firefox DoH Canary | NXDOMAIN for `use-application-dns.net` disables Firefox built-in DoH automatically |
| DNS Trap | Routes hardcoded public DNS IPs (8.8.8.8, 1.1.1.1, 9.9.9.9, etc.) through filter |
| Private DNS Detection | Warns when Android Private DNS could bypass filtering |
| Battery Optimization | OEM-specific guidance (Samsung, Xiaomi, OnePlus, Huawei, etc.) |
| Per-App DNS Block | NXDOMAIN all queries for selected apps |
| No Analytics | Zero data collection, telemetry, or tracking |
| High-Frequency Tracker Alerts | Notifications when apps exceed 50 blocked queries in 5 minutes |

### System Integration

| Feature | Description |
|---------|-------------|
| Quick Settings Tile | Toggle protection from notification shade |
| Home Widget | Status and toggle widget |
| Tasker / MacroDroid API | Broadcast intents for automation (enable, disable, toggle, firewall) |
| Boot Persistence | Auto-restores VPN, root DNS, and iptables on boot |
| Backup / Restore | Full config export including firewall rules |
| Profile Scheduling | Time-based blocking profile switching with firewall integration |
| Update Checker | Check for new releases from GitHub directly in Settings |
| Foreground Service | Persistent notification keeps root mode alive on Android 14+ |
| Network Stats | Per-app data usage with upload/download breakdown |

## Architecture

```
+----------------------------------------------------------+
|                     HostShield App                        |
+----------------+----------------+------------------------+
|  VPN Mode      |  Root Mode     |  Network Firewall      |
|                |                |                        |
|  DnsVpnService |  RootDnsLogger |  IptablesManager       |
|  (local VPN)   |  (DNS proxy    |  (AFWall+ chains)      |
|                |   + logcat     |                        |
|                |   + dumpsys)   |  NflogReader           |
|                |                |  (connection log)      |
+----------------+----------------+------------------------+
|  BlocklistHolder (Trie-optimized domain matching)        |
+----------------------------------------------------------+
|  Room Database (v4)                                      |
|  host_sources | user_rules | dns_logs | firewall_rules   |
|  block_stats  | profiles   | connection_log              |
+----------------------------------------------------------+
|  DataStore Preferences | WorkManager | Hilt DI           |
+----------------------------------------------------------+
```

### Root DNS Proxy (3-Coroutine Design)

```
                  +-------------------+
                  |   iptables NAT    |
                  |   :53 -> :5454    |
                  +---------+---------+
                            |
+---------------------------v----------------------------+
|             DNS Proxy (127.0.0.1:5454)                 |
|                                                        |
|  1. Parse hostname from raw DNS packet                 |
|  2. Check BlocklistHolder (trie lookup)                |
|  3. If blocked: return NXDOMAIN (flags 0x8403)         |
|  4. If allowed: forward upstream, relay response       |
|  5. Log query to Room (hostname, timestamp)            |
+--------------------------------------------------------+

+-----------------------+    +----------------------------+
|  UID Resolver         |    |  Dumpsys Poller            |
|                       |    |  (fallback, every 3s)      |
|  - Enables verbose    |    |                            |
|    DnsResolver logs   |    |  - Reads DnsQueryLog       |
|  - Tails logcat for   |    |    ring buffer             |
|    hostname + UID     |    |  - Enriches DB entries     |
|  - Builds cache       |    |    with missing app info   |
|    (30s TTL)          |    |                            |
+-----------------------+    +----------------------------+
```

### iptables Chain Hierarchy

```
OUTPUT -> hs-main
  +-- hs-wifi    (wlan+, eth+, ap+)
  +-- hs-mobile  (rmnet+, ccmni+, pdp+)
  +-- hs-vpn     (tun+, pptp+, l2tp+)
  +-- hs-lan     (10.0.0.0/8, 192.168.0.0/16, 172.16.0.0/12)
  +-- hs-reject  (NFLOG group 40 + REJECT)
```

## Automation API

Control HostShield from Tasker, MacroDroid, or shell:

```bash
am broadcast -a com.hostshield.ACTION_ENABLE       # Start protection
am broadcast -a com.hostshield.ACTION_DISABLE      # Stop protection
am broadcast -a com.hostshield.ACTION_TOGGLE       # Toggle on/off
am broadcast -a com.hostshield.ACTION_APPLY_FIREWALL   # Apply iptables rules
am broadcast -a com.hostshield.ACTION_CLEAR_FIREWALL   # Clear iptables rules
am broadcast -a com.hostshield.ACTION_STATUS       # Query status
```

**Tasker:** Action > Send Intent > Action: `com.hostshield.ACTION_TOGGLE` > Target: Broadcast Receiver

## Building

### Prerequisites

- Android Studio Hedgehog+
- JDK 17+
- Android SDK 35
- Kotlin 1.9+

```bash
./gradlew assembleDebug       # Debug APK
./gradlew assembleRelease     # Release APK (requires signing config)
```

### Dependencies

| Library | Purpose |
|---------|---------|
| Jetpack Compose + Material 3 | UI framework |
| Room 2.6 | SQLite database with migrations |
| Hilt 2.53 | Dependency injection |
| WorkManager 2.10 | Background scheduling |
| DataStore 1.1 | Preferences |
| OkHttp 4.12 | HTTP client |
| libsu 6.0 (topjohnwu) | Root shell access |

## FAQ

**Does this work without root?**
Yes. VPN mode works on any device. Root mode and the network firewall require root (Magisk, KernelSU, etc).

**Will this drain my battery?**
VPN mode has minimal impact. Root mode runs a lightweight DNS proxy. The iptables firewall has zero battery overhead (kernel-level).

**Why doesn't root DNS attribution show the app for every query?**
Android's DnsResolver service (netd, UID 1051) makes DNS queries on behalf of apps. The requesting app's UID only exists inside the dnsproxyd socket (SO_PEERCRED). HostShield uses verbose DnsResolver logging + logcat parsing + dumpsys polling to correlate queries, but there can be a small attribution gap.

**Can I use this alongside other VPNs?**
In root mode, yes. In VPN mode, no -- Android only allows one active VPN.

**What Android versions are supported?**
Android 7+ (API 24). The iptables firewall works best on Android 10+ with stock or lightly modified kernels.

## Contributing

Issues and PRs welcome. Please test on at least one rooted device before submitting firewall-related changes.

## License

GPL-3.0. See [LICENSE](LICENSE) for details.
