# Changelog

All notable changes to HostShield will be documented in this file. Detailed
release notes per version live in [`app/CHANGELOG.md`](app/CHANGELOG.md).

## [v6.5.2] - 2026-05-14

### Reliability
- Added Android 16 always-on VPN recovery detection for the post-update
  lockdown corruption pattern where the VPN is established but receives no
  tunnel traffic despite a validated physical network.
- Added a Home advisory banner that tells the user to restart the device and,
  on rooted devices, exposes a one-tap restart action.
- Added focused detector coverage so the advisory only appears for Android 16+
  always-on lockdown sessions with a valid TUN fd, validated physical network,
  two-minute observation window, and zero inbound packets.

## [v6.5.1] - 2026-05-13

Premium product-polish pass focused on first-run trust, visual cohesion, action
discoverability, and on-device layout correctness.

### UX / UI
- Refined the global Compose shape system and removed oversized rounded surfaces
  from the checked user-facing flows for a sharper, more intentional visual
  language.
- Reworked onboarding footer layout so page indicators no longer collide with
  primary actions, converted the feature overview into a compact two-column
  summary, and kept DNS resolver choice scrollable with a fixed Continue action.
- Improved Sources and Rules action placement by moving add/gallery/paste
  actions into compact header controls instead of floating over list content.
- Added clearer loading, empty, error, disabled, selection, and accessibility
  semantics across onboarding, home, sources, rules, stats, and settings.
- Added consistent destructive-action confirmations for clearing logs,
  fingerprints, crash reports, and deleting custom rules or sources.
- Added a deliberate empty state for Connection Log's top blocked apps view so
  the secondary monitoring surface no longer appears blank with no data.
- Improved accessible names for prominent icon-only controls, warning banners,
  status dismiss actions, DNS save affordances, and accent color swatches.

### Reliability
- Fixed debug/release side-by-side installation by deriving the automation
  permission from the package id instead of hard-coding the release id.
- Fixed WebDAV connection testing so a failed PROPFIND is not reported as a
  successful empty remote directory.

### Verification
- Built and installed the full debug APK on a connected Samsung SM-S938B.
- Smoked onboarding, dashboard, sources, rules, stats, and settings on-device.

## [v6.5.0] - 2026-05-13

Engineering hardening pass — focused on real correctness, security, and reliability
issues found in a deep audit of the v6.4 codebase.

### Security
- **Parental PIN: fail-closed + brute-force lockout.** `verifyPin` previously
  returned `true` when no PIN was set — every PIN-gated action could be bypassed
  by clearing the stored hash. Now returns false unless `isPinSet()` is also
  true, with a new `verifyPinDetailed` returning `Success / Wrong / LockedOut /
  NoPin`. After 5 wrong attempts the caller is locked out for 30 s → 60 s →
  120 s → 300 s (exponential).
- **Backup encryption strengthened.** PBKDF2 iteration count raised from
  `100_000` → `600_000` (OWASP 2023 baseline) on both `EncryptedBackup` and
  `BackupCrypto`. Fixed off-by-one in `decrypt()` that rejected legal
  minimum-size ciphertexts and silently mis-classified ones too small to contain
  a GCM tag. Key bytes zeroed after `SecretKeySpec` is built.
- **SecureStore.verifyPin constant-time on raw bytes.** Previously compared
  Base64 strings; now decodes salt and expected hash, validates length, wraps
  decode in try/catch, and uses `MessageDigest.isEqual` on the byte arrays.
- **RootUtil hostname injection guard.** `appendHostEntry` / `removeHostEntry`
  now validate the hostname against RFC 1123, validate the redirect IP, switch
  `echo` for `printf`, and use token-aware line filtering on remove so entries
  with comments / tabs / multi-host lines are correctly removed.
- **Device-transfer no longer leaks encrypted prefs.** `data_extraction_rules.xml`
  now excludes `hostshield_secure_prefs` from `<device-transfer>` — the source
  device's hardware-backed master key cannot unwrap on the destination, so
  copying the ciphertext only locks the user out.
- **Widget receiver no longer launchable by third-party apps.** Removed
  `com.hostshield.WIDGET_TOGGLE` from the exported `HostShieldWidgetProvider`
  intent-filter. The widget's own PendingIntent targets the receiver by
  component, so toggling still works.
- **WireGuard input validation.** `buildDnsUdpPacket` no longer crashes on
  IPv6 / hostname inputs; `parseEndpoint` correctly handles `[v6]:port` form.
  WireGuard remains marked EXPERIMENTAL until a full Noise_IKpsk2 binding lands.

