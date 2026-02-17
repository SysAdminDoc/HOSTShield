package com.hostshield.data.model

import androidx.room.*

// HostShield v1.6.0 - Data Models

enum class SourceCategory {
    ADS, TRACKERS, MALWARE, ADULT, SOCIAL, CRYPTO, CUSTOM
}

enum class RuleType {
    BLOCK, ALLOW, REDIRECT
}

enum class BlockMethod {
    ROOT_HOSTS, VPN, DISABLED
}

enum class SourceHealth {
    UNKNOWN, OK, STALE, ERROR, DEAD
}

@Entity(tableName = "host_sources")
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
    @ColumnInfo(name = "consecutive_failures") val consecutiveFailures: Int = 0
)

@Entity(
    tableName = "user_rules",
    indices = [Index(value = ["hostname"], unique = true)]
)
data class UserRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "hostname") val hostname: String,
    @ColumnInfo(name = "type") val type: RuleType = RuleType.BLOCK,
    @ColumnInfo(name = "redirect_ip") val redirectIp: String = "",
    @ColumnInfo(name = "comment") val comment: String = "",
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "is_wildcard") val isWildcard: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "dns_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["blocked", "timestamp"]),  // composite for filtered log queries
        Index(value = ["hostname"]),
        Index(value = ["app_package"])
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
    @ColumnInfo(name = "query_type") val queryType: String = "A"
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
    @ColumnInfo(name = "days_of_week") val daysOfWeek: String = "0,1,2,3,4,5,6"
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
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
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
