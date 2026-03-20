# HostShield

## Overview
Modern, AMOLED-dark hosts-based ad blocker app for Android. Inspired by AdAway. v3.4.0.

## Tech Stack
- Kotlin, Jetpack Compose, Material 3
- Hilt for dependency injection
- Room + DataStore for persistence
- Coroutines + Flow for async, ViewModels + StateFlow for UI state
- OkHttp for source downloads, libsu for root access
- Gradle version catalog (`libs.versions.toml`) for dependency management

## Key Architecture
- **BlocklistHolder** - Trie-based O(m) domain lookup, 200K+ domains, volatile root for thread safety
- **DnsVpnService** - Local VPN DNS interception, TUN interface, dual-stack, DNS trap, DoH/DoT blocking
- **RootDnsLogger** - Root-mode DNS proxy on 127.0.0.1:5454, iptables NAT redirect, UID attribution
- **IptablesManager** - AFWall+-style per-app firewall, 20+ interface patterns, BLACKLIST/WHITELIST modes
- **HostsUpdateWorker** - Periodic blocklist refresh + DoH bypass list update + allowlist subtraction

## Source Categories
- ADS, TRACKERS, MALWARE, ADULT, SOCIAL, CRYPTO, **ALLOWLIST**, CUSTOM
- ALLOWLIST sources are subtracted from blocklist during updates (not added to block trie)

## Android / Kotlin Conventions
- AMOLED black backgrounds default
- Material 3 dynamic theming
- Root mode and VPN mode dual support
- CNAME cloaking detection

## Build
```bash
./gradlew assembleFullDebug     # Full flavor (root features)
./gradlew assemblePlayDebug     # Play Store flavor
./gradlew testFullDebugUnitTest # Run unit tests
```

## Version History
- v3.4.0: Privacy score, scheduled blocking, query type filter, suspicious TLD detection, batch source health check
- v3.3.0: DNS leak test, clipboard import, accent color picker, auto backup, IP blocking, domain pinning
- v3.2.0: App shortcuts, enhanced widget, bulk log actions, DNS latency chart, network-aware profiles, regex rules, domain reputation lookup, source changelog tracking
- v3.1.0: DoH bypass scheduling, multi-upstream DNS, auto update check, allowlist sources, overlap analysis, CSV stats export
- v3.0.0: DNS cache, CNAME cloaking, 7-day trend charts, diagnostic export, CI/CD
- v2.1.0: Automation API, iptables firewall, connection logging
- v2.0.0: DoH, DNS trap, batch domain test, network stats

## CI/CD
- `.github/workflows/build.yml` auto-compiles APKs on release
- `ubuntu-latest` runner, `./gradlew assembleRelease`
- `workflow_dispatch` with optional tag input
- Sign release APKs via repo secrets (`KEYSTORE_BASE64`, `KEY_ALIAS`, `KEY_PASSWORD`, `STORE_PASSWORD`)

## Gotchas
- Room stores enums as strings - adding new enum values (like ALLOWLIST) doesn't need migration
- HostsUpdateWorker has DohBypassUpdater injected - runs on every periodic cycle regardless of blocking state
- OverlapAnalysisScreen downloads sources with `forceDownload=true` to bypass ETag caching
