# Prioritization Matrix

Research date: 2026-05-17

## Scoring

Each candidate is scored 1 to 5:

- Impact: user/security/reliability value.
- Evidence: source confidence and local fit.
- Effort: 1 low, 5 high.
- Risk: 1 low, 5 high.
- Fit: alignment with local-first Android DNS firewall philosophy.

Priority formula used qualitatively:

`Priority = Impact + Evidence + Fit - Effort - Risk`

## Now

| ID | Candidate | Impact | Evidence | Effort | Risk | Fit | Priority | Rationale |
|---|---|---:|---:|---:|---:|---:|---:|---|
| F012 | README/doc drift repair | 4 | 5 | 1 | 1 | 5 | 12 | Current docs contradict source in user-visible security claims. |
| F006 | Structured local event log and diagnostics ZIP | 5 | 5 | 3 | 2 | 5 | 10 | Needed for reliability without telemetry. |
| F017 | Room migration golden database suite | 5 | 5 | 3 | 3 | 5 | 9 | Data-loss risk is high and current schemas are incomplete. |
| F018 | DNS/parser fuzz harness | 5 | 5 | 3 | 3 | 5 | 9 | Protocol/security app with many parsers. |
| F001 | Complete DNSCrypt engine decision | 5 | 5 | 4 | 4 | 5 | 7 | High value but must start with audited engine choice. |
| F005 | DoH pin rotation manifest | 4 | 5 | 2 | 2 | 5 | 10 | Fail-closed is right, but pin breakage needs a safe local lifecycle. |
| F014 | Replace AndroidX Security Crypto alpha APIs | 4 | 4 | 3 | 3 | 5 | 7 | Security store dependency is aging and alpha. |
| F008 | Hagezi tier pack chooser | 4 | 5 | 2 | 2 | 5 | 10 | Clear blocklist ROI and low implementation cost. |
| F020 | TalkBack/semantics pass | 4 | 4 | 3 | 2 | 5 | 8 | Required for a 31+ screen security app. |
| F024 | Reproducible builds and release provenance | 4 | 4 | 3 | 2 | 5 | 8 | Distribution trust improvement before broader publishing. |

## Next

| ID | Candidate | Impact | Evidence | Effort | Risk | Fit | Priority | Rationale |
|---|---|---:|---:|---:|---:|---:|---:|---|
| F007 | Per-resolver health card | 4 | 5 | 2 | 2 | 5 | 10 | Uses existing EMA/failure signals and improves support. |
| F009 | Blocklist diff/delta pipeline | 4 | 4 | 3 | 2 | 5 | 8 | Useful after Hagezi pack chooser. |
| F010 | Subscribed allowlists | 4 | 4 | 2 | 2 | 5 | 9 | Reduces breakage; fits existing allowlist precedence. |
| F011 | Failed-source alerting | 3 | 4 | 1 | 1 | 5 | 10 | Small, practical reliability UX. |
| F015 | Argon2id migration path | 4 | 4 | 3 | 3 | 5 | 7 | Security improvement with migration complexity. |
| F016 | AES-GCM nonce ledger/assertion | 4 | 5 | 2 | 2 | 5 | 10 | Low-cost hardening around backup crypto. |
| F019 | Compose top-flow tests | 4 | 4 | 4 | 3 | 5 | 6 | Important but more expensive than JVM seams. |
| F021 | Locale/pseudolocale scaffolding | 3 | 4 | 4 | 2 | 4 | 5 | High code-touch count due hardcoded strings. |
| F025 | Obtainium docs/config | 3 | 4 | 1 | 1 | 5 | 10 | Low effort distribution UX. |
| F026 | Play AAB build lane | 3 | 4 | 2 | 2 | 4 | 7 | Needed if Play flavor is maintained seriously. |

## Later

| ID | Candidate | Impact | Evidence | Effort | Risk | Fit | Priority | Rationale |
|---|---|---:|---:|---:|---:|---:|---:|---|
| F003 | ODoH resolver | 4 | 5 | 4 | 4 | 5 | 6 | Strong privacy fit, but protocol complexity. |
| F004 | EDE parsing in logs | 3 | 5 | 2 | 2 | 5 | 9 | Nice after diagnostics foundation. |
| F027 | Firestack/tun2socks feasibility spike | 5 | 4 | 5 | 5 | 4 | 3 | Important architecture research, not immediate rewrite. |
| F028 | Userspace no-root per-app firewall | 5 | 4 | 5 | 5 | 5 | 4 | Major v7 work gated by TUN layer. |
| F033 | Self-hosted encrypted sync server | 4 | 3 | 5 | 5 | 4 | 1 | Fits principles but very large. |
| F035 | LAN read-only dashboard | 3 | 4 | 4 | 3 | 5 | 5 | Useful once diagnostics/local DNS mature. |
| F036 | Exodus signature mirror/update | 4 | 4 | 3 | 3 | 5 | 7 | Good privacy analytics upgrade. |
| F037 | DuckDuckGo Tracker Radar classifier | 4 | 4 | 3 | 3 | 5 | 7 | Complements static tracker scan. |
| F040 | PCAP-NG verdict export | 3 | 3 | 3 | 2 | 5 | 6 | Valuable diagnostics but not first. |
| F046 | GrapheneOS compatibility profile | 3 | 4 | 2 | 2 | 5 | 8 | Good validation target. |

## Watch / Investigate

| ID | Candidate | Reason |
|---|---|
| F002 | Requires F001 first. |
| F022 | High-contrast theme is worthwhile after TalkBack semantics. |
| F023 | Dynamic type audit pairs with locale/accessibility pass. |
| F032 | Requires production-grade WireGuard engine first. |
| F034 | Desktop companion is large and may split into its own repo. |
| F038 | ML-lite classifier needs dataset and evaluation plan first. |
| F041 | ECH/SVCB policy should wait for clearer Android/browser deployment patterns. |
| F042 | DNSSEC validation is operationally complex and should be opt-in only. |
| F044 | Smart/Split DNS belongs after resolver health and diagnostics. |
| F049 | AdGuard Home compatibility only matters if LAN DNS/server mode becomes a focus. |

## Not Planned

| ID | Candidate | Reason |
|---|---|
| F050 | Hosted cloud resolver account model conflicts with local-first/no-telemetry identity. |
| F051 | Browser extension form factor is already served by browser blockers and dilutes OS-level positioning. |
| F052 | Third-party telemetry SDKs conflict with product principles; local manual export is the substitute. |
