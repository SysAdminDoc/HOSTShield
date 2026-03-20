package com.hostshield.ui.screens.sources;

import com.hostshield.data.repository.HostShieldRepository;
import com.hostshield.data.source.SourceDownloader;
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
public final class OverlapViewModel_Factory implements Factory<OverlapViewModel> {
  private final Provider<HostShieldRepository> repositoryProvider;

  private final Provider<SourceDownloader> downloaderProvider;

  public OverlapViewModel_Factory(Provider<HostShieldRepository> repositoryProvider,
      Provider<SourceDownloader> downloaderProvider) {
    this.repositoryProvider = repositoryProvider;
    this.downloaderProvider = downloaderProvider;
  }

  @Override
  public OverlapViewModel get() {
    return newInstance(repositoryProvider.get(), downloaderProvider.get());
  }

  public static OverlapViewModel_Factory create(Provider<HostShieldRepository> repositoryProvider,
      Provider<SourceDownloader> downloaderProvider) {
    return new OverlapViewModel_Factory(repositoryProvider, downloaderProvider);
  }

  public static OverlapViewModel newInstance(HostShieldRepository repository,
      SourceDownloader downloader) {
    return new OverlapViewModel(repository, downloader);
  }
}
