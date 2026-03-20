package com.hostshield.service;

import android.content.Context;
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
public final class NetworkStatsTracker_Factory implements Factory<NetworkStatsTracker> {
  private final Provider<Context> contextProvider;

  public NetworkStatsTracker_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public NetworkStatsTracker get() {
    return newInstance(contextProvider.get());
  }

  public static NetworkStatsTracker_Factory create(Provider<Context> contextProvider) {
    return new NetworkStatsTracker_Factory(contextProvider);
  }

  public static NetworkStatsTracker newInstance(Context context) {
    return new NetworkStatsTracker(context);
  }
}
