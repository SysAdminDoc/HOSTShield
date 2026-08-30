# HostShield Roadmap

Actionable work only. Historical and completed roadmap material is archived in CHANGELOG.md; blocked work is kept in Roadmap_Blocked.md.

## Actionable Items


## Audit Findings — 2026-08-10

Remaining open items from the 2026-08-10 deep audit. The rest were implemented
in v6.9.68 (see CHANGELOG.md); device-gated and decision-gated findings moved to
Roadmap_Blocked.md.

- [ ] P3 — Localization: extract remaining hardcoded screen copy to strings.xml
  Category: a11y
  Where: ~150 `Text("...")` literals across the ui tree — StatsScreen, OnboardingScreen, FirewallScreen, DnsToolsScreen, LogsScreen detail sheet, WebDavSyncScreen, ParentalControlScreen, RuleTestScreen, NetworkStatsScreen, DnsBenchmarkScreen, CrashReporterScreen, BlocklistGalleryScreen, OverlapAnalysisScreen, AppsScreen, AppPrivacyScreen, RulesScreen add-dialog, `MainActivity.kt:360-363` (StartupLoadingScreen); nav titles `ui/navigation/Navigation.kt:18-22`
  Problem: v6.9.1/6.9.27 claimed localization readiness but extraction reached only Home/Settings/DNS/widgets, so translation is blocked and a pseudolocale run exercises a minority of the UI.
  Evidence: `strings.xml` is ~445 lines against ~150 remaining literals; grep of `Text("` across `ui/`.
  Fix: Extract per screen to `strings.xml`. Nav titles need a string-resource id on `Screen` rather than a `String` field, resolved with `stringResource` at render.
  Acceptance: `lintFullDebug` with `HardcodedText` promoted from the baseline reports no new UI-tree hits; a pseudolocale run shows expanded text on Stats and Onboarding.
  Confidence: Verified
  Effort: L
  NOTE (2026-08-11): the highest-leverage slice is DONE — the shared a11y state
  descriptions ("On"/"Off"/"Disabled"/"Selected"/"Not selected") that TalkBack
  reads for every toggle in the app now resolve from resources, and the four
  hardcoded "Cancel" buttons use `R.string.action_cancel`. What remains is
  per-screen body copy.

- [ ] P3 — Areas not covered by this audit
  Category: docs
  Where: n/a
  Problem: The following were not deeply audited and warrant a future pass: (1) instrumented `androidTest/` behavior on a real device/emulator (blocked, per Roadmap_Blocked — but the 22-test suite is thin for the 8-caller FGS matrix and 30+ screens it claims to cover); (2) actual on-device visual review in light + dynamic-color + high-contrast modes (partially blocked); (3) full DNSCrypt/DoQ/WireGuard crypto correctness (debug-only, deferred); (4) the `.ai/research/` provenance corpus and `RESEARCH.md` factual currency; (5) runtime performance profiling (allocations in the packet loop, chart rendering) beyond static reads.
  Fix: Schedule device-connected passes for (1)/(2) when an emulator/device is available; treat (3)/(4)/(5) as separate focused audits.
  Confidence: Verified (scope statement)
  Effort: n/a
  NOTE (2026-08-11): (1) and (2) are no longer gated — an `SM-S938B` (Android 16 /
  SDK 36) is connected and an API-37 AVD (`clearcut-api37-ps16k`) plus API 37.0/37.1
  platforms are installed. See HS-2026-08-P1-019. (4) is done: RESEARCH.md was
  rewritten 2026-08-11 and several of its prior claims were found stale.

## Research-Driven Additions

Added 2026-08-11 from a full external research pass (RESEARCH.md, same date).
IDs continue the existing `HS-YYYY-MM-P<n>-NNN` scheme from HS-2026-06-P2-010.

### P0

### P1

- [ ] P1: Publish the signed IPv6 DNS and DoH trap IP manifest
  Why: the app parses and stores schema-2 IPv4/IPv6 trap sets, but the published manifest is still schema 1, so clients keep using the compiled fallback.
  Evidence: `DohBypassManifestVerifier`, the committed remote manifest, and the dual-stack route planner.
  Touches: `doh-bypass-list.json`, the manifest signing tool, and the dual-stack device check.
  Acceptance: publish a valid schema-2 manifest signed by the existing HostShield release key, then verify an IPv6 `dig` request on a dual-stack device.
  Complexity: S

### P2

