# Research — HostShield

## Executive Summary
Verified: HostShield is a GPL-3.0 Android/Kotlin local-first DNS firewall and blocker for Android 8+ that is strongest when it stays focused on on-device DNS interception, root/VPN/proxy protection, curated blocklists, diagnostics, and fail-closed encrypted DNS behavior. The highest-value direction is trust and platform hardening: keep the DNS engine honest about what it supports, prove protection survives Android lifecycle/profile edge cases, and tighten supply-chain/provenance around remotely fetched policy data before adding larger protocol or network-server claims. Top opportunities: remote DoH-bypass manifest provenance; connected always-on/work-profile/Private Space release evidence; LAN `LocalDnsServer` productization or removal; DNS stamp capability diagnostics for DNSCrypt/ODoH; API 37/adaptive-layout readiness already tracked in `ROADMAP.md`; tracker-owner dataset generation already tracked; JSONL diagnostic/query export; large-list ergonomics for Logs/Sources/Apps.

## Product Map
- Core workflows: choose protection mode, configure encrypted DNS and blocklists, manage rules/sources, inspect DNS logs/stats/tracker attribution, export backups/diagnostics/PCAP evidence.
- User personas: privacy-focused Android users, rooted power users, family/admin users using schedules and content controls, maintainers/support users collecting local evidence.
- Platforms and distribution: Android minSdk 26, targetSdk 36, full/play flavors, signed local APK release flow, no required backend, GPL-3.0.
- Key integrations and data flows: `VpnService` and root iptables route DNS into local engines; `SourceDownloader.kt` fetches user-selected lists; `DohBypassUpdater.kt` fetches additive policy from GitHub; Room/DataStore store local state; FileProvider/SAF/WebDAV export and sync user-owned artifacts.

## Competitive Landscape
- RethinkDNS: strong at split DNS, SOCKS5/HTTP CONNECT/WireGuard routing, and leak guardrails. Learn from its explicit tunnel/proxy state and DNS-leak controls; avoid turning HostShield into a full traffic proxifier.
- AdGuard DNS / AdGuard Home: strong rule syntax, `$dnstype`, `$client`, `$dnsrewrite`, per-client configuration, and strict unsupported-modifier semantics. Learn from rejecting unsupported scoped rules safely; avoid server-admin UI complexity and cloud-account assumptions.
- NetGuard / personalDNSfilter / DNS66 / DNSNet: strong examples of local VPN/hosts/proxy constraints and blunt compatibility docs. Learn from clear one-VPN-slot, battery, Private DNS, and first-party-ad limitations; avoid vague protection claims.
- PCAPdroid: strong local capture/export workflows, PCAP export, and issue demand for JSON export. Learn from explicit dump modes and export destinations; avoid TLS MITM/decryption features as defaults in a DNS blocker.
- TrackerControl + DuckDuckGo Tracker Radar: strong tracker-company/category attribution from audited datasets. Learn from generated ownership metadata and provenance; avoid telemetry or runtime remote dependency for attribution.
- NextDNS / Control D / Pi-hole: strong dashboards, log retention controls, profiles, APIs, and server-side pagination. Learn from retention/export ergonomics and reversible disable/status patterns; avoid hosted-account and multi-device SaaS direction.
- AdAway: strong simple root/hosts UX, source management, and backup/restore expectations. Learn from clear source/rule flows; avoid reducing HostShield to hosts-file parity when its differentiator is richer DNS policy.

## Security, Privacy, and Reliability
- Verified risk: `app/app/src/main/java/com/hostshield/service/DohBypassUpdater.kt` trusts raw GitHub HTTPS JSON, validates only shape/size/count, and writes accepted domains to DataStore. A compromised publish path can add overblocking policy until cache is corrected.
- Verified risk: `app/app/src/main/java/com/hostshield/service/LocalDnsServer.kt` exists as a LAN DNS server with policy tests, while release docs and README still contain local-DNS/LAN-adjacent references. It is not visible as a first-class Settings/status/permission-gated workflow.
- Verified risk: `DnsStampParser.kt` parses DNSCrypt, DNSCrypt relay, ODoH target, and ODoH relay stamps, and `DnsCryptRoutePlanner.kt` validates routing envelopes, but no production DNSCrypt/ODoH crypto transport engine is wired. Resolver import UX must not imply unsupported protocols work.
- Verified fixed/narrowed risk: `$dnstype` survives rebuilds in v6.9.57 and `$app=`, `$client=`, and `$ctag=` rules are now skipped instead of globalized in `AdblockRuleParser.kt`; the remaining value in the old roadmap item is diagnostics or true scoped support, not fixing global fallback.
- Missing guardrails: connected release evidence does not yet prove always-on VPN, lockdown, boot/update resume, work-profile/Private Space warnings, and battery-exemption failure paths on a real device matrix.
- Recovery and rollback needs: remote policy updates should have signed versioning, downgrade/tamper rejection, cached-known-good fallback, and a release-script check before publishing.

