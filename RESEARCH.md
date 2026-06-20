# Research - HostShield

Last refreshed: 2026-06-19
Baseline: v6.9.47, versionCode 129, Kotlin 2.3, AGP 9.2, compileSdk 36, targetSdk 36

## Executive Summary

HostShield is a mature GPL-3.0 Android DNS firewall with VPN/root/proxy modes, pinned DoH/DoT, local blocklists with trie + hash set + regex engine, threat-intel feeds, per-app iptables firewall, CNAME/SVCB cloaking detection, 31+ Compose screens, encrypted backup, PCAP export, widgets, automation API, and comprehensive release provenance (SBOM, OSV, attestations). At 50K LOC across 226 Kotlin files with 382 test methods, the codebase is well-hardened for a single-maintainer project.

The highest-value direction is not more feature breadth — it is **platform modernization, testing depth, and UX polish** that builds trust and distribution readiness. The project already leads its OSS peers in DNS correctness (serve-stale, EDE, NXNAME, CNAME cloaking); the gap is in Android platform adoption (predictive back, dynamic color, locale config, API 37 readiness) and distribution maturity (F-Droid reproducibility, instrumented CI tests).

Top opportunities, priority ordered:
1. **Predictive back gesture support** — Android 16 enforces this; HostShield has no `BackHandler` or predictive back handling across 31+ screens.
2. **Dynamic color / Material You** — every major competitor supports it; HostShield is hardcoded AMOLED palette only.
3. **Android per-app language preferences** — `generateLocaleConfig` not enabled; 243 string resources exist but no real locale translations.
4. **ViewModel-per-screen extraction** — 7 screens still embed ViewModels in the Composable file (1,000+ LOC screens).
5. **Bloom filter pre-check** for BlocklistHolder — RethinkDNS's succinct radix trie handles 13.5M domains; HostShield's hash set covers exact matches but has no probabilistic pre-filter for misses.
6. **DNS query deduplication** — concurrent identical queries all hit upstream; RethinkDNS and Pi-hole v6 coalesce in-flight queries.
7. **Structured DNS Errors display** — EDE codes are emitted but not surfaced in the query detail UI.
8. **Split/conditional DNS routing** — domain-based resolver selection (e.g., internal domains to corporate DNS) is a top community request.
9. **Compose test coverage for ViewModel state** — Turbine/Flow testing is absent; ViewModels are large and largely untested.
10. **F-Droid/IzzyOnDroid reproducible build readiness** — metadata is stale; reproducibility approach undecided.

## Product Map

- Core workflows: enable protection (VPN/root/proxy) → manage sources → configure encrypted DNS → review logs/stats → add domain/app rules → pause or allow false positives → export backups/diagnostics/PCAP → sync via WebDAV → automate via broadcast intents.
- User personas: privacy-focused Android users, rooted power users (Magisk/KernelSU/APatch), parents using content controls, local-first network admins, and maintainers shipping signed APKs.
- Platforms: Android 8+ (minSdk 26), GitHub/Obtainium/F-Droid distribution, full + play flavors.
- Key integrations: blocklist sources → SourceDownloader → BlocklistHolder trie; DNS packets → DnsVpnService/RootDnsService/DnsProxyService → DoH/DoT; threat feeds → ThreatIntelManager radix trie; Room DB (15 migrations) for logs/rules/sources; WorkManager for periodic refresh/backup/cleanup.

## Competitive Landscape

### RethinkDNS
Does well: Go firestack for per-app connection-level firewall (not just DNS), split DNS, WireGuard routing, app-level traffic stats, DNS query deduplication, compressed radix trie for 13.5M domains. Learn from: query deduplication pattern, per-app bandwidth stats, split DNS routing. Avoid: Go/gomobile build complexity, full-traffic VPN battery cost.

### AdGuard for Android
Does well: mature adblock syntax engine with `$app=`/`$client=` scope modifiers, HTTPS filtering, DNS rewrites with full modifier support, stealth mode, Material You dynamic color. Learn from: modifier-scoped rules, dynamic color theming, per-app DNS statistics. Avoid: closed-source filtering core, cloud-account dependency.

### PCAPdroid
Does well: PCAPng export with app UID annotations, SNI/DNS/HTTP extraction, remote PCAP streaming, clear privacy boundaries, malware detection with per-blacklist status. Learn from: PCAPng metadata model, export privacy controls. Avoid: MITM as default surface.

