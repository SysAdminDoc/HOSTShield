package com.hostshield.service;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.hostshield.data.database.ProfileDao;
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
public final class ProfileScheduleWorker_Factory {
  private final Provider<ProfileDao> profileDaoProvider;

  private final Provider<HostShieldRepository> repositoryProvider;

  private final Provider<AppPreferences> prefsProvider;

  private final Provider<IptablesManager> iptablesManagerProvider;

  private final Provider<SourceDownloader> downloaderProvider;

  private final Provider<BlocklistHolder> blocklistHolderProvider;

  public ProfileScheduleWorker_Factory(Provider<ProfileDao> profileDaoProvider,
      Provider<HostShieldRepository> repositoryProvider, Provider<AppPreferences> prefsProvider,
      Provider<IptablesManager> iptablesManagerProvider,
      Provider<SourceDownloader> downloaderProvider,
      Provider<BlocklistHolder> blocklistHolderProvider) {
    this.profileDaoProvider = profileDaoProvider;
    this.repositoryProvider = repositoryProvider;
    this.prefsProvider = prefsProvider;
    this.iptablesManagerProvider = iptablesManagerProvider;
    this.downloaderProvider = downloaderProvider;
    this.blocklistHolderProvider = blocklistHolderProvider;
  }

  public ProfileScheduleWorker get(Context context, WorkerParameters params) {
    return newInstance(context, params, profileDaoProvider.get(), repositoryProvider.get(), prefsProvider.get(), iptablesManagerProvider.get(), downloaderProvider.get(), blocklistHolderProvider.get());
  }

  public static ProfileScheduleWorker_Factory create(Provider<ProfileDao> profileDaoProvider,
      Provider<HostShieldRepository> repositoryProvider, Provider<AppPreferences> prefsProvider,
      Provider<IptablesManager> iptablesManagerProvider,
      Provider<SourceDownloader> downloaderProvider,
      Provider<BlocklistHolder> blocklistHolderProvider) {
    return new ProfileScheduleWorker_Factory(profileDaoProvider, repositoryProvider, prefsProvider, iptablesManagerProvider, downloaderProvider, blocklistHolderProvider);
  }

  public static ProfileScheduleWorker newInstance(Context context, WorkerParameters params,
      ProfileDao profileDao, HostShieldRepository repository, AppPreferences prefs,
      IptablesManager iptablesManager, SourceDownloader downloader,
      BlocklistHolder blocklistHolder) {
    return new ProfileScheduleWorker(context, params, profileDao, repository, prefs, iptablesManager, downloader, blocklistHolder);
  }
}
