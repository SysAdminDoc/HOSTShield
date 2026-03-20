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
public final class BlockingScheduleWorker_AssistedFactory_Impl implements BlockingScheduleWorker_AssistedFactory {
  private final BlockingScheduleWorker_Factory delegateFactory;

  BlockingScheduleWorker_AssistedFactory_Impl(BlockingScheduleWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public BlockingScheduleWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<BlockingScheduleWorker_AssistedFactory> create(
      BlockingScheduleWorker_Factory delegateFactory) {
    return InstanceFactory.create(new BlockingScheduleWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<BlockingScheduleWorker_AssistedFactory> createFactoryProvider(
      BlockingScheduleWorker_Factory delegateFactory) {
    return InstanceFactory.create(new BlockingScheduleWorker_AssistedFactory_Impl(delegateFactory));
  }
}
