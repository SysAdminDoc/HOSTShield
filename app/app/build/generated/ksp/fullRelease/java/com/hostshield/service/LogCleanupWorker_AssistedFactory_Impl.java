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
public final class LogCleanupWorker_AssistedFactory_Impl implements LogCleanupWorker_AssistedFactory {
  private final LogCleanupWorker_Factory delegateFactory;

  LogCleanupWorker_AssistedFactory_Impl(LogCleanupWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public LogCleanupWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<LogCleanupWorker_AssistedFactory> create(
      LogCleanupWorker_Factory delegateFactory) {
    return InstanceFactory.create(new LogCleanupWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<LogCleanupWorker_AssistedFactory> createFactoryProvider(
      LogCleanupWorker_Factory delegateFactory) {
    return InstanceFactory.create(new LogCleanupWorker_AssistedFactory_Impl(delegateFactory));
  }
}
