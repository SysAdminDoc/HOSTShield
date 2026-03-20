package com.hostshield.util;

import com.hostshield.data.database.ConnectionLogDao;
import com.hostshield.data.database.DnsLogDao;
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
public final class PcapExporter_Factory implements Factory<PcapExporter> {
  private final Provider<DnsLogDao> dnsLogDaoProvider;

  private final Provider<ConnectionLogDao> connectionLogDaoProvider;

  public PcapExporter_Factory(Provider<DnsLogDao> dnsLogDaoProvider,
      Provider<ConnectionLogDao> connectionLogDaoProvider) {
    this.dnsLogDaoProvider = dnsLogDaoProvider;
    this.connectionLogDaoProvider = connectionLogDaoProvider;
  }

  @Override
  public PcapExporter get() {
    return newInstance(dnsLogDaoProvider.get(), connectionLogDaoProvider.get());
  }

  public static PcapExporter_Factory create(Provider<DnsLogDao> dnsLogDaoProvider,
      Provider<ConnectionLogDao> connectionLogDaoProvider) {
    return new PcapExporter_Factory(dnsLogDaoProvider, connectionLogDaoProvider);
  }

  public static PcapExporter newInstance(DnsLogDao dnsLogDao, ConnectionLogDao connectionLogDao) {
    return new PcapExporter(dnsLogDao, connectionLogDao);
  }
}