### Pi-hole v6
Does well: session-based queries, per-client/group policies, API-first architecture, DNS query deduplication (in-flight coalescing), FTLDNS + embedded SQLite for query log. Learn from: query deduplication to reduce upstream load, client group policies. Avoid: server-centric architecture assumptions.

### TrackerControl / DuckDuckGo App Tracking Protection
Does well: tracker company attribution (shows "Google tracked you 847 times" not just blocked domains), DuckDuckGo Tracker Radar dataset. Learn from: company-level tracking narrative that users understand. Avoid: VPN-only enforcement ambiguity.

### NextDNS / Control D
Does well: analytics dashboards (top blocked domains, per-device stats, block rate trends), NRD blocking, homograph/DGA detection, profile-based filtering. Learn from: analytics drill-down patterns — these are paywalled at $2-7/mo, so offering them locally is a strong differentiator. Avoid: cloud-required model.

### Blokada 6
Does well: network-aware profile switching by Wi-Fi SSID, activity log with search, cloud relay option. Learn from: SSID-based profiles are a common user request. Avoid: freemium model confusion.

## Security, Privacy, and Reliability

### Verified Strengths
- DoH/DoT is fail-closed at both resolver and VPN service layers. No plaintext fallback path.
- Certificate pinning with primary/backup SPKI pins per provider, with review/expiry dates and diagnostics.
- PIN hashing uses PBKDF2 (210K iterations) with forced legacy SHA-256 upgrade gate.
- Encrypted backups use AES-256-GCM with nonce ledger preventing reuse.
- All sync URLs are HTTPS-only with 10MB bounds and SHA-256 integrity.
- Shell injection prevention in root mode (quoted paths, Kotlin-side filtering).
- Release pipeline: SBOM generation, OSV scanning with allowlist, GitHub artifact attestations, page alignment verification, provenance checksums.
- VPN route canonicalization prevents Android 11+ route validation failures.
- Regex rules capped at 500 chars with per-rule execution deadlines and ReDoS prevention.

### Gaps and Risks
- **`systemExempted` FGS type**: lint still baselines `ForegroundServicePermission`. Android 15+ behavior for long-running `systemExempted` services needs connected device validation (already on blocked roadmap).
- **No Certificate Transparency readiness**: Android 17 enforces CT for all connections; DoH/DoT providers need CT-logged certificates verified. Already on blocked roadmap.
- **WebDAV password stored in DataStore**: backup v2 serializes `webdav_url` and `webdav_username` (not password), but the credential storage approach in DataStore should be audited for at-rest encryption.
- **GeoIP lookup to ipapi.co**: rate-limited and bounded, but the external call leaks resolved IP addresses to a third-party service. Already documented as opt-in.
- **network_security_config.xml lacks pin-sets**: the file exists (disables cleartext, whitelists captive portal) but has no `<pin-set>` entries for DoH/DoT providers. Adding declarative pin-sets alongside OkHttp pinning provides defense-in-depth and enables Android 17 ECH opt-in via `<domainEncryption>`.
- **24 lint baseline entries remaining**: includes `ForegroundServicePermission`, likely `NewApi`, and dependency-related warnings. Each should be resolved or have documented justification.

## Architecture Assessment

### Critical Refactor Candidates
- **DnsVpnService.kt (2,564 LOC)**: God-class containing packet loop, DNS forwarding, logging, blocklist management, VPN recovery, notification management, watchdog, and network monitoring. Blocked on connected device verification but remains the highest maintainability risk. Extract: DnsQueryProcessor, DnsForwarder, DnsLogManager, VpnRecoveryMonitor, VpnNotificationController.
- **StatsScreen.kt (1,314 LOC)**, **LogsScreen.kt (1,282 LOC)**, **SettingsScreen.kt (1,218 LOC)**, **SourcesScreen.kt (1,166 LOC)**: screens that embed ViewModel, state management, and UI composition in single files. Extract ViewModels to separate files for testability.
- **HomeViewModel.kt (976 LOC)**: largest ViewModel, handles blocklist builds, DNS config, stats aggregation, and UI state. Candidate for splitting into smaller focused ViewModels.

### Module Boundaries
- Service layer has 56 files — well-factored for most concerns but DnsVpnService is the bottleneck.
- UI layer has clean screen-per-file structure but ViewModels are co-located with Composables in 7+ screens.
- No multi-module structure — single `:app` module. For a project of this size, this is acceptable but limits parallel compilation and test isolation.

