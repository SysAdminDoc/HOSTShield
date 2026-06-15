# WorkManager and Foreground-Service Audit

Last audited for HostShield v6.9.11 on 2026-06-15.

## Foreground Services

| Service | Manifest type | Runtime type | Notes |
|---|---|---|---|
| `DnsVpnService` | `systemExempted` | `FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED` on Android 14+ | Local VPN DNS filtering, watchdog, 60-second heartbeat. |
| `RootDnsService` | `systemExempted` | `FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED` on Android 14+ | Root-mode local DNS proxy and logger. |
| `DnsProxyService` | `systemExempted` | `FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED` on Android 14+ | Local DNS proxy mode. |

`specialUse` is no longer used for HostShield's protection services. The app
declares `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` for Android 14+ foreground-service
type enforcement.

## WorkManager Jobs

| Worker | Schedule | Constraints | Doze/App Standby behavior |
|---|---|---|---|
| `HostsUpdateWorker` | Periodic by user interval; immediate one-shot via shortcuts/automation/settings | Connected or unmetered when configured | Immediate refresh uses `setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)`. Periodic refresh remains constrained and backoff-managed. |
| `SourceHealthWorker` | Periodic 6-hour source health check; one-shot manual check | Connected | Non-urgent diagnostics; safe to defer under Doze. |
| `LogCleanupWorker` | Daily cleanup | None | Maintenance-only cleanup; safe to defer. |
| `ProfileScheduleWorker` | 15-minute profile evaluation | None | Applies scheduled profiles and restarts active protection if needed. |
| `BlockingScheduleWorker` | 15-minute schedule enforcement when enabled | None | Starts/stops blocking windows from user profiles. |
| `PauseResumeWorker` | One-shot resume after user pause duration | None | Replaces broadcast `goAsync()` sleeps so long pauses survive process death and Doze. |
| `ThreatIntelWorker` | Daily refresh | Connected | Background feed refresh; safe to defer. |
| `AutoBackupWorker` | Weekly backup | None | Local backup maintenance; safe to defer. |

No direct `JobScheduler` usage exists in the source tree; scheduling is routed
through WorkManager.

## VPN Heartbeat

`DnsVpnService` now runs a 60-second in-process heartbeat and a matching
60-second watchdog alarm. The heartbeat asserts that the TUN file descriptor is
still valid while the VPN is marked running. If the fd is invalid, HostShield
logs a structured JSON event:

```json
{"event":"vpn_heartbeat_failed","timestamp_ms":0,"reason":"tun_fd_invalid","uptime_ms":0,"action":"restart"}
```

If Android restarts the service through the watchdog while preferences still
say protection should be enabled, HostShield logs:

```json
{"event":"vpn_os_kill","timestamp_ms":0,"source":"watchdog","action":"restart"}
```
