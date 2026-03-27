package com.hostshield.data.model

import androidx.room.*

// HostShield v1.6.0 - Data Models

enum class SourceCategory {
    ADS, TRACKERS, MALWARE, ADULT, SOCIAL, CRYPTO, ALLOWLIST, CUSTOM
}

enum class RuleType {
    BLOCK, ALLOW, REDIRECT
}

enum class BlockMethod {
    ROOT_HOSTS, VPN, DNS_PROXY, DISABLED
}

enum class SourceHealth {
    UNKNOWN, OK, STALE, ERROR, DEAD
}

@Entity(
    tableName = "host_sources",
    indices = [
        Index(value = ["enabled"]),
        Index(value = ["category"])
    ]
)
data class HostSource(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "url") val url: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "category") val category: SourceCategory = SourceCategory.ADS,
    @ColumnInfo(name = "entry_count") val entryCount: Int = 0,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long = 0L,
    @ColumnInfo(name = "last_modified_online") val lastModifiedOnline: String = "",
    @ColumnInfo(name = "etag") val etag: String = "",
    @ColumnInfo(name = "is_builtin") val isBuiltin: Boolean = false,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long = 0L,
    @ColumnInfo(name = "health") val health: SourceHealth = SourceHealth.UNKNOWN,
    @ColumnInfo(name = "last_error") val lastError: String = "",
    @ColumnInfo(name = "consecutive_failures") val consecutiveFailures: Int = 0,
    @ColumnInfo(name = "prev_entry_count") val prevEntryCount: Int = 0, // entry count before last update
    @ColumnInfo(name = "domains_added") val domainsAdded: Int = 0,     // new domains in last update
    @ColumnInfo(name = "domains_removed") val domainsRemoved: Int = 0  // removed domains in last update
)

@Entity(
    tableName = "user_rules",
    indices = [
        Index(value = ["hostname"], unique = true),
        Index(value = ["enabled", "type"])
    ]
)
data class UserRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "hostname") val hostname: String,
    @ColumnInfo(name = "type") val type: RuleType = RuleType.BLOCK,
    @ColumnInfo(name = "redirect_ip") val redirectIp: String = "",
    @ColumnInfo(name = "comment") val comment: String = "",
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "is_wildcard") val isWildcard: Boolean = false,
    @ColumnInfo(name = "is_regex") val isRegex: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "dns_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["blocked", "timestamp"]),  // composite for filtered log queries
        Index(value = ["hostname"]),
        Index(value = ["app_package"]),
        Index(value = ["app_package", "blocked", "timestamp"])  // composite for per-app drill-down
    ]
)
data class DnsLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "hostname") val hostname: String,
    @ColumnInfo(name = "blocked") val blocked: Boolean,
    @ColumnInfo(name = "app_package") val appPackage: String = "",
    @ColumnInfo(name = "app_label") val appLabel: String = "",
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "source_ip") val sourceIp: String = "",
    @ColumnInfo(name = "query_type") val queryType: String = "A",
    @ColumnInfo(name = "response_time_ms") val responseTimeMs: Int = 0,
    @ColumnInfo(name = "upstream_server") val upstreamServer: String = "",
    @ColumnInfo(name = "cname_chain") val cnameChain: String = "",   // comma-separated CNAME targets
    @ColumnInfo(name = "resolved_ips") val resolvedIps: String = "",  // comma-separated answer IPs
    @ColumnInfo(name = "tracker_category") val trackerCategory: String = "",  // v6.0: network tracker category (Advertising, Analytics, etc.)
    @ColumnInfo(name = "tracker_owner") val trackerOwner: String = ""  // v6.0: tracker owner (Google, Facebook, etc.)
)

@Entity(tableName = "block_stats")
data class BlockStats(
    @PrimaryKey val date: String, // yyyy-MM-dd
    @ColumnInfo(name = "blocked_count") val blockedCount: Int = 0,
    @ColumnInfo(name = "allowed_count") val allowedCount: Int = 0,
    @ColumnInfo(name = "total_queries") val totalQueries: Int = 0
)

