# Research — HostShield
Date: 2026-08-11 — replaces all prior research in this file.

## Executive Summary

HostShield (v6.9.69, versionCode 151, GPL-3.0, 55,947 LOC Kotlin/Compose, 642 unit tests + 22 instrumented) is a local-first Android DNS firewall whose real differentiator is tri-mode protection through VPN, root/iptables, or a no-VPN local proxy. This addresses Android's single-VPN-slot constraint. The engine is mature and unusually hardened: zero `TODO`/`FIXME` markers in the tree, fail-closed encrypted DNS at two layers, a signed remote policy manifest, and 22 of 30 dependency pins already at latest stable with no advisory affecting any pinned version. The prior contents of this file were substantially stale. The `DohBypassUpdater` provenance risk, the unproductized `LocalDnsServer`, the missing adaptive-layout path, and the missing JSONL export were all fixed between then and now (verified below).

The highest-value direction is **shipping and proving what already exists**, not building more. Three of the top four findings are delivery failures, not engineering failures.

Top opportunities, priority order:

1. **Live outage:** 12 of 46 curated blocklist URLs return HTTP 404 in the shipped app (all HaGeZi; 3 marked `recommended`, 2 are allowlists). Verified 2026-08-11.
2. **Release lag, resolved 2026-08-29:** v6.9.69 publishes the accumulated fixes and preserves the v6.9.65 signing certificate.
3. **The catalog test pins URL strings but never fetches them** — a gate that certifies whatever is listed in it. No release check would have caught #1.
4. **IPv6 filtering bypass:** `DNS_TRAP_IPS` and `DOH_BYPASS_IPS` are IPv4-only hardcoded literals; an app with a hardcoded IPv6 resolver evades DNS interception entirely.
5. **Certificate pins expire 2027-06-13 with only a build-time gate** — a sideloaded install that stops updating silently loses pinning with no user-visible signal.
6. **The device/SDK backlog is no longer blocked.** API 37.0/37.1 platforms, an API-37 system image, an `clearcut-api37-ps16k` AVD, and a connected `SM-S938B` (Android 16, SDK 36) are all present on this workstation. ~15 items in `Roadmap_Blocked.md` are runnable today.
7. **Documentation is duplicated and already drifted** — `README.md`/`app/README.md` and `CHANGELOG.md`/`app/CHANGELOG.md` are hand-synced pairs (511 file-touches across 200 commits) and their text has diverged.
8. **`$app=` scoped rules are parsed, counted, and thrown away** while `AppDnsRuleEngine` exists to enforce them — the largest cheap capability gain available.
9. **English-only:** no `values-<locale>/` directories exist at all, and ~38% of visible strings (206 `Text("…")` literals vs 336 `stringResource`) are still not externalized.
10. **Distribution is GitHub-releases-only** (37 downloads on the latest APK, 14 stars). Not on IzzyOnDroid (404), not on Accrescent, and Google's Developer Verification enforcement begins 2026-09-30. Meanwhile a near-clone with a comparable feature set — BlockAds (1,725★, GPL-3.0, v6.5.2) — ships on F-Droid and IzzyOnDroid and releases faster. The engine is not the constraint; reach is.

Method note: findings below are labelled Verified where I reproduced them directly against this checkout or over the network on 2026-08-11. No build, test, or device run was performed in this pass — items whose acceptance depends on runtime behavior say so.

## Product Map

- **Core workflows:** choose protection mode (VPN / root hosts+iptables / no-VPN proxy); subscribe and curate blocklist sources; author allow/block/regex/adblock rules; inspect DNS logs, per-app attribution, and stats; export backups, diagnostics, JSONL evidence, and PCAP.
- **Personas:** privacy-focused Android users; rooted power users; family administrators (PIN, schedules, content categories); the maintainer collecting local evidence for support.
- **Platforms and distribution:** minSdk 26 / targetSdk 36 / compileSdk 37; `full` and `play` flavors; locally-signed APK published to GitHub Releases only; no backend required.
- **Key integrations and data flows:** `VpnService` split-route TUN (`10.120.0.1/24` + `fd00::1/120`, host routes only) and root iptables feed the DNS engines; `SourceDownloader` fetches user-selected lists (80 MB cap, SHA-256 integrity); `DohBypassUpdater` fetches an RSA-signed policy manifest from GitHub; Room v20 + DataStore hold local state; FileProvider/SAF/WebDAV export user-owned artifacts.