- [ ] P2 — HS-2026-08-P2-023 — Loopback-bound local REST API, Pi-hole v6 compatible
  Why: HostShield exposes automation only through signature-permission broadcasts,
  which the repo itself has documented as unreachable for ordinary users. A
  localhost HTTP API that matches Pi-hole v6's stats/queries/enable/disable shapes
  inherits its entire client ecosystem (Home Assistant, Homepage, mobile dashboards)
  without a single line of backend, and Pi-hole self-serves its OpenAPI doc as the
  model to copy.
  Evidence: https://docs.pi-hole.net/api/; `Roadmap_Blocked.md` "Decide the external
  automation permission policy"; `service/LocalDnsServer.kt` already carries the
  per-client throttle and access-gate machinery to reuse.
  Touches: new `service/LocalApiServer.kt`, `service/AutomationActionContract.kt`
  (share the action layer), `ui/screens/settings/ProtectionSettingsSection.kt`,
  `AndroidManifest.xml`.
  Note: bind `127.0.0.1` only by default; LAN exposure must reuse the existing LAN
  DNS access gate plus a generated bearer token, and must be off by default. The
  LAN-exposed form is the deferred "Read-only LAN household dashboard" item in
  `Roadmap_Blocked.md` — build the loopback API first and treat the dashboard as a
  later consumer of it, not a separate server.
  Acceptance: `curl http://127.0.0.1:<port>/api/stats/summary` returns Pi-hole-v6-shaped
  JSON; enable/disable/refresh work; the port is closed when the toggle is off; a
  request from a non-loopback address is refused unless LAN mode is explicitly on.
  Complexity: L

- [ ] P2 — HS-2026-08-P2-024 — "Runs alongside your VPN" first-run detection
  Why: the single VPN slot is the #1 complaint across every venue surveyed, and
  every competitor answers it with "you can't." HostShield already has the two
  escapes nobody else has together — root hosts/iptables mode and the no-VPN DNS
  proxy — and buries both in settings.
  Evidence: GrapheneOS discuss.grapheneos.org/d/3242 ("the consensus is that there
  is no solution… the limitation of android allowing only one VPN layer");
  HN 41931035; https://adguard.com/kb/adguard-for-android/features/integration-with-vpn/.
  Touches: `ui/screens/onboarding/OnboardingScreen.kt`,
  `ui/screens/home/HomeWarningsSection.kt`, `util/RootUtil.kt`, `README.md`.
  Note: detect other VPN apps via the `play` flavor's `<queries>` MAIN intent so the
  feature is not `QUERY_ALL_PACKAGES`-only.
  Acceptance: with another VPN app installed, onboarding offers root or proxy mode
  with a one-line explanation of the tradeoff and configures the chosen one; the
  README leads with tri-mode coexistence.
  Complexity: M

- [ ] P2 — HS-2026-08-P2-025 — Honest battery and memory accounting panel
  Why: Android bills all tunneled traffic to the VPN app, so users blame the blocker
  for other apps' usage — and competitors are bleeding users over numbers that are
  largely an accounting artifact (RethinkDNS has a user-measured 2.8 GB RSS report;
  AdGuard loses users to bare Private DNS over battery). Being the blocker that can
  *prove* its cost is defensible and nobody attempts it.
  Evidence: https://github.com/celzero/rethink-app/issues/2393 (dev concedes the
  network engine "has always been lax with memory use");
  https://github.com/blokadaorg/blokada/issues/636 (open since 2020);
  `service/DnsVpnService.kt:1168, 1301, 1868, 1926` (1–3 allocations per query,
  never profiled — see the "Areas not covered" item above).
  Touches: new `util/ResourceAccounting.kt`, `ui/screens/stats/StatsScreen.kt`,
  `util/DiagnosticEventStore.kt`, `service/DnsVpnService.kt` (reuse buffers).
  Note: pairs with "Battery-friendly mode (doze-aware resolver tuning)" in
  `Roadmap_Blocked.md`, which is blocked on *measuring* drain — this item builds the
  measurement. Do them in that order. WorkManager 2.12's `work-analytics` artifact
  (per-worker duration, `stopReasonCounts`, retry counts) covers the worker half of
  this once it reaches stable; the repo currently hand-rolls that event recording.
  Acceptance: a Stats panel separates OS-attributed battery from measured
  CPU/wakelock time and shows process RSS with a high-water mark; a Macrobenchmark
  or `adb shell dumpsys meminfo` sample before/after the buffer reuse shows a
  measurable drop in per-query allocation.
  Complexity: M

