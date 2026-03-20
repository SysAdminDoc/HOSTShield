package com.hostshield.ui.screens.home;

import android.app.Application;
import com.hostshield.data.database.ConnectionLogDao;
import com.hostshield.data.database.DnsLogDao;
import com.hostshield.data.preferences.AppPreferences;
import com.hostshield.data.repository.HostShieldRepository;
import com.hostshield.data.source.SourceDownloader;
import com.hostshield.domain.BlocklistHolder;
import com.hostshield.service.IptablesManager;
import com.hostshield.service.NflogReader;
import com.hostshield.util.BatteryOptimizationUtil;
import com.hostshield.util.PrivacyScorer;
import com.hostshield.util.PrivateDnsDetector;
import com.hostshield.util.RootUtil;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class HomeViewModel_Factory implements Factory<HomeViewModel> {
  private final Provider<Application> applicationProvider;

  private final Provider<HostShieldRepository> repositoryProvider;

  private final Provider<RootUtil> rootUtilProvider;

  private final Provider<AppPreferences> prefsProvider;

  private final Provider<SourceDownloader> downloaderProvider;

  private final Provider<BlocklistHolder> blocklistHolderProvider;

  private final Provider<PrivateDnsDetector> privateDnsDetectorProvider;

  private final Provider<BatteryOptimizationUtil> batteryUtilProvider;

  private final Provider<IptablesManager> iptablesManagerProvider;

  private final Provider<NflogReader> nflogReaderProvider;

  private final Provider<DnsLogDao> dnsLogDaoProvider;

  private final Provider<ConnectionLogDao> connectionLogDaoProvider;

  private final Provider<PrivacyScorer> privacyScorerProvider;

  public HomeViewModel_Factory(Provider<Application> applicationProvider,
      Provider<HostShieldRepository> repositoryProvider, Provider<RootUtil> rootUtilProvider,
      Provider<AppPreferences> prefsProvider, Provider<SourceDownloader> downloaderProvider,
      Provider<BlocklistHolder> blocklistHolderProvider,
      Provider<PrivateDnsDetector> privateDnsDetectorProvider,
      Provider<BatteryOptimizationUtil> batteryUtilProvider,
      Provider<IptablesManager> iptablesManagerProvider, Provider<NflogReader> nflogReaderProvider,
      Provider<DnsLogDao> dnsLogDaoProvider, Provider<ConnectionLogDao> connectionLogDaoProvider,
      Provider<PrivacyScorer> privacyScorerProvider) {
    this.applicationProvider = applicationProvider;
    this.repositoryProvider = repositoryProvider;
    this.rootUtilProvider = rootUtilProvider;
    this.prefsProvider = prefsProvider;
    this.downloaderProvider = downloaderProvider;
    this.blocklistHolderProvider = blocklistHolderProvider;
    this.privateDnsDetectorProvider = privateDnsDetectorProvider;
    this.batteryUtilProvider = batteryUtilProvider;
    this.iptablesManagerProvider = iptablesManagerProvider;
    this.nflogReaderProvider = nflogReaderProvider;
    this.dnsLogDaoProvider = dnsLogDaoProvider;
    this.connectionLogDaoProvider = connectionLogDaoProvider;
    this.privacyScorerProvider = privacyScorerProvider;
  }

  @Override
  public HomeViewModel get() {
    return newInstance(applicationProvider.get(), repositoryProvider.get(), rootUtilProvider.get(), prefsProvider.get(), downloaderProvider.get(), blocklistHolderProvider.get(), privateDnsDetectorProvider.get(), batteryUtilProvider.get(), iptablesManagerProvider.get(), nflogReaderProvider.get(), dnsLogDaoProvider.get(), connectionLogDaoProvider.get(), privacyScorerProvider.get());
  }

  public static HomeViewModel_Factory create(Provider<Application> applicationProvider,
      Provider<HostShieldRepository> repositoryProvider, Provider<RootUtil> rootUtilProvider,
      Provider<AppPreferences> prefsProvider, Provider<SourceDownloader> downloaderProvider,
      Provider<BlocklistHolder> blocklistHolderProvider,
      Provider<PrivateDnsDetector> privateDnsDetectorProvider,
      Provider<BatteryOptimizationUtil> batteryUtilProvider,
      Provider<IptablesManager> iptablesManagerProvider, Provider<NflogReader> nflogReaderProvider,
      Provider<DnsLogDao> dnsLogDaoProvider, Provider<ConnectionLogDao> connectionLogDaoProvider,
      Provider<PrivacyScorer> privacyScorerProvider) {
    return new HomeViewModel_Factory(applicationProvider, repositoryProvider, rootUtilProvider, prefsProvider, downloaderProvider, blocklistHolderProvider, privateDnsDetectorProvider, batteryUtilProvider, iptablesManagerProvider, nflogReaderProvider, dnsLogDaoProvider, connectionLogDaoProvider, privacyScorerProvider);
  }

  public static HomeViewModel newInstance(Application application, HostShieldRepository repository,
      RootUtil rootUtil, AppPreferences prefs, SourceDownloader downloader,
      BlocklistHolder blocklistHolder, PrivateDnsDetector privateDnsDetector,
      BatteryOptimizationUtil batteryUtil, IptablesManager iptablesManager, NflogReader nflogReader,
      DnsLogDao dnsLogDao, ConnectionLogDao connectionLogDao, PrivacyScorer privacyScorer) {
    return new HomeViewModel(application, repository, rootUtil, prefs, downloader, blocklistHolder, privateDnsDetector, batteryUtil, iptablesManager, nflogReader, dnsLogDao, connectionLogDao, privacyScorer);
  }
}
