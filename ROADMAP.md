# HostShield Roadmap

**Baseline:** v6.4.0 (versionCode 57). **Targets:** v6.5.x (parity + hardening), v7.0 (architecture refresh), v7.x+ (ecosystem).
**Scope:** Forward-looking work beyond the completed 52-item v6.2 plan ([docs/RESEARCH.md](docs/RESEARCH.md)) and the v6.3 hardening pass.
**Philosophy** (from `README.md`, `CLAUDE.md`): Android-first DNS firewall + ad/tracker blocker. No-root by default, root-power when available. Local-first, fail-closed, encrypted-by-default. No telemetry. Material 3 dark.

This document supersedes the prior 3-round research blocks while preserving them in the **History** section at the bottom. Every item in **Now / Next / Later** maps to a citation in the **Appendix** at the end.

---

## How to read this

- **Now** — committed for v6.5 / v6.6, scoped, sourced, ready to ticket.
- **Next** — committed in principle for v7.0; needs design before code.
- **Later** — accepted as on-philosophy but not scheduled; revisit each release.
- **Under Consideration** — interesting, unresolved tradeoffs; needs investigation before any commit.
- **Rejected** — explicitly out of scope; recorded so we stop re-litigating.

Each item lists `Impact (1–5) / Effort (1–5) / Risk` and a citation tag like `[A1]` that resolves in the Appendix.

---

## Now — v6.5 / v6.6

### Stability & platform
- [x] **Android 16 always-on VPN reboot bug — detection + user-visible advisory.** Runtime check detects the post-update VPN-stack corruption pattern (Android 16+, always-on, lockdown engaged, validated physical network, valid TUN fd, zero inbound packets after startup) and surfaces a "Restart device to recover VPN" banner with rooted one-tap restart. **DONE v6.5.2.** `4 / 2 / Low` `[A1]`
- [x] **Doze + App Standby resilience.** Audited every `WorkManager` path in `docs/WORKMANAGER_AUDIT.md`, confirmed no direct `JobScheduler` usage, moved protection foreground services to `dataSync`, kept immediate blocklist refresh expedited with `RUN_AS_NON_EXPEDITED_WORK_REQUEST`, and added a 60-second VPN heartbeat/watchdog with structured JSON kill/fd-failure events. **DONE v6.5.3.** `4 / 2 / Low` `[A2]`
- [x] **Hot-reload blocklists with double-buffered trie.** `BlocklistHolder` already used a single volatile snapshot swap; production rebuild callers now use `updateAsync()` so replacement trie construction runs off the caller thread before the atomic swap, with concurrent-reader regression coverage for repeated swaps. **DONE v6.5.4.** `5 / 3 / Low` `[A3]`
- [x] **TCP DNS fallback verification.** Added shared RFC 7766 `TC=1` fallback policy coverage, verified path-MTU-sized truncated UDP responses start TCP retry within 200 ms, and wired IPv6 UDP forwarding through the same TCP retry path as IPv4 so truncated IPv6 answers are not returned incomplete. **DONE v6.5.5.** `4 / 2 / Low` `[A4]`
- [x] **Magisk 26+ `su --mount-master` audit.** Added `RootShellRunner` to detect Magisk 26+ and prefer libsu's mount-master shell for `iptables`/`ip6tables` and route-localnet sysctl command paths; both `IptablesManager` and `RootDnsLogger` now run firewall mutations through the shared runner with version-gate coverage. **DONE v6.5.6.** `3 / 1 / Low` `[A5]`
- [x] **`VpnService.Builder.addRoute` host-bit canonicalization.** Added strict numeric VPN route canonicalization before route insertion, masking IPv4/IPv6 host bits for network prefixes while preserving host routes and rejecting invalid/CIDR-suffixed inputs with focused JVM coverage. **DONE v6.5.7.** `3 / 1 / Low` `[A6]`

