package com.hostshield.service;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
import dagger.internal.InstanceFactory;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class HostsUpdateWorker_AssistedFactory_Impl implements HostsUpdateWorker_AssistedFactory {
  private final HostsUpdateWorker_Factory delegateFactory;

  HostsUpdateWorker_AssistedFactory_Impl(HostsUpdateWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public HostsUpdateWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<HostsUpdateWorker_AssistedFactory> create(
      HostsUpdateWorker_Factory delegateFactory) {
    return InstanceFactory.create(new HostsUpdateWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<HostsUpdateWorker_AssistedFactory> createFactoryProvider(
      HostsUpdateWorker_Factory delegateFactory) {
    return InstanceFactory.create(new HostsUpdateWorker_AssistedFactory_Impl(delegateFactory));
  }
}
