# Dataset, Model, And Integration Review

Research date: 2026-05-17

HostShield is not an ML-first app, but it has several dataset and integration surfaces:

- DNS blocklists.
- CNAME cloak databases.
- Tracker SDK signatures.
- Tracker domain intelligence.
- Threat intel feeds.
- GeoIP and ASN data.
- DNS stamp/resolver metadata.
- Optional future on-device domain classification.

## Current Local Data Assets

| Asset | Location | Current state |
|---|---|---|
| Static blocklists | `blocklists/` | 22 files; largest is `ipv4.txt` with 45,213 entries. |
| Curated blocklist gallery | `app/app/src/main/assets/curated_blocklists.json` | 7 categories, 44 lists. |
| Tracker SDK signatures | `TrackerSignatureDb.kt` | 405 hardcoded signatures across tracker categories. |
| Network tracker domains | `NetworkTrackerDb.kt` | Local tracker-domain map. |
| Threat intel feeds | `ThreatIntelManager.kt`, `ThreatIntelWorker.kt` | URLhaus, Spamhaus, Emerging Threats, Disconnect Malware are represented in current docs/source. |
| GeoIP/ASN | `OfflineGeoIp.kt`, `GeoIpLookup.kt` | Offline preferred; ipapi.co HTTPS fallback exists. |
| DNS stamps | `DnsStampParser.kt` | Local parse/encode support for multiple protocols. |

## External Dataset Opportunities

| Dataset | URL | Fit | Use |
|---|---|---|---|
| Hagezi DNS blocklists | https://github.com/hagezi/dns-blocklists | High | Tiered packs, threat intel packs, NRD, DynDNS, abused TLDs, diff-aware updates. |
| OISD | https://oisd.nl/ | High | ABP/wildcard list ecosystem validation and source gallery update. |
| StevenBlack hosts | https://github.com/StevenBlack/hosts | High | Conservative default pack. |
| AdGuard DNS filter | https://github.com/AdguardTeam/AdGuardSDNSFilter | High | DNS filter syntax and regression corpus. |
| AdGuard Hostlists Registry | https://github.com/AdguardTeam/HostlistsRegistry | High | Metadata model for curated gallery. |
| FilterLists | https://github.com/collinbarrett/FilterLists | Medium | Source discovery and metadata. |
| Exodus Privacy | https://reports.exodus-privacy.eu.org/ | High | Tracker signature freshness and scanner validation. |
| Exodus source | https://github.com/Exodus-Privacy/exodus | High | Signature ingestion and local cache format. |
| DuckDuckGo Tracker Radar | https://github.com/duckduckgo/tracker-radar | High | Network tracker domain classification. |
| URLhaus | https://urlhaus.abuse.ch/ | High | Malware domain feed. |
| Spamhaus DROP | https://www.spamhaus.org/drop/ | High | IP/CIDR threat intel. |
| Emerging Threats | https://rules.emergingthreats.net/ | Medium | IP/domain reputation augmentation. |
| DNSCrypt resolver lists | https://github.com/DNSCrypt/dnscrypt-resolvers | High | DNSCrypt resolver/relay catalog once engine exists. |

## Model Opportunities

| Model idea | Status | Required before implementation |
|---|---|---|
| Local n-gram/TLD/domain-age classifier | Research only | Curated positive/negative corpus, false-positive cost model, offline training pipeline, on-device TFLite benchmark. |
| Tracker behavior classifier | Research only | Merge static SDK signatures with DNS behavior labels from Tracker Radar and local logs. |
| Blocklist breakage predictor | Research only | User-local allow events and list-overlap features. Must remain local-only. |
| Federated learning | Watch only | Too complex for current privacy/audit posture. |

## Integration Opportunities

- Hagezi pack installer with local manifest cache.
- DNSCrypt resolver/relay catalog import once crypto engine is ready.
- Obtainium config block for GitHub release installs.
- IzzyOnDroid metadata after reproducible build evidence is generated.
- GrapheneOS compatibility profile and guidance.
- Tailscale coexistence guide.
- AdGuard Home API compatibility only if local DNS server and LAN dashboard become a supported power-user mode.

## Evaluation Plan

Any new dataset/model integration should have:

1. Source license check.
2. Update cadence and availability check.
3. Local cache format with version, timestamp, source URL, and SHA-256.
4. False-positive review flow before enabling by default.
5. Unit tests from a frozen mini-corpus.
6. User-visible source health and last-updated status.
7. Offline behavior defined explicitly.
