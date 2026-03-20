package com.hostshield.service;

import android.content.Context;
import com.hostshield.data.database.BlockStatsDao;
import com.hostshield.data.database.DnsLogDao;
import com.hostshield.data.preferences.AppPreferences;
import com.hostshield.domain.BlocklistHolder;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class RootDnsLogger_Factory implements Factory<RootDnsLogger> {
  private final Provider<Context> contextProvider;

  private final Provider<DnsLogDao> dnsLogDaoProvider;

  private final Provider<BlockStatsDao> blockStatsDaoProvider;

  private final Provider<BlocklistHolder> blocklistProvider;

  private final Provider<AppPreferences> prefsProvider;

  public RootDnsLogger_Factory(Provider<Context> contextProvider,
      Provider<DnsLogDao> dnsLogDaoProvider, Provider<BlockStatsDao> blockStatsDaoProvider,
      Provider<BlocklistHolder> blocklistProvider, Provider<AppPreferences> prefsProvider) {
    this.contextProvider = contextProvider;
    this.dnsLogDaoProvider = dnsLogDaoProvider;
    this.blockStatsDaoProvider = blockStatsDaoProvider;
    this.blocklistProvider = blocklistProvider;
    this.prefsProvider = prefsProvider;
  }

  @Override
  public RootDnsLogger get() {
    return newInstance(contextProvider.get(), dnsLogDaoProvider.get(), blockStatsDaoProvider.get(), blocklistProvider.get(), prefsProvider.get());
  }

  public static RootDnsLogger_Factory create(Provider<Context> contextProvider,
      Provider<DnsLogDao> dnsLogDaoProvider, Provider<BlockStatsDao> blockStatsDaoProvider,
      Provider<BlocklistHolder> blocklistProvider, Provider<AppPreferences> prefsProvider) {
    return new RootDnsLogger_Factory(contextProvider, dnsLogDaoProvider, blockStatsDaoProvider, blocklistProvider, prefsProvider);
  }

  public static RootDnsLogger newInstance(Context context, DnsLogDao dnsLogDao,
      BlockStatsDao blockStatsDao, BlocklistHolder blocklist, AppPreferences prefs) {
    return new RootDnsLogger(context, dnsLogDao, blockStatsDao, blocklist, prefs);
  }
}
