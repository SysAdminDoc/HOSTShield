package com.hostshield.data.source;

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
public final class SourceDownloader_Factory implements Factory<SourceDownloader> {
  @Override
  public SourceDownloader get() {
    return newInstance();
  }

  public static SourceDownloader_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static SourceDownloader newInstance() {
    return new SourceDownloader();
  }

  private static final class InstanceHolder {
    private static final SourceDownloader_Factory INSTANCE = new SourceDownloader_Factory();
  }
}
