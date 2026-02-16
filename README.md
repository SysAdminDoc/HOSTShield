# HostShield

![Version](https://img.shields.io/badge/version-1.0.0-blue)
![License](https://img.shields.io/badge/license-GPLv3-green)
![Platform](https://img.shields.io/badge/platform-Android%208.0+-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)
![Status](https://img.shields.io/badge/status-active-success)

> A modern, AMOLED-dark hosts-based ad blocker for rooted Android with VPN fallback, DNS logging, and a premium UI.

<!-- ![Screenshot](screenshot.png) -->

## Quick Start

```bash
git clone https://github.com/SysAdminDoc/HostShield.git
```

1. Open in **Android Studio** (Ladybug or newer)
2. Let Gradle sync
3. Connect a rooted Android device via USB (Developer Options → USB Debugging)
4. Hit **Run** — deploys directly to device

## Features

| Feature | Description | Status |
|---------|-------------|--------|
| Root Hosts Blocking | Writes merged blocklist to `/system/etc/hosts` | ✅ |
| VPN DNS Blocking | Local VPN tunnel for non-root devices | ✅ |
| Magisk Systemless | Auto-detects and uses systemless hosts overlay | ✅ |
| DNS Query Logging | Real-time log with blocked/allowed status | ✅ |
| DNS over HTTPS | Cloudflare, Google, Quad9 provider support | ✅ |
| 8 Built-in Sources | StevenBlack, OISD, AdAway, GoodbyeAds, more | ✅ |
| Custom Sources | Add any hosts-format URL | ✅ |
| User Rules | Per-domain block, allow, and redirect rules | ✅ |
| Auto-Updates | WorkManager scheduled source updates | ✅ |
| App Exclusions | Bypass VPN blocking per-app | ✅ |
| Import/Export | JSON and hosts-format import/export | ✅ |
| Full Backup/Restore | SAF file picker, all config included | ✅ |
| Hosts File Viewer | Syntax-highlighted viewer with line numbers | ✅ |
| Homescreen Widget | Quick toggle with block count | ✅ |
| Boot Persistence | Auto-restarts VPN/reschedules updates on boot | ✅ |
| AMOLED Dark Theme | Catppuccin Mocha palette, glassmorphism | ✅ |
| Category Tagging | Sources tagged: Ads, Trackers, Malware, Adult | ✅ |
| Wildcard Blocking | Pattern rules like *.ads.* or *tracking* | ✅ |
| Statistics Dashboard | Hourly charts, top domains, per-app stats | ✅ |
| Source Health Monitor | Detects stale, errored, or dead sources | ✅ |
| Onboarding Wizard | First-launch setup with mode selection | ✅ |
| Log Cleanup | Automatic purge based on retention setting | ✅ |
| Block/Whitelist from Logs | Tap any log entry to add rules inline | ✅ |
| Log Filtering | Filter by blocked/allowed, search by domain | ✅ |
| Detailed Log View | Expand entries for query type, timestamp, actions | ✅ |
| Settings Dialogs | Edit IP redirect, interval, retention in-app | ✅ |
| Notification Permission | Android 13+ POST_NOTIFICATIONS flow | ✅ |
| Android 14/15 Compat | foregroundServiceType, ServiceCompat, edge-to-edge | ✅ |

## Built-in Hosts Sources

| Source | Entries | Category |
|--------|---------|----------|
| StevenBlack Unified | ~79,000 | Ads |
| AdAway Default | ~400 | Ads |
| Peter Lowe's List | ~3,000 | Ads |
| OISD Small | ~70,000 | Ads |
| OISD Big | ~200,000+ | Ads |
| GoodbyeAds | Aggressive | Ads |
| StevenBlack Extended | Fakenews/Gambling/Porn | Adult |
| URLHaus Malware Filter | Variable | Malware |

## How It Works

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Hosts Sources   │────>│   Parser &       │────>│  /system/etc/    │
│  (Remote URLs)   │     │   Merger         │     │  hosts           │
│  StevenBlack,    │     │                  │     │  (Root mode)     │
│  OISD, AdAway    │     │  + User Rules    │     │                  │
└─────────────────┘     │  (allow/block/   │     └─────────────────┘
                        │   redirect)      │
                        └────────┬─────────┘     ┌─────────────────┐
                                 │              │  Local VPN       │
                                 └─────────────>│  DNS Filter      │
                                                │  (Non-root mode) │
                                                └─────────────────┘
```

**Root Mode:** Downloads and parses all enabled hosts sources, deduplicates, applies user allow/block/redirect rules, then writes the merged file to `/system/etc/hosts` (or Magisk systemless overlay). DNS cache is flushed automatically.

**VPN Mode:** Runs a local VPN service that intercepts DNS queries. Blocked domains receive NXDOMAIN responses. Supports DNS-over-HTTPS for encrypted resolution. Logs all queries with app attribution.

## Architecture

- **Language:** Kotlin 2.1
- **UI:** Jetpack Compose + Material 3
- **Database:** Room with Flow-based reactive queries
- **DI:** Hilt
- **Networking:** OkHttp with ETag caching
- **Root:** libsu (supports Magisk, KernelSU, SuperSU)
- **Background:** WorkManager for scheduled updates
- **Preferences:** DataStore

## Configuration

All settings are accessible from the Settings screen:

- **IPv4/IPv6 redirect IPs** — defaults to `0.0.0.0` / `::`
- **Auto-update interval** — 1-168 hours
- **WiFi-only updates** — skip on metered connections
- **DNS logging** — enable/disable query recording
- **Log retention** — 1-30 days
- **DNS over HTTPS** — Cloudflare, Google, or Quad9
- **Persistent notification** — show block count in status bar
- **App exclusions** — exempt specific apps from VPN filtering

## Permissions

| Permission | Reason |
|------------|--------|
| `INTERNET` | Download hosts sources, forward DNS queries |
| `ACCESS_NETWORK_STATE` | WiFi-only update constraint |
| `RECEIVE_BOOT_COMPLETED` | Restart VPN / reschedule updates on boot |
| `FOREGROUND_SERVICE` | VPN service persistence |
| `POST_NOTIFICATIONS` | Block count notification |
| `QUERY_ALL_PACKAGES` | App exclusion list with labels |

## FAQ

**Q: Does it work without root?**
Yes — VPN mode provides DNS-level blocking without root. Root mode is more efficient and system-wide.

**Q: Will it break apps?**
Use the allowlist to whitelist domains that break specific apps, or exclude entire apps from VPN filtering.

**Q: How is this different from AdAway?**
HostShield adds a modern Compose UI, DNS logging with app attribution, DoH support, blocking profiles, import/export, homescreen widget, and a premium AMOLED dark theme.

**Q: Where is the hosts file written?**
Root mode: `/system/etc/hosts` (or `/data/adb/modules/hosts/system/etc/hosts` for Magisk systemless). VPN mode: in-memory only.

## License

[GPLv3](LICENSE) — Free and open source.

## Contributing

Issues and PRs welcome. Please test on a real rooted device before submitting.