- [ ] P2 — HS-2026-08-P2-026 — Translation delivery pipeline
  Why: there are **no** `values-<locale>/` directories — the app is English-only
  despite RTL support, pseudolocales, and a `LocaleLayoutScaffoldTest`. Extraction
  alone (tracked in the P3 localization item above) produces nothing shippable
  without somewhere for translations to come from.
  Evidence: `ls app/app/src/main/res/values*` → only `values/` and `values-v27/`;
  `res/resources.properties` pins `unqualifiedResLocale=en-US`; 372 strings + 8
  plurals in `strings.xml`.
  Touches: `app/app/src/main/res/values/strings.xml` (translatable audit),
  a Weblate or Crowdin config, `README.md` (contribution section),
  `app/app/build.gradle.kts` (`generateLocaleConfig` is already on).
  Note: depends on the existing P3 extraction item for full coverage, but the
  pipeline can land first against the 372 strings already externalized.
  Acceptance: at least one non-English locale ships end-to-end and is selectable
  via per-app language settings; new strings appear in the translation platform
  without manual export.
  Complexity: M

- [ ] P2 — HS-2026-08-P2-027 — Submit to Accrescent and IzzyOnDroid
  Why: distribution is GitHub-releases-only (37 downloads on the latest APK, 14
  stars). `apt.izzysoft.de/fdroid/index/apk/com.hostshield` returns 404 and the app
  is not on Accrescent. `CLAUDE.md` already records Accrescent eligibility with no
  code changes required, Accrescent has early access to Google's verification
  program, and both stores accept developer-signed APKs — unlike F-Droid, which
  re-signs and has publicly opposed the verification program.
  Evidence: store probes 2026-08-11; https://blog.accrescent.app/posts/android-developer-verification/;
  https://f-droid.org/en/2026/02/24/open-letter-opposing-developer-verification.html;
  `app/metadata/en-US/` already carries Fastlane-shaped metadata current through
  changelog 150.
  Touches: `app/metadata/en-US/` (add `images/`), `README.md`, release flow.
  Note: the GitHub release-key custody issue is resolved. The Developer Verification item in `Roadmap_Blocked.md` remains, and enforcement starts
  2026-09-30, so decide before then. This also corrects the premise of the
  "IzzyOnDroid/reproducible build readiness" blocked item, which says
  `app/metadata/en-US` is stale: it carries changelogs through 150, the current
  versionCode. Only `images/` is genuinely missing. F-Droid proper is a separate
  question — it re-signs with its own key and has publicly opposed Developer
  Verification, so Accrescent and IzzyOnDroid are the lower-friction targets.
  Acceptance: the app is installable from at least one third-party store and the
  README links it; `app/metadata` includes screenshots and a current changelog.
  Complexity: M

- [ ] P2 — HS-2026-08-P2-028 — Escalate persistently failing sources to the user
  Why: the HaGeZi outage was invisible for at least 10 days because last-good
  carry-forward masks failure by design. Carry-forward is correct; silence is not.
  Evidence: `service/BlocklistSourceCoordinator.kt` (per-source last-good),
  `service/SourceFailureNotifier.kt` (101 LOC, exists but did not surface this);
  `HostSource.consecutiveFailures` already tracked in `data/model/Entities.kt`.
  Touches: `service/SourceFailureNotifier.kt`, `ui/screens/home/HomeWarningsSection.kt`,
  `ui/screens/sources/SourcesScreen.kt`.
  Acceptance: a source failing N consecutive refreshes raises a Home warning naming
  it and its last successful update; the warning distinguishes "serving stale data"
  from "never loaded"; an ALLOWLIST source failing is escalated harder than a
  blocklist, because a missing allowlist means silent overblocking.
  Complexity: S

- [ ] P2 — HS-2026-08-P2-029 — Paginate Logs and Connection Log
  Why: every list screen loads a fixed `LIMIT :limit` into memory with no Paging
  dependency, so scrolling simply stops. Pi-hole moved to server-side query-log
  pagination in v6 and AdGuard Home redesigned its query log around auto-loading in
  v2.21 — both for the same reason.
  Evidence: `data/database/Daos.kt:130-209`; no `androidx.paging` in
  `app/gradle/libs.versions.toml`.
  Touches: `app/gradle/libs.versions.toml`, `data/database/Daos.kt`,
  `ui/screens/logs/LogsViewModel.kt`, `LogsScreen.kt`, `ConnectionLogScreen.kt`,
  `AppLogsScreen.kt`.
  Note: keep the existing saved-filter chips and jump controls working over the
  paged source — they are the reason the current fixed-limit design was tolerable.
  Acceptance: Logs scrolls past the old limit to the full retention window with flat
  memory; filters and the top/middle/end jump controls still work.
  Complexity: M

