package com.hostshield.util;

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
public final class PrivacyScorer_Factory implements Factory<PrivacyScorer> {
  private final Provider<AppPreferences> prefsProvider;

  private final Provider<BatteryOptimizationUtil> batteryUtilProvider;

  public PrivacyScorer_Factory(Provider<AppPreferences> prefsProvider,
      Provider<BatteryOptimizationUtil> batteryUtilProvider) {
    this.prefsProvider = prefsProvider;
    this.batteryUtilProvider = batteryUtilProvider;
  }

  @Override
  public PrivacyScorer get() {
    return newInstance(prefsProvider.get(), batteryUtilProvider.get());
  }

  public static PrivacyScorer_Factory create(Provider<AppPreferences> prefsProvider,
      Provider<BatteryOptimizationUtil> batteryUtilProvider) {
    return new PrivacyScorer_Factory(prefsProvider, batteryUtilProvider);
  }

  public static PrivacyScorer newInstance(AppPreferences prefs,
      BatteryOptimizationUtil batteryUtil) {
    return new PrivacyScorer(prefs, batteryUtil);
  }
}
