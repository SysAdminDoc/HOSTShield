# Competitor Matrix

Research date: 2026-05-17

GitHub metadata was sampled live during the run. Counts are point-in-time.

| Project | Source | Snapshot | Positioning | Strong patterns to harvest | HostShield comparison |
|---|---|---:|---|---|---|
| RethinkDNS / Rethink | https://github.com/celzero/rethink-app | 4,871 stars, pushed 2026-05-16 | Android DNS + firewall + WireGuard + anti-censorship app | Go firestack, split DNS, WireGuard orchestration, universal firewall rules, active release channel | Closest direct competitor. HostShield is more local-first/no-account and already has deep tracker/privacy diagnostics. Rethink is stronger on userspace firewall and network orchestration. |
| AdGuard for Android | https://github.com/AdguardTeam/AdguardForAndroid | 1,755 stars, pushed 2026-02-20 | Commercial Android ad blocker with filtering engine and HTTPS filtering | Mature filter engine, UX polish, commercial support model | HostShield should not copy the cloud/commercial model, but should learn from filter syntax completeness and user diagnostics. |
| AdGuard Home | https://github.com/AdguardTeam/AdGuardHome | 34,030 stars, pushed 2026-05-15 | Self-hosted DNS filtering server | Admin UX, upstream health, pending request dedupe, per-client query/stat controls, API compatibility | Good reference for local household dashboard and resolver health, not for hosted service direction. |
| Pi-hole | https://github.com/pi-hole/pi-hole | 58,661 stars, pushed 2026-05-16 | LAN DNS sinkhole | Gravity model, allowlist/subscription concepts, stable admin mental model | Inspires allowlist packs and explainable update previews. HostShield remains mobile-first. |
| NetGuard | https://github.com/M66B/NetGuard | 3,626 stars, pushed 2026-05-02 | Android no-root VPN firewall | Per-app network toggles, screen-state rules, robust VPN sinkhole design | HostShield has stronger DNS analytics and root dual-path. NetGuard remains a reference for no-root firewall UX. |
| AdAway | https://github.com/AdAway/AdAway | 9,077 stars, pushed 2026-02-10 | Root hosts blocker plus VPN mode | Systemless hosts, local web server response behavior, simple trust model | HostShield is broader. AdAway patterns are useful for root fallback and breakage recovery. |
| DNS66 | https://github.com/julian-klode/dns66 | 2,245 stars, pushed 2026-03-02 | Minimal DNS-only VPN blocker | Low-battery DNS-only model, straightforward source management | HostShield already implements richer features; keep DNS-only path lean. |
| personalDNSfilter | https://github.com/IngoZenz/personaldnsfilter | 879 stars, pushed 2026-05-17 | Lightweight DNS filter and LAN proxy | Filter decision cache, LAN DNS proxy, CNAME handling | HostShield already has local DNS server and decision cache; use as regression comparison for performance. |
| InviZible Pro | https://github.com/Gedsh/InviZible | 2,575 stars, pushed 2026-05-14 | DNSCrypt + Tor + I2P + firewall | Multi-engine privacy routing, root mode, DNSCrypt operational proof | Useful for DNSCrypt and proxy mode lessons. HostShield should avoid becoming a bundled Tor/VPN product. |
| Nebulo | https://github.com/Ch4t4r/Nebulo | 240 stars, last pushed 2022-11-06 | Android encrypted DNS client | DNS stamps and DoH/DoT/DoQ UX | Lower activity, but still useful as a compact DNS client reference. |
| Intra | https://github.com/Jigsaw-Code/Intra | 2,089 stars, pushed 2025-11-19 | Simple Android encrypted DNS / anti-censorship | Simple onboarding, anti-censorship framing, Jigsaw DNS stack lineage | HostShield is much broader; Intra is a benchmark for minimal safe onboarding. |
| DNSCrypt proxy | https://github.com/DNSCrypt/dnscrypt-proxy | 13,312 stars, pushed 2026-05-12 | Cross-platform DNSCrypt/DoH/ODoH resolver | DNSCrypt stamp parser, Anonymized DNSCrypt relay, plugins, block-name semantics | Library-of-record for DNSCrypt correctness. HostShield should not expose DNSCrypt until it matches audited semantics or delegates to an audited engine. |
| Hagezi DNS blocklists | https://github.com/hagezi/dns-blocklists | 22,892 stars, pushed 2026-05-17 | Tiered blocklist ecosystem | Multiple tiers, threat intel packs, NRD, abused TLDs, output formats, diffs | Strongest blocklist-gallery upgrade opportunity. |
| Exodus Privacy | https://github.com/Exodus-Privacy/exodus | 771 stars, pushed 2026-04-13 | Tracker database/scanner ecosystem | Tracker signature curation and app-report evidence | HostShield has local static signatures; mirroring/caching external signatures improves freshness. |
| NextDNS | https://nextdns.io/ | Commercial service | Hosted configurable DNS | Cloud resolver UX, device sync, logs | Rejected as hosted-account direction. Useful only as feature comparison. |
| uBlock Origin | https://github.com/gorhill/uBlock | Browser extension | Browser ad blocking | Cosmetic filtering and browser-specific blocking | Not a HostShield target. It defines the browser-specific niche HostShield should not chase. |

## Lessons

- RethinkDNS sets the bar for Android userspace firewall depth.
- DNSCrypt proxy sets the bar for DNSCrypt correctness.
- Pi-hole and AdGuard Home set the bar for explainable DNS operations, local dashboards, and source health.
- Hagezi sets the bar for blocklist tiering and metadata.
- HostShield's differentiator remains the combination of local-first Android coverage, no telemetry, root/no-root duality, tracker privacy analysis, and DNS diagnostics.
