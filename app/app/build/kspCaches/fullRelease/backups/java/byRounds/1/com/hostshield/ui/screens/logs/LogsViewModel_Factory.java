package com.hostshield.ui.screens.logs;

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
public final class LogsViewModel_Factory implements Factory<LogsViewModel> {
  private final Provider<HostShieldRepository> repositoryProvider;

  private final Provider<BlocklistHolder> blocklistProvider;

  private final Provider<RootUtil> rootUtilProvider;

  private final Provider<AppPreferences> prefsProvider;

  public LogsViewModel_Factory(Provider<HostShieldRepository> repositoryProvider,
      Provider<BlocklistHolder> blocklistProvider, Provider<RootUtil> rootUtilProvider,
      Provider<AppPreferences> prefsProvider) {
    this.repositoryProvider = repositoryProvider;
    this.blocklistProvider = blocklistProvider;
    this.rootUtilProvider = rootUtilProvider;
    this.prefsProvider = prefsProvider;
  }

  @Override
  public LogsViewModel get() {
    return newInstance(repositoryProvider.get(), blocklistProvider.get(), rootUtilProvider.get(), prefsProvider.get());
  }

  public static LogsViewModel_Factory create(Provider<HostShieldRepository> repositoryProvider,
      Provider<BlocklistHolder> blocklistProvider, Provider<RootUtil> rootUtilProvider,
      Provider<AppPreferences> prefsProvider) {
    return new LogsViewModel_Factory(repositoryProvider, blocklistProvider, rootUtilProvider, prefsProvider);
  }

  public static LogsViewModel newInstance(HostShieldRepository repository,
      BlocklistHolder blocklist, RootUtil rootUtil, AppPreferences prefs) {
    return new LogsViewModel(repository, blocklist, rootUtil, prefs);
  }
}
