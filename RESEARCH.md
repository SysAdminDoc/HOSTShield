# Research - HostShield

## Executive Summary

HostShield is a mature GPL-3.0 Android DNS firewall and blocker: VPN/root/proxy protection, pinned DoH/DoT, local blocklists, threat-intel feeds, per-app rules, diagnostics, PCAP export, encrypted backup, widgets, and automation are already present. Its strongest current shape is a local-first Android alternative to RethinkDNS/AdAway/NetGuard that avoids account-bound DNS services. The highest-value direction is not more breadth; it is correctness and trust evidence. Top opportunities: fix scoped AdGuard DNS rule handling before it over-blocks; verify already-generated release attestations and publish user commands; gate DoH/DoT pin freshness and live-chain drift; add license/provenance metadata for curated blocklists; add automated accessibility regressions for Compose flows; and extend release truth checks to root evidence docs that still contain stale claims.

## Product Map

- Core workflows: enable protection; choose VPN/root/proxy mode; manage curated/custom sources; configure encrypted DNS; review logs/stats; add domain/app rules; pause or allow false positives; export backups, diagnostics, and PCAP; sync via WebDAV; transfer with QR; automate by broadcast intents.
- User personas: privacy-focused Android users, rooted power users, parents using content controls, local-first admins, and maintainers shipping GitHub/F-Droid/Obtainium-style APKs.
- Platforms and distribution: Android minSdk 26, targetSdk 36, Kotlin 2.3, AGP 9.2, Compose Material 3, Hilt, Room, DataStore, WorkManager, OkHttp, libsu; full and play flavors; GitHub Actions release workflow.
- Key integrations and data flows: blocklists feed `SourceDownloader`/`BlocklistHolder`; DNS packets flow through `DnsVpnService`, root, or proxy services to DoH/DoT and debug-only experimental engines; threat feeds annotate logs; backups/diagnostics/PCAP remain local; release workflow emits APK/AAB, checksums, SBOM, OSV, page-alignment report, and provenance.
- Category coverage: security is strong but needs rule-scope correctness and attestation verification; accessibility helpers exist but need regression gates; i18n is already roadmapped; observability is strong locally but source provenance is thin; testing needs a11y and release-evidence gates; docs need truth checks for root-generated docs; distribution already has SBOM/OSV/page-alignment and needs verification commands; plugin ecosystem should stay limited to automation intents; mobile/offline/multi-user/migration/upgrade work is mostly already represented in existing roadmap items.

## Competitive Landscape

- RethinkDNS: does well at local DNS/firewall/proxy control, WireGuard routing, and per-app connection visibility. Learn from its app/upstream routing model; avoid copying its complexity where HostShield's value is calmer local defaults.
- AdAway and NetGuard: do well at simple root/no-root mode clarity, custom hosts sources, and conservative battery expectations. Learn from explicit mode boundaries and support docs; avoid maintenance debt from older hosts-file semantics.
- TrackerControl: does well at explaining app tracking and exposing tracker companies. Learn from user-facing tracker context; avoid ambiguous "blocked vs allowed" states called out in open issues.
- PCAPdroid: does well at diagnostics, PCAP/PCAPng export, and clear packet-capture boundaries. Learn from export evidence quality; avoid TLS MITM as a default privacy product surface.
- AdGuard for Android / AdGuard DNS: does well at DNS rule syntax, client profiles, stats, and portable configuration. Learn from syntax compatibility and source registry governance; avoid cloud-account dependence and opaque filtering cores.
- NextDNS and Control D: do well at profiles, analytics, service catalogs, homograph/NRD/DGA security heuristics, and audit trails. Learn from explainable policy dashboards; avoid making remote logs or hosted control planes mandatory.
- Pi-hole / AdGuard Home / Technitium: do well at admin workflows, per-client policies, query logs, and source metadata. Learn from client/profile ergonomics; avoid assuming a server is always available.

## Security, Privacy, and Reliability

- Verified: `app/app/src/main/java/com/hostshield/domain/parser/AdblockRuleParser.kt` ignores `$app=` and `$client=` modifiers but still applies the base rule globally. AdGuard documents these as scope-limiting modifiers; applying them globally can create false positives for custom or imported DNS rules.
- Verified: `.github/workflows/release.yml` now generates SBOM/APK/AAB attestations, correcting stale prior research. The remaining gap is that CI and `tools/release-provenance.ps1` do not verify those attestations with `gh attestation verify` or publish copy-ready verification commands for users.
- Verified: `DohPinManifest.kt` has review and expiry dates plus primary/backup SPKI pins, and `DotResolver.kt` reuses those pins. The missing guardrail is a release/CI failure when pins are review-due, expired, or no longer present in live verified provider chains.
- Verified: `app/app/src/main/assets/curated_blocklists.json` has labels, URLs, categories, warnings, and entry estimates, but no source homepage, license, maintainer, last-reviewed date, upstream issue URL, mirror policy, or format/license compatibility gate. AdGuard HostlistsRegistry and StevenBlack both model richer source metadata.
- Verified: `app/app/src/androidTest/java/com/hostshield/ui/TopFlowComposeTest.kt` covers real flows, but there is no automated assertion layer for minimum touch targets, missing labels/content descriptions, live-region regressions, or TalkBack-relevant semantics.
- Verified: `tools/check-release-docs.ps1` validates README/app metadata/workmanager/platform claims, but it does not read root `CLAUDE.md` or `LOGO_PROMPTS.md`; `LOGO_PROMPTS.md` still claims offline GeoIP and contains truncated product copy.
- Corrected stale findings: current code no longer has the old DoT TLS 1.3/API 26 gap, pseudo DoQ/WireGuard are debug-gated in service loading and settings, offline GeoIP dependencies/assets were removed from release checks, and release attestations are generated.

