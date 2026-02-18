package com.hostshield.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hostshield.data.model.*

@Database(
    entities = [
        HostSource::class,
        UserRule::class,
        DnsLogEntry::class,
        BlockStats::class,
        BlockingProfile::class,
        FirewallRule::class,
        ConnectionLogEntry::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class HostShieldDatabase : RoomDatabase() {
    abstract fun hostSourceDao(): HostSourceDao
    abstract fun userRuleDao(): UserRuleDao
    abstract fun dnsLogDao(): DnsLogDao
    abstract fun blockStatsDao(): BlockStatsDao
    abstract fun profileDao(): ProfileDao
    abstract fun firewallRuleDao(): FirewallRuleDao
    abstract fun connectionLogDao(): ConnectionLogDao
}
