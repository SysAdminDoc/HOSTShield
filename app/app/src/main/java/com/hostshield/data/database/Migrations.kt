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
 * - v1: Initial (host_sources, user_rules, dns_logs, block_stats)
 * - v2: Added profiles table
 * - v3: Added firewall_rules table + user_rules.is_wildcard column
 * - v4: Added connection_log table + indices
 * - v5: Added dns_logs.query_type + dns_logs indices + source health columns
 * - v6: Added dns_logs.response_time_ms, dns_logs.upstream_server,
 *        dns_logs.cname_chain columns for per-query detail view
 */
object Migrations {

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

    /** All migrations in order. Pass to Room.databaseBuilder().addMigrations(). */
    val ALL = arrayOf(MIGRATION_5_6)
}
