# Research - HostShield

Last refreshed: 2026-06-28
Baseline: v6.9.53, versionCode 135, Kotlin 2.3.21, AGP 9.2.1, compileSdk 36, targetSdk 36

## Executive Summary

HostShield is a mature GPL-3.0 Android DNS firewall and blocker with VPN/root/proxy modes, pinned DoH/DoT, blocklist trie + exact-set + regex decisions, CNAME/SVCB cloaking detection, serve-stale DNS caching, in-flight DNS query coalescing, local threat-intel analytics, encrypted backup, PCAP/diagnostic export flows, automation intents, and a polished dark-first Compose UI. Recent work closed many prior research gaps: dynamic color, predictive back manifest readiness, locale config generation, source metadata, per-app stats, ViewModel test seams, and DNS coalescing are present. The highest-value direction is now trust repair around claimed-but-unwired features, cold-start/update reliability, and policy correctness for modern adblock syntax and Android local-network behavior.

Top opportunities in priority order:
- Fix periodic blocklist rebuild semantics and source metadata persistence.
- Wire or de-advertise the Local DNS Server feature, including Android local-network permission readiness.
- Hide or hard-disable release-build DoQ/WireGuard DNS toggles until their engines are production-effective.
- Preserve AdGuard `$dnstype=` rules as query-type-aware decisions instead of parsing then dropping them.
- Expand tracker attribution from the small local owner map toward a generated Tracker Radar-style dataset.
- Add an Android 16 large-screen/adaptive-layout gate before tablet, foldable, Chromebook, or API 37 distribution claims.
- Drain the remaining lint baseline as an API 37/toolchain compatibility batch.

## Product Map

- Core workflows: enable protection through VPN/root/proxy, manage block/allow sources, configure encrypted DNS, inspect DNS/firewall logs, recover false positives, export diagnostics/backups/PCAP, sync via WebDAV, automate with signed broadcast intents.
- User personas: privacy-focused Android users, rooted power users, local-first network admins, parents using content filters, and maintainers publishing signed APK/AAB artifacts.
- Platforms and distribution: Android 8+ minSdk 26, full flavor for GitHub/F-Droid/Obtainium, play flavor without `QUERY_ALL_PACKAGES`, local signed release builds only.
- Key integrations and data flows: source URLs -> `SourceDownloader`/`HostsParser` -> `BlocklistHolder`; DNS packets -> `DnsVpnService`/`DnsProxyService`/`RootDnsLogger` -> DoH/DoT/plain UDP; threat feeds -> `ThreatIntelManager`; Room stores logs/rules/sources/profiles; DataStore and secure prefs hold configuration/secrets.

## Competitive Landscape

### RethinkDNS
Does well: split DNS, per-app firewall, WireGuard routing, app traffic stats, and in-flight DNS query coalescing. Learn from: resolver-routing UX and connection-level policy visibility. Avoid: gomobile/firestack complexity for HostShield's DNS-first architecture.

### AdGuard for Android
Does well: broad AdGuard syntax support, `$app=`, `$client=`, `$dnstype=`, `$denyallow=`, DNS rewrites, and polished Android filtering controls. Learn from: scoped and query-type-aware rule semantics. Avoid: closed-source filtering-core dependency and HTTPS MITM as a default surface.

### PCAPdroid
Does well: app-attributed packet capture, PCAPng diagnostics, remote dump modes, and clear malware-list status UX. Learn from: diagnostics that preserve evidence while making privacy disclosure explicit. Avoid: making MITM/TLS-decryption workflows part of the normal DNS blocker path.

### Pi-hole v6
Does well: per-client/group policies, query database, API-first management, and DNS-layer operational maturity. Learn from: durable source/cache state and policy observability. Avoid: server-centric assumptions that do not fit an Android local-first app.

