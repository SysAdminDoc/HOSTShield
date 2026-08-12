package com.hostshield.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room Database Migrations
 *
 * Every schema change MUST have a corresponding migration.
 * Without these, Room crashes on startup for existing users.
 *
 * Version history:
 * - v1: Initial host_sources, user_rules, dns_logs
 * - v2: Added block_stats table
 * - v3: Added profiles table + dns_logs app columns
 * - v4: Added firewall_rules and connection_log tables
 * - v5: Added dns_logs.query_type/source_ip and indices
 * - v6: Added dns_logs.response_time_ms, dns_logs.upstream_server,
 *        dns_logs.cname_chain columns for per-query detail view
 * - v13: Composite indices for common query patterns
 * - v14: Added index on host_sources.category
 * - v15: Added host_sources.last_http_status for source failure feedback
 * - v16: Added dns_logs decision provenance columns
 * - v17: Enable default AdAway and StevenBlack Unified built-in sources
 * - v18: Added dns_logs(app_package, hostname) index for app-domain aggregation
 * - v19: Add Spotify Ads built-in source and clear disabled-source health errors
 * - v20: Move Spotify Ads to the HostShield-maintained blocklist
 * - v21: Add optional expiry timestamps to user rules
 */
object Migrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS block_stats (
                    date TEXT NOT NULL PRIMARY KEY,
                    blocked_count INTEGER NOT NULL DEFAULT 0,
                    allowed_count INTEGER NOT NULL DEFAULT 0,
                    total_queries INTEGER NOT NULL DEFAULT 0
                )
            """)
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS profiles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    is_active INTEGER NOT NULL DEFAULT 0,
                    source_ids TEXT NOT NULL DEFAULT '',
                    schedule_start TEXT NOT NULL DEFAULT '',
                    schedule_end TEXT NOT NULL DEFAULT '',
                    days_of_week TEXT NOT NULL DEFAULT '0,1,2,3,4,5,6'
                )
            """)
            try {
                db.execSQL("ALTER TABLE dns_logs ADD COLUMN app_package TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE dns_logs ADD COLUMN app_label TEXT NOT NULL DEFAULT ''")
            } catch (_: Exception) {
                // Some early builds already carried these columns.
            }
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS firewall_rules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    uid INTEGER NOT NULL,
                    package_name TEXT NOT NULL,
                    app_label TEXT NOT NULL,
                    wifi_allowed INTEGER NOT NULL DEFAULT 1,
                    mobile_allowed INTEGER NOT NULL DEFAULT 1,
                    vpn_allowed INTEGER NOT NULL DEFAULT 1,
                    is_system INTEGER NOT NULL DEFAULT 0,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    updated_at INTEGER NOT NULL DEFAULT 0
                )
            """)
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_firewall_rules_uid ON firewall_rules (uid)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS connection_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    uid INTEGER NOT NULL,
                    package_name TEXT NOT NULL,
                    app_label TEXT NOT NULL,
                    destination TEXT NOT NULL,
                    port INTEGER NOT NULL,
                    protocol TEXT NOT NULL,
                    action TEXT NOT NULL,
                    interface_name TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_connection_log_timestamp ON connection_log (timestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_connection_log_uid ON connection_log (uid)")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_dns_logs_blocked_timestamp ON dns_logs (blocked, timestamp)")
            try { db.execSQL("ALTER TABLE dns_logs ADD COLUMN source_ip TEXT NOT NULL DEFAULT ''") } catch (_: Exception) { }
            try { db.execSQL("ALTER TABLE dns_logs ADD COLUMN query_type TEXT NOT NULL DEFAULT 'A'") } catch (_: Exception) { }
            db.execSQL("CREATE INDEX IF NOT EXISTS index_dns_logs_hostname ON dns_logs (hostname)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_dns_logs_app_package ON dns_logs (app_package)")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add response_time_ms column for latency tracking
            db.execSQL("ALTER TABLE dns_logs ADD COLUMN response_time_ms INTEGER NOT NULL DEFAULT 0")
            // Add upstream_server column (which DNS server answered)
            db.execSQL("ALTER TABLE dns_logs ADD COLUMN upstream_server TEXT NOT NULL DEFAULT ''")
            // Add cname_chain column (comma-separated CNAME targets found)
            db.execSQL("ALTER TABLE dns_logs ADD COLUMN cname_chain TEXT NOT NULL DEFAULT ''")
            // Add resolved_ips column (comma-separated answer IPs)
            db.execSQL("ALTER TABLE dns_logs ADD COLUMN resolved_ips TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add wifi_ssids column for network-aware profile switching
            db.execSQL("ALTER TABLE profiles ADD COLUMN wifi_ssids TEXT NOT NULL DEFAULT ''")
            // Add is_regex column for regex pattern rules
            db.execSQL("ALTER TABLE user_rules ADD COLUMN is_regex INTEGER NOT NULL DEFAULT 0")
            // Add source changelog tracking columns
            db.execSQL("ALTER TABLE host_sources ADD COLUMN prev_entry_count INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE host_sources ADD COLUMN domains_added INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE host_sources ADD COLUMN domains_removed INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Context-aware firewall columns
            db.execSQL("ALTER TABLE firewall_rules ADD COLUMN block_screen_off INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE firewall_rules ADD COLUMN block_background INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE firewall_rules ADD COLUMN block_metered INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Tracker scan cache table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS tracker_scan_cache (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    package_name TEXT NOT NULL,
                    app_label TEXT NOT NULL DEFAULT '',
                    tracker_count INTEGER NOT NULL DEFAULT 0,
                    tracker_names TEXT NOT NULL DEFAULT '',
                    categories TEXT NOT NULL DEFAULT '',
                    scanned_at INTEGER NOT NULL DEFAULT 0,
                    app_version_code INTEGER NOT NULL DEFAULT 0
                )
            """)
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tracker_scan_cache_package_name ON tracker_scan_cache (package_name)")

            // Automation audit log table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS automation_audit_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    action TEXT NOT NULL,
                    caller_uid INTEGER NOT NULL,
                    caller_package TEXT NOT NULL DEFAULT '',
                    result TEXT NOT NULL DEFAULT 'OK',
                    timestamp INTEGER NOT NULL DEFAULT 0
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_automation_audit_log_timestamp ON automation_audit_log (timestamp)")

            // VPN stability metrics table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS vpn_stability (
                    date TEXT NOT NULL PRIMARY KEY,
                    uptime_ms INTEGER NOT NULL DEFAULT 0,
                    rebuild_count INTEGER NOT NULL DEFAULT 0,
                    fd_errors INTEGER NOT NULL DEFAULT 0,
                    dropped_queries INTEGER NOT NULL DEFAULT 0,
                    total_queries INTEGER NOT NULL DEFAULT 0
                )
            """)
        }
    }

    // v5.1: Add blocked_countries and lan_allowed columns to firewall_rules
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE firewall_rules ADD COLUMN blocked_countries TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE firewall_rules ADD COLUMN lan_allowed INTEGER NOT NULL DEFAULT 1")
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE dns_logs ADD COLUMN tracker_category TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE dns_logs ADD COLUMN tracker_owner TEXT NOT NULL DEFAULT ''")
        }
    }

    // v6.1: Domain-per-app DNS rules — per-app allow/block rules for specific domains
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS app_dns_rules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    package_name TEXT NOT NULL,
                    domain TEXT NOT NULL,
                    action TEXT NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    created_at INTEGER NOT NULL DEFAULT 0
                )
            """)
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_app_dns_rules_package_name_domain ON app_dns_rules (package_name, domain)")
        }
    }

    // v6.2.1: Add composite indices for common query patterns
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Composite index for per-app DNS log drill-down (AppLogsScreen)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_dns_logs_app_blocked_ts ON dns_logs (app_package, blocked, timestamp)")
            // Index on enabled for source/rule filtering
            db.execSQL("CREATE INDEX IF NOT EXISTS index_host_sources_enabled ON host_sources (enabled)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_user_rules_enabled_type ON user_rules (enabled, type)")
        }
    }

    // v6.2.2: Add index on host_sources.category for category-filtered queries
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_host_sources_category ON host_sources (category)")
        }
    }

    // v6.5.10: Persist the latest HTTP response code for failed source downloads
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE host_sources ADD COLUMN last_http_status INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE dns_logs ADD COLUMN decision_reason TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE dns_logs ADD COLUMN decision_source TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE dns_logs ADD COLUMN matched_value TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE dns_logs ADD COLUMN decision_precedence TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS index_dns_logs_app_blocked_ts")
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_dns_logs_app_package_blocked_timestamp
                ON dns_logs (app_package, blocked, timestamp)
                """.trimIndent()
            )
            db.execSQL(
                """
                UPDATE host_sources
                SET enabled = 1
                WHERE is_builtin = 1
                  AND (
                    label IN ('AdAway Default', 'StevenBlack Unified')
                    OR url IN (
                        'https://adaway.org/hosts.txt',
                        'https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts'
                    )
                  )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_dns_logs_app_package_hostname
                ON dns_logs (app_package, hostname)
                """.trimIndent()
            )
        }
    }

    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Disabled lists are not actionable health failures. Older builds
            // probed every source and left large, disabled lists permanently
            // marked DEAD after their 5 MiB validation read was exhausted.
            db.execSQL(
                """
                UPDATE host_sources
                SET health = 'UNKNOWN',
                    last_error = '',
                    last_http_status = 0,
                    consecutive_failures = 0
                WHERE enabled = 0
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO host_sources (
                    url,
                    label,
                    description,
                    enabled,
                    category,
                    entry_count,
                    last_updated,
                    last_modified_online,
                    etag,
                    is_builtin,
                    size_bytes,
                    health,
                    last_error,
                    last_http_status,
                    consecutive_failures,
                    prev_entry_count,
                    domains_added,
                    domains_removed
                )
                SELECT
                    'https://raw.githubusercontent.com/Mireli5656/adblock360-/refs/heads/main/lists/spotifyadlist.hosts',
                    'Spotify Ads',
                    'Aggressive Spotify ad and telemetry list. May interrupt playback or app updates. ~79 entries.',
                    0,
                    'ADS',
                    0,
                    0,
                    '',
                    '',
                    1,
                    0,
                    'UNKNOWN',
                    '',
                    0,
                    0,
                    0,
                    0,
                    0
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM host_sources
                    WHERE url = 'https://raw.githubusercontent.com/Mireli5656/adblock360-/refs/heads/main/lists/spotifyadlist.hosts'
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val legacyUrl =
                "https://raw.githubusercontent.com/Mireli5656/adblock360-/refs/heads/main/lists/spotifyadlist.hosts"
            val hostedUrl =
                "https://raw.githubusercontent.com/SysAdminDoc/HostShield/main/blocklists/SpotifyAds.txt"
            val description =
                "Aggressive Spotify ad and telemetry list maintained by HostShield. May interrupt playback or app updates. ~84 entries."

            // If the user already added the hosted URL manually, keep that row
            // and remove the now-redundant legacy built-in row.
            db.execSQL(
                """
                UPDATE host_sources
                SET label = 'Spotify Ads',
                    description = ?,
                    category = 'ADS',
                    is_builtin = 1,
                    entry_count = 0,
                    last_updated = 0,
                    last_modified_online = '',
                    etag = '',
                    size_bytes = 0,
                    health = 'UNKNOWN',
                    last_error = '',
                    last_http_status = 0,
                    consecutive_failures = 0,
                    prev_entry_count = 0,
                    domains_added = 0,
                    domains_removed = 0
                WHERE url = ?
                """.trimIndent(),
                arrayOf(description, hostedUrl)
            )
            db.execSQL(
                """
                DELETE FROM host_sources
                WHERE url = ?
                  AND EXISTS (
                    SELECT 1
                    FROM host_sources
                    WHERE url = ?
                  )
                """.trimIndent(),
                arrayOf(legacyUrl, hostedUrl)
            )
            db.execSQL(
                """
                UPDATE host_sources
                SET url = ?,
                    label = 'Spotify Ads',
                    description = ?,
                    category = 'ADS',
                    is_builtin = 1,
                    entry_count = 0,
                    last_updated = 0,
                    last_modified_online = '',
                    etag = '',
                    size_bytes = 0,
                    health = 'UNKNOWN',
                    last_error = '',
                    last_http_status = 0,
                    consecutive_failures = 0,
                    prev_entry_count = 0,
                    domains_added = 0,
                    domains_removed = 0
                WHERE url = ?
                """.trimIndent(),
                arrayOf(hostedUrl, description, legacyUrl)
            )
        }
    }

    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE user_rules ADD COLUMN expires_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_user_rules_expires_at ON user_rules (expires_at)")
        }
    }

    /** All migrations in order. Pass to Room.databaseBuilder().addMigrations(). */
    val ALL = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19,
        MIGRATION_19_20,
        MIGRATION_20_21
    )
}
