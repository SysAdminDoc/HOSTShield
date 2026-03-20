package com.hostshield.ui.screens.stats;

import com.hostshield.service.NetworkStatsTracker;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class NetworkStatsViewModel_Factory implements Factory<NetworkStatsViewModel> {
  private final Provider<NetworkStatsTracker> trackerProvider;

  public NetworkStatsViewModel_Factory(Provider<NetworkStatsTracker> trackerProvider) {
    this.trackerProvider = trackerProvider;
  }

  @Override
  public NetworkStatsViewModel get() {
    return newInstance(trackerProvider.get());
  }

  public static NetworkStatsViewModel_Factory create(
      Provider<NetworkStatsTracker> trackerProvider) {
    return new NetworkStatsViewModel_Factory(trackerProvider);
  }

  public static NetworkStatsViewModel newInstance(NetworkStatsTracker tracker) {
    return new NetworkStatsViewModel(tracker);
  }
}
