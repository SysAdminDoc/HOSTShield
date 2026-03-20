package com.hostshield.service;

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
public final class RootDnsService_MembersInjector implements MembersInjector<RootDnsService> {
  private final Provider<RootDnsLogger> rootDnsLoggerProvider;

  private final Provider<BlockNotificationService> blockNotificationServiceProvider;

  public RootDnsService_MembersInjector(Provider<RootDnsLogger> rootDnsLoggerProvider,
      Provider<BlockNotificationService> blockNotificationServiceProvider) {
    this.rootDnsLoggerProvider = rootDnsLoggerProvider;
    this.blockNotificationServiceProvider = blockNotificationServiceProvider;
  }

  public static MembersInjector<RootDnsService> create(
      Provider<RootDnsLogger> rootDnsLoggerProvider,
      Provider<BlockNotificationService> blockNotificationServiceProvider) {
    return new RootDnsService_MembersInjector(rootDnsLoggerProvider, blockNotificationServiceProvider);
  }

  @Override
  public void injectMembers(RootDnsService instance) {
    injectRootDnsLogger(instance, rootDnsLoggerProvider.get());
    injectBlockNotificationService(instance, blockNotificationServiceProvider.get());
  }

  @InjectedFieldSignature("com.hostshield.service.RootDnsService.rootDnsLogger")
  public static void injectRootDnsLogger(RootDnsService instance, RootDnsLogger rootDnsLogger) {
    instance.rootDnsLogger = rootDnsLogger;
  }

  @InjectedFieldSignature("com.hostshield.service.RootDnsService.blockNotificationService")
  public static void injectBlockNotificationService(RootDnsService instance,
      BlockNotificationService blockNotificationService) {
    instance.blockNotificationService = blockNotificationService;
  }
}
