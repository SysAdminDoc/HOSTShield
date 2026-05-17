# Security And Dependency Review

Research date: 2026-05-17

## Security Posture

Strengths verified in source:

- DoH is fail-closed and refuses unpinned downgrade in `DohResolver.kt`.
- DoH/DoT responses are bounded before parser handoff.
- New backup encryption uses AES-256-GCM with Argon2id-derived keys in `BackupCrypto.kt`; PBKDF2-HMAC-SHA256 remains for v1 backup imports.
- New PIN hashing uses Argon2id records through `PasswordKdf`; PBKDF2-HMAC-SHA256 remains for legacy PIN verification and upgrade.
- Backup exports guard AES-GCM key/IV reuse with `BackupNonceLedger`, which tracks recent Argon2id parameter/salt/IV tuples and refuses duplicate generated exports.
- `data_extraction_rules.xml` excludes both legacy and v2 secure-store prefs from device transfer.
- `AndroidManifest.xml` sets `usesCleartextTraffic="false"`.
- Automation receiver is protected by a signature permission.
- Root command hardening has a shared `RootShellRunner`.
- Room database has explicit migration chain and no destructive fallback.

High-risk areas:

- `DoqResolver.kt` is explicitly a simplified DoQ implementation without full TLS 1.3/QUIC stack behavior.
- `WireGuardProxy.kt` is explicitly simplified and uses AES-GCM rather than WireGuard's ChaCha20-Poly1305 transport.
- DNSCrypt crypto is not implemented; only stamp/route groundwork exists.
- AndroidX `EncryptedSharedPreferences` / `MasterKeys` usage has been replaced in runtime code. `SecureStore` now writes to a local Android Keystore AES-GCM wrapper; Tink is retained only to read legacy AndroidX keysets during migration.
- Room schema exports are incomplete for historical versions, which limits migration verification.
- README security claims drift from source truth, which can cause unsafe support assumptions.

## Dependency Snapshot

| Area | Current | Review |
|---|---|---|
| Gradle | 9.5.1 | Updated wrapper and wrapper jar/scripts in the dependency refresh batch. |
| AGP | 9.2.1 | Updated; AGP 9 built-in Kotlin support means `org.jetbrains.kotlin.android` is no longer applied. |
| Kotlin Compose plugin | 2.3.21 | Updated with KSP 2.3.8; Kotlin 2.3 warnings are tracked for cleanup. |
| Compose BOM | 2026.05.00 | Updated. |
| Room | 2.8.4 | Updated runtime, KTX, compiler, and testing together. |
| WorkManager | 2.11.2 | Updated. |
| Hilt | 2.59.2 | Updated with AndroidX Hilt 1.3.0. |
| OkHttp | 5.3.2 | Updated; compile warnings identify non-null `ResponseBody` cleanup sites. |
| Cronet embedded | 143.7445.0 | Large embedded network surface; keep version refresh in dependency audit. |
| libsu | 6.0.0 | Current release observed; root behavior still needs device validation. |
| Tink Android | 1.21.0 | Direct dependency for legacy secure-pref migration only; new secure writes use local Android Keystore AES-GCM wrapper. |
| Bouncy Castle bcprov-jdk18on | 1.84 | Lightweight Argon2id implementation for new PIN hashes and backup KDF records. |
| Vico | 2.5.0 | Updated within 2.x; Vico 3.1.0 was rejected for this batch due package/API moves. |
| Lottie | 6.7.1 | Updated. |
| Glance | 1.1.1 | Widget dependency; update with Compose stack. |
| ZXing | 3.5.4 | Updated. |
| MaxMind GeoIP2 | 5.1.0 | Updated; accessor deprecations need follow-up cleanup. |
| org.json | 20240303 test only | Held; no newer stable metadata checked in this batch. |

## Immediate Hardening Recommendations

1. Keep `.ai/research/2026-05-17/DEPENDENCY_REFRESH_REPORT.md` current or replace it with a scripted `./gradlew dependencyUpdates` equivalent.
2. Validate local security store and KDF migration on a device seeded with pre-v6.6 encrypted WebDAV, WireGuard, parental PIN, and encrypted backup values.
3. Add DoH pin manifest tests: current pin set, backup pin, expiry/rotation metadata, fail-closed behavior, and user-visible diagnostics.
4. Gate DNSCrypt, DoQ, and WireGuard user exposure behind explicit engine maturity states until audited or replaced.
5. Add parser fuzz harnesses for DNS packets, DNS stamps, hosts/adblock syntax, backup import, and blocklist update files.
6. Add Room golden migration tests for every v1->v14 transition before changing schema again.
7. Add release provenance: APK SHA-256, signing cert fingerprint, reproducible-build metadata, and GitHub release asset verification.

## Advisories And Watchlist

- Android platform security bulletins: monitor before each release.
- Android foreground service and VPN policy changes: monitor Android 15/16 docs and behavior.
- OkHttp 5.x: evaluate once DoH pinning and HTTP/3 behavior are covered.
- Cronet: update cadence matters because it embeds Chromium networking.
- libsu/Magisk: keep mount-master/root command behavior tests current.
- AndroidX Security: removed from app dependencies; keep the legacy migration bridge covered until enough releases have carried users onto `hostshield_secure_store_v2`.
