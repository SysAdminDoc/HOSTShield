# HostShield v3.1.0

## New Features

### Scheduled DoH Bypass List Updates
`DohBypassUpdater` now runs automatically on every periodic `HostsUpdateWorker` cycle. Remote DoH domain/wildcard lists are fetched from GitHub and merged into the blocklist trie without requiring app updates.

### Multiple Upstream DNS with Fallback
`AppPreferences.customUpstreamDns` now supports comma-separated DNS servers (e.g., `1.1.1.1, 8.8.8.8, 9.9.9.9`). The DNS Tools Config tab shows server count and explains fallback behavior. Helper `getUpstreamDnsList()` provides parsed list for DNS forwarding code.

### Auto Update Check on Settings Open
`SettingsViewModel` now silently checks GitHub Releases when the Settings screen opens. Only shows a notification banner if an update is available (no noise for "up to date" or errors). Manual check button still available. Shows changelog, release date, and direct APK download link.

### Allowlist Sources
New `ALLOWLIST` source category added to `SourceCategory` enum. Three curated allowlist sources seeded by default (disabled):
- **Anudeep's Whitelist** (~400 domains) - Prevents common false positives
- **Anudeep's Optional Whitelist** (~100 domains) - Extended CDN/analytics safe domains
- **HaGeZi Whitelist** - Referral allowlist preventing breakage from aggressive blocklists

`HostsUpdateWorker` now downloads allowlist sources separately and subtracts their domains from the blocklist before updating `BlocklistHolder`. New DAO queries: `getEnabledAllowlistSources()`, `getEnabledBlockSources()`.

### Blocklist Overlap Analysis
New `OverlapAnalysisScreen` accessible from Settings > Tools. Downloads all enabled block sources, parses domains, and computes pairwise overlap:
- Summary: total entries, unique domains, wasted (redundant) entries, efficiency %
- Source sizes with bar chart visualization
- Pairwise overlap cards sorted by overlap %, color-coded (red >80%, yellow >40%, green <40%)
- Helps users optimize their source selection by removing redundant lists

### Stats CSV Export
New export functionality in Settings > Diagnostics & Export:
- Exports daily stats (up to 90 days), top 50 blocked domains, top 30 blocked apps
- CSV format with sections, compatible with Excel/Google Sheets
- File picker integration via Android SAF

## Files Changed/Added

**New files:**
- `OverlapAnalysisScreen.kt` - Blocklist overlap analysis UI + ViewModel

**Modified:**
- `build.gradle.kts` - v3.0.0 -> v3.1.0 (versionCode 30 -> 31)
- `Entities.kt` - Added `ALLOWLIST` to `SourceCategory` enum
- `Daos.kt` - Added `getEnabledAllowlistSources()`, `getEnabledBlockSources()` queries
- `AppPreferences.kt` - Added `getUpstreamDnsList()` helper, multi-DNS docs
- `HostShieldRepository.kt` - Added allowlist source repos + 3 curated allowlist seeds
- `HostsUpdateWorker.kt` - DoH bypass refresh + allowlist source subtraction
- `SettingsViewModel.kt` - Auto update check, CSV export functions
- `SettingsScreen.kt` - Overlap analysis nav, CSV export button, diagnostics section
- `DnsToolsScreen.kt` - Multiple upstream DNS input with server count
- `SourcesScreen.kt` - ALLOWLIST category color (Green)
- `Navigation.kt` - Added `OVERLAP_ANALYSIS` sub-screen
- `MainActivity.kt` - Overlap analysis navigation composable
- `README.md` - v3.1.0, new feature entries
