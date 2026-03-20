package com.hostshield.ui.screens.settings;

import com.hostshield.data.repository.HostShieldRepository;
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
public final class RuleTestViewModel_Factory implements Factory<RuleTestViewModel> {
  private final Provider<BlocklistHolder> blocklistProvider;

  private final Provider<HostShieldRepository> repositoryProvider;

  public RuleTestViewModel_Factory(Provider<BlocklistHolder> blocklistProvider,
      Provider<HostShieldRepository> repositoryProvider) {
    this.blocklistProvider = blocklistProvider;
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public RuleTestViewModel get() {
    return newInstance(blocklistProvider.get(), repositoryProvider.get());
  }

  public static RuleTestViewModel_Factory create(Provider<BlocklistHolder> blocklistProvider,
      Provider<HostShieldRepository> repositoryProvider) {
    return new RuleTestViewModel_Factory(blocklistProvider, repositoryProvider);
  }

  public static RuleTestViewModel newInstance(BlocklistHolder blocklist,
      HostShieldRepository repository) {
    return new RuleTestViewModel(blocklist, repository);
  }
}