### TrackerControl / DuckDuckGo Tracker Radar
Does well: tracker company attribution that users understand at a glance. Learn from: generated tracker-owner data rather than a small hand-maintained map. Avoid: any remote telemetry or cloud scoring loop.

### NextDNS / Control D
Does well: profile-based filtering, analytics, parental controls, NRD/security categories, and approachable dashboards. Learn from: local analytics and policy profile presentation. Avoid: hosted-account and remote-log requirements.

### AdAway
Does well: simple rooted hosts blocking and systemless/root ecosystem compatibility. Learn from: clarity of root-mode expectations. Avoid: limiting HostShield to hosts-only behavior when VPN/root/proxy DNS engines already provide richer policy.

## Security, Privacy, and Reliability

- Verified: DoH/DoT fail closed in resolver and VPN paths; DoT now fails closed when pins are missing; DoH/DoT pin sets exist in `app/app/src/main/res/xml/network_security_config.xml`; source URLs are HTTPS-only; imports and backups are bounded; release signing fails closed unless real signing or explicit local debug signing is configured.
- Verified risk: `app/app/src/main/java/com/hostshield/service/HostsUpdateWorker.kt` calls `downloader.download(source)` without `forceDownload = true`, unlike `DnsVpnService.kt`, `HomeViewModel.kt`, and `SourcesViewModel.kt`. On `304 Not Modified`, it skips parsing content; it also does not persist block-source `entryCount`, `lastUpdated`, `etag`, `lastModifiedOnline`, or `sizeBytes` for changed block sources. This can leave periodic health/metadata stale and makes cold or empty in-memory rebuilds dependent on another path.
- Verified risk: `LocalDnsServer.kt` is advertised in README as "Portable Pi-hole" mode, but `git grep LocalDnsServer` finds no production start/stop call site or Settings surface. Android's local-network permission model also needs a user-facing permission/readiness path before LAN DNS is made first-class.
- Verified risk: `DnsSettingsSection.kt` exposes DoQ and WireGuard DNS toggles, while `DnsVpnService.kt` forces `useDoQ` and `useWireGuard` to false outside `BuildConfig.DEBUG`. Release users can enable preferences that the production resolver ignores.
- Verified risk: `AdblockRuleParser.kt` parses `$dnstype=`, but `HostsParser.parseForBlocking()` drops any rule with `dnsTypes != null`; `DnsVpnService` already has qtype context. This silently loses AdGuard DNS rules that competitors honor.
- Verified risk: HostShield targets API 36 and ships 43 Compose screen files, but `rg "WindowSizeClass|NavigationRail|NavigationSuiteScaffold|ListDetailPaneScaffold|sw600"` finds no adaptive large-screen layout hooks. Android 16 ignores orientation, resizability, and aspect-ratio restrictions on displays at or above 600dp, so dense Settings/Logs/Stats/Sources flows need explicit tablet/foldable verification before API 37.
- Likely risk: `lint-baseline.xml` still suppresses `ForegroundServicePermission`, `BatteryLife`, compileSdk 37, and dependency freshness entries. Some are justified, but the baseline mixes policy-sensitive issues with mechanical KTX/dependency items.

## Architecture Assessment

- `DnsVpnService.kt` remains the largest risk boundary: it owns packet loop, resolver routing, cache behavior, blocklist rebuild, logging, and VPN recovery. Prior extractions helped, but further changes should keep tests around resolver routing and fail-closed behavior.
- `HostsUpdateWorker.kt`, `ProfileScheduleWorker.kt`, `DnsVpnService.kt`, `HomeViewModel.kt`, and `SourcesViewModel.kt` duplicate source merge/build logic. A shared blocklist rebuild coordinator would reduce divergence like the current `forceDownload` mismatch.
- `LocalDnsServer.kt` is isolated and unit-tested at policy level, but not integrated into app state, notification lifecycle, permissions, or UI.
- `AdblockRuleParser.kt` has better syntax awareness than `BlocklistHolder` can currently consume. A typed rule model keyed by domain + qtype + app/client scope would prevent parse/drop drift.
- Tests are strong for parsers, crypto, DNS wire format, and several ViewModels. Missing coverage: periodic source 304 rebuild behavior, block-source metadata persistence, release-build experimental toggle behavior, Local DNS Server lifecycle/permission flow, and query-type-specific rule decisions.
- UI architecture is phone-first: the main navigation and dense detail surfaces are regular Compose screens without Material 3 adaptive panes or a large-screen test matrix. Start with an adaptive-ready gate before adding navigation-suite dependencies.

