package com.hostshield.ui.screens.apps;

import com.hostshield.data.database.DnsLogDao;
import com.hostshield.data.preferences.AppPreferences;
import com.hostshield.data.repository.HostShieldRepository;
import com.hostshield.domain.BlocklistHolder;
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
public final class AppsViewModel_Factory implements Factory<AppsViewModel> {
  private final Provider<DnsLogDao> dnsLogDaoProvider;

  private final Provider<HostShieldRepository> repositoryProvider;

  private final Provider<BlocklistHolder> blocklistProvider;

  private final Provider<AppPreferences> prefsProvider;

  private final Provider<RootUtil> rootUtilProvider;

  public AppsViewModel_Factory(Provider<DnsLogDao> dnsLogDaoProvider,
      Provider<HostShieldRepository> repositoryProvider,
      Provider<BlocklistHolder> blocklistProvider, Provider<AppPreferences> prefsProvider,
      Provider<RootUtil> rootUtilProvider) {
    this.dnsLogDaoProvider = dnsLogDaoProvider;
    this.repositoryProvider = repositoryProvider;
    this.blocklistProvider = blocklistProvider;
    this.prefsProvider = prefsProvider;
    this.rootUtilProvider = rootUtilProvider;
  }

  @Override
  public AppsViewModel get() {
    return newInstance(dnsLogDaoProvider.get(), repositoryProvider.get(), blocklistProvider.get(), prefsProvider.get(), rootUtilProvider.get());
  }

  public static AppsViewModel_Factory create(Provider<DnsLogDao> dnsLogDaoProvider,
      Provider<HostShieldRepository> repositoryProvider,
      Provider<BlocklistHolder> blocklistProvider, Provider<AppPreferences> prefsProvider,
      Provider<RootUtil> rootUtilProvider) {
    return new AppsViewModel_Factory(dnsLogDaoProvider, repositoryProvider, blocklistProvider, prefsProvider, rootUtilProvider);
  }

  public static AppsViewModel newInstance(DnsLogDao dnsLogDao, HostShieldRepository repository,
      BlocklistHolder blocklist, AppPreferences prefs, RootUtil rootUtil) {
    return new AppsViewModel(dnsLogDao, repository, blocklist, prefs, rootUtil);
  }
}
