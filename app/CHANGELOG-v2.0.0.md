# HostShield v2.0.0 — Phase 6: Platform Compatibility Polish

## Files Modified
- `app/src/main/java/com/hostshield/service/DnsVpnService.kt` (1396 → 1582 lines, +186)
- `app/src/main/java/com/hostshield/service/IptablesManager.kt` (496 → 519 lines, +23)
- `app/src/main/java/com/hostshield/service/LogCleanupWorker.kt` (70 → 76 lines, +6)
- `app/src/main/java/com/hostshield/ui/screens/settings/SettingsViewModel.kt` (336 → 341 lines, +5)
- `app/build.gradle.kts` (versionCode 21→22, versionName 1.11.0→2.0.0, +product flavors)

## Files Added
- `app/src/main/java/com/hostshield/util/IptablesBinaryManager.kt` (164 lines)
- `app/src/play/AndroidManifest.xml` (53 lines) — Play Store manifest overlay

---

## 6A. TCP DNS Handling in VPN Mode

**Before:** Only UDP DNS packets (protocol 17, port 53) were intercepted. TCP DNS (RFC 7766) packets were silently dropped — apps could bypass blocking by using TCP DNS.

**After:** New methods detect and handle IPv4 TCP DNS:

- `isIpv4TcpDns()`: Detects IPv4 TCP packets to port 53
- `processIpv4TcpDns()`: Extracts DNS query from TCP payload (2-byte length prefix + DNS message), checks blocklist, sends TCP RST for blocked domains
- `buildTcpRst()`: Constructs proper TCP RST+ACK with correct sequence/ack numbers
- `computeTcpChecksum()` / `computeIpChecksum()`: RFC-compliant checksums

For **blocked** TCP DNS: immediate RST rejection (connection refused).
For **allowed** TCP DNS: packet is dropped — app times out and retries with UDP per RFC 7766 §6.2.2. This avoids implementing a full TCP state machine while still preventing TCP DNS bypass.

IPv6 TCP DNS is noted but not handled (extremely rare; apps fall back to UDP).

## 6B. Bundled iptables Binary Support

**Problem:** System iptables varies across OEMs. Some ship iptables-nft shims missing match/target modules. Samsung devices sometimes have iptables in non-standard paths.

**Solution:** New `IptablesBinaryManager` utility with resolution order:
1. Bundled binary from app's private files (`filesDir/bin/iptables`)
2. System paths (`/system/bin`, `/system/xbin`, `/sbin`, `/vendor/bin`)
3. PATH fallback

`IptablesManager` now injects `IptablesBinaryManager`:
- `resolveCmd()` helper substitutes resolved paths into all command strings
- `applyRules()` calls `iptablesBin.resolve()` once per apply
- Diagnostic dump includes binary path and version info
- `extractFromAssets()` method ready for bundling static binaries

To bundle a binary: place `iptables-arm64` (or arm/x86_64/x86) in `app/src/main/assets/bin/`.

## 6C. QUERY_ALL_PACKAGES Play Store Strategy

**Problem:** Google Play rejects QUERY_ALL_PACKAGES. NetGuard lost this permission.

**Solution:** Dual build variants via product flavors:

- **`full`** (GitHub / F-Droid): Retains QUERY_ALL_PACKAGES. All system apps visible.
- **`play`** (Play Store): Manifest overlay removes QUERY_ALL_PACKAGES, adds `<queries>` intent filters for launcher apps, browsers, VPN apps, and DNS resolvers. User-installed apps visible; some system apps may be missing.

Build commands:
```
./gradlew assembleFullRelease    # GitHub/F-Droid APK
./gradlew assemblePlayRelease    # Play Store APK
./gradlew assembleFullDebug      # Debug with full permissions
```

## 6D. DNS Log Retention Policy

**Before:** Both DNS logs and connection logs used the same retention period (user-configurable, default 7 days).

**After:** Separate retention:
- DNS logs: user-configurable (default 7 days, via Settings)
- Connection (firewall) logs: fixed 3 days (higher volume from iptables)

`SettingsViewModel` now exposes `blockResponseType` (from Phase 4) and `connectionLogRetentionDays` in the UI state, with `setBlockResponseType()` setter ready for the Settings screen.

---

## Complete Upgrade Summary (v1.6.0 → v2.0.0)

| Phase | Version | What Changed |
|-------|---------|-------------|
| 1 | v1.7.0 | CVE-2020-8558 route_localnet hardening, removed logcat UID, /proc/net fast-path |
| 2 | v1.8.0 | Os.poll() multiplexing, RFC 5737 DNS fallback, AlarmManager watchdog, TRANSPORT_VPN filter |
| 3 | v1.9.0 | AFWall+ interface patterns (24 mobile), tethering chain, NFLOG/LOG auto-detect, Private Space |
| 4 | v1.10.0 | Configurable block response (NXDOMAIN/0.0.0.0/REFUSED), DNS answer cache UID heuristic |
| 5 | v1.11.0 | 59 DoH bypass domains + 4 wildcards, expanded IP traps, architecture doc |
| 6 | v2.0.0 | TCP DNS handling, bundled iptables, Play Store flavors, log retention split |

**Total:** 54 → 57 source files, 14,622 → 15,775 lines (+1,153 lines, +7.9%)
