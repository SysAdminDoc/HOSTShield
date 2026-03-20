package com.hostshield.util;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class SuspiciousTldDetector_Factory implements Factory<SuspiciousTldDetector> {
  @Override
  public SuspiciousTldDetector get() {
    return newInstance();
  }

  public static SuspiciousTldDetector_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static SuspiciousTldDetector newInstance() {
    return new SuspiciousTldDetector();
  }

  private static final class InstanceHolder {
    private static final SuspiciousTldDetector_Factory INSTANCE = new SuspiciousTldDetector_Factory();
  }
}
