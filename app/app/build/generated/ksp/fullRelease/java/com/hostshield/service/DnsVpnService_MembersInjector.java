package com.hostshield.service;

import com.hostshield.data.database.BlockStatsDao;
import com.hostshield.data.database.DnsLogDao;
import com.hostshield.data.preferences.AppPreferences;
import com.hostshield.data.repository.HostShieldRepository;
import com.hostshield.data.source.SourceDownloader;
import com.hostshield.domain.BlocklistHolder;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class DnsVpnService_MembersInjector implements MembersInjector<DnsVpnService> {
  private final Provider<DnsLogDao> dnsLogDaoProvider;

  private final Provider<BlockStatsDao> blockStatsDaoProvider;

  private final Provider<BlocklistHolder> blocklistProvider;

  private final Provider<AppPreferences> prefsProvider;

  private final Provider<HostShieldRepository> repositoryProvider;

  private final Provider<SourceDownloader> downloaderProvider;

  private final Provider<DohResolver> dohResolverProvider;

  public DnsVpnService_MembersInjector(Provider<DnsLogDao> dnsLogDaoProvider,
      Provider<BlockStatsDao> blockStatsDaoProvider, Provider<BlocklistHolder> blocklistProvider,
      Provider<AppPreferences> prefsProvider, Provider<HostShieldRepository> repositoryProvider,
      Provider<SourceDownloader> downloaderProvider, Provider<DohResolver> dohResolverProvider) {
    this.dnsLogDaoProvider = dnsLogDaoProvider;
    this.blockStatsDaoProvider = blockStatsDaoProvider;
    this.blocklistProvider = blocklistProvider;
    this.prefsProvider = prefsProvider;
    this.repositoryProvider = repositoryProvider;
    this.downloaderProvider = downloaderProvider;
    this.dohResolverProvider = dohResolverProvider;
  }

  public static MembersInjector<DnsVpnService> create(Provider<DnsLogDao> dnsLogDaoProvider,
      Provider<BlockStatsDao> blockStatsDaoProvider, Provider<BlocklistHolder> blocklistProvider,
      Provider<AppPreferences> prefsProvider, Provider<HostShieldRepository> repositoryProvider,
      Provider<SourceDownloader> downloaderProvider, Provider<DohResolver> dohResolverProvider) {
    return new DnsVpnService_MembersInjector(dnsLogDaoProvider, blockStatsDaoProvider, blocklistProvider, prefsProvider, repositoryProvider, downloaderProvider, dohResolverProvider);
  }

  @Override
  public void injectMembers(DnsVpnService instance) {
    injectDnsLogDao(instance, dnsLogDaoProvider.get());
    injectBlockStatsDao(instance, blockStatsDaoProvider.get());
    injectBlocklist(instance, blocklistProvider.get());
    injectPrefs(instance, prefsProvider.get());
    injectRepository(instance, repositoryProvider.get());
    injectDownloader(instance, downloaderProvider.get());
    injectDohResolver(instance, dohResolverProvider.get());
  }

  @InjectedFieldSignature("com.hostshield.service.DnsVpnService.dnsLogDao")
  public static void injectDnsLogDao(DnsVpnService instance, DnsLogDao dnsLogDao) {
    instance.dnsLogDao = dnsLogDao;
  }

  @InjectedFieldSignature("com.hostshield.service.DnsVpnService.blockStatsDao")
  public static void injectBlockStatsDao(DnsVpnService instance, BlockStatsDao blockStatsDao) {
    instance.blockStatsDao = blockStatsDao;
  }

  @InjectedFieldSignature("com.hostshield.service.DnsVpnService.blocklist")
  public static void injectBlocklist(DnsVpnService instance, BlocklistHolder blocklist) {
    instance.blocklist = blocklist;
  }

  @InjectedFieldSignature("com.hostshield.service.DnsVpnService.prefs")
  public static void injectPrefs(DnsVpnService instance, AppPreferences prefs) {
    instance.prefs = prefs;
  }

  @InjectedFieldSignature("com.hostshield.service.DnsVpnService.repository")
  public static void injectRepository(DnsVpnService instance, HostShieldRepository repository) {
    instance.repository = repository;
  }

  @InjectedFieldSignature("com.hostshield.service.DnsVpnService.downloader")
  public static void injectDownloader(DnsVpnService instance, SourceDownloader downloader) {
    instance.downloader = downloader;
  }

  @InjectedFieldSignature("com.hostshield.service.DnsVpnService.dohResolver")
  public static void injectDohResolver(DnsVpnService instance, DohResolver dohResolver) {
    instance.dohResolver = dohResolver;
  }
}
