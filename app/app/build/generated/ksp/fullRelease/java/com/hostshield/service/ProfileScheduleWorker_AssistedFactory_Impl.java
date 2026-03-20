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
public final class ProfileScheduleWorker_AssistedFactory_Impl implements ProfileScheduleWorker_AssistedFactory {
  private final ProfileScheduleWorker_Factory delegateFactory;

  ProfileScheduleWorker_AssistedFactory_Impl(ProfileScheduleWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public ProfileScheduleWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<ProfileScheduleWorker_AssistedFactory> create(
      ProfileScheduleWorker_Factory delegateFactory) {
    return InstanceFactory.create(new ProfileScheduleWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<ProfileScheduleWorker_AssistedFactory> createFactoryProvider(
      ProfileScheduleWorker_Factory delegateFactory) {
    return InstanceFactory.create(new ProfileScheduleWorker_AssistedFactory_Impl(delegateFactory));
  }
}
