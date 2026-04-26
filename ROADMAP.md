# HostShield Roadmap

Forward features for HostShield (v6.2+) beyond the completed 52-item v6.2 roadmap. Focus: deeper protocol coverage, smarter detection, and cross-device sync.

## Planned Features

### DNS engine
- **Encrypted Client Hello (ECH)** awareness — parse SVCB/HTTPS ech config lists, block/allow based on policy, warn when ECH hides SNI from upstream filtering
- **DNS-over-HTTP/3 (DoH3)** resolver alongside DoQ, measured and ranked by the same latency EMA
- **Hot-reload blocklists** — diff-apply new source drops without rebuilding the trie (currently ~1-2s pause on large reloads)
- **Per-network DNS profile auto-switch** — chain onto existing SSID-profile logic: attach profile by captive portal hash, BSSID, or mobile carrier MCC/MNC
- **Domain classification cache** — local ML-lite classifier (domain n-grams + TLD + age) that grades unknown domains before they hit upstream, reducing leakage during cold-cache startup
- **Oblivious DoH (ODoH, RFC 9230)** — route through proxy so upstream DoH never sees client IP

### Per-app / firewall
- **Userspace firewall for VPN mode** — per-app block/allow without needing root, by bouncing UIDs at the TUN layer
- **Scheduled firewall profiles** — "bedtime": block social apps from 10pm-6am, "focus": block streaming during work hours
- **Connection categorization** — label outbound flows (CDN / analytics / ad / API / first-party) using reverse-DNS + ASN + domain class

### Privacy analytics
- **SDK tracker diff over time** — show when an app added or removed a tracker SDK across versions, with notification
- **Cross-device tracker correlation** — if Android and browser both hit the same tracker domain, surface the cross-join
- **Fingerprinting canvas / font / WebRTC stats** where available from client-hello fingerprints

### Sync & multi-device
- **Self-hosted sync server** (Go binary + Docker) — end-to-end encrypted sync of rules, allowlists, firewall config across phones/tablets
- **Desktop companion** — Windows/macOS/Linux resolver binary that enrolls as a second "device" under the same account
- **Per-household parental dashboard** — optional read-only web UI hosted on the local DNS server port for parents to review blocks

### UX
- **Redesigned Home dashboard** — consolidate the live query rate + cache sparkline + latency chart into one animated hero card
- **Block-screen customization** — when a page fails due to NXDOMAIN, display a custom local page with allowlist button (HTTPS interception limitations noted)
- **Query insights onboarding** — after 24h, show a personalized report: top blocked apps, worst offenders, privacy score delta

## Competitive Research

- **NextDNS** — cloud-based, multi-device sync, ECS controls. Gap: cloud-only, no on-device kernel or iptables integration. Takeaway: copy the cross-device config sync UX via self-hosted backend.
- **RethinkDNS + Firewall** — VPN-only, strong per-app rules, excellent DoH/DoT/DoQ support. Takeaway: match its DoH3 coverage and steal its "firewall on DNS" unified log UX.
- **AdGuard Home** — server-side only (Pi-hole competitor). Opportunity: HostShield's "Local DNS Server" mode already bridges this niche — extend with AdGuard Home-compatible REST API for drop-in replacement.
- **Blokada 6 / DNS66** — simple, no root. Takeaway: nail the onboarding flow and private DNS conflict detection HostShield already has.

## Nice-to-Haves

- **Plain-text rules language** — dnsmasq-style `address=/foo.com/0.0.0.0` + Pi-hole regex compat for power users
- **"Block X domain for Y minutes"** — temporary blocks with expiration (inverse of the existing pause feature)
- **Energy-saver DNS mode** — when battery < 15%, reduce cache prefetch aggressiveness and pause tracker SDK scans
- **Wear OS tile** — block count and toggle from watch
- **Android Auto integration** — stats tile only (no toggling while driving)
- **Export to PCAP-NG with metadata** — one-tap PCAP that includes per-packet HostShield verdict (blocked/allowed/cached)

## Open-Source Research (Round 2)

### Related OSS Projects
- **RethinkDNS (celzero/rethink-app)** — https://github.com/celzero/rethink-app — DNS + Firewall + VPN for Android 6+; VPN/DNS/Firewall tri-mode; DoH/DoT/DNSCrypt/ODoH + WireGuard split-tunnel; per-app block rules keyed on screen/foreground state
- **Blokada (blokadaorg/five-android)** — https://github.com/blokadaorg — DNS adblocker + VPN; clean lifecycle; Blokada 5 for Android
- **t895/DNSNet** — https://github.com/t895/DNSNet — DNS-based host blocker, ships curated host lists, good lean reference
- **julian-klode/dns66** — https://github.com/julian-klode/dns66 — oldest of the generation, very small codebase; good for learning the VPNService DNS intercept pattern
- **hagezi/dns-blocklists** — https://github.com/hagezi/dns-blocklists — Light/Normal/Pro/Pro++/Ultimate/TIF/DynDNS/Badware Hoster lists; low false-positive curation
- **AdGuard Home (adguardteam/adguardhome)** — https://github.com/AdguardTeam/AdGuardHome — self-hosted network-wide blocker; reference API surface (DoH, DoT, client rules)
- **Intra (Jigsaw)** — https://github.com/Jigsaw-Code/Intra — the ancestor of RethinkDNS; canonical tun2socks-Go VPN architecture
- **NetGuard** — https://github.com/M66B/NetGuard — no-root firewall via local VPN; per-app rules

