package com.hostshield.domain;

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
public final class BlocklistHolder_Factory implements Factory<BlocklistHolder> {
  @Override
  public BlocklistHolder get() {
    return newInstance();
  }

  public static BlocklistHolder_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static BlocklistHolder newInstance() {
    return new BlocklistHolder();
  }

  private static final class InstanceHolder {
    private static final BlocklistHolder_Factory INSTANCE = new BlocklistHolder_Factory();
  }
}
