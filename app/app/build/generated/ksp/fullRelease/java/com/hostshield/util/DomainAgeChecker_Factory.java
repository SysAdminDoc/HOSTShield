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
public final class DomainAgeChecker_Factory implements Factory<DomainAgeChecker> {
  @Override
  public DomainAgeChecker get() {
    return newInstance();
  }

  public static DomainAgeChecker_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static DomainAgeChecker newInstance() {
    return new DomainAgeChecker();
  }

  private static final class InstanceHolder {
    private static final DomainAgeChecker_Factory INSTANCE = new DomainAgeChecker_Factory();
  }
}
