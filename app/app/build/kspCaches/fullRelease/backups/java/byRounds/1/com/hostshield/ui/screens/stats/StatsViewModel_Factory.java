package com.hostshield.ui.screens.stats;

import com.hostshield.data.repository.HostShieldRepository;
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
public final class StatsViewModel_Factory implements Factory<StatsViewModel> {
  private final Provider<HostShieldRepository> repositoryProvider;

  public StatsViewModel_Factory(Provider<HostShieldRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public StatsViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static StatsViewModel_Factory create(Provider<HostShieldRepository> repositoryProvider) {
    return new StatsViewModel_Factory(repositoryProvider);
  }

  public static StatsViewModel newInstance(HostShieldRepository repository) {
    return new StatsViewModel(repository);
  }
}
