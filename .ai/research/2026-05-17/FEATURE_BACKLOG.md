# Feature Backlog

Research date: 2026-05-17

Raw candidates before prioritization. Scores and tiers are in `PRIORITIZATION_MATRIX.md` and `ROADMAP.md`.

| ID | Candidate | Evidence | Notes |
|---|---|---|---|
| F001 | Complete DNSCrypt v2 query encryption/decryption with audited engine choice | L020, L021, E037-E041 | Current parser/route planner is groundwork only. Highest protocol gap. |
| F002 | Anonymized DNSCrypt user-facing route planner and validation UI | L020, L021, E041 | Only after F001. Must prevent resolver-as-relay privacy collapse. |
| F003 | ODoH target/proxy resolver | L020, E046 | Stamp parser has ODoH support but resolver path is absent. |
| F004 | EDE parsing in logs | E047, L013, L015 | Distinguish upstream `Blocked`, `Filtered`, `Censored`, `DNSSEC Bogus`, stale answers. |
| F005 | DoH pin rotation manifest with two-pin overlap | L015, E054 | Fail-closed is correct but brittle without a managed local pin lifecycle. |
| F006 | Structured local event log and diagnostics ZIP | L013, L015, L018, L019 | Replace scattered logcat reliance while preserving no telemetry. |
| F007 | Per-resolver health card | L015, L016, E020, E021 | Surface latency, success rate, fallback counts, EDE rate, pin failures. |
| F008 | Hagezi tier pack chooser | L029, E026 | Strong list ecosystem delta; current gallery has 44 lists and lacks Hagezi tiers. |
| F009 | Blocklist source diff and delta-update pipeline | E026, L029 | Reduce downloads and show change impact. |
| F010 | Subscribed allowlists / unbreak packs | E024, E025, E026, L014 | Align with allowlist precedence and reduce breakage. |
| F011 | Visual failed-source alerting | L029, L003, E015 | Foreground notification plus source badge. |
| F012 | README/doc drift repair | L003, L015, L027, L028 | DoH fail-closed, 405 signatures, ipapi.co. |
| F013 | Dependency refresh plan | L008, E008-E014, E054-E062 | AGP/Kotlin/Compose/OkHttp/AndroidX updates. |
| F014 | Replace AndroidX Security Crypto alpha APIs | L022, E009 | Migrate secrets to Android Keystore + Tink or custom audited wrapper. |
| F015 | Argon2id PIN and backup KDF migration | L022, L023, E052 | PBKDF2 remains functional; Argon2id is preferred for new designs. |
| F016 | AES-GCM nonce ledger/assertion for backups | L023, E053 | Low-cost assurance around random IV collision and key reuse. |
| F017 | Room migration golden database suite | L024-L026, E010 | Highest data-loss prevention item. |
| F018 | DNS/parser fuzz harness | L014, L020, L015, E042-E051 | DNS packets, stamps, hosts/adblock parser, backups. |
| F019 | Compose top-flow tests | L031, E012 | Onboarding, VPN start/stop, source add, rule add, backup/restore, PIN. |
| F020 | TalkBack/semantics pass | L003, L031, E011 | 31+ screens, icon buttons, state descriptions. |
| F021 | Locale scaffolding and pseudolocale CI | L003, strings count, E011 | `strings.xml` is tiny and UI hardcoded string count is high. |
| F022 | High-contrast theme mode | L031, E014 | Accessibility before broader visual refresh. |
| F023 | Dynamic type/layout audit | L031, E011 | Many fixed `fontSize` usages. |
| F024 | Reproducible builds and release provenance | L011, E073, E074 | Needed before IzzyOnDroid/F-Droid-style listing. |
| F025 | Obtainium install config docs | E075, L011 | Low-cost sideload UX win. |
| F026 | AAB build lane for Play flavor | L008, E006 | APK-only release workflow today. |
| F027 | Firestack/tun2socks feasibility spike | L013, E017, E018 | Bigger v7 architecture option. |
| F028 | Userspace per-app firewall in no-root VPN mode | E015, E030 | Gated by TUN architecture choice. |
| F029 | Connection-state-aware firewall rules | E015, E030 | Screen-off, metered, background-only, until unlock. |
| F030 | Scheduled firewall profiles | L003, L006 | Previous roadmap concept still fits. |
| F031 | Rule notes and rule-detail bottom sheet | E015 | Small UX improvement. |
| F032 | WireGuard SSID-based start/stop | E016, L019 | Only after WireGuard engine is production-grade. |
| F033 | Self-hosted encrypted sync server | L003, E020, E037 | Local-first alternative to hosted accounts. Large. |
| F034 | Desktop companion resolver CLI | E037 | Could share DNSCrypt/blocklist logic. Large. |
| F035 | LAN read-only household dashboard | E020, E024 | Local dashboard, no remote account. |
| F036 | Exodus signature mirror/update path | L027, E067, E068 | Keep tracker signatures fresh. |
| F037 | DuckDuckGo Tracker Radar network classifier | E069, L027 | Improve network-based tracker classification. |
| F038 | Local ML-lite domain classifier | E069 | Requires dataset/eval pipeline first. |
| F039 | Threat intel freshness dashboard | L030, E070-E072 | Show feed status, age, and impact. |
| F040 | PCAP-NG export with verdict metadata | L031, E051 | Richer diagnostics export. |
| F041 | SVCB/HTTPS/ECH awareness | E051 | Current SVCB parsing exists but UX/policy can grow. |
| F042 | DNSSEC validation toggle | E047, E051 | Keep opt-in due complexity. |
| F043 | Quick Settings pause durations | L003, E015 | Practical UX improvement. |
| F044 | Smart/Split DNS routing | E016 | Advanced resolver routing. |
| F045 | Tailscale/coexistence guide | E078 | Low-cost docs for single-VPN Android constraints. |
| F046 | GrapheneOS compatibility profile | E076, E077 | Hardened Android validation target. |
| F047 | Fast scroll on long lists | E015 | Useful with 44+ sources and app lists. |
| F048 | JSON/CSV unencrypted backup export with explicit warning | E015, L023 | Useful but must be visibly risky. |
| F049 | AdGuard Home REST-compatible local DNS mode | E020, E021 | Useful only if LAN dashboard/local server grows. |
| F050 | Reject hosted cloud resolver account model | L003, E020 | Conflicts with local-first/no-telemetry principle. |
| F051 | Reject browser extension form factor | E079 | Browser blockers already own that surface. |
| F052 | Reject third-party telemetry SDKs | L003 | Use local event log and manual exports instead. |
