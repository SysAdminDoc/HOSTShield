package com.hostshield.service;

import android.content.Context;
import com.hostshield.data.database.DnsLogDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class BlockNotificationService_Factory implements Factory<BlockNotificationService> {
  private final Provider<Context> contextProvider;

  private final Provider<DnsLogDao> dnsLogDaoProvider;

  public BlockNotificationService_Factory(Provider<Context> contextProvider,
      Provider<DnsLogDao> dnsLogDaoProvider) {
    this.contextProvider = contextProvider;
    this.dnsLogDaoProvider = dnsLogDaoProvider;
  }

  @Override
  public BlockNotificationService get() {
    return newInstance(contextProvider.get(), dnsLogDaoProvider.get());
  }

  public static BlockNotificationService_Factory create(Provider<Context> contextProvider,
      Provider<DnsLogDao> dnsLogDaoProvider) {
    return new BlockNotificationService_Factory(contextProvider, dnsLogDaoProvider);
  }

  public static BlockNotificationService newInstance(Context context, DnsLogDao dnsLogDao) {
    return new BlockNotificationService(context, dnsLogDao);
  }
}
