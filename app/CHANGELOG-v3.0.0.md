# HostShield v3.0.0 — Best-in-Class DNS Blocker

**62 Kotlin source files | 17,458 source lines | 11 test files | 1,408 test lines**

## New in v3.0.0

### DNS Response Cache (LRU + TTL)
`DnsCache` — 2000-entry positive cache + 500-entry negative cache with TTL-aware expiration. Integrated into all forwarding paths (UDP, UDP fallback, DoH, IPv6). Serves repeated queries from memory instead of hitting upstream.

- TTL extracted from DNS response (minimum across all RRs)
- TTL clamped to 10s floor / 1 hour ceiling
- Truncated and SERVFAIL responses never cached
- NXDOMAIN cached with shorter 60s TTL
- LRU eviction when cache is full
- Cache stats: hit rate, size, eviction count
- Transaction ID patching on cache hits

### CNAME Cloaking Detection
`CnameCloakDetector` — Inspects DNS response CNAME chains against the active blocklist. Catches first-party CNAME cloaking (the #1 technique ad networks use to bypass DNS blockers).

- Extracts all CNAME targets from answer section
- Checks each target against `BlocklistHolder`
- Integrated into all forwarding paths — if any CNAME target is blocked, the entire response is replaced with a block response
- Also extracts resolved IPs from responses for detail view
- Max chain depth of 10 to prevent abuse

### Database Migration System
`Migrations.kt` — Proper Room database migrations for safe upgrades from any version. Prevents the crash-on-update bomb that existed in v2.x.

- MIGRATION_5_6: Adds `response_time_ms`, `upstream_server`, `cname_chain`, `resolved_ips` columns to `dns_logs`
- Registered in `DatabaseModule` alongside existing migrations
- `fallbackToDestructiveMigration()` kept as safety net

### DnsLogEntry Enhanced Schema
4 new columns for per-query detail view:
- `response_time_ms` — Latency tracking (INT)
- `upstream_server` — Which DNS server answered (TEXT)
- `cname_chain` — Comma-separated CNAME targets found (TEXT)
- `resolved_ips` — Comma-separated answer IPs (TEXT)

### 7-Day Trend Line Chart
`TrendLineChart` composable in Stats screen — dual-line canvas chart showing blocked (red) vs. total (blue) queries per day over the past week. Day labels, data points, and legend.

New `DailyBreakdown` query in DnsLogDao groups by date with blocked/total counts.

### Diagnostic Report Generator
`DiagnosticExporter` — Generates comprehensive text report for debugging:
- Device info (model, Android version, ABI, kernel)
- App config (block method, DoH, DNS trap, firewall, etc.)
- Blocklist stats
- Last 50 DNS log entries
- VPN interface state (TUN detection)
- System DNS servers
- Private DNS detection
- Shareable via Android share sheet (FileProvider)

Settings screen has "Generate diagnostic report" button in new Diagnostics section.

### CI/CD Pipeline
`.github/workflows/ci.yml` — GitHub Actions workflow:
- **test**: Runs `testFullDebugUnitTest` on push/PR
- **build**: Builds both `full` and `play` debug APKs (matrix strategy)
- **release**: Attaches release APKs to GitHub Releases
- Gradle caching for fast builds
- Test result upload as artifacts

## Improvements

### Dead Code Removal
Removed ~130 lines of dead code from DnsVpnService:
- Inline `buildNxdomain()`, `buildZeroIpResponse()`, `buildRefusedResponse()` — already delegated to `DnsPacketBuilder`
- `SOA_RDATA` lazy val and `buildSoaRdata()` — only used by removed methods
- VPN service is now 1,621 lines (was 1,628)

### DNS Cache Integration in Forwarding
All forwarding methods now:
1. Check cache before sending upstream query
2. Run CNAME cloaking detection on upstream response
3. Cache successful responses with TTL
4. Block if any CNAME target is in blocklist

### Repository Layer
Added `getDailyBreakdown()` passthrough for 7-day trend chart.

### New DAO Queries
- `getLogsForApp(pkg)` — Filter DNS logs by app package
- `getById(id)` — Single log entry lookup for detail view
- `getDailyBreakdown(since)` — Daily blocked/total aggregation for trend charts

---

## Cumulative Stats (v1.0.0 → v3.0.0)

| Metric | v1.6.0 | v2.1.0 | v3.0.0 | 
|--------|--------|--------|--------|
| Kotlin source files | 54 | 60 | 62 |
| Source lines | 14,622 | 16,504 | 17,458 |
| Test files | 8 | 10 | 11 |
| Test lines | 688 | 1,088 | 1,408 |
| Database version | 5 | 5 | 6 |
| DNS response cache | No | No | LRU + TTL |
| CNAME cloaking detection | No | No | Full chain |
| Diagnostic export | No | No | Shareable |
| CI/CD | No | No | GitHub Actions |
| Dead code (VPN service) | ~80 lines | ~130 lines | 0 lines |

## Files Changed/Added in v3.0.0

**New files:**
- `DnsCache.kt` (238 lines) — DNS response cache
- `CnameCloakDetector.kt` (202 lines) — CNAME cloaking detection
- `DiagnosticExporter.kt` (211 lines) — Diagnostic report generator
- `Migrations.kt` (38 lines) — Database migration v5→v6
- `.github/workflows/ci.yml` — CI/CD pipeline

**Modified:**
- `DnsVpnService.kt` (1628→1621) — Cache + CNAME integration, dead code removal
- `Entities.kt` (128→132) — 4 new DnsLogEntry columns
- `Daos.kt` (354→378) — 3 new queries + DailyBreakdown projection
- `HostShieldDatabase.kt` — version 5→6
- `DatabaseModule.kt` — MIGRATION_5_6 registered
- `HostShieldRepository.kt` — getDailyBreakdown()
- `StatsScreen.kt` (391→480) — 7-day trend chart + TrendLineChart composable
- `SettingsScreen.kt` — Diagnostics section
- `SettingsViewModel.kt` — generateDiagnosticReport()