- [ ] P2 — HS-2026-08-P2-030 — Prune R8 keep rules and delete dead build config
  Why: `proguard-rules.pro` carries ~90 hand-written `-keep class … { *; }` blocks
  that actively suppress R8 on a build with `isMinifyEnabled = true` and
  `isShrinkResources = true`. Separately, `kotlinx-serialization-bom` is declared in
  two configurations with **zero** `kotlinx.serialization` references in the tree and
  the plugin not applied — inert at runtime, but it lands in the CycloneDX SBOM that
  feeds the OSV gate.
  Evidence: `grep -rn "kotlinx.serialization|@Serializable" app/app/src/` → 0 hits;
  `app/app/build.gradle.kts:195-196, 284`; AGP 9.3 adds
  `./gradlew :app:analyzeReleaseR8Config`.
  Touches: `app/app/proguard-rules.pro`, `app/app/build.gradle.kts`,
  `app/gradle/libs.versions.toml`.
  Note: depends on HS-2026-08-P1-018 for the AGP bump that provides the analyzer.
  Acceptance: `analyzeReleaseR8Config` reports no redundant keeps; the release APK
  shrinks; the app launches and DNS resolves on the connected device after the prune
  (reflection-dependent paths — Room, Hilt, Tink, BouncyCastle — must be verified at
  runtime, not by compile).
  Complexity: M

- [ ] P2 — HS-2026-08-P2-038 — Restore DoH3; the stated blocker has cleared
  Why: v6.9.2 removed embedded Cronet because no maintained non-vulnerable artifact
  existed. That is no longer true — `org.chromium.net:cronet-bundled:500.0.1` is
  published and current (group index `lastUpdated 20260729151703`), and
  `cronet-embedded` at 500.0.1 is now literally named "(DEPRECATED) Cronet Embedded"
  pointing at it. Meanwhile DNSNet ships DoH3 as its *default* transport (1.3.14,
  2026-03-22), so the capability HostShield dropped is now table stakes.
  Evidence: https://dl.google.com/dl/android/maven2/org/chromium/net/group-index.xml
  (POM verified 200, 2026-08-11); `service/Doh3Resolver.kt:10-12, 68-75` — a 76-line
  hard-disabled stub returning `null`/`emptyMap()`; `tools/check-cronet-posture.ps1`.
  Touches: `app/gradle/libs.versions.toml`, `app/app/build.gradle.kts`,
  `service/Doh3Resolver.kt`, `service/DohResolver.kt`,
  `tools/check-cronet-posture.ps1`, `tools/check-osv-report.ps1`.
  Note: **OSV has zero Maven-ecosystem advisories for any Cronet artifact**, so the
  existing OSV gate would silently certify an arbitrarily stale Chromium as clean —
  exactly the "gate that certifies what it does not enumerate" failure. Do not adopt
  without a purpose-built freshness check pinning the published `cronet-bundled`
  version. `play-services-cronet` is not an option: it breaks the FOSS/Accrescent
  posture. `android.net.http.HttpEngine` (API 34+) is the third path but needs a
  fallback for API 26-33 and is absent on de-Googled ROMs.
  Acceptance: DoH3 resolves on the connected device with `DoH3:<provider>` in the
  query log and falls back to pinned OkHttp DoH when HTTP/3 is unavailable; a
  release check fails when the pinned Cronet version falls behind the published one.
  Complexity: M

