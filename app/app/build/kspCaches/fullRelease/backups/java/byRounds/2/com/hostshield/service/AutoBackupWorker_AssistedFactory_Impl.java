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
public final class AutoBackupWorker_AssistedFactory_Impl implements AutoBackupWorker_AssistedFactory {
  private final AutoBackupWorker_Factory delegateFactory;

  AutoBackupWorker_AssistedFactory_Impl(AutoBackupWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public AutoBackupWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<AutoBackupWorker_AssistedFactory> create(
      AutoBackupWorker_Factory delegateFactory) {
    return InstanceFactory.create(new AutoBackupWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<AutoBackupWorker_AssistedFactory> createFactoryProvider(
      AutoBackupWorker_Factory delegateFactory) {
    return InstanceFactory.create(new AutoBackupWorker_AssistedFactory_Impl(delegateFactory));
  }
}
