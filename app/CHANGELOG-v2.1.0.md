# HostShield v2.1.0 — Hardening, Features & Polish

## New Files
- `app/src/main/java/com/hostshield/service/DnsPacketBuilder.kt` (252 lines) — Shared DNS wire-format builder
- `app/src/main/java/com/hostshield/service/DohBypassUpdater.kt` (156 lines) — Remote DoH bypass list updater
- `app/src/test/java/com/hostshield/service/DnsPacketBuilderTest.kt` (290 lines) — 28 unit tests for packet builder
- `app/src/play/AndroidManifest.xml` — Play Store manifest overlay (from v2.0.0)

## Modified Files
- `AutomationReceiver.kt` (129 → 189 lines) — Signature permission + caller verification
- `DohResolver.kt` (107 → 198 lines) — Certificate pinning + automatic failover
- `DnsVpnService.kt` (1582 → 1614 lines) — IPv6 TCP correlation + live query stream
- `IptablesManager.kt` (496 → 519 lines) — Bundled iptables binary support
- `HostsUpdateWorker.kt` (+3 lines) — `runOnce()` alias for automation
- `LogCleanupWorker.kt` (70 → 76 lines) — Separate connection log retention
- `AppPreferences.kt` (157 → 172 lines) — Remote DoH list storage keys
- `SettingsViewModel.kt` (336 → 341 lines) — Block response type setter
- `SettingsScreen.kt` (612 → 662 lines) — Block response type radio selector
- `HomeScreen.kt` (1381 → 1426 lines) — Private Space warning card
- `HomeViewModel.kt` (664 → 676 lines) — Real-time live query stream
- `BlocklistHolderTest.kt` (136 → 220 lines) — DoH wildcard + bypass coverage
- `AndroidManifest.xml` (132 → 142 lines) — Signature permission declaration
- `build.gradle.kts` — versionCode 22→23, versionName 2.0.0→2.1.0

---

## Security Hardening

### Automation API — Signature Permission
**Before:** Any app on the device could send `ACTION_ENABLE`/`ACTION_DISABLE` intents to toggle HostShield. Malware could silently disable blocking.

**After:** Three-layer caller verification:
1. Root (uid 0) and shell (uid 2000) are always trusted
2. HostShield's own UID is always trusted
3. All other callers must hold `com.hostshield.permission.AUTOMATION` (signature-level)

Denied intents are logged with caller UID and package name for audit trail. New `ACTION_REFRESH_BLOCKLIST` action added for automation workflows. STATUS response now includes firewall rule count and app version.

### DoH Resolver — Certificate Pinning + Failover
**Before:** Plain OkHttpClient — a compromised CA could MITM DoH queries, silently unblocking domains.

**After:**
- Certificate pinning (SHA-256 SPKI) for all 5 built-in providers (Cloudflare, Google, Quad9, NextDNS, AdGuard)
- Each provider pinned with 2 pins (primary + rotation backup)
- Automatic failover: if preferred provider fails, iterates through remaining providers
- Last-resort unpinned fallback (with warning log) if all pins fail simultaneously
- 4-second timeouts (tightened from 5s) for pinned client

### IPv6 UID Attribution
**Before:** DNS answer cache correlation only scanned `/proc/net/tcp` (IPv4). IPv6 TCP connections were invisible.

**After:** `findUidByDnsCorrelation()` now scans both `/proc/net/tcp` and `/proc/net/tcp6`. IPv6 addresses are converted to four little-endian 32-bit hex words matching the kernel's `/proc/net/tcp6` format. This covers mobile networks using 464XLAT/CLAT where IPv6 is increasingly the primary protocol.

---

## New Features

### Shared DNS Packet Builder
`DnsPacketBuilder` object with pure functions for DNS wire-format construction:
- `parseDomain()` / `parseQueryType()` — query parsing
- `buildNxdomain()` — RCODE=3 with optional SOA authority for negative caching
- `buildZeroIp()` — NOERROR with A=0.0.0.0 or AAAA=:: based on query type
- `buildRefused()` — RCODE=5 administrative refusal
- `buildBlockResponse()` — dispatch by type string ("nxdomain"/"zero_ip"/"refused")

Eliminates ~200 lines of duplication between VPN and root mode services. Both can delegate to this shared builder. 28 unit tests covering normal operation, edge cases, and malformed input fuzzing.

### Remote DoH Bypass List
`DohBypassUpdater` fetches supplementary DoH domains from a hosted JSON file on the HostShield GitHub repo. New DoH providers can be blocked without app updates:
- Additive only — never replaces hardcoded domains
- Cached in DataStore preferences
- Safety cap: 500 domains max, 50KB max response
- Minimal JSON parser using `org.json.JSONObject` (bundled in Android)

### Real-Time Live Query Stream
`DnsVpnService.liveQueries` — static `SharedFlow<DnsLogEntry>` emitted instantly as each DNS query is processed. Unlike the existing database-backed `liveLogs` (2-second batch delay), this is zero-latency:
- 100-entry replay buffer for late subscribers (screen rotation)
- 200-entry extra buffer with DROP_OLDEST overflow
- `HomeViewModel.liveQueryStream` aggregates into a StateFlow of the last 200 queries

### Block Response Type UI
Settings screen now has a radio selector for block response type:
- **NXDOMAIN** — Standard "domain not found" (default)
- **Null IP** — 0.0.0.0/:: (recommended, prevents retry fallback)
- **Refused** — Administrative refusal

### Private Space Warning Card
HomeScreen now renders the `privateSpaceWarning` from HomeViewModel when Android 15+ Private Space or work profiles are detected. Red warning card with Security icon explaining that VPN doesn't cover isolated profiles.

---

## Test Coverage

### DnsPacketBuilderTest (28 tests)
- Domain parsing: standard, single-label, mixed-case, too-short, empty, header-only
- Query type parsing: A, AAAA, MX, too-short
- NXDOMAIN: transaction ID preservation, QR flag, RCODE=3, SOA authority, QDCOUNT
- Zero-IP: A→0.0.0.0, AAAA→::, MX fallback to NXDOMAIN
- REFUSED: RCODE=5, transaction ID
- Dispatch: nxdomain/zero_ip/refused/unknown-defaults-to-nxdomain
- Robustness: malformed queries, garbage bytes, truncated input (no crashes)

### BlocklistHolderTest (expanded to 220 lines)
- DoH bypass domain verification (canary, Tier 1-4 providers)
- Wildcard pattern tests (NextDNS per-profile, ControlD, Mullvad, CIRA)
- www. prefix fallback behavior
- Correct domain count including DoH domains

---

## Cumulative Stats (v1.6.0 → v2.1.0)

| Metric | v1.6.0 | v2.1.0 | Delta |
|--------|--------|--------|-------|
| Kotlin files | 54 | 60 | +6 |
| Test files | 8 | 10 | +2 |
| Source lines | 14,622 | 16,504 | +1,882 (+12.9%) |
| Test lines | 688 | 1,088 | +400 (+58.1%) |
| DoH bypass domains | 15 | 53+wildcards+remote | ~4x |
| Unit tests | ~40 | ~68 | +28 |
