package com.hostshield.ui.screens.settings;

import com.hostshield.util.RootUtil;
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
public final class HostsDiffViewModel_Factory implements Factory<HostsDiffViewModel> {
  private final Provider<RootUtil> rootUtilProvider;

  public HostsDiffViewModel_Factory(Provider<RootUtil> rootUtilProvider) {
    this.rootUtilProvider = rootUtilProvider;
  }

  @Override
  public HostsDiffViewModel get() {
    return newInstance(rootUtilProvider.get());
  }

  public static HostsDiffViewModel_Factory create(Provider<RootUtil> rootUtilProvider) {
    return new HostsDiffViewModel_Factory(rootUtilProvider);
  }

  public static HostsDiffViewModel newInstance(RootUtil rootUtil) {
    return new HostsDiffViewModel(rootUtil);
  }
}