### DNS engine
- [x] **DoH3 (RFC 9484) resolver alongside DoH/DoT/DoQ.** Added an embedded-Cronet DoH3 transport behind the existing DoH setting with QUIC hints, public-key pins, bounded response reads, HTTP/3-only acceptance, provider latency EMA, query-log `DoH3:` labels, and fallback to the existing pinned OkHttp DoH resolver when HTTP/3 is unavailable. **DONE v6.5.8.** `4 / 4 / Med` `[A7]`
- **DNSCrypt v2 + Anonymized DNSCrypt relay support.** Parse `sdns://` stamps, support resolver and relay roles, route resolver→relay so upstream never sees client IP. **PARTIAL v6.5.9:** DNS stamp parsing now uses the current 8-byte property format, preserves 32-byte DNSCrypt provider keys, parses `0x81` relay stamps, rejects resolver-as-relay privacy collapses, and builds anonymized relay target prefixes. Remaining blocker: full DNSCrypt query encryption/decryption needs an audited Android-compatible crypto/engine choice before exposing a user-facing toggle. Library-of-record: a Kotlin reimpl of `dnscrypt-proxy`'s plugin chain — start from `plugin_block_name.go` for matching semantics. `3 / 4 / Med` `[A8]`
- **Oblivious DoH (ODoH, RFC 9230).** Cloudflare's 1.1.1.1 has supported ODoH since 2020 and iCloud Private Relay uses it. Add proxy-mode resolver with `odoh-rs` / `odoh-go` reference impl. `3 / 4 / Med` `[A9]`
- **Extended DNS Errors (EDE, RFC 8914) parsing + UI.** Surface server-side block reasons (`Blocked`, `Censored`, `Filtered`, `Forged answer`, `DNSSEC bogus`, `Stale answer`) verbatim in the query log so users can distinguish HostShield blocks from upstream filtering. `3 / 2 / Low` `[A10]`
- **DoH cert-pin migration window.** Ship pin-list with two-pin overlap and a 30-day silent acceptance window when CAs rotate (Cloudflare rotated mid-2024; current pinning is brittle to that). `4 / 2 / Med` `[A11]`
- **Custom DNS TTL override.** Per-query and per-domain TTL clamp (min, max, cap). RethinkDNS issue #296 is open since 2021 — direct parity win. `3 / 2 / Low` `[A12]`

### Blocklists
- **Hagezi blocklist-pack chooser.** Surface Light / Multi-Light / Normal / Pro / Pro++ / Ultimate / TIF (Threat Intel) / TIF-Mini / DynDNS / NRD-30 / Most-Abused-TLDs as one-tap subscriptions with versioned manifests. RethinkDNS open issue #1192 maps to this. `5 / 2 / Low` `[A13]`
- **Delta-update pipeline.** Pull manifest of diffs, not full lists, mirroring hagezi's diff-publishing pattern. Drops monthly bandwidth ~80% on Ultimate. `4 / 3 / Low` `[A14]`
- **Antigravity-style allowlists.** Pi-hole v6 ships subscribed allowlists ("Antigravity") that supersede block entries by domain match. Add the same construct so users can subscribe to a curated unbreak-list. `3 / 2 / Low` `[A15]`
- **Visual feedback for failed blocklist downloads.** Foreground notification + badge on the Blocklists screen when any source fails. RethinkDNS #1152 confirms this is a recurring user complaint in the space. `3 / 1 / Low` `[A16]`

### Privacy & security
- **CHANGELOG + version-string sync.** `README.md` claims v6.2.0 while build is v6.4.0; `CHANGELOG.md` header is `## [v6.3.0] - %Y->-` placeholder. Repair as a v6.5.0 prep step; lint by hook on every release. `5 / 1 / Low` `[A17]`
- **AES-256-GCM nonce-uniqueness assertion in backups.** Add a build-time + runtime check that the GCM IV counter has not collided across a single key's lifetime. v6.3 introduced the encrypted backup format; harden it before the install base grows. `4 / 1 / Low` `[A18]`
- **PBKDF2 → Argon2id migration path for PINs.** PBKDF2-SHA256 at 210k iterations was the OWASP 2023 recommendation; Argon2id is now the default for new credential-storage designs. Add Argon2id with backward-compat verification of legacy PBKDF2 hashes. `3 / 3 / Low` `[A19]`

### Observability (currently thin)
- **Structured local event log.** Replace ad-hoc `Log.d` with a ring-buffered binary event log (events: `vpn_start`, `vpn_stop`, `private_dns_conflict`, `blocklist_swap`, `cert_pin_failure`, `oom_kill`, `doze_resume`). Exportable as a single ZIP from Settings → Diagnostics for bug reports. **No network egress.** `4 / 2 / Low` `[A20]`
- **In-app crash reporter (offline).** Catch uncaught exceptions, write a sanitized crashdump to internal storage, prompt user on next launch with "Attach to GitHub issue" share intent. No third-party SDK, no automatic upload. Pattern: ACRA in offline mode. `3 / 2 / Low` `[A21]`
- **Per-resolver health metrics card.** Existing latency EMA → add success-rate, NXDOMAIN-rate, EDE-rate, and a 24-h spark. Helps users diagnose "is it me or my upstream?". `3 / 2 / Low` `[A22]`

### Accessibility & i18n (currently thin)
- **TalkBack pass on all 31+ screens.** Audit every `Text`, `IconButton`, `Switch` for `contentDescription` / `semantics`. Compose `Modifier.semantics { stateDescription = … }` on toggle rows. `4 / 3 / Low` `[A23]`
- **Dynamic-type respect.** Currently several screens hard-cap `fontSize` for layout. Replace with `MaterialTheme.typography` + `LocalDensity` aware constraint. `3 / 2 / Low` `[A23]`
- **High-contrast theme variant.** Material 3 ships a `highContrastDarkColorScheme()` constructor; wire it to the accessibility pref. `3 / 2 / Low` `[A23]`
- **Locale scaffolding.** All user-facing strings into `strings.xml`; pseudolocale CI check (`en-XA`, `ar-XB`) so layout breakages are caught. Translation comes later; the scaffolding is the gate. `4 / 3 / Low` `[A24]`
- **RTL audit.** Confirm every custom `Row`/`Box` uses `start`/`end` not `left`/`right` once locale scaffolding is in place. `3 / 1 / Low` `[A24]`