## Competitive Landscape

- **RethinkDNS** (5,264★, v0.5.6 2026-08-09, the only competitor shipping monthly). Learn: explicit tunnel/proxy state, per-app WireGuard routing, honest split-DNS framing. Its own README concedes that **on-device blocklists "aren't possible"** for it under Play constraints — that is HostShield's single clearest architectural advantage over the category leader, and it is not being said out loud anywhere. Avoid its failure mode: 614 open issues, a user-measured 2.8 GB RSS ([#2393](https://github.com/celzero/rethink-app/issues/2393)), GrapheneOS users calling it incomprehensible, and a paid VPN (RPN, Windscribe, $1.75/mo) that vacates the pure-local position.
- **BlockAds: Clean Internet** ([pass-with-high-score/blockads-android](https://github.com/pass-with-high-score/blockads-android), 1,725★, GPL-3.0, v6.5.2 2026-07-04, F-Droid + IzzyOnDroid). **The closest thing to a HostShield clone, and it ships faster.** Learn: filter compilation surfaced through WorkManager with a progress notification instead of a silent rebuild; trusted-Wi-Fi auto-pause; named profile presets (Default/Strict/Family/Gaming); Android TV D-pad navigation (v6.5.1). Avoid: its userspace-TCP/IP HTTPS MITM — though its **284-domain banking/payment/government passthrough list** is a reusable "never block critical services" safety concept. Note F-Droid flags it with the "depends on a non-free network service" anti-feature for GitHub-hosted rules; HostShield carries the same exposure via its GitHub-hosted DoH-bypass manifest and Spotify list.
- **AdAway** (9,335★, last release 2024-10-27, 668 open issues). Its top-voted open requests — custom DNS ([#2949](https://github.com/AdAway/AdAway/issues/2949)), crypto-miner blocking ([#937](https://github.com/AdAway/AdAway/issues/937)), no-reboot hosts application ([#1072](https://github.com/AdAway/AdAway/issues/1072)), wildcards and regex ([#842](https://github.com/AdAway/AdAway/issues/842), ten years open) — are **already HostShield's shipped feature list**. The only one it lacks is **Shizuku** ([#4240](https://github.com/AdAway/AdAway/issues/4240)). Read that tracker as a migration map, not a competitor. Avoid: 117 open "compatibility report" issues with zero triage tooling — the clearest unserved need in the ecosystem.
- **DNSNet** ([t895/DNSNet](https://github.com/t895/DNSNet), 907★, 1.3.16 2026-05-31) — the modern DNS66 successor, distributed on **F-Droid + Play + Accrescent** with Weblate translations. Learn two things directly: it **ships DoH3 as the default transport** (since 1.3.14, 2026-03-22), solving the same problem HostShield answered by removing DoH3 in v6.9.2; and its top open request is always-on VPN support ([#14](https://github.com/t895/DNSNet/issues/14), 14👍), with per-log source attribution second ([#17](https://github.com/t895/DNSNet/issues/17)) — HostShield already stores that attribution.
- **Athena** ([Kin69/Athena](https://github.com/Kin69/Athena), 680★, 1.8 2026-01-18) — the most direct positional competitor ("Material You firewall and ad blocker … rooted and non-rooted"), and quiet for seven months while HostShield shipped ten releases. Learn: **Shizuku as a third privilege tier** and a kill switch. This is the most winnable head-to-head in the survey.
- **AdGuard for Android** (v4.13.1, 2026-08-03) — the feature ceiling. v4.13 shipped **differential filter updates** (delta patches instead of full re-download), **post-quantum crypto in DnsLibs**, and **CRLite** for revocation. HostShield already tracks ETag/Last-Modified/SHA-256 per source, so delta updates are an incremental step with a real battery/data payoff on 200K-domain lists. Its most-voted gap, "specify default filtering policy for new app installations" ([#4482](https://github.com/AdguardTeam/AdguardForAndroid/issues/4482), 20👍), is also HostShield's. Avoid: HTTPS filtering and Stealth Mode require a user-installed MITM CA.
- **AdGuard DNS + AdGuard Home + Pi-hole v6** (v2.23 Jul 2026 / v0.107.78 / FTL 6.7). Learn the rule grammar — HostShield already enforces `$important`, `$badfilter`, `$dnstype`, `$denyallow`, `$dnsrewrite`; the remaining deltas are `$app`/`$client` scoping and `$respgeo` (new Jul 2026). Learn from Pi-hole v6: a self-documenting REST API served by the instance itself, one declarative config file, **Basic/Expert UI modes**, and async history import so DNS answers before the log DB hydrates. A loopback-bound, Pi-hole-v6-compatible API inherits that whole dashboard/Home-Assistant client ecosystem while staying strictly local. Avoid: server-admin IA.
- **NextDNS and Control D.** Learn from NextDNS: the security-heuristic taxonomy (NRD, DGA, typosquatting, IDN homographs, DDNS, parking, rebinding) as one-tap toggles; its own most-voted unshipped request is **comments on allow/deny entries** (132 votes) — a DB column here. Learn from Control D: four rule verbs (Block / Allow / **Redirect** / **Bypass**), per-action TTL overrides, three analytics privacy levels (off / counters / full), and the "request unblock" approval workflow, which on a phone is a notification action. Avoid: hosted accounts and proxy-exit fleets. Both paywall volume, not capability — not a surface a local app competes on. And **dns0.eu shut down on 2025-10-20 with no notice**, taking its users' filtering with it: the case for local-first, and the argument for surfacing upstream failover rather than doing it silently.

Also surveyed: **AFWall+** (3,441★, v4.1.0 2026-08-06 — a reactivated root firewall whose release notes are a checklist of iptables correctness HostShield's `IptablesManager` should be audited against), **TrackerControl** (2,591★, only 2 open issues, ships DuckDuckGo Tracker Radar attribution and Android 17 local-network handling), **InviZible Pro** (production DNSCrypt 2.1.16 + Tor, which HostShield only parses stamps for), **PCAPdroid** (HAR export, pcap-over-ip, bundled offline country/ASN databases), **NetGuard** (issues disabled; its README documents a real interop class HostShield's FAQ omits — IPsec-based Wi-Fi calling and MMS breaking under VPN mode), **personalDNSfilter** (whose top five requests are all already shipped here), **Blokada** (v5 frozen since 2023-05-25, v6 is paid cloud — the largest abandoned free user base in the category), **DNS66** (archived), **Intra**, **Karma Firewall** (stale, but declares *no* INTERNET permission at all — an unfalsifiable trust posture), **Nebulo**, **Daedalus** (dead), and the remote-management clients for Pi-hole/AdGuard Home/NextDNS.

## Security, Privacy, and Reliability

**Verified defects**

- **IPv6 DNS-trap and DoH-bypass gap.** `service/DnsVpnService.kt:164-231, 704-713` — `DNS_TRAP_IPS` (16 addresses) and `DOH_BYPASS_IPS` (~20 addresses) are IPv4 literals added as `/32` routes. The only IPv6 route added is `addCanonicalRoute(VDNS6_PRIMARY, 128)` (`:699`). On a dual-stack or IPv6-only network an app that hardcodes `2606:4700:4700::1111`, `2001:4860:4860::8888`, or `2620:fe::fe` bypasses both DNS interception and the port-443 DoH-bypass drop. The IPv4 literals are also anycast/CDN addresses that rot silently. Confidence: Verified (code); the behavioral consequence is Likely and warrants a device check.
- **Blocklist source outage.** 12 of 46 URLs in `app/app/src/main/assets/curated_blocklists.json` return 404 (probed 2026-08-11; StevenBlack/oisd/adaway/1Hosts controls all 200). The HaGeZi GitHub account is gone. Additionally `data/repository/SourceRepository.kt:127` seeds a dead built-in allowlist, and `repairKnownSourceUrls` (`:170-180`) actively *migrates* existing users' HaGeZi allowlist URLs onto the now-404 path. All four threat-intel feeds and both CNAME-cloak feeds are healthy.
- **The catalog test cannot detect this.** `test/.../sources/CuratedBlocklistsCatalogTest.kt:18-84` asserts exact URL strings against the same asset — string equality against itself, never a fetch. Same class of defect as `gotcha-gate-that-enumerates-what-it-guards`.
- **Pin expiry has no runtime surface.** `service/DohPinManifest.kt:82-123` sets `reviewAfter = 2026-12-13`, `expiresAfter = 2027-06-13` for all five providers, mirrored in `res/xml/network_security_config.xml`. `freshness()` is consumed only by the diagnostic map (`:59-63`) and by a build-time test. An installed APK that outlives the expiry loses NSC pin enforcement with no warning — a realistic outcome for a sideloaded app with a 3-version release lag.
- **Unreleased security fixes.** GitHub's latest release is v6.9.65 (2026-07-28, 37 downloads). Commits since include `security: stop honoring spoofable shortcut toggles below API 34`, `fix: secret prefs re-emit after save`, `fix: snapshot race, BOM-prefixed lists, and unguarded backup secrets`. That APK was also signed through the debug-keystore fallback *before* the `-debugsigned` versionName marker existed, so it is indistinguishable from a properly signed build.

**Verified fixed — do not re-investigate**

- `DohBypassUpdater` now verifies schema, canonical payload SHA-256, rollback version, and an RSA-2048 signature against a cert pinned in `DohBypassManifestVerifier.kt:27`. I independently recomputed the canonical payload hash and verified the signature of the shipped `doh-bypass-list.json`: **both valid**. `tools/check-release-docs.ps1` exits 0. The `CLAUDE.md:63-65` note claiming this signature fails the release gate is **stale and should be deleted**.
- `LocalDnsServer` is productized (`LocalDnsServerService`, Settings wiring at `SettingsViewModel.kt:572-620`, `specialUse` FGS with a declared subtype).
- Adaptive navigation exists (`ui/navigation/AdaptiveNavigationScaffold.kt`, `NavigationSuiteScaffold`).
- JSONL evidence export is wired to Settings (`ProtectionSettingsSection.kt:52-56`).
- No advisory affects any pinned dependency version. `bcprov-jdk18on 1.84` sits exactly on the fix boundary for CVE-2026-5598 (FrodoKEM timing channel, HIGH) — not vulnerable, but 1.85.2 carries further fixes.

**Missing guardrails**

- No liveness check on any remote URL the app depends on (blocklists, feeds, manifest) in either the test suite or `tools/check-release-docs.ps1`.
- Weakened crypto in `WireGuardProxy.kt:396-452` (XOR-with-SHA256 in place of X25519 DH + AEAD; SHA-256 chaining in place of HKDF) is correctly forced off in release builds by `ExperimentalEngineDisclosure`, but has zero tests and would be a critical defect if the gate ever regressed. Same for `DoqResolver`.
- `DnsVpnService.kt:476, 730` swallow `prefs.setEnabled(false)` failures inside the fail-closed shutdown path — a persistence failure there leaves the widget and QS tile claiming "Protected."
- No reproducible-build or published-artifact-hash story. Across all 11 surveyed competitor repos I found essentially no reproducible-build discussion — an open field, and one the repo's existing SHA-256 source integrity work already gestures at.

**Recovery and rollback**

- The blocklist last-good carry-forward is solid, but there is no "this source has been failing for N days" escalation that would have made the HaGeZi outage visible to a user rather than silent.
- Signing-key custody remains undecided (`Roadmap_Blocked.md`), which is the actual gate on both distribution and update integrity.

## Architecture Assessment

- **Refactor candidates (>800 LOC):** `service/DnsVpnService.kt` (2,733 — 87 all-time touches, the single hottest source file and where both historical P0s lived), `ui/screens/settings/SettingsScreen.kt` (1,410), `SettingsViewModel.kt` (1,391), `ui/screens/stats/StatsScreen.kt` (1,169), `domain/BlocklistHolder.kt` (1,154), `ui/screens/home/HomeViewModel.kt` (1,005), `logs/LogsScreen.kt` (965), `onboarding/OnboardingScreen.kt` (865), `sources/SourcesScreen.kt` (859), `service/RootDnsLogger.kt` (858), `home/HomeComponents.kt` (852). The `DnsVpnService` decomposition is already tracked in `Roadmap_Blocked.md`; the device blocker on it has cleared.
- **Boundary gap:** `ui/screens/settings` is the #1 directory by churn (154 touches / 200 commits) because every new capability lands there. Repeated "remove dead settings toggles" fixes show toggles shipping wired to nothing. A settings registry (declared key → pref → live-reload path) would make an unwired toggle a compile error instead of an audit finding.
- **Test gaps (zero coverage):** root package (`MainActivity` 615, `HostShieldApp` 81), `di`, `ui.components` (1,408 LOC incl. `PremiumSurfaces` 764 and `VicoCharts` 390), `ui.navigation`, `ui.accessibility`, and `HomeViewModel` (1,005 — the largest untested ViewModel). `DnsVpnService`, `RootDnsLogger`, `DoqResolver`, `WireGuardProxy`, `ContentFilterManager`, and 5 of 7 workers have no direct tests.
- **No pagination.** All list screens use `LIMIT :limit` DAO flows (`data/database/Daos.kt:130-209`); there is no Paging 3 dependency. Logs/Connection-log scroll stops at the limit.
- **Per-packet allocation** in the read loop: `packet.copyOf(length)` at `DnsVpnService.kt:1168, 1301`, `ByteArray(1500)` at `:1868`, `ByteArray(respLen)` at `:1926` — one to three allocations per query. Never profiled (`ROADMAP.md` says so explicitly).
- **Documentation architecture is the largest process defect.** `README.md`+`app/README.md` and `CHANGELOG.md`+`app/CHANGELOG.md` are duplicated pairs kept in sync by hand and enforced by a 32 KB PowerShell script that itself churned 14 times in 200 commits. They have already diverged (root README says "high-contrast AMOLED mode", `app/README.md` says "high-contrast mode"). Combined doc churn (511 touches) exceeds every source file by 4×.
- **Git history contains a duplicated lineage.** `Initial commit` exists twice (`ba99fc7`, `d33242c`) with an identical tree hash; every pre-June-2026 commit appears twice, from `35c3593` merging a rewritten history with `--allow-unrelated-histories` instead of replacing it. Real commit count is ~500, not 776. All `git log` statistics before 2026-06 are inflated 2×.
- **Dead configuration:** `kotlinx-serialization-bom` is declared in both `implementation` and `androidTestImplementation` (`app/app/build.gradle.kts:195-196, 284`) with **zero** `kotlinx.serialization` references in the tree and the plugin not applied. It is inert at runtime but appears in the CycloneDX SBOM that feeds the OSV gate.
- **Stale notes in `CLAUDE.md`** that will mislead the next pass: the `doh-bypass-list.json` signature note (verified passing), "Vico 2.5.0" (catalog pins 3.2.3), "DB version is 14" (Room is at v20), "Room 3.0 alpha / ~1h migration" (3.0.1 stable 2026-07-29 under a new `androidx.room3` group with `SupportSQLiteDatabase` removed — multi-session work), "10MB limit" on sync URLs (sources cap is 80 MB; 10 MB applies only to rule sync), and the `.github/workflows/ci.yml` reference (deleted; no `.github` directory exists).

## Rejected Ideas

- **RFC 6724 ULA source-address downgrade** (RethinkDNS [#1253](https://github.com/celzero/rethink-app/issues/1253)) — does not apply. HostShield's TUN carries `fd00::1/120` but adds **only host routes** (`/32`, `/128`) for virtual DNS and trap addresses, never a default route, so global IPv6 destinations keep the physical interface's GUA source. Investigated and dismissed; re-check only if a default route is ever added.
- **Full traffic proxifier / tun2socks** (RethinkDNS) — contradicts the DNS-first design and multiplies battery, memory, and native-networking risk; already deferred in `Roadmap_Blocked.md`.
- **HTTPS filtering / Stealth Mode** (AdGuard paid tier) — requires a user-installed MITM CA. Correct answer is a README statement of the boundary, not a partial implementation.
- **`play-services-cronet` for DoH3** — breaks the FOSS/Accrescent/F-Droid posture the repo explicitly banks on.
- **Hosted dashboard, accounts, multi-tenancy, SIEM streaming, SSO** (NextDNS, Control D, AdGuard Team) — all require a backend; the local analogue (WebDAV sync, QR config, JSONL export) already ships.
- **Cloud threat intelligence / "AI malware filter"** — licensed feeds with per-query lookups. Ship the *heuristics* (NRD, DGA entropy, homograph distance, dormant-then-active) which run on-device instead.
- **Android TV / leanback build** (BlockAds shipped D-pad nav in v6.5.1; RethinkDNS [#2664](https://github.com/celzero/rethink-app/issues/2664); DNS66 [#316](https://github.com/julian-klode/dns66/issues/316)) — real demand, but a second navigation model across 31+ screens for a codebase whose top complaint class is complexity. Revisit only after a Basic/Expert UI split exists.
- **Tor / I2P / DNSTT censorship transports** (InviZible Pro v7.5.0) — a different product with a different threat model and three more native binaries to keep patched.
- **Paid proxy/relay network** (RethinkDNS RPN; the only working OSS monetization in the survey) — requires operated infrastructure and inverts the no-backend claim.
- **Userspace TCP/IP HTTPS filtering + scriptlet engine** (BlockAds v6.3.0) — same MITM objection as AdGuard's paid tier. Its 284-domain banking/government passthrough list is worth borrowing as a "never block critical services" safety list; the interception is not.
- **Dynamic plugin marketplace** — a security app benefits from audited static pipelines, not runtime third-party code.
- **Per-log source attribution as new work** (DNSNet [#17](https://github.com/t895/DNSNet/issues/17)) — already implemented. `data/model/Entities.kt:109-112` stores `decision_reason`, `decision_source`, `matched_value`, and `decision_precedence`, and `LogsScreen.kt:657-664` renders all four. Only the *filter facet* and the aggregate chart are missing (HS-2026-08-P2-021).
- **Room 3.0 migration now** — stable since 2026-07-01 and Room 2.8.x is maintenance-only, but the migration is a new Maven group, a new package, `SupportSQLiteDatabase` removed, 20 migrations to `SQLiteConnection`, and all DAO methods forced `suspend`. Zero security pressure. Defer past the targetSdk-37 work.
- **DELEG-based transport discovery** — `draft-ietf-deleg-11`, expires 2027-01-24. Too early to build on.
- **ODoH production client** — RFC 9230 is Experimental and explicitly not IETF-endorsed; Cloudflare is effectively the only target, at +50–100 ms/query. Stamp parsing is the right level of investment.

## Sources

Project
- https://github.com/SysAdminDoc/HostShield
- https://api.github.com/repos/SysAdminDoc/HostShield/releases

OSS competitors
- https://github.com/celzero/rethink-app · issues 1047, 1253, 2393, 2521, 2608, 2664, 2882
- https://github.com/pass-with-high-score/blockads-android · https://github.com/t895/DNSNet (issues 14, 17, 42)
- https://github.com/Kin69/Athena (issues 41, 80) · https://github.com/ukanth/afwall (v4.1.0 release notes)
- https://github.com/AdAway/AdAway · issues 842, 937, 1072, 2949, 4240
- https://github.com/M66B/NetGuard · https://github.com/TrackerControl/tracker-control-android
- https://github.com/emanuele-f/PCAPdroid · https://github.com/Gedsh/InviZible
- https://github.com/julian-klode/dns66 (archived) · https://github.com/blokadaorg/blokada
- https://github.com/AdguardTeam/AdguardForAndroid (issue 4482) · https://github.com/AdguardTeam/AdGuardHome
- https://github.com/AdguardTeam/HostlistCompiler · https://github.com/DNSCrypt/dnscrypt-proxy

Commercial / server-side
- https://adguard-dns.io/kb/general/dns-filtering-syntax/ · https://adguard-dns.io/en/versions/dns/release.html
- https://docs.controld.com/docs/blocked-query-response · https://controld.com/blog/control-d-updates-march-2026/
- https://nextdns.github.io/api/ · https://help.nextdns.io/category/ideas
- https://pi-hole.net/blog/2025/02/18/introducing-pi-hole-v6/ · https://docs.pi-hole.net/api/
- https://adguard.com/kb/adguard-for-android/features/
- https://www.bleepingcomputer.com/news/security/dns0eu-private-dns-service-shuts-down-over-sustainability-issues/

Android platform / distribution
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/privacy-and-security/local-network-permission
- https://developer.android.com/privacy-and-security/security-config
- https://developer.android.com/developer-verification
- https://support.google.com/googleplay/android-developer/answer/11926878
- https://support.google.com/googleplay/android-developer/answer/12564964
- https://f-droid.org/en/2026/02/24/open-letter-opposing-developer-verification.html
- https://blog.accrescent.app/posts/android-developer-verification/

Dependencies
- https://dl.google.com/dl/android/maven2/org/chromium/net/group-index.xml
- https://developer.android.com/jetpack/androidx/releases/room3 · .../security · .../work · .../datastore
- https://developer.android.com/build/releases/agp-9-3-0-release-notes
- https://kotlinlang.org/docs/whatsnew24.html · https://github.com/google/ksp/releases
- https://github.com/google/dagger/releases/tag/dagger-2.60.1
- https://github.com/square/okhttp/blob/master/CHANGELOG.md
- https://osv.dev/vulnerability/GHSA-p93r-85wp-75v3

Standards
- https://datatracker.ietf.org/doc/rfc9849/ (ECH) · https://www.rfc-editor.org/info/rfc9460/ (SVCB/HTTPS)
- https://www.rfc-editor.org/rfc/rfc9462.html (DDR) · https://www.rfc-editor.org/info/rfc9606/ (RESINFO)
- https://datatracker.ietf.org/doc/draft-ietf-dnsop-compact-denial-of-existence/ (RFC 9824)
- https://datatracker.ietf.org/doc/draft-ietf-deleg/

Community signal
- https://news.ycombinator.com/item?id=41931035 · 48453216 · 43202812 · 42372438
- https://discuss.grapheneos.org/d/3242 · /d/13236 · /d/7930
- https://community.blokada.org/t/google-cracks-down-on-vpn-based-adblockers/26110
- https://github.com/hagezi/dns-blocklists/issues/10546 (deprecation notice, repo now 404)
- https://forum.netgate.com/topic/200858/hagezi-discontinuation-of-legacy-domains-and-hosts-lists

## Open Questions

- **Which HaGeZi replacement?** Recommended: **mirror the ABP lists into `SysAdminDoc/HostShield/blocklists/`**, matching the `SpotifyAds.txt` precedent — it keeps the supply chain under the same key as the app. The alternatives are repointing at `hagezi-mirror.dnsbunker.org` (fresh: `adblock/pro.txt` was 4.70 MB / 213,908 entries on 2026-08-11, but a third-party host with no publisher signature — a trust downgrade from `raw.githubusercontent.com`) or dropping the HaGeZi tier and leaning on oisd/1Hosts/AdGuard. Only the maintainer can accept the mirror's ongoing maintenance cost or its trust tradeoff, so flag the choice — but do not leave the P0 unfixed waiting on it.
- **Signing-key custody** (already open in `Roadmap_Blocked.md`) now gates three things at once: distributable releases, Accrescent/IzzyOnDroid submission, and Developer Verification registration before the 2026-09-30 enforcement date. It is the single highest-leverage unanswered question in the project.