## Architecture Assessment
- Module boundaries: `DohBypassUpdater.kt` should separate transport fetch, manifest verification, parser, and DataStore commit so tampered or downgraded manifests never reach policy state.
- Refactor candidates: `LocalDnsServer.kt` needs either full lifecycle/Settings/notification/permission integration or removal from release-facing claims and keep rules; `DnsStampParser.kt` needs a capability classifier separate from parse support.
- UI/platform gaps: no `WindowSizeClass`/adaptive navigation path was found for dense Compose screens; `LocaleLayoutScaffoldTest.kt` checks reusable surfaces only, not top flows under real RTL/pseudolocale.
- Test gaps: unit tests are broad, but connected tests do not exercise the actual Android VPN/profile/lifecycle matrix; `TopFlowComposeTest.kt` is a useful UI smoke suite, not protection evidence.
- Documentation gaps: README still mentions local DNS/proxy compatibility in places that need sharper wording so root proxy, on-device proxy, and LAN DNS server are not conflated.

## Rejected Ideas
- Full traffic VPN/proxy platform like RethinkDNS: rejected because HostShield's architecture and README position it as DNS-first local blocking, and full traffic routing would multiply battery, policy, and native-networking risk.
- Hosted cloud dashboard/accounts like NextDNS or Control D: rejected because the app's privacy value is local-first operation without a required backend.
- Default TLS MITM/decryption like PCAPdroid's advanced mode: rejected because it requires certificate installation, broad consent UX, and a much larger trust surface than DNS evidence export.
- Dynamic plugin marketplace: rejected because a security/privacy app benefits more from audited static rule/provenance pipelines than runtime third-party code.
- Production DNSCrypt/ODoH engine immediately: rejected for now because parser/route groundwork exists, but dependency/licensing/AAR-size and crypto-maintenance decisions need a bounded spike before user-facing enablement.
- Multi-user SaaS/admin console: rejected because Android work profiles and Private Spaces are platform boundaries, and public evidence favors local warnings plus root/lockdown guidance over a backend console.

## Sources
Project:
- https://github.com/SysAdminDoc/HostShield

OSS and adjacent:
- https://github.com/celzero/rethink-app
- https://github.com/AdAway/AdAway
- https://github.com/M66B/NetGuard
- https://github.com/IngoZenz/personaldnsfilter
- https://github.com/TrackerControl/tracker-control-android
- https://github.com/emanuele-f/PCAPdroid
- https://github.com/duckduckgo/tracker-radar
- https://github.com/hagezi/dns-blocklists

Commercial and server-side DNS:
- https://adguard-dns.io/kb/general/dns-filtering-syntax/
- https://nextdns.io/
- https://controld.com/features
- https://docs.pi-hole.net/api/
- https://github.com/AdguardTeam/AdGuardHome/wiki/Clients
- https://pi-hole.net/blog/2025/02/18/introducing-pi-hole-v6/

Android platform and dependencies:
- https://developer.android.com/privacy-and-security/local-network-permission
- https://developer.android.com/develop/connectivity/vpn
- https://developer.android.com/reference/android/net/VpnService
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://android-developers.googleblog.com/2026/05/android-adaptive-development-ecosystem.html
- https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive
- https://developer.android.com/jetpack/androidx/releases/room3
- https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html
- https://developer.android.com/jetpack/androidx/releases/work
- https://square.github.io/okhttp/changelogs/changelog/

Standards and security:
- https://datatracker.ietf.org/doc/html/rfc8484
- https://www.rfc-editor.org/info/rfc9230/
- https://datatracker.ietf.org/doc/html/rfc9250
- https://www.rfc-editor.org/info/rfc8914/
- https://www.bouncycastle.org/download/bouncy-castle-java/

## Open Questions
- None block prioritization. The LAN DNS server choice is an implementation decision: productize it behind explicit UX/permission/lifecycle gates or remove release-facing claims until it is first-class.
