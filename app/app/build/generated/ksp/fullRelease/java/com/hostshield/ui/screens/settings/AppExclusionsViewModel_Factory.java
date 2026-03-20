package com.hostshield.ui.screens.settings;

import com.hostshield.data.preferences.AppPreferences;
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
public final class AppExclusionsViewModel_Factory implements Factory<AppExclusionsViewModel> {
  private final Provider<AppPreferences> prefsProvider;

  public AppExclusionsViewModel_Factory(Provider<AppPreferences> prefsProvider) {
    this.prefsProvider = prefsProvider;
  }

  @Override
  public AppExclusionsViewModel get() {
    return newInstance(prefsProvider.get());
  }

  public static AppExclusionsViewModel_Factory create(Provider<AppPreferences> prefsProvider) {
    return new AppExclusionsViewModel_Factory(prefsProvider);
  }

  public static AppExclusionsViewModel newInstance(AppPreferences prefs) {
    return new AppExclusionsViewModel(prefs);
  }
}
