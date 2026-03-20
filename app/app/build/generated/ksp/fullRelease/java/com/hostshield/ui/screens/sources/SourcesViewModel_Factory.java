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
public final class SourcesViewModel_Factory implements Factory<SourcesViewModel> {
  private final Provider<HostShieldRepository> repositoryProvider;

  private final Provider<SourceDownloader> downloaderProvider;

  public SourcesViewModel_Factory(Provider<HostShieldRepository> repositoryProvider,
      Provider<SourceDownloader> downloaderProvider) {
    this.repositoryProvider = repositoryProvider;
    this.downloaderProvider = downloaderProvider;
  }

  @Override
  public SourcesViewModel get() {
    return newInstance(repositoryProvider.get(), downloaderProvider.get());
  }

  public static SourcesViewModel_Factory create(Provider<HostShieldRepository> repositoryProvider,
      Provider<SourceDownloader> downloaderProvider) {
    return new SourcesViewModel_Factory(repositoryProvider, downloaderProvider);
  }

  public static SourcesViewModel newInstance(HostShieldRepository repository,
      SourceDownloader downloader) {
    return new SourcesViewModel(repository, downloader);
  }
}
