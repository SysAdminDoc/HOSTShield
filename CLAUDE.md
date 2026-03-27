# HostShield

## Overview
Modern, AMOLED-dark hosts-based ad blocker app for Android. Inspired by AdAway. v6.3.0. All 52 roadmap items in `docs/RESEARCH.md` are DONE.

## Tech Stack
- Kotlin, Jetpack Compose, Material 3
- Hilt for dependency injection
- Room + DataStore for persistence (+ EncryptedSharedPreferences via security-crypto)
- Coroutines + Flow for async, ViewModels + StateFlow for UI state
- OkHttp for source downloads + DoH resolver, libsu for root access
- Vico 2.0.1 (charts), Lottie (animations), Glance 1.1.1 (widgets), ZXing (QR codes)

## Key Architecture
- **AppPreferences** - v6.3: Facade over 6 domain-specific managers (BlockingPreferences, DnsPreferences, FirewallPreferences, SecurityPreferences, UiPreferences, SyncPreferences). All share a single HostShieldDataStore. Old code works through the facade; new code should inject specific managers.
- **PacketClassifier** - v6.3: Extracted from DnsVpnService. Singleton for IPv4/IPv6 packet classification (isIpv4UdpDns, isIpv6UdpDns, isIpv4TcpDns, isIpv6TcpDns).
- **SecureStore** - v6.3: PBKDF2-HMAC-SHA256 PIN hashing (210K iterations). `hashPin()` returns `"base64(salt):base64(hash)"`. `verifyPin()` for validation.
- **BlocklistHolder** - v6.3: Unified trie walk gathers all decision signals in single traversal. Priority: wildcard allow > (exact+trie+wildcard block) > regex. Hash set fast path (O(1) exact match before trie). Regex capped at 500 chars with ReDoS prevention.
- **DnsVpnService** - Local VPN DNS interception, TUN interface, dual-stack (IPv4+IPv6), DNS trap, DoH/DoT/DoQ/WireGuard encrypted DNS forwarding, TCP DNS RST. `forwardEncrypted()` dispatch: WireGuard > DoQ > DoT > DoH > UDP with automatic fallback. TLS fingerprinting (JA3/JA4) for both IPv4/IPv6 non-DNS TCP. v6.3: Packet classification delegated to PacketClassifier.
- **DohResolver** - RFC 8484 POST+GET, certificate pinning, smart latency-based failover (EMA per provider). v6.3: Fail-closed — no unpinned fallback.
- **DotResolver** - RFC 7858, TLSv1.3, SNI + hostname verification, 4 providers. v6.3: Response boundary check (12-4096 bytes).
- **DoqResolver** - v6.2: RFC 9250 DNS-over-QUIC. EXPERIMENTAL. Falls back to DoT.
- **WireGuardProxy** - v6.2: EXPERIMENTAL. Noise_IKpsk2 handshake, AES-256-GCM. v6.3: Nonce randomization (SecureRandom init).
- **HostsUpdateWorker** - Periodic blocklist refresh. v6.3: HTTPS-only sync URLs, 10MB size limit, SHA-256 integrity hashing.
- **ParentalControlManager** - v6.3: PBKDF2 PIN hashing (auto-migrates legacy SHA-256 on next login).
- **GeoIpLookup** - v6.3: Switched from ip-api.com (HTTP) to ipapi.co (HTTPS). OfflineGeoIp (MaxMind) preferred for new code.
- **BackupRestoreUtil** - v6.3: Optional AES-256-GCM encryption via BackupCrypto. Auto-detects encrypted vs plaintext.
- **RootUtil** - v6.3: Shell injection prevention — quoted all paths, replaced sed with Kotlin-side filtering.
- **CaptivePortalHandler** - Captive portal detection + VPN auto-pause/resume. Uses HTTP intentionally (not HTTPS) for portal redirect detection.

## Source Categories
- ADS, TRACKERS, MALWARE, ADULT, SOCIAL, CRYPTO, **ALLOWLIST**, CUSTOM

