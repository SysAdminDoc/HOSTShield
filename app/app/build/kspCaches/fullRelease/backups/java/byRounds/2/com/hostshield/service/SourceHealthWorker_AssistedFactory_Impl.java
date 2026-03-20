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
public final class SourceHealthWorker_AssistedFactory_Impl implements SourceHealthWorker_AssistedFactory {
  private final SourceHealthWorker_Factory delegateFactory;

  SourceHealthWorker_AssistedFactory_Impl(SourceHealthWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public SourceHealthWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<SourceHealthWorker_AssistedFactory> create(
      SourceHealthWorker_Factory delegateFactory) {
    return InstanceFactory.create(new SourceHealthWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<SourceHealthWorker_AssistedFactory> createFactoryProvider(
      SourceHealthWorker_Factory delegateFactory) {
    return InstanceFactory.create(new SourceHealthWorker_AssistedFactory_Impl(delegateFactory));
  }
}