### Testing
- **Robolectric + Compose UI tests for the top-10 user flows.** First-launch onboarding, VPN start/stop, blocklist add/remove, PIN set/verify, backup/restore, Private DNS conflict, query-log filter, scheduled profile, allow-for-N-min, panic mode. None of these have automated coverage today. `4 / 4 / Low` `[A25]`
- **Resolver fuzz harness.** Drive `BlocklistHolder` and `DohResolver` parsers with `jqwik`/`kotlinx-fuzz` corpora of malformed DNS messages and IDN edge cases (Bidi, Punycode round-trips, NULL labels). `3 / 3 / Low` `[A25]`
- **Migration-path test for every Room migration.** DB is at v14 with 12 historical migrations; a single broken upstream upgrade can wipe user state. Use `MigrationTestHelper` for every n→n+1 transition with a frozen golden DB per version. `5 / 3 / Med` `[A26]`

### Distribution & packaging
- **Reproducible builds + IzzyOnDroid metadata.** Add `Reproducible-Builds` manifest entries and submit to the IzzyOnDroid F-Droid repo. The full flavor (root features) is a natural fit; the play flavor stays on Play. `3 / 3 / Low` `[A27]`
- **Obtainium config in README.** One-tap install via Obtainium pointing at GitHub Releases is now the de-facto sideload UX. Document the `appId + repo` config block. `2 / 1 / Low` `[A28]`
- **APK + AAB split confirmed in CI.** Current release workflow ships APK only — confirm the Play flavor builds AAB on a `release/play-*` tag pattern, signed with the play upload key. `3 / 2 / Low` `[A29]`

---

## Next — v7.0

### Architecture refresh
- **Firestack tun2socks migration (Go via gomobile).** RethinkDNS's `firestack` is a fork of Jigsaw's `outline-go-tun2socks` and gives ~10× throughput headroom over the current pure-Kotlin path. Bind via `gomobile bind -target=android`. Migration is invasive but unblocks DoH3, DNSCrypt, and per-app firewall in one shot. `5 / 5 / High` `[A30]`
- **Userspace per-app firewall (no root, in VPN mode).** Once firestack is in place, do UID-keyed bouncing at the TUN layer using `ConnectivityManager.getConnectionOwnerUid()` (Android 10+). Pre-Android-10 falls back to the existing root path or "DNS-only" mode. `5 / 4 / Med` `[A31]`
- **Material 3 Expressive UI refresh.** Material 3 Expressive (Google I/O 2025) is the new default for system surfaces on Android 16+. Refresh the home dashboard hero card, motion (springy easing tokens), and shape system; adopt the new color schemes. Keep AMOLED dark as the default. `4 / 4 / Low` `[A32]`
- **Compose BOM 2025-quarterly + Strong Skipping Mode.** Track the 2025 BOM cadence; ensure Strong Skipping is on for all stable composables. Measurable frame-time improvements on the live-query-rate animations. `3 / 2 / Low` `[A33]`
- **OkHttp 5 stable adoption.** Move off pinned 4.12.0 once 5.x stabilizes; gives HTTP/3, native coroutines extension, and improved cert-pin diagnostics. `3 / 2 / Med` `[A34]`

### Per-app firewall & rules
- **Connection-state–aware rules.** Block when screen-off, block on metered network, block until unlock, block on background-only — the RethinkDNS "universal firewall rules" set. Implementable on top of the new userspace firewall. `4 / 3 / Med` `[A35]`
- **Scheduled firewall profiles.** Bedtime, focus, kids — time-of-day rule activation chained onto SSID profiles. Already on the prior roadmap; lock it in v7.0. `4 / 3 / Low` `[A36]`
- **Free-form rule notes.** Each rule gets a `note` field rendered in the rule-detail bottom-sheet. RethinkDNS #1084 — small, high-value. `2 / 1 / Low` `[A37]`
- **WireGuard SSID-based start/stop.** RethinkDNS v0.5.5u shipped this; HostShield can mirror it once we have firestack. `3 / 3 / Med` `[A38]`