## Key Files
- `app/app/src/main/java/com/hostshield/service/DnsVpnService.kt` - VPN packet loop
- `app/app/src/main/java/com/hostshield/domain/BlocklistHolder.kt` - Trie + hash set + regex engine
- `app/app/src/main/java/com/hostshield/data/preferences/AppPreferences.kt` - Preferences facade
- `app/app/src/main/java/com/hostshield/data/preferences/SecureStore.kt` - PBKDF2 hashing
- `app/app/src/main/java/com/hostshield/data/database/Migrations.kt` - DB migrations (v5-v14)
- `app/app/src/main/java/com/hostshield/service/DohResolver.kt` - DoH (fail-closed)
- `app/app/src/main/java/com/hostshield/service/DotResolver.kt` - DoT (boundary checked)
- `app/app/src/main/java/com/hostshield/service/DoqResolver.kt` - DoQ (EXPERIMENTAL)
- `app/app/src/main/java/com/hostshield/service/WireGuardProxy.kt` - WireGuard (EXPERIMENTAL)
- `app/app/src/main/java/com/hostshield/service/DnsCache.kt` - LRU DNS cache + serve-stale + prefetch
- `app/app/src/main/java/com/hostshield/service/HostsUpdateWorker.kt` - Blocklist refresh (HTTPS-only sync)
- `app/app/src/main/java/com/hostshield/util/TrackerSignatureDb.kt` - APK tracker scanner (405 signatures)
- `app/app/src/main/java/com/hostshield/service/ThreatIntelManager.kt` - Threat intel feeds + radix trie
- `app/app/src/main/java/com/hostshield/ui/screens/settings/SettingsScreen.kt` - v6.3: Decomposed into section composables

## Screens (31+)
Home, Sources, Rules, Stats, Settings, Logs, Apps, AppPrivacy, AppLogs, Firewall (DNS/Network/Context tabs), ConnectionLog, DnsTools, NetworkStats, OverlapAnalysis, DnsLeakTest, RuleTest, HostsEditor, HostsDiff, AppExclusions, Onboarding, BlocklistGallery, AutomationAudit, ContentFilter, ParentalControls, DnsBenchmark, WebDavSync, CrashReports, QrConfig, TlsFingerprints

