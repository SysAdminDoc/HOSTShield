# Memory Consolidation

Research date: 2026-05-17

## Instruction Inventory

| Source | Git tracked | Status | Notes |
|---|---:|---|---|
| `AGENTS.md` | No, ignored | Current pointer | Points to repo `CLAUDE.md` and global shared memory. No conflict with repo code. |
| `CLAUDE.md` | No, ignored | Current working notes | Current through v6.5.9 and is the best repo-local working memory. |
| `C:/Users/--/.claude/CLAUDE.md` | External | Global behavior | Requires roadmap auto-continue, RTK command preference, no GUI pill backdrops, and known-issues memory usage. |
| `C:/Users/--/CLAUDE.md` | External | Global project rules | Requires repo CLAUDE read, recent git log, stack memory, auto commit/push, build verification, and doc sync. |
| Claude shared memory `MEMORY.md` | External | Partially stale | Lists HostShield as active project and points to `hostshield.md`. |
| Claude shared memory `hostshield.md` | External | Stale | Frozen at v6.3.0; useful for historical release/build context only. |
| Codex memory `MEMORY.md` | External | Narrowly useful | Contains a v6.5.1 signed release and adb reinstall flow. |

## Canonical Facts Extracted To `PROJECT_CONTEXT.md`

- HostShield is Android-first, local-first, no-telemetry DNS firewall/ad/tracker blocker.
- Current version truth is `app/app/build.gradle.kts`: `versionName = "6.5.9"`, `versionCode = 67`.
- Current DB version is 14.
- DoH is fail-closed, not fail-open.
- Tracker signature DB has 405 static tracker signatures.
- Online GeoIP fallback uses ipapi.co over HTTPS.
- DoQ and WireGuard are experimental simplified implementations.
- DNSCrypt stamp parsing and relay route planning exist, but full DNSCrypt crypto is not implemented.
- Full flavor is the GitHub/F-Droid-style artifact; Play flavor has package-visibility tradeoffs.

## Stale Or Contradictory Claims

| Claim | Location | Current evidence | Resolution |
|---|---|---|---|
| Current roadmap baseline is v6.4.0. | Old `ROADMAP.md` | Build and changelog are v6.5.9. | New roadmap baseline is v6.5.9. |
| DoH has unpinned fallback as last resort. | `README.md` | `DohResolver.kt` logs fail-closed refusal to downgrade. | Treat README line as stale. Add doc-sync roadmap item. |
| Tracker SDK scanner has about 60 signatures. | `README.md` | `TrackerSignatureDb.kt` has 405 `TrackerInfo` entries. | Treat README line as stale. Add doc-sync roadmap item. |
| GeoIP fallback is ip-api.com. | `README.md` | `GeoIpLookup.kt` uses `https://ipapi.co/$ip/json/`. | Treat README line as stale. Add doc-sync roadmap item. |
| v6.5 prep still needs version-string sync. | Old `ROADMAP.md` | `CHANGELOG.md`, README badge, and build version agree on v6.5.9. | Remove as active roadmap work. |
| Shared Claude HostShield memory is current. | `hostshield.md` | It says v6.3.0. | Use only as historical memory. |

## Open Conflicts

No unresolved instruction conflict blocks work.

One process tension remains: global project rules prefer not committing AI working files, while this user request explicitly requires `.ai/research/<date>/` artifacts and `PROJECT_CONTEXT.md`. The direct repository task wins for this research session, and the generated files are intentionally committed as durable project artifacts.

## What Future Sessions Should Read First

1. `PROJECT_CONTEXT.md`
2. `ROADMAP.md`
3. `.ai/research/2026-05-17/STATE_OF_REPO.md`
4. `.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md`
5. `CLAUDE.md` if available locally
6. `app/app/build.gradle.kts`
7. `app/app/src/main/java/com/hostshield/service/DnsVpnService.kt`
8. `app/app/src/main/java/com/hostshield/domain/BlocklistHolder.kt`