## Rejected Ideas

- Full HTTPS/TLS MITM inspection: contradicts HostShield's DNS-firewall identity and risks breaking pinned apps. Source: AdGuard/PCAPdroid comparison.
- Hosted cloud dashboard or account sync: conflicts with local-first/no-telemetry principles. Source: NextDNS/Control D comparison.
- Arbitrary plugin ecosystem: too much Android security and maintenance risk; signed automation intents are the right extension boundary. Source: project principles and Android exported-component model.
- Reintroducing offline GeoIP without a licensed update channel: prior code removed stale GeoIP assets; bounded opt-in ipapi.co remains safer. Source: current changelog and `GeoIpLookup`.
- Shipping DoQ/WireGuard DNS as normal release toggles before a real audited engine exists: current production code ignores them, so surfacing them as active controls would harm trust. Source: `DnsVpnService.kt`, `DnsSettingsSection.kt`.
- PCAPng/TLS decryption secrets as a near-term default: useful for experts but high privacy risk; keep diagnostics conservative unless a separate consent model lands. Source: PCAPdroid and Wireshark documentation.
- Hosted multi-user household dashboard: appealing for parental review but contradicts the no-account/no-telemetry model unless implemented later as local-only LAN read-only mode. Source: NextDNS/Control D comparison and current blocked roadmap.

## Sources

### Project
- https://github.com/SysAdminDoc/HostShield

### OSS Competitors And Adjacent Tools
- https://github.com/celzero/rethink-app
- https://github.com/AdAway/AdAway
- https://github.com/M66B/NetGuard
- https://github.com/TrackerControl/tracker-control-android
- https://github.com/emanuele-f/PCAPdroid
- https://github.com/blokadaorg/blokada
- https://github.com/DNSCrypt/dnscrypt-proxy
- https://github.com/duckduckgo/tracker-radar

### Commercial Products And Docs
- https://adguard.com/kb/adguard-for-android/features/
- https://adguard-dns.io/kb/general/dns-filtering-syntax/
- https://nextdns.io/
- https://controld.com/features
- https://pi-hole.net/blog/2024/11/15/introducing-pi-hole-v6/
- https://emanuele-f.github.io/PCAPdroid/dump_modes

### Platform, Standards, Dependencies, Security
- https://developer.android.com/privacy-and-security/local-network-permission
- https://developer.android.com/about/versions/16/behavior-changes-all
- https://developer.android.com/about/versions/16/behavior-changes-16
- https://developer.android.com/guide/topics/large-screens
- https://developer.android.com/docs/quality-guidelines/adaptive-app-quality
- https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive
- https://developer.android.com/develop/ui/compose/navigation/predictive-back-gesture
- https://developer.android.com/guide/topics/resources/app-languages
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/jetpack/androidx/releases/work
- https://square.github.io/okhttp/changelogs/changelog/
- https://datatracker.ietf.org/doc/rfc8914/

## Open Questions

- Should the Local DNS Server remain a user-facing feature, or should README/app copy remove it until a complete Settings + permission + lifecycle path exists?
- Should `$dnstype=` support become a first-class `BlocklistHolder` rule model now, or should such rules be reported as unsupported source diagnostics until app/client-scoped rules are designed too?
- Should release builds hide experimental DoQ/WireGuard controls entirely, or show disabled controls with debug-only copy?