### Correctness & reliability
- **`ACTION_PAUSE > 10 s now works.** `AutomationReceiver` previously used
  `delay(N * 60_000L)` inside `goAsync()`, which Android killed after ~10 s.
  Replaced with `PauseResumeWorker` (new) — WorkManager survives Doze, so
  user-requested pauses of any documented length (up to 24 h) resume reliably.
- **TCP DNS fallback on TC=1 (RFC 7766 §6.2).** When the upstream UDP response
  has the TC (truncated) bit set, the VPN forwarder now retries the same query
  over TCP and substitutes the response. Large DNSSEC RRSIG / TXT records that
  exceeded the 1500-byte UDP buffer used to be silently truncated.
- **`HostsUpdateWorker` per-source error surfacing.** Failed block / allowlist
  / sync-URL downloads now log a warning with the URL and the error, plus a
  summary line at the end of the run. Previously `.onSuccess { }` silently
  swallowed 404s / DNS errors / SSL failures.
- **`HostsUpdateWorker.runNow` is now expedited.** Marked
  `RUN_AS_NON_EXPEDITED_WORK_REQUEST` and enqueued under a unique name so
  back-to-back invocations don't pile up.
- **`BlocklistHolder` atomic snapshot swap.** All read-side state (trie root,
  exact-match set, regex rules, IP blocks, count) is now packed into a single
  immutable `Snapshot`. Readers see either fully-old or fully-new state, never
  a torn view that previously could mis-classify a domain mid-update.
- **`BlocklistHolder` decision LRU is now actually LRU.** Replaced
  `ConcurrentHashMap` + random eviction with a synchronized
  `LinkedHashMap(accessOrder=true)` and `removeEldestEntry` — fixes TOCTOU on
  the size check + random eviction order (was not actually evicting the LRU
  entries).
- **`BlocklistHolder.removeDomain` no longer drops the count for never-added
  domains.** Counter UI used to drift over time.
- **`DohResolver` size cap + cert-pin diagnostics.** Responses are now read with
  an explicit 65 535-byte cap (RFC 8484 maximum) so a malicious or misconfigured
  endpoint can't OOM the VPN process. `SSLPeerUnverifiedException` is logged
  loudly so cert rotation failures are debuggable from `logcat` rather than
  silently failing closed. `failoverOrder` rotation (previously dead code) is
  now wired up — providers move to the end after 3 consecutive failures.
- **`DotResolver` response cap raised from 4 096 to 65 535.** RFC 7858 allows
  the full 16-bit length-prefix range; the old cap silently dropped legitimate
  large DNSSEC responses.
- **`DnsCache` honours RFC 2308 MINIMUM=0.** SOA-derived "do not cache" now
  actually skips the negative cache instead of falling back to a 60 s TTL.
  Positive-cache parse failures return TTL=0 (don't cache) instead of pinning
  the response at 5 minutes. RR scan cap raised from 20 → 100 for large CDN /
  CNAME chains. Eviction now sorts a single snapshot to avoid concurrent-map
  inconsistency.
- **`VpnService.Builder.addAddress` defensive wrap.** OEM-restricted builds that
  reject the IPv6 ULA address now degrade to IPv4-only instead of failing
  startup entirely.

### UX & polish
- **Onboarding DNS choice is now persisted.** The DNS picker on page 3 hoists
  its selection up to `OnboardingScreen` and threads it through `onComplete`;
  the chosen upstream is written to `customUpstreamDns` before the protection
  mode starts. Previously the choice was dropped on screen exit.
- **Onboarding state survives rotation.** `page` / `selectedMethod` /
  `selectedDns` switched from `remember` to `rememberSaveable`.
- **PushPin icon now shows pinned vs unpinned state visually.** Previously both
  branches drew `Icons.Filled.PushPin`; only the tint differed. Unpinned now
  draws `Icons.Outlined.PushPin`, and the content description updates.
- **Sources: `Add source` dialog validates URLs + includes category picker.**
  Previously any non-blank string was accepted, then crashed downstream; the
  unused `category` state had no UI so every user-added source was filed as
  ADS. New URL validation (`http://`/`https://` only), inline error text, and
  a FlowRow of category chips.
- **Settings update-check is throttled.** `autoCheckForUpdate` previously fired
  on every Settings open; now throttled to once per process per 24 h.

### Docs
- `CHANGELOG.md` repaired (was literal `## [v6.3.0] - %Y->-` placeholder).
- README version badge corrected (was `6.2.0` while build was `6.4.0`).

## [v6.4.0] - 2026-03-27

See [`app/CHANGELOG.md`](app/CHANGELOG.md) for the full v6.4.0 release notes.

## [v6.3.0]

- Security hardening audit, preferences refactor, DB indices, error handling.

## [v6.2.0]

- Major release — encrypted DNS, content filtering, parental controls, threat
  intel, 7 new screens.

## [v5.0.0]

- Core engine upgrades — serve-stale DNS, hash set fast path, offline GeoIP.

## [v4.6.0]

- Latency sparkline, source stats, search history, query type chart.
