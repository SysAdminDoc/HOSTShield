# HostShield Architecture: DNS-Only vs Full-Traffic VPN

## Decision: DNS-Only Interception (Retained)

HostShield uses **DNS-only route interception** (`/32` routes to virtual DNS addresses + known public DNS IPs) rather than routing all traffic (`0.0.0.0/0`) through the TUN interface. This document explains why.

---

## The DoH Bypass Problem

Any app that implements its own DNS-over-HTTPS (DoH) client can bypass HostShield's DNS filtering by sending HTTPS requests directly to a DoH endpoint (e.g., `https://dns.google/dns-query`). Since HostShield only intercepts DNS traffic (UDP/TCP port 53 to known IPs), HTTPS traffic on port 443 to arbitrary DoH endpoints flows outside the TUN entirely.

This is a fundamental limitation of DNS-only VPN architecture, shared by DNS66, AdAway (VPN mode), and personalDNSfilter.

## Why We Stay DNS-Only

### Full-traffic mode costs

Routing `0.0.0.0/0` through TUN means **every packet** on the device passes through our userspace code:

| Concern | DNS-Only | Full-Traffic |
|---------|----------|-------------|
| Battery attribution | Minimal (DNS only) | **All network usage attributed to HostShield** in battery stats — #1 user complaint for NetGuard/RethinkDNS |
| CPU overhead | ~0.1% (DNS packets only) | 2-5% continuous (copying every packet through TUN) |
| App compatibility | None (transparent) | VPN-sensitive apps break (banking, streaming DRM, corporate VPN) |
| Latency | Zero for non-DNS traffic | +0.5-2ms per packet (userspace round-trip) |
| Code complexity | ~1,100 lines (DNS parsing) | ~4,000+ lines (full IP stack, TCP state tracking, connection table) |
| Maintenance | Stable (DNS protocol is simple) | Ongoing (new protocols, QUIC, ECH, etc.) |

### DNS-only mitigation effectiveness

HostShield's layered defense covers the vast majority of real-world bypass:

1. **DoH domain blocking** (~65 domains + 4 wildcard patterns in `BlocklistHolder.dohBypassDomains`)
   - Blocks resolution of DoH endpoint hostnames
   - Firefox canary (`use-application-dns.net`) NXDOMAIN disables Firefox DoH
   - Covers Google, Cloudflare, Quad9, AdGuard, NextDNS, OpenDNS, CleanBrowsing, Mullvad, ControlD, DNS.SB, DNS0.eu, and 20+ more

2. **DoH IP routing** (~24 IPs in `DOH_BYPASS_IPS`)
   - Routes known DoH endpoint IPs through TUN
   - Drops HTTPS (port 443) traffic to these IPs
   - Supplementary to domain blocking (IPs rotate, domains don't)

3. **DNS Trap** (~14 IPs in `DNS_TRAP_IPS`)
   - Routes hardcoded public DNS IPs (8.8.8.8, 1.1.1.1, etc.) through TUN
   - Captures apps that bypass system DNS settings

4. **DoT port blocking**
   - DNS-over-TLS on port 853 to known resolver IPs is dropped
   - Forces fallback to port 53 where we filter

5. **Zero-IP block response** (Phase 4)
   - Returns `0.0.0.0`/`::` instead of NXDOMAIN
   - Prevents DNS retry behavior that could trigger alternate resolver fallback

### What bypasses this

Only apps that meet ALL of these conditions can bypass HostShield:

1. Implement their own DoH client (not system DNS)
2. Use a DoH endpoint not in our domain blocklist
3. Connect to a DoH IP not in our IP trap list
4. OR hardcode the DoH endpoint IP (no DNS resolution needed)

In practice, this means:
- **Chrome/Firefox**: Covered (canary domain + known endpoints)
- **Most Android apps**: Use system DNS resolver → fully covered
- **Malware with custom DoH**: Can bypass (but this is an adversarial threat model beyond DNS blocking)
- **Self-hosted DoH**: Can bypass (no way to enumerate)

### The competition agrees

| App | Architecture | DoH Strategy |
|-----|-------------|-------------|
| DNS66 | DNS-only | Domain blocking only |
| AdAway (VPN) | DNS-only | Domain blocking only |
| personalDNSfilter | DNS-only | Domain blocking + IP blocking |
| **HostShield** | **DNS-only** | **Domain blocking + IP blocking + canary + wildcard** |
| NetGuard | Full-traffic | Can inspect SNI |
| RethinkDNS | Full-traffic | Can inspect SNI + QUIC |

NetGuard and RethinkDNS pay the battery/complexity cost for marginally better DoH coverage. Their approach also breaks with ECH (Encrypted Client Hello) which hides SNI.

## Revisiting This Decision

Consider full-traffic mode if:
- Android adds a system API for per-app DNS policy (eliminating the VPN requirement)
- ECH + QUIC adoption makes domain blocking ineffective for >20% of traffic
- A lightweight kernel-level packet filter becomes available without root (unlikely)

Until then, the DNS-only approach with comprehensive domain/IP blocking provides the best tradeoff of effectiveness, battery life, compatibility, and maintainability.

---

*Last updated: v1.11.0 (Phase 5)*
