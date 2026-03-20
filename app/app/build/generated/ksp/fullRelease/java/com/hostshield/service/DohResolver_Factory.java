package com.hostshield.service;

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
public final class DohResolver_Factory implements Factory<DohResolver> {
  @Override
  public DohResolver get() {
    return newInstance();
  }

  public static DohResolver_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static DohResolver newInstance() {
    return new DohResolver();
  }

  private static final class InstanceHolder {
    private static final DohResolver_Factory INSTANCE = new DohResolver_Factory();
  }
}