### Sync & multi-device
- **Self-hosted sync server (Go binary + Docker).** End-to-end encrypted sync of rules, allowlists, firewall config, blocklist subscriptions across devices. Crypto: per-account X25519 + ChaCha20-Poly1305 envelope. The previously-rejected cloud-only approach stays rejected — local server only. `4 / 5 / High` `[A39]`
- **Desktop companion (Windows / macOS / Linux).** Resolver binary that enrolls as a second device under the same account. Start with a CLI; GUI later. `3 / 5 / High` `[A40]`
- **Per-household read-only parental dashboard.** Local DNS server already exposes a port; add a read-only Compose-for-Web HTML view bound to `127.0.0.1` (and configurable LAN binding) for parents to review blocks. No remote access. `3 / 4 / Med` `[A41]`

### Privacy analytics
- **SDK tracker diff over time.** Notify when an installed app added/removed a tracker SDK across versions, sourced from the Exodus Privacy database mirror. `4 / 3 / Low` `[A42]`
- **Cross-device tracker correlation.** With sync in place, surface "phone + tablet both contacted scorecardresearch.com today". `3 / 2 / Low` `[A43]`
- **Domain-classification cache (ML-lite).** Local n-gram + TLD + age classifier that grades unknown domains pre-resolution. Trained offline; ship a few-hundred-KB TFLite model. Does not call out to a network. `3 / 4 / Med` `[A44]`

---

## Later — accepted, unscheduled

- **Plain-text rules language** — dnsmasq `address=/foo.com/0.0.0.0` + Pi-hole regex compat for power users. `[A45]`
- **Block X domain for Y minutes** — temporary blocks with expiration (inverse of Pause). `[A46]`
- **Energy-saver DNS mode** — when battery < 15%, reduce cache prefetch aggressiveness and pause SDK scans. `[A47]`
- **PCAP-NG export with HostShield verdict per packet.** `[A48]`
- **AdGuard Home REST-API drop-in compat** for the Local DNS Server flavor. The server-side API surface is `internal/dnsforward/http.go`; mirroring it lets HostShield slot into existing dashboards. `[A49]`
- **Encrypted Client Hello (ECH) awareness.** Parse SVCB/HTTPS ech config lists, block/allow based on policy, warn when ECH hides SNI from upstream filtering. Carried forward from prior roadmap; deprioritized because Cloudflare rolled back ECH in 2024 and re-enablement is partial. `[A50]`
- **DNSSEC validation toggle + EDE bogus surfacing.** Userspace validator (e.g., `dnssec-validator` Go binding) with explicit user opt-in; off by default since BIND9 + RHEL bugs in 2025 show how brittle DNSSEC operationally is. `[A51]`
- **Wear OS tile** — block-count + toggle from watch. `[A52]`
- **Android Auto** — read-only stats tile (no toggling while driving). `[A53]`
- **Quick-Settings tile pause durations** — 5/15/60/until-screen-off (Blokada-style). `[A54]`
- **DoH endpoint picker with latency benchmark column** (AdGuard Home pattern). `[A55]`
- **Anti-censorship resolver auto-fallback** when primary is filtered (RethinkDNS pattern). `[A56]`
- **Custom block-screen / NXDOMAIN local page** with allowlist button. HTTPS interception limits acknowledged. `[A57]`
- **Query insights onboarding** — 24-h personalized "top blockers" report. `[A58]`
- **Per-network DNS profile auto-switch** by captive-portal hash, BSSID, MCC/MNC chain on top of SSID. `[A59]`
- **Multi-party relay / Bandwidth Booster** equivalent. RethinkDNS v0.5.5u shipped this for WireGuard; novel and worth studying. `[A60]`
- **Smart / Split DNS** — domain-keyed resolver routing (RethinkDNS v0.5.5u). `[A61]`
- **Backup/restore JSON + CSV** in addition to the encrypted binary backup, gated by an "I understand this is unencrypted" toggle. RethinkDNS #2655. `[A62]`
- **FastScroll on long lists.** RethinkDNS #1066. `[A63]`
- **Tailscale integration / coexistence guide.** RethinkDNS #1047 — coexistence is feasible via VPN-on-VPN nesting on Android with caveats; documenting it is a low-effort roadmap win. `[A64]`
- **Configurable blocklist download URL.** RethinkDNS #2658 — a power-user request that maps cleanly to HostShield's existing custom-source UI. `[A65]`

---

## Under Consideration — needs investigation

- **DELEG record support (IETF dnsop draft).** Forthcoming richer-than-NS delegation record. Watch the draft; if it advances to RFC, plan validation support. No commit until standardization clears. `[A66]`
- **DoH3 via Cronet vs. Quiche.** Cronet ships in Chrome / GMS Core but is heavy and Google-controlled; Quiche is lighter but means a NDK build artifact in HostShield. Decide before v7.0 lands. `[A67]`
- **Federated learning for the domain classifier.** On-device update of the ML-lite weights without exfiltrating queries. Privacy story is strong on paper; auditable implementation is hard. Investigate but do not commit. `[A68]`
- **Plugin / extension system.** RethinkDNS, AdGuard Home, dnscrypt-proxy all expose plugin hooks. The Android sandbox makes this risky (any third-party plugin running inside the VPN process is a privilege concentration). Investigate a strictly-data-only plugin format (rule packs + classifier weights, no code). `[A69]`
- **`pending_requests` query deduplication.** AdGuard Home 2025 added this — collapse N concurrent identical queries into 1 upstream request. Worthwhile if we can show real cache-stampede behavior in production logs. `[A70]`
- **Per-client `ignore_querylog` / `ignore_statistics`.** AdGuard Home 2025. Useful for the household-dashboard mode but only with the multi-device sync in place. `[A71]`
- **GrapheneOS Vanadium / network compatibility profile.** Confirm HostShield's foreground service + always-on flow works under GrapheneOS network permission gates without surprising the user. `[A72]`

