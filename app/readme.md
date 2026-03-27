# HostShield

![Version](https://img.shields.io/badge/version-6.2.0-blue)
![License](https://img.shields.io/badge/license-GPL--3.0-green)
![Platform](https://img.shields.io/badge/platform-Android%208+-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Status](https://img.shields.io/badge/status-active-success)

> System-wide DNS-based ad/tracker/malware blocker for Android with per-app firewall, CNAME cloaking detection, serve-stale DNS caching, encrypted DNS (DoH/DoT/DoQ/WireGuard), offline GeoIP, content filtering, parental controls, and a professional AMOLED dark UI.

See the [root README](../README.md) for full feature documentation, architecture diagrams, and FAQ.

## Build

```bash
# Prerequisites: JDK 17, Android SDK 35

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
│       ├── HostsParser.kt    # Hosts file parser with wildcard support
│       └── AdblockRuleParser.kt # Adblock-syntax parser (||, @@, $important)
├── service/
│   ├── DnsVpnService.kt      # VPN packet loop (~2700 lines)
│   ├── DnsCache.kt           # LRU + serve-stale + prefetch + negative/failure cache
│   ├── DnsPacketBuilder.kt   # DNS wire format builder/parser
│   ├── DohResolver.kt        # DoH with smart latency failover
│   ├── DotResolver.kt        # DoT (RFC 7858, TLSv1.3, 4 providers)
│   ├── DoqResolver.kt        # DoQ (RFC 9250, QUIC Initial, 3 providers)
│   ├── WireGuardProxy.kt     # DNS-over-WireGuard (Noise_IKpsk2, AES-256-GCM)
│   ├── CnameCloakDetector.kt # CNAME + SVCB/HTTPS cloak detection
│   ├── LocalDnsServer.kt     # LAN DNS server on port 5353
│   ├── DnsProxyService.kt    # No-VPN proxy mode DNS blocking
│   ├── ContentFilterManager.kt # 15 content filter categories
│   ├── ParentalControlManager.kt # Age-profile parental controls + PIN
│   ├── AppDnsRuleEngine.kt   # Per-app domain DNS rules
│   ├── ConnectionTracker.kt  # Real-time per-app connection tracking
│   ├── ThreatIntelManager.kt # Threat intel feeds + radix trie IP lookup
│   ├── RootDnsService.kt     # Root-mode DNS proxy
│   ├── IptablesManager.kt    # Per-app firewall rule management
│   ├── AutomationReceiver.kt # Broadcast intent API
│   └── *Worker.kt            # HostsUpdate, AutoBackup, LogCleanup, etc.
├── ui/
│   ├── navigation/    # Compose navigation graph
│   ├── screens/       # 31+ screens (Home, Logs, Stats, Settings, Firewall, ...)
│   ├── components/    # Vico charts, Lottie animations, animated log feed
│   ├── widget/        # Glance widgets (toggle + stats)
│   └── theme/         # Material 3 theme + accent colors
└── util/
    ├── OfflineGeoIp.kt        # MaxMind GeoLite2 offline lookups
    ├── TlsFingerprinter.kt    # JA3/JA4 TLS ClientHello fingerprinting
    ├── TrackerSignatureDb.kt   # Exodus-style APK tracker scanner
    ├── EncryptedBackup.kt      # AES-256-GCM encrypted backups
    ├── WebDavSync.kt           # WebDAV cloud sync
    ├── QrConfigSharing.kt      # QR code config sharing (GZIP+Base64)
    ├── CrashReporter.kt        # Custom crash reporting
    ├── DnsBenchmark.kt         # DNS resolver latency benchmark
    ├── ImportExportUtil.kt     # Multi-format import/export
    ├── DiagnosticExporter.kt   # One-tap diagnostic report
    └── RootUtil.kt             # Root detection + binary management
```

## Key Features

- **3 Blocking Modes**: VPN (no root), Root (iptables), Proxy (no-VPN no-root)
- **DNS Blocking**: Trie + hash set + regex + wildcard, 200K+ domains, filter decision cache
- **Encrypted DNS**: DoH (RFC 8484), DoT (RFC 7858), DoQ (RFC 9250), WireGuard — with smart latency failover
- **DNS Cache**: 2000-entry LRU, serve-stale (RFC 8767), negative caching (RFC 2308), SERVFAIL caching (RFC 9520), prefetching
- **CNAME Cloaking Detection**: Full CNAME chain + SVCB/HTTPS inspection with dedicated AdGuard/NextDNS databases
- **Per-App Firewall**: iptables Wi-Fi/mobile/VPN per-app rules (root)
- **Content Filtering**: 15 toggleable categories + parental controls with PIN lock
- **Privacy Analysis**: Tracker SDK scanning (405 signatures), A-F privacy grades, threat intel feeds
- **Offline GeoIP**: MaxMind GeoLite2 Country + ASN — unlimited, zero-latency
- **31+ Screens**: Modern Material 3 Compose UI with Vico charts, Lottie animations, Glance widgets
- **Automation**: Tasker/MacroDroid broadcast API, scheduled blocking, network-aware profiles

## License

[GPL-3.0](../LICENSE)
