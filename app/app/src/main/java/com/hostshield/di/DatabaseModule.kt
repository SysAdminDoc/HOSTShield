package com.hostshield.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hostshield.data.database.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// HostShield v1.6.0 - Database DI Module

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // v1->v2: Added block_stats table
    // Entity: date TEXT PK, blocked_count, allowed_count, total_queries
    private val MIGRATION_1_2 = object : Migration(1, 2) {
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

    // v2->v3: Added profiles table + dns_logs app columns
    private val MIGRATION_2_3 = object : Migration(2, 3) {
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
            // Add per-app tracking columns to dns_logs
            try {
                db.execSQL("ALTER TABLE dns_logs ADD COLUMN app_package TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE dns_logs ADD COLUMN app_label TEXT NOT NULL DEFAULT ''")
            } catch (_: Exception) { /* columns may already exist */ }
        }
    }

    // v3->v4: Added firewall_rules + connection_log tables
    private val MIGRATION_3_4 = object : Migration(3, 4) {
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
                    package_name TEXT NOT NULL DEFAULT '',
                    app_label TEXT NOT NULL DEFAULT '',
                    destination TEXT NOT NULL DEFAULT '',
                    port INTEGER NOT NULL DEFAULT 0,
                    protocol TEXT NOT NULL DEFAULT 'TCP',
                    action TEXT NOT NULL DEFAULT 'REJECT',
                    interface_name TEXT NOT NULL DEFAULT '',
                    timestamp INTEGER NOT NULL DEFAULT 0
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_connection_log_timestamp ON connection_log (timestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_connection_log_uid ON connection_log (uid)")
        }
    }

    // v4->v5: Add composite index on dns_logs (blocked, timestamp) for fast filtered queries.
    //         Also add source_ip and query_type columns that were added to the entity but
    //         never had migration ALTER TABLE statements.
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_dns_logs_blocked_timestamp ON dns_logs (blocked, timestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_connection_log_action_timestamp ON connection_log (action, timestamp)")
            // Columns added to DnsLogEntry entity but missing from previous migrations
            try { db.execSQL("ALTER TABLE dns_logs ADD COLUMN source_ip TEXT NOT NULL DEFAULT ''") } catch (_: Exception) { }
            try { db.execSQL("ALTER TABLE dns_logs ADD COLUMN query_type TEXT NOT NULL DEFAULT 'A'") } catch (_: Exception) { }
            // Indices on hostname and app_package (defined in entity annotations)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_dns_logs_hostname ON dns_logs (hostname)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_dns_logs_app_package ON dns_logs (app_package)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HostShieldDatabase {
        @Suppress("DEPRECATION")
        return Room.databaseBuilder(
            context,
            HostShieldDatabase::class.java,
            "hostshield.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                com.hostshield.data.database.Migrations.MIGRATION_5_6)
            .fallbackToDestructiveMigration() // safety net for unhandled versions
            .build()
    }

    @Provides fun provideHostSourceDao(db: HostShieldDatabase): HostSourceDao = db.hostSourceDao()
    @Provides fun provideUserRuleDao(db: HostShieldDatabase): UserRuleDao = db.userRuleDao()
    @Provides fun provideDnsLogDao(db: HostShieldDatabase): DnsLogDao = db.dnsLogDao()
    @Provides fun provideBlockStatsDao(db: HostShieldDatabase): BlockStatsDao = db.blockStatsDao()
    @Provides fun provideProfileDao(db: HostShieldDatabase): ProfileDao = db.profileDao()
    @Provides fun provideFirewallRuleDao(db: HostShieldDatabase): FirewallRuleDao = db.firewallRuleDao()
    @Provides fun provideConnectionLogDao(db: HostShieldDatabase): ConnectionLogDao = db.connectionLogDao()
}
