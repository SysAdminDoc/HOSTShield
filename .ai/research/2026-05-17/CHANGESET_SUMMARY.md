# Changeset Summary

Research date: 2026-05-17

## Files Created

- `PROJECT_CONTEXT.md` - canonical consolidated project context for future sessions.
- `.ai/research/2026-05-17/STATE_OF_REPO.md` - local repository reconnaissance memo.
- `.ai/research/2026-05-17/MEMORY_CONSOLIDATION.md` - instruction and memory reconciliation.
- `.ai/research/2026-05-17/SOURCE_REGISTER.md` - local, memory, and external source index.
- `.ai/research/2026-05-17/RESEARCH_LOG.md` - research process, queries, failed searches, and saturation notes.
- `.ai/research/2026-05-17/COMPETITOR_MATRIX.md` - competitor and adjacent project comparison.
- `.ai/research/2026-05-17/FEATURE_BACKLOG.md` - raw opportunity backlog.
- `.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md` - scored and tiered candidate matrix.
- `.ai/research/2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md` - dependency, advisory, and hardening review.
- `.ai/research/2026-05-17/DATASET_MODEL_INTEGRATION_REVIEW.md` - data/model/integration review.

## Files Modified

- `ROADMAP.md` - replaced stale v6.4/v6.5 roadmap with a v6.5.9 source-keyed plan grounded in this research run.

## Roadmap Implementation Update

- `README.md` and `app/README.md` - repaired v6.5.9 documentation drift for DoH fail-closed pinning, 405 tracker SDK signatures, ipapi.co GeoIP fallback, Kotlin 2.1, Android SDK 35, and Room v1-v14 migration wording. Public docs now also surface the unresolved root/app license-file conflict instead of asserting one canonical license.
- `tools/check-release-docs.ps1` - added a release consistency gate for stale version and security phrases.
- `tools/release-provenance.ps1` - added APK SHA-256, signing fingerprint, build commit, Gradle/AGP/Kotlin version, and artifact-path provenance generation.
- Versioned source-header comments were normalized to stable component descriptions without runtime behavior changes.
- `DiagnosticEventStore` - added a local-only JSONL ring buffer capped at 500 events for VPN lifecycle, Private DNS conflicts, blocklist swaps, source failures, DoH pin/failover issues, watchdog/doze recovery, root command failures, and backup restore failures.
- `DiagnosticExporter` and Settings diagnostics - changed diagnostic sharing from a single text file to a ZIP package with the text report, raw `diagnostic-events.jsonl`, and a manifest.
- `DohResolver` and `DnsToolsScreen` - added a 24-hour resolver health window and DNS Tools Status card for selected provider, active transport, latency, success rate, failovers, pin failures, and EDE placeholder.
- Failed-source feedback - added Room v15 `host_sources.last_http_status`, typed source download HTTP failures, persisted source health updates across workers/service/manual flows, a local source-failure notification helper, and Sources screen failure details showing last failure, HTTP status, and last successful update.
- `docs/decisions/0001-dnscrypt-engine.md` - added the DNSCrypt engine decision record. The chosen direction is an audited engine extraction behind a Kotlin facade; full `dnscrypt-proxy` via `gomobile` is a packaging/correctness spike, and native Kotlin crypto is deferred until audited primitives and the required test corpus exist.
- Experimental resolver gating - added shared maturity labels for DoQ and WireGuard DNS in Settings, diagnostics, runtime logs, and README. Production DNS defaults remain pinned DoH/DoH3/DoT, with DoQ and WireGuard DNS opt-in only.
- DoH pin rotation manifest - moved built-in provider SPKI pins into `DohPinManifest` with manifest version, issued date, primary/backup labels, review dates, expiry dates, diagnostic summary lines, and pin-failure event fields while keeping OkHttp fail-closed certificate pinning.
- Room migration golden test matrix - moved v1-v5 migrations from private DI fields into public `Migrations.ALL`, added a current database version constant, wired Room schema JSONs into androidTest assets, added `HostShieldMigrationTest` with frozen SQL fixtures for start versions 1 through 14, and documented unreconstructable missing official schema exports in `docs/database-migration-fixtures.md`.

## Files Intentionally Not Modified

- `AGENTS.md` and `CLAUDE.md` - both are ignored by Git in this repo. They were read and reconciled, but the durable tracked pointer is now `PROJECT_CONTEXT.md`.
- `CHANGELOG.md` - already carried the v6.5.9 source truth for this batch.
- Runtime source behavior - only versioned header comments were changed in Kotlin/build files.

## Continuation File

No `CONTINUE_FROM_HERE.md` was created because the hard completion criteria were met in this run.

## Verification

- `git diff --check` completed with only the expected Windows CRLF warning for `ROADMAP.md`.
- `.\gradlew.bat :app:testFullDebugUnitTest` passed from `app/` with JDK/Android SDK environment set.