@Entity(tableName = "profiles")
data class BlockingProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean = false,
    @ColumnInfo(name = "source_ids") val sourceIds: String = "",
    @ColumnInfo(name = "schedule_start") val scheduleStart: String = "",
    @ColumnInfo(name = "schedule_end") val scheduleEnd: String = "",
    @ColumnInfo(name = "days_of_week") val daysOfWeek: String = "0,1,2,3,4,5,6",
    @ColumnInfo(name = "wifi_ssids") val wifiSsids: String = "" // Comma-separated SSIDs to auto-activate on
)

@Entity(
    tableName = "firewall_rules",
    indices = [Index(value = ["uid"], unique = true)]
)
data class FirewallRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "uid") val uid: Int,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_label") val appLabel: String,
    @ColumnInfo(name = "wifi_allowed") val wifiAllowed: Boolean = true,
    @ColumnInfo(name = "mobile_allowed") val mobileAllowed: Boolean = true,
    @ColumnInfo(name = "vpn_allowed") val vpnAllowed: Boolean = true,
    @ColumnInfo(name = "is_system") val isSystem: Boolean = false,
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    // Context-aware firewall: block when screen is off or app is in background
    @ColumnInfo(name = "block_screen_off") val blockScreenOff: Boolean = false,
    @ColumnInfo(name = "block_background") val blockBackground: Boolean = false,
    @ColumnInfo(name = "block_metered") val blockMetered: Boolean = false,
    // v5.1: Country-based blocking — comma-separated ISO country codes (e.g., "CN,RU,IR")
    // When non-empty, connections to IPs in these countries are blocked for this app.
    @ColumnInfo(name = "blocked_countries", defaultValue = "") val blockedCountries: String = "",
    // v5.1: LAN access control — allow/deny local network while blocking internet
    // Inspired by AFWall+'s dedicated LAN toggle per app.
    @ColumnInfo(name = "lan_allowed", defaultValue = "1") val lanAllowed: Boolean = true
)

@Entity(
    tableName = "connection_log",
    indices = [Index(value = ["timestamp"]), Index(value = ["uid"])]
)
data class ConnectionLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "uid") val uid: Int,
    @ColumnInfo(name = "package_name") val packageName: String = "",
    @ColumnInfo(name = "app_label") val appLabel: String = "",
    @ColumnInfo(name = "destination") val destination: String = "",
    @ColumnInfo(name = "port") val port: Int = 0,
    @ColumnInfo(name = "protocol") val protocol: String = "TCP",
    @ColumnInfo(name = "action") val action: String = "REJECT", // REJECT, ALLOW
    @ColumnInfo(name = "interface_name") val interfaceName: String = "", // wlan0, rmnet0
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "tracker_scan_cache",
    indices = [Index(value = ["package_name"], unique = true)]
)
data class TrackerScanCacheEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_label") val appLabel: String = "",
    @ColumnInfo(name = "tracker_count") val trackerCount: Int = 0,
    @ColumnInfo(name = "tracker_names") val trackerNames: String = "",      // comma-separated
    @ColumnInfo(name = "categories") val categories: String = "",           // comma-separated
    @ColumnInfo(name = "scanned_at") val scannedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "app_version_code") val appVersionCode: Long = 0     // invalidate on app update
)

@Entity(
    tableName = "app_dns_rules",
    indices = [Index(value = ["package_name", "domain"], unique = true)]
)
data class AppDnsRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "domain") val domain: String,      // domain pattern (exact or wildcard like *.facebook.com)
    @ColumnInfo(name = "action") val action: String,       // "block" or "allow"
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "automation_audit_log",
    indices = [Index(value = ["timestamp"])]
)
data class AutomationAuditEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "action") val action: String,
    @ColumnInfo(name = "caller_uid") val callerUid: Int,
    @ColumnInfo(name = "caller_package") val callerPackage: String = "",
    @ColumnInfo(name = "result") val result: String = "OK", // OK, DENIED, ERROR
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "vpn_stability")
data class VpnStabilityEntry(
    @PrimaryKey val date: String, // yyyy-MM-dd
    @ColumnInfo(name = "uptime_ms") val uptimeMs: Long = 0,
    @ColumnInfo(name = "rebuild_count") val rebuildCount: Int = 0,
    @ColumnInfo(name = "fd_errors") val fdErrors: Int = 0,
    @ColumnInfo(name = "dropped_queries") val droppedQueries: Int = 0,
    @ColumnInfo(name = "total_queries") val totalQueries: Int = 0
)
