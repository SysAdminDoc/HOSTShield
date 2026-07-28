package com.hostshield.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostShieldMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HostShieldDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanup() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migrationsAreContinuousFromV1ToCurrent() {
        assertEquals(1, Migrations.ALL.first().startVersion)
        assertEquals(HOST_SHIELD_DATABASE_VERSION, Migrations.ALL.last().endVersion)
        for (i in 0 until Migrations.ALL.lastIndex) {
            val left = Migrations.ALL[i]
            val right = Migrations.ALL[i + 1]
            assertEquals(left.endVersion, right.startVersion)
        }
    }

    @Test
    fun migratesEveryHistoricalFixtureToCurrentSchema() {
        HistoricalRoomFixtures.supportedStartVersions.forEach { version ->
            context.deleteDatabase(TEST_DB)
            createHistoricalDatabase(version)

            helper.runMigrationsAndValidate(
                TEST_DB,
                HOST_SHIELD_DATABASE_VERSION,
                true,
                *Migrations.ALL
            ).use { db ->
                assertCurrentSchemaColumns(db)
                assertSeedDataSurvived(db, version)
            }
        }
    }

    @Test
    fun roomBuilderRejectsMissingMigrationPaths() {
        context.deleteDatabase(TEST_DB)
        createHistoricalDatabase(14)

        val db = Room.databaseBuilder(context, HostShieldDatabase::class.java, TEST_DB).build()
        try {
            try {
                db.openHelper.writableDatabase
                throw AssertionError("Room opened a v14 database without explicit migrations")
            } catch (expected: IllegalStateException) {
                assertTrue(expected.message.orEmpty().contains("migration", ignoreCase = true))
            }
        } finally {
            db.close()
        }
    }

    private fun createHistoricalDatabase(version: Int) {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    HistoricalRoomFixtures.create(db, version)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        FrameworkSQLiteOpenHelperFactory().create(config).use { helper ->
            helper.writableDatabase.close()
        }
    }

    private fun assertCurrentSchemaColumns(db: SupportSQLiteDatabase) {
        assertColumn(db, "host_sources", "last_http_status")
        assertColumn(db, "host_sources", "prev_entry_count")
        assertColumn(db, "dns_logs", "tracker_category")
        assertColumn(db, "dns_logs", "tracker_owner")
        assertColumn(db, "dns_logs", "decision_reason")
        assertColumn(db, "dns_logs", "decision_source")
        assertColumn(db, "dns_logs", "matched_value")
        assertColumn(db, "dns_logs", "decision_precedence")
        assertColumn(db, "profiles", "wifi_ssids")
        assertColumn(db, "firewall_rules", "blocked_countries")
        assertColumn(db, "firewall_rules", "lan_allowed")
        assertColumn(db, "app_dns_rules", "created_at")
        assertIndex(db, "index_dns_logs_app_package_blocked_timestamp")
        assertIndex(db, "index_dns_logs_app_package_hostname")
        assertIndex(db, "index_host_sources_enabled")
        assertIndex(db, "index_host_sources_category")
        assertIndex(db, "index_user_rules_enabled_type")
    }

    private fun assertSeedDataSurvived(db: SupportSQLiteDatabase, version: Int) {
        assertEquals(1, db.queryInt("SELECT COUNT(*) FROM host_sources WHERE url = 'https://fixture.hostshield.test/hosts.txt'"))
        assertEquals(1, db.queryInt("SELECT enabled FROM host_sources WHERE label = 'AdAway Default'"))
        assertEquals(1, db.queryInt("SELECT enabled FROM host_sources WHERE label = 'StevenBlack Unified'"))
        assertEquals(1, db.queryInt("SELECT COUNT(*) FROM host_sources WHERE label = 'Spotify Ads'"))
        assertEquals(0, db.queryInt("SELECT enabled FROM host_sources WHERE label = 'Spotify Ads'"))
        assertEquals("UNKNOWN", db.queryString("SELECT health FROM host_sources WHERE label = 'Disabled Dead Fixture'"))
        assertEquals("", db.queryString("SELECT last_error FROM host_sources WHERE label = 'Disabled Dead Fixture'"))
        assertEquals(0, db.queryInt("SELECT consecutive_failures FROM host_sources WHERE label = 'Disabled Dead Fixture'"))
        assertEquals(0, db.queryInt("SELECT last_http_status FROM host_sources WHERE label = 'Disabled Dead Fixture'"))
        assertEquals(1, db.queryInt("SELECT COUNT(*) FROM user_rules WHERE hostname = 'ads.fixture.test'"))
        assertEquals(1, db.queryInt("SELECT COUNT(*) FROM dns_logs WHERE hostname = 'ads.fixture.test'"))
        assertEquals(
            if (version >= 16) "source_list" else "",
            db.queryString("SELECT decision_reason FROM dns_logs WHERE hostname = 'ads.fixture.test'")
        )
        assertEquals(
            if (version >= 16) "Fixture Hosts" else "",
            db.queryString("SELECT decision_source FROM dns_logs WHERE hostname = 'ads.fixture.test'")
        )
        assertEquals(0, db.queryInt("SELECT last_http_status FROM host_sources WHERE url = 'https://fixture.hostshield.test/hosts.txt'"))

        if (version >= 2) {
            assertEquals(7, db.queryInt("SELECT blocked_count FROM block_stats WHERE date = '2026-05-17'"))
        }
        if (version >= 3) {
            assertEquals(1, db.queryInt("SELECT COUNT(*) FROM profiles WHERE name = 'fixture-profile'"))
        }
        if (version >= 4) {
            assertEquals(1, db.queryInt("SELECT COUNT(*) FROM firewall_rules WHERE package_name = 'com.fixture.app'"))
            assertEquals(1, db.queryInt("SELECT COUNT(*) FROM connection_log WHERE destination = '203.0.113.10'"))
        }
        if (version >= 9) {
            assertEquals(1, db.queryInt("SELECT COUNT(*) FROM tracker_scan_cache WHERE package_name = 'com.fixture.app'"))
            assertEquals(1, db.queryInt("SELECT COUNT(*) FROM automation_audit_log WHERE action = 'STATUS'"))
            assertEquals(1, db.queryInt("SELECT COUNT(*) FROM vpn_stability WHERE date = '2026-05-17'"))
        }
        if (version >= 12) {
            assertEquals(1, db.queryInt("SELECT COUNT(*) FROM app_dns_rules WHERE package_name = 'com.fixture.app'"))
        }
    }

    private fun assertColumn(db: SupportSQLiteDatabase, table: String, column: String) {
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == column) {
                    found = true
                    break
                }
            }
            assertTrue("$table.$column missing", found)
        }
    }

    private fun assertIndex(db: SupportSQLiteDatabase, name: String) {
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?", arrayOf(name)).use { cursor ->
            assertTrue("$name missing", cursor.moveToFirst())
        }
    }

    private fun SupportSQLiteDatabase.queryInt(sql: String): Int {
        query(sql).use { cursor ->
            assertTrue("No result for $sql", cursor.moveToFirst())
            return cursor.getInt(0)
        }
    }

    private fun SupportSQLiteDatabase.queryString(sql: String): String {
        query(sql).use { cursor ->
            assertTrue("No result for $sql", cursor.moveToFirst())
            return cursor.getString(0)
        }
    }

    private object HistoricalRoomFixtures {
        val supportedStartVersions = 1 until HOST_SHIELD_DATABASE_VERSION

        fun create(db: SupportSQLiteDatabase, version: Int) {
            require(version in supportedStartVersions)
            createHostSources(db, version)
            createUserRules(db, version)
            createDnsLogs(db, version)
            if (version >= 2) createBlockStats(db)
            if (version >= 3) createProfiles(db, version)
            if (version >= 4) {
                createFirewallRules(db, version)
                createConnectionLog(db, version)
            }
            if (version >= 9) {
                createTrackerScanCache(db)
                createAutomationAuditLog(db)
                createVpnStability(db)
            }
            if (version >= 12) createAppDnsRules(db)
        }

        private fun createHostSources(db: SupportSQLiteDatabase, version: Int) {
            val columns = mutableListOf(
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL",
                "url TEXT NOT NULL",
                "label TEXT NOT NULL",
                "description TEXT NOT NULL",
                "enabled INTEGER NOT NULL",
                "category TEXT NOT NULL",
                "entry_count INTEGER NOT NULL",
                "last_updated INTEGER NOT NULL",
                "last_modified_online TEXT NOT NULL",
                "etag TEXT NOT NULL",
                "is_builtin INTEGER NOT NULL",
                "size_bytes INTEGER NOT NULL",
                "health TEXT NOT NULL",
                "last_error TEXT NOT NULL",
                "consecutive_failures INTEGER NOT NULL"
            )
            if (version >= 7) {
                columns += "prev_entry_count INTEGER NOT NULL"
                columns += "domains_added INTEGER NOT NULL"
                columns += "domains_removed INTEGER NOT NULL"
            }
            if (version >= 15) {
                columns += "last_http_status INTEGER NOT NULL DEFAULT 0"
            }
            db.execSQL("CREATE TABLE host_sources (${columns.joinToString(", ")})")
            if (version >= 13) db.execSQL("CREATE INDEX index_host_sources_enabled ON host_sources (enabled)")
            if (version >= 14) db.execSQL("CREATE INDEX index_host_sources_category ON host_sources (category)")

            val insertColumns = mutableListOf(
                "url",
                "label",
                "description",
                "enabled",
                "category",
                "entry_count",
                "last_updated",
                "last_modified_online",
                "etag",
                "is_builtin",
                "size_bytes",
                "health",
                "last_error",
                "consecutive_failures"
            )
            if (version >= 7) {
                insertColumns += listOf("prev_entry_count", "domains_added", "domains_removed")
            }
            if (version >= 15) {
                insertColumns += "last_http_status"
            }

            fun insertHostSource(
                url: String,
                label: String,
                enabled: Int,
                health: String = "OK",
                lastError: String = "",
                consecutiveFailures: Int = 0,
                lastHttpStatus: Int = 0
            ) {
                val values = mutableListOf(
                    "'$url'",
                    "'$label'",
                    "'migration fixture'",
                    enabled.toString(),
                    "'ADS'",
                    "42",
                    "1800000000000",
                    "'Sat, 17 May 2026 12:00:00 GMT'",
                    "'fixture-etag'",
                    "1",
                    "2048",
                    "'$health'",
                    "'$lastError'",
                    consecutiveFailures.toString()
                )
                if (version >= 7) {
                    values += listOf("40", "2", "0")
                }
                if (version >= 15) {
                    values += lastHttpStatus.toString()
                }
                db.execSQL("INSERT INTO host_sources (${insertColumns.joinToString(", ")}) VALUES (${values.joinToString(", ")})")
            }

            insertHostSource("https://fixture.hostshield.test/hosts.txt", "Fixture Hosts", 1)
            val builtInEnabled = if (version >= 17) 1 else 0
            insertHostSource("https://adaway.org/hosts.txt", "AdAway Default", builtInEnabled)
            insertHostSource("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts", "StevenBlack Unified", builtInEnabled)
            insertHostSource(
                url = "https://disabled.fixture.test/hosts.txt",
                label = "Disabled Dead Fixture",
                enabled = 0,
                health = "DEAD",
                lastError = "source validation exceeded limit",
                consecutiveFailures = 11,
                lastHttpStatus = 200
            )
        }

        private fun createUserRules(db: SupportSQLiteDatabase, version: Int) {
            val columns = mutableListOf(
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL",
                "hostname TEXT NOT NULL",
                "type TEXT NOT NULL",
                "redirect_ip TEXT NOT NULL",
                "comment TEXT NOT NULL",
                "enabled INTEGER NOT NULL",
                "is_wildcard INTEGER NOT NULL",
                "created_at INTEGER NOT NULL"
            )
            if (version >= 7) columns.add(columns.lastIndex, "is_regex INTEGER NOT NULL")
            db.execSQL("CREATE TABLE user_rules (${columns.joinToString(", ")})")
            db.execSQL("CREATE UNIQUE INDEX index_user_rules_hostname ON user_rules (hostname)")
            if (version >= 13) db.execSQL("CREATE INDEX index_user_rules_enabled_type ON user_rules (enabled, type)")

            val insertColumns = mutableListOf("hostname", "type", "redirect_ip", "comment", "enabled", "is_wildcard")
            val values = mutableListOf("'ads.fixture.test'", "'BLOCK'", "''", "'seed'", "1", "0")
            if (version >= 7) {
                insertColumns += "is_regex"
                values += "0"
            }
            insertColumns += "created_at"
            values += "1800000000000"
            db.execSQL("INSERT INTO user_rules (${insertColumns.joinToString(", ")}) VALUES (${values.joinToString(", ")})")
        }

        private fun createDnsLogs(db: SupportSQLiteDatabase, version: Int) {
            val columns = mutableListOf(
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL",
                "hostname TEXT NOT NULL",
                "blocked INTEGER NOT NULL",
                "timestamp INTEGER NOT NULL"
            )
            if (version >= 3) columns.addAll(listOf("app_package TEXT NOT NULL", "app_label TEXT NOT NULL"))
            if (version >= 5) columns.addAll(listOf("source_ip TEXT NOT NULL", "query_type TEXT NOT NULL"))
            if (version >= 6) {
                columns += "response_time_ms INTEGER NOT NULL"
                columns += "upstream_server TEXT NOT NULL"
                columns += "cname_chain TEXT NOT NULL"
                columns += "resolved_ips TEXT NOT NULL"
            }
            if (version >= 11) {
                columns += "tracker_category TEXT NOT NULL"
                columns += "tracker_owner TEXT NOT NULL"
            }
            if (version >= 16) {
                columns += "decision_reason TEXT NOT NULL DEFAULT ''"
                columns += "decision_source TEXT NOT NULL DEFAULT ''"
                columns += "matched_value TEXT NOT NULL DEFAULT ''"
                columns += "decision_precedence TEXT NOT NULL DEFAULT ''"
            }
            db.execSQL("CREATE TABLE dns_logs (${columns.joinToString(", ")})")
            db.execSQL("CREATE INDEX index_dns_logs_timestamp ON dns_logs (timestamp)")
            if (version >= 5) {
                db.execSQL("CREATE INDEX index_dns_logs_blocked_timestamp ON dns_logs (blocked, timestamp)")
                db.execSQL("CREATE INDEX index_dns_logs_hostname ON dns_logs (hostname)")
                db.execSQL("CREATE INDEX index_dns_logs_app_package ON dns_logs (app_package)")
            }
            if (version >= 13) {
                db.execSQL("CREATE INDEX index_dns_logs_app_package_blocked_timestamp ON dns_logs (app_package, blocked, timestamp)")
            }
            if (version >= 18) {
                db.execSQL("CREATE INDEX index_dns_logs_app_package_hostname ON dns_logs (app_package, hostname)")
            }

            val insertColumns = mutableListOf("hostname", "blocked", "timestamp")
            val values = mutableListOf("'ads.fixture.test'", "1", "1800000000000")
            if (version >= 3) {
                insertColumns += listOf("app_package", "app_label")
                values += listOf("'com.fixture.app'", "'Fixture App'")
            }
            if (version >= 5) {
                insertColumns += listOf("source_ip", "query_type")
                values += listOf("'10.0.0.2'", "'A'")
            }
            if (version >= 6) {
                insertColumns += listOf("response_time_ms", "upstream_server", "cname_chain", "resolved_ips")
                values += listOf("12", "'DoH:fixture'", "''", "'203.0.113.10'")
            }
            if (version >= 11) {
                insertColumns += listOf("tracker_category", "tracker_owner")
                values += listOf("'Advertising'", "'Fixture Tracker'")
            }
            if (version >= 16) {
                insertColumns += listOf("decision_reason", "decision_source", "matched_value", "decision_precedence")
                values += listOf("'source_list'", "'Fixture Hosts'", "'ads.fixture.test'", "'exact block match'")
            }
            db.execSQL("INSERT INTO dns_logs (${insertColumns.joinToString(", ")}) VALUES (${values.joinToString(", ")})")
        }

        private fun createBlockStats(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE block_stats (
                    date TEXT NOT NULL PRIMARY KEY,
                    blocked_count INTEGER NOT NULL DEFAULT 0,
                    allowed_count INTEGER NOT NULL DEFAULT 0,
                    total_queries INTEGER NOT NULL DEFAULT 0
                )
            """)
            db.execSQL("INSERT INTO block_stats (date, blocked_count, allowed_count, total_queries) VALUES ('2026-05-17', 7, 5, 12)")
        }

        private fun createProfiles(db: SupportSQLiteDatabase, version: Int) {
            val wifi = if (version >= 7) ", wifi_ssids TEXT NOT NULL" else ""
            db.execSQL("""
                CREATE TABLE profiles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    is_active INTEGER NOT NULL DEFAULT 0,
                    source_ids TEXT NOT NULL DEFAULT '',
                    schedule_start TEXT NOT NULL DEFAULT '',
                    schedule_end TEXT NOT NULL DEFAULT '',
                    days_of_week TEXT NOT NULL DEFAULT '0,1,2,3,4,5,6'$wifi
                )
            """)
            val extraColumn = if (version >= 7) ", wifi_ssids" else ""
            val extraValue = if (version >= 7) ", 'FixtureWiFi'" else ""
            db.execSQL("INSERT INTO profiles (name, is_active, source_ids, schedule_start, schedule_end, days_of_week$extraColumn) VALUES ('fixture-profile', 1, '1', '08:00', '17:00', '1,2,3,4,5'$extraValue)")
        }

        private fun createFirewallRules(db: SupportSQLiteDatabase, version: Int) {
            val columns = mutableListOf(
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL",
                "uid INTEGER NOT NULL",
                "package_name TEXT NOT NULL",
                "app_label TEXT NOT NULL",
                "wifi_allowed INTEGER NOT NULL",
                "mobile_allowed INTEGER NOT NULL",
                "vpn_allowed INTEGER NOT NULL",
                "is_system INTEGER NOT NULL",
                "enabled INTEGER NOT NULL",
                "updated_at INTEGER NOT NULL"
            )
            if (version >= 8) {
                columns += "block_screen_off INTEGER NOT NULL"
                columns += "block_background INTEGER NOT NULL"
                columns += "block_metered INTEGER NOT NULL"
            }
            if (version >= 10) {
                columns += "blocked_countries TEXT NOT NULL DEFAULT ''"
                columns += "lan_allowed INTEGER NOT NULL DEFAULT 1"
            }
            db.execSQL("CREATE TABLE firewall_rules (${columns.joinToString(", ")})")
            db.execSQL("CREATE UNIQUE INDEX index_firewall_rules_uid ON firewall_rules (uid)")

            val insertColumns = mutableListOf("uid", "package_name", "app_label", "wifi_allowed", "mobile_allowed", "vpn_allowed", "is_system", "enabled", "updated_at")
            val values = mutableListOf("12345", "'com.fixture.app'", "'Fixture App'", "1", "0", "1", "0", "1", "1800000000000")
            if (version >= 8) {
                insertColumns += listOf("block_screen_off", "block_background", "block_metered")
                values += listOf("0", "1", "0")
            }
            if (version >= 10) {
                insertColumns += listOf("blocked_countries", "lan_allowed")
                values += listOf("'ZZ'", "1")
            }
            db.execSQL("INSERT INTO firewall_rules (${insertColumns.joinToString(", ")}) VALUES (${values.joinToString(", ")})")
        }

        private fun createConnectionLog(db: SupportSQLiteDatabase, version: Int) {
            db.execSQL("""
                CREATE TABLE connection_log (
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
            db.execSQL("CREATE INDEX index_connection_log_timestamp ON connection_log (timestamp)")
            db.execSQL("CREATE INDEX index_connection_log_uid ON connection_log (uid)")
            db.execSQL("INSERT INTO connection_log (uid, package_name, app_label, destination, port, protocol, action, interface_name, timestamp) VALUES (12345, 'com.fixture.app', 'Fixture App', '203.0.113.10', 443, 'TCP', 'REJECT', 'wlan0', 1800000000000)")
        }

        private fun createTrackerScanCache(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE tracker_scan_cache (
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
            db.execSQL("CREATE UNIQUE INDEX index_tracker_scan_cache_package_name ON tracker_scan_cache (package_name)")
            db.execSQL("INSERT INTO tracker_scan_cache (package_name, app_label, tracker_count, tracker_names, categories, scanned_at, app_version_code) VALUES ('com.fixture.app', 'Fixture App', 1, 'FixtureTracker', 'Advertising', 1800000000000, 1)")
        }

        private fun createAutomationAuditLog(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE automation_audit_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    action TEXT NOT NULL,
                    caller_uid INTEGER NOT NULL,
                    caller_package TEXT NOT NULL DEFAULT '',
                    result TEXT NOT NULL DEFAULT 'OK',
                    timestamp INTEGER NOT NULL DEFAULT 0
                )
            """)
            db.execSQL("CREATE INDEX index_automation_audit_log_timestamp ON automation_audit_log (timestamp)")
            db.execSQL("INSERT INTO automation_audit_log (action, caller_uid, caller_package, result, timestamp) VALUES ('STATUS', 2000, 'com.fixture.caller', 'OK', 1800000000000)")
        }

        private fun createVpnStability(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE vpn_stability (
                    date TEXT NOT NULL PRIMARY KEY,
                    uptime_ms INTEGER NOT NULL DEFAULT 0,
                    rebuild_count INTEGER NOT NULL DEFAULT 0,
                    fd_errors INTEGER NOT NULL DEFAULT 0,
                    dropped_queries INTEGER NOT NULL DEFAULT 0,
                    total_queries INTEGER NOT NULL DEFAULT 0
                )
            """)
            db.execSQL("INSERT INTO vpn_stability (date, uptime_ms, rebuild_count, fd_errors, dropped_queries, total_queries) VALUES ('2026-05-17', 60000, 1, 0, 0, 12)")
        }

        private fun createAppDnsRules(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE app_dns_rules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    package_name TEXT NOT NULL,
                    domain TEXT NOT NULL,
                    action TEXT NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    created_at INTEGER NOT NULL DEFAULT 0
                )
            """)
            db.execSQL("CREATE UNIQUE INDEX index_app_dns_rules_package_name_domain ON app_dns_rules (package_name, domain)")
            db.execSQL("INSERT INTO app_dns_rules (package_name, domain, action, enabled, created_at) VALUES ('com.fixture.app', 'ads.fixture.test', 'block', 1, 1800000000000)")
        }
    }

    private companion object {
        const val TEST_DB = "hostshield-migration-test"
    }
}