## Build
```bash
cd app
./gradlew assembleFullRelease  # Signed release (needs env vars or debug keystore fallback)
./gradlew assembleFullDebug    # Full flavor (root features)
./gradlew assemblePlayDebug    # Play Store flavor
```
- JDK: Android Studio bundled JBR at `/c/Program Files/Android/Android Studio/jbr`
- SDK: `$LOCALAPPDATA/Android/Sdk` (set in `app/local.properties`)
- Signing: env vars `KEYSTORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (falls back to debug keystore)
- Release keystore: `~/repos/HostShield/hostshield-release.keystore` (alias: hostshield, pass: hostshield123)

## CI/CD
- `.github/workflows/release.yml` -- triggers on tag push (`v*`) or `workflow_dispatch`
- `ubuntu-latest` runner, `working-directory: app`, `./gradlew assembleFullRelease`
- Secrets configured: `KEYSTORE_BASE64`, `KEY_ALIAS`, `KEY_PASSWORD`, `STORE_PASSWORD`

## Version History
- v6.3.0: Security hardening audit. PBKDF2 PIN hashing (auto-migrates SHA-256), encrypted backups (AES-256-GCM), DoH fail-closed (removed unpinned fallback), DoT response boundary check, HTTPS-only sync URLs (10MB limit + SHA-256 integrity), RootUtil shell injection fixes, WireGuard nonce randomization, GeoIP switched to ipapi.co (HTTPS). AppPreferences facade over 6 domain managers. PacketClassifier extraction from DnsVpnService. BlocklistHolder unified trie walk. SettingsScreen decomposed into section composables. SettingsViewModel consolidated flows. DB v14 (composite indices for dns_logs, host_sources, user_rules). Error/loading states on Logs/Firewall/Sources. Search history chips. Accessibility content descriptions.
- v6.2.0: DoQ (RFC 9250), WireGuard DNS proxy, 7 new screens (ContentFilter, ParentalControls, DnsBenchmark, WebDavSync, CrashReports, QrConfig, TlsFingerprints), TLS fingerprinting (JA3/JA4), release hardening audit (operator precedence fixes across 6 files, OkHttp leak fixes in 8 files, security patches). 52/52 roadmap items complete.
- v6.1.0: Per-app DNS rules, content filtering (12+ categories), proxy mode (no-VPN), QR config, parental controls (PIN lock), crash reporter, WebDAV sync, connection tracker, Vico charts, Glance widgets, Lottie animations.
- v6.0.0: Threat intel integration, NetworkTrackerDb (200+ domains), DoT, local DNS server, safe search, encrypted backups, DNS stamps, schedule presets, DNS benchmark. DB v11.
- v5.2.0: Expanded tracker DB (405 signatures, 8 categories), threat intel feeds, captive portal handling.
- v5.0.0: Adblock-syntax parser, two-tier DNS cache (L1+L2), serve-stale (RFC 8767), hash set fast path, offline GeoIP (MaxMind), CNAME cloak DBs. DB v9.
- v4.x: Latency sparkline, query type charts, per-app drill-down, DNS cache UI, notification pause, CNAME badge, bug audits, anomaly detection, firewall export, automation API.
- v3.x: Blocklist gallery, tracker scanning, privacy report, scheduling, leak test, widgets, DoH, CI/CD.
- v2.x: Automation API, iptables firewall, DoH, DNS trap.

## Research & Roadmap
- Full competitive analysis: `docs/RESEARCH.md` (30+ open-source projects, 52-item roadmap across 6 phases)
- **All 52 roadmap items COMPLETE** as of v6.2.0. v6.3.0 is hardening/quality.

## Gotchas
### Build
- `gradlew` lives in `app/` not repo root -- CI uses `working-directory: app`
- Vico 2.0 axes: `VerticalAxis.rememberStart()` / `HorizontalAxis.rememberBottom()` (import from `...axis.rememberStart/rememberBottom`)
- Vico 2.0 CartesianValueFormatter: `{ _, value, _ -> }` (first param is context, second is value)
- Glance ColorProvider: `ColorProvider(Color(0xFF...))` single arg, NOT `ColorProvider(day=, night=)`
- FlowRow needs `@OptIn(ExperimentalLayoutApi::class)` per composable function
- `collectAsStateWithLifecycle` needs explicit import from `androidx.lifecycle.compose`
- SecretKeyFactory method is `generateSecret()` NOT `generateSecretKey()`
- Google Tink/errorprone annotations need `-dontwarn` ProGuard rules (for EncryptedSharedPreferences)
- `.gitignore` has `/com/` (root-only) -- plain `com/` would block app source code

### Runtime
- BlockMethod enum has DNS_PROXY -- all exhaustive `when(BlockMethod)` must include it
- `isActive` in viewModelScope.launch is ambiguous with Flow -- use `currentCoroutineContext().isActive`
- WgConfig takes ByteArray keys (Base64.decode from prefs), not raw strings
- AppPreferences field is `dohEnabled` not `useDoH`
- DnsProxyService/LocalDnsServer `forwardToUpstream` must be `suspend` for DoH calls
- KDoc brackets `[...]` can cause KSP parse errors

### Database
- DB version is 14. Migration chain: v1-v5 inline in DatabaseModule, v5-v14 in Migrations.kt
- v12->v13: composite indices for dns_logs, host_sources, user_rules
- v13->v14: index on host_sources.category
- Room stores enums as strings -- adding new enum values doesn't need migration

### Security
- DoH is fail-closed (no unpinned fallback) -- if all pinned providers fail, resolution fails
- DoT response boundary check: 12-4096 bytes
- Sync URLs: HTTPS-only, 10MB limit, SHA-256 integrity hashing
- PIN: PBKDF2 (210K iterations). Legacy SHA-256 hashes auto-migrate on next verification
- WireGuard nonces: randomly initialized (SecureRandom), not starting at 0
- RootUtil: all shell paths quoted, no sed (Kotlin-side filtering)
- CaptivePortalHandler: HTTP is intentional (TLS would break portal redirect detection)
- OkHttp responses MUST be closed -- `response.use{}` or try-finally
- WireGuardProxy.encryptTransport() returns null on failure -- never fall back to plaintext
