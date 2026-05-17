package com.hostshield.di

import android.content.Context
import androidx.room.Room
import com.hostshield.data.database.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

// Database and network dependency injection module

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HostShieldDatabase {
        return Room.databaseBuilder(
            context,
            HostShieldDatabase::class.java,
            "hostshield.db"
        )
            .addMigrations(*Migrations.ALL)
            // No fallbackToDestructiveMigration — every version must have an explicit migration.
            // Missing migrations will crash on startup, surfacing the bug during development
            // rather than silently deleting user data in production.
            .build()
    }

    @Provides @Singleton fun provideHostSourceDao(db: HostShieldDatabase): HostSourceDao = db.hostSourceDao()
    @Provides @Singleton fun provideUserRuleDao(db: HostShieldDatabase): UserRuleDao = db.userRuleDao()
    @Provides @Singleton fun provideDnsLogDao(db: HostShieldDatabase): DnsLogDao = db.dnsLogDao()
    @Provides @Singleton fun provideBlockStatsDao(db: HostShieldDatabase): BlockStatsDao = db.blockStatsDao()
    @Provides @Singleton fun provideProfileDao(db: HostShieldDatabase): ProfileDao = db.profileDao()
    @Provides @Singleton fun provideFirewallRuleDao(db: HostShieldDatabase): FirewallRuleDao = db.firewallRuleDao()
    @Provides @Singleton fun provideConnectionLogDao(db: HostShieldDatabase): ConnectionLogDao = db.connectionLogDao()
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton fun provideTrackerScanCacheDao(db: HostShieldDatabase): TrackerScanCacheDao = db.trackerScanCacheDao()
    @Provides @Singleton fun provideAutomationAuditDao(db: HostShieldDatabase): AutomationAuditDao = db.automationAuditDao()
    @Provides @Singleton fun provideVpnStabilityDao(db: HostShieldDatabase): VpnStabilityDao = db.vpnStabilityDao()
    @Provides @Singleton fun provideAppDnsRuleDao(db: HostShieldDatabase): AppDnsRuleDao = db.appDnsRuleDao()
}
