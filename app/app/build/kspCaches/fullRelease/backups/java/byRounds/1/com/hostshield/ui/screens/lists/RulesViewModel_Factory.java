package com.hostshield.ui.screens.lists;

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
public final class RulesViewModel_Factory implements Factory<RulesViewModel> {
  private final Provider<HostShieldRepository> repositoryProvider;

  public RulesViewModel_Factory(Provider<HostShieldRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public RulesViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static RulesViewModel_Factory create(Provider<HostShieldRepository> repositoryProvider) {
    return new RulesViewModel_Factory(repositoryProvider);
  }

  public static RulesViewModel newInstance(HostShieldRepository repository) {
    return new RulesViewModel(repository);
  }
}