### Features to Borrow
- Per-app firewall keyed on foreground vs background state, screen on/off (RethinkDNS)
- WireGuard split-tunnel so a user can run HostShield AND a real VPN (RethinkDNS) — solves the "Android one-VPN limit" pain
- DNSCrypt and ODoH support in addition to DoH/DoT (RethinkDNS)
- Blocklist-pack system with one-tap install of hagezi's Light/Pro/Ultimate/TIF packs, versioned (RethinkDNS + hagezi)
- Live DNS query log with regex filter, per-domain "allow for 5 min" (RethinkDNS)
- Anti-censorship resolver selector with automatic fallback if primary is blocked (RethinkDNS)
- Connection tracker: show which app made which request and whether it was blocked (NetGuard, RethinkDNS)
- "Universal firewall rules": block all incoming-only apps, block until screen on, block on metered network (RethinkDNS)
- Always-on VPN interop guidance and explicit opt-in warning flow (dns66, Blokada)
- Custom DoH endpoint picker with latency benchmark column (AdGuard Home client pattern)
- Quick-Settings tile for "Pause for 5 / 15 / 60 min" (Blokada style)

### Patterns & Architectures Worth Studying
- Go-based firestack (tun2socks) fork of outline-go-tun2socks for maximum perf and portability (RethinkDNS)
- VPNService as the interception mechanism on non-root Android — all DNS/firewall apps converge on this pattern (dns66, NetGuard, Blokada, RethinkDNS)
- Cloud-delivered resolver config (sky.rethinkdns.com on Cloudflare Workers) for the default path, while still letting users bring their own (RethinkDNS)
- Blocklist as a delta-update pipeline: app pulls a manifest of diffs, not full lists, to minimize data (hagezi publishes diffs)
- Per-app policy evaluation chain: app → domain → IP → time/schedule → connectivity-type; first-match wins (RethinkDNS, NetGuard)

## Implementation Deep Dive (Round 3)

### Reference Implementations to Study
- **celzero/rethink-app/app/src/main/java/com/celzero/bravedns/service/BraveVPNService.kt** — https://github.com/celzero/rethink-app — canonical Android VPNService DNS-trap implementation with per-app UID-based policy. Template for HostShield's VPN-mode userspace firewall roadmap item.
- **celzero/firestack** — https://github.com/celzero/firestack — Go-based tun2socks fork of Jigsaw-Code/outline-go-tun2socks. Direct swap-in for HostShield's DNS-intercept pipeline (current impl is pure Kotlin; firestack gives ~10x throughput headroom).
- **Jigsaw-Code/outline-go-tun2socks/tun2socks/tun.go** — https://github.com/Jigsaw-Code/outline-go-tun2socks/blob/master/tun2socks/tun.go — upstream lwIP-based TUN loop showing canonical packet-read/packet-inject pattern. Firestack is a fork; this is simpler to read first.
- **M66B/NetGuard/app/src/main/java/eu/faircode/netguard/ServiceSinkhole.java** — https://github.com/M66B/NetGuard/blob/master/app/src/main/java/eu/faircode/netguard/ServiceSinkhole.java — pure-Java no-root firewall via local VPN. Reference for HostShield's "Userspace firewall for VPN mode" roadmap item.
- **AdguardTeam/AdGuardHome/internal/dnsforward/http.go** — https://github.com/AdguardTeam/AdGuardHome/blob/master/internal/dnsforward/http.go — REST API surface for drop-in compat. Template for exposing HostShield's Local DNS Server as AdGuard Home-compatible endpoint.
- **hagezi/dns-blocklists** — https://github.com/hagezi/dns-blocklists — Light/Normal/Pro/Pro++/Ultimate/TIF lists. Already referenced; the `adblock/` and `domains/` directory structure + per-list metadata header is worth copying for HostShield's blocklist-pack system.
- **DNSCrypt/dnscrypt-proxy/dnscrypt-proxy/plugin_block_name.go** — https://github.com/DNSCrypt/dnscrypt-proxy/blob/master/dnscrypt-proxy/plugin_block_name.go — canonical wildcard/exact/regex matching + pattern_matcher. Compare against HostShield's `BlocklistHolder` for edge cases (IDN, case folding).
- **julian-klode/dns66/app/src/main/java/org/jak_linux/dns66/vpn/AdVpnThread.java** — https://github.com/julian-klode/dns66/blob/master/app/src/main/java/org/jak_linux/dns66/vpn/AdVpnThread.java — minimal VPN DNS intercept reference; small enough to read in one sitting for understanding the packet-flow.
- **outline/outline-ss-server** — https://github.com/Jigsaw-Code/outline-ss-server — not directly relevant but shows how Go-based network libraries are packaged for Android via gomobile. Useful if migrating HostShield's crypto to Go.

