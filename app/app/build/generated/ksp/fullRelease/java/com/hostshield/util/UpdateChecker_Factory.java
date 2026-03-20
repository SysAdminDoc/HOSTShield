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
public final class UpdateChecker_Factory implements Factory<UpdateChecker> {
  @Override
  public UpdateChecker get() {
    return newInstance();
  }

  public static UpdateChecker_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static UpdateChecker newInstance() {
    return new UpdateChecker();
  }

  private static final class InstanceHolder {
    private static final UpdateChecker_Factory INSTANCE = new UpdateChecker_Factory();
  }
}
