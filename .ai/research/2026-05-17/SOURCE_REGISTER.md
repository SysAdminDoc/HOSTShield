# Source Register

Research date: 2026-05-17

## Local Sources

| ID | Source | Used for |
|---|---|---|
| L001 | `AGENTS.md` | Repo agent entrypoint and instruction delegation. |
| L002 | `CLAUDE.md` | Current repo architecture, version history, build commands, gotchas. |
| L003 | `README.md` | Public product positioning, feature claims, doc drift audit. |
| L004 | `CHANGELOG.md` | Current release chronology through v6.5.9. |
| L005 | `ROADMAP.md` before refresh | Existing priorities, source IDs, stale baseline detection. |
| L006 | `docs/RESEARCH.md` | Prior competitive research and completed v5-v6 roadmap. |
| L007 | `docs/WORKMANAGER_AUDIT.md` | Background work and Doze/App Standby hardening evidence. |
| L008 | `app/app/build.gradle.kts` | Version, SDKs, plugins, dependencies, flavors, signing. |
| L009 | `app/build.gradle.kts` | Root plugin versions and stale comment check. |
| L010 | `app/settings.gradle.kts` | Repository policy, JitPack dependency surface. |
| L011 | `.github/workflows/release.yml` | Release build and APK upload workflow. |
| L012 | `app/app/src/main/AndroidManifest.xml` | Permissions, exported components, foreground service types, file provider. |
| L013 | `app/app/src/main/java/com/hostshield/service/DnsVpnService.kt` | VPN architecture, DNS traps, encrypted resolver dispatch, heartbeat. |
| L014 | `app/app/src/main/java/com/hostshield/domain/BlocklistHolder.kt` | Block engine, trie/cache design, DoH bypass domains. |
| L015 | `app/app/src/main/java/com/hostshield/service/DohResolver.kt` | DoH3-first, pinned DoH fallback, fail-closed behavior. |
| L016 | `app/app/src/main/java/com/hostshield/service/Doh3Resolver.kt` | Cronet HTTP/3 transport. |
| L017 | `app/app/src/main/java/com/hostshield/service/DotResolver.kt` | DNS-over-TLS path. |
| L018 | `app/app/src/main/java/com/hostshield/service/DoqResolver.kt` | Experimental DoQ limitations. |
| L019 | `app/app/src/main/java/com/hostshield/service/WireGuardProxy.kt` | Experimental DNS-over-WireGuard limitations. |
| L020 | `app/app/src/main/java/com/hostshield/util/DnsStampParser.kt` | DNS stamp formats and relay parsing. |
| L021 | `app/app/src/main/java/com/hostshield/service/DnsCryptRoutePlanner.kt` | DNSCrypt relay route validation and wrapping. |
| L022 | `app/app/src/main/java/com/hostshield/data/preferences/SecureStore.kt` | EncryptedSharedPreferences, PBKDF2 PIN hashing. |
| L023 | `app/app/src/main/java/com/hostshield/util/BackupCrypto.kt` | AES-GCM backup crypto and PBKDF2 iteration count. |
| L024 | `app/app/src/main/java/com/hostshield/data/database/HostShieldDatabase.kt` | Room entities and DB version. |
| L025 | `app/app/src/main/java/com/hostshield/data/database/Migrations.kt` | v5-v14 migration chain. |
| L026 | `app/app/src/main/java/com/hostshield/di/DatabaseModule.kt` | v1-v5 migration chain and no destructive fallback. |
| L027 | `app/app/src/main/java/com/hostshield/util/TrackerSignatureDb.kt` | 405 tracker signature count and APK scanner design. |
| L028 | `app/app/src/main/java/com/hostshield/util/GeoIpLookup.kt` | ipapi.co fallback and rate limiting. |
| L029 | `app/app/src/main/assets/curated_blocklists.json` | Current curated gallery size and sources. |
| L030 | `blocklists/` | Bundled static blocklists and local entries. |
| L031 | `app/app/src/test/java/...` | Current JVM test classes. |

## Memory Sources

