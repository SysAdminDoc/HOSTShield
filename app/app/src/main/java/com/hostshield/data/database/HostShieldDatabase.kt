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
        ConnectionLogEntry::class,
        TrackerScanCacheEntry::class,
        AppDnsRule::class,
        AutomationAuditEntry::class,
        VpnStabilityEntry::class
    ],
    version = 14,
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
    abstract fun trackerScanCacheDao(): TrackerScanCacheDao
    abstract fun automationAuditDao(): AutomationAuditDao
    abstract fun vpnStabilityDao(): VpnStabilityDao
    abstract fun appDnsRuleDao(): AppDnsRuleDao
}
