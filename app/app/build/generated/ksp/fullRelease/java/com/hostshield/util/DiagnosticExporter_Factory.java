package com.hostshield.util;

import com.hostshield.data.database.ConnectionLogDao;
import com.hostshield.data.database.DnsLogDao;
import com.hostshield.data.preferences.AppPreferences;
import com.hostshield.domain.BlocklistHolder;
import com.hostshield.service.IptablesManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
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
public final class DiagnosticExporter_Factory implements Factory<DiagnosticExporter> {
  private final Provider<AppPreferences> prefsProvider;

  private final Provider<BlocklistHolder> blocklistProvider;

  private final Provider<IptablesManager> iptablesManagerProvider;

  private final Provider<DnsLogDao> dnsLogDaoProvider;

  private final Provider<ConnectionLogDao> connectionLogDaoProvider;

  private final Provider<PrivateDnsDetector> privateDnsDetectorProvider;

  public DiagnosticExporter_Factory(Provider<AppPreferences> prefsProvider,
      Provider<BlocklistHolder> blocklistProvider,
      Provider<IptablesManager> iptablesManagerProvider, Provider<DnsLogDao> dnsLogDaoProvider,
      Provider<ConnectionLogDao> connectionLogDaoProvider,
      Provider<PrivateDnsDetector> privateDnsDetectorProvider) {
    this.prefsProvider = prefsProvider;
    this.blocklistProvider = blocklistProvider;
    this.iptablesManagerProvider = iptablesManagerProvider;
    this.dnsLogDaoProvider = dnsLogDaoProvider;
    this.connectionLogDaoProvider = connectionLogDaoProvider;
    this.privateDnsDetectorProvider = privateDnsDetectorProvider;
  }

  @Override
  public DiagnosticExporter get() {
    return newInstance(prefsProvider.get(), blocklistProvider.get(), iptablesManagerProvider.get(), dnsLogDaoProvider.get(), connectionLogDaoProvider.get(), privateDnsDetectorProvider.get());
  }

  public static DiagnosticExporter_Factory create(Provider<AppPreferences> prefsProvider,
      Provider<BlocklistHolder> blocklistProvider,
      Provider<IptablesManager> iptablesManagerProvider, Provider<DnsLogDao> dnsLogDaoProvider,
      Provider<ConnectionLogDao> connectionLogDaoProvider,
      Provider<PrivateDnsDetector> privateDnsDetectorProvider) {
    return new DiagnosticExporter_Factory(prefsProvider, blocklistProvider, iptablesManagerProvider, dnsLogDaoProvider, connectionLogDaoProvider, privateDnsDetectorProvider);
  }

  public static DiagnosticExporter newInstance(AppPreferences prefs, BlocklistHolder blocklist,
      IptablesManager iptablesManager, DnsLogDao dnsLogDao, ConnectionLogDao connectionLogDao,
      PrivateDnsDetector privateDnsDetector) {
    return new DiagnosticExporter(prefs, blocklist, iptablesManager, dnsLogDao, connectionLogDao, privateDnsDetector);
  }
}