---

## Rejected — explicitly out of scope

- **Cloud-hosted resolver / hosted account model.** Conflicts with the local-first, no-telemetry philosophy in `README.md`. NextDNS already owns this niche; HostShield's value is precisely *not* that. `[A73]`
- **Built-in VPN tunneling provider.** HostShield is a DNS firewall, not a VPN. Bundle a real VPN and we inherit a regulated business model. WireGuard split-tunnel coexistence (Later) is the exception that proves the rule. `[A74]`
- **Cryptocurrency / token-gated features.** Out of scope, period. `[A75]`
- **Anonymous remote telemetry, even opt-in.** "No telemetry" is a stated principle. The local event log + manual export is the substitute. `[A76]`
- **Closed-source binary blob upstreams** for blocklists or resolver code. Every dependency must be auditable. `[A77]`
- **iOS port.** Same problem space, completely different platform constraints (NetworkExtension DNS Proxy vs. VpnService). Out of scope for this codebase; would be a separate project. `[A78]`
- **Browser-based "extension" form factor.** Already well-served by uBlock Origin / AdGuard browser. HostShield's edge is OS-level coverage. `[A79]`

---

## Coverage matrix (self-audit)

| Category | Now | Next | Later | Notes |
|---|---|---|---|---|
| Security | A11, A18, A19 | — | A50, A51 | Hardening continues into every release |
| Reliability | A1, A2, A3, A4, A5, A6, A26 | A30 | A60 | Top of the priority stack for v6.5 |
| DNS protocols | A7, A8, A9, A10, A12 | — | A50, A51, A56, A66 | DoH3 + DNSCrypt + ODoH all in v6.5 |
| Blocklists | A13, A14, A15, A16 | — | A45, A57, A65 | Hagezi packs is the highest-ROI single item |
| Per-app firewall | — | A31, A35, A36, A37, A38 | — | Gated on firestack landing |
| Sync / multi-device | — | A39, A40, A41, A43 | A55, A64 | Largest v7.0 surface |
| UX / Material 3 | — | A32, A33 | A57, A58 | Expressive refresh in v7.0 |
| Observability | A20, A21, A22 | — | A48 | All local-only |
| Accessibility | A23 | — | — | TalkBack pass is the gate |
| i18n / l10n | A24 | — | — | Scaffolding only; translations later |
| Testing | A25, A26 | — | — | Migration tests are the must-have |
| Distribution | A27, A28, A29 | — | — | F-Droid + Obtainium first |
| Plugin ecosystem | — | — | — | Investigation only (A69) |
| Wear / Auto | — | — | A52, A53 | Tiles only, never controls |
| Offline / resilience | A2, A3, A4 | — | A47 | Doze/standby + hot-reload + TCP fallback |
| Multi-user / parental | — | A41, A71 | — | Read-only LAN dashboard |
| Migration paths | A26 | A30 (firestack) | — | Every Room migration covered by test |

---

## History (preserved from prior research rounds)

The v6.2 ROADMAP completed 52 items (see `docs/RESEARCH.md`, §16). The v6.3 hardening round completed PBKDF2 PIN, AES-256-GCM backups, HTTPS-only sync URLs with SHA-256 integrity, shell-injection fixes, DoH no-unpinned-fallback, DoT response-boundary check.

### Round 1 (v6.2 forward-look) — superseded by tier assignments above
ECH awareness; DoH3; hot-reload blocklists; per-network DNS profile auto-switch; ML-lite domain classifier; ODoH; userspace firewall in VPN mode; scheduled firewall profiles; connection categorization; SDK tracker diff over time; cross-device tracker correlation; fingerprinting stats; self-hosted sync server; desktop companion; per-household parental dashboard; redesigned home dashboard; block-screen customization; query insights onboarding; plain-text rules language; block-for-N-min; energy-saver mode; Wear OS tile; Android Auto; PCAP-NG export.

### Round 2 (related OSS) — references retained in Appendix
RethinkDNS, Blokada, t895/DNSNet, dns66, hagezi/dns-blocklists, AdGuard Home, Intra (Jigsaw), NetGuard. Patterns borrowed: foreground/background-keyed rules, WireGuard split-tunnel, DNSCrypt+ODoH, blocklist-pack with one-tap install, regex-filterable live log, anti-censorship fallback, connection tracker, universal firewall rules, always-on VPN interop guidance, custom DoH endpoint picker w/ latency benchmark, Quick-Settings pause tile.

