package com.hostshield.service;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.hostshield.data.preferences.AppPreferences;
import com.hostshield.data.repository.HostShieldRepository;
import com.hostshield.data.source.SourceDownloader;
import com.hostshield.domain.BlocklistHolder;
import dagger.internal.DaggerGenerated;
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
public final class HostsUpdateWorker_Factory {
  private final Provider<HostShieldRepository> repositoryProvider;

  private final Provider<AppPreferences> prefsProvider;

  private final Provider<SourceDownloader> downloaderProvider;

  private final Provider<BlocklistHolder> blocklistHolderProvider;

  private final Provider<DohBypassUpdater> dohBypassUpdaterProvider;

  public HostsUpdateWorker_Factory(Provider<HostShieldRepository> repositoryProvider,
      Provider<AppPreferences> prefsProvider, Provider<SourceDownloader> downloaderProvider,
      Provider<BlocklistHolder> blocklistHolderProvider,
      Provider<DohBypassUpdater> dohBypassUpdaterProvider) {
    this.repositoryProvider = repositoryProvider;
    this.prefsProvider = prefsProvider;
    this.downloaderProvider = downloaderProvider;
    this.blocklistHolderProvider = blocklistHolderProvider;
    this.dohBypassUpdaterProvider = dohBypassUpdaterProvider;
  }

  public HostsUpdateWorker get(Context context, WorkerParameters workerParams) {
    return newInstance(context, workerParams, repositoryProvider.get(), prefsProvider.get(), downloaderProvider.get(), blocklistHolderProvider.get(), dohBypassUpdaterProvider.get());
  }

  public static HostsUpdateWorker_Factory create(Provider<HostShieldRepository> repositoryProvider,
      Provider<AppPreferences> prefsProvider, Provider<SourceDownloader> downloaderProvider,
      Provider<BlocklistHolder> blocklistHolderProvider,
      Provider<DohBypassUpdater> dohBypassUpdaterProvider) {
    return new HostsUpdateWorker_Factory(repositoryProvider, prefsProvider, downloaderProvider, blocklistHolderProvider, dohBypassUpdaterProvider);
  }

  public static HostsUpdateWorker newInstance(Context context, WorkerParameters workerParams,
      HostShieldRepository repository, AppPreferences prefs, SourceDownloader downloader,
      BlocklistHolder blocklistHolder, DohBypassUpdater dohBypassUpdater) {
    return new HostsUpdateWorker(context, workerParams, repository, prefs, downloader, blocklistHolder, dohBypassUpdater);
  }
}
