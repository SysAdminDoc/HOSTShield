package com.hostshield.service;

import com.hostshield.data.preferences.AppPreferences;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class DohBypassUpdater_Factory implements Factory<DohBypassUpdater> {
  private final Provider<AppPreferences> prefsProvider;

  public DohBypassUpdater_Factory(Provider<AppPreferences> prefsProvider) {
    this.prefsProvider = prefsProvider;
  }

  @Override
  public DohBypassUpdater get() {
    return newInstance(prefsProvider.get());
  }

  public static DohBypassUpdater_Factory create(Provider<AppPreferences> prefsProvider) {
    return new DohBypassUpdater_Factory(prefsProvider);
  }

  public static DohBypassUpdater newInstance(AppPreferences prefs) {
    return new DohBypassUpdater(prefs);
  }
}