### Test Gaps
- 48 unit test files with 382 test methods — good coverage of parsers, crypto, policy, and DNS wire format.
- Only 5 androidTest files — minimal instrumented coverage.
- No ViewModel Flow testing with Turbine or similar.
- No Compose semantics/accessibility assertions.
- No Robolectric tests (would cover Android-dependent code without a device).

## Rejected Ideas

- **Full HTTPS/TLS MITM inspection**: contradicts local-first DNS-firewall posture, breaks pinned apps. Source: PCAPdroid, AdGuard approach analysis.
- **Cloud account dashboard**: mandatory remote logs undercut privacy promise. Source: NextDNS/Control D model.
- **Arbitrary plugin ecosystem**: risky on Android; broadcast intents are the right boundary. Source: competitive analysis.
- **Default NRD mega-feeds**: very large and false-positive-prone per HaGeZi warnings. Keep as opt-in.
- **Bundled general-purpose VPN**: should coexist with VPNs, not become one. Source: architecture decision record.
- **Re-adding offline GeoIP**: removed for good reason (stale dependency); reintroduce only with licensed update path.
- **Succinct radix trie (RethinkDNS-style)**: RethinkDNS compresses 13.5M domains into ~40MB; HostShield targets 200K-1M domains where hash set + standard trie is sufficient. Complexity not justified at current scale.
- **gomobile TUN layer**: build complexity and maintenance burden outweigh throughput gains at DNS-only scale. Source: RethinkDNS firestack analysis.
- **Material 3 Expressive adoption now**: requires compileSdk 37+ and visual verification. Source: Android developer docs.

## Sources

### Project and OSS competitors
- https://github.com/celzero/rethink-app
- https://github.com/AdAway/AdAway
- https://github.com/M66B/NetGuard
- https://github.com/TrackerControl/tracker-control-android
- https://github.com/emanuele-f/PCAPdroid
- https://github.com/nickolasburr/InviZible
- https://github.com/blokadaorg/blokada
- https://github.com/julian-klode/dns66
- https://github.com/IngoZenz/personaldnsfilter
- https://github.com/Ch4t4r/Nebulo

### Commercial and adjacent products
- https://adguard.com/kb/adguard-for-android/features/
- https://nextdns.io/
- https://controld.com/features
- https://pi-hole.net/blog/2024/11/15/introducing-pi-hole-v6/
- https://developers.cloudflare.com/cloudflare-one/traffic-policies/

### Standards and platform
- https://datatracker.ietf.org/doc/rfc9461/ (DDR)
- https://datatracker.ietf.org/doc/rfc9462/ (DNR)
- https://datatracker.ietf.org/doc/rfc9230/ (ODoH)
- https://datatracker.ietf.org/doc/rfc8767/ (Serve-Stale)
- https://datatracker.ietf.org/doc/rfc9824/ (NXNAME)
- https://developer.android.com/about/versions/16/behavior-changes-all
- https://developer.android.com/develop/ui/compose/navigation/predictive-back-gesture
- https://developer.android.com/develop/ui/views/theming/dynamic-colors
- https://developer.android.com/guide/topics/resources/app-languages
- https://developer.android.com/privacy-and-security/local-network-permission

### Dependency and security references
- https://square.github.io/okhttp/changelogs/changelog/
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/jetpack/androidx/releases/lifecycle
- https://github.com/AdguardTeam/HostlistsRegistry
- https://github.com/hagezi/dns-blocklists
- https://github.com/StevenBlack/hosts

### Community signal
- https://discuss.privacyguides.net/t/which-app-block-allow-in-firewall/30603
- https://www.reddit.com/r/androidapps/comments/dns_blocker_comparison
- https://forum.f-droid.org/t/rethink-dns-yes-or-no/29561
- https://github.com/TrackerControl/tracker-control-android/issues

## Open Questions

- Should HostShield add `network_security_config.xml` for defense-in-depth pinning alongside OkHttp-level pins?
- Should dynamic color be opt-in alongside the existing AMOLED palette, or replace it as default on Android 12+?
- Is Play Store distribution a near-term goal, or should full APK/F-Droid/Obtainium remain the primary lane?
- Should WebDAV credentials receive Android Keystore-backed encryption rather than DataStore storage?