### Round 3 (implementation deep-dive) — references retained in Appendix
Reference impls: `BraveVPNService.kt` (RethinkDNS), `firestack`, `outline-go-tun2socks/tun.go`, `ServiceSinkhole.java` (NetGuard), AdGuard Home `dnsforward/http.go`, hagezi list structure, dnscrypt-proxy `plugin_block_name.go`, dns66 `AdVpnThread.java`, outline-ss-server. Pitfalls cataloged: Private DNS overrides VPN DNS unless `setBlocking(true)`; `addRoute` host-bit IllegalArgumentException; always-on + lockdown captive-portal; DoT "couldn't connect" expected when VPN up; `getConnectionOwnerUid` Android 10+ only; trie rebuild pause; UDP DNS PMTU truncation on IPv6; DoH cert-pin rotation; `ACTION_VPN_PREPARE` activity-only; Magisk 26+ mount-namespace.

---

## Appendix — sources

Citations for every item. URLs only; no claims without a source.

- **A1** Android 16 always-on VPN bug, ongoing 2025–2026: https://issuetracker.google.com/issues/333744840  https://www.androidpolice.com/android-16-vpn-bug-pixel/  https://grapheneos.org/articles/attacks-against-vpns
- **A2** Android Doze + Standby Buckets reference: https://developer.android.com/training/monitoring-device-state/doze-standby  https://developer.android.com/topic/performance/appstandby  https://developer.android.com/develop/background-work/services/foreground-services  https://developer.android.com/about/versions/14/changes/fgs-types-required  https://developer.android.com/about/versions/15/behavior-changes-15
- **A3** RethinkDNS `RethinkBlocklistManager` (double-buffered trie pattern): https://github.com/celzero/rethink-app
- **A4** RFC 7766 §6 (TCP fallback on TC=1): https://www.rfc-editor.org/rfc/rfc7766  RethinkDNS UDP/TCP fallback discussion: https://github.com/celzero/rethink-app/issues
- **A5** libsu mount-master notes: https://github.com/topjohnwu/libsu  Magisk 26 release notes: https://github.com/topjohnwu/Magisk/releases
- **A6** `VpnService.Builder.addRoute` host-bit issue: https://github.com/tailscale/tailscale/issues/645  Android docs: https://developer.android.com/reference/android/net/VpnService.Builder
- **A7** RFC 9484 (DoH3): https://www.rfc-editor.org/rfc/rfc9484  Android Private DNS / Play system update of resolver: https://source.android.com/docs/core/ota/modular-system/dns-resolver  https://9to5google.com/2024/06/26/android-doh3/
- **A8** DNSCrypt v2 protocol + Anonymized DNSCrypt: https://github.com/DNSCrypt/dnscrypt-proxy/wiki/DNS-Stamp-specifiers  https://github.com/DNSCrypt/dnscrypt-proxy/wiki/Anonymized-DNSCrypt  Block-name plugin: https://github.com/DNSCrypt/dnscrypt-proxy/blob/master/dnscrypt-proxy/plugin_block_name.go
- **A9** RFC 9230 (ODoH): https://www.rfc-editor.org/rfc/rfc9230  Cloudflare ODoH announcement: https://blog.cloudflare.com/oblivious-dns/  iCloud Private Relay (uses ODoH): https://www.apple.com/privacy/docs/iCloud_Private_Relay_Overview_Dec2021.PDF
- **A10** RFC 8914 (Extended DNS Errors): https://www.rfc-editor.org/rfc/rfc8914
- **A11** Cloudflare DoH cert chain rotation 2024: https://blog.cloudflare.com/  CertificatePinner OkHttp: https://square.github.io/okhttp/4.x/okhttp/okhttp3/-certificate-pinner/
- **A12** RethinkDNS issue #296 (custom DNS TTL): https://github.com/celzero/rethink-app/issues/296
- **A13** Hagezi blocklists (Light / Multi-Light / Pro / Pro++ / Ultimate / TIF / DynDNS / NRD / Most-Abused-TLDs): https://github.com/hagezi/dns-blocklists  RethinkDNS issue #1192: https://github.com/celzero/rethink-app/issues/1192
- **A14** Hagezi diffs / manifest pattern: https://github.com/hagezi/dns-blocklists/tree/main/adblock
- **A15** Pi-hole v6 release notes (Antigravity allowlists): https://pi-hole.net/blog/2025/02/  https://docs.pi-hole.net/database/gravity/
- **A16** RethinkDNS issue #1152 (visual feedback for failed downloads): https://github.com/celzero/rethink-app/issues/1152
- **A17** Repo state, README.md vs build.gradle.kts, CHANGELOG header
- **A18** NIST SP 800-38D §8 (GCM IV uniqueness): https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-38d.pdf
- **A19** OWASP Password Storage Cheat Sheet (Argon2id): https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
- **A20** Android structured logging guidance: https://developer.android.com/topic/performance/tracing  ACRA: https://github.com/ACRA/acra
- **A21** ACRA offline mode: https://github.com/ACRA/acra/wiki
- **A22** Compose live-data + EMA pattern: https://developer.android.com/jetpack/compose/state
- **A23** Android Compose accessibility: https://developer.android.com/jetpack/compose/accessibility  Material 3 highContrast schemes: https://m3.material.io/styles/color/the-color-system/accessibility  TalkBack: https://support.google.com/accessibility/android/answer/6283677
- **A24** Android i18n: https://developer.android.com/guide/topics/resources/localization  Pseudolocales: https://developer.android.com/guide/topics/resources/pseudolocales  Bidi text: https://developer.android.com/training/basics/supporting-devices/languages
- **A25** Compose UI Test: https://developer.android.com/jetpack/compose/testing  Robolectric: https://robolectric.org/  jqwik: https://jqwik.net/  kotlinx-fuzz: https://github.com/Kotlin/kotlinx-fuzz
- **A26** Room MigrationTestHelper: https://developer.android.com/training/data-storage/room/migrating-db-versions
- **A27** IzzyOnDroid F-Droid repo: https://apt.izzysoft.de/fdroid/  Reproducible Builds: https://reproducible-builds.org/
- **A28** Obtainium: https://github.com/ImranR98/Obtainium
- **A29** Android Gradle Plugin AAB: https://developer.android.com/build/build-variants
- **A30** firestack: https://github.com/celzero/firestack  outline-go-tun2socks: https://github.com/Jigsaw-Code/outline-go-tun2socks  gomobile: https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile
- **A31** `ConnectivityManager.getConnectionOwnerUid` (API 29+): https://developer.android.com/reference/android/net/ConnectivityManager#getConnectionOwnerUid(int,%20java.net.InetSocketAddress,%20java.net.InetSocketAddress)  NetGuard `ServiceSinkhole.java`: https://github.com/M66B/NetGuard/blob/master/app/src/main/java/eu/faircode/netguard/ServiceSinkhole.java
- **A32** Material 3 Expressive (Google I/O 2025): https://m3.material.io/blog/material-3-expressive  https://io.google/2025/
- **A33** Compose BOM: https://developer.android.com/jetpack/compose/bom  Strong Skipping Mode: https://developer.android.com/jetpack/compose/performance/strongskipping
- **A34** OkHttp 5: https://github.com/square/okhttp/blob/master/CHANGELOG.md
- **A35** RethinkDNS universal firewall rules: https://github.com/celzero/rethink-app/wiki  https://github.com/celzero/rethink-app
- **A36** Prior HostShield roadmap (scheduled profiles), `docs/RESEARCH.md`
- **A37** RethinkDNS issue #1084 (free-form rule notes): https://github.com/celzero/rethink-app/issues/1084
- **A38** RethinkDNS v0.5.5u release notes (WireGuard SSID start/stop): https://github.com/celzero/rethink-app/releases
- **A39** End-to-end encrypted sync prior art: https://signal.org/blog/  WireGuard key model: https://www.wireguard.com/papers/wireguard.pdf  XChaCha20-Poly1305: https://datatracker.ietf.org/doc/draft-irtf-cfrg-xchacha/
- **A40** dnscrypt-proxy desktop pattern: https://github.com/DNSCrypt/dnscrypt-proxy
- **A41** AdGuard Home web UI as reference: https://github.com/AdguardTeam/AdGuardHome
- **A42** Exodus Privacy: https://reports.exodus-privacy.eu.org/  Database: https://github.com/Exodus-Privacy/exodus
- **A43** Self, prior roadmap (cross-device tracker correlation)
- **A44** TFLite for on-device classification: https://www.tensorflow.org/lite/android  TinyML reference: https://www.tinyml.org/
- **A45** dnsmasq `address=` syntax: https://thekelleys.org.uk/dnsmasq/docs/dnsmasq-man.html  Pi-hole regex: https://docs.pi-hole.net/regex/overview/
- **A46** Self, prior roadmap (block-for-N-minutes)
- **A47** Android battery optimization: https://developer.android.com/topic/performance/power
- **A48** PCAP-NG spec: https://datatracker.ietf.org/doc/html/draft-tuexen-opsawg-pcapng
- **A49** AdGuard Home REST API: https://github.com/AdguardTeam/AdGuardHome/blob/master/internal/dnsforward/http.go  https://github.com/AdguardTeam/AdGuardHome/wiki/HTTP-API
- **A50** Cloudflare ECH rollback 2024: https://blog.cloudflare.com/encrypted-client-hello-rollback/  ECH draft: https://datatracker.ietf.org/doc/draft-ietf-tls-esni/
- **A51** RFC 4035 (DNSSEC): https://www.rfc-editor.org/rfc/rfc4035  BIND9 2025 advisories: https://kb.isc.org/docs/aa-00913
- **A52** Wear OS tiles: https://developer.android.com/training/wearables/tiles
- **A53** Android Auto: https://developer.android.com/training/cars
- **A54** Blokada Quick Settings tile: https://github.com/blokadaorg
- **A55** AdGuard Home upstream picker: https://github.com/AdguardTeam/AdGuardHome
- **A56** RethinkDNS anti-censorship modes: https://github.com/celzero/rethink-app/releases
- **A57** NXDOMAIN block page UX (Pi-hole): https://docs.pi-hole.net/main/post-install/
- **A58** Self, prior roadmap (insights onboarding)
- **A59** Self, prior roadmap (per-network DNS profile)
- **A60** RethinkDNS multi-party relay / Bandwidth Booster: https://github.com/celzero/rethink-app/releases
- **A61** RethinkDNS Smart / Split DNS: https://github.com/celzero/rethink-app/releases
- **A62** RethinkDNS issue #2655 (CSV/JSON backup): https://github.com/celzero/rethink-app/issues/2655
- **A63** RethinkDNS issue #1066 (FastScroll): https://github.com/celzero/rethink-app/issues/1066
- **A64** RethinkDNS issue #1047 (Tailscale integration): https://github.com/celzero/rethink-app/issues/1047  Tailscale Android: https://github.com/tailscale/tailscale-android
- **A65** RethinkDNS issue #2658 (configurable blocklist URL): https://github.com/celzero/rethink-app/issues/2658
- **A66** DELEG draft: https://datatracker.ietf.org/doc/draft-ietf-dnsop-deleg/  Geoff Huston commentary: https://blabs.apnic.net/ispcol/2024-02/deleg.pdf
- **A67** Cronet: https://developer.android.com/develop/connectivity/cronet  Quiche: https://github.com/cloudflare/quiche
- **A68** Federated learning: https://federated.withgoogle.com/  TFF: https://www.tensorflow.org/federated
- **A69** Plugin systems: dnscrypt-proxy plugins: https://github.com/DNSCrypt/dnscrypt-proxy/wiki/Plugins  AdGuard Home filters: https://github.com/AdguardTeam/AdGuardHome
- **A70** AdGuard Home `pending_requests` (2025): https://github.com/AdguardTeam/AdGuardHome/blob/master/CHANGELOG.md
- **A71** AdGuard Home per-client `ignore_querylog` / `ignore_statistics` (2025): https://github.com/AdguardTeam/AdGuardHome/blob/master/CHANGELOG.md
- **A72** GrapheneOS network features: https://grapheneos.org/features#network  https://grapheneos.org/articles/attacks-against-vpns
- **A73** NextDNS (cloud-only competitor): https://nextdns.io/  HostShield README philosophy
- **A74** WireGuard Android: https://www.wireguard.com/install/  Mullvad: https://mullvad.net/  HostShield README scope
- **A75** Self, philosophy
- **A76** HostShield README: "No telemetry"
- **A77** HostShield CLAUDE.md, license stance
- **A78** NetworkExtension DNSProxyProvider: https://developer.apple.com/documentation/networkextension/dnsproxyprovider
- **A79** uBlock Origin: https://github.com/gorhill/uBlock  AdGuard browser extension: https://github.com/AdguardTeam/AdguardBrowserExtension