- [ ] P2 — HS-2026-08-P2-039 — Tailscale / MagicDNS-aware split DNS
  Why: "Integrate Tailscale" is the **single most-upvoted open request across every
  competitor tracker surveyed** (RethinkDNS [#1047](https://github.com/celzero/rethink-app/issues/1047),
  53👍, open since 2023). HostShield's README already documents Tailscale
  coexistence in root mode; formalizing it is DNS-shaped work, not tunnel work, so
  it does not drag the app toward being a proxifier.
  Evidence: Rethink #1047, #1040 "Split DNS" (9👍), #1153 "Pin this domain to
  system/local DNS" (5👍); Pi-hole's long-running "wildcards in local DNS records"
  request (72k views). No `tailscale` reference exists anywhere in the tree.
  Touches: `service/DnsVpnService.kt` (per-suffix upstream selection),
  `service/AppDnsRuleEngine.kt`, `data/preferences/DnsPreferences.kt`,
  `ui/screens/settings/DnsSettingsSection.kt`, `README.md`.
  Note: implement as a general **per-suffix resolver override** (`*.ts.net`,
  `*.lan`, corporate suffixes) and ship a Tailscale preset on top. That is the same
  primitive as Control D's "Bypass" verb and it fixes the perennial split-horizon
  complaint at the same time. This **supersedes** two deferred entries in
  `Roadmap_Blocked.md` — "Smart/Split DNS routing" and the docs-only "Tailscale and
  GrapheneOS compatibility guides"; retire both when this lands.
  Acceptance: a configured suffix resolves via the chosen upstream (system/DHCP DNS,
  a specific encrypted provider, or the Tailscale resolver) while everything else
  keeps the pinned path; `100.64.0.0/10` and `*.ts.net` resolve correctly with
  Tailscale active in root mode; JVM tests cover suffix precedence and the fail-closed
  interaction.
  Complexity: L

- [ ] P2 — HS-2026-08-P2-040 — Default policy for newly installed apps
  Why: a new app installs and is silently unfiltered/unfirewalled until the user
  notices. This is a top-voted gap in three separate trackers and HostShield has no
  handling at all — no `PACKAGE_ADDED` receiver exists.
  Evidence: AdGuard [#4482](https://github.com/AdguardTeam/AdguardForAndroid/issues/4482)
  (20👍) "Specify default filtering policy for new app installations";
  Karma [#3](https://github.com/StarGW-net/karma-firewall/issues/3) "Block new apps";
  shipped as a universal rule in RethinkDNS and NetGuard.
  `grep -rin "PACKAGE_ADDED" app/app/src/main/java/` → 0 hits.
  Touches: new `service/PackageAddedReceiver.kt`, `AndroidManifest.xml`,
  `data/preferences/FirewallPreferences.kt`, `service/AppDnsRuleEngine.kt`,
  `ui/screens/apps/AppsScreen.kt`, `ui/screens/settings/FirewallScreen.kt`.
  Note: the `play` flavor has no `QUERY_ALL_PACKAGES`, so the receiver must degrade
  to launchable apps only there; state that in the setting's description.
  Acceptance: a configurable default (allow / block / block-on-metered / notify-only)
  applies to a freshly installed package without opening the app; a notification
  names the new app and links to its policy; the default is visible and changeable
  from Firewall settings.
  Complexity: M

- [ ] P2 — HS-2026-08-P2-041 — Kill switch and a first-class always-on setup flow
  Why: "block connections when protection is down" ships in NetGuard, RethinkDNS,
  Athena, Karma, and BlockAds and is absent here. HostShield only *detects* Android's
  lockdown state for its Android 16 recovery advisory; it never helps the user turn
  it on, and in root mode it could enforce a real kill switch itself.
  Evidence: `service/DnsVpnService.kt:2617-2632` and
  `util/Android16VpnRecoveryDetector.kt` (detection only);
  https://github.com/celzero/rethink-app/issues/2608 "Block all network connections
  … until it is fully connected (prevent IP leaks on boot)";
  https://github.com/Kin69/Athena (kill switch shipped).
  Touches: `service/IptablesManager.kt` (root-mode default-DROP until the DNS path is
  verified up), `ui/screens/onboarding/OnboardingScreen.kt`,
  `ui/screens/home/HomeWarningsSection.kt`, `README.md`.
  Note: in VPN mode a true kill switch is the OS's "Always-on VPN + Block connections
  without VPN"; the app can only deep-link and verify it. Say that plainly rather
  than implying an app-level guarantee it cannot make.
  Acceptance: root mode drops non-DNS traffic until the blocking path is confirmed
  active and restores it on teardown; VPN mode shows always-on/lockdown status with
  a deep link to the system setting and a warning when it is off.
  Complexity: M

- [ ] P2 — HS-2026-08-P2-042 — Offline GeoIP/ASN database and geo/ASN rule actions
  Why: `GeoIpLookup` calls `ipapi.co` over the network — a rate-limited third-party
  dependency and a privacy leak in an app whose pitch is local-first;
  `CLAUDE.md` already admits "No offline MaxMind asset/dependency is currently
  present" and `util/OfflineGeoIp.kt` was deleted as dead code. Three competitors
  bundle an offline database, and both AdGuard (`$respgeo`, v2.23 Jul 2026) and
  Control D (Geo Custom Rules) promoted geography from decoration to a rule *action*.
  Evidence: `util/GeoIpLookup.kt` (180 LOC, HTTPS + cache + backoff);
  commit `fe199c9 chore: remove dead geoip2 dependency and OfflineGeoIp`;
  https://adguard-dns.io/en/versions/dns/release.html (v2.23 country/ASN user rules);
  PCAPdroid bundles country + ASN databases.
  Touches: new asset + `util/GeoIpLookup.kt`, `domain/BlocklistHolder.kt`,
  `domain/parser/AdblockRuleParser.kt` (`$respgeo`), `ui/screens/logs/LogsViewModel.kt:465`
  (currently returns `emptyList()` when online lookup is off, so callers cannot tell
  "feature off" from "no results").
  Note: pick a redistributable database (DB-IP Lite CC-BY, or the IPinfo/MaxMind
  free tiers) and check the licence against GPL-3.0 before committing an asset.
  Acceptance: country and ASN resolve with no network call; a rule can block on the
  answer's country or ASN; the online lookup becomes an explicit opt-in enrichment
  rather than the only path.
  Complexity: L

- [ ] P2 — HS-2026-08-P2-043 — Shizuku/ADB as a third privilege tier
  Why: it is the one capability AdAway's audience asks for that HostShield does not
  have, Athena already ships it, and it escapes **both** constraints at once — no
  root, and no VPN slot consumed. `grep -ril shizuku app/app/src/` returns nothing.
  Evidence: https://github.com/AdAway/AdAway/issues/4240 (7👍, plus /4137, /4227, /2932);
  https://github.com/Kin69/Athena/issues/41 ("rely solely on Shizuku");
  https://github.com/celzero/rethink-app/issues/2461.
  Touches: new `util/ShizukuRunner.kt` alongside `util/RootShellRunner.kt`,
  `service/IptablesManager.kt`, `service/ProtectionServiceStarter.kt`,
  `ui/screens/onboarding/OnboardingScreen.kt`, `AndroidManifest.xml`.
  Note: also resolves the "Decide the external automation permission policy" item in
  `Roadmap_Blocked.md` — a Shizuku-granted shell can hold a permission an `adb shell
  am broadcast` caller cannot. Verify what Shizuku actually grants on Android 16/17
  before committing; the privilege set is narrower than root.
  Acceptance: with Shizuku running, protection starts and per-app rules apply without
  root and without the VPN slot; the mode is offered in onboarding only when Shizuku
  is present; the automation receiver is reachable through it.
  Complexity: L

### P3

- [ ] P3 — Re-capture README screenshots
  Re-capture the five images embedded at `README.md:17-21` on the isolated
  emulator after the v6.9.63-v6.9.68 UI changes, including Material You colors,
  redirect/WireGuard settings, schedule pickers, and the widget rework.

- [ ] P3 — Ship a startup and protection-enable Baseline Profile
  Add the Macrobenchmark/profile generation path, generate the profile on the
  available emulator, and record before/after startup metrics.

- [ ] P3 — Material 3 Expressive motion/shape adoption pass
  Apply MotionScheme and updated progress/shape tokens app-wide, then verify the
  result under font scaling on the isolated emulator.

- [ ] P3 — Android 17 ECH opt-in for DoH/DoT transport
  Add `<domainEncryption>` opportunistic/enabled per resolver host in
  `network_security_config.xml` and verify resolver support on API 37.

- [ ] P3 — HS-2026-08-P3-031 — Reconcile the stale notes in CLAUDE.md
  Why: six documented facts are wrong and each will mislead the next engineering
  pass — the repo's own memory protocol treats a note that led you astray as a
  high-value correction.
  Evidence, each verified 2026-08-11: (1) `CLAUDE.md:63-65` says
  `doh-bypass-list.json` fails the release-doc signature check — I recomputed the
  canonical payload hash and verified the RSA-SHA256 signature against the pinned
  cert; both valid, and `tools/check-release-docs.ps1` exits 0. (2) "Vico 2.5.0" —
  the catalog pins 3.2.3. (3) "DB version is 14" — Room is at v20. (4) "Room 3.0
  currently alpha… ~1h mechanical" — 3.0.1 stable 2026-07-29 under the new
  `androidx.room3` group with `SupportSQLiteDatabase` removed and all DAO methods
  forced `suspend`; multi-session work. (5) "Sync URLs: 10MB limit" — sources cap at
  80 MB (`SourceDownloader.kt:46`); 10 MB applies only to rule sync. (6) the
  `.github/workflows/ci.yml` reference — no `.github` directory exists.
  Touches: `CLAUDE.md`.
  Acceptance: each claim above is corrected in place with a verification date.
  Complexity: S

- [ ] P3 — HS-2026-08-P3-032 — Repair repository and release hygiene
  Why: two small defects make the project read as less maintained than it is.
  Evidence: (a) `Initial commit` exists twice (`ba99fc7`, `d33242c`) with an
  identical tree hash — `35c3593` merged a rewritten history with
  `--allow-unrelated-histories` instead of replacing it, so every pre-2026-06 commit
  is duplicated and all history statistics before that date are inflated 2×.
  (b) GitHub topics are `ad-blocker, android, dark-theme,
  hosts-file, kotlin` — missing `dns`, `vpn`, `privacy`, `dns-over-https`,
  `tracker-blocker`, `foss`, the terms this app is actually searched by.
  Touches: git history (or a documented decision to leave it), tags, repo settings.
  Note: rewriting published history force-pushes over 776 commits and breaks every
  existing clone. Leaving it and documenting the duplication in `CLAUDE.md` is a
  defensible call — decide explicitly rather than by default. The topic update is free.
  Acceptance: topics cover the search terms;
  the lineage duplication is either fixed or recorded as an accepted artifact.
  Complexity: S

- [ ] P3 — HS-2026-08-P3-033 — Unit-test the zero-coverage packages
  Why: 642 unit tests cover the engine well but leave whole packages at zero:
  root (`MainActivity` 615 LOC, `HostShieldApp` 81), `di`, `ui.components` (1,408
  LOC incl. `PremiumSurfaces` 764 and `VicoCharts` 390), `ui.navigation`,
  `ui.accessibility`, and `HomeViewModel` (1,005 — the largest untested ViewModel).
  `DoqResolver` and `WireGuardProxy` have zero tests despite containing deliberately
  weakened crypto that only a release-build gate keeps unreachable.
  Evidence: full test inventory in RESEARCH.md "Architecture Assessment".
  Touches: `app/app/src/test/java/com/hostshield/ui/screens/home/`,
  `.../ui/components/`, `.../di/`, `.../service/`.
  Note: highest value first — a Hilt graph-validation test in `di` and a
  `HomeViewModel` state test, then a test asserting the release build forces DoQ and
  WireGuard off (the gate protecting the weakened crypto has no direct coverage).
  Acceptance: each named package has at least one meaningful test exercising real
  code paths (not construction smoke); the release-gate test fails if either
  experimental engine becomes reachable outside debug.
  Complexity: L

- [ ] P3 — HS-2026-08-P3-034 — Basic / Expert UI mode
  Why: the app has 31+ screens and the ecosystem's loudest usability complaint is
  aimed at exactly this shape — a GrapheneOS user on RethinkDNS: "I am straight up
  microwaving my frontal cortex trying to comprehend this galaxy brain app."
  Pi-hole v6 shipped Basic/Expert modes for the same reason. One switch, no feature
  removal, and it is the prerequisite for ever attempting a TV or tablet surface.
  Evidence: https://discuss.grapheneos.org/d/13236-rethinkdns;
  https://pi-hole.net/blog/2025/02/18/introducing-pi-hole-v6/.
  Touches: `data/preferences/UiPreferences.kt`, `ui/navigation/Navigation.kt`,
  `ui/screens/settings/SettingsScreen.kt`, `ui/screens/onboarding/OnboardingScreen.kt`.
  Acceptance: Basic mode hides the diagnostic/experimental surfaces (DNS tools, TLS
  fingerprints, automation audit, PCAP, WireGuard keys, LAN DNS) behind one toggle
  without disabling them; the mode is chosen during onboarding and is reversible.
  Complexity: M

- [ ] P3 — HS-2026-08-P3-035 — Extend `$dnsrewrite` to HTTPS/SVCB and RCODE
  Why: HostShield already enforces `$dnsrewrite`, and `$dnstype` rules already flow
  through to the decision paths — but rewriting the HTTPS/SVCB record (RR type 65)
  is the practical lever against ECH-based filter evasion and against apps that
  publish alternative endpoints there. NextDNS's Rewrites are A/AAAA-only, so this
  beats the market leader cheaply. Android 17 also adds a `DnsResolver` API for
  querying HTTPS records containing ECH configs.
  Evidence: https://adguard-dns.io/kb/general/dns-filtering-syntax/;
  https://www.rfc-editor.org/info/rfc9460/; https://datatracker.ietf.org/doc/rfc9849/;
  `domain/parser/AdblockRuleParser.kt:488` already lists `dnsrewrite` as enforced.
  Touches: `domain/parser/AdblockRuleParser.kt`, `service/DnsPacketBuilder.kt`,
  `service/DnsVpnService.kt`, `domain/DnsTypeRule.kt`.
  Acceptance: a rule can synthesize or suppress an HTTPS/SVCB answer and can set an
  RCODE; a query for a domain with an `ech=`-stripping rule receives an HTTPS RR
  without the `ech` parameter; JVM tests cover build and parse for RR type 65.
  Complexity: M

- [ ] P3 — HS-2026-08-P3-036 — Handle RFC 9824 Compact Denial of Existence
  Why: published as an RFC in September 2025 and already deployed by major
  authoritative providers. It signals NXDOMAIN as NODATA plus EDE 21, so a resolver
  or client that infers non-existence from the RCODE alone caches the wrong thing.
  HostShield does its own negative caching and already parses EDE.
  Evidence: https://datatracker.ietf.org/doc/draft-ietf-dnsop-compact-denial-of-existence/;
  `service/DnsCache.kt` (RFC 2308 MINIMUM handling), EDE support added in v6.9.16.
  Touches: `service/DnsCache.kt`, `service/DnsPacketParser.kt`.
  Acceptance: a NODATA response carrying EDE 21 is negative-cached as
  non-existence with the correct TTL; a JVM test pins both the compact and the
  classic NXDOMAIN shapes.
  Complexity: S

- [ ] P3 — HS-2026-08-P3-037 — Show upstream resolver capabilities via RESINFO
  Why: RFC 9606 (Proposed Standard, June 2024) lets a client ask its resolver what
  it actually does — `qnamemin`, `exterr`, `infourl`, and the drafted `dnssecval`
  and `dns64` keys. HostShield already has a DNS Benchmark screen and resolver
  health tracking; this turns "which resolver should I pick" from marketing copy
  into a measured fact, and no Android blocker surfaces it.
  Evidence: https://www.rfc-editor.org/info/rfc9606/;
  `ui/screens/settings/DnsBenchmarkScreen.kt`, `util/DnsBenchmark.kt`.
  Touches: `util/DnsBenchmark.kt`, `ui/screens/settings/DnsBenchmarkScreen.kt`,
  `service/DnsPacketParser.kt`.
  Acceptance: the benchmark shows, per resolver, whether it advertises RESINFO and
  which capabilities it claims; a resolver that does not answer is shown as
  "unknown", never as "unsupported".
  Complexity: M

- [ ] P3 — HS-2026-08-P3-044 — Differential blocklist updates
  Why: HostShield re-downloads whole lists on every refresh — up to tens of MB for
  the large tiers, on a schedule, often over mobile data. AdGuard shipped delta
  updates in v4.13 (2026-07-28) specifically so "filters will load automatically
  without consuming tons of traffic and overloading servers." HostShield already
  persists per-source ETag, Last-Modified, SHA-256, size, and entry deltas, so most
  of the bookkeeping exists.
  Evidence: `service/BlocklistSourceCoordinator.kt` (per-source metadata),
  `data/source/SourceDownloader.kt:46` (`MAX_SOURCE_DOWNLOAD_BYTES = 80 MiB`);
  AdGuard v4.13 release notes (`FiltersListManager`).
  Touches: `data/source/SourceDownloader.kt`, `service/BlocklistSourceCoordinator.kt`,
  `service/HostsUpdateWorker.kt`.
  Note: start with HTTP range/`If-None-Match` correctness and an append-only fast
  path for sources whose prefix hash is unchanged; a true patch format needs
  publisher cooperation and is not available from most list maintainers.
  Acceptance: an unchanged source costs one conditional request; a source with a
  changed tail transfers materially less than the full body; the SHA-256 integrity
  check still covers the reconstructed list, not just the delta.
  Complexity: M

- [ ] P3 — HS-2026-08-P3-045 — Root firewall rule-correctness pass
  Why: AFWall+ v4.1.0 (2026-08-06) is a checklist of exactly the iptables edge cases
  a per-app firewall gets wrong, from a project with a decade of field reports.
  `IptablesManager.kt` documents none of them.
  Evidence: AFWall+ v4.1.0 release notes — ICMPv6 RS/RA/NS/NA allowances, loopback
  routing, LAN discovery (multicast/broadcast/mDNS/SSDP), tethered DHCP replies,
  reject-chain logging, per-app localhost blocking ([#1421](https://github.com/ukanth/afwall/issues/1421)),
  multiple LAN subnets routing to WAN ([#1362](https://github.com/ukanth/afwall/issues/1362)),
  `CaptivePortalLogin` missing from the app list ([#1476](https://github.com/ukanth/afwall/issues/1476)),
  and apply-success semantics that wait for **both** IPv4 and IPv6 to complete.
  Touches: `service/IptablesManager.kt`, `service/CaptivePortalHandler.kt`,
  `test/.../service/IptablesManagerScriptTest.kt`.
  Note: overlaps the blocked item "Wire the hs-lan firewall chain / remove WHITELIST
  dead code" in `Roadmap_Blocked.md` — do both in one rooted-device pass. That
  blocker cleared per HS-2026-08-P1-019.
  Acceptance: generated scripts allow ICMPv6 neighbour/router discovery and LAN
  service discovery, handle tethered DHCP, and report success only when both address
  families applied; `CaptivePortalLogin`'s UID is handled explicitly; script tests
  pin each rule shape.
  Complexity: M
