package com.hostshield.ui.screens.settings;

import com.hostshield.data.preferences.AppPreferences;
import com.hostshield.domain.BlocklistHolder;
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
public final class DnsToolsViewModel_Factory implements Factory<DnsToolsViewModel> {
  private final Provider<AppPreferences> prefsProvider;

  private final Provider<BlocklistHolder> blocklistProvider;

  public DnsToolsViewModel_Factory(Provider<AppPreferences> prefsProvider,
      Provider<BlocklistHolder> blocklistProvider) {
    this.prefsProvider = prefsProvider;
    this.blocklistProvider = blocklistProvider;
  }

  @Override
  public DnsToolsViewModel get() {
    return newInstance(prefsProvider.get(), blocklistProvider.get());
  }

  public static DnsToolsViewModel_Factory create(Provider<AppPreferences> prefsProvider,
      Provider<BlocklistHolder> blocklistProvider) {
    return new DnsToolsViewModel_Factory(prefsProvider, blocklistProvider);
  }

  public static DnsToolsViewModel newInstance(AppPreferences prefs, BlocklistHolder blocklist) {
    return new DnsToolsViewModel(prefs, blocklist);
  }
}
