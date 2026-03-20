package com.hostshield.ui.screens.apps;

import com.hostshield.util.AppPrivacyScorer;
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
public final class AppPrivacyViewModel_Factory implements Factory<AppPrivacyViewModel> {
  private final Provider<AppPrivacyScorer> scorerProvider;

  public AppPrivacyViewModel_Factory(Provider<AppPrivacyScorer> scorerProvider) {
    this.scorerProvider = scorerProvider;
  }

  @Override
  public AppPrivacyViewModel get() {
    return newInstance(scorerProvider.get());
  }

  public static AppPrivacyViewModel_Factory create(Provider<AppPrivacyScorer> scorerProvider) {
    return new AppPrivacyViewModel_Factory(scorerProvider);
  }

  public static AppPrivacyViewModel newInstance(AppPrivacyScorer scorer) {
    return new AppPrivacyViewModel(scorer);
  }
}