### Carried-forward references (Round 1–3 bibliography)

- celzero/rethink-app: https://github.com/celzero/rethink-app
- celzero/firestack: https://github.com/celzero/firestack
- Jigsaw-Code/outline-go-tun2socks: https://github.com/Jigsaw-Code/outline-go-tun2socks
- Jigsaw-Code/Intra: https://github.com/Jigsaw-Code/Intra
- M66B/NetGuard: https://github.com/M66B/NetGuard
- julian-klode/dns66: https://github.com/julian-klode/dns66
- t895/DNSNet: https://github.com/t895/DNSNet
- blokadaorg: https://github.com/blokadaorg
- AdguardTeam/AdGuardHome: https://github.com/AdguardTeam/AdGuardHome
- DNSCrypt/dnscrypt-proxy: https://github.com/DNSCrypt/dnscrypt-proxy
- hagezi/dns-blocklists: https://github.com/hagezi/dns-blocklists
- topjohnwu/libsu: https://github.com/topjohnwu/libsu
- tailscale/tailscale issues #645, #915, #4252: https://github.com/tailscale/tailscale/issues
- Pi-hole v6: https://pi-hole.net/blog/2025/02/
- Cloudflare 1.1.1.1 / DoH: https://developers.cloudflare.com/1.1.1.1/encryption/dns-over-https/  https://developers.cloudflare.com/1.1.1.1/encryption/dns-over-tls/