### Known Pitfalls from Similar Projects
- **System Private DNS (Android 9+) overrides VPN DNS unless `setBlocking(true)` + DNS-server explicitly set** — tailscale#915 — HostShield must detect Private DNS = "dns.cloudflare.com" etc. and show a user prompt to either disable it or switch to "Automatic". Currently HostShield has conflict detection; verify it warns on Android 14+. https://github.com/tailscale/tailscale/issues/915
- **`VpnService.Builder.addRoute` IllegalArgumentException on Android 11+** — tailscale#645 — adding route with non-zero host bits fails. Mask the IP first: `InetAddresses.forString(ip).hostAddress` → canonicalize. https://github.com/tailscale/tailscale/issues/645
- **Always-on VPN + lockdown mode blocks captive-portal detection** — HostShield's `CaptivePortalHandler` auto-pauses but Android 11+ `VpnService.Builder.setMetered(false)` is required to whitelist captive-portal traffic without dropping the VPN. Verify captive-portal URLs are in the `addDisallowedApplication` list via `resolveActivity`.
- **DoT "couldn't connect" on Android settings page when VPN is up** — tailscale#4252 — system evaluates Private DNS against the VPN's tunnel, not the physical interface. User-facing guidance: "Private DNS shown as unavailable is expected when HostShield is running; DNS is intercepted locally." https://github.com/tailscale/tailscale/issues/4252
- **`ConnectivityService.getConnectionOwnerUid()` only works on Android 10+** — for UID-based per-app firewall. Pre-Android-10 requires `/proc/net/tcp` parsing (root-only). HostShield's `rootless` flavor must gate firewall behind Android 10+.
- **Blocklist trie rebuild pauses DNS for 1-2s on large updates** — call out in HostShield roadmap. Solution: double-buffer the trie (build new atomically, swap reference). Pattern in RethinkDNS's `RethinkBlocklistManager`.
- **UDP DNS path MTU issue on IPv6 — fragments lost on some carriers** — RethinkDNS issues — always enable TCP DNS fallback when UDP packet > 512 bytes or reply is truncated. HostShield has TCP-DNS RST; verify fallback direction.
- **DoH certificate pinning breaks when CA rotates** — Cloudflare rotated their chain in 2024. Ship pin-list with a silent 30-day migration window (accept old+new).
- **`ACTION_VPN_PREPARE` must be requested from an Activity, not Service** — HostShield's VPN-start flow must always route through `MainActivity`; tile-service starts must use `PendingIntent` to activity, not direct service start.
- **Root libsu shell commands break on Magisk 26+ namespace-sandboxing** — iptables rules applied in one namespace don't apply to others. HostShield's root firewall mode must declare `su --mount-master` or rules silently no-op on Magisk 26+. https://github.com/topjohnwu/libsu/issues

### Library Integration Checklist
- **Firestack (tun2socks Go library)** — no Maven; build via `gomobile bind -target=android ./...` on the firestack fork. Entry: `Tun.Connect(tunFd, fakeDns="10.111.222.3", blocker=IBlocker{...})`. Gotcha: firestack requires a `VpnService.Builder.addAddress("10.111.222.1/24")` + `setMtu(1500)` exactly — any MTU mismatch triggers silent packet drops.
- **Quad9 DoH / Cloudflare DoH** — no Maven; raw OkHttp. Entry: `OkHttpClient.Builder().certificatePinner(CertificatePinner.Builder().add("1.1.1.1", "sha256/...").build()).build()` + POST to `https://1.1.1.1/dns-query` with `application/dns-message` body. Gotcha: must set `Accept: application/dns-message` or Cloudflare returns JSON; DoH RFC 8484 requires wire-format + correct Content-Type.
- **OkHttp WebSocket (for ODoH relay)** — `com.squareup.okhttp3:okhttp:4.12.0` (already pinned) — entry: `OkHttpClient.newWebSocket(Request, WebSocketListener)`. Gotcha: WebSocket pings default to 0s (disabled); set `pingInterval(30, SECONDS)` or connection silently idles out after carrier NAT timeout (~5min).


