package com.hostshield;

import androidx.hilt.work.HiltWorkerFactory;
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
public final class HostShieldApp_MembersInjector implements MembersInjector<HostShieldApp> {
  private final Provider<HiltWorkerFactory> workerFactoryProvider;

  public HostShieldApp_MembersInjector(Provider<HiltWorkerFactory> workerFactoryProvider) {
    this.workerFactoryProvider = workerFactoryProvider;
  }

  public static MembersInjector<HostShieldApp> create(
      Provider<HiltWorkerFactory> workerFactoryProvider) {
    return new HostShieldApp_MembersInjector(workerFactoryProvider);
  }

  @Override
  public void injectMembers(HostShieldApp instance) {
    injectWorkerFactory(instance, workerFactoryProvider.get());
  }

  @InjectedFieldSignature("com.hostshield.HostShieldApp.workerFactory")
  public static void injectWorkerFactory(HostShieldApp instance, HiltWorkerFactory workerFactory) {
    instance.workerFactory = workerFactory;
  }
}
