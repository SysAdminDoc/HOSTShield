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
public final class ImportExportUtil_Factory implements Factory<ImportExportUtil> {
  @Override
  public ImportExportUtil get() {
    return newInstance();
  }

  public static ImportExportUtil_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ImportExportUtil newInstance() {
    return new ImportExportUtil();
  }

  private static final class InstanceHolder {
    private static final ImportExportUtil_Factory INSTANCE = new ImportExportUtil_Factory();
  }
}