| ID | Source | Used for |
|---|---|---|
| M001 | `C:/Users/--/.claude/projects/c--Users----repos/memory/MEMORY.md` | HostShield active project pointer and stack memory lookup. |
| M002 | `C:/Users/--/.claude/projects/c--Users----repos/memory/hostshield.md` | Historical v6.3.0 memory, marked stale. |
| M003 | `C:/Users/--/.claude/projects/c--Users----repos/memory/stack-android.md` | Android stack conventions. |
| M004 | `C:/Users/--/.claude/projects/c--Users----repos/memory/android-apk.md` | APK release and signing process. |
| M005 | `C:/Users/--/.codex/memories/MEMORY.md` | Prior HostShield v6.5.1 release-flow memory pointer. |
| M006 | `C:/Users/--/.codex/memories/rollout_summaries/2026-05-13T15-00-58-zz9c-hostshield_v6_5_1_signed_release_and_adb_reinstall.md` | Signed release and adb reinstall flow. |

## External Sources

| ID | Source | URL | Used for |
|---|---|---|---|
| E001 | Android VpnService reference | https://developer.android.com/reference/android/net/VpnService | VPN service behavior and always-on/lockdown context. |
| E002 | Android VpnService.Builder reference | https://developer.android.com/reference/android/net/VpnService.Builder | Route/address API behavior. |
| E003 | Android Doze and App Standby docs | https://developer.android.com/training/monitoring-device-state/doze-standby | Background reliability roadmap. |
| E004 | Android foreground service type changes | https://developer.android.com/about/versions/14/changes/fgs-types-required | Foreground service compliance. |
| E005 | Android package visibility docs | https://developer.android.com/training/package-visibility | Full vs Play flavor policy implications. |
| E006 | Android App Bundle docs | https://developer.android.com/guide/app-bundle | Play distribution and AAB roadmap. |
| E007 | Android build docs | https://developer.android.com/build | Build variants, signing, AGP. |
| E008 | AndroidX releases | https://developer.android.com/jetpack/androidx/releases | Dependency refresh planning. |
| E009 | AndroidX Security Crypto reference | https://developer.android.com/reference/kotlin/androidx/security/crypto/package-summary | EncryptedSharedPreferences migration risk. |
| E010 | Room migration docs | https://developer.android.com/training/data-storage/room/migrating-db-versions | Migration test roadmap. |
| E011 | Compose accessibility docs | https://developer.android.com/develop/ui/compose/accessibility | TalkBack and semantics roadmap. |
| E012 | Compose testing docs | https://developer.android.com/develop/ui/compose/testing | UI test roadmap. |
| E013 | Compose BOM docs | https://developer.android.com/develop/ui/compose/bom | Compose update cadence. |
| E014 | Material 3 Expressive | https://m3.material.io/blog/material-3-expressive | Future UI direction. |
| E015 | RethinkDNS app repository | https://github.com/celzero/rethink-app | Direct Android competitor and feature comparison. |
| E016 | RethinkDNS releases | https://github.com/celzero/rethink-app/releases | Current release features and cadence. |
| E017 | RethinkDNS firestack | https://github.com/celzero/firestack | Tun2socks/firewall architecture option. |
| E018 | Jigsaw outline-go-tun2socks | https://github.com/Jigsaw-Code/outline-go-tun2socks | firestack lineage and Go TUN layer prior art. |
| E019 | AdGuard for Android repository | https://github.com/AdguardTeam/AdguardForAndroid | Commercial Android competitor. |
| E020 | AdGuard Home repository | https://github.com/AdguardTeam/AdGuardHome | DNS server competitor and API ideas. |
| E021 | AdGuard Home changelog | https://github.com/AdguardTeam/AdGuardHome/blob/master/CHANGELOG.md | Server-side feature ideas. |
| E022 | AdGuard DNS filter | https://github.com/AdguardTeam/AdGuardSDNSFilter | DNS filter source landscape. |
| E023 | AdGuard Hostlists Registry | https://github.com/AdguardTeam/HostlistsRegistry | Curated source metadata model. |
| E024 | Pi-hole repository | https://github.com/pi-hole/pi-hole | Server-side DNS blocker benchmark. |
| E025 | Pi-hole docs | https://docs.pi-hole.net/ | Gravity, allowlists, admin concepts. |
| E026 | Hagezi DNS blocklists | https://github.com/hagezi/dns-blocklists | Tiered pack and diff pipeline opportunities; refreshed against upstream README and list headers on 2026-05-17 for HaGeZi pack URLs, size expectations, and warnings. |
| E027 | OISD | https://oisd.nl/ | List format and blocklist ecosystem trend. |
| E028 | StevenBlack hosts | https://github.com/StevenBlack/hosts | Major hosts list source. |
| E029 | FilterLists | https://github.com/collinbarrett/FilterLists | Source directory / metadata reference. |
| E030 | NetGuard repository | https://github.com/M66B/NetGuard | Android VPN firewall prior art. |
| E031 | AdAway repository | https://github.com/AdAway/AdAway | Root hosts and systemless hosts prior art. |
| E032 | DNS66 repository | https://github.com/julian-klode/dns66 | Minimal DNS-only VPN prior art. |
| E033 | personalDNSfilter repository | https://github.com/IngoZenz/personaldnsfilter | DNS filter cache and LAN proxy ideas. |
| E034 | InviZible Pro repository | https://github.com/Gedsh/InviZible | DNSCrypt/Tor/I2P/root mode competitor. |
| E035 | Nebulo repository | https://github.com/Ch4t4r/Nebulo | DNS stamps and encrypted DNS competitor. |
| E036 | Intra repository | https://github.com/Jigsaw-Code/Intra | Simple Android encrypted DNS reference. |
| E037 | DNSCrypt proxy repository | https://github.com/DNSCrypt/dnscrypt-proxy | DNSCrypt implementation reference. |
| E038 | DNSCrypt stamps implementation | https://github.com/DNSCrypt/dnscrypt-proxy/blob/master/vendor/github.com/jedisct1/go-dnsstamps/dnsstamps.go | Stamp protocol constants and property width. |
| E039 | DNSCrypt block-name plugin | https://github.com/DNSCrypt/dnscrypt-proxy/blob/master/dnscrypt-proxy/plugin_block_name.go | Blocklist matching semantics for DNSCrypt engine. |
| E040 | DNSCrypt stamp spec wiki | https://github.com/DNSCrypt/dnscrypt-proxy/wiki/DNS-Stamp-specifiers | Stamp format reference. |
| E041 | Anonymized DNSCrypt wiki | https://github.com/DNSCrypt/dnscrypt-proxy/wiki/Anonymized-DNSCrypt | Relay privacy model. |
| E079 | DNSCrypt protocol draft | https://dnscrypt.github.io/dnscrypt-protocol/draft-denis-dprive-dnscrypt.html | DNSCrypt protocol, certificates, nonce, and anonymized relay requirements. |
| E080 | Go Mobile documentation | https://go.dev/wiki/Mobile | `gomobile bind` Android AAR packaging path and limitations. |
| E081 | libsodium ChaCha20-Poly1305 docs | https://doc.libsodium.org/secret-key_cryptography/aead/chacha20-poly1305 | Native primitive availability for fallback analysis. |
| E082 | libsodium XChaCha20 docs | https://doc.libsodium.org/advanced/stream_ciphers/xchacha20 | XChaCha20 implementation and nonce context for fallback analysis. |
| E042 | RFC 8484 DNS over HTTPS | https://www.rfc-editor.org/rfc/rfc8484 | DoH protocol. |
| E043 | RFC 7858 DNS over TLS | https://www.rfc-editor.org/rfc/rfc7858 | DoT protocol. |
| E044 | RFC 9250 DNS over QUIC | https://www.rfc-editor.org/rfc/rfc9250 | DoQ protocol. |
| E045 | RFC 9484 DoH over HTTP/3 | https://www.rfc-editor.org/rfc/rfc9484 | DoH3 protocol. |
| E046 | RFC 9230 ODoH | https://www.rfc-editor.org/rfc/rfc9230 | Oblivious DoH protocol. |
| E047 | RFC 8914 Extended DNS Errors | https://www.rfc-editor.org/rfc/rfc8914 | EDE parsing and query-log roadmap. |
| E048 | RFC 7766 DNS over TCP | https://www.rfc-editor.org/rfc/rfc7766 | TCP fallback and truncation behavior. |
| E049 | RFC 8767 Serve Stale | https://www.rfc-editor.org/rfc/rfc8767 | Cache resilience. |
| E050 | RFC 9520 negative caching update | https://www.rfc-editor.org/rfc/rfc9520 | Failure caching policy. |
| E051 | RFC 9460 SVCB/HTTPS | https://www.rfc-editor.org/rfc/rfc9460 | ECH/SVCB awareness. |
| E052 | OWASP Password Storage Cheat Sheet | https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html | Argon2id and PBKDF2 review. |
| E053 | NIST SP 800-38D | https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-38d.pdf | AES-GCM nonce uniqueness requirements. |
| E054 | OkHttp changelog | https://github.com/square/okhttp/blob/master/CHANGELOG.md | OkHttp 5 upgrade planning. |
| E055 | libsu repository | https://github.com/topjohnwu/libsu | Root shell dependency. |
| E056 | Magisk releases | https://github.com/topjohnwu/Magisk/releases | Root ecosystem behavior. |
| E057 | Chromium Cronet docs | https://developer.android.com/develop/connectivity/cronet | DoH3 transport context. |
| E058 | Gradle releases | https://docs.gradle.org/current/release-notes.html | Build tool refresh. |
| E059 | Android Gradle Plugin release notes | https://developer.android.com/build/releases/gradle-plugin | AGP refresh. |
| E060 | Kotlin releases | https://kotlinlang.org/docs/releases.html | Kotlin refresh. |
| E061 | Hilt releases | https://github.com/google/dagger/releases | Dependency refresh. |
| E062 | WorkManager releases | https://developer.android.com/jetpack/androidx/releases/work | Worker dependency refresh. |
| E063 | Vico repository | https://github.com/patrykandpatrick/vico | Chart dependency. |
| E064 | Lottie Android repository | https://github.com/airbnb/lottie-android | Animation dependency. |
| E065 | ZXing repository | https://github.com/zxing/zxing | QR dependency. |
| E066 | MaxMind GeoIP2 Java repository | https://github.com/maxmind/GeoIP2-java | Offline GeoIP dependency. |
| E067 | Exodus Privacy reports | https://reports.exodus-privacy.eu.org/ | Tracker signature ecosystem. |
| E068 | Exodus Privacy exodus repository | https://github.com/Exodus-Privacy/exodus | Tracker database and scanner reference. |
| E069 | DuckDuckGo Tracker Radar | https://github.com/duckduckgo/tracker-radar | Tracker domain dataset opportunity. |
| E070 | URLhaus | https://urlhaus.abuse.ch/ | Threat intel feed reference. |
| E071 | Spamhaus DROP | https://www.spamhaus.org/drop/ | IP threat intel feed reference. |
| E072 | Emerging Threats rules | https://rules.emergingthreats.net/ | Threat intel source. |
| E073 | Reproducible Builds | https://reproducible-builds.org/ | Release reproducibility roadmap. |
| E074 | IzzyOnDroid | https://apt.izzysoft.de/fdroid/ | Android sideload distribution channel. |
| E075 | Obtainium | https://github.com/ImranR98/Obtainium | GitHub release install UX. |
| E076 | GrapheneOS network features | https://grapheneos.org/features#network | Hardened Android compatibility. |
| E077 | GrapheneOS VPN article | https://grapheneos.org/articles/attacks-against-vpns | VPN threat model. |
| E078 | Tailscale Android | https://github.com/tailscale/tailscale-android | VPN coexistence docs. |
| E079 | uBlock Origin | https://github.com/gorhill/uBlock | Browser extension comparison. |
| E080 | GitHub API repo metadata snapshot | https://api.github.com/repos/celzero/rethink-app | Live competitor metadata sample. |
