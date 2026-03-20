# HostShield

## Overview
Modern, AMOLED-dark hosts-based ad blocker app for Android. Inspired by AdAway. v3.9.0.

## Tech Stack
- Kotlin, Jetpack Compose, Material 3
- Hilt for dependency injection
- Room + DataStore for persistence
- Coroutines + Flow for async, ViewModels + StateFlow for UI state
- OkHttp for source downloads + DoH resolver, libsu for root access
- Gradle version catalog (`libs.versions.toml`) for dependency management

## Key Architecture
- **BlocklistHolder** - Trie-based O(m) domain lookup, 200K+ domains, volatile root for thread safety. Regex rules capped at 500 chars with nested quantifier rejection (ReDoS prevention).
- **DnsVpnService** - Local VPN DNS interception, TUN interface, dual-stack (IPv4+IPv6), DNS trap, DoH/DoT blocking, TCP DNS RST for both IPv4 and IPv6. Bounded log buffer (LinkedBlockingQueue 5000). Context-aware firewall checks (screen off/background/metered).
- **DohResolver** - RFC 8484 POST+GET, certificate pinning, smart latency-based failover (EMA per provider, auto-selects fastest), unpinned fallback as last resort.
- **RootDnsLogger** - Root-mode DNS proxy on 127.0.0.1:5454, iptables NAT redirect, UID attribution
- **IptablesManager** - AFWall+-style per-app firewall, 20+ interface patterns, BLACKLIST/WHITELIST modes
- **HostsUpdateWorker** - Periodic blocklist refresh + DoH bypass list update + allowlist subtraction
- **TrackerSignatureDb** - Exodus-style APK dex scanning for ~60 tracker SDK signatures (advertising, analytics, crash, social, location). Integrated into AppPrivacyScorer for A-F grades.
- **GeoIpLookup** - ip-api.com with in-memory cache, country flags + ISP + ASN for resolved IPs in DNS log detail sheet.
- **ContextState** - Screen on/off receiver + metered network detection + foreground app tracking. Drives context-aware firewall rules in DnsVpnService.
- **PrivateDnsDetector** - Detects Strict/Automatic Private DNS that bypasses VPN filtering. Integrated into onboarding flow.

## Source Categories
- ADS, TRACKERS, MALWARE, ADULT, SOCIAL, CRYPTO, **ALLOWLIST**, CUSTOM
- ALLOWLIST sources are subtracted from blocklist during updates (not added to block trie)

## Key Files
- `app/app/src/main/java/com/hostshield/service/DnsVpnService.kt` - VPN packet loop (~1400 lines)
- `app/app/src/main/java/com/hostshield/domain/BlocklistHolder.kt` - Trie + regex + wildcard engine
- `app/app/src/main/java/com/hostshield/service/DohResolver.kt` - DoH with smart latency failover
- `app/app/src/main/java/com/hostshield/service/DnsCache.kt` - LRU DNS cache with TTL
- `app/app/src/main/java/com/hostshield/service/DnsPacketBuilder.kt` - DNS wire format builder/parser
- `app/app/src/main/java/com/hostshield/util/TrackerSignatureDb.kt` - APK tracker SDK scanner
- `app/app/src/main/java/com/hostshield/util/GeoIpLookup.kt` - GeoIP/ASN lookup
- `app/app/src/main/java/com/hostshield/util/AppPrivacyScorer.kt` - Per-app A-F privacy grades
- `app/app/src/main/java/com/hostshield/service/ScreenStateReceiver.kt` - ContextState for firewall
- `app/app/src/main/java/com/hostshield/ui/screens/sources/BlocklistGalleryScreen.kt` - Curated gallery
- `app/app/src/main/java/com/hostshield/data/model/Entities.kt` - Room entities
- `app/app/src/main/java/com/hostshield/data/database/Migrations.kt` - DB migrations (v5→v6→v7→v8)
- `app/app/src/main/assets/curated_blocklists.json` - 70+ categorized blocklist definitions

## Screens (22+)
Home, Sources, Rules, Stats, Settings, Logs, Apps, AppPrivacy, Firewall (DNS/Network/Context tabs), ConnectionLog, DnsTools, NetworkStats, OverlapAnalysis, DnsLeakTest, RuleTest, HostsEditor, HostsDiff, AppExclusions, Onboarding (with Private DNS warning), BlocklistGallery

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
- v3.9.0: Private DNS conflict warning in onboarding, smart DNS latency-based failover, GeoIP/ASN/country in DNS log details, automation broadcast security fix, IPv6 TCP DNS support (RST blocking)
- v3.8.0: Curated blocklist gallery (70+ lists), Exodus tracker SDK detection in App Privacy Report, context-aware firewall (screen off/background/metered), regex DoS protection, bounded log buffer, LRU cache fix, DNS answer cache cleanup
- v3.7.0: App privacy report, rule sync URLs, blocked domain trends, per-app scoring, category toggles on Home
- v3.6.0: Live query rate, category toggles, hosts editor, Pi-hole import, deep links, notification actions
- v3.5.0: Rule tester, temporary allow, domain age check, stats widget, privacy score card, search history
- v3.4.0: Privacy score, scheduled blocking, query type filter, suspicious TLD detection, batch source health check
- v3.3.0: DNS leak test, clipboard import, accent color picker, auto backup, IP blocking, domain pinning
- v3.2.0: App shortcuts, enhanced widget, bulk log actions, DNS latency chart, network-aware profiles, regex rules, domain reputation lookup, source changelog tracking
- v3.1.0: DoH bypass scheduling, multi-upstream DNS, auto update check, allowlist sources, overlap analysis, CSV stats export
- v3.0.0: DNS cache, CNAME cloaking, 7-day trend charts, diagnostic export, CI/CD
- v2.1.0: Automation API, iptables firewall, connection logging
- v2.0.0: DoH, DNS trap, batch domain test, network stats

## Gotchas
- Room stores enums as strings — adding new enum values (like ALLOWLIST) doesn't need migration
- HostsUpdateWorker has DohBypassUpdater injected — runs on every periodic cycle regardless of blocking state
- OverlapAnalysisScreen downloads sources with `forceDownload=true` to bypass ETag caching
- Regex rules limited to 500 chars with nested quantifier rejection (ReDoS prevention)
- Log buffer is LinkedBlockingQueue(5000) not unbounded — will drop entries under extreme load
- DB version is 8 (v7→v8 adds context-aware firewall columns: block_screen_off, block_background, block_metered)
- TrackerSignatureDb reads raw dex bytes from APK — scans can be slow on devices with 100+ apps
- ContextState.register() must be called in startVpn(), unregister() in stopVpn()
- Context-aware firewall requires Usage Stats permission for foreground app detection
- GeoIpLookup uses ip-api.com (free tier, 45 req/min) — has in-memory cache to reduce calls
- DohResolver smart DNS prefers fastest measured provider but falls back through all on failure
- Automation STATUS_RESULT broadcast is permission-protected (signature-level) — only trusted callers receive it
- IPv6 TCP RST uses separate checksum function (computeTcpChecksumV6) with IPv6 pseudo-header
- Private DNS warning only shows in onboarding when VPN mode selected AND Private DNS is active
- `gradlew` lives in `app/` not repo root — CI workflow uses `working-directory: app`
- Release keystore is gitignored (*.keystore in .gitignore)
