package com.hostshield.util;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class PrivateDnsDetector_Factory implements Factory<PrivateDnsDetector> {
  private final Provider<Context> contextProvider;

  public PrivateDnsDetector_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public PrivateDnsDetector get() {
    return newInstance(contextProvider.get());
  }

  public static PrivateDnsDetector_Factory create(Provider<Context> contextProvider) {
    return new PrivateDnsDetector_Factory(contextProvider);
  }

  public static PrivateDnsDetector newInstance(Context context) {
    return new PrivateDnsDetector(context);
  }
}
