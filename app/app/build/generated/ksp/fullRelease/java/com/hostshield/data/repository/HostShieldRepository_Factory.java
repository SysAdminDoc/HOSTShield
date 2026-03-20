package com.hostshield.data.repository;

import com.hostshield.data.database.BlockStatsDao;
import com.hostshield.data.database.DnsLogDao;
import com.hostshield.data.database.HostSourceDao;
import com.hostshield.data.database.ProfileDao;
import com.hostshield.data.database.UserRuleDao;
import com.hostshield.data.source.SourceDownloader;
import com.hostshield.util.RootUtil;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class HostShieldRepository_Factory implements Factory<HostShieldRepository> {
  private final Provider<HostSourceDao> sourceDaoProvider;

  private final Provider<UserRuleDao> ruleDaoProvider;

  private final Provider<DnsLogDao> logDaoProvider;

  private final Provider<BlockStatsDao> statsDaoProvider;

  private final Provider<ProfileDao> profileDaoProvider;

  private final Provider<SourceDownloader> downloaderProvider;

  private final Provider<RootUtil> rootUtilProvider;

  public HostShieldRepository_Factory(Provider<HostSourceDao> sourceDaoProvider,
      Provider<UserRuleDao> ruleDaoProvider, Provider<DnsLogDao> logDaoProvider,
      Provider<BlockStatsDao> statsDaoProvider, Provider<ProfileDao> profileDaoProvider,
      Provider<SourceDownloader> downloaderProvider, Provider<RootUtil> rootUtilProvider) {
    this.sourceDaoProvider = sourceDaoProvider;
    this.ruleDaoProvider = ruleDaoProvider;
    this.logDaoProvider = logDaoProvider;
    this.statsDaoProvider = statsDaoProvider;
    this.profileDaoProvider = profileDaoProvider;
    this.downloaderProvider = downloaderProvider;
    this.rootUtilProvider = rootUtilProvider;
  }

  @Override
  public HostShieldRepository get() {
    return newInstance(sourceDaoProvider.get(), ruleDaoProvider.get(), logDaoProvider.get(), statsDaoProvider.get(), profileDaoProvider.get(), downloaderProvider.get(), rootUtilProvider.get());
  }

  public static HostShieldRepository_Factory create(Provider<HostSourceDao> sourceDaoProvider,
      Provider<UserRuleDao> ruleDaoProvider, Provider<DnsLogDao> logDaoProvider,
      Provider<BlockStatsDao> statsDaoProvider, Provider<ProfileDao> profileDaoProvider,
      Provider<SourceDownloader> downloaderProvider, Provider<RootUtil> rootUtilProvider) {
    return new HostShieldRepository_Factory(sourceDaoProvider, ruleDaoProvider, logDaoProvider, statsDaoProvider, profileDaoProvider, downloaderProvider, rootUtilProvider);
  }

  public static HostShieldRepository newInstance(HostSourceDao sourceDao, UserRuleDao ruleDao,
      DnsLogDao logDao, BlockStatsDao statsDao, ProfileDao profileDao, SourceDownloader downloader,
      RootUtil rootUtil) {
    return new HostShieldRepository(sourceDao, ruleDao, logDao, statsDao, profileDao, downloader, rootUtil);
  }
}