## Architecture Assessment

- Rule engine boundary: extend `AdblockRuleParser.DnsRule` and downstream matching for Android package/client scopes, or reject scoped rules safely. Silent global fallback is the wrong failure mode for a security utility.
- Source catalog boundary: make `curated_blocklists.json` the governed source of truth and generate/validate any hardcoded seeds in `SourceRepository.kt`; add a small validation tool or test for source metadata, duplicate URLs, HTTPS-only URLs, supported formats, license fields, and stale review dates.
- Release evidence boundary: bind `.github/workflows/release.yml`, `tools/release-provenance.ps1`, checksums, SBOM, OSV, page alignment, and attestation verification into one chain. The project is close; the missing step is verification and user-facing commands.
- Pinning boundary: `DohPinManifest.kt` should be tested as policy, not just diagnostics. A CI check should fail before review/expiry deadlines or live-chain mismatch creates a resolver outage.
- Accessibility boundary: reusable modifiers in `ui/accessibility/AccessibilityModifiers.kt` are useful, but they need tests that prevent regressions across Home, Sources, Rules, Logs, Settings, DNS Tools, and Parental Controls.
- Documentation boundary: release truth checks should include root docs that carry product claims or remove/regenerate stale generated docs. Stale security/privacy claims are high-cost in this repo because trust is the product.

## Rejected Ideas

- Full HTTPS/TLS MITM inspection: PCAPdroid, AdGuard, and Cloudflare show the capability, but it contradicts HostShield's local-first DNS-firewall posture, breaks pinned apps, and expands legal/support risk.
- Cloud account dashboard or hosted multi-tenancy: NextDNS, Control D, and AdGuard DNS prove the market value, but mandatory remote logs or accounts would undercut HostShield's privacy promise.
- Arbitrary plugin ecosystem: useful in server products, but risky on Android; Tasker/MacroDroid broadcasts are the right extension boundary for now.
- Promoting debug DoQ/WireGuard engines: current implementations are intentionally simplified; expose only after an audited library path lands.
- Default NRD mega-feeds: HaGeZi warns that NRD lists are very large and false-positive-prone; keep this as opt-in threat-intel work already covered by the roadmap.
- Re-adding offline GeoIP now: PCAPdroid uses offline ASN/country lookup, but HostShield already removed the stale GeoIP dependency; reintroduce only with a licensed update path and size/privacy budget.

## Sources

### Project and OSS competitors

- https://github.com/celzero/rethink-app
- https://github.com/AdAway/AdAway
- https://github.com/M66B/NetGuard
- https://github.com/TrackerControl/tracker-control-android
- https://github.com/emanuele-f/PCAPdroid
- https://f-droid.org/en/packages/dnsfilter.android/
- https://github.com/AdguardTeam/HostlistsRegistry
- https://github.com/StevenBlack/hosts

### Commercial and adjacent products

- https://adguard.com/kb/adguard-for-android/features/
- https://adguard-dns.io/en/welcome.html
- https://nextdns.io/
- https://controld.com/features
- https://developers.cloudflare.com/cloudflare-one/traffic-policies/

### Standards, platform, and dependencies

- https://adguard-dns.io/kb/general/dns-filtering-syntax/
- https://github.com/AdguardTeam/Adguardhome/wiki/Clients
- https://docs.github.com/en/actions/concepts/security/artifact-attestations
- https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations
- https://cli.github.com/manual/gh_attestation_verify
- https://developer.android.com/develop/ui/compose/accessibility/testing
- https://developer.android.com/training/testing/espresso/accessibility-checking
- https://developer.android.com/privacy-and-security/local-network-permission
- https://developer.android.com/guide/practices/page-sizes
- https://square.github.io/okhttp/changelogs/changelog/
- https://github.com/google/osv-scanner-action

### Community signal

- https://forum.f-droid.org/t/rethink-dns-yes-or-no/29561
- https://news.ycombinator.com/item?id=33250974
- https://github.com/TrackerControl/tracker-control-android/issues
- https://github.com/AdAway/AdAway/issues/2423
- https://github.com/IngoZenz/personaldnsfilter/issues/299
- https://discuss.privacyguides.net/t/which-app-block-allow-in-firewall/30603

## Open Questions

- Should HostShield map AdGuard `$app=` scopes to Android package names, or should scoped rules be rejected until an explicit app-scope model exists?
- Should root `LOGO_PROMPTS.md` remain a maintained product artifact, or be regenerated/removed so release truth checks do not need to validate generated prompt copy?
