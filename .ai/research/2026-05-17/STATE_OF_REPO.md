# State Of Repo

Research date: 2026-05-17
Repository: `C:\Users\--\repos\HostShield`
Baseline: `main` at `9494e0b` / tag `v6.5.9`

## Git State

- `git status --short --branch`: clean `main...origin/main` before research edits.
- Recent commits:
  - `9494e0b` `feat: add DNSCrypt relay route planning`
  - `d3e2d22` `feat: add DoH3 transport`
  - `51c93f9` `fix: canonicalize VPN routes`
  - `176df2f` `fix: harden Magisk firewall shell`
  - `60c9395` `test: verify TCP DNS fallback`
  - `1f327fd` `feat: async blocklist snapshot reloads`
  - `8e89532` `feat: harden VPN doze resilience`
  - `2918342` `feat: add Android 16 VPN recovery advisory`
  - `c7c40b1` `Improve HostShield action accessibility`
  - `d8e83c7` `Confirm destructive HostShield actions`
- Latest tags observed: `v6.5.9`, `v6.5.1`, `v6.5.0`, `v6.4.0`, `v6.3.0`, `v6.2.0`.

## Repo Shape

- Android project under `app/`; Gradle wrapper is in `app/`, not repo root.
- Kotlin app module path: `app/app`.
- Public docs: `README.md`, `CHANGELOG.md`, `ROADMAP.md`, `docs/RESEARCH.md`, `docs/WORKMANAGER_AUDIT.md`.
- Ignored tool notes: `AGENTS.md`, `CLAUDE.md`.
- Static local blocklists live in `blocklists/`.
- Curated remote blocklist gallery lives at `app/app/src/main/assets/curated_blocklists.json`.

## Build And Config

Evidence: `app/app/build.gradle.kts`, `app/build.gradle.kts`, `app/settings.gradle.kts`, `gradle.properties`, `.github/workflows/release.yml`.

- App version: `6.5.9`, versionCode `67`.
- Android Gradle Plugin: `8.7.3`.
- Kotlin plugin: `2.1.0`.
- compileSdk/targetSdk: `35`.
- minSdk: `26`.
- Java/Kotlin target: `17`.
- Product flavors:
  - `full`: full visibility/root feature flavor.
  - `play`: `applicationIdSuffix = ".play"` with Play policy tradeoffs.
- Release build enables R8 and resource shrinking.
- Release signing reads `KEYSTORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, with debug-keystore fallback for local builds.
- GitHub release workflow builds `assembleFullRelease`, renames `HostShield-v<version>-full-release.apk`, and uploads it to a tag release.

## Source Metrics

- 169 Kotlin files total.
- 151 main Kotlin files.
- 18 JVM test Kotlin files.
- 0 Android instrumentation Kotlin files observed.
- Main Kotlin lines: about 40,822.
- JVM test lines: about 2,442.
- Main package distribution:
  - `service`: 49 Kotlin files.
  - `ui`: 44 Kotlin files.
  - `util`: 32 Kotlin files.
  - `data`: 20 Kotlin files.
  - `domain`: 3 Kotlin files.
  - `di`: 1 Kotlin file.

## Core Runtime Surfaces

- `DnsVpnService.kt`: central VPN DNS interception, traps common DNS/DoH/DoT bypass paths, handles encrypted upstream dispatch, heartbeat/watchdog, app attribution, and live query flow.
- `BlocklistHolder.kt`: exact set + trie + wildcard + regex block engine with snapshot swaps and decision cache.
- `DohResolver.kt`: pinned DoH with DoH3-first path, failover, response size bounds, and fail-closed behavior.
- `Doh3Resolver.kt`: embedded-Cronet HTTP/3 transport.
- `DotResolver.kt`: DNS-over-TLS resolver.
- `DoqResolver.kt`: explicitly experimental simplified DoQ.
- `WireGuardProxy.kt`: explicitly experimental simplified WireGuard DNS-only tunnel.
- `DnsStampParser.kt`: DNS stamp parser/encoder including DNSCrypt, DoH, DoT, DoQ, ODoH, and relay stamps.
- `DnsCryptRoutePlanner.kt`: relay route validation and relay-prefix builder.
- `HostsUpdateWorker.kt`: blocklist refresh.
- `IptablesManager.kt`, `RootDnsService.kt`, `RootDnsLogger.kt`, `RootShellRunner.kt`: root mode.
- `SecureStore.kt`, `BackupCrypto.kt`, `EncryptedBackup.kt`: local secrets and encrypted backups.

## Database State

Evidence: `HostShieldDatabase.kt`, `Migrations.kt`, `DatabaseModule.kt`, schema export directory.

- Room DB version: 14.
- Entities: sources, user rules, DNS logs, block stats, profiles, firewall rules, connection logs, tracker scan cache, app DNS rules, automation audit, VPN stability.
- Migrations exist from v1 through v14.
- Destructive fallback is intentionally absent.
- Exported schemas observed only for `7.json`, `8.json`, `9.json`, `12.json`, `14.json`.
- Highest-risk gap: no complete golden migration test matrix covering every supported upgrade transition.

## Local Blocklist State

- Static blocklists: 22 files.
- Largest static list: `ipv4.txt` with 45,213 non-comment entries.
- `Whitelist.txt`: 1,451 non-comment entries.
- Curated asset gallery: 7 categories and 44 lists.
- Current gallery trails the external Hagezi and OISD landscape; Hagezi tier packs and diff-aware update workflows are a clear opportunity.

## Test State

Observed JVM test classes:

- `BlocklistHolderTest`
- `HostsParserTest`
- `DnsCacheTest`
- `DnsPacketBuilderTest`
- `DnsPacketParserTest`
- `DnsTcpFallbackTest`
- `Doh3ResolverTest`
- `DnsCryptRoutePlannerTest`
- `PacketClassifierTest`
- `VpnRouteCanonicalizerTest`
- `Android16VpnRecoveryDetectorTest`
- `DnsStampParserTest`
- `BackupRestoreUtilTest`
- `ImportExportUtilTest`
- `PcapExporterTest`
- `RootShellRunnerTest`
- `ConvertersTest`
- `FormatBytesTest`

Coverage is strongest around recently hardened protocol and utility seams. It is thin for Compose flows, Room migration upgrades, full VPN service behavior, root firewall command generation under device variance, and end-to-end backup/import flows.

## Confirmed Drift

- `README.md` says DoH certificate pinning has an "unpinned fallback as last resort", but `DohResolver.kt` fails closed and logs that it refuses unpinned downgrade.
- `README.md` says tracker scanning has about 60 signatures, but `TrackerSignatureDb.kt` contains 405 `TrackerInfo` entries.
- `README.md` still says online GeoIP fallback is ip-api.com; current `GeoIpLookup.kt` uses `https://ipapi.co`.
- `ROADMAP.md` baseline still says v6.4.0 even though build/docs are v6.5.9.
- `ROADMAP.md` contains a completed v6.5.0 doc/version-sync item as if it were still active.
- Top-level `app/build.gradle.kts` still has comment `HostShield v1.6.0`; executable version truth is in `app/app/build.gradle.kts`.

## Reconnaissance Conclusion

HostShield is already beyond a basic hosts blocker. The next roadmap should stop treating v6.2 research items as active implementation backlog and focus on four concrete risks:

1. Experimental protocol code that needs audited engines before user exposure.
2. Diagnostics and reproducible evidence collection without network telemetry.
3. Upgrade and parser safety tests for a complex local-first security app.
4. Documentation and dependency drift that can mislead release, support, or future implementation agents.
