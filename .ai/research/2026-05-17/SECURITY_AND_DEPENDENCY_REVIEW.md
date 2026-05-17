# Security And Dependency Review

Research date: 2026-05-17

## Security Posture

Strengths verified in source:

- DoH is fail-closed and refuses unpinned downgrade in `DohResolver.kt`.
- DoH/DoT responses are bounded before parser handoff.
- Backup encryption uses AES-256-GCM with 600K PBKDF2-HMAC-SHA256 iterations in `BackupCrypto.kt`.
- PIN hashing uses PBKDF2-HMAC-SHA256 and constant-time decoded-byte compare in `SecureStore.kt`.
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
| AGP | 8.7.3 | Stable but behind current release cadence; plan a controlled refresh. |
| Kotlin | 2.1.0 | Good Compose-era baseline; watch 2.2+ compatibility before upgrade. |
| Compose BOM | 2024.12.01 | Behind current 2025/2026 BOM cadence. |
| Room | 2.6.1 | Mature but update candidate after migration tests are in place. |
| WorkManager | 2.10.0 | Modern; keep aligned with Android foreground/background behavior. |
| Hilt | 2.53.1 | Update candidate after KSP/Kotlin compatibility check. |
| OkHttp | 4.12.0 | Stable but OkHttp 5.x is current per changelog; evaluate after DoH pin tests. |
| Cronet embedded | 143.7445.0 | Large embedded network surface; keep version refresh in dependency audit. |
| libsu | 6.0.0 | Current release observed; root behavior still needs device validation. |
| Tink Android | 1.21.0 | Direct dependency for legacy secure-pref migration only; new secure writes use local Android Keystore AES-GCM wrapper. |
| Vico | 2.0.1 | UI dependency; test chart API on upgrade. |
| Lottie | 6.6.2 | UI dependency; low security criticality. |
| Glance | 1.1.1 | Widget dependency; update with Compose stack. |
| ZXing | 3.5.3 | QR dependency; watch CVE feed but low exposure. |
| MaxMind GeoIP2 | 4.2.1 | Offline lookup dependency; update with tests. |
| org.json | 20240303 test only | Test-only. |

## Immediate Hardening Recommendations

1. Add a `./gradlew dependencyUpdates` equivalent or Gradle version-catalog review script that produces a markdown dependency report.
2. Validate local security store migration on a device seeded with pre-v6.6 encrypted WebDAV, WireGuard, and parental PIN values.
3. Add backup IV collision defense: store recent backup key-id/IV tuples or assert uniqueness per exported backup stream.
4. Add DoH pin manifest tests: current pin set, backup pin, expiry/rotation metadata, fail-closed behavior, and user-visible diagnostics.
5. Gate DNSCrypt, DoQ, and WireGuard user exposure behind explicit engine maturity states until audited or replaced.
6. Add parser fuzz harnesses for DNS packets, DNS stamps, hosts/adblock syntax, backup import, and blocklist update files.
7. Add Room golden migration tests for every v1->v14 transition before changing schema again.
8. Add release provenance: APK SHA-256, signing cert fingerprint, reproducible-build metadata, and GitHub release asset verification.

## Advisories And Watchlist

- Android platform security bulletins: monitor before each release.
- Android foreground service and VPN policy changes: monitor Android 15/16 docs and behavior.
- OkHttp 5.x: evaluate once DoH pinning and HTTP/3 behavior are covered.
- Cronet: update cadence matters because it embeds Chromium networking.
- libsu/Magisk: keep mount-master/root command behavior tests current.
- AndroidX Security: removed from app dependencies; keep the legacy migration bridge covered until enough releases have carried users onto `hostshield_secure_store_v2`.
