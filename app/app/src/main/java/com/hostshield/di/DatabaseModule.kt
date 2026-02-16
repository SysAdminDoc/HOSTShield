package com.hostshield.di

import android.content.Context
import androidx.room.Room
import com.hostshield.data.database.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HostShieldDatabase {
        @Suppress("DEPRECATION")
        return Room.databaseBuilder(
            context,
            HostShieldDatabase::class.java,
            "hostshield.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideHostSourceDao(db: HostShieldDatabase): HostSourceDao = db.hostSourceDao()
    @Provides fun provideUserRuleDao(db: HostShieldDatabase): UserRuleDao = db.userRuleDao()
    @Provides fun provideDnsLogDao(db: HostShieldDatabase): DnsLogDao = db.dnsLogDao()
    @Provides fun provideBlockStatsDao(db: HostShieldDatabase): BlockStatsDao = db.blockStatsDao()
    @Provides fun provideProfileDao(db: HostShieldDatabase): ProfileDao = db.profileDao()
}
